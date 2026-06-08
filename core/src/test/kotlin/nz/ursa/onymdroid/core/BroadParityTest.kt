// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.core

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

// Broad parity sweep over a stratified sample of the vocabulary (every 200th headword plus a set of
// morphology edge cases), guarding the long tail of the engine against regressions. Heavier than the
// focused OracleParityTest, and skipped unless both the system WordNet data and the onym-cli oracle
// are present; any mismatch is reported in full in the assertion message.
internal class BroadParityTest {
    @Test
    fun broadSampleMatchesOracle() {
        assumeTrue("WordNet data not installed", TestWordNet.available)
        val cli = System.getProperty("onym.cli", "")
        assumeTrue("oracle not built", cli.isNotEmpty() && File(cli).canExecute())
        val engine = OnymEngine.open(TestWordNet.preparedDir())
        val wnDir = TestWordNet.directory.absolutePath

        val words = LinkedHashSet<String>()
        for (name in listOf("index.noun", "index.verb", "index.adj", "index.adv")) {
            var i = 0
            File(TestWordNet.directory, name).bufferedReader(Charsets.ISO_8859_1).useLines { lines ->
                for (line in lines) {
                    if (line.isEmpty() || line[0] == ' ') continue
                    if (i % 200 == 0) words.add(line.substringBefore(' ').replace('_', ' '))
                    i++
                }
            }
        }
        words += listOf(
            "running", "ran", "mice", "better", "best", "children", "feet", "oxen", "geese",
            "ice cream", "hot dog", "Einstein", "abalone", "good", "bad", "fast", "tree",
        )

        val mismatches = ArrayList<String>()
        val report = StringBuilder()
        for (word in words) {
            val expected = oracleDump(cli, wnDir, word)
            val actual = engine.dump(word)
            if (expected != actual) {
                val e = expected.lines()
                val a = actual.lines()
                val diff = (0 until maxOf(e.size, a.size)).firstOrNull { e.getOrNull(it) != a.getOrNull(it) }
                mismatches += word
                report.appendLine("WORD \"$word\" oracle=${e.size}L engine=${a.size}L diff@${diff?.plus(1)}")
                report.appendLine("  oracle: ${e.getOrNull(diff ?: 0)}")
                report.appendLine("  engine: ${a.getOrNull(diff ?: 0)}")
            }
        }
        assertEquals(
            "broad parity mismatches over ${words.size} words (${mismatches.size}):\n$report",
            0,
            mismatches.size,
        )
    }

    private fun oracleDump(cli: String, wnDir: String, word: String): String {
        val process = ProcessBuilder(cli, "--dump", word).apply { environment()["WNSEARCHDIR"] = wnDir }.start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        return output
    }
}
