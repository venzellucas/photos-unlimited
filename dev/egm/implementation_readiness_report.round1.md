# Goldfish Review Report

**Target:** `docs/designs/photos-unlimited-1.1.md` (`631-1015`, "Detailed Implementation Plan" to end of file)

**Stage:** 9, Implementation Readiness Test

## Reviewer Status

| Reviewer | Status |
| --- | --- |
| `egm-implementation-readiness` | completed |
| `egm-codebase-details` | completed |
| `egm-style` | completed |

## Summary

| Severity | Count |
| --- | --- |
| 🔴 Blocker | 2 |
| 🟡 Major | 24 |
| 🟢 Nit | 25 |

Verdict: **not ready to implement.** All three reviewers found two code-level contradictions in the profile-diagnosis work (Part 3). An implementer would either get stuck or build the wrong thing. Most Majors fall into four groups:

1. No phases, and no decision point for Outcome A vs. Outcome B.
2. The update card can offer Download when it should not, and the update check runs on the shared UI-data executor.
3. Planned tests cannot be written with the seams the plan gives.
4. Line and text references in the docs, scripts and public-repo items don't match the real files.

How findings were merged: when two or more reviewers reported the same problem, they appear as one finding. It keeps the id of the most complete report, and the other reviewers are listed under "Also raised by". When reviewers gave different severities, the higher one is used. I checked the code facts behind both Blockers against the source: `ProfileApi.java:19`, `:91-93`, `PhotosApi.java:34-35`, `:55`, the design doc line 382, and `MainActivity.java:24-25`.

Several things in the plan were confirmed correct and need no change:

- `versionCode 6` → `7` (`build.gradle:9-10`).
- No `strings.xml` exists, so marking it Create is right.
- Manifest: `BootReceiver` already handles both actions, and no new permission is needed.
- Channel creation is at `ReclaimService.java:47-52`.
- `page.addView(counts)` is at `MainActivity.java:371`, the "Account and app" card is at `:599-607`, and "Refresh profile photo" is at `:1545`.
- `ProfileStore.java:61` sets the one-hour retry.
- There are exactly 57 JVM tests.
- The `<queries>` reasoning in item 11 holds.
- The public README lines 7-11 and 39, and the public `docs/index.html` lines 41, 44, 92 and 93, hold the v1.0.0 text the plan names.

---

## Prioritized Findings (Blockers -> Major -> Nits)

### 🔴 Blockers

### implementation-readiness-1-1: The `Failure` label check rejects the plan's own `AUTH_ERROR_*` labels - Severity: Blocker
* **Reviewer Lens:** `egm-implementation-readiness`. Also raised by `egm-codebase-details` (B2) and `egm-style` (M4).
* **Affected Scope:** Lines 764-766, 777-778, 842-843. Context: the table at line 382.
* **Discrepancy Description**: The `Failure` constructor accepts only `[A-Z0-9_]{1,48}`. But `AUTH_ERROR_<value>` keeps Google's `Error=` value "cut to `[A-Za-z]{1,40}`" (line 382). That allows lowercase letters and labels up to 51 characters. The planned test case `Error=BadAuthentication` gives `AUTH_ERROR_BadAuthentication`, so the constructor throws on exactly the case the test checks.
* **Rationale & Recommendation**: In `refreshProfile` that exception would be treated as "any other exception" and shown as `NETWORK`. The most important diagnostic label would be lost in the live run that Part 3.3 depends on. Choose one fix and state it in item 9 and in the 3.1 table:
  - uppercase the value;
  - cut the value to 37 characters;
  - or widen the regex and length limit.

### implementation-readiness-1-2: The token request's HTTP status has no path to `fetch`, and the tests can't fake it - Severity: Blocker
* **Reviewer Lens:** `egm-implementation-readiness`. Also raised by `egm-codebase-details` (B1) and `egm-style` (M3).
* **Affected Scope:** Lines 770-781, 842-843.
* **Discrepancy Description**: `ProfileApi.Network implements PhotosApi.Transport` (`ProfileApi.java:91`). Its `call` must return `PhotosApi.Response(byte[] bytes, Map headers)` (`PhotosApi.java:34-35, 55`), which has no status field. `ProfileApi.fetch(String, PhotosApi.Transport, AtomicBoolean)` (`ProfileApi.java:19`) only sees that interface. So `Network.call` cannot return the proposed `record AuthResponse(int status, byte[] body)` to `fetch` without a new interface or a new signature, and the plan names neither. The existing fake transport (`ProfileInstrumentedTest.java:48-64`) returns `PhotosApi.Response` with no status, so the "auth 403 with `Error=BadAuthentication`" test can't be written as specified. The sentence also states the rejected option (changing `PhotosApi.Response`) before the decision.
* **Rationale & Recommendation**: The implementer has to design the transport seam. Specify the exact interface or method signature, for example a `ProfileApi.Transport` interface or a separate `static AuthResponse auth(...)`. Say how `fetch` receives the result and how tests inject a status, and delete the rejected alternative. Possible reuse: `PhotosApi.ApiException` already carries `httpStatus` (`PhotosApi.java:65, 675-683`), but not the error body.

---

### 🟡 Major

### implementation-readiness-1-3: No phases or checkpoints, and no decision point between Outcome A and Outcome B - Severity: Major
* **Reviewer Lens:** `egm-implementation-readiness`. Also raised by `egm-style` (M2).
* **Affected Scope:** Lines 638-967. The Outcome branches are at 735-737, 785, 794-797, 842-848 and 928-929. Live check 4 is at 998.
* **Discrepancy Description**: The plan is a flat list of 27 files. The standard requires phases, each of which leaves the build stable and testable. Order matters: item 6 uses `UpdateCheck`, `UpdateNotice`, `ProfileApi.Failure` and the new return value of `warnIfOnlyOnPhone`. There is also a real gate in the middle of the work: ship the diagnostic labels, run live check 4 and experiments 3.3/3.4, then choose Outcome A or B. "Outcome A" and "Outcome B" are never defined in this range, and their labels are written three different ways.
* **Rationale & Recommendation**: An implementer working top to bottom can't tell whether to build A, B, or both. Define both outcomes at line 633, with a link to Part 3.5/3.6. Split the work into phases with a build/test checkpoint after each, and place the diagnosis gate explicitly. Use one label format for the outcome branches.

### implementation-readiness-1-4: "Copied the same way as for 1.0.0" is not documented anywhere - Severity: Major
* **Reviewer Lens:** `egm-implementation-readiness`. Also raised by `egm-style` (M14) and `egm-codebase-details` (nit).
* **Affected Scope:** Lines 633-636, 963-967.
* **Discrepancy Description**: The repo has no sync script and the plan gives no file list or exclusions. Item 27 copies "items 1-15" with a `public:app/**` glob, even though item 16 is mirrored separately and differently (see implementation-readiness-1-18).
* **Rationale & Recommendation**: An implementer without the author can't run this step. Give the copy command or a procedure, and list the exact files instead of a glob.

### implementation-readiness-1-5: The update card can offer Download while items are only on the phone - Severity: Major
* **Reviewer Lens:** `egm-implementation-readiness`. Also raised by `egm-codebase-details` (M2).
* **Affected Scope:** Lines 717-727. Context: lines 323-325 and 622-623.
* **Discrepancy Description**: `unsafeCount` starts at 0. On a cold start, `drawBackup()` → `drawUpdateCard()` runs from `onCreate` (`MainActivity.java:151`) before the `onResume` `io` task has read `unsafeAll()`. A version stored from an earlier launch then shows AVAILABLE with a Download button. The count is refreshed only in `onResume`, so after a Free up run is paused, `busy()` is false (`ReclaimService.java:276`) and the stale count can bring the button back.
* **Rationale & Recommendation**: This breaks G4 and the data-safety NFR ("never offered"). Add an "unknown" value, for example -1, treated as BLOCKED or HIDDEN, and refresh the count on every Backup draw. `unfinishedReclaim()` (`MainActivity.java:1517-1521`) already reads `unsafeAll()` and can be reused.

### implementation-readiness-1-6: The update check runs on the shared single-thread `io` executor - Severity: Major
* **Reviewer Lens:** `egm-implementation-readiness`. Also raised by `egm-codebase-details` (M1).
* **Affected Scope:** Lines 717-723.
* **Discrepancy Description**: `io` is `Executors.newSingleThreadExecutor()` (`MainActivity.java:24`). It also runs `refresh()` on a 1.5 s ticker (`:65-71, 657-738`), `chooseFolders`, `about` and `ReclaimRecovery.index`. A slow GitHub request (10 s connect plus 15 s read) would block the backup counts and folder selection for up to about 25 s.
* **Rationale & Recommendation**: This works against G1's "never changes backup status" intent. Run the network step on a dedicated executor or on the existing `profileIo` (`MainActivity.java:25`). If you keep `io`, say that the delay is accepted.

### implementation-readiness-1-7: The failure-label mapping is incomplete, and `TIMEOUT` can never be produced - Severity: Major
* **Reviewer Lens:** `egm-implementation-readiness`. Also raised by `egm-codebase-details` (M4) and `egm-style` (M5).
* **Affected Scope:** Lines 733-734, 767-769, 845. Context: 383-385.
* **Discrepancy Description**:
  - "`imageAddress` callers" is unclear: `imageAddress` returns a boolean, and `parse` is its only caller.
  - `IMAGE_DECODE` and `IMAGE_TOO_LARGE` come from `ProfileStore.decode` (`ProfileStore.java:86, 92`), and `ProfileStore.save` throws a plain `IOException` (`:110`). Neither file is in scope for the rewrite, so these errors are labelled `NETWORK`.
  - The single combined throw at `ProfileApi.java:61-64` must be split into `PROFILE_FIELDS_MISSING` and `PROFILE_EMAIL_MISMATCH`.
  - Several throws have no label: "Unrecognized profile request" (`:108`), userinfo "too large" (`:136, :143`), "Too many profile redirects" (`:152`), and `PhotosApi.parseForm` (`PhotosApi.java:560`).
  - `SocketTimeoutException` is never mapped to `TIMEOUT`. It is also an `InterruptedIOException`, the same type user cancellation throws (`PhotosApi.java:585`).
  - The plan doesn't say which label wins when a 403 also carries `Error=`.
* **Rationale & Recommendation**: The implementer has to map about 11 throw sites to about 19 labels without guidance. Add a table from throw site to label that covers `ProfileStore.decode` and `save`, the timeout and cancellation split, and which label takes precedence.

### implementation-readiness-1-8: Some Outcome A tests can't reach the code they test - Severity: Major
* **Reviewer Lens:** `egm-implementation-readiness`. Also raised by `egm-codebase-details` (M5).
* **Affected Scope:** Lines 842-845.
* **Discrepancy Description**: "Image redirect to a disallowed host" and "non-image type" are checked only inside `ProfileApi.Network.call` (`ProfileApi.java:111-134`). That code opens real HTTPS connections to Google hosts. A fake `PhotosApi.Transport` skips it. A mock server is ruled out by "no new dependencies", and the host allowlist blocks localhost.
* **Rationale & Recommendation**: Move those checks into pure functions and test those, or say how the tests reach them.

### implementation-readiness-1-9: Experiments 2 and 3 change more than form fields - Severity: Major
* **Reviewer Lens:** `egm-implementation-readiness`
* **Affected Scope:** Lines 782-784. Context: 420-426.
* **Discrepancy Description**: Experiment 2 also changes `parse` so that it compares the userinfo `email` only when one is present (`ProfileApi.java:63`). Experiment 3 sets `app=com.google.android.gms`, but the request header `app` is hard-coded to `com.google.android.apps.photos` (`ProfileApi.java:44-45`). The plan doesn't say whether `callerPkg` and `callerSig` change too.
* **Rationale & Recommendation**: "Change only the form fields" is wrong for these two experiments. List every code change each experiment needs.

### implementation-readiness-1-10: The BootReceiver test can't be run as described - Severity: Major
* **Reviewer Lens:** `egm-implementation-readiness`. Also raised by `egm-codebase-details` (M7) and `egm-style` (N11).
* **Affected Scope:** Lines 739-748, 850-856.
* **Discrepancy Description**:
  - `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` are protected broadcasts, so the test can't send them.
  - Calling `onReceive` directly makes `goAsync()` return null, so `r.finish()` throws a NullPointerException.
  - Checking notification 7403 needs `POST_NOTIFICATIONS` granted. The existing pattern is at `BackupInstrumentedTest.java:104-111`.
  - No `reclaim.db` cleanup is specified, so the seeded row would leak into later tests.
  - "Acts only for" the two actions doesn't say whether to add an action check. Today `BootReceiver.java:7-9` checks none.
* **Rationale & Recommendation**: Specify a testable entry point, for example a static `BootReceiver.handle(Context)` that doesn't use `goAsync`. Also specify the permission grant, the database cleanup, and whether to add an action check. `ReclaimEngine.Job.of(...)` (`ReclaimEngineTest.java:216`) can seed the row.

### implementation-readiness-1-11: Outcome B removal is incomplete, and its one-time cleanup may never stop running - Severity: Major
* **Reviewer Lens:** `egm-implementation-readiness`. Also raised by `egm-codebase-details` (M6) and `egm-style` (M6, M7, N17).
* **Affected Scope:** Lines 735-737, 794-797, 847-848.
* **Discrepancy Description**:
  - "In `Application` start or `MainActivity.onCreate`" offers a choice, but the app has no `Application` subclass (`AndroidManifest.xml:19`).
  - The preferences file for `profile_removed` isn't named. If the flag lives in `profile`, `ProfileStore.clear` wipes it (`ProfileStore.java:46`), and `AccountStore` calls `clear` on every account change (`AccountStore.java:40, 71`).
  - `cached`, `save` and `file()` are neither kept nor removed. "`name` (always returns empty)" doesn't say whether that is a change to make.
  - `refreshProfile`, `profileIo`, `profileStop`, `profileLoading` and the `onResume` call (`MainActivity.java:184, 1578-1591`) aren't addressed.
  - Removing the menu item shifts the `which == 1/2/3` dispatch (`:1549-1553`).
  - "Keep a test that the initial-letter icon is drawn" refers to a test that doesn't exist. Three non-network tests (`ProfileInstrumentedTest.java:100-189`) would break or fail to compile.
* **Rationale & Recommendation**: List every member as keep, remove or change. Name `MainActivity.onCreate` and a preferences file that survives `clear`. Address the menu dispatch, and say which tests to delete, keep or add.

### implementation-readiness-1-12: Deleting the Outcome B tests breaks `tools/validate.py` - Severity: Major
* **Reviewer Lens:** `egm-implementation-readiness`
* **Affected Scope:** Lines 847-848.
* **Discrepancy Description**: `tools/validate.py:46` pulls `profile-light.png` and `profile-dark.png`, which `cachedPhotoAppearsWithoutTintInBothThemes` writes (`ProfileInstrumentedTest.java:136-189`). If that test is removed, the `adb pull` with `check=True` fails. `validate.py` is not in the file list.
* **Rationale & Recommendation**: Add `tools/validate.py` to the Outcome B changes, or keep that test.

### implementation-readiness-1-13: The update card's UI wiring doesn't match the `Ui` helpers - Severity: Major
* **Reviewer Lens:** `egm-implementation-readiness`. Also raised by `egm-codebase-details` (M3) and `egm-style` (M8).
* **Affected Scope:** Lines 717, 724-728. Context: 310-313.
* **Discrepancy Description**:
  - The `updateCard` field is never declared and isn't added to the per-screen reset in `draw()` (`MainActivity.java:318-329`).
  - `drawUpdateCard()` is also called after the Settings toggle, on a screen where the card doesn't exist, and no null check is specified.
  - `ui.card()` returns the inner body, not the `MaterialCardView` (`Ui.java:73-88`). Hiding that body for HIDDEN leaves an empty card shell and a 16 dp margin.
  - "Tonal" buttons have no helper: `Ui.button` supports only filled or borderless (`Ui.java:91-109`), and `Ui.java` is not in the file list.
* **Rationale & Recommendation**: Declare and reset the field, and hide the parent card. Add a null check for screens without the card. Either add `Ui.java` with a tonal-button method or choose an existing style.

### implementation-readiness-1-14: The card text depends on an unresolved open question - Severity: Major
* **Reviewer Lens:** `egm-implementation-readiness`
* **Affected Scope:** Line 650. Context: 306-308, Q3.
* **Discrepancy Description**: The sentence "If Android doesn't allow the install, keep using this version" depends on Q3, which is "to be checked before the release". Item 2 doesn't say whether the sentence goes into `strings.xml`, or who decides and when.
* **Rationale & Recommendation**: Resolve Q3, or make the string conditional and give a point at which it is decided.

### implementation-readiness-1-15: It's unclear whether automatic profile refreshes show the failure label - Severity: Major
* **Reviewer Lens:** `egm-implementation-readiness`. Also raised by `egm-codebase-details` (nit).
* **Affected Scope:** Lines 733-734.
* **Discrepancy Description**: Today a message appears only when `force` is true (`MainActivity.java:1602-1606`). `onResume` calls `refreshProfile(false)`. The plan doesn't say whether the labelled message also shows on that automatic call, which could mean a daily toast.
* **Rationale & Recommendation**: State that the label is shown only on a manual refresh, or state the alternative.

### implementation-readiness-1-16: The `check-publication.py` changes would fail on the current public tree - Severity: Major
* **Reviewer Lens:** `egm-implementation-readiness`. Also raised by `egm-codebase-details` (M9) and `egm-style` (N20).
* **Affected Scope:** Lines 875-883.
* **Discrepancy Description**: A plain email regex matches `https://user@lh3.googleusercontent.com/a` (public `ProfileInstrumentedTest.java:93`) and `Me@Example.com` (`ReclaimEngineTest.java:554`). Neither is covered by the allowlist as written, and the plan doesn't say whether matching ignores case. "Files with text extensions" isn't defined; the tree has `.png`, `.bin`, `.jar` and `.svg` files. The allowlist is written in three different notations.
* **Rationale & Recommendation**: Give the exact regex, the case handling, the extension list, and how the test URL is exempted.

### implementation-readiness-1-17: The README item uses public-README line numbers for both READMEs - Severity: Major
* **Reviewer Lens:** `egm-implementation-readiness`. Also raised by `egm-codebase-details` (M10) and `egm-style` (M11).
* **Affected Scope:** Lines 919-931.
* **Discrepancy Description**: "Lines 7-11" and "line 39" match only `public:README.md`. The private README has neither, and its own lines would become false:
  - line 38, "failures back off for an hour";
  - lines 225-226, "only contacts fixed Google endpoints";
  - lines 34-42, the profile text, under Outcome B.

  Public line 7 is plain text ("**v1.0.0 · Android 13+ · MIT license**"), not a link, and no new text is given for it. Public line 153 claims "57 JVM tests".
* **Rationale & Recommendation**: Split the instructions by file, and give the old and new text for each change.

### implementation-readiness-1-18: The private and public `build-release.sh` are different scripts, not mirrors - Severity: Major
* **Reviewer Lens:** `egm-implementation-readiness`. Also raised by `egm-codebase-details` (M8) and `egm-style` (N19).
* **Affected Scope:** Lines 858-867.
* **Discrepancy Description**:
  - The private script hard-codes `~/...` and creates a keystore (lines 4-21). The public one requires `PHOTOS_KEYSTORE` and `PHOTOS_SIGN_PASSWORD` (lines 3-5). Copying the private script would trip check-publication's "local workstation path" pattern (`check-publication.py:9`).
  - `RELEASE_TAG` isn't defined as an environment variable or an argument.
  - The plan doesn't say how versionName is read, whether `dist/<version>` has a `v` prefix, or where `apksigner` lives. `verify-release.py:17` pins `build-tools/37.0.0`, and its `certificate()` (`:51-53`) can be reused.
* **Rationale & Recommendation**: Give separate edits for each script, and fill in these details.

### implementation-readiness-1-19: The `verify-release.py` item is too vague to implement - Severity: Major
* **Reviewer Lens:** `egm-implementation-readiness`. Also raised by `egm-codebase-details` (M13) and `egm-style` (M9).
* **Affected Scope:** Lines 869-873.
* **Discrepancy Description**: The plan gives no path for the new `OLD` baseline; today it is `<previous releases>/Photos-Unlimited-0.2.0.apk` (`verify-release.py:16`). "Look for the fixed asset name" matches no code: the script reads only `app-release.apk` (`:15`).
* **Rationale & Recommendation**: Give the new `OLD` path, and say whether `APK` should point to `dist/1.1.0/Photos-Unlimited.apk`.

### implementation-readiness-1-20: The CI workflow leaves choices open and uses the wrong JDK - Severity: Major
* **Reviewer Lens:** `egm-implementation-readiness`. Also raised by `egm-codebase-details` (M12) and `egm-style` (N21).
* **Affected Scope:** Lines 897-910.
* **Discrepancy Description**: "`android-actions/setup-android`, or the runner's preinstalled SDK" is an open either/or. The pinned SHAs and the build-tools version are not given; the tools use 37.0.0. Temurin 17 contradicts both READMEs ("Use JDK 21": public README:130, private README:240) and the toolchain (AGP 9.2.1, Gradle 9.6.1). The Gradle tasks omit `:app:`, unlike the Verification Plan.
* **Rationale & Recommendation**: Choose one setup, use JDK 21, and list the SDK packages and the pinned SHAs.

### implementation-readiness-1-21: The PRIVACY.md item points to a line that doesn't exist and leaves out the profile changes - Severity: Major
* **Reviewer Lens:** `egm-implementation-readiness`. Also raised by `egm-codebase-details` (M11) and `egm-style` (M10).
* **Affected Scope:** Lines 912-917, 945-952.
* **Discrepancy Description**: `public:PRIVACY.md` has no "contacts only Google" summary line. The closest are lines 3-4 and 32. The "Profile photo" bullet (lines 26-30) and lines 33-34 must change under Outcome B, and under experiment 3 (context line 426), but neither is listed. The release notes have the same gap.
* **Rationale & Recommendation**: Quote the target line, or drop that bullet. Add the conditional profile-photo wording for PRIVACY.md and the release notes.

### implementation-readiness-1-22: Private vulnerability reporting is never turned on - Severity: Major
* **Reviewer Lens:** `egm-implementation-readiness`
* **Affected Scope:** Lines 885-895.
* **Discrepancy Description**: SECURITY.md and the `config.yml` contact link point to GitHub's private vulnerability reporting form. No step turns that setting on, for example `gh api -X PUT repos/venzellucas/photos-unlimited/private-vulnerability-reporting`, and the contact-link URL isn't given. "Security advisory form" is a third name for the same feature.
* **Rationale & Recommendation**: Without the setting, the link leads nowhere. Add the step, give the URL, and use one name for the feature.

### implementation-readiness-1-23: Publication order is not defined, so download links can break - Severity: Major
* **Reviewer Lens:** `egm-implementation-readiness`
* **Affected Scope:** Lines 933-943, 984-991, 1005-1009.
* **Discrepancy Description**: The README and site links change to `releases/latest/download/Photos-Unlimited.apk`. If the public repo and Pages are pushed before v1.1.0 exists with that asset, every link breaks. The v1.0.0 asset upload is presented as a test aid, not as a publication prerequisite. The `gh release create` command is missing the asset paths, the notes file, `--repo`, and where it runs.
* **Rationale & Recommendation**: Give the order of steps, with the asset upload as a precondition, and the full release command.

### style-1-1: Cross-references use five formats, and some point to sections that don't exist - Severity: Major
* **Reviewer Lens:** `egm-style`
* **Affected Scope:** Lines 666, 698, 769, 782-784, 888, 893, 916.
* **Discrepancy Description**:
  - "Builds the headers in 1.1" (666) collides with the release number 1.1.
  - The other references switch between "Technical Plan 1.2", "Technical Plan Part 4" and "Technical Plan 'Docs and privacy text'".
  - "Experiments 3.4.1-3.4.3" (782) points to subsections that don't exist: 3.4 is a plain list numbered 1-3. Line 784 then says "Experiment 3".
* **Rationale & Recommendation**: Use one format that includes the full heading, for example "Technical Plan > Part 1 > 1.1 (Request)" or "3.4 item 3".

### style-1-2: The `docs/index.html` item has wrong line references and leaves text out - Severity: Major
* **Reviewer Lens:** `egm-style`. Also raised by `egm-implementation-readiness` (N8) and `egm-codebase-details` (nit). Those two rated it Nit; the higher rating is kept.
* **Affected Scope:** Lines 933-943.
* **Discrepancy Description**:
  - Line 45 has no version number, only "3.5 MB".
  - The line 41 badge "v1.0.0 · Release" isn't addressed.
  - The item doesn't say which new link replaces which old one (lines 41, 44, 92, 93).
  - "Update the FAQ privacy answer" gives no new text.
  - The "77 automated tests" and "version 1.0.0" FAQ text (lines 97 and 99) isn't listed, though Technical Plan line 485 requires removing test counts.
* **Rationale & Recommendation**: List each line with its old and new text.

### style-1-3: Test environments are named but never defined - Severity: Major
* **Reviewer Lens:** `egm-style`. Also raised by `egm-implementation-readiness` (N9) and `egm-codebase-details` (nit).
* **Affected Scope:** Lines 828, 971, 979, 992, 998.
* **Discrepancy Description**: "the build host" isn't defined in the repo. "photos-api37 emulator", "reference emulator" and "the test emulator" are three names for at most two emulators. The plan gives no instrumentation command; the existing route is `tools/validate.py` or `adb -s emulator-5560 shell am instrument ...` (private README:249-256).
* **Rationale & Recommendation**: Define each environment once at the top of the Verification Plan: purpose, whether it's signed in, and its AVD or serial. Use one name for each, and give the commands.

---

### 🟢 Nits

### implementation-readiness-1-24: Types and key names are not declared - Severity: Nit
* **Reviewer Lens:** `egm-implementation-readiness`. Also raised by `egm-style` (N1, N6) and `egm-codebase-details` (nit).
* **Affected Scope:** Lines 666-670, 708-711.
* **Discrepancy Description**: The `Result` type is never declared. The Settings setter types aren't given. Only an `update_` prefix is given, not the exact key names; existing keys are camelCase with no prefix. The field names at line 710 differ from the accessor names. Clearing `dismissed` isn't mentioned in the Technical Plan context at lines 335-338.
* **Rationale & Recommendation**: Declare `Result`, give each setter's type and exact key string, and use one set of names.

### implementation-readiness-1-25: `runIfDue`'s `onDone` and step 3 both redraw the card - Severity: Nit
* **Reviewer Lens:** `egm-implementation-readiness`. Also raised by `egm-style` (N4, N9).
* **Affected Scope:** Lines 686-690, 722-723.
* **Discrepancy Description**: The card is redrawn twice. The plan doesn't say whether `onDone` runs when the check isn't due. `screen == 1` is a magic number, must be read on the UI thread, and the plan doesn't say what "stores the result" does for the unchanged and failed outcomes. It also doesn't say that a debuggable build skips the check.
* **Rationale & Recommendation**: Make a single redraw path, and name the Settings setters it calls.

### implementation-readiness-1-26: `lastAttempt` must be written with `commit()` - Severity: Nit
* **Reviewer Lens:** `egm-implementation-readiness`. Also raised by `egm-codebase-details` (nit).
* **Affected Scope:** Lines 689, 708.
* **Discrepancy Description**: Settings uses both `apply()` and `commit()` (`Settings.java:30` vs `:44`). If `lastAttempt` is written with `apply()`, a crash can lose it, which defeats the crash-safety goal (context lines 272-273).
* **Rationale & Recommendation**: Specify `commit()` for this write.

### implementation-readiness-1-27: Parser test cases have no expected results - Severity: Nit
* **Reviewer Lens:** `egm-implementation-readiness`. Also raised by `egm-style` (N14, N15).
* **Affected Scope:** Lines 812-813, 829-834.
* **Discrepancy Description**: The plan doesn't say whether leading zeros or whitespace are accepted, or whether a release with no `draft` or `prerelease` field is accepted (`optBoolean` defaults to false). A numeric `tag_name` needs an `instanceof String` check, because `optString` converts numbers. "Real-shaped GitHub response" is vague.
* **Rationale & Recommendation**: Give the expected result for each input, and name a fixture.

### implementation-readiness-1-28: The `1.1.0-dev` test case matches no real build - Severity: Nit
* **Reviewer Lens:** `egm-implementation-readiness`
* **Affected Scope:** Line 818.
* **Discrepancy Description**: `build.gradle` sets no `-dev` suffix, and debug builds also get `1.1.0`.
* **Rationale & Recommendation**: Explain why the case is tested, or add the suffix to debug builds.

### implementation-readiness-1-29: `UpdateNotice.state(...)` has three different signatures in the document - Severity: Nit
* **Reviewer Lens:** `egm-implementation-readiness`. Also raised by `egm-codebase-details` and `egm-style` (N5).
* **Affected Scope:** Lines 695-697. Context: 217 and 282.
* **Discrepancy Description**: The signature has 6 parameters here, 5 in Part 1.2, and 4 in the diagram.
* **Rationale & Recommendation**: Align the three, or mark item 4 as the authoritative version.

### implementation-readiness-1-30: No method is given for inspecting state or seeding data on the test device - Severity: Nit
* **Reviewer Lens:** `egm-implementation-readiness`. Also raised by `egm-codebase-details`.
* **Affected Scope:** Lines 984-993.
* **Discrepancy Description**: Reading `lastAttempt` and the ETag from a non-debuggable release build needs root, because `run-as` doesn't work. Seeding a DELETED journal row has no method either.
* **Rationale & Recommendation**: Give the method, for example `adb root` plus `sqlite3`, or a debug hook.

### implementation-readiness-1-31: String resource names and escaping are not specified - Severity: Nit
* **Reviewer Lens:** `egm-implementation-readiness`. Also raised by `egm-codebase-details`.
* **Affected Scope:** Lines 649-653.
* **Discrepancy Description**: No resource names are given. Strings such as "Don't..." and "What's new" need escaped apostrophes, or aapt fails. The plan doesn't say that item 6 must use `getString(R.string...)`.
* **Rationale & Recommendation**: List the resource names, note the escaping, and state that item 6 reads the strings by resource.

### implementation-readiness-1-32: Issue form fields are not specified - Severity: Nit
* **Reviewer Lens:** `egm-implementation-readiness`
* **Affected Scope:** Lines 890-895.
* **Discrepancy Description**: The field types and ids for `bug_report.yml` are not given.
* **Rationale & Recommendation**: List each field with its type and id.

### implementation-readiness-1-33: Verification commands are imprecise - Severity: Nit
* **Reviewer Lens:** `egm-implementation-readiness`. Also raised by `egm-style` (N21).
* **Affected Scope:** Lines 975-981.
* **Discrepancy Description**: "Zero new warnings" has no baseline. `python3 public:tools/check-publication.py` uses the document's path prefix inside a shell command. "The existing suite" must exclude `LiveReclaimProbe`.
* **Rationale & Recommendation**: Give a lint baseline, real paths, and the exact instrumentation filter.

### codebase-details-1-1: `openUrl` could reuse existing helpers - Severity: Nit
* **Reviewer Lens:** `egm-codebase-details`. Also raised by `egm-style` (N10).
* **Affected Scope:** Lines 726-730.
* **Discrepancy Description**: The `message(String)` toast helper (`MainActivity.java:1785-1786`) and an `ActivityNotFoundException` pattern (`:254-257`) already exist. The verbs are also imprecise: "Reads `state(...)`" should be "Calls", and "Runs `ACTION_VIEW`" should be "Starts an `Intent.ACTION_VIEW` activity".
* **Rationale & Recommendation**: Point to the existing helpers and tighten the verbs.

### codebase-details-1-2: The new AUTH POST has no stated timeouts or headers - Severity: Nit
* **Reviewer Lens:** `egm-codebase-details`
* **Affected Scope:** Lines 770-775.
* **Discrepancy Description**: `PhotosApi.Network` uses 30 s/60 s timeouts and `ProfileApi.Network` uses 10 s/15 s. The plan doesn't say which applies, or whether to keep `Accept-Encoding: identity` and fixed-length streaming (`PhotosApi.java:661-667`). "As today" is ambiguous.
* **Rationale & Recommendation**: State the timeouts and the request settings.

### codebase-details-1-3: "Exactly the four listed" headers holds only when an ETag is stored - Severity: Nit
* **Reviewer Lens:** `egm-codebase-details`. Also raised by `egm-style` (N14).
* **Affected Scope:** Lines 820-823.
* **Discrepancy Description**: `If-None-Match` is sent only when an ETag is stored (context 237-242). Without one, only three headers are sent.
* **Rationale & Recommendation**: Test both cases.

### codebase-details-1-4: The design-system.md target lines are imprecise - Severity: Nit
* **Reviewer Lens:** `egm-codebase-details`. Also raised by `egm-style` (N21).
* **Affected Scope:** Lines 958-961.
* **Discrepancy Description**: The stale text is at `design-system.md:174` and reads "Reclamation actions are not part of this release". The Settings row in the screen table (`:67`) should also list the new toggle.
* **Rationale & Recommendation**: Quote the stale line with its number, and add the `:67` edit.

### codebase-details-1-5: `:app:assembleRelease` fails without a keystore - Severity: Nit
* **Reviewer Lens:** `egm-codebase-details`
* **Affected Scope:** Lines 971-973.
* **Discrepancy Description**: Outside `build-release.sh`, the `preview` signingConfig has no storeFile unless `PHOTOS_KEYSTORE` is set (`build.gradle:15-21`).
* **Rationale & Recommendation**: Note the environment the build needs, or build the release APK only through the script.

### style-1-4: Time values are written as code but are not Java - Severity: Nit
* **Reviewer Lens:** `egm-style`
* **Affected Scope:** Lines 679-680, 792-793.
* **Discrepancy Description**: `24h`, `now + 24h` and `now + 1h` are formatted as code but aren't valid Java, and the units of `now` aren't stated.
* **Rationale & Recommendation**: Write `24 * 60 * 60 * 1000L` ms, as `ProfileStore.java:75` does.

### style-1-5: The timeouts don't say which is connect and which is read - Severity: Nit
* **Reviewer Lens:** `egm-style`
* **Affected Scope:** Line 683.
* **Discrepancy Description**: "Timeouts of 10 s and 15 s" doesn't say which is which. Context line 248 does.
* **Rationale & Recommendation**: Repeat "connect 10 s, read 15 s" here.

### style-1-6: A `warnIfOnlyOnPhone` change is repeated, and the `""` argument is unexplained - Severity: Nit
* **Reviewer Lens:** `egm-style`
* **Affected Scope:** Lines 719-720, 744.
* **Discrepancy Description**: The return-value change belongs to item 8 (line 757) but is repeated inside item 6. The account argument `""` is unused (`ReclaimService.java:122-126`), and the plan doesn't say so.
* **Rationale & Recommendation**: Remove the duplicate from item 6, and add a short note about the unused argument.

### style-1-7: "Now" and "today" are ambiguous - Severity: Nit
* **Reviewer Lens:** `egm-style`
* **Affected Scope:** Lines 754, 773.
* **Discrepancy Description**: "Now in `onCreate`" can be read as "at this moment", and "as today" has the same problem.
* **Rationale & Recommendation**: Use "currently in" or name the existing code.

### style-1-8: "Scratch build" is undefined, and the reader is addressed inconsistently - Severity: Nit
* **Reviewer Lens:** `egm-style`
* **Affected Scope:** Lines 782-784, 1000-1001.
* **Discrepancy Description**: "Scratch build" isn't defined. The passages are in the passive voice. "The author's approval" conflicts with "if you approve" in context line 426.
* **Rationale & Recommendation**: Define the term and pick one way of addressing the reader.

### style-1-9: The test class name and scope don't match what it tests - Severity: Nit
* **Reviewer Lens:** `egm-style`
* **Affected Scope:** Lines 807-823.
* **Discrepancy Description**: `UpdateNoticeTest` mostly tests `UpdateCheck`. "Every combination" contradicts the list of 10 single cases. The parenthetical about `org.json` stubs and the phrase "non-JSON paths" are compressed jargon.
* **Rationale & Recommendation**: Rename the class or explain why, and rewrite the stubs note in plain English, citing `build.gradle:31`.

### style-1-10: "Cases for each label" overstates the list - Severity: Nit
* **Reviewer Lens:** `egm-style`
* **Affected Scope:** Lines 842-846.
* **Discrepancy Description**: Nine cases are listed, but the label table has about 19 labels. No expected label is given per case.
* **Rationale & Recommendation**: Write "for these labels" and give the expected label for each case.

### style-1-11: Notification wording is ambiguous - Severity: Nit
* **Reviewer Lens:** `egm-style`
* **Affected Scope:** Lines 854-856.
* **Discrepancy Description**: "Notification 7403" should say "notification ID 7403"; 7403 is also `Scheduler.CONTINUE` (`Scheduler.java:8`). "It is cancelled" has an unclear subject. "DELETED state" should be `ReclaimEngine.State.DELETED`.
* **Rationale & Recommendation**: Use the exact names.

### style-1-12: Small wording problems - Severity: Nit
* **Reviewer Lens:** `egm-style`
* **Affected Scope:** Lines 888, 975, 994-995, 1004, 1006.
* **Discrepancy Description**:
  - "About 20 lines" (888) adds nothing.
  - "That's" (975) is informal.
  - The comma splice at 994-995 joins the action and the expected result.
  - "E2E" (1004) is never defined.
  - "Both files" (1006) doesn't name the files.
* **Rationale & Recommendation**: Fix each one.

### style-1-13: The live checks are out of order and miss preconditions - Severity: Nit
* **Reviewer Lens:** `egm-style`
* **Affected Scope:** Lines 984-1003.
* **Discrepancy Description**:
  - The v1.0.0 asset upload comes after the step that needs it.
  - "Temporarily set the installed versionName" doesn't say how, or which versionCode to use.
  - The blocked-state check doesn't state that an update must be available first.
  - "If needed" (1000-1001) should restate the criterion from Part 3.3 step 3.
  - The upgrade check doesn't name a signed-in device.
* **Rationale & Recommendation**: Put the prerequisites first, and state each precondition.
