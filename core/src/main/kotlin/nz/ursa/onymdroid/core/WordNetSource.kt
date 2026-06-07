// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.core

/*
 * The boundary to the WordNet reader. The engine talks only to WordNetSource and the neutral Wn*
 * types below; nothing here or above knows that extJWNL exists, so replacing the reader means
 * writing one more implementation of this interface and nothing else (see ExtjwnlWordNetSource and
 * PLAN §0.2).
 *
 * The types mirror exactly what the lookup needs from WordNet: the senses of a word, the synset a
 * pointer leads to, and morphology. Word and pointer indices follow WordNet's convention — 0 means
 * the whole synset (a semantic pointer) and a positive index identifies one word (a lexical
 * pointer), which is the distinction the antonym logic turns on.
 */

internal enum class WnPos { NOUN, VERB, ADJECTIVE, ADVERB }

internal enum class WnRelation {
    ANTONYM,
    HYPERNYM,
    INSTANCE_HYPERNYM,
    HYPONYM,
    INSTANCE_HYPONYM,
    ENTAILMENT,
    SIMILAR_TO,
    MEMBER_HOLONYM,
    SUBSTANCE_HOLONYM,
    PART_HOLONYM,
    MEMBER_MERONYM,
    SUBSTANCE_MERONYM,
    PART_MERONYM,
    CAUSE,
    PERTAINYM,
    ATTRIBUTE,
    DERIVATION,
    VERB_GROUP,
    ALSO_SEE,
    PARTICIPLE,
    CATEGORY,
    USAGE,
    REGION,
    CATEGORY_MEMBER,
    USAGE_MEMBER,
    REGION_MEMBER,
}

/** One word of a synset: its lemma in WordNet form (underscores for spaces) and its tagged-use count. */
internal data class WnWordRef(
    val lemma: String,
    val useCount: Int,
)

/**
 * A pointer from one synset to another. [sourceWordIndex] and [targetWordIndex] are 0 for a
 * whole-synset (semantic) pointer, or a positive word position for a lexical pointer.
 */
internal data class WnPointer(
    val relation: WnRelation,
    val sourceWordIndex: Int,
    val targetWordIndex: Int,
    val targetPos: WnPos,
    val targetOffset: Long,
)

/** A synset: its part of speech, offset, words, gloss, adjective-satellite flag, and outgoing pointers. */
internal data class WnSynset(
    val pos: WnPos,
    val offset: Long,
    val words: List<WnWordRef>,
    val gloss: String,
    val adjectiveSatellite: Boolean,
    val pointers: List<WnPointer>,
)

/** The WordNet reader the engine depends on; only its adapter implementation knows extJWNL. */
internal interface WordNetSource {
    /** Base forms of [lemma] for [pos] via morphology; empty if there are none. */
    fun baseForms(
        lemma: String,
        pos: WnPos,
    ): List<String>

    /** The senses of [lemma] in [pos], in WordNet sense order; empty if [lemma] is not defined. */
    fun sensesOf(
        lemma: String,
        pos: WnPos,
    ): List<WnSynset>

    /** The synset at [pos] and [offset], for following a pointer; null if absent. */
    fun synsetAt(
        pos: WnPos,
        offset: Long,
    ): WnSynset?
}
