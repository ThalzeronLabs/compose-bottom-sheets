package com.thalzeronlabs.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.awaitCancellation
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

    @Test
    fun pickDateRange() = snapshot("pick-date-range") {
        pickDateRange(
            title = "Trip dates",
            initial = DateRange(MARCH_12_2026_UTC, MARCH_12_2026_UTC + 3 * DAY),
        )
    }

    @Test
    fun rate() = snapshot("rate") {
        // Also shows a per-call accent: the stars and the button take it, the rest of the theme stays the app's.
        rate(
            title = "Rate your stay",
            message = "How was your trip to Lisbon?",
            initial = 4,
            accent = DEEP_AMBER,
        )
    }

    @Test
    fun progress() = snapshot("progress") {
        withProgress(title = "Uploading photos", message = "2 of 3 uploaded") {
            fraction = 2 / 3f
            awaitCancellation()
        }
    }

    @Test
    fun custom() = snapshot("custom") {
        custom<Int>(title = "Quantity") { QuantityPicker() }
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
        const val DAY = 86_400_000L
        val DEEP_AMBER = Color(0xFFA15C00)
    }
}

/** What an app might pass to `custom`: a stepper and a button that answers with the count. */
@Composable
private fun SheetScope<Int>.QuantityPicker() {
    var count by remember { mutableIntStateOf(2) }
    Text(
        "Lisbon city card, 72 h",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedIconButton(onClick = { if (count > 1) count-- }, Modifier.semantics { contentDescription = "Fewer" }) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text("$count", style = MaterialTheme.typography.headlineMedium)
        OutlinedIconButton(onClick = { count++ }, Modifier.semantics { contentDescription = "More" }) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
    Spacer(Modifier.height(20.dp))
    Button(onClick = { answer(count) }, Modifier.fillMaxWidth().height(52.dp), shape = MaterialTheme.shapes.large) {
        Text("Add to cart · €${count * 29}")
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
