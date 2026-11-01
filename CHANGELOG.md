# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - 2026-11-01

First public release.

### Added

- `SheetHostState` with suspend functions `confirm`, `choose`, `pickDate`, `pickTime` and `input`, drawn by
  `SheetHost` as Material 3 `ModalBottomSheet`s.
- One sheet at a time (later requests queue), cancellation closes the sheet, swipe-to-dismiss answers `false` / `null`.
- Publishing through JitPack: `com.github.ThalzeronLabs:compose-bottom-sheets:v0.1.0`.
- Screenshot tests with Robolectric + Roborazzi; the images in `docs/screenshots/` are verified in CI.
- Dokka API docs and a GitHub Pages workflow.
- `CONTRIBUTING.md`, issue and pull request templates.

### Fixed

- The date picker no longer clips its last column (Saturday) on 360dp-wide screens; it now uses the sheet's full
  width. Found by the new screenshot tests.

[Unreleased]: https://github.com/ThalzeronLabs/compose-bottom-sheets/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/ThalzeronLabs/compose-bottom-sheets/releases/tag/v0.1.0
