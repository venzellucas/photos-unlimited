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
  /** Only the update check runs here, so a slow GitHub answer can't delay the backup counts. */
  private final ExecutorService updateIo = Executors.newSingleThreadExecutor();
  private static final int SCREEN_BACKUP = 1;
  private LinearLayout updateCard;
  /** Items only on this phone; -1 until read, which keeps the update card hidden. */
  private volatile int unsafeCount = -1;
  private final ExecutorService profileIo = Executors.newSingleThreadExecutor();
  private final java.util.concurrent.atomic.AtomicBoolean profileStop =
      new java.util.concurrent.atomic.AtomicBoolean();
  private ImageButton accountButton;
  private boolean profileLoading;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private Settings settings;
  private AccountStore account;
  private Ui ui;
  private LinearLayout page, historyRows, storageRows, reclaimCard, reclaimRows, selectionBar;
  private boolean selecting;
  private final LinkedHashMap<String, LibraryProtocol.Item> chosen = new LinkedHashMap<>();
  private TextView selectionInfo;
  private MaterialButton selectButton, selectAllButton, freeSelectedButton;
  private ScrollView scroll;
  private BottomNavigationView navigation;
  private TextView status,
      detail,
      counts,
      progressLabel,
      storageTotal,
      storageSummary,
      storageStatus,
      storagePage,
      reclaimStatus;
  private MaterialButton primary,
      folders,
      scanButton,
      previous,
      next,
      reclaimButton,
      cleanupButton,
      reviewButton;
  private List<ReclaimRecovery.Orphan> pendingReview = List.of();
  private LinearProgressIndicator progress, scanProgress;
  private int screen = 1, generation, quotaFilter = 1, mediaFilter, storageOffset;
  private boolean loading, photosPrompted, awaitingPhotosReturn;
  static final String GOOGLE_PHOTOS = "com.google.android.apps.photos";
  private String visibleAccount = "";
  private String renderedHistory = "", renderedStorage = "", renderedReclaim = "";
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
    if (saved != null) {
      photosPrompted = saved.getBoolean("photosPrompted");
      awaitingPhotosReturn = saved.getBoolean("awaitingPhotos");
    }
    if (screen < 1 || screen > 4) screen = 1;
    // Make staged copies left by an earlier install visible to the Free up card.
    io.execute(() -> ReclaimRecovery.index(this));
    if (saved != null) {
      quotaFilter = saved.getInt("quotaFilter", 1);
      mediaFilter = saved.getInt("mediaFilter");
      storageOffset = saved.getInt("offset");
    }
    LinearLayout root = ui.column();
    root.setBackgroundColor(ui.surface());
    MaterialToolbar toolbar = new MaterialToolbar(this);
    toolbar.setTitle(R.string.main_app_name);
    toolbar.setTitleTextColor(ui.ink());
    toolbar.setLogo(R.drawable.ic_backup);
    toolbar.setContentInsetStartWithNavigation(ui.dp(16));
    accountButton = new ImageButton(this, null, android.R.attr.actionButtonStyle);
    accountButton.setLayoutParams(new ViewGroup.LayoutParams(ui.dp(48), ui.dp(48)));
    accountButton.setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8));
    accountButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
    accountButton.setOnClickListener(v -> manageAccount());
    accountButton.setId(R.id.menu_account);
    var accountMenu = toolbar.getMenu().add(0, R.id.menu_account, 0, R.string.main_account_menu);
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
    navigation.getMenu().add(0, R.id.nav_backup, 0, R.string.main_nav_backup).setIcon(R.drawable.ic_backup);
    navigation.getMenu().add(0, R.id.nav_activity, 1, R.string.main_nav_activity).setIcon(R.drawable.ic_activity);
    navigation.getMenu().add(0, R.id.nav_storage, 2, R.string.main_nav_storage).setIcon(R.drawable.ic_storage);
    navigation.getMenu().add(0, R.id.nav_settings, 3, R.string.main_nav_settings).setIcon(R.drawable.ic_settings);
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
    checkGooglePhotosBackup();
    io.execute(
        () -> {
          try {
            unsafeCount = ReclaimService.warnIfOnlyOnPhone(this, ""); // also re-posts the warning
          } catch (RuntimeException e) {
            unsafeCount = -1; // the Storage screen still shows the only-on-phone state
          }
          runOnUiThread(this::drawUpdateCard);
        });
    updateIo.execute(
        () -> UpdateCheck.runIfDue(this, settings, () -> runOnUiThread(this::drawUpdateCard)));
  }

  /** Google Photos is installed and not disabled. */
  private boolean googlePhotosActive() {
    try {
      return getPackageManager().getApplicationInfo(GOOGLE_PHOTOS, 0).enabled;
    } catch (android.content.pm.PackageManager.NameNotFoundException e) {
      return false;
    }
  }

  /**
   * Android does not let other apps read Google Photos' settings. Ask on each app open until the
   * user confirms its backup is off; afterwards ask again only when backup finds a new photo that
   * another uploader got to first.
   */
  private void checkGooglePhotosBackup() {
    if (awaitingPhotosReturn) {
      awaitingPhotosReturn = false;
      new MaterialAlertDialogBuilder(this)
          .setTitle(R.string.main_gp_off_title)
          .setMessage(R.string.main_gp_off_message)
          .setPositiveButton(
              R.string.main_gp_off_yes,
              (d, w) -> {
                settings.confirmPhotosBackupOff();
                settings.clearOtherUploader();
              })
          .setNegativeButton(R.string.main_gp_off_no, null)
          .show();
      return;
    }
    if (photosPrompted || !settings.enabled() || !googlePhotosActive()) return;
    long confirmed = settings.photosBackupOffConfirmed();
    boolean evidence = settings.otherUploaderAt() > confirmed;
    if (confirmed > 0 && !evidence) return;
    photosPrompted = true;
    String message =
        evidence
            ? getResources()
                .getQuantityString(
                    R.plurals.main_gp_prompt_evidence,
                    settings.otherUploaderCount(),
                    settings.otherUploaderCount(),
                    settings.otherUploaderName())
            : getString(R.string.main_gp_prompt_plain);
    new MaterialAlertDialogBuilder(this)
        .setTitle(R.string.main_gp_prompt_title)
        .setMessage(message)
        .setPositiveButton(R.string.main_gp_open, (d, w) -> openGooglePhotosBackup())
        .setNegativeButton(
            R.string.main_gp_already_off,
            (d, w) -> {
              settings.confirmPhotosBackupOff();
              settings.clearOtherUploader();
            })
        .setNeutralButton(R.string.main_later, null)
        .show();
  }

  private void openGooglePhotosBackup() {
    Intent backup =
        new Intent(Intent.ACTION_VIEW, Uri.parse("https://photos.google.com/link/backup"))
            .setPackage(GOOGLE_PHOTOS);
    try {
      startActivity(backup);
      awaitingPhotosReturn = true;
    } catch (ActivityNotFoundException e) {
      Intent launch = getPackageManager().getLaunchIntentForPackage(GOOGLE_PHOTOS);
      if (launch == null) {
        message(getString(R.string.main_gp_unavailable));
        return;
      }
      startActivity(launch);
      awaitingPhotosReturn = true;
      message(getString(R.string.main_gp_manual_hint));
    }
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
    updateIo.shutdownNow();
    profileStop.set(true);
    profileIo.shutdownNow();
    super.onDestroy();
  }

  @Override
  public void onSaveInstanceState(Bundle saved) {
    saved.putInt("screen", screen);
    saved.putBoolean("photosPrompted", photosPrompted);
    saved.putBoolean("awaitingPhotos", awaitingPhotosReturn);
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
    updateCard = null;
    progress = scanProgress = null;
    historyRows = storageRows = reclaimRows = reclaimCard = selectionBar = null;
    selectionInfo = null;
    selectButton = selectAllButton = freeSelectedButton = cleanupButton = reviewButton = null;
    reclaimStatus = null;
    reclaimButton = null;
    renderedReclaim = "";
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
    heading(getString(R.string.backup_heading), getString(R.string.backup_subtitle));
    LinearLayout hero = ui.card(page, true);
    ImageView cloud = new ImageView(this);
    cloud.setImageResource(R.drawable.ic_backup);
    cloud.setImageTintList(
        ColorStateList.valueOf(
            ui.color(com.google.android.material.R.attr.colorOnPrimaryContainer)));
    hero.addView(cloud, new LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)));
    status = ui.text(getString(R.string.backup_checking), 24, false);
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
    primary = ui.button(getString(R.string.backup_back_up_now), R.drawable.ic_backup, true);
    ui.space(primary, 20, 0);
    hero.addView(primary);
    primary.setOnClickListener(v -> startBackup());
    counts = ui.supporting(getString(R.string.backup_loading_counts));
    ui.space(counts, 4, 20);
    page.addView(counts);
    updateCard = ui.card(page, false);
    ((View) updateCard.getParent()).setVisibility(View.GONE);
    unsafeCount = -1; // every Backup draw reads the count before the card can show Download
    io.execute(
        () -> {
          try (ReclaimDb db = new ReclaimDb(this)) {
            unsafeCount = db.unsafeAll();
          } catch (RuntimeException e) {
            unsafeCount = -1; // unknown stays hidden
          }
          runOnUiThread(this::drawUpdateCard);
        });
    LinearLayout group = ui.card(page, false);
    folders = ui.row(
            group, getString(R.string.backup_folders), R.drawable.ic_folder, this::chooseFolders);
    ui.row(
        group,
        getString(R.string.backup_recent_activity),
        R.drawable.ic_activity,
        () -> selectScreen(2));
    MaterialButton photos = ui.button(getString(R.string.backup_open_google_photos), R.drawable.ic_open, false);
    page.addView(photos);
    photos.setOnClickListener(v -> openPhotos());
    TextView note = ui.supporting(getString(R.string.backup_originals_stay));
    ui.space(note, 12, 0);
    page.addView(note);
  }

  /** The update card: below the main backup action, normal card style, no warning colors. */
  private void drawUpdateCard() {
    if (!alive() || updateCard == null || screen != SCREEN_BACKUP) return;
    String latest = settings.updateLatest();
    UpdateNotice.State state =
        UpdateNotice.state(
            settings.updateCheck(),
            installedVersionName(),
            latest,
            settings.updateDismissed(),
            unsafeCount,
            ReclaimService.busy());
    updateCard.removeAllViews();
    View frame = (View) updateCard.getParent();
    if (state == UpdateNotice.State.HIDDEN) {
      frame.setVisibility(View.GONE);
      return;
    }
    UpdateCheck.Version version = UpdateCheck.Version.parse(latest);
    updateCard.addView(ui.text(getString(R.string.update_title, latest), 18, true));
    boolean blocked = state == UpdateNotice.State.BLOCKED;
    TextView body =
        ui.supporting(getString(blocked ? R.string.update_blocked_body : R.string.update_body));
    ui.space(body, 4, 12);
    updateCard.addView(body);
    MaterialButton main =
        blocked
            ? ui.button(getString(R.string.update_open_free_up), R.drawable.ic_storage, true)
            : ui.button(getString(R.string.update_download), R.drawable.ic_open, true);
    main.setOnClickListener(
        v -> {
          if (blocked) selectScreen(3);
          else openUrl(UpdateNotice.downloadUrl());
        });
    updateCard.addView(main);
    MaterialButton whatsNew = ui.button(getString(R.string.update_whats_new), 0, false);
    whatsNew.setOnClickListener(v -> openUrl(UpdateNotice.releaseUrl(version)));
    updateCard.addView(whatsNew);
    if (!blocked) {
      MaterialButton notNow = ui.button(getString(R.string.update_not_now), 0, false);
      notNow.setOnClickListener(
          v -> {
            settings.updateDismissed(latest);
            drawUpdateCard();
          });
      updateCard.addView(notNow);
    }
    frame.setVisibility(View.VISIBLE);
  }

  private String installedVersionName() {
    try {
      return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
    } catch (android.content.pm.PackageManager.NameNotFoundException e) {
      return "";
    }
  }

  private void openUrl(String url) {
    try {
      startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    } catch (ActivityNotFoundException e) {
      message(getString(R.string.no_browser));
    }
  }

  private void drawActivity() {
    heading(getString(R.string.activity_heading), getString(R.string.activity_subtitle));
    MaterialButton retry = ui.button(getString(R.string.activity_retry), R.drawable.ic_refresh, false);
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
    heading(getString(R.string.storage_heading), getString(R.string.storage_subtitle));
    LinearLayout card = ui.card(page, true);
    storageTotal = ui.text(getString(R.string.storage_no_scan), 28, false);
    storageTotal.setTextColor(ui.color(com.google.android.material.R.attr.colorOnPrimaryContainer));
    card.addView(storageTotal);
    TextView caption = ui.supporting(getString(R.string.storage_caption));
    caption.setTextColor(storageTotal.getCurrentTextColor());
    ui.space(caption, 4, 12);
    card.addView(caption);
    storageSummary = ui.supporting("");
    storageSummary.setTextColor(storageTotal.getCurrentTextColor());
    card.addView(storageSummary);
    storageStatus = ui.supporting(getString(R.string.storage_scan_prompt));
    ui.space(storageStatus, 12, 8);
    card.addView(storageStatus);
    scanProgress = new LinearProgressIndicator(this);
    scanProgress.setIndeterminate(true);
    card.addView(scanProgress);
    scanProgress.setVisibility(View.GONE);
    scanButton = ui.button(getString(R.string.storage_scan), R.drawable.ic_storage, true);
    ui.space(scanButton, 16, 0);
    card.addView(scanButton);
    scanButton.setOnClickListener(v -> startScan());
    TextView note =
        ui.supporting(getString(R.string.storage_scan_note));
    ui.space(note, 0, 12);
    page.addView(note);
    reclaimCard = ui.card(page, false);
    reclaimCard.addView(ui.text(getString(R.string.freeup_title), 18, true));
    reclaimStatus = ui.supporting("");
    ui.space(reclaimStatus, 6, 8);
    reclaimCard.addView(reclaimStatus);
    reclaimRows = ui.column();
    reclaimCard.addView(reclaimRows);
    reclaimButton = ui.button(getString(R.string.freeup_resume), R.drawable.ic_refresh, false);
    ui.space(reclaimButton, 8, 0);
    reclaimCard.addView(reclaimButton);
    reclaimButton.setOnClickListener(v -> toggleReclaim());
    cleanupButton = ui.button(getString(R.string.freeup_remove_copies), 0, false);
    ui.space(cleanupButton, 8, 0);
    reclaimCard.addView(cleanupButton);
    cleanupButton.setOnClickListener(v -> removeRecoveredCopies());
    reviewButton = ui.button(getString(R.string.freeup_review_files), 0, false);
    ui.space(reviewButton, 8, 0);
    reclaimCard.addView(reviewButton);
    reviewButton.setOnClickListener(v -> reviewLeftovers());
    reviewButton.setVisibility(View.GONE);
    reclaimCard.setVisibility(View.GONE);
    LinearLayout filters = ui.card(page, false);
    picker(
        filters,
        getString(R.string.storage_filter_status),
        new String[] {
          getString(R.string.storage_filter_all),
          getString(R.string.storage_filter_using),
          getString(R.string.storage_filter_unknown),
          getString(R.string.storage_filter_reduced)
        },
        quotaFilter,
        value -> {
          quotaFilter = value;
          storageOffset = 0;
          renderedStorage = "";
          refresh();
        });
    picker(
        filters,
        getString(R.string.storage_filter_media),
        new String[] {
          getString(R.string.storage_media_all),
          getString(R.string.storage_media_photos),
          getString(R.string.storage_media_videos)
        },
        mediaFilter,
        value -> {
          mediaFilter = value;
          storageOffset = 0;
          renderedStorage = "";
          refresh();
        });
    ui.row(
        filters,
        getString(R.string.storage_fresh_scan),
        R.drawable.ic_refresh,
        this::restartScan);
    storagePage = ui.supporting(getString(R.string.storage_sort_note));
    page.addView(storagePage);
    selectButton = ui.button(
            getString(selecting ? R.string.storage_done_selecting : R.string.storage_select_items),
            0,
            false);
    ui.space(selectButton, 8, 4);
    page.addView(selectButton);
    selectButton.setOnClickListener(
        v -> {
          selecting = !selecting;
          if (!selecting) chosen.clear();
          selectButton.setText(
              getString(selecting ? R.string.storage_done_selecting : R.string.storage_select_items));
          updateSelection();
          renderedStorage = "";
          refresh();
        });
    selectionBar = ui.card(page, true);
    selectionInfo = ui.text("", 16, true);
    selectionBar.addView(selectionInfo);
    selectAllButton = ui.button(getString(R.string.storage_select_all), 0, false);
    ui.space(selectAllButton, 8, 0);
    selectionBar.addView(selectAllButton);
    selectAllButton.setOnClickListener(v -> selectAllEligible());
    freeSelectedButton = ui.button(getString(R.string.freeup_button), R.drawable.ic_storage, true);
    ui.space(freeSelectedButton, 8, 0);
    selectionBar.addView(freeSelectedButton);
    freeSelectedButton.setOnClickListener(v -> beforeReclaim(this::confirmBatch));
    updateSelection();
    storageRows = ui.column();
    page.addView(storageRows);
    LinearLayout pages = new LinearLayout(this);
    previous = ui.button(getString(R.string.storage_previous), R.drawable.ic_back, false);
    next = ui.button(getString(R.string.storage_next), 0, false);
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
    heading(getString(R.string.settings_heading), getString(R.string.settings_subtitle));
    section(getString(R.string.settings_section_backup));
    LinearLayout backup = ui.card(page, false);
    ui.toggle(
        backup,
        getString(R.string.settings_automatic),
        settings.enabled(),
        (b, on) -> {
          if (on && !ready()) {
            b.setChecked(false);
            return;
          }
          settings.enabled(on);
          Scheduler.configure(this);
          if (on) {
            photosPrompted = false;
            checkGooglePhotosBackup();
          }
          if (on) Scheduler.continuation(this);
          else {
            BackupEngine.cancel();
            stopService(new Intent(this, BackupService.class));
          }
        });
    ui.toggle(
        backup,
        getString(R.string.settings_unmetered),
        settings.wifi(),
        (b, on) -> {
          settings.wifi(on);
          Scheduler.configure(this);
        });
    ui.toggle(
        backup,
        getString(R.string.settings_charging),
        settings.charging(),
        (b, on) -> {
          settings.charging(on);
          Scheduler.configure(this);
        });
    if (googlePhotosActive())
      ui.row(
          backup,
          getString(R.string.settings_gp_off_row),
          R.drawable.ic_open,
          this::openGooglePhotosBackup);
    section(getString(R.string.settings_section_appearance));
    LinearLayout appearance = ui.card(page, false);
    SharedPreferences prefs = getSharedPreferences("appearance", 0);
    String theme = prefs.getString("theme", "system");
    int selected = theme.equals("dark") ? 2 : theme.equals("light") ? 1 : 0;
    picker(
        appearance,
        getString(R.string.settings_theme),
        new String[] {
          getString(R.string.settings_theme_system),
          getString(R.string.settings_theme_light),
          getString(R.string.settings_theme_dark)
        },
        selected,
        value -> {
          prefs.edit().putString("theme", new String[] {"system", "light", "dark"}[value]).apply();
          recreate();
        });
    String[] languages = Languages.NATIVE_NAMES.clone();
    languages[0] = getString(R.string.settings_language_system);
    // Android applies the choice to the whole app and recreates this screen in that language.
    picker(
        appearance,
        getString(R.string.settings_language),
        languages,
        Languages.current(this),
        value -> Languages.choose(this, value));
    ui.toggle(
        appearance,
        getString(R.string.settings_wallpaper),
        prefs.getBoolean("dynamic", true),
        (b, on) -> {
          prefs.edit().putBoolean("dynamic", on).apply();
          recreate();
        });
    section(getString(R.string.settings_section_account));
    LinearLayout app = ui.card(page, false);
    ui.row(
        app,
        account.email().isEmpty()
            ? getString(R.string.main_connect_account)
            : getString(R.string.settings_account_row, account.email()),
        R.drawable.ic_account,
        this::manageAccount);
    ui.row(
        app, getString(R.string.settings_permissions), R.drawable.ic_photo, this::permissions);
    ui.toggle(
        app,
        getString(R.string.update_setting),
        settings.updateCheck(),
        (b, on) -> settings.updateCheck(on));
    app.addView(ui.supporting(getString(R.string.update_setting_summary)));
    ui.row(app, getString(R.string.settings_about), R.drawable.ic_info, this::about);
    ui.row(
        app,
        getString(R.string.settings_diagnostic),
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
    button.setContentDescription(getString(R.string.main_picker_description, label, values[selected]));
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
                        button.setContentDescription(
                            getString(R.string.main_picker_description, label, values[which]));
                        changed.accept(which);
                      }
                    })
                .setNegativeButton(R.string.main_cancel, null)
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
          List<ReclaimEngine.Job> jobList = List.of();
          int[] safety = new int[2];
          List<ReclaimRecovery.Orphan> reviewList = new ArrayList<>();
          try {
            if (view == 3) {
              try (InventoryDb db = new InventoryDb(this)) {
                summary = db.summary(email);
                items = db.list(email, q, m, offset);
              }
              try (ReclaimDb db = new ReclaimDb(this)) {
                jobList = db.recent(email, 20);
                safety[0] = db.unsafe(email);
              }
              if (!ReclaimService.busy()) {
                Set<String> cleanup = settings.recoveryCleanup();
                Set<String> approved = settings.recoveryApproved();
                for (ReclaimRecovery.Orphan o : ReclaimRecovery.orphans(this)) {
                  String uri = o.uri().toString();
                  if (cleanup.contains(uri)) continue;
                  if (o.owned() || approved.contains(uri)) safety[1]++;
                  else if (!o.partial()) reviewList.add(o);
                }
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
            List<ReclaimEngine.Job> jobs = jobList;
            List<ReclaimRecovery.Orphan> review = reviewList;
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
                  else if (view == 3) {
                    updateStorage(result, rows);
                    updateReclaim(jobs, safety[0], safety[1], review);
                  }
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  loading = false;
                  if (alive() && revision == generation) {
                    if (status != null) status.setText(getString(R.string.main_read_failed));
                    if (storageStatus != null)
                      storageStatus.setText(getString(R.string.storage_cache_failed));
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
        failed > 0
            ? getString(R.string.backup_counts_attention, uploaded, existing, pending, failed)
            : getString(R.string.backup_counts, uploaded, existing, pending));
    List<String> names = new ArrayList<>();
    for (String f : selected) names.add(f.substring(f.indexOf(':') + 1).replaceAll("/$", ""));
    Collections.sort(names);
    folders.setText(
        getString(
            R.string.backup_folders_row,
            names.isEmpty()
                ? getString(R.string.backup_choose_folders)
                : String.join(", ", names)));
    String title, description;
    int actionRes = R.string.backup_back_up_now;
    int icon = R.drawable.ic_backup;
    MediaScanner scanner = new MediaScanner(this);
    BackupEngine.ProgressState live = BackupEngine.progress();
    boolean busy = BackupEngine.busy();
    progress.setVisibility(View.GONE);
    progressLabel.setText("");
    if (account.email().isEmpty()) {
      title = getString(R.string.backup_title_connect);
      description = getString(R.string.backup_desc_connect);
      actionRes = R.string.main_connect_account;
    } else if (!scanner.fullAccess() || !scanner.originalAccess()) {
      title = getString(R.string.backup_title_media);
      description = getString(R.string.backup_desc_media);
      actionRes = R.string.backup_action_permissions;
    } else if (selected.isEmpty()) {
      title = getString(R.string.backup_title_folders);
      description = getString(R.string.backup_desc_folders);
      actionRes = R.string.backup_choose_folders;
    } else if (busy) {
      title =
          settings.enabled()
              ? getString(
                  live.uploading() ? R.string.backup_title_running : R.string.backup_title_checking)
              : getString(R.string.backup_title_pausing);
      description =
          account.email().equals(live.account())
              ? live.detail()
              : getString(R.string.backup_desc_preparing);
      actionRes = R.string.backup_action_pause;
      icon = R.drawable.ic_pause;
      if (live.uploading() && live.total() > 0 && account.email().equals(live.account())) {
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(false);
        progress.setProgressCompat(
            (int) Math.min(1000, 1000.0 * live.sent() / live.total()), false);
        progressLabel.setText(
            getString(R.string.backup_progress_label, bytes(live.sent()), bytes(live.total())));
      }
    } else if (!settings.failure().isEmpty()) {
      title = getString(R.string.backup_title_attention);
      description = settings.status();
      actionRes =
          settings.failure().equals("auth")
              ? R.string.backup_action_reconnect
              : settings.failure().equals("media")
                  ? R.string.backup_action_permissions
                  : R.string.backup_back_up_now;
      icon = R.drawable.ic_warning;
    } else if (!settings.enabled()) {
      title = getString(R.string.backup_title_paused);
      description = settings.status();
      actionRes = R.string.backup_back_up_now;
      icon = R.drawable.ic_play;
    } else if (!BackupEngine.allowed(this, settings)) {
      title = getString(R.string.backup_title_waiting);
      description = waitingReason();
      actionRes = R.string.backup_action_settings;
      icon = R.drawable.ic_settings;
    } else if (failed > 0) {
      title = getString(R.string.backup_title_some_attention);
      description = getString(R.string.backup_desc_some_attention);
      actionRes = R.string.backup_action_activity;
      icon = R.drawable.ic_warning;
    } else if (pending > 0 || settings.lastRun() == 0) {
      title = getString(R.string.backup_title_scheduled);
      description = getString(R.string.backup_desc_scheduled);
      actionRes = R.string.backup_back_up_now;
    } else {
      title = getString(R.string.backup_title_up_to_date);
      description =
          getString(
              R.string.backup_desc_up_to_date,
              DateUtils.getRelativeTimeSpanString(settings.lastRun()));
      actionRes = R.string.backup_back_up_now;
      icon = R.drawable.ic_check;
    }
    if (!title.contentEquals(status.getText())) status.setText(title);
    detail.setText(description);
    primary.setText(getString(actionRes));
    primary.setIconResource(icon);
    final int choice = actionRes;
    primary.setOnClickListener(
        v -> {
          if (choice == R.string.backup_action_settings) selectScreen(4);
          else if (choice == R.string.backup_action_activity) selectScreen(2);
          else if (choice == R.string.backup_action_reconnect) connect();
          else if (choice == R.string.backup_action_permissions) permissions();
          else startBackup();
        });
  }

  private String waitingReason() {
    ConnectivityManager cm = getSystemService(ConnectivityManager.class);
    NetworkCapabilities network = cm.getNetworkCapabilities(cm.getActiveNetwork());
    if (network == null || !network.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
      return getString(R.string.backup_waiting_internet);
    if (settings.wifi() && !network.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
      return getString(R.string.backup_waiting_unmetered);
    return getString(R.string.backup_waiting_charging);
  }

  private void updateActivity(List<String> recent) {
    String key = account.email() + recent.toString();
    if (key.equals(renderedHistory)) return;
    renderedHistory = key;
    historyRows.removeAllViews();
    if (recent.isEmpty()) {
      TextView empty = ui.supporting(getString(R.string.activity_empty));
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
            ? getString(R.string.storage_no_results)
            : summary.items() > 0 && summary.unknown() == summary.items()
                ? getString(R.string.storage_usage_unknown)
                : bytes(summary.chargedBytes()));
    storageSummary.setText(
        getString(
            R.string.storage_summary,
            summary.items(),
            summary.chargedItems(),
            summary.unknown(),
            summary.zero()));
    storageStatus.setText(
        summary.scan().pages() > 0
            ? getResources()
                .getQuantityString(
                    done ? R.plurals.storage_status_finished : R.plurals.storage_status_partial,
                    summary.scan().pages(),
                    summary.scan().pages(),
                    summary.scan().message())
            : done
                ? getString(R.string.storage_status_finished_nopages, summary.scan().message())
                : summary.scan().message());
    scanProgress.setVisibility(active ? View.VISIBLE : View.GONE);
    scanButton.setText(
        account.email().isEmpty()
            ? getString(R.string.main_connect_account)
            : active
                ? getString(R.string.storage_pause_scan)
                : done
                    ? getString(R.string.storage_refresh_scan)
                    : summary.scan().generation().isEmpty()
                        ? getString(R.string.storage_scan)
                        : getString(R.string.storage_continue_scan));
    int visible = Math.min(rows.size(), 50);
    storagePage.setText(
        visible == 0
            ? getString(R.string.storage_no_matches)
            : getString(R.string.storage_range, storageOffset + 1, storageOffset + visible));
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
            + storageOffset
            + "/"
            + selecting;
    if (fingerprint.equals(renderedStorage)) return;
    renderedStorage = fingerprint;
    storageRows.removeAllViews();
    if (rows.isEmpty()) {
      TextView empty =
          ui.supporting(
              summary.scan().pages() == 0
                  ? getString(R.string.storage_empty_start)
                  : quotaFilter == 1
                      ? getString(R.string.storage_empty_none_charged)
                      : getString(R.string.storage_empty_no_match));
      ui.space(empty, 20, 20);
      storageRows.addView(empty);
    }
    for (int i = 0; i < visible; i++) {
      LibraryProtocol.Item item = rows.get(i);
      LinearLayout card = ui.card(storageRows, false);
      card.setClickable(true);
      card.setFocusable(true);
      android.util.TypedValue ripple = new android.util.TypedValue();
      getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
      card.setForeground(getDrawable(ripple.resourceId));
      String why = ReclaimEngine.ineligible(item);
      LinearLayout body = card;
      if (selecting) {
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        com.google.android.material.checkbox.MaterialCheckBox box =
            new com.google.android.material.checkbox.MaterialCheckBox(this);
        box.setChecked(chosen.containsKey(item.key()));
        box.setEnabled(why == null);
        box.setClickable(false);
        line.addView(box);
        body = ui.column();
        line.addView(body, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(line);
        card.setAlpha(why == null ? 1f : 0.6f);
        card.setOnClickListener(
            v -> {
              if (why != null) {
                message(getString(R.string.storage_cant_select, why));
                return;
              }
              if (chosen.remove(item.key()) == null) chosen.put(item.key(), item);
              box.setChecked(chosen.containsKey(item.key()));
              updateSelection();
            });
      } else card.setOnClickListener(v -> showItem(item));
      body.addView(ui.text(item.name(), 16, false));
      String type = mediaTypeLabel(item);
      String info =
          item.size() == null
              ? getString(R.string.storage_item_info_nosize, type)
              : getString(R.string.storage_item_info, type, bytes(item.size()));
      if (item.trash()) info += " · " + getString(R.string.storage_flag_trash);
      if (item.archived()) info += " · " + getString(R.string.storage_flag_archived);
      if (item.origin() == 3 || item.origin() == 4)
        info += " · " + getString(R.string.storage_flag_shared);
      if (item.extra().edited()) info += " · " + getString(R.string.storage_flag_edited);
      if (item.extra().quality() == LibraryProtocol.Quality.REDUCED)
        info += " · " + getString(R.string.storage_filter_reduced);
      TextView sub = ui.supporting(info);
      ui.space(sub, 6, 8);
      body.addView(sub);
      body.addView(
          ui.text(
              item.charged() == null
                  ? getString(R.string.storage_usage_unknown_row)
                  : getString(R.string.storage_reported, bytes(item.charged())),
              16,
              true));
    }
  }

  private String mediaTypeLabel(LibraryProtocol.Item item) {
    return getString(
        item.type() == 2
            ? R.string.storage_type_video
            : item.type() == 1 ? R.string.storage_type_photo : R.string.storage_type_media);
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
    boolean wasOn = settings.enabled();
    settings.enabled(true);
    Scheduler.configure(this);
    settings.status(getString(R.string.backup_preparing_status));
    if (!wasOn) {
      photosPrompted = false;
      checkGooglePhotosBackup();
    }
    try {
      startForegroundService(new Intent(this, BackupService.class));
    } catch (RuntimeException e) {
      message(getString(R.string.backup_start_failed));
    }
    refresh();
  }

  private String stateLabel(ReclaimEngine.State state) {
    return getString(
        switch (state) {
          case QUEUED -> R.string.freeup_state_waiting;
          case DOWNLOADED -> R.string.freeup_state_saved;
          case TRASHING, TRASHED -> R.string.freeup_state_trash;
          case DELETING, DELETED -> R.string.freeup_state_uploading;
          case UPLOADED -> R.string.freeup_state_verifying;
          case DONE -> R.string.freeup_state_freed;
          case CHARGED -> R.string.freeup_state_charged;
          case FAILED -> R.string.freeup_state_failed;
          case ATTENTION -> R.string.freeup_state_attention;
        });
  }

  private void updateReclaim(
      List<ReclaimEngine.Job> jobs,
      int onlyOnPhone,
      int leftovers,
      List<ReclaimRecovery.Orphan> review) {
    if (reclaimCard == null) return;
    boolean running = ReclaimService.busy();
    int cleanup = settings.recoveryCleanup().size();
    int otherAccount = settings.recoveryOtherAccount();
    pendingReview = review;
    reclaimCard.setVisibility(
        jobs.isEmpty()
                && !running
                && leftovers == 0
                && cleanup == 0
                && review.isEmpty()
                && otherAccount == 0
            ? View.GONE
            : View.VISIBLE);
    long freed = 0;
    int active = 0;
    for (ReclaimEngine.Job job : jobs) {
      if (job.state == ReclaimEngine.State.DONE && job.charged != null) freed += job.charged;
      if (!job.state.terminal()) active++;
    }
    String text =
        (freed > 0 ? getString(R.string.freeup_freed, bytes(freed)) + " " : "")
            + (running
                ? ReclaimService.status()
                : active > 0
                    ? getResources()
                        .getQuantityString(R.plurals.freeup_unfinished, active, active)
                    : getString(R.string.freeup_idle));
    if (onlyOnPhone > 0)
      text +=
          "\n\n"
              + getResources()
                  .getQuantityString(R.plurals.freeup_only_on_phone, onlyOnPhone, onlyOnPhone);
    if (leftovers > 0 && !running)
      text +=
          "\n\n"
              + getResources().getQuantityString(R.plurals.freeup_leftovers, leftovers, leftovers);
    if (!review.isEmpty() && !running)
      text +=
          "\n\n"
              + getResources()
                  .getQuantityString(R.plurals.freeup_review, review.size(), review.size());
    if (otherAccount > 0)
      text +=
          "\n\n"
              + getResources()
                  .getQuantityString(R.plurals.freeup_other_account, otherAccount, otherAccount);
    if (cleanup > 0)
      text +=
          "\n\n"
              + getResources().getQuantityString(R.plurals.freeup_cleanup, cleanup, cleanup);
    reclaimStatus.setText(text);
    reclaimButton.setText(
        getString(
            running
                ? R.string.freeup_pause
                : leftovers > 0 && active == 0
                    ? R.string.freeup_finish_now
                    : R.string.freeup_resume));
    reclaimButton.setVisibility(running || active > 0 || leftovers > 0 ? View.VISIBLE : View.GONE);
    cleanupButton.setVisibility(cleanup > 0 && !running ? View.VISIBLE : View.GONE);
    reviewButton.setVisibility(!review.isEmpty() && !running ? View.VISIBLE : View.GONE);
    StringBuilder fingerprint = new StringBuilder();
    for (ReclaimEngine.Job job : jobs)
      fingerprint.append(job.key).append(job.state).append(job.message).append('|');
    if (fingerprint.toString().equals(renderedReclaim)) return;
    renderedReclaim = fingerprint.toString();
    reclaimRows.removeAllViews();
    for (ReclaimEngine.Job job : jobs) {
      TextView title = ui.text(
              getString(R.string.freeup_row_title, job.name, stateLabel(job.state)), 15, false);
      ui.space(title, 10, 0);
      reclaimRows.addView(title);
      TextView sub =
          ui.supporting(
              job.charged == null
                  ? job.message
                  : getString(R.string.freeup_row_detail, bytes(job.charged), job.message));
      reclaimRows.addView(sub);
    }
  }

  private void showItem(LibraryProtocol.Item item) {
    String why = ReclaimEngine.ineligible(item);
    StringBuilder info = new StringBuilder();
    info.append(
        getString(
            R.string.storage_detail_header,
            mediaTypeLabel(item),
            item.size() == null ? getString(R.string.storage_size_unknown) : bytes(item.size()),
            item.charged() == null
                ? getString(R.string.storage_use_unknown)
                : getString(R.string.storage_reported, bytes(item.charged()))));
    MaterialAlertDialogBuilder dialog =
        new MaterialAlertDialogBuilder(this).setTitle(item.name()).setNegativeButton(R.string.main_close, null);
    if (why != null) {
      info.append(getString(R.string.freeup_detail_cant, why));
      dialog.setMessage(info.toString()).show();
      return;
    }
    info.append(getString(R.string.freeup_detail_explain));
    info.append("\n\n").append(getString(R.string.freeup_detail_lost));
    if (item.extra().edited()) info.append(" ").append(getString(R.string.freeup_detail_edited));
    dialog
        .setMessage(info.toString())
        .setPositiveButton(
            item.extra().quality() == LibraryProtocol.Quality.REDUCED
                    && (item.charged() == null || item.charged() == 0)
                ? getString(R.string.freeup_replace_original)
                : getString(
                    R.string.freeup_button_size, bytes(item.charged() == null ? 0 : item.charged())),
            (d, w) -> beforeReclaim(() -> confirmReclaim(item)))
        .show();
  }

  private void updateSelection() {
    if (selectionBar == null) return;
    selectionBar.setVisibility(selecting ? View.VISIBLE : View.GONE);
    long total = 0;
    for (LibraryProtocol.Item item : chosen.values())
      if (item.charged() != null) total += item.charged();
    selectionInfo.setText(
        chosen.isEmpty()
            ? getString(R.string.storage_tap_to_select)
            : getString(R.string.storage_selected_info, chosen.size(), bytes(total)));
    freeSelectedButton.setEnabled(!chosen.isEmpty());
    freeSelectedButton.setText(
        chosen.isEmpty()
            ? getString(R.string.freeup_button)
            : getResources()
                .getQuantityString(R.plurals.freeup_button_count, chosen.size(), chosen.size()));
  }

  /** Every eligible item under the current filters, across all pages. */
  private void selectAllEligible() {
    String email = account.email();
    int q = quotaFilter, m = mediaFilter;
    io.execute(
        () -> {
          List<LibraryProtocol.Item> all = new ArrayList<>();
          try (InventoryDb db = new InventoryDb(this)) {
            for (int offset = 0; ; offset += 50) {
              List<LibraryProtocol.Item> page = db.list(email, q, m, offset);
              all.addAll(page.subList(0, Math.min(50, page.size())));
              if (page.size() <= 50) break;
            }
          }
          runOnUiThread(
              () -> {
                if (!alive() || !email.equals(account.email())) return;
                int skipped = 0;
                for (LibraryProtocol.Item item : all)
                  if (ReclaimEngine.ineligible(item) == null) chosen.put(item.key(), item);
                  else skipped++;
                updateSelection();
                renderedStorage = "";
                refresh();
                if (skipped > 0)
                  message(
                      getResources()
                          .getQuantityString(R.plurals.freeup_skipped, skipped, skipped));
              });
        });
  }

  private void confirmBatch() {
    if (chosen.isEmpty()) return;
    long total = 0;
    int reduced = 0, edited = 0;
    for (LibraryProtocol.Item item : chosen.values()) {
      if (item.charged() != null) total += item.charged();
      if (item.extra().quality() == LibraryProtocol.Quality.REDUCED) reduced++;
      if (item.extra().edited()) edited++;
    }
    String detail =
        getString(R.string.freeup_batch_body)
            + (edited > 0
                ? "\n\n"
                    + getResources()
                        .getQuantityString(R.plurals.freeup_batch_edited, edited, edited)
                : "")
            + (reduced > 0
                ? "\n\n"
                    + getResources()
                        .getQuantityString(R.plurals.freeup_batch_reduced, reduced, reduced)
                : "")
            + "\n\n"
            + getString(R.string.freeup_batch_footer);
    new MaterialAlertDialogBuilder(this)
        .setTitle(
            getResources()
                .getQuantityString(
                    R.plurals.freeup_batch_title, chosen.size(), bytes(total), chosen.size()))
        .setMessage(detail)
        .setPositiveButton(
            R.string.freeup_delete_reupload,
            (d, w) -> {
              String email = account.email();
              List<LibraryProtocol.Item> items = new ArrayList<>(chosen.values());
              io.execute(
                  () -> {
                    int added = 0;
                    try (ReclaimDb db = new ReclaimDb(this)) {
                      for (LibraryProtocol.Item item : items)
                        if (db.enqueue(ReclaimEngine.Job.of(email, item))) added++;
                    }
                    int queued = added;
                    runOnUiThread(
                        () -> {
                          if (!alive()) return;
                          chosen.clear();
                          selecting = false;
                          if (selectButton != null)
                            selectButton.setText(getString(R.string.storage_select_items));
                          updateSelection();
                          renderedStorage = "";
                          message(
                              getResources()
                                  .getQuantityString(R.plurals.freeup_queued, queued, queued));
                          startReclaim();
                        });
                  });
            })
        .setNegativeButton(R.string.main_cancel, null)
        .show();
  }

  /**
   * Google Photos with backup on deletes the phone copy of anything removed from the cloud. Free
   * up removes originals from the cloud, so it only starts once that backup is confirmed off.
   */
  private void beforeReclaim(Runnable next) {
    boolean confirmed = settings.photosBackupOffConfirmed() > 0;
    boolean evidence = settings.otherUploaderAt() > settings.photosBackupOffConfirmed();
    if (!googlePhotosActive() || (confirmed && !evidence)) {
      next.run();
      return;
    }
    new MaterialAlertDialogBuilder(this)
        .setTitle(R.string.freeup_gp_title)
        .setMessage(R.string.freeup_gp_message)
        .setPositiveButton(R.string.main_gp_open, (d, w) -> openGooglePhotosBackup())
        .setNegativeButton(
            R.string.freeup_gp_its_off,
            (d, w) -> {
              settings.confirmPhotosBackupOff();
              settings.clearOtherUploader();
              next.run();
            })
        .setNeutralButton(R.string.main_cancel, null)
        .show();
  }

  private void confirmReclaim(LibraryProtocol.Item item) {
    new MaterialAlertDialogBuilder(this)
        .setTitle(R.string.freeup_confirm_title)
        .setMessage(R.string.freeup_confirm_message)
        .setPositiveButton(
            R.string.freeup_delete_reupload,
            (d, w) -> {
              String email = account.email();
              io.execute(
                  () -> {
                    boolean added;
                    try (ReclaimDb db = new ReclaimDb(this)) {
                      added = db.enqueue(ReclaimEngine.Job.of(email, item));
                    }
                    runOnUiThread(
                        () -> {
                          if (!alive()) return;
                          if (!added) message(getString(R.string.freeup_already_queued));
                          startReclaim();
                        });
                  });
            })
        .setNegativeButton(R.string.main_cancel, null)
        .show();
  }

  private void startReclaim() {
    if (account.email().isEmpty()) {
      connect();
      return;
    }
    if (InventoryService.busy()) {
      message(getString(R.string.freeup_scan_running));
      return;
    }
    try {
      startForegroundService(new Intent(this, ReclaimService.class));
    } catch (RuntimeException e) {
      message(getString(R.string.freeup_start_failed));
    }
    renderedReclaim = "";
    refresh();
  }

  /**
   * Files without an owner are what an earlier installation leaves behind, but any app could have
   * written them. Upload them only after the user saw exactly which files they are.
   */
  private void reviewLeftovers() {
    List<ReclaimRecovery.Orphan> review = pendingReview;
    if (review.isEmpty()) return;
    StringBuilder list = new StringBuilder();
    for (ReclaimRecovery.Orphan o : review.subList(0, Math.min(30, review.size())))
      list.append(getString(R.string.freeup_review_item, ReclaimEngine.stagedName(o.name()), bytes(o.size())))
          .append("\n");
    if (review.size() > 30)
      list.append(getString(R.string.freeup_review_more, review.size() - 30)).append("\n");
    new MaterialAlertDialogBuilder(this)
        .setTitle(R.string.freeup_review_title)
        .setMessage(getString(R.string.freeup_review_message, list))
        .setPositiveButton(
            R.string.freeup_review_upload,
            (d, w) -> {
              Set<String> approved = settings.recoveryApproved();
              for (ReclaimRecovery.Orphan o : review) approved.add(o.uri().toString());
              settings.recoveryApproved(approved);
              startReclaim();
            })
        .setNegativeButton(R.string.main_not_now, null)
        .show();
  }

  /** Copies written by an earlier install need the user's consent before Android deletes them. */
  private void removeRecoveredCopies() {
    List<Uri> uris = new ArrayList<>();
    for (String uri : settings.recoveryCleanup()) uris.add(Uri.parse(uri));
    if (uris.isEmpty()) return;
    try {
      startIntentSenderForResult(
          android.provider.MediaStore.createDeleteRequest(getContentResolver(), uris)
              .getIntentSender(),
          42,
          null,
          0,
          0,
          0);
    } catch (Exception e) {
      message(getString(R.string.freeup_delete_request_failed));
    }
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == 42 && result == RESULT_OK) {
      settings.recoveryCleanup(Set.of());
      renderedReclaim = "";
      refresh();
    }
  }

  private void toggleReclaim() {
    if (ReclaimService.busy()) {
      startService(new Intent(this, ReclaimService.class).setAction("pause"));
      refresh();
    } else startReclaim();
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
    if (ReclaimService.busy()) {
      message(getString(R.string.storage_pause_freeup_first));
      return;
    }
    try {
      startForegroundService(new Intent(this, InventoryService.class));
    } catch (RuntimeException e) {
      message(getString(R.string.storage_start_failed));
    }
    refresh();
  }

  private void restartScan() {
    if (InventoryService.busy()) {
      message(getString(R.string.storage_pause_scan_first));
      return;
    }
    if (ReclaimService.busy()) {
      message(getString(R.string.storage_pause_freeup));
      return;
    }
    if (account.email().isEmpty()) {
      connect();
      return;
    }
    new MaterialAlertDialogBuilder(this)
        .setTitle(R.string.storage_fresh_title)
        .setMessage(R.string.storage_fresh_message)
        .setPositiveButton(
            R.string.storage_scan_again,
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
        .setNegativeButton(R.string.main_cancel, null)
        .show();
  }

  private void connect() {
    if (BackupEngine.busy() || InventoryService.busy() || ReclaimService.busy()) {
      message(getString(R.string.account_pause_first));
      return;
    }
    if (unfinishedReclaim()) return;
    startActivity(new Intent(this, SignInActivity.class));
  }

  /**
   * While an original exists only on this phone, its account must stay connected so the upload
   * can finish. Returns true (and explains) when account changes must wait.
   */
  private boolean unfinishedReclaim() {
    int unsafe;
    try (ReclaimDb db = new ReclaimDb(this)) {
      unsafe = db.unsafeAll();
    }
    if (unsafe == 0) return false;
    new MaterialAlertDialogBuilder(this)
        .setTitle(R.string.account_finish_title)
        .setMessage(
            getResources().getQuantityString(R.plurals.account_unfinished, unsafe, unsafe))
        .setPositiveButton(R.string.main_ok, null)
        .show();
    return true;
  }

  private void manageAccount() {
    if (account.email().isEmpty()) {
      connect();
      return;
    }
    String name = new ProfileStore(this).name(account.email());
    new MaterialAlertDialogBuilder(this)
        .setTitle(R.string.account_title)
        .setItems(
            new String[] {
              name.isBlank() ? account.email() : name + "\n" + account.email(),
              getString(R.string.account_refresh_photo),
              getString(R.string.account_reconnect),
              getString(R.string.account_disconnect)
            },
            (d, which) -> {
              if (which == 1) refreshProfile(true);
              else if (which == 2) connect();
              else if (which == 3) disconnect();
            })
        .setNegativeButton(R.string.main_close, null)
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
    String label =
        email.isEmpty()
            ? getString(R.string.main_connect_account)
            : getString(R.string.account_description, email);
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
          // Profile access never changes the working backup sign-in. A failure keeps a fixed,
          // secret-free label so a refused lookup can be diagnosed; null means the user stopped it.
          String failure = null;
          try {
            store.refresh(email, account.load(), profileStop, force);
          } catch (ProfileApi.Failure f) {
            failure = f.label();
          } catch (java.net.SocketTimeoutException e) {
            failure = "TIMEOUT";
          } catch (java.io.InterruptedIOException e) {
            failure = "";
          } catch (Exception e) {
            failure = "NETWORK";
          }
          var bitmap = store.cached(email);
          String label = failure;
          runOnUiThread(
              () -> {
                profileLoading = false;
                if (!alive()) return;
                if (!email.equals(account.email())) {
                  refreshProfile(false);
                  return;
                }
                showProfile(email, bitmap);
                if (force && label == null) message(getString(R.string.account_profile_refreshed));
                else if (force && !label.isEmpty())
                  message(getString(R.string.profile_failed, label));
              });
        });
  }

  private void disconnect() {
    if (unfinishedReclaim()) return;
    new MaterialAlertDialogBuilder(this)
        .setTitle(R.string.account_disconnect_title)
        .setMessage(R.string.account_disconnect_message)
        .setPositiveButton(
            R.string.account_disconnect,
            (d, w) -> {
              String old = account.email();
              settings.enabled(false);
              BackupEngine.cancel();
              Scheduler.configure(this);
              stopService(new Intent(this, BackupService.class));
              stopService(new Intent(this, InventoryService.class));
              stopService(new Intent(this, ReclaimService.class));
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
                            settings.status(getString(R.string.account_disconnected_status));
                            draw();
                          }
                        });
                  });
            })
        .setNegativeButton(R.string.main_cancel, null)
        .show();
  }

  private void permissions() {
    new MaterialAlertDialogBuilder(this)
        .setTitle(R.string.main_perm_title)
        .setMessage(R.string.main_perm_message)
        .setPositiveButton(
            R.string.main_continue,
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
            R.string.main_app_settings,
            (d, w) ->
                startActivity(
                    new Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()))))
        .setNegativeButton(R.string.main_cancel, null)
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
    message(getString(R.string.backup_finding_folders));
    io.execute(
        () -> {
          try {
            List<MediaScanner.Folder> found = new MediaScanner(this).folders();
            runOnUiThread(
                () -> {
                  if (!alive()) return;
                  if (found.isEmpty()) {
                    message(getString(R.string.backup_no_folders));
                    return;
                  }
                  Set<String> selected = settings.folders();
                  CharSequence[] labels = new CharSequence[found.size()];
                  boolean[] checked = new boolean[found.size()];
                  for (int i = 0; i < found.size(); i++) {
                    MediaScanner.Folder folder = found.get(i);
                    labels[i] =
                        getResources()
                            .getQuantityString(
                                R.plurals.backup_folder_label,
                                folder.count(),
                                folder.label(),
                                folder.count());
                    checked[i] = selected.contains(folder.key());
                  }
                  new MaterialAlertDialogBuilder(this)
                      .setTitle(R.string.backup_folders)
                      .setMultiChoiceItems(
                          labels, checked, (dialog, which, on) -> checked[which] = on)
                      .setPositiveButton(
                          R.string.backup_save_folders,
                          (dialog, which) -> {
                            Set<String> choices = new HashSet<>();
                            for (int i = 0; i < checked.length; i++)
                              if (checked[i]) choices.add(found.get(i).key());
                            if (!choices.equals(settings.folders())) settings.lastRun(0);
                            settings.folders(choices);
                            Scheduler.configure(this);
                            refresh();
                          })
                      .setNegativeButton(R.string.main_cancel, null)
                      .show();
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  if (alive()) message(getString(R.string.backup_folders_failed));
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
            String version = installedVersionName();
            String text = getString(R.string.about_text, version, license);
            runOnUiThread(
                () -> {
                  if (alive())
                    new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.about_title)
                        .setMessage(text)
                        .setPositiveButton(R.string.main_close, null)
                        .show();
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  if (alive()) message(getString(R.string.about_failed));
                });
          }
        });
  }

  private void message(String text) {
    Toast.makeText(this, text, Toast.LENGTH_LONG).show();
  }
}
