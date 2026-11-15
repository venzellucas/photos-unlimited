package app.photos.unlimited;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import java.io.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Small account-scoped cache. A missing picture must never block backup or trigger sign-in. */
final class ProfileStore {
  private static final Object LOCK = new Object();
  private final Context context;

  ProfileStore(Context context) {
    this.context = context.getApplicationContext();
  }

  private SharedPreferences prefs() {
    return context.getSharedPreferences("profile", 0);
  }

  private File file() {
    return new File(context.getNoBackupFilesDir(), "google-profile.png");
  }

  String name(String account) {
    synchronized (LOCK) {
      return account.equals(prefs().getString("account", "")) ? prefs().getString("name", "") : "";
    }
  }

  Bitmap cached(String account) {
    synchronized (LOCK) {
      if (account.isEmpty() || !account.equals(prefs().getString("account", ""))) return null;
      return BitmapFactory.decodeFile(file().getPath());
    }
  }

  static void clear(Context context) {
    synchronized (LOCK) {
      ProfileStore store = new ProfileStore(context);
      store.file().delete();
      store.prefs().edit().clear().commit();
    }
  }

  void refresh(String email, String credential, AtomicBoolean stop, boolean force)
      throws IOException {
    long now = System.currentTimeMillis();
    synchronized (LOCK) {
      if (!email.equals(new AccountStore(context).email())) return;
      if (!email.equals(prefs().getString("account", ""))) clear(context);
      long retry = prefs().getLong("retryAfter", 0);
      if (!force && retry > now && retry - now <= 24 * 60 * 60 * 1000L) return;
      prefs()
          .edit()
          .putString("account", email)
          .putLong("retryAfter", now + 60 * 60 * 1000L)
          .commit();
    }
    ProfileApi.Network transport = new ProfileApi.Network();
    ProfileApi.Profile profile = ProfileApi.fetch(credential, transport, stop);
    Bitmap bitmap = null;
    if (!profile.picture().isEmpty()) {
      byte[] data = transport.call(profile.picture(), "GET", Map.of(), null, 0, stop).bytes();
      bitmap = decode(data);
    }
    PhotosApi.checkStopped(stop);
    synchronized (LOCK) {
      if (!email.equals(new AccountStore(context).email()) || stop.get()) return;
      save(email, profile.name(), bitmap);
      prefs().edit().putLong("retryAfter", now + 24 * 60 * 60 * 1000L).commit();
    }
  }

  static Bitmap decode(byte[] data) throws IOException {
    BitmapFactory.Options bounds = new BitmapFactory.Options();
    bounds.inJustDecodeBounds = true;
    BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
    if (bounds.outWidth <= 0
        || bounds.outHeight <= 0
        || bounds.outWidth > 4096
        || bounds.outHeight > 4096) throw new IOException("Invalid profile image dimensions");
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inSampleSize = 1;
    while (Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 512)
      options.inSampleSize *= 2;
    Bitmap decoded = BitmapFactory.decodeByteArray(data, 0, data.length, options);
    if (decoded == null) throw new IOException("Invalid profile image");
    int edge = Math.min(decoded.getWidth(), decoded.getHeight());
    Bitmap square =
        Bitmap.createBitmap(
            decoded, (decoded.getWidth() - edge) / 2, (decoded.getHeight() - edge) / 2, edge, edge);
    return Bitmap.createScaledBitmap(square, 128, 128, true);
  }

  void save(String email, String name, Bitmap bitmap) throws IOException {
    synchronized (LOCK) {
      if (!email.equals(new AccountStore(context).email())) return;
      if (bitmap == null) file().delete();
      else {
        android.util.AtomicFile target = new android.util.AtomicFile(file());
        FileOutputStream out = null;
        try {
          out = target.startWrite();
          if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
            throw new IOException("Could not cache profile image");
          target.finishWrite(out);
        } catch (IOException e) {
          target.failWrite(out);
          throw e;
        }
      }
      prefs().edit().putString("account", email).putString("name", name).commit();
    }
  }

  static Drawable icon(
      Context context, Bitmap bitmap, String email, int background, int foreground) {
    if (bitmap == null && !email.isEmpty()) {
      bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(bitmap);
      canvas.drawColor(background);
      Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
      paint.setColor(foreground);
      paint.setTextSize(64);
      paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
      paint.setTextAlign(Paint.Align.CENTER);
      String initial =
          email.substring(0, email.offsetByCodePoints(0, 1)).toUpperCase(java.util.Locale.ROOT);
      canvas.drawText(initial, 64, 64 - (paint.ascent() + paint.descent()) / 2, paint);
    }
    if (bitmap == null)
      return androidx.appcompat.content.res.AppCompatResources.getDrawable(
          context, R.drawable.ic_account);
    var rounded = RoundedBitmapDrawableFactory.create(context.getResources(), bitmap);
    rounded.setCircular(true);
    return rounded;
  }
}
