package app.photos.unlimited;

import static org.junit.Assert.*;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.LocaleList;
import android.os.SystemClock;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LanguageInstrumentedTest {
  Context context;

  @Before
  public void prepare() {
    context = ApplicationProvider.getApplicationContext();
  }

  @After
  public void cleanup() {
    Languages.choose(context, 0);
    // Later tests assert English text, so the reset must really have happened.
    assertTrue(waitFor(() -> english(R.string.job_done).equals(Text.get(R.string.job_done))));
  }

  @Test
  public void choosingALanguageChangesStoredMessagesAndSystemDefaultRestoresEnglish() {
    assertEquals(0, Languages.current(context));
    String english = english(R.string.job_done);
    String portuguese = in("pt-BR", R.string.job_done);
    assertNotEquals("the pt-BR translation must exist", english, portuguese);

    Languages.choose(context, 2);
    assertEquals("pt-BR", Languages.TAGS[Languages.current(context)]);
    assertTrue(
        "Text.get should follow the app language",
        waitFor(() -> portuguese.equals(Text.get(R.string.job_done))));
    assertEquals(
        in("pt-BR", R.string.backup_checking_file, "a.jpg"),
        Text.get(R.string.backup_checking_file, "a.jpg"));

    Languages.choose(context, 0);
    assertEquals(0, Languages.current(context));
    assertTrue(waitFor(() -> english.equals(Text.get(R.string.job_done))));
  }

  @Test
  public void everyOfferedLanguageHasItsOwnTranslation() {
    String english = english(R.string.reclaim_dont_uninstall);
    for (int i = 2; i < Languages.TAGS.length; i++)
      assertNotEquals(
          Languages.TAGS[i], english, in(Languages.TAGS[i], R.string.reclaim_dont_uninstall));
    // A system language the app doesn't have falls back to English.
    assertEquals(english, in("fr-FR", R.string.reclaim_dont_uninstall));
    // Traditional Chinese is not offered, so it must not get the Simplified text either.
    assertEquals(english, in("zh-TW", R.string.reclaim_dont_uninstall));
    assertEquals(
        in("zh-Hans", R.string.reclaim_dont_uninstall), in("zh-CN", R.string.reclaim_dont_uninstall));
  }

  @Test
  public void reclaimJournalUpgradeIsHarmlessWhenRunTwice() {
    SQLiteDatabase db = SQLiteDatabase.create(null);
    try {
      // Version 3 schema: the jobs table without the motion column.
      db.execSQL("CREATE TABLE jobs(account TEXT NOT NULL, media_key TEXT NOT NULL)");
      db.execSQL("ALTER TABLE jobs ADD COLUMN reduced INTEGER NOT NULL DEFAULT 0");
      db.execSQL("ALTER TABLE jobs ADD COLUMN phone TEXT NOT NULL DEFAULT ''");
      ContentValues row = new ContentValues();
      row.put("account", "a");
      row.put("media_key", "k");
      db.insertOrThrow("jobs", null, row);
      ReclaimDb helper = new ReclaimDb(context);
      helper.onUpgrade(db, 3, 4);
      // A second helper that saw the old version at the same moment upgrades again.
      helper.onUpgrade(db, 3, 4);
      helper.onUpgrade(db, 1, 4);
      helper.close();
      assertEquals(List.of("account", "media_key", "reduced", "phone", "motion"), columns(db));
      try (Cursor c = db.rawQuery("SELECT motion, reduced, phone FROM jobs", null)) {
        assertTrue(c.moveToFirst());
        assertEquals(0, c.getInt(0));
        assertEquals(0, c.getInt(1));
        assertEquals("", c.getString(2));
      }
    } finally {
      db.close();
    }
  }

  private static List<String> columns(SQLiteDatabase db) {
    List<String> names = new ArrayList<>();
    try (Cursor c = db.rawQuery("PRAGMA table_info(jobs)", null)) {
      while (c.moveToNext()) names.add(c.getString(c.getColumnIndexOrThrow("name")));
    }
    return names;
  }

  private String english(int id) {
    return in("en-US", id);
  }

  private String in(String tag, int id, Object... args) {
    Configuration config = new Configuration(context.getResources().getConfiguration());
    config.setLocales(new LocaleList(Locale.forLanguageTag(tag)));
    return context.createConfigurationContext(config).getString(id, args);
  }

  private static boolean waitFor(java.util.function.BooleanSupplier condition) {
    long end = SystemClock.uptimeMillis() + 10_000;
    while (SystemClock.uptimeMillis() < end) {
      if (condition.getAsBoolean()) return true;
      SystemClock.sleep(100);
    }
    return condition.getAsBoolean();
  }
}
