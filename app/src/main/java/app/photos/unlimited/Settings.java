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
    prefs.edit().putBoolean("enabled", value).commit();
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

  public long lastRun() {
    return prefs.getLong("lastRun", 0);
  }
}
