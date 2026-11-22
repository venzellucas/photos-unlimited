# EGM-Lite checklist: launcher icon style C (tonal Material You)

**Task.** Keep the cloud and up-arrow, but change the style. Instead of today's thin outline, the icon becomes a
filled dark-sage cloud `#18513C` with the arrow cut out, on a mint background `#B5F1D1`. The
author chose style C from the preview artifact on 2026-11-20. The website icon becomes sage to match
(the author's answer: "sage").

**Review baseline.**

- Dev repo at `241b156`. Pre-existing uncommitted changes from the
  page-label task: `Proto.java`, `LibraryProtocol.java`, `LibrarySync.java`, `LibraryProtocolTest.java`,
  `LibrarySyncInstrumentedTest.java` (new), `docs/designs/photos-unlimited-1.1.md` and `.egm/`. Keep
  them out of this review; the icon task touches only `tools/make-icon.py`,
  `ic_launcher_foreground.xml` and `docs/design-system.md`.
- Public repo the public repo at `f9869ab`. Its `docs/icon.svg` change ships with the
  v1.1.0 publish (P7), not before.

## Targets

- [x] **`tools/make-icon.py`** (new, dev-only, standard library only).
  - It prints the single outline path for the silhouette, in the 108 dp adaptive canvas. Variant C
    from the preview, shift `(-3, 3.5)` baked in:
    - **Cloud:** circles c(57,49) r15, c(41,57) r10 and c(72,56) r11, plus rect x41–72, y47–67. The outline
      is computed as one closed contour: the lower-left arc of the left circle, the top arc of the
      big circle and the right arc of the right circle, each between their computed intersection
      points, then the flat base at y67.
    - **Arrow hole:** the outline of a 5.5-wide round-capped, round-joined stroke of `M57 63V46` and
      `M50 53L57 46L64 53`. That is a stem capsule plus two 45° arm capsules. Their inner edges are
      joined at computed intersection points, and the apex is a round arc of radius 2.75 around
      (57,46).
  - It asserts that the hole lies inside the cloud, with a margin of at least 1 dp, and that every
    point lies within the 66 dp safe zone (21–87). Coordinates are rounded to 2 decimals.
  - It also writes the same geometry as a 64-unit SVG for the website (scale 64/108 inside the
    rounded square).
- [x] **`app/src/main/res/drawable/ic_launcher_foreground.xml`**
  - One `<path>` with `fillColor="#18513C"`, `fillType="evenOdd"` and the generated path data; no
    stroke and no group transform.
  - The arrow is a real hole, so the themed (monochrome) icon shows it too.
- [x] **`mipmap-anydpi-v26/ic_launcher.xml`**: unchanged. The background stays `@color/launcher_background`
  (#B5F1D1), and the foreground and monochrome layers both point to the same drawable. `minSdk 33`
  means no PNG fallbacks are needed.
- [x] **`docs/design-system.md`**, Icon specification:
  - replace "a clean silhouette, at a consistent stroke weight" with the tonal rule: a filled
    dark-sage silhouette `#18513C` (the dark theme's `primaryContainer`) on mint `#B5F1D1` (the
    light theme's `primaryContainer`), with the arrow cut out;
  - name the generator, and note that the monochrome layer reuses the foreground's alpha.
- [x] **Public `docs/icon.svg`**: the generated sage version (`#B5F1D1` rounded square, `#18513C`
  cloud with the arrow cut out) replaces the blue outline. `index.html` doesn't change, since the
  favicon, header and footer all reference the same file.
- [x] **Checkpoint on the build host:** the build, unit tests and lint (0 errors, still 53 warnings), then
  instrumentation on the test emulator.
- [x] **Visual check on the test emulator:**
  - screenshot the home screen or app drawer with the debug build installed, in light and dark mode;
  - themed icons: only simulated in the renders; the real launcher check is the deferred phone
    test below [⏳ ACK];
  - rasterize the generated website SVG with `magick` and compare it side by side with the preview's
    variant C.
- [x] **Commit locally** after the diff review (private repo). The public repo changes are committed with the
  1.1 publish.

## Spec review round 1 fixes (author: exec, 2026-11-20)

- [x] **[1] [P1] Website scale.** The SVG maps the visible 72 dp viewport, not the 108 dp canvas:
  `v' = (v - 18) * 64/72`, on the `rx 18` mint square. Assert the SVG cloud bounds are about
  8.9–55.1 × 17.3–46.7.
- [x] **[2] [P1] Shift.** The listed coordinates are pre-shift; the script adds (-3, +3.5) to every
  point. Post-shift: circles (54,52.5) r15, (38,60.5) r10, (69,59.5) r11; rect 38–69 × 50.5–70.5;
  stem x54 from y66.5 to 49.5; apex (54,49.5); arms to (47,56.5) and (61,56.5). Assert cloud bounds
  x 28–80, y 37.5–70.5, centre (54,54) ±0.01.
- [x] **[3] [P2] Exact contour.** Left circle from the base point up its left side to its top (split
  into two arcs, so no arc is exactly 180°), a short flat edge along the rect top to where it meets
  the big circle, the big circle's top arc and the right circle's arc, each split at its extreme point
  (every arc under 180°, flag 0; see Exec result), then the base line. `A` arc commands, not Béziers.
  Self-check: on a 0.25 dp grid, the even-odd fill of the emitted outline (sampled) matches the
  analytic shape (union minus arrow stroke) everywhere more than 0.1 dp from an edge.
- [x] **[4] [P2] Safe zone and margin.** Over a dense sampling of every arc and segment, assert
  `hypot(x-54, y-54) <= 33`, and a minimum hole-to-cloud-outline distance of at least 1 dp. Render
  the icon under circle (r36 of the 72 dp view) and squircle masks.
- [x] **[5] [P2] Design doc.** Keep the in-app stroke-weight rule; add the launcher tonal rule as its
  own sentence.
- [x] **[6] [P2] Outputs and public files.** `make-icon.py --svg-out PATH` writes the website SVG;
  pathData goes to stdout. No hardcoded absolute paths. At the 1.1 publish the public repo gets
  `docs/icon.svg`, `app/src/main/res/drawable/ic_launcher_foreground.xml` and `tools/make-icon.py`
  (published so the icon can be rebuilt); `check-publication.py` must pass.
- [x] **[7] [P2] Runnable visual check.** On `emulator-5560`: `cmd uimode night no|yes`, `input
  keyevent KEYCODE_HOME`, swipe up to the app drawer, `exec-out screencap -p` per mode. Renders are
  made locally with `/usr/bin/magick`, next to variant C from the preview generator
  (`scratchpad/icon-preview/gen.py`, session-only). Pass: the arrow hole is visible, the cloud is
  centred, and no mask clips it.

## Exec result (2026-11-20)

- Generator: 73,759 grid points match; max radius 27.27 ≤ 33; hole margin 1.25 dp; cloud bounds
  28–80 × 37.5–70.5. The arcs are split at their extreme points: a near-180° arc moved 0.08 dp under
  2-decimal rounding. A flipped arc flag, a flipped cap and a moved point are each caught.
- Path data is 428 characters, with no new lint warning. Checkpoint: build passed; 66 unit tests and
  22 instrumentation tests pass (after uninstalling a leftover versionCode 7 build); lint 0 errors,
  53 warnings.
- Renders: circle and squircle masks, simulated themed light and dark, and the website at 64 and 16 px
  all look right. On the emulator the light home screen shows the icon centred with the hole visible;
  the dark drawer shows the same icon.
- Public `docs/icon.svg` was replaced (uncommitted). `check-publication.py` passes on a copy with
  `make-icon.py` and the new drawable added.

## Diff review round 1 fixes (author: exec, 2026-11-20)

- [x] **[1] [P2]** Outputs are built in full before any file is opened; a failed check writes nothing.
- [x] **[2] [P2]** `--drawable-out` writes the whole XML from a template; `--check` fails when the
  drawable or website SVG differs from the generated text. The regeneration command is in the docstring
  and `design-system.md`.
- [x] **[3] [P2]** Themed-icon sub-item retagged `[⏳ ACK]`; fix [3] describes the split arcs.
- [x] **[4] [P3]** `require()` replaces `assert` (survives `python -O`); at least 70,000 grid points
  must be compared.
- [x] **[5] [P3]** Removed `APEX`; corrected the segment comment.

## Deferred empirical tests

- [⏳ ACK] **The author's phone.** With a 1.1 test build installed, the icon shows in the Pixel
  launcher's mask, in themed mode, and in dark mode.

## Pre-review exclusions (agreed in critique)

- The cloud and arrow motif stays (the author: "the icon isn't the problem, the style is").
- No gradient or lighting effects (styles A and B were not chosen). No baked-in mask.
- In-app icons (`ic_backup`, `ic_cloud` and others) are out of scope.

---

### 🔍 Assumption Lifecycle Legend:

- `[⚠️ UNVERIFIED]`: Siloed external contract. Requires indisputable evidence to verify.
- `[⏳ ACK]`: Acknowledged assumption. Factual evidence unavailable; risk accepted and opaque-box
  empirical test strictly scheduled.
