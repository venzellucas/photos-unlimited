# EGM-Lite checklist: a scan of a large library stops with `SCAN_FORMAT`

**Task.** Author's phone, test build 1.1.0-test2: the scan read the whole main listing, then
stopped with `SCAN_FORMAT`. Option A of `.egm/egm_lite_alternatives.md`, approved by the author:

- an item whose key reads, but whose other core fields don't, is kept as "unknown" and never freed;
- a file name that doesn't decode makes the item unknown (the original Option A used replacement
  characters; diff review round 1 fix [1] replaced that, approved by the author with `exec`);
- the paths that stay strict get specific labels.

**Review baseline.**

- Dev repo at commit `b66217f` (1.1 P2).
- Pre-existing changes: `.egm/` (untracked). Keep it out of the review input.
- The build host's checkout has the Gate D test version (versionCode
  7 / `1.1.0-test2`) in `app/build.gradle`. It is never committed.

## Targets

- [x] **`LibraryProtocol.java`** ([parse/parsePage](../app/src/main/java/app/photos/unlimited/LibraryProtocol.java#L110-L178))
  - **Page level.** In `parsePage`, wrap these reads:
    - the root read (`Proto.message(response, 1)`, `Proto.values(root)`);
    - the wire-type and "recognized" checks;
    - the `Proto.fields(root, 2)` and `Proto.fields(root, 9)` iteration;
    - the `resume` / `sync` / `more` reads at the end.

    An `IOException` from any of them that isn't a `ScanFailure` becomes
    `ScanFailure("SCAN_FORMAT_PAGE", message)`. The empty-root and unrecognized-response throws use
    `SCAN_FORMAT_PAGE` too. The public `parse` wrapper's fallback becomes `SCAN_FORMAT_PAGE`, so no
    path still throws plain `SCAN_FORMAT`.
  - **Item key (still strict).**
    - `text(media, 1)` failing, other than `SCAN_ITEM_TEXT_LONG`, → `SCAN_FORMAT_KEY`.
    - A blank key stays `SCAN_ITEM_INVALID`.
    - A key over 16 KiB stays `SCAN_ITEM_TEXT_LONG`.
  - **Item body (lenient).** Move the rest of the per-item reads into a private
    `static Item item(String key, byte[] media) throws IOException` (details, name, kind, type,
    charged, size, taken, trash, archived, origin, `extra`).
    - Present but empty `details` stays `SCAN_ITEM_INVALID`: the item has no details at all.
    - Any other `IOException` from `item(…)`, including `ScanFailure("SCAN_ITEM_TEXT_LONG")` from
      the name, gives `unknown(key)`:
      `new Item(key, "Unnamed media", 0, null, null, 0, false, false, 0, Extra.NONE)`.
    - `charged == null` and `type == 0`, so `ReclaimEngine.ineligible` returns
      "Google hasn’t reported its storage use." and the item counts as "unknown" in the summary.
    - `InterruptedIOException` is never caught here. It is not thrown inside parsing, but rethrow it
      if seen.
  - **Name decoding.** The file name (`details` field 4) decodes strictly inside `item(…)`, so a
    malformed name makes the item unknown (round 1 fix [1]: the name is reused for the re-upload).
  - **Deletions (still strict).** Any `IOException` reading a deletion entry →
    `SCAN_FORMAT_DELETION`. The blank deletion key stays `SCAN_ITEM_INVALID`.
  - Add a comment on why the item body is lenient and keys and deletions are strict: a lost deletion
    could make reclaim trust a cloud copy that is gone.
- [x] **`LibraryProtocolTest.java`** ([scanFailuresCarryFixedLabels](../app/src/test/java/app/photos/unlimited/LibraryProtocolTest.java#L133-L160))
  - Change the two `SCAN_FORMAT` assertions to `SCAN_FORMAT_PAGE`.
  - New test `unreadableItemBecomesUnknownAndPageStillParses`: one page with three items:
    - a normal item;
    - an item whose `details` repeats field 10 (ambiguous integer);
    - an item whose name bytes are invalid UTF-8 (`0xC3 0x28`).

    It asserts:
    - all 3 items are returned;
    - item 2 has `charged == null`, `name == "Unnamed media"`, `extra() == Extra.NONE`, and
      `ReclaimEngine.ineligible(item)` is non-null;
    - item 3 (bad name) is unknown and ineligible too (round 1 fix [1]);
    - item 1 is unchanged.
  - New assertions:
    - an item whose key field 1 is repeated → `SCAN_FORMAT_KEY`;
    - a deletion entry with a repeated field → `SCAN_FORMAT_DELETION`;
    - a page whose item (field 2) is cut off → `SCAN_FORMAT_PAGE`.
- [x] **`ScanLabelTest.java`** ([test](../app/src/test/java/app/photos/unlimited/ScanLabelTest.java#L22)): use
  `SCAN_FORMAT_PAGE` in the existing pass-through case (the label passes through unchanged).
- [x] **Design doc label table** ([Part 5](../docs/designs/photos-unlimited-1.1.md#L516-L546), item 29 at lines 1500-1565): replace `SCAN_FORMAT` with
  `SCAN_FORMAT_PAGE` / `SCAN_FORMAT_KEY` / `SCAN_FORMAT_DELETION`. Add one paragraph: item-body
  failures make the item "unknown" instead of stopping the scan (author approved, 2026-11-20, after
  the phone report).
- [x] **Checkpoint on the build host** (tar sync per the design's Conventions):
  `./gradlew --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug`.
  Then the full instrumentation run on the test emulator (emulator-5560, `notClass LiveReclaimProbe`).
- [x] **Checkpoint result (2026-11-20):** build passed; 61 unit tests and 21 instrumentation
  tests pass; lint shows 0 errors. Release-signed `1.1.0-test3` built: SHA-256
  `0d58f3ed…a227a63`, signing certificate `cbd14272…`. Not served yet.
- [ ] **Commit locally** (private repo) after the diff review.

## Diff review round 1 fixes (author: exec, 2026-11-20)

- [x] **[1] [P1] A broken file name could be re-uploaded garbled.** `Item.name` feeds the reclaim
  re-upload (`ReclaimRemote.java:99-100`) and the staged file name (`ReclaimEngine.java:649-661`).
  Remove the lenient `name()` decoding. A malformed name throws inside `item(…)`, so the item
  becomes unknown and ineligible. Update the test: the bad-name item is unknown, and
  `ineligible` returns non-null.
- [x] **[2] [P2] `SCAN_FORMAT_KEY` can blame a sound key.** Decode the item envelope
  (`Proto.values(media)`) first. A failure there → `SCAN_FORMAT_ITEM` (strict: without an envelope
  there is no trustworthy key). `SCAN_FORMAT_KEY` is then only for field 1 itself. Add a test with
  an envelope whose field 5 runs past its end. Add the label to the design doc table.
- [x] **[3] [P2] Two branches untested.** Add tests:
  - a keyed item with no `details` → `SCAN_ITEM_INVALID`;
  - a keyed item with a 16385-byte name → unknown, and the page's other item still parses.

- [x] **Re-check (2026-11-20):** build passed; 62 unit tests and 21 instrumentation tests pass;
  lint shows 0 errors. `1.1.0-test3` rebuilt: SHA-256 `7a8a5941…2cba7d41`, signing certificate
  `cbd14272…`.

## Diff review round 2 fixes (author: exec, 2026-11-20)

- [x] **[1] [P2]** Design doc: no `.egm/` path; the approval is stated inline.
- [x] **[2] [P2]** This checklist: the stale name-handling text is rewritten (above).
- [x] **[3] [P3]** `LibraryProtocol.java`: the two comments name the no-details exception.
- [x] **[4] [P3]** Deletion tests: an overlong key → `SCAN_ITEM_TEXT_LONG`, a type-2 entry is
  skipped, a blank key → `SCAN_ITEM_INVALID`.
- [x] **[5] [P3]** Design doc test list: matches the tests.
- [x] **Re-check (2026-11-20):** build passed; 63 unit tests and 21 instrumentation tests pass;
  lint shows 0 errors. `1.1.0-test3` rebuilt: SHA-256 `ff75a549…c43897a824`, signing certificate
  `cbd14272…`.

## Deferred empirical tests

- [⏳ ACK] **The phone run.** Which item shape Google sends on the page after the main listing is unknown.
  - **Test:** release-signed `1.1.0-test3` on the author's phone. Continue the scan, which resumes
    at the page after the main listing.
  - **Pass:** the scan completes. A few "unknown" items are acceptable.
  - **If it still stops:** the label is now `SCAN_FORMAT_PAGE`, `_KEY` or `_DELETION`, which names
    the next step.

## Backlog (outside this change; outward-facing, needs the author's approval)

- [x] **A GitHub issue on the public repo** (#1, opened 2026-11-21) for investigating the items that end up "unknown":
  - which field shape causes it;
  - whether those items can be read properly and made eligible.

  Draft (no library size or other account details):

  > **Title:** Some library items are only partly readable and show as "unknown"
  >
  > Since 1.1, when the Storage scan meets an item whose details Google sends in an unexpected
  > shape, the app keeps the item as "unknown" instead of stopping the scan. Unknown items are
  > never freed. Investigate which fields cause this (a duplicated field, an unexpected type, or a
  > file name that isn't valid UTF-8) and whether these items can be read fully, so they can be
  > freed too. Labels: `bug`, `help wanted`.

- [x] **A second GitHub issue** (#2, opened 2026-11-21) for the delta page with no known fields (from the delta-page task,
  2026-11-20). Draft (no library size or account details):

  > **Title:** Identify the library page that ends a delta sync with no known fields
  >
  > Since 1.1, when the catch-up (delta) part of the Storage scan receives a page that has none
  > of the fields the app reads (resume, items, sync, "more" or deletions), the app treats it as
  > an empty last page, as the upstream GPMC client does. The scan then finishes with
  > "Scan finished (SCAN_FORMAT_PAGE_SHAPE_NONE_F<n>_D skipped)", where `<n>` is the lowest field
  > on that page. On a real library the page's lowest field is 4 (`…_SHAPE_NONE_F4_D`), so it
  > is not the collections field (3). Investigate which fields such pages hold (field 4, and
  > maybe the envelopes field 12) and whether any of them can carry deletions or trash changes,
  > or a continuation token. If they can,
  > the app would keep a deleted item as present until the next full scan, and reclaim could
  > remove a phone copy whose cloud copy is gone. Labels: `bug`, `help wanted`.

## Pre-review exclusions (agreed in critique)

- Option C (skip unreadable pages) is rejected: deletions and cursor position would be lost.
- No UI change. The "unknown" count already shows these items.
- No telemetry and no saving of raw responses: they hold private photo metadata.
- "0 using storage" for every item is correct (author confirmed).

---

### 🔍 Assumption Lifecycle Legend:

- `[⚠️ UNVERIFIED]`: Siloed external contract. Requires indisputable evidence to verify.
- `[⏳ ACK]`: Acknowledged assumption. Factual evidence unavailable; risk accepted and opaque-box
  empirical test strictly scheduled.
