package app.photos.unlimited;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Read-only native-account experiment. Never exchanges or persists a master credential. */
public final class AccountProbe {
  static final String PHOTOS_SCOPE = "https://www.googleapis.com/auth/photos.native";
  static final String BASIC_SCOPE = "https://www.googleapis.com/auth/userinfo.email";
  static final String LOOKUP = PhotosApi.RPC + "5084965799730810217";

  private AccountProbe() {}

  static String checkPhotos(String token, PhotosApi.Transport network, AtomicBoolean stop)
      throws IOException {
    PhotosApi.checkStopped(stop);
    if (token == null || token.isBlank() || token.indexOf('\r') >= 0 || token.indexOf('\n') >= 0)
      return "INVALID_TOKEN_RESULT";
    // A lookup for a synthetic SHA-1. No files, upload sessions, or library mutations.
    byte[] request =
        new Proto()
            .message(
                1, new Proto().message(1, new Proto().bytes(1, new byte[20])).bytes(2, new byte[0]))
            .build();
    try {
      PhotosApi.Response response =
          network.call(
              LOOKUP,
              "POST",
              Map.of(
                  "Authorization",
                  "Bearer " + token,
                  "User-Agent",
                  PhotosApi.UA,
                  "Content-Type",
                  "application/x-protobuf",
                  "Accept-Language",
                  "en_US"),
              output -> output.write(request),
              request.length,
              stop);
      PhotosApi.checkStopped(stop);
      try {
        Proto.fields(response.bytes(), 1);
      } catch (IOException malformed) {
        return "PHOTOS_UNEXPECTED_RESPONSE";
      }
      return "PHOTOS_LOOKUP_ACCEPTED";
    } catch (PhotosApi.ApiException failure) {
      return switch (failure.httpStatus) {
        case 401 -> "PHOTOS_HTTP_401";
        case 403 -> "PHOTOS_HTTP_403";
        case 429 -> "PHOTOS_RATE_LIMITED";
        default -> "PHOTOS_HTTP_FAILURE";
      };
    }
  }

  /** Only fixed diagnostic labels leave this boundary; never return raw authenticator messages. */
  static String authFailure(Throwable failure) {
    for (int depth = 0; failure != null && depth < 6; depth++, failure = failure.getCause()) {
      String message = failure.getMessage();
      if (message == null) continue;
      String upper = message.toUpperCase(Locale.ROOT);
      if (upper.contains("UNREGISTERED_ON_API_CONSOLE")) return "APP_NOT_REGISTERED";
      if (upper.contains("DEVELOPER CONSOLE IS NOT SET UP CORRECTLY"))
        return "APP_REGISTRATION_CONFIGURATION";
      if (upper.contains("INVALID_CLIENT") || upper.contains("UNAUTHORIZED_CLIENT"))
        return "CLIENT_REJECTED";
      if (upper.contains("NOT SUPPORTED") || upper.contains("UNSUPPORTED"))
        return "METHOD_UNSUPPORTED";
      if (upper.contains("INVALID_SCOPE") || upper.contains("INVALIDSCOPE"))
        return "SCOPE_REJECTED";
      if (upper.contains("NEEDSBROWSER") || upper.contains("NEEDS_BROWSER"))
        return "GOOGLE_REAUTH_REQUIRED";
      if (upper.contains("BADAUTHENTICATION") || upper.contains("BAD_AUTHENTICATION"))
        return "GOOGLE_AUTH_REJECTED";
      if (upper.contains("PERMISSION_DENIED") || upper.contains("ACCESS_DENIED"))
        return "ACCESS_DENIED";
      if (upper.contains("SERVICEDISABLED") || upper.contains("SERVICE_DISABLED"))
        return "SERVICE_DISABLED";
    }
    return "AUTHENTICATOR_FAILURE";
  }

  static String apiFailure(int statusCode, Throwable failure) {
    String recognized = authFailure(failure);
    if (!recognized.equals("AUTHENTICATOR_FAILURE")) return recognized;
    return switch (statusCode) {
      case 10 -> "GOOGLE_DEVELOPER_ERROR";
      case 7 -> "NETWORK_FAILURE";
      case 15 -> "TIMED_OUT";
      case 16 -> "CANCELED";
      case 17 -> "GOOGLE_API_UNAVAILABLE";
      default -> "GOOGLE_API_FAILURE";
    };
  }

  static boolean requestedScopeGranted(boolean photos, java.util.List<String> scopes) {
    if (scopes == null) return false;
    return scopes.contains(photos ? PHOTOS_SCOPE : BASIC_SCOPE)
        || (!photos && scopes.contains("email"));
  }

  static String explanation(String result) {
    return switch (result) {
      case "PHOTOS_LOOKUP_ACCEPTED" ->
          "Google issued a token and accepted a Photos lookup. This is promising. Uploads, quota"
              + " accounting, and background token renewal still need testing.";
      case "BASIC_TOKEN_ISSUED" ->
          "Google issued a basic account token. This checks the authorization path, not Photos"
              + " access.";
      case "APP_NOT_REGISTERED" ->
          "Google requires an OAuth registration for this app and signing certificate. This result"
              + " does not tell us whether the Photos permission would be allowed after"
              + " registration.";
      case "APP_REGISTRATION_CONFIGURATION", "CLIENT_REJECTED", "GOOGLE_DEVELOPER_ERROR" ->
          "Google reported an app configuration problem. Check the Android OAuth registration for"
              + " this package and signing certificate. This does not establish whether Photos"
              + " access is allowed.";
      case "GOOGLE_API_UNAVAILABLE" -> "Google's authorization API is unavailable on this device.";
      case "METHOD_UNSUPPORTED" -> "The provider rejected this authorization method.";
      case "SCOPE_NOT_GRANTED" ->
          "The requested permission was not included in Google's result. No Photos lookup was"
              + " made.";
      case "SCOPE_REJECTED" -> "Google rejected the requested permission for this app.";
      case "PHOTOS_HTTP_401" ->
          "A token was issued, but the Photos service rejected its authentication.";
      case "PHOTOS_HTTP_403" ->
          "A token was issued, but the Photos service refused the lookup. This alone does not"
              + " identify the cause.";
      case "GOOGLE_REAUTH_REQUIRED" -> "Google requires account verification or a fresh sign-in.";
      case "CANCELED" -> "The request was canceled or declined. No conclusion about Photos access.";
      case "NETWORK_FAILURE", "PHOTOS_RATE_LIMITED", "TIMED_OUT" ->
          "The test could not finish. Try again later.";
      case "NO_GOOGLE_AUTHENTICATOR" -> "Google account services are not available on this device.";
      case "DEVICE_ACCESS_DENIED" -> "Android or the account provider denied this request.";
      case "INTERRUPTED" -> "The test was interrupted. Start it again.";
      default ->
          "The test did not establish Photos access. Copy the diagnostic result for investigation.";
    };
  }
}
