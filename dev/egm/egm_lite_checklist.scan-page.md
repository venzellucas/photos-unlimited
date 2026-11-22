# EGM-Lite checklist: name the exact cause of `SCAN_FORMAT_PAGE`

**Task.** Author's phone, test build `1.1.0-test3`: the scan stops at the same place as before
(after the whole main listing) with `SCAN_FORMAT_PAGE`. Option A of
`.egm/egm_lite_alternatives.scan-page.md`, approved by the author on 2026-11-20:

- the label names the step, the reason, the field and wire numbers, and the scan stage;
- what the parser accepts does not change.

**Review baseline.**

- Dev repo at commit `241b156`.
- Pre-existing changes: `.egm/` (untracked). Keep it out of the review input.
- The build host checkout has the Gate D test version in `app/build.gradle`. It is never committed.

**Label grammar.** `SCAN_FORMAT_PAGE_<STEP>_<REASON>[_F<field>][_W<wire>]_<STAGE>`, made only of
fixed tokens and numbers. The longest form, `SCAN_FORMAT_PAGE_SHAPE_WIRE_F536870911_W7_I`, is 43
characters, within `ScanFailure`'s `[A-Z0-9_]{1,48}`. (Spec review fix [6]: the longest *reachable* label is
`SCAN_FORMAT_PAGE_ROOT_WIRE_F536870911_W7_I`, 42 characters, from root tag bytes `FF FF FF FF 0F`.)

- STEP:
  - `ENV`: response field 1 (`Proto.message(response, 1)`), or that field is absent;
  - `ROOT`: decoding the root (`Proto.values(root)`, `Proto.fields(root, 2|9)`);
  - `SHAPE`: the root's field-type and "recognized" checks;
  - `END`: the resume (1), sync (6) and more (7) reads.
- REASON:
  - `DUP`: a field that must appear once appears twice;
  - `WIRE`: a field has an unexpected wire type, or a wire type the decoder doesn't support;
  - `CUT`: a length runs past the end;
  - `TAG`: an invalid tag or varint;
  - `NONE`: an empty or unrecognized response.
- `F<field>` is added only when the field is known and between 1 and 536870911. `W<wire>` is added
  only for `WIRE`.
- STAGE: the first letter of the cursor stage, `N`, `I`, `S` or `D`.

## Targets

- [x] **`Proto.java`** ([read/values/message/integer/fields](../app/src/main/java/app/photos/unlimited/Proto.java#L46-L152))
  - Add `static final class Malformed extends IOException` with `final String reason` (one of
    `DUP`, `WIRE`, `CUT` or `TAG`), `final int field` (0 = unknown) and `final int wire` (-1 =
    unknown).
  - Every existing `throw new IOException("…")` in the decoder becomes a `Malformed` with the
    **same message text**:
    - "Incomplete protocol response" → `CUT` (with the field when known);
    - "Invalid protocol integer" and "Invalid protocol field" → `TAG`;
    - "Unsupported protocol field" → `WIRE` (with field and wire).
  - `message` / `integer` split their combined check:
    - a wrong wire type → `WIRE` (field, wire), message "Ambiguous protocol message" / "…integer";
    - a second occurrence → `DUP` (field), same messages.
  - No caller reads these message texts (grep: none outside `Proto.java`). Uploads see the same
    `IOException` type and text.
- [x] **`LibraryProtocol.java`** ([parse/parsePage](../app/src/main/java/app/photos/unlimited/LibraryProtocol.java#L110-L190))
  - Wrap each of the four steps so that a `Proto.Malformed` becomes
    `ScanFailure("SCAN_FORMAT_PAGE_<STEP>_<REASON>[_F][_W]", message)`.
  - The current explicit throws become:
    - empty root → `SCAN_FORMAT_PAGE_ENV_NONE`;
    - unexpected root field type → `SCAN_FORMAT_PAGE_SHAPE_WIRE_F<n>_W<w>`;
    - unrecognized → `SCAN_FORMAT_PAGE_SHAPE_NONE`.
  - The `parse` fallback for any other unlabelled `IOException` stays plain `SCAN_FORMAT_PAGE`.
    It should not happen.
  - Add a pure `static ScanFailure withStage(ScanFailure e, Stage stage)`. It appends `_` plus the
    stage's first letter only to labels starting with `SCAN_FORMAT_PAGE`, and returns other
    failures unchanged.
  - Item, key and deletion handling is untouched.
- [x] **`LibrarySync.java`** ([page loop](../app/src/main/java/app/photos/unlimited/LibrarySync.java#L73)):
  wrap `api.libraryPage(…)`, catch `ScanFailure` and rethrow
  `LibraryProtocol.withStage(e, current.cursor().stage())`.
- [x] **Tests (JVM)** in `LibraryProtocolTest.java`. Each case asserts the full label from `parse`,
  then the stage suffix through `withStage`:
  - `ENV_DUP_F1`: the response has field 1 twice;
  - `ENV_NONE`: the response has no field 1;
  - `ROOT_CUT`: root field 2 claims 5 bytes and has 1 (the existing cut-off case, now
    `SCAN_FORMAT_PAGE_ROOT_CUT_F2`);
  - `ROOT_WIRE_F12_W3`: the root contains tag `0x63` (field 12, wire 3 group);
  - `SHAPE_WIRE_F7_W2`: root field 7 is length-delimited;
  - `SHAPE_NONE`: the root has only field 90;
  - `END_DUP_F6`: root field 6 appears twice;
  - `END_DUP_F7`: root field 7 appears twice;
  - `withStage`: `SCAN_FORMAT_PAGE_END_DUP_F6` with `INITIAL` gives `…_I`, `SCAN_FORMAT_KEY` is
    unchanged, and the result always matches `[A-Z0-9_]{1,48}`.

  Update the existing `SCAN_FORMAT_PAGE` assertions (empty response, `{10,5,8}`, cut-off page) to
  their precise labels. Also add a `Proto` case: `Malformed` keeps the old message texts.
- [x] **Design doc** ([Part 5 table](../docs/designs/photos-unlimited-1.1.md#L516-L560) and the test
  list): describe the label grammar in one row, and add the phone result for `1.1.0-test3`.
- [x] **Checkpoint on the build host:** the build, unit tests and lint, then instrumentation on the
  test emulator (uploads use `Proto`).
- [x] **Commit locally** after the diff review.

## Spec review round 1 fixes (author: exec, 2026-11-20)

- [x] **[1] [P2] Field on `CUT`/`TAG`.** `read()` throws without a field. `values()` and `fields()` catch
  `Malformed` from the value/length read after a tag is parsed and rethrow it with that field
  (`Malformed.at(field)`, only if the field is still 0). `CUT` and `TAG` both get `F<n>` when known.
- [x] **[2] [P2] `ENV` field numbers.** `ENV` covers decoding the whole outer response, and its `F<n>` is a
  field of the outer response, not of the root. Say so in the code comment and the design doc row.
- [x] **[3] [P2] Stage suffix wired in `LibrarySync`.** New androidTest (test emulator only) that drives
  `LibrarySync.run` with a fake `PhotosApi.Transport` returning a malformed page and asserts the
  label ends in `_N`. Note: `ReclaimRemote.lookup` uses the same loop; its labels change too, which
  is harmless (the reclaim path shows only the message).
- [x] **[4] [P3] Check order.** In `message`/`integer`, the wire check runs first: a right-type field
  followed by a wrong-type duplicate → `WIRE`. Tested: `ENV_WIRE_F1_W0`.
- [x] **[5] [P3] Wrapped field numbers.** A field number outside 1..536870911 is recorded as 0 in both
  `values()` and `fields()`. Tested on `fields()` with tag varint `82 80 80 80 40` (field 2^31).
- [x] **[6] [P3] Exact expected labels.**
  - empty response → `SCAN_FORMAT_PAGE_ENV_NONE`;
  - `{10,5,8}` → `SCAN_FORMAT_PAGE_ENV_CUT_F1`;
  - cut-off page → `SCAN_FORMAT_PAGE_ROOT_CUT_F2`.

  The longest reachable label is tested through `parse` and `withStage`. `withStage` keeps
  `e.getMessage()`.

- [x] **Checkpoint (2026-11-20):** build passed; 66 unit tests and 22 instrumentation tests pass
  (new: `LibrarySyncInstrumentedTest`); lint shows 0 errors. Release-signed `1.1.0-test4`: SHA-256
  `708b33f1…56bd346`, signing certificate `cbd14272…`. Not served yet.

## Diff review round 1 fixes (author: exec, 2026-11-20)

- [x] **[1] [P2] Missing tests.** `ROOT_CUT_F7` (field added by `Malformed.at` after a parsed tag),
  `ROOT_TAG` (zero tag), and `Proto.integer` with a wrong-type duplicate → `WIRE` F3 W2.
- [x] **[2] [P2] Design doc §29** rewritten: step labels, `Proto.Malformed` (same type hierarchy and
  texts for uploads), the `LibrarySync.run` stage suffix, and the `ReclaimRemote.lookup` note.
- [x] **[3] [P3] Line refs** in §29 and the Part 5 table now cite method names.
- [x] **[4] [P3] "Strictness is deliberate" comment** moved back above `parsePage`.
- [x] **[5] [P3] `LibrarySyncInstrumentedTest`** has its own bullet in the test list.
- [x] **Checkpoint (2026-11-20, round 1):** build passed; 66 unit tests and 22 instrumentation tests
  pass; lint 0 errors, 53 warnings. Release-signed `1.1.0-test4`: SHA-256 `aadf8990…16889e7`,
  signing certificate `cbd14272…`. Served on the LAN for the phone run.

## Deferred empirical tests

- [⏳ ACK] **The phone run.** Release-signed `1.1.0-test4`. The author taps Continue, and it
  fails at the page after the main listing with the precise label. The fix follows as its own small `egm-lite` change.

## Pre-review exclusions (agreed in critique)

- Option B (guess a page-level fix now) is rejected: a repeated resume or sync token can't be
  resolved safely.
- Option C (the author's main account on the test emulator) is rejected unless A's label isn't
  enough.
- No raw response is stored or shown. It holds private photo metadata.

---

### 🔍 Assumption Lifecycle Legend:

- `[⚠️ UNVERIFIED]`: Siloed external contract. Requires indisputable evidence to verify.
- `[⏳ ACK]`: Acknowledged assumption. Factual evidence unavailable; risk accepted and opaque-box
  empirical test strictly scheduled.
