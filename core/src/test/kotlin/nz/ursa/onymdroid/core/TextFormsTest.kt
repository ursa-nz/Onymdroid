// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TextFormsTest {
    @Test
    fun queryFormTrimsAndUnderscoresSpaces() {
        assertEquals("ice_cream", toQueryForm("  ice cream "))
        assertEquals("run", toQueryForm("run"))
        assertEquals("hot_dog", toQueryForm("hot dog"))
    }

    @Test
    fun displayFormTurnsUnderscoresToSpaces() {
        assertEquals("ice cream", toDisplayForm("ice_cream"))
        assertEquals("hot dog", toDisplayForm("hot_dog"))
        assertEquals("run", toDisplayForm("run"))
    }

    @Test
    fun editDistanceMatchesKnownValues() {
        assertEquals(0, editDistance("abc", "abc"))
        assertEquals(3, editDistance("", "abc"))
        assertEquals(3, editDistance("abc", ""))
        assertEquals(3, editDistance("kitten", "sitting"))
        assertEquals(1, editDistance("beutiful", "beautiful"))
        assertEquals(2, editDistance("wrod", "word"))
    }
}
