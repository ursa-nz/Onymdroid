// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.core

import net.sf.extjwnl.data.POS
import net.sf.extjwnl.data.PointerType
import net.sf.extjwnl.dictionary.Dictionary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Confirms extJWNL can read the WordNet 3.0 database and exposes the pointer-level detail the
 * engine relies on (the source/target word indices, the holonym and meronym subtypes, adjective
 * clusters, and Morphy). It skips itself when no database is present, so it is safe to run
 * anywhere; override the location with -Donym.wordnet.dir=/path.
 *
 * This is the reader spike: it de-risks the choice of extJWNL before the engine is built on it.
 */
class ExtJwnlReaderTest {
    companion object {
        private val dataDir: String = System.getProperty("onym.wordnet.dir", "/usr/share/wordnet")
        private lateinit var dictionary: Dictionary

        @BeforeClass
        @JvmStatic
        fun open() {
            val source = File(dataDir)
            assumeTrue("WordNet data not found at $dataDir", File(source, "index.noun").exists())
            // extJWNL opens the database read-write, so work from a writable copy — exactly what the
            // app does when it unpacks the bundled data to its files directory. The Debian package
            // ships cntlist.rev but not the plain cntlist that extJWNL expects, so add an empty one.
            val work = Files.createTempDirectory("onym-wordnet").toFile()
            source.listFiles()?.forEach { file ->
                if (file.isFile) file.copyTo(File(work, file.name), overwrite = true)
            }
            File(work, "cntlist").createNewFile()
            dictionary = Dictionary.getFileBackedInstance(work.absolutePath)
        }
    }

    @Test
    fun readsNounSensesGlossesAndHypernyms() {
        val dog = dictionary.lookupIndexWord(POS.NOUN, "dog")
        assertTrue("dog should have senses", dog != null && dog.senses.isNotEmpty())
        val first = dog!!.senses[0]
        assertTrue("dog sense 1 has a gloss", first.gloss.isNotBlank())
        assertTrue(
            "dog sense 1 is synonymous with domestic dog",
            first.words.any { it.lemma.replace('_', ' ') == "domestic dog" },
        )
        assertTrue(
            "dog has a hypernym pointer",
            first.pointers.any { it.type == PointerType.HYPERNYM },
        )
    }

    @Test
    fun morphyResolvesIrregularInflections() {
        val morph = dictionary.morphologicalProcessor
        assertEquals("run", morph.lookupBaseForm(POS.VERB, "ran")?.lemma)
        assertEquals("mouse", morph.lookupBaseForm(POS.NOUN, "mice")?.lemma)
    }

    @Test
    fun exposesAdjectiveClusterAntonymRoute() {
        val beautiful = dictionary.lookupIndexWord(POS.ADJECTIVE, "beautiful")
        assertTrue("beautiful should have senses", beautiful != null && beautiful.senses.isNotEmpty())
        val reachesAntonym =
            beautiful!!.senses.any { sense ->
                sense.pointers.any { it.type == PointerType.ANTONYM } ||
                    (sense.isAdjectiveCluster && sense.pointers.any { it.type == PointerType.SIMILAR_TO })
            }
        assertTrue("beautiful reaches an antonym directly or via similar-to", reachesAntonym)
    }

    @Test
    fun exposesHolonymAndMeronymSubtypes() {
        val hand = dictionary.lookupIndexWord(POS.NOUN, "hand")
        assertTrue("hand should have senses", hand != null && hand.senses.isNotEmpty())
        val types =
            hand!!
                .senses
                .flatMap { it.pointers }
                .map { it.type }
                .toSet()
        assertTrue(
            "hand has a meronym subtype",
            PointerType.PART_MERONYM in types ||
                PointerType.MEMBER_MERONYM in types ||
                PointerType.SUBSTANCE_MERONYM in types,
        )
        assertTrue(
            "hand has a holonym subtype",
            PointerType.PART_HOLONYM in types ||
                PointerType.MEMBER_HOLONYM in types ||
                PointerType.SUBSTANCE_HOLONYM in types,
        )
    }

    @Test
    fun lexicalPointersExposeWordIndices() {
        // The antonym logic needs the source/target word indices (Artha's pfrm/pto).
        val good = dictionary.lookupIndexWord(POS.ADJECTIVE, "good")
        assertTrue("good should have senses", good != null && good.senses.isNotEmpty())
        val lexical = good!!.senses.flatMap { it.pointers }.firstOrNull { it.isLexical }
        assertTrue("an adjective exposes lexical pointers", lexical != null)
        assertTrue("a lexical pointer reports a source word index", lexical!!.sourceIndex >= 0)
    }
}
