package app.photos.unlimited;

import android.app.job.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BackupJob extends JobService {
  private final ConcurrentHashMap<Integer, AtomicBoolean> stops = new ConcurrentHashMap<>();

  @Override
  public boolean onStartJob(JobParameters parameters) {
    AtomicBoolean stop = new AtomicBoolean();
    stops.put(parameters.getJobId(), stop);
    new Thread(
            () -> {
              boolean more = BackupEngine.run(this, stop, (status, sent, total) -> {});
              getMainExecutor()
                  .execute(
                      () -> {
                        if (stops.remove(parameters.getJobId(), stop))
                          jobFinished(parameters, false);
                        if (parameters.getJobId() == Scheduler.CHANGES) Scheduler.watch(this);
                        if (more && !stop.get()) Scheduler.continuation(this);
                      });
            },
            "photos-background")
        .start();
    return true;
  }

  @Override
  public boolean onStopJob(JobParameters parameters) {
    AtomicBoolean stop = stops.remove(parameters.getJobId());
    if (stop != null) stop.set(true);
    return new Settings(this).enabled();
  }
}
