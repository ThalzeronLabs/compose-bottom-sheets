package com.thalzeronlabs.sheets

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

/**
 * Bottom sheets you can `await`, like `SnackbarHostState` for snackbars.
 *
 * ```kotlin
 * val sheets = remember { SheetHostState() }
 * SheetHost(sheets)
 *
 * scope.launch {
 *     if (sheets.confirm("Delete this note?", destructive = true)) viewModel.delete(note)
 *     val date = sheets.pickDate("Due date") ?: return@launch
 * }
 * ```
 *
 * One sheet at a time: a second request waits until the first is answered. Cancelling the calling coroutine
 * closes its sheet. Swiping the sheet away answers `false` / `null`.
 */
@Stable
class SheetHostState {

    /** The sheet on screen right now, or null. Read by [SheetHost]. */
    var current: SheetRequest<*>? by mutableStateOf(null)
        private set

    private val queue = Mutex()

    /** Asks a yes/no question. Returns true only when the user taps [confirmLabel]. */
    suspend fun confirm(
        title: String,
        message: String? = null,
        confirmLabel: String = "Confirm",
        dismissLabel: String = "Cancel",
        destructive: Boolean = false,
    ): Boolean = show<Boolean> { SheetRequest.Confirm(title, message, confirmLabel, dismissLabel, destructive, it) } ?: false

    /** Lets the user pick one of [options]. Returns the chosen value, or null if dismissed. */
    suspend fun <T> choose(title: String, options: List<SheetOption<T>>): T? {
        require(options.isNotEmpty()) { "options must not be empty" }
        return show<T> { SheetRequest.Choose(title, options, it) }
    }

    /** A Material 3 date picker inside a sheet. Returns UTC midnight in epoch millis, or null. */
    suspend fun pickDate(title: String, initialUtcMillis: Long? = null): Long? =
        show<Long> { SheetRequest.PickDate(title, initialUtcMillis, it) }

    /** A Material 3 time picker inside a sheet. */
    suspend fun pickTime(title: String, initial: TimeOfDay = TimeOfDay(9, 0), is24Hour: Boolean = true): TimeOfDay? =
        show<TimeOfDay> { SheetRequest.PickTime(title, initial, is24Hour, it) }

    /**
     * A text field inside a sheet. [validate] returns an error message to show, or null when the text is valid;
     * the confirm button stays disabled while there is an error.
     */
    suspend fun input(
        title: String,
        initial: String = "",
        label: String? = null,
        confirmLabel: String = "Save",
        validate: (String) -> String? = { null },
    ): String? = show<String> { SheetRequest.Input(title, initial, label, confirmLabel, validate, it) }

    private suspend fun <T> show(build: (Resolver<T>) -> SheetRequest<T>): T? = queue.withLock {
        suspendCancellableCoroutine { cont: CancellableContinuation<T?> ->
            val request = build(Resolver { value -> if (cont.isActive) cont.resume(value) })
            current = request
            cont.invokeOnCancellation { if (current === request) current = null }
        }.also { current = null }
    }
}

/** Delivers the answer of a sheet back to the suspended caller. */
fun interface Resolver<T> {
    fun resolve(value: T?)
}

data class SheetOption<T>(
    val label: String,
    val value: T,
    val description: String? = null,
    val destructive: Boolean = false,
)

data class TimeOfDay(val hour: Int, val minute: Int) {
    init {
        require(hour in 0..23) { "hour must be 0..23, was $hour" }
        require(minute in 0..59) { "minute must be 0..59, was $minute" }
    }

    override fun toString() = "%02d:%02d".format(hour, minute)
}

/** What [SheetHost] knows how to draw. Each request carries the [Resolver] that answers it. */
sealed interface SheetRequest<T> {
    val title: String
    val resolver: Resolver<T>

    /** Called when the sheet is swiped away or dismissed without an answer. */
    fun dismiss() = resolver.resolve(null)

    data class Confirm(
        override val title: String,
        val message: String?,
        val confirmLabel: String,
        val dismissLabel: String,
        val destructive: Boolean,
        override val resolver: Resolver<Boolean>,
    ) : SheetRequest<Boolean> {
        override fun dismiss() = resolver.resolve(false)
    }

    data class Choose<T>(
        override val title: String,
        val options: List<SheetOption<T>>,
        override val resolver: Resolver<T>,
    ) : SheetRequest<T>

    data class PickDate(
        override val title: String,
        val initialUtcMillis: Long?,
        override val resolver: Resolver<Long>,
    ) : SheetRequest<Long>

    data class PickTime(
        override val title: String,
        val initial: TimeOfDay,
        val is24Hour: Boolean,
        override val resolver: Resolver<TimeOfDay>,
    ) : SheetRequest<TimeOfDay>

    data class Input(
        override val title: String,
        val initial: String,
        val label: String?,
        val confirmLabel: String,
        val validate: (String) -> String?,
        override val resolver: Resolver<String>,
    ) : SheetRequest<String>
}
