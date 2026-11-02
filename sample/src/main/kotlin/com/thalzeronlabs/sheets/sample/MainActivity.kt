package com.thalzeronlabs.sheets.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.thalzeronlabs.sheets.SheetHost
import com.thalzeronlabs.sheets.SheetHostState
import com.thalzeronlabs.sheets.SheetOption
import com.thalzeronlabs.sheets.SheetScope
import com.thalzeronlabs.sheets.TimeOfDay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone

/** Every sheet of the library, one button each; the last answer is shown at the top. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                SampleScreen()
            }
        }
    }
}

@Composable
private fun SampleScreen() {
    val sheets = remember { SheetHostState() }
    val scope = rememberCoroutineScope()
    var answer by remember { mutableStateOf("Tap a button to open a sheet.") }

    fun ask(block: suspend () -> String) {
        scope.launch { answer = block() }
    }

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("compose-bottom-sheets", style = MaterialTheme.typography.headlineSmall)
            Text(answer, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))

            DemoButton("confirm") {
                ask {
                    val delete = sheets.confirm(
                        "Delete this note?",
                        "You can't undo this.",
                        confirmLabel = "Delete",
                        destructive = true,
                    )
                    "confirm → $delete"
                }
            }
            DemoButton("choose") {
                ask {
                    val sort = sheets.choose(
                        "Sort notes by",
                        listOf(
                            SheetOption("Last edited", "edited", "Most recent first"),
                            SheetOption("Title", "title", "A to Z"),
                        ),
                    )
                    "choose → $sort"
                }
            }
            DemoButton("pickDate") {
                ask { "pickDate → ${sheets.pickDate("Due date")?.let(::formatUtcDay)}" }
            }
            DemoButton("pickDateRange") {
                ask {
                    val trip = sheets.pickDateRange("Trip dates")
                    "pickDateRange → " + trip?.let {
                        "${formatUtcDay(it.startUtcMillis)} – ${formatUtcDay(it.endUtcMillis)} (${it.days} days)"
                    }
                }
            }
            DemoButton("pickTime") {
                ask { "pickTime → ${sheets.pickTime("Remind me at", TimeOfDay(9, 30))}" }
            }
            DemoButton("input") {
                ask {
                    val name = sheets.input(
                        "Rename note",
                        initial = "Groceries",
                        label = "Title",
                        validate = { if (it.isBlank()) "A title is required" else null },
                    )
                    "input → $name"
                }
            }
            DemoButton("rate (with accent)") {
                ask {
                    val stars = sheets.rate("Rate your stay", "How was your trip?", accent = Color(0xFFA15C00))
                    "rate → $stars"
                }
            }
            DemoButton("withProgress") {
                ask {
                    val uploaded = sheets.withProgress("Uploading photos", "0 of 3") {
                        repeat(3) { i ->
                            delay(700)
                            fraction = (i + 1) / 3f
                            message = "${i + 1} of 3"
                        }
                        3
                    }
                    "withProgress → $uploaded uploaded"
                }
            }
            DemoButton("custom") {
                ask { "custom → ${sheets.custom<Int>("Quantity") { QuantityPicker() }}" }
            }
        }
    }
    SheetHost(sheets)
}

@Composable
private fun DemoButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(label) }
}

/** Content for `custom`: a stepper and a button that answers with the count. */
@Composable
private fun SheetScope<Int>.QuantityPicker() {
    var count by rememberSaveable { mutableIntStateOf(1) }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedIconButton(onClick = { if (count > 1) count-- }, Modifier.semantics { contentDescription = "Fewer" }) {
            Text("−")
        }
        Text("$count", style = MaterialTheme.typography.headlineMedium)
        OutlinedIconButton(onClick = { count++ }, Modifier.semantics { contentDescription = "More" }) {
            Text("+")
        }
    }
    Spacer(Modifier.height(16.dp))
    Button(onClick = { answer(count) }, Modifier.fillMaxWidth().height(52.dp)) { Text("Add to cart") }
}

/** The pickers answer UTC midnight; format it in UTC so the day doesn't shift with the device's time zone. */
private fun formatUtcDay(utcMillis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(utcMillis))
