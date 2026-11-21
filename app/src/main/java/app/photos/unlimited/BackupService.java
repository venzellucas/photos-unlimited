package app.photos.unlimited;

import android.app.*;
import android.content.*;
import android.os.IBinder;
import android.os.PowerManager;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BackupService extends Service {
  private final AtomicBoolean stop = new AtomicBoolean();
  private boolean running;
  private long lastNotification;
  private PowerManager.WakeLock wakeLock;

  @Override
  public void onCreate() {
    super.onCreate();
    getSystemService(NotificationManager.class)
        .createNotificationChannel(
            new NotificationChannel(
                "backup", getString(R.string.channel_backup), NotificationManager.IMPORTANCE_LOW));
  }

  private Notification notification(String text, long sent, long total) {
    PendingIntent open =
        PendingIntent.getActivity(
            this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
    PendingIntent pause =
        PendingIntent.getService(
            this,
            1,
            new Intent(this, BackupService.class).setAction("pause"),
            PendingIntent.FLAG_IMMUTABLE);
    Notification.Builder builder =
        new Notification.Builder(this, "backup")
            .setSmallIcon(R.drawable.ic_backup)
            .setContentTitle("Photos Unlimited")
            .setContentText(text)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(new Notification.Action.Builder(null, getString(R.string.action_pause), pause).build());
    if (total > 0) builder.setProgress(100, (int) Math.min(100, sent * 100 / total), false);
    return builder.build();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent != null && "pause".equals(intent.getAction())) {
      new Settings(this).enabled(false);
      Scheduler.configure(this);
      stop.set(true);
      stopSelf();
      return START_NOT_STICKY;
    }
    startForeground(7400, notification(getString(R.string.backup_notification_preparing), 0, 0));
    if (running) return START_NOT_STICKY;
    running = true;
    wakeLock =
        getSystemService(PowerManager.class)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PhotosUnlimited:upload");
    wakeLock.setReferenceCounted(false);
    wakeLock.acquire(10 * 60 * 1000L);
    new Thread(
            () -> {
              try {
                boolean more =
                    BackupEngine.run(
                        this,
                        stop,
                        (status, sent, total) -> {
                          if (System.currentTimeMillis() - lastNotification > 1500) {
                            lastNotification = System.currentTimeMillis();
                            if (!stop.get()) wakeLock.acquire(10 * 60 * 1000L);
                            getSystemService(NotificationManager.class)
                                .notify(7400, notification(status, sent, total));
                          }
                        });
                if (more && !stop.get()) Scheduler.continuation(this);
              } finally {
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
              }
            },
            "photos-manual-backup")
        .start();
    return START_NOT_STICKY;
  }

  @Override
  public void onDestroy() {
    stop.set(true);
    if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    super.onDestroy();
  }

  @Override
  public void onTimeout(int startId, int foregroundServiceType) {
    stop.set(true);
    Scheduler.continuation(this);
    stopForeground(STOP_FOREGROUND_REMOVE);
    stopSelf();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }
}
