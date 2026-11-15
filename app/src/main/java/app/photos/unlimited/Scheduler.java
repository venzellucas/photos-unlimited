package app.photos.unlimited;

import android.app.job.*;
import android.content.*;
import android.provider.MediaStore;

public final class Scheduler {
  static final int PERIODIC = 7401, CHANGES = 7402, CONTINUE = 7403;

  static JobInfo.Builder builder(Context c, int id) {
    Settings settings = new Settings(c);
    return new JobInfo.Builder(id, new ComponentName(c, BackupJob.class))
        .setRequiredNetworkType(
            settings.wifi() ? JobInfo.NETWORK_TYPE_UNMETERED : JobInfo.NETWORK_TYPE_ANY)
        .setRequiresCharging(settings.charging())
        .setRequiresBatteryNotLow(true)
        .setBackoffCriteria(30000, JobInfo.BACKOFF_POLICY_EXPONENTIAL);
  }

  public static void configure(Context c) {
    JobScheduler jobs = c.getSystemService(JobScheduler.class);
    Settings settings = new Settings(c);
    if (!settings.enabled()) {
      jobs.cancel(PERIODIC);
      jobs.cancel(CHANGES);
      jobs.cancel(CONTINUE);
      BackupEngine.cancel();
      return;
    }
    jobs.schedule(builder(c, PERIODIC).setPeriodic(15 * 60 * 1000).setPersisted(true).build());
    watch(c);
  }

  public static void ensure(Context c) {
    JobScheduler jobs = c.getSystemService(JobScheduler.class);
    if (new Settings(c).enabled()) {
      if (jobs.getPendingJob(PERIODIC) == null) configure(c);
      else if (jobs.getPendingJob(CHANGES) == null) watch(c);
    }
  }

  public static void watch(Context c) {
    if (!new Settings(c).enabled()) return;
    c.getSystemService(JobScheduler.class)
        .schedule(
            builder(c, CHANGES)
                .addTriggerContentUri(
                    new JobInfo.TriggerContentUri(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                .addTriggerContentUri(
                    new JobInfo.TriggerContentUri(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                .setTriggerContentUpdateDelay(15000)
                .setTriggerContentMaxDelay(60000)
                .build());
  }

  public static void continuation(Context c) {
    Settings settings = new Settings(c);
    if (!settings.enabled()) return;
    long delay = 60000;
    try (BackupDb db = new BackupDb(c)) {
      delay =
          Math.max(
              delay,
              db.nextAttemptAt(new AccountStore(c).email(), settings.folders())
                  - System.currentTimeMillis());
    }
    c.getSystemService(JobScheduler.class)
        .schedule(builder(c, CONTINUE).setMinimumLatency(delay).build());
  }
}
