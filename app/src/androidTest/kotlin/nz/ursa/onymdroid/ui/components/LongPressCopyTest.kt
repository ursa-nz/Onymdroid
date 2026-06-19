// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.ui.components

import android.content.ClipboardManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import nz.ursa.onymdroid.core.OnymEngine
import nz.ursa.onymdroid.core.OnymResult
import nz.ursa.onymdroid.core.OnymSection
import nz.ursa.onymdroid.core.OnymTreeNode
import nz.ursa.onymdroid.data.WordNetAssets
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for the long-press copy that mirrors the desktop's right-click copy menu: the headword
 * hero, both chip forms, and the tree terms put the bare term on the clipboard, a long press never
 * navigates, tapping still does, and the action reaches accessibility services under its "Copy"
 * label. The tree test walks a real entry through the packaged engine, as the tree suite does, so
 * the joining comma the display label carries is the one the decoder actually produces.
 */
@RunWith(AndroidJUnit4::class)
class LongPressCopyTest {
    @get:Rule
    val compose = createComposeRule()

    private fun clipboardText(): String? {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        return clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.text
            ?.toString()
    }

    // The copy lands from a coroutine on the main dispatcher, so the clipboard is polled rather
    // than read once. Timing out is the assertion failure.
    private fun awaitClipboard(expected: String) {
        compose.waitUntil(timeoutMillis = 5_000) { clipboardText() == expected }
    }

    @Test
    fun navigableChipCopiesOnLongPressAndStillNavigatesOnTap() {
        var navigated: String? = null
        compose.setContent {
            WordChip(
                label = "felicitous",
                navigable = true,
                tone = ChipTones.related,
                onClick = { navigated = "tapped" },
            )
        }
        compose.onNodeWithText("felicitous").performTouchInput { longClick() }
        awaitClipboard("felicitous")
        compose.runOnIdle { assertNull("a long press must not navigate", navigated) }

        compose.onNodeWithText("felicitous").performClick()
        compose.runOnIdle { assertEquals("tapped", navigated) }
    }

    @Test
    fun nonNavigableChipCopiesOnLongPress() {
        compose.setContent {
            WordChip(
                label = "perspicacious",
                navigable = false,
                tone = ChipTones.related,
                onClick = {},
            )
        }
        compose.onNodeWithText("perspicacious").performTouchInput { longClick() }
        awaitClipboard("perspicacious")
    }

    @Test
    fun heroCopiesTheHeadwordAndNamesTheActionCopy() {
        compose.setContent {
            WordHero(term = "serendipity", partsOfSpeech = listOf("noun"))
        }
        val action =
            compose
                .onNodeWithText("serendipity")
                .fetchSemanticsNode()
                .config[SemanticsActions.OnLongClick]
        assertEquals(COPY_ACTION_LABEL, action.label)

        compose.onNodeWithText("serendipity").performTouchInput { longClick() }
        awaitClipboard("serendipity")
    }

    @Test
    fun treeTermCopiesTheBareTermAndStillNavigates() {
        // The shallowest multi-term node across the entry's trees: its first term renders with a
        // joining comma, and the copy must carry the term alone. The first term is also marked
        // navigable, exercising the combinedClickable path rather than the gesture-detector one.
        val (title, node) = multiTermNode()
        val first = node.terms.first()
        var navigated: String? = null
        compose.setContent {
            TreeSection(
                title = title,
                nodes = listOf(node),
                navigable = setOf(first),
                expandedByDefault = false,
                onNavigate = { navigated = it },
            )
        }
        compose.onAllNodesWithText("$first,").onFirst().performTouchInput { longClick() }
        awaitClipboard(first)
        compose.runOnIdle { assertNull("a long press must not navigate", navigated) }

        compose.onAllNodesWithText("$first,").onFirst().performClick()
        compose.runOnIdle { assertEquals(first, navigated) }
    }

    /** Breadth-first, so the found node is shallow and composes on screen even when collapsed. */
    private fun multiTermNode(): Pair<String, OnymTreeNode> {
        val queue = ArrayDeque<Pair<String, OnymTreeNode>>()
        entry.sections.filterIsInstance<OnymSection.Tree>().forEach { section ->
            section.items.forEach { queue.add(section.title to it) }
        }
        while (queue.isNotEmpty()) {
            val (title, node) = queue.removeFirst()
            if (node.terms.size >= 2) return title to node
            node.children.forEach { queue.add(title to it) }
        }
        error("no multi-term tree node in the looked-up entry")
    }

    companion object {
        private lateinit var engine: OnymEngine
        private lateinit var entry: OnymResult

        // One engine for the class, as in TreeSectionTest: opening reads the whole database, so
        // the cost is paid once, and closing keeps native memory bounded across a larger run.
        @JvmStatic
        @BeforeClass
        fun openEngine() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            engine = OnymEngine.open(WordNetAssets.ensureUnpacked(context))
            entry = requireNotNull(engine.lookup("dog")) { "dog missing from the bundled WordNet data" }
        }

        @JvmStatic
        @AfterClass
        fun closeEngine() {
            engine.close()
        }
    }
}
