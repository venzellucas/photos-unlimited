package app.photos.unlimited;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.net.http.SslError;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dedicated Google-hosted sign-in. No JavaScript bridge, password scraping, or credential logging.
 */
public final class SignInActivity extends ThemedActivity {
  private WebView web;
  private TextView status;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  private final AtomicBoolean stop = new AtomicBoolean();
  private boolean connecting;
  private final Runnable poll =
      new Runnable() {
        public void run() {
          capture();
          if (!connecting) handler.postDelayed(this, 1000);
        }
      };

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(new Ui(this).surface());
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
          v.setPadding(16, bars.top + 12, 16, bars.bottom + 12);
          return insets;
        });
    status = new TextView(this);
    status.setText("Google sign-in · accounts.google.com");
    status.setTextSize(16);
    status.setPadding(8, 8, 8, 12);
    root.addView(status);
    TextView help = new TextView(this);
    help.setText(
        "Try the account already on your phone, or sign in below. The phone-account option is"
            + " a diagnostic test; it does not connect backup yet.");
    help.setPadding(8, 0, 8, 12);
    root.addView(help);
    Button phoneAccount = new com.google.android.material.button.MaterialButton(this);
    phoneAccount.setText("Test account on this phone");
    phoneAccount.setAllCaps(false);
    root.addView(phoneAccount);
    phoneAccount.setOnClickListener(
        v -> startActivity(new Intent(this, AccountProbeActivity.class)));
    Button fallback = new com.google.android.material.button.MaterialButton(this);
    fallback.setText("Use browser setup token");
    fallback.setAllCaps(false);
    root.addView(fallback);
    fallback.setOnClickListener(v -> importToken());
    web = new WebView(this);
    WebSettings settings = web.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(true);
    settings.setAllowFileAccess(false);
    settings.setAllowContentAccess(false);
    settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    settings.setSafeBrowsingEnabled(true);
    settings.setSaveFormData(false);
    CookieManager.getInstance().setAcceptCookie(true);
    CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
    WebView.setWebContentsDebuggingEnabled(false);
    web.setWebViewClient(
        new WebViewClient() {
          @Override
          public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            String host = uri.getHost();
            return !"https".equals(uri.getScheme())
                || host == null
                || !Set.of("accounts.google.com", "myaccount.google.com", "www.google.com")
                    .contains(host);
          }

          @Override
          public void onPageFinished(WebView view, String url) {
            capture();
          }

          @Override
          public void onReceivedSslError(
              WebView view, SslErrorHandler errorHandler, SslError error) {
            errorHandler.cancel();
            status.setText("Could not verify Google's secure connection.");
          }
        });
    root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
    setContentView(root);
    CookieManager.getInstance()
        .removeAllCookies(removed -> web.loadUrl("https://accounts.google.com/EmbeddedSetup"));
    handler.postDelayed(poll, 1000);
  }

  private void capture() {
    if (connecting || stop.get()) return;
    String cookies = CookieManager.getInstance().getCookie("https://accounts.google.com");
    if (cookies == null) return;
    for (String cookie : cookies.split(";")) {
      String value = cookie.trim();
      if (value.startsWith("oauth_token=")) {
        connect(value.substring(12));
        break;
      }
    }
  }

  private void connect(String token) {
    if (connecting) return;
    connecting = true;
    handler.removeCallbacks(poll);
    status.setText("Connecting to your Google Photos library…");
    web.setVisibility(View.INVISIBLE);
    io.execute(
        () -> {
          try {
            String credential = PhotosApi.connect(token, stop);
            PhotosApi.checkStopped(stop);
            AccountStore store = new AccountStore(this);
            String old = store.email();
            store.save(credential);
            Settings settings = new Settings(this);
            if (!old.equals(store.email())) {
              settings.enabled(false);
              settings.lastRun(0);
            }
            settings.failure("");
            settings.status("Account connected. Choose folders, then start your first backup.");
            runOnUiThread(
                () -> {
                  CookieManager.getInstance().removeAllCookies(null);
                  setResult(RESULT_OK);
                  finish();
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  if (isFinishing() || isDestroyed()) return;
                  connecting = false;
                  web.setVisibility(View.VISIBLE);
                  status.setText(
                      e instanceof PhotosApi.ApiException
                          ? e.getMessage()
                          : "Couldn't connect. Try a fresh browser setup token.");
                  CookieManager.getInstance().removeAllCookies(null);
                });
          }
        });
  }

  private void importToken() {
    if (connecting) return;
    EditText token = new EditText(this);
    token.setSingleLine(true);
    token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    token.setHint("Google setup token");
    token.setSaveEnabled(false);
    token.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(32, 12, 32, 4);
    TextView text = new TextView(this);
    text.setText(
        "On a computer, visit accounts.google.com/EmbeddedSetup and sign in. In browser developer"
            + " tools → Application/Storage → Cookies → accounts.google.com, copy only the"
            + " oauth_token value. Enter it here on your phone.\n\n"
            + "Keep this token private. It provides account access; never send it in chat.");
    layout.addView(text);
    layout.addView(token);
    new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
        .setTitle("Browser setup")
        .setView(layout)
        .setPositiveButton(
            "Connect",
            (dialog, which) -> {
              String value = token.getText().toString();
              token.setText("");
              connect(value);
            })
        .setNeutralButton(
            "Open setup page",
            (d, w) ->
                startActivity(
                    new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://accounts.google.com/EmbeddedSetup"))))
        .setNegativeButton("Cancel", (d, w) -> token.setText(""))
        .show();
  }

  @Override
  public void onDestroy() {
    stop.set(true);
    handler.removeCallbacks(poll);
    io.shutdownNow();
    if (web != null) {
      web.stopLoading();
      web.clearCache(true);
      web.destroy();
    }
    CookieManager.getInstance().removeAllCookies(null);
    super.onDestroy();
  }
}
