package com.thalzeronlabs.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * One accessibility test per sheet, on the JVM. Each one opens the sheet and checks that:
 *
 * - every tappable element has a label TalkBack can read (its text or a content description);
 * - every tappable element offers at least a 48 x 48dp touch target;
 * - the reading / focus order follows the visual order (title, content, then the buttons left to right);
 * - sheet-specific semantics hold (radio stars, live progress, ...).
 *
 * Color contrast is covered without rendering in [SheetContrastTest].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h740dp-xhdpi")
class SheetAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun confirm() {
        open { confirm("Delete this note?", "You can't undo this.", confirmLabel = "Delete", destructive = true) }
        assertActionsAreLabelledAndLargeEnough()
        assertReadingOrder("Delete this note?", "You can't undo this.", "Delete", "Cancel")
    }

    @Test
    fun choose() {
        open {
            choose("Sort notes by", listOf(SheetOption("Last edited", 0, "Most recent first"), SheetOption("Title", 1)))
        }
        assertActionsAreLabelledAndLargeEnough()
        // Each option is one stop for TalkBack: its label and description are read together.
        assertReadingOrder("Sort notes by", "Last edited Most recent first", "Title")
    }

    @Test
    fun pickDate() {
        open { pickDate("Due date", initialUtcMillis = MARCH_12_2026_UTC) }
        assertActionsAreLabelledAndLargeEnough()
        assertReadingOrder("Due date", "Cancel", "OK")
    }

    @Test
    fun pickDateRange() {
        open { pickDateRange("Trip dates", DateRange(MARCH_12_2026_UTC, MARCH_12_2026_UTC + 3 * DAY)) }
        assertActionsAreLabelledAndLargeEnough()
        assertReadingOrder("Trip dates", "Cancel", "OK")
        compose.onNodeWithText("OK").assertIsEnabled()
    }

    @Test
    fun pickTime() {
        open { pickTime("Remind me at", TimeOfDay(9, 30)) }
        assertActionsAreLabelledAndLargeEnough()
        assertReadingOrder("Remind me at", "Cancel", "OK")
    }

    @Test
    fun input() {
        open { input("Rename note", initial = "Groceries", label = "Title") }
        assertActionsAreLabelledAndLargeEnough()
        assertReadingOrder("Rename note", "Title", "Cancel", "Save")
    }

    @Test
    fun rate() {
        open { rate("Rate your stay", "How was Lisbon?", initial = 0) }
        assertActionsAreLabelledAndLargeEnough()
        assertReadingOrder("Rate your stay", "How was Lisbon?", "1 of 5 stars", "5 of 5 stars", "Cancel", "Submit")

        val stars = compose.onAllNodesWithContentDescription("of 5 stars", substring = true)
        assertEquals(5, stars.fetchSemanticsNodes().size)
        repeat(5) { stars[it].assert(hasRole(Role.RadioButton)).assertIsNotSelected() }
        compose.onNodeWithText("Submit").assertIsNotEnabled()

        compose.onNodeWithContentDescription("4 of 5 stars").performClick()
        compose.onNodeWithContentDescription("4 of 5 stars").assertIsSelected()
        compose.onNodeWithText("Submit").assertIsEnabled()
    }

    @Test
    fun progress() {
        open {
            withProgress("Uploading photos", "2 of 3 uploaded") {
                fraction = 0.66f
                awaitCancellation()
            }
        }
        // Nothing to tap: no scrim to close it, no drag handle. It closes when the work is done.
        assertTrue("a progress sheet can't be dismissed", visibleActions().isEmpty())
        assertReadingOrder("Uploading photos", "2 of 3 uploaded")
        compose.onNodeWithText("2 of 3 uploaded").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite),
        )
        compose.onNode(hasProgressBarRangeInfo(androidx.compose.ui.semantics.ProgressBarRangeInfo(0.66f, 0f..1f)))
            .assertExists()
    }

    @Test
    fun custom() {
        open {
            custom<Int>("Quantity") {
                var count by remember { mutableIntStateOf(1) }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedIconButton(onClick = { count-- }, Modifier.semantics { contentDescription = "Fewer" }) {
                        Text("−")
                    }
                    Text("$count")
                    OutlinedIconButton(onClick = { count++ }, Modifier.semantics { contentDescription = "More" }) {
                        Text("+")
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = { answer(count) }, Modifier.fillMaxWidth()) { Text("Add to cart") }
            }
        }
        assertActionsAreLabelledAndLargeEnough()
        assertReadingOrder("Quantity", "Fewer", "1", "More", "Add to cart")
    }

    private fun open(ask: suspend SheetHostState.() -> Unit) {
        compose.setContent {
            MaterialTheme {
                val sheets = remember { SheetHostState() }
                SheetHost(sheets)
                LaunchedEffect(Unit) { sheets.ask() }
            }
        }
        compose.waitForIdle()
    }

    /**
     * Every node TalkBack can activate has something to say and offers at least a 48 x 48dp touch target.
     * "Activate" includes nodes that only offer accessibility actions such as dismiss, expand or collapse.
     * Nodes clipped out of view (months a lazy calendar keeps composed off-screen) can't be reached, so they're skipped.
     * Then the drag handle gets the stricter check of [assertDragHandleIsReachable].
     */
    private fun assertActionsAreLabelledAndLargeEnough(dragHandle: Boolean = true) {
        val nodes = visibleActions()
        assertTrue("the sheet has no actions", nodes.isNotEmpty())
        val problems = nodes.mapNotNull { node ->
            val label = node.spokenText()
            val touch = node.touchBoundsInRoot
            with(node.layoutInfo.density) {
                val width = touch.width.toDp()
                val height = touch.height.toDp()
                when {
                    label.isBlank() -> "action without a label: ${node.config}"
                    width < MIN_TOUCH || height < MIN_TOUCH -> "'$label' touch target is $width x $height"
                    else -> null
                }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
        if (dragHandle) assertDragHandleIsReachable()
    }

    /**
     * The drag handle is the one control every dismissible sheet has. Its own bounds, which is what it reports to
     * accessibility services and what the Accessibility Test Framework's `TouchTargetSizeCheck` measures on a
     * device, must be 48 x 48dp, not just Compose's enlarged touch area: Material's default handle is a 32dp-wide
     * target that ATF reports as too small. It must also be labelled and offer the dismiss action.
     */
    private fun assertDragHandleIsReachable() {
        val handle = compose.onNode(hasContentDescription("Drag handle", substring = true) and hasClickAction())
            .fetchSemanticsNode()
        with(handle.layoutInfo.density) {
            val width = handle.boundsInRoot.width.toDp()
            val height = handle.boundsInRoot.height.toDp()
            assertTrue("drag handle is $width x $height, below $MIN_TOUCH", width >= MIN_TOUCH && height >= MIN_TOUCH)
        }
        assertTrue("drag handle has no dismiss action", SemanticsActions.Dismiss in handle.config)
    }

    private fun visibleActions(): List<SemanticsNode> =
        compose.onAllNodes(hasClickAction() or hasAccessibilityAction()).fetchSemanticsNodes()
            .filterNot { it.boundsInRoot.isEmpty }

    private fun hasAccessibilityAction() = SemanticsMatcher("has dismiss, expand or collapse") { node ->
        listOf(SemanticsActions.Dismiss, SemanticsActions.Expand, SemanticsActions.Collapse)
            .any { it in node.config }
    }

    /**
     * [labels] appear in this order when walking the semantics tree depth-first, which is the order TalkBack and
     * keyboard focus follow (nothing in the sheets overrides it with `traversalIndex`).
     */
    private fun assertReadingOrder(vararg labels: String) {
        var root = compose.onNodeWithText(labels.first()).fetchSemanticsNode()
        while (root.parent != null) root = root.parent!!
        val spoken = mutableListOf<String>()
        fun walk(node: SemanticsNode) {
            spoken += node.spokenText()
            node.children.forEach(::walk)
        }
        walk(root)
        var position = -1
        labels.forEach { label ->
            val next = spoken.withIndex().firstOrNull { (i, text) -> i > position && label in text }?.index
            assertTrue("'$label' is not read after ${labels.toList().subList(0, labels.indexOf(label))} in $spoken", next != null)
            position = next!!
        }
    }

    private fun SemanticsNode.spokenText(): String = listOfNotNull(
        config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" "),
        config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text },
        config.getOrNull(SemanticsProperties.EditableText)?.text,
    ).joinToString(" ")

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private companion object {
        val MIN_TOUCH = 48.dp
        const val DAY = 86_400_000L
        const val MARCH_12_2026_UTC = 1_773_273_600_000L
    }
}
