package com.thalzeronlabs.sheets

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG 2.x contrast of the color pairs the sheets draw: 4.5:1 for text, 3:1 for icons such as the outlined star.
 * Runs on the default Material 3 schemes and on every accent below.
 */
class SheetContrastTest {

    private val schemes = mapOf("light" to lightColorScheme(), "dark" to darkColorScheme())

    private val accents = listOf(
        Color(0xFFA15C00), // the deep amber used in the README
        Color(0xFF00796B),
        Color(0xFF1565C0),
        Color(0xFFFFB300), // light: needs black text on it
        Color(0xFF7F7F7F), // mid grey: the hardest case for black / white
        Color.Black,
        Color.White,
        Color(0x80E91E63), // translucent accents are made opaque first
    )

    @Test
    fun `text and icons on the sheet meet WCAG AA in the default schemes`() {
        schemes.forEach { (name, scheme) ->
            val sheet = scheme.surfaceContainerLow // ModalBottomSheet's container color
            assertContrast("$name: title and options", scheme.onSurface, sheet, TEXT)
            assertContrast("$name: messages and descriptions", scheme.onSurfaceVariant, sheet, TEXT)
            assertContrast("$name: destructive option", scheme.error, sheet, TEXT)
            assertContrast("$name: outlined button label", scheme.primary, sheet, TEXT)
            assertContrast("$name: filled button label", scheme.onPrimary, scheme.primary, TEXT)
            assertContrast("$name: destructive button label", scheme.onError, scheme.error, TEXT)
            assertContrast("$name: empty star outline", scheme.onSurfaceVariant, sheet, ICON)
            assertContrast("$name: filled star", scheme.primary, sheet, ICON)
        }
    }

    @Test
    fun `text drawn on an accent is always readable`() {
        schemes.forEach { (name, base) ->
            accents.forEach { accent ->
                val scheme = base.withAccent(accent)
                val label = "$name + ${accent.hex()}"
                assertEquals("$label: accent is opaque", 1f, scheme.primary.alpha)
                assertContrast("$label: button label", scheme.onPrimary, scheme.primary, TEXT)
                assertContrast("$label: time field", scheme.onPrimaryContainer, scheme.primaryContainer, TEXT)
                assertContrast("$label: date range band", scheme.onSecondaryContainer, scheme.secondaryContainer, TEXT)
            }
        }
    }

    @Test
    fun `an accent only touches primary and its containers`() {
        val base = lightColorScheme()
        val accented = base.withAccent(Color(0xFF00796B))
        assertEquals(Color(0xFF00796B), accented.primary)
        assertEquals(base.surface, accented.surface)
        assertEquals(base.onSurface, accented.onSurface)
        assertEquals(base.error, accented.error)
        assertEquals(base.tertiary, accented.tertiary)
    }

    @Test
    fun `contrast ratio matches the WCAG reference values`() {
        assertEquals(21f, contrastRatio(Color.Black, Color.White), 0.01f)
        assertEquals(1f, contrastRatio(Color(0xFF6750A4), Color(0xFF6750A4)), 0.0001f)
        assertEquals(contrastRatio(Color.Red, Color.White), contrastRatio(Color.White, Color.Red), 0.0001f)
        assertEquals(Color.White, readableOn(Color(0xFF1565C0)))
        assertEquals(Color.Black, readableOn(Color(0xFFFFB300)))
    }

    private fun assertContrast(what: String, foreground: Color, background: Color, minimum: Float) {
        val ratio = contrastRatio(foreground, background)
        assertTrue("$what: ${"%.2f".format(ratio)}:1 is below $minimum:1", ratio >= minimum)
    }

    private fun Color.hex() = "#%08X".format(
        ((alpha * 255).toInt() shl 24) or ((red * 255).toInt() shl 16) or ((green * 255).toInt() shl 8) or (blue * 255).toInt(),
    )

    private companion object {
        const val TEXT = 4.5f
        const val ICON = 3f
    }
}
