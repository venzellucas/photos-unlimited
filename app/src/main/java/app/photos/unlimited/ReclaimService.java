package app.photos.unlimited;

import android.app.*;
import android.content.*;
import android.os.IBinder;
import android.os.PowerManager;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Works through queued reclaim jobs in a visible foreground service. Stops the whole run as soon
 * as one replacement is charged: the remaining originals then stay untouched.
 */
public final class ReclaimService extends Service {
  private static final AtomicBoolean BUSY = new AtomicBoolean();
  private static volatile String status = "";
  private final AtomicBoolean stop = new AtomicBoolean();
  private PowerManager.WakeLock wake;
  private boolean ownsRun;

  public static boolean busy() {
    return BUSY.get();
  }

  public static String status() {
    return status;
  }

  /** Where staged copies live, as MediaStore records it. */
  public static final String STAGING_PATH = "Pictures/Photos Unlimited/Pending upload/";

  /**
   * Staged copies live in shared storage, not private app storage: they survive uninstalling the
   * app or clearing its data, and a new install can finish them (see ReclaimRecovery).
   */
  public static File dir(Context context) {
    return new File(
        android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_PICTURES),
        "Photos Unlimited/Pending upload");
  }

  @Override
  public void onCreate() {
    super.onCreate();
    ensureChannel(this);
  }

  /** Creating a channel that already exists does nothing. */
  static void ensureChannel(Context context) {
    context
        .getSystemService(NotificationManager.class)
        .createNotificationChannel(
            new NotificationChannel("reclaim", "Free up storage", NotificationManager.IMPORTANCE_LOW));
  }

  private Notification notification(String text, long done, long total) {
    PendingIntent open =
        PendingIntent.getActivity(
            this,
            6,
            new Intent(this, MainActivity.class).putExtra("screen", 3),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    PendingIntent pause =
        PendingIntent.getService(
            this,
            7,
            new Intent(this, ReclaimService.class).setAction("pause"),
            PendingIntent.FLAG_IMMUTABLE);
    Notification.Builder b =
        new Notification.Builder(this, "reclaim")
            .setSmallIcon(R.drawable.ic_storage)
            .setContentTitle("Freeing up Google Photos storage")
            .setContentText(text)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(new Notification.Action.Builder(null, "Pause", pause).build());
    if (total > 0) b.setProgress(1000, (int) (done * 1000 / total), false);
    else b.setProgress(0, 0, true);
    return b.build();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent != null && "pause".equals(intent.getAction())) {
      stop.set(true);
      return START_NOT_STICKY;
    }
    startForeground(7402, notification("Preparing…", 0, 0));
    if (!BUSY.compareAndSet(false, true)) return START_NOT_STICKY;
    ownsRun = true;
    wake =
        getSystemService(PowerManager.class)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PhotosUnlimited:reclaim");
    wake.setReferenceCounted(false);
    wake.acquire(30 * 60 * 1000L);
    new Thread(this::work, "photos-reclaim").start();
    return START_NOT_STICKY;
  }

  /** Finishes staged copies left by an earlier install; see ReclaimRecovery. */
  private void recover(PhotosApi api, String account) throws IOException {
    ReclaimRecovery.index(this);
    java.util.Set<String> pendingConsent = new Settings(this).recoveryCleanup();
    List<ReclaimRecovery.Orphan> orphans = new java.util.ArrayList<>();
    // Already verified and in Google Photos; only waiting for the user's delete consent.
    for (ReclaimRecovery.Orphan o : ReclaimRecovery.orphans(this))
      if (!pendingConsent.contains(o.uri().toString())) orphans.add(o);
    if (orphans.isEmpty()) return;
    say("Finishing " + orphans.size() + " item(s) left from before…");
    Settings settings = new Settings(this);
    ReclaimRecovery.Result r =
        ReclaimRecovery.finish(this, api, account, orphans, settings.recoveryApproved(), stop);
    java.util.Set<String> cleanup = settings.recoveryCleanup();
    for (android.net.Uri uri : r.consent()) cleanup.add(uri.toString());
    settings.recoveryCleanup(cleanup);
    settings.recoveryOtherAccount(r.otherAccount());
  }

  /**
   * Android gives apps no warning before they are uninstalled, and uninstalling removes nothing
   * from the staging folder. Still, while an original exists only on this phone, say so plainly.
   * Also runs at boot, after an app update and when the app opens, because a swiped-away or
   * rebooted notification would otherwise stay gone. {@code account} is unused (every account
   * counts); new callers pass "". Returns the count of items only on this phone. Synchronized so a
   * read-then-post from one caller can't land after another caller's newer cancel.
   */
  static synchronized int warnIfOnlyOnPhone(Context context, String account) {
    ensureChannel(context); // a boot-time post must work even if Free up never ran in this install
    NotificationManager nm = context.getSystemService(NotificationManager.class);
    int unsafe;
    try (ReclaimDb db = new ReclaimDb(context)) {
      unsafe = db.unsafeAll(); // every account: switching accounts must not hide this
    }
    if (unsafe == 0) {
      nm.cancel(7403);
      return 0;
    }
    PendingIntent open =
        PendingIntent.getActivity(
            context,
            8,
            new Intent(context, MainActivity.class).putExtra("screen", 3),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    String text =
        unsafe
            + " item(s) are only on this phone until they’re uploaded again. Open the app to"
            + " finish. Copies are kept in Pictures/Photos Unlimited/Pending upload.";
    nm.notify(
        7403,
        new Notification.Builder(context, "reclaim")
            .setSmallIcon(R.drawable.ic_storage)
            .setContentTitle("Don’t uninstall Photos Unlimited yet")
            .setContentText(text)
            .setStyle(new Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setOngoing(true)
            .build());
    return unsafe;
  }

  /** Google Photos removed phone copies about 3 s after a cloud deletion in testing. */
  static final long GUARD_DELAY = 60_000;

  private void guard(ReclaimDb db, ReclaimEngine engine, String account, long minAge) {
    for (ReclaimEngine.Job job : db.guarding(account)) {
      if (System.currentTimeMillis() - job.updated < minAge) continue;
      try {
        engine.guard(job);
      } catch (IOException e) {
        // Kept for the next run: the app's copy is only removed after a successful check.
      }
    }
  }

  private void say(String text) {
    status = text;
    getSystemService(NotificationManager.class).notify(7402, notification(text, 0, 0));
  }

  private void work() {
    AccountStore store = new AccountStore(this);
    String account = store.email();
    long deadline = System.currentTimeMillis() + 25 * 60 * 1000L;
    try (ReclaimDb db = new ReclaimDb(this)) {
      if (account.isEmpty()) return;
      String credential = store.load();
      // Never act for one account with another account's credential.
      if (!PhotosApi.email(credential).equalsIgnoreCase(account)) return;
      PhotosApi api = new PhotosApi(credential, stop);
      long[] lastNotice = {0};
      ReclaimEngine engine =
          new ReclaimEngine(
              new ReclaimRemote(this, api, account, stop),
              db,
              new ReclaimEngine.Events() {
                public void step(ReclaimEngine.Job job, String detail) {
                  say(job.name + ": " + detail);
                }

                public void bytes(ReclaimEngine.Job job, long done, long total) {
                  long now = System.currentTimeMillis();
                  if (now - lastNotice[0] < 1000) return;
                  lastNotice[0] = now;
                  getSystemService(NotificationManager.class)
                      .notify(7402, notification(job.name, done, total));
                }
              },
              dir(this),
              ReclaimEngine.SYSTEM);
      recover(api, account);
      long wait = 5000;
      String stopped = null;
      guard(db, engine, account, GUARD_DELAY);
      outer:
      while (System.currentTimeMillis() < deadline) {
        List<ReclaimEngine.Job> pending = db.pending(account);
        if (pending.isEmpty()) break;
        boolean progressed = false, waiting = false;
        // At most one item may be between "original removed" and "replacement confirmed": a
        // charged replacement must stop the run before any other original is touched.
        boolean inFlight = false;
        for (ReclaimEngine.Job j : pending) if (j.state.remoteChanged()) inFlight = true;
        for (ReclaimEngine.Job job : pending) {
          PhotosApi.checkStopped(stop);
          if (inFlight && !job.state.remoteChanged()) continue;
          if (!account.equals(store.email())) throw new InterruptedIOException();
          ReclaimEngine.State before = job.state;
          try {
            engine.run(job, deadline);
          } catch (ReclaimEngine.Later e) {
            waiting = true;
            say(job.name + ": " + e.getMessage());
          }
          if (job.state != before) progressed = true;
          if (job.state == ReclaimEngine.State.CHARGED
              || job.state == ReclaimEngine.State.ATTENTION) {
            // Something unexpected happened after a deletion: don't touch more originals.
            db.cancelQueued(account);
            stopped =
                job.state == ReclaimEngine.State.CHARGED
                    ? "Stopped: a replacement still used storage. Remaining items were not"
                        + " touched."
                    : "Stopped: " + job.name + " needs attention. Remaining items were not"
                        + " touched.";
            break outer;
          }
          guard(db, engine, account, GUARD_DELAY);
        }
        if (!waiting) continue;
        // Google needs a moment to forget a deleted original or report a new item's storage.
        ReclaimEngine.SYSTEM.sleep(progressed ? 5000 : wait);
        wait = progressed ? 5000 : Math.min(wait * 2, 60000);
      }
      // Give Google Photos time to react to the deletions, then check every phone file.
      long last = 0;
      for (ReclaimEngine.Job job : db.guarding(account)) last = Math.max(last, job.updated);
      if (last > 0) {
        say("Checking that photos are still on this phone…");
        long wait2 = last + GUARD_DELAY - System.currentTimeMillis();
        if (wait2 > 0) ReclaimEngine.SYSTEM.sleep(Math.min(wait2, GUARD_DELAY));
        guard(db, engine, account, 0);
      }
      int left = db.pending(account).size();
      say(
          stopped != null
              ? stopped
              : left == 0
                  ? "Finished."
                  : left + " item(s) still in progress. Open Storage to continue.");
    } catch (InterruptedIOException e) {
      say("Paused. Items already started will continue when you resume.");
    } catch (PhotosApi.ApiException e) {
      say(
          e.signIn
              ? "Reconnect your Google account to continue freeing storage."
              : "Google Photos stopped the run: " + e.getMessage());
    } catch (IOException e) {
      say("Interrupted: " + e.getMessage() + " Resume to continue.");
    } catch (Exception e) {
      say("The run stopped unexpectedly. Items already started will resume safely.");
    } finally {
      warnIfOnlyOnPhone(this, account);
      BUSY.set(false);
      ownsRun = false;
      if (wake != null && wake.isHeld()) wake.release();
      stopForeground(STOP_FOREGROUND_REMOVE);
      stopSelf();
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
