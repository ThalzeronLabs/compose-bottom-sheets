package com.thalzeronlabs.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every sheet on the JVM (Robolectric native graphics) and stores the PNGs in `docs/screenshots/`.
 *
 * - `./gradlew :sheets:recordRoborazziDebug` rewrites the images after an intentional UI change.
 * - `./gradlew :sheets:verifyRoborazziDebug` (run in CI) fails when a sheet no longer matches its image.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h740dp-xhdpi")
@OptIn(ExperimentalRoborazziApi::class)
class SheetScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun confirm() = snapshot("confirm") {
        confirm(
            title = "Delete this note?",
            message = "\"Groceries\" will be removed from all your devices. You can't undo this.",
            confirmLabel = "Delete",
            destructive = true,
        )
    }

    @Test
    fun choose() = snapshot("choose") {
        choose(
            title = "Sort notes by",
            options = listOf(
                SheetOption("Last edited", 0, "Most recently changed first"),
                SheetOption("Date created", 1, "Newest first"),
                SheetOption("Title", 2, "A to Z"),
                SheetOption("Move all to trash", 3, destructive = true),
            ),
        )
    }

    @Test
    fun pickDate() = snapshot("pick-date") {
        // A fixed month in the past keeps the image stable: no "today" ring, no drift as the calendar moves on.
        pickDate(title = "Due date", initialUtcMillis = MARCH_12_2026_UTC)
    }

    @Test
    fun pickTime() = snapshot("pick-time") {
        pickTime(title = "Remind me at", initial = TimeOfDay(9, 30))
    }

    @Test
    fun input() = snapshot("input") {
        input(
            title = "Rename note",
            initial = "Groceries",
            label = "Title",
            validate = { if (it.isBlank()) "A title is required" else null },
        )
    }

    private fun snapshot(name: String, ask: suspend SheetHostState.() -> Unit) {
        compose.setContent {
            MaterialTheme {
                val sheets = remember { SheetHostState() }
                FakeScreen()
                SheetHost(sheets)
                LaunchedEffect(Unit) { sheets.ask() }
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("${SCREENSHOT_DIR}/$name.png")
    }

    private companion object {
        const val SCREENSHOT_DIR = "../docs/screenshots"
        const val MARCH_12_2026_UTC = 1_773_273_600_000L
    }
}

/** A plain app screen behind the sheet so the screenshots show the scrim in context. */
@Composable
private fun FakeScreen() {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.statusBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Notes", style = MaterialTheme.typography.headlineMedium)
            listOf("Groceries", "Trip to Lisbon", "Book club picks").forEach {
                Text(it, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
