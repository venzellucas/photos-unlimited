# 1.2 translations: how to translate the messages the app stores

> **BLUF:** Recommend B: look up the translated text at the moment the message is written. Status
> lines, backup errors and Free up journal messages are already stored as finished text, and they
> keep being stored that way. No database change is needed. Trade-off: after the language is
> changed, an old message stays in the old language until it's overwritten. Most of these messages
> are overwritten on the next run.

## Alternatives Considered

### A. Store a message code and its arguments, and translate when the screen shows it

- **For:** a language change also changes old messages.
- **Against:**
  - three stores change shape: `Settings.status`, the `uploads.error` column in `BackupDb`, and
    `jobs.message` in `ReclaimDb`;
  - rows written by 1.1 hold English text, so the app must read both shapes forever;
  - the database version changes, which runs the migration that backlog item 4 says is fragile.

### B. Translate when the message is written (recommended)

- **For:**
  - the stored shape stays the same, and 1.1 rows still display;
  - each `"text"` becomes `getString(R.string.x, args)`, which is mechanical.
- **Against:**
  - old rows keep the language they were written in;
  - code without a `Context` (the Free up engine, the backup engine, `ReclaimEngine.ineligible`)
    needs a small lookup: a `Context` where one exists, and the `R.string` id where a JVM test
    calls it.

### C. Translate only the screens and keep stored messages in English

- **For:** least work.
- **Against:** the backup status line and the Free up steps are what people read most. They would
  stay in English inside a translated app.
