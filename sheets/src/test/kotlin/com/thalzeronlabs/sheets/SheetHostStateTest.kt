package com.thalzeronlabs.sheets

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
