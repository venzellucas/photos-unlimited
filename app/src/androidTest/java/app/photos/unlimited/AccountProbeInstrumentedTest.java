package app.photos.unlimited;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AccountProbeInstrumentedTest {
  @Test
  public void googleAuthorizationRequestUsesSelectedAccountAndOnlyRequestedScope() {
    var account = new android.accounts.Account("fixture@example.com", "com.google");
    var photos = AccountProbeActivity.authorizationRequest(account, true);
    assertEquals(account, photos.getAccount());
    assertEquals(1, photos.getRequestedScopes().size());
    assertEquals(
        "https://www.googleapis.com/auth/photos.native",
        photos.getRequestedScopes().get(0).getScopeUri());
    var basic = AccountProbeActivity.authorizationRequest(account, false);
    assertEquals(account, basic.getAccount());
    assertEquals(1, basic.getRequestedScopes().size());
    assertEquals(
        "https://www.googleapis.com/auth/userinfo.email",
        basic.getRequestedScopes().get(0).getScopeUri());
  }

  @Test
  public void frameworkChooserFiltersGoogleAccountsAndResolvesToSystem() {
    Context context = ApplicationProvider.getApplicationContext();
    Intent chooser = AccountProbeActivity.chooserIntent();
    assertArrayEquals(
        new String[] {"com.google"}, chooser.getStringArrayExtra("allowableAccountTypes"));
    assertNotNull(chooser.getComponent());
    assertEquals("android", chooser.getComponent().getPackageName());
    assertNotNull(
        context.getPackageManager().resolveActivity(chooser, PackageManager.MATCH_DEFAULT_ONLY));
    assertNull(chooser.getStringExtra("authTokenType"));
  }

  @Test
  public void openingProbePreservesSavedAccountAndBackupSettings() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    AccountStore store = new AccountStore(context);
    String previous = store.load();
    boolean enabled = new Settings(context).enabled();
    String fixture = "Email=probe-fixture%40example.com&Token=synthetic-value&androidId=1234";
    var instrumentation = InstrumentationRegistry.getInstrumentation();
    Activity activity = null;
    try {
      store.save(fixture);
      activity =
          instrumentation.startActivitySync(
              new Intent(context, AccountProbeActivity.class)
                  .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      instrumentation.waitForIdleSync();
      assertEquals(fixture, store.load());
      assertEquals(enabled, new Settings(context).enabled());
    } finally {
      if (activity != null) {
        Activity current = activity;
        instrumentation.runOnMainSync(current::finish);
      }
      if (previous.isEmpty()) store.clear();
      else store.save(previous);
    }
  }
}
