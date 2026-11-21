# Photos Unlimited 1.1.0

Fixes for large libraries and the profile photo, plus a way to hear about new versions.

- **Update notice:** once a day while the app is open, it asks GitHub whether a newer
  version exists and shows a card on the Backup screen when one is out. GitHub sees your
  IP address, a fixed user agent and the time; no account, photo or device identifiers
  are sent. It is on by default; turn it off in Settings → **Check for updates**. While
  Free up has photos that are only on the phone, the card asks you to finish Free up
  first.
- **"Don't uninstall yet" comes back:** the warning about photos that are only on the
  phone is posted again after a reboot, after an app update and when the app opens.
- **Profile photo now loads.** Google refuses the profile scopes to the Photos client
  the app uploads as, so the read-only profile token is now requested as Google Play
  services, separately from the upload sign-in. If you tap **Refresh profile photo** and
  it fails, the message names the failing step; backup is never affected.
- **The storage scan finishes on large libraries.** An item whose details can't be fully
  read no longer stops the scan: it is counted as "unknown" and never freed
  ([#1](https://github.com/venzellucas/photos-unlimited/issues/1)). A catch-up page with
  none of the fields the app reads now ends the catch-up pass, as the upstream client
  does, and the finished message names it
  ([#2](https://github.com/venzellucas/photos-unlimited/issues/2)). When a scan does
  stop, the message ends with a short label in brackets, such as
  `SCAN_FORMAT_PAGE_…`, to quote in a bug report.
- **New icon:** a filled sage cloud with the arrow cut out, also in themed icons.

## Install

Android 13 or newer. Download `Photos-Unlimited.apk` and install it over 1.0.0 to keep
your setup. From this release the APK has the same name in every release, so
`releases/latest/download/Photos-Unlimited.apk` always gets the newest one. Verify it
with `SHA256SUMS.txt`. The signing certificate's SHA-256 fingerprint is
`cbd14272c6aaa0568df8cc8be4ec10be9c6a2ebc0354eb202b5a05d8cda74bce`; Android refuses
any update not signed with this key.

## Known limits

Google's undocumented protocol may change, and an accepted upload is not proof of quota
exemption. Items the scan can't fully read stay "unknown" (#1).
Automatic backup runs when Android allows it: on phones with aggressive battery
managers, use **Back up now** for a large first backup, or allow the app to run
unrestricted.

## Validation

76 JVM tests and 28 Android instrumentation tests passed; lint had no errors. On a test
emulator, the update card and the re-posted warning (after a reboot and after an update)
were checked, and an in-place signed upgrade from 1.0.0 kept the settings.

# Photos Unlimited 1.0.0

The core backup features are verified on a real device. This release adds a way to free
the Google storage used by earlier uploads.

- **Free up storage:** replace items that use Google storage with copies uploaded
  through the app, one at a time or in batches (Storage → tap an item, or Select items).
  Each original is proved byte-identical against Google's own checksum before it is
  deleted, so embedded metadata (dates, location, camera) is kept. Caption, favorite and
  archive are restored; albums, faces and edits made in Google Photos are not.
- **Safe by design:** a crash-safe journal; copies kept in
  `Pictures/Photos Unlimited/Pending upload/` so uninstalling can't lose them; a new
  install finishes leftovers (after you approve files it didn't write); phone photos that
  Google Photos removes in response are put back.
- **Storage saver items** become originals again when the original is still on the phone.
- **Motion Photos** keep their motion; iPhone Live Photos are refused to protect theirs.
- **Google Photos backup reminder** with a direct link to its Backup page. Turn Google
  Photos backup off before freeing storage: otherwise it deletes the phone's copies.
- The storage scan is rebuilt once after updating (new checksum, motion and quality fields).

## Install

Android 13 or newer. Download `Photos-Unlimited-1.0.0.apk` and install it over 0.2.1 to
keep your setup (same signing key). Verify with `SHA256SUMS.txt`. Updates are manual.

## Known limits

Google's undocumented protocol may change, and an accepted upload is not proof of quota
exemption. Free up storage was tested live on a test account (photos, videos, Storage
saver items, a Motion Photo, recovery after cleared app data), not yet on a large real
library: start with a few items. Other devices with Google Photos backup on can still
remove their copies of replaced items.

## Validation

57 JVM tests and 20 Android instrumentation tests passed; debug/release lint had no
errors; findings from an independent security review were fixed and re-verified live; an
in-place signed upgrade from 0.2.1 succeeded.

Independent software; not affiliated with Google. MIT-licensed project code, with
upstream notices retained.

# Photos Unlimited 0.2.1 — MVP

The first public MVP is a small Android app for backing up original photos and
videos to Google Photos while continuing to browse and edit in the official app.

- Choose folders and run manual or Android-scheduled backups.
- Use unmetered-network and charging preferences.
- Follow file progress, upload history, existing matches, and retryable failures.
- Native Material UI with light/dark themes, wallpaper colors, and adaptive icons.
- Optional cached Google profile photo, with an account initial as fallback.
- Experimental read-only Google Photos storage inventory.

## Install

Android 13 or newer is required. Download `Photos-Unlimited-0.2.1.apk`. Install over
an existing signed release to retain the setup. Use `SHA256SUMS.txt` to verify the
APK or source archive. Updates are manual.

## Known limits

Google's undocumented protocol may change. Upload acceptance does not establish
quota exemption. The storage scan is a preview, excludes Drive/Gmail, and does not
establish account-wide quota totals. Live profile authorization is not fully
validated. Automatic re-upload/deletion to reclaim storage is not implemented:
metadata preservation and independent replacement verification are prerequisites.

## Validation

23 JVM tests and 19 Android instrumentation tests passed. Debug/release lint had
no errors. The existing signing certificate and preferences were preserved in an
in-place update from 0.2.0. Light/dark and large-text screens were inspected. These
checks use synthetic credentials and do not establish live quota treatment.

Independent software; not affiliated with Google. MIT-licensed project code, with
upstream notices retained.
