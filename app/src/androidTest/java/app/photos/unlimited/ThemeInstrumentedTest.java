package app.photos.unlimited;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Set;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ThemeInstrumentedTest {
  Context context;

  @Before
  public void prepare() {
    context = ApplicationProvider.getApplicationContext();
    new Settings(context).enabled(false);
    Scheduler.configure(context);
    context.getSharedPreferences("appearance", 0).edit().clear().commit();
  }

  @After
  public void cleanup() {
    new AccountStore(context).clear();
    new Settings(context).enabled(false);
    context.getSharedPreferences("appearance", 0).edit().clear().commit();
    context.deleteDatabase("uploads.db");
  }

  @Test
  public void darkRecreationPreservesCredentialsFoldersAndLedger() throws Exception {
    String credential =
        "Email=theme-fixture%40example.com&Token=synthetic-test-only&androidId=1234";
    new AccountStore(context).save(credential);
    // Keep this synthetic account offline while exercising activity recreation.
    context
        .getSharedPreferences("profile", 0)
        .edit()
        .putString("account", "theme-fixture@example.com")
        .putLong("retryAfter", System.currentTimeMillis() + 24 * 60 * 60 * 1000L)
        .commit();
    new Settings(context).folders(Set.of("external_primary:DCIM/ThemeFixture/"));
    try (BackupDb db = new BackupDb(context)) {
      db.discover(
          "theme-fixture@example.com",
          "content://fixture/1",
          "one.jpg",
          "external_primary:DCIM/ThemeFixture/",
          "image/jpeg",
          10,
          1,
          1);
      db.complete(
          db.next("theme-fixture@example.com", new Settings(context).folders()),
          "remote-fixture",
          false);
    }
    context
        .getSharedPreferences("appearance", 0)
        .edit()
        .putString("theme", "light")
        .putBoolean("dynamic", false)
        .commit();
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(a -> assertEquals(Color.parseColor("#F6FBF5"), new Ui(a).surface()));
      context.getSharedPreferences("appearance", 0).edit().putString("theme", "dark").commit();
      scenario.recreate();
      scenario.onActivity(
          a -> {
            assertEquals(
                Configuration.UI_MODE_NIGHT_YES,
                a.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK);
            assertEquals(Color.parseColor("#101511"), new Ui(a).surface());
          });
    }
    assertEquals(credential, new AccountStore(context).load());
    assertEquals(Set.of("external_primary:DCIM/ThemeFixture/"), new Settings(context).folders());
    try (BackupDb db = new BackupDb(context)) {
      assertEquals(Integer.valueOf(1), db.counts("theme-fixture@example.com").get("done"));
    }
  }

  @Test
  public void bothThemesLaunchAllNavigationDestinations() {
    for (String theme : new String[] {"light", "dark"}) {
      context
          .getSharedPreferences("appearance", 0)
          .edit()
          .putString("theme", theme)
          .putBoolean("dynamic", true)
          .commit();
      try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
        for (int id = 1; id <= 4; id++) {
          int destination = id;
          scenario.onActivity(
              a -> {
                var nav = find(a.getWindow().getDecorView());
                assertNotNull(nav);
                int target = nav.getMenu().getItem(destination - 1).getItemId();
                nav.setSelectedItemId(target);
                assertEquals(target, nav.getSelectedItemId());
              });
        }
      }
    }
  }

  private com.google.android.material.bottomnavigation.BottomNavigationView find(
      android.view.View v) {
    if (v instanceof com.google.android.material.bottomnavigation.BottomNavigationView nav)
      return nav;
    if (v instanceof android.view.ViewGroup group)
      for (int i = 0; i < group.getChildCount(); i++) {
        var found = find(group.getChildAt(i));
        if (found != null) return found;
      }
    return null;
  }
}
