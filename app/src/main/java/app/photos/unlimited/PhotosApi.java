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

    /** Streams a GET response into the sink; only media download hosts are allowed. */
    default void stream(
        String url, Map<String, String> headers, OutputStream sink, AtomicBoolean stop)
        throws IOException {
      throw new IOException("Downloads are not supported by this transport");
    }
  }

  /** A URL found in the download response, with the protobuf path where it appeared. */
  public record Link(String path, String url) {}

  public record Response(byte[] bytes, Map<String, String> headers) {}

  public static final class ApiException extends UserFacingException {
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
        throw new ApiException(Text.get(R.string.api_incomplete_credentials), true, false);
    if (this.credentials.containsKey("token_binding_alias"))
      throw new ApiException(Text.get(R.string.api_device_bound), true, false);
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
      throw new UserFacingException(Text.get(R.string.api_enter_token));
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
      throw new UserFacingException(Text.get(R.string.api_no_credentials));
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

  static final String MUTATE = RPC + "17490284929287180316";
  static final String DOWNLOAD =
      "https://photosdata-pa.googleapis.com/$rpc/social.frontend.photos.preparedownloaddata.v1"
          + ".PhotosPrepareDownloadDataService/PhotosPrepareDownload";
  static final Map<String, String> EXT =
      Map.of(
          "x-goog-ext-173412678-bin", "CgcIAhClARgC",
          "x-goog-ext-174067345-bin", "CgIIAg==");

  private static Proto clientInfo() {
    return new Proto()
        .number(1, 5)
        .message(2, new Proto().number(1, 49029607).text(2, "28"));
  }

  private static Proto mutationMask(boolean full) {
    Proto mask = new Proto().message(2, new Proto()).message(3, new Proto().message(1, new Proto()));
    if (full) mask.message(4, new Proto()).message(5, new Proto().message(1, new Proto()));
    return new Proto().message(4, mask);
  }

  /** action 1 = move to trash, 2 = delete permanently, 3 = restore from trash. */
  static byte[] mutationBody(int action, List<String> dedupKeys) {
    if (dedupKeys.isEmpty()) throw new IllegalArgumentException("No items");
    Proto body = new Proto().number(2, action);
    for (String key : dedupKeys) body.text(3, key);
    body.number(4, action == 1 ? 1 : 2);
    body.message(8, mutationMask(action != 3));
    if (action == 2) body.text(9, "");
    else body.message(9, clientInfo());
    return body.build();
  }

  static byte[] downloadBody(String mediaKey) {
    return new Proto()
        .message(1, new Proto().message(1, new Proto().text(1, mediaKey)))
        .message(
            2,
            new Proto()
                .message(1, new Proto().message(7, new Proto().message(2, new Proto())))
                .message(
                    5,
                    new Proto()
                        .message(2, new Proto())
                        .message(3, new Proto())
                        .message(5, new Proto().message(1, new Proto()).number(3, 0))))
        .build();
  }

  static byte[] captionBody(String dedupKey, String caption) {
    return new Proto().text(2, caption).text(3, dedupKey).build();
  }

  static byte[] favoriteBody(String dedupKey, boolean favorite) {
    return new Proto()
        .message(1, new Proto().text(2, dedupKey))
        .message(2, new Proto().number(1, favorite ? 1 : 2))
        .message(3, new Proto().message(1, new Proto().message(19, new Proto())))
        .build();
  }

  static byte[] archiveBody(List<String> dedupKeys, boolean archived) {
    Proto body = new Proto();
    for (String key : dedupKeys)
      body.message(
          1, new Proto().text(1, key).message(2, new Proto().number(1, archived ? 1 : 2)));
    return body.number(3, 1).build();
  }

  public byte[] moveToTrash(List<String> dedupKeys) throws IOException {
    return post(MUTATE, mutationBody(1, dedupKeys), Map.of()).bytes;
  }

  public byte[] deletePermanently(List<String> dedupKeys) throws IOException {
    return post(MUTATE, mutationBody(2, dedupKeys), Map.of()).bytes;
  }

  public byte[] restoreFromTrash(List<String> dedupKeys) throws IOException {
    return post(MUTATE, mutationBody(3, dedupKeys), EXT).bytes;
  }

  public void setCaption(String dedupKey, String caption) throws IOException {
    post(RPC + "1552790390512470739", captionBody(dedupKey, caption), EXT);
  }

  public void setFavorite(String dedupKey, boolean favorite) throws IOException {
    post(RPC + "5144645502632292153", favoriteBody(dedupKey, favorite), EXT);
  }

  public void setArchived(List<String> dedupKeys, boolean archived) throws IOException {
    post(RPC + "6715446385130606868", archiveBody(dedupKeys, archived), EXT);
  }

  /** Raw PrepareDownload response; see {@link #links(byte[])}. */
  public byte[] prepareDownload(String mediaKey) throws IOException {
    return post(DOWNLOAD, downloadBody(mediaKey), EXT).bytes;
  }

  /**
   * Every https URL in a response, ordered so the documented original-photo field comes first,
   * then video fields, then everything else. The caller proves which one is the original by its
   * SHA-1; order only saves bandwidth.
   */
  public static List<Link> links(byte[] response) {
    List<Link> found = new ArrayList<>();
    collect(response, "", 0, found);
    List<Link> ordered = new ArrayList<>();
    for (String prefix : List.of("1.5.2.6", "1.5.3.", "1.5."))
      for (Link link : found)
        if ((link.path().equals(prefix) || link.path().startsWith(prefix))
            && !ordered.contains(link)
            && !link.path().equals("1.5.2.5")) ordered.add(link);
    for (Link link : found)
      if (!ordered.contains(link) && !link.path().equals("1.5.2.5")) ordered.add(link);
    for (Link link : found) if (!ordered.contains(link)) ordered.add(link);
    return ordered;
  }

  private static void collect(byte[] data, String path, int depth, List<Link> out) {
    if (depth > 10) return;
    List<Proto.Value> values;
    try {
      values = Proto.values(data);
    } catch (IOException e) {
      return;
    }
    for (Proto.Value v : values) {
      if (v.wire() != 2) continue;
      String here = path.isEmpty() ? Integer.toString(v.field()) : path + "." + v.field();
      String text = asciiUrl(v.bytes());
      if (text != null) out.add(new Link(here, text));
      else collect(v.bytes(), here, depth + 1, out);
    }
  }

  private static String asciiUrl(byte[] bytes) {
    if (bytes.length < 12 || bytes.length > 8192) return null;
    for (byte b : bytes) if (b < 0x21 || b > 0x7e) return null;
    String text = new String(bytes, StandardCharsets.US_ASCII);
    return text.startsWith("https://") ? text : null;
  }

  /** Hosts Google's download response points at (observed 2026-11-19); the token goes only here. */
  static boolean firstDownloadHost(String host) {
    return host != null
        && (host.matches("lh[0-9]{1,2}\\.googleusercontent\\.com")
            || host.equals("video-downloads.googleusercontent.com"));
  }

  static boolean downloadHost(String host) {
    return host != null
        && (host.equals("googleusercontent.com") || host.endsWith(".googleusercontent.com"));
  }

  /**
   * Streams one URL into {@code target}, returning its SHA-1. The caller compares the digest with
   * the library's recorded original hash; nothing here decides that a file is the original.
   */
  public byte[] download(String url, File target, long limit, Progress progress)
      throws IOException {
    URL parsed = new URL(url);
    if (!parsed.getProtocol().equals("https") || !firstDownloadHost(parsed.getHost()))
      throw new ApiException(
          Text.get(R.string.api_bad_host, parsed.getHost()), false, true);
    MessageDigest digest = sha1();
    long[] count = {0};
    Map<String, String> headers = headers();
    headers.remove("Content-Type");
    try (OutputStream file = new FileOutputStream(target)) {
      OutputStream sink =
          new OutputStream() {
            @Override
            public void write(int b) throws IOException {
              write(new byte[] {(byte) b}, 0, 1);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
              checkStopped(stop);
              count[0] += len;
              if (count[0] > limit) throw new UserFacingException(Text.get(R.string.api_download_too_large));
              digest.update(b, off, len);
              file.write(b, off, len);
              progress.update(count[0], limit);
            }
          };
      transport.stream(url, headers, sink, stop);
      file.flush();
      if (file instanceof FileOutputStream f) f.getFD().sync();
    }
    return digest.digest();
  }

  /** Unparsed library response, for diagnostics only. */
  public byte[] libraryPageRaw(byte[] request) throws IOException {
    return post(RPC + "18047484249733410717", request, EXT).bytes;
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
    if (size <= 0) throw new ApiException(Text.get(R.string.api_file_empty), false, true);
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
    if (id.isEmpty()) throw new UserFacingException(Text.get(R.string.api_no_session));
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
      throw new UserFacingException(Text.get(R.string.api_file_changed));
    // Receipt is an opaque protobuf envelope. Preserve every byte for commit.
    if (Proto.fields(uploaded.bytes, 2).isEmpty())
      throw new UserFacingException(Text.get(R.string.api_bad_receipt));
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
      throw new UserFacingException(Text.get(R.string.api_unconfirmed));
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
        throw new ApiException(Text.get(R.string.api_renew_sign_in), true, false);
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
            case "NeedsBrowser" -> Text.get(R.string.api_needs_browser);
            case "MissingDroidguard" -> Text.get(R.string.api_droidguard);
            default -> Text.get(R.string.api_rejected);
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
    @Override
    public void stream(
        String address, Map<String, String> headers, OutputStream sink, AtomicBoolean stop)
        throws IOException {
      String current = address;
      for (int hop = 0; hop < 6; hop++) {
        checkStopped(stop);
        URL url = new URL(current);
        if (!url.getProtocol().equals("https") || !downloadHost(url.getHost()))
          throw new UserFacingException(Text.get(R.string.api_download_bad_redirect));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
          connection.setInstanceFollowRedirects(false);
          connection.setConnectTimeout(30000);
          connection.setReadTimeout(60000);
          connection.setRequestProperty("Accept-Encoding", "identity");
          headers.forEach(connection::setRequestProperty);
          int code = connection.getResponseCode();
          if (code >= 300 && code < 400) {
            String location = connection.getHeaderField("Location");
            if (location == null) throw new UserFacingException(Text.get(R.string.api_download_bad_redirect));
            URL next = new URL(url, location);
            if (!next.getHost().equals(url.getHost())) {
              // The account token never follows a redirect to another host.
              headers = new java.util.HashMap<>(headers);
              headers.remove("Authorization");
            }
            current = next.toString();
            continue;
          }
          if (code < 200 || code >= 300)
            throw new ApiException(
                Text.get(R.string.api_download_http, code),
                code == 401,
                code == 404 || code == 410,
                code);
          try (InputStream in = connection.getInputStream()) {
            byte[] b = new byte[128 * 1024];
            int n;
            while ((n = in.read(b)) != -1) {
              checkStopped(stop);
              sink.write(b, 0, n);
            }
          }
          return;
        } finally {
          connection.disconnect();
        }
      }
      throw new UserFacingException(Text.get(R.string.api_download_bad_redirect));
    }

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
              Text.get(
                  code == 401 || code == 403
                      ? R.string.api_upload_http_auth
                      : R.string.api_upload_http,
                  code),
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
