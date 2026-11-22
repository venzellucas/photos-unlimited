# Developer notes

These are the working notes behind Photos Unlimited: designs, decisions, reviews and test plans.
They come from the author's private development tree, which was merged here on 2026-11-22 so that
anyone can continue the work. Personal details (the author's machines, network and library) were
taken out; nothing else was rewritten.

When a note says "the dev repo" or "the private repo", it means that old tree. Its app code was
always the same as this repository's `app/`. "The public repo" is this repository.

## What is here

| Path | What it holds |
| --- | --- |
| [`designs/photos-unlimited-1.1.md`](designs/photos-unlimited-1.1.md) | The approved 1.1 design (update check, profile photo, scan labels, release tooling), with every phone run that shaped the scan code. |
| [`design-system/`](design-system/) | The app's visual rules, design tokens and an HTML mockup. All screen text lives in `res/values*/strings*.xml`, in five languages. |
| [`android-account-investigation.md`](android-account-investigation.md) | Why the app signs in the way it does. |
| [`dev-tree-readme.md`](dev-tree-readme.md) | The old development README: feature notes on the storage inventory, Free up, background work and accounts. |
| [`egm/`](egm/) | The record of each change, made with the [EGM](https://egm.dev) process (below). |

### The `egm/` folder

Each small change has two files, named by topic:

- `egm_lite_alternatives.<topic>.md`: the options that were considered, and which one was chosen and why;
- `egm_lite_checklist.<topic>.md`: the targets, test results, review findings and how each one was handled.

| Topic | Change |
| --- | --- |
| `scan-item`, `scan-page`, `delta-page` | 1.1: the large-library scan stop, found step by step from phone runs. |
| `icon` | 1.1: the launcher icon. |
| `translations` | 1.2.0: five languages and the language menu. |
| `shape-diagnostic` | 1.2.1: the shape-only scan diagnostic that closed issues #1 and #2. |

The 1.1 design went through the full process. The files are `concepts_brief.md`, `design_draft.md`,
`implementation_readiness_report*.md` and `goldfish_review_report*.md`. `backlog.md` lists ideas
that were never started.

`concepts_brief.md` was written before the 1.2 decisions were made. Where it differs from the
checklists, the checklists record what was built. In particular, every translation was made by a
language model, and no person reviewed any of them.

## How to continue

- **Build.** JDK 21 and the Android SDK with platform 37. See [Build](../README.md#build) in the main README.
- **Tests.** Run the instrumentation on a dedicated emulator with no Google account
  (`tools/emulator.sh`, serial `emulator-5560`). Some test classes clear the app's account.
  `LiveReclaimProbe` is the only class meant for an emulator that is signed in to a spare
  account, and it only runs by hand.
- **Release.** Run `tools/build-release.sh`, which needs the signing keystore in
  `PHOTOS_KEYSTORE`/`PHOTOS_SIGN_PASSWORD`. Then run `tools/verify-release.py` with
  `PREVIOUS_APK` pointing at the last signed release, and finish with `tools/check-publication.py`.
  - Only the author holds the signing key, so updates that install over existing copies come
    from the author's releases.
  - The next release needs versionCode 13 or higher, because two 1.2.1 test builds used 11.
- **Scan problems.** Ask for the report from Settings → Advanced diagnostic → Copy report. Its
  "Library shape" part holds only field numbers, wire types and length buckets.
