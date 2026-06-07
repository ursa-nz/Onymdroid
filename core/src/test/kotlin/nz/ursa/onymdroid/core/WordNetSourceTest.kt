// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Verifies the extJWNL-backed [WordNetSource] adapter maps the reader onto the neutral model the
 * engine consumes: senses with words and pointers, following a pointer by offset, multiword lookup,
 * morphology, and — the fiddly one — that the adjective-satellite flag matches the pointer structure
 * the antonym logic relies on (a head carries a direct antonym; a satellite carries similar-to).
 */
internal class WordNetSourceTest {
    companion object {
        private lateinit var source: WordNetSource

        @BeforeClass
        @JvmStatic
        fun open() {
            assumeTrue("WordNet data not installed", TestWordNet.available)
            source = TestWordNet.openSource()
        }
    }

    @Test
    fun mapsNounSensesWithWordsGlossAndHypernym() {
        val senses = source.sensesOf("dog", WnPos.NOUN)
        assertTrue("dog has senses", senses.isNotEmpty())
        val first = senses[0]
        assertTrue("first sense has a gloss", first.gloss.isNotBlank())
        assertTrue("first sense includes the word dog", first.words.any { it.lemma == "dog" })
        assertTrue("first sense has a hypernym", first.pointers.any { it.relation == WnRelation.HYPERNYM })
    }

    @Test
    fun followsAPointerToAnotherSynset() {
        val dog = source.sensesOf("dog", WnPos.NOUN).first()
        val hypernym = dog.pointers.first { it.relation == WnRelation.HYPERNYM }
        val target = source.synsetAt(hypernym.targetPos, hypernym.targetOffset)
        assertNotNull("the hypernym target resolves", target)
        assertTrue("the target has words", target!!.words.isNotEmpty())
    }

    @Test
    fun looksUpMultiwordLemmasInQueryForm() {
        assertTrue("ice cream is defined", source.sensesOf("ice_cream", WnPos.NOUN).isNotEmpty())
    }

    @Test
    fun morphologyReturnsBaseForms() {
        assertTrue("ran -> run", "run" in source.baseForms("ran", WnPos.VERB))
        assertTrue("mice -> mouse", "mouse" in source.baseForms("mice", WnPos.NOUN))
    }

    @Test
    fun adjectiveSatelliteFlagMatchesPointerStructure() {
        val senses = source.sensesOf("beautiful", WnPos.ADJECTIVE)
        assertTrue("beautiful has senses", senses.isNotEmpty())

        val head = senses.firstOrNull { sense -> sense.pointers.any { it.relation == WnRelation.ANTONYM } }
        assertNotNull("a head sense carries a direct antonym", head)
        assertFalse("the head sense is not a satellite", head!!.adjectiveSatellite)

        val satellite =
            senses.firstOrNull { sense ->
                sense.pointers.none { it.relation == WnRelation.ANTONYM } &&
                    sense.pointers.any { it.relation == WnRelation.SIMILAR_TO }
            }
        assertNotNull("a satellite sense carries similar-to but no direct antonym", satellite)
        assertTrue("the satellite sense is flagged", satellite!!.adjectiveSatellite)
    }
}
