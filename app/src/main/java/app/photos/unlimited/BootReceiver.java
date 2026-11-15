package app.photos.unlimited;

import android.content.*;

public final class BootReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context c, Intent intent) {
    Scheduler.ensure(c);
  }
}
