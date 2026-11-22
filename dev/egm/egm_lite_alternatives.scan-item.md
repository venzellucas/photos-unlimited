# Scan stops with SCAN_FORMAT on a large library — alternatives

> **BLUF:** Recommend A: one test build that both fixes the likely causes and narrows the label if
> it still stops. Each phone round trip is costly, and a diagnosis-only build (B) costs one more
> round trip for the same outcome. Skipping whole pages (C) is unsafe.

---

## Evidence

- Author's phone, test build 1.1.0-test2: the whole main listing read, then `SCAN_FORMAT`.
- `SCAN_FORMAT` merges several different failures (`LibraryProtocol.parse`, `LibraryProtocol.java:110-120`):
  - **Page level:** the response root is missing, a root field has the wrong wire type, or the
    response is cut off (`Proto.values` reports "Incomplete protocol response").
  - **Item level:** one strict read in one item fails, and that fails the whole page:
    - a file name that isn't valid UTF-8 (`text(details, 4)` decodes strictly);
    - a field that appears twice, or with an unexpected wire type, in `details` 7/10/16/29/30/35 or
      in `kind` 1/2/3 (`Proto.message` / `Proto.integer` report "Ambiguous …").
- `extra()` already reads its own fields leniently. The core fields in `parsePage` don't.
- What reclaim requires (`ReclaimEngine.java:212-220`): `charged > 0`, not in trash, and a known
  SHA-1. An item with `charged = null` and no hash is never eligible.

## Alternatives Considered

### A. Lenient items plus page-level labels (recommended)

- **Item fields.** If the key (field 1) reads, but any other core field of that item fails:
  - keep the item, named "Unnamed media";
  - mark it unknown (`charged = null`, `Extra.NONE`), so it shows under "unknown" and reclaim
    never touches it.
- **File names.** Decode them with replacement characters instead of failing.
- **Still strict:** the item key, deletions and the page root. Losing a deletion could make reclaim
  trust a cloud copy that is gone.
- **Labels for the strict paths that remain:** `SCAN_FORMAT_PAGE` (root or cut-off response),
  `SCAN_FORMAT_KEY` and `SCAN_FORMAT_DELETION`. The next phone run then names the cause if it still
  stops.
- **Pro:** most likely fixed in one round trip, and fails safe.
- **Con:** if an item-level cause is real, a few items show as "unknown" with no explanation.

### B. Diagnosis-only build first

- Split `SCAN_FORMAT` into fine labels (`SCAN_FORMAT_NAME_UTF8`, `SCAN_FORMAT_FIELD_<n>`, …) and
  change nothing else. Fix after the next phone report.
- **Pro:** exact cause before any behaviour change.
- **Con:** one more phone round trip, and the scan stays broken in between.

### C. Skip a page that doesn't parse

- **Rejected:** a skipped page drops deletions and the cursor's position. Reclaim could then trust
  stale cloud state.
