package app.photos.unlimited;

import android.content.*;
import android.os.SystemClock;
import android.util.Log;

public final class BootReceiver extends BroadcastReceiver {
  private static final String TAG = "PhotosUnlimited";

  /**
   * After an update Android clears the app's notifications, and on an API 37 emulator that cleanup
   * landed about a second after this receiver had posted. Posting again later survives it; the
   * update broadcast is a background one, so the receiver may run well past this.
   */
  static final long UPDATE_REPOST_DELAY = 15_000;

  @Override
  public void onReceive(Context c, Intent intent) {
    PendingResult r = goAsync();
    String action = intent.getAction();
    new Thread(
            () -> {
              try {
                handle(c, action);
                if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
                  SystemClock.sleep(UPDATE_REPOST_DELAY);
                  warn(c);
                }
              } finally {
                r.finish();
              }
            })
        .start();
  }

  /**
   * Reschedules backup and re-posts "Don't uninstall yet". Each step is caught on its own, so a
   * scheduling failure can't stop the warning. Tests call this directly.
   */
  static void handle(Context c, String action) {
    if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
        && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
    try {
      Scheduler.ensure(c);
    } catch (RuntimeException e) {
      Log.w(TAG, "Couldn't reschedule backup after " + action, e);
    }
    warn(c);
  }

  private static void warn(Context c) {
    try {
      ReclaimService.warnIfOnlyOnPhone(c, "");
    } catch (RuntimeException e) {
      Log.w(TAG, "Couldn't re-post the only-on-phone warning", e);
    }
  }
}
