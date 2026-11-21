package app.photos.unlimited;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import org.junit.Test;

/** What may be stored as an upload error or shown as the backup status. */
public class SafeMessageTest {
  private static final String UNCONFIRMED = Text.get(R.string.backup_error_unconfirmed);

  @Test
  public void onlyMessagesWrittenForTheUserPassThrough() {
    assertEquals("shown", BackupEngine.safeMessage(new UserFacingException("shown")));
    assertEquals(
        "signed out",
        BackupEngine.safeMessage(new PhotosApi.ApiException("signed out", true, false)));
  }

  @Test
  public void otherMessagesNeverReachStorage() {
    // The old English whitelist let these through by their first word; now the type decides.
    assertEquals(UNCONFIRMED, BackupEngine.safeMessage(new IOException("File at https://x/y")));
    assertEquals(UNCONFIRMED, BackupEngine.safeMessage(new IOException("Google host example.invalid")));
    assertEquals(UNCONFIRMED, BackupEngine.safeMessage(new IOException((String) null)));
    assertEquals(UNCONFIRMED, BackupEngine.safeMessage(new UserFacingException(null)));
  }

  @Test
  public void networkFailuresGetTheirOwnFixedText() {
    assertEquals(
        Text.get(R.string.backup_error_timeout),
        BackupEngine.safeMessage(new java.net.SocketTimeoutException("https://private")));
    assertEquals(
        Text.get(R.string.backup_error_offline),
        BackupEngine.safeMessage(new java.net.UnknownHostException("private.example")));
    assertEquals(
        Text.get(R.string.backup_error_tls),
        BackupEngine.safeMessage(new javax.net.ssl.SSLException("details")));
  }

  @Test
  public void reasonsShowTranslatedMessagesOrOnlyTheType() {
    assertEquals("shown", Text.reason(new UserFacingException("shown")));
    assertEquals("IOException", Text.reason(new IOException("https://private/url")));
    assertEquals("UserFacingException", Text.reason(new UserFacingException(null)));
  }
}
