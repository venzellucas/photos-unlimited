# Photos Unlimited 1.1: update notice, safety re-post, profile photo, repository basics

*   **Author:** venzellucas
*   **Status:** Draft
*   **Date:** 2026-11-20

--------------------------------------------------------------------------------

## Overview

Photos Unlimited is a sideloaded Android app (Java, minSdk 33) that backs up
phone photos to Google Photos without using Google storage, and can "free up"
Google storage by replacing items that count against it. It ships only as an
APK on GitHub Releases. Version 1.0.0 is the current release.

Release 1.1 does four things:

1.  **Update notice.** Once a day, while the app is open, it asks GitHub which
    release is the latest. If that release is newer than the installed one, the
    app shows a notice with a button that downloads the new APK in the browser.
    The user installs it through Android's normal installer.
2.  **"Only on this phone" warning after reboot and update.** When Free up has
    deleted items from Google Photos that are not yet re-uploaded, the app warns
    the user not to uninstall it. Today that warning is posted only at the end of
    a Free up run. 1.1 posts it again after a reboot, after the app is updated,
    and when the app is opened.
3.  **Profile photo diagnosis and fix.** The account picture always shows the
    first letter of the email. 1.1 makes the failure name the step that failed,
    uses that to find the cause, and then fixes it or removes the network photo.
4.  **Repository basics.** A security policy, a bug report template, an automatic
    build check on GitHub, a stricter pre-publication scan, and the published
    fingerprint of the app's signing key.

Translation into other languages is a separate design for 1.2. Text added in 1.1
goes into a new English `res/values/strings.xml`, so 1.2 does not have to move
it again.

## The Problem

**Users never hear about new versions.** Version 1.0.0 contacts only Google.
Nobody who installed it learns about a new release unless they go back to
GitHub. This matters more for this app than for most. Free up deletes items
from Google Photos, and the app depends on a private Google protocol that Google
can change at any time. A fix that users never install doesn't protect them.
Version 1.0.0 can't be changed after the fact. The earlier 1.1 ships, the
earlier there is a version that can tell users about the next one.

**Updating can put photos at risk.** While Free up has items that exist only on
the phone, the user must not uninstall the app (the app keeps the verified copy
and finishes the re-upload later). Installing an update stops any running Free
up job. A failed sideload install often leads people to "uninstall first, then
install", which is exactly the dangerous step. On top of that, the "Don't
uninstall yet" notification is posted only at the end of a Free up run.
`BootReceiver` does not post it again (`ReclaimService.java:275`,
`BootReceiver.java`). After a reboot or an update, the warning is gone until the
user opens the app.

**The profile photo never loads, and the app can't say why.** Every failure in
the profile lookup is caught and turned into one message,
`MainActivity.java:1588`. The HTTP layer also throws away Google's error text
on any non-2xx answer, before the profile code can read it
(`PhotosApi.java:673-683`). There is no way to tell a refused permission from a
network error or a bad image.

**The repository is missing the files open-source users expect.** There is no
`SECURITY.md`, no issue template, and no automatic build check. The signing key
fingerprint is not published, so users can't check an APK they got elsewhere.
`check-publication.py` reads files with the platform encoding and has no email
pattern, so it can miss personal details in new files.

## Goals

*   **G1 Update check.** At most one request per 24 hours, only while the app is
    open, to GitHub for the latest release of `venzellucas/photos-unlimited`.
    No account or device identifiers are sent. Any failure is silent and never
    changes backup status.
*   **G2 Update notice.** When the latest release is strictly newer than the
    installed version, the app shows a calm notice (not a warning). It has a
    "Download latest version" button that opens
    `https://github.com/venzellucas/photos-unlimited/releases/latest/download/Photos-Unlimited.apk`
    in the browser, and a smaller "What's new" link to the release page. The
    notice disappears by itself once the new version is installed.
*   **G3 Setting.** One switch in Settings, "Check for updates", on by default
    for new installs and for upgrades from 1.0.0. The release notes and
    PRIVACY.md say so.
*   **G4 Safe moment only.** While any Free up item exists only on the phone
    (`ReclaimDb.unsafeAll() > 0`) or a Free up run is active, the download
    button is replaced by "Finish Free up before updating". The notice always
    says "Install over the current app. Don't uninstall it first."
*   **G5 Warning re-post.** The "Don't uninstall yet" notification is posted
    again after boot, after the app is updated, and when the app is opened,
    whenever `unsafeAll() > 0`.
*   **G6 Profile diagnosis.** Each profile failure produces a fixed label that
    contains no secrets, such as `AUTH_HTTP_403` or `IMAGE_DECODE`. One live run
    on a real account identifies the failing step.
*   **G7 Profile outcome.** Either the profile photo loads for an account that
    has one, or, if Google refuses the profile permission, the network photo
    code is removed and the app keeps the initial letter. Either way the app
    stops making a failing profile request on every open.
*   **G8 Release contract.** Every release from 1.1 onward has a tag `vX.Y.Z`
    equal to `"v" + versionName`, attaches the APK under the fixed name
    `Photos-Unlimited.apk` plus `SHA256SUMS.txt` before it is published, and is
    never marked prerelease while being "latest". `build-release.sh` checks the
    tag against versionName.
*   **G9 Repository basics.** `SECURITY.md` (GitHub private vulnerability
    reporting, no personal email), one bug-report issue template with a required
    "I removed tokens, cookies and private photos" checkbox, one GitHub Actions
    workflow (unsigned build, unit tests, lint, publication scan), UTF-8 reading
    and an email pattern in `check-publication.py`, and the signing certificate
    SHA-256 fingerprint in the README and release notes.
*   **G10 Honest docs.** PRIVACY.md, the README, the site FAQ and every
    "Updates are manual" line say that the app contacts GitHub once a day and
    what GitHub can see (IP address, a generic user agent, the time). Versioned
    download links become `releases/latest` links.

## Non-Goals

*   Installing updates from inside the app, `REQUEST_INSTALL_PACKAGES`,
    DownloadManager, or checking the APK checksum in the app. Android's own
    check that the update is signed with the same key is the trust root.
*   A background update job or an update notification. The check runs only
    while the app is open.
*   Showing any text fetched from GitHub (release notes) in the app, or using the
    update check to deliver messages.
*   Registering for Google's Android developer verification, or an install guide
    for blocked phones.
*   Translation of anything. That is the 1.2 design.
*   Database schema changes. All three databases refuse unknown versions and have
    no downgrade path, so 1.1 keeps every schema as it is.
*   New sign-in methods for the profile photo beyond the diagnosis experiments
    listed in the Technical Plan.
*   CHANGELOG.md, CONTRIBUTING.md, F-Droid or fastlane metadata, reproducible
    builds, new screenshots.

## Open Questions

*   **Q1 Update source.** The GitHub REST API (`/repos/.../releases/latest`,
    read `tag_name`) or the `github.com/.../releases/latest` web redirect (read
    the version from the `Location` header). Decided in the Technical Plan.
*   **Q2 Notice placement.** A card on the Backup screen or a row in Settings.
    Decided in the Technical Plan.
*   **Q3 Developer verification.** Is Google currently blocking installs and
    updates of apps from unregistered developers on certified phones in Brazil,
    and does that include updates of already-installed apps? This decides one
    neutral sentence in the notice ("If Android refuses the install, keep using
    this version"). To be checked before the release, not designed around.
*   **Q4 Profile root cause.** Unknown until the live diagnosis runs. The likely
    cause is that Google refuses to give the Photos sign-in a token for the
    profile scopes.

## Critical User Journeys (CUJs)

### CUJ 1: A newer version is available

*   **Given**: 1.1.0 is installed, "Check for updates" is on, the last check was
    more than 24 hours ago, the phone is online, and GitHub's latest release is
    `v1.1.1`. No Free up item exists only on the phone.
*   **When**: The user opens the app.
*   **Then**: The app makes one request to GitHub in the background, finds
    1.1.1 > 1.1.0, and shows the update notice. Tapping "Download latest
    version" opens the fixed APK URL in the browser, and the browser downloads
    `Photos-Unlimited.apk`. The user taps the download and Android's installer
    updates the app in place. On the next open the notice is gone.

### CUJ 2: An update is available during an unfinished Free up

*   **Given**: An update is available, and Free up has 3 items deleted from
    Google Photos that are not yet re-uploaded (`unsafeAll() = 3`).
*   **When**: The user opens the app.
*   **Then**: The notice shows "Finish Free up before updating" instead of the
    download button. Once the items are re-uploaded and `unsafeAll() = 0`, the
    button comes back.

### CUJ 3: The phone reboots while items exist only on the phone

*   **Given**: `unsafeAll() > 0` and the phone restarts (or the app is updated
    by the user).
*   **When**: Android delivers `BOOT_COMPLETED` (or `MY_PACKAGE_REPLACED`).
*   **Then**: The "Don't uninstall Photos Unlimited yet" notification is posted
    again without the user opening the app.

### CUJ 4: The update check can't reach GitHub

*   **Given**: The phone is offline, GitHub is blocked, or GitHub answers with
    an error or a rate limit.
*   **When**: The daily check runs.
*   **Then**: Nothing visible happens. Backup status, error counts and
    notifications are unchanged. The next attempt is at least 24 hours later.

### CUJ 5: The user turns the check off

*   **Given**: The user doesn't want the app to contact GitHub.
*   **When**: They turn off "Check for updates" in Settings.
*   **Then**: The app makes no further GitHub requests, and any notice already
    shown is hidden.

### CUJ 6: The profile photo fails

*   **Given**: The account has a Google profile photo.
*   **When**: The user taps the avatar and "Refresh profile photo".
*   **Then (before the fix)**: The message names the failing step with a fixed
    label, for example "Couldn't load your Google profile photo (AUTH_HTTP_403).
    Backups are not affected."
*   **Then (after the fix)**: The photo appears, or the network photo feature is
    gone and the menu item no longer exists.

## Technical Plan (Architecture)

The four parts share no code except `Settings` and the new
`res/values/strings.xml`. They can be built and tested in any order.

```text
 MainActivity.onResume
   ├─ io: ReclaimService.warnIfOnlyOnPhone(...)      (part 2)
   ├─ io: UpdateCheck.runIfDue(...) ──HTTPS──▶ api.github.com   (part 1)
   │        └─ stores latest version / ETag / last attempt in Settings
   └─ drawBackup() ──▶ UpdateNotice.state(installed, latest, unsafe, busy)
                         └─ card: Download │ What's new │ Not now
                                   │
                                   └─ ACTION_VIEW ─▶ browser ─▶ github.com/.../releases/latest/download/Photos-Unlimited.apk
                                                                   └─ Android installer (user taps)

 BootReceiver (BOOT_COMPLETED, MY_PACKAGE_REPLACED)
   ├─ Scheduler.ensure(...)            (unchanged)
   └─ ReclaimService.warnIfOnlyOnPhone(...)   (part 2, new)

 Refresh profile photo ─▶ ProfileStore.refresh ─▶ ProfileApi.fetch
                                                    └─ ProfileApi.Failure(label)  (part 3)
```

### Part 1: Update check and notice

**1.1 `UpdateCheck` (new, plain Java, no Android types in its core).**

*   **Request.** `GET https://api.github.com/repos/venzellucas/photos-unlimited/releases/latest`
    with these headers and no others:
    *   `Accept: application/vnd.github+json`
    *   `X-GitHub-Api-Version: 2022-11-28`
    *   `User-Agent: PhotosUnlimited-UpdateCheck`. It's fixed: no app version,
        no device model, no Android version. The default Dalvik user agent would
        send the device model.
    *   `If-None-Match: <stored ETag>` when one is stored. GitHub does not count
        a `304 Not Modified` answer against its rate limit. Most days the answer
        is 304, so shared IPs mostly stay under the 60 requests per hour limit.
*   **Transport.** A new `HttpURLConnection` used only for this request:
    *   the address must be exactly the constant above;
    *   redirects are not followed;
    *   timeouts of 10 s to connect and 15 s to read;
    *   no cookies;
    *   the response is capped at 1 MiB, and a larger answer counts as a failure.

    It doesn't share or widen `PhotosApi.Network`'s Google-only allowlist.
*   **Parsing.** `org.json` (part of Android, so no new dependency). Read only
    `tag_name`, `draft` and `prerelease`. Accept the answer only if
    `draft == false`, `prerelease == false`, and `tag_name` matches
    `^v(\d{1,4})\.(\d{1,4})\.(\d{1,4})$`. Every other field is ignored and
    nothing from the response is ever shown or opened.
*   **Outcomes:**
    *   200 with a valid tag: store the version (as `major.minor.patch`) and the
        ETag.
    *   304: keep the stored version.
    *   Anything else (offline, timeout, 403, 404, 429, 5xx, bad JSON, bad tag):
        do nothing. No retry and no message. Nothing is written to backup status
        or `Settings.failure`.
*   **When it runs.** From `MainActivity.onResume`, on the existing `io`
    executor, only if all of these hold:
    *   "Check for updates" is on;
    *   the build is not a debug build;
    *   at least 24 hours have passed since the last *attempt*, or the clock
        has moved backwards since then.

    `lastAttempt` is written *before* the request, so a crash or a failing
    network can't cause repeated requests. When the check finishes and the
    Backup screen is showing, it redraws the notice.
*   **Testing.** The transport is an interface, so JVM tests can feed it
    answers: 200, 304, 403, oversized, malformed JSON, `prerelease: true`,
    `draft: true`, tags such as `v1.2`, `1.2.3`, `v1.2.3-rc1` and
    `v99999.0.0`, and a clock moving backwards.

**1.2 `UpdateNotice` (new, plain Java decision function).**

`state(installed, latest, dismissed, unsafeCount, freeUpRunning)` returns one
of three values:

*   `HIDDEN`: the check is off; or no version is stored; or the installed
    version name doesn't match `X.Y.Z` (debug and dev builds); or the stored
    version is not strictly newer than the installed one; or the user dismissed
    this exact version.
*   `BLOCKED`: newer, not dismissed, and `unsafeCount > 0` or `freeUpRunning`
    (`ReclaimService.busy()`).
*   `AVAILABLE`: newer, not dismissed, and Free up is safe.

The installed version comes from `PackageInfo.versionName`, the same way
`MainActivity.java:1751` reads it. The comparison runs when the screen is
drawn, not when the version is fetched. After the user installs the update,
installed equals latest and the card disappears without any extra code.

**1.3 The card on the Backup screen.**

*   **Placement.** In `drawBackup()`, after the backup counts line and before the
    "Backup folders" / "Recent activity" group. That's below the main backup
    action, as `docs/design-system.md:71` requires. Normal card style, no
    warning colors, no ⚠.
*   **AVAILABLE:**
    *   Title "Version X.Y.Z is available".
    *   Body "Install it over this app. Don't uninstall Photos Unlimited first.
        If Android doesn't allow the install, keep using this version." The
        last sentence depends on Q3.
    *   Buttons:
        *   "Download latest version" (tonal) opens
            `https://github.com/venzellucas/photos-unlimited/releases/latest/download/Photos-Unlimited.apk`.
        *   "What's new" (text button) opens
            `https://github.com/venzellucas/photos-unlimited/releases/tag/vX.Y.Z`.
        *   "Not now" (text button) stores X.Y.Z as dismissed. The card comes
            back only for a newer version.
*   **BLOCKED:** same title. Body "Finish Free up before updating. Some photos
    are only on this phone until Free up uploads them again." One button,
    "Open Free up", goes to the Storage screen. "What's new" stays.
*   **Building the URLs.** Both URLs are built from the constant repository
    path and the parsed numeric version, never taken from the response. They
    open with `Intent.ACTION_VIEW`. If no browser can handle it, the app shows
    the existing toast style: "No browser found".
*   **The unsafe count.** `ReclaimDb.unsafeAll()` is read on `io` during the
    same `onResume` pass that re-posts the warning (part 2), and cached in a
    field for drawing. `ReclaimService.busy()` is read at draw time.

**1.4 Setting.**

In `drawSettings()`, in the "Account and app" card, a toggle "Check for updates"
with the supporting line "Once a day while the app is open, asks GitHub for the
newest version. GitHub sees your IP address."

*   Stored as `Settings.updateCheck()`, a boolean that defaults to `true` when
    absent. That covers upgrades from 1.0.0 too.
*   Turning it off clears the stored version and ETag, so the card disappears
    at once.
*   Turning it on doesn't check right away. The next app open checks, because
    `lastAttempt` was cleared.

**1.5 Release contract** (applies to every release from 1.1.0 on).

*   Tag is `vX.Y.Z`, and it must equal `"v" + versionName`. versionCode always
    goes up. `tools/build-release.sh` refuses to build when the tag passed to
    it doesn't match versionName.
*   The APK is attached as `Photos-Unlimited.apk`, the same name in every
    release, with `SHA256SUMS.txt` next to it. The release is created with
    both files in one `gh release create` call, so it never exists as "latest"
    without them.
*   No release is ever marked prerelease, and "latest" never moves backwards.
    A fix after a bad release is a new, higher version.
*   The GitHub account and repository are never renamed. A rename would let
    someone else claim the old name, and every installed app would point at
    their releases.

### Part 2: "Don't uninstall yet" re-post

*   **`BootReceiver.onReceive`.** Already registered for `BOOT_COMPLETED` and
    `MY_PACKAGE_REPLACED`. It keeps calling `Scheduler.ensure`, then calls
    `goAsync()` and runs `ReclaimService.warnIfOnlyOnPhone(context, "")` on a
    background thread, finishing the pending result when it's done. That is
    one small database read, well within the receiver time limit.
*   **`MainActivity.onResume`.** Runs the same call on `io`. It also saves the
    count it reads, for part 1.
*   **`warnIfOnlyOnPhone`.** Before it posts, it creates the `reclaim`
    notification channel if it's missing (creating a channel twice does
    nothing). Today the channel is created only by `ReclaimService.onCreate`,
    so a boot-time post on a phone where Free up never ran in this install
    would fail silently.
*   **Notification permission off.** If notifications aren't allowed, nothing
    is shown. The Storage screen's Free up card already shows the
    only-on-phone state when the app is opened. That fallback is unchanged.

### Part 3: Profile photo diagnosis, then fix or removal

**3.1 Fixed failure labels.** `ProfileApi` throws
`ProfileApi.Failure extends IOException` with a `label()` made only of
`[A-Z0-9_]`:

| Step | Labels |
| --- | --- |
| Credential | `NO_CREDENTIAL` |
| Token request | `AUTH_HTTP_<status>`, `AUTH_ERROR_<value>` (Google's `Error=` value, cut to `[A-Za-z]{1,40}`), `AUTH_NO_TOKEN` |
| Userinfo | `USERINFO_HTTP_<status>`, `USERINFO_BAD_JSON`, `PROFILE_FIELDS_MISSING`, `PROFILE_EMAIL_MISMATCH`, `PICTURE_HOST` |
| Image | `IMAGE_HTTP_<status>`, `IMAGE_REDIRECT_HOST`, `IMAGE_TYPE`, `IMAGE_TOO_LARGE`, `IMAGE_DECODE` |
| Other | `NETWORK` (any other `IOException`), `TIMEOUT` |

To read Google's `Error=` value, the token request no longer goes through
`PhotosApi.Network`, which throws away the body of any non-2xx answer.
`ProfileApi.Network` makes that one POST itself:
*   the address must be `PhotosApi.AUTH`;
*   it reads the body on any status, error body included, capped at 16 KiB;
*   the token request it sends is the same as today.

`PhotosApi` itself isn't changed, so the upload path is untouched. The label
never contains tokens, emails, cookies or URLs.

**3.2 Showing the label.** `MainActivity.refreshProfile` catches
`ProfileApi.Failure` and shows "Couldn't load your Google profile photo
(LABEL). Backups are not affected." The current text is "Google profile photo
is unavailable. Backup sign-in is unchanged." Any other exception shows the
`NETWORK` label. Automatic retries after a failure move from every hour to
every 24 hours (`ProfileStore.refresh`), so a failing token request for the
main account goes out at most once a day.

**3.3 Live diagnosis.** A release-signed test build of 1.1.0 is installed over
1.0.0, first on the reference emulator with the spare account. The steps:

1.  Read the label.
2.  If it's an `AUTH_*` label, the spare account is enough to diagnose: the
    token refusal doesn't depend on whether a photo exists.
3.  If the spare account gets past the token request, ask you to install the
    test build on your own phone and read the label. That's needed only if the
    remaining question is about the photo itself.

**3.4 Experiments if the token is refused.** Run in order, stopping at the
first that works. Each is a change to the form fields in `ProfileApi.fetch`:

1.  Add `has_permission=1`. The first sign-in exchange sends it
    (`PhotosApi.java:117`), and the profile request doesn't.
2.  Ask for one scope (`oauth2:https://www.googleapis.com/auth/userinfo.profile`)
    instead of three. Keep the email check by comparing the userinfo `email`
    only if it's present.
3.  Request the profile token as the Google Play services identity
    (`app=com.google.android.gms`, `client_sig=GMS_SIG`). This is a separate
    token, so the upload credential isn't affected. It changes what PRIVACY.md
    must say, so it ships only if you approve it after the diagnosis.

**3.5 Outcome A (a fix works).** Keep the working change and the labels. Test
the decision paths with the existing fake-transport
`ProfileInstrumentedTest`, and the real path with one live run.

**3.6 Outcome B (Google refuses every variant).**
*   Remove `ProfileApi`, the network part of `ProfileStore`, and the "Refresh
    profile photo" menu item.
*   On first start of 1.1, delete the cached profile file and its preferences
    once (`ProfileStore.clear`).
*   The account icon is always the initial letter (`ProfileStore.icon`, kept),
    and the account dialog shows the email address instead of a name.
*   Remove the README line telling users to tap "Refresh profile photo".

### Part 4: Repository basics (public repo, no APK change)

*   **`SECURITY.md`.** Report vulnerabilities through GitHub's "Report a
    vulnerability" (private vulnerability reporting, turned on in repo
    settings). No personal email. Never post tokens, cookies or private photos.
    Only the latest release is supported.
*   **`.github/ISSUE_TEMPLATE/bug_report.yml`.** Fields:
    *   app version;
    *   Android version and phone model;
    *   what happened and what was expected;
    *   the profile label or Account diagnostic result, if relevant;
    *   a required checkbox "I removed tokens, cookies, email addresses and
        private photos".

    Plus `config.yml` pointing security reports to the private form.
*   **`.github/workflows/ci.yml`.** Runs on push and pull request, with
    `permissions: contents: read`. Actions are pinned by commit SHA. It runs on
    Ubuntu with JDK 17 and runs:
    *   `./gradlew testDebugUnitTest lintDebug assembleDebug`. This needs no
        signing key: the debug build uses the debug key, and the release
        signing block only applies when `PHOTOS_KEYSTORE` is set.
    *   `python3 tools/check-publication.py`.
*   **`tools/check-publication.py`.**
    *   Read every text file as UTF-8, and fail with the file name if one can't
        be decoded, instead of skipping it silently.
    *   Add an email-address pattern. Allowed: `noreply@github.com`-style
        addresses and the documented example domain.
*   **Signing certificate fingerprint.** Get the SHA-256 of the signing
    certificate from the 1.0.0 APK with `apksigner verify --print-certs`.
    Publish it in the README "Install" section and in each release's notes,
    with one sentence saying Android refuses any update not signed by this
    key.

### Docs and privacy text (same change as part 1)

*   **PRIVACY.md:** a new "GitHub" section:
    *   once a day while the app is open, the app asks api.github.com for the
        latest release;
    *   GitHub sees the IP address, the fixed user agent and the time;
    *   no account, photo or device identifiers are sent;
    *   it can be turned off in Settings.
*   **README (public and private) and site FAQ:** replace "Updates are
    manual" and the "only contacts Google" wording. Change the pinned
    `v1.0.0` download links to `releases/latest/download/Photos-Unlimited.apk`.
    Remove the hard-coded size and test counts from the site.
*   **RELEASE_NOTES.md:** a 1.1.0 entry announcing the update check, the
    default, and how to turn it off.
*   **`docs/design-system.md`:** add the update card to the component list and
    record the "notices sit below the main action" rule.

## Alternatives Considered

### Alternative A: Install updates from inside the app

*   **Pros**: One tap. The app could download, check the checksum and start
    the installer itself. On Android 12+ it could even update itself without a
    confirmation screen in some cases.
*   **Cons**: Needs `REQUEST_INSTALL_PACKAGES`, a powerful permission, in an app
    that already deletes photos. Adds download, storage and installer code that
    must be kept safe. Can still be blocked by phone makers.
*   **Reason for Rejection**: Rejected by author: too permissive. The browser
    and Android's installer do the same job with no new permission.

### Alternative B: Read the version from the `github.com/.../releases/latest` redirect

*   **Pros**: No JSON parsing. Not subject to the 60 requests per hour API limit.
*   **Cons**: GitHub doesn't document this behaviour. If it changes, the check
    stops working without any error anyone sees.
*   **Reason for Rejection**: Rejected by author: the documented API is
    preferred. Conditional requests (`If-None-Match`) keep most checks out of
    the rate limit.

### Alternative C: Redirect first, API as fallback

*   **Pros**: Works in the most network situations.
*   **Cons**: Two code paths to write, test and keep working.
*   **Reason for Rejection**: Rejected by author: not worth the second path.

### Alternative D: Background daily check with a notification

*   **Pros**: Reaches users who rarely open the app.
*   **Cons**:
    *   Every JobScheduler job in this app runs `BackupEngine.run`
        (`BackupJob.java:16`), so a new job would start a backup.
    *   `Scheduler.configure` cancels all jobs when backup is off.
    *   It needs a new notification channel, its own strings and boot handling.
*   **Reason for Rejection**: Rejected by author: more machinery than the
    benefit justifies. A check while the app is open is enough for a backup app
    people open regularly.

### Alternative E: No network check, only a "Check for updates" link

*   **Pros**: No new network contact. Nothing to disclose. Users could also
    follow GitHub "Watch → Releases" or the `releases.atom` feed.
*   **Cons**: Users have to think of checking. Safety fixes for Free up and for
    Google protocol changes wouldn't reach the people who need them.
*   **Reason for Rejection**: Rejected by author: the point of the feature is to
    reach users who don't look.

### Alternative F: Update notice in Settings with a tab dot, or in both places

*   **Pros**: Settings-only leaves the main screen untouched. "Both" is the
    hardest to miss.
*   **Cons**: Settings-only is easy to miss. "Both" adds the most interface.
*   **Reason for Rejection**: Rejected by author: a single dismissible card on
    the Backup screen, below the main action.

### Alternative G: Button opens the release page instead of the APK

*   **Pros**: Shows release notes and checksums. Works even if the asset name
    changes.
*   **Cons**: Non-technical users see "Source code (zip)" and other files and
    may download the wrong one.
*   **Reason for Rejection**: Rejected by author: the direct APK link, with a
    separate "What's new" link to the release page.

### Alternative H: Read the download URL from the API response (`browser_download_url`)

*   **Pros**: Doesn't depend on a fixed asset name.
*   **Cons**: The app would open an address supplied by the network. A
    compromised or spoofed answer could send users anywhere.
*   **Reason for Rejection**: Rejected by author: both URLs are built from the
    constant repository path and the parsed version number. The fixed asset
    name is enforced by the release contract instead.

### Alternative I: Ship translation in the same release

*   **Pros**: One release instead of two.
*   **Cons**: Translation needs a refactor of stored messages that touches the
    Free up journal. It's about 450 string changes. Every month it delays the
    update check is a month in which 1.1 users can't be told about the next
    release.
*   **Reason for Rejection**: Rejected by author: translation is the 1.2 design.

### Alternative J: Keep trying new sign-in paths until the profile photo works

*   **Pros**: The photo might eventually load.
*   **Cons**: Each attempt is another unusual token request on the account the
    whole app depends on. New identities change what the privacy policy must
    say.
*   **Reason for Rejection**: Rejected by author: try the three listed
    experiments, then remove the network photo if they all fail.

### Alternative K: Register for Google's developer verification, or add an install guide for blocked phones

*   **Pros**: Updates would install on phones where Google enforces
    verification.
*   **Cons**: Costs a fee and needs identity registration. A guide makes the
    website more complex.
*   **Reason for Rejection**: Rejected by author: stay unregistered and keep the
    site simple. The notice says at most "If Android doesn't allow the install,
    keep using this version."

### Alternative L: CHANGELOG, CONTRIBUTING, F-Droid/fastlane metadata, reproducible builds

*   **Pros**: Common in larger open-source Android projects.
*   **Cons**:
    *   CHANGELOG duplicates RELEASE_NOTES.md.
    *   F-Droid won't accept the non-free `play-services-auth` dependency.
    *   fastlane metadata is a store listing for stores the app isn't in.
    *   Reproducible builds are several days of work with R8 and a private
        signing key.
*   **Reason for Rejection**: Rejected by author for 1.1: keep the repository
    small. Reproducible builds can be revisited once CI exists.

--------------------------------------------------------------------------------

## Non-Functional Requirements

*   **Security and privacy.**
    *   The update check sends no account, photo or device identifiers. Its
        user agent is fixed.
    *   It talks to one fixed HTTPS address, follows no redirects, and caps
        the response size.
    *   No text or URL from the response is ever shown or opened.
    *   Profile failure labels contain only `[A-Z0-9_]`, never tokens, emails
        or URLs.
    *   No new Android permissions.
*   **Account safety.** The upload credential and `PhotosApi` request
    handling don't change. Failed profile token requests drop from at most
    hourly to at most daily.
*   **Data safety.** No database schema changes. The update button is never
    offered while Free up has items only on the phone or a run is active.
*   **Rollout and rollback.** 1.0.0 → 1.1.0 is an in-place upgrade with the
    same signing key. All stored data stays readable. Rollback is
    forward-only: ship 1.1.1 with a higher versionCode, because Android
    refuses a downgrade over an installed app.

--------------------------------------------------------------------------------

## Detailed Implementation Plan

Paths are relative to the private repo the dev repo unless they
start with `public:`, which means the public repo. App code
changes are made in the private repo and copied to the public repo the same way
as for 1.0.0, followed by `public:tools/check-publication.py`.

### Target Files to Create/Modify

#### 1. `app/build.gradle` (Modify)

*   **Type / Scope**: Build configuration.
*   **Proposed Changes**: Set `versionCode 7` and `versionName '1.1.0'`. No new
    dependencies.

#### 2. `app/src/main/res/values/strings.xml` (Create)

*   **Type / Scope**: Android string resources (English only).
*   **Proposed Changes**: All new text for 1.1:
    *   update card title, body, BLOCKED body and buttons;
    *   the Settings toggle and its supporting line;
    *   "No browser found";
    *   the profile failure message with a `%1$s` label placeholder.

    Existing strings are not moved. That is part of 1.2.

#### 3. `app/src/main/java/app/photos/unlimited/UpdateCheck.java` (Create)

*   **Type / Scope**: Plain Java class plus a small Android-facing transport.
*   **Proposed Changes**:
    *   Constants: `REPO = "venzellucas/photos-unlimited"`,
        `API = "https://api.github.com/repos/" + REPO + "/releases/latest"`,
        `USER_AGENT = "PhotosUnlimited-UpdateCheck"`, `MAX_BYTES = 1 << 20`.
    *   `interface Transport { Response get(String url, Map<String,String> headers) throws IOException; }`
        and `record Response(int status, String etag, byte[] body)`.
    *   `static Result check(Transport t, String etag)`. Builds the headers in
        1.1, calls the transport, and returns one of:
        *   `Result.latest(version, etag)` on 200 with a valid tag;
        *   `Result.unchanged()` on 304;
        *   `Result.failed()` otherwise.
    *   `static Version parseTag(String tag)`. Accepts only
        `^v(\d{1,4})\.(\d{1,4})\.(\d{1,4})$`.
    *   `static Version parseRelease(byte[] json)` uses `org.json`. It rejects
        drafts, prereleases, missing or invalid tags, and bodies over
        `MAX_BYTES`.
    *   `record Version(int major, int minor, int patch) implements Comparable<Version>`,
        with `parse(String)` for `versionName` (`X.Y.Z` only, else `null`) and
        `toString()` returning `X.Y.Z`.
    *   `static boolean due(long now, long lastAttempt)`: `true` when
        `now - lastAttempt >= 24h` or `now < lastAttempt`.
    *   `static final class Network implements Transport`: an
        `HttpURLConnection` that only accepts the exact `API` string, with
        `setInstanceFollowRedirects(false)`, timeouts of 10 s and 15 s,
        `setUseCaches(false)`, and a capped body read. For status 304 or ≥ 400
        it doesn't read the body.
    *   `static void runIfDue(Context, Settings, Runnable onDone)`. Checks the
        setting, the debuggable flag
        (`ApplicationInfo.FLAG_DEBUGGABLE`, so no `BuildConfig` change is
        needed), and `due`. Writes `lastAttempt`, runs `check`, stores the
        result, then calls `onDone`. Catches every exception.

#### 4. `app/src/main/java/app/photos/unlimited/UpdateNotice.java` (Create)

*   **Type / Scope**: Plain Java decision function.
*   **Proposed Changes**: `enum State { HIDDEN, BLOCKED, AVAILABLE }` and
    `static State state(boolean enabled, String installedName, String latest,
    String dismissed, int unsafeCount, boolean freeUpRunning)`, following
    the rules in Technical Plan 1.2. Also `static String downloadUrl()` and
    `static String releaseUrl(UpdateCheck.Version v)`, both built from
    `UpdateCheck.REPO`.

#### 5. `app/src/main/java/app/photos/unlimited/Settings.java` (Modify)

*   **Type / Scope**: SharedPreferences wrapper.
*   **Proposed Changes**:
    *   `updateCheck()` (default `true`) and `updateCheck(boolean)`. Turning it
        off calls `clearUpdate()`.
    *   `updateLastAttempt()` and its setter, `updateLatest()` and its setter,
        `updateEtag()` and its setter, `updateDismissed()` and its setter.
    *   `clearUpdate()` removes latest, ETag, lastAttempt and dismissed.
    *   New keys use an `update_` prefix. No existing key changes.

#### 6. `app/src/main/java/app/photos/unlimited/MainActivity.java` (Modify)

*   **Type / Scope**: UI.
*   **Proposed Changes**:
    *   **Field.** `private volatile int unsafeCount`.
    *   **`onResume`.** Submit one `io` task that:
        1.  calls `ReclaimService.warnIfOnlyOnPhone(this, "")` and saves the
            unsafe count. Change `warnIfOnlyOnPhone` to return the count;
        2.  calls `UpdateCheck.runIfDue(...)`;
        3.  posts a redraw of the update card to the UI thread if
            `screen == 1`.
    *   **`drawBackup`.** After `page.addView(counts)`, add
        `updateCard = ui.card(page, false)` and call `drawUpdateCard()`.
    *   **`drawUpdateCard()`.** Reads `UpdateNotice.state(...)` and fills the
        card for AVAILABLE or BLOCKED, or hides it for HIDDEN. It is also
        called after "Not now" and after the setting changes.
    *   **`openUrl(String)`.** Runs `ACTION_VIEW`. On
        `ActivityNotFoundException` it shows "No browser found".
    *   **`drawSettings`.** Add the "Check for updates" toggle to the
        "Account and app" card, above "About and open-source credits".
    *   **`refreshProfile`.** Catch `ProfileApi.Failure` and show the labelled
        message. Any other exception uses the label `NETWORK`.
    *   **Outcome B only.** Remove the "Refresh profile photo" option from the
        account menu (around line 1545) and call `ProfileStore.icon` with a
        null bitmap.

#### 7. `app/src/main/java/app/photos/unlimited/BootReceiver.java` (Modify)

*   **Type / Scope**: Broadcast receiver.
*   **Proposed Changes**: After `Scheduler.ensure(c)`:
    *   `PendingResult r = goAsync()`;
    *   start a new thread that runs `ReclaimService.warnIfOnlyOnPhone(c, "")`
        inside try/catch and `finally r.finish()`.

    It acts only for `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`, the two
    actions already registered.

#### 8. `app/src/main/java/app/photos/unlimited/ReclaimService.java` (Modify)

*   **Type / Scope**: Foreground service plus static helper.
*   **Proposed Changes**:
    *   Move the channel creation now in `onCreate` (around line 50) into
        `static void ensureChannel(Context)`, called from `onCreate` and at the
        start of `warnIfOnlyOnPhone`.
    *   `warnIfOnlyOnPhone` returns `int` (the unsafe count). Callers that
        ignore it are unchanged.

#### 9. `app/src/main/java/app/photos/unlimited/ProfileApi.java` (Modify)

*   **Type / Scope**: Profile network client.
*   **Proposed Changes**:
    *   Add `static final class Failure extends IOException` with
        `String label()`. Its constructor checks the label against
        `[A-Z0-9_]{1,48}`.
    *   Every `throw new IOException(...)` in `fetch`, `parse`, `imageAddress`
        callers and `Network.call` becomes `throw new Failure(<label>)`, with
        labels from Technical Plan 3.1.
    *   `Network.call` for `PhotosApi.AUTH` uses its own `HttpURLConnection`
        POST:
        *   exact address check;
        *   same headers and body as today;
        *   `setInstanceFollowRedirects(false)`;
        *   reads `getErrorStream()` when status ≥ 400, capped at 16 KiB.

        It returns the status with the body so `fetch` can create
        `AUTH_HTTP_<n>` or `AUTH_ERROR_<value>`. That needs a small change to
        `PhotosApi.Response`, or a profile-only record:
        `record AuthResponse(int status, byte[] body)`. Use the profile-only
        record so `PhotosApi` isn't touched.
    *   Experiments 3.4.1-3.4.3 change only the form fields in `fetch`. Each
        is tried in a scratch build, and only the one that works is
        committed. Experiment 3 needs the author's approval before it ships.
    *   **Outcome B:** delete this file.

#### 10. `app/src/main/java/app/photos/unlimited/ProfileStore.java` (Modify)

*   **Type / Scope**: Profile cache.
*   **Proposed Changes**:
    *   The retry delay after a failure changes from 1 hour to 24 hours.
        Write `retryAfter = now + 24h` before the request instead of
        `now + 1h`.
    *   **Outcome B:** remove `refresh` and `decode`, keep `icon`, `name`
        (always returns empty) and `clear`. In `Application` start or
        `MainActivity.onCreate`, call `clear` once, guarded by a
        `profile_removed` preference, to delete the cached image.

#### 11. `app/src/main/AndroidManifest.xml` (No change, verified)

*   **Type / Scope**: Manifest.
*   **Proposed Changes**: None. `BootReceiver` already listens for both
    actions, and no new permission is needed. `ACTION_VIEW` with an https URL
    needs no `<queries>` entry, because the code uses `startActivity` with a
    catch rather than `resolveActivity`.

#### 12. `app/src/test/java/app/photos/unlimited/UpdateNoticeTest.java` (Create)

*   **Type / Scope**: JVM unit test (no `org.json`: JVM tests use Android stubs
    that return defaults).
*   **Proposed Changes**:
    *   `Version.parse` and `parseTag`: valid; leading zeros; `v1.2`; `1.2.3`
        with no v; `v1.2.3-rc1`; whitespace; 5-digit parts; null.
    *   Ordering: 1.0.10 > 1.0.9; 2.0.0 > 1.99.99.
    *   `due` at 23h59m, 24h and with the clock moving backwards.
    *   `state` for every combination: disabled, no latest, same version,
        older version, dismissed equals latest, dismissed older than latest,
        installed `1.1.0-dev`, `unsafeCount > 0`, running, and safe.
    *   The URL builders return the exact expected strings.
    *   `UpdateCheck.check` with a fake transport, using non-JSON paths only:
        304 returns unchanged and sends the stored ETag; 403, 429, 500 and an
        IOException return failed; the request headers equal exactly the four
        listed.

#### 13. `app/src/androidTest/java/app/photos/unlimited/UpdateCheckInstrumentedTest.java` (Create)

*   **Type / Scope**: Instrumentation test (needs the real `org.json`). Runs on
    the photos-api37 emulator, never on the signed-in reference emulator.
*   **Proposed Changes**: `parseRelease` with:
    *   a real-shaped GitHub response;
    *   `draft: true` and `prerelease: true`;
    *   a missing `tag_name`, and a numeric one;
    *   malformed JSON;
    *   an oversized body.

    Also `check` returning `latest` on a fake 200.

#### 14. `app/src/androidTest/java/app/photos/unlimited/ProfileInstrumentedTest.java` (Modify)

*   **Type / Scope**: Instrumentation test.
*   **Proposed Changes**:
    *   **Outcome A:** add cases for each label: auth 403 with
        `Error=BadAuthentication`, auth 200 without `Auth`, userinfo 401,
        missing `email_verified`, email mismatch, bad picture host, image
        redirect to a disallowed host, non-image type, and undecodable bytes.
        Check that `label()` contains no `@`, `/` or `=`.
    *   **Outcome B:** delete the network tests and keep a test that the
        initial-letter icon is drawn for an email.

#### 15. `app/src/androidTest/java/app/photos/unlimited/BackupInstrumentedTest.java` (Modify)

*   **Type / Scope**: Instrumentation test.
*   **Proposed Changes**:
    *   Add a test that broadcasts to `BootReceiver` with a seeded `ReclaimDb`
        row in the DELETED state, and asserts notification 7403 is active.
    *   Add a test that with no unsafe rows it is cancelled.

#### 16. `tools/build-release.sh` (Modify; mirrored to `public:tools/build-release.sh`)

*   **Type / Scope**: Release script.
*   **Proposed Changes**:
    *   Take a required `RELEASE_TAG` variable and refuse to build unless it
        equals `v` + the versionName read from `app/build.gradle`.
    *   After the build, copy the APK to `dist/<version>/Photos-Unlimited.apk`
        and write `SHA256SUMS.txt` for that name.
    *   Print the signing certificate SHA-256 from
        `apksigner verify --print-certs`.

#### 17. `tools/verify-release.py` (Modify)

*   **Type / Scope**: Private release check.
*   **Proposed Changes**: Compare against the 1.0.0 APK instead of 0.2.0, and
    look for the fixed asset name.

#### 18. `public:tools/check-publication.py` (Modify)

*   **Type / Scope**: Pre-publication scan.
*   **Proposed Changes**:
    *   `read_text(encoding="utf-8")`. Fail with the path on
        `UnicodeDecodeError` for files with text extensions, instead of
        skipping them.
    *   Add an email regex. Allowed: `*@users.noreply.github.com`,
        `noreply@*` and `@example.com`.

#### 19. `public:SECURITY.md` (Create)

*   **Type / Scope**: Policy doc.
*   **Proposed Changes**: As in Technical Plan Part 4. About 20 lines.

#### 20. `public:.github/ISSUE_TEMPLATE/bug_report.yml` and `public:.github/ISSUE_TEMPLATE/config.yml` (Create)

*   **Type / Scope**: GitHub issue forms.
*   **Proposed Changes**: As in Technical Plan Part 4.
    `blank_issues_enabled: false`. The `contact_links` entry points to the
    security advisory form.

#### 21. `public:.github/workflows/ci.yml` (Create)

*   **Type / Scope**: GitHub Actions.
*   **Proposed Changes**:
    *   Triggers: `push` and `pull_request` on `main`, with
        `permissions: contents: read`.
    *   Steps, all actions pinned by commit SHA:
        1.  `actions/checkout`;
        2.  `actions/setup-java` with Temurin 17;
        3.  `android-actions/setup-android`, or the runner's preinstalled SDK
            with `sdkmanager` for platform 37 and build-tools;
        4.  `./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug`;
        5.  `python3 tools/check-publication.py`.
    *   No secrets are used.

#### 22. `public:PRIVACY.md` (Modify)

*   **Type / Scope**: Privacy policy.
*   **Proposed Changes**:
    *   Add the "GitHub" section from Technical Plan "Docs and privacy text".
    *   Update the summary line that says the app contacts only Google.

#### 23. `public:README.md` and `README.md` (Modify)

*   **Type / Scope**: Docs.
*   **Proposed Changes**:
    *   Version line: replace the pinned `v1.0.0` links (lines 7-11) with
        `releases/latest/download/Photos-Unlimited.apk` and
        `releases/latest`.
    *   Replace "Updates are manual" (line 39) with the update-check
        description and how to turn it off.
    *   Profile photo: Outcome A, keep the "Refresh profile photo" line;
        Outcome B, remove it.
    *   Add a "Verify the APK" line with the certificate fingerprint and a
        link to SECURITY.md.

#### 24. `public:docs/index.html` (Modify)

*   **Type / Scope**: GitHub Pages site.
*   **Proposed Changes**:
    *   Lines 41, 44, 92 and 93: links to
        `releases/latest/download/Photos-Unlimited.apk`,
        `releases/latest/download/SHA256SUMS.txt` and `releases/latest`.
    *   Remove "3.5 MB" and the version numbers in lines 45 and 92.
    *   Replace "Updates are manual." with "The app tells you when a new
        version is out."
    *   Update the FAQ privacy answer.

#### 25. `public:RELEASE_NOTES.md` (Modify)

*   **Type / Scope**: Release notes.
*   **Proposed Changes**:
    *   Add a 1.1.0 entry: the update check (on by default, how to turn it
        off, what GitHub sees), the "Don't uninstall yet" re-post, the
        profile outcome, and the certificate fingerprint.
    *   Note the fixed asset name.

#### 26. `docs/design-system.md` (Modify)

*   **Type / Scope**: Design doc.
*   **Proposed Changes**:
    *   Status changes to 1.1.0.
    *   Add the update card under components, with its placement rule and no
        warning styling.
    *   Fix the stale line saying Free up is not part of the release.

#### 27. `public:app/**` (Sync)

*   **Type / Scope**: Public mirror of app code.
*   **Proposed Changes**: Copy items 1-15 the same way as for 1.0.0, then run
    `check-publication.py`.

### Verification Plan

*   **Compilation/Build**: on the build host,
    `./gradlew :app:assembleDebug :app:assembleRelease`, and
    `tools/build-release.sh` with `RELEASE_TAG=v1.1.0`. Also check that
    `RELEASE_TAG=v1.0.9` is refused.
*   **Unit Tests**: `./gradlew :app:testDebugUnitTest`. That's the existing 57
    plus the new `UpdateNoticeTest`.
*   **Static Analysis & Linting**: `./gradlew :app:lintDebug :app:lintRelease`
    with zero new warnings. `python3 public:tools/check-publication.py` passes.
*   **Instrumentation** (photos-api37 emulator only):
    `UpdateCheckInstrumentedTest`, `ProfileInstrumentedTest`,
    `BackupInstrumentedTest`, and the existing suite.
*   **Live checks:**
    1.  **Update check against the real GitHub API.**
        1.  Install a release-signed 1.0.9 test build (versionName `1.0.9`).
            It sees v1.0.0 as older, so no card appears. Confirm the request
            was made: `lastAttempt` is set and the ETag is stored.
        2.  Temporarily set the installed versionName to `0.9.0`. The card
            appears, and "Download latest version" downloads
            `Photos-Unlimited.apk`. This requires the 1.0.0 release to also
            carry that asset name. Before the test, upload a copy of the
            1.0.0 APK named `Photos-Unlimited.apk` to the v1.0.0 release.
    2.  **Blocked state.** Seed a DELETED journal row on the test emulator.
        The card shows "Finish Free up before updating".
    3.  **Re-post after boot.** With the seeded row, `adb reboot`, and
        notification 7403 is posted without opening the app. With
        `adb install -r` of a newer build, it's posted on
        `MY_PACKAGE_REPLACED`.
    4.  **Profile diagnosis** on the reference emulator with the spare
        account. That emulator runs only `LiveReclaimProbe`, so this step is
        a manual tap on "Refresh profile photo" and reading the label. If
        needed, the author does the same on their own phone.
    5.  **Upgrade.** Install 1.0.0, then 1.1.0, in place. Sign-in, settings
        and the journal are kept.
*   **E2E/Integration**: `tools/verify-release.py` against the 1.1.0 APK.
*   **Publish:**
    1.  `gh release create v1.1.0` with both files in one call.
    2.  Check
        `curl -sI https://github.com/venzellucas/photos-unlimited/releases/latest/download/Photos-Unlimited.apk`
        redirects to the v1.1.0 asset.

--------------------------------------------------------------------------------

<!-- Provenance: Indicates initial structural drafting via EGM. -->

*Drafted with EGM*
