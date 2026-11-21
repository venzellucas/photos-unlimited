package app.photos.unlimited;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MediaScanner {
  public record Folder(String key, String label, int count) {}

  /** A phone file and what is needed to put it back exactly where it was. */
  public record Candidate(String uri, String name, String path, String mime, long taken) {}

  private final Context context;

  public MediaScanner(Context c) {
    context = c;
  }

  public boolean fullAccess() {
    return context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)
            == PackageManager.PERMISSION_GRANTED
        && context.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO)
            == PackageManager.PERMISSION_GRANTED;
  }

  public boolean originalAccess() {
    return context.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION)
        == PackageManager.PERMISSION_GRANTED;
  }

  public List<Folder> folders() throws IOException {
    return scan(null, "", Set.of(), new AtomicBoolean());
  }

  public List<Folder> scan(BackupDb db, String account, Set<String> selected, AtomicBoolean stop)
      throws IOException {
    Map<String, Integer> counts = new TreeMap<>();
    if (!fullAccess())
      throw new UserFacingException(Text.get(R.string.media_need_full_access));
    for (String volume : MediaStore.getExternalVolumeNames(context)) {
      for (boolean video : new boolean[] {false, true}) {
        Uri base =
            video
                ? MediaStore.Video.Media.getContentUri(volume)
                : MediaStore.Images.Media.getContentUri(volume);
        String[] columns = {
          MediaStore.MediaColumns._ID,
          MediaStore.MediaColumns.DISPLAY_NAME,
          MediaStore.MediaColumns.RELATIVE_PATH,
          MediaStore.MediaColumns.SIZE,
          MediaStore.MediaColumns.DATE_MODIFIED,
          MediaStore.MediaColumns.MIME_TYPE,
          MediaStore.MediaColumns.DATE_TAKEN
        };
        try (Cursor cursor =
            context
                .getContentResolver()
                .query(base, columns, "is_pending=0 AND is_trashed=0", null, null)) {
          if (cursor == null)
            throw new UserFacingException(Text.get(R.string.media_library_unavailable));
          while (cursor.moveToNext()) {
            PhotosApi.checkStopped(stop);
            String path = cursor.getString(2);
            if (path == null) path = "";
            // Free up's staging copies are not the user's media and never backed up.
            if (path.equals(ReclaimService.STAGING_PATH)) continue;
            String folder = volume + ":" + path;
            counts.merge(folder, 1, Integer::sum);
            long size = cursor.getLong(3), modified = cursor.getLong(4);
            // Give cameras/editors time to finish publishing a stable file.
            if (db != null
                && selected.contains(folder)
                && size > 0
                && modified < System.currentTimeMillis() / 1000 - 10) {
              String name = cursor.getString(1), mime = cursor.getString(5);
              if (name == null) name = "media-" + cursor.getLong(0);
              if (mime == null) mime = video ? "video/mp4" : "image/jpeg";
              db.discover(
                  account,
                  ContentUris.withAppendedId(base, cursor.getLong(0)).toString(),
                  name,
                  folder,
                  mime,
                  size,
                  modified,
                  cursor.getLong(6));
            }
          }
        } catch (SecurityException e) {
          throw new UserFacingException(Text.get(R.string.media_permission_changed));
        }
      }
    }
    List<Folder> result = new ArrayList<>();
    counts.forEach(
        (key, count) ->
            result.add(
                new Folder(key, key.substring(key.indexOf(':') + 1).replaceAll("/$", ""), count)));
    return result;
  }

  /**
   * Phone media that could be a library item's original: same name or same size, name first.
   * Google reports a reduced copy's size, not the original's, so a name match alone counts.
   * Empty without full media and media-location access, since only complete originals can match.
   */
  public List<Candidate> candidates(String name, long size) {
    List<Candidate> named = new ArrayList<>(), sized = new ArrayList<>();
    if (!fullAccess() || !originalAccess()) return named;
    for (String volume : MediaStore.getExternalVolumeNames(context)) {
      for (boolean video : new boolean[] {false, true}) {
        Uri base =
            video
                ? MediaStore.Video.Media.getContentUri(volume)
                : MediaStore.Images.Media.getContentUri(volume);
        try (Cursor c =
            context
                .getContentResolver()
                .query(
                    base,
                    new String[] {
                      MediaStore.MediaColumns._ID,
                      MediaStore.MediaColumns.DISPLAY_NAME,
                      MediaStore.MediaColumns.RELATIVE_PATH,
                      MediaStore.MediaColumns.MIME_TYPE,
                      MediaStore.MediaColumns.DATE_TAKEN
                    },
                    "is_pending=0 AND is_trashed=0 AND (_display_name=? OR _size=?)",
                    new String[] {name, Long.toString(size)},
                    null)) {
          if (c == null) continue;
          while (c.moveToNext()) {
            if (ReclaimService.STAGING_PATH.equals(c.getString(2))) continue;
            String uri = ContentUris.withAppendedId(base, c.getLong(0)).toString();
            String found = c.getString(1) == null ? "" : c.getString(1);
            Candidate candidate =
                new Candidate(
                    uri,
                    found,
                    c.getString(2) == null ? "" : c.getString(2),
                    c.getString(3) == null ? (video ? "video/mp4" : "image/jpeg") : c.getString(3),
                    c.isNull(4) ? 0 : c.getLong(4));
            (name.equals(found) ? named : sized).add(candidate);
          }
        } catch (RuntimeException e) {
          // A volume that disappears mid-query just contributes no candidates.
        }
      }
    }
    named.addAll(sized);
    return named.size() > 10 ? named.subList(0, 10) : named;
  }

  /** Whether a media row still exists (it disappears when another app deletes the file). */
  public boolean exists(String address) throws IOException {
    try (Cursor c =
        context
            .getContentResolver()
            .query(Uri.parse(address), new String[] {MediaStore.MediaColumns._ID}, null, null)) {
      if (c == null) throw new UserFacingException(Text.get(R.string.media_library_unavailable));
      return c.moveToFirst();
    } catch (RuntimeException e) {
      // Unknown is not "deleted": a later check decides, so no duplicate gets restored.
      throw new UserFacingException(Text.get(R.string.media_phone_copy_check_failed));
    }
  }

  /**
   * Writes a file back into the shared media library at its old folder and name. Returns the new
   * content URI. The bytes are copied unchanged, so embedded metadata is the original's.
   */
  public String restore(Candidate where, File source) throws IOException {
    boolean video = where.mime().startsWith("video/");
    Uri collection =
        video
            ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            : MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
    ContentValues values = new ContentValues();
    values.put(MediaStore.MediaColumns.DISPLAY_NAME, where.name());
    values.put(MediaStore.MediaColumns.MIME_TYPE, where.mime());
    if (!where.path().isEmpty()) values.put(MediaStore.MediaColumns.RELATIVE_PATH, where.path());
    if (where.taken() > 0) values.put(MediaStore.MediaColumns.DATE_TAKEN, where.taken());
    values.put(MediaStore.MediaColumns.IS_PENDING, 1);
    Uri uri;
    try {
      uri = context.getContentResolver().insert(collection, values);
    } catch (IllegalArgumentException e) {
      // Some folders only accept certain media types; fall back to the standard one.
      values.put(
          MediaStore.MediaColumns.RELATIVE_PATH,
          (video ? "Movies" : "Pictures") + "/Photos Unlimited/");
      uri = context.getContentResolver().insert(collection, values);
    }
    if (uri == null) throw new UserFacingException(Text.get(R.string.media_restore_failed));
    try (InputStream in = new FileInputStream(source);
        OutputStream out = context.getContentResolver().openOutputStream(uri)) {
      if (out == null) throw new UserFacingException(Text.get(R.string.media_restore_failed));
      in.transferTo(out);
    } catch (IOException | RuntimeException e) {
      context.getContentResolver().delete(uri, null, null);
      throw e instanceof IOException io ? io : new IOException(e);
    }
    values.clear();
    values.put(MediaStore.MediaColumns.IS_PENDING, 0);
    context.getContentResolver().update(uri, values, null, null);
    return uri.toString();
  }

  public InputStream openOriginal(String address) throws IOException {
    if (!originalAccess())
      throw new UserFacingException(Text.get(R.string.media_need_location));
    try {
      InputStream stream =
          context
              .getContentResolver()
              .openInputStream(MediaStore.setRequireOriginal(Uri.parse(address)));
      if (stream == null) throw new FileNotFoundException("Local file is unavailable");
      return stream;
    } catch (SecurityException | UnsupportedOperationException e) {
      throw new UserFacingException(Text.get(R.string.media_original_unavailable));
    }
  }
}
