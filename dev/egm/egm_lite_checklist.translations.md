# EGM-Lite checklist: 1.2 translations (en-US, pt-BR, es, zh-Hans, ja)

**Task.** Every user-visible text is translated into the five languages. The app follows the system
language, falls back to English, and has a language menu. The public README links to translated
copies, and the site has a language selector at the top right. Author's direction (2026-11-21):
"go ahead, with good sense"; the translations need no human check; option B of
`egm_lite_alternatives.md` (translate when a message is written); backlog item 4 is included.

**Review baseline.**

- Dev repo at commit `60b6542`. `.egm/` is untracked and stays out of
  the review input.
- Public repo the public repo at commit `1fe7934`.

## Targets

- [x] **Text helper** `Text.java` (new): `get(@StringRes id, args…)` and `plural(id, count, args…)`
      from the application context. `init(Context)` is called by a new `App extends Application`.
      Without a context (JVM tests), it returns `"#" + id`, so tests never depend on wording.
- [x] **Shown errors** `UserFacingException extends IOException` (new). `PhotosApi.ApiException`
      extends it. `BackupEngine.safeMessage` passes through `UserFacingException` messages instead
      of the English prefix list (`"File "`, `"Google "`, …). Every exception that the prefix list let
      through becomes a `UserFacingException` with translated text: `AccountStore` 61,
      `BackupEngine` 134, `MediaScanner` 66/166/217, `PhotosApi` 150/399/412/438/441/455/511/529-533
      and the HTTP messages at 621/676.
- [x] **Engine and service texts** to `res/values/strings.xml`: `BackupEngine`, `BackupService`,
      `BackupDb` (state names), `ReclaimEngine` (step messages, `ineligible`, `Later`),
      `ReclaimService`, `ReclaimRemote`, `ReclaimRecovery`, `InventoryService`, `InventoryDb`,
      `LibrarySync`, `MediaScanner`, `AccountStore`, `Settings` (default status), `PhotosApi`,
      `AccountProbe` (result texts), `LibraryProtocol` (`"Unnamed media"` only).
      - **Stays English (diagnostic, never shown as a sentence):** protocol and scan labels
        (`SCAN_*`, `Proto` messages), HTTP headers, log lines, `IllegalStateException` texts.
      - Counts use `<plurals>`.
      - Notification channel names are translated; `createNotificationChannel` renames an
        existing channel.
- [x] **Screens** to `res/values/strings_ui.xml`: `MainActivity`, `SignInActivity`,
      `AccountProbeActivity`. The Backup card's button logic compares fixed action ids, not label
      text (`MainActivity` 942-945).
- [x] **Language menu.** Settings → Appearance → "Language": System default, English,
      Português (Brasil), Español, 简体中文, 日本語. It uses `LocaleManager.setApplicationLocales`
      (Android 13+, the app's minimum), so Android's own per-app language screen shows the same
      choice. `res/xml/locales_config.xml` plus `android:localeConfig` in the manifest.
- [x] **Translations**: `values-pt-rBR`, `values-es`, `values-b+zh+Hans`, `values-ja`, for every
      string file. Placeholders, `\'` escaping and product names are kept. Lint's
      `MissingTranslation` (an error) proves that no key is missing.
- [x] **Backlog item 4** `ReclaimDb.onUpgrade`: add each column only when `PRAGMA table_info`
      doesn't list it, and add `motion` only for `oldVersion < 4`. Test: upgrading twice is
      harmless.
- [x] **Tests**
  - Unit: `Text.get` without a context returns the id marker.
  - Instrumented: switching the app locale to `pt-BR` makes `Text.get` return Portuguese, and
    switching back restores English. `@After` resets to the system language.
  - Existing tests keep passing. The emulator is English, so English assertions still hold.
- [x] **Public README**: a language line under the title, and `README.pt-BR.md`, `README.es.md`,
      `README.zh-Hans.md`, `README.ja.md`. A short "Translations" note: improvements welcome by pull
      request. `PRIVACY.md`, `SECURITY.md` and the release notes stay English.
- [x] **Site**: `docs/pt-BR/`, `docs/es/`, `docs/zh-Hans/`, `docs/ja/` index pages; a language
      selector at the top right on every page; `hreflang` links. No automatic redirect.
- [x] **Docs (private)**: `docs/design-system.md` gets a rule: all user-visible text is in
      `res/values*/strings*.xml`, in all five languages.
- [x] **Checkpoint on the build host**: build, unit tests, lint (0 errors), then instrumentation
      on the test emulator (`notClass LiveReclaimProbe`). Screenshots of the main screens in pt-BR,
      es, zh-Hans and ja, checked for cut-off text.
- [x] **Version** 1.2.0 (versionCode 10) and a release notes entry. Publishing waits for the
      author.
- [x] **Commit locally** after the diff review; sync the public repo, run
      `tools/check-publication.py`, push after the author's OK.

## Exec result (2026-11-21)

- Build passed; 77 unit tests and 31 instrumentation tests pass (3 new in
  `LanguageInstrumentedTest`); lint 0 errors, 31 warnings.
- As built, beyond the targets: `LibraryProtocol.UNNAMED` stays English (library data that can
  name a re-uploaded file); the advanced diagnostic's clipboard report stays English; the
  translated download/redirect errors are `UserFacingException`s.
- Screenshots in pt-BR, es, zh-Hans and ja: the bottom bar wrapped "Armazenamento",
  "Almacenamiento" and "Copia de seguridad"; nav-only labels shortened ("Espaço", "Copia",
  "Espacio") and a length note added to the English comment. Charged storage reads as
  "uses storage", not "billed", in pt-BR, es and ja.

## Diff review round 1 fixes (author: "go ahead, with good sense", 2026-11-21)

- [x] **[1] [P2]** `SafeMessageTest`: user-facing messages pass, any other message (old
  whitelisted prefixes, null) becomes the generic text, network failures get fixed texts.
- [x] **[2] [P2]** Incomplete or device-bound credentials are sign-in `ApiException`s, so the
  card offers Reconnect. Other newly shown messages (media permission, restore, download) are
  intended: they are translated and actionable.
- [x] **[3] [P2]** `Text.reason(e)`: Free up step and run messages show a translated message or
  only the exception type, never raw text or "null".
- [x] **[4] [P2]** "Unnamed media" stays English as library data (recorded in Exec result).
- [x] **[5] [P3]** The language test's cleanup asserts the reset to English.
- [x] **[6] [P3]** ReclaimDb comment states the version bound; `addColumn` is private.
- [x] **[7] [P3]** ReclaimService channel comment.
- [x] **[10] [P3]** Site: relative language links; translated pages link to their README.
- **CONTEST [8] [P3]** `Local.label` unused: removing it touches four call sites for no change.
- **CONTEST [9] [P3]** One message for three redirect failures: they need the same action.
- [x] **Re-check:** 81 unit tests, 31 instrumentation tests, lint 0 errors. Release-signed
  1.2.0 (versionCode 10): SHA-256 `fb7016c2…6c72f50f`, certificate `cbd14272…`;
  `verify-release.py`: in-place upgrade from 1.1.0 keeps preferences.

## Pre-review exclusions (agreed)

- Old stored messages keep the language they were written in (option B).
- Chinese is Simplified only; a Traditional Chinese phone shows English.
- No automatic redirect on the site.
- Out of scope: the "wider standards check", issues #1 and #2.

---

### 🔍 Assumption Lifecycle Legend:

- `[⚠️ UNVERIFIED]`: Siloed external contract. Requires indisputable evidence to verify.
- `[⏳ ACK]`: Acknowledged assumption. Factual evidence unavailable; risk accepted and opaque-box
  empirical test strictly scheduled.
