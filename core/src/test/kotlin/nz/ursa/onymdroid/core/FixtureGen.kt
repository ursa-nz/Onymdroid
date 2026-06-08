// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.core

import java.io.File

/*
 * Regenerates the onym-engine conformance fixtures from this engine. The Kotlin engine is the
 * reference implementation of the onym-engine spec until the Rust core takes over, so this tool is
 * temporary scaffolding: it writes the same tree conformance/gen-fixtures does, in one JVM, by
 * calling the engine directly instead of capturing a dumper's stdout once per word.
 *
 * Run with: ./gradlew :core:generateFixtures --args=/path/to/onym-engine
 */

// The completion and suggestion lists mirror conformance/gen-fixtures, which owns them.
private val COMPLETE_PREFIXES =
    listOf("ser", "ice", "run", "bea", "sh", "fel", "anti", "ice cr", "dog", "zz")
private val SUGGEST_WORDS =
    listOf("wrod", "recieve", "beutiful", "hapy", "runnin", "hte", "dogg")

fun main(args: Array<String>) {
    require(args.size == 1) { "usage: FixtureGen /path/to/onym-engine" }
    val repo = File(args[0])
    val corpus = File(repo, "conformance/corpus.txt")
    require(corpus.isFile) { "no corpus at $corpus" }
    require(TestWordNet.available) { "WordNet data not installed" }

    val engine = OnymEngine.open(TestWordNet.preparedDir())
    val fixtures = File(repo, "conformance/fixtures")
    require(fixtures.deleteRecursively()) { "could not clear $fixtures" }
    val dump = File(fixtures, "dump").apply { mkdirs() }
    val complete = File(fixtures, "complete").apply { mkdirs() }
    val suggest = File(fixtures, "suggest").apply { mkdirs() }

    // The dictionary files are ISO-8859-1, and the oracle emitted their bytes untouched, so the
    // fixtures are written in the same encoding rather than the platform default.
    fun File.writeFixture(text: String) = writeBytes(text.toByteArray(Charsets.ISO_8859_1))

    var dumps = 0
    corpus.useLines(Charsets.ISO_8859_1) { lines ->
        for (line in lines) {
            val word = line.trim()
            if (word.isEmpty() || word.startsWith("#")) continue
            File(dump, toQueryForm(word) + ".txt").writeFixture(engine.dump(word))
            dumps++
        }
    }
    for (prefix in COMPLETE_PREFIXES) {
        val text = engine.complete(prefix, 20).joinToString("") { it + "\n" }
        File(complete, toQueryForm(prefix) + ".txt").writeFixture(text)
    }
    for (word in SUGGEST_WORDS) {
        val text = engine.suggest(word, 10).joinToString("") { it + "\n" }
        File(suggest, toQueryForm(word) + ".txt").writeFixture(text)
    }
    println("fixtures: $dumps dumps, ${COMPLETE_PREFIXES.size} completions, ${SUGGEST_WORDS.size} suggestions")
}
