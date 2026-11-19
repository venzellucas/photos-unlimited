package app.photos.unlimited;

import android.app.*;
import android.content.*;
import android.os.IBinder;
import android.os.PowerManager;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.atomic.AtomicBoolean;

/** User-started, read-only metadata scan; uploads use a completely separate service. */
public final class InventoryService extends Service {
  private static final AtomicBoolean BUSY = new AtomicBoolean();
  private static volatile String scanningAccount = "";
  private final AtomicBoolean stop = new AtomicBoolean();
  private PowerManager.WakeLock wake;
  private boolean ownsRun;

  public static boolean busy() {
    return BUSY.get();
  }

  public static boolean busy(String account) {
    return busy() && scanningAccount.equals(account);
  }

  @Override
  public void onCreate() {
    super.onCreate();
    getSystemService(NotificationManager.class)
        .createNotificationChannel(
            new NotificationChannel(
                "inventory", "Storage scan", NotificationManager.IMPORTANCE_LOW));
  }

  private Notification notification(String text) {
    PendingIntent open =
        PendingIntent.getActivity(
            this,
            4,
            new Intent(this, MainActivity.class).putExtra("screen", 3),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    PendingIntent pause =
        PendingIntent.getService(
            this,
            5,
            new Intent(this, InventoryService.class).setAction("pause"),
            PendingIntent.FLAG_IMMUTABLE);
    return new Notification.Builder(this, "inventory")
        .setSmallIcon(R.drawable.ic_storage)
        .setContentTitle("Scanning Google Photos")
        .setContentText(text)
        .setContentIntent(open)
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .setProgress(0, 0, true)
        .addAction(new Notification.Action.Builder(null, "Pause scan", pause).build())
        .build();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent != null && "pause".equals(intent.getAction())) {
      stop.set(true);
      return START_NOT_STICKY;
    }
    startForeground(7401, notification("Reading storage metadata…"));
    if (!BUSY.compareAndSet(false, true)) return START_NOT_STICKY;
    if (!LibrarySync.tryLock()) {
      BUSY.set(false);
      stopForeground(STOP_FOREGROUND_REMOVE);
      stopSelf();
      return START_NOT_STICKY;
    }
    ownsRun = true;
    scanningAccount = new AccountStore(this).email();
    wake =
        getSystemService(PowerManager.class)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PhotosUnlimited:inventory");
    wake.setReferenceCounted(false);
    wake.acquire(10 * 60 * 1000L);
    new Thread(this::scan, "photos-inventory").start();
    return START_NOT_STICKY;
  }

  private void scan() {
    String account = scanningAccount, generation = "";
    try (InventoryDb db = new InventoryDb(this)) {
      if (account.isEmpty()) return;
      AccountStore store = new AccountStore(this);
      InventoryDb.Scan current = db.scan(account);
      if (current.generation().isEmpty() || current.cursor().stage() == LibraryProtocol.Stage.DONE)
        current = db.reset(account);
      generation = current.generation();
      PhotosApi api = new PhotosApi(store.load(), stop);
      current =
          LibrarySync.run(
              this,
              db,
              api,
              store,
              account,
              stop,
              500,
              System.currentTimeMillis() + 8 * 60 * 1000L,
              scan ->
                  getSystemService(NotificationManager.class)
                      .notify(7401, notification(scan.pages() + " pages checked · Read-only scan")));
      if (current.cursor().stage() != LibraryProtocol.Stage.DONE)
        db.message(account, generation, "Scan paused. Continue to read the remaining items.");
    } catch (InterruptedIOException e) {
      setMessage(account, generation, "Scan paused. Continue when you’re ready.");
    } catch (PhotosApi.ApiException e) {
      setMessage(
          account,
          generation,
          e.signIn
              ? "Reconnect your Google account to continue the scan."
              : "Google Photos could not complete the scan (HTTP "
                  + e.httpStatus
                  + "). You can continue or start over.");
    } catch (IOException e) {
      setMessage(
          account,
          generation,
          "Scan interrupted or metadata format unavailable. Results are partial. Continue or start"
              + " over.");
    } catch (Exception e) {
      setMessage(
          account, generation, "The scan stopped. Results are partial. Try continuing the scan.");
    } finally {
      scanningAccount = "";
      LibrarySync.unlock();
      BUSY.set(false);
      ownsRun = false;
      if (wake != null && wake.isHeld()) wake.release();
      stopForeground(STOP_FOREGROUND_REMOVE);
      stopSelf();
    }
  }

  private void setMessage(String account, String generation, String message) {
    if (generation.isEmpty()) return;
    try (InventoryDb db = new InventoryDb(this)) {
      db.message(account, generation, message);
    }
  }

  @Override
  public void onDestroy() {
    stop.set(true);
    if (!ownsRun && wake != null && wake.isHeld()) wake.release();
    super.onDestroy();
  }

  @Override
  public void onTimeout(int startId, int type) {
    stop.set(true);
    stopForeground(STOP_FOREGROUND_REMOVE);
    stopSelf();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }
}
