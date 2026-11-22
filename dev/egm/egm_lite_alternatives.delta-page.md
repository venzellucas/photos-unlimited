# Scan stops with SCAN_FORMAT_PAGE_SHAPE_NONE_D — alternatives

> **BLUF:** Recommend A: in the delta (catch-up) stage only, accept a page whose root has none of
> the fields the app reads, as an empty page with no resume token. That ends the delta pass the
> way upstream GPMC does. Elsewhere the label names the root's lowest field number. Trade-off: an
> unrecognized delta page is no longer an error; it carries no items, deletions or cursors
> the app could have read.

---

## Evidence

- Phone, test build `1.1.0-test4`: `SCAN_FORMAT_PAGE_SHAPE_NONE_D`.
  - `D` is `Stage.DELTA`. The initial listing finished; the failing request is the
    catch-up pass (`request()` sends resume field 4 and sync field 6).
  - `SHAPE_NONE`: the root decoded cleanly and is non-empty, but has none of fields 1 (resume),
    2 (items), 6 (sync), 7 (more) or 9 (deletions).
- **Upstream GPMC** (pinned `94b1b26`, `gpmc/db_update_parser.py` and `client.py`):
  - `parse_db_update` reads root 1, 2, 6 and 9 and ignores everything else; the root also carries
    collections (3) and envelopes (12), which it leaves commented out.
  - `_process_pages` stops the delta loop when the page has no resume token.
  - So a delta page that holds only album or shared-envelope changes, or nothing, is normal there.
- **Our code:** the "recognized" check in `LibraryProtocol.parsePage` rejects such a page. Continue
  retries the same delta request, so it fails at the same place every time.
- **Unknown:** which root fields the page has. Fields 3 and/or 12 are the likely ones
  [⚠️ UNVERIFIED].

## Alternatives Considered

### A. In the delta stage only, such a page counts as empty; elsewhere the label names its fields (recommended)

- **Delta stage:** `LibrarySync.run` catches `SCAN_FORMAT_PAGE_SHAPE_NONE…` and uses an empty page:
  - no items, deletions, resume or sync, and `more` is false;
  - `advance` then does what it already does for an empty delta page: `DONE`, or `SYNC` when an
    earlier page said "more", or `SCAN_CURSOR` when the sync token didn't change.
- **Other stages stay strict:**
  - an unrecognized page in `INITIAL` would end the listing early, so it stays an error;
  - in `NEW` and `SYNC`, a missing sync token is already `SCAN_CURSOR`.
- **The label names the root's lowest field number:** `SHAPE_NONE_F<n>`. If the error ever
  appears in another stage, the label says which field to look at.
- **Pro:**
  - finishes this scan on the next run, and matches upstream for the delta pass;
  - no deletion or cursor can be lost, because a page with field 9, 1 or 6 is still parsed
    strictly.
- **Con:** if Google ever moved deletions to a new field, a delta page would be treated as empty
  rather than stopping.
  - This is the same risk upstream accepts.
  - Reclaim still refuses any item that isn't known and has no hash.

### B. Accept only fields 3 and 12, and name any other field in the label

- Treat a root of only fields 3 (collections) and/or 12 (envelopes) as empty. Any other field
  number stays `SHAPE_NONE_F<n>_D`.
- **Pro:** stricter.
- **Con:** fields 3 and 12 are a guess [⚠️ UNVERIFIED]. If the page holds a different field, it
  costs another phone run.

### C. Another diagnostic build first

- The label names the root field numbers, and nothing else changes.
- **Con:** a third phone round for a case upstream already treats as normal.
