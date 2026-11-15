package app.photos.unlimited;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** User-initiated probe of the phone's Google authenticator, under this app's actual identity. */
public final class AccountProbeActivity extends ThemedActivity {
  private static final int CHOOSE_ACCOUNT = 410;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  private static final int GOOGLE_CONSENT = 411;
  private boolean awaitingConsent;

  private enum Stage {
    IDLE,
    CHOOSER,
    AUTHORIZE_REQUEST,
    GOOGLE_CONSENT,
    TOKEN_RESULT,
    PHOTOS_LOOKUP
  }

  private Stage stage = Stage.IDLE;
  private String photosDetail = "NOT_RUN", basicDetail = "NOT_RUN";
  private String detail = "NOT_RUN";
  private AtomicBoolean stop = new AtomicBoolean();
  private Button photos, basic, copy;
  private TextView status;
  private boolean busy, photosTest, awaitingChooser;
  private int generation;
  private String photosResult = "NOT_RUN", basicResult = "NOT_RUN";
  private String fingerprint = "unavailable",
      gmsVersion = "unavailable",
      appVersion = "unavailable";

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    readAppIdentity();
    if (saved != null) {
      photosResult = saved.getString("photosResult", "NOT_RUN");
      basicResult = saved.getString("basicResult", "NOT_RUN");
      photosTest = saved.getBoolean("photosTest");
      awaitingChooser = saved.getBoolean("awaitingChooser");
      awaitingConsent = saved.getBoolean("awaitingConsent");
      photosDetail = saved.getString("photosDetail", "NOT_RUN");
      basicDetail = saved.getString("basicDetail", "NOT_RUN");
      busy = awaitingChooser || awaitingConsent;
      stage = awaitingConsent ? Stage.GOOGLE_CONSENT : Stage.CHOOSER;
    }
    ScrollView scroll = new ScrollView(this);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(new Ui(this).surface());
    root.setPadding(dp(24), dp(20), dp(24), dp(24));
    scroll.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
          v.setPadding(0, bars.top, 0, bars.bottom);
          return insets;
        });
    TextView title = new TextView(this);
    title.setText("Test your phone account");
    title.setTextSize(26);
    title.setTextColor(new Ui(this).ink());
    root.addView(title);
    TextView help = new TextView(this);
    help.setText(
        "Start with the basic Google access test. If it succeeds, test Photos access. Choose the"
            + " account already on this phone when prompted.\n\n"
            + "Google handles consent and account verification. This diagnostic does not upload"
            + " photos or connect automatic backup.");
    help.setTextSize(16);
    help.setPadding(0, dp(16), 0, dp(20));
    root.addView(help);
    photos = button(root, "Test Photos access");
    basic = button(root, "Test basic Google access");
    photos.setOnClickListener(v -> choose(true));
    basic.setOnClickListener(v -> choose(false));
    status = new TextView(this);
    status.setTextSize(16);
    status.setPadding(0, dp(20), 0, dp(20));
    root.addView(status);
    copy = button(root, "Copy diagnostic result");
    copy.setOnClickListener(
        v -> {
          getSystemService(ClipboardManager.class)
              .setPrimaryClip(ClipData.newPlainText("Account test", report()));
          Toast.makeText(this, "Copied: no email, password, or tokens", Toast.LENGTH_SHORT).show();
        });
    button(root, "Back").setOnClickListener(v -> finish());
    scroll.addView(root);
    setContentView(scroll);
    show(
        saved == null
            ? "Ready to test."
            : "Previous results restored. Restart an interrupted test.");
    if (awaitingConsent) armTimeout(generation);
  }

  static Intent chooserIntent() {
    return AccountManager.newChooseAccountIntent(
        null,
        null,
        new String[] {"com.google"},
        "Choose an account for the Photos access test",
        null,
        null,
        null);
  }

  private void choose(boolean photosAccess) {
    if (busy) return;
    photosTest = photosAccess;
    stop = new AtomicBoolean();
    generation++;
    detail = "NONE";
    stage = Stage.CHOOSER;
    busy = true;
    show("Choose an account in Android's account chooser…");
    boolean available = false;
    for (var authenticator : AccountManager.get(this).getAuthenticatorTypes()) {
      if ("com.google".equals(authenticator.type)) available = true;
    }
    if (!available) {
      complete("NO_GOOGLE_AUTHENTICATOR");
      return;
    }
    try {
      awaitingChooser = true;
      startActivityForResult(chooserIntent(), CHOOSE_ACCOUNT);
    } catch (RuntimeException unavailable) {
      awaitingChooser = false;
      complete("CHOOSER_UNAVAILABLE");
    }
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == GOOGLE_CONSENT && awaitingConsent) {
      awaitingConsent = false;
      stage = Stage.GOOGLE_CONSENT;
      if (data == null) {
        detail = "stage=GOOGLE_CONSENT; resultCode=" + result + "; data=ABSENT";
        complete(result == RESULT_CANCELED ? "CANCELED" : "EMPTY_AUTHORIZATION_RESULT");
        return;
      }
      try {
        // Parse error-bearing data even when Android reports RESULT_CANCELED.
        consumeGoogleResult(
            Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data), false);
      } catch (ApiException failure) {
        googleFailure(failure);
      } catch (RuntimeException failure) {
        runtimeFailure(failure);
      }
      return;
    }
    if (request != CHOOSE_ACCOUNT || !awaitingChooser) return;
    awaitingChooser = false;
    if (result != RESULT_OK || data == null) {
      complete("CANCELED");
      return;
    }
    String name = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
    String type = data.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE);
    if (name == null || name.isBlank() || !"com.google".equals(type)) {
      complete("INVALID_ACCOUNT_RESULT");
      return;
    }
    requestToken(new Account(name, type));
  }

  static AuthorizationRequest authorizationRequest(Account account, boolean photos) {
    return AuthorizationRequest.builder()
        .setAccount(account)
        .setRequestedScopes(
            List.of(new Scope(photos ? AccountProbe.PHOTOS_SCOPE : AccountProbe.BASIC_SCOPE)))
        .setOptOutIncludingGrantedScopes(true)
        .build();
  }

  private void requestToken(Account account) {
    int current = generation;
    stage = Stage.AUTHORIZE_REQUEST;
    show(
        "Requesting "
            + (photosTest ? "Photos" : "basic account")
            + " access through Google Play services…");
    armTimeout(current);
    try {
      Identity.getAuthorizationClient(this)
          .authorize(authorizationRequest(account, photosTest))
          .addOnSuccessListener(
              result -> {
                if (!active(current)) return;
                try {
                  consumeGoogleResult(result, true);
                } catch (RuntimeException failure) {
                  runtimeFailure(failure);
                }
              })
          .addOnFailureListener(
              failure -> {
                if (!active(current)) return;
                if (failure instanceof ApiException apiFailure) googleFailure(apiFailure);
                else {
                  detail =
                      "stage="
                          + stage
                          + "; type=NON_API_EXCEPTION; hint="
                          + AccountProbe.authFailure(failure);
                  complete("GOOGLE_API_FAILURE");
                }
              });
    } catch (RuntimeException failure) {
      runtimeFailure(failure);
    }
  }

  private void consumeGoogleResult(AuthorizationResult result, boolean mayResolve) {
    if (result.hasResolution()) {
      if (!mayResolve || result.getPendingIntent() == null) {
        detail = "stage=" + stage + "; result=UNRESOLVED";
        complete("GOOGLE_RESOLUTION_FAILED");
        return;
      }
      stage = Stage.GOOGLE_CONSENT;
      detail = "stage=GOOGLE_CONSENT; resolution=REQUESTED";
      awaitingConsent = true;
      show("Continue in Google's authorization screen…");
      try {
        startIntentSenderForResult(
            result.getPendingIntent().getIntentSender(), GOOGLE_CONSENT, null, 0, 0, 0);
      } catch (IntentSender.SendIntentException failure) {
        awaitingConsent = false;
        detail = "stage=GOOGLE_CONSENT; type=INTENT_SENDER_FAILURE";
        complete("GOOGLE_RESOLUTION_FAILED");
      }
      return;
    }
    stage = Stage.TOKEN_RESULT;
    boolean granted = AccountProbe.requestedScopeGranted(photosTest, result.getGrantedScopes());
    String token = result.getAccessToken();
    boolean present = token != null && !token.isBlank();
    detail = "stage=TOKEN_RESULT; requestedScopeGranted=" + granted + "; tokenPresent=" + present;
    if (!granted) {
      complete("SCOPE_NOT_GRANTED");
      return;
    }
    if (!present) {
      complete("EMPTY_AUTHORIZATION_RESULT");
      return;
    }
    if (!photosTest) {
      complete("BASIC_TOKEN_ISSUED");
      return;
    }
    stage = Stage.PHOTOS_LOOKUP;
    detail = "stage=PHOTOS_LOOKUP; requestedScopeGranted=true; tokenPresent=true";
    show("Token issued. Checking access to the Photos service…");
    int current = generation;
    AtomicBoolean canceled = stop;
    io.execute(
        () -> {
          String outcome;
          try {
            outcome = AccountProbe.checkPhotos(token, new PhotosApi.Network(), canceled);
          } catch (IOException failure) {
            outcome = "NETWORK_FAILURE";
          } catch (RuntimeException failure) {
            outcome = "PHOTOS_CHECK_FAILED";
          }
          String safeOutcome = outcome;
          runOnUiThread(
              () -> {
                if (active(current)) complete(safeOutcome);
              });
        });
  }

  private void googleFailure(ApiException failure) {
    String result = AccountProbe.apiFailure(failure.getStatusCode(), failure);
    detail =
        "stage="
            + stage
            + "; type=API_EXCEPTION; statusCode="
            + failure.getStatusCode()
            + "; hint="
            + AccountProbe.authFailure(failure);
    complete(result);
  }

  private void runtimeFailure(RuntimeException failure) {
    String kind =
        failure instanceof SecurityException
            ? "SECURITY_EXCEPTION"
            : failure instanceof IllegalArgumentException
                ? "ILLEGAL_ARGUMENT"
                : failure instanceof IllegalStateException ? "ILLEGAL_STATE" : "RUNTIME_EXCEPTION";
    detail = "stage=" + stage + "; type=" + kind;
    complete(
        failure instanceof SecurityException ? "DEVICE_ACCESS_DENIED" : "CLIENT_RUNTIME_FAILURE");
  }

  private void armTimeout(int current) {
    main.removeCallbacksAndMessages(null);
    main.postDelayed(
        () -> {
          if (!active(current)) return;
          generation++;
          stop.set(true);
          detail = "stage=" + stage + "; type=TIMEOUT";
          complete("TIMED_OUT");
        },
        180000);
  }

  private boolean active(int current) {
    return current == generation && busy && !stop.get() && !isFinishing() && !isDestroyed();
  }

  private void complete(String result) {
    main.removeCallbacksAndMessages(null);
    busy = false;
    awaitingChooser = false;
    awaitingConsent = false;
    if (photosTest) {
      photosResult = result;
      photosDetail = detail;
    } else {
      basicResult = result;
      basicDetail = detail;
    }
    show(AccountProbe.explanation(result));
  }

  private void show(String message) {
    status.setText(message + "\n\nPhotos: " + photosResult + "\nBasic: " + basicResult);
    photos.setEnabled(!busy);
    basic.setEnabled(!busy);
    copy.setEnabled(!busy);
  }

  private String report() {
    return "Photos Unlimited account test "
        + appVersion
        + "\nAndroid API: "
        + android.os.Build.VERSION.SDK_INT
        + "\nGoogle Play services: "
        + gmsVersion
        + "\nApp: "
        + getPackageName()
        + "\nSigning SHA-1: "
        + fingerprint
        + "\nMethod: Google Identity AuthorizationClient (actual app identity)"
        + "\nPhotos: "
        + photosResult
        + "\nPhotos detail: "
        + photosDetail
        + "\nBasic: "
        + basicResult
        + "\nBasic detail: "
        + basicDetail
        + "\nNo account identifiers or credentials included. No uploads performed by this test.";
  }

  private void readAppIdentity() {
    try {
      var info =
          getPackageManager()
              .getPackageInfo(getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
      appVersion = info.versionName;
      byte[] digest =
          MessageDigest.getInstance("SHA-1")
              .digest(info.signingInfo.getApkContentsSigners()[0].toByteArray());
      StringBuilder hex = new StringBuilder();
      for (byte b : digest) {
        if (hex.length() > 0) hex.append(':');
        hex.append(String.format(java.util.Locale.ROOT, "%02X", b & 255));
      }
      fingerprint = hex.toString();
    } catch (Exception ignored) {
      /* Only public app metadata; no authenticator errors. */
    }
    try {
      gmsVersion = getPackageManager().getPackageInfo("com.google.android.gms", 0).versionName;
    } catch (PackageManager.NameNotFoundException ignored) {
    }
  }

  private Button button(LinearLayout root, String label) {
    Button button = new com.google.android.material.button.MaterialButton(this);
    button.setText(label);
    button.setAllCaps(false);
    root.addView(button, new LinearLayout.LayoutParams(-1, -2));
    return button;
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    super.onSaveInstanceState(state);
    state.putBoolean("photosTest", photosTest);
    state.putBoolean("awaitingChooser", awaitingChooser);
    state.putBoolean("awaitingConsent", awaitingConsent);
    state.putString("photosDetail", photosDetail);
    state.putString("basicDetail", basicDetail);
    state.putString(
        "photosResult",
        busy && !awaitingChooser && !awaitingConsent && photosTest ? "INTERRUPTED" : photosResult);
    state.putString(
        "basicResult",
        busy && !awaitingChooser && !awaitingConsent && !photosTest ? "INTERRUPTED" : basicResult);
  }

  @Override
  protected void onDestroy() {
    generation++;
    stop.set(true);
    main.removeCallbacksAndMessages(null);
    io.shutdownNow();
    super.onDestroy();
  }
}
