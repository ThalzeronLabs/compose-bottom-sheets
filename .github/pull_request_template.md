## What

<!-- One or two sentences. Link the issue: "Closes #123". -->

## Why

<!-- The problem this solves for someone using the library. -->

## Screenshots

<!-- If a sheet looks different, paste before / after (the new docs/screenshots/*.png is fine). -->

## Checklist

- [ ] `./gradlew :sheets:verifyRoborazziDebug` passes locally
- [ ] Screenshots re-recorded (`recordRoborazziDebug`) if the UI changed on purpose
- [ ] New public API has KDoc, and `sheets/api/sheets.api` is updated (`./gradlew :sheets:apiDump`) if the API changed
- [ ] `CHANGELOG.md` updated under `[Unreleased]`
