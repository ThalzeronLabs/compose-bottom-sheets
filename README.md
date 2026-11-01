# compose-bottom-sheets

[![CI](https://github.com/ThalzeronLabs/compose-bottom-sheets/actions/workflows/ci.yml/badge.svg)](https://github.com/ThalzeronLabs/compose-bottom-sheets/actions/workflows/ci.yml)
[![JitPack](https://jitpack.io/v/ThalzeronLabs/compose-bottom-sheets.svg)](https://jitpack.io/#ThalzeronLabs/compose-bottom-sheets)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
![minSdk 23](https://img.shields.io/badge/minSdk-23-brightgreen.svg)
![Jetpack Compose · Material 3](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4.svg)
<!-- Uncomment once GitHub Pages is enabled (Settings → Pages → Source: GitHub Actions):
[![API docs](https://img.shields.io/badge/API%20docs-Dokka-7F52FF.svg)](https://thalzeronlabs.github.io/compose-bottom-sheets/)
-->

**Bottom sheets you can `await` in Jetpack Compose — confirm, choose, date, time and text input, one line each.**

Dialogs in the middle of the screen are awkward on tall phones. Bottom sheets sit where the thumb is, but wiring each
one by hand means a `Boolean` flag, a callback and a lot of boilerplate per question. This library turns every sheet
into a `suspend` call, the same way `SnackbarHostState` works for snackbars.

## Install

Add JitPack to your repositories (`settings.gradle.kts`):

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

Then the one line (`app/build.gradle.kts`):

```kotlin
implementation("com.github.ThalzeronLabs:compose-bottom-sheets:v0.1.0")
```

`minSdk` 23. Built against `compileSdk` 37 and Compose BOM `2026.09.00`; the theme comes from your `MaterialTheme`.

## Quick start

**Before** — one flag, one sheet state and one callback per question:

```kotlin
var askDelete by remember { mutableStateOf(false) }
val sheetState = rememberModalBottomSheetState()
val scope = rememberCoroutineScope()

Button(onClick = { askDelete = true }) { Text("Delete") }

if (askDelete) {
    ModalBottomSheet(onDismissRequest = { askDelete = false }, sheetState = sheetState) {
        Text("Delete this note?")
        Button(onClick = {
            scope.launch { sheetState.hide() }.invokeOnCompletion {
                askDelete = false
                viewModel.delete(note)
            }
        }) { Text("Delete") }
        OutlinedButton(onClick = {
            scope.launch { sheetState.hide() }.invokeOnCompletion { askDelete = false }
        }) { Text("Cancel") }
    }
}
```

**After** — the question is a function call and the answer is its return value:

```kotlin
val sheets = remember { SheetHostState() }
val scope = rememberCoroutineScope()
SheetHost(sheets)   // once per screen

Button(onClick = {
    scope.launch {
        if (sheets.confirm("Delete this note?", "You can't undo this.", confirmLabel = "Delete", destructive = true)) {
            viewModel.delete(note)
        }
    }
}) { Text("Delete") }
```

## What's inside

| Call | Returns | Draws |
|---|---|---|
| `confirm(title, message, destructive)` | `Boolean` | two full-width buttons (red when destructive) |
| `choose(title, options)` | the option's typed value, or `null` | a tappable list with descriptions |
| `pickDate(title, initialUtcMillis)` | UTC epoch millis, or `null` | the Material 3 `DatePicker` inside a sheet |
| `pickTime(title, initial, is24Hour)` | `TimeOfDay`, or `null` | the Material 3 `TimePicker` inside a sheet |
| `input(title, initial, validate)` | `String`, or `null` | a text field with live validation |

```kotlin
val sort = sheets.choose("Sort by", listOf(SheetOption("Newest", Sort.NEW), SheetOption("Oldest", Sort.OLD)))
val due = sheets.pickDate("Due date") ?: return
val at = sheets.pickTime("Remind me at", TimeOfDay(9, 0))
val name = sheets.input("Rename", note.title, validate = { if (it.isBlank()) "Required" else null })
```

## Gallery

Every image below is rendered by a test ([`SheetScreenshotTest`](sheets/src/test/kotlin/com/thalzeronlabs/sheets/SheetScreenshotTest.kt))
on the JVM with Robolectric + [Roborazzi](https://github.com/takahirom/roborazzi), no emulator. CI runs
`verifyRoborazziDebug`, so a pull request that changes how a sheet looks fails until the images are re-recorded.

| `confirm` | `choose` | `pickDate` | `pickTime` | `input` |
|---|---|---|---|---|
| <img src="docs/screenshots/confirm.png" width="150" alt="confirm"> | <img src="docs/screenshots/choose.png" width="150" alt="choose"> | <img src="docs/screenshots/pick-date.png" width="150" alt="pickDate"> | <img src="docs/screenshots/pick-time.png" width="150" alt="pickTime"> | <img src="docs/screenshots/input.png" width="150" alt="input"> |

## Why suspend sheets

- **The flow reads top to bottom.** "Ask for a name, then a due date, then save" is three lines in one coroutine,
  not three flags and three callbacks spread across the composable.
- **The answer is typed.** `choose` returns your `T`, `pickTime` returns a `TimeOfDay`, a dismissal is `null`.
  No `Any` payloads, no state to reset after the sheet closes.
- **Cancellation is free.** The sheet belongs to the coroutine that asked. If the screen leaves and its scope is
  cancelled, the sheet closes with it.
- **It is testable without UI.** `SheetHostState` is plain state: a test calls `confirm(...)`, reads `current`, and
  resolves it. The 10 JVM unit tests in this repo do exactly that.
- **It's a pattern you already know.** `SnackbarHostState.showSnackbar()` works the same way; this applies it to sheets.

## Behaviour you can rely on

- **One sheet at a time.** A second request waits in line until the first is answered.
- **Cancellation-safe.** Cancel the calling coroutine (e.g. the screen leaves) and its sheet closes.
- **Swipe to dismiss = no answer.** `confirm` returns `false`, everything else returns `null`.
- **The sheet animates out before your code continues**, so the next UI change never fights the closing sheet.
- **Theming comes from your `MaterialTheme`.**

## Tests

```bash
./gradlew :sheets:verifyRoborazziDebug
```

That runs the 10 unit tests for the request queue, answers, dismissals and cancellation, plus the 5 screenshot
tests. After an intentional UI change, re-record the images with `./gradlew :sheets:recordRoborazziDebug`.

## Contributing

Issues labelled [good first issue](https://github.com/ThalzeronLabs/compose-bottom-sheets/labels/good%20first%20issue)
are scoped to a single afternoon. See [CONTRIBUTING.md](CONTRIBUTING.md) and the [changelog](CHANGELOG.md).

## License

Apache-2.0 © Esdras Souza (Thalzeron Labs).
