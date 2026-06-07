// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.core

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * The engine's acceptance test: its `--dump` output must match the onym-cli golden oracle byte for
 * byte. It covers the cases Onym calls out as fiddly — indirect antonyms, deep hyponym trees,
 * holonym/meronym grouping, multi-word lemmas, morphology, and a missed word. The test skips itself
 * when the database or the oracle binary is absent, so it is safe to run anywhere.
 */
internal class OracleParityTest {
    companion object {
        private val cliPath: String = System.getProperty("onym.cli", "")
        private lateinit var engine: OnymEngine
        private lateinit var wordNetDir: String

        @BeforeClass
        @JvmStatic
        fun setUp() {
            assumeTrue("WordNet data not installed", TestWordNet.available)
            assumeTrue("onym-cli oracle not built at $cliPath", cliPath.isNotEmpty() && File(cliPath).canExecute())
            wordNetDir = TestWordNet.directory.absolutePath
            engine = OnymEngine.open(TestWordNet.preparedDir())
        }

        private fun oracle(
            mode: String,
            arg: String,
        ): String {
            val process =
                ProcessBuilder(cliPath, mode, arg)
                    .apply {
                        environment()["WNSEARCHDIR"] = wordNetDir
                    }.start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            return output
        }
    }

    private fun assertParity(word: String) {
        val expected = oracle("--dump", word).lines()
        val actual = engine.dump(word).lines()
        for (i in 0 until maxOf(expected.size, actual.size)) {
            val oracleLine = expected.getOrNull(i)
            val engineLine = actual.getOrNull(i)
            if (oracleLine != engineLine) {
                fail(
                    "\"$word\" differs at line ${i + 1} " +
                        "(oracle ${expected.size} lines, engine ${actual.size} lines):\n" +
                        "  oracle: $oracleLine\n" +
                        "  engine: $engineLine",
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

    private fun assertCompletion(prefix: String) {
        // onym-cli caps completion at 20.
        val actual = engine.complete(prefix, 20).joinToString("") { "$it\n" }
        assertEquals("completion of \"$prefix\"", oracle("--complete", prefix), actual)
    }

    private fun assertSuggestion(word: String) {
        // onym-cli caps suggestions at 10.
        val actual = engine.suggest(word, 10).joinToString("") { "$it\n" }
        assertEquals("suggestion for \"$word\"", oracle("--suggest", word), actual)
    }

    @Test
    fun completion() = listOf("fel", "anti", "ice cr", "dog", "zz").forEach(::assertCompletion)

    @Test
    fun suggestion() = listOf("beutiful", "wrod", "runnin", "hte", "dogg").forEach(::assertSuggestion)
}
