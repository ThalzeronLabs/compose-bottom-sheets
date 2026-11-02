package com.thalzeronlabs.sheets

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** End-to-end behaviour of [SheetHost]: real taps on rendered sheets, answers delivered to the caller. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h740dp-xhdpi")
@OptIn(ExperimentalCoroutinesApi::class) // getCompleted()
class SheetHostTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var sheets: SheetHostState

    @Test
    fun `a queued sheet slides in after the first one is answered`() {
        val answers = mutableListOf<Boolean>()
        host {
            launch { answers += sheets.confirm("First", confirmLabel = "Yes") }
            launch { answers += sheets.confirm("Second", confirmLabel = "Sure") }
        }
        compose.onNodeWithText("Yes").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Second").assertIsDisplayed()
        compose.onNodeWithText("Sure").performClick()
        compose.waitForIdle()
        assertEquals(listOf(true, true), answers)
        assertNull(sheets.current)
    }

    @Test
    fun `the progress sheet closes itself and hands back the result`() {
        val gate = CompletableDeferred<Unit>()
        val result = CompletableDeferred<String>()
        host {
            val upload = async { sheets.withProgress("Uploading") { fraction = 0.5f; gate.await(); "done" } }
            result.complete(upload.await())
        }
        compose.onNodeWithText("Uploading").assertIsDisplayed()

        gate.complete(Unit)
        compose.waitForIdle()
        assertTrue(result.isCompleted)
        assertEquals("done", result.getCompleted())
        assertEquals(0, compose.onAllNodes(hasText("Uploading")).fetchSemanticsNodes().size)
    }

    @Test
    fun `a custom sheet returns what its content answers`() {
        val result = CompletableDeferred<Int?>()
        host {
            result.complete(
                sheets.custom<Int>("Pick a number") {
                    Button(onClick = { answer(7) }) { Text("Seven") }
                },
            )
        }
        compose.onNodeWithText("Seven").performClick()
        compose.waitForIdle()
        assertEquals(7, result.getCompleted())
    }

    @Test
    fun `rating a sheet returns the tapped star`() {
        val result = CompletableDeferred<Int?>()
        host { result.complete(sheets.rate("Rate it")) }
        compose.onNode(hasContentDescription("3 of 5 stars")).performClick()
        compose.onNodeWithText("Submit").performClick()
        compose.waitForIdle()
        assertEquals(3, result.getCompleted())
    }

    private fun host(script: suspend CoroutineScope.() -> Unit) {
        compose.setContent {
            MaterialTheme {
                sheets = remember { SheetHostState() }
                SheetHost(sheets)
                LaunchedEffect(Unit) { script() }
            }
        }
        compose.waitForIdle()
    }
}
