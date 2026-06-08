// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.core

import java.io.File

/*
 * WordNet's generic verb example sentences, which extJWNL does not parse. A verb sense maps (in
 * sentidx.vrb, keyed by sense key) to one or more frame numbers, and each frame number names a
 * template (in sents.vrb) with a single `%s` placeholder for the verb. This reproduces Onym's
 * find_example / get_example: the templates are filled with the verb and returned in the same order
 * Onym emits them (it prepends as it reads, so the file order is reversed).
 */
internal class VerbExampleIndex private constructor(
    private val templates: Map<String, String>,
    private val frames: Map<String, List<String>>,
) {
    /** The example sentences for [senseKey] (e.g. `cow%2:37:00::`), with [displayWord] substituted in. */
    fun sentences(
        senseKey: String,
        displayWord: String,
    ): List<String> {
        val numbers = frames[senseKey] ?: return emptyList()
        return numbers
            .mapNotNull { number -> templates[number]?.replaceFirst("%s", displayWord) }
            .asReversed()
    }

    companion object {
        /** Load the verb example tables from [dataDir]; both tables are empty when the files are absent. */
        fun load(dataDir: File): VerbExampleIndex {
            val templates = LinkedHashMap<String, String>()
            forEachLine(File(dataDir, "sents.vrb")) { line ->
                val space = line.indexOf(' ')
                if (space > 0) templates[line.substring(0, space)] = line.substring(space + 1).trim()
            }
            val frames = LinkedHashMap<String, List<String>>()
            forEachLine(File(dataDir, "sentidx.vrb")) { line ->
                val space = line.indexOf(' ')
                if (space > 0) {
                    val numbers = line.substring(space + 1).split(' ', ',').filter { it.isNotBlank() }
                    if (numbers.isNotEmpty()) frames[line.substring(0, space)] = numbers
                }
            }
            return VerbExampleIndex(templates, frames)
        }

        // WordNet data files are Latin-1; read them as such so any high-bit bytes round-trip.
        private inline fun forEachLine(
            file: File,
            action: (String) -> Unit,
        ) {
            if (!file.exists()) return
            file.bufferedReader(Charsets.ISO_8859_1).useLines { lines ->
                for (line in lines) if (line.isNotBlank()) action(line)
            }
        }
    }
}
