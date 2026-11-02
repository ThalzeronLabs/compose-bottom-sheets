package com.thalzeronlabs.sheets

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * An open sheet across a real activity recreation (`ActivityScenario.recreate()`), the path a rotation, a dark mode
 * switch or a language change takes. The screen keeps its [SheetHostState] in a real [ViewModel] and asks from
 * `viewModelScope`, the usual setup.
 *
 * Process death (or "Don't keep activities") is emulated by clearing the ViewModel store before recreating: the
 * activity comes back from its saved state, but the ViewModel and the coroutine that was waiting for the answer
 * are gone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h740dp-xhdpi")
@OptIn(ExperimentalCoroutinesApi::class) // getCompleted()
class SheetRecreationTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** The app's screen ViewModel. */
    class ScreenViewModel : ViewModel() {
        val sheets = SheetHostState()
    }

    private val app: Application = ApplicationProvider.getApplicationContext()

    /** What every (re)created activity shows, like an app's onCreate. */
    private var screen: (ComponentActivity) -> Unit = ::showScreen

    private val setContentOnCreate = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) =
            screen(activity as ComponentActivity)
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    @Before
    fun setUp() {
        // The rule already created the first activity; every recreated one sets its content like an app's onCreate.
        compose.runOnUiThread { showScreen(compose.activity) }
        app.registerActivityLifecycleCallbacks(setContentOnCreate)
    }

    @After
    fun tearDown() = app.unregisterActivityLifecycleCallbacks(setContentOnCreate)

    @Test
    fun `typed text survives a configuration change`() {
        val answer = ask { input("Rename note", initial = "Groceries", label = "Title") }

        compose.onNodeWithText("Groceries").performTextReplacement("Groceries for Sunday")
        recreate()

        compose.onNodeWithText("Groceries for Sunday").assertIsDisplayed()
        compose.onNodeWithText("Save").performClick()
        compose.waitForIdle()
        assertEquals("Groceries for Sunday", answer.getCompleted())
    }

    @Test
    fun `the chosen rating survives a configuration change`() {
        val answer = ask { rate("Rate your stay") }

        compose.onNode(hasContentDescription("4 of 5 stars")).performClick()
        recreate()

        compose.onNode(hasContentDescription("4 of 5 stars")).assertIsSelected()
        compose.onNodeWithText("Submit").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(4, answer.getCompleted())
    }

    @Test
    fun `the picked date survives a configuration change`() {
        val answer = ask { pickDate("Due date", initialUtcMillis = MARCH_12_2026_UTC) }

        compose.onNode(hasText("March 20,", substring = true) and hasClickAction()).performClick()
        recreate()

        compose.onNodeWithText("OK").performClick()
        compose.waitForIdle()
        assertEquals(MARCH_12_2026_UTC + 8 * DAY, answer.getCompleted())
    }

    @Test
    fun `the picked date range survives a configuration change`() {
        val answer = ask { pickDateRange("Trip dates", DateRange(MARCH_12_2026_UTC, MARCH_12_2026_UTC + 3 * DAY)) }

        // A new range: the first tap sets the start, the second the end.
        compose.onNode(hasText("March 20,", substring = true) and hasClickAction()).performClick()
        compose.onNode(hasText("March 22,", substring = true) and hasClickAction()).performClick()
        recreate()

        compose.onNodeWithText("OK").performClick()
        compose.waitForIdle()
        assertEquals(DateRange(MARCH_12_2026_UTC + 8 * DAY, MARCH_12_2026_UTC + 10 * DAY), answer.getCompleted())
    }

    @Test
    fun `custom content that uses rememberSaveable survives a configuration change`() {
        val answer = ask {
            custom<Int>("Quantity") {
                var count by rememberSaveable { mutableIntStateOf(1) }
                Button(onClick = { count++ }) { Text("More") }
                Button(onClick = { answer(count) }) { Text("Add $count to cart") }
            }
        }

        compose.onNodeWithText("More").performClick()
        compose.onNodeWithText("More").performClick()
        recreate()

        compose.onNodeWithText("Add 3 to cart").performClick()
        compose.waitForIdle()
        assertEquals(3, answer.getCompleted())
    }

    @Test
    fun `a confirm sheet is still there after a configuration change and still answers`() {
        val answer = ask { confirm("Delete this note?", confirmLabel = "Delete", destructive = true) }

        recreate()

        compose.onNodeWithText("Delete this note?").assertIsDisplayed()
        compose.onNodeWithText("Delete").performClick()
        compose.waitForIdle()
        assertTrue(answer.getCompleted())
    }

    @Test
    fun `a progress sheet keeps its progress across a configuration change and still closes itself`() {
        val gate = CompletableDeferred<Unit>()
        val answer = ask {
            withProgress("Uploading photos", "1 of 3") {
                message = "2 of 3"
                fraction = 2 / 3f
                gate.await()
                "done"
            }
        }

        recreate()
        compose.onNodeWithText("2 of 3").assertIsDisplayed()

        gate.complete(Unit)
        compose.waitForIdle()
        assertEquals("done", answer.getCompleted())
        assertEquals(0, compose.onAllNodes(hasText("Uploading photos")).fetchSemanticsNodes().size)
    }

    @Test
    fun `after process death the question is gone and the screen comes back without a sheet`() {
        val answer = ask { input("Rename note", initial = "Groceries") }
        val caller = viewModel().viewModelScope.coroutineContext[Job]!!

        compose.onNodeWithText("Groceries").performTextReplacement("Groceries for Sunday")
        recreate(processDeath = true)

        assertTrue("the coroutine waiting for the answer died with the ViewModel", caller.isCancelled)
        assertTrue(answer.isCancelled)
        assertEquals(0, compose.onAllNodes(hasText("Rename note")).fetchSemanticsNodes().size)

        // The screen is usable: a new question opens a fresh sheet with its own initial text.
        val again = ask { input("Rename note", initial = "Groceries") }
        compose.onNodeWithText("Groceries").assertIsDisplayed()
        compose.onNodeWithText("Save").performClick()
        compose.waitForIdle()
        assertEquals("Groceries", again.getCompleted())
    }

    @Test
    fun `with remember and rememberCoroutineScope, a configuration change cancels the question`() {
        var asked: Job? = null
        screen = { activity ->
            activity.setContent {
                MaterialTheme {
                    val sheets = remember { SheetHostState() }
                    val scope = rememberCoroutineScope()
                    SheetHost(sheets)
                    // Asked once, like from a click handler.
                    if (asked == null) asked = scope.launch { sheets.confirm("Leave without saving?") }
                }
            }
        }
        compose.runOnUiThread { screen(compose.activity) }
        compose.waitForIdle()
        compose.onNodeWithText("Leave without saving?").assertIsDisplayed()

        recreate()

        assertTrue("the caller's scope went away with the old composition", asked!!.isCancelled)
        assertEquals(0, compose.onAllNodes(hasText("Leave without saving?")).fetchSemanticsNodes().size)
    }

    @Test
    fun `the picked time survives a configuration change`() {
        val answer = ask { pickTime("Remind me at", TimeOfDay(9, 30)) }

        compose.onNode(hasContentDescription("14 hours") and hasClickAction()).performClick()
        recreate()

        compose.onNodeWithText("OK").performClick()
        compose.waitForIdle()
        assertEquals(TimeOfDay(14, 30), answer.getCompleted())
    }

    private fun showScreen(activity: ComponentActivity) {
        val viewModel = ViewModelProvider(activity)[ScreenViewModel::class.java]
        activity.setContent { MaterialTheme { SheetHost(viewModel.sheets) } }
    }

    private fun viewModel(): ScreenViewModel = ViewModelProvider(compose.activity)[ScreenViewModel::class.java]

    /** Asks from the ViewModel's scope, as an app would from a click handler. */
    private fun <T> ask(question: suspend SheetHostState.() -> T): CompletableDeferred<T> {
        val answer = CompletableDeferred<T>()
        compose.runOnUiThread {
            val viewModel = viewModel()
            viewModel.viewModelScope.launch {
                try {
                    answer.complete(viewModel.sheets.question())
                } catch (cancelled: CancellationException) {
                    answer.cancel(cancelled)
                    throw cancelled
                }
            }
        }
        compose.waitForIdle()
        return answer
    }

    private fun recreate(processDeath: Boolean = false) {
        if (processDeath) compose.runOnUiThread { compose.activity.viewModelStore.clear() }
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
    }

    private companion object {
        const val DAY = 86_400_000L
        const val MARCH_12_2026_UTC = 1_773_273_600_000L
    }
}
