package app.photos.unlimited;

import android.Manifest;
import android.content.*;
import android.content.res.ColorStateList;
import android.net.*;
import android.os.*;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.view.*;
import android.widget.*;
import androidx.activity.OnBackPressedCallback;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import java.util.*;
import java.util.concurrent.*;

/** Native Material screens; the existing credential, upload and scheduler paths are retained. */
public final class MainActivity extends ThemedActivity {
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  private final ExecutorService profileIo = Executors.newSingleThreadExecutor();
  private final java.util.concurrent.atomic.AtomicBoolean profileStop =
      new java.util.concurrent.atomic.AtomicBoolean();
  private ImageButton accountButton;
  private boolean profileLoading;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private Settings settings;
  private AccountStore account;
  private Ui ui;
  private LinearLayout page, historyRows, storageRows;
  private ScrollView scroll;
  private BottomNavigationView navigation;
  private TextView status,
      detail,
      counts,
      progressLabel,
      storageTotal,
      storageSummary,
      storageStatus,
      storagePage;
  private MaterialButton primary, folders, scanButton, previous, next;
  private LinearProgressIndicator progress, scanProgress;
  private int screen = 1, generation, quotaFilter = 1, mediaFilter, storageOffset;
  private boolean loading;
  private String visibleAccount = "";
  private String renderedHistory = "", renderedStorage = "";
  private final Runnable ticker =
      new Runnable() {
        public void run() {
          refresh();
          handler.postDelayed(this, 1500);
        }
      };

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    ui = new Ui(this);
    settings = new Settings(this);
    account = new AccountStore(this);
    screen = saved == null ? getIntent().getIntExtra("screen", 1) : saved.getInt("screen", 1);
    if (screen < 1 || screen > 4) screen = 1;
    if (saved != null) {
      quotaFilter = saved.getInt("quotaFilter", 1);
      mediaFilter = saved.getInt("mediaFilter");
      storageOffset = saved.getInt("offset");
    }
    LinearLayout root = ui.column();
    root.setBackgroundColor(ui.surface());
    MaterialToolbar toolbar = new MaterialToolbar(this);
    toolbar.setTitle("Photos Unlimited");
    toolbar.setTitleTextColor(ui.ink());
    toolbar.setLogo(R.drawable.ic_backup);
    toolbar.setContentInsetStartWithNavigation(ui.dp(16));
    accountButton = new ImageButton(this, null, android.R.attr.actionButtonStyle);
    accountButton.setLayoutParams(new ViewGroup.LayoutParams(ui.dp(48), ui.dp(48)));
    accountButton.setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8));
    accountButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
    accountButton.setOnClickListener(v -> manageAccount());
    accountButton.setId(R.id.menu_account);
    var accountMenu = toolbar.getMenu().add(0, R.id.menu_account, 0, "Google account");
    accountMenu.setActionView(accountButton);
    accountMenu.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    showProfile(account.email(), null);
    toolbar.setOnMenuItemClickListener(
        item -> {
          manageAccount();
          return true;
        });
    root.addView(toolbar);
    scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setClipToPadding(false);
    page = ui.column();
    page.setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(24));
    scroll.addView(page);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    navigation = new BottomNavigationView(this);
    // At large font scales, give the selected label room and retain accessible
    // names for every destination instead of squeezing four labels into one row.
    navigation.setLabelVisibilityMode(
        getResources().getConfiguration().fontScale > 1.3f
            ? NavigationBarView.LABEL_VISIBILITY_SELECTED
            : NavigationBarView.LABEL_VISIBILITY_LABELED);
    navigation.setItemHorizontalTranslationEnabled(true);
    navigation.getMenu().add(0, R.id.nav_backup, 0, "Backup").setIcon(R.drawable.ic_backup);
    navigation.getMenu().add(0, R.id.nav_activity, 1, "Activity").setIcon(R.drawable.ic_activity);
    navigation.getMenu().add(0, R.id.nav_storage, 2, "Storage").setIcon(R.drawable.ic_storage);
    navigation.getMenu().add(0, R.id.nav_settings, 3, "Settings").setIcon(R.drawable.ic_settings);
    selectScreen(screen);
    navigation.setOnItemSelectedListener(
        item -> {
          for (int i = 0; i < 4; i++)
            if (navigation.getMenu().getItem(i).getItemId() == item.getItemId()) screen = i + 1;
          draw();
          return true;
        });
    root.addView(navigation, new LinearLayout.LayoutParams(-1, -2));
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          android.graphics.Insets bars =
              insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
          v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
          return WindowInsets.CONSUMED;
        });
    setContentView(root);
    draw();
    Scheduler.ensure(this);
    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                if (screen != 1) selectScreen(1);
                else finish();
              }
            });
    if (saved != null) {
      int y = saved.getInt("scrollY");
      scroll.post(() -> scroll.scrollTo(0, y));
    }
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    selectScreen(intent.getIntExtra("screen", 1));
  }

  @Override
  public void onResume() {
    super.onResume();
    if (!visibleAccount.equals(account.email())) {
      visibleAccount = account.email();
      showProfile(visibleAccount, null);
      if (screen == 4) draw();
    }
    refreshProfile(false);
    handler.removeCallbacks(ticker);
    handler.post(ticker);
  }

  @Override
  public void onPause() {
    handler.removeCallbacks(ticker);
    super.onPause();
  }

  @Override
  public void onDestroy() {
    handler.removeCallbacks(ticker);
    io.shutdownNow();
    profileStop.set(true);
    profileIo.shutdownNow();
    super.onDestroy();
  }

  @Override
  public void onSaveInstanceState(Bundle saved) {
    saved.putInt("screen", screen);
    saved.putInt("quotaFilter", quotaFilter);
    saved.putInt("mediaFilter", mediaFilter);
    saved.putInt("offset", storageOffset);
    saved.putInt("scrollY", scroll.getScrollY());
    super.onSaveInstanceState(saved);
  }

  private void heading(String title, String subtitle) {
    TextView h = ui.text(title, 28, false);
    h.setAccessibilityHeading(true);
    ui.space(h, 0, 4);
    page.addView(h);
    TextView sub = ui.supporting(subtitle);
    ui.space(sub, 0, 24);
    page.addView(sub);
  }

  private void section(String label) {
    TextView h = ui.supporting(label);
    h.setAccessibilityHeading(true);
    ui.space(h, 8, 12);
    page.addView(h);
  }

  private void draw() {
    generation++;
    page.removeAllViews();
    renderedHistory = "";
    renderedStorage = "";
    status =
        detail =
            counts =
                progressLabel = storageTotal = storageSummary = storageStatus = storagePage = null;
    primary = folders = scanButton = previous = next = null;
    progress = scanProgress = null;
    historyRows = storageRows = null;
    switch (screen) {
      case 2 -> drawActivity();
      case 3 -> drawStorage();
      case 4 -> drawSettings();
      default -> drawBackup();
    }
    scroll.scrollTo(0, 0);
    refresh();
  }

  private void drawBackup() {
    heading("Backup", "Original photos and videos.");
    LinearLayout hero = ui.card(page, true);
    ImageView cloud = new ImageView(this);
    cloud.setImageResource(R.drawable.ic_backup);
    cloud.setImageTintList(
        ColorStateList.valueOf(
            ui.color(com.google.android.material.R.attr.colorOnPrimaryContainer)));
    hero.addView(cloud, new LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)));
    status = ui.text("Checking backup status…", 24, false);
    status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    status.setTextColor(ui.color(com.google.android.material.R.attr.colorOnPrimaryContainer));
    ui.space(status, 16, 8);
    hero.addView(status);
    detail = ui.supporting("");
    detail.setTextColor(status.getCurrentTextColor());
    hero.addView(detail);
    progress = new LinearProgressIndicator(this);
    progress.setMax(1000);
    ui.space(progress, 20, 8);
    hero.addView(progress);
    progress.setVisibility(View.GONE);
    progressLabel = ui.supporting("");
    progressLabel.setTextColor(status.getCurrentTextColor());
    hero.addView(progressLabel);
    primary = ui.button("Back up now", R.drawable.ic_backup, true);
    ui.space(primary, 20, 0);
    hero.addView(primary);
    primary.setOnClickListener(v -> startBackup());
    counts = ui.supporting("Loading device backup records…");
    ui.space(counts, 4, 20);
    page.addView(counts);
    LinearLayout group = ui.card(page, false);
    folders = ui.row(group, "Backup folders", R.drawable.ic_folder, this::chooseFolders);
    ui.row(group, "Recent activity", R.drawable.ic_activity, () -> selectScreen(2));
    MaterialButton photos = ui.button("Open Google Photos", R.drawable.ic_open, false);
    page.addView(photos);
    photos.setOnClickListener(v -> openPhotos());
    TextView note = ui.supporting("Original files stay on your phone.");
    ui.space(note, 12, 0);
    page.addView(note);
  }

  private void drawActivity() {
    heading("Activity", "Recent backup records from this device.");
    MaterialButton retry = ui.button("Retry unsuccessful items", R.drawable.ic_refresh, false);
    page.addView(retry);
    retry.setOnClickListener(
        v -> {
          String email = account.email();
          io.execute(
              () -> {
                try (BackupDb db = new BackupDb(this)) {
                  db.retry(email);
                }
                runOnUiThread(
                    () -> {
                      if (alive() && account.email().equals(email)) startBackup();
                    });
              });
        });
    historyRows = ui.column();
    page.addView(historyRows);
  }

  private void drawStorage() {
    heading("Storage", "Google Photos inventory · Preview");
    LinearLayout card = ui.card(page, true);
    storageTotal = ui.text("No scan yet", 28, false);
    storageTotal.setTextColor(ui.color(com.google.android.material.R.attr.colorOnPrimaryContainer));
    card.addView(storageTotal);
    TextView caption = ui.supporting("Reported storage used by scanned items");
    caption.setTextColor(storageTotal.getCurrentTextColor());
    ui.space(caption, 4, 12);
    card.addView(caption);
    storageSummary = ui.supporting("");
    storageSummary.setTextColor(storageTotal.getCurrentTextColor());
    card.addView(storageSummary);
    storageStatus = ui.supporting("Read metadata to find photos and videos using storage.");
    ui.space(storageStatus, 12, 8);
    card.addView(storageStatus);
    scanProgress = new LinearProgressIndicator(this);
    scanProgress.setIndeterminate(true);
    card.addView(scanProgress);
    scanProgress.setVisibility(View.GONE);
    scanButton = ui.button("Scan Google Photos", R.drawable.ic_storage, true);
    ui.space(scanButton, 16, 0);
    card.addView(scanButton);
    scanButton.setOnClickListener(v -> startScan());
    TextView note =
        ui.supporting(
            "Read-only scan. Google may omit items or storage details. Unknown is not zero. This"
                + " total excludes Drive and Gmail and may differ from your account total.");
    ui.space(note, 0, 12);
    page.addView(note);
    LinearLayout filters = ui.card(page, false);
    picker(
        filters,
        "Storage status",
        new String[] {"All scanned items", "Using storage", "Storage unknown"},
        quotaFilter,
        value -> {
          quotaFilter = value;
          storageOffset = 0;
          renderedStorage = "";
          refresh();
        });
    picker(
        filters,
        "Media type",
        new String[] {"All media", "Photos", "Videos"},
        mediaFilter,
        value -> {
          mediaFilter = value;
          storageOffset = 0;
          renderedStorage = "";
          refresh();
        });
    ui.row(filters, "Start a fresh scan", R.drawable.ic_refresh, this::restartScan);
    storagePage = ui.supporting("Largest reported storage first");
    page.addView(storagePage);
    storageRows = ui.column();
    page.addView(storageRows);
    LinearLayout pages = new LinearLayout(this);
    previous = ui.button("Previous", R.drawable.ic_back, false);
    next = ui.button("Next", 0, false);
    pages.addView(previous, new LinearLayout.LayoutParams(0, -2, 1));
    pages.addView(next, new LinearLayout.LayoutParams(0, -2, 1));
    page.addView(pages);
    previous.setOnClickListener(
        v -> {
          storageOffset = Math.max(0, storageOffset - 50);
          renderedStorage = "";
          refresh();
          scroll.smoothScrollTo(0, 0);
        });
    next.setOnClickListener(
        v -> {
          storageOffset += 50;
          renderedStorage = "";
          refresh();
          scroll.smoothScrollTo(0, 0);
        });
  }

  private void drawSettings() {
    heading("Settings", "Make backup fit your day.");
    section("Backup");
    LinearLayout backup = ui.card(page, false);
    ui.toggle(
        backup,
        "Automatic backup",
        settings.enabled(),
        (b, on) -> {
          if (on && !ready()) {
            b.setChecked(false);
            return;
          }
          settings.enabled(on);
          Scheduler.configure(this);
          if (on) Scheduler.continuation(this);
          else {
            BackupEngine.cancel();
            stopService(new Intent(this, BackupService.class));
          }
        });
    ui.toggle(
        backup,
        "Unmetered networks only\nUsually Wi-Fi",
        settings.wifi(),
        (b, on) -> {
          settings.wifi(on);
          Scheduler.configure(this);
        });
    ui.toggle(
        backup,
        "Only while charging",
        settings.charging(),
        (b, on) -> {
          settings.charging(on);
          Scheduler.configure(this);
        });
    section("Appearance");
    LinearLayout appearance = ui.card(page, false);
    SharedPreferences prefs = getSharedPreferences("appearance", 0);
    String theme = prefs.getString("theme", "system");
    int selected = theme.equals("dark") ? 2 : theme.equals("light") ? 1 : 0;
    picker(
        appearance,
        "Theme",
        new String[] {"Follow system", "Light", "Dark"},
        selected,
        value -> {
          prefs.edit().putString("theme", new String[] {"system", "light", "dark"}[value]).apply();
          recreate();
        });
    ui.toggle(
        appearance,
        "Wallpaper colors",
        prefs.getBoolean("dynamic", true),
        (b, on) -> {
          prefs.edit().putBoolean("dynamic", on).apply();
          recreate();
        });
    section("Account and app");
    LinearLayout app = ui.card(page, false);
    ui.row(
        app,
        account.email().isEmpty() ? "Connect Google account" : "Google account\n" + account.email(),
        R.drawable.ic_account,
        this::manageAccount);
    ui.row(app, "Photo and video permissions", R.drawable.ic_photo, this::permissions);
    ui.row(app, "About and open-source credits", R.drawable.ic_info, this::about);
    ui.row(
        app,
        "Advanced account diagnostic",
        R.drawable.ic_settings,
        () -> startActivity(new Intent(this, AccountProbeActivity.class)));
  }

  private void picker(
      LinearLayout parent,
      String label,
      String[] values,
      int selected,
      java.util.function.IntConsumer changed) {
    int[] current = {selected};
    TextView caption = ui.supporting(label);
    parent.addView(caption);
    MaterialButton button = ui.button(values[selected], 0, false);
    button.setContentDescription(label + ": " + values[selected]);
    ui.space(button, 0, 8);
    parent.addView(button);
    button.setOnClickListener(
        v ->
            new MaterialAlertDialogBuilder(this)
                .setTitle(label)
                .setSingleChoiceItems(
                    values,
                    current[0],
                    (dialog, which) -> {
                      dialog.dismiss();
                      if (which != current[0]) {
                        current[0] = which;
                        button.setText(values[which]);
                        button.setContentDescription(label + ": " + values[which]);
                        changed.accept(which);
                      }
                    })
                .setNegativeButton("Cancel", null)
                .show());
  }

  private void selectScreen(int destination) {
    navigation.setSelectedItemId(
        navigation.getMenu().getItem(Math.max(1, Math.min(4, destination)) - 1).getItemId());
  }

  private boolean alive() {
    return !isFinishing() && !isDestroyed();
  }

  private void refresh() {
    if (!alive() || settings == null || loading) return;
    if (screen == 4) return;
    loading = true;
    int revision = generation,
        view = screen,
        q = quotaFilter,
        m = mediaFilter,
        offset = storageOffset;
    String email = account.email();
    Set<String> selected = settings.folders();
    io.execute(
        () -> {
          Map<String, Integer> c = Map.of();
          List<String> history = List.of();
          InventoryDb.Summary summary = null;
          List<LibraryProtocol.Item> items = List.of();
          try {
            if (view == 3) {
              try (InventoryDb db = new InventoryDb(this)) {
                summary = db.summary(email);
                items = db.list(email, q, m, offset);
              }
            } else
              try (BackupDb db = new BackupDb(this)) {
                c = db.counts(email, selected);
                if (view == 2) history = db.recent(email);
              }
            Map<String, Integer> countsResult = c;
            List<String> recent = history;
            InventoryDb.Summary result = summary;
            List<LibraryProtocol.Item> rows = items;
            runOnUiThread(
                () -> {
                  loading = false;
                  if (!alive()) return;
                  if (revision != generation
                      || !email.equals(account.email())
                      || q != quotaFilter
                      || m != mediaFilter
                      || offset != storageOffset) {
                    refresh();
                    return;
                  }
                  if (view == 1) updateBackup(countsResult, selected);
                  else if (view == 2) updateActivity(recent);
                  else if (view == 3) updateStorage(result, rows);
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  loading = false;
                  if (alive() && revision == generation) {
                    if (status != null) status.setText("Couldn’t read backup records");
                    if (storageStatus != null)
                      storageStatus.setText("Couldn’t read the local scan cache. Try again.");
                  }
                });
          }
        });
  }

  private void updateBackup(Map<String, Integer> c, Set<String> selected) {
    int uploaded = c.getOrDefault("done", 0), existing = c.getOrDefault("existing", 0);
    int
        pending =
            c.getOrDefault("ready", 0) + c.getOrDefault("running", 0) + c.getOrDefault("error", 0),
        failed = c.getOrDefault("blocked", 0) + c.getOrDefault("error", 0);
    counts.setText(
        uploaded
            + " uploaded · "
            + existing
            + " already in library\n"
            + pending
            + " waiting"
            + (failed > 0 ? " · " + failed + " need attention" : ""));
    List<String> names = new ArrayList<>();
    for (String f : selected) names.add(f.substring(f.indexOf(':') + 1).replaceAll("/$", ""));
    Collections.sort(names);
    folders.setText(
        "Backup folders\n" + (names.isEmpty() ? "Choose folders" : String.join(", ", names)));
    String title, description, action;
    int icon = R.drawable.ic_backup;
    MediaScanner scanner = new MediaScanner(this);
    BackupEngine.ProgressState live = BackupEngine.progress();
    boolean busy = BackupEngine.busy();
    progress.setVisibility(View.GONE);
    progressLabel.setText("");
    if (account.email().isEmpty()) {
      title = "Connect your account";
      description = "Choose the Google Photos library for your backup.";
      action = "Connect Google account";
    } else if (!scanner.fullAccess() || !scanner.originalAccess()) {
      title = "Allow media access";
      description = "Photo, video and media location access preserve complete originals.";
      action = "Review permissions";
    } else if (selected.isEmpty()) {
      title = "Choose your folders";
      description = "Select the photos and videos you want to back up.";
      action = "Choose folders";
    } else if (busy) {
      title =
          settings.enabled()
              ? (live.uploading() ? "Backing up" : "Checking your folders")
              : "Pausing backup…";
      description =
          account.email().equals(live.account()) ? live.detail() : "Preparing your backup…";
      action = "Pause backup";
      icon = R.drawable.ic_pause;
      if (live.uploading() && live.total() > 0 && account.email().equals(live.account())) {
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(false);
        progress.setProgressCompat(
            (int) Math.min(1000, 1000.0 * live.sent() / live.total()), false);
        progressLabel.setText(bytes(live.sent()) + " / " + bytes(live.total()) + " · Current file");
      }
    } else if (!settings.failure().isEmpty()) {
      title = "Backup needs attention";
      description = settings.status();
      action =
          settings.failure().equals("auth")
              ? "Reconnect account"
              : settings.failure().equals("media") ? "Review permissions" : "Back up now";
      icon = R.drawable.ic_warning;
    } else if (!settings.enabled()) {
      title = "Backup is paused";
      description = settings.status();
      action = "Back up now";
      icon = R.drawable.ic_play;
    } else if (!BackupEngine.allowed(this, settings)) {
      title = "Waiting to back up";
      description = waitingReason();
      action = "Backup settings";
      icon = R.drawable.ic_settings;
    } else if (failed > 0) {
      title = "Some items need attention";
      description = "Open Activity to review unsuccessful uploads and retries.";
      action = "Review activity";
      icon = R.drawable.ic_warning;
    } else if (pending > 0 || settings.lastRun() == 0) {
      title = "Backup scheduled";
      description =
          "Android will run background work when conditions allow. You can also start now.";
      action = "Back up now";
    } else {
      title = "You’re up to date";
      description =
          "Selected folders are backed up. Last checked "
              + DateUtils.getRelativeTimeSpanString(settings.lastRun())
              + ".";
      action = "Back up now";
      icon = R.drawable.ic_check;
    }
    if (!title.contentEquals(status.getText())) status.setText(title);
    detail.setText(description);
    primary.setText(action);
    primary.setIconResource(icon);
    final String choice = action;
    primary.setOnClickListener(
        v -> {
          if (choice.equals("Backup settings")) selectScreen(4);
          else if (choice.equals("Review activity")) selectScreen(2);
          else if (choice.equals("Reconnect account")) connect();
          else if (choice.equals("Review permissions")) permissions();
          else startBackup();
        });
  }

  private String waitingReason() {
    ConnectivityManager cm = getSystemService(ConnectivityManager.class);
    NetworkCapabilities network = cm.getNetworkCapabilities(cm.getActiveNetwork());
    if (network == null || !network.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
      return "Waiting for an internet connection.";
    if (settings.wifi() && !network.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
      return "Waiting for an unmetered connection, usually Wi-Fi.";
    return "Waiting for your phone to charge.";
  }

  private void updateActivity(List<String> recent) {
    String key = account.email() + recent.toString();
    if (key.equals(renderedHistory)) return;
    renderedHistory = key;
    historyRows.removeAllViews();
    if (recent.isEmpty()) {
      TextView empty = ui.supporting("Your confirmed uploads and queued items will appear here.");
      ui.space(empty, 24, 0);
      historyRows.addView(empty);
      return;
    }
    for (String row : recent) {
      LinearLayout card = ui.card(historyRows, false);
      int split = row.indexOf('\n');
      card.addView(ui.text(split < 0 ? row : row.substring(0, split), 16, false));
      if (split >= 0) {
        TextView sub = ui.supporting(row.substring(split + 1));
        ui.space(sub, 6, 0);
        card.addView(sub);
      }
    }
  }

  private void updateStorage(InventoryDb.Summary summary, List<LibraryProtocol.Item> rows) {
    boolean active = InventoryService.busy(account.email());
    boolean done = summary.scan().cursor().stage() == LibraryProtocol.Stage.DONE;
    storageTotal.setText(
        summary.scan().pages() == 0
            ? "No results yet"
            : summary.items() > 0 && summary.unknown() == summary.items()
                ? "Usage unknown"
                : bytes(summary.chargedBytes()));
    storageSummary.setText(
        summary.items()
            + " scanned · "
            + summary.chargedItems()
            + " using storage\n"
            + summary.unknown()
            + " unknown · "
            + summary.zero()
            + " reported as zero");
    storageStatus.setText(
        (done ? "Finished" : summary.scan().pages() > 0 ? "Partial results" : "")
            + (summary.scan().pages() > 0 ? " · " + summary.scan().pages() + " pages\n" : "")
            + summary.scan().message());
    scanProgress.setVisibility(active ? View.VISIBLE : View.GONE);
    scanButton.setText(
        account.email().isEmpty()
            ? "Connect Google account"
            : active
                ? "Pause scan"
                : done
                    ? "Refresh scan"
                    : summary.scan().generation().isEmpty()
                        ? "Scan Google Photos"
                        : "Continue scan");
    int visible = Math.min(rows.size(), 50);
    storagePage.setText(
        visible == 0
            ? "No matching scanned items"
            : "Items "
                + (storageOffset + 1)
                + "–"
                + (storageOffset + visible)
                + " · Largest charged size first");
    previous.setEnabled(storageOffset > 0);
    next.setEnabled(rows.size() > 50);
    String fingerprint =
        account.email()
            + "/"
            + summary.scan().generation()
            + "/"
            + summary.scan().pages()
            + "/"
            + quotaFilter
            + "/"
            + mediaFilter
            + "/"
            + storageOffset;
    if (fingerprint.equals(renderedStorage)) return;
    renderedStorage = fingerprint;
    storageRows.removeAllViews();
    if (rows.isEmpty()) {
      TextView empty =
          ui.supporting(
              summary.scan().pages() == 0
                  ? "Start a scan to read the metadata for your Google Photos library."
                  : quotaFilter == 1
                      ? "No items with reported charged storage in this view. Check Storage unknown"
                          + " for items without accounting data."
                      : "No items match these filters.");
      ui.space(empty, 20, 20);
      storageRows.addView(empty);
    }
    for (int i = 0; i < visible; i++) {
      LibraryProtocol.Item item = rows.get(i);
      LinearLayout card = ui.card(storageRows, false);
      card.addView(ui.text(item.name(), 16, false));
      String type = item.type() == 2 ? "Video" : item.type() == 1 ? "Photo" : "Media";
      String info = type + " · File size " + (item.size() == null ? "unknown" : bytes(item.size()));
      if (item.trash()) info += " · In trash";
      if (item.archived()) info += " · Archived";
      if (item.origin() == 3 || item.origin() == 4) info += " · Shared";
      TextView sub = ui.supporting(info);
      ui.space(sub, 6, 8);
      card.addView(sub);
      card.addView(
          ui.text(
              item.charged() == null
                  ? "Storage usage unknown"
                  : bytes(item.charged()) + " reported storage",
              16,
              true));
    }
  }

  private String bytes(long n) {
    return Formatter.formatFileSize(this, n);
  }

  private boolean ready() {
    if (account.email().isEmpty()) {
      connect();
      return false;
    }
    MediaScanner scanner = new MediaScanner(this);
    if (!scanner.fullAccess() || !scanner.originalAccess()) {
      permissions();
      return false;
    }
    if (settings.folders().isEmpty()) {
      chooseFolders();
      return false;
    }
    return true;
  }

  private void startBackup() {
    if (BackupEngine.busy()) {
      settings.enabled(false);
      BackupEngine.cancel();
      Scheduler.configure(this);
      stopService(new Intent(this, BackupService.class));
      refresh();
      return;
    }
    if (!ready()) return;
    settings.enabled(true);
    Scheduler.configure(this);
    settings.status("Preparing backup…");
    try {
      startForegroundService(new Intent(this, BackupService.class));
    } catch (RuntimeException e) {
      message("Android could not start backup. Reopen the app and try again.");
    }
    refresh();
  }

  private void startScan() {
    if (account.email().isEmpty()) {
      connect();
      return;
    }
    if (InventoryService.busy()) {
      stopService(new Intent(this, InventoryService.class));
      refresh();
      return;
    }
    try {
      startForegroundService(new Intent(this, InventoryService.class));
    } catch (RuntimeException e) {
      message("Android could not start the scan. Reopen the app and try again.");
    }
    refresh();
  }

  private void restartScan() {
    if (InventoryService.busy()) {
      message("Pause the current scan first.");
      return;
    }
    if (account.email().isEmpty()) {
      connect();
      return;
    }
    new MaterialAlertDialogBuilder(this)
        .setTitle("Start a fresh scan?")
        .setMessage("Replaces this app’s cached inventory. Your photos and videos stay in place.")
        .setPositiveButton(
            "Scan again",
            (d, w) -> {
              String email = account.email();
              io.execute(
                  () -> {
                    try (InventoryDb db = new InventoryDb(this)) {
                      db.reset(email);
                    }
                    runOnUiThread(
                        () -> {
                          if (alive() && email.equals(account.email())) {
                            storageOffset = 0;
                            startScan();
                          }
                        });
                  });
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void connect() {
    if (BackupEngine.busy() || InventoryService.busy()) {
      message("Pause backup and the storage scan before changing accounts.");
      return;
    }
    startActivity(new Intent(this, SignInActivity.class));
  }

  private void manageAccount() {
    if (account.email().isEmpty()) {
      connect();
      return;
    }
    String name = new ProfileStore(this).name(account.email());
    new MaterialAlertDialogBuilder(this)
        .setTitle("Google account")
        .setItems(
            new String[] {
              name.isBlank() ? account.email() : name + "\n" + account.email(),
              "Refresh profile photo",
              "Reconnect",
              "Disconnect"
            },
            (d, which) -> {
              if (which == 1) refreshProfile(true);
              else if (which == 2) connect();
              else if (which == 3) disconnect();
            })
        .setNegativeButton("Close", null)
        .show();
  }

  private void showProfile(String email, android.graphics.Bitmap bitmap) {
    accountButton.setImageDrawable(
        ProfileStore.icon(
            this,
            bitmap,
            email,
            ui.color(com.google.android.material.R.attr.colorPrimaryContainer),
            ui.color(com.google.android.material.R.attr.colorOnPrimaryContainer)));
    accountButton.setImageTintList(email.isEmpty() ? ColorStateList.valueOf(ui.ink()) : null);
    String label = email.isEmpty() ? "Connect Google account" : "Google account: " + email;
    accountButton.setContentDescription(label);
    accountButton.setTooltipText(label);
  }

  private void refreshProfile(boolean force) {
    String email = account.email();
    if (email.isEmpty() || profileLoading) return;
    profileLoading = true;
    profileIo.execute(
        () -> {
          ProfileStore store = new ProfileStore(this);
          var cached = store.cached(email);
          runOnUiThread(
              () -> {
                if (alive() && email.equals(account.email())) showProfile(email, cached);
              });
          boolean success = false;
          try {
            store.refresh(email, account.load(), profileStop, force);
            success = true;
          } catch (Exception ignored) {
            /* Profile access never changes the working backup sign-in. */
          }
          var bitmap = store.cached(email);
          boolean loaded = success;
          runOnUiThread(
              () -> {
                profileLoading = false;
                if (!alive()) return;
                if (!email.equals(account.email())) {
                  refreshProfile(false);
                  return;
                }
                showProfile(email, bitmap);
                if (force)
                  message(
                      loaded
                          ? "Profile refreshed."
                          : "Google profile photo is unavailable. Backup sign-in is unchanged.");
              });
        });
  }

  private void disconnect() {
    new MaterialAlertDialogBuilder(this)
        .setTitle("Disconnect account?")
        .setMessage(
            "Stops uploads and scanning, removes the saved sign-in and cached storage inventory."
                + " Your local files and Google Photos library stay in place.")
        .setPositiveButton(
            "Disconnect",
            (d, w) -> {
              String old = account.email();
              settings.enabled(false);
              BackupEngine.cancel();
              Scheduler.configure(this);
              stopService(new Intent(this, BackupService.class));
              stopService(new Intent(this, InventoryService.class));
              account.clear();
              showProfile("", null);
              io.execute(
                  () -> {
                    try (InventoryDb db = new InventoryDb(this)) {
                      db.clear(old);
                    }
                    runOnUiThread(
                        () -> {
                          if (alive()) {
                            settings.status("Account disconnected.");
                            draw();
                          }
                        });
                  });
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void permissions() {
    new MaterialAlertDialogBuilder(this)
        .setTitle("Keep complete originals")
        .setMessage(
            "Allow all photos and videos so folder backup can discover new files. Media location"
                + " access preserves embedded location in your originals.")
        .setPositiveButton(
            "Continue",
            (d, w) ->
                requestPermissions(
                    new String[] {
                      Manifest.permission.READ_MEDIA_IMAGES,
                      Manifest.permission.READ_MEDIA_VIDEO,
                      Manifest.permission.ACCESS_MEDIA_LOCATION,
                      Manifest.permission.POST_NOTIFICATIONS
                    },
                    10))
        .setNeutralButton(
            "App settings",
            (d, w) ->
                startActivity(
                    new Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()))))
        .setNegativeButton("Cancel", null)
        .show();
  }

  @Override
  public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(code, permissions, results);
    refresh();
    if (code == 10 && new MediaScanner(this).fullAccess() && settings.folders().isEmpty())
      chooseFolders();
  }

  private void chooseFolders() {
    if (!new MediaScanner(this).fullAccess()) {
      permissions();
      return;
    }
    message("Finding photo and video folders…");
    io.execute(
        () -> {
          try {
            List<MediaScanner.Folder> found = new MediaScanner(this).folders();
            runOnUiThread(
                () -> {
                  if (!alive()) return;
                  if (found.isEmpty()) {
                    message("No photo or video folders found yet.");
                    return;
                  }
                  Set<String> selected = settings.folders();
                  CharSequence[] labels = new CharSequence[found.size()];
                  boolean[] checked = new boolean[found.size()];
                  for (int i = 0; i < found.size(); i++) {
                    MediaScanner.Folder folder = found.get(i);
                    labels[i] = folder.label() + " · " + folder.count() + " items";
                    checked[i] = selected.contains(folder.key());
                  }
                  new MaterialAlertDialogBuilder(this)
                      .setTitle("Backup folders")
                      .setMultiChoiceItems(
                          labels, checked, (dialog, which, on) -> checked[which] = on)
                      .setPositiveButton(
                          "Save folders",
                          (dialog, which) -> {
                            Set<String> choices = new HashSet<>();
                            for (int i = 0; i < checked.length; i++)
                              if (checked[i]) choices.add(found.get(i).key());
                            if (!choices.equals(settings.folders())) settings.lastRun(0);
                            settings.folders(choices);
                            Scheduler.configure(this);
                            refresh();
                          })
                      .setNegativeButton("Cancel", null)
                      .show();
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  if (alive()) message("Couldn’t read folders. Check photo and video permissions.");
                });
          }
        });
  }

  private void openPhotos() {
    Intent intent = getPackageManager().getLaunchIntentForPackage("com.google.android.apps.photos");
    if (intent == null)
      intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://photos.google.com"));
    startActivity(intent);
  }

  private void about() {
    io.execute(
        () -> {
          try {
            String license;
            try (var input = getAssets().open("XOB0T-LICENSE.txt")) {
              license = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            String text =
                "Photos Unlimited 0.2.1\n\n"
                    + "Independent Android uploader. Not affiliated with Google.\n\n"
                    + "Turn off backup in the official Google Photos app when this app handles"
                    + " uploads. Original files stay on your phone. Upload acceptance is not proof"
                    + " of storage exemption. Existing library items keep their accounting.\n\n"
                    + "Storage scans are read-only previews of Google Photos metadata; Drive and"
                    + " Gmail are not included. Missing accounting is shown as unknown.\n\n"
                    + "Protocol adapted from xob0t’s GPMC and gotohp.\n\n"
                    + license;
            runOnUiThread(
                () -> {
                  if (alive())
                    new MaterialAlertDialogBuilder(this)
                        .setTitle("About Photos Unlimited")
                        .setMessage(text)
                        .setPositiveButton("Close", null)
                        .show();
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  if (alive()) message("Couldn’t read credits.");
                });
          }
        });
  }

  private void message(String text) {
    Toast.makeText(this, text, Toast.LENGTH_LONG).show();
  }
}
