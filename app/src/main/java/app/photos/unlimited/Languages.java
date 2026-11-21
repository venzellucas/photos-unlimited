package app.photos.unlimited;

import android.app.LocaleManager;
import android.content.Context;
import android.os.LocaleList;

/**
 * The app's language choice. It uses Android's per-app language setting, so the in-app menu and
 * Android's own "App languages" screen always show the same choice. An empty tag follows the
 * system; a system language the app doesn't have falls back to English.
 */
final class Languages {
  /** Language tags, in menu order. Keep in sync with res/xml/locales_config.xml. */
  static final String[] TAGS = {"", "en-US", "pt-BR", "es", "zh-Hans", "ja"};

  /** Each language's name in that language; index 0 is replaced by "System default". */
  static final String[] NATIVE_NAMES = {
    "", "English", "Português (Brasil)", "Español", "简体中文", "日本語"
  };

  private Languages() {}

  /** Index into TAGS of the current choice; 0 when following the system. */
  static int current(Context c) {
    LocaleList list = c.getSystemService(LocaleManager.class).getApplicationLocales();
    if (list.isEmpty()) return 0;
    String tag = list.get(0).toLanguageTag();
    for (int i = 1; i < TAGS.length; i++) if (TAGS[i].equalsIgnoreCase(tag)) return i;
    return 0;
  }

  static void choose(Context c, int index) {
    LocaleList list =
        index <= 0 ? LocaleList.getEmptyLocaleList() : LocaleList.forLanguageTags(TAGS[index]);
    c.getSystemService(LocaleManager.class).setApplicationLocales(list);
  }
}
