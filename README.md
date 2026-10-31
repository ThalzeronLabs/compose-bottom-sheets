# compose-bottom-sheets

**Bottom sheets you can `await` in Jetpack Compose — confirm, choose, date, time and text input, one line each.**

Dialogs in the middle of the screen are awkward on tall phones. Bottom sheets sit where the thumb is, but wiring each
one by hand means a `Boolean` flag, a callback and a lot of boilerplate per question. This library turns every sheet
into a `suspend` call, the same way `SnackbarHostState` works for snackbars:

```kotlin
val sheets = remember { SheetHostState() }
SheetHost(sheets)   // once per screen

Button(onClick = {
    scope.launch {
        if (!sheets.confirm("Delete this note?", "You can't undo this.", confirmLabel = "Delete", destructive = true)) return@launch
        viewModel.delete(note)
    }
}) { Text("Delete") }
```

## What's inside

| Call | Returns | Draws |
|---|---|---|
| `confirm(title, message, destructive)` | `Boolean` | two full-width buttons (red when destructive) |
| `choose(title, options)` | the option's typed value, or `null` | a tappable list with descriptions |
| `pickDate(title, initial)` | UTC epoch millis, or `null` | the Material 3 `DatePicker` inside a sheet |
| `pickTime(title, initial, is24Hour)` | `TimeOfDay`, or `null` | the Material 3 `TimePicker` inside a sheet |
| `input(title, initial, validate)` | `String`, or `null` | a text field with live validation |

```kotlin
val sort = sheets.choose("Sort by", listOf(SheetOption("Newest", Sort.NEW), SheetOption("Oldest", Sort.OLD)))
val due = sheets.pickDate("Due date") ?: return
val at = sheets.pickTime("Remind me at", TimeOfDay(9, 0))
val name = sheets.input("Rename", note.title, validate = { if (it.isBlank()) "Required" else null })
```

## Behaviour you can rely on

- **One sheet at a time.** A second request waits in line until the first is answered.
- **Cancellation-safe.** Cancel the calling coroutine (e.g. the screen leaves) and its sheet closes.
- **Swipe to dismiss = no answer.** `confirm` returns `false`, everything else returns `null`.
- **The sheet animates out before your code continues**, so the next UI change never fights the closing sheet.
- **Theming comes from your `MaterialTheme`.**

## Tests

The request queue, answers, dismissals and cancellation are covered by 10 plain JVM unit tests:

```bash
./gradlew :sheets:testDebugUnitTest
```

## License

Apache-2.0 © Esdras Souza (Thalzeron Labs).
