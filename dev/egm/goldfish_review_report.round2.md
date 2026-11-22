# Goldfish Annotated Review Report — Stage 9, round 2 (re-audit)

Status: resolved (2026-11-20). Author chose "auto accept": all PROPOSE FIX items applied; declined nits are accepted trade-offs to settle during implementation. Stage 9 re-audit cap reached; review concluded.

**Target:** `docs/designs/photos-unlimited-1.1.md` (lines 704-1554, plus Part 5 at 516-546)
**Source findings:** `.egm/implementation_readiness_report.md` (1 Blocker, 24 Majors, 35 Nits; all three reviewers completed)

This is the one re-audit EGM allows for Stage 9. After this resolution the
review concludes. Anything still open is recorded as an accepted trade-off.

| Category | Count |
| --- | --- |
| PROPOSE FIX | 25 (the Blocker, all 24 Majors) + 11 Nits that are real bugs |
| DECLINE (wording nits; fixed while writing the code) | 24 |
| CONTEST / ESCALATE | 0 |

I checked the Blocker and these Majors against the source: IR-2-2, IR-2-5,
IR-2-10, CD-2-5 and IR-2-11. All are accurate. One fact for IR-2-5: the
private repo has no git remote, so it is committed locally and never pushed.

## Blocker

### [Issue 1] CD-2-1 — The instrumentation threshold is in `tools/validate.py`, not `verify-release.py`
*   **Scope:** lines 1222-1232, 1497-1508, 729
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Proposed Edit:***
    *   Add item 17b, `tools/validate.py`: change `>= 19` (`validate.py:44`)
        to the total from the P4 instrumentation run.
    *   Item 17 no longer mentions a threshold.
    *   The Verification Plan's release check runs `tools/validate.py` first,
        then `tools/verify-release.py`.

## Majors

| # | Id | AI suggestion | Proposed edit |
| --- | --- | --- | --- |
| 2 | IR-2-1 (re-opens R1 #2) | PROPOSE FIX | `fetch` returns `record Fetched(String name, byte[] image)`. The image download moves into `fetch` (image steps of the label table). `ProfileStore.refresh` calls `fetch`, then `decode(fetched.image())` when it isn't null. Update the kept tests to the new return type, and the expected call count to 3 (token, userinfo, image). |
| 3 | IR-2-2 | PROPOSE FIX | Move `profile_failed` (and the creation of `strings.xml`) into P1. P4 adds the update strings to the same file. |
| 4 | style-2-2 | PROPOSE FIX | Rewrite the phase table with item parts: P1 = 2 (`profile_failed`), 6 (`refreshProfile`), 9 (P1 part), 10 (retry, `decode`/`save` throws), 14 (P1 part), 28, 29, 30; P2 = 5 (`profileRemoved`, Outcome B), 6/9/10/14 outcome parts; P3 = 6 (`onResume` warning part), 7, 8, 15; P4 = 2 (update strings), 3, 4, 5 (update keys), 6 (card, toggle, `onResume` check part), 12, 13, item 27 step 1. The test versions from item 1 are used at Gate D. |
| 5 | IR-2-3 | PROPOSE FIX | The checkpoint also runs `:app:assembleDebugAndroidTest`. After P1 and P4, it also runs the instrumentation command on the test emulator. |
| 6 | IR-2-5 | PROPOSE FIX | Add "Moving code to the build host": from the dev repo, run `tar c --exclude='*/build' --exclude=__pycache__ --exclude=.gradle --exclude=dist --exclude=.git app README.md tools docs \| <run on the build host> tar x`. Bring `dist/` back with the reverse `tar` on the build host. The private repo is committed locally only (it has no remote). Only the public repo is pushed, with git over HTTPS as venzellucas. |
| 7 | style-2-3 | PROPOSE FIX | One definition, in Conventions. **A** = E1, E2, or E3 with the author's approval loads the photo. **B** = otherwise, including a non-`AUTH_*` failure that the author's phone can't clear, and E3 working without approval. Experiments run in order and stop at the first success. Item 9 and the live checks point to Conventions. |
| 8 | IR-2-6 | PROPOSE FIX | Add labels: protobuf or UTF-8 decode errors in `LibraryProtocol` → `SCAN_FORMAT`; credential or `ApiException` sign-in → keeps its own message plus `SCAN_SIGN_IN`. Page-cap or deadline pause → message unchanged plus `SCAN_PAUSED_LIMIT`. Stop → unchanged, no label (the user asked for it). |
| 9 | IR-2-7 | PROPOSE FIX | Give exact message texts. The label goes at the end, in parentheses, after the last sentence. The HTTP message drops its own "(HTTP n)" and uses `SCAN_HTTP_n`. Fix the CUJ 7 example to the real `IOException` text. `SCAN_TIMEOUT` text: "The scan timed out. Results are partial. Continue or start over. (SCAN_TIMEOUT)". |
| 10 | CD-2-5 | PROPOSE FIX | "Scan is already complete" (`LibraryProtocol.java:265, 303`) → `SCAN_CURSOR`. Item 29 lists each line number instead of a range. |
| 11 | IR-2-8 | PROPOSE FIX | `LibraryProtocolTest` gets one case per `ScanFailure` label it can reach: `SCAN_ITEM_TEXT_LONG`, `SCAN_FORMAT`, `SCAN_ITEM_INVALID`, `SCAN_CURSOR`. Add a pure function `InventoryService.label(Throwable)` with JVM tests for the catch order: timeout, ApiException, ScanFailure, too-large message, other IOException, RuntimeException. |
| 12 | IR-2-9 | PROPOSE FIX | Live check 3 keeps the row. Check 4 uses it, then deletes it. The reinstall build is the same `0.9.0` test APK, installed with `adb install -r`. |
| 13 | IR-2-10 (re-opens R1 #25) | PROPOSE FIX | Re-map from the file: "77 automated tests" is line 99, and "version 1.0.0" is line 97. Quote each old string in full instead of relying on line numbers alone. |
| 14 | IR-2-11 | PROPOSE FIX | Outcome B also deletes `decodingPreservesColorAndRejectsInvalidImages`. Give the kept test's new body: draw `ProfileStore.icon(context, null, "a@example.com", bg, fg)` in light and dark, write the two PNGs, and assert the pixel at the centre isn't the background colour. |
| 15 | CD-2-7 (re-opens R1 #32) | PROPOSE FIX | Replace the 5-parameter `state` in § 1.2 with the 6-parameter one, using the same parameter names as item 4, in both § 1.2 and the diagram. |
| 16 | CD-2-9 | PROPOSE FIX | `errorLabel(String googleError, int status)`. "Empty" means empty after stripping. Split row 1035 into two rows: `Error=` present and not empty after stripping → `errorLabel`; no `Auth=` → `AUTH_NO_TOKEN`. |
| 17 | CD-2-10 | PROPOSE FIX | E3 also sets `callerSig=PhotosApi.GMS_SIG` when the form has `callerSig`. |
| 18 | CD-2-11 (re-opens R1 #21) | PROPOSE FIX | Quote the actual PRIVACY.md sentences that need GitHub added (found by text, not line), and add a new GitHub section after the Google section. |
| 19 | CD-2-13 | PROPOSE FIX | Outcome B list adds public `README.md` 19-20, 56 and 159, `PRIVACY.md` 44, and `index.html` 98-99, each with the old sentence quoted. |
| 20 | style-2-9 | PROPOSE FIX | Add `AUTH_TOO_LARGE` to the table. Say that `Network` returns `status = -1` with an empty body when the cap is hit, and `fetch` maps `-1` to the step's `*_TOO_LARGE` label. |
| 21 | style-2-7 | PROPOSE FIX | Reorder the live checks to follow the phases: Gate D (profile, scan) → P3 checks (reboot) → P4 checks (card, blocked) → upgrade → publish. |
| 22 | style-2-8 | PROPOSE FIX | Upgrade check: first `adb uninstall app.photos.unlimited` on the reference emulator (the spare account has to sign in again afterwards), then install 1.0.0, sign in, then install 1.1.0 over it. |
| 23 | style-2-14 | PROPOSE FIX | The rsync dry run expects the P1-P4 app files plus `app/build.gradle`. |
| 24 | style-2-16 | PROPOSE FIX | The 1.0.0 notes don't name the flaky test, so drop the allowance. Any failure counts. If a re-run passes, record the test name and both outputs in the release notes, and still treat it as needing a fix. |
| 25 | style-2-17 | PROPOSE FIX | The seeded job uses `sha1 = new byte[20]` and a fixed media key. Cleanup runs `DELETE FROM jobs WHERE media_key = ?` through a test-only `ReclaimDb.deleteForTest(String)`, annotated `@VisibleForTesting`. |

## Nits

| Id | AI suggestion | Proposed edit |
| --- | --- | --- |
| IR-2-13 | PROPOSE FIX | § 1.1 and the diagram say `updateIo`. |
| IR-2-20 | PROPOSE FIX | A redirect with no `Location` → `IMAGE_REDIRECT_HOST` (null check before `imageAddress`). |
| IR-2-21 | PROPOSE FIX | `updateLatest`, `updateEtag` and `updateDismissed` default to `""`. |
| IR-2-26 | PROPOSE FIX | Overview says "five parts", and the Technical Plan intro says "build in the phase order". |
| CD-2-16 | PROPOSE FIX | `startWrite()` failures also map to `CACHE_WRITE`. |
| CD-2-17 | PROPOSE FIX | Both places say "more than `MAX_BYTES`". |
| CD-2-20 | PROPOSE FIX | The extension allowlist applies only to the new UTF-8 and email checks. The existing secret patterns keep scanning every file as they do in 1.0.0. |
| CD-2-23 | PROPOSE FIX | `installedVersionName()` returns `""` on `NameNotFoundException` (which gives HIDDEN). |
| style-2-37 | PROPOSE FIX | "Commit and push with git over HTTPS (credentials through gh)". |
| style-2-40 | PROPOSE FIX | Pin to the commit SHA of a specific release tag, dereferencing annotated tags (`gh api repos/actions/checkout/commits/v4.x.y --jq .sha`). |
| style-2-41 | PROPOSE FIX | G4's quoted text matches `update_body`, and § 3.3 says "the author". |
| All other nits (IR-2-16, IR-2-23, IR-2-25, CD-2-14, CD-2-15, style-2-18, -20 to -36, -38, -39, minor wording) | DECLINE | Wording, line-number drift or obvious implementation details (attaching views, form labels). They are fixed while writing the code. They are not worth another document pass. |
