package app.photos.unlimited;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
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
 * <p>Trust: a file name's hash proves integrity, not origin, and any app can write into a shared
 * folder. So only files this app wrote are finished automatically. Files without an owner (what
 * an uninstalled earlier install leaves behind) are uploaded only after the user approved those
 * exact files. Files owned by any other app are ignored.
 *
 * <p>A staged copy is removed only once Google's library shows a live (not trashed) item with the
 * same SHA-1 and size: Google keeps reporting a deleted or trashed original for a while.
 */
public final class ReclaimRecovery {
  public enum Origin {
    OURS,
    UNOWNED,
    FOREIGN
  }

  public record Orphan(
      Uri uri, String name, long size, long taken, boolean partial, Origin origin, boolean video) {
    public boolean owned() {
      return origin == Origin.OURS;
    }
  }

  public record Result(
      int uploaded,
      int removed,
      int otherAccount,
      int unverified,
      int waiting,
      int failed,
      List<Uri> consent) {}

  /** Same per-file limits as backup. */
  static final long PHOTO_LIMIT = 200L * 1024 * 1024, VIDEO_LIMIT = 10L * 1024 * 1024 * 1024;

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

  /** Staged files not referenced by any journal entry, excluding other apps' files. */
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
          String owner = c.getString(4);
          Origin origin =
              owner == null
                  ? Origin.UNOWNED
                  : context.getPackageName().equals(owner) ? Origin.OURS : Origin.FOREIGN;
          if (origin == Origin.FOREIGN) continue; // planted by another app: never ours to act on
          result.add(
              new Orphan(
                  ContentUris.withAppendedId(base, c.getLong(0)),
                  name,
                  c.getLong(2),
                  c.isNull(3) ? 0 : c.getLong(3),
                  name.startsWith(ReclaimEngine.PARTIAL),
                  origin,
                  video));
        }
      } catch (RuntimeException e) {
        // Without media access, files from an earlier install are simply not visible.
      }
    }
    return result;
  }

  /**
   * Finishes orphans: uploads verified copies Google lacks, then removes copies Google provably
   * holds. Unowned files are touched only if listed in {@code approved}.
   */
  public static Result finish(
      Context context,
      PhotosApi api,
      String account,
      List<Orphan> orphans,
      Set<String> approved,
      AtomicBoolean stop)
      throws IOException {
    MediaScanner scanner = new MediaScanner(context);
    ReclaimRemote library = new ReclaimRemote(context, api, account, stop);
    String tag = ReclaimEngine.accountTag(account);
    int uploaded = 0, removed = 0, other = 0, unverified = 0, waiting = 0, failed = 0;
    List<Uri> consent = new ArrayList<>();
    for (Orphan orphan : orphans) {
      PhotosApi.checkStopped(stop);
      if (!orphan.owned() && !approved.contains(orphan.uri().toString())) continue;
      try {
        if (orphan.partial()) {
          // An interrupted copy: never complete, safe to drop.
          if (remove(context, orphan)) removed++;
          else consent.add(orphan.uri());
          continue;
        }
        String expected = ReclaimEngine.stagedHash(orphan.name());
        if (expected == null) continue; // Not a staged copy; leave it alone.
        if (!tag.equals(ReclaimEngine.stagedAccount(orphan.name()))) {
          other++;
          continue;
        }
        long limit = orphan.video() ? VIDEO_LIMIT : PHOTO_LIMIT;
        if (orphan.size() <= 0 || orphan.size() > limit) {
          unverified++;
          continue;
        }
        MessageDigest digest = PhotosApi.sha1();
        long size = 0;
        try (InputStream in = scanner.openOriginal(orphan.uri().toString())) {
          byte[] buffer = new byte[128 * 1024];
          int n;
          while ((n = in.read(buffer)) != -1) {
            PhotosApi.checkStopped(stop);
            size += n;
            if (size > limit) throw new IOException("Too large");
            digest.update(buffer, 0, n);
          }
        }
        byte[] sha1 = digest.digest();
        if (size <= 0 || !ReclaimEngine.hex(sha1).equals(expected)) {
          unverified++; // Changed, truncated or planted: never uploaded, kept for the user.
          continue;
        }
        String key = api.find(sha1);
        if (key.isEmpty()) {
          long when =
              orphan.taken() > 0 ? orphan.taken() / 1000 : System.currentTimeMillis() / 1000;
          key =
              api.upload(
                  () -> scanner.openOriginal(orphan.uri().toString()),
                  ReclaimEngine.stagedName(orphan.name()),
                  size,
                  when,
                  sha1,
                  (a, b) -> {});
          uploaded++;
        }
        LibraryProtocol.Item item = library.lookup(key);
        if (item == null
            || item.trash()
            || !MessageDigest.isEqual(item.extra().sha1(), sha1)
            || (item.size() != null && item.size() != size)) {
          waiting++; // Not provably safe in Google Photos yet: keep the copy.
          continue;
        }
        if (remove(context, orphan)) removed++;
        else consent.add(orphan.uri());
      } catch (InterruptedIOException e) {
        throw e;
      } catch (ReclaimEngine.Later e) {
        waiting++;
      } catch (PhotosApi.ApiException e) {
        if (e.signIn) throw e;
        failed++;
      } catch (IOException | RuntimeException e) {
        failed++; // One bad file must not stop the others or the run.
      }
    }
    return new Result(uploaded, removed, other, unverified, waiting, failed, consent);
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
