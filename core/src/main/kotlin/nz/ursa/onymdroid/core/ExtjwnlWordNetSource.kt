// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.core

import net.sf.extjwnl.data.POS
import net.sf.extjwnl.data.Pointer
import net.sf.extjwnl.data.PointerType
import net.sf.extjwnl.data.Synset
import net.sf.extjwnl.dictionary.Dictionary

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
) : WordNetSource {
    override fun baseForms(
        lemma: String,
        pos: WnPos,
    ): List<String> = dictionary.morphologicalProcessor.lookupAllBaseForms(pos.toExtjwnl(), lemma).orEmpty()

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
