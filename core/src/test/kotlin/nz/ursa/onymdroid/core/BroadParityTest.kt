// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.core

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

// Broad parity sweep over the whole onym-engine conformance corpus (a stratified sample of the
// vocabulary plus every edge case the spec names), guarding the long tail of the engine against
// regressions. Heavier than the focused OracleParityTest, and skipped unless both the system
// WordNet data and the conformance kit are present; any mismatch is reported in full in the
// assertion message.
internal class BroadParityTest {
    @Test
    fun corpusMatchesFixtures() {
        assumeTrue("WordNet data not installed", TestWordNet.available)
        assumeTrue("onym-engine conformance kit not found", ConformanceKit.available)
        val engine = OnymEngine.open(TestWordNet.preparedDir())

        val mismatches = ArrayList<String>()
        val report = StringBuilder()
        for (word in ConformanceKit.corpus) {
            val expected = ConformanceKit.dump(word)
            val actual = engine.dump(word)
            if (expected != actual) {
                val e = expected.lines()
                val a = actual.lines()
                val diff = (0 until maxOf(e.size, a.size)).firstOrNull { e.getOrNull(it) != a.getOrNull(it) }
                mismatches += word
                report.appendLine("WORD \"$word\" fixture=${e.size}L engine=${a.size}L diff@${diff?.plus(1)}")
                report.appendLine("  fixture: ${e.getOrNull(diff ?: 0)}")
                report.appendLine("  engine:  ${a.getOrNull(diff ?: 0)}")
            }
        }
        assertEquals(
            "broad parity mismatches over ${ConformanceKit.corpus.size} words (${mismatches.size}):\n$report",
            0,
            mismatches.size,
        )
    }
}
