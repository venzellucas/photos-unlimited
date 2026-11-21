package app.photos.unlimited;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Map;
import org.junit.Test;

/** The update check's decisions; JSON parsing needs the real org.json and runs on the emulator. */
public class UpdateLogicTest {
  static final long H = 60 * 60 * 1000L;

  static UpdateCheck.Version v(int a, int b, int c) {
    return new UpdateCheck.Version(a, b, c);
  }

  @Test
  public void parseTagAcceptsOnlyVxyz() {
    assertEquals(v(1, 2, 3), UpdateCheck.parseTag("v1.2.3"));
    assertEquals(v(1, 2, 3), UpdateCheck.parseTag("v01.2.3"));
    for (String bad : new String[] {"v1.2", "1.2.3", "v1.2.3-rc1", " v1.2.3", "v10000.0.0", null})
      assertNull(bad, UpdateCheck.parseTag(bad));
  }

  @Test
  public void versionNames() {
    assertEquals(v(1, 1, 0), UpdateCheck.Version.parse("1.1.0"));
    assertEquals("1.1.0", UpdateCheck.Version.parse("1.1.0").toString());
    assertNull(UpdateCheck.Version.parse("1.1.0-rc1"));
  }

  @Test
  public void ordering() {
    assertTrue(v(1, 0, 10).compareTo(v(1, 0, 9)) > 0);
    assertTrue(v(2, 0, 0).compareTo(v(1, 99, 99)) > 0);
    assertEquals(0, v(1, 1, 0).compareTo(v(1, 1, 0)));
  }

  @Test
  public void dueOncePerDayOrWhenTheClockMovesBack() {
    long now = 1_800_000_000_000L;
    assertFalse(UpdateCheck.due(now, now - (24 * H - 60_000)));
    assertTrue(UpdateCheck.due(now, now - 24 * H));
    assertTrue(UpdateCheck.due(now, now + 1));
    assertTrue(UpdateCheck.due(now, 0));
  }

  static UpdateNotice.State state(
      boolean enabled, String installed, String latest, String dismissed, int unsafe, boolean busy) {
    return UpdateNotice.state(enabled, installed, latest, dismissed, unsafe, busy);
  }

  @Test
  public void noticeStates() {
    UpdateNotice.State hidden = UpdateNotice.State.HIDDEN;
    assertEquals(hidden, state(false, "1.1.0", "1.2.0", "", 0, false));
    assertEquals(hidden, state(true, "1.1.0", "1.2.0", "", -1, false));
    assertEquals(hidden, state(true, "1.1.0", "", "", 0, false));
    assertEquals(hidden, state(true, "1.1.0", "1.1.0", "", 0, false));
    assertEquals(hidden, state(true, "1.1.0", "1.0.9", "", 0, false));
    assertEquals(hidden, state(true, "1.1.0", "1.2.0", "1.2.0", 0, false));
    assertEquals(UpdateNotice.State.AVAILABLE, state(true, "1.1.0", "1.2.0", "1.1.5", 0, false));
    assertEquals(hidden, state(true, "1.1.0-rc1", "1.2.0", "", 0, false));
    assertEquals(UpdateNotice.State.BLOCKED, state(true, "1.1.0", "1.2.0", "", 3, false));
    assertEquals(UpdateNotice.State.BLOCKED, state(true, "1.1.0", "1.2.0", "", 0, true));
    assertEquals(UpdateNotice.State.AVAILABLE, state(true, "1.1.0", "1.2.0", "", 0, false));
  }

  @Test
  public void urlsAreBuiltFromConstants() {
    assertEquals(
        "https://github.com/venzellucas/photos-unlimited/releases/latest/download/Photos-Unlimited.apk",
        UpdateNotice.downloadUrl());
    assertEquals(
        "https://github.com/venzellucas/photos-unlimited/releases/tag/v1.2.3",
        UpdateNotice.releaseUrl(v(1, 2, 3)));
    assertEquals(
        "https://api.github.com/repos/venzellucas/photos-unlimited/releases/latest",
        UpdateCheck.API);
  }

  static UpdateCheck.Transport answer(int status) {
    return (url, headers) -> new UpdateCheck.Response(status, null, null);
  }

  @Test
  public void checkWithoutJson() {
    assertEquals(UpdateCheck.Kind.UNCHANGED, UpdateCheck.check(answer(304), "W/\"abc\"").kind());
    for (int status : new int[] {403, 429, 500})
      assertEquals(UpdateCheck.Kind.FAILED, UpdateCheck.check(answer(status), "").kind());
    UpdateCheck.Transport offline =
        (url, headers) -> {
          throw new IOException("offline");
        };
    assertEquals(UpdateCheck.Kind.FAILED, UpdateCheck.check(offline, "").kind());
  }

  @Test
  public void headersAreFixed() {
    Map<String, String> plain = UpdateCheck.headers("");
    assertEquals(3, plain.size());
    assertEquals("application/vnd.github+json", plain.get("Accept"));
    assertEquals("2022-11-28", plain.get("X-GitHub-Api-Version"));
    assertEquals("PhotosUnlimited-UpdateCheck", plain.get("User-Agent"));
    Map<String, String> cached = UpdateCheck.headers("W/\"abc\"");
    assertEquals(4, cached.size());
    assertEquals("W/\"abc\"", cached.get("If-None-Match"));
  }

  @Test
  public void checkAsksOnlyTheApiAddress() {
    String[] asked = {null};
    UpdateCheck.check(
        (url, headers) -> {
          asked[0] = url;
          return new UpdateCheck.Response(304, null, null);
        },
        "");
    assertEquals(UpdateCheck.API, asked[0]);
  }
}
