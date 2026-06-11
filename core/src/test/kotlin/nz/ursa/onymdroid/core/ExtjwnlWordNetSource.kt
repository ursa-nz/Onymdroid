// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.core

import net.sf.extjwnl.data.POS
import net.sf.extjwnl.data.Pointer
import net.sf.extjwnl.data.PointerType
import net.sf.extjwnl.data.Synset
import net.sf.extjwnl.dictionary.Dictionary
import java.io.File

/*
 * The WordNetSource backed by extJWNL. This is the only file in the project that imports extJWNL: it
 * maps extJWNL's Synset and Pointer types onto the neutral Wn* model the engine consumes, and does
 * nothing else. Keeping the borrow sealed here (PLAN §0.2) keeps the path open to swapping the reader
 * without disturbing the engine.
 *
 * Lookups use the exact index (getIndexWord), not extJWNL's morphology-applying lookup, so the engine
 * keeps full control over morphology and reproduces Onym's order of trying the surface form first.
 */
internal class ExtjwnlWordNetSource(
    private val dictionary: Dictionary,
    private val dataDir: File,
) : WordNetSource {
    // The verb example tables live beside the database; load them once, only if a verb example is asked for.
    private val verbExamples by lazy { VerbExampleIndex.load(dataDir) }

    // WordNet's own morphology, reading the exception files beside the database and checking candidate
    // existence against the index — the engine inflects words this way rather than via extJWNL's.
    // morphstr's existence test is WordNet's is_defined, which matches a candidate through its getindex
    // variants, so a base form spelled differently still counts (horse_race -> horse-race).
    private val morphology by lazy {
        Morphology.load(dataDir) { posCode, candidate ->
            val pos = posCode.toWnPos() ?: return@load false
            indexVariants(candidate).any { indexWordExists(it, pos) }
        }
    }

    override fun baseForms(
        lemma: String,
        posShift: Int,
    ): List<String> = morphology.morphstr(lemma, posShift)

    override fun indexWordExists(
        lemma: String,
        pos: WnPos,
    ): Boolean = dictionary.getIndexWord(pos.toExtjwnl(), lemma) != null

    override fun sensesOf(
        lemma: String,
        pos: WnPos,
    ): List<WnSynset> {
        val indexWord = dictionary.getIndexWord(pos.toExtjwnl(), lemma) ?: return emptyList()
        return indexWord.senses.map { it.toWn() }
    }

    override fun synsetAt(
        pos: WnPos,
        offset: Long,
    ): WnSynset? = dictionary.getSynsetAt(pos.toExtjwnl(), offset)?.toWn()

    override fun exampleSentences(
        pos: WnPos,
        offset: Long,
        wordIndex: Int,
    ): List<String> {
        if (pos != WnPos.VERB || wordIndex < 1) return emptyList()
        val synset = dictionary.getSynsetAt(POS.VERB, offset) ?: return emptyList()
        val word = synset.words.getOrNull(wordIndex - 1) ?: return emptyList()
        // The sense key Onym builds for sentidx.vrb: lemma%2:lexfilenum:lexid:: (verb pos is 2).
        val key = "%s%%2:%02d:%02d::".format(word.lemma.replace(' ', '_'), synset.lexFileNum, word.lexId)
        return verbExamples.sentences(key, word.lemma.replace('_', ' '))
    }

    private fun Synset.toWn(): WnSynset =
        WnSynset(
            pos = pos.toWn(),
            offset = offset,
            words = words.map { WnWordRef(it.lemma, it.useCount) },
            gloss = gloss,
            // isAdjectiveCluster() is only defined for adjectives and throws for other parts of speech.
            adjectiveSatellite = pos == POS.ADJECTIVE && isAdjectiveCluster,
            pointers = pointers.mapNotNull { it.toWn() },
        )

    private fun Pointer.toWn(): WnPointer? {
        val relation = type.toWn() ?: return null
        return WnPointer(
            relation = relation,
            sourceWordIndex = sourceIndex,
            targetWordIndex = targetIndex,
            targetPos = targetPOS.toWn(),
            targetOffset = targetOffset,
        )
    }

    private fun POS.toWn(): WnPos =
        when (this) {
            POS.NOUN -> WnPos.NOUN
            POS.VERB -> WnPos.VERB
            POS.ADJECTIVE -> WnPos.ADJECTIVE
            POS.ADVERB -> WnPos.ADVERB
        }

    private fun WnPos.toExtjwnl(): POS =
        when (this) {
            WnPos.NOUN -> POS.NOUN
            WnPos.VERB -> POS.VERB
            WnPos.ADJECTIVE -> POS.ADJECTIVE
            WnPos.ADVERB -> POS.ADVERB
        }

    // WordNet's part-of-speech numbering, as the morphology uses it; null for the degenerate code 0.
    private fun Int.toWnPos(): WnPos? =
        when (this) {
            Morphology.NOUN -> WnPos.NOUN
            Morphology.VERB -> WnPos.VERB
            Morphology.ADJ -> WnPos.ADJECTIVE
            Morphology.ADV -> WnPos.ADVERB
            else -> null
        }

    private fun PointerType.toWn(): WnRelation? =
        when (this) {
            PointerType.ANTONYM -> WnRelation.ANTONYM

            PointerType.HYPERNYM -> WnRelation.HYPERNYM

            PointerType.INSTANCE_HYPERNYM -> WnRelation.INSTANCE_HYPERNYM

            PointerType.HYPONYM -> WnRelation.HYPONYM

            PointerType.INSTANCES_HYPONYM -> WnRelation.INSTANCE_HYPONYM

            PointerType.ENTAILMENT -> WnRelation.ENTAILMENT

            PointerType.SIMILAR_TO -> WnRelation.SIMILAR_TO

            PointerType.MEMBER_HOLONYM -> WnRelation.MEMBER_HOLONYM

            PointerType.SUBSTANCE_HOLONYM -> WnRelation.SUBSTANCE_HOLONYM

            PointerType.PART_HOLONYM -> WnRelation.PART_HOLONYM

            PointerType.MEMBER_MERONYM -> WnRelation.MEMBER_MERONYM

            PointerType.SUBSTANCE_MERONYM -> WnRelation.SUBSTANCE_MERONYM

            PointerType.PART_MERONYM -> WnRelation.PART_MERONYM

            PointerType.CAUSE -> WnRelation.CAUSE

            PointerType.PERTAINYM -> WnRelation.PERTAINYM

            PointerType.ATTRIBUTE -> WnRelation.ATTRIBUTE

            PointerType.DERIVATION -> WnRelation.DERIVATION

            PointerType.VERB_GROUP -> WnRelation.VERB_GROUP

            PointerType.SEE_ALSO -> WnRelation.ALSO_SEE

            PointerType.PARTICIPLE_OF -> WnRelation.PARTICIPLE

            PointerType.CATEGORY -> WnRelation.CATEGORY

            PointerType.USAGE -> WnRelation.USAGE

            PointerType.REGION -> WnRelation.REGION

            PointerType.CATEGORY_MEMBER -> WnRelation.CATEGORY_MEMBER

            PointerType.USAGE_MEMBER -> WnRelation.USAGE_MEMBER

            PointerType.REGION_MEMBER -> WnRelation.REGION_MEMBER

            // DOMAIN_ALL and MEMBER_ALL are aggregates the engine does not use.
            else -> null
        }
}
