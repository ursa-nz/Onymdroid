// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.core

import org.junit.Assert.assertEquals
import org.junit.Test

class OnymResultTest {
    @Test
    fun treeNodeLabelJoinsTermsWithCommaSpace() {
        assertEquals("frozen dessert", OnymTreeNode(listOf("frozen dessert"), emptyList()).label)
        assertEquals(
            "dessert, sweet, afters",
            OnymTreeNode(listOf("dessert", "sweet", "afters"), emptyList()).label,
        )
    }
}
