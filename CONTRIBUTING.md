# Contributing

Thanks for taking the time! This is a small library, so small pull requests are the norm and are reviewed quickly.

## Find something to work on

- Issues labelled [`good first issue`](https://github.com/ThalzeronLabs/compose-bottom-sheets/labels/good%20first%20issue)
  each describe the change, the files involved and how to know you're done.
- For anything bigger (a new sheet type, an API change), please open an issue first so we can agree on the API
  before you write the code.
- Comment on the issue you pick so nobody else starts the same work.

## Set up

You need JDK 21 (Robolectric needs it for Android 16 screenshots) and the Android SDK; Android Studio ships both.

```bash
git clone https://github.com/ThalzeronLabs/compose-bottom-sheets.git
cd compose-bottom-sheets
./gradlew :sheets:verifyRoborazziDebug
```

That one command runs everything CI runs for tests:

- `SheetHostStateTest` — plain JVM tests of the request queue, answers, dismissal and cancellation.
- `SheetAccessibilityTest` — one test per sheet: labels, 48dp touch targets, reading order.
- `SheetContrastTest` — WCAG contrast of the color pairs the sheets draw.
- `SheetHostTest` — real taps on rendered sheets (queueing, answers, the progress sheet closing itself).
- `SheetThemeTest` — the app's `MaterialTheme` and per-call accents reach the rendered sheet.
- `SheetScreenshotTest` — renders each sheet with Robolectric and compares it with `docs/screenshots/*.png`.

## Screenshots

The PNGs in `docs/screenshots/` are both the README gallery and the expected output of the screenshot tests.

- If you **changed how a sheet looks on purpose**, re-record and commit the new images:

  ```bash
  ./gradlew :sheets:recordRoborazziDebug
  ```

- If `verifyRoborazziDebug` fails and you **didn't** mean to change the UI, look at
  `sheets/build/outputs/roborazzi/*_compare.png` (CI uploads them as the `screenshot-diffs` artifact).
- A **new sheet type** should come with a new test in `SheetScreenshotTest`, one in `SheetAccessibilityTest`, and a
  cell in the README gallery.

## Public API

`sheets/api/sheets.api` is the library's public API, as the binary-compatibility-validator sees it in the release AAR.
CI runs `./gradlew :sheets:apiCheck` and fails when it changes.

- Changed the API **on purpose**? Run `./gradlew :sheets:apiDump` and commit the updated file: the diff shows reviewers
  exactly what changed for users.
- Didn't mean to? Make the new declaration `internal` or `private`.

## Code style

- Kotlin official code style (`kotlin.code.style=official`), 4-space indent, max line length ~120.
- Public API gets a KDoc comment — it ends up in the Dokka docs (`./gradlew :sheets:dokkaGenerate`).
- Keep the logic in `SheetHostState` (testable without UI) and the drawing in `SheetHost`.
- Code, comments, commit messages and docs are in English.

## Pull requests

1. Branch from `main`.
2. Keep the PR focused on one change; fill in the PR template.
3. Add a line under `## [Unreleased]` in [CHANGELOG.md](CHANGELOG.md).
4. CI must be green: unit + screenshot tests, release AAR build, Maven publication and Dokka.

PRs are squash-merged, so the PR title becomes the commit message — write it in the imperative ("Add multi-choice sheet").

## Releases

Maintainers move the `[Unreleased]` notes under a new version heading in `CHANGELOG.md`, then either push a `vX.Y.Z` tag or run the `Release` workflow on `main` from the Actions tab.
It creates the GitHub release from that changelog section; JitPack builds the tag on first request.
The project follows [Semantic Versioning](https://semver.org/); while it's `0.x`, minor versions may change the API.

## Code of conduct

Be kind and assume good intent. Harassment or personal attacks aren't tolerated.
