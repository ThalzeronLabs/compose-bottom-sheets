package com.thalzeronlabs.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Draws whatever [state] is asking for as a Material 3 `ModalBottomSheet`.
 * Put it once near the root of a screen; it renders nothing while no sheet is requested.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetHost(state: SheetHostState) {
    val request = state.current ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // Animate the sheet down first, then hand the answer back to the caller.
    fun finish(answer: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { answer() }
    }

    ModalBottomSheet(onDismissRequest = { request.dismiss() }, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SheetPadding)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
        ) {
            Text(request.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            when (request) {
                is SheetRequest.Confirm -> ConfirmContent(request, ::finish)
                is SheetRequest.Choose<*> -> ChooseContent(request, ::finish)
                is SheetRequest.PickDate -> DateContent(request, ::finish)
                is SheetRequest.PickTime -> TimeContent(request, ::finish)
                is SheetRequest.Input -> InputContent(request, ::finish)
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
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = colors,
    ) { Text(request.confirmLabel) }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = { finish { request.resolver.resolve(false) } },
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
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

private val SheetPadding = 24.dp

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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp)) {
            Text("Cancel")
        }
        Button(
            onClick = onConfirm,
            enabled = enabled,
            modifier = Modifier.weight(1f).height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text(confirmLabel) }
    }
}
