package app.photos.unlimited;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.*;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ProfileInstrumentedTest {
  Context context;
  static final String ACCOUNT = "avatar-fixture@example.com";
  static final String CREDENTIAL =
      "Email=avatar-fixture%40example.com&Token=synthetic-only&androidId=1234&service=original-upload-scope";

  static byte[] json(String body) {
    return body.getBytes(StandardCharsets.UTF_8);
  }

  static String valid() {
    return "{\"sub\":\"fixture\",\"email\":\""
        + ACCOUNT
        + "\",\"email_verified\":true,\"name\":\"Avatar"
        + " Fixture\",\"picture\":\"https://lh3.googleusercontent.com/a/fixture\"}";
  }

  @Before
  public void setup() {
    context = ApplicationProvider.getApplicationContext();
    new AccountStore(context).clear();
  }

  @After
  public void cleanup() {
    new AccountStore(context).clear();
  }

  static ProfileApi.Reply ok(String type, byte[] body) {
    return new ProfileApi.Reply(200, type, null, body);
  }

  static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

  @Test
  public void profileTokenUsesSeparateScopesAndVerifiedAccount() throws Exception {
    List<String> calls = new ArrayList<>();
    ProfileApi.Transport transport =
        (url, method, headers, body, stop) -> {
          calls.add(url);
          if (url.equals(PhotosApi.AUTH)) {
            var form = PhotosApi.parseForm(new String(body, StandardCharsets.UTF_8));
            assertEquals(ProfileApi.SCOPES, form.get("service"));
            assertEquals("synthetic-only", form.get("Token"));
            assertEquals(ProfileApi.GMS, form.get("app"));
            assertEquals(ProfileApi.GMS, form.get("callerPkg"));
            assertEquals(PhotosApi.GMS_SIG, form.get("client_sig"));
            assertEquals(ProfileApi.GMS, headers.get("app"));
            return ok("text/plain", json("Auth=synthetic-profile-bearer\n"));
          }
          if (url.equals(ProfileApi.USERINFO)) {
            assertEquals("GET", method);
            assertNull(body);
            assertEquals("Bearer synthetic-profile-bearer", headers.get("Authorization"));
            return ok("application/json", json(valid()));
          }
          assertEquals("https://lh3.googleusercontent.com/a/fixture", url);
          assertTrue(headers.isEmpty());
          return ok("image/png", PNG);
        };
    new AccountStore(context).save(CREDENTIAL);
    var profile = ProfileApi.fetch(CREDENTIAL, transport, new AtomicBoolean());
    assertEquals("Avatar Fixture", profile.name());
    assertArrayEquals(PNG, profile.image());
    assertEquals(3, calls.size());
    assertEquals(CREDENTIAL, new AccountStore(context).load());
    assertEquals("original-upload-scope", PhotosApi.parseForm(CREDENTIAL).get("service"));
  }

  /** Answers the token, userinfo and image steps from the given replies, in order. */
  static String failure(ProfileApi.Reply auth, ProfileApi.Reply info, ProfileApi.Reply... image)
      throws Exception {
    int[] next = {0};
    ProfileApi.Transport transport =
        (url, method, headers, body, stop) -> {
          if (url.equals(PhotosApi.AUTH)) return auth;
          if (url.equals(ProfileApi.USERINFO)) return info;
          return image[Math.min(next[0]++, image.length - 1)];
        };
    try {
      var fetched = ProfileApi.fetch(CREDENTIAL, transport, new AtomicBoolean());
      if (fetched.image() != null) ProfileStore.decode(fetched.image());
      fail("Expected a profile failure");
      return null;
    } catch (ProfileApi.Failure f) {
      assertTrue(f.label().matches("[A-Z0-9_]+"));
      assertFalse(f.label().contains("@") || f.label().contains("/") || f.label().contains("="));
      return f.label();
    }
  }

  @Test
  public void everyProfileFailureNamesItsStep() throws Exception {
    var token = ok("text/plain", json("Auth=t\n"));
    var info = ok("application/json", json(valid()));
    var image = ok("image/png", PNG);
    assertEquals(
        "AUTH_ERROR_BADAUTHENTICATION",
        failure(new ProfileApi.Reply(403, "text/plain", null, json("Error=BadAuthentication\n")), info));
    assertEquals(
        "AUTH_HTTP_403", failure(new ProfileApi.Reply(403, "text/plain", null, new byte[0]), info));
    assertEquals("AUTH_NO_TOKEN", failure(ok("text/plain", json("Foo=bar\n")), info));
    assertEquals(
        "AUTH_TOO_LARGE", failure(new ProfileApi.Reply(-1, null, null, new byte[0]), info));
    assertEquals(
        "USERINFO_HTTP_401", failure(token, new ProfileApi.Reply(401, null, null, new byte[0])));
    assertEquals(
        "PROFILE_FIELDS_MISSING",
        failure(token, ok("application/json", json(valid().replace("\"email_verified\":true,", "")))));
    assertEquals(
        "PROFILE_EMAIL_MISMATCH",
        failure(token, ok("application/json", json(valid().replace(ACCOUNT, "other@example.com")))));
    assertEquals(
        "PICTURE_HOST",
        failure(
            token,
            ok(
                "application/json",
                json(valid().replace("https://lh3.googleusercontent.com/a/fixture", "https://example.com/a")))));
    assertEquals(
        "IMAGE_REDIRECT_HOST",
        failure(token, info, new ProfileApi.Reply(302, null, "https://example.com/a.png", new byte[0])));
    assertEquals(
        "IMAGE_REDIRECT_HOST", failure(token, info, new ProfileApi.Reply(302, null, null, new byte[0])));
    assertEquals(
        "IMAGE_REDIRECTS",
        failure(
            token,
            info,
            new ProfileApi.Reply(302, null, "https://lh4.googleusercontent.com/a", new byte[0])));
    assertEquals("IMAGE_TYPE", failure(token, info, ok("text/html", PNG)));
    assertEquals("IMAGE_DECODE", failure(token, info, ok("image/png", json("abc"))));
    assertEquals("NO_CREDENTIAL", noCredential());
  }

  static String noCredential() throws Exception {
    try {
      ProfileApi.fetch("Email=&Token=&androidId=", (u, m, h, b, s) -> null, new AtomicBoolean());
      fail();
      return null;
    } catch (ProfileApi.Failure f) {
      return f.label();
    }
  }

  @Test
  public void googleErrorValuesAreNormalized() {
    assertEquals("AUTH_ERROR_BADAUTHENTICATION", ProfileApi.errorLabel("BadAuthentication", 403));
    assertEquals("AUTH_HTTP_403", ProfileApi.errorLabel("--", 403));
    assertEquals(47, ProfileApi.errorLabel("x".repeat(80), 200).length());
  }

  @Test
  public void unverifiedMismatchedAndUntrustedProfilesAreRejected() throws Exception {
    for (String data :
        List.of(
            valid().replace(ACCOUNT, "other@example.com"),
            valid().replace("true", "false"),
            valid()
                .replace(
                    "https://lh3.googleusercontent.com/a/fixture", "https://example.com/avatar"),
            "{}")) {
      try {
        ProfileApi.parse(json(data), ACCOUNT);
        fail("Profile should have been rejected");
      } catch (IOException expected) {
      }
    }
    for (String url :
        List.of(
            "http://lh3.googleusercontent.com/a",
            "https://lh3.googleusercontent.com.evil.example/a",
            "https://user@lh3.googleusercontent.com/a",
            "https://lh3.googleusercontent.com:444/a",
            "https://lh3.googleusercontent.com/a#fragment"))
      assertFalse(ProfileApi.imageAddress(url));
    assertTrue(ProfileApi.imageAddress("https://lh3.googleusercontent.com/a/fixture=s96-c"));
  }

  @Test
  public void avatarCacheIsAccountScopedAndClearedOnDisconnect() throws Exception {
    AccountStore account = new AccountStore(context);
    account.save(CREDENTIAL);
    ProfileStore store = new ProfileStore(context);
    Bitmap bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
    bitmap.eraseColor(Color.BLUE);
    store.save(ACCOUNT, "Avatar Fixture", bitmap);
    assertNotNull(new ProfileStore(context).cached(ACCOUNT));
    assertNull(store.cached("other@example.com"));
    assertEquals("Avatar Fixture", store.name(ACCOUNT));
    account.clear();
    assertNull(store.cached(ACCOUNT));
    assertEquals("", store.name(ACCOUNT));
    // A delayed response for the old account must not repopulate a disconnected cache.
    store.save(ACCOUNT, "late", bitmap);
    assertNull(store.cached(ACCOUNT));
  }

  @Test
  public void decodingPreservesColorAndRejectsInvalidImages() throws Exception {
    Bitmap bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888);
    bitmap.eraseColor(Color.RED);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes);
    Bitmap avatar = ProfileStore.decode(bytes.toByteArray());
    assertEquals(128, avatar.getWidth());
    assertEquals(128, avatar.getHeight());
    assertEquals(Color.RED, avatar.getPixel(64, 64));
    try {
      ProfileStore.decode(json("not an image"));
      fail();
    } catch (IOException expected) {
    }
  }

  @Test
  public void cachedPhotoAppearsWithoutTintInBothThemes() throws Exception {
    new AccountStore(context).save(CREDENTIAL);
    Bitmap bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
    bitmap.eraseColor(Color.rgb(20, 160, 100));
    new ProfileStore(context).save(ACCOUNT, "Avatar Fixture", bitmap);
    context
        .getSharedPreferences("profile", 0)
        .edit()
        .putLong("retryAfter", System.currentTimeMillis() + 24 * 60 * 60 * 1000L)
        .commit();
    new Settings(context).enabled(false);
    Scheduler.configure(context);
    try {
      for (String theme : List.of("light", "dark")) {
        context
            .getSharedPreferences("appearance", 0)
            .edit()
            .putString("theme", theme)
            .putBoolean("dynamic", false)
            .commit();
        try (var scenario = androidx.test.core.app.ActivityScenario.launch(MainActivity.class)) {
          AtomicBoolean visible = new AtomicBoolean();
          for (int i = 0; i < 30 && !visible.get(); i++) {
            scenario.onActivity(
                activity -> {
                  android.widget.ImageButton button = activity.findViewById(R.id.menu_account);
                  assertNotNull(button);
                  assertNull(button.getImageTintList());
                  assertTrue(button.getContentDescription().toString().contains(ACCOUNT));
                  if (button.getDrawable()
                      instanceof androidx.core.graphics.drawable.RoundedBitmapDrawable drawable)
                    visible.set(drawable.getBitmap().getPixel(64, 64) == Color.rgb(20, 160, 100));
                });
            if (!visible.get()) Thread.sleep(100);
          }
          assertTrue("Cached photo should appear", visible.get());
          androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync();
          Bitmap screenshot =
              androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                  .getUiAutomation()
                  .takeScreenshot();
          assertNotNull(screenshot);
          try (FileOutputStream out =
              new FileOutputStream(
                  new File(context.getExternalFilesDir(null), "profile-" + theme + ".png"))) {
            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, out));
          }
        }
      }
    } finally {
      context.getSharedPreferences("appearance", 0).edit().clear().commit();
    }
  }
}
