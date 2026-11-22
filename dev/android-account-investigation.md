# Android account authorization investigation

The account chooser selects an existing account; Google must then authorize this
app separately. Version 0.1.1 used the platform `AccountManager.getAuthToken`
route. A real-device test returned `AUTHENTICATOR_FAILURE` for both the Photos
scope and the public email scope. That control failure is inconclusive: the
coarse diagnostic did not distinguish app configuration, provider rejection,
method compatibility, or a local runtime failure.

Version 0.1.2 uses Google's recommended `AuthorizationClient` from Play services.
The chooser still selects the account, but token acquisition and consent are now
handled through Google's authorization SDK, under our actual app identity.
Authorization uses only the selected test scope and opts out of merging previously
granted scopes. Returned grants are checked before a token can reach Photos.

Google's documented Android authorization setup requires an OAuth client registered
with the application's package name and signing certificate. This preview has no
such registration. A registration/configuration failure therefore does not settle
whether Google would permit the private `photos.native` scope after registration.
That scope is absent from the public Photos API scope list. Changing the client
library does not provide an OAuth registration or establish scope eligibility.

## Diagnostic preview

In 0.1.2, open **Connect Google account → Test account on this phone**:

1. Start with **Test basic Google access**. Android selects a `com.google` account;
   Google's `AuthorizationClient` requests only
   `https://www.googleapis.com/auth/userinfo.email`. No userinfo HTTP request is
   made and this control token is never sent to Photos.
2. If the control succeeds, **Test Photos access** requests only
   `https://www.googleapis.com/auth/photos.native`.
3. If that scope is granted and a token is issued, make one read-only mobile-protocol
   duplicate lookup using an all-zero synthetic SHA-1. No local file is read and no
   upload is initiated.
4. **Copy diagnostic result** exports fixed outcome labels, the execution stage,
   Google API numeric status codes, and public app/OS metadata. It excludes account
   identifiers, tokens, raw exception messages, and response bodies. Error-bearing
   consent results are parsed even if Android labels the activity result canceled.

This adds the official Google Play services authorization SDK, not a replacement
Google services app. No microG, root, account-enumeration permission, or backend is
added. Release code is shrunk to remove unused library code. Access tokens are
held in memory only during the test; Google may independently cache authorization.
The diagnostic preserves the uploader's saved account, folders, and backup state.

| Result | What it establishes |
| --- | --- |
| `APP_NOT_REGISTERED` | App registration blocks this request. Private-scope eligibility remains unknown. |
| `GOOGLE_DEVELOPER_ERROR` / `APP_REGISTRATION_CONFIGURATION` / `CLIENT_REJECTED` | Google reported an app configuration/client error; verify registration before judging private Photos access. |
| `BASIC_TOKEN_ISSUED` | The authenticator issued a token for the public control scope. |
| `SCOPE_NOT_GRANTED` | The requested permission was absent from the result; no Photos lookup was made. |
| `SCOPE_REJECTED` | The authenticator explicitly rejected the requested scope. |
| `PHOTOS_HTTP_401` / `PHOTOS_HTTP_403` | A token was issued but the Photos lookup failed. The HTTP status alone does not identify the cause. |
| `PHOTOS_LOOKUP_ACCEPTED` | A token was issued and the lookup returned a successful HTTP response with parseable protobuf. This does not verify uploads, quota exemption, or background renewal. |
| `CANCELED` / network or generic failures | Inconclusive; a visible Google error may provide more context. |

The test emulator has no signed-in Google account. Automated tests cover
request construction, grant checks, error classification and redaction, read-only
lookup behavior, preservation of existing backup settings, and UI navigation.
Real token issuance and Photos access require the user's phone. Neither a passing
control nor a successful lookup proves upload acceptance or quota exemption.

## Primary sources

- [AccountManager: chooser and token requests](https://developer.android.com/reference/android/accounts/AccountManager)
- [AOSP caller package assignment](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/accounts/AccountManager.java)
- [Google Android authorization and client registration](https://developer.android.com/identity/authorization)
- [AuthorizationClient](https://developers.google.com/android/reference/com/google/android/gms/auth/api/identity/AuthorizationClient)
- [Authorization results and granted scopes](https://developers.google.com/android/reference/com/google/android/gms/auth/api/identity/AuthorizationResult)
- [Public Google Photos authorization scopes](https://developers.google.com/photos/overview/authorization)

If a real account returns `PHOTOS_LOOKUP_ACCEPTED`, the next implementation step is
to integrate background token acquisition and validate a small upload. If both
tests report `APP_NOT_REGISTERED`, create a proper Android OAuth client for this
package and signing fingerprint before interpreting scope acceptance. Do not
present either outcome as proof of unlimited storage.
