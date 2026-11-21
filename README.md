# Photos Unlimited

**English** · [Português (Brasil)](README.pt-BR.md) · [Español](README.es.md) ·
[简体中文](README.zh-Hans.md) · [日本語](README.ja.md)

A small, independent Android uploader for a Google Photos library. Choose folders,
back up original photos and videos, and keep using the official Google Photos app
to browse and edit your library.

**Android 13+ · MIT license**

[Website](https://venzellucas.github.io/photos-unlimited/) ·
[Download APK](https://github.com/venzellucas/photos-unlimited/releases/latest/download/Photos-Unlimited.apk) ·
[Release notes](https://github.com/venzellucas/photos-unlimited/releases/latest)

**Verify the APK.** Every release is signed with the certificate whose SHA-256
fingerprint is
`cbd14272c6aaa0568df8cc8be4ec10be9c6a2ebc0354eb202b5a05d8cda74bce`. Android refuses any
update not signed with this key. Each release also lists the APK's checksum in
`SHA256SUMS.txt`. To report a security problem, see [SECURITY.md](SECURITY.md).

## What it does

- Select photo and video folders for original-file backup.
- Run manual backups or let Android schedule automatic work, with unmetered-network
  and charging preferences.
- See current-file progress, upload history, existing matches, and files needing a retry.
- Use native Material screens, light/dark themes, wallpaper colors, an adaptive icon,
  and a cached Google profile photo when available.
- Scan Google Photos metadata to inspect reported storage usage, with filters for
  media type and charged or unknown storage. This feature is an experimental preview.
- Use the app in English, Português (Brasil), Español, 简体中文 or 日本語. It follows
  the phone's language; choose another in Settings → **Language**.

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

The app checks GitHub once a day while it is open and shows a card when a new version
is out. Turn this off in Settings → **Check for updates**. If the account button shows an initial, open it and choose
**Refresh profile photo**. Profile lookup uses a separate read-only token, at most once a day;
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
changes can affect coverage. Live accounting is not fully validated.

## Free up storage

Items that already use Google storage can be replaced by a copy uploaded through this
app: tap an item in **Storage**, or select several (**Select all eligible** works across
pages). Google identifies media by content hash and deletes by the same key, so the
original must be gone before an identical replacement can exist. Each item therefore:

1. Is checked against Google's record (no other item may share its bytes).
2. Gets a copy from the phone if present, otherwise downloaded from Google, accepted only
   if its SHA-1 equals the hash Google recorded for the original upload. This proves a
   byte-identical file, so embedded metadata (EXIF date, GPS, camera, video location) is
   kept exactly.
3. Is moved to trash and deleted permanently, then the copy is uploaded again.
4. Is confirmed only when Google reports 0 charged bytes for the replacement; caption,
   favorite and archive are restored.

Safety properties:

- Every step is journaled before it runs; a crash resumes safely. If the copy changes
  before deletion, the original is restored from trash. A charged replacement or any
  item needing attention stops the whole run.
- The copy lives in `Pictures/Photos Unlimited/Pending upload/`, not private app
  storage, so uninstalling or clearing the app cannot lose it. File names carry the
  SHA-1 and a short account hash; a new install proves each leftover by its hash and
  finishes it (never into a different account). Only files this app wrote are finished
  automatically; files left by an earlier installation are listed for your approval first,
  and files written by other apps are ignored. A staged copy is removed only once Google
  shows a live, untrashed item with the same checksum. A notification warns not to
  uninstall while anything is only on the phone, account changes wait until it is finished,
  and Android's uninstall dialog offers to keep data.
- Right before deleting, the item is looked up again and must still be eligible, the move to
  trash must be visible in Google's record, and only one item at a time is between deletion
  and a confirmed replacement.
- **Turn off Google Photos backup first.** While its backup is on, Google Photos deletes
  a phone's copy of anything removed from the cloud, on every device of the account
  ([Google Help](https://support.google.com/photos/answer/6128858)). Free up asks for
  this, and as a safety net puts a removed phone copy back, byte-identical.
- **Storage saver items** are replaced with the original only when the original file is
  still on the phone (proved by Google's recorded hash); Google's reduced copy is never
  used instead. This also upgrades free pre-2021 reduced uploads.
- **Motion Photos** (Pixel, Samsung) are single files and keep their motion; Google
  re-extracts the identical embedded video. **iPhone Live Photos** keep their video in a
  separate file, so they are refused rather than losing the motion.

Lost on replacement: albums, shared-album copies, comments, face groups, and date/location
or other edits made inside Google Photos. Other devices with Google Photos backup on can
still delete their copies. Tested live on a test account (photos, videos, Storage saver
items, a Motion Photo, a stopped-mid-run video recovered after the app's data was
cleared); not yet tested on a large real library.

### Google Photos backup reminder

Two backup apps on one phone race, and Google Photos' copies use storage. Android does
not let apps read Google Photos' settings, so while automatic backup is on the app asks
on each open until you confirm Google Photos backup is off, linking straight to its
Backup page, and asks again only if it later sees a new photo uploaded by another app first.

Android controls background scheduling. Network, battery, charging, and idle rules
may delay work. Force-stopping the app stops automatic work until it is reopened.
Interrupted file transfers restart the file. Very large videos may need a manual run
on a stable connection. RAW, HDR, and edits need format-specific validation; separate
photo/video files (iPhone Live Photos) are not assembled into pairs.

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
`PHOTOS_SIGN_PASSWORD`, set `ANDROID_HOME`, then run
`RELEASE_TAG=vX.Y.Z tools/build-release.sh`, where `X.Y.Z` is the `versionName` in
`app/build.gradle`. The APK and its `SHA256SUMS.txt` are written to `dist/X.Y.Z/`.
Never commit these values or the keystore. Preserve a release signing key across
updates.

The library masks can be regenerated with `tools/generate-library-templates.py`
using `blackboxprotobuf` and the pinned GPMC checkout listed below.

## Validation

Each release passes the JVM and Android instrumentation suites, lint with no errors,
and an in-place signed upgrade from the previous release. Version 1.0.0 also had an
independent security review (findings fixed before release) and live Free up runs as
described above. Tests cover protocol
handling, confirmed uploads, interruption, persisted queues, account separation,
metadata pagination, appearance, and profile-cache isolation. Test credentials and
network responses are synthetic. Light/dark and large-text screens were inspected.
These checks do not prove live quota exemption or complete library accounting.

## Translations

The app, this README and the website are available in English, Brazilian Portuguese,
Spanish, Simplified Chinese and Japanese. The translations were made with a language
model and may lag behind the English text, which is the reference. Corrections are
welcome as pull requests: app text is in `app/src/main/res/values-*/strings*.xml`,
and the translated READMEs and site pages sit next to the English ones.

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
