// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import nz.ursa.onymdroid.core.OnymEngine
import nz.ursa.onymdroid.core.OnymResult
import nz.ursa.onymdroid.core.OnymSection
import nz.ursa.onymdroid.data.WordNetAssets
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression tests for the relation trees, composing the real entry for "family Cathartidae" — the
 * word a crash on a device was reported against — through the same engine, decoder, and packaged
 * native library the app ships. The entry's "Is a kind of" tree is a seven-level chain, deep enough
 * that every recursion level of [TreeNodeRow] takes part, and its flat "Parts" tree puts several
 * sibling nodes on screen at once. The tests pin down the three behaviours that depend on tree
 * state keys being stable and collision-free: a fully expanded deep tree composes, collapsing a
 * branch hides only its own descendants, and open/closed state survives activity recreation.
 */
@RunWith(AndroidJUnit4::class)
class TreeSectionTest {
    @get:Rule
    val compose = createComposeRule()

    private val kindOf: OnymSection.Tree
        get() = entry.sections.filterIsInstance<OnymSection.Tree>().first { it.title == "Is a kind of" }

    @Test
    fun deepEntryComposesFullyExpanded() {
        compose.setContent {
            Column {
                entry.sections.filterIsInstance<OnymSection.Tree>().forEach { section ->
                    TreeSection(
                        title = section.title,
                        nodes = section.items,
                        navigable = emptySet(),
                        expandedByDefault = true,
                        onNavigate = {},
                    )
                }
            }
        }
        // The bottom of the seven-level "Is a kind of" chain and the last of the "Parts" siblings
        // both composed, so no recursion level and no sibling was lost or rejected.
        compose.onNodeWithText("entity").assertExists()
        compose.onNodeWithText("genus Sarcorhamphus").assertExists()
    }

    @Test
    fun collapsingABranchHidesOnlyItsDescendants() {
        compose.setContent {
            TreeSection(
                title = kindOf.title,
                nodes = kindOf.items,
                navigable = emptySet(),
                expandedByDefault = true,
                onNavigate = {},
            )
        }
        compose.onNodeWithText("entity").assertExists()

        // Collapse the depth-one "family" node; everything beneath it goes, its ancestors stay.
        compose.onNodeWithText("family").performClick()
        compose.onNodeWithText("entity").assertDoesNotExist()
        compose.onNodeWithText("bird family").assertExists()
        compose.onNodeWithText("family").assertExists()

        compose.onNodeWithText("family").performClick()
        compose.onNodeWithText("entity").assertExists()
    }

    @Test
    fun openStateSurvivesActivityRecreation() {
        val tester = StateRestorationTester(compose)
        tester.setContent {
            TreeSection(
                title = kindOf.title,
                nodes = kindOf.items,
                navigable = emptySet(),
                expandedByDefault = true,
                onNavigate = {},
            )
        }
        compose.onNodeWithText("family").performClick()
        compose.onNodeWithText("entity").assertDoesNotExist()

        // Recreation rebuilds the composition from saved state: the collapsed branch must come
        // back collapsed, and its still-open parent must come back open.
        tester.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("entity").assertDoesNotExist()
        compose.onNodeWithText("bird family").assertExists()
        compose.onNodeWithText("family").performClick()
        compose.onNodeWithText("entity").assertExists()
    }

    companion object {
        private lateinit var engine: OnymEngine
        private lateinit var entry: OnymResult

        // One engine for the class: opening reads the whole database, so the cost is paid once,
        // and closing keeps native memory bounded across a larger test run.
        @JvmStatic
        @BeforeClass
        fun openEngine() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            engine = OnymEngine.open(WordNetAssets.ensureUnpacked(context))
            entry =
                requireNotNull(engine.lookup("family Cathartidae")) {
                    "family Cathartidae missing from the bundled WordNet data"
                }
        }

        @JvmStatic
        @AfterClass
        fun closeEngine() {
            engine.close()
        }
    }
}
