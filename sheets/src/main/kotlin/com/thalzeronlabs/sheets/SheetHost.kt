package com.thalzeronlabs.sheets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DateRangePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.NumberFormat
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws whatever [state] is asking for as a Material 3 `ModalBottomSheet`.
 * Put it once near the root of a screen, inside your `MaterialTheme`; it renders nothing while no sheet is requested.
 *
 * Colors, typography and shapes come from the surrounding `MaterialTheme`; a request's `accent` overrides
 * `primary` for that one sheet.
 */
@Composable
fun SheetHost(state: SheetHostState) {
    val request = state.current ?: return
    // A fresh sheet state per request: a queued sheet must slide in again even if it follows another one directly.
    key(request) {
        AccentTheme(request.accent) { Sheet(request) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(request: SheetRequest<*>) {
    val progress = request as? SheetRequest.Progress
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || progress == null || progress.finished },
    )
    val scope = rememberCoroutineScope()

    // Animate the sheet down first, then hand the answer back to the caller.
    fun finish(answer: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { answer() }
    }

    if (progress != null) {
        LaunchedEffect(progress) {
            snapshotFlow { progress.finished }.first { it }
            try {
                sheetState.hide()
            } finally {
                progress.resolver.resolve(Unit)
            }
        }
    }

    val dismissible = progress == null
    ModalBottomSheet(
        onDismissRequest = { request.dismiss() },
        sheetState = sheetState,
        sheetGesturesEnabled = dismissible,
        dragHandle = if (dismissible) ({ BottomSheetDefaults.DragHandle() }) else null,
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = dismissible,
            shouldDismissOnClickOutside = dismissible,
        ),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SheetPadding)
                .padding(top = if (dismissible) 0.dp else SheetPadding, bottom = 24.dp)
                .navigationBarsPadding(),
        ) {
            Text(request.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            when (request) {
                is SheetRequest.Confirm -> ConfirmContent(request, ::finish)
                is SheetRequest.Choose<*> -> ChooseContent(request, ::finish)
                is SheetRequest.PickDate -> DateContent(request, ::finish)
                is SheetRequest.PickDateRange -> DateRangeContent(request, ::finish)
                is SheetRequest.PickTime -> TimeContent(request, ::finish)
                is SheetRequest.Input -> InputContent(request, ::finish)
                is SheetRequest.Rate -> RateContent(request, ::finish)
                is SheetRequest.Progress -> ProgressContent(request)
                is SheetRequest.Custom<*> -> CustomContent(request, ::finish)
            }
        }
    }
}

@Composable
private fun ConfirmContent(request: SheetRequest.Confirm, finish: (() -> Unit) -> Unit) {
    request.message?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
    }
    val colors = if (request.destructive) {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        )
    } else {
        ButtonDefaults.buttonColors()
    }
    Button(
        onClick = { finish { request.resolver.resolve(true) } },
        modifier = Modifier.fillMaxWidth().height(ButtonHeight),
        shape = MaterialTheme.shapes.large,
        colors = colors,
    ) { Text(request.confirmLabel) }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = { finish { request.resolver.resolve(false) } },
        modifier = Modifier.fillMaxWidth().height(ButtonHeight),
        shape = MaterialTheme.shapes.large,
    ) { Text(request.dismissLabel) }
}

@Composable
private fun <T> ChooseContent(request: SheetRequest.Choose<T>, finish: (() -> Unit) -> Unit) {
    request.options.forEachIndexed { index, option ->
        if (index > 0) HorizontalDivider()
        Column(
            Modifier
                .fillMaxWidth()
                .clickable { finish { request.resolver.resolve(option.value) } }
                .padding(vertical = 14.dp),
        ) {
            Text(
                option.label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (option.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            option.description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateContent(request: SheetRequest.PickDate, finish: (() -> Unit) -> Unit) {
    val picker = rememberDatePickerState(initialSelectedDateMillis = request.initialUtcMillis)
    // The Material 3 DatePicker needs 360dp; let it use the sheet's side padding so it isn't clipped on 360dp phones.
    DatePicker(
        state = picker,
        modifier = Modifier.bleed(SheetPadding),
        title = null,
        headline = null,
        showModeToggle = false,
        colors = DatePickerDefaults.colors(containerColor = Color.Transparent),
    )
    ConfirmRow(
        enabled = picker.selectedDateMillis != null,
        onConfirm = { finish { request.resolver.resolve(picker.selectedDateMillis) } },
        onCancel = { finish { request.dismiss() } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeContent(request: SheetRequest.PickDateRange, finish: (() -> Unit) -> Unit) {
    val picker = rememberDateRangePickerState(
        initialSelectedStartDateMillis = request.initial?.startUtcMillis,
        initialSelectedEndDateMillis = request.initial?.endUtcMillis,
        initialDisplayedMonthMillis = request.initial?.startUtcMillis,
    )
    val formatter = remember { DatePickerDefaults.dateFormatter() }
    // The range picker scrolls month by month on its own, so it gets a fixed height inside the scrolling sheet.
    DateRangePicker(
        state = picker,
        modifier = Modifier.bleed(SheetPadding).height(DateRangePickerHeight),
        dateFormatter = formatter,
        title = null,
        headline = {
            DateRangePickerDefaults.DateRangePickerHeadline(
                selectedStartDateMillis = picker.selectedStartDateMillis,
                selectedEndDateMillis = picker.selectedEndDateMillis,
                displayMode = picker.displayMode,
                dateFormatter = formatter,
                modifier = Modifier.padding(start = SheetPadding, end = SheetPadding, bottom = 12.dp),
            )
        },
        showModeToggle = false,
        colors = DatePickerDefaults.colors(containerColor = Color.Transparent),
    )
    val start = picker.selectedStartDateMillis
    val end = picker.selectedEndDateMillis
    ConfirmRow(
        enabled = start != null && end != null,
        onConfirm = { finish { request.resolver.resolve(DateRange(start!!, end!!)) } },
        onCancel = { finish { request.dismiss() } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeContent(request: SheetRequest.PickTime, finish: (() -> Unit) -> Unit) {
    val picker = rememberTimePickerState(request.initial.hour, request.initial.minute, request.is24Hour)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        TimePicker(state = picker)
    }
    ConfirmRow(
        enabled = true,
        onConfirm = { finish { request.resolver.resolve(TimeOfDay(picker.hour, picker.minute)) } },
        onCancel = { finish { request.dismiss() } },
    )
}

@Composable
private fun InputContent(request: SheetRequest.Input, finish: (() -> Unit) -> Unit) {
    var text by remember { mutableStateOf(request.initial) }
    val error = request.validate(text)
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier.fillMaxWidth(),
        label = request.label?.let { { Text(it) } },
        isError = error != null && text.isNotEmpty(),
        supportingText = error?.takeIf { text.isNotEmpty() }?.let { { Text(it) } },
        singleLine = true,
    )
    ConfirmRow(
        enabled = error == null,
        confirmLabel = request.confirmLabel,
        onConfirm = { finish { request.resolver.resolve(text) } },
        onCancel = { finish { request.dismiss() } },
    )
}

@Composable
private fun RateContent(request: SheetRequest.Rate, finish: (() -> Unit) -> Unit) {
    var rating by remember { mutableIntStateOf(request.initial) }
    request.message?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
    }
    // Each star is one radio button: TalkBack reads "3 of 5 stars, selected" and swipes through them in order.
    FlowRow(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.Center,
    ) {
        for (star in 1..request.stars) {
            val label = request.starLabel(star, request.stars)
            Box(
                Modifier
                    .size(StarTouchSize)
                    .clip(CircleShape)
                    .selectable(selected = star == rating, role = Role.RadioButton, onClick = { rating = star })
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Star(filled = star <= rating, modifier = Modifier.size(StarSize))
            }
        }
    }
    ConfirmRow(
        enabled = rating > 0,
        confirmLabel = request.confirmLabel,
        onConfirm = { finish { request.resolver.resolve(rating) } },
        onCancel = { finish { request.dismiss() } },
    )
}

/** A five-pointed star: filled with `primary` when selected, outlined (3:1+ against the sheet) otherwise. */
@Composable
private fun Star(filled: Boolean, modifier: Modifier) {
    val fill = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        val outer = size.minDimension / 2f
        val inner = outer * 0.42f
        val center = Offset(size.width / 2f, size.height / 2f + outer * 0.06f)
        val path = Path().apply {
            for (i in 0 until 10) {
                val radius = if (i % 2 == 0) outer else inner
                val angle = -PI / 2 + i * PI / 5
                val x = center.x + (radius * cos(angle)).toFloat()
                val y = center.y + (radius * sin(angle)).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        if (filled) drawPath(path, fill) else drawPath(path, outline, style = Stroke(width = 2.dp.toPx()))
    }
}

@Composable
private fun ProgressContent(request: SheetRequest.Progress) {
    request.message?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // Announced when the block changes it ("2 of 3 photos"), without interrupting.
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    Spacer(Modifier.height(20.dp))
    val fraction = request.fraction
    if (fraction == null) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
    } else {
        val shown by animateFloatAsState(fraction, ProgressIndicatorDefaults.ProgressAnimationSpec, label = "progress")
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(progress = { shown }, modifier = Modifier.weight(1f))
            Text(
                remember(fraction) { NumberFormat.getPercentInstance().format(fraction) },
                modifier = Modifier.padding(start = 16.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun <T> CustomContent(request: SheetRequest.Custom<T>, finish: (() -> Unit) -> Unit) {
    val scope = remember(request) {
        object : SheetScope<T> {
            override fun answer(value: T) = finish { request.resolver.resolve(value) }
            override fun dismiss() = finish { request.dismiss() }
        }
    }
    request.content(scope)
}

private val SheetPadding = 24.dp
private val ButtonHeight = 52.dp
private val StarTouchSize = 56.dp
private val StarSize = 40.dp
private val DateRangePickerHeight = 460.dp

/** Lets content extend [horizontal] past the parent's padding on both sides. */
private fun Modifier.bleed(horizontal: Dp) = layout { measurable, constraints ->
    val extra = if (constraints.hasBoundedWidth) (horizontal * 2).roundToPx() else 0
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra),
    )
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}

@Composable
private fun ConfirmRow(
    enabled: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    confirmLabel: String = "OK",
) {
    Spacer(Modifier.height(16.dp))
    // Cancel first, then confirm: the same order for sight (left to right) and for TalkBack / keyboard focus.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.weight(1f).height(ButtonHeight),
            shape = MaterialTheme.shapes.large,
        ) {
            Text("Cancel")
        }
        Button(
            onClick = onConfirm,
            enabled = enabled,
            modifier = Modifier.weight(1f).height(ButtonHeight),
            shape = MaterialTheme.shapes.large,
        ) { Text(confirmLabel) }
    }
}
