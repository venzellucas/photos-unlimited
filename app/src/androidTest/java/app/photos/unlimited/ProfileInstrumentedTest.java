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

  @Test
  public void profileTokenUsesSeparateScopesAndVerifiedAccount() throws Exception {
    List<String> calls = new ArrayList<>();
    PhotosApi.Transport transport =
        (url, method, headers, body, length, stop) -> {
          calls.add(url);
          if (url.equals(PhotosApi.AUTH)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            body.write(out);
            var form = PhotosApi.parseForm(out.toString(StandardCharsets.UTF_8));
            assertEquals(ProfileApi.SCOPES, form.get("service"));
            assertEquals("synthetic-only", form.get("Token"));
            return new PhotosApi.Response(json("Auth=synthetic-profile-bearer\n"), Map.of());
          }
          assertEquals(ProfileApi.USERINFO, url);
          assertEquals("GET", method);
          assertNull(body);
          assertEquals("Bearer synthetic-profile-bearer", headers.get("Authorization"));
          return new PhotosApi.Response(json(valid()), Map.of());
        };
    new AccountStore(context).save(CREDENTIAL);
    var profile = ProfileApi.fetch(CREDENTIAL, transport, new AtomicBoolean());
    assertEquals("Avatar Fixture", profile.name());
    assertEquals(2, calls.size());
    assertEquals(CREDENTIAL, new AccountStore(context).load());
    assertEquals("original-upload-scope", PhotosApi.parseForm(CREDENTIAL).get("service"));
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
