package app.photos.unlimited;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/**
 * Optional profile lookup with its own token; never changes the upload credential or scopes.
 * Every failure carries a fixed, secret-free label so a refused lookup can be diagnosed.
 */
final class ProfileApi {
  static final String USERINFO = "https://openidconnect.googleapis.com/v1/userinfo";
  static final String SCOPES =
      "oauth2:openid https://www.googleapis.com/auth/userinfo.email"
          + " https://www.googleapis.com/auth/userinfo.profile";
  static final int AUTH_LIMIT = 16 * 1024;
  static final int USERINFO_LIMIT = 64 * 1024;
  static final int IMAGE_LIMIT = 2 * 1024 * 1024;
  static final int MAX_REDIRECTS = 4;
  static final String GMS = "com.google.android.gms";

  record Profile(String name, String picture) {}

  /** The profile name and the downloaded image bytes; image is null when there is no picture. */
  record Fetched(String name, byte[] image) {}

  /** Status -1 means the body was larger than the step's limit. */
  record Reply(int status, String contentType, String location, byte[] body) {}

  interface Transport {
    Reply call(
        String url, String method, Map<String, String> headers, byte[] body, AtomicBoolean stop)
        throws IOException;
  }

  /** A profile failure with a fixed label made only of A-Z, 0-9 and underscore. */
  static final class Failure extends IOException {
    private final String label;

    Failure(String label) {
      super("Profile lookup failed: " + label);
      if (label == null || !label.matches("[A-Z0-9_]{1,48}"))
        throw new IllegalArgumentException("Invalid profile failure label");
      this.label = label;
    }

    String label() {
      return label;
    }
  }

  static String errorLabel(String googleError, int status) {
    String value = googleError == null ? "" : googleError.replaceAll("[^A-Za-z0-9]", "");
    value = value.toUpperCase(Locale.ROOT);
    if (value.length() > 36) value = value.substring(0, 36);
    return value.isEmpty() ? "AUTH_HTTP_" + status : "AUTH_ERROR_" + value;
  }

  private static String status(String prefix, int status) {
    return prefix + (status < 0 ? 0 : Math.min(status, 999));
  }

  static Fetched fetch(String credential, Transport transport, AtomicBoolean stop)
      throws IOException {
    Map<String, String> form;
    try {
      form = PhotosApi.parseForm(credential);
    } catch (IOException e) {
      throw new Failure("NO_CREDENTIAL");
    }
    String email = form.getOrDefault("Email", "");
    if (email.isBlank()
        || form.getOrDefault("Token", "").isBlank()
        || form.getOrDefault("androidId", "").isBlank()) throw new Failure("NO_CREDENTIAL");
    form.remove("it_caveat_types");
    form.remove("assertion_jwt");
    form.put("service", SCOPES);
    // Google refuses profile scopes to the Photos client (Error=RestrictedClient), so this
    // separate profile-only token is requested as Google Play services. The upload credential
    // and its scopes are unchanged.
    form.put("app", GMS);
    form.put("callerPkg", GMS);
    form.put("client_sig", PhotosApi.GMS_SIG);
    if (form.containsKey("callerSig")) form.put("callerSig", PhotosApi.GMS_SIG);
    byte[] data = PhotosApi.form(form).getBytes(StandardCharsets.UTF_8);
    Reply auth =
        transport.call(
            PhotosApi.AUTH,
            "POST",
            Map.of(
                "Content-Type",
                "application/x-www-form-urlencoded",
                "User-Agent",
                "GoogleAuth/1.4 (Pixel XL PQ2A.190205.001)",
                "device",
                form.get("androidId"),
                "app",
                GMS),
            data,
            stop);
    if (auth.status() == -1) throw new Failure("AUTH_TOO_LARGE");
    Map<String, String> result = PhotosApi.lines(new String(auth.body(), StandardCharsets.UTF_8));
    if (result.containsKey("Error"))
      throw new Failure(errorLabel(result.get("Error"), auth.status()));
    if (auth.status() != 200) throw new Failure(status("AUTH_HTTP_", auth.status()));
    String token = result.getOrDefault("Auth", "");
    if (token.isBlank()) throw new Failure("AUTH_NO_TOKEN");

    Reply info =
        transport.call(USERINFO, "GET", Map.of("Authorization", "Bearer " + token), null, stop);
    if (info.status() == -1) throw new Failure("USERINFO_TOO_LARGE");
    if (info.status() != 200) throw new Failure(status("USERINFO_HTTP_", info.status()));
    Profile profile = parse(info.body(), email);
    if (profile.picture().isEmpty()) return new Fetched(profile.name(), null);
    return new Fetched(profile.name(), image(profile.picture(), transport, stop));
  }

  /** Follows at most four redirects itself, so every host and type check runs here. */
  static byte[] image(String address, Transport transport, AtomicBoolean stop)
      throws IOException {
    for (int redirects = 0; ; redirects++) {
      PhotosApi.checkStopped(stop);
      Reply reply = transport.call(address, "GET", Map.of(), null, stop);
      int code = reply.status();
      if (code == -1) throw new Failure("IMAGE_TOO_LARGE");
      if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
        String location = reply.location();
        if (location == null || !imageAddress(location)) throw new Failure("IMAGE_REDIRECT_HOST");
        if (redirects >= MAX_REDIRECTS) throw new Failure("IMAGE_REDIRECTS");
        address = location;
        continue;
      }
      if (code != 200) throw new Failure(status("IMAGE_HTTP_", code));
      String type = reply.contentType();
      if (type == null || !type.toLowerCase(Locale.ROOT).startsWith("image/"))
        throw new Failure("IMAGE_TYPE");
      return reply.body();
    }
  }

  static Profile parse(byte[] data, String expectedEmail) throws IOException {
    JSONObject json;
    try {
      json = new JSONObject(new String(data, StandardCharsets.UTF_8));
    } catch (JSONException e) {
      throw new Failure("USERINFO_BAD_JSON");
    }
    if (json.optString("sub").isBlank() || !Boolean.TRUE.equals(json.opt("email_verified")))
      throw new Failure("PROFILE_FIELDS_MISSING");
    if (!expectedEmail.equalsIgnoreCase(json.optString("email")))
      throw new Failure("PROFILE_EMAIL_MISMATCH");
    String name = json.optString("name", "");
    String picture = json.optString("picture", "");
    if (name.length() > 200) name = name.substring(0, 200);
    if (!picture.isEmpty() && !imageAddress(picture)) throw new Failure("PICTURE_HOST");
    return new Profile(name, picture);
  }

  static boolean imageAddress(String address) {
    if (address == null) return false;
    try {
      URI uri = new URI(address);
      return "https".equals(uri.getScheme())
          && uri.getUserInfo() == null
          && uri.getFragment() == null
          && (uri.getPort() == -1 || uri.getPort() == 443)
          && uri.getHost() != null
          && (uri.getHost().matches("lh[0-9]+\\.googleusercontent\\.com")
              || uri.getHost().matches("lh[0-9]+\\.ggpht\\.com"));
    } catch (URISyntaxException e) {
      return false;
    }
  }

  /** Does I/O only: one request per call, no redirects, never throws for an HTTP status. */
  static final class Network implements Transport {
    @Override
    public Reply call(
        String address,
        String method,
        Map<String, String> headers,
        byte[] body,
        AtomicBoolean stop)
        throws IOException {
      boolean auth = PhotosApi.AUTH.equals(address);
      boolean info = USERINFO.equals(address);
      boolean picture = !auth && !info && imageAddress(address);
      if (auth ? !"POST".equals(method) || body == null : !"GET".equals(method) || body != null)
        throw new IOException("Unrecognized profile request");
      if (!auth && !info && (!picture || !headers.isEmpty()))
        throw new IOException("Unrecognized profile request");
      int limit = auth ? AUTH_LIMIT : info ? USERINFO_LIMIT : IMAGE_LIMIT;
      PhotosApi.checkStopped(stop);
      HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
      try {
        c.setInstanceFollowRedirects(false);
        c.setUseCaches(false);
        c.setConnectTimeout(auth ? 30000 : 10000);
        c.setReadTimeout(auth ? 60000 : 15000);
        c.setRequestMethod(method);
        c.setRequestProperty("Accept-Encoding", "identity");
        headers.forEach(c::setRequestProperty);
        if (auth) {
          c.setDoOutput(true);
          c.setFixedLengthStreamingMode(body.length);
          try (OutputStream out = c.getOutputStream()) {
            out.write(body);
          }
        }
        PhotosApi.checkStopped(stop);
        int status = c.getResponseCode();
        String type = c.getContentType();
        String location = c.getHeaderField("Location");
        if (c.getContentLengthLong() > limit) return new Reply(-1, type, location, new byte[0]);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        InputStream stream = status < 400 ? c.getInputStream() : c.getErrorStream();
        if (stream != null) {
          try (InputStream in = stream) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
              PhotosApi.checkStopped(stop);
              if (bytes.size() + n > limit) return new Reply(-1, type, location, new byte[0]);
              bytes.write(buffer, 0, n);
            }
          }
        }
        return new Reply(status, type, location, bytes.toByteArray());
      } finally {
        c.disconnect();
      }
    }
  }
}
