# Goldfish Annotated Review Report

Status: resolved (2026-11-20). Author chose "auto accept": all PROPOSE FIX items applied to docs/designs/photos-unlimited-1.1.md; declined nits left as recorded. Same edit also added Part 5 (library scan diagnosis) at the author's request.

**Target:** `docs/designs/photos-unlimited-1.1.md` (lines 631-1015, Detailed Implementation Plan)
**Source findings:** `.egm/implementation_readiness_report.md` (Stage 9, round 1: 2 Blockers, 24 Majors, 25 Nits)

Summary of suggestions:

| Category | Count |
| --- | --- |
| PROPOSE FIX | 47 |
| DECLINE (nit, recorded) | 4 |
| CONTEST | 0 |
| ESCALATE | 0 |

I checked every Blocker and Major against the source and found none to
contest. The plan reads as finished, but too much of it still depends on what
I know and the document doesn't say. Most fixes are additions:
- exact signatures;
- old and new text for each doc edit;
- phases;
- named environments.

The proposed edits below give the content of each fix. Applying them means
rewriting the Detailed Implementation Plan section, plus small changes to
Technical Plan 1.2 (the signature), 1.3 (card text) and 3.1 (the label
rule).

## Prioritized Findings & AI Suggestions

### [Issue 1] [`implementation-readiness`] - Severity: Blocker
*   **Affected Scope:** Lines 764-766, 777-778, 842-843 (item 9); table at line 382
*   **Discrepancy Description:** The `Failure` constructor allows only `[A-Z0-9_]{1,48}`, but `AUTH_ERROR_<value>` keeps Google's mixed-case value of up to 40 characters, so `AUTH_ERROR_BadAuthentication` throws.
*   **Rationale:** The key diagnostic label would show as `NETWORK`.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid. The value has to be normalized before the check.
*   ***Proposed Edit:***
    *   Location: Technical Plan 3.1 table, and item 9.
    *   Change:
        *   `AUTH_ERROR_<VALUE>`, where VALUE is Google's `Error=` value with
            every character outside `[A-Za-z0-9]` removed, then upper-cased,
            then cut to 36 characters.
        *   The constructor keeps `[A-Z0-9_]{1,48}`.
        *   The test expects `AUTH_ERROR_BADAUTHENTICATION`.

### [Issue 2] [`implementation-readiness`] - Severity: Blocker
*   **Affected Scope:** Lines 770-781, 842-843
*   **Discrepancy Description:** `ProfileApi.Network` implements `PhotosApi.Transport`, whose `Response` has no status. So `AuthResponse` can't reach `fetch`, and tests can't fake a status.
*   **Rationale:** The implementer would have to design the transport seam.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid. `PhotosApi.java:34-35, 55` confirms it.
*   ***Proposed Edit:***
    *   Location: item 9.
    *   Change: Add a profile-only seam:
        ```text
        interface ProfileApi.Transport {
          Reply call(String url, String method, Map<String,String> headers,
                     byte[] body, AtomicBoolean stop) throws IOException;
        }
        record Reply(int status, String contentType, byte[] body)
        ```
        *   `fetch(String credential, ProfileApi.Transport t, AtomicBoolean stop)`
            uses it for all three requests: token, userinfo and image.
        *   `ProfileApi.Network` implements the new interface. It does not
            follow redirects; `fetch` follows image redirects itself, at most
            4, checking each `Location` with `imageAddress`.
        *   The status is always returned, never thrown. Error bodies are
            capped at 16 KiB.
        *   `ProfileStore.refresh` passes `new ProfileApi.Network()`.
        *   Tests use a lambda fake that returns any status.
        *   Remove the sentence about changing `PhotosApi.Response`.
        *   `PhotosApi` is still not modified.

### [Issue 3] [`implementation-readiness`] - Severity: Major
*   **Affected Scope:** Lines 638-967
*   **Discrepancy Description:** The plan has no phases or checkpoints, and no defined decision between Outcome A and Outcome B.
*   **Rationale:** An implementer can't tell what to build first, or whether to build A or B.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid.
*   ***Proposed Edit:***
    *   Location: start of the Detailed Implementation Plan.
    *   Change: Add an "Outcomes" paragraph defining Outcome A (an experiment
        works) and Outcome B (none works). Then group the files into phases,
        each ending with build, unit tests and lint:
        *   **P1 Profile diagnosis:** items 9, 10 (retry delay), the
            labelled message in item 6, and the tests.
        *   **Gate D:** live diagnosis plus experiments. The author approves
            experiment 3 if needed. Choose A or B.
        *   **P2 Profile outcome:** A or B edits.
        *   **P3 Warning re-post:** items 7, 8, and the test.
        *   **P4 Update check:** items 3, 4, 5, 6 (card and toggle), 2, and
            the tests.
        *   **P5 Release tooling and repository basics:** items 16-21.
        *   **P6 Docs.**
        *   **P7 Publish,** in a fixed order (see Issue 23).

        Outcome-specific lines are written as "**If Outcome B:**".

### [Issue 4] [`implementation-readiness`] - Severity: Major
*   **Affected Scope:** Lines 633-636, 963-967
*   **Discrepancy Description:** "Copied the same way as for 1.0.0" isn't written down anywhere.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid. A human implementer also needs the procedure.
*   ***Proposed Edit:***
    *   Location: item 27.
    *   Change: Give the procedure:
        1.  From `/root`, run
            `rsync -rcn --delete --exclude=.git --exclude=build/ --exclude=.gradle/ --exclude=__pycache__/ --exclude=dist/ --exclude=local.properties --out-format=%n photos-unlimited/app/ photos-unlimited-public/app/`.
            This is a dry run.
        2.  Check that the listed files are exactly the app files changed
            in P1-P4.
        3.  Run the same command without `-n`.
        4.  Run
            `python3 tools/check-publication.py`.

        Top-level files and `tools/` are never synced. Each public file is
        edited directly as listed.

### [Issue 5] [`implementation-readiness`] - Severity: Major
*   **Affected Scope:** Lines 717-727
*   **Discrepancy Description:** `unsafeCount` starts at 0 and goes stale, so Download can show while items are only on the phone.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid, and a safety gap.
*   ***Proposed Edit:***
    *   Location: items 4 and 6.
    *   Change:
        *   `unsafeCount` starts at `-1` (unknown).
        *   `UpdateNotice.state` returns `HIDDEN` when the count is `< 0`.
        *   `drawBackup` always reads the count fresh on a background thread
            (reusing `unfinishedReclaim()`, `MainActivity.java:1517`) before
            filling the card.
        *   The `onResume` count is only a fast path.

### [Issue 6] [`implementation-readiness`] - Severity: Major
*   **Affected Scope:** Lines 717-723
*   **Discrepancy Description:** The network check on the single-thread `io` executor can stall the Backup screen for about 25 s.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid.
*   ***Proposed Edit:***
    *   Location: item 6.
    *   Change: Add a dedicated
        `private final ExecutorService updateIo = Executors.newSingleThreadExecutor()`,
        shut down in `onDestroy` with the others. Only `UpdateCheck.runIfDue`
        runs on it.

### [Issue 7] [`implementation-readiness`] - Severity: Major
*   **Affected Scope:** Lines 733-734, 767-769, 845
*   **Discrepancy Description:** The label mapping is incomplete: `ProfileStore.decode` and `save`, unlabelled throws, `TIMEOUT` never produced, and no precedence rule.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid.
*   ***Proposed Edit:***
    *   Location: item 9 and item 10.
    *   Change: Add a throw-site → label table:
        *   `parseForm`/credential → `NO_CREDENTIAL`;
        *   token status ≠ 200 → `AUTH_ERROR_<V>` if the body has `Error=`,
            else `AUTH_HTTP_<n>` (the `Error=` value takes precedence);
        *   200 without `Auth` → `AUTH_NO_TOKEN`;
        *   userinfo status ≠ 200 → `USERINFO_HTTP_<n>`;
        *   body > 64 KiB → `USERINFO_TOO_LARGE`;
        *   `JSONException` → `USERINFO_BAD_JSON`;
        *   missing `sub` or `email_verified` → `PROFILE_FIELDS_MISSING`;
        *   email differs → `PROFILE_EMAIL_MISMATCH`;
        *   bad picture address → `PICTURE_HOST`;
        *   image status ≠ 200 → `IMAGE_HTTP_<n>`;
        *   redirect to a disallowed host → `IMAGE_REDIRECT_HOST`;
        *   more than 4 redirects → `IMAGE_REDIRECTS`;
        *   not `image/*` → `IMAGE_TYPE`;
        *   > 2 MiB → `IMAGE_TOO_LARGE`;
        *   `ProfileStore.decode` failures → `IMAGE_DECODE`;
        *   `ProfileStore.save` failure → `CACHE_WRITE`;
        *   `SocketTimeoutException` → `TIMEOUT` (checked before the
            cancellation case);
        *   other `InterruptedIOException` → no message (the user
            cancelled);
        *   any other `IOException` → `NETWORK`.

        `ProfileStore.decode` and `save` throw `ProfileApi.Failure`.

### [Issue 8] [`implementation-readiness`] - Severity: Major
*   **Affected Scope:** Lines 842-845
*   **Discrepancy Description:** The redirect-host and image-type checks live only in `Network.call`, which a fake transport skips.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid. It's resolved by the Issue 2 seam.
*   ***Proposed Edit:***
    *   Location: item 9 and item 14.
    *   Change: The redirect loop, the host check and the content-type check
        move into `fetch` (see Issue 2). `Network` only does I/O. The fake
        transport can now return a 302 to a disallowed host, or `text/html`,
        and the tests expect `IMAGE_REDIRECT_HOST` and `IMAGE_TYPE`.

### [Issue 9] [`implementation-readiness`] - Severity: Major
*   **Affected Scope:** Lines 782-784
*   **Discrepancy Description:** Experiments 2 and 3 change more than form fields.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid. The `app` header is hard-coded at `ProfileApi.java:44-45`.
*   ***Proposed Edit:***
    *   Location: item 9, experiments.
    *   Change: List each experiment's changes:
        *   **E1:** form `has_permission=1`.
        *   **E2:** form `service=oauth2:https://www.googleapis.com/auth/userinfo.profile`,
            plus `parse` checks the email only if the response has one, and
            no longer requires `email_verified`.
        *   **E3:** form `app=com.google.android.gms`,
            `callerPkg=com.google.android.gms`, `client_sig=` the GMS
            signature constant `PhotosApi.GMS_SIG`; header
            `app: com.google.android.gms`. User-Agent unchanged.

        Each experiment is a build made only for that test and never
        committed unless it works. E3 ships only with the author's approval.

### [Issue 10] [`implementation-readiness`] - Severity: Major
*   **Affected Scope:** Lines 739-748, 850-856
*   **Discrepancy Description:** The BootReceiver test can't run: protected broadcasts, a null `goAsync`, no permission grant, no cleanup, and an unspecified action check.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid.
*   ***Proposed Edit:***
    *   Location: items 7 and 15.
    *   Change:
        *   `BootReceiver` gets `static void handle(Context c, String action)`.
            It returns at once unless the action is `BOOT_COMPLETED` or
            `MY_PACKAGE_REPLACED`. Otherwise it calls `Scheduler.ensure`,
            then `ReclaimService.warnIfOnlyOnPhone`.
        *   `onReceive` calls it inside `goAsync` on a thread.
        *   The test calls `handle` directly.
        *   It grants `POST_NOTIFICATIONS` as in
            `BackupInstrumentedTest.java:104-111`.
        *   It seeds one `ReclaimEngine.State.DELETED` job through
            `ReclaimDb.enqueue` and `save`.
        *   It asserts that notification ID 7403 is active.
        *   It deletes the job and asserts notification ID 7403 is
            cancelled.
        *   In `@After`, it deletes `reclaim.db` rows created by the test.

### [Issue 11] [`implementation-readiness`] - Severity: Major
*   **Affected Scope:** Lines 735-737, 794-797, 847-848
*   **Discrepancy Description:** Outcome B removal is incomplete. There is no `Application` class, the flag would be wiped by `clear`, and the members, menu dispatch and tests aren't addressed.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid.
*   ***Proposed Edit:***
    *   Location: items 6, 10 and 14 (If Outcome B).
    *   Change: A keep/remove list:
        *   **ProfileStore:** keep `icon` and `clear`. Remove `refresh`,
            `decode`, `save`, `cached`, `file()` and `name`.
        *   **MainActivity:** remove `refreshProfile`, `profileIo`,
            `profileStop` and `profileLoading`, the `onResume` call and the
            "Refresh profile photo" option. The menu becomes a two-entry
            array, and the dispatch is rewritten by label index with
            constants.
        *   **Cleanup:** in `MainActivity.onCreate`, if the `settings`
            preference `profileRemoved` is false, call `ProfileStore.clear`
            and set it true with `commit()`. That flag lives in `Settings`,
            not `profile`, so `clear` can't wipe it.
        *   **Tests:** delete the network tests in `ProfileInstrumentedTest`.
            Keep `cachedPhotoAppearsWithoutTintInBothThemes`, changed to draw
            the initial-letter icon and still write
            `profile-light.png`/`profile-dark.png`.

### [Issue 12] [`implementation-readiness`] - Severity: Major
*   **Affected Scope:** Lines 847-848
*   **Discrepancy Description:** Deleting tests breaks the `tools/validate.py` pull of `profile-*.png`.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid. It's resolved by Issue 11 keeping that test and its files.
*   ***Proposed Edit:***
    *   Location: item 14.
    *   Change: Add a note that `tools/validate.py:46` depends on these files,
        which is why the test is kept.

### [Issue 13] [`implementation-readiness`] - Severity: Major
*   **Affected Scope:** Lines 717, 724-728
*   **Discrepancy Description:** `updateCard` is undeclared and not reset; `ui.card` returns the inner body; there is no tonal-button helper.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid. See `Ui.java:73-109`.
*   ***Proposed Edit:***
    *   Location: item 6.
    *   Change:
        *   Declare `private LinearLayout updateCard` and add it to the reset
            in `draw()`.
        *   Hide or show `(View) updateCard.getParent()`.
        *   `drawUpdateCard()` returns if `updateCard == null`.
        *   Buttons: "Download latest version" uses
            `ui.button(..., R.drawable.ic_open, true)` (filled). "What's new"
            and "Not now" use `ui.button(..., 0, false)`. `Ui.java` is not
            changed.

### [Issue 14] [`implementation-readiness`] - Severity: Major
*   **Affected Scope:** Line 650; Q3
*   **Discrepancy Description:** The card text depends on the unresolved Q3.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid. The sentence "If Android doesn't allow the install, keep using this version" is true whether or not Google enforces verification. It is not an install guide.
*   ***Proposed Edit:***
    *   Location: Technical Plan 1.3, Q3 and item 2.
    *   Change:
        *   The sentence ships unconditionally.
        *   Q3 is closed as "no design dependency". Checking whether Google
            enforces verification stays a release-note task.

### [Issue 15] [`implementation-readiness`] - Severity: Major
*   **Affected Scope:** Lines 733-734
*   **Discrepancy Description:** It's unclear whether automatic refreshes show the label.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Proposed Edit:***
    *   Location: item 6.
    *   Change: Add "The labelled message is shown only when `force` is
        true (manual refresh), the same as today (`MainActivity.java:1602-1606`)."

### [Issue 16] [`implementation-readiness`] - Severity: Major
*   **Affected Scope:** Lines 875-883
*   **Discrepancy Description:** The email regex would fail on the current tree, and case handling and the extension list are undefined.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Proposed Edit:***
    *   Location: item 18.
    *   Change:
        *   Regex `(?<![\w/@.:-])[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)+`.
            The look-behind skips `https://user@host`.
        *   Matching ignores case.
        *   Allowed:
            *   addresses ending in `@users.noreply.github.com` or
                `@example.com`, any case;
            *   addresses starting with `noreply@`.
        *   Text extensions: `.java .gradle .xml .md .py .sh .yml .yaml .json
            .properties .html .css .js .svg .txt .pro .kts`. Other files are
            skipped.
        *   A file with a text extension that isn't valid UTF-8 fails the
            scan.

### [Issue 17] [`implementation-readiness`] - Severity: Major
*   **Affected Scope:** Lines 919-931
*   **Discrepancy Description:** The public README line numbers are applied to the private README, and stale private lines are missed.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Proposed Edit:***
    *   Location: item 23.
    *   Change: Split it into 23a (public) and 23b (private), each giving the
        old text and the new text.
        *   **Public:**
            *   line 7: `**Android 13+ · MIT license**` (drop the version);
            *   lines 10-11: `latest` links;
            *   line 39: the update-check paragraph;
            *   line 153: drop the test count;
            *   a new "Verify the APK" line.
        *   **Private:**
            *   line 38: "back off for an hour" becomes "a day";
            *   lines 225-226: name `api.github.com`;
            *   lines 34-42: the profile text under Outcome B.

### [Issue 18] [`implementation-readiness`] - Severity: Major
*   **Affected Scope:** Lines 858-867
*   **Discrepancy Description:** The private and public `build-release.sh` are different scripts.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Proposed Edit:***
    *   Location: item 16.
    *   Change: Separate edits, both taking `RELEASE_TAG` as an environment
        variable.
        1.  Read versionName with
            `sed -n "s/.*versionName '\(.*\)'.*/\1/p" app/build.gradle`.
        2.  Refuse unless `RELEASE_TAG == "v$version"`.
        3.  After the build, copy to `dist/$version/Photos-Unlimited.apk`
            (no `v` in the folder name) and run
            `sha256sum Photos-Unlimited.apk > SHA256SUMS.txt` there.
        4.  Print `apksigner verify --print-certs`.

        The private script uses `/opt/android-sdk/build-tools/37.0.0/apksigner`.
        The public one uses `$ANDROID_HOME/build-tools/37.0.0/apksigner`.

### [Issue 19] [`implementation-readiness`] - Severity: Major
*   **Affected Scope:** Lines 869-873
*   **Discrepancy Description:** The `verify-release.py` item is vague.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Proposed Edit:***
    *   Location: item 17.
    *   Change:
        *   `OLD = <previous releases>/Photos-Unlimited-1.0.0.apk`,
            copied from the v1.0.0 release before the run.
        *   `APK = ROOT/'dist/1.1.0/Photos-Unlimited.apk'`.
        *   Keep the certificate-equality check against `OLD`.
        *   The instrumentation threshold changes from `>= 19` to the new
            count.

### [Issue 20] [`implementation-readiness`] - Severity: Major
*   **Affected Scope:** Lines 897-910
*   **Discrepancy Description:** CI leaves an either/or open, uses the wrong JDK, and has no SHAs.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid. The READMEs say JDK 21.
*   ***Proposed Edit:***
    *   Location: item 21.
    *   Change: Temurin **21**. Use the runner's preinstalled SDK plus
        `sdkmanager "platforms;android-37" "build-tools;37.0.0"`, with no
        third-party setup action. Only `actions/checkout` and
        `actions/setup-java`, pinned to the commit SHAs of their latest v4
        tags, resolved with `gh api` at implementation time and written in
        the file with a version comment. Tasks:
        `./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.

### [Issue 21] [`implementation-readiness`] - Severity: Major
*   **Affected Scope:** Lines 912-917, 945-952
*   **Discrepancy Description:** The PRIVACY.md summary line doesn't exist, and the profile changes are missing.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Proposed Edit:***
    *   Location: items 22 and 25.
    *   Change:
        *   PRIVACY.md lines 3-4 and 32: add GitHub to the list of services
            contacted.
        *   New GitHub section.
        *   "Profile photo" bullet (lines 26-30): Outcome A, unchanged
            unless E3 shipped, then name the Google Play services identity;
            Outcome B, remove it.
        *   Release notes: the same profile wording.

### [Issue 22] [`implementation-readiness`] - Severity: Major
*   **Affected Scope:** Lines 885-895
*   **Discrepancy Description:** Private vulnerability reporting is never turned on, and the URL isn't given.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Proposed Edit:***
    *   Location: item 19.
    *   Change:
        *   Step: `gh api -X PUT repos/venzellucas/photos-unlimited/private-vulnerability-reporting`.
        *   URL: `https://github.com/venzellucas/photos-unlimited/security/advisories/new`.
        *   One name throughout: "private vulnerability reporting".

### [Issue 23] [`implementation-readiness`] - Severity: Major
*   **Affected Scope:** Lines 933-943, 984-991, 1005-1009
*   **Discrepancy Description:** No publication order is given, so links can break, and the release command is incomplete.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Proposed Edit:***
    *   Location: new phase P7.
    *   Change: Fixed order:
        1.  Upload `Photos-Unlimited.apk` (a copy of 1.0.0) and a matching
            `SHA256SUMS.txt` to v1.0.0 with `gh release upload v1.0.0 ...
            --repo venzellucas/photos-unlimited --clobber`. This is done in
            P4, before any doc change, so `latest/download` works.
        2.  Push the public repo, including docs.
        3.  Run
            `gh release create v1.1.0 dist/1.1.0/Photos-Unlimited.apk dist/1.1.0/SHA256SUMS.txt --repo venzellucas/photos-unlimited --target <public commit SHA> --title "Photos Unlimited 1.1.0" --notes-file <notes>`,
            from the public repo after copying `dist/1.1.0`
            there (the copy is not committed).
        4.  Check with `curl -sI`.

### [Issue 24] [`style`] - Severity: Major
*   **Affected Scope:** Lines 666, 698, 769, 782-784, 888, 893, 916
*   **Discrepancy Description:** Cross-references use five formats and some point to sections that don't exist.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Proposed Edit:***
    *   Location: throughout.
    *   Change: One format, "Technical Plan § 1.1 (Request)", plus "E1-E3"
        for the experiments.

### [Issue 25] [`style`] - Severity: Major
*   **Affected Scope:** Lines 933-943
*   **Discrepancy Description:** The `index.html` item has wrong line references and leaves text out.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Proposed Edit:***
    *   Location: item 24.
    *   Change: Old and new text per line:
        *   41 badge: "Latest release ↗" to `releases/latest`;
        *   44: APK `latest/download`;
        *   45: `Android 13+ · Free & open source`;
        *   92: link plus `download-meta` "Android 13+";
        *   93: "The app tells you when a new version is out." plus
            `latest/download/SHA256SUMS.txt`;
        *   97: remove the "77 automated tests" figure;
        *   98: the FAQ privacy answer gains "and GitHub, once a day, to
            check for updates (can be turned off)";
        *   99: replace "version 1.0.0".

### [Issue 26] [`style`] - Severity: Major
*   **Affected Scope:** Lines 828, 971, 979, 992, 998
*   **Discrepancy Description:** Test environments are named but never defined.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Proposed Edit:***
    *   Location: start of the Verification Plan.
    *   Change: Define:
        *   **Build host:** A dedicated Linux container, user `builder`,
            with its own checkout.
        *   **Test emulator:** AVD `photos-api37`, serial `emulator-5560`,
            not signed in to Google. All instrumentation runs here, with
            `adb -s emulator-5560 shell am instrument -w -e notClass app.photos.unlimited.LiveReclaimProbe app.photos.unlimited.test/androidx.test.runner.AndroidJUnitRunner`.
        *   **Reference emulator:** serial `emulator-5554`, signed in to the
            spare Google account. Only `LiveReclaimProbe` and manual taps.

        One emulator runs at a time.

### [Issues 27-51] Nits

| # | Finding | Suggestion | Proposed edit |
| --- | --- | --- | --- |
| 27 | IR-1-24 types/keys undeclared | PROPOSE FIX | Declare `sealed`-style `Result(kind LATEST/UNCHANGED/FAILED, String version, String etag)`. Keys: `updateCheck`, `updateLastAttempt` (long), `updateLatest`, `updateEtag`, `updateDismissed` (String), all camelCase like existing keys. |
| 28 | IR-1-25 double redraw | PROPOSE FIX | `onDone` is the only redraw path. It runs on the UI thread through `runOnUiThread` and checks `screen == SCREEN_BACKUP` (a new constant, value 1). It isn't called when the check isn't due. |
| 29 | IR-1-26 `commit()` | PROPOSE FIX | `lastAttempt` is written with `commit()`. |
| 30 | IR-1-27 parser expectations | PROPOSE FIX | Leading zeros are accepted (`v01.2.3` = 1.2.3). Whitespace is rejected. Missing `draft` or `prerelease` = false. Non-string `tag_name` is rejected (`instanceof String`). Fixture `releases_latest.json`, trimmed from a real response, in `androidTest/assets`. |
| 31 | IR-1-28 `-dev` case | DECLINE | The case tests "any non-`X.Y.Z` name hides the card", a defensive rule. Keep it, renamed `installed name "1.1.0-rc1"`. |
| 32 | IR-1-29 signature drift | PROPOSE FIX | Item 4 is the authoritative version. § 1.2 and the diagram are aligned to 6 parameters. |
| 33 | IR-1-30 inspecting state | PROPOSE FIX | The test emulator is a `google_apis` image, so `adb root` works. Read `shared_prefs/settings.xml` and seed `reclaim.db` with `sqlite3`. |
| 34 | IR-1-31 string names and escaping | PROPOSE FIX | List `update_title`, `update_body`, `update_blocked_body`, `update_download`, `update_whats_new`, `update_not_now`, `update_open_free_up`, `update_setting`, `update_setting_summary`, `no_browser`, `profile_failed`. Apostrophes escaped as `\'`. Item 6 reads them with `getString`. |
| 35 | IR-1-32 issue form fields | PROPOSE FIX | `input` app_version (required), `input` android (required), `textarea` what_happened (required), `textarea` expected, `input` diagnostic, `checkboxes` privacy (required). |
| 36 | IR-1-33 verification commands | PROPOSE FIX | Lint baseline = the 1.0.0 result (0 errors). Real paths. The instrumentation filter is as in Issue 26. |
| 37 | CD-1-1 reuse helpers | PROPOSE FIX | `openUrl` uses `message()` and the `ActivityNotFoundException` pattern at `MainActivity.java:254-257`. Tighten the verbs. |
| 38 | CD-1-2 AUTH timeouts | PROPOSE FIX | Connect 30 s, read 60 s, `Accept-Encoding: identity`, fixed-length body: the same as `PhotosApi.Network`. |
| 39 | CD-1-3 header count | PROPOSE FIX | Test both: 3 headers with no ETag, 4 with one. |
| 40 | CD-1-4 design-system lines | PROPOSE FIX | Replace `design-system.md:174` ("Reclamation actions are not part of this release"). Add the toggle to the `:67` Settings row. |
| 41 | CD-1-5 assembleRelease without a keystore | PROPOSE FIX | The release APK is built only through `build-release.sh`. The Verification Plan uses `assembleDebug` elsewhere. |
| 42 | style-1-4 time units | PROPOSE FIX | Write `24 * 60 * 60 * 1000L` ms, and say that `now` is `System.currentTimeMillis()`. |
| 43 | style-1-5 timeouts | PROPOSE FIX | "Connect 10 s, read 15 s". |
| 44 | style-1-6 duplicate, `""` | PROPOSE FIX | Remove the duplicate from item 6. Note that the account argument is unused and kept for source compatibility. |
| 45 | style-1-7 "now"/"today" | PROPOSE FIX | Use "currently in `onCreate`" and "the request `fetch` sends in 1.0.0". |
| 46 | style-1-8 "scratch build", voice | PROPOSE FIX | Use "a test build made only for that experiment", and address the reader as "the author" consistently. |
| 47 | style-1-9 test class name | PROPOSE FIX | Rename to `UpdateLogicTest`. "Each of these cases". Plain-English stub note citing `build.gradle:31`. |
| 48 | style-1-10 "each label" | PROPOSE FIX | "For these labels", with the expected label beside each case. |
| 49 | style-1-11 notification wording | PROPOSE FIX | "Notification ID 7403", `ReclaimEngine.State.DELETED`, explicit subjects. |
| 50 | style-1-12 small wording | DECLINE (partial) | Fix "Both files" and the comma splice. "About 20 lines" and "That's" aren't worth an edit. |
| 51 | style-1-13 live-check order | PROPOSE FIX | Order the checks: the asset upload first; install 1.0.9 by uninstalling then installing (`versionCode 7`, `versionName '1.0.9'`); the update-available precondition for the blocked check; restate the § 3.3 criterion; the upgrade check runs on the reference emulator. |

Declined nits: 31, 50 (part), plus the "E2E" label and "About 20 lines"
wording, which are kept as they are.
