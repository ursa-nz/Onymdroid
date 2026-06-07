// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.core

/*
 * The bridge from the WordNet reader to the public model. It walks the senses a WordNetSource
 * returns and gathers them into the ordered, titled sections the model carries, reproducing the
 * rules of Onym's wni.c engine and onym-lookup.c bridge:
 *
 *   overview definitions  -> a Definitions section, grouped by part of speech and sense
 *   overview synonyms     -> a Synonyms section
 *   antonyms              -> an Antonyms section, direct and indirect (the adjective-cluster case)
 *   derivations, similar, attributes, causes, entails -> flat Words sections
 *   pertainyms, hypernyms, hyponyms, holonyms, meronyms -> Tree sections, grown to full depth
 *   domains               -> a Domains words section
 *
 * Lexical knowledge lives here, not in the UI: this file decides section order, titles, the
 * direct/indirect antonym distinction, and which sections are dropped for being empty.
 */
internal class WordNetLookup(
    private val source: WordNetSource,
) {
    /** Look [query] up and build its entry, or null when the word is simply not in WordNet. */
    fun lookup(query: String): OnymResult? {
        val normalized = normalize(query) ?: return null
        val senses = gatherSenses(normalized)
        if (senses.isEmpty()) return null

        val definitionItems = buildDefinitionItems(senses, normalized)
        if (definitionItems.isEmpty()) return null
        val headwordLemmas = definitionItems.map { it.lemma }.toSet()
        val headword = definitionItems.first().displayLemma

        val sections =
            buildList {
                addDefinitions(definitionItems)
                addWords("Synonyms", buildSynonyms(senses, headwordLemmas))
                addAntonyms(senses)
                addWords("Derived forms", buildFlat(senses, setOf(WnRelation.DERIVATION)))
                addWords("Similar to", buildFlat(senses, setOf(WnRelation.SIMILAR_TO), adjectivesOnly = true))
                addWords("Attributes", buildFlat(senses, setOf(WnRelation.ATTRIBUTE)))
                addWords("Causes", buildFlat(senses, setOf(WnRelation.CAUSE)))
                addWords("Entails", buildFlat(senses, setOf(WnRelation.ENTAILMENT)))
                addTree("Pertains to", buildPertainyms(senses))
                addTree("Is a kind of", buildTree(senses, HYPERNYM_GROUPS))
                addTree("Kinds", buildTree(senses, HYPONYM_GROUPS))
                addTree("Part of", buildTree(senses, HOLONYM_GROUPS))
                addTree("Parts", buildMeronyms(senses))
                addWords("Domains", buildDomains(senses))
            }
        return OnymResult(headword, sections)
    }

    /** A processed sense: the synset, the lemma that found it, and that lemma's index in the synset. */
    private class Sense(
        val lemma: String,
        val pos: WnPos,
        val synset: WnSynset,
        val whichWord: Int,
    )

    private fun normalize(query: String): String? {
        var lemma = query.trim().replace(' ', '_')
        val paren = lemma.indexOf('(')
        if (paren >= 0) lemma = lemma.substring(0, paren)
        lemma = asciiLower(lemma)
        if (lemma.isEmpty() || lemma == "." || lemma == "-" || lemma == "_") return null
        return lemma
    }

    /** Gather every sense across the four parts of speech, surface form then morphology, deduped by offset. */
    private fun gatherSenses(normalized: String): List<Sense> {
        val senses = ArrayList<Sense>()
        // Each (lemma, part of speech) contributes its senses independently — Onym does not
        // de-duplicate across morphology variants, so a synset shared by two of them (good and well
        // both mean "resulting favorably") is listed under each, as the oracle does.
        for (pos in POS_ORDER) {
            val candidates = LinkedHashSet<String>()
            candidates.add(normalized)
            val wordCount = normalized.count { it == '_' }
            for (base in source.baseForms(normalized, pos)) {
                val candidate = asciiLower(toQueryForm(base))
                // Morphology must preserve the collocation's word count: extJWNL otherwise decomposes a
                // multiword query into its parts (ice cream -> ice, cream), which WordNet's morphstr does not.
                if (candidate.count { it == '_' } == wordCount) candidates.add(candidate)
            }
            for (lemma in candidates) {
                for (synset in source.sensesOf(lemma, pos)) {
                    senses.add(Sense(lemma, pos, synset, whichWord(synset, lemma)))
                }
            }
        }
        return senses
    }

    private fun whichWord(
        synset: WnSynset,
        lemma: String,
    ): Int {
        val target = displayLower(lemma)
        val index = synset.words.indexOfFirst { displayLower(it.lemma) == target }
        return if (index >= 0) index + 1 else 0
    }

    // --- Definitions and the headword ---------------------------------------------------------------

    private class DefinitionItem(
        val lemma: String,
        val displayLemma: String,
        val pos: WnPos,
        val definitions: List<OnymDefinition>,
        val tagCount: Int,
        val polysemy: Int,
    )

    /**
     * Group senses into one item per part of speech, then order the items as Onym does: an exact match
     * to the query wins, then the higher first-sense tag count, then the higher polysemy. The headword
     * is the top item's lemma.
     */
    private fun buildDefinitionItems(
        senses: List<Sense>,
        normalized: String,
    ): List<DefinitionItem> {
        // One item per (lemma, part of speech), as Onym does: morphology can yield several lemmas
        // (better -> good, well, better) and each is ordered independently.
        val grouped = LinkedHashMap<Pair<String, WnPos>, MutableList<Sense>>()
        for (sense in senses) grouped.getOrPut(sense.lemma to sense.pos) { ArrayList() }.add(sense)

        val items = ArrayList<DefinitionItem>()
        for ((key, group) in grouped) {
            val (lemma, pos) = key
            val displayLemma = properCase(lemma, group.first().synset)
            val definitions =
                group.map { sense ->
                    val (gloss, examples) = parseDefinition(sense.synset.gloss)
                    OnymDefinition(posName(pos), gloss, examples)
                }
            val tagCount =
                group.first().let {
                    it.synset.words
                        .getOrNull(it.whichWord - 1)
                        ?.useCount ?: 0
                }
            items.add(DefinitionItem(lemma, displayLemma, pos, definitions, tagCount, group.size))
        }

        items.sortWith(
            Comparator { a, b ->
                val aExact = if (displayLower(a.lemma) == displayLower(normalized)) 1 else 0
                val bExact = if (displayLower(b.lemma) == displayLower(normalized)) 1 else 0
                if (aExact != bExact) return@Comparator bExact - aExact
                if (a.tagCount != b.tagCount) return@Comparator b.tagCount - a.tagCount
                b.polysemy - a.polysemy
            },
        )
        return items
    }

    /** Restore the case WordNet stores for the headword (e.g. wordsworth -> Wordsworth). */
    private fun properCase(
        lemma: String,
        synset: WnSynset,
    ): String {
        val match = synset.words.firstOrNull { displayLower(it.lemma) == displayLower(lemma) }
        return toDisplayForm(match?.lemma ?: lemma)
    }

    private fun MutableList<OnymSection>.addDefinitions(items: List<DefinitionItem>) {
        val definitions = items.flatMap { it.definitions }
        if (definitions.isNotEmpty()) add(OnymSection.Definitions("Definitions", definitions))
    }

    // --- Synonyms -----------------------------------------------------------------------------------

    private fun buildSynonyms(
        senses: List<Sense>,
        headwordLemmas: Set<String>,
    ): List<OnymWord> {
        val headwords = headwordLemmas.map { displayLower(it) }.toSet()
        // A multiword headword's space-collapsed variant (e.g. "icecream" for "ice cream") is not
        // listed as its own synonym, matching the oracle.
        val collapsedMultiword = headwords.filter { ' ' in it }.map { it.replace(" ", "") }.toSet()
        val seen = LinkedHashSet<String>()
        for (sense in senses) {
            val selfLower = displayLower(sense.lemma)
            for (word in sense.synset.words) {
                val lower = displayLower(word.lemma)
                if (lower == selfLower || lower in headwords) continue
                if (lower.replace(" ", "") in collapsedMultiword) continue
                seen.add(toDisplayForm(word.lemma))
            }
        }
        return seen.map { OnymWord(it) }
    }

    // --- Antonyms -----------------------------------------------------------------------------------

    private fun MutableList<OnymSection>.addAntonyms(senses: List<Sense>) {
        val antonyms = LinkedHashMap<String, OnymAntonym>()
        for (sense in senses) {
            val gathered =
                if (sense.pos == WnPos.ADJECTIVE) adjectiveAntonyms(sense) else plainAntonyms(sense)
            for (antonym in gathered) {
                val existing = antonyms[antonym.term]
                antonyms[antonym.term] =
                    if (existing == null) {
                        antonym
                    } else {
                        existing.copy(implications = mergeWords(existing.implications, antonym.implications))
                    }
            }
        }
        if (antonyms.isNotEmpty()) add(OnymSection.Antonyms("Antonyms", antonyms.values.toList()))
    }

    /** Noun, verb, and adverb antonyms: every one is direct; implications are the antonym's synset-mates. */
    private fun plainAntonyms(sense: Sense): List<OnymAntonym> {
        val result = ArrayList<OnymAntonym>()
        for (pointer in sense.synset.pointers) {
            if (pointer.relation != WnRelation.ANTONYM) continue
            if (!sourceApplies(pointer, sense.whichWord) || pointer.targetWordIndex == 0) continue
            val target = source.synsetAt(pointer.targetPos, pointer.targetOffset) ?: continue
            val term = toDisplayForm(target.words[pointer.targetWordIndex - 1].lemma)
            val implications =
                target.words
                    .map { toDisplayForm(it.lemma) }
                    .filter { it != term }
                    .map { OnymWord(it) }
            result.add(OnymAntonym(term, direct = true, implications = implications))
        }
        return result
    }

    /**
     * Adjective antonyms. A cluster head (or standalone) reports its direct antonyms; a satellite has
     * none of its own, so it follows similar-to to its head and reports the head's antonym indirectly.
     */
    private fun adjectiveAntonyms(sense: Sense): List<OnymAntonym> =
        if (sense.synset.adjectiveSatellite) {
            indirectAdjectiveAntonyms(sense)
        } else {
            directAdjectiveAntonyms(sense)
        }

    private fun directAdjectiveAntonyms(sense: Sense): List<OnymAntonym> {
        val result = ArrayList<OnymAntonym>()
        for (pointer in sense.synset.pointers) {
            if (pointer.relation != WnRelation.ANTONYM || pointer.sourceWordIndex != sense.whichWord) continue
            val antonymSynset = source.synsetAt(pointer.targetPos, pointer.targetOffset) ?: continue
            val term = toDisplayForm(antonymSynset.words.first().lemma)
            val implications = LinkedHashSet<String>()
            antonymSynset.words.drop(1).forEach { implications.add(toDisplayForm(it.lemma)) }
            for (similar in antonymSynset.pointers.filter { it.relation == WnRelation.SIMILAR_TO }) {
                val cluster = source.synsetAt(similar.targetPos, similar.targetOffset) ?: continue
                cluster.words.forEach { implications.add(toDisplayForm(it.lemma)) }
            }
            implications.remove(term)
            result.add(OnymAntonym(term, direct = true, implications = implications.map { OnymWord(it) }))
        }
        return result
    }

    private fun indirectAdjectiveAntonyms(sense: Sense): List<OnymAntonym> {
        val head =
            sense.synset.pointers
                .firstOrNull { it.relation == WnRelation.SIMILAR_TO }
                ?.let { source.synsetAt(it.targetPos, it.targetOffset) }
                ?: return emptyList()

        val result = ArrayList<OnymAntonym>()
        for (pointer in head.pointers) {
            if (pointer.relation != WnRelation.ANTONYM) continue
            val antonymHead = source.synsetAt(pointer.targetPos, pointer.targetOffset) ?: continue
            val term = indirectVia(antonymHead) ?: continue
            val implications =
                antonymHead.words
                    .map { toDisplayForm(it.lemma) }
                    .filter { it != term }
                    .map { OnymWord(it) }
            result.add(OnymAntonym(term, direct = false, implications = implications))
        }
        return result
    }

    /**
     * Resolve the specific opposing word for an indirect antonym: from the antonym's head, follow its
     * antonym pointers back to the synset that points at ours, and take the word at that pointer's end.
     */
    private fun indirectVia(antonymHead: WnSynset): String? {
        for (pointer in antonymHead.pointers) {
            if (pointer.relation != WnRelation.ANTONYM || pointer.sourceWordIndex != 1) continue
            val back = source.synsetAt(pointer.targetPos, pointer.targetOffset) ?: continue
            for (inner in back.pointers) {
                if (inner.relation == WnRelation.ANTONYM && inner.targetWordIndex == 1 &&
                    inner.targetOffset == antonymHead.offset
                ) {
                    val wordIndex = if (inner.sourceWordIndex > 0) inner.sourceWordIndex - 1 else 0
                    return back.words.getOrNull(wordIndex)?.let { toDisplayForm(it.lemma) }
                }
            }
        }
        return null
    }

    // --- Flat relations and domains -----------------------------------------------------------------

    private fun buildFlat(
        senses: List<Sense>,
        relations: Set<WnRelation>,
        adjectivesOnly: Boolean = false,
    ): List<OnymWord> {
        val seen = LinkedHashSet<String>()
        for (sense in senses) {
            if (adjectivesOnly && sense.pos != WnPos.ADJECTIVE) continue
            val selfLower = displayLower(sense.lemma)
            for (pointer in sense.synset.pointers) {
                if (pointer.relation !in relations) continue
                if (!sourceApplies(pointer, sense.whichWord)) continue
                val target = source.synsetAt(pointer.targetPos, pointer.targetOffset) ?: continue
                for (word in target.words) {
                    if (displayLower(word.lemma) == selfLower) continue
                    seen.add(toDisplayForm(word.lemma))
                }
            }
        }
        return seen.map { OnymWord(it) }
    }

    private fun buildDomains(senses: List<Sense>): List<OnymWord> =
        buildFlat(
            senses,
            setOf(
                WnRelation.CATEGORY,
                WnRelation.USAGE,
                WnRelation.REGION,
                WnRelation.CATEGORY_MEMBER,
                WnRelation.USAGE_MEMBER,
                WnRelation.REGION_MEMBER,
            ),
        )

    private fun MutableList<OnymSection>.addWords(
        title: String,
        words: List<OnymWord>,
    ) {
        if (words.isNotEmpty()) add(OnymSection.Words(title, words))
    }

    // --- Trees --------------------------------------------------------------------------------------

    private fun MutableList<OnymSection>.addTree(
        title: String,
        items: List<OnymTreeNode>,
    ) {
        if (items.isNotEmpty()) add(OnymSection.Tree(title, items))
    }

    /**
     * Build a tree section: per sense, grow each relation group to [maxDepth], then de-duplicate the
     * top-level nodes by label across senses (first occurrence wins), as the bridge does.
     */
    private fun buildTree(
        senses: List<Sense>,
        groups: List<Set<WnRelation>>,
        maxDepth: Int = MAX_TREE_DEPTH,
    ): List<OnymTreeNode> {
        val seen = HashSet<String>()
        val nodes = ArrayList<OnymTreeNode>()
        for (sense in senses) {
            for (group in groups) {
                for (node in growNodes(sense.synset, sense.whichWord, group, displayLower(sense.lemma), 0, maxDepth)) {
                    if (seen.add(node.label)) nodes.add(node)
                }
            }
        }
        return nodes
    }

    private fun growNodes(
        synset: WnSynset,
        whichWord: Int,
        group: Set<WnRelation>,
        selfLemmaLower: String,
        depth: Int,
        maxDepth: Int,
    ): List<OnymTreeNode> {
        val nodes = ArrayList<OnymTreeNode>()
        for (pointer in synset.pointers) {
            if (pointer.relation !in group) continue
            if (!sourceApplies(pointer, whichWord)) continue
            val target = source.synsetAt(pointer.targetPos, pointer.targetOffset) ?: continue
            val terms = target.words.map { toDisplayForm(it.lemma) }.filter { displayLower(it) != selfLemmaLower }
            if (terms.isEmpty()) continue
            val children =
                if (depth + 1 < maxDepth) {
                    growNodes(target, 0, group, selfLemmaLower, depth + 1, maxDepth)
                } else {
                    emptyList()
                }
            nodes.add(OnymTreeNode(terms, children))
        }
        return nodes
    }

    /** Pertainyms, with one level of hypernyms shown beneath each, as Onym does. */
    private fun buildPertainyms(senses: List<Sense>): List<OnymTreeNode> {
        val seen = HashSet<String>()
        val nodes = ArrayList<OnymTreeNode>()
        for (sense in senses) {
            for (pointer in sense.synset.pointers) {
                if (pointer.relation != WnRelation.PERTAINYM) continue
                if (!sourceApplies(pointer, sense.whichWord)) continue
                val target = source.synsetAt(pointer.targetPos, pointer.targetOffset) ?: continue
                val selfLower = displayLower(sense.lemma)
                val terms = target.words.map { toDisplayForm(it.lemma) }.filter { displayLower(it) != selfLower }
                if (terms.isEmpty()) continue
                val children = growNodes(target, 0, HYPERNYM_GROUPS.first(), selfLower, 0, maxDepth = 1)
                val node = OnymTreeNode(terms, children)
                if (seen.add(node.label)) nodes.add(node)
            }
        }
        return nodes
    }

    /**
     * Parts (meronyms): the three subtypes combined in order. The tree is flat unless a hypernym
     * ancestor of the word also has meronyms (Onym's bit(HMERONYM) test); then the meronyms grow to
     * full depth and each ancestor's inherited meronyms are traced as well.
     */
    private fun buildMeronyms(senses: List<Sense>): List<OnymTreeNode> {
        val inherited = senses.any { ancestorHasMeronym(it.synset) }
        val maxDepth = if (inherited) MAX_TREE_DEPTH else 1
        val seen = HashSet<String>()
        val nodes = ArrayList<OnymTreeNode>()
        for (sense in senses) {
            val selfLower = displayLower(sense.lemma)
            val topLevel = ArrayList<OnymTreeNode>()
            for (group in MERONYM_GROUPS) {
                topLevel.addAll(growNodes(sense.synset, sense.whichWord, group, selfLower, 0, maxDepth))
            }
            if (inherited) {
                topLevel.addAll(traceInherit(sense.synset, sense.whichWord, selfLower, 1))
            }
            for (node in topLevel) {
                if (seen.add(node.label)) nodes.add(node)
            }
        }
        return nodes
    }

    /** Whether an immediate hypernym of the synset has a meronym; Onym's bit(HMERONYM) test. */
    private fun ancestorHasMeronym(synset: WnSynset): Boolean =
        synset.pointers.any { pointer ->
            pointer.relation == WnRelation.HYPERNYM &&
                source
                    .synsetAt(pointer.targetPos, pointer.targetOffset)
                    ?.pointers
                    ?.any { it.relation in MERONYM_RELATIONS } == true
        }

    /** Trace inherited meronyms up the is-a chain, keeping only ancestors that contribute parts. */
    private fun traceInherit(
        synset: WnSynset,
        whichWord: Int,
        selfLemmaLower: String,
        depth: Int,
    ): List<OnymTreeNode> {
        val nodes = ArrayList<OnymTreeNode>()
        for (pointer in synset.pointers) {
            if (pointer.relation != WnRelation.HYPERNYM) continue
            if (!sourceApplies(pointer, whichWord)) continue
            val ancestor = source.synsetAt(pointer.targetPos, pointer.targetOffset) ?: continue
            val ancestorTerms =
                ancestor.words.map { toDisplayForm(it.lemma) }.filter { displayLower(it) != selfLemmaLower }
            if (ancestorTerms.isEmpty()) continue
            val children = ArrayList<OnymTreeNode>()
            for (group in MERONYM_GROUPS) {
                children.addAll(growNodes(ancestor, 0, group, selfLemmaLower, 0, MAX_TREE_DEPTH))
            }
            if (depth + 1 < MAX_TREE_DEPTH) {
                children.addAll(traceInherit(ancestor, 0, selfLemmaLower, depth + 1))
            }
            if (children.isNotEmpty()) nodes.add(OnymTreeNode(ancestorTerms, children))
        }
        return nodes
    }

    // --- Shared helpers -----------------------------------------------------------------------------

    private fun sourceApplies(
        pointer: WnPointer,
        whichWord: Int,
    ): Boolean = pointer.sourceWordIndex == 0 || pointer.sourceWordIndex == whichWord

    private fun mergeWords(
        existing: List<OnymWord>,
        extra: List<OnymWord>,
    ): List<OnymWord> {
        val terms = LinkedHashSet(existing.map { it.term })
        extra.forEach { terms.add(it.term) }
        return terms.map { OnymWord(it) }
    }

    /**
     * Split a gloss into its definition and example sentences, reproducing Onym's parse_definition.
     * WordNet glosses run the definition and quoted examples together, separated by semicolons, and an
     * example may carry an attribution after its closing quote ("...- Wordsworth"). The gloss is wrapped
     * in parentheses to match the form the original parser expects (the WordNet C library adds them; the
     * reader here strips them).
     */
    private fun parseDefinition(gloss: String): Pair<String, List<String>> {
        // extJWNL keeps a trailing space the WordNet C library trims; drop it so examples match.
        val str = "(" + gloss.trimEnd() + ")"
        val len = str.length - 1 // skip the closing parenthesis
        val out = StringBuilder()
        var braceMet = 0
        var doubleQuotes = 0
        var justEnded = false
        var i = 1 // skip the opening parenthesis
        while (i < len) {
            var ch: Char? = str[i]
            when {
                str[i] == '"' -> {
                    // An opening quote (even count) starts an example: turn the preceding separator into a
                    // delimiter, collapsing a preceding comma as the original does for "compound".
                    if (doubleQuotes and 1 == 0 && i > 0 && str[i - 1] != '(') {
                        if (out.length >= 2 && out[out.length - 2] == ',') out.setLength(out.length - 1)
                        if (out.isNotEmpty() && out[out.length - 1] != '|') out[out.length - 1] = '|'
                    }
                    doubleQuotes++
                    justEnded = false
                    ch = null
                }

                str[i] == ' ' && justEnded -> {
                    ch = null
                }

                str[i] == '(' && justEnded -> {
                    justEnded = false
                    braceMet = 1
                }

                str[i] == ')' && braceMet != 0 -> {
                    out.insert(braceMet - 1, ") ")
                    braceMet = 0
                    ch = null
                }

                str[i] == ';' &&
                    (
                        i + 1 == len ||
                            (doubleQuotes and 1 == 0 && str.getOrNull(i + 2) == '"') ||
                            str.getOrNull(i + 2) == '('
                    ) -> {
                    ch = '|'
                    justEnded = true
                }
            }
            if (ch != null) {
                if (braceMet == 0) {
                    out.append(ch)
                } else {
                    out.insert(braceMet - 1, ch)
                    braceMet++
                }
            }
            i++
        }
        val parts = out.toString().split('|')
        return parts[0] to parts.drop(1).filter { it.isNotEmpty() }
    }

    private fun posName(pos: WnPos): String =
        when (pos) {
            WnPos.NOUN -> "noun"
            WnPos.VERB -> "verb"
            WnPos.ADJECTIVE -> "adjective"
            WnPos.ADVERB -> "adverb"
        }

    private companion object {
        private val POS_ORDER = listOf(WnPos.NOUN, WnPos.VERB, WnPos.ADJECTIVE, WnPos.ADVERB)
        private const val MAX_TREE_DEPTH = 20

        private val HYPERNYM_GROUPS = listOf(setOf(WnRelation.HYPERNYM, WnRelation.INSTANCE_HYPERNYM))
        private val HYPONYM_GROUPS = listOf(setOf(WnRelation.HYPONYM, WnRelation.INSTANCE_HYPONYM))
        private val HOLONYM_GROUPS =
            listOf(
                setOf(WnRelation.MEMBER_HOLONYM),
                setOf(WnRelation.SUBSTANCE_HOLONYM),
                setOf(WnRelation.PART_HOLONYM),
            )
        private val MERONYM_GROUPS =
            listOf(
                setOf(WnRelation.MEMBER_MERONYM),
                setOf(WnRelation.SUBSTANCE_MERONYM),
                setOf(WnRelation.PART_MERONYM),
            )
        private val MERONYM_RELATIONS =
            setOf(WnRelation.MEMBER_MERONYM, WnRelation.SUBSTANCE_MERONYM, WnRelation.PART_MERONYM)
    }
}

/** ASCII-only lower-casing, matching WordNet's strtolower (locale-independent). */
internal fun asciiLower(value: String): String =
    buildString(value.length) {
        for (c in value) append(if (c in 'A'..'Z') c + 32 else c)
    }

/** The lowercased display form: underscores to spaces and ASCII upper-case to lower-case. */
internal fun displayLower(value: String): String = asciiLower(toDisplayForm(value))
