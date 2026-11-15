package app.photos.unlimited;

import static org.junit.Assert.*;

import android.app.job.*;
import android.content.*;
import android.graphics.Bitmap;
import android.provider.MediaStore;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BackupInstrumentedTest {
  private Context context;

  @Before
  public void prepare() {
    context = ApplicationProvider.getApplicationContext();
    context.deleteDatabase("uploads.db");
    new Settings(context).enabled(false);
    Scheduler.configure(context);
  }

  @After
  public void cleanup() {
    new Settings(context).enabled(false);
    Scheduler.configure(context);
    new AccountStore(context).clear();
    context.deleteDatabase("uploads.db");
  }

  @Test
  public void interruptedQueueSurvivesReopeningAndIsAccountScoped() {
    String folder = "external_primary:DCIM/Camera/";
    try (BackupDb db = new BackupDb(context)) {
      db.discover("a", "content://test/1", "one.jpg", folder, "image/jpeg", 50, 10, 100);
      assertNotNull(db.next("a", Set.of(folder)));
    }
    try (BackupDb db = new BackupDb(context)) {
      assertNull(db.next("b", Set.of(folder)));
      db.recover("a");
      BackupDb.Item item = db.next("a", Set.of(folder));
      assertNotNull(item);
      db.complete(item, "confirmed", false);
      db.discover("a", "content://test/1", "one.jpg", folder, "image/jpeg", 50, 10, 100);
      assertNull(db.next("a", Set.of(folder)));
      db.discover("a", "content://test/1", "one.jpg", folder, "image/jpeg", 60, 11, 100);
      assertNotNull(db.next("a", Set.of(folder)));
    }
  }

  @Test
  public void failedUploadsBackOffAndUnselectedFoldersStayUntouched() {
    try (BackupDb db = new BackupDb(context)) {
      db.discover("a", "content://test/1", "one.jpg", "selected", "image/jpeg", 50, 10, 100);
      db.discover("a", "content://test/2", "two.jpg", "private", "image/jpeg", 50, 10, 100);
      BackupDb.Item item = db.next("a", Set.of("selected"));
      assertNotNull(item);
      db.fail(item, "Network down", false);
      assertNull(db.next("a", Set.of("selected")));
      db.retry("a");
      assertNotNull(db.next("a", Set.of("selected")));
      assertEquals(1, (int) db.counts("a").get("ready"));
    }
  }

  @Test
  public void accountCredentialsAreEncryptedAndPersistAcrossInstances() throws Exception {
    String credential = "Email=fixture%40example.com&Token=private-test-value&androidId=1234";
    new AccountStore(context).save(credential);
    assertEquals(credential, new AccountStore(context).load());
    String sealed = context.getSharedPreferences("account", 0).getString("sealed", "");
    assertFalse(sealed.contains("private-test-value"));
    assertEquals("fixture@example.com", new AccountStore(context).email());
  }

  @Test
  public void automaticBackupUsesPersistedNetworkAndChargingConstraints() {
    Settings settings = new Settings(context);
    settings.enabled(true);
    settings.wifi(true);
    settings.charging(true);
    Scheduler.configure(context);
    JobInfo job = context.getSystemService(JobScheduler.class).getPendingJob(Scheduler.PERIODIC);
    assertNotNull(job);
    assertTrue(job.isPersisted());
    assertTrue(job.isRequireCharging());
    assertTrue(job.isRequireBatteryNotLow());
    assertEquals(15 * 60 * 1000, job.getIntervalMillis());
    assertNotNull(job.getRequiredNetwork());
    settings.enabled(false);
    Scheduler.configure(context);
    assertNull(context.getSystemService(JobScheduler.class).getPendingJob(Scheduler.PERIODIC));
    settings.charging(false);
  }

  @Test
  public void mediaScannerFindsOnlySelectedPublishedMedia() throws Exception {
    InstrumentationRegistry.getInstrumentation()
        .getUiAutomation()
        .grantRuntimePermission(
            context.getPackageName(), android.Manifest.permission.READ_MEDIA_IMAGES);
    InstrumentationRegistry.getInstrumentation()
        .getUiAutomation()
        .grantRuntimePermission(
            context.getPackageName(), android.Manifest.permission.READ_MEDIA_VIDEO);
    ContentValues values = new ContentValues();
    // External media can outlive uninstall/reinstall and retain a different owner.
    // A fresh directory prevents collisions with earlier disposable test runs.
    String fixtureFolder = "Pictures/PhotosUnlimitedTest-" + UUID.randomUUID();
    values.put(MediaStore.Images.Media.DISPLAY_NAME, "fixture.jpg");
    values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
    values.put(MediaStore.Images.Media.RELATIVE_PATH, fixtureFolder + "/");
    values.put(MediaStore.Images.Media.IS_PENDING, 1);
    android.net.Uri uri =
        context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    assertNotNull(uri);
    try {
      try (java.io.OutputStream out = context.getContentResolver().openOutputStream(uri)) {
        Bitmap image = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888);
        image.eraseColor(android.graphics.Color.GREEN);
        image.compress(Bitmap.CompressFormat.JPEG, 90, out);
        image.recycle();
      }
      values.clear();
      values.put(MediaStore.Images.Media.IS_PENDING, 0);
      context.getContentResolver().update(uri, values, null, null);
      MediaScanner scanner = new MediaScanner(context);
      List<MediaScanner.Folder> folders = scanner.folders();
      MediaScanner.Folder folder =
          folders.stream().filter(f -> f.label().equals(fixtureFolder)).findFirst().orElseThrow();
      assertTrue(folder.count() > 0);
      try (BackupDb db = new BackupDb(context)) {
        scanner.scan(db, "fixture", Set.of(), new AtomicBoolean());
        assertTrue(db.counts("fixture").isEmpty());
        // MediaStore owns DATE_MODIFIED. Wait for the real publication/settling
        // window instead of trying to backdate that read-only field.
        long deadline = android.os.SystemClock.uptimeMillis() + 20000;
        BackupDb.Item discovered = null;
        while (discovered == null && android.os.SystemClock.uptimeMillis() < deadline) {
          scanner.scan(db, "fixture", Set.of(folder.key()), new AtomicBoolean());
          discovered = db.next("fixture", Set.of(folder.key()));
          if (discovered == null) Thread.sleep(500);
        }
        assertNotNull(discovered);
      }
    } finally {
      context.getContentResolver().delete(uri, null, null);
    }
  }
}
