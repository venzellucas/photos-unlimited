# EGM-Lite checklist: shape diagnostic for issues #1 and #2

**Task.** Learn which item fields make an item "unknown" (issue #1) and what the delta page with
no known fields holds (issue #2), without any content leaving the phone. Author's direction
(2026-11-21): go ahead with the issues, with good sense. Option A of
`egm_lite_alternatives.shape-diagnostic.md`.

**Review baseline.** Dev repo at commit `d6e2df3`. `.egm/` is
untracked and stays out of the review input.

## Targets

- [x] **Shape sketch** `LibraryShape.sketch(bytes, depth)` (new, pure Java). It returns field
      numbers and wire types, with fields merged and counted (`4:2x3`).
  - A varint shows its value only when it is below 16; any larger value shows as `n`.
  - A length-delimited value is `T` (valid UTF-8 with no control characters), `M{…}` (it parses
    fully as a message with fields ≤ 1000), or `B`. Each one carries a length bucket: `0`,
    `s` (<16), `m` (<256), `l` (<4096) or `x`.
  - Text is never opened. The output is capped at 1,500 characters.
- [x] **Unknown items (issue #1).** `LibraryProtocol.item` records the step it is on (`M2`,
      `D4`, `M5`, `K1`, `D35`, `D35.2`, …).
  - A failure becomes a label `<step>_<reason>[_F<field>][_W<wire>]`. The reason is `CUT`,
    `TAG`, `WIRE`, `DUP`, `UTF8` (an undecodable name) or `LONG`.
  - The sketch shows the failing field's occurrences, one level deep, with at most 3 of them.
  - The file name (`D4`) is never sketched below the top level: only `M` or `B` and its length
    bucket.
- [x] **Delta page (issue #2).** A `SHAPE_NONE` failure carries a sketch of the page root (depth
      4) in a new optional `ScanFailure.shape()`. `withStage` keeps it.
- [x] **Root fields per stage.** Each parsed page reports its root field list. The summary counts
      the pages that had each `field:wire`, per stage. This shows whether field 4 also comes on
      normal pages, where it is ignored today.
- [x] **Per-scan summary.** `LibraryShape` holds the counts.
  - Unknown labels: at most 20, then `OTHER`. Each keeps a count and the sketch of the first item.
  - Root fields: counted per stage.
  - Skipped delta pages: at most 3 distinct sketches, each with a count.
  - It is stored in SharedPreferences keyed by scan generation, so it adds up across runs of one
    scan and starts empty on a new one.
  - `LibrarySync.run` loads it, adds each page, and saves it in `finally`.
- [x] **Report.** `AccountProbeActivity.report()` appends `LibraryShape.report(context)`: a header
      saying it holds only field numbers and wire types, then pages per stage, root fields,
      unknown labels and skipped page sketches. It stays English, like the rest of that report.
- [x] **Tests (JVM)**
  - Duplicated `D35` gives `D35_DUP_F35` with a sketch.
  - An invalid UTF-8 name gives `D4_UTF8` with `B(s)`, and no byte of the name appears.
  - A text value becomes `T(bucket)`; a varint ≥ 16 becomes `n`.
  - `SHAPE_NONE` carries the root sketch.
  - Root fields are reported.
  - The summary round-trips through its stored form and keeps its caps.
- [x] **Tests (instrumented)** `LibrarySyncInstrumentedTest`: after a delta page with no known
      fields, the stored summary has the skipped sketch and the root fields per stage.
- [x] **Checkpoint on the build host.** Build, unit tests, lint (0 errors), instrumentation on the
      test emulator (`notClass LiveReclaimProbe`).
- [x] **Test build** `1.2.1-test1`, versionCode 11, release-signed with certificate `cbd14272…`.
      It is never committed, so the next release must use versionCode 12 or higher. It is served
      on the local network for the author's phone.
- [x] **Commit locally** after the diff review. No public push until the phone run is done and
      the author says so.

## Exec result (2026-11-21)

- Build passed; 87 unit tests (6 new in `LibraryShapeTest`) and 31 instrumentation tests pass;
  lint 0 errors, 31 warnings.
- `1.2.1-test1` (versionCode 11): SHA-256 `abbc62ce…5d833ed8`, certificate `cbd14272…`. The
  first attempt ran `build-release.sh` without `RELEASE_TAG`; the script refused, and the wrapper
  copied the stale 1.2.0 APK (its SHA matched 1.2.0; the share was stopped within a minute, before the author was told). The wrapper now deletes
  the old APK first and passes the tag.

## Diff review round 1 fixes (2026-11-21)

- [x] **[1] [P2]** Text with tab or line breaks counts as text, so it is never opened as a guessed
  message whose field numbers would spell its bytes. Test: `"@\n"` gives `T(s)`. Residual risk,
  accepted: bytes that are not UTF-8 and still parse fully as a message (fields ≤ 1000) are opened.
  This can only be a binary value, never a file name (the name is never opened).
- [x] **[2] [P2]** A skipped page is counted only after it is committed, like normal pages.
- [x] **[3] [P3]** Steps `D` and `K` parse the details and kind bodies first, so a damaged body
  isn't blamed on the file name. Test: `D_CUT_F4`.
- [x] **[4] [P3]** The report names the scan (the first 8 characters of its generation).
- [x] **[5] [P3]** `save` uses `commit()`.
- [x] **[6] [P3]** "Unreadable item occurrences": a delta pass re-sends changed items.
- [x] **[7] [P3]** Diagnostics run inside `record(…)`, which swallows runtime errors.
- **CONTEST [8] [P3]** An `M2` failure opens `details` one level, where the name shows as
  `T(bucket)`. That is its kind and length only, which is the same rule.
- [x] **[9] [P3]** `rootFields` is immutable. **CONTEST:** `Unknown` stays package-private (the
  app is one package); a capped sketch may be 1,501 characters.
- [x] **[10] [P3]** Tests: varint 15/16/negative, three occurrences then `x4`, `K1`, `D`,
  adding up across runs. **CONTEST:** no saving-on-failure test; the code path is one line.
- [x] **Re-check:** 87 unit tests and 31 instrumentation tests pass (the known flaky
  `BackupInstrumentedTest` media test failed once after the fresh install, then passed 6 of 6 on
  its own); lint 0 errors. `1.2.1-test2` (versionCode 11): SHA-256 `8eabd392…319eae7c`,
  certificate `cbd14272…`, served on the LAN instead of test1.

## Deferred empirical test

- [x] **Phone run (2026-11-22, `1.2.1-test2`).** The run continued a scan already in the main listing, so only its last part was read. Root fields: I
  `1,2,4,22`; S `1,2,4,6,9,10,20,21`; D `1,4,21`. 0 unreadable items. The skipped delta page holds
  only root field 4: one collection-like record (texts, flags, small numbers), with no resume,
  sync, more, items or deletions.
  - **Issue #2 answered:** ending the delta pass there loses nothing. Follow-up fix (author's
    go ahead with the issues): a wire-2 root field 4 counts as recognized, so the page is a normal
    empty last page. Tests: `aPageWithOnlyTheCollectionFieldIsAnEmptyLastPage`,
    `aLastDeltaPageWithOnlyTheCollectionFieldFinishesNormally`. 88 unit and 32 instrumentation
    tests pass; lint 0 errors. Design doc: fifth phone run.
  - [x] **Issue #1:** full "Scan again" on 1.2.1 (2026-11-22): every stage read, 0 unreadable items, 0 skipped pages (the field-4 page now reads normally).
- [x] **Published 1.2.1** (author: build 1.2.1, then close both issues):
  private `c90d5ba`, `d9b3ee5`; public `8534a37` (CI passed); release v1.2.1 with
  SHA-256 `c2115152…fdedda1df` (latest link verified); issues #1 and #2 closed with comments.

## Pre-review exclusions

- No raw response is stored or shown.
- No fix for either issue yet: the fix depends on the phone report.
- No new screen text; the report is English diagnostic text.
