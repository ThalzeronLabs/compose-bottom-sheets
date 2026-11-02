# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Public API guard: the binary-compatibility-validator dumps the public API to `sheets/api/sheets.api`, and CI runs
  `./gradlew :sheets:apiCheck`, so an API change can't slip in unnoticed. Update the dump with `./gradlew :sheets:apiDump`.

- `sample/`: a one-screen app with a button for every sheet (`./gradlew :sample:installDebug`). CI builds it against
  the library, and a new **Consumer check** workflow builds it against the artifact JitPack serves for the latest
  release (after every release, weekly, and on demand).
- The API docs (Dokka) are published on GitHub Pages: https://thalzeronlabs.github.io/compose-bottom-sheets/

### Fixed

- Text typed into `input` and the star picked in `rate` were lost when the activity was recreated (rotation, dark mode,
  language) while the sheet was open; they are now saved like the pickers' state. `SheetRecreationTest` covers every
  sheet across a real recreation and emulated process death, with the state in a ViewModel or in `remember`.

### Removed

- Two internal constants (`DateRange.DAY_MILLIS`, `SheetHostState.HIDE_TIMEOUT_MS`) that leaked as public static fields
  in 0.2.0. They were never meant to be API.

## [0.2.0] - 2026-11-02

New sheets, per-call accents and accessibility checks for every sheet.

### Added

- `pickDateRange(title, initial)`: the Material 3 `DateRangePicker` in a sheet, returning a `DateRange`
  (`startUtcMillis`, `endUtcMillis`, `days`).
- `rate(title, message, stars, initial)`: tappable stars (1 to 10), returning the rating. Each star is a 56dp radio
  button read as "3 of 5 stars"; `starLabel` lets apps translate that.
- `withProgress(title, message) { ... }`: a progress sheet that closes itself when the block returns or throws, and
  returns the block's result. The block can set `fraction` (determinate bar) and `message` (announced politely by
  TalkBack). It can't be swiped away; opening another sheet from inside the block fails fast instead of deadlocking.
- `custom<T>(title) { ... }`: your own composable content with `answer(value)` / `dismiss()`, returning a typed `T?`.
- `accent: Color?` on every sheet: replaces `primary` for that sheet only; the text on it is black or white, whichever
  meets WCAG AA.
- `SheetAccessibilityTest`: one test per sheet for labels, 48dp touch targets, reading order and sheet-specific
  semantics. `SheetContrastTest`: WCAG contrast of the color pairs in light, dark and accented schemes.
  `SheetThemeTest`: the app theme and accents reach the rendered sheet. `SheetHostTest`: end-to-end taps on rendered
  sheets.
- Screenshots for the new sheets (`pick-date-range`, `rate`, `progress`, `custom`) in the README gallery.

### Changed

- Buttons take their corner shape from `MaterialTheme.shapes.large` instead of a fixed 16dp, so an app's shapes apply
  (unchanged with the default theme).

### Fixed

- A sheet queued behind another one stayed hidden after the first was answered (it reused the first sheet's hidden
  state), so its caller waited forever. Each request now gets a fresh sheet state. Covered by `SheetHostTest`.

## [0.1.0] - 2026-11-01

First public release.

### Added

- `SheetHostState` with suspend functions `confirm`, `choose`, `pickDate`, `pickTime` and `input`, drawn by
  `SheetHost` as Material 3 `ModalBottomSheet`s.
- One sheet at a time (later requests queue), cancellation closes the sheet, swipe-to-dismiss answers `false` / `null`.
- Publishing through JitPack: `com.github.ThalzeronLabs:compose-bottom-sheets:v0.1.0`.
- Screenshot tests with Robolectric + Roborazzi; the images in `docs/screenshots/` are verified in CI.
- Dokka API docs and a GitHub Pages workflow.
- A release workflow (tag push or manual run) that creates the GitHub release from this changelog, with the AAR attached.
- `CONTRIBUTING.md`, issue and pull request templates.

### Fixed

- The date picker no longer clips its last column (Saturday) on 360dp-wide screens; it now uses the sheet's full
  width. Found by the new screenshot tests.

[Unreleased]: https://github.com/ThalzeronLabs/compose-bottom-sheets/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/ThalzeronLabs/compose-bottom-sheets/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/ThalzeronLabs/compose-bottom-sheets/releases/tag/v0.1.0
