# Photos Unlimited 1.1: update notice, safety re-post, profile photo, repository basics

*   **Author:** venzellucas
*   **Status:** Approved (EGM review: Stage 9, two rounds, 2026-11-20)
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
5.  **Library scan diagnosis.** On the author's real account (a large
    library) the Storage scan stops with a generic message. 1.1
    makes the scan name the step that failed, so the cause can be found and
    fixed.

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

**The Storage scan stops on large libraries, and the app can't say why.**
`InventoryService.scan` turns every `IOException` into "Scan interrupted or
metadata format unavailable" (`InventoryService.java:122-127`) and every other
exception into "The scan stopped" (`:128-130`). Possible causes include a
response over the 4 MiB cap in `PhotosApi.Network` (`PhotosApi.java:690`), one
item text over 16 KiB (`LibraryProtocol.text`), an item with no identifier
or details (`:124, :126`), or a cursor that doesn't advance (`:267-300`).
Nobody can tell which.

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
*   **G11 Scan diagnosis.** When the Storage scan stops, its message includes a
    fixed label (for example `SCAN_RESPONSE_TOO_LARGE` or `SCAN_ITEM_TEXT_LONG`)
    that contains no secrets. One run on the author's account identifies the
    cause. Fixing the cause is a follow-up change, sized once the label is
    known.

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
*   **Q3 Developer verification (closed, no design dependency).** Whether
    Google currently blocks installs from unregistered developers in Brazil
    doesn't change the design: the notice sentence "If Android doesn't allow the
    install, keep using this version" is true either way and ships
    unconditionally. Checking enforcement stays a release-notes task.
*   **Q4 Profile root cause.** Unknown until the live diagnosis runs. The likely
    cause is that Google refuses to give the Photos sign-in a token for the
    profile scopes.
*   **Q5 Scan root cause.** Unknown until the author runs the labelled scan on
    their account.

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

### CUJ 7: The Storage scan stops on a large library

*   **Given**: The author's account has a large library.
*   **When**: They run the Storage scan and it stops.
*   **Then**: The Storage screen message ends with a fixed label, for example
    "The scan stopped (SCAN_RESPONSE_TOO_LARGE). Results are partial.", which
    the author reports so the cause can be fixed.

## Technical Plan (Architecture)

The four parts share no code except `Settings` and the new
`res/values/strings.xml`. They can be built and tested in any order.

```text
 MainActivity.onResume
   ├─ io: ReclaimService.warnIfOnlyOnPhone(...)      (part 2)
   ├─ io: UpdateCheck.runIfDue(...) ──HTTPS──▶ api.github.com   (part 1)
   │        └─ stores latest version / ETag / last attempt in Settings
   └─ drawBackup() ──▶ UpdateNotice.state(enabled, installed, latest,
                         dismissed, unsafe, busy)
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
        last sentence ships unconditionally (Q3 is closed).
    *   Buttons:
        *   "Download latest version" (filled button) opens
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
*   **The unsafe count.** It starts as unknown (`-1`), and `UpdateNotice`
    treats unknown as HIDDEN, so the Download button can't appear before the
    count is read. Every Backup-screen draw reads `ReclaimDb.unsafeAll()` on
    the `io` executor before it fills the card. The `onResume` pass (part 2)
    also updates it. `ReclaimService.busy()` is read at draw time.

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
| Token request | `AUTH_ERROR_<VALUE>`, `AUTH_HTTP_<status>`, `AUTH_NO_TOKEN`, `AUTH_TOO_LARGE` |
| Userinfo | `USERINFO_HTTP_<status>`, `USERINFO_TOO_LARGE`, `USERINFO_BAD_JSON`, `PROFILE_FIELDS_MISSING`, `PROFILE_EMAIL_MISMATCH`, `PICTURE_HOST` |
| Image | `IMAGE_HTTP_<status>`, `IMAGE_REDIRECT_HOST`, `IMAGE_REDIRECTS`, `IMAGE_TYPE`, `IMAGE_TOO_LARGE`, `IMAGE_DECODE`, `CACHE_WRITE` |
| Other | `TIMEOUT`, `NETWORK` (any other `IOException`) |

`<VALUE>` is Google's `Error=` value with every character outside
`[A-Za-z0-9]` removed, then upper-cased, then cut to 36 characters. So
`Error=BadAuthentication` becomes `AUTH_ERROR_BADAUTHENTICATION`. When a
non-200 token answer carries `Error=`, `AUTH_ERROR_<VALUE>` is used instead of
`AUTH_HTTP_<status>`. The exact mapping from each throw site to its label is
in Detailed Implementation item 9.

To read Google's `Error=` value, the profile code gets its own small
transport interface, `ProfileApi.Transport`, which returns the HTTP status,
content type and body for every answer instead of throwing on non-2xx.
`PhotosApi.Network` throws away the body of any non-2xx answer, so the token
request no longer goes through it. `ProfileApi.Network` implements the new
interface and makes all three requests (token, userinfo, image). It doesn't
follow redirects. `fetch` follows image redirects itself and runs the host and
content-type checks, so tests with a fake transport reach every check.

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
first that works. Each is a test build made only for that experiment, and only
the one that works is committed. Detailed Implementation item 9 lists every
code change each one needs.

*   **E1.** Add `has_permission=1`. The first sign-in exchange sends it
    (`PhotosApi.java:117`), and the profile request doesn't.
*   **E2.** Ask for one scope (`oauth2:https://www.googleapis.com/auth/userinfo.profile`)
    instead of three. `parse` then compares the email only if the response has
    one.
*   **E3.** Request the profile token as the Google Play services identity.
    This is a separate token, so the upload credential isn't affected. It
    changes what PRIVACY.md must say, so it ships only if the author approves
    it after the diagnosis.

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
    Ubuntu with JDK 21 (the version both READMEs require) and runs:
    *   `./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`. This needs no
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

### Part 5: Library scan diagnosis

*   **Labels.** `InventoryService.scan` keeps its current messages and adds a
    fixed label in parentheses, chosen from where the exception was thrown:

    | Source | Label |
    | --- | --- |
    | `PhotosApi.ApiException` | `SCAN_HTTP_<status>` (the sign-in case keeps its own message) |
    | "Service response is too large" (`PhotosApi.java:691`) | `SCAN_RESPONSE_TOO_LARGE` |
    | "Library text is too long" (`LibraryProtocol.text`) | `SCAN_ITEM_TEXT_LONG` |
    | A page that doesn't decode, or has an unexpected shape | `SCAN_FORMAT_PAGE_<STEP>_<REASON>[_F<field>][_W<wire>]_<STAGE>` (below). Exception: in the delta stage, a page with none of root fields 1, 2, 6, 7 or 9, nor the collection record 4 (`SHAPE_NONE_F<lowest field>`), is read as empty; when that run reaches the end, the message is "Scan finished (<label> skipped)". An empty root (`ENV_NONE`) stays an error in every stage, although upstream GPMC treats it as a last page. |
    | An item envelope that doesn't decode | `SCAN_FORMAT_ITEM` |
    | An item key that doesn't decode | `SCAN_FORMAT_KEY` |
    | A deletion entry that doesn't decode | `SCAN_FORMAT_DELETION` |
    | "Library item has no identifier", "…no details" or "Invalid library deletion" (`:124, :126, :155`) | `SCAN_ITEM_INVALID` |
    | "Library pagination did not advance", "…sync cursor is missing" or "…sync did not advance" (`:267-300`) | `SCAN_CURSOR` |
    | "Library cursor cycle" (`LibrarySync.run`) | `SCAN_CURSOR_CYCLE` |
    | `SocketTimeoutException` | `SCAN_TIMEOUT` |
    | Any other `IOException` | `SCAN_NETWORK` |
    | Any other `Exception` | `SCAN_CRASH_<SimpleClassName>` (upper-cased, cut to 32 characters) |

    The labels come from a new `ScanFailure extends IOException` with a fixed
    label, thrown at each of those sites in place of the plain
    `IOException`. The message text of each throw is unchanged. The sites in
    `PhotosApi.java:691` stay plain `IOException`s with an unchanged message,
    because that code is shared with uploads. `InventoryService` maps that one
    message to `SCAN_RESPONSE_TOO_LARGE`.
*   **Where the label appears.** In the existing scan message stored by
    `InventoryDb.message`, which the Storage screen already shows. No new UI.
*   **Diagnosis.** The author installs the 1.1 test build on their own phone
    over 1.0.0, runs the scan, and reports the label. The fix for that cause is
    a separate change, done with `egm-lite` once the label is known. This
    design doesn't guess the fix.
*   **Gate D result (2026-11-20).** The author's scan stopped with
    `SCAN_FORMAT` after the main listing of a large library. The author chose
    the fix below on 2026-11-20 (an `egm-lite` change with two
    fresh-context diff reviews), including the strict name handling that
    the first review asked for. It splits that label in four, as in the
    table above. An item whose key
    reads but whose other fields don't decode no longer stops the scan
    (an item with no details at all is still `SCAN_ITEM_INVALID`).
    It's kept as an "unknown" item (no storage value, no hash), which
    `ReclaimEngine.ineligible` always refuses. A file name that doesn't
    decode also makes the item unknown, because the name is reused for the
    re-upload. Item envelopes, keys, deletions and the page itself stay
    strict, because a lost deletion or key could make reclaim trust a cloud
    copy that is gone.
*   **Second phone run (`1.1.0-test3`, 2026-11-20).** The scan stopped at
    the same place with `SCAN_FORMAT_PAGE`, so the page itself fails. The
    page label now names the cause, from fixed tokens only:
    *   STEP: `ENV` (decoding the whole outer response; its field numbers
        are fields of that response, not of the page root), `ROOT`
        (decoding the page root), `SHAPE` (the root's field types) or `END`
        (the resume, sync and "more" fields).
    *   REASON: `DUP` (a single field repeated), `WIRE` (an unexpected or
        unsupported wire type), `CUT` (a length past the end), `TAG` (an
        invalid tag or varint) or `NONE` (empty or unrecognized).
    *   `F<field>` when the field is known and between 1 and 536870911;
        `W<wire>` for `WIRE` only. STAGE is the scan stage's first letter
        (`N`, `I`, `S`, `D`), added by `LibrarySync`.
    *   The longest reachable label is
        `SCAN_FORMAT_PAGE_ROOT_WIRE_F536870911_W7_I` (42 characters).
    What the parser accepts is unchanged. The fix follows once the phone
    reports the precise label.
*   **Third phone run (`1.1.0-test4`, 2026-11-20).** The label was
    `SCAN_FORMAT_PAGE_SHAPE_NONE_D`: the main listing finished, and the
    first delta (catch-up) page had none of the root fields the app reads.
    Upstream GPMC reads root fields 1, 2, 6 and 9 from these pages and
    ends the delta loop when there is no resume token. From `1.1.0-test5`
    such a page counts as empty in the delta stage only (item 29). Which
    root fields it holds is still open and tracked as a GitHub issue; the
    finished message names the lowest one.
*   **Fourth phone run (`1.1.0-test5`, 2026-11-21).** After Continue the
    scan finished, with the same totals as before and the message
    "Scan finished (`SCAN_FORMAT_PAGE_SHAPE_NONE_F4_D` skipped)". The
    page's lowest root field is 4; its other fields, and whether any carry
    deletions, are still open (the GitHub issue).
*   **Fifth phone run (`1.2.1-test2`, 2026-11-22, shape diagnostic).** The
    skipped page holds one root field, 4: a single collection-like record
    (text fields, settings flags and small numbers), with no resume, sync,
    "more", items or deletions. Field 4 is on every page of every stage,
    and normal delta pages carry fields 1, 4 and 21. No item was unreadable
    in the pages read. From 1.2.1, a wire-2 field 4 counts as a known
    (unused) root field, so that page is a normal last page and the scan
    ends with a plain "Scan finished" (issue #2). The `SHAPE_NONE` rule
    below stays for any other unknown root field.

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

### Conventions

*   **Paths.** Paths are relative to the private repo the dev repo
    unless they start with `public:`, which means
    the public repo.
*   **Cross-references.** References look like "Technical Plan § 1.3"
    (section number, then the bold paragraph number inside it).
*   **Time values.** Times are milliseconds from `System.currentTimeMillis()`.
    "24 hours" means `24 * 60 * 60 * 1000L`.
*   **Outcomes.** The profile work ends in one of two outcomes, decided at
    Gate D (below):
    *   **Outcome A:** one of the experiments E1-E3 (Technical Plan § 3.4)
        loads the profile.
    *   **Outcome B:** none does, and the network profile photo is removed
        (Technical Plan § 3.6).

    Lines that apply to only one outcome start with "**If Outcome A:**" or
    "**If Outcome B:**".

### Phases

Each phase ends with a checkpoint on the build host (see Verification Plan
§ Environments). The checkpoint runs
`./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`
and must pass before the next phase starts.

| Phase | Work | Items |
| --- | --- | --- |
| P1 Diagnosis labels | Profile failure labels and the new transport; daily retry; scan labels | 6 (profile message only), 9, 10 (retry delay), 14 (Outcome A tests), 28, 29, 30 |
| **Gate D** | Install a release-signed test build. Run the profile diagnosis (Technical Plan § 3.3) and experiments E1-E3. The author approves E3 if it is the one that works. Choose Outcome A or B. The author runs the labelled scan on their phone and reports the label (Technical Plan § Part 5). | — |
| P2 Profile outcome | The chosen outcome's edits | 6, 9, 10, 14 (outcome-specific parts) |
| P3 Warning re-post | Boot and update re-post | 7, 8, 15 |
| P4 Update check | Check, card, setting, strings, tests; upload the fixed-name asset to v1.0.0 | 2, 3, 4, 5, 6 (card, toggle), 12, 13, and step 1 of P7 |
| P5 Release tooling and repository basics | Release scripts, CI, policy files | 16, 17, 18, 19, 20, 21 |
| P6 Docs | Privacy, READMEs, site, release notes, design system | 22, 23, 24, 25, 26 |
| P7 Publish | Sync to the public repo and release, in the fixed order of item 27 | 1, 27 |

### Target Files to Create/Modify

#### 1. `app/build.gradle` (Modify)

*   **Type / Scope**: Build configuration.
*   **Proposed Changes**: Set `versionCode 9` and `versionName '1.1.0'` in P7
    (amended 2026-11-21: Gate D test builds used 7 and 8, and the author's
    phone has `versionCode 8`),
    just before the release build. Gate D test builds used `versionCode 7`
    (`1.1.0-test1` to `-test4`) and `8` (`1.1.0-test5`, on the author's
    phone), and are never published.
    No new dependencies.

#### 2. `app/src/main/res/values/strings.xml` (Create)

*   **Type / Scope**: Android string resources (English only).
*   **Proposed Changes**: These strings. Apostrophes are written as `\'`, and
    `%1$s` marks a placeholder.

    | Name | Text |
    | --- | --- |
    | `update_title` | `Version %1$s is available` |
    | `update_body` | `Install it over this app. Don\'t uninstall Photos Unlimited first. If Android doesn\'t allow the install, keep using this version.` |
    | `update_blocked_body` | `Finish Free up before updating. Some photos are only on this phone until Free up uploads them again.` |
    | `update_download` | `Download latest version` |
    | `update_whats_new` | `What\'s new` |
    | `update_not_now` | `Not now` |
    | `update_open_free_up` | `Open Free up` |
    | `update_setting` | `Check for updates` |
    | `update_setting_summary` | `Once a day while the app is open, asks GitHub for the newest version. GitHub sees your IP address.` |
    | `no_browser` | `No browser found` |
    | `profile_failed` | `Couldn\'t load your Google profile photo (%1$s). Backups are not affected.` |

    The code reads every new string with `getString(R.string.…)`. Existing
    strings are not moved; that is part of 1.2.

#### 3. `app/src/main/java/app/photos/unlimited/UpdateCheck.java` (Create)

*   **Type / Scope**: Plain Java logic plus one Android-facing method and an
    HTTP transport.
*   **Proposed Changes**:
    *   **Constants:**
        *   `REPO = "venzellucas/photos-unlimited"`;
        *   `API = "https://api.github.com/repos/" + REPO + "/releases/latest"`;
        *   `USER_AGENT = "PhotosUnlimited-UpdateCheck"`;
        *   `MAX_BYTES = 1 << 20`;
        *   `DAY = 24 * 60 * 60 * 1000L`.
    *   **Types:**
        *   `interface Transport { Response get(String url, Map<String,String> headers) throws IOException; }`;
        *   `record Response(int status, String etag, byte[] body)`;
        *   `record Result(Kind kind, String version, String etag)` with
            `enum Kind { LATEST, UNCHANGED, FAILED }`;
        *   `record Version(int major, int minor, int patch) implements Comparable<Version>`.
    *   **`static Map<String,String> headers(String etag)`** returns exactly
        these headers:
        *   `Accept: application/vnd.github+json`;
        *   `X-GitHub-Api-Version: 2022-11-28`;
        *   `User-Agent: PhotosUnlimited-UpdateCheck`;
        *   `If-None-Match: <etag>`, only when `etag` is not empty.
    *   **`static Result check(Transport t, String etag)`:**
        *   200 → `parseRelease(body)`. A version gives `LATEST`, `null`
            gives `FAILED`.
        *   304 → `UNCHANGED`.
        *   Any other status, or any exception → `FAILED`.
    *   **`static Version parseTag(String tag)`** accepts only
        `^v(\d{1,4})\.(\d{1,4})\.(\d{1,4})$`. Leading zeros are allowed
        (`v01.2.3` is 1.2.3). Anything else, whitespace included, returns
        `null`.
    *   **`Version.parse(String versionName)`** applies the same rule
        without the `v`. `toString()` returns `X.Y.Z`.
    *   **`static Version parseRelease(byte[] json)`** uses `org.json`.
        It returns `null` when:
        *   the body is over `MAX_BYTES`;
        *   the JSON is malformed;
        *   `tag_name` is missing or not a `String` (checked with
            `instanceof`);
        *   `draft` or `prerelease` is `true`. A missing field counts as
            `false`.

        Otherwise it returns `parseTag(tag_name)`.
    *   **`static boolean due(long now, long lastAttempt)`** is `true` when
        `now - lastAttempt >= DAY` or `now < lastAttempt`.
    *   **`static final class Network implements Transport`:**
        *   throws `IOException` unless `url.equals(API)`;
        *   `HttpURLConnection` with `setInstanceFollowRedirects(false)`,
            `setUseCaches(false)`, connect timeout 10 s and read timeout
            15 s;
        *   sets only the given headers;
        *   reads the body only for status 200, and throws if it reaches
            `MAX_BYTES`;
        *   returns the `ETag` header.
    *   **`static void runIfDue(Context c, Settings s, Runnable onDone)`:**
        1.  Return without calling `onDone` if any of these holds:
            `!s.updateCheck()`; the app is debuggable
            (`(c.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0`);
            or `!due(now, s.updateLastAttempt())`.
        2.  Write `updateLastAttempt(now)` with `commit()`.
        3.  Run `check(new Network(), s.updateEtag())`.
        4.  `LATEST` → `storeUpdate(version.toString(), etag)`, which
            writes both under one lock and drops them if the check was
            turned off meanwhile. `UNCHANGED` and `FAILED` → no write.
        5.  Call `onDone`.

        Every exception is caught and ignored. `onDone` still runs.

#### 4. `app/src/main/java/app/photos/unlimited/UpdateNotice.java` (Create)

*   **Type / Scope**: Plain Java decision function. This is the authoritative
    signature. Technical Plan § 1.2 and the diagram match it.
*   **Proposed Changes**:
    *   `enum State { HIDDEN, BLOCKED, AVAILABLE }`.
    *   `static State state(boolean enabled, String installedName, String latest, String dismissed, int unsafeCount, boolean freeUpRunning)`:
        *   HIDDEN when any of these holds:
            *   `!enabled`;
            *   `unsafeCount < 0` (unknown);
            *   `Version.parse(installedName) == null`;
            *   `latest` is empty or doesn't parse;
            *   `latest` is not strictly greater than installed;
            *   `latest.equals(dismissed)`.
        *   Otherwise BLOCKED when `unsafeCount > 0 || freeUpRunning`.
        *   Otherwise AVAILABLE.
    *   `static String downloadUrl()` returns
        `"https://github.com/" + REPO + "/releases/latest/download/Photos-Unlimited.apk"`.
    *   `static String releaseUrl(Version v)` returns
        `"https://github.com/" + REPO + "/releases/tag/v" + v`.

#### 5. `app/src/main/java/app/photos/unlimited/Settings.java` (Modify)

*   **Type / Scope**: Wrapper around the `settings` SharedPreferences file.
*   **Proposed Changes**: New keys use camelCase like the existing ones.
    *   `boolean updateCheck()`, key `updateCheck`, default `true`.
    *   `void updateCheck(boolean)`. `false` also calls `clearUpdate()`.
    *   `long updateLastAttempt()` and `void updateLastAttempt(long)`, key
        `updateLastAttempt`, written with `commit()`.
    *   `String updateLatest()`, key `updateLatest`, and `String updateEtag()`,
        key `updateEtag`. Both are written only by
        `void storeUpdate(String latest, String etag)`, which holds the same
        static lock as `updateCheck(boolean)` and does nothing while the
        check is off, so a check finishing after the user turned it off
        can't bring its result back.
    *   `String updateDismissed()` and `void updateDismissed(String)`, key
        `updateDismissed`.
    *   `void clearUpdate()` removes `updateLastAttempt`, `updateLatest`,
        `updateEtag` and `updateDismissed`.
    *   **If Outcome B:** add `boolean profileRemoved()` and
        `void profileRemoved(boolean)`, key `profileRemoved`, written with
        `commit()`. It lives in `settings`, so `ProfileStore.clear` (which
        clears the `profile` file) can't wipe it.

#### 6. `app/src/main/java/app/photos/unlimited/MainActivity.java` (Modify)

*   **Type / Scope**: UI.
*   **Proposed Changes**:
    *   **Fields:**
        *   `private LinearLayout updateCard`, set to `null` in the per-screen
            reset in `draw()` (`MainActivity.java:324`);
        *   `private volatile int unsafeCount = -1`;
        *   `private static final int SCREEN_BACKUP = 1`;
        *   `private final ExecutorService updateIo = Executors.newSingleThreadExecutor()`,
            with `updateIo.shutdownNow()` added to `onDestroy`
            (`MainActivity.java:276-282`). Only the update check runs on it,
            so a slow GitHub answer can't delay the backup counts on `io`.
    *   **`onResume`** (P3, P4). Submit one task to `io` that:
        1.  sets `unsafeCount = ReclaimService.warnIfOnlyOnPhone(this, "")`;
        2.  posts `drawUpdateCard()` to the UI thread.

        Then call
        `updateIo.execute(() -> UpdateCheck.runIfDue(this, settings, () -> runOnUiThread(this::drawUpdateCard)))`.
        `onDone` is the only redraw that follows a check.
    *   **`drawBackup`** (P4). After `page.addView(counts)`:
        *   `updateCard = ui.card(page, false)`;
        *   hide its parent card with
            `((View) updateCard.getParent()).setVisibility(View.GONE)`;
        *   set `unsafeCount = -1`, so a count from an earlier visit can't
            fill the card, and submit an `io` task that reads `unsafeCount` from
            `ReclaimDb.unsafeAll()` (the same read `unfinishedReclaim()` does
            at `MainActivity.java:1517-1520`), then posts `drawUpdateCard()`.
    *   **`drawUpdateCard()`** (P4):
        *   Return if `updateCard == null` or `screen != SCREEN_BACKUP`.
        *   Call `UpdateNotice.state(settings.updateCheck(), installedVersionName(), settings.updateLatest(), settings.updateDismissed(), unsafeCount, ReclaimService.busy())`.
            `installedVersionName()` holds the `PackageInfo` read currently
            at `MainActivity.java:1751`, moved into a helper.
        *   Clear the card.
        *   HIDDEN → hide the parent card.
        *   AVAILABLE → show it with:
            *   `ui.text(getString(R.string.update_title, latest), 18, true)`;
            *   `ui.supporting(getString(R.string.update_body))`;
            *   `ui.button(getString(R.string.update_download), R.drawable.ic_open, true)`,
                which calls `openUrl(UpdateNotice.downloadUrl())`;
            *   `ui.button(getString(R.string.update_whats_new), 0, false)`,
                which calls `openUrl(UpdateNotice.releaseUrl(...))`;
            *   `ui.button(getString(R.string.update_not_now), 0, false)`,
                which calls `settings.updateDismissed(latest)` and then
                `drawUpdateCard()`.
        *   BLOCKED → the same title, `update_blocked_body`, an
            `update_open_free_up` filled button that calls `selectScreen(3)`,
            and the "What's new" button.
        *   `Ui.java` isn't changed.
    *   **`openUrl(String url)`** (P4). Starts an `Intent.ACTION_VIEW`
        activity for the URL. On `ActivityNotFoundException` it calls the
        existing `message(getString(R.string.no_browser))`, following the
        pattern at `MainActivity.java:254-257`.
    *   **`drawSettings`** (P4). In the "Account and app" card, before
        "About and open-source credits", add
        `ui.toggle(app, getString(R.string.update_setting), settings.updateCheck(), (b, on) -> settings.updateCheck(on))`,
        followed by `ui.supporting(getString(R.string.update_setting_summary))`.
        When it is turned on again, `lastAttempt` has already been cleared,
        so the next `onResume` checks.
    *   **`refreshProfile`** (P1). Replace `catch (Exception ignored)`
        (`MainActivity.java:1588`) with:
        *   `catch (ProfileApi.Failure f)`, which keeps `f.label()`;
        *   `catch (java.net.SocketTimeoutException e)`, label `TIMEOUT`;
        *   `catch (java.io.InterruptedIOException e)`, no label and no
            message;
        *   `catch (Exception e)`, label `NETWORK`.

        The message `getString(R.string.profile_failed, label)` is shown
        only when `force` is true (manual refresh), the same as the current
        `force` check at `MainActivity.java:1602-1606`. Automatic refreshes
        stay silent.
    *   **If Outcome B** (P2):
        *   Remove `refreshProfile`, the `profileIo`, `profileStop` and
            `profileLoading` fields, their lines in `onDestroy`, and the
            `refreshProfile(false)` call in `onResume`
            (`MainActivity.java:184`).
        *   `manageAccount` no longer calls `ProfileStore.name` and shows
            three items: the email,
            `"Reconnect"` and `"Disconnect"`. The handler becomes
            `if (which == 1) connect(); else if (which == 2) disconnect();`.
        *   `showProfile` always passes a `null` bitmap.
        *   In `onCreate`, if `!settings.profileRemoved()`, call
            `ProfileStore.clear(this)`, then `settings.profileRemoved(true)`.

#### 7. `app/src/main/java/app/photos/unlimited/BootReceiver.java` (Modify, P3)

*   **Type / Scope**: Broadcast receiver.
*   **Proposed Changes**:
    *   Add `static void handle(Context c, String action)`. It returns at
        once unless `action` is `Intent.ACTION_BOOT_COMPLETED` or
        `Intent.ACTION_MY_PACKAGE_REPLACED`. Then it calls
        `Scheduler.ensure(c)` and
        `ReclaimService.warnIfOnlyOnPhone(c, "")`, each in its own
        `try`/`catch (RuntimeException)` that logs with `Log.w`, so a
        scheduling failure can't stop the warning.
    *   `onReceive` reads the action, then does
        `PendingResult r = goAsync(); new Thread(() -> { try { handle(c, action); … } finally { r.finish(); } }).start();`.
    *   **After an update, post twice.** On the test emulator (live check
        3, 2026-11-21) Android's own cleanup of the updated app's
        notifications (`notification_cancel_all`, reason 5) landed about
        1 s *after* the receiver had posted 7403, and removed it. For
        `MY_PACKAGE_REPLACED`, the thread therefore posts again after
        `UPDATE_REPOST_DELAY` (15 s). It is a background broadcast, so the
        receiver may run that long.
    *   Tests call `handle` directly, because `goAsync()` returns `null`
        outside a real broadcast.

#### 8. `app/src/main/java/app/photos/unlimited/ReclaimService.java` (Modify, P3)

*   **Type / Scope**: Foreground service plus static helper.
*   **Proposed Changes**:
    *   Move the channel creation currently in `onCreate`
        (`ReclaimService.java:50-51`) into `static void ensureChannel(Context)`.
        Call it from `onCreate` and at the start of `warnIfOnlyOnPhone`.
    *   `warnIfOnlyOnPhone` is `synchronized`: with the `onResume` caller,
        a read-then-post must not land after another caller's newer
        cancel.
    *   `warnIfOnlyOnPhone` returns `int`, the `unsafeAll()` count. Its
        `account` argument is unused and stays only so existing callers
        compile; new callers pass `""`.

#### 9. `app/src/main/java/app/photos/unlimited/ProfileApi.java` (Modify, P1 and P2)

*   **Type / Scope**: Profile network client.
*   **Proposed Changes (P1)**:
    *   **`static final class Failure extends IOException`** has
        `String label()`. The constructor throws `IllegalArgumentException`
        unless the label matches `[A-Z0-9_]{1,48}`.
    *   **`static String errorLabel(String googleError, int status)`** removes
        every character outside `[A-Za-z0-9]`, upper-cases with `Locale.ROOT`,
        and cuts to 36 characters. If the result is empty, it returns
        `"AUTH_HTTP_" + status`. Otherwise it returns `"AUTH_ERROR_" + value`.
        `BadAuthentication` becomes `AUTH_ERROR_BADAUTHENTICATION`.
    *   **The new seam:**
        *   `interface Transport { Reply call(String url, String method, Map<String,String> headers, byte[] body, AtomicBoolean stop) throws IOException; }`;
        *   `record Reply(int status, String contentType, String location, byte[] body)`.
    *   **`static Fetched fetch(String credential, Transport t, AtomicBoolean stop)`**
        replaces the `PhotosApi.Transport` version and returns
        `record Fetched(String name, byte[] image)`. `image` is `null` when the
        profile has no picture. `fetch` now makes all three requests: token,
        userinfo and image. The image download moves here from
        `ProfileStore.refresh` (`ProfileStore.java:64-70`).
    *   **`ProfileApi.Network implements ProfileApi.Transport`.** One
        `HttpURLConnection` per call:
        *   allowed addresses: exactly `PhotosApi.AUTH` (POST), exactly
            `USERINFO` (GET), or an address accepted by `imageAddress` (GET
            with no caller headers);
        *   `setInstanceFollowRedirects(false)`;
        *   `Accept-Encoding: identity`;
        *   the AUTH POST uses a fixed-length body, connect 30 s and read
            60 s, the same as `PhotosApi.Network`; the GETs use connect 10 s
            and read 15 s;
        *   it reads `getInputStream()` for status < 400, otherwise
            `getErrorStream()`, capped at 16 KiB for AUTH, 64 KiB for
            userinfo and 2 MiB for images. When a body is over its cap,
            `Network` returns `status = -1` with an empty body, and `fetch`
            maps `-1` to that step's `AUTH_TOO_LARGE`, `USERINFO_TOO_LARGE` or
            `IMAGE_TOO_LARGE` label;
        *   it returns status, content type, `Location` and body, and never
            throws for a status.
    *   **The AUTH request** is the one `fetch` sends in 1.0.0: same form
        fields and the same headers (`Content-Type`, `User-Agent`, `device`,
        `app`).
    *   **Mapping each step to its label.** The first matching row wins.

        | Step | Condition | Label |
        | --- | --- | --- |
        | Credential | `parseForm` fails, or `Email`, `Token` or `androidId` is blank | `NO_CREDENTIAL` |
        | Token | status `-1` (body over 16 KiB) | `AUTH_TOO_LARGE` |
        | Token | any status, and the body has an `Error=` line | `errorLabel(value, status)` |
        | Token | status ≠ 200 otherwise | `AUTH_HTTP_<status>` |
        | Token | status 200 and no non-empty `Auth=` line | `AUTH_NO_TOKEN` |
        | Userinfo | status `-1` (body over 64 KiB) | `USERINFO_TOO_LARGE` |
        | Userinfo | status ≠ 200 | `USERINFO_HTTP_<status>` |
        | Userinfo | `JSONException` | `USERINFO_BAD_JSON` |
        | Userinfo | `sub` blank, or `email_verified` missing or not `true` | `PROFILE_FIELDS_MISSING` (an unverified email counts as missing) |
        | Userinfo | `email` differs from the account (ignoring case) | `PROFILE_EMAIL_MISMATCH` |
        | Userinfo | `picture` not empty and not `imageAddress` | `PICTURE_HOST` |
        | Image | status 301/302/303/307/308 and `Location` missing, or not accepted by `imageAddress` | `IMAGE_REDIRECT_HOST` |
        | Image | more than 4 redirects | `IMAGE_REDIRECTS` |
        | Image | other status ≠ 200 | `IMAGE_HTTP_<status>` |
        | Image | content type not `image/*` | `IMAGE_TYPE` |
        | Image | status `-1` (body over 2 MiB) | `IMAGE_TOO_LARGE` |
        | Decode | any `ProfileStore.decode` failure | `IMAGE_DECODE` |
        | Cache | any `ProfileStore.save` failure, `AtomicFile.startWrite()` included | `CACHE_WRITE` |

        The old combined check at `ProfileApi.java:61-64` is split into the
        two `PROFILE_*` rows. The redirect loop moves from `Network.call`
        (`ProfileApi.java:109-152`) into `fetch`, so a fake transport reaches
        every check. The `-1` rows come before the status rows.
*   **Experiments (Gate D).** Each is a test build made only for that
    experiment. Only the one that works is committed in P2.
    *   **E1:** add form field `has_permission=1`.
    *   **E2:** form `service=oauth2:https://www.googleapis.com/auth/userinfo.profile`.
        `parse` then compares `email` only when the response has one, and no
        longer requires `email_verified`. `sub` is still required.
    *   **E3:** form `app=com.google.android.gms`,
        `callerPkg=com.google.android.gms`, `client_sig=PhotosApi.GMS_SIG`
        (`PhotosApi.java:17`), `callerSig=PhotosApi.GMS_SIG` when the stored
        form has a `callerSig` field, and the request header
        `app: com.google.android.gms`. The `User-Agent` is unchanged. It ships
        only with the author's approval.
*   **If Outcome B (P2):** delete this file.

#### 10. `app/src/main/java/app/photos/unlimited/ProfileStore.java` (Modify, P1 and P2)

*   **Type / Scope**: Profile cache.
*   **Proposed Changes (P1)**:
    *   In `refresh`, the `retryAfter` written before the request changes from
        `now + 60 * 60 * 1000L` to `now + 24 * 60 * 60 * 1000L`
        (`ProfileStore.java:61`).
    *   `refresh` calls `ProfileApi.fetch(...)`, then
        `decode(fetched.image())` when the image isn't `null`, then `save`. It
        no longer downloads the image itself (`ProfileStore.java:64-70` is
        removed).
    *   `decode` throws `ProfileApi.Failure("IMAGE_DECODE")` in place of its
        two `IOException`s (`:86, :92`). `save` wraps its whole write,
        `startWrite()` included (`:108-114`), and throws
        `ProfileApi.Failure("CACHE_WRITE")`.
*   **If Outcome B (P2):**
    *   keep `icon` and `clear`;
    *   remove `refresh`, `decode`, `save`, `cached`, `file()`, `name` and
        `prefs()`;
    *   `clear` deletes the file at
        `new File(context.getNoBackupFilesDir(), "google-profile.png")` and
        clears the `profile` preferences.

    The `manageAccount` change is in item 6.

#### 11. `app/src/main/AndroidManifest.xml` (No change, verified)

*   **Type / Scope**: Manifest.
*   **Proposed Changes**: None.
    *   `BootReceiver` already listens for both actions
        (`AndroidManifest.xml:29-31`).
    *   No new permission is needed.
    *   `ACTION_VIEW` needs no `<queries>` entry, because the code starts the
        activity and catches `ActivityNotFoundException` instead of calling
        `resolveActivity`.

#### 12. `app/src/test/java/app/photos/unlimited/UpdateLogicTest.java` (Create, P4)

*   **Type / Scope**: JVM unit test. These tests run with
    `unitTests.returnDefaultValues = true` (`app/build.gradle:31`), so Android
    classes, `org.json` included, are empty stand-ins that return default
    values. JSON parsing is therefore tested on the emulator in item 13.
*   **Proposed Changes**: Each of these cases, with its expected result:
    *   **`parseTag`:**
        *   `v1.2.3` → 1.2.3;
        *   `v01.2.3` → 1.2.3;
        *   `v1.2`, `1.2.3`, `v1.2.3-rc1`, ` v1.2.3`, `v10000.0.0` and `null`
            → `null`.
    *   **`Version.parse`:** `1.1.0` → 1.1.0; `1.1.0-rc1` → `null`.
    *   **Ordering:** 1.0.10 > 1.0.9; 2.0.0 > 1.99.99; 1.1.0 == 1.1.0.
    *   **`due(now, last)`:** `now - last` = 23 h 59 m → false; 24 h → true;
        `now < last` → true; `last = 0` → true.
    *   **`UpdateNotice.state`:**
        *   disabled → HIDDEN;
        *   `unsafeCount = -1` → HIDDEN;
        *   latest empty → HIDDEN;
        *   latest equal → HIDDEN;
        *   latest older → HIDDEN;
        *   dismissed equals latest → HIDDEN;
        *   dismissed older than latest → AVAILABLE;
        *   installed `1.1.0-rc1` → HIDDEN;
        *   `unsafeCount = 3` → BLOCKED;
        *   `freeUpRunning` → BLOCKED;
        *   safe → AVAILABLE.
    *   **URL builders:** exact strings.
    *   **`UpdateCheck.check` with a fake transport** (no JSON involved):
        *   304 → `UNCHANGED`;
        *   403, 429, 500 and a thrown `IOException` → `FAILED`;
        *   `headers("")` has exactly 3 entries;
        *   `headers("W/\"abc\"")` has exactly 4, including `If-None-Match`.

#### 13. `app/src/androidTest/java/app/photos/unlimited/UpdateCheckInstrumentedTest.java` (Create, P4)

*   **Type / Scope**: Instrumentation test (needs the real `org.json`), run on
    the test emulator.
*   **Proposed Changes**:
    *   **Fixture.** `app/src/androidTest/assets/releases_latest.json`, a
        trimmed copy of a real `releases/latest` answer for v1.0.0 (tag,
        draft, prerelease, one asset).
    *   **Expected results:**
        *   the fixture → 1.0.0;
        *   with `draft: true` → `null`;
        *   with `prerelease: true` → `null`;
        *   with `draft` and `prerelease` removed → 1.0.0;
        *   without `tag_name` → `null`;
        *   with `tag_name: 110` → `null`;
        *   `{` → `null`;
        *   a body of `MAX_BYTES + 1` bytes → `null`.
    *   **`check`** with a fake 200 answer carrying the fixture → `LATEST`
        with version `1.0.0` and the fake ETag.

#### 14. `app/src/androidTest/java/app/photos/unlimited/ProfileInstrumentedTest.java` (Modify)

*   **Type / Scope**: Instrumentation test.
*   **Proposed Changes (P1, kept in Outcome A)**:
    *   The fake transport becomes a `ProfileApi.Transport` lambda.
    *   Cases and expected labels:
        *   token 403 with body `Error=BadAuthentication` →
            `AUTH_ERROR_BADAUTHENTICATION`;
        *   token 403 with an empty body → `AUTH_HTTP_403`;
        *   token 200 without `Auth` → `AUTH_NO_TOKEN`;
        *   userinfo 401 → `USERINFO_HTTP_401`;
        *   `email_verified` missing → `PROFILE_FIELDS_MISSING`;
        *   another email → `PROFILE_EMAIL_MISMATCH`;
        *   picture on `example.com` → `PICTURE_HOST`;
        *   image 302 to `https://example.com/a.png` → `IMAGE_REDIRECT_HOST`;
        *   image `text/html` → `IMAGE_TYPE`;
        *   image bytes `abc` → `IMAGE_DECODE`.
    *   Every label matches `[A-Z0-9_]+` and contains no `@`, `/` or `=`.
    *   The existing success-path tests are kept and moved to the new
        transport and the `Fetched` return type. The success test that
        asserts `calls.size()` (`ProfileInstrumentedTest.java:68`) now
        expects 3 calls (token, userinfo, image) instead of 2.
*   **If Outcome B (P2):**
    *   Delete the network tests, and the tests in
        `ProfileInstrumentedTest.java:100-135` that use `cached`, `save` or
        `decode` (`decodingPreservesColorAndRejectsInvalidImages` included).
    *   Keep `cachedPhotoAppearsWithoutTintInBothThemes`
        (`ProfileInstrumentedTest.java:136-189`) with a new body:
        1.  for the light and the dark theme, draw
            `ProfileStore.icon(context, null, "a@example.com", background, foreground)`
            into a 128 × 128 bitmap;
        2.  write it to `profile-light.png` or `profile-dark.png` in the same
            folder as today, because `tools/validate.py:46` pulls those files;
        3.  assert that the centre pixel is not the background colour (the
            letter was drawn).

#### 15. `app/src/androidTest/java/app/photos/unlimited/BackupInstrumentedTest.java` (Modify, P3)

*   **Type / Scope**: Instrumentation test.
*   **Proposed Changes**: A new test `onlyOnPhoneWarningIsPostedAtBoot`:
    1.  Grant `POST_NOTIFICATIONS` the way
        `BackupInstrumentedTest.java:104-111` does.
    2.  Seed one job in `ReclaimEngine.State.DELETED` through
        `ReclaimDb.enqueue` and `ReclaimDb.save`, built with
        `ReclaimEngine.Job.of(...)` as `ReclaimEngineTest.java:216` does, with
        `sha1 = new byte[20]` (the column is `NOT NULL`) and media key
        `test-boot-warning`.
    3.  Cancel 7403 and delete the `reclaim` channel (as on a phone where
        Free up never ran), and assert `unsafeAll()` is 1. Call
        `BootReceiver.handle(context, Intent.ACTION_BOOT_COMPLETED)`.
        Assert that notification ID 7403 is in
        `NotificationManager.getActiveNotifications()`.
    4.  Delete the job with a new test-only method
        `ReclaimDb.deleteForTest(String mediaKey)` (annotated
        `@VisibleForTesting`, runs `DELETE FROM jobs WHERE media_key = ?`).
        Call `handle` again. Assert that notification ID 7403 is no longer
        active.
    5.  In `@After`, call `deleteForTest("test-boot-warning")` and cancel
        7403. Both checks poll up to 5 s, because posting and cancelling
        reach `getActiveNotifications()` asynchronously.

    `ReclaimDb.java` is modified only to add `deleteForTest`.

    Note: 7403 is also `Scheduler.CONTINUE`, a job ID, which is unrelated.

#### 16. `tools/build-release.sh` and `public:tools/build-release.sh` (Modify, P5)

*   **Type / Scope**: Release scripts. These are two different scripts. Each
    is edited on its own and they are never copied over each other.
*   **Proposed Changes (both)**:
    1.  Require `RELEASE_TAG` as an environment variable.
    2.  Read the version with
        `version=$(sed -n "s/.*versionName '\(.*\)'.*/\1/p" app/build.gradle)`.
    3.  Exit with an error unless `"$RELEASE_TAG" == "v$version"`.
    4.  After the build:
        *   `mkdir -p "dist/$version"`;
        *   copy `app/build/outputs/apk/release/app-release.apk` to
            `dist/$version/Photos-Unlimited.apk`;
        *   in that folder, run
            `sha256sum Photos-Unlimited.apk > SHA256SUMS.txt`;
        *   print `apksigner verify --print-certs dist/$version/Photos-Unlimited.apk`.
*   **Private script:** apksigner is at
    `/opt/android-sdk/build-tools/37.0.0/apksigner`.
*   **Public script:** apksigner is at
    `"$ANDROID_HOME/build-tools/37.0.0/apksigner"`.
*   **As built (P5):** the public script also requires `ANDROID_HOME`, and the
    public README (item 23a) gives `RELEASE_TAG=vX.Y.Z tools/build-release.sh`
    and the `dist/X.Y.Z/` output. The tag check runs before the signing key is read
    or created. Checked on the build host: a missing `RELEASE_TAG` and a
    mismatched one both exit with an error before any build.

#### 17. `tools/verify-release.py` (Modify, P5)

*   **Type / Scope**: Private release check, run on the build host.
*   **Proposed Changes**:
    *   `APK = ROOT/'dist/1.1.0/Photos-Unlimited.apk'`.
    *   `OLD = Path('<previous releases>/Photos-Unlimited-1.0.0.apk')`.
        Copy it there from the v1.0.0 release before the run.
    *   Keep the certificate-equality check (`certificate()`,
        `verify-release.py:51-53`).
    *   No threshold change here. The instrumentation threshold lives in
        `tools/validate.py` (item 17b).

#### 17b. `tools/validate.py` (Modify, P5)

*   **Type / Scope**: Private validation script, run on the build host
    against the test emulator. It writes `validation.json`, which
    `verify-release.py` reads.
*   **Proposed Changes**: Change `>= 19` (`validate.py:44`) to the total
    instrumentation count from the P4 checkpoint run (the instrumentation
    command run after P4). The final checks run `tools/validate.py` first,
    then `tools/verify-release.py`.
    *   **As built (P5, 2026-11-21):** the threshold is 28, the P4 count.
        `validate.py` also passes `-e notClass app.photos.unlimited.LiveReclaimProbe`,
        like every other instrumentation run, so the count matches and the
        probe never runs on the test emulator.

#### 18. `public:tools/check-publication.py` (Modify, P5)

*   **Type / Scope**: Pre-publication scan.
*   **Proposed Changes**:
    *   **The existing secret patterns** keep scanning every file exactly as
        in 1.0.0. The two new checks below apply only to text files.
    *   **Text files.** These extensions count as text: `.java .gradle .xml
        .md .py .sh .yml .yaml .json .properties .html .css .js .svg .txt .pro
        .kts`, plus files with no extension. Each text file is read with
        `read_text(encoding="utf-8")`. A `UnicodeDecodeError` fails the scan
        and names the file.
    *   **Email pattern.**
        `(?<![\w/@.:-])[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+`,
        matched with `re.IGNORECASE`. The look-behind skips URLs such as
        `https://user@lh3.googleusercontent.com/a`.
    *   **As built (P5):** `mailto` link addresses are also matched (the
        look-behind skips anything after `:`), every file that decodes as
        text gets the email check, and suffixes are compared lower-cased.
    *   **Allowed addresses** (compared lower-cased): those ending in
        `@users.noreply.github.com` or `@example.com`, or starting with
        `noreply@`. That covers `Me@Example.com` in `ReclaimEngineTest.java`.
    *   **Check.** Run it on the current public tree before committing and
        confirm it passes.

#### 19. `public:SECURITY.md` (Create, P5)

*   **Type / Scope**: Policy doc.
*   **Proposed Changes**:
    *   **Supported version:** the latest release only.
    *   **Reporting:** use GitHub private vulnerability reporting at
        `https://github.com/venzellucas/photos-unlimited/security/advisories/new`.
        No personal email.
    *   **What never to include:** tokens, cookies, email addresses or
        private photos.
    *   **Setup step (P5):** turn the feature on with
        `gh api -X PUT repos/venzellucas/photos-unlimited/private-vulnerability-reporting`,
        then check it with
        `gh api repos/venzellucas/photos-unlimited/private-vulnerability-reporting`.

#### 20. `public:.github/ISSUE_TEMPLATE/bug_report.yml` and `public:.github/ISSUE_TEMPLATE/config.yml` (Create, P5)

*   **Type / Scope**: GitHub issue forms.
*   **Proposed Changes**:
    *   **`bug_report.yml` fields:**
        *   `input` id `app_version`, required;
        *   `input` id `android`, "Android version and phone model", required;
        *   `textarea` id `what_happened`, required;
        *   `textarea` id `expected`;
        *   `input` id `diagnostic`, "Profile or scan label, or Account
            diagnostic result";
        *   `checkboxes` id `privacy`, with one required option: "I removed
            tokens, cookies, email addresses and private photos".
    *   **`config.yml`:** `blank_issues_enabled: false`, and one
        `contact_links` entry named "Report a security problem privately"
        with the URL from item 19.

#### 21. `public:.github/workflows/ci.yml` (Create, P5)

*   **Type / Scope**: GitHub Actions.
*   **Proposed Changes**:
    *   **Triggers:** `push` and `pull_request` on `main`, with
        `permissions: contents: read`. Runs on `ubuntu-latest`. No secrets.
    *   **Steps:**
        1.  `actions/checkout`.
        2.  `actions/setup-java` with `distribution: temurin` and
            `java-version: 21`.
        3.  Install SDK packages with the runner's preinstalled SDK:
            `"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platforms;android-37" "build-tools;37.0.0"`.
            No third-party setup action. Licenses are accepted with
            `yes |`, and the long output goes to `/dev/null`.
        4.  `./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
        5.  `python3 tools/check-publication.py`.
    *   **Pinning.** Each action is pinned to the full commit SHA of a
        specific release tag (the newest `v4.x.y` when the file is written).
        Resolve it with `gh api repos/actions/checkout/commits/v4.x.y --jq .sha`
        (and the same for `actions/setup-java`), which dereferences annotated
        tags to the commit. Add a `# v4.x.y` comment.
    *   **Check.** The workflow passes on GitHub after the first push.

#### 22. `public:PRIVACY.md` (Modify, P6)

*   **Type / Scope**: Privacy policy.
*   **Proposed Changes**:
    *   **After the sentence** "It has no project-owned account or
        photo-upload server and includes no advertising or analytics SDK."
        (lines 3-4), add: "Besides Google, it contacts GitHub
        (api.github.com) once a day while the app is open to check for a newer
        version, unless you turn this off in Settings."
    *   **After the paragraph** "Google receives requests, account
        identifiers, uploaded content, and ordinary connection information
        under its own policies." (line 32), add the new GitHub section.
    *   **New "GitHub" section:** the request, what GitHub sees (IP address,
        the fixed user agent `PhotosUnlimited-UpdateCheck`, the time), that no
        account, photo or device identifiers are sent, and how to turn it off.
    *   **"Profile photo" bullet (lines 26-30):**
        *   **If Outcome A** with E1 or E2: unchanged.
        *   **If Outcome A** with E3: say that the profile token is requested
            as Google Play services, separately from the upload sign-in.
        *   **If Outcome B:** remove the bullet; remove the sentence "Profile
            images are downloaded without forwarding the account's
            authorization token to the image host." (lines 33-34); and in
            "Disconnect removes the saved credential, profile cache, and
            account's storage inventory" (line 44) remove "profile cache, ".

#### 23. `public:README.md` (23a) and `README.md` (23b) (Modify, P6)

*   **Type / Scope**: Docs.
*   **23a, public README:**
    *   **Line 7:** `**v1.0.0 · Android 13+ · MIT license**` becomes
        `**Android 13+ · MIT license**`.
    *   **Lines 10-11:**
        *   `[Download APK](…/releases/download/v1.0.0/Photos-Unlimited-1.0.0.apk)`
            becomes
            `[Download APK](https://github.com/venzellucas/photos-unlimited/releases/latest/download/Photos-Unlimited.apk)`;
        *   `[Release notes](…/releases/tag/v1.0.0)` becomes
            `[Release notes](https://github.com/venzellucas/photos-unlimited/releases/latest)`.
    *   **Line 39:**
        *   Replace "Updates are manual." with: "The app checks GitHub once a
            day while it is open and shows a card when a new version is out.
            Turn this off in Settings → Check for updates."
        *   **If Outcome B:** also delete the "Refresh profile photo"
            sentences on lines 39-41.
    *   **Lines 154-157:** replace the test-count sentences ("Version 1.0.0
        passed 57 JVM tests, 20 Android instrumentation tests, …" and "The
        0.2.1 APK passed 23 JVM tests, 19 Android instrumentation tests, …")
        with "Each release passes the JVM and Android instrumentation suites,
        lint with no errors, and an in-place signed upgrade from the previous
        release."
    *   **If Outcome B:** also change line 19-20 "and a cached Google profile
        photo when available" to "and an account initial"; line 56 "Live
        accounting and profile authorization are not fully" to "Live
        accounting is not fully"; and remove ", and profile-cache isolation"
        from line 159.
    *   **New "Verify the APK" lines** after the download links: the SHA-256
        certificate fingerprint, the sentence "Android refuses any update not
        signed with this key", and a link to SECURITY.md.
*   **23b, private README:**
    *   **Line 38:** "failures back off for an hour" becomes "failures back
        off for a day".
    *   **Lines 225-226:** "only contacts fixed Google endpoints" becomes
        "contacts fixed Google endpoints and, once a day while open,
        api.github.com".
    *   **If Outcome B:** rewrite lines 34-42 to say the account button shows
        the account initial.

#### 24. `public:docs/index.html` (Modify, P6)

*   **Type / Scope**: GitHub Pages site.
*   **Proposed Changes**, line by line:

    | Line | Old | New |
    | --- | --- | --- |
    | 41 | badge `v1.0.0 · Release ↗` linking to `releases/tag/v1.0.0` | `Latest release ↗` linking to `releases/latest` |
    | 44 | `releases/download/v1.0.0/Photos-Unlimited-1.0.0.apk` | `releases/latest/download/Photos-Unlimited.apk` |
    | 45 | `Android 13+ · Free & open source · 3.5 MB` | `Android 13+ · Free & open source` |
    | 92 | APK link as line 44; release-notes link to `tag/v1.0.0`; `download-meta` `v1.0.0 · Android 13+ · 3.5 MB` | the same new APK link; `releases/latest`; `download-meta` `Android 13+` |
    | 93 | `Updates are manual. Android may ask…`; checksums at `download/v1.0.0/SHA256SUMS.txt` | `The app tells you when a new version is out. Android may ask…`; `releases/latest/download/SHA256SUMS.txt` |
    | 97 | "Yes, with Free up storage in version 1.0.0." | "Yes, with Free up storage." |
    | 98 | "Profile photos and the optional storage inventory are cached locally." | "Profile photos and the optional storage inventory are cached locally. Once a day while the app is open, it asks GitHub whether a newer version exists; you can turn this off." (**If Outcome B:** "The optional storage inventory is cached locally." plus the GitHub sentence.) |
    | 99 | "Version 1.0.0 passed 77 automated tests, a security review and a signed upgrade check." | "Each release passes the automated tests and a signed upgrade check. Version 1.0.0 also had an independent security review." (amended in P6: 1.1 has no security review) (**If Outcome B:** also "Live storage accounting and profile authorization are not fully validated." becomes "Live storage accounting is not fully validated.") |

#### 25. `public:RELEASE_NOTES.md` (Modify, P6)

*   **Type / Scope**: Release notes.
*   **Proposed Changes**: A new 1.1.0 entry at the top, covering:
    *   the update check (on by default, what GitHub sees, how to turn it
        off);
    *   the "Don't uninstall yet" re-post after reboot and update;
    *   the profile outcome, worded as in item 22;
    *   the scan failure labels;
    *   the fixed asset name `Photos-Unlimited.apk`;
    *   the certificate fingerprint.

#### 26. `docs/design-system.md` (Modify, P6)

*   **Type / Scope**: Design doc.
*   **Proposed Changes**:
    *   Status changes to 1.1.0.
    *   **Line 67** (Settings row of the screen table): add "Check for
        updates".
    *   Add an "Update card" entry under components: placed below the
        Backup hero and counts, normal card style, no warning colors.
    *   **Line 174:** replace "Reclamation actions are not part of this
        release" with a one-line description of Free up.

#### 27. Public sync and publication (P7)

*   **Type / Scope**: Procedure.
*   **Proposed Changes**: In this order:
    1.  **Fixed-name asset for v1.0.0** (done in P4, before any doc links
        change).
        1.  In the scratchpad, run
            `gh release download v1.0.0 --repo venzellucas/photos-unlimited --pattern 'Photos-Unlimited-1.0.0.apk'`.
        2.  Copy it to `Photos-Unlimited.apk`.
        3.  Run
            `gh release upload v1.0.0 Photos-Unlimited.apk --repo venzellucas/photos-unlimited`.

        Existing assets are untouched. The v1.0.0 `SHA256SUMS.txt` already
        lists the same bytes under the versioned name, so it isn't changed.
    2.  **Release build.** Set item 1's versions, then run
        `RELEASE_TAG=v1.1.0 tools/build-release.sh` on the build host. Copy
        `dist/1.1.0/` back to `dist/1.1.0/`.
    3.  **Sync app code.** From `/root`, run
        `rsync -rcn --delete --exclude=.git --exclude=build/ --exclude=.gradle/ --exclude=__pycache__/ --exclude=dist/ --exclude=local.properties --out-format=%n photos-unlimited/app/ photos-unlimited-public/app/`.
        Check that the listed files are exactly the app files changed in
        P1-P4 plus `app/build.gradle`. Then run it again without `-n`. Top-level files and `tools/`
        are never synced; each public file is edited as listed.
    4.  **Publication scan.**
        `python3 tools/check-publication.py`
        must pass.
    5.  **Commit.** Commit the private repo locally (it has no remote).
        Commit the public repo and push it with git over HTTPS (credentials
        through gh), as venzellucas.
    6.  **Create the release.** From the public repo, run
        `gh release create v1.1.0 dist/1.1.0/Photos-Unlimited.apk dist/1.1.0/SHA256SUMS.txt --repo venzellucas/photos-unlimited --target <full public commit SHA> --title "Photos Unlimited 1.1.0" --notes-file <1.1.0 section of RELEASE_NOTES.md saved to the scratchpad>`.
        The APK and the checksum file are attached in this same command, so
        the release is never "latest" without them.
    7.  **Check the link.**
        `curl -sI https://github.com/venzellucas/photos-unlimited/releases/latest/download/Photos-Unlimited.apk`
        must redirect to the v1.1.0 asset.

#### 28. `app/src/main/java/app/photos/unlimited/ScanFailure.java` (Create, P1)

*   **Type / Scope**: Exception type.
*   **Proposed Changes**:
    `final class ScanFailure extends IOException { ScanFailure(String label, String message) }`
    with `label()`. The label must match `[A-Z0-9_]{1,48}`.

#### 29. `app/src/main/java/app/photos/unlimited/LibraryProtocol.java` and `LibrarySync.java` (Modify, P1)

*   **Type / Scope**: Library scan parsing and paging.
*   **Proposed Changes**: Each `throw new IOException(msg)` below becomes
    `throw new ScanFailure(<label>, msg)`, with the same message text.
    *   `LibraryProtocol.text` → `SCAN_ITEM_TEXT_LONG`.
    *   `LibraryProtocol.parsePage`, page structure → `SCAN_FORMAT_PAGE_…`
        step labels (split after Gate D; grammar in Part 5).
    *   `LibraryProtocol.parsePage` (blank item or deletion key) and
        `LibraryProtocol.item` (no details) → `SCAN_ITEM_INVALID`.
    *   `LibraryProtocol.advance` → `SCAN_CURSOR`.
    *   `LibrarySync.run` (a repeated cursor position) → `SCAN_CURSOR_CYCLE`.
    *   `Proto`'s decoder throws `Proto.Malformed`, a subclass of
        `IOException` carrying a reason (`DUP`, `WIRE`, `CUT` or `TAG`), the
        field number (0 when unknown) and the wire type. The message texts
        are unchanged. Uploads share `Proto`, so they see the same exception
        type hierarchy and the same texts as before.
    *   In `LibraryProtocol.parsePage`, a `Proto.Malformed` raised in one of
        the four page steps (`ENV`, `ROOT`, `SHAPE`, `END`) becomes
        `ScanFailure("SCAN_FORMAT_PAGE_<STEP>_<REASON>[_F<field>][_W<wire>]",
        e.getMessage())`. In `LibraryProtocol.parse`, any other unlabelled
        `IOException` is still rethrown as plain `SCAN_FORMAT_PAGE`. Item
        envelopes map to `SCAN_FORMAT_ITEM`, item keys to `SCAN_FORMAT_KEY`
        and deletions to `SCAN_FORMAT_DELETION`. Other item fields make the
        item "unknown" (Part 5). A `ScanFailure` passes through unchanged.
    *   `LibrarySync.run` catches a `ScanFailure` from each page request and
        rethrows `LibraryProtocol.withStage(e, stage)`, which appends `_` and
        the scan stage's first letter (`N`, `I`, `S`, `D`) to
        `SCAN_FORMAT_PAGE…` labels only. `ReclaimRemote.lookup` uses the
        same loop; its labels change too, which is harmless because reclaim
        shows only the message.
    *   **Delta pages with no known fields.** Root field 4 (wire 2, the
        collection record) counts as known from 1.2.1 (fifth phone run).
        `SHAPE_NONE` names the root's
        lowest field (`SHAPE_NONE_F<n>`). In the `DELTA` stage only,
        `LibrarySync.run` reads that failure as `LibraryProtocol.EMPTY` and
        continues through `advance` and `apply`. `advance` then gives `DONE`,
        `SYNC` (an earlier page said "more" and the sync token moved) or
        `SCAN_CURSOR`. When the same run ends in `DONE`, the message is
        "Scan finished (<staged label> skipped)"; if the run pauses first,
        the later run's message is a plain "Scan finished". Other stages
        stay strict: in `INITIAL` an early end would skip items.
        `ReclaimRemote.lookup` runs the same loop, so its catch-up pass
        before `ReclaimEngine.verify` also reads such a page as empty, and
        it writes the same message to the scans row the Storage screen
        shows.
        *   **Accepted risk.** If a delta page carried a deletion or trash
            signal in a new root field, it would be read as empty. The cached
            item stays known, hashed and not trashed until the next full
            Storage scan, because later lookups start from the new sync
            token. Meanwhile `ReclaimRecovery`'s orphan removal and
            `ReclaimEngine.verify`'s copy deletion could delete a phone file.
            The added exposure is small: `parsePage` already ignores unknown
            root fields on any page that also has 1, 2, 6, 7 or 9. The risk
            is tracked in a GitHub issue.

    `ScanFailure` is a subclass of `IOException`, so every existing caller
    and test still compiles and behaves the same.

#### 30. `app/src/main/java/app/photos/unlimited/InventoryService.java` (Modify, P1)

*   **Type / Scope**: Scan service.
*   **Proposed Changes**:
    *   **`static String label(Throwable e)`**, a pure function with this
        order:
        1.  `SocketTimeoutException` → `SCAN_TIMEOUT`;
        2.  other `InterruptedIOException` → `null` (user stop, no label);
        3.  `PhotosApi.ApiException` with `signIn` → `SCAN_SIGN_IN`;
        4.  other `ApiException` → `SCAN_HTTP_<status>`;
        5.  `ScanFailure` → its label;
        6.  `IOException` with message `"Service response is too large"` →
            `SCAN_RESPONSE_TOO_LARGE`;
        7.  other `IOException` → `SCAN_NETWORK`;
        8.  anything else → `SCAN_CRASH_<NAME>` (simple class name,
            upper-cased with `Locale.ROOT`, cut to 32 characters).
    *   **`scan()`** (`InventoryService.java:85-131`) stores these exact
        messages. `<L>` is the label from `label(e)`:

        | Case | Stored message |
        | --- | --- |
        | Page limit or deadline reached | `Scan paused. Continue to read the remaining items. (SCAN_PAUSED_LIMIT)` |
        | Timeout | `The scan timed out. Results are partial. Continue or start over. (SCAN_TIMEOUT)` |
        | User stop | `Scan paused. Continue when you’re ready.` (unchanged, no label) |
        | Sign-in | `Reconnect your Google account to continue the scan. (SCAN_SIGN_IN)` |
        | Other HTTP | `Google Photos could not complete the scan. You can continue or start over. (SCAN_HTTP_<n>)` |
        | Other `IOException` | `Scan interrupted or metadata format unavailable. Results are partial. Continue or start over. (<L>)` |
        | Anything else | `The scan stopped. Results are partial. Try continuing the scan. (<L>)` |

        The timeout case gets its own `catch (SocketTimeoutException e)`
        before the `InterruptedIOException` catch.
*   **Tests** (P1):
    *   New JVM test `app/src/test/java/app/photos/unlimited/ScanLabelTest.java`
        covers each step of `label` in order:
        *   `SocketTimeoutException` → `SCAN_TIMEOUT`;
        *   `InterruptedIOException` → `null`;
        *   sign-in `ApiException` → `SCAN_SIGN_IN`;
        *   `ApiException` with status 500 → `SCAN_HTTP_500`;
        *   `ScanFailure("SCAN_FORMAT_PAGE", …)` → `SCAN_FORMAT_PAGE`;
        *   `IOException("Service response is too large")` →
            `SCAN_RESPONSE_TOO_LARGE`;
        *   `IOException("x")` → `SCAN_NETWORK`;
        *   `IllegalStateException` → `SCAN_CRASH_ILLEGALSTATEEXCEPTION`.
    *   `app/src/androidTest/java/app/photos/unlimited/LibrarySyncInstrumentedTest.java`
        (test emulator only; it clears the account) runs `LibrarySync.run`
        against a fake `PhotosApi.Transport` that returns a cut-off page,
        and expects `SCAN_FORMAT_PAGE_ENV_CUT_F1_N`, which proves the stage
        suffix is wired. Two more cases drive a page sequence: NEW → SYNC →
        a delta page of field 3 only ends in `DONE` with the
        `…SHAPE_NONE_F3_D skipped` message (and the delta request carries the
        resume and first sync token), while the same page in `INITIAL` fails
        with `SCAN_FORMAT_PAGE_SHAPE_NONE_F3_I`. Strictness elsewhere: an
        empty delta root fails with `…_ENV_NONE_D`, a delta field 9 with
        wire 0 with `…_SHAPE_WIRE_F9_W0_D`, and a page with no known fields
        in `SYNC` with `…_SHAPE_NONE_F3_S`; each keeps its stage.
    *   `app/src/test/java/app/photos/unlimited/LibraryProtocolTest.java`
        gets one case per reachable `ScanFailure` label, each asserting the
        label:
        *   a 16385-byte text field → `SCAN_ITEM_TEXT_LONG`;
        *   page labels: an empty response → `SCAN_FORMAT_PAGE_ENV_NONE`;
            `{10,5,8}` → `…_ENV_CUT_F1`; a cut-off root field 2 →
            `…_ROOT_CUT_F2`; plus `ENV_DUP_F1`, `ENV_WIRE_F1_W0` (the wire
            check runs first), `ROOT_WIRE_F12_W3`, `SHAPE_WIRE_F7_W2`,
            `SHAPE_NONE_F90`, `SHAPE_NONE_F3` (fields 3 and 12),
            `END_DUP_F6`, `END_DUP_F7`, `ROOT_CUT_F7` (a cut-off value after
            a parsed tag), `ROOT_TAG` (a zero tag), and the longest label
            through `withStage`;
        *   an empty delta page: `DONE` with the next sync token; `SYNC` when
            an earlier page said "more" and the token moved; `SCAN_CURSOR`
            when it didn't;
        *   `Proto.Malformed` keeps the old message texts, a field number
            past 536870911 is recorded as 0, and an integer field repeated
            with the wrong type reports `WIRE`;
        *   a broken item envelope → `SCAN_FORMAT_ITEM`;
        *   a repeated key field → `SCAN_FORMAT_KEY`;
        *   a deletion with a repeated field → `SCAN_FORMAT_DELETION`;
        *   an item with an ambiguous field, a file name that isn't valid
            UTF-8, or a 16385-byte file name becomes "unknown" and
            ineligible, and the page's other items still parse;
        *   an item with a blank key, a keyed item with no details, or a
            deletion with a blank key → `SCAN_ITEM_INVALID`;
        *   a deletion with a 16385-byte key → `SCAN_ITEM_TEXT_LONG`;
        *   a deletion entry of another type is skipped;
        *   a page that doesn't advance the cursor → `SCAN_CURSOR`.

### Verification Plan

#### Environments

*   **Build host:** A dedicated Linux machine with the JDK and Android SDK, user `builder`. Builds and emulators run here.
*   **Test emulator:** AVD `photos-api37`, serial `emulator-5560`. A
    `google_apis` image, not signed in to Google, so `adb root` works. All
    instrumentation runs here:
    `adb -s emulator-5560 shell am instrument -w -e notClass app.photos.unlimited.LiveReclaimProbe app.photos.unlimited.test/androidx.test.runner.AndroidJUnitRunner`.
*   **Reference emulator:** serial `emulator-5554`, signed in to the spare
    Google account. Only `LiveReclaimProbe` and manual taps run there.
    Never the full suite.
*   **Author's phone:** the author's real account. Used only for the
    profile and scan labels at Gate D, installed by the author.

Only one emulator runs at a time.

#### Checks

*   **Build** on the build host:
    `./gradlew --no-daemon :app:assembleDebug`. The release APK is built
    only through `tools/build-release.sh`, which supplies the signing key.
    `RELEASE_TAG=v1.0.9 tools/build-release.sh` must exit with an error
    while versionName is `1.1.0`.
*   **Unit tests:** `./gradlew --no-daemon :app:testDebugUnitTest`. The
    existing 57 plus `UpdateLogicTest`, `ScanLabelTest` and the new
    `LibraryProtocolTest` cases, all passing.
*   **Lint:** `./gradlew --no-daemon :app:lintDebug :app:lintRelease`,
    with 0 errors (the 1.0.0 result). Warnings are not counted.
*   **Publication scan:**
    `python3 tools/check-publication.py`
    passes.
*   **Instrumentation:** the command under Environments, on the test
    emulator. All tests pass. Any failure counts. If a re-run passes, record
    the test name and both outputs in the release notes, and treat it as a
    bug to fix.
*   **Release check:** `tools/validate.py`, then `tools/verify-release.py`
    against `dist/1.1.0/Photos-Unlimited.apk`.

#### Live checks, in order

The order follows the phases.

1.  **Profile diagnosis (Gate D, after P1).**
    1.  On the reference emulator, install the Gate D test build over 1.0.0
        (test `versionCode 7` is higher than 1.0.0's 6).
    2.  Tap the avatar, then "Refresh profile photo", and read the label.
    3.  If the label starts with `AUTH_`, try E1, then E2, then E3 there,
        stopping at the first that works.
    4.  If the spare account gets past the token step (any other label,
        or success with no photo), the author installs the test build on
        their phone and reports the label.
    5.  Decide the outcome by Conventions.
2.  **Scan diagnosis (Gate D).** The author runs the Storage scan on their
    phone with the test build and reports the label shown.
3.  **Re-post after reboot (after P3), on the test emulator.**
    1.  Install a release-signed build with `versionCode 7` and
        `versionName '0.9.0'` (the "0.9.0 test build").
    2.  `adb root`. Insert one row with `state = 'DELETED'` and a 20-byte
        zero `sha1` into
        `/data/data/app.photos.unlimited/databases/reclaim.db` with
        `sqlite3`, filling every `NOT NULL` column from `ReclaimDb`'s
        create statement.
    3.  `adb reboot`. Expected: notification ID 7403 is shown without
        opening the app.
    4.  Reinstall the same 0.9.0 test build with `adb install -r`.
        Expected: notification ID 7403 is posted again
        (`MY_PACKAGE_REPLACED`).
    5.  Keep the row for check 5.
4.  **The update card (after P4 and item 27 step 1), on the test
    emulator.**
    1.  Delete the row from check 3 with `sqlite3`, then open the app.
    2.  Expected: the card shows "Version 1.0.0 is available".
    3.  Tap Download. Expected: the browser downloads
        `Photos-Unlimited.apk`.
    4.  `adb shell cat /data/data/app.photos.unlimited/shared_prefs/settings.xml`.
        Expected: `updateLastAttempt` and `updateEtag` are set.
5.  **The blocked card.** Insert the check 3 row again, then reopen the
    app. Expected: "Finish Free up before updating" and "Open Free up".
    Delete the row.
6.  **Upgrade (after P7 step 2), on the reference emulator.**
    1.  `adb uninstall app.photos.unlimited` (a `versionCode` 7 or 8 test
        build may be installed, and Android refuses to install 1.0.0 over
        it).
    2.  Install 1.0.0 and sign in to the spare account again.
    3.  Set one setting, and run Free up on one item so the journal has a
        row.
    4.  Install `dist/1.1.0/Photos-Unlimited.apk` over it.
    5.  Expected: sign-in, the setting and the Free up journal are kept,
        and the Storage screen still lists the library.
7.  **Publish.** Item 27 steps 6-7.

--------------------------------------------------------------------------------

<!-- Provenance: Indicates initial structural drafting via EGM. -->

*Drafted with EGM*
