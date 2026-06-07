// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.core

import java.io.File

/*
 * The lemma index: every WordNet headword once, lowercased and in display form, sorted. It reads the
 * index files directly and depends on nothing else, which makes prefix completion a binary search and
 * spelling suggestions a bounded edit-distance scan. This mirrors Onym's wn-index.c.
 */
internal class LemmaIndex private constructor(
    private val lemmas: List<String>,
) {
    /**
     * Headwords beginning with [prefix], in lowercased display form and alphabetical order, capped at
     * [max] (0 means no cap).
     */
    fun complete(
        prefix: String,
        max: Int,
    ): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val needle = displayLower(prefix)
        if (needle.isEmpty()) return emptyList()

        val result = ArrayList<String>()
        var i = lowerBound(needle)
        while (i < lemmas.size && (max == 0 || result.size < max)) {
            val lemma = lemmas[i]
            if (!lemma.startsWith(needle)) break
            result.add(lemma)
            i++
        }
        return result
    }

    /**
     * Headwords close to [word] by edit distance, for a "did you mean" prompt. Candidates differ in
     * length by at most two and in edit distance by one or two, ordered by distance and then
     * alphabetically, capped at [max] (0 means no cap). Exact matches (distance zero) are excluded.
     */
    fun suggest(
        word: String,
        max: Int,
    ): List<String> {
        if (word.isEmpty()) return emptyList()
        val needle = displayLower(word)
        if (needle.isEmpty()) return emptyList()

        val candidates = ArrayList<Candidate>()
        for (lemma in lemmas) {
            val gap =
                if (lemma.length > needle.length) lemma.length - needle.length else needle.length - lemma.length
            if (gap > 2) continue
            val distance = editDistance(needle, lemma)
            if (distance in 1..2) candidates.add(Candidate(lemma, distance))
        }
        candidates.sortWith(compareBy({ it.distance }, { it.term }))
        val limit = if (max == 0) candidates.size else minOf(max, candidates.size)
        return candidates.subList(0, limit).map { it.term }
    }

    private fun lowerBound(key: String): Int {
        var lo = 0
        var hi = lemmas.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (lemmas[mid] < key) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private data class Candidate(
        val term: String,
        val distance: Int,
    )

    companion object {
        private val INDEX_FILES = listOf("index.noun", "index.verb", "index.adj", "index.adv")

        /** Build the index from the WordNet `index.*` files in [dataDir]. */
        fun build(dataDir: File): LemmaIndex {
            val raw = ArrayList<String>()
            for (name in INDEX_FILES) {
                val file = File(dataDir, name)
                if (!file.isFile) continue
                // The files are ASCII; read byte-for-byte so ordering matches WordNet's strcmp.
                file.bufferedReader(Charsets.ISO_8859_1).useLines { lines ->
                    for (line in lines) {
                        // Lines beginning with a space are the licence header, not lemmas.
                        if (line.isEmpty() || line[0] == ' ') continue
                        val space = line.indexOf(' ')
                        val token = if (space >= 0) line.substring(0, space) else line
                        raw.add(displayLower(token))
                    }
                }
            }
            raw.sort()

            val deduped = ArrayList<String>(raw.size)
            var previous: String? = null
            for (lemma in raw) {
                if (lemma != previous) {
                    deduped.add(lemma)
                    previous = lemma
                }
            }
            return LemmaIndex(deduped)
        }

        /** The lowercased display form: underscores to spaces and ASCII upper-case to lower-case. */
        private fun displayLower(value: String): String =
            buildString(value.length) {
                for (c in value) {
                    append(
                        when {
                            c == '_' -> ' '
                            c in 'A'..'Z' -> c + 32
                            else -> c
                        },
                    )
                }
            }
    }
}
