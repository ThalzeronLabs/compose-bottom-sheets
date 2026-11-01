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
- `SheetScreenshotTest` — renders each sheet with Robolectric and compares it with `docs/screenshots/*.png`.

## Screenshots

The PNGs in `docs/screenshots/` are both the README gallery and the expected output of the screenshot tests.

- If you **changed how a sheet looks on purpose**, re-record and commit the new images:

  ```bash
  ./gradlew :sheets:recordRoborazziDebug
  ```

- If `verifyRoborazziDebug` fails and you **didn't** mean to change the UI, look at
  `sheets/build/outputs/roborazzi/*_compare.png` (CI uploads them as the `screenshot-diffs` artifact).
- A **new sheet type** should come with a new test in `SheetScreenshotTest` and a row in the README gallery.

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

Maintainers tag `vX.Y.Z` on `main` and create a GitHub release; JitPack builds the tag on first request.
The project follows [Semantic Versioning](https://semver.org/); while it's `0.x`, minor versions may change the API.

## Code of conduct

Be kind and assume good intent. Harassment or personal attacks aren't tolerated.
