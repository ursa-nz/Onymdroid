// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.core

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * The engine's acceptance test: its `--dump` output must match the onym-engine conformance fixtures
 * byte for byte. It covers the cases Onym calls out as fiddly (indirect antonyms, deep hyponym
 * trees, holonym/meronym grouping, multi-word lemmas, morphology, a missed word) plus the spec's two
 * deliberate fixes. The test skips itself when the database or the conformance kit is absent, so it
 * is safe to run anywhere.
 */
internal class OracleParityTest {
    companion object {
        private lateinit var engine: OnymEngine

        @BeforeClass
        @JvmStatic
        fun setUp() {
            assumeTrue("WordNet data not installed", TestWordNet.available)
            assumeTrue("onym-engine conformance kit not found", ConformanceKit.available)
            // The native engine reads the system database in place, read-only; no prepared copy.
            engine = OnymEngine.open(TestWordNet.directory)
        }
    }

    private fun assertParity(word: String) {
        val expected = ConformanceKit.dump(word).lines()
        val actual = engine.dump(word).lines()
        for (i in 0 until maxOf(expected.size, actual.size)) {
            val fixtureLine = expected.getOrNull(i)
            val engineLine = actual.getOrNull(i)
            if (fixtureLine != engineLine) {
                fail(
                    "\"$word\" differs at line ${i + 1} " +
                        "(fixture ${expected.size} lines, engine ${actual.size} lines):\n" +
                        "  fixture: $fixtureLine\n" +
                        "  engine:  $engineLine",
                )
            }
        }
    }

    @Test
    fun indirectAntonyms() = listOf("beautiful", "fast", "good").forEach(::assertParity)

    @Test
    fun deepHyponymTrees() = listOf("animal", "dog").forEach(::assertParity)

    @Test
    fun holonymAndMeronymGrouping() = listOf("hand", "tree").forEach(::assertParity)

    @Test
    fun multiWordLemma() = assertParity("ice cream")

    @Test
    fun morphology() = listOf("ran", "better", "mice").forEach(::assertParity)

    @Test
    fun missedWord() = assertParity("wrod")

    @Test
    fun fixedVariantTruncation() = listOf("shore bird", "ash bin", "hot dog", "pica-pica").forEach(::assertParity)

    @Test
    fun fixedTreeAttachment() = listOf("door", "sing", "sang").forEach(::assertParity)

    private fun assertCompletion(prefix: String) {
        // The fixtures hold onym-cli's cap of 20.
        val actual = engine.complete(prefix, 20).joinToString("") { "$it\n" }
        assertEquals("completion of \"$prefix\"", ConformanceKit.complete(prefix), actual)
    }

    private fun assertSuggestion(word: String) {
        // The fixtures hold onym-cli's cap of 10.
        val actual = engine.suggest(word, 10).joinToString("") { "$it\n" }
        assertEquals("suggestion for \"$word\"", ConformanceKit.suggest(word), actual)
    }

    @Test
    fun completion() = listOf("fel", "anti", "ice cr", "dog", "zz").forEach(::assertCompletion)

    @Test
    fun suggestion() = listOf("beutiful", "wrod", "runnin", "hte", "dogg").forEach(::assertSuggestion)
}
