# Unreleased (main)

Not in a downloadable APK yet.

- **Free up storage:** replace items that use Google storage with copies uploaded through
  the app, one at a time or in batches, verified byte-for-byte against Google's hash of
  the original. Uninstall-safe staging, crash-safe journal, phone-copy protection.
- Storage saver items upgraded to originals when the original is on the phone.
- Motion Photos supported; iPhone Live Photos refused to protect their motion.
- Google Photos backup reminder with a direct link to its Backup page.
- Storage scan now records Motion Photo, quality, checksum and caption fields; the cached
  inventory is rebuilt once after updating.

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
