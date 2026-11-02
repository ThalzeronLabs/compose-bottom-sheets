package com.thalzeronlabs.sheets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
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
 *     sheets.withProgress("Saving") { viewModel.save(date) }
 * }
 * ```
 *
 * One sheet at a time: a second request waits until the first is answered. Cancelling the calling coroutine
 * closes its sheet. Swiping the sheet away answers `false` / `null`.
 *
 * Keep it in a ViewModel and ask from `viewModelScope` to survive configuration changes: the open sheet stays, with
 * what the user already entered, and still answers. With `remember` and `rememberCoroutineScope()` a configuration
 * change cancels the question instead.
 *
 * Every sheet takes an optional `accent` color. When it is null the sheet uses your `MaterialTheme` as is; when it
 * is set, the accent replaces `primary` for that one sheet and the text drawn on it is picked automatically
 * (black or white, whichever has the higher contrast).
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
        accent: Color? = null,
    ): Boolean = show<Boolean> {
        SheetRequest.Confirm(title, message, confirmLabel, dismissLabel, destructive, it, accent)
    } ?: false

    /** Lets the user pick one of [options]. Returns the chosen value, or null if dismissed. */
    suspend fun <T> choose(title: String, options: List<SheetOption<T>>, accent: Color? = null): T? {
        require(options.isNotEmpty()) { "options must not be empty" }
        return show<T> { SheetRequest.Choose(title, options, it, accent) }
    }

    /** A Material 3 date picker inside a sheet. Returns UTC midnight in epoch millis, or null. */
    suspend fun pickDate(title: String, initialUtcMillis: Long? = null, accent: Color? = null): Long? =
        show<Long> { SheetRequest.PickDate(title, initialUtcMillis, it, accent) }

    /**
     * A Material 3 date range picker inside a sheet. The confirm button is enabled once both ends are picked.
     * Returns the range (UTC midnights in epoch millis), or null if dismissed.
     */
    suspend fun pickDateRange(title: String, initial: DateRange? = null, accent: Color? = null): DateRange? =
        show<DateRange> { SheetRequest.PickDateRange(title, initial, it, accent) }

    /** A Material 3 time picker inside a sheet. */
    suspend fun pickTime(
        title: String,
        initial: TimeOfDay = TimeOfDay(9, 0),
        is24Hour: Boolean = true,
        accent: Color? = null,
    ): TimeOfDay? = show<TimeOfDay> { SheetRequest.PickTime(title, initial, is24Hour, it, accent) }

    /**
     * A text field inside a sheet. [validate] returns an error message to show, or null when the text is valid;
     * the confirm button stays disabled while there is an error.
     */
    suspend fun input(
        title: String,
        initial: String = "",
        label: String? = null,
        confirmLabel: String = "Save",
        accent: Color? = null,
        validate: (String) -> String? = { null },
    ): String? = show<String> { SheetRequest.Input(title, initial, label, confirmLabel, validate, it, accent) }

    /**
     * A row of [stars] tappable stars. Returns the rating (1..[stars]) once the user taps [confirmLabel],
     * or null if dismissed.
     *
     * Each star is a 56dp radio button; [starLabel] is what TalkBack reads for it (`starLabel(3, 5)` →
     * "3 of 5 stars"). Pass a translated lambda if your app is not in English.
     */
    suspend fun rate(
        title: String,
        message: String? = null,
        stars: Int = 5,
        initial: Int = 0,
        confirmLabel: String = "Submit",
        accent: Color? = null,
        starLabel: (star: Int, of: Int) -> String = { star, of -> "$star of $of stars" },
    ): Int? {
        require(stars in 1..10) { "stars must be 1..10, was $stars" }
        require(initial in 0..stars) { "initial must be 0..$stars, was $initial" }
        return show<Int> { SheetRequest.Rate(title, message, stars, initial, confirmLabel, starLabel, it, accent) }
    }

    /**
     * Shows a progress sheet while [block] runs and closes it when [block] returns or throws.
     * Returns what [block] returns; exceptions from [block] are rethrown after the sheet closes.
     *
     * ```kotlin
     * val urls = sheets.withProgress("Uploading", "3 photos") {
     *     photos.mapIndexed { i, photo ->
     *         fraction = i / photos.size.toFloat()
     *         api.upload(photo)
     *     }
     * }
     * ```
     *
     * The bar is indeterminate until [block] sets [ProgressScope.fraction]. The user cannot swipe this sheet away;
     * cancel the calling coroutine to stop the work. Don't open another sheet of the same [SheetHostState] from
     * inside [block]: it would wait for this one forever, so it throws [IllegalStateException] instead.
     */
    suspend fun <R> withProgress(
        title: String,
        message: String? = null,
        accent: Color? = null,
        block: suspend ProgressScope.() -> R,
    ): R {
        checkNotInsideProgress()
        return queue.withLock {
            val hidden = CompletableDeferred<Unit>()
            val request = SheetRequest.Progress(title, message, { hidden.complete(Unit) }, accent)
            current = request
            try {
                withContext(InsideProgress(this)) { request.block() }
            } finally {
                request.finished = true
                // Let SheetHost slide the sheet down before the caller moves on, unless the caller was cancelled.
                if (currentCoroutineContext().isActive) withTimeoutOrNull(HIDE_TIMEOUT_MS) { hidden.await() }
                if (current === request) current = null
            }
        }
    }

    /**
     * A sheet with your own [content] under the usual title. Call [SheetScope.answer] to close it and return a
     * typed value, or [SheetScope.dismiss] to return null. Swiping it away also returns null.
     * Use `rememberSaveable` for state inside [content] that should survive a configuration change.
     *
     * ```kotlin
     * val quantity: Int? = sheets.custom("Quantity") {
     *     var n by rememberSaveable { mutableIntStateOf(1) }
     *     Stepper(n, onChange = { n = it })
     *     Button(onClick = { answer(n) }) { Text("Add to cart") }
     * }
     * ```
     */
    suspend fun <T> custom(
        title: String,
        accent: Color? = null,
        content: @Composable SheetScope<T>.() -> Unit,
    ): T? = show<T> { SheetRequest.Custom(title, content, it, accent) }

    private suspend fun <T> show(build: (Resolver<T>) -> SheetRequest<T>): T? {
        checkNotInsideProgress()
        return queue.withLock {
            suspendCancellableCoroutine { cont: CancellableContinuation<T?> ->
                val request = build(Resolver { value -> if (cont.isActive) cont.resume(value) })
                current = request
                cont.invokeOnCancellation { if (current === request) current = null }
            }.also { current = null }
        }
    }

    private suspend fun checkNotInsideProgress() {
        check(currentCoroutineContext()[InsideProgress]?.host !== this) {
            "Can't open a sheet from inside withProgress { } of the same SheetHostState; it would wait forever."
        }
    }

    /** Marks coroutines running inside [withProgress], so a nested sheet fails fast instead of deadlocking. */
    private class InsideProgress(val host: SheetHostState) : AbstractCoroutineContextElement(InsideProgress) {
        companion object Key : CoroutineContext.Key<InsideProgress>
    }
}

// Top-level and private, so they don't become public static fields (a `const val` in a private companion would).

/** Upper bound on waiting for the closing animation of a progress sheet. */
private const val HIDE_TIMEOUT_MS = 1_000L

private const val DAY_MILLIS = 86_400_000L

/** Delivers the answer of a sheet back to the suspended caller. */
fun interface Resolver<T> {
    fun resolve(value: T?)
}

/**
 * One row of [SheetHostState.choose].
 *
 * @property description optional second line, drawn smaller and dimmer.
 * @property destructive draws the label in the theme's error color.
 */
data class SheetOption<T>(
    val label: String,
    val value: T,
    val description: String? = null,
    val destructive: Boolean = false,
)

/** A wall-clock time without a date, as returned by [SheetHostState.pickTime]. */
data class TimeOfDay(val hour: Int, val minute: Int) {
    init {
        require(hour in 0..23) { "hour must be 0..23, was $hour" }
        require(minute in 0..59) { "minute must be 0..59, was $minute" }
    }

    override fun toString() = "%02d:%02d".format(hour, minute)
}

/**
 * An inclusive range of days, as returned by [SheetHostState.pickDateRange].
 * Both ends are UTC midnight in epoch millis, like [SheetHostState.pickDate].
 */
data class DateRange(val startUtcMillis: Long, val endUtcMillis: Long) {
    init {
        require(endUtcMillis >= startUtcMillis) { "end ($endUtcMillis) must not be before start ($startUtcMillis)" }
    }

    /** Number of days in the range, counting both ends (a single-day range is 1). */
    val days: Int get() = ((endUtcMillis - startUtcMillis) / DAY_MILLIS).toInt() + 1
}

/** What the block of [SheetHostState.withProgress] can update while it runs. Safe to set from any thread. */
interface ProgressScope {
    /** Progress from 0f to 1f (clamped), or null for an indeterminate bar. Starts as null. */
    var fraction: Float?

    /** The line under the title, e.g. "2 of 3 photos". TalkBack announces changes politely. */
    var message: String?
}

/** Lets the content of [SheetHostState.custom] close its sheet. */
@Stable
interface SheetScope<T> {
    /** Closes the sheet; [SheetHostState.custom] returns [value]. */
    fun answer(value: T)

    /** Closes the sheet; [SheetHostState.custom] returns null. */
    fun dismiss()
}

/** What [SheetHost] knows how to draw. Each request carries the [Resolver] that answers it. */
sealed interface SheetRequest<T> {
    val title: String
    val resolver: Resolver<T>

    /** Replaces the theme's `primary` for this sheet; null keeps the app's `MaterialTheme`. */
    val accent: Color?

    /** Called when the sheet is swiped away or dismissed without an answer. */
    fun dismiss() = resolver.resolve(null)

    data class Confirm(
        override val title: String,
        val message: String?,
        val confirmLabel: String,
        val dismissLabel: String,
        val destructive: Boolean,
        override val resolver: Resolver<Boolean>,
        override val accent: Color? = null,
    ) : SheetRequest<Boolean> {
        override fun dismiss() = resolver.resolve(false)
    }

    data class Choose<T>(
        override val title: String,
        val options: List<SheetOption<T>>,
        override val resolver: Resolver<T>,
        override val accent: Color? = null,
    ) : SheetRequest<T>

    data class PickDate(
        override val title: String,
        val initialUtcMillis: Long?,
        override val resolver: Resolver<Long>,
        override val accent: Color? = null,
    ) : SheetRequest<Long>

    data class PickDateRange(
        override val title: String,
        val initial: DateRange?,
        override val resolver: Resolver<DateRange>,
        override val accent: Color? = null,
    ) : SheetRequest<DateRange>

    data class PickTime(
        override val title: String,
        val initial: TimeOfDay,
        val is24Hour: Boolean,
        override val resolver: Resolver<TimeOfDay>,
        override val accent: Color? = null,
    ) : SheetRequest<TimeOfDay>

    data class Input(
        override val title: String,
        val initial: String,
        val label: String?,
        val confirmLabel: String,
        val validate: (String) -> String?,
        override val resolver: Resolver<String>,
        override val accent: Color? = null,
    ) : SheetRequest<String>

    data class Rate(
        override val title: String,
        val message: String?,
        val stars: Int,
        val initial: Int,
        val confirmLabel: String,
        val starLabel: (star: Int, of: Int) -> String,
        override val resolver: Resolver<Int>,
        override val accent: Color? = null,
    ) : SheetRequest<Int>

    /**
     * The sheet of [SheetHostState.withProgress]. It is also the [ProgressScope] of the running block.
     * The user can't dismiss it; [finished] turns true when the block is done and [SheetHost] then hides it.
     */
    class Progress(
        override val title: String,
        message: String?,
        override val resolver: Resolver<Unit>,
        override val accent: Color? = null,
    ) : SheetRequest<Unit>, ProgressScope {
        private var progress by mutableStateOf<Float?>(null)

        override var fraction: Float?
            get() = progress
            set(value) {
                progress = value?.takeUnless { it.isNaN() }?.coerceIn(0f, 1f)
            }

        override var message: String? by mutableStateOf(message)

        /** True once the block has returned or thrown. */
        var finished: Boolean by mutableStateOf(false)
            internal set

        /** A progress sheet ignores swipes and back presses; it closes when its block is done. */
        override fun dismiss() = Unit
    }

    data class Custom<T>(
        override val title: String,
        val content: @Composable SheetScope<T>.() -> Unit,
        override val resolver: Resolver<T>,
        override val accent: Color? = null,
    ) : SheetRequest<T>
}
