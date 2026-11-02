package com.thalzeronlabs.sheets

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The sheets draw with the app's `MaterialTheme`, and a per-call `accent` wins for that sheet only. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h740dp-xhdpi")
class SheetThemeTest {

    @get:Rule
    val compose = createComposeRule()

    private val appPrimary = Color(0xFF006A60)
    private val accent = Color(0xFFA15C00)

    @Test
    fun `buttons use the app theme's primary color`() {
        open { confirm("Archive?", confirmLabel = "Archive") }
        assertEquals(appPrimary, fillOf("Archive"))
    }

    @Test
    fun `an accent replaces primary for that sheet`() {
        open { confirm("Archive?", confirmLabel = "Archive", accent = accent) }
        assertEquals(accent, fillOf("Archive"))
    }

    @Test
    fun `the accent reaches the pickers and new sheets too`() {
        open { rate("Rate your stay", initial = 3, accent = accent) }
        assertEquals(accent, fillOf("Submit"))
    }

    private fun open(ask: suspend SheetHostState.() -> Unit) {
        compose.setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = appPrimary)) {
                val sheets = remember { SheetHostState() }
                SheetHost(sheets)
                LaunchedEffect(Unit) { sheets.ask() }
            }
        }
        compose.waitForIdle()
    }

    /** The color of a button's container, sampled left of its centered label. */
    private fun fillOf(label: String): Color {
        val pixels = compose.onNodeWithText(label).captureToImage().toPixelMap()
        return pixels[pixels.width / 10, pixels.height / 2]
    }
}
