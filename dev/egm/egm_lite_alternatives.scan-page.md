# Scan stops with SCAN_FORMAT_PAGE at the page after the main listing — alternatives

> **BLUF:** Recommend A: make `SCAN_FORMAT_PAGE` name the exact step, reason, field and scan
> stage, all fixed and secret-free, and change nothing else. A fix at page level is a guess today,
> and a wrong guess at this level could drop deletions or cursor tokens. One more phone run with
> A names the exact cause. Then the fix is a small, targeted change.

---

## Evidence

- Test build `1.1.0-test3`: the failure repeats at the same place (after the whole main listing). Every item-level cause is now lenient or has its own label, so the page itself fails.
- It is deterministic. The same page fails on both test builds, so a network truncation is
  unlikely: Android's HTTP stack reports a short body as a network error (`SCAN_NETWORK`).
- What can still produce `SCAN_FORMAT_PAGE` (`LibraryProtocol.parsePage`, `Proto`):
  1. **Response envelope:** response field 1 is repeated or not length-delimited (`Proto.message(response, 1)`).
  2. **Root decode:** a malformed tag, an unsupported wire type (3/4 groups, or 6/7), or a length past the end
     (`Proto.values(root)`).
  3. **Root shape:** field 1, 2, 6 or 9 is not length-delimited, field 7 is not a varint, or none of
     them is present.
  4. **End reads:** resume (root 1) or sync (root 6) is repeated, or "more" (root 7) is repeated.
- **Unknown:** which of these happens, and which stage the scan is in. The page after the main listing may be the first
  page after the initial listing, where the request and response shapes change.

## Alternatives Considered

### A. A precise page label (recommended)

- **The label:** `SCAN_FORMAT_PAGE_<STEP>_<REASON>[_F<field>][_W<wire>]_<STAGE>`. All parts are
  fixed tokens:
  - STEP: `ENV`, `ROOT`, `SHAPE` or `END`;
  - REASON: `DUP`, `WIRE`, `CUT`, `TAG` or `NONE`;
  - STAGE: `N`, `I`, `S` or `D`.
- **Example:** `SCAN_FORMAT_PAGE_END_DUP_F6_I`. The longest form fits the 48-character label
  limit.
- `Proto` exceptions keep their message text but carry the field and wire numbers (a small
  `IOException` subclass). Upload code that only reads the message is unchanged.
- `LibrarySync` adds the stage letter.
- **Pro:** names the cause on the next run, with no change to what the app accepts.
- **Con:** one more phone run before the fix.

### B. Guess and fix too

- Tolerate the most likely shapes as well: skip unknown root fields with group wire types, and
  take the last value of a repeated "more".
- **Con:** a repeated resume or sync token can't be resolved safely, so the most dangerous causes
  would stay strict anyway. It adds unverified leniency next to the cursor.

### C. Capture the raw page on the test emulator

- The author signs in to their main account on the reference emulator. Probes then capture the page after
  the main listing locally, so the fix can be iterated without phone runs.
- **Con:**
  - the main account's credential would sit on a test emulator;
  - the raw page (private photo metadata) would pass through the build host and this session;
  - Google may flag the new device.
- Not recommended unless A's label is still not enough.
