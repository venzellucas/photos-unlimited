package app.photos.unlimited;

/** Decides whether the Backup screen shows the update card. Plain Java, so JVM tests cover it. */
final class UpdateNotice {
  enum State {
    HIDDEN,
    BLOCKED,
    AVAILABLE
  }

  private UpdateNotice() {}

  /**
   * {@code unsafeCount} is the number of items only on this phone; -1 means not read yet, which
   * hides the card so the Download button can't appear before the count is known.
   */
  static State state(
      boolean enabled,
      String installedName,
      String latest,
      String dismissed,
      int unsafeCount,
      boolean freeUpRunning) {
    if (!enabled || unsafeCount < 0) return State.HIDDEN;
    UpdateCheck.Version installed = UpdateCheck.Version.parse(installedName);
    UpdateCheck.Version newest = UpdateCheck.Version.parse(latest);
    if (installed == null || newest == null || newest.compareTo(installed) <= 0) return State.HIDDEN;
    if (latest.equals(dismissed)) return State.HIDDEN;
    return unsafeCount > 0 || freeUpRunning ? State.BLOCKED : State.AVAILABLE;
  }

  /** The fixed-name asset of the latest release; never a URL from GitHub's answer. */
  static String downloadUrl() {
    return "https://github.com/" + UpdateCheck.REPO + "/releases/latest/download/Photos-Unlimited.apk";
  }

  static String releaseUrl(UpdateCheck.Version v) {
    return "https://github.com/" + UpdateCheck.REPO + "/releases/tag/v" + v;
  }
}
