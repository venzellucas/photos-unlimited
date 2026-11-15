package app.photos.unlimited;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.WindowCompat;
import com.google.android.material.color.DynamicColors;

/** Shared appearance settings leave existing backup preferences intact. */
public abstract class ThemedActivity extends AppCompatActivity {
  @Override
  public void onCreate(Bundle saved) {
    String appearance = getSharedPreferences("appearance", 0).getString("theme", "system");
    getDelegate()
        .setLocalNightMode(
            appearance.equals("dark")
                ? AppCompatDelegate.MODE_NIGHT_YES
                : appearance.equals("light")
                    ? AppCompatDelegate.MODE_NIGHT_NO
                    : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    super.onCreate(saved);
    if (getSharedPreferences("appearance", 0).getBoolean("dynamic", true))
      DynamicColors.applyToActivityIfAvailable(this);
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    boolean light = (getResources().getConfiguration().uiMode & 48) != 32;
    WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
        .setAppearanceLightStatusBars(light);
    WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
        .setAppearanceLightNavigationBars(light);
  }
}
