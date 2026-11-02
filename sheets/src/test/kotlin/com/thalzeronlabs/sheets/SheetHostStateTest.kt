package com.thalzeronlabs.sheets

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SheetHostStateTest {

    @Test
    fun `confirm suspends until the user answers`() = runTest {
        val sheets = SheetHostState()
        val answer = async { sheets.confirm("Delete?", destructive = true) }
        runCurrent()

        val request = sheets.current as SheetRequest.Confirm
        assertEquals("Delete?", request.title)
        assertTrue(request.destructive)
        assertFalse(answer.isCompleted)

        request.resolver.resolve(true)
        assertTrue(answer.await())
        assertNull(sheets.current)
    }

    @Test
    fun `dismissing a confirm sheet means no`() = runTest {
        val sheets = SheetHostState()
        val answer = async { sheets.confirm("Leave?") }
        runCurrent()
        sheets.current!!.dismiss()
        assertFalse(answer.await())
    }

    @Test
    fun `choose returns the typed value of the tapped option`() = runTest {
        val sheets = SheetHostState()
        val answer = async {
            sheets.choose("Sort by", listOf(SheetOption("Newest", 1), SheetOption("Oldest", 2)))
        }
        runCurrent()
        @Suppress("UNCHECKED_CAST")
        val request = sheets.current as SheetRequest.Choose<Int>
        assertEquals(listOf("Newest", "Oldest"), request.options.map { it.label })
        request.resolver.resolve(2)
        assertEquals(2, answer.await())
    }

    @Test
    fun `dismissing a picker returns null`() = runTest {
        val sheets = SheetHostState()
        val date = async { sheets.pickDate("Due date") }
        runCurrent()
        sheets.current!!.dismiss()
        assertNull(date.await())
    }

    @Test
    fun `time picker carries its initial value and answers a TimeOfDay`() = runTest {
        val sheets = SheetHostState()
        val time = async { sheets.pickTime("Reminder", TimeOfDay(7, 30)) }
        runCurrent()
        val request = sheets.current as SheetRequest.PickTime
        assertEquals(TimeOfDay(7, 30), request.initial)
        request.resolver.resolve(TimeOfDay(8, 15))
        assertEquals("08:15", time.await().toString())
    }

    @Test
    fun `input exposes the validator to the UI`() = runTest {
        val sheets = SheetHostState()
        val name = async { sheets.input("Rename", initial = "a", validate = { if (it.length < 3) "Too short" else null }) }
        runCurrent()
        val request = sheets.current as SheetRequest.Input
        assertEquals("Too short", request.validate("ab"))
        assertNull(request.validate("abc"))
        request.resolver.resolve("abc")
        assertEquals("abc", name.await())
    }

    @Test
    fun `requests are queued, one sheet at a time`() = runTest {
        val sheets = SheetHostState()
        val first = async { sheets.confirm("First") }
        val second = async { sheets.confirm("Second") }
        runCurrent()

        assertEquals("First", sheets.current!!.title)
        (sheets.current as SheetRequest.Confirm).resolver.resolve(true)
        runCurrent()

        assertEquals("Second", sheets.current!!.title)
        (sheets.current as SheetRequest.Confirm).resolver.resolve(false)
        assertTrue(first.await())
        assertFalse(second.await())
    }

    @Test
    fun `cancelling the caller closes its sheet`() = runTest {
        val sheets = SheetHostState()
        val job = async { sheets.confirm("Pending") }
        runCurrent()
        job.cancel()
        runCurrent()
        assertNull(sheets.current)
    }

    @Test
    fun `answering twice is harmless`() = runTest {
        val sheets = SheetHostState()
        val answer = async { sheets.confirm("Twice?") }
        runCurrent()
        val request = sheets.current as SheetRequest.Confirm
        request.resolver.resolve(true)
        request.resolver.resolve(false)
        assertTrue(answer.await())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid times are rejected`() {
        TimeOfDay(24, 0)
    }

    @Test
    fun `pickDateRange answers a DateRange and dismissing returns null`() = runTest {
        val sheets = SheetHostState()
        val trip = DateRange(MARCH_12, MARCH_12 + 3 * DAY)
        val range = async { sheets.pickDateRange("Trip dates", initial = trip) }
        runCurrent()
        val request = sheets.current as SheetRequest.PickDateRange
        assertEquals(trip, request.initial)
        request.resolver.resolve(trip)
        assertEquals(4, range.await()!!.days)

        val dismissed = async { sheets.pickDateRange("Trip dates") }
        runCurrent()
        sheets.current!!.dismiss()
        assertNull(dismissed.await())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a range cannot end before it starts`() {
        DateRange(MARCH_12, MARCH_12 - DAY)
    }

    @Test
    fun `rate returns the number of stars`() = runTest {
        val sheets = SheetHostState()
        val rating = async { sheets.rate("Rate your stay", stars = 5, initial = 3) }
        runCurrent()
        val request = sheets.current as SheetRequest.Rate
        assertEquals(3, request.initial)
        assertEquals("4 of 5 stars", request.starLabel(4, 5))
        request.resolver.resolve(4)
        assertEquals(4, rating.await())
    }

    @Test
    fun `rate rejects an initial value outside the stars`() = runTest {
        val sheets = SheetHostState()
        try {
            sheets.rate("Rate", stars = 5, initial = 6)
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertNull(sheets.current)
        }
    }

    @Test
    fun `withProgress shows a sheet while the block runs and returns its result`() = runTest {
        val sheets = SheetHostState()
        val gate = CompletableDeferred<Unit>()
        val result = async {
            sheets.withProgress("Uploading", "Starting") {
                fraction = 0.25f
                gate.await()
                message = "Almost there"
                fraction = 7f
                "done"
            }
        }
        runCurrent()
        val request = sheets.current as SheetRequest.Progress
        assertEquals("Uploading", request.title)
        assertEquals(0.25f, request.fraction)
        assertFalse(request.finished)

        gate.complete(Unit)
        runCurrent()
        assertEquals(1f, request.fraction) // clamped
        assertEquals("Almost there", request.message)
        assertTrue(request.finished)

        // The host acknowledges once its closing animation is over.
        request.resolver.resolve(Unit)
        assertEquals("done", result.await())
        assertNull(sheets.current)
    }

    @Test
    fun `withProgress closes on its own even without a host`() = runTest {
        val sheets = SheetHostState()
        val result = async { sheets.withProgress("Saving") { 42 } }
        advanceUntilIdle()
        assertEquals(42, result.await())
        assertNull(sheets.current)
    }

    @Test
    fun `withProgress rethrows and closes when the block fails`() = runTest {
        val sheets = SheetHostState()
        val result = async { runCatching { sheets.withProgress("Syncing") { error("offline") } } }
        advanceUntilIdle()
        assertEquals("offline", result.await().exceptionOrNull()?.message)
        assertNull(sheets.current)
    }

    @Test
    fun `a progress sheet ignores dismissal`() = runTest {
        val sheets = SheetHostState()
        val gate = CompletableDeferred<Unit>()
        val result = async { sheets.withProgress("Working") { gate.await(); "ok" } }
        runCurrent()
        val request = sheets.current!!
        request.dismiss()
        runCurrent()
        assertSame(request, sheets.current)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals("ok", result.await())
    }

    @Test
    fun `opening a sheet inside withProgress fails fast instead of deadlocking`() = runTest {
        val sheets = SheetHostState()
        val result = async { runCatching { sheets.withProgress("Working") { sheets.confirm("Sure?") } } }
        advanceUntilIdle()
        assertTrue(result.await().exceptionOrNull() is IllegalStateException)
        assertNull(sheets.current)
    }

    @Test
    fun `cancelling withProgress cancels the block and closes the sheet`() = runTest {
        val sheets = SheetHostState()
        val job = async { sheets.withProgress("Working") { CompletableDeferred<Unit>().await() } }
        runCurrent()
        job.cancel()
        runCurrent()
        assertNull(sheets.current)
    }

    @Test
    fun `custom returns the typed value its content answers`() = runTest {
        val sheets = SheetHostState()
        val quantity = async { sheets.custom<Int>("Quantity") { } }
        runCurrent()
        @Suppress("UNCHECKED_CAST")
        val request = sheets.current as SheetRequest.Custom<Int>
        assertEquals("Quantity", request.title)
        request.resolver.resolve(3)
        assertEquals(3, quantity.await())
    }

    @Test
    fun `every sheet carries its accent, and none by default`() = runTest {
        val sheets = SheetHostState()
        val amber = Color(0xFFA15C00)
        val first = async { sheets.choose("Pick", listOf(SheetOption("A", 1)), accent = amber) }
        val second = async { sheets.confirm("Plain") }
        runCurrent()
        assertEquals(amber, sheets.current!!.accent)
        sheets.current!!.dismiss()
        runCurrent()
        assertNull(sheets.current!!.accent)
        sheets.current!!.dismiss()
        assertNull(first.await())
        assertFalse(second.await())
    }

    private companion object {
        const val DAY = 86_400_000L
        const val MARCH_12 = 1_773_273_600_000L
    }
}
