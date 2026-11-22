# Photos Unlimited design system

Status: native UI implemented for version 0.2.0, 13 November 2026; current as of
version 1.1.0 (update card, Check for updates, tonal launcher icon). The earlier
interactive prototype remains a sample-data design reference. Release validation
is recorded separately in the build artifacts.

Photos Unlimited should feel at home on a modern Android phone and make one
question easy to answer: what is happening with my backup? Version 0.2.0 establishes
shared themes, components, navigation, and status messages, alongside a read-only
Google Photos storage inventory preview. Uploads are working according to the user's latest
device report; preserve that working setup throughout the UI migration.

## Platform and appearance

Use Material 3 as the foundation, with supported Expressive components where
they improve hierarchy and touch interaction. Use platform conventions for Back,
permissions, account selection, notifications, system bars, and keyboard insets.
Material supports light and dark schemes and wallpaper-derived dynamic color.
[Android Material guidance](https://developer.android.com/develop/ui/compose/designsystems/material3)

The default appearance follows the system. Settings offers System, Light, and
Dark, plus a switch for wallpaper colors. Store these choices independently of
backup settings. Change themes without losing navigation, selected folders,
credentials, or an active upload. Every app-owned screen, dialog, and notification
must use the same semantic color roles; Google-owned sign-in screens retain
Google's styling.

The preview uses a sage fallback palette and offers an ocean palette to illustrate
personalization. It cannot read Android wallpaper colors. In the Android app,
obtain dynamic color from the supported Material theme API. An Android upgrade
does not automatically update the app's bundled component library.

### Language

All user-visible text lives in `res/values/strings.xml` (engines, services,
notifications) and `res/values/strings_ui.xml` (screens), with translations in
`values-pt-rBR`, `values-es`, `values-b+zh+Hans` and `values-ja`. English in
`values/` is the reference and the fallback for any other system language. Write
whole sentences with positional placeholders (`%1$s`); never glue fragments in
Java, and use `<plurals>` for counts. Code without a Context uses `Text.get`.
Stored messages (backup status, upload errors, Free up steps) are translated when
they are written. Protocol and scan labels (`SCAN_*`), log lines and the library
placeholder name stay English. The app follows the system language; Settings →
Language (Android's per-app language, `Languages.java` and
`res/xml/locales_config.xml`) overrides it. Lint's `MissingTranslation` error
blocks a release with an untranslated key.

### Foundation tokens

Use semantic tokens rather than color literals or individual widget styles.
The example fallback values are specified in `design-tokens.json`.

| Foundation | Rule |
| --- | --- |
| Color | Surface, surface container, primary/on-primary, primary container/on-primary-container, outline, error/on-error-container. Always use matching foreground/background pairs. |
| Typography | System sans; headline 28/36 sp, title 22/28 sp, body 16/24 sp, supporting text 14/20 sp, labels 14/20 sp. Use regular and medium weights. |
| Spacing | 4 dp base; use 8, 12, 16, 24, and 32 dp. Compact screen horizontal margins: 20 dp. |
| Shape | Large status surface: 28 dp. Grouped settings surface: 24 dp. Small status icon background: 16 dp. Pill-shaped primary button. |
| Touch | At least 48 × 48 dp effective targets. Increase row height with text scaling. |
| Motion | Short, purposeful state changes; respect reduced animation settings. No decorative continuous movement. |

Android recommends a minimum 48 dp touch target. Keep labels available to screen
readers, announce state transitions rather than every progress tick, and test at
200% font scale. Check text contrast of at least 4.5:1 for ordinary text and 3:1
for large text and essential non-text indicators.
[Android accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)

## Navigation and screens

Use four top-level destinations: **Backup**, **Activity**, **Storage**, and **Settings**.
Keep their order stable, retain their state, and label every destination. The
folder picker is a native selection dialog opened from Backup. The account entry is in Settings.
The phone layout expands vertically with content; larger windows use a centered,
bounded reading column. A navigation rail can be added when a larger-window
layout has been validated.

| Screen | Main content and actions |
| --- | --- |
| Backup | One status surface, current-file progress when known, confirmed device backup counts, selected folders, and an Open Google Photos action. The main action follows the current state. |
| Folders | Names and paths, selected state, permission recovery, and an explicit Save action. Back discards an unsaved selection. Do not select new folders silently. |
| Activity | Current queue and confirmed upload history; separate Uploaded, Already in library, Waiting, and Needs attention. Offer retry only for applicable items. |
| Settings | Automatic backup, unmetered-network constraint, charging constraint, appearance, account management, Check for updates, and About. Diagnostics live under an advanced entry. |

Account setup and missing permissions should appear only when required. The
connected account remains accessible from the Backup header. Do not place
developer diagnostics or a permanent release announcement above everyday actions.
Explain material backup behavior during setup and retain it in About.

## Reusable components

- **App bar:** screen title and contextual account/back action; respect insets.
- **Backup status:** icon, short state label, useful explanation, optional
  progress, and one primary action. Never communicate state by color alone.
- **Setting row:** icon, label, supporting text, and one switch or navigation
  action. The entire switch row is a single accessible target.
- **Media row:** filename, media type, status, and useful time/size information.
  Missing metadata is unknown. Wrap long names without covering actions.
- **Navigation item:** icon and text label with a selected indicator. At large text
  sizes, show the selected label and keep accessible names on every destination.
- **Dialog or sheet:** clear title, focused decision, and ordinary Android Back
  behavior. Keep the underlying state when dismissed.
- **Update card:** "Version X.Y.Z is available" on the Backup screen, below the hero
  and the counts line, in the normal card style with no warning colors. Notices sit
  below the main action, never above it. While Free up has items only on the phone,
  the card asks to finish Free up instead of offering the download.
- **Feedback:** inline recovery for persistent failures; brief confirmation for
  saved preferences. Use platform permission UI for actual permission requests.

## Backup state contract

The presentation layer should consume typed state, not infer state by matching
the current free-form `Settings.status()` strings. Keep stable account, folder,
and ledger identifiers in the existing backup layer.

| State | Example label | Main action and behavior |
| --- | --- | --- |
| Account missing | Connect your account | Open the existing working account setup. |
| Permission missing | Allow photo and video access | Explain the missing permission and launch the platform flow. |
| No folders selected | Choose your folders | Open folder selection. |
| Scanning | Checking your folders | Indeterminate progress; do not invent a completion percentage. |
| Uploading | Backing up | Show current-file bytes when known. Pause stops future work according to the existing engine's cancellation behavior. |
| Waiting for network | Waiting for an unmetered connection | Show pending count and link to network preferences. Do not call every unmetered network Wi-Fi. |
| Waiting for charging | Waiting for charging | Explain the selected charging constraint. |
| Waiting for Android | Backup scheduled | Explain that Android controls background execution when no more specific blocker is known. |
| Paused | Backup is paused | Back up now uses the existing resume/start behavior. |
| Retry scheduled | Will retry automatically | Show the reason and next retry time only when known. |
| Needs attention | Some items need attention | Review affected items before a targeted retry. Other eligible items may continue. |
| Complete | Selected folders are backed up | Show the last completed check and a Back up now action. |

Derive the prominent state from missing prerequisites, current work, blocking
conditions, and the queue. Show partial failures separately rather than hiding an
active upload. A successful upload is not evidence of quota exemption. Existing
library matches remain a separate count. Never present the local upload ledger
as the size or completeness of the user's entire Google Photos library.

The prototype uses a fixed current-file example of 75 MB of 120 MB, not a live
transfer. Android integration must expose the engine's existing byte callbacks
to UI state. Percentages are per file unless a separate, validated batch total
exists. Do not infer an ETA from a sample or show invented throughput.

## Icon specification

Retain the cloud/upload idea with a clean silhouette, at a consistent stroke
weight for in-app icons. Create a vector launcher foreground, separate opaque
background, and a true monochrome layer. Let the launcher supply its mask;
avoid baking a circular mask into the artwork. Test circle and squircle masks,
themed icons, and light/dark wallpaper combinations. The native application now includes a
vector foreground, independent background, and monochrome adaptive layer.

The launcher icon uses a tonal style: a filled dark-sage cloud `#18513C` (the
dark theme's `primaryContainer`) on mint `#B5F1D1` (the light theme's
`primaryContainer`), with the up-arrow cut out as a real hole. The monochrome
layer reuses the foreground, so themed icons take its alpha and keep the arrow.
`tools/make-icon.py` generates the outline and checks it (shape, 66 dp safe
zone, centring). Its docstring has the regeneration command, which writes the
drawable (`--drawable-out`) and the matching website icon (`--svg-out`); add
`--check` to confirm both files still match.
[Android adaptive icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)

## Implementation and release sequence

1. **Design foundation:** this specification, tokens, and interactive light/dark
   screen preview with sample backup states.
2. **UI implementation:** Material Components for Android 1.14.0 supplies the
   native Expressive theme, navigation, buttons, switches, dialogs, and progress
   indicators in the existing Java application. Shared theme roles also apply to
   app-owned account screens. The HTML prototype is not the app runtime.
3. **Validation:** check all states in light/dark, multiple dynamic palettes,
   320 dp and larger widths, large text, TalkBack, keyboard/Back, edge-to-edge,
   rotation and process recreation. Verify persistence during a theme change and
   an upgrade over the signed working APK. Run existing engine regression tests
   and a controlled upload smoke test when delivering the Android UI.
4. **Release:** preserve package, signing key, credentials, settings, and ledger;
   distribute a signed update and record known limitations with its validation.
5. **Feedback:** address real-device issues before broadening the feature set.

The UI milestone is complete when all current user flows share the design system,
follow appearance preferences, remain accessible, and retain working backup
behavior after an in-place update. A prototype alone does not complete that
implementation milestone.

## Storage inventory preview and future work

The native preview provides a read-only Google Photos inventory: filename,
photo/video type, original file size, and quota-charged bytes. It sorts by
charged size and filters by media type. Thumbnails are deferred until their
retrieval and caching behavior has been validated. Keep unknown quota values distinct from
confirmed zero; preserve metadata presence instead of inheriting a default zero.

The pinned upstream GPMC parser includes `quota_charged_bytes`, which is a useful
research lead. Validate library pagination, deleted/archived/shared items, field
presence, and charged-byte semantics against account-visible examples before
building a feature on it. The parser alone does not establish complete coverage
or reliable accounting. Google's public Library API is limited to app-created
content, so it is not a general full-library inventory alternative.
[Upstream parser](https://github.com/xob0t/gpmc/blob/94b1b267ca6819da542d4330a7dd216747eb0437/gpmc/db_update_parser.py#L134),
[Photos API scope changes](https://developers.google.com/photos/support/updates)

Drive files and Gmail require separate integrations for an account-wide view.
The native Storage destination now contains the inventory preview. The earlier
interactive prototype intentionally remains a record of the initial three-tab
design. Free up storage replaces chosen items that use Google storage with verified
copies uploaded through the app.
