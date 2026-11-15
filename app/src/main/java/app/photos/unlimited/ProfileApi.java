package app.photos.unlimited;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** Optional profile lookup with its own token; never changes the upload credential or scopes. */
final class ProfileApi {
  static final String USERINFO = "https://openidconnect.googleapis.com/v1/userinfo";
  static final String SCOPES =
      "oauth2:openid https://www.googleapis.com/auth/userinfo.email"
          + " https://www.googleapis.com/auth/userinfo.profile";

  record Profile(String name, String picture) {}

  static Profile fetch(String credential, PhotosApi.Transport transport, AtomicBoolean stop)
      throws IOException {
    Map<String, String> form = PhotosApi.parseForm(credential);
    String email = form.getOrDefault("Email", "");
    if (email.isBlank()
        || form.getOrDefault("Token", "").isBlank()
        || form.getOrDefault("androidId", "").isBlank())
      throw new IOException("Account is unavailable");
    form.remove("it_caveat_types");
    form.remove("assertion_jwt");
    form.put("service", SCOPES);
    form.put("app", "com.google.android.apps.photos");
    form.put("callerPkg", "com.google.android.apps.photos");
    byte[] data = PhotosApi.form(form).getBytes(StandardCharsets.UTF_8);
    PhotosApi.Response auth =
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
                "com.google.android.apps.photos"),
            out -> out.write(data),
            data.length,
            stop);
    Map<String, String> result = PhotosApi.lines(new String(auth.bytes(), StandardCharsets.UTF_8));
    String token = result.getOrDefault("Auth", "");
    if (result.containsKey("Error") || token.isBlank())
      throw new IOException("Profile access unavailable");
    PhotosApi.Response response =
        transport.call(USERINFO, "GET", Map.of("Authorization", "Bearer " + token), null, 0, stop);
    return parse(response.bytes(), email);
  }

  static Profile parse(byte[] data, String expectedEmail) throws IOException {
    try {
      JSONObject json = new JSONObject(new String(data, StandardCharsets.UTF_8));
      if (json.optString("sub").isBlank()
          || !Boolean.TRUE.equals(json.opt("email_verified"))
          || !expectedEmail.equalsIgnoreCase(json.optString("email")))
        throw new IOException("Profile does not match the connected account");
      String name = json.optString("name", "");
      String picture = json.optString("picture", "");
      if (name.length() > 200) name = name.substring(0, 200);
      if (!picture.isEmpty() && !imageAddress(picture))
        throw new IOException("Unrecognized profile picture address");
      return new Profile(name, picture);
    } catch (JSONException e) {
      throw new IOException("Profile information is unavailable");
    }
  }

  static boolean imageAddress(String address) {
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

  static final class Network implements PhotosApi.Transport {
    @Override
    public PhotosApi.Response call(
        String address,
        String method,
        Map<String, String> headers,
        PhotosApi.Body body,
        long length,
        AtomicBoolean stop)
        throws IOException {
      if (PhotosApi.AUTH.equals(address))
        return new PhotosApi.Network().call(address, method, headers, body, length, stop);
      boolean profile = USERINFO.equals(address);
      if (!"GET".equals(method)
          || body != null
          || (!profile && !imageAddress(address))
          || (!profile && !headers.isEmpty()))
        throw new IOException("Unrecognized profile request");
      for (int redirects = 0; redirects < 4; redirects++) {
        PhotosApi.checkStopped(stop);
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        try {
          c.setInstanceFollowRedirects(false);
          c.setConnectTimeout(10000);
          c.setReadTimeout(15000);
          c.setRequestProperty("Accept-Encoding", "identity");
          headers.forEach(c::setRequestProperty);
          int status = c.getResponseCode();
          if (!profile
              && (status == 301
                  || status == 302
                  || status == 303
                  || status == 307
                  || status == 308)) {
            String location = c.getHeaderField("Location");
            if (location == null || !imageAddress(location))
              throw new IOException("Unrecognized image redirect");
            address = location;
            continue;
          }
          if (status != 200) throw new IOException("Profile is unavailable");
          String type = c.getContentType();
          if (!profile && (type == null || !type.toLowerCase(Locale.ROOT).startsWith("image/")))
            throw new IOException("Invalid profile image");
          int limit = profile ? 65536 : 2 * 1024 * 1024;
          if (c.getContentLengthLong() > limit) throw new IOException("Profile response too large");
          ByteArrayOutputStream bytes = new ByteArrayOutputStream();
          try (InputStream in = c.getInputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
              PhotosApi.checkStopped(stop);
              if (bytes.size() + n > limit) throw new IOException("Profile response too large");
              bytes.write(buffer, 0, n);
            }
          }
          return new PhotosApi.Response(bytes.toByteArray(), Map.of());
        } finally {
          c.disconnect();
        }
      }
      throw new IOException("Too many profile redirects");
    }
  }
}
