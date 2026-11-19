package app.photos.unlimited;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Finishes staged copies that no journal entry is responsible for, which happens after the app
 * was uninstalled (or its data cleared) while an item was between deletion and re-upload.
 *
 * <p>A staged file is trusted only when its bytes hash to the SHA-1 in its own name. If Google
 * Photos lacks that content it is uploaded; once Google has it, the staged copy is removed. Files
 * written by an earlier install belong to that install, so Android asks the user before they can
 * be deleted; those are returned for a consent request instead.
 */
public final class ReclaimRecovery {
  public record Orphan(Uri uri, String name, long size, long taken, boolean partial, boolean owned) {}

  public record Result(int uploaded, int removed, int otherAccount, int unverified, List<Uri> consent) {}

  private ReclaimRecovery() {}

  /**
   * Asks Android to index files in the staging folder. Files written with java.io are not always
   * in the media index right away, and a later install can only find them through it.
   */
  public static void index(Context context, java.io.File... files) {
    String[] paths;
    if (files.length > 0) {
      paths = new String[files.length];
      for (int i = 0; i < files.length; i++) paths[i] = files[i].getAbsolutePath();
    } else {
      java.io.File[] found = ReclaimService.dir(context).listFiles();
      if (found == null || found.length == 0) return;
      paths = new String[found.length];
      for (int i = 0; i < found.length; i++) paths[i] = found[i].getAbsolutePath();
    }
    java.util.concurrent.CountDownLatch done =
        new java.util.concurrent.CountDownLatch(paths.length);
    android.media.MediaScannerConnection.scanFile(
        context, paths, null, (path, uri) -> done.countDown());
    try {
      done.await(20, java.util.concurrent.TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** Staged files not referenced by any journal entry. Needs media read access for old files. */
  public static List<Orphan> orphans(Context context) {
    Set<String> referenced = new HashSet<>();
    try (ReclaimDb db = new ReclaimDb(context)) {
      referenced.addAll(db.stagedNames());
    }
    List<Orphan> result = new ArrayList<>();
    for (boolean video : new boolean[] {false, true}) {
      Uri base =
          video
              ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
              : MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
      try (Cursor c =
          context
              .getContentResolver()
              .query(
                  base,
                  new String[] {
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATE_TAKEN,
                    MediaStore.MediaColumns.OWNER_PACKAGE_NAME
                  },
                  "relative_path=?",
                  new String[] {ReclaimService.STAGING_PATH},
                  null)) {
        if (c == null) continue;
        while (c.moveToNext()) {
          String name = c.getString(1);
          if (name == null || referenced.contains(name)) continue;
          result.add(
              new Orphan(
                  ContentUris.withAppendedId(base, c.getLong(0)),
                  name,
                  c.getLong(2),
                  c.isNull(3) ? 0 : c.getLong(3),
                  name.startsWith(ReclaimEngine.PARTIAL),
                  context.getPackageName().equals(c.getString(4))));
        }
      } catch (RuntimeException e) {
        // Without media access, files from an earlier install are simply not visible.
      }
    }
    return result;
  }

  public static Result finish(
      Context context, PhotosApi api, String account, List<Orphan> orphans, AtomicBoolean stop)
      throws IOException {
    MediaScanner scanner = new MediaScanner(context);
    String tag = ReclaimEngine.accountTag(account);
    int uploaded = 0, removed = 0, other = 0, unverified = 0;
    List<Uri> consent = new ArrayList<>();
    for (Orphan orphan : orphans) {
      PhotosApi.checkStopped(stop);
      if (orphan.partial()) {
        // An interrupted copy: never complete, safe to drop.
        if (remove(context, orphan)) removed++;
        else consent.add(orphan.uri());
        continue;
      }
      String expected = ReclaimEngine.stagedHash(orphan.name());
      if (expected == null) continue; // Not ours; leave it alone.
      if (!tag.equals(ReclaimEngine.stagedAccount(orphan.name()))) {
        other++;
        continue;
      }
      MessageDigest digest = PhotosApi.sha1();
      long size = 0;
      try (InputStream in = scanner.openOriginal(orphan.uri().toString())) {
        byte[] buffer = new byte[128 * 1024];
        int n;
        while ((n = in.read(buffer)) != -1) {
          PhotosApi.checkStopped(stop);
          digest.update(buffer, 0, n);
          size += n;
        }
      } catch (IOException e) {
        if (e instanceof java.io.InterruptedIOException) throw e;
        unverified++;
        continue;
      }
      byte[] sha1 = digest.digest();
      if (!ReclaimEngine.hex(sha1).equals(expected)) {
        unverified++; // Changed or truncated: keep it for the user, never upload it.
        continue;
      }
      if (api.find(sha1).isEmpty()) {
        long when = orphan.taken() > 0 ? orphan.taken() / 1000 : System.currentTimeMillis() / 1000;
        String key =
            api.upload(
                () -> scanner.openOriginal(orphan.uri().toString()),
                ReclaimEngine.stagedName(orphan.name()),
                size,
                when,
                sha1,
                (a, b) -> {});
        if (key.isEmpty()) throw new IOException("Upload was not confirmed");
        uploaded++;
      }
      if (remove(context, orphan)) removed++;
      else consent.add(orphan.uri());
    }
    return new Result(uploaded, removed, other, unverified, consent);
  }

  private static boolean remove(Context context, Orphan orphan) {
    if (!orphan.owned()) return false;
    try {
      return context.getContentResolver().delete(orphan.uri(), null, null) > 0;
    } catch (RuntimeException e) {
      return false;
    }
  }
}
