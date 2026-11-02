package com.thalzeronlabs.sheets

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/**
 * Applies a per-sheet [accent] on top of the app's `MaterialTheme`. With no accent the app theme is used untouched,
 * so typography, shapes and every other color always come from the app.
 */
@Composable
internal fun AccentTheme(accent: Color?, content: @Composable () -> Unit) {
    if (accent == null || !accent.isSpecified) {
        content()
        return
    }
    val base = MaterialTheme.colorScheme
    val scheme = remember(base, accent) { base.withAccent(accent) }
    MaterialTheme(colorScheme = scheme, shapes = MaterialTheme.shapes, typography = MaterialTheme.typography, content = content)
}

/**
 * Swaps `primary` (buttons, selected day, stars, focused text field, progress bar) for [accent] and derives a soft
 * container from it (time picker fields, date range band). "On" colors are black or white, whichever reads better,
 * so text on the accent always has a WCAG contrast of at least 4.5:1.
 */
internal fun ColorScheme.withAccent(accent: Color): ColorScheme {
    val solid = accent.copy(alpha = 1f).compositeOver(surface)
    val container = lerp(surfaceContainerLow, solid, ContainerAccentFraction)
    return copy(
        primary = solid,
        onPrimary = readableOn(solid),
        primaryContainer = container,
        onPrimaryContainer = readableOn(container),
        secondaryContainer = container,
        onSecondaryContainer = readableOn(container),
        surfaceTint = solid,
    )
}

/** Black or white, whichever has the higher contrast against [background] (always at least 4.58:1). */
internal fun readableOn(background: Color): Color =
    if (contrastRatio(Color.White, background) >= contrastRatio(Color.Black, background)) Color.White else Color.Black

/** WCAG 2.x contrast ratio between two opaque colors, from 1 (same) to 21 (black on white). */
internal fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
}

private const val ContainerAccentFraction = 0.3f
