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
      case "PHOTOS_LOOKUP_ACCEPTED" -> Text.get(R.string.probe_photos_accepted);
      case "BASIC_TOKEN_ISSUED" -> Text.get(R.string.probe_basic_token);
      case "APP_NOT_REGISTERED" -> Text.get(R.string.probe_not_registered);
      case "APP_REGISTRATION_CONFIGURATION", "CLIENT_REJECTED", "GOOGLE_DEVELOPER_ERROR" -> Text.get(R.string.probe_configuration);
      case "GOOGLE_API_UNAVAILABLE" -> Text.get(R.string.probe_api_unavailable);
      case "METHOD_UNSUPPORTED" -> Text.get(R.string.probe_method_unsupported);
      case "SCOPE_NOT_GRANTED" -> Text.get(R.string.probe_scope_not_granted);
      case "SCOPE_REJECTED" -> Text.get(R.string.probe_scope_rejected);
      case "PHOTOS_HTTP_401" -> Text.get(R.string.probe_http_401);
      case "PHOTOS_HTTP_403" -> Text.get(R.string.probe_http_403);
      case "GOOGLE_REAUTH_REQUIRED" -> Text.get(R.string.probe_reauth);
      case "CANCELED" -> Text.get(R.string.probe_canceled);
      case "NETWORK_FAILURE", "PHOTOS_RATE_LIMITED", "TIMED_OUT" -> Text.get(R.string.probe_retry_later);
      case "NO_GOOGLE_AUTHENTICATOR" -> Text.get(R.string.probe_no_authenticator);
      case "DEVICE_ACCESS_DENIED" -> Text.get(R.string.probe_denied);
      case "INTERRUPTED" -> Text.get(R.string.probe_interrupted);
      default -> Text.get(R.string.probe_default);
    };
  }
}
