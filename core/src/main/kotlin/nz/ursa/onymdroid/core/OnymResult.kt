// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.core

/*
 * The public lexical model a lookup returns. Every type is an immutable data class and every
 * collection a list, carrying no WordNet or engine types, so the model is the only thing that
 * crosses the boundary out of the engine. Constructors are internal: the engine builds the model,
 * and consumers only read it.
 */

/** The whole entry for a looked-up word: the resolved headword and its ordered sections. */
data class OnymResult internal constructor(
    val term: String,
    val sections: List<OnymSection>,
)

/** A titled group of items of one kind; the kind is encoded by the concrete type. */
sealed interface OnymSection {
    val title: String

    /** Numbered meanings, grouped sense by sense. */
    data class Definitions internal constructor(
        override val title: String,
        val items: List<OnymDefinition>,
    ) : OnymSection

    /** A flat list of clickable terms: synonyms, derived forms, domains, and the like. */
    data class Words internal constructor(
        override val title: String,
        val items: List<OnymWord>,
    ) : OnymSection

    /** Opposites, each direct or indirect, possibly carrying implication terms. */
    data class Antonyms internal constructor(
        override val title: String,
        val items: List<OnymAntonym>,
    ) : OnymSection

    /** A lexical hierarchy, such as is-a, kinds, or part-of, as expandable nodes. */
    data class Tree internal constructor(
        override val title: String,
        val items: List<OnymTreeNode>,
    ) : OnymSection
}

/** A single activatable term, such as a synonym, in display form. */
data class OnymWord internal constructor(
    val term: String,
)

/** One sense of a word: its part of speech (which may be absent), gloss, and example sentences. */
data class OnymDefinition internal constructor(
    val pos: String?,
    val gloss: String,
    val examples: List<String>,
)

/**
 * An opposite of the looked-up word. It is either a direct antonym or an indirect one reached
 * through a similar sense, and it may carry related implication terms.
 */
data class OnymAntonym internal constructor(
    val term: String,
    val direct: Boolean,
    val implications: List<OnymWord>,
)

/**
 * One node of a lexical hierarchy. A node is one synset, so it carries several terms, each a word
 * that can be looked up; [label] is those terms joined for display and for the tree de-duplication
 * the engine does across senses.
 */
data class OnymTreeNode internal constructor(
    val terms: List<String>,
    val children: List<OnymTreeNode>,
) {
    val label: String get() = terms.joinToString(", ")
}
