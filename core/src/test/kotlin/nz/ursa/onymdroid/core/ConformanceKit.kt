// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.core

import java.io.File

/**
 * The onym-engine conformance kit, the golden oracle the parity tests answer to. The kit lives in a
 * sibling checkout (override with `-Donym.conformance=/path`); its fixtures carry the spec's two
 * deliberate fixes, which is why the tests no longer diff against a live onym-cli. Fixtures are
 * written in the dictionary's ISO-8859-1, so they are read the same way.
 */
internal object ConformanceKit {
    private val root: File = File(System.getProperty("onym.conformance", ""))

    val available: Boolean
        get() = File(root, "corpus.txt").isFile && File(root, "fixtures/dump").isDirectory

    /** The corpus words in file order, comments and duplicates dropped. */
    val corpus: List<String>
        get() =
            File(root, "corpus.txt")
                .readLines(Charsets.ISO_8859_1)
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .distinct()

    private fun read(
        kind: String,
        name: String,
    ): String = File(root, "fixtures/$kind/${toQueryForm(name)}.txt").readText(Charsets.ISO_8859_1)

    fun dump(word: String): String = read("dump", word)

    fun complete(prefix: String): String = read("complete", prefix)

    fun suggest(word: String): String = read("suggest", word)
}
