> **Note.** This is the README of the private development tree, kept for its feature notes
> (storage inventory, Free up, background behavior, account handling). Parts are older than
> the public [README](../README.md), which wins where they differ.

# Photos Unlimited

A small, independent Android uploader for a Google Photos library. Select media
folders, connect an account, and let the app upload originals. Use the official
Google Photos app to browse and edit the resulting library, with its automatic
backup turned off.

This is an experimental preview. It uses Google's undocumented mobile protocol,
adapted from xob0t's MIT-licensed GPMC and gotohp. The Pixel upload profile requests
the grandfathered Original-quality treatment, but an accepted upload is **not**
proof of quota exemption. Confirm backup quality, downloadable originals, and
actual quota accounting on fresh sample files before relying on it. Existing
matching library items are skipped; their storage accounting is not changed.

## Use

1. Install the signed preview APK on Android 13 or newer.
2. Connect your Google account. The app opens Google's Embedded Setup page.
   If the page stays blank or Google rejects embedded sign-in, use **Use browser setup token**. Follow the
   instructions on a computer and enter the resulting token only in the app.
   This fallback is manual; neither path has been validated with the user's account.
3. Grant access to **all photos and videos** and media location access. The latter
   is required to preserve complete original bytes, including embedded location.
4. Choose folders. The first backup includes existing media in those folders.
5. Turn off automatic backup in official Google Photos, then tap **Back up now**.
   This also enables automatic backup. Select unmetered-only and charging options.

Version 0.2.0 adds native Material 3 Expressive screens with Backup, Activity,
Storage, and Settings navigation. Appearance follows Android by default, with
Light/Dark overrides and optional wallpaper colors. The launcher icon supports
adaptive masks and themed icons. Install over the existing app to retain sign-in,
folder choices, preferences, and the upload ledger.

Version 0.2.1 adds a circular Google profile photo to the account button. It uses
a separate, optional read-only profile token for the already-connected account,
checks that the verified email matches, and caches a small image in private app
storage. The upload credential and its scopes stay unchanged. Successful profile
lookups refresh daily; failures back off for a day. Tap the account button and
choose **Refresh profile photo** to retry immediately. An account initial is the
fallback when Google does not supply a usable picture. Disconnecting clears the
profile cache. Live profile authorization still needs verification on the device;
a failed lookup never triggers a new login or stops backup.

The existing phone-account diagnostic is available in **Settings → Advanced
account diagnostic** and on the connection screen. It is separate from backup
sign-in. See the [investigation and result meanings](docs/android-account-investigation.md).

Originals stay on the phone. Backup never deletes anything; the separate Free up storage
action is described below. There is no two-way sync feature. Google Photos changes and deletions are not mirrored back into this app's
ledger. Edited files with a changed size or modification timestamp are treated as
new upload candidates. Permissions, folder selection, and account identity are
checked independently of the Google Photos app.

## UI design

The native UI uses Material Components for Android 1.14.0, shared semantic colors,
scalable text, standard controls, and appearance settings separate from backup
preferences. See the [design system](docs/design-system.md) and
[reference tokens](docs/design-tokens.json). The earlier
[interactive design prototype](docs/photos-unlimited-design.html) is a sample-data
reference, not the app runtime. The upload screen shows current-file byte progress
from the engine; confirmed uploads and already-present items remain separate.

## Storage inventory preview

Open **Storage → Scan Google Photos** to read library metadata with the connected
account. This is a read-only Google Photos scan. It lists names and file sizes,
reported charged storage, media type, and archive/trash/shared markers when the
service supplies them. Filter by photos/videos and by charged or unknown storage;
results are sorted by reported charged bytes, in pages of 50.

Missing or invalid accounting remains **unknown**, distinct from an explicit
zero. The displayed total sums reported charged bytes for scanned records. It is
not an account-wide quota measurement and excludes Drive and Gmail. Shared items,
alternate versions, omissions, and changes during scanning can affect comparisons
with Google's account total. Live coverage and accounting semantics still require
validation against account-visible examples. No remote or local media is deleted,
reuploaded, recompressed, or changed by the scan itself.

Each scan runs in a separate foreground service with a Pause action. The private
SQLite cache stores page data and opaque continuation cursors in one transaction,
scoped to the account. Interrupted scans can continue; a run pauses after eight
minutes or 500 pages. Initial pagination is followed by delta synchronization;
repeating cursors stop the scan instead of silently declaring success. Partial
results stay labeled as partial. Refreshing a completed scan or choosing Start a
fresh scan replaces only the local inventory cache. Disconnect removes that
account's cache and credential. There is no periodic storage scan.

The request templates and parser are adapted from the pinned GPMC revision below.
`tools/generate-library-templates.py /path/to/gpmc` regenerates the packaged binary
masks using `blackboxprotobuf`. The upstream fixed account identifier is omitted;
authentication and returned cursors identify the connected account. The service
can reject or change this undocumented read API independently of uploads.

### Free up storage (re-upload charged items)

Tap a charged item in **Storage** and choose **Free up**, or tap **Select items** to pick
several (or **Select all eligible** across every page of the current filters) and free
them in one run. The app replaces each with a copy uploaded through the Pixel profile. Google identifies media by content hash and
deletes by the same key, so a replacement with identical bytes cannot coexist with the
original. The only safe order is therefore:

1. Confirm Google still maps the item's SHA-1 to this item and to no other item.
2. Look for the original on this phone first (same name or size), then download it from
   Google; either way require its SHA-1 to equal the hash Google recorded at the first
   upload. This proves a byte-identical original, so all embedded metadata
   (EXIF date, GPS, camera, video location atoms) is preserved. No match: stop, nothing
   changed.
3. Move to trash, then delete permanently. Each request is journaled before it is sent
   (`reclaim.db`, separate from the inventory cache).
4. Upload the saved copy. If Google still reports the deleted original, wait; never
   upload into an existing item.
5. Run a delta scan and require the replacement to report 0 charged bytes, then restore
   caption, favorite and archive, and remove the local copy.

**Storage saver items** (filter: *Reduced quality*). Google keeps the original's SHA-1
even for a reduced copy, but serves only the recompressed bytes and, once processed,
reports the reduced size. These items are replaced only by a phone file whose SHA-1
equals that recorded hash; Google's reduced copy is never used as a stand-in. Reduced
items qualify even when they are free (pre-2021 uploads), because the result upgrades
them to the original. Without the original on the phone they are left untouched.

A changed local copy before deletion restores the original from trash. A failure after
deletion keeps the verified copy in private app storage and retries. A charged
replacement, or any item needing attention, stops the whole run before more originals
are touched. Not restored: albums, shared-album copies, comments,
face groups, and date/location edits made inside Google Photos. Not supported: alternate
versions and Locked Folder.

**Motion Photos.** Google marks Motion/Live Photos with a field-5 envelope in the item
record (upstream GPMC's `5.5.2.4` path is not sent; earlier builds therefore missed them,
and the inventory cache is rebuilt once). Android Motion Photos (Pixel MotionPhoto and
legacy MicroVideo, Samsung) are one file with the MP4 appended; the checksum match proves
the video part is included, and the engine additionally requires an embedded MP4 box in the
verified file. An iPhone Live Photo keeps its video in a separate file that Google pairs
with the image: its image can match the checksum perfectly, yet a single re-upload would
lose the motion, so items flagged as motion without an embedded video are refused before
any change. Live test (2026-11-19): a Pixel-format Motion Photo (EXIF/GPS, XMP container
directory, 3 s MP4) replaced at 0 bytes, flagged as motion immediately, and the video
Google extracts from the replacement is byte-identical to the embedded MP4. Live Photo
refusal is unit-tested only (no iPhone pair was available).

**Staged copies survive uninstalling.** The verified copy is written to
`Pictures/Photos Unlimited/Pending upload/` in shared storage, not private app storage, and
indexed immediately. Its name is `<sha1>_<account tag>_<original name>`: the tag is a
short hash of the account, never the address. A fresh install, or the same install after
**Clear storage**, finds files no journal entry owns, trusts one only when its bytes hash
to the SHA-1 in its name, refuses files tagged for another account, uploads what Google
Photos is missing, and removes copies Google already has; copies written by an earlier
install are removed only after Android's own delete confirmation. While any item is only on
the phone, a notification and the Storage card say not to uninstall, and the manifest sets
`hasFragileUserData`, so Android's uninstall dialog offers to keep the app's data. Live test
(2026-11-19): a 30 MB video stopped right after deletion, the journal deleted, and Google
Photos (backup on) removing the phone copy; **Finish now** restored it to Google Photos at 0
bytes, byte-identical. The foreign-owned cleanup path was tested with a shell-written copy.
Not tested: an actual uninstall and reinstall with a new sign-in.

**1.0.0 security review hardening.** Recovery finishes only files owned by this app;
ownerless files (an earlier install) need the user's per-file approval in the Storage card;
other apps' files are ignored. A staged copy is removed only after a library lookup shows a
live, untrashed item with the same SHA-1 and size. Before deleting, the engine re-reads the
item (eligibility re-checked), reads back the trash state, and verifies the replacement's
SHA-1/size; one item at a time may be between deletion and confirmation; account changes
wait while anything is only on the phone. Download tokens go only to `lh*` and
`video-downloads` hosts and are stripped on cross-host redirects. All re-verified live
2026-11-19 (download path for a photo and a video, planted-file and ownerless-file recovery).

**Photos on the phone are kept.** Google Photos deletes a phone's copy of anything removed
from the cloud while its backup is on, on every device signed in to the account
([Google's help](https://support.google.com/photos/answer/6128858)). Live testing showed
this within 3 seconds with backup on; with backup off the phone copy stayed, also after
switching backup back on. So Free up asks you to turn Google Photos backup off first
(single item and batch). As a safety net, the engine records which phone file matched the
original, keeps its verified copy about a minute after the replacement is confirmed, and
if the phone file disappeared writes it back to the same folder and name, byte-identical
(tested with backup deliberately left on). The restored file belongs to Photos Unlimited
and has a new file date; the capture date is in its unchanged EXIF. Restored files never
raise the backup-conflict warning. Other phones or tablets with Google Photos backup on
for the same account can still lose their copies; the warning says so.

### Google Photos backup reminder

Two backup apps on one phone race: whichever uploads first wins, and Google Photos'
copies use storage. Android does not let apps read Google Photos' settings, so while
automatic backup is on, Photos Unlimited asks on every open until you confirm Google
Photos backup is off, and links straight to its Backup page
(`https://photos.google.com/link/backup`). After that it asks again only on evidence:
a photo created after backup was switched on that was already in the library before
this app uploaded it. **Settings → Turn off Google Photos backup** opens the page any time.

Live result (2026-11-19, spare test account, three charged files with EXIF/GPS and a
video with a location atom): all three replaced in under 10 s each, charged storage
1.25 MB / 625 kB / 2.13 MB to 0, the replacements download byte-identical to the
originals, and caption (with emoji), favorite and archive were restored.

`LiveReclaimProbe` is the live harness (`-e live observe|reclaim|decorate -e name F`).
Run only that class on a signed-in device: other instrumentation classes clear the saved
account during cleanup.

## Background behavior

Android JobScheduler receives media-change triggers and a persisted 15-minute
periodic catch-up job. Android controls when jobs actually run. Network, battery,
charging, and device idle policies may delay work. Force-stopping the app blocks
automatic work until it is opened again. A manual run uses a visible data-sync
foreground service. Individual runs stop after a bounded batch and queue more work.

The SQLite ledger survives process death. Each local media version is scoped to
its Google account. Every attempt first checks the remote content hash, including
after an ambiguous failure. Interrupted transfers currently restart the file; byte
range resumption is not implemented. Large videos may therefore require a manual
run and a stable connection. A file is marked uploaded only after a media key is
returned. Already-present items are counted separately and have unverified quota
treatment. Nonretryable files remain visible for manual attention.

Ordinary photos and videos are implemented. RAW formats, HDR, Motion Photos,
paired Apple Live Photos, and edits require format-specific live validation; the
app does not assemble separate photo/video files into a paired Live Photo.

## Account handling

No microG, root, API key, backend server, or subscription is needed by this app's
architecture. Google can reject or change the sign-in and upload methods.
Credentials are encrypted with an Android Keystore AES-GCM key in private app
storage, excluded from Android backup, and never logged. The network client
contacts fixed Google endpoints and, once a day while open, api.github.com, with TLS
verification, and rejects redirects.
The sign-in WebView has no JavaScript bridge, blocks file access, prevents screenshots,
and clears its cookies on exit. It reads only the setup token for exchanging into
an account credential, not password fields. Google credentials obtained this way
are sensitive and must never be sent in chat or included in bug reports.

Disconnect removes the app's saved credential and stops future jobs. An in-flight
server request may have completed before cancellation. Revocation of Google's
server-side session is managed in the Google account, not by local disconnect.

## Build and validate

A dedicated Linux build machine holds the JDK and Android SDK. The build uses JDK 21,
Gradle wrapper, and Android 37 platform. The account diagnostic uses the official Google Play services authorization SDK;
release code shrinking removes unused library code. The Material dependency supplies native Views components.

```sh
./gradlew --no-daemon --max-workers=2 :app:assembleDebug \
  :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
```

Run instrumentation only on the dedicated emulator. Use an explicit ADB serial:

```sh
adb -s emulator-5560 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5560 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5560 shell am instrument -w \
  app.photos.unlimited.test/androidx.test.runner.AndroidJUnitRunner
```

Tests cover the upstream-compatible wire format, preservation of opaque receipts,
credential refresh, cancellation, prevention of committing changed files, confirmed
upload results, persisted queues, account separation, retry backoff, encrypted
credential storage, scheduling constraints, and selected-folder discovery. Test
credentials and network responses are synthetic. Passing tests do not establish
live sign-in, upload acceptance, original-file preservation by Google, or quota
exemption. Those require an account-local smoke test on the user's device.

The APK is a preview. Keep the signing key outside the checkout and preserve it
for future upgrades. See `tools/build-release.sh` for the release build.

Validation artifacts record unit tests, Android instrumentation, debug/release
lint, native light/dark and large-font screenshots, and an in-place update from
the previous signed release. Tests use synthetic credentials and metadata; no
Google account is signed into the dedicated emulator. The user reports uploads
working on their device. The new storage scan has not yet been verified against
a live account, and no test result establishes quota exemption.

## Upstream provenance

- [xob0t/gpmc](https://github.com/xob0t/gpmc), revision
  `94b1b267ca6819da542d4330a7dd216747eb0437`: wire protocol and upload behavior.
- [xob0t/gotohp](https://github.com/xob0t/gotohp), revision
  `2a5c1b9678281b6a2ff08661a53ea1dc6b1e2edc`: Embedded Setup credential exchange.

The upstream MIT notice is bundled in the APK and accessible from About. Google,
Google Photos, Android, and Pixel are trademarks of their respective owners.
This project is not affiliated with Google.
