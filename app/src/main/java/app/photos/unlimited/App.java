package app.photos.unlimited;

import android.app.Application;

public final class App extends Application {
  @Override
  public void onCreate() {
    super.onCreate();
    Text.init(this);
  }
}
