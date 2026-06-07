// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.core

import net.sf.extjwnl.dictionary.Dictionary
import java.io.File

/**
 * The engine's public face. It resolves a word to the [OnymResult] model, completes a typed prefix,
 * and suggests near-misses for a missed word. It is the only type the app needs: the WordNet reader,
 * the lemma index, and the relation mapping are all sealed behind it.
 */
class OnymEngine private constructor(
    private val lookup: WordNetLookup,
    private val index: LemmaIndex,
) {
    /**
     * Look [word] up. Returns null when the word is simply not in WordNet (distinct from a missing
     * database, which surfaces as an exception when the engine is opened).
     */
    fun lookup(word: String): OnymResult? = lookup.lookup(word)

    /** Headwords beginning with [prefix], capped at [max] (0 means no cap). */
    fun complete(
        prefix: String,
        max: Int,
    ): List<String> = index.complete(prefix, max)

    /** Spelling suggestions for a missed [word], capped at [max] (0 means no cap). */
    fun suggest(
        word: String,
        max: Int,
    ): List<String> = index.suggest(word, max)

    /**
     * Render [word]'s entry in the onym-cli `--dump` text format. This exists so the engine can be
     * diffed, byte for byte, against the golden oracle; the app renders the model directly instead.
     */
    fun dump(word: String): String =
        buildString {
            val result = lookup.lookup(word)
            if (result == null) {
                append("No entry for \"").append(word).append("\".\n")
                val suggestions = index.suggest(word, SUGGEST_CAP)
                if (suggestions.isNotEmpty()) {
                    append("Did you mean: ").append(suggestions.joinToString(", ")).append('\n')
                }
            } else {
                renderResult(result, this)
            }
        }

    private fun renderResult(
        result: OnymResult,
        out: StringBuilder,
    ) {
        out.append("term: ").append(result.term).append('\n')
        for (section in result.sections) {
            out.append('[').append(section.title).append("]\n")
            when (section) {
                is OnymSection.Definitions -> section.items.forEach { renderDefinition(it, out) }
                is OnymSection.Words -> section.items.forEach { out.append("  - ").append(it.term).append('\n') }
                is OnymSection.Antonyms -> section.items.forEach { renderAntonym(it, out) }
                is OnymSection.Tree -> section.items.forEach { renderTreeNode(it, 0, out) }
            }
        }
    }

    private fun renderDefinition(
        definition: OnymDefinition,
        out: StringBuilder,
    ) {
        if (definition.pos != null) {
            out
                .append("  - (")
                .append(definition.pos)
                .append(") ")
                .append(definition.gloss)
                .append('\n')
        } else {
            out.append("  - ").append(definition.gloss).append('\n')
        }
        definition.examples.forEach { out.append("      \"").append(it).append("\"\n") }
    }

    private fun renderAntonym(
        antonym: OnymAntonym,
        out: StringBuilder,
    ) {
        out
            .append("  - ")
            .append(antonym.term)
            .append(if (antonym.direct) " (direct)\n" else " (indirect)\n")
        antonym.implications.forEach { out.append("      -> ").append(it.term).append('\n') }
    }

    private fun renderTreeNode(
        node: OnymTreeNode,
        depth: Int,
        out: StringBuilder,
    ) {
        repeat(depth + 1) { out.append("  ") }
        out.append("- ").append(node.label).append('\n')
        node.children.forEach { renderTreeNode(it, depth + 1, out) }
    }

    companion object {
        private const val SUGGEST_CAP = 5

        /**
         * Open an engine over the WordNet database in [dataDir]. The directory must be writable: the
         * reader opens the database read-write (see [nz.ursa.onymdroid.core] notes), so on Android the
         * bundled data is unpacked to the files directory first.
         */
        fun open(dataDir: File): OnymEngine {
            val dictionary: Dictionary = Dictionary.getFileBackedInstance(dataDir.absolutePath)
            val source = ExtjwnlWordNetSource(dictionary)
            val index = LemmaIndex.build(dataDir)
            return OnymEngine(WordNetLookup(source), index)
        }
    }
}
