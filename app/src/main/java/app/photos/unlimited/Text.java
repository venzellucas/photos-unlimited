package app.photos.unlimited;

import android.content.Context;
import androidx.annotation.PluralsRes;
import androidx.annotation.StringRes;

/**
 * Translated text for code that has no Context of its own (engines, exceptions, stored messages).
 * Messages are looked up when they are written, in the app's current language.
 */
final class Text {
  // The application context lives as long as the process, so holding it here leaks nothing.
  @android.annotation.SuppressLint("StaticFieldLeak")
  private static volatile Context app;

  private Text() {}

  static void init(Context c) {
    if (app == null) app = c.getApplicationContext();
  }

  static String get(@StringRes int id, Object... args) {
    Context c = app;
    // JVM unit tests have no resources; they must never depend on the wording.
    if (c == null) return "#" + id;
    return args.length == 0 ? c.getString(id) : c.getString(id, args);
  }

  /**
   * Why something failed, safe to show and store: the translated message of a UserFacingException,
   * otherwise only the exception's type (a fixed English label, like the scan codes), never raw
   * transport text, which can hold private URLs.
   */
  static String reason(Throwable e) {
    if (e instanceof UserFacingException && e.getMessage() != null) return e.getMessage();
    return e.getClass().getSimpleName();
  }

  static String plural(@PluralsRes int id, int count, Object... args) {
    Context c = app;
    if (c == null) return "#" + id + ":" + count;
    return c.getResources().getQuantityString(id, count, args);
  }
}
