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
 *   pertainyms, hypernyms, hyponyms -> Tree sections, grown to full depth
 *   holonyms, meronyms    -> Tree sections, grown to full depth only when Onym's is_defined depth
 *                            bit is set (see buildHolonyms / buildMeronyms), otherwise kept flat
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
        val gathered = gatherSenses(normalized)
        val senses = gathered.senses
        if (senses.isEmpty()) return null

        val definitionItems = buildDefinitionItems(senses, normalized)
        if (definitionItems.isEmpty()) return null
        val headword = definitionItems.first().displayLemma
        val nounDepth = computeNounDepth(senses)

        val sections =
            buildList {
                addDefinitions(definitionItems)
                addWords("Synonyms", buildSynonyms(senses, gathered.lemmas))
                addAntonyms(senses)
                addWords("Derived forms", buildFlat(senses, setOf(WnRelation.DERIVATION)))
                addWords("Similar to", buildFlat(senses, setOf(WnRelation.SIMILAR_TO), adjectivesOnly = true))
                addWords("Attributes", buildFlat(senses, setOf(WnRelation.ATTRIBUTE)))
                addWords("Causes", buildFlat(senses, setOf(WnRelation.CAUSE)))
                addWords("Entails", buildFlat(senses, setOf(WnRelation.ENTAILMENT)))
                addTree("Pertains to", buildPertainyms(senses))
                addTree("Is a kind of", buildTree(senses, HYPERNYM_GROUPS))
                addTree("Kinds", buildTree(senses, HYPONYM_GROUPS))
                addTree("Part of", buildHolonyms(senses, nounDepth))
                addTree("Parts", buildMeronyms(senses, nounDepth))
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

    /** The gathered senses and the set of lemmas that found them, for synonym exclusion. */
    private class Gathered(
        val senses: List<Sense>,
        val lemmas: Set<String>,
    )

    /**
     * Gather every sense across the four parts of speech, reproducing Onym's wni_request_nyms: for each
     * part of speech the surface form is searched, then morphology, and each searched form is expanded
     * to its index variants the way WordNet's getindex does (hyphen/underscore/joined/period forms).
     * Every variant that resolves contributes a lemma (used to suppress those forms as their own
     * synonyms), and its senses unless their synset has already been seen for that searched form.
     *
     * The morphology dispatch mirrors wni.c exactly, including its Ubuntu work-around: morphology is
     * tried first with the part of speech shifted down by one, and only if that yields nothing (across
     * all parts of speech so far — the flag is sticky) is it retried at the correct part of speech.
     */
    private fun gatherSenses(normalized: String): Gathered {
        val senses = ArrayList<Sense>()
        val lemmas = LinkedHashSet<String>()
        var morphwordInFile = true
        for ((index, pos) in POS_ORDER.withIndex()) {
            // The surface form, then each base form WordNet's morphstr yields.
            gather(normalized, pos, senses, lemmas)

            val shiftedBases = if (morphwordInFile) source.baseForms(normalized, index) else emptyList()
            if (shiftedBases.isNotEmpty()) {
                for (base in shiftedBases) gather(base, pos, senses, lemmas)
            } else {
                val bases = source.baseForms(normalized, index + 1)
                if (bases.isNotEmpty()) {
                    morphwordInFile = false
                    for (base in bases) gather(base, pos, senses, lemmas)
                }
            }
        }
        return Gathered(senses, lemmas)
    }

    /**
     * Search [form] in [pos] through its WordNet getindex variants, adding each resolved variant's
     * lemma to [lemmas] and its senses to [senses] (offset-deduplicated across variants, as Onym's
     * populate does).
     *
     * Every resolving variant is always visited. This is fix 1 of the onym-engine spec's deliberate
     * fixes: the WordNet C library's populate, while building a noun's part-of / parts tree, calls
     * is_defined, which shares getindex's static iteration state and cuts short populate's own walk
     * over the remaining variants, so a noun carrying meronyms or holonyms only ever saw its first
     * variant. That is why "shore bird" used to keep "shorebird" as a synonym while "ash bin"
     * suppressed ash-bin and ashbin. Variant suppression now behaves uniformly for every word.
     */
    private fun gather(
        form: String,
        pos: WnPos,
        senses: MutableList<Sense>,
        lemmas: MutableSet<String>,
    ) {
        val seenOffsets = HashSet<Long>()
        for (variant in indexVariants(form)) {
            val variantSenses = source.sensesOf(variant, pos)
            if (variantSenses.isEmpty()) continue
            lemmas.add(displayLower(variant))
            senses.addAll(
                variantSenses
                    .filter { seenOffsets.add(it.offset) }
                    .map { Sense(variant, pos, it, whichWord(it, variant)) },
            )
        }
    }

    private fun whichWord(
        synset: WnSynset,
        lemma: String,
    ): Int {
        // WordNet's read_synset keeps the LAST synset word whose lower-case matches, not the first, so
        // a synset carrying both "utopian" and "Utopian" resolves to the second; lexical pointers
        // restricted to that word index then apply, which is how "utopian" reaches its "Utopia"
        // derivation.
        val target = displayLower(lemma)
        val index = synset.words.indexOfLast { displayLower(it.lemma) == target }
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

        // Items are built in WordNet's part-of-speech order (noun, verb, adjective, adverb), which is
        // the order [POS_ORDER] gathered them, so case-claiming below follows Onym's processing order.
        val claimed = HashSet<String>()
        val items = ArrayList<DefinitionItem>()
        for ((key, group) in grouped) {
            val (lemma, pos) = key
            val displayLemma = resolveDisplayLemma(lemma, group.first().synset, claimed)
            claimed.add(displayLemma)
            val definitions =
                group.map { sense ->
                    val (gloss, examples) = parseDefinition(sense.synset.gloss)
                    // A verb whose gloss carries no example falls back to WordNet's generic sentence
                    // frames, exactly as Onym's find_example does.
                    val resolved =
                        if (examples.isEmpty() && pos == WnPos.VERB) {
                            source.exampleSentences(sense.pos, sense.synset.offset, sense.whichWord)
                        } else {
                            examples
                        }
                    OnymDefinition(posName(pos), gloss, resolved)
                }
            // The first-sense tag count, keyed on the FIRST synset word matching the lemma — Onym's
            // GetTagcnt builds its sense key from WNSnsToStr, which takes the first match, not the last
            // one whichWord resolves to (a synset may list both "Moon" and "moon", with only the
            // capitalised first one carrying the tag count).
            val first = group.first()
            val target = displayLower(first.lemma)
            val tagCount = first.synset.words.firstOrNull { displayLower(it.lemma) == target }?.useCount ?: 0
            items.add(DefinitionItem(lemma, displayLemma, pos, definitions, tagCount, group.size))
        }

        // Onym's pos_list_compare: when the lemmas differ, the one that matches the search string
        // exactly (case sensitively) wins; otherwise the higher first-sense tag count, then polysemy.
        items.sortWith(
            Comparator { a, b ->
                if (a.displayLemma != b.displayLemma) {
                    if (normalized == a.displayLemma) return@Comparator -1
                    if (normalized == b.displayLemma) return@Comparator 1
                }
                if (a.tagCount != b.tagCount) return@Comparator b.tagCount - a.tagCount
                b.polysemy - a.polysemy
            },
        )
        return items
    }

    /**
     * Resolve the lemma's case the way Onym's populate_synonyms does. While listing the prime sense's
     * words it re-points the lemma at each synset word that matches it case-insensitively, so the LAST
     * such word wins: "wordsworth" becomes "Wordsworth", but a synset listing both "Moon" and "moon"
     * settles on "moon", which is why the lower-cased query then sorts as an exact match. A spelling
     * already claimed as another item's lemma is skipped (is_synm_a_lemma), so a demonym adjective
     * stays lower-case once its proper-noun twin has taken the capital.
     */
    private fun resolveDisplayLemma(
        lemma: String,
        sense1Synset: WnSynset,
        claimed: Set<String>,
    ): String {
        var current = toDisplayForm(lemma)
        val target = displayLower(lemma)
        for (word in sense1Synset.words) {
            val wordDisplay = toDisplayForm(word.lemma)
            if (wordDisplay in claimed) continue
            if (displayLower(wordDisplay) == target) current = wordDisplay
        }
        return current
    }

    private fun MutableList<OnymSection>.addDefinitions(items: List<DefinitionItem>) {
        val definitions = items.flatMap { it.definitions }
        if (definitions.isNotEmpty()) add(OnymSection.Definitions("Definitions", definitions))
    }

    // --- Synonyms -----------------------------------------------------------------------------------

    private fun buildSynonyms(
        senses: List<Sense>,
        lemmas: Set<String>,
    ): List<OnymWord> {
        val seen = LinkedHashSet<String>()
        val result = ArrayList<OnymWord>()
        for (sense in senses) {
            for (word in sense.synset.words) {
                // A synset word is its own lemma's synonym only when it is not itself a searched form:
                // Onym suppresses every getindex variant and morphology base form (ash bin, ash-bin,
                // ashbin) as a synonym of one another, so only genuinely distinct words remain.
                val lower = displayLower(word.lemma)
                if (lower in lemmas) continue
                // Synonyms are de-duplicated case-insensitively, first spelling kept, as Onym's
                // check_term_in_list does — so "wye" lists "Y" once, not both "Y" and "y".
                if (seen.add(lower)) result.add(OnymWord(toDisplayForm(word.lemma)))
            }
        }
        return result
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
        ignoreSourceWord: Boolean = false,
    ): List<OnymWord> {
        // Terms are de-duplicated case-insensitively with the first spelling kept, as Onym's
        // check_term_in_list does — so a derived form listed as "Catholicity" is not repeated as
        // "catholicity".
        val seen = LinkedHashSet<String>()
        val result = ArrayList<OnymWord>()
        for (sense in senses) {
            if (adjectivesOnly && sense.pos != WnPos.ADJECTIVE) continue
            val selfLower = displayLower(sense.lemma)
            for (pointer in sense.synset.pointers) {
                if (pointer.relation !in relations) continue
                if (!ignoreSourceWord && !sourceApplies(pointer, sense.whichWord)) continue
                val target = source.synsetAt(pointer.targetPos, pointer.targetOffset) ?: continue
                for (word in target.words) {
                    val lower = displayLower(word.lemma)
                    if (lower == selfLower) continue
                    if (seen.add(lower)) result.add(OnymWord(toDisplayForm(word.lemma)))
                }
            }
        }
        return result
    }

    /**
     * Domains: the in-domain pointers (category / usage / region) and the domain-member pointers.
     *
     * These behave unusually, mirroring WordNet's index files and Onym's populate. The section appears
     * for a part of speech only if the searched word is itself the source of a domain pointer there
     * (the `;` / `-` symbols its index line would carry — is_defined's CLASSIFICATION / CLASS bits).
     * But once it appears, populate follows every domain pointer of those senses regardless of which
     * word it springs from. So "chequing account" (whose own word has a region link) lists all of its
     * synset's UK, Canadian and US domains, whereas "nadolol" (whose only domain link springs from its
     * synonym "Corgard") shows no domains at all.
     */
    private fun buildDomains(senses: List<Sense>): List<OnymWord> {
        val gatedPositions =
            senses
                .filter { sense ->
                    sense.synset.pointers.any { it.relation in DOMAIN_RELATIONS && sourceApplies(it, sense.whichWord) }
                }.map { it.pos }
                .toSet()
        val gated = senses.filter { it.pos in gatedPositions }
        return buildFlat(gated, DOMAIN_RELATIONS, ignoreSourceWord = true)
    }

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

    /** A tree node under construction: its terms and a growing list of child nodes. */
    private class TreeBuilder(
        val terms: List<String>,
        val children: ArrayList<TreeBuilder> = ArrayList(),
    ) {
        fun build(): OnymTreeNode = OnymTreeNode(terms, children.map { it.build() })
    }

    private fun growNodes(
        synset: WnSynset,
        whichWord: Int,
        group: Set<WnRelation>,
        selfLemmaLower: String,
        depth: Int,
        maxDepth: Int,
    ): List<OnymTreeNode> {
        val nodes = ArrayList<TreeBuilder>()
        growInto(nodes, synset, whichWord, group, selfLemmaLower, depth, maxDepth)
        return nodes.map { it.build() }
    }

    /**
     * Grow [group] relation nodes under [parent], following WordNet/Onym's grow_tree with one
     * deliberate departure, fix 2 of the onym-engine spec: grow_tree never reset its current node
     * between pointers, so when a pointer's target carried only the searched word itself (no new
     * term, no node created), the target's children were appended to the previous sibling instead.
     * That is why door's "casing, case" used to gain a phantom "lock" child and sing's "choir,
     * chorus" the bare-"sing" synset's hyponyms. A target that contributes no node now contributes
     * no children either; siblings only ever carry their own.
     */
    private fun growInto(
        parent: ArrayList<TreeBuilder>,
        synset: WnSynset,
        whichWord: Int,
        group: Set<WnRelation>,
        selfLemmaLower: String,
        depth: Int,
        maxDepth: Int,
    ) {
        for (pointer in synset.pointers) {
            if (pointer.relation !in group) continue
            if (!sourceApplies(pointer, whichWord)) continue
            val target = source.synsetAt(pointer.targetPos, pointer.targetOffset) ?: continue
            val terms = target.words.map { toDisplayForm(it.lemma) }.filter { displayLower(it) != selfLemmaLower }
            if (terms.isEmpty()) continue
            val node = TreeBuilder(terms)
            parent.add(node)
            if (depth + 1 < maxDepth) {
                growInto(node.children, target, 0, group, selfLemmaLower, depth + 1, maxDepth)
            }
        }
    }

    /**
     * Pertainyms, with one level of hypernyms shown beneath the first only. grow_tree zeroes its depth
     * the first time it descends into a pertainym's hypernyms, so every later pertainym of the same
     * sense is left bare — which is why "hasidic" shows "Orthodox Judaism" under "Hasidism" but nothing
     * under "Hasidim". The oracle does this, so the engine must.
     */
    private fun buildPertainyms(senses: List<Sense>): List<OnymTreeNode> {
        val seen = HashSet<String>()
        val nodes = ArrayList<OnymTreeNode>()
        for (sense in senses) {
            var grown = false
            for (pointer in sense.synset.pointers) {
                if (pointer.relation != WnRelation.PERTAINYM) continue
                if (!sourceApplies(pointer, sense.whichWord)) continue
                val target = source.synsetAt(pointer.targetPos, pointer.targetOffset) ?: continue
                val selfLower = displayLower(sense.lemma)
                val terms = target.words.map { toDisplayForm(it.lemma) }.filter { displayLower(it) != selfLower }
                if (terms.isEmpty()) continue
                val children =
                    if (!grown) growNodes(target, 0, HYPERNYM_GROUPS.first(), selfLower, 0, maxDepth = 1) else emptyList()
                grown = true
                val node = OnymTreeNode(terms, children)
                if (seen.add(node.label)) nodes.add(node)
            }
        }
        return nodes
    }

    /** Onym's is_defined depth bits for a noun lemma. */
    private class NounDepth(
        val meronym: Boolean,
        val holonym: Boolean,
    )

    /**
     * Compute Onym's is_defined depth bits per noun lemma. is_defined(lemma, NOUN) sets bit(HMERONYM)
     * / bit(HHOLONYM) when any of the lemma's noun senses has an immediate hypernym that itself carries
     * a meronym / holonym pointer (WordNet's HasHoloMero).
     *
     * Two faithful details matter. is_defined resolves the lemma through getindex's variants and ORs
     * the bits across all of them, so a word's depth can be raised by a same-spelt homograph: the plant
     * "pica-pica" grows its part-of tree deep only because the magpie "pica_pica" (a getindex variant)
     * inherits a holonym. And is_defined is given the space-separated lemma, whose getindex variants
     * never recover an underscored multiword index key, so a multiword term never resolves and stays
     * flat.
     */
    private fun computeNounDepth(senses: List<Sense>): Map<String, NounDepth> {
        val result = HashMap<String, NounDepth>()
        for (lemma in senses.filter { it.pos == WnPos.NOUN }.map { it.lemma }.toSet()) {
            var meronym = false
            var holonym = false
            // is_defined sees the space form, whose variants are looked up by exact key; WordNet index
            // keys never contain spaces, so a still-spaced multiword variant cannot match (it is only
            // extJWNL that would resolve it), which is what keeps multiword nouns' trees flat.
            for (variant in indexVariants(toDisplayForm(lemma)).filter { ' ' !in it }) {
                for (synset in source.sensesOf(variant, WnPos.NOUN)) {
                    for (pointer in synset.pointers) {
                        if (pointer.relation != WnRelation.HYPERNYM) continue
                        val hypernym = source.synsetAt(pointer.targetPos, pointer.targetOffset) ?: continue
                        if (!meronym && hypernym.pointers.any { it.relation in MERONYM_RELATIONS }) meronym = true
                        if (!holonym && hypernym.pointers.any { it.relation in HOLONYM_RELATIONS }) holonym = true
                    }
                }
            }
            result[lemma] = NounDepth(meronym, holonym)
        }
        return result
    }

    /**
     * Part of (holonyms): the three subtypes combined in order. Grown to full depth only when the
     * lemma's HHOLONYM bit is set (a noun sense's immediate hypernym is itself part/member/substance
     * of something); otherwise just the word's own holonyms, one level deep.
     */
    private fun buildHolonyms(
        senses: List<Sense>,
        nounDepth: Map<String, NounDepth>,
    ): List<OnymTreeNode> {
        val seen = HashSet<String>()
        val nodes = ArrayList<OnymTreeNode>()
        for (sense in senses) {
            val maxDepth = if (nounDepth[sense.lemma]?.holonym == true) MAX_TREE_DEPTH else 1
            val selfLower = displayLower(sense.lemma)
            for (group in HOLONYM_GROUPS) {
                for (node in growNodes(sense.synset, sense.whichWord, group, selfLower, 0, maxDepth)) {
                    if (seen.add(node.label)) nodes.add(node)
                }
            }
        }
        return nodes
    }

    /**
     * Parts (meronyms): the three subtypes combined in order. Grown to full depth, with each
     * ancestor's inherited meronyms traced, only when the lemma's HMERONYM bit is set; otherwise just
     * the word's own meronyms, one level deep.
     */
    private fun buildMeronyms(
        senses: List<Sense>,
        nounDepth: Map<String, NounDepth>,
    ): List<OnymTreeNode> {
        val seen = HashSet<String>()
        val nodes = ArrayList<OnymTreeNode>()
        for (sense in senses) {
            val deep = nounDepth[sense.lemma]?.meronym == true
            val maxDepth = if (deep) MAX_TREE_DEPTH else 1
            val selfLower = displayLower(sense.lemma)
            val topLevel = ArrayList<OnymTreeNode>()
            for (group in MERONYM_GROUPS) {
                topLevel.addAll(growNodes(sense.synset, sense.whichWord, group, selfLower, 0, maxDepth))
            }
            if (deep) {
                topLevel.addAll(traceInherit(sense.synset, sense.whichWord, selfLower, 1))
            }
            for (node in topLevel) {
                if (seen.add(node.label)) nodes.add(node)
            }
        }
        return nodes
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
        private val HOLONYM_RELATIONS =
            setOf(WnRelation.MEMBER_HOLONYM, WnRelation.SUBSTANCE_HOLONYM, WnRelation.PART_HOLONYM)
        private val DOMAIN_RELATIONS =
            setOf(
                WnRelation.CATEGORY,
                WnRelation.USAGE,
                WnRelation.REGION,
                WnRelation.CATEGORY_MEMBER,
                WnRelation.USAGE_MEMBER,
                WnRelation.REGION_MEMBER,
            )
    }
}

/**
 * WordNet's getindex search forms for [form]: the form itself, with underscores turned to hyphens,
 * with hyphens turned to underscores, with spaces and hyphens removed, and with periods removed —
 * de-duplicated, in that order. This is how a hyphenated query also finds its joined spelling
 * (cut-in -> cutin), how variant spellings of a headword are recognised as the same word, and how
 * morphology's is_defined test accepts a base form spelled differently (horse_race -> horse-race).
 */
internal fun indexVariants(form: String): List<String> {
    val variants = LinkedHashSet<String>()
    variants.add(form)
    variants.add(form.replace('_', '-'))
    variants.add(form.replace('-', '_'))
    variants.add(form.filterNot { it == '_' || it == '-' })
    variants.add(form.filterNot { it == '.' })
    return variants.toList()
}

/** ASCII-only lower-casing, matching WordNet's strtolower (locale-independent). */
internal fun asciiLower(value: String): String =
    buildString(value.length) {
        for (c in value) append(if (c in 'A'..'Z') c + 32 else c)
    }

/** The lowercased display form: underscores to spaces and ASCII upper-case to lower-case. */
internal fun displayLower(value: String): String = asciiLower(toDisplayForm(value))
