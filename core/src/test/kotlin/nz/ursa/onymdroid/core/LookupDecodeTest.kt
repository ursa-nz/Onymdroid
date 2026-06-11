// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test

/*
 * A focused guard on the lookup wire path — JNI call, codec, decoder — for "family Cathartidae",
 * the entry a crash on a device was reported against. The parity suite proves the dump text, but
 * the app never reads dumps: it reads the encoded entry buffer, and this test pins that route,
 * asserting the decoded model carries the entry's full shape, down to the bottom of its
 * seven-level "Is a kind of" chain. The completion probe at the end is the membership test the
 * app runs per rendered term to decide navigability, so the whole lookup-then-render data path
 * is answered for.
 */
internal class LookupDecodeTest {
    @Test
    fun familyCathartidaeCrossesTheWireIntact() {
        assumeTrue("WordNet data not installed", TestWordNet.available)
        val engine = OnymEngine.open(TestWordNet.directory)
        try {
            val entry = engine.lookup("family Cathartidae")
            assertNotNull("entry should resolve", entry)
            assertEquals("family Cathartidae", entry!!.term)

            val kindOf =
                entry.sections.filterIsInstance<OnymSection.Tree>().first { it.title == "Is a kind of" }
            val chain = generateSequence(kindOf.items.single()) { it.children.singleOrNull() }.toList()
            assertEquals("the is-a chain runs seven levels deep", 7, chain.size)
            assertEquals(listOf("bird family"), chain.first().terms)
            assertEquals(listOf("entity"), chain.last().terms)

            val parts =
                entry.sections.filterIsInstance<OnymSection.Tree>().first { it.title == "Parts" }
            assertEquals("six flat part nodes", 6, parts.items.size)

            // The app tests a term's navigability with an exact-match completion on its
            // display-lower form; the headword itself must pass its own test.
            assertEquals(listOf("family cathartidae"), engine.complete("family cathartidae", 1))
        } finally {
            engine.close()
        }
    }
}
