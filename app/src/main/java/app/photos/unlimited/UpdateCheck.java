package app.photos.unlimited;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Asks GitHub once a day, while the app is open, for the newest release's version. Only {@code
 * tag_name}, {@code draft} and {@code prerelease} are read; nothing from the answer is shown or
 * opened. Every failure is silent.
 */
final class UpdateCheck {
  static final String REPO = "venzellucas/photos-unlimited";
  static final String API = "https://api.github.com/repos/" + REPO + "/releases/latest";
  static final String USER_AGENT = "PhotosUnlimited-UpdateCheck";
  static final int MAX_BYTES = 1 << 20;
  static final long DAY = 24 * 60 * 60 * 1000L;

  private static final Pattern NUMBERS = Pattern.compile("(\\d{1,4})\\.(\\d{1,4})\\.(\\d{1,4})");

  interface Transport {
    Response get(String url, Map<String, String> headers) throws IOException;
  }

  record Response(int status, String etag, byte[] body) {}

  enum Kind {
    LATEST,
    UNCHANGED,
    FAILED
  }

  record Result(Kind kind, String version, String etag) {}

  record Version(int major, int minor, int patch) implements Comparable<Version> {
    /** {@code X.Y.Z} only, each part 1 to 4 digits; anything else (a suffix, spaces) is null. */
    static Version parse(String name) {
      if (name == null) return null;
      Matcher m = NUMBERS.matcher(name);
      if (!m.matches()) return null;
      return new Version(
          Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
    }

    @Override
    public int compareTo(Version o) {
      if (major != o.major) return Integer.compare(major, o.major);
      if (minor != o.minor) return Integer.compare(minor, o.minor);
      return Integer.compare(patch, o.patch);
    }

    @Override
    public String toString() {
      return major + "." + minor + "." + patch;
    }
  }

  private UpdateCheck() {}

  /** Exactly these headers: no app version, device model or Android version. */
  static Map<String, String> headers(String etag) {
    Map<String, String> h = new LinkedHashMap<>();
    h.put("Accept", "application/vnd.github+json");
    h.put("X-GitHub-Api-Version", "2022-11-28");
    h.put("User-Agent", USER_AGENT);
    if (etag != null && !etag.isEmpty()) h.put("If-None-Match", etag);
    return h;
  }

  static Result check(Transport t, String etag) {
    try {
      Response r = t.get(API, headers(etag));
      if (r.status() == 304) return new Result(Kind.UNCHANGED, null, null);
      if (r.status() != 200) return new Result(Kind.FAILED, null, null);
      Version v = parseRelease(r.body());
      return v == null
          ? new Result(Kind.FAILED, null, null)
          : new Result(Kind.LATEST, v.toString(), r.etag());
    } catch (IOException | RuntimeException e) {
      return new Result(Kind.FAILED, null, null);
    }
  }

  /** {@code vX.Y.Z} only; leading zeros are allowed. */
  static Version parseTag(String tag) {
    if (tag == null || !tag.startsWith("v")) return null;
    return Version.parse(tag.substring(1));
  }

  static Version parseRelease(byte[] json) {
    if (json == null || json.length > MAX_BYTES) return null;
    try {
      JSONObject release = new JSONObject(new String(json, StandardCharsets.UTF_8));
      Object tag = release.opt("tag_name");
      if (!(tag instanceof String)) return null;
      if (release.optBoolean("draft", false) || release.optBoolean("prerelease", false))
        return null;
      return parseTag((String) tag);
    } catch (JSONException e) {
      return null;
    }
  }

  /** A day since the last attempt, or the clock moved backwards. */
  static boolean due(long now, long lastAttempt) {
    return now - lastAttempt >= DAY || now < lastAttempt;
  }

  /** Talks only to {@link #API}: no redirects, no caches, no cookies, at most 1 MiB. */
  static final class Network implements Transport {
    @Override
    public Response get(String url, Map<String, String> headers) throws IOException {
      if (!API.equals(url)) throw new IOException("Unexpected update address");
      HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
      try {
        c.setInstanceFollowRedirects(false);
        c.setUseCaches(false);
        c.setConnectTimeout(10_000);
        c.setReadTimeout(15_000);
        for (Map.Entry<String, String> h : headers.entrySet())
          c.setRequestProperty(h.getKey(), h.getValue());
        int status = c.getResponseCode();
        if (status != 200) return new Response(status, null, null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        try (InputStream in = c.getInputStream()) {
          for (int n; (n = in.read(buffer)) != -1; ) {
            out.write(buffer, 0, n);
            if (out.size() >= MAX_BYTES) throw new IOException("Update answer is too large");
          }
        }
        return new Response(status, c.getHeaderField("ETag"), out.toByteArray());
      } finally {
        c.disconnect();
      }
    }
  }

  /** Runs on a background thread from {@code onResume}; {@code onDone} follows a real check. */
  static void runIfDue(Context c, Settings s, Runnable onDone) {
    try {
      long now = System.currentTimeMillis();
      if (!s.updateCheck()
          || (c.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0
          || !due(now, s.updateLastAttempt())) return;
      s.updateLastAttempt(now); // before the request: a crash or bad network can't repeat it
      Result r = check(new Network(), s.updateEtag());
      if (r.kind() == Kind.LATEST) s.storeUpdate(r.version(), r.etag() == null ? "" : r.etag());
    } catch (RuntimeException ignored) {
      // Silent by design.
    }
    try {
      onDone.run();
    } catch (RuntimeException ignored) {
      // The next draw shows the stored state.
    }
  }
}
