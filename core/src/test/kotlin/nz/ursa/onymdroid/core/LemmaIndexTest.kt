// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test

internal class LemmaIndexTest {
    companion object {
        private lateinit var index: LemmaIndex

        @BeforeClass
        @JvmStatic
        fun build() {
            assumeTrue("WordNet data not installed", TestWordNet.available)
            index = LemmaIndex.build(TestWordNet.directory)
        }
    }

    @Test
    fun completionReturnsSortedPrefixMatchesWithinCap() {
        val matches = index.complete("fel", 8)
        assertTrue("at most the cap", matches.size <= 8)
        assertTrue("all start with the prefix", matches.all { it.startsWith("fel") })
        assertEquals("alphabetical", matches.sorted(), matches)
        assertTrue("felicitous is a known match", "felicitous" in index.complete("fel", 0))
    }

    @Test
    fun completionIgnoresCaseAndUnderscores() {
        assertTrue("ice cream" in index.complete("ICE CR", 20))
    }

    @Test
    fun suggestionRanksByDistanceThenAlphabetically() {
        val suggestions = index.suggest("beutiful", 5)
        assertTrue("a suggestion is offered", suggestions.isNotEmpty())
        assertEquals("the nearest is beautiful", "beautiful", suggestions[0])
    }

    @Test
    fun suggestionExcludesTheExactWord() {
        // dog is itself a headword (distance zero), so it must not be suggested for itself.
        assertTrue("dog" !in index.suggest("dog", 10))
    }
}
