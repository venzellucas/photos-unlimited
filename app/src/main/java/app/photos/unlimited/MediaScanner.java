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
      throw new IOException(
          "Allow access to all photos and videos to discover and back up folders.");
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
          if (cursor == null) throw new IOException("Android's media library is unavailable.");
          while (cursor.moveToNext()) {
            PhotosApi.checkStopped(stop);
            String path = cursor.getString(2);
            if (path == null) path = "";
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
          throw new IOException("Media permission changed. Allow access to all photos and videos.");
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

  public InputStream openOriginal(String address) throws IOException {
    if (!originalAccess())
      throw new IOException("Allow media location access to preserve the complete original file.");
    try {
      InputStream stream =
          context
              .getContentResolver()
              .openInputStream(MediaStore.setRequireOriginal(Uri.parse(address)));
      if (stream == null) throw new FileNotFoundException("Local file is unavailable");
      return stream;
    } catch (SecurityException | UnsupportedOperationException e) {
      throw new IOException(
          "Original media is unavailable. Check photo and media location permissions.");
    }
  }
}
