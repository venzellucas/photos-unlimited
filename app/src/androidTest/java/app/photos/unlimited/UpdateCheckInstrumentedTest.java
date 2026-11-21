package app.photos.unlimited;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Parsing with the real org.json, against a trimmed copy of GitHub's v1.0.0 answer. */
@RunWith(AndroidJUnit4.class)
public class UpdateCheckInstrumentedTest {
  private String fixture;

  @Before
  public void load() throws Exception {
    try (InputStream in =
        InstrumentationRegistry.getInstrumentation()
            .getContext()
            .getAssets()
            .open("releases_latest.json")) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      in.transferTo(out);
      fixture = out.toString(StandardCharsets.UTF_8.name());
    }
  }

  private static UpdateCheck.Version parse(String json) {
    return UpdateCheck.parseRelease(json.getBytes(StandardCharsets.UTF_8));
  }

  private String with(String key, Object value) throws Exception {
    JSONObject o = new JSONObject(fixture);
    if (value == null) o.remove(key);
    else o.put(key, value);
    return o.toString();
  }

  @Test
  public void parsesOnlyPublishedReleases() throws Exception {
    UpdateCheck.Version v100 = new UpdateCheck.Version(1, 0, 0);
    assertEquals(v100, parse(fixture));
    assertNull(parse(with("draft", true)));
    assertNull(parse(with("prerelease", true)));
    JSONObject bare = new JSONObject(fixture);
    bare.remove("draft");
    bare.remove("prerelease");
    assertEquals(v100, parse(bare.toString()));
    assertNull(parse(with("tag_name", null)));
    assertNull(parse(with("tag_name", 110)));
    assertNull(parse("{"));
    byte[] big = new byte[UpdateCheck.MAX_BYTES + 1];
    java.util.Arrays.fill(big, (byte) ' ');
    assertNull(UpdateCheck.parseRelease(big));
  }

  @Test
  public void aFreshAnswerGivesTheVersionAndEtag() {
    UpdateCheck.Result r =
        UpdateCheck.check(
            (url, headers) ->
                new UpdateCheck.Response(
                    200, "W/\"fake\"", fixture.getBytes(StandardCharsets.UTF_8)),
            "");
    assertEquals(UpdateCheck.Kind.LATEST, r.kind());
    assertEquals("1.0.0", r.version());
    assertEquals("W/\"fake\"", r.etag());
  }
}
