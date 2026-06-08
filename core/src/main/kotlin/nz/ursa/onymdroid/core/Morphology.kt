// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.core

import java.io.File

/*
 * WordNet's morphology, ported faithfully from the WordNet 3.0 C library (lib/morph.c: morphstr,
 * morphword, wordbase, exc_lookup, hasprep, morphprep). extJWNL's own morphology over-generates
 * relative to morphstr (robed -> rob, puss -> several senses), so the engine inflects words itself
 * and reproduces morphstr to the letter.
 *
 * The algorithm: the exception files are consulted first; a hit returns every base form listed on
 * the word's line. Failing that, the per-part-of-speech suffix tables are applied in order, the first
 * candidate that exists in the index winning; a -ful noun is morphed on its stem and re-suffixed; and
 * a collocation is morphed component by component and recombined on its original separators, with a
 * verb-plus-preposition phrase handled specially (morphprep). Candidate existence is checked through
 * [isDefined], which the adapter wires to the WordNet index (WordNet's is_defined / in_wn) — that is
 * index reading, not morphology, so the morphology logic stays here.
 *
 * [posCode] follows WordNet's numbering: 1 noun, 2 verb, 3 adjective, 4 adverb. The engine also calls
 * morphstr with the part of speech shifted down by one (Onym's Ubuntu work-around in wni.c), which
 * passes code 0 for a noun; code 0 has no exception file and no suffix rules, so it yields nothing,
 * exactly as the C library does.
 */
internal class Morphology(
    private val exceptions: Map<Int, Map<String, List<String>>>,
    private val isDefined: (posCode: Int, candidate: String) -> Boolean,
) {
    /**
     * The base forms of [origstr] for [posCode], in the order WordNet's morphstr returns them (the
     * sequence of its first call and its subsequent strtok-style calls). Empty when there are none.
     */
    fun morphstr(
        origstr: String,
        posCode: Int,
    ): List<String> {
        val str = asciiLower(origstr).replace(' ', '_')
        val result = ArrayList<String>()

        // First try the exception list: a hit returns every base form on the word's line.
        val excWords = excLookup(str, posCode)
        if (excWords.isNotEmpty() && excWords.first() != str) {
            result.addAll(excWords)
            return result
        }

        // Then try a straight morph of the whole string (verbs skip this and go via the loop below).
        if (posCode != VERB) {
            val word = morphword(str, posCode)
            if (word != null && word != str) {
                result.add(word)
                return result
            }
        }

        if (posCode == VERB && cntwords(str, '_') > 1 && hasprep(str)) {
            // A verb followed by a preposition: morph the verb and re-attach the rest.
            morphprep(str)?.let { result.add(it) }
            return result
        }

        // Otherwise morph each component of the (possibly single-word) string and recombine on its
        // original separators. For a single word this just morphs the whole word; either way the
        // recombined form must differ from the input and be defined, exactly as morphstr requires.
        val searchstr = StringBuilder()
        var stIdx = 0
        var remaining = cntwords(str, '-')
        while (remaining > 1) {
            val underscore = str.indexOf('_', stIdx)
            val hyphen = str.indexOf('-', stIdx)
            val (endIdx, append) =
                when {
                    underscore in 0..<hyphen || (underscore >= 0 && hyphen < 0) -> underscore to "_"
                    else -> hyphen to "-"
                }
            if (endIdx < 0) return result
            val component = str.substring(stIdx, endIdx)
            searchstr.append(morphword(component, posCode) ?: component).append(append)
            stIdx = endIdx + 1
            remaining--
        }
        val lastComponent = str.substring(stIdx)
        searchstr.append(morphword(lastComponent, posCode) ?: lastComponent)
        val candidate = searchstr.toString()
        if (candidate != str && isDefined(posCode, candidate)) result.add(candidate)
        return result
    }

    /** WordNet's morphword: the base form of a single [word] in [posCode], or null. */
    private fun morphword(
        word: String,
        posCode: Int,
    ): String? {
        if (word.isEmpty()) return null

        // The exception list wins, and is the only source for adverbs.
        excLookup(word, posCode).firstOrNull()?.let { return it }
        if (posCode == ADV) return null

        var stem = word
        var end = ""
        if (posCode == NOUN) {
            if (word.endsWith("ful")) {
                stem = word.substring(0, word.lastIndexOf('f'))
                end = "ful"
            } else if (word.endsWith("ss") || word.length <= 2) {
                return null
            }
        }

        val offset = OFFSETS[posCode]
        val count = COUNTS[posCode]
        for (i in 0 until count) {
            val candidate = wordbase(stem, i + offset)
            if (candidate != stem && isDefined(posCode, candidate)) return candidate + end
        }
        return null
    }

    /** WordNet's wordbase: strip suffix [ender] from [word] and append its replacement, if it matches. */
    private fun wordbase(
        word: String,
        ender: Int,
    ): String {
        if (word.endsWith(SUFX[ender])) {
            return word.substring(0, word.length - SUFX[ender].length) + ADDR[ender]
        }
        return word
    }

    /**
     * WordNet's morphprep: assume the first word of [phrase] is a verb, strip it, and try morphs of the
     * verb (exception list then suffix rules) with the rest re-attached, returning the first phrase that
     * is defined. A three-or-more-word phrase also tries morphing the trailing noun.
     */
    private fun morphprep(phrase: String): String? {
        val firstUnderscore = phrase.indexOf('_')
        val lastUnderscore = phrase.lastIndexOf('_')
        val rest = phrase.substring(firstUnderscore)
        var end: String? = null
        if (firstUnderscore != lastUnderscore) {
            val lastWord = morphword(phrase.substring(lastUnderscore + 1), NOUN)
            if (lastWord != null) end = phrase.substring(firstUnderscore, lastUnderscore + 1) + lastWord
        }

        val word = phrase.substring(0, firstUnderscore)
        if (word.any { !it.isLetterOrDigit() }) return null

        val excWord = excLookup(word, VERB).firstOrNull()
        if (excWord != null && excWord != word) {
            if (isDefined(VERB, excWord + rest)) return excWord + rest
            if (end != null && isDefined(VERB, excWord + end)) return excWord + end
        }

        val offset = OFFSETS[VERB]
        val count = COUNTS[VERB]
        for (i in 0 until count) {
            val base = wordbase(word, i + offset)
            if (base != word) {
                if (isDefined(VERB, base + rest)) return base + rest
                if (end != null && isDefined(VERB, base + end)) return base + end
            }
        }

        if (phrase != word + rest) return word + rest
        if (end != null && phrase != word + end) return word + end
        return null
    }

    /** WordNet's hasprep: true when one of [phrase]'s words after the first is a known preposition. */
    private fun hasprep(phrase: String): Boolean {
        var from = 0
        while (true) {
            val underscore = phrase.indexOf('_', from)
            if (underscore < 0) return false
            val after = underscore + 1
            for (prep in PREPOSITIONS) {
                if (phrase.startsWith(prep, after)) {
                    val boundary = after + prep.length
                    if (boundary == phrase.length || phrase[boundary] == '_') return true
                }
            }
            from = after
        }
    }

    /** Every base form listed for [word] on its line of [posCode]'s exception file; empty if absent. */
    private fun excLookup(
        word: String,
        posCode: Int,
    ): List<String> = exceptions[posCode]?.get(word).orEmpty()

    companion object {
        const val NOUN = 1
        const val VERB = 2
        const val ADJ = 3
        const val ADV = 4

        // The exception files keyed by WordNet part-of-speech number; the file names follow partnames[].
        private val EXC_FILES = mapOf(NOUN to "noun.exc", VERB to "verb.exc", ADJ to "adj.exc", ADV to "adv.exc")

        // morph.c's sufx[]/addr[] suffix-rule tables: noun rules at 0, verb at 8, adjective at 16.
        private val SUFX =
            arrayOf(
                "s", "ses", "xes", "zes", "ches", "shes", "men", "ies",
                "s", "ies", "es", "es", "ed", "ed", "ing", "ing",
                "er", "est", "er", "est",
            )
        private val ADDR =
            arrayOf(
                "", "s", "x", "z", "ch", "sh", "man", "y",
                "", "y", "e", "", "e", "", "e", "",
                "", "", "e", "e",
            )

        // morph.c's offsets[]/cnts[] into the tables, indexed by part-of-speech number (0 is the
        // degenerate shifted-noun case: no rules). Adverbs use only the exception list.
        private val OFFSETS = intArrayOf(0, 0, 8, 16, 0)
        private val COUNTS = intArrayOf(0, 8, 8, 4, 0)

        // morph.c's preposition table, used to spot a verb-plus-preposition phrase.
        private val PREPOSITIONS =
            listOf("to", "at", "of", "on", "off", "in", "out", "up", "down", "from", "with", "into", "for", "about", "between")

        /** Load the exception files from [dataDir]; absent files contribute nothing. */
        fun load(
            dataDir: File,
            isDefined: (posCode: Int, candidate: String) -> Boolean,
        ): Morphology {
            val exceptions = HashMap<Int, Map<String, List<String>>>()
            for ((posCode, name) in EXC_FILES) {
                val file = File(dataDir, name)
                if (!file.isFile) continue
                val map = HashMap<String, List<String>>()
                // The exception files are Latin-1, whitespace-separated: headword then its base forms.
                file.bufferedReader(Charsets.ISO_8859_1).useLines { lines ->
                    for (line in lines) {
                        if (line.isEmpty() || line[0] == ' ') continue
                        val parts = line.trim().split(' ')
                        if (parts.size >= 2) map[parts[0]] = parts.drop(1)
                    }
                }
                exceptions[posCode] = map
            }
            return Morphology(exceptions, isDefined)
        }
    }
}

/** WordNet's cntwords: the number of words in [s] split on spaces, underscores, or [separator]. */
internal fun cntwords(
    s: String,
    separator: Char,
): Int {
    var count = 0
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == separator || c == ' ' || c == '_') {
            count++
            while (i < s.length && (s[i] == separator || s[i] == ' ' || s[i] == '_')) i++
        } else {
            i++
        }
    }
    return count + 1
}
