# Issues #1 and #2: how to learn the shape of what the scan can't read

> **BLUF:** Recommend A. The scan records a shape-only summary of what it can't read: field
> numbers, wire types, rough lengths, and small numbers below 16. It records this for each
> "unknown" item and for each delta page with no known fields. The summary is added to the
> existing "Copy report" of the advanced diagnostic. It is committed code, so it ships in the next
> release and other people can attach it to the issues. The author runs it once on a release-signed
> test build (`1.2.1-test1`), with one full "Scan again".

## Alternatives Considered

### A. A committed shape recorder, shown in the diagnostic report (recommended)

- **For:**
  - the code is reviewed and kept, so issue reporters can use it later;
  - no content leaves the phone: names, dates, sizes and tokens are reduced to field numbers,
    wire types and length buckets;
  - the report already has a copy button and is plain English.
- **Against:** about 200 lines of new code, plus one optional field on `Page` and on
  `ScanFailure`.

### B. A throwaway build with uncommitted logging

- **For:** no lasting code.
- **Against:** nobody reviews it, and it is lost after one run. The issues ask for help, and
  others would have no way to report the same data.

### C. Save the raw responses

- **Rejected:** they hold private photo metadata (agreed in earlier tasks).
