# Goldfish Review Report

**Stage:** 9, Implementation Readiness Test, round 2 (re-audit)
**Target:** `docs/designs/photos-unlimited-1.1.md` (lines 704-1554, Detailed Implementation Plan to end of file; plus lines 516-546, Technical Plan Part 5: Library scan diagnosis)
**Settled input:** `.egm/goldfish_review_report.md` (round 1). Its declined items were not re-raised. Findings that re-open a round-1 item are marked "Re-opens round-1 Issue N".

## Reviewer Status

| Reviewer | Status | Raw findings |
| --- | --- | --- |
| `egm-implementation-readiness` (IR) | completed | 0 Blockers, 11 Majors, 15 Nits |
| `egm-codebase-details` (CD) | completed | 1 Blocker, 13 Majors, 9 Nits |
| `egm-style` (style) | completed | 0 Blockers, 17 Majors, 24 Nits |

## Summary

After deduplication there are **1 Blocker, 24 Majors and 35 Nits**.

All three reviewers say the plan is not ready yet. Most round-1 fixes landed. The gaps that remain are:
- **The profile transport seam is only half specified.** The image download still lives in `ProfileStore.refresh`.
- **The phase order can't be built as written.** P1 uses a string resource that is only added in P4.
- **The new Part 5 scan labels are under-specified and barely tested.**
- **Several release and live-check steps fail as written.** These are the wrong file for the test threshold, swapped `index.html` lines, a deleted row that the next check needs, and a downgrade install.

**How severity was set.** When reviewers rated the same issue differently, this report uses the highest rating. Where that raised a single reviewer's Nit, the merged entry says so. The synthesizer checked the following claims against the source:
- the threshold is at `tools/validate.py:44`, and `verify-release.py:57` only reads it;
- `docs/index.html` lines 97 and 99;
- the image GET is in `ProfileStore.java:64-70`;
- the phase table at lines 732-741.

Four round-1 items are re-opened:
- Issue 2 (profile seam);
- Issue 25 (`index.html` line map);
- Issue 32 (`state` signature in § 1.2);
- Issue 21 (PRIVACY.md lines).

Issues 6, 24 and 46 are re-opened only in part, as Nits.

## Prioritized Findings (Blockers -> Major -> Nits)

### CD-2-1: Release check edits the wrong file and never produces its input - Severity: Blocker
* **Reviewer Lens:** `codebase-details` (Blocker). Also IR-2-4 and style-2-4 (Major), and part of IR-2-3.
* **Affected Scope:** Lines 1222-1232 (item 17), 1497-1508 (Release check), 729.
* **Discrepancy Description:**
    * **Wrong file.** Item 17 says to change the instrumentation threshold `>= 19` in `tools/verify-release.py`. That check is in `tools/validate.py:44`. `verify-release.py:56-57` only asserts `instrumentation_passed`, which it reads from `validation.json`, and only `validate.py` writes that file.
    * **Missing file entry.** `tools/validate.py` is not in the Target Files list.
    * **Missing run.** The Release check never runs `validate.py`, so `verify-release.py` fails on a missing or stale `validation.json`. The Verification Plan writes its own `am instrument` command (line 1481) instead, which duplicates `validate.py:33-46`.
    * **No source for the new number.** It is said to come from "the P4 checkpoint run", but the checkpoint (line 729) runs no instrumentation tests, so it prints no total.
* **Rationale & Recommendation:** The release gate cannot run as written. Two reviewers rated this Major because the fix is obvious. It stays a Blocker because the final release step fails.
    * Add `tools/validate.py` (Modify, P5) for the threshold change.
    * Make the Release check "run `tools/validate.py`, then `tools/verify-release.py`".
    * Take the new count from a named instrumentation run (see IR-2-3).

### IR-2-1: Profile `fetch` seam is half specified; the image GET is still in `ProfileStore.refresh` - Severity: Major
* **Reviewer Lens:** `implementation-readiness`. Also CD-2-3 and style-2-10. **Re-opens round-1 Issue 2.**
* **Affected Scope:** Lines 1004-1009, 1050-1053, 1067-1076, 1166-1171; context 429-432.
* **Discrepancy Description:**
    * **What the plan says.** `fetch` makes all three requests and follows image redirects itself.
    * **What the code does.** `fetch` returns `Profile(name, picture)`, where `picture` is a URL (`ProfileApi.java:17-56`). The image is downloaded in `ProfileStore.refresh` with the old 6-argument `PhotosApi.Transport` call (`ProfileStore.java:64-70`). That call won't compile once `Network` implements only the new `ProfileApi.Transport`.
    * **What the plan leaves out:**
        * `fetch`'s new return type;
        * the rewrite of `refresh`;
        * where decoding happens.
    * **Tests that break or can't run:**
        * The P1 test "image bytes `abc` → `IMAGE_DECODE`" can't come from `fetch`.
        * The kept success test asserts `calls.size() == 2` (`ProfileInstrumentedTest.java:68`). That becomes 3 calls.
* **Rationale & Recommendation:** Give the exact signature, for example `record Profile(String name, byte[] image)`, and say that:
    * `refresh` calls `ProfileStore.decode(profile.image())` when the image is non-null;
    * `ProfileStore.java:66-70` is deleted;
    * the success test expects 3 calls and returns an `image/png` reply for the third.

### IR-2-2: P1 build fails because `R.string.profile_failed` arrives in P4 - Severity: Major
* **Reviewer Lens:** `implementation-readiness`. Also CD-2-2 and style-2-2.
* **Affected Scope:** Lines 734, 738, 951; 753.
* **Discrepancy Description:** P1 includes item 6's `getString(R.string.profile_failed, label)`. But `res/values/strings.xml` (item 2) doesn't exist yet and is only created in P4. The P1 `assembleDebug` checkpoint fails, and the Gate D test build can't be made.
* **Rationale & Recommendation:** Move item 2, or at least `profile_failed`, into P1.

### style-2-2: Phase table does not list the item parts each phase needs - Severity: Major
* **Reviewer Lens:** `style`. Also IR-2-14 (Nit).
* **Affected Scope:** Lines 734-741, 898, 964, 1074-1076, 1168.
* **Discrepancy Description:**
    * **P1.** It lists "10 (retry delay)", but item 14's P1 test expects `IMAGE_DECODE`. That label needs item 10's change that makes `decode` and `save` throw `ProfileApi.Failure`.
    * **P3.** Item 6's `onResume` is tagged "(P3, P4)", but P3 lists only items 7, 8 and 15. Its P3 half posts `drawUpdateCard()`, which exists only in P4.
    * **P2.** Outcome B in P2 calls `settings.profileRemoved()`, but item 5 is only listed in P4.
    * **P7.** Item 1's Gate D test-version edit sits under P7.
* **Rationale & Recommendation:** Every checkpoint "must pass before the next phase starts", so each mismatch breaks one. Make each row list exactly the item parts that phase needs.

### IR-2-3: Phase checkpoints never compile or run the instrumentation tests - Severity: Major
* **Reviewer Lens:** `implementation-readiness`.
* **Affected Scope:** Lines 727-730, 734-738, 1232.
* **Discrepancy Description:** The checkpoint runs `assembleDebug testDebugUnitTest lintDebug`. P1-P4 change `androidTest` sources (items 13-15), but nothing compiles or runs them, so a phase can pass its checkpoint with a broken test tree.
* **Rationale & Recommendation:**
    * Add `:app:assembleDebugAndroidTest` to every checkpoint.
    * Run the instrumentation command from the Environments section at the end of P1, P3 and P4.
    * Take item 17's count from that run.

### IR-2-5: Two working copies are never connected, and the private repo has no remote - Severity: Major
* **Reviewer Lens:** `implementation-readiness`. Also style-2-15.
* **Affected Scope:** Lines 708, 727-730, 1407-1409, 1418-1419, 1475-1477.
* **Discrepancy Description:**
    * **No transfer step.** Edits happen in the dev repo, while checkpoints and the release build run in the build host's checkout on the build host. The plan never says how changes get there. "Copy `dist/1.1.0/` back" gives no command.
    * **Push target missing.** Step 5 says "push both repos", but `.git/config` has no remote.
* **Rationale & Recommendation:**
    * Give the transfer commands in both directions, for example scp or rsync.
    * Change step 5 to "commit the private repo; commit and push the public repo".

### style-2-3: Outcome A/B definitions leave cases undecided - Severity: Major
* **Reviewer Lens:** `style`.
* **Affected Scope:** Lines 715-723, 735, 1539; context 455-481.
* **Discrepancy Description:**
    * **Three definitions.** The Conventions define Outcome A as "one of E1-E3 loads the profile". § 3.5 says "a fix works". § 3.6 defines B as "Google refuses every variant".
    * **Undecided cases:**
        * a non-`AUTH_*` label such as `PICTURE_HOST` or `IMAGE_DECODE`, where no experiment applies;
        * E3 works but the author doesn't approve it.
    * **Run all or stop early?** Gate D and live check 5.2 say "run E1-E3". § 3.4 says to run them only if the token is refused, in order, and to stop at the first that works.
* **Rationale & Recommendation:** The A/B choice drives P2 and items 22-23. Define one rule that covers every diagnosis result, and use § 3.4's stop-at-first wording everywhere.

### IR-2-6: Part 5 labels malformed responses as `SCAN_NETWORK`, and some stop paths get no label - Severity: Major
* **Reviewer Lens:** `implementation-readiness`. Also CD-2-4 and IR-2-17 (Nit).
* **Affected Scope:** Lines 518-539, 531, 1448-1465; context 128-132.
* **Discrepancy Description:**
    * **Plain `IOException`s that the "any other `IOException`" row would show as `SCAN_NETWORK`:**
        * `Proto` decoding errors (`Proto.java:49-148`: "Incomplete protocol response", "Ambiguous protocol message", and others);
        * invalid UTF-8 in `LibraryProtocol.text` (`:96`, `CharacterCodingException`);
        * `PhotosApi` constructor credential errors (`:89, :91`);
        * asset-open failures (`LibrarySync.java:70`).
    * **Stops that get no label at all:**
        * the 500-page cap and the 8-minute deadline (`InventoryService.java:104-110`);
        * `onTimeout`/`onDestroy` stops;
        * account-change `InterruptedIOException`s (`LibrarySync.java:62, 75, 77`).
* **Rationale & Recommendation:** A malformed response or a page cap on a large library is among the leading suspects G11 is meant to tell apart. As written, Gate D could return a misleading label or none.
    * Rethrow non-`ScanFailure` `IOException`s from `LibraryProtocol.parse` as `SCAN_FORMAT` (or add a `SCAN_PROTO` row).
    * Label the paused outcomes, for example `SCAN_PAUSED_LIMIT` and `SCAN_PAUSED_STOP`.

### IR-2-7: Scan message text and label position are undefined, and the CUJ 7 example is wrong - Severity: Major
* **Reviewer Lens:** `implementation-readiness`. Also CD-2-12 and style-2-12.
* **Affected Scope:** Lines 518-519, 523, 530-532, 1448-1469; CUJ 7 at 231-232.
* **Discrepancy Description:**
    * **(a) Timeout message.** The new `catch (SocketTimeoutException)` has a label but no message text. Today a timeout shows "Scan paused. Continue when you're ready." (`InventoryService.java:111-112`).
    * **(b) Label position.** "Added after its first sentence" puts the label after the period. CUJ 7 puts it before the period.
    * **(c) Wrong CUJ 7 example.** CUJ 7 pairs `SCAN_RESPONSE_TOO_LARGE` with "The scan stopped". By the mapping, that label comes from the `IOException` branch ("Scan interrupted or metadata format unavailable").
    * **(d) Double code.** The HTTP branch would read "(HTTP 500) (SCAN_HTTP_500)".
* **Rationale & Recommendation:** These labels are the user-visible output of the diagnosis. Give the exact final string for each branch and correct CUJ 7.

### CD-2-5: Item 29 range covers throws that have no label - Severity: Major
* **Reviewer Lens:** `codebase-details`. Also style-2-13 (Major) and IR-2-15 (Nit).
* **Affected Scope:** Lines 1441-1442 against 528 and 75.
* **Discrepancy Description:** Item 29 converts `LibraryProtocol.java:265-303`, while Part 5 and the Problem section cite `:267-300`. Lines 265 and 303 throw "Scan is already complete", which has no row in the Part 5 table.
* **Rationale & Recommendation:** Use one range, and either give those two throws a label or exclude them explicitly.

### IR-2-8: The scan-label mapping in `InventoryService` has no test - Severity: Major
* **Reviewer Lens:** `implementation-readiness`.
* **Affected Scope:** Lines 1466-1469.
* **Discrepancy Description:** Only `SCAN_ITEM_TEXT_LONG` and `SCAN_ITEM_INVALID` are tested. The mapping itself, the actual G11 deliverable, has no test. It depends on catch order:
    * `SocketTimeoutException` before `InterruptedIOException`;
    * `ApiException` before `IOException`;
    * `ScanFailure` before the message match.

    Because the mapping sits inside a `Service` thread, mistakes would only show up on the author's phone at Gate D.
* **Rationale & Recommendation:** Extract `static String scanLabel(Exception e)` and the message builder into plain Java. Add JVM cases for each table row, plus a `LibraryProtocol.advance` case expecting `SCAN_CURSOR`.

### IR-2-9: Live check 3 deletes the row that live check 4 needs - Severity: Major
* **Reviewer Lens:** `implementation-readiness`. Also style-2-6 (Major) and CD-2-18 (Nit).
* **Affected Scope:** Lines 1524-1535.
* **Discrepancy Description:**
    * **Deleted row.** Check 3 step 4 deletes the seeded `DELETED` row. Check 4 then starts "With the seeded row, run `adb reboot`" and expects notification 7403. But `warnIfOnlyOnPhone` cancels 7403 when `unsafeAll() == 0` (`ReclaimService.java:128-130`).
    * **Unnamed build.** Check 4's "install a newer build with `adb install -r`" doesn't say which build, or how it matches the installed signature.
* **Rationale & Recommendation:** Move the deletion to the end of check 4, and name the build used for the reinstall.

### IR-2-10: `index.html` line map still pairs text with the wrong lines - Severity: Major
* **Reviewer Lens:** `implementation-readiness`. Also CD-2-6 and style-2-5. **Re-opens round-1 Issue 25.**
* **Affected Scope:** Lines 1356-1367 (item 24), especially 1365 and 1367.
* **Discrepancy Description:** "77 automated tests" is on public `docs/index.html:99` ("Version 1.0.0 passed 77 automated tests"). Line 97 has "Free up storage in version 1.0.0". The synthesizer confirmed this. As written, the line-97 edit finds nothing, and line 99 keeps the test count.
* **Rationale & Recommendation:**
    * Line 97: "in version 1.0.0" becomes "in the current version".
    * Line 99: replace the whole "Version 1.0.0 passed 77 automated tests" clause.

### IR-2-11: Outcome B test edits leave a test that won't compile and give no new body - Severity: Major
* **Reviewer Lens:** `implementation-readiness`. Also CD-2-8 (Major) and style-2-35 (Nit).
* **Affected Scope:** Lines 1172-1180.
* **Discrepancy Description:**
    * **A test that won't compile.** `decodingPreservesColorAndRejectsInvalidImages` (`ProfileInstrumentedTest.java:119-134`) uses only `decode`, which Outcome B removes. It doesn't match "tests that use `cached` or `save`", so it survives, and `androidTest` fails to compile.
    * **No new body for the kept test.** The kept `cachedPhotoAppearsWithoutTintInBothThemes` calls the removed `save` (line 141) and asserts a green pixel (line 168). The plan gives no new set-up or assertion, and the test keeps a name that no longer fits.
* **Rationale & Recommendation:** List every test method to delete, and write out the kept test's new body and name.

### CD-2-7: Item 4 claims § 1.2 matches its signature; it does not - Severity: Major
* **Reviewer Lens:** `codebase-details`. Also style-2-1 (Major) and IR-2-12 (Nit). **Re-opens round-1 Issue 32.**
* **Affected Scope:** Lines 846-850; context 245-246, 311-320.
* **Discrepancy Description:**
    * **§ 1.2.** Line 311 still shows the 5-parameter `state(installed, latest, dismissed, unsafeCount, freeUpRunning)`, with no `enabled`. Its HIDDEN list also leaves out `unsafeCount < 0`.
    * **The diagram.** It has 6 parameters, but names the last two `unsafe, busy`.
* **Rationale & Recommendation:** Align § 1.2 and the diagram with item 4, or drop the claim that they match.

### CD-2-9: `errorLabel` can't produce `AUTH_HTTP_<status>` without a status - Severity: Major
* **Reviewer Lens:** `codebase-details`. Also style-2-11 (Major) and IR-2-18 (Nit).
* **Affected Scope:** Lines 999-1003, 1033-1035.
* **Discrepancy Description:**
    * **Signature.** `errorLabel(String googleError)` is said to return `AUTH_HTTP_<status>` for an empty value, but it has no status parameter.
    * **"Empty".** It isn't said whether "empty" is tested before or after stripping characters (`Error=---`).
    * **Row 1035.** "status 200 and body has `Error=` or no `Auth=`" maps to "`errorLabel(value)` or `AUTH_NO_TOKEN`". The pairing and the precedence when both hold aren't stated.
* **Rationale & Recommendation:** Either add a status parameter or move the fallback to the caller. Define "empty" as "after stripping", and split row 1035 into two rows.

### CD-2-10: E3 leaves `callerSig` unspecified - Severity: Major
* **Reviewer Lens:** `codebase-details`. Also IR-2-24 (Nit).
* **Affected Scope:** Lines 1060-1063.
* **Discrepancy Description:** E3 sets `client_sig=GMS_SIG` but leaves `callerSig`, which the stored credential sets to `PHOTOS_SIG` (`PhotosApi.java:157-158`). `connect` always sets both together (`:129-130`, `:157-158`).
* **Rationale & Recommendation:** State whether E3 also changes `callerSig`. As written, the experiment sends a mixed identity.

### CD-2-11: PRIVACY.md lines 3-4 don't list contacted services - Severity: Major
* **Reviewer Lens:** `codebase-details`. Also part of style-2-39. **Re-opens round-1 Issue 21.**
* **Affected Scope:** Lines 1310-1312 (item 22).
* **Discrepancy Description:** `PRIVACY.md:3-4` says there is no project server and no analytics. Line 32 is about what Google receives. Neither lists the services the app contacts.
* **Rationale & Recommendation:** Give the exact old and new sentences, and where each GitHub mention goes.

### CD-2-13: Outcome B doc edits miss stale profile-photo text - Severity: Major
* **Reviewer Lens:** `codebase-details`. Also IR-2-26 (Nit).
* **Affected Scope:** Lines 1316-1320, 1322-1352, 1356-1367.
* **Discrepancy Description:** Under Outcome B these still describe the profile photo or profile authorization:
    * public `README.md:19-20`, `:56` and `:159`;
    * `PRIVACY.md:44`;
    * `docs/index.html:98-99`.

    Items 23a and 24 change only lines 39-41.
* **Rationale & Recommendation:** Add an "If Outcome B" edit, with old and new text, for each of these lines.

### style-2-9: The AUTH size cap uses a label that doesn't exist, and it's unclear which component raises the size labels - Severity: Major
* **Reviewer Lens:** `style`. Also IR-2-19 and CD-2-21 (Nit).
* **Affected Scope:** Lines 1019-1022; context 413-416, 1036-1046.
* **Discrepancy Description:**
    * **Missing label.** `Network` caps AUTH bodies at 16 KiB and says a body over the cap fails "with the matching `*_TOO_LARGE` label". No `AUTH_TOO_LARGE` label exists.
    * **Unclear owner.** `USERINFO_TOO_LARGE` and `IMAGE_TOO_LARGE` appear as `fetch` steps, but `Network` enforces the caps. A fake transport therefore can't reach them.
* **Rationale & Recommendation:** Add `AUTH_TOO_LARGE` (or say which label applies), and say which component raises each size label.

### style-2-7: Live-check order contradicts the phase order - Severity: Major
* **Reviewer Lens:** `style`.
* **Affected Scope:** Lines 1510-1548 against 732-741.
* **Discrepancy Description:** The live checks run "in order" place the Gate D checks (5 and 6) after checks 2-4. Checks 2-4 need P3 and P4 code, and check 1 needs the P4 asset upload. The phase table puts Gate D right after P1.
* **Rationale & Recommendation:** Either reorder the list to match the phases, or state that it is grouped by topic.

### style-2-8: Live check 7 installs 1.0.0 over the `versionCode 7` test build - Severity: Major
* **Reviewer Lens:** `style`.
* **Affected Scope:** Lines 1536-1538, 1545-1547; 749.
* **Discrepancy Description:** Check 5 installs the Gate D test build (`versionCode 7`) on the reference emulator. Check 7 then installs 1.0.0 (`versionCode 6`) on the same emulator, which Android refuses as a downgrade.
* **Rationale & Recommendation:** Add an uninstall step first, as live check 2 already does.

### style-2-14: The rsync dry-run check fails every time - Severity: Major
* **Reviewer Lens:** `style`.
* **Affected Scope:** Lines 1407-1413; 748.
* **Discrepancy Description:** Step 3 expects exactly "the app files changed in P1-P4". But `app/build.gradle` changes in P7 step 2 (item 1), which runs before step 3.
* **Rationale & Recommendation:** Include item 1's edit in the expected list.

### style-2-16: "One known flaky test may be re-run once" names no test - Severity: Major
* **Reviewer Lens:** `style`.
* **Affected Scope:** Lines 1505-1506.
* **Discrepancy Description:** With no test named, any failure can be called "the flaky one".
* **Rationale & Recommendation:** Name the test, or drop the allowance.

### style-2-17: The boot-warning test deletes a job with a method `ReclaimDb` lacks, and the seed is incomplete - Severity: Major
* **Reviewer Lens:** `style`. Also IR-2-22 (Nit).
* **Affected Scope:** Lines 1188-1197.
* **Discrepancy Description:**
    * **No delete method.** `ReclaimDb` has no single-job delete. Its only `delete` (line 149) removes QUEUED jobs by account.
    * **Incomplete seed.** The seeded `Item` needs a non-null 20-byte `sha1`, because the column is `NOT NULL` (`ReclaimDb.java:31`). Otherwise `enqueue` fails silently, and `save` throws "Reclaim job disappeared" (`:59, :72`).
* **Rationale & Recommendation:**
    * Name the delete, for example `getWritableDatabase().delete("jobs", "account=? AND media_key=?", ...)`.
    * Specify the seed fields.

### Nits

| Id (also) | Lines | Discrepancy and recommendation |
| --- | --- | --- |
| IR-2-13 (CD-2-19), re-opens round-1 Issue 6 in part | 243, 294-295, 894-897 | § 1.1 and the diagram still say the check runs on `io`. Item 6 uses `updateIo`. Align them. |
| IR-2-16 (style-2-19) | 532, 1461-1462 | `SCAN_CRASH_<SimpleClassName>` "cut to 32": say whether the class name or the whole label is cut. Add `Locale.ROOT`. |
| IR-2-20 | 1042 | A redirect with no `Location` header makes `imageAddress(null)` throw an NPE, which shows as `NETWORK`. State the null check. |
| IR-2-21 | 873-877 | There are no defaults for `updateLatest`, `updateEtag` and `updateDismissed`. A `null` causes NPEs. Specify `""`. |
| IR-2-23 (CD-2-22) | 920-940 | `ui.text`, `ui.supporting` and `ui.button` return views that aren't attached to anything. Say where each is added and set the click listeners. Settings toggles put their summary after `\n` in the label (`MainActivity.java:557`), but the plan adds a separate view. |
| IR-2-25 | 1272-1283 | The issue form needs `attributes.label` for each field and `about` for each contact link. Give that text. |
| IR-2-26 | 16, 237-238 | The Overview and Technical Plan still say "four" parts "in any order", which contradicts Part 5 and the phase order. |
| CD-2-14 | 1052 | The redirect loop starts at `ProfileApi.java:109`, not 111. |
| CD-2-15 | 986 | The channel creation is `ReclaimService.java:49-51`. |
| CD-2-16 | 1048, 1075-1076 | `startWrite()` failures (`ProfileStore.java:108, 112-114`) map to `NETWORK`, not `CACHE_WRITE`. |
| CD-2-17 (style-2-24) | 812, 828-829 | "reaches `MAX_BYTES`" and "over `MAX_BYTES`" disagree for a body of exactly 1 MiB. |
| CD-2-20 | 1238-1242 | The new extension allowlist would also narrow the existing secret scan. Scope it to the new checks, or accept the narrower scan explicitly. |
| CD-2-23 | 915-916 | `installedVersionName()` must handle `NameNotFoundException`. State the return value, for example `""` (which gives HIDDEN). |
| style-2-18, re-opens round-1 Issue 24 in part | 711-712, 727-728, 735, 738, 1397, 1439, 1512, 1548 | Cross-references use mixed forms ("§ Part 5", "step 1 of P7" and "P7 step 1" for item 27 step 1, which runs in P4). Use one form. |
| style-2-20 | 534-539, 73 | "Thrown at each of those sites" covers rows that aren't throw sites. "The sites in `:691`" is plural for one line. The Problem section cites `:690`. |
| style-2-21 | 523, 1454 | It's unclear whether the sign-in message also gets a label. |
| style-2-22 | 527-528 | The ellipsis shorthand stands for exact strings. Write them out in full, and give the file name for each line. |
| style-2-23 | 544 | "Done with `egm-lite`" is process jargon. Write "a separate, smaller change". |
| style-2-25 | 832, 842 | It's unclear whether an exception in step 1 still runs `onDone`. |
| style-2-26 | 862-863, 920, 925 | `latest` is undefined in `drawUpdateCard`, the argument to `releaseUrl(...)` isn't shown, and `UpdateNotice` uses `REPO`, which is defined in `UpdateCheck`. |
| style-2-27 | 909-910 | The direction is reversed: it reads `unsafeAll()` into `unsafeCount`. |
| style-2-28 | 913, 929-931 | `SCREEN_BACKUP` sits beside a bare `selectScreen(3)`. The BLOCKED filled button has no icon argument. |
| style-2-29 | 915-917 | "Holds the `PackageInfo` read" should read "wraps the `versionName` read". "Clear the card" should name `updateCard.removeAllViews()`. |
| style-2-30 | 1013, 1015 | "GET with no headers" conflicts with `Accept-Encoding: identity`. "An `imageAddress`" should read "an address accepted by `imageAddress`". |
| style-2-31 | 1006, 426-427 | `Reply` has a `location` field that § 3.1 doesn't mention. |
| style-2-32 | 1039 | `email_verified=false` maps to `PROFILE_FIELDS_MISSING`, but the field is present, not missing. |
| style-2-33 | 1084 | The `manageAccount` change belongs in item 6's Outcome B list, not item 10. |
| style-2-34 | 1107, 1113-1124 | The leading space in "` v1.2.3`" is easy to miss. The `state` cases don't give their baseline inputs. |
| style-2-36 | 1399, 1421, 708 | "The scratchpad" is undefined, and the path style is mixed. |
| style-2-37 | 1418-1419 | Commits and pushes are git operations, not gh. |
| style-2-38 | 729, 1500 | The checkpoint runs `lintDebug`, the Verification Plan adds `lintRelease`. Say which one is the gate. |
| style-2-39 | 1340, 1366, 1386-1391 | Leftovers: public README line 156 test counts; the index.html line 98 row doesn't say which sentence to change; "Backup hero" isn't the design-system term ("Backup status"); the "notices sit below the main action" rule promised at 561-562 was dropped. |
| style-2-40 | 1300-1303 | `git/ref/tags/v4` reads the moving tag, and for an annotated tag it can return a tag-object SHA rather than the commit SHA. Resolve to the commit SHA of a specific release. |
| style-2-41, re-opens round-1 Issue 46 (outside target) | 101-102, 451-452 | § 3.3 still says "you". G4 quotes notice text that doesn't match `update_body` (line 762). |
| style (minor wording) | 1468 and others | "16 385-byte" uses a space separator. "Print `apksigner verify`" means "run it and show its output". The UI label is "Advanced account diagnostic". "Gate D" is never expanded. Use the full "Download latest version" label. Live check 4 doesn't say how to see notification 7403 (`dumpsys notification`). |
