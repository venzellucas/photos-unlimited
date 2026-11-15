# Photos Unlimited

A small, independent Android uploader for a Google Photos library. Choose folders,
back up original photos and videos, and keep using the official Google Photos app
to browse and edit your library.

**MVP: v0.2.1 · Android 13+ · MIT license**

[Website](https://venzellucas.github.io/photos-unlimited/) ·
[Download APK](https://github.com/venzellucas/photos-unlimited/releases/download/v0.2.1/Photos-Unlimited-0.2.1.apk) ·
[Release notes](https://github.com/venzellucas/photos-unlimited/releases/tag/v0.2.1)

## What it does

- Select photo and video folders for original-file backup.
- Run manual backups or let Android schedule automatic work, with unmetered-network
  and charging preferences.
- See current-file progress, upload history, existing matches, and files needing a retry.
- Use native Material screens, light/dark themes, wallpaper colors, an adaptive icon,
  and a cached Google profile photo when available.
- Scan Google Photos metadata to inspect reported storage usage, with filters for
  media type and charged or unknown storage. This feature is an experimental preview.

No root, custom ROM, microG, API key, or Photos Unlimited server is required.

## Install and connect

1. Download the signed APK from the release. Android may ask you to allow installation
   from your browser. Install over an existing version to preserve your setup.
2. Connect your account through Google's setup page in the app. If Google rejects
   embedded sign-in, use the browser setup-token fallback described in the connection
   screen. Enter that token only in the app; never post it in an issue or message.
3. Allow access to all photos and videos and media location. The latter is needed
   to read complete original file bytes, including embedded location.
4. Choose folders. Existing files in those folders are included in the first backup.
5. Turn off automatic backup in the official Google Photos app, then tap **Back up now**.
   This also enables automatic backup in Photos Unlimited.

Updates are manual. If the account button shows an initial, open it and choose
**Refresh profile photo**. Profile lookup uses a separate optional read-only token;
its failure does not invalidate the upload credential. The advanced phone-account
chooser is a diagnostic, not an alternative backup sign-in.

## MVP boundaries

The app uses Google's undocumented mobile protocol, adapted from xob0t's GPMC and
gotohp. It requests original-quality treatment with a Pixel upload profile.
**An accepted upload is not proof of quota exemption.** Google controls storage
accounting and may change or reject the methods. Verify fresh sample uploads,
downloadable originals, and actual quota use before relying on the app. Existing
matching library items are skipped and retain their accounting.

The storage scan covers Google Photos, not Drive or Gmail. Unknown accounting is
not zero. Its total sums reported charged bytes for scanned records and can differ
from account-wide usage. Shared items, alternate versions, omissions, and concurrent
changes can affect coverage. Live accounting and profile authorization are not fully
validated.

There is **no automatic re-upload, deletion, or quota-reclaim feature**. Google Photos
can store additional metadata separately from the original file. A cleanup workflow
would need to preserve and verify that metadata and independently identify any
replacement before removing an original. Those checks are not implemented.

Android controls background scheduling. Network, battery, charging, and idle rules
may delay work. Force-stopping the app stops automatic work until it is reopened.
Interrupted file transfers restart the file. Very large videos may need a manual run
on a stable connection. RAW, HDR, Motion Photos, paired Live Photos, and edits need
format-specific validation; separate photo/video files are not assembled into pairs.

## Privacy

See [PRIVACY.md](PRIVACY.md). Credentials are encrypted in private app storage;
photos are sent directly to Google. There is no project-owned account server.
Never include credentials, cookies, setup tokens, or private photos in bug reports.

## Build

Use JDK 21 and Android SDK platform/build tools 37. Configure `ANDROID_HOME` or an
untracked `local.properties` file. The repository includes a Gradle wrapper.

```sh
./gradlew --no-daemon --max-workers=2 :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The debug APK is in `app/build/outputs/apk/debug/`. Debug builds cannot update an
installed signed release. Run instrumentation only on a disposable test emulator:

```sh
ANDROID_SERIAL=YOUR_TEST_EMULATOR ./gradlew :app:connectedDebugAndroidTest
```

For release builds, supply an external keystore using `PHOTOS_KEYSTORE` and
`PHOTOS_SIGN_PASSWORD`, then run `tools/build-release.sh`. Never commit these values
or the keystore. Preserve a release signing key across updates.

The library masks can be regenerated with `tools/generate-library-templates.py`
using `blackboxprotobuf` and the pinned GPMC checkout listed below.

## Validation

The 0.2.1 APK passed 23 JVM tests, 19 Android instrumentation tests, debug/release
lint with no errors, and an in-place signed upgrade from 0.2.0. Tests cover protocol
handling, confirmed uploads, interruption, persisted queues, account separation,
metadata pagination, appearance, and profile-cache isolation. Test credentials and
network responses are synthetic. Light/dark and large-text screens were inspected.
These checks do not prove live quota exemption or complete library accounting.

## Credits and license

MIT-licensed protocol adaptations:

- [xob0t/gpmc](https://github.com/xob0t/gpmc), revision
  `94b1b267ca6819da542d4330a7dd216747eb0437`.
- [xob0t/gotohp](https://github.com/xob0t/gotohp), revision
  `2a5c1b9678281b6a2ff08661a53ea1dc6b1e2edc`.

The upstream notice is retained in
[app/src/main/assets/XOB0T-LICENSE.txt](app/src/main/assets/XOB0T-LICENSE.txt) and in
the app's About screen. Android and bundled libraries retain their own licenses.
See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Google, Google Photos, Android, and Pixel are trademarks of their respective owners.
Photos Unlimited is independent and is not affiliated with Google.
