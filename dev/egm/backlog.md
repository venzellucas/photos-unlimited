# Backlog (not in 1.1)

- **ReclaimDb migrations are not idempotent** (P3 review finding 4, 2026-11-21, contested for 1.1).
  `onUpgrade` runs `ALTER TABLE … ADD COLUMN motion` unconditionally. Two `ReclaimDb` helpers that
  open the journal at the same moment after an update (BootReceiver on MY_PACKAGE_REPLACED,
  MainActivity's `io`, ReclaimService) can both see the old version, and the second would fail with
  "duplicate column". 1.1 has no reclaim schema change, and separate helpers already opened the
  journal concurrently before P3. Before the next reclaim schema bump: check existing columns in
  `onUpgrade` (PRAGMA table_info) or serialize opening with a static lock.
