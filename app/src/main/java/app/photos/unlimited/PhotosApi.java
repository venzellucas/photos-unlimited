package app.photos.unlimited;

// Protocol adapted from xob0t/gpmc and xob0t/gotohp, MIT; see bundled notice.
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PhotosApi {
  static final String AUTH = "https://android.googleapis.com/auth";
  static final String EXCHANGE = "https://android.clients.google.com/auth";
  static final String UPLOAD = "https://photos.googleapis.com/data/upload/uploadmedia/interactive";
  static final String RPC = "https://photosdata-pa.googleapis.com/6439526531001121323/";
  static final String PHOTOS_SIG = "24bb24c05e47e0aefa68a58a766179d9b613a600";
  static final String GMS_SIG = "38918a453d07199354f8b19af05ec6562ced5788";
  static final String UA =
      "com.google.android.apps.photos/49029607 (Linux; U; Android 9; en_US; Pixel XL;"
          + " Build/PQ2A.190205.001; Cronet/127.0.6510.5) (gzip)";

  public interface Source {
    InputStream open() throws IOException;
  }

  public interface Progress {
    void update(long bytes, long total);
  }

  public interface Body {
    void write(OutputStream output) throws IOException;
  }

  public interface Transport {
    Response call(
        String url,
        String method,
        Map<String, String> headers,
        Body body,
        long length,
        AtomicBoolean stop)
        throws IOException;
  }

  public record Response(byte[] bytes, Map<String, String> headers) {}

  public static final class ApiException extends IOException {
    public final boolean signIn, permanent;
    public final int httpStatus;

    public ApiException(String message, boolean signIn, boolean permanent) {
      this(message, signIn, permanent, 0);
    }

    public ApiException(String message, boolean signIn, boolean permanent, int httpStatus) {
      super(message);
      this.signIn = signIn;
      this.permanent = permanent;
      this.httpStatus = httpStatus;
    }
  }

  private final Map<String, String> credentials;
  private final Transport transport;
  private final AtomicBoolean stop;
  private String bearer = "";
  private long expiry;

  public PhotosApi(String credentials, AtomicBoolean stop) throws IOException {
    this(credentials, new Network(), stop);
  }

  public PhotosApi(String credentials, Transport transport, AtomicBoolean stop) throws IOException {
    this.credentials = parseForm(credentials);
    this.transport = transport;
    this.stop = stop;
    for (String key : List.of("Email", "Token", "androidId"))
      if (this.credentials.getOrDefault(key, "").isBlank())
        throw new IOException("Incomplete account credentials");
    if (this.credentials.containsKey("token_binding_alias"))
      throw new IOException("This credential needs device-bound keys. Use Google sign-in instead.");
  }

  public static String email(String credentials) throws IOException {
    return parseForm(credentials).getOrDefault("Email", "");
  }

  public static String connect(String input, AtomicBoolean stop) throws IOException {
    return connect(input, new Network(), stop);
  }

  static String connect(String input, Transport transport, AtomicBoolean stop) throws IOException {
    String token = input.trim();
    if (token.startsWith("oauth_token=")) token = token.substring(12);
    if (token.length() < 16
        || token.length() > 8192
        || token.contains("\n")
        || token.contains("\r"))
      throw new IOException("Enter the sign-in token from Google setup.");
    byte[] id = new byte[8];
    new SecureRandom().nextBytes(id);
    StringBuilder androidId = new StringBuilder();
    for (byte b : id) androidId.append(String.format(Locale.ROOT, "%02x", b & 255));
    Map<String, String> form = new LinkedHashMap<>();
    form.put("accountType", "HOSTED_OR_GOOGLE");
    form.put("Email", "oauth-token@example.com");
    form.put("has_permission", "1");
    form.put("add_account", "1");
    form.put("ACCESS_TOKEN", "1");
    form.put("Token", token);
    form.put("service", "ac2dm");
    form.put("source", "android");
    form.put("androidId", androidId.toString());
    form.put("device_country", "us");
    form.put("operatorCountry", "us");
    form.put("lang", "en");
    form.put("sdk_version", "17");
    form.put("google_play_services_version", "240913000");
    form.put("client_sig", GMS_SIG);
    form.put("callerSig", GMS_SIG);
    form.put("droidguard_results", "dummy123");
    byte[] data = form(form).getBytes(StandardCharsets.UTF_8);
    Response response =
        transport.call(
            EXCHANGE,
            "POST",
            Map.of(
                "Content-Type",
                "application/x-www-form-urlencoded",
                "User-Agent",
                "GoogleAuth/1.4"),
            o -> o.write(data),
            data.length,
            stop);
    Map<String, String> result = lines(new String(response.bytes, StandardCharsets.UTF_8));
    checkAuth(result);
    String mail = result.getOrDefault("Email", "");
    String master = result.getOrDefault("Token", "");
    if (!mail.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || master.isEmpty())
      throw new IOException("Google did not return usable account credentials.");
    form.clear();
    form.put("Email", mail);
    form.put("Token", master);
    form.put("androidId", androidId.toString());
    form.put("app", "com.google.android.apps.photos");
    form.put("callerPkg", "com.google.android.apps.photos");
    form.put("client_sig", PHOTOS_SIG);
    form.put("callerSig", PHOTOS_SIG);
    form.put(
        "service",
        "oauth2:openid https://www.googleapis.com/auth/mobileapps.native"
            + " https://www.googleapis.com/auth/photos.native");
    form.put("device_country", "us");
    form.put("operatorCountry", "us");
    form.put("lang", "en_US");
    form.put("sdk_version", "33");
    form.put("google_play_services_version", "240913000");
    form.put("oauth2_foreground", "1");
    form.put("source", "android");
    String credential = form(form);
    new PhotosApi(credential, transport, stop).validate();
    return credential;
  }

  public void validate() throws IOException {
    find(new byte[20]);
  }

  public LibraryProtocol.Page libraryPage(byte[] request) throws IOException {
    return LibraryProtocol.parse(
        post(
                RPC + "18047484249733410717",
                request,
                Map.of(
                    "x-goog-ext-173412678-bin", "CgcIAhClARgC",
                    "x-goog-ext-174067345-bin", "CgIIAg=="))
            .bytes);
  }

  public String find(byte[] hash) throws IOException {
    byte[] request =
        new Proto()
            .message(1, new Proto().message(1, new Proto().bytes(1, hash)).bytes(2, new byte[0]))
            .build();
    return Proto.path(post(RPC + "5084965799730810217", request, Map.of()).bytes, 1, 2, 2, 1);
  }

  public String upload(
      Source source, String name, long size, long timestamp, byte[] hash, Progress progress)
      throws IOException {
    if (size <= 0) throw new ApiException("File is empty.", false, true);
    byte[] request =
        new Proto().number(1, 2).number(2, 2).number(3, 1).number(4, 3).number(7, size).build();
    Response init =
        post(
            UPLOAD,
            request,
            Map.of(
                "X-Goog-Hash",
                "sha1=" + Base64.getEncoder().encodeToString(hash),
                "X-Upload-Content-Length",
                Long.toString(size)));
    String id = init.headers.getOrDefault("x-guploader-uploadid", "");
    if (id.isEmpty()) throw new IOException("Google did not create an upload session.");
    MessageDigest sentDigest = sha1();
    long[] sent = {0};
    Body body =
        out -> {
          try (InputStream in = source.open()) {
            byte[] buffer = new byte[128 * 1024];
            int count;
            while ((count = in.read(buffer)) != -1) {
              checkStopped(stop);
              out.write(buffer, 0, count);
              sentDigest.update(buffer, 0, count);
              sent[0] += count;
              progress.update(sent[0], size);
            }
          }
        };
    Response uploaded =
        transport.call(
            UPLOAD + "?upload_id=" + URLEncoder.encode(id, StandardCharsets.UTF_8),
            "PUT",
            headers(),
            body,
            -1,
            stop);
    if (sent[0] != size || !MessageDigest.isEqual(hash, sentDigest.digest()))
      throw new IOException("File changed during upload; it will be checked again.");
    // Receipt is an opaque protobuf envelope. Preserve every byte for commit.
    if (Proto.fields(uploaded.bytes, 2).isEmpty())
      throw new IOException("Google returned an invalid upload receipt.");
    byte[] commit = commitBody(uploaded.bytes, name, hash, timestamp);
    Response result =
        post(
            RPC + "16538846908252377752",
            commit,
            Map.of(
                "x-goog-ext-173412678-bin",
                "CgcIAhClARgC",
                "x-goog-ext-174067345-bin",
                "CgIIAg=="));
    String key = Proto.path(result.bytes, 1, 3, 1);
    if (key.isEmpty())
      throw new IOException(
          "Upload was not confirmed. The next attempt will check for duplicates.");
    return key;
  }

  static byte[] commitBody(byte[] receipt, String name, byte[] hash, long timestamp) {
    Proto item =
        new Proto()
            .bytes(1, receipt)
            .text(2, name)
            .bytes(3, hash)
            .message(4, new Proto().number(1, timestamp).number(2, 46000000))
            .number(7, 3)
            .number(10, 1);
    return new Proto()
        .message(1, item)
        .message(2, new Proto().text(3, "Pixel XL").text(4, "Google").number(5, 28))
        .bytes(3, new byte[] {1, 3})
        .build();
  }

  private Response post(String url, byte[] data, Map<String, String> extra) throws IOException {
    Map<String, String> headers = headers();
    headers.put("Content-Type", "application/x-protobuf");
    headers.putAll(extra);
    return transport.call(url, "POST", headers, o -> o.write(data), data.length, stop);
  }

  private Map<String, String> headers() throws IOException {
    checkStopped(stop);
    if (bearer.isEmpty() || expiry <= System.currentTimeMillis() / 1000 + 60) {
      Map<String, String> form = new LinkedHashMap<>(credentials);
      form.remove("it_caveat_types");
      form.remove("assertion_jwt");
      form.put("app", "com.google.android.apps.photos");
      form.put("callerPkg", "com.google.android.apps.photos");
      byte[] data = form(form).getBytes(StandardCharsets.UTF_8);
      Response response =
          transport.call(
              AUTH,
              "POST",
              Map.of(
                  "Content-Type",
                  "application/x-www-form-urlencoded",
                  "User-Agent",
                  "GoogleAuth/1.4 (Pixel XL PQ2A.190205.001)",
                  "device",
                  credentials.get("androidId"),
                  "app",
                  "com.google.android.apps.photos"),
              o -> o.write(data),
              data.length,
              stop);
      Map<String, String> parsed = lines(new String(response.bytes, StandardCharsets.UTF_8));
      checkAuth(parsed);
      bearer = parsed.getOrDefault("Auth", "");
      if (bearer.isEmpty())
        throw new ApiException("Google sign-in needs to be renewed.", true, false);
      try {
        expiry = Long.parseLong(parsed.getOrDefault("Expiry", "0"));
      } catch (NumberFormatException e) {
        expiry = 0;
      }
    }
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Authorization", "Bearer " + bearer);
    headers.put("User-Agent", UA);
    headers.put("Accept-Language", "en_US");
    return headers;
  }

  private static void checkAuth(Map<String, String> values) throws ApiException {
    if (values.containsKey("Error"))
      throw new ApiException(
          switch (values.get("Error")) {
            case "NeedsBrowser" -> "Google requires a new sign-in. Reconnect your account.";
            case "MissingDroidguard" ->
                "Google rejected device verification. This sign-in method is currently"
                    + " unavailable.";
            default -> "Google rejected this sign-in. Try a fresh Google setup token.";
          },
          true,
          false);
  }

  static Map<String, String> lines(String body) {
    Map<String, String> map = new LinkedHashMap<>();
    for (String line : body.split("\n")) {
      int p = line.indexOf('=');
      if (p > 0) map.put(line.substring(0, p).trim(), line.substring(p + 1).trim());
    }
    return map;
  }

  static Map<String, String> parseForm(String body) throws IOException {
    try {
      Map<String, String> map = new LinkedHashMap<>();
      for (String pair : body.split("&")) {
        int p = pair.indexOf('=');
        if (p > 0)
          map.put(
              URLDecoder.decode(pair.substring(0, p), StandardCharsets.UTF_8),
              URLDecoder.decode(pair.substring(p + 1), StandardCharsets.UTF_8));
      }
      return map;
    } catch (IllegalArgumentException e) {
      throw new IOException("Invalid account credential format");
    }
  }

  static String form(Map<String, String> values) {
    StringJoiner result = new StringJoiner("&");
    values.forEach(
        (k, v) ->
            result.add(
                URLEncoder.encode(k, StandardCharsets.UTF_8)
                    + "="
                    + URLEncoder.encode(v, StandardCharsets.UTF_8)));
    return result.toString();
  }

  static MessageDigest sha1() {
    try {
      return MessageDigest.getInstance("SHA-1");
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  static void checkStopped(AtomicBoolean stop) throws InterruptedIOException {
    if (stop.get() || Thread.currentThread().isInterrupted())
      throw new InterruptedIOException("Backup paused");
  }

  public static final class Network implements Transport {
    public Response call(
        String address,
        String method,
        Map<String, String> headers,
        Body body,
        long length,
        AtomicBoolean stop)
        throws IOException {
      checkStopped(stop);
      URL url = new URL(address);
      if (!url.getProtocol().equals("https")
          || !Set.of(
                  "android.googleapis.com",
                  "android.clients.google.com",
                  "photos.googleapis.com",
                  "photosdata-pa.googleapis.com")
              .contains(url.getHost())) throw new IOException("Unrecognized service address");
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      try {
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(60000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept-Encoding", "identity");
        headers.forEach(connection::setRequestProperty);
        connection.setDoOutput(true);
        if (length >= 0) connection.setFixedLengthStreamingMode(length);
        else connection.setChunkedStreamingMode(128 * 1024);
        try (OutputStream output = connection.getOutputStream()) {
          body.write(output);
        }
        checkStopped(stop);
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300)
          throw new ApiException(
              "Google returned HTTP "
                  + code
                  + (code == 401 || code == 403
                      ? ". Reconnect your account if this persists."
                      : ". Upload is not confirmed."),
              code == 401,
              code >= 400 && code < 500 && code != 401 && code != 403 && code != 408 && code != 429,
              code);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream in = connection.getInputStream()) {
          byte[] b = new byte[8192];
          int n;
          while ((n = in.read(b)) != -1) {
            checkStopped(stop);
            if (bytes.size() + n > 4 * 1024 * 1024)
              throw new IOException("Service response is too large");
            bytes.write(b, 0, n);
          }
        }
        Map<String, String> responseHeaders = new HashMap<>();
        connection
            .getHeaderFields()
            .forEach(
                (k, v) -> {
                  if (k != null && !v.isEmpty())
                    responseHeaders.put(k.toLowerCase(Locale.ROOT), v.get(0));
                });
        return new Response(bytes.toByteArray(), responseHeaders);
      } finally {
        connection.disconnect();
      }
    }
  }
}
