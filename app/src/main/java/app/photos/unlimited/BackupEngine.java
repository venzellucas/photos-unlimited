package app.photos.unlimited;

import android.content.*;
import android.net.*;
import android.os.BatteryManager;
import java.io.*;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.atomic.*;

public final class BackupEngine {
  private static final AtomicBoolean BUSY = new AtomicBoolean();
  private static final AtomicReference<AtomicBoolean> CURRENT = new AtomicReference<>();

  public record ProgressState(
      String account, String detail, long sent, long total, boolean uploading) {}

  private static volatile ProgressState progress = new ProgressState("", "", 0, 0, false);

  public static ProgressState progress() {
    return progress;
  }

  public interface Listener {
    void status(String text, long sent, long total);
  }

  /** A phone copy restored after Free up is expected to be in the library already. */
  private static boolean reclaimed(Context context, String email, byte[] digest) {
    try (ReclaimDb db = new ReclaimDb(context)) {
      return db.reclaimed(email, digest);
    } catch (RuntimeException e) {
      return false;
    }
  }

  public static boolean busy() {
    return BUSY.get();
  }

  public static void cancel() {
    AtomicBoolean current = CURRENT.get();
    if (current != null) current.set(true);
  }

  public static boolean allowed(Context context, Settings settings) {
    ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
    NetworkCapabilities capabilities = cm.getNetworkCapabilities(cm.getActiveNetwork());
    if (capabilities == null
        || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return false;
    if (settings.wifi()
        && !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
      return false;
    Intent battery =
        context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    return !settings.charging()
        || (battery != null && battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0);
  }

  public static boolean run(Context context, AtomicBoolean stop, Listener listener) {
    if (!BUSY.compareAndSet(false, true)) return false;
    CURRENT.set(stop);
    Settings settings = new Settings(context);
    AccountStore account = new AccountStore(context);
    boolean[] transferring = {false};
    Listener notify =
        (text, sent, total) -> {
          progress = new ProgressState(account.email(), text, sent, total, transferring[0]);
          listener.status(text, sent, total);
        };
    long started = System.currentTimeMillis();
    try (BackupDb db = new BackupDb(context)) {
      if (!settings.enabled()) return false;
      settings.failure("");
      String credentials = account.load(), email = account.email();
      if (credentials.isEmpty()) {
        settings.status("Connect your Google account to start backing up.");
        return false;
      }
      if (settings.folders().isEmpty()) {
        settings.status("Choose at least one folder to back up.");
        return false;
      }
      if (!allowed(context, settings)) {
        settings.status("Waiting for your selected network and charging conditions.");
        return false;
      }
      MediaScanner scanner = new MediaScanner(context);
      if (!scanner.originalAccess()) {
        settings.status("Allow media location access to upload complete original files.");
        return false;
      }
      settings.status("Checking selected folders…");
      notify.status("Checking selected folders…", 0, 0);
      db.recover(email);
      scanner.scan(db, email, settings.folders(), stop);
      PhotosApi api = new PhotosApi(credentials, stop);
      BackupDb.Item item;
      while (!stop.get()
          && settings.enabled()
          && System.currentTimeMillis() - started < 8 * 60 * 1000
          && (item = db.next(email, settings.folders())) != null) {
        final BackupDb.Item current = item;
        try {
          if (!allowed(context, settings)) {
            db.release(item);
            settings.status("Waiting for your selected network and charging conditions.");
            return false;
          }
          if (!account.email().equals(email)) {
            db.release(item);
            return false;
          }
          long max =
              item.mime().startsWith("video/") ? 10L * 1024 * 1024 * 1024 : 200L * 1024 * 1024;
          if (item.size() > max)
            throw new PhotosApi.ApiException(
                "File exceeds Google's individual upload size limit.", false, true);
          settings.status("Checking " + item.name());
          transferring[0] = false;
          notify.status("Checking " + item.name(), 0, item.size());
          MessageDigest hash = PhotosApi.sha1();
          long size = 0;
          try (InputStream in = scanner.openOriginal(item.uri())) {
            byte[] buffer = new byte[128 * 1024];
            int n;
            while ((n = in.read(buffer)) != -1) {
              PhotosApi.checkStopped(stop);
              hash.update(buffer, 0, n);
              size += n;
            }
          }
          if (size != item.size())
            throw new IOException("File changed; waiting for a stable copy.");
          byte[] digest = hash.digest();
          String existing = api.find(digest);
          if (!existing.isEmpty()) {
            // A file created after our backup was switched on is already in the library:
            // something else uploaded it first, almost always Google Photos' own backup.
            long since = settings.enabledSince();
            if (since > 0 && item.modified() > since && !reclaimed(context, email, digest))
              settings.otherUploader(item.name());
            db.complete(item, existing, true);
            continue;
          }
          settings.status("Uploading " + item.name());
          transferring[0] = true;
          long[] lastPolicyCheck = {0};
          String key =
              api.upload(
                  () -> scanner.openOriginal(current.uri()),
                  item.name(),
                  item.size(),
                  item.modified(),
                  digest,
                  (bytes, total) -> {
                    if (System.currentTimeMillis() - lastPolicyCheck[0] > 1500) {
                      lastPolicyCheck[0] = System.currentTimeMillis();
                      if (!settings.enabled() || !allowed(context, settings)) stop.set(true);
                    }
                    notify.status("Uploading " + current.name(), bytes, total);
                  });
          db.complete(item, key, false);
        } catch (InterruptedIOException e) {
          db.release(item);
          throw e;
        } catch (PhotosApi.ApiException e) {
          db.fail(item, e.getMessage(), e.permanent);
          if (e.signIn) {
            settings.failure("auth");
            settings.enabled(false);
            settings.status("Sign-in expired. Reconnect your Google account, then resume backup.");
            return false;
          }
        } catch (FileNotFoundException e) {
          db.fail(item, "Local file is no longer available.", true);
        } catch (IOException e) {
          db.fail(item, safeMessage(e), false);
        } catch (SecurityException e) {
          db.release(item);
          settings.failure("media");
          settings.status("Media access changed. Review permissions before resuming.");
          return false;
        }
      }
      settings.lastRun(System.currentTimeMillis());
      Map<String, Integer> counts = db.counts(email, settings.folders());
      int failures = counts.getOrDefault("error", 0) + counts.getOrDefault("blocked", 0);
      if (stop.get() || !settings.enabled())
        settings.status("Backup paused. Pending files are kept in the queue.");
      else if (failures > 0)
        settings.status(
            failures
                + " item"
                + (failures == 1 ? " needs" : "s need")
                + " attention. See recent activity.");
      else if (db.pending(email, settings.folders()))
        settings.status("More files are queued. Backup will continue automatically.");
      else settings.status("Selected folders are up to date.");
      return settings.enabled() && db.pending(email, settings.folders());
    } catch (InterruptedIOException e) {
      settings.status("Backup paused. It will resume when conditions allow.");
      return settings.enabled();
    } catch (IOException e) {
      settings.failure(e instanceof PhotosApi.ApiException auth && auth.signIn ? "auth" : "other");
      settings.status(safeMessage(e));
      return false;
    } catch (Exception e) {
      settings.failure("other");
      settings.status("Backup stopped unexpectedly. Reopen the app and retry.");
      return false;
    } finally {
      CURRENT.compareAndSet(stop, null);
      BUSY.set(false);
      transferring[0] = false;
      notify.status(settings.status(), 0, 0);
    }
  }

  static String safeMessage(IOException e) {
    // Network exception details may contain private URLs; never persist raw transport diagnostics.
    if (e instanceof java.net.SocketTimeoutException)
      return "Connection timed out. Will retry automatically.";
    if (e instanceof java.net.UnknownHostException || e instanceof java.net.ConnectException)
      return "Connection unavailable. Will retry automatically.";
    if (e instanceof javax.net.ssl.SSLException)
      return "Could not establish a secure Google connection.";
    String message = e.getMessage();
    if (message != null
        && (message.startsWith("File ")
            || message.startsWith("Google ")
            || message.startsWith("Upload ")
            || message.startsWith("Allow ")
            || message.startsWith("Original ")
            || message.startsWith("Your saved ")
            || message.startsWith("Android's "))) return message;
    return "Upload could not be confirmed. Will retry automatically.";
  }
}
