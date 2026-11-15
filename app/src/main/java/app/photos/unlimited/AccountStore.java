package app.photos.unlimited;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Account secret stays encrypted in app-private storage; excluded from Android backup. */
public final class AccountStore {
  private static final String KEY = "photos-unlimited-account-v1";
  private final Context context;

  public AccountStore(Context context) {
    this.context = context.getApplicationContext();
  }

  public synchronized void save(String credentials) throws IOException {
    try {
      String previous = email();
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key());
      String value =
          Base64.getEncoder().encodeToString(cipher.getIV())
              + ":"
              + Base64.getEncoder()
                  .encodeToString(cipher.doFinal(credentials.getBytes(StandardCharsets.UTF_8)));
      if (!context
          .getSharedPreferences("account", 0)
          .edit()
          .putString("sealed", value)
          .putString("email", PhotosApi.email(credentials))
          .commit()) throw new IOException("Could not save account");
      if (!previous.equals(email())) ProfileStore.clear(context);
    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      throw new IOException("Could not securely save your account");
    }
  }

  public synchronized String load() throws IOException {
    String value = context.getSharedPreferences("account", 0).getString("sealed", "");
    if (value.isEmpty()) return "";
    try {
      String[] parts = value.split(":", 2);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.DECRYPT_MODE,
          key(),
          new GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])));
      return new String(
          cipher.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IOException("Your saved sign-in is unavailable. Reconnect your Google account.");
    }
  }

  public String email() {
    return context.getSharedPreferences("account", 0).getString("email", "");
  }

  public void clear() {
    context.getSharedPreferences("account", 0).edit().clear().commit();
    ProfileStore.clear(context);
  }

  private SecretKey key() throws Exception {
    KeyStore store = KeyStore.getInstance("AndroidKeyStore");
    store.load(null);
    if (!store.containsAlias(KEY)) {
      KeyGenerator generator =
          KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
      generator.init(
          new KeyGenParameterSpec.Builder(
                  KEY, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
              .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
              .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
              .build());
      generator.generateKey();
    }
    return (SecretKey) store.getKey(KEY, null);
  }
}
