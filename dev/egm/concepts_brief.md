# Photos Unlimited 1.1: update awareness, profile photo fix, translations, project hygiene

* **Author:** venzellucas
* **Date:** 2026-11-20

## Reviewer Status

| Reviewer | Role title | Status |
| --- | --- | --- |
| `egm-design` | Skeptical Architect Brainstormer | completed |
| `egm-codebase-details` | Codebase Grounding Brainstormer | completed |
| `egm-zoom-out` | Contrarian / YAGNI Brainstormer | completed |
| `egm-comprehension` | UX & Ergonomics Brainstormer | completed |
| `egm-implementation-readiness` | Implementation Readiness & Migration Brainstormer | completed |
| `egm-structural` | Structure & Long-Term Maintenance Brainstormer | completed |
| `egm-inventive-catalyst` | Inventive Catalyst Brainstormer | completed |

The synthesizer checked the load-bearing code claims against the source before writing this brief. They are marked "(verified)" below. Two claims about outside policy could not be checked by any reviewer, because none could open URLs: GitHub API rate limits and Google's Android developer verification rollout. They are marked "(unverified)".

## Overview

Photos Unlimited is a sideloaded Android app (Java, minSdk 33, targetSdk 37, versionName 1.0.0, versionCode 6). It backs up phone photos to Google Photos through Google's private mobile protocol, using a Pixel XL device profile. It can also "free up storage": it downloads an item that counts against Google storage, deletes it from Google Photos, and re-uploads it so it no longer counts. One developer maintains it, and it is distributed only as an APK on GitHub Releases.

The 1.1 concept has four parts:

- **(a) Update awareness.** Once a day the app asks GitHub for the latest release. If a newer version exists, the app shows a notice with a button that hands the user to Android's normal installer through the browser. There is no self-install and no `REQUEST_INSTALL_PACKAGES`. A setting turns the check off.
- **(b) Profile photo bug.** "Refresh profile photo" always fails, and the avatar always shows the initial letter.
- **(c) Translations.** Move all user-visible text into Android string resources. Translate into pt-BR, es, zh-CN and ja (machine translated and machine checked). Follow the system language, with a per-app override. Translate the README and add a language switcher to the website.
- **(d) Gap check.** Compare the repository with well-maintained open-source Android projects and pick what is worth adding.

The target users are non-technical phone owners worldwide, including VPN users in China. The core value of 1.1 is that users learn about safety fixes, see the account they connected, and can read the app in their own language. All of this must stay within the hard rules: a simple UI and website, and safety warnings that are never wrong or stale in any language.

All seven reviewers reached the same conclusion: **the four parts should not ship as one release.**

- **(a) and (b)** are small and should ship first. Only builds that contain the update check can tell users about later releases.
- **(c)** is larger than described. The app saves finished English sentences to storage and makes decisions by comparing English text. Translation therefore needs a refactor of how status and messages are stored, and that refactor touches the Free up journal, the most safety-sensitive code in the app.
- **The three machine-translated languages** cannot meet the "never wrong or stale" rule without a verification gate that does not exist yet.
- **(d)** reduces to three or four small files plus one CI workflow.

## Analysis

### Facts established from the code

- minSdk is 33 (`app/build.gradle:7`) (verified). Android's built-in per-app language setting therefore exists on every supported device. The "Android 13+" condition in the concept always holds, and no custom language picker or backport is needed.
- The app does use AppCompat. `ThemedActivity` extends `AppCompatActivity`, and AppCompat arrives through the Material dependency (codebase-details).
- The only HTTP stack is `HttpURLConnection`. `PhotosApi.Network` allows only fixed Google hosts (`PhotosApi.java:651-657`). `ProfileApi.Network` allows only the userinfo endpoint and the `lh*.googleusercontent.com` / `lh*.ggpht.com` image hosts.
- Background work uses JobScheduler only. All three job IDs run `BackupJob`, which always calls `BackupEngine.run` (`BackupJob.java:16`). `Scheduler.configure` cancels every job when backup is off (`Scheduler.java:23-28`).
- There is no `res/values/strings.xml`. Reviewers estimate about 400-450 user-visible strings across about 20-24 files, not only MainActivity:
  - about 230-260 in MainActivity.java;
  - about 35 in ReclaimEngine;
  - about 17 in ReclaimService;
  - about 20 in BackupEngine;
  - about 15 in SignInActivity;
  - about 50 in AccountProbe and AccountProbeActivity;
  - exception text in PhotosApi.

  There are 27 "item(s)"-style plurals and many sentences built by concatenation.
- PRIVACY.md, the site FAQ (`docs/index.html:98`) and the private README all say or imply that the app contacts only Google. "Updates are manual" appears at `README.md:39`, `docs/index.html:93` and in the RELEASE_NOTES install sections. All of these become false once (a) ships.
- Release links are pinned to `v1.0.0` in `README.md:7-11, 153` and `docs/index.html:41, 44, 92, 93`, together with the "3.5 MB" size and the test counts.
- `docs/design-system.md:71` says "Do not place developer diagnostics or a permanent release announcement above everyday actions" (verified). Lines 92-94 already ask the UI to "consume typed state, not infer state by matching the current free-form `Settings.status()` strings" (verified).

### Blockers

**B1. Translation is not only "move literals to strings.xml". Saved English text and logic that reads English text must change first.**
Raised by all seven reviewers in different forms.

Stored English sentences:
- `Settings.status()` saves finished English sentences to SharedPreferences (`Settings.java:105-111`), written from about 20 places in `BackupEngine.java:77-210`.
- `BackupDb.error` holds exception text (`BackupEngine.java:168, 176, 178`).
- `InventoryDb.message` holds scan messages (`InventoryService.java:110, 145`).
- The Free up journal column `ReclaimDb jobs.message` holds safety text (`ReclaimEngine.java:577-584`). Examples: "The verified copy is kept in Pictures/Photos Unlimited/Pending upload" and "Check Google Photos trash" (`ReclaimEngine.java:566`). Some of these sentences are built by appending (`:526, :531`).

A sentence saved before a language change stays in the old language. Rows written by 1.0.0 stay English forever. That is the "stale safety warning" case the hard rule forbids.

Logic that depends on English text:
- The primary status button chooses its action by comparing the visible label: `choice.equals("Reconnect account")` and similar (`MainActivity.java:836-844`) (verified). Once translated, every action falls through to `startBackup()`.
- `BackupEngine.safeMessage` shows an exception message only if it starts with English words such as "File ", "Google " or "Upload " (`BackupEngine.java:229-236`) (verified). Translated messages would turn into the generic fallback without any error.
- `AccountProbe.authFailure` matches English words in messages (`AccountProbe.java:62-84`).
- The private test scripts `tools/verify-release.py` and `tools/validate.py` tap controls by their English text.

The engines that produce text have no Android Context. `ReclaimEngine` and `PhotosApi` are plain Java, and unit tests run with `returnDefaultValues = true`, so a `getString` call there would quietly return null in tests.

*What is needed.* Engines report what happened as a message code plus arguments, and the UI turns the code into text only when it is shown. Screen actions are chosen by an enum or ID, not by label. Safety warnings come from saved state (`job.state`, `ReclaimDb.unsafeAll()`) rather than from saved sentences. The concept must say how 1.0.0 rows that contain English text are shown. The suggested answer: show a localized line based on the job's state, with the old English text as a secondary "details" line.

Reviewers disagree on the database:
- design and implementation-readiness: avoid a `ReclaimDb` schema change in this work. `onUpgrade` refuses versions above 3 and always adds the `motion` column (`ReclaimDb.java:44-47`), so a version-5 migration is risky on a journal that must never be lost. The user-facing line can be built from the existing `state` enum plus counts.
- structural: add code and args columns in schema v4.

The design doc must settle this.

**B2. Machine-translated safety text cannot meet "never wrong or stale" as described.**
Raised by all reviewers except codebase-details, which treated it as a lint concern.

No person reviews the translations. "Machine checked" is not defined, and a second machine can repeat the first machine's mistakes. Android lint's `MissingTranslation` check finds missing keys, but not a translation that is out of date after the English changed. Specific hazards:

- **"charged"** (meaning "counts toward Google storage") will be machine-translated as billed, or as battery charging. The app already has an "Only while charging" setting. The state label "Still charged" (`MainActivity.java:1063`) is at risk (comprehension).
- **"Free up storage"** is close to Google Photos' own "Free up space", which deletes *phone* copies, the opposite action. In pt-BR, es, zh and ja the two feature names may be translated with the same words (comprehension).
- **"storage"** in zh and ja usually means phone storage, not Google account storage.
- **Instructions that name Google Photos menus** ("open your profile picture, then Backup", `MainActivity.java:265`) must use Google Photos' own translated label in each language. Otherwise the user looks for a button that does not exist, and that step is what keeps phone photos safe during Free up.

*What is needed.*
- A named set of safety-critical keys (about 10-20), kept in a separate file such as `strings_safety.xml` or marked with a `safety_` prefix.
- A short glossary per language.
- A lock file that records a hash of the English source for each translated key. A check script fails the build when the English changed and the translation did not.
- A rule for what to do then. Most reviewers proposed: delete the stale translation so Android falls back to English for that one string. Correct English beats a wrong translation.
- A defined review for each unreviewed language. Proposed: back-translate the safety strings with a different model and compare against the glossary, and check screenshots on a device.

**B3. The staging folder path and protocol constants must never pass through translation.**
Raised by codebase-details, comprehension, structural, implementation-readiness and inventive-catalyst.

`Pictures/Photos Unlimited/Pending upload/` is a real folder on disk (`ReclaimService.java:33`, used by recovery) (verified). It also appears inside at least 7 display sentences (`MainActivity.java:1107, 1113, 1119, 1391`; `ReclaimEngine.java:466, 478`; `ReclaimService.java:141`).

- If a translator localizes the path inside a sentence, the warning sends users to a folder that does not exist.
- If the constant itself ever comes from resources, recovery looks in the wrong directory and staged originals are orphaned. That is a data-loss path.

Protocol strings are part of the Pixel device profile that the free-storage treatment depends on, so translation must never change them:
- `lang=en_US`, `device_country=us` and `Accept-Language: en_US` (`PhotosApi.java:124-126, 163-166, 521`);
- the User-Agent strings;
- the notification channel IDs;
- the preference values.

*What is needed.*
- The path stays one Java constant and is inserted into sentences as a `%1$s` placeholder.
- Brand names, the path and URLs go in a `translatable="false"` file.
- A check confirms that every locale's version of a path-bearing string contains the placeholder.
- A unit test confirms that the protocol form fields still equal the 1.0 values.

**B4. The profile failure cannot be diagnosed today.**
Raised as Blocker by codebase-details and as Major by the other reviewers.

- `catch (Exception ignored)` at `MainActivity.java:1588` turns every failure into the same message (verified).
- There is no `android.util.Log` in `app/src/main`.
- `PhotosApi.Network.call` throws "Google returned HTTP n" on any non-2xx response before it reads the body (`PhotosApi.java:673-683`) (verified). ProfileApi sends its auth request through that code (`ProfileApi.java:101-102`), so its own `Error=` check (`ProfileApi.java:51`) never runs when Google answers with HTTP 403. Google's actual reason is discarded twice.
- Live profile authorization was never validated: `RELEASE_NOTES.md:65`, `README.md:56`, and `ProfileInstrumentedTest.java:48-64`, which uses only a fake transport.

Ranked hypotheses (codebase-details, inventive-catalyst, implementation-readiness), none of which can be confirmed without a live call:

1. **Google refuses to issue a userinfo-scoped token for the Photos client identity.** The request reuses the stored Photos credential (`client_sig = PHOTOS_SIG`, `app = com.google.android.apps.photos`, `oauth2_foreground = 1`) and swaps only `service` to the openid/userinfo scopes (`ProfileApi.java:13-15, 29-31`). The refusal would come back as HTTP 403 (`NeedPermission`, `BadAuthentication`, `INVALID_SCOPE`) or as HTTP 200 with `issueAdvice` and no `Auth`. `has_permission=1` was sent only in the first exchange (`PhotosApi.java:117`) and is not in the stored credential.
2. **The userinfo GET returns a non-200 status.**
3. **`parse` rejects the response** (`ProfileApi.java:61-64`): missing `email` or `email_verified`, or an email alias mismatch such as gmail.com vs googlemail.com.
4. **The image stage fails**: redirect host, content type or decode.

*What is needed.* Record a fixed failure label with no secrets in it:

| Stage | Labels |
| --- | --- |
| Auth | `AUTH_HTTP_<n>`, `AUTH_ERROR_<value>`, `AUTH_NO_TOKEN` (plus the response key names) |
| Userinfo | `USERINFO_HTTP_<n>` |
| Profile check | `PROFILE_FIELDS_MISSING`, `PROFILE_EMAIL_MISMATCH` |
| Image | `IMAGE_HTTP_<n>`, `IMAGE_REDIRECT_HOST`, `IMAGE_TYPE`, `IMAGE_DECODE` |

Keep the auth response body on the profile path. Show the label in the failure message or behind the existing "Copy diagnostic result" in AccountProbeActivity, reusing the fixed-label approach of `AccountProbe.authFailure`. Run it once on a real device. Experiments to try, in order:

1. add `has_permission=1`;
2. request a single scope;
3. request the profile token as `app=com.google.android.gms` with `GMS_SIG` (`PhotosApi.java:17`). This uses a separate token, so it cannot affect the upload credential, but it changes what PRIVACY.md must say.

If Google refuses the scopes, most reviewers recommend removing the network avatar and keeping the initial letter.

The design reviewer adds an account risk. While the lookup fails, the app makes another non-Photos scope token request under the Photos client identity on every app open, at most once an hour (`ProfileStore.java:56-62`). It does this on the account whose credential the whole app depends on.

### Major

**M1. Ship the update check first and on its own.**
Raised by design, structural, zoom-out and implementation-readiness.

1.0.0 contacts only Google, so 1.0.0 users can never be told about 1.1 from inside the app. Every month (a) waits behind (c) is a month in which 1.1 users cannot be told about 1.2. Bundling (a) with about 450 string changes also makes regressions on the deletion path hard to trace.

1.0.0 users can only be reached through:
- the README;
- the site;
- the release itself;
- GitHub "Watch → Custom → Releases" or the `releases.atom` feed.

**M2. How the update check should work.**
The reviewers mostly agree on the shape:

- **When it runs.** In the foreground only, from `MainActivity.onResume` on the existing `io` executor, at most once per 24 hours, using a timestamp in Settings. Store the time of the last attempt, not the last success, and treat a clock that moved backwards as "due".
  - Do not add a JobScheduler job. A new job ID would start a backup, because `BackupJob` always runs `BackupEngine.run`. `Scheduler.configure` would also cancel it when backup is off. And a background check needs a notification channel to tell anyone.
  - Cost: users who never open the app never see the notice. implementation-readiness suggested also running the check from the periodic `BackupJob`. Without a notification, that only changes when the result is fetched, not when it is seen.
- **Transport.** A new `UpdateCheck` class with its own `HttpURLConnection` and a one-host allowlist. Do not widen the Google-only transport.
  - Send a fixed, generic User-Agent. The default Dalvik User-Agent sends the device model and build.
  - Send no account or device identifiers.
- **Comparison.** Accept only tags matching `^v(\d+)\.(\d+)\.(\d+)$`. Compare numerically with `PackageInfo.versionName`, read the way `MainActivity.java:1751` already does. Show the notice only if the remote version is strictly newer.
  - Compare when drawing the screen, not when fetching, so the notice clears itself after an update.
  - Turn the check off in debug builds. Keep the repository name a constant.
- **Failures** (offline, 403, 429, 404, GitHub blocked) are silent and never touch backup status or `Settings.failure`. implementation-readiness and codebase-details suggest a "Last checked" line in Settings.
- **The button.** The browser hand-off is the only option that fits the no-`REQUEST_INSTALL_PACKAGES` rule (implementation-readiness B4). In-app download followed by the installer needs that permission. Which URL to open is undecided (see Key Decisions).
  - Never show text fetched from GitHub (release body) in the app. It is English-only and comes from outside.
  - Never use the update channel to send remote messages or "kill switch" text (design).
- **The trust root** is Android refusing an update signed with a different key. The app does not need its own checksum check.
  - Publish the signing certificate's SHA-256 fingerprint.
  - Keep an offline keystore backup.
  - Never rename the GitHub account. Someone else could register the old name, and every installed notice would point at their releases (design).
- **Placement.** Not a permanent banner above everyday actions (design-system.md:71). Options:
  - a Settings row near About, plus a badge on the Settings tab;
  - a calm card on Backup, dismissible once per version, shown below any safety card.

  It should be called an "update notice", not a "warning", and use no ⚠ styling. The app already uses warning styling for real danger (comprehension).
- **Privacy text** changes in the same commit:
  - PRIVACY.md gets a GitHub section;
  - the site FAQ;
  - the private README "Account handling";
  - every "Updates are manual" line.

  The new text says GitHub sees the IP address, user agent and time. It must not claim "no data".

**M3. The update path and the "only on this phone" state.**
Raised by comprehension, design, implementation-readiness and inventive-catalyst.

Installing an update kills the process, including a running `ReclaimService`. A sideloaded install can fail ("App not installed", "package conflicts", a block from developer verification), and the usual advice online is "uninstall first". Uninstalling while Free up has items deleted from Google but not yet re-uploaded is exactly what the app's "Don't uninstall Photos Unlimited yet" warning forbids.

An existing gap makes this worse:
- `warnIfOnlyOnPhone` is called only at the end of a reclaim run (`ReclaimService.java:275`) (verified).
- `BootReceiver` only calls `Scheduler.ensure` (verified).

After a reboot or an update, nothing posts that warning again until the user opens the app.

*What is needed.*
- While `ReclaimDb.unsafeAll() > 0` or a Free up run is active, replace the update button with "Finish Free up before updating".
- Call `warnIfOnlyOnPhone` from `BootReceiver` on `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`, and from `MainActivity.onResume`.
- The notice text says, in every language: "Install over the current app. Do not uninstall it first." Add "If Android refuses the install, keep using this version."
- Test the 1.0.0 → 1.1 upgrade with a job in the DELETED state.

**M4. Android developer verification and Brazil (unverified).**
Raised by comprehension, design, structural, implementation-readiness and inventive-catalyst.

Several reviewers recall that Google announced blocking installs from unregistered developers on certified devices. They recall Brazil in the first wave from around September 2026, and that it probably covers updates. pt-BR is a main target. If enforcement is active, "Download latest version" sends Brazilian users into a blocked or long, frightening install flow that the app, README and site never explain.

This is not a request to change the developer's decision. The request:
1. Check the current enforcement status, and test an install on a certified phone set to the Brazil region.
2. Do not promise in any language that tapping the button completes an update.
3. Consider one neutral sentence stating the limit ("Android may block installing apps from outside the Play Store on some phones; your installed version keeps working"). That is not an install guide.

**M5. Do not build an in-app language picker.**
All seven reviewers agree.

With minSdk 33:
- Declare the supported locales (AGP `androidResources { generateLocaleConfig true }` plus `res/resources.properties` with `unqualifiedResLocale=en-US`, or a hand-written `locales_config.xml`). The app then appears in Android's own Settings → System → Languages → App languages.
- Add at most one Settings row that opens `Settings.ACTION_APP_LOCALE_SETTINGS`. comprehension suggests a globe icon and a label that also includes the English word ("Idioma / Language").
- Keep no stored override of the app's own. `LocaleManager` is the single source of truth.
- Use `localeFilters` limited to the shipped languages. Otherwise Material/AppCompat library strings show partly in, for example, French.

**M6. Text that will not switch language on its own.**
Raised by codebase-details, structural, design and implementation-readiness.

- Ongoing notifications keep the language they were posted in.
- Notification channel names are set from hardcoded English (`ReclaimService.java:51`, `BackupService.java:20-21`, `InventoryService.java:32-33`).
- `ReclaimService.status` is a cached static string.

*What is needed.* Re-create the channels from resources on each service start. Re-post the "only on this phone" notification on resume and on `ACTION_LOCALE_CHANGED`. The Google sign-in page follows the system language, not the app language. Document that and do not try to force it.

**M7. Plurals and sentences built from pieces.**
Raised by codebase-details, comprehension, design, implementation-readiness and inventive-catalyst.

Examples:
- `"Free up " + bytes + " from " + n + " item(s)?"` (`MainActivity.java:1263`);
- the reclaim card built from up to 6 conditional paragraphs (`:1094-1132`);
- `failures + " item" + (failures == 1 ? " needs" : "s need")` (`BackupEngine.java:192-196`);
- title and subtitle packed into one literal with `\n` (`MainActivity.java:557, 574, 603`).

Japanese and Chinese word order breaks fixed concatenation. Every sentence becomes one resource with positional placeholders and `<plurals>`, and the safety strings get a translator comment.

Other UI points:
- Android 12+ cuts toasts to two lines, and es and pt-BR text runs 20-30% longer. Any safety or "what to do" message moves from `message()` (Toast) to a dialog or card (comprehension).
- Check every screen in each language at the largest font size.
- Enable `pseudoLocalesEnabled` on debug and screenshot `en-XA`. Leftover English then shows up as missed literals, which lint cannot find because text goes through the custom `Ui` helpers.

**M8. Every release blocks on translations.**
Raised by zoom-out and implementation-readiness.

`tools/build-release.sh:6` runs `lintRelease`, and `MissingTranslation` is an error by default. With five languages, every English change waits for four translations, including urgent fixes when Google changes the private protocol. Turning the check off lets wrong text ship silently.

Two answers came back:
- ship fewer languages (zoom-out);
- keep the lint error only for the safety set and allow English fallback for the rest (design, inventive-catalyst).

Either way, AccountProbeActivity's roughly 50 diagnostic strings should be `translatable="false"`.

**M9. Translated README and site pages will drift.**
Raised by all seven reviewers.

The README is 179 lines, and much of it is dense Free up safety text (`README.md:59-107`). Five full copies drift apart, and a drifted README is a wrong safety warning with no review step. Version pinning multiplies one release edit into about 40-50 edits. `site.js` has English labels, alt text and the caption built in (`site.js:9-14, 31-32`). Copying `<link rel="canonical">` unchanged marks translated pages as duplicates.

Reviewer positions:
- **zoom-out:** cut translated READMEs and the site switcher entirely. Browser translation (Chrome on Android offers it on GitHub and Pages) covers most of the need for no work.
- **The other six:** if done at all, each `README.<lang>.md` is a short summary. It covers what the app is, install and update, and the three safety rules. It starts with "The English README is the complete version; if they differ, English is correct", and for ja and zh adds "machine translated". It carries a source-section hash marker that a check compares with the English. It contains no version numbers or test counts. Build, Validation, PRIVACY, RELEASE_NOTES and legal text stay English.
- **Site:** static per-language pages (`docs/<lang>/index.html`) generated from one template by a stdlib-only script, with the output committed. Each page sets its own `lang`, canonical URL and `hreflang`. The switcher is a plain row of links in each language's own name, with no flags. No JavaScript text swapping (it breaks the no-JS path and search indexing) and no automatic redirect. Move the site.js text into `data-` attributes. Keep English screenshots, with alt text saying so.
- **Everyone:** replace versioned links with `releases/latest`. If a direct download link is wanted, also publish a stable asset name (`Photos-Unlimited.apk`) so `releases/latest/download/Photos-Unlimited.apk` works.

**M10. One locale list should drive every surface.**
Raised by structural and inventive-catalyst.

Six places each list languages:
- the `values-*` folders;
- the locale config;
- the system language row;
- the README set;
- the README language bar;
- the site switcher.

Codes differ by surface (`pt-BR` in file names, `values-pt-rBR` in Android). Keep one table with the code, the Android qualifier, the native-script label and the order, and check the surfaces against it.

Qualifier choices that add reach for free (inventive-catalyst):
- `values-pt` instead of `values-pt-rBR`, so pt-PT and pt-AO users get Portuguese instead of English;
- `values-es` (covers es-419 and es-ES);
- `values-b+zh+Hans` instead of `zh-rCN` (covers Singapore and Malaysia).

Record that zh-Hant users get English.

**M11. Tooling gaps the new files would expose.**
Raised by codebase-details, structural, design and implementation-readiness.

- `tools/check-publication.py:19-20` calls `read_text()` with the platform encoding and skips undecodable files without notice. On a non-UTF-8 host, zh and ja files would be skipped by the secret scan.
- It has no email pattern, so new SECURITY.md, CONTRIBUTING.md, translator credits or templates could leak personal details unnoticed.
- `tools/verify-release.py:16` still compares against the 0.2.0 APK and installs with `adb install -r`, which skips the real installer flow.
- Translation and doc-consistency checks belong in a separate script (for example `tools/check-consistency.py`), mirrored to the public repo and run from `build-release.sh` and CI.

**M12. Version and release contract for installed clients.**
Raised by structural and implementation-readiness.

Once 1.1 is installed, every later release must stay readable by it:
- tag format `vX.Y.Z` equal to `"v" + versionName`, with versionCode always increasing;
- publish only after the APK and `SHA256SUMS.txt` are attached;
- never publish 1.x prereleases as "latest";
- never move "latest" backwards.

`build-release.sh` should check the tag against versionName. No database schema change in 1.1. All three databases throw on unknown versions and have no `onDowngrade`, so rollback is forward-only (a 1.1.1 with a higher versionCode).

### Nits

- **zh-CN as a reach argument is weak** (zoom-out). Users who already get around the firewall to reach Google can likely manage an English APK plus browser translation, and GitHub Pages is unreliable from mainland China.
- **"Free" and "Unlimited" read as a guarantee once translated** (comprehension). Keep "Photos Unlimited" untranslated. Translate "Free & open source" as "open source, no cost". Rewrite the "quota exemption isn't guaranteed" hedge in plain words, with the same visual weight in every language.
- **The current profile failure text scares people** (comprehension). "Backup sign-in" is jargon, and "unavailable" sounds like an account problem. Suggested: "Couldn't load your Google profile photo. Backups are not affected." Remove the README instruction to tap "Refresh profile photo" until it works.
- **The hard rule "never ask users for Google passwords/tokens"** appears to conflict with the existing setup-token fallback ("Enter that token only in the app") (comprehension). Clarify that the rule means never ask users to *send or post* tokens.
- **ETag / `If-None-Match`** on the update check (a 304 does not count toward the API rate limit), if the API is used.
- **The `lh` host patterns differ**: `lh[0-9]+` in `ProfileApi.java:84` vs `lh[0-9]{1,2}` in `PhotosApi.java:335`.
- **Strategic risk** (design). Wider reach raises the chance Google closes the Pixel-profile path, which shortens how long the translation investment pays off.
- **RELEASE_NOTES.md** repeats H2 headings under each version H1, so anchors collide. Restructure it as one H1 with an H2 per version (structural).
- **design-system.md is stale.** Its status is 0.2.0, it says Free up is not part of the release, and it has no i18n section. Update it in the same changes as (a) and (c).
- **The private README contradicts itself.** Line 123-124 says a failure after deletion keeps the copy in private app storage, and line 143 says shared storage. Fix it before anything is translated from it. Translate only from the public README.

### Inventive contradictions and resolutions (inventive-catalyst, with support from others)

| Contradiction | Transform | Resulting design | Cost |
| --- | --- | --- | --- |
| Users must learn about updates vs the app contacts only Google and stays simple | Extraction; inversion (check only when the user is present) | One request a day, only while the app is open, with no identifiers, either to the API (`tag_name`) or the `releases/latest` redirect. A browser hand-off. No job, permission, download code or parser if the redirect is used. | One new network contact to disclose. Users who never open the app are not told. |
| Encourage updates vs never prompt at a dangerous moment | Beforehand cushioning | Hide the button while items exist only on the phone. Re-post the warning on boot or update. Add "don't uninstall" text. | One condition and one safety string. |
| Pure-Java engines vs localized, current text | Inversion: engines report codes, the UI words them | Code plus arguments stored, text rendered at display time | A refactor across BackupEngine, ReclaimEngine, ReclaimService and InventoryService, and JVM tests that assert on English. |
| Reach in five languages vs one human verifier | Segmentation by risk; fail closed to English | About 10-20 safety strings under a hash lock. A stale or unreviewed safety string falls back to English one string at a time. Non-safety strings only warn. | A lock file and check script. Fixing an English typo in a safety string means re-translating or falling back in four languages. |
| Translated docs vs a single source of truth | Segmentation by stability; extraction of versions | Short summary READMEs with hash markers, version-free links, and site pages generated from one template | A generator script and false alarms on typo fixes |
| "Profile failure must not disturb backup" vs "the failure must be diagnosable" | Blessing in disguise | Failure becomes a fixed, non-secret stage code | A technical "details" line |
| Per-app language override vs UI simplicity | None needed | The system App languages page plus at most one row | One row |

**Ideal Final Result, per part:**
- **(a)** At most one anonymous request a day while the app is open. One button that hands off to the browser and Android's installer, disabled while anything is only on the phone. No new permission, scheduler or remote text.
- **(b)** "Refresh profile photo" either shows the photo or names the stage that failed. The fix follows that evidence, or the feature is removed.
- **(c)** Every visible sentence is created at display time from resources. Each safety string is either a translation checked against the current English, or English, and the build cannot ship anything else. The system provides the language picker. Translated docs are short, have no version numbers, and are checked for staleness by machine.
- **(d)** One CI workflow plus three or four short policy files.

## Goals vs. Non-Goals

* **In-Scope Goals:**
  - **Update notice.** A foreground check, at most once per 24 hours, against the latest GitHub release of `venzellucas/photos-unlimited`.
    - One on/off setting (default to be decided).
    - Silent on any failure.
    - A calm "update notice" (not a warning) with a button that opens a GitHub URL in the browser.
    - Disabled while Free up has items only on the phone.
    - PRIVACY.md, README, site FAQ and every "Updates are manual" line updated in the same change.
  - **Re-post "only on this phone".** Re-post the warning after boot and after an app update (an existing gap that the update flow makes worse).
  - **Profile diagnosis.** Fixed, non-secret failure-stage labels for the profile lookup; one live diagnosis; then either a targeted fix or removal of the network avatar.
  - **Translation groundwork.** Typed message codes instead of stored English sentences. Code- or ID-based dispatch instead of label comparison. The path and protocol constants kept out of resources.
  - **String extraction.** All UI, notification and dialog text moved into string resources, with plurals and positional placeholders, a separate safety-string set and a non-translatable file.
  - **Translations with a safety gate.** pt-BR, with es, zh-Hans and ja behind the safety-string gate: glossary, hash lock, a defined review, and English fallback for stale or unreviewed safety strings.
  - **System language setting.** A locale config so Android's own App languages setting works, plus at most one Settings row that opens it.
  - **Repository hygiene:**
    - SECURITY.md using GitHub private vulnerability reporting (no personal email);
    - one bug issue template with app version, Android version, app language, the profile diagnostic code, and a required "I removed tokens, cookies and private photos" checkbox;
    - one CI workflow running the unsigned build, unit tests, `lintDebug`, `check-publication.py` and the translation-staleness check;
    - UTF-8 reading and an email pattern in `check-publication.py`;
    - the signing certificate fingerprint published.

* **Out-of-Scope Non-Goals:**
  - In-app download, self-install, `REQUEST_INSTALL_PACKAGES`, DownloadManager, in-app checksum checking.
  - A background update job or an update notification.
  - Showing GitHub release text in the app, and any remote-message channel.
  - Registering for Google developer verification, or an advanced sideload guide. These are the developer's decisions, recorded as a known limit.
  - A custom in-app language picker or stored locale override.
  - Translating AccountProbeActivity, PRIVACY.md, RELEASE_NOTES.md, THIRD_PARTY_NOTICES.md, license texts, issue templates or the Build and Validation sections.
  - Translated screenshots.
  - Traditional Chinese.
  - CHANGELOG.md (RELEASE_NOTES.md already serves).
  - fastlane or F-Droid metadata. The `play-services-auth` dependency is non-free, and no store distribution is planned.
  - Reproducible builds (deferred until CI exists).
  - Database schema changes in 1.1.
  - New authentication paths for the profile photo beyond the diagnosis experiments.

## Alternatives Weighed

- **Automatic in-app update install (with `REQUEST_INSTALL_PACKAGES`).** Rejected by author: too permissive.
- **Registering for Google's Android developer verification.** Rejected by author. Reviewers ask that the consequence for Brazilian users be checked and stated, not that the decision change.
- **An advanced sideload guide on the website.** Rejected by author. Reviewers suggest at most one neutral sentence stating the limit, which they consider within that decision.
- **No network check at all: a "Check for updates" row that opens `releases/latest`, plus "Watch → Releases" or the `releases.atom` feed in the README.** Proposed by zoom-out and inventive-catalyst as the zero-network option. Reviewers recommend against it as the only mechanism: Free up deletes data and depends on a protocol Google can change, so safety fixes must reach users who never think to look.
- **A background (JobScheduler) daily check with a notification.** Reviewers recommend against it:
  - a new job ID would run `BackupEngine.run`;
  - `Scheduler.configure` cancels all jobs when backup is off;
  - it needs a new channel, strings and boot handling.
- **In-app download through DownloadManager.** Not possible without `REQUEST_INSTALL_PACKAGES`. The user would have to find the file in Files.
- **Reading `browser_download_url` or `html_url` from the API response and opening it.** Recommended against by zoom-out and implementation-readiness. Build the URL from the constant repository name and the parsed version, so no URL from the network is ever opened. design accepts `html_url` only with a host and path check.
- **Machine translation of all strings, safety strings included, with "machine checking" only.** Recommended against by six reviewers: it cannot meet "never wrong or stale".
- **Leaving safety strings in English inside an otherwise translated UI, as a permanent design.** zoom-out calls it safe but likely to look broken. The other reviewers accept English fallback per string as a temporary, gated state, not as the plan.
- **Moving literals into `strings.xml` without changing stored messages.** Recommended against by all reviewers (B1).
- **A custom in-app language picker.** Recommended against by all reviewers (M5).
- **Full translations of the README, and a JavaScript text swap on the site.** Recommended against: drift, no-JS breakage and search indexing. zoom-out goes further and recommends no translated docs at all, relying on browser translation.
- **One combined 1.1 release with all four parts.** Recommended against by design, structural, zoom-out and implementation-readiness.
- **CHANGELOG.md, CONTRIBUTING.md, fastlane/F-Droid metadata, reproducible builds, new or translated screenshots.**
  - CHANGELOG duplicates RELEASE_NOTES.md.
  - fastlane metadata adds a sixth translated surface for excluded channels.
  - Reproducible builds are multi-day work with R8 and a private key, and are deferred.
  - Reviewers split on CONTRIBUTING.md: zoom-out says cut it; structural, design and inventive-catalyst suggest about 20 lines saying the public repo is a mirror and translation fixes arrive as issues.
- **Working around Google's refusal of the profile scopes with new auth paths.** Recommended against beyond the listed experiments. Removing the network avatar is the fallback.

## Key Decisions & Staging for Next Steps

### Proposed staging (consensus of design, structural, zoom-out, implementation-readiness)

1. **1.1.0: update notice + profile diagnosis or fix + warning re-post.**
   - Part (a) gets a short design doc: network contract, privacy text, placement, and the update in design-system.md.
   - Part (b) is a bug fix. Use `egm-lite`, not a design doc.
   - New strings for 1.1 go straight into a new English-only `res/values/strings.xml`.
2. **Repository hygiene, any time, with no APK.** SECURITY.md, the issue template, CI, the `check-publication.py` fixes and the certificate fingerprint. Land SECURITY.md and the template before any translated docs bring in non-English users.
3. **1.2.0: app translation, the main design doc**, in this order:
   - **c0.** Typed status and message codes; ID-based dispatch; the `safeMessage` replacement; protocol-constant pinning test. No visible change.
   - **c1.** Extract all strings in English, with plurals, placeholders, the safety set and the non-translatable file. Check with pseudolocales. No visible change.
   - **c2.** The glossary, safety-key list, hash lock and check script, then pt-BR.
   - **c3.** es, zh-Hans and ja, each only after its safety strings pass the defined review. Until then those strings fall back to English.
   - **c4.** Locale config and the one Settings row.
4. **After 1.2.0, no APK.** Short translated READMEs and generated per-language site pages, if still wanted. Do these after the app is translated, because the docs quote UI labels that change in step c.

### Decisions the author must make before `/egm-draft`

1. **Update check default** for users upgrading from 1.0.0: on or off, and how it is disclosed (release notes, first launch). Reviewers lean toward on by default, with disclosure.
2. **Update source:**
   - GitHub API `releases/latest` (`tag_name`): a documented contract, but about 60 unauthenticated requests per hour per IP (unverified), so shared VPN and carrier-NAT IPs will see 403s.
   - The `github.com/.../releases/latest` redirect (read the `Location` header): no JSON and not under the API limit, but not a documented contract.

   Reviewers are split.
3. **Button target:**
   - The release page: shows notes and checksums and survives asset renames, but naive users see "Source code (zip)".
   - The direct `.apk` URL built from the tag: one step shorter, but the browser shows a file warning.

   Most reviewers prefer the release page. comprehension and inventive-catalyst prefer the direct APK, with a "What's new (English)" link. Publishing a stable asset name would make both easier.
4. **Placement:** a Settings row plus tab badge, or a dismissible card on Backup below any safety card.
5. **Stored-message migration:** add code/args columns to `ReclaimDb` (needs a fixed `onUpgrade`), or derive safety lines from the existing `state` enum and keep `message` as an English details line (no schema change).
6. **Language set and gate:**
   - Who signs off on es, zh-Hans and ja safety strings, and what "machine checked" means (back-translation by a different model, plus device screenshots).
   - Whether English fallback for a stale safety string is the accepted rule.

   zoom-out recommends shipping only en until a named native reviewer exists for each language.
7. **Qualifiers:** `values-pt` or `values-pt-rBR`; `values-b+zh+Hans` or `values-zh-rCN`. Confirm that Traditional Chinese falls back to English.
8. **Translated docs:** none (rely on browser translation), short summary READMEs only, or short READMEs plus generated site pages.
9. **Profile photo:** is removing the network avatar an acceptable outcome if Google refuses the scopes? Decide this in advance.
10. **Developer verification:** check current enforcement in Brazil and whether it covers updates of installed apps. Decide the wording of the notice and known limits.
11. **Release contract:** the tag format, "publish only after assets are attached", the no-prerelease-as-latest rule, keystore backup, and publishing the certificate fingerprint.
12. **The "never ask for tokens" rule vs the existing setup-token fallback:** confirm the intended meaning.

### Author decisions (2026-11-20)

1. **Staging:** accepted. 1.1.0 = update notice, profile diagnosis/fix, "only on phone" warning re-post on boot/update/resume. Translation is a separate later release.
2. **Update check default:** on for everyone, including upgrades from 1.0.0; disclosed in release notes and PRIVACY.md. Can be turned off in Settings.
3. **Update button:** direct APK download from a stable asset name (e.g. `Photos-Unlimited.apk` on every release), plus a small "What's new" link to the release page. Button hidden while any reclaim job is unsafe (`unsafeAll() > 0`).
4. **Profile photo:** diagnose live first with fixed failure-stage labels. If Google refuses the profile scopes, drop the network avatar and keep the initial letter.
5. **Repository hygiene (part d):** SECURITY.md, one bug template, one CI workflow, check-publication.py UTF-8 + email pattern, published signing-certificate fingerprint. Accepted by default.
6. **Translation scope:** decided — translate the app fully in 1.2: en-US, pt-BR, es, zh-Hans, ja, all made by a language model with no human review (author, 2026-11-21). es/zh-Hans/ja safety strings go through the glossary, an independent back-translation check, and a hash lock; a stale safety string falls back to English. README and website in the same five languages after the app strings settle. A "Request a translation" issue template invites native reviewers later.
7. Technical choices (API vs redirect, resource qualifiers, ReclaimDb migration approach, release contract) are delegated to the drafter.
