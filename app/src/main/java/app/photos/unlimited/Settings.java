package app.photos.unlimited;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

public final class Settings {
  private final SharedPreferences prefs;

  public Settings(Context c) {
    prefs = c.getSharedPreferences("settings", 0);
  }

  public boolean enabled() {
    return prefs.getBoolean("enabled", false);
  }

  public boolean wifi() {
    return prefs.getBoolean("wifi", true);
  }

  public boolean charging() {
    return prefs.getBoolean("charging", false);
  }

  public void enabled(boolean value) {
    SharedPreferences.Editor edit = prefs.edit().putBoolean("enabled", value);
    if (value && !enabled()) edit.putLong("enabledSince", System.currentTimeMillis() / 1000);
    edit.commit();
  }

  /** Seconds since epoch when automatic backup was last switched on; 0 if unknown. */
  public long enabledSince() {
    return prefs.getLong("enabledSince", 0);
  }

  /** When the user last said Google Photos backup is off (millis); 0 = never. */
  public long photosBackupOffConfirmed() {
    return prefs.getLong("photosOff", 0);
  }

  public void confirmPhotosBackupOff() {
    prefs.edit().putLong("photosOff", System.currentTimeMillis()).apply();
  }

  /**
   * Evidence that another uploader (normally Google Photos backup) is active: a file created after
   * our backup was enabled was already in the library before we uploaded it.
   */
  public void otherUploader(String name) {
    prefs
        .edit()
        .putLong("otherUploaderAt", System.currentTimeMillis())
        .putInt("otherUploaderCount", prefs.getInt("otherUploaderCount", 0) + 1)
        .putString("otherUploaderName", name)
        .apply();
  }

  public long otherUploaderAt() {
    return prefs.getLong("otherUploaderAt", 0);
  }

  public int otherUploaderCount() {
    return prefs.getInt("otherUploaderCount", 0);
  }

  public String otherUploaderName() {
    return prefs.getString("otherUploaderName", "");
  }

  public void clearOtherUploader() {
    prefs
        .edit()
        .remove("otherUploaderAt")
        .remove("otherUploaderCount")
        .remove("otherUploaderName")
        .apply();
  }

  public void wifi(boolean value) {
    prefs.edit().putBoolean("wifi", value).apply();
  }

  public void charging(boolean value) {
    prefs.edit().putBoolean("charging", value).apply();
  }

  public Set<String> folders() {
    return new HashSet<>(prefs.getStringSet("folders", Set.of()));
  }

  public void folders(Set<String> values) {
    prefs.edit().putStringSet("folders", new HashSet<>(values)).commit();
  }

  public String failure() {
    return prefs.getString("failure", "");
  }

  public void failure(String kind) {
    prefs.edit().putString("failure", kind).apply();
  }

  public String status() {
    return prefs.getString("status", "Choose an account and the folders you want to back up.");
  }

  public void status(String status) {
    prefs.edit().putString("status", status).apply();
  }

  public void lastRun(long now) {
    prefs.edit().putLong("lastRun", now).apply();
  }

  /** Staged copies from an earlier install that are safe to remove once the user agrees. */
  public Set<String> recoveryCleanup() {
    return new HashSet<>(prefs.getStringSet("recoveryCleanup", Set.of()));
  }

  public void recoveryCleanup(Set<String> uris) {
    prefs.edit().putStringSet("recoveryCleanup", new HashSet<>(uris)).apply();
  }

  /** Ownerless staged files (from an earlier install) the user approved for upload. */
  public Set<String> recoveryApproved() {
    return new HashSet<>(prefs.getStringSet("recoveryApproved", Set.of()));
  }

  public void recoveryApproved(Set<String> uris) {
    prefs.edit().putStringSet("recoveryApproved", new HashSet<>(uris)).apply();
  }

  /** Staged files tagged for a different Google account than the connected one. */
  public int recoveryOtherAccount() {
    return prefs.getInt("recoveryOther", 0);
  }

  public void recoveryOtherAccount(int count) {
    prefs.edit().putInt("recoveryOther", count).apply();
  }

  public long lastRun() {
    return prefs.getLong("lastRun", 0);
  }
}
