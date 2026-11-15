package app.photos.unlimited;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class AccountProbeTest {
  @Test
  public void issuedTokenMakesOnlySyntheticReadOnlyLookup() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    PhotosApi.Transport transport =
        (url, method, headers, body, length, stop) -> {
          calls.incrementAndGet();
          assertEquals(PhotosApi.RPC + "5084965799730810217", url);
          assertEquals("POST", method);
          assertEquals("Bearer synthetic-issued-token", headers.get("Authorization"));
          assertFalse(headers.containsKey("app"));
          ByteArrayOutputStream bytes = new ByteArrayOutputStream();
          body.write(bytes);
          byte[] outer = Proto.fields(bytes.toByteArray(), 1).get(0);
          byte[] inner = Proto.fields(outer, 1).get(0);
          assertArrayEquals(new byte[20], Proto.fields(inner, 1).get(0));
          assertEquals(bytes.size(), length);
          return new PhotosApi.Response(new byte[0], Map.of());
        };
    assertEquals(
        "PHOTOS_LOOKUP_ACCEPTED",
        AccountProbe.checkPhotos("synthetic-issued-token", transport, new AtomicBoolean()));
    assertEquals(1, calls.get());
  }

  @Test
  public void tokenDoesNotCountAsPhotosAccessWhenLookupRefusesIt() throws Exception {
    PhotosApi.Transport transport =
        (url, method, headers, body, length, stop) -> {
          throw new PhotosApi.ApiException("synthetic secret raw error", false, true, 403);
        };
    assertEquals(
        "PHOTOS_HTTP_403", AccountProbe.checkPhotos("fake-token", transport, new AtomicBoolean()));
    assertFalse(AccountProbe.explanation("PHOTOS_HTTP_403").contains("secret"));
  }

  @Test
  public void reportsOnlyAllowlistedAuthenticatorErrors() {
    assertEquals(
        "APP_NOT_REGISTERED",
        AccountProbe.authFailure(
            new IOException("Email=secret@example.com Token=secret UNREGISTERED_ON_API_CONSOLE")));
    assertEquals(
        "SCOPE_REJECTED", AccountProbe.authFailure(new IOException("INVALID_SCOPE: secret")));
    assertEquals(
        "GOOGLE_REAUTH_REQUIRED", AccountProbe.authFailure(new IOException("NeedsBrowser secret")));
    assertEquals(
        "AUTHENTICATOR_FAILURE",
        AccountProbe.authFailure(new IOException("Email=secret@example.com Token=secret")));
    assertEquals(
        "APP_NOT_REGISTERED",
        AccountProbe.authFailure(
            new IOException("wrapped", new IOException("UNREGISTERED_ON_API_CONSOLE"))));
  }

  @Test
  public void googleStatusCodesDistinguishConfigurationAndTransientFailures() {
    assertEquals(
        "GOOGLE_DEVELOPER_ERROR", AccountProbe.apiFailure(10, new IOException("redacted")));
    assertEquals("NETWORK_FAILURE", AccountProbe.apiFailure(7, new IOException("redacted")));
    assertEquals(
        "GOOGLE_API_FAILURE",
        AccountProbe.apiFailure(999, new IOException("Email=private@example.com Token=secret")));
    assertEquals(
        "APP_REGISTRATION_CONFIGURATION",
        AccountProbe.apiFailure(
            16,
            new IOException(
                "Developer console is not set up correctly [28444] private@example.com secret")));
    assertEquals(
        "APP_NOT_REGISTERED",
        AccountProbe.apiFailure(10, new IOException("UNREGISTERED_ON_API_CONSOLE secret")));
  }

  @Test
  public void tokenForWrongOrDeclinedScopeCannotPassPhotosGate() {
    assertFalse(
        AccountProbe.requestedScopeGranted(true, java.util.List.of(AccountProbe.BASIC_SCOPE)));
    assertFalse(AccountProbe.requestedScopeGranted(true, java.util.List.of()));
    assertFalse(AccountProbe.requestedScopeGranted(true, null));
    assertTrue(
        AccountProbe.requestedScopeGranted(true, java.util.List.of(AccountProbe.PHOTOS_SCOPE)));
    assertTrue(AccountProbe.requestedScopeGranted(false, java.util.List.of("email")));
  }

  @Test
  public void malformedResponseDoesNotReportSuccess() throws Exception {
    PhotosApi.Transport transport =
        (url, method, headers, body, length, stop) ->
            new PhotosApi.Response(
                "<html>not protobuf</html>".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                Map.of());
    assertEquals(
        "PHOTOS_UNEXPECTED_RESPONSE",
        AccountProbe.checkPhotos("fake-token", transport, new AtomicBoolean()));
  }

  @Test
  public void cancellationAndInvalidTokenNeverReachNetwork() throws Exception {
    PhotosApi.Transport forbidden =
        (url, method, headers, body, length, stop) -> {
          throw new AssertionError("Unexpected network request");
        };
    assertThrows(
        InterruptedIOException.class,
        () -> AccountProbe.checkPhotos("fake-token", forbidden, new AtomicBoolean(true)));
    assertEquals(
        "INVALID_TOKEN_RESULT",
        AccountProbe.checkPhotos("token\r\nInjected: header", forbidden, new AtomicBoolean()));
    assertEquals(
        "INVALID_TOKEN_RESULT", AccountProbe.checkPhotos("", forbidden, new AtomicBoolean()));
  }
}
