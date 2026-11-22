# EGM-Lite checklist: finish the delta pass on a page with no fields the app reads

**Task.** On the author's phone, test build `1.1.0-test4` stops with `SCAN_FORMAT_PAGE_SHAPE_NONE_D`, after the whole main listing. The main listing finishes. The failing request is the delta (catch-up)
pass, and its page root has none of fields 1, 2, 6, 7 or 9. Upstream GPMC reads root fields 1, 2, 6 and 9 from
these pages, and ends the delta loop when there is no resume token. The author approved option A of
`.egm/egm_lite_alternatives.md` on 2026-11-20, and asked that this stays tracked as an open issue on
GitHub.

**Review baseline.**

- Dev repo at commit `b53aed1`. The working tree is clean apart from `.egm/`, which
  is untracked; keep it out of the review input.
- The build host checkout's `app/build.gradle` gets a test version, which is never committed.

## Targets

- [x] **`LibraryProtocol.java`** ([parsePage](../app/src/main/java/app/photos/unlimited/LibraryProtocol.java#L151-L238))
  - `SHAPE_NONE` names the root's lowest field number: `SCAN_FORMAT_PAGE_SHAPE_NONE_F<n>`. The
    longest form with a stage, `SCAN_FORMAT_PAGE_SHAPE_NONE_F536870911_I`, is 40 characters.
  - Add `static final Page EMPTY = new Page(List.of(), List.of(), new byte[0], new byte[0], false)`.
  - Nothing else changes. Known root fields keep their strict type checks (`SHAPE_WIRE_…`), and an
    empty root stays `ENV_NONE`.
- [x] **`LibrarySync.java`** ([page loop](../app/src/main/java/app/photos/unlimited/LibrarySync.java#L73-L78))
  - In the existing `catch (ScanFailure e)`: when the cursor stage is `DELTA` and the label starts
    with `SCAN_FORMAT_PAGE_SHAPE_NONE`, use `LibraryProtocol.EMPTY` as the page and continue.
    Otherwise rethrow `withStage(e, stage)` as today.
  - `advance` is unchanged. An empty delta page goes to `DONE`, or to `SYNC` when an earlier page said
    "more", or to `SCAN_CURSOR` when the sync token didn't change. All three behaviours already exist.
  - **Covered by this (stated, not changed):** `ReclaimRemote.lookup` runs the same loop through
    `beginDelta`, so reclaim's catch-up pass also ends on such a page.
- [x] **Tests (JVM)** in `LibraryProtocolTest`:
  - the `SHAPE_NONE` case becomes `SCAN_FORMAT_PAGE_SHAPE_NONE_F90`;
  - a root of fields 3 and 12 gives `…_SHAPE_NONE_F3`;
  - `advance(DELTA cursor, EMPTY)` gives `DONE`, with `nextSync` kept as the sync token;
  - with `more` set by an earlier page and the sync token changed, it gives `SYNC`.
- [x] **Tests (instrumented, test emulator only)** in `LibrarySyncInstrumentedTest`, using a fake
  transport with a sequence of pages:
  - NEW `{sync s1}` → SYNC `{sync s2, resume r}` → DELTA `{root field 3 only}` ends in `DONE`, with no
    failure;
  - the same unrecognized page as the first INITIAL page fails with
    `SCAN_FORMAT_PAGE_SHAPE_NONE_F3_I`. The listing must never end early.
- [x] **Design doc** `docs/designs/photos-unlimited-1.1.md`:
  - the Part 5 PAGE row: `SHAPE_NONE_F<n>`, and the delta exception;
  - §29;
  - the test list;
  - a "Third phone run (1.1.0-test4)" paragraph, with no library size.
- [x] **GitHub issue drafts** (backlog, outward-facing). Add a second draft next to the
  "unknown items" draft in `egm_lite_checklist.scan-item.md`:
  - title: "Identify the library page that ends a delta sync with no known fields";
  - body: what the app now does, the reclaim risk, and what to learn (which root fields; whether
    any carry deletions);
  - labels `bug` and `help wanted`;
  - no library size or account details.

  Both drafts are shown to the author and opened only after this fix works on the phone and the
  author confirms the text.
- [x] **Checkpoint on the build host:** the build, unit tests and lint (0 errors, 53 warnings), then
  instrumentation on the test emulator. Uninstall first, because a leftover versionCode 7 build blocks
  the install. Then build the release-signed `1.1.0-test5` and verify the certificate `cbd14272…`.
- [x] **Commit locally** after the diff review.

## Spec review round 1 fixes (author: exec, 2026-11-20)

- [x] **[1] [P1] Instrumented tests that can pass.** Each new test calls `db.reset(ACCOUNT)` before
  `LibrarySync.run` (as `InventoryService` does), uses `maxPages` 5, and answers `PhotosApi.AUTH`
  separately.
  - Delta test: NEW `{sync s1}` → SYNC `{sync s2, resume r}` → DELTA `{root field 3 only}`. Assert:
    - the third request carries field 4 = `r` and field 6 = `s1`;
    - the final cursor is `DONE` with `sync` = `nextSync` = `s2`;
    - the message ends with `(SCAN_FORMAT_PAGE_SHAPE_NONE_F3_D skipped)`.
  - Initial test: NEW `{sync s1, resume r0}`, then INITIAL `{root field 3 only}`, fails with
    `SCAN_FORMAT_PAGE_SHAPE_NONE_F3_I`.
- [x] **[2] [P1] Keep the evidence.** When the lenient path fires, `LibrarySync.run` remembers the
  staged label (`withStage(e, DELTA)`). If the run ends in `DONE`, it sets the message to
  `Scan finished (<label> skipped)` through `db.message(account, generation, …)`. That is a fixed,
  secret-free token, and the phone run reads the field number from it.
- [x] **[3] [P2] Exact control flow.** In the existing catch:
  `if (stage == Stage.DELTA && e.label().startsWith("SCAN_FORMAT_PAGE_SHAPE_NONE")) page = LibraryProtocol.EMPTY; else throw LibraryProtocol.withStage(e, stage);`
  Then fall through to `checkStopped`, `advance` and `apply`, with no `continue`. The prefix check runs
  on the unstaged label.
- [x] **[4] [P2] Honest risk.**
  - **The risk:** a delta page that carried a deletion or trash signal in a new root field would be
    read as empty. The cached item then stays known, hashed and not trashed until the next full
    Storage scan, because later lookups start the delta from the new sync token.
  - **The files at stake:** in the meantime, `ReclaimRecovery`'s orphan removal and
    `ReclaimEngine.verify`'s copy deletion could delete a phone file.
  - **Why the added exposure is small:** `parsePage` already ignores unknown root fields on any
    page that also has 1/2/6/7/9, so the change only adds pages with none of them.
  - **Where it's stated:** the deferred ACK, the design doc §29 and the issue draft.
- [x] **[5] [P3] Accuracy.**
  - Upstream reads root fields 1, 2, 6 and 9 from delta pages; 7 comes only from the state response.
  - Upstream also treats an empty root as a last page. Here `ENV_NONE_D` deliberately stays an
    error; say so in the design-doc row.
  - Add a JVM case: a DELTA cursor with `more=true` and `nextSync == sync`, then
    `advance(c, EMPTY)`, gives `SCAN_CURSOR`.

## Exec result (2026-11-20)

- Build passed; 67 unit tests pass; lint 0 errors, 53 warnings.
- Instrumentation: 24 tests, including both new `LibrarySyncInstrumentedTest` cases.
  - `ProfileInstrumentedTest` first failed with EACCES: the emulator's
    `Android/data/app.photos.unlimited/files` folder was left owned by an earlier install's user ID.
  - After removing that folder, the class passes (7 of 7). This was an environment problem, not a code
    problem.
- Release-signed `1.1.0-test5` (versionCode 8): SHA-256 `0662df25…01b3eb9d`, certificate `cbd14272…`.
  Served on the LAN for the phone run.
- The second GitHub issue draft is in `egm_lite_checklist.scan-item.md` (Backlog).

## Diff review round 1 fixes (author: "review and then commit everything", 2026-11-21)

- [x] **[1] [P2]** Fourth phone run paragraph in the design doc (lowest field 4); phone run
  ticked; tag narrowed; F4 added to the issue draft.
- [x] **[2] [P2]** Instrumented `onlyShapeNoneIsReadAsEmptyAndOnlyInTheDelta`: `ENV_NONE_D` and
  `SHAPE_WIRE_F9_W0_D` in DELTA, `SHAPE_NONE_F3_S` in SYNC, each failing and keeping its stage.
- [x] **[3] [P3]** Doc row and §29 say the message appears only when the same run reaches `DONE`.
- [x] **[4] [P3]** `SHAPE_NONE` always appends `_F<lowest>`; the unreachable bare label is gone.
- [x] **[5] [P3]** Test-list line re-wrapped.
- [x] **[6] [P3]** §29 names `ReclaimRemote.lookup` and its message side effect.

## Deferred empirical tests

- [x] **Phone run** (2026-11-21). `1.1.0-test5` after Continue: "Finished", same totals, message
  "Scan finished (SCAN_FORMAT_PAGE_SHAPE_NONE_F4_D skipped)". The page's lowest root field is 4.
- [⏳ ACK] **Accepted risk.** See fix [4]. It is tracked by the GitHub issue above
  [⚠️ UNVERIFIED: which root fields besides 4 the page holds, and whether any carry deletions or a
  continuation token].

## Pre-review exclusions (agreed in critique)

- Option B (accept only fields 3 and 12) and option C (another diagnostic build) were rejected by
  choosing A.
- No lenient handling in `NEW`, `INITIAL` or `SYNC`.
- No raw response is stored or shown. It holds private photo metadata.

---

### 🔍 Assumption Lifecycle Legend:

- `[⚠️ UNVERIFIED]`: Siloed external contract. Requires indisputable evidence to verify.
- `[⏳ ACK]`: Acknowledged assumption. Factual evidence unavailable; risk accepted and opaque-box
  empirical test strictly scheduled.
