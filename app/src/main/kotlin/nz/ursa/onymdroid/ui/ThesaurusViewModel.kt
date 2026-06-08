// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nz.ursa.onymdroid.core.OnymResult
import nz.ursa.onymdroid.core.OnymSection
import nz.ursa.onymdroid.core.OnymTreeNode
import nz.ursa.onymdroid.data.OnymRepository
import nz.ursa.onymdroid.data.Settings
import nz.ursa.onymdroid.data.SettingsRepository

/** A looked-up entry paired with the terms in it that are themselves navigable headwords. */
data class RenderedWord(
    val result: OnymResult,
    val navigable: Set<String>,
)

/** What the detail pane should show for the current word. */
sealed interface DetailState {
    /** No word looked up yet (cold start before the first navigation resolves). */
    data object Empty : DetailState

    /** A lookup is in flight for [term]. */
    data class Loading(
        val term: String,
    ) : DetailState

    /** [word] resolved and is ready to render. */
    data class Loaded(
        val word: RenderedWord,
    ) : DetailState

    /** [term] is not in WordNet; [suggestions] are the "did you mean" near-misses. */
    data class Missing(
        val term: String,
        val suggestions: List<String>,
    ) : DetailState
}

/** The back/forward navigation history across looked-up words. */
private data class History(
    val terms: List<String> = emptyList(),
    val index: Int = -1,
) {
    val current: String? get() = terms.getOrNull(index)
    val canBack: Boolean get() = index > 0
    val canForward: Boolean get() = index in 0 until terms.lastIndex

    fun push(term: String): History {
        if (current == term) return this
        val kept = terms.subList(0, index + 1)
        return History(kept + term, kept.size)
    }

    fun back(): History = if (canBack) copy(index = index - 1) else this

    fun forward(): History = if (canForward) copy(index = index + 1) else this
}

/**
 * Owns search and navigation state for the thesaurus and serves entries from the [OnymRepository].
 * Search is debounced: as the user types, [completions] tracks headword prefixes; on submit or when
 * a synonym is tapped, the word is looked up — falling back to spelling suggestions when it is
 * missing. Every lookup and completion runs on a background dispatcher inside the repository, so the
 * main thread is never blocked. History drives the top-bar back/forward arrows; the current word and
 * recent searches are persisted so a relaunch lands on the same place.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class ThesaurusViewModel(
    application: Application,
    private val repository: OnymRepository,
    private val settingsRepository: SettingsRepository,
) : AndroidViewModel(application) {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val historyState = MutableStateFlow(History())
    val canBack: StateFlow<Boolean> =
        historyState
            .map { it.canBack }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val canForward: StateFlow<Boolean> =
        historyState
            .map { it.canForward }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val currentTerm: StateFlow<String?> =
        historyState
            .map { it.current }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _detail = MutableStateFlow<DetailState>(DetailState.Empty)
    val detail: StateFlow<DetailState> = _detail.asStateFlow()

    val settings: StateFlow<Settings> =
        settingsRepository.settings
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), Settings())

    /** Live completions for the current query, debounced so each keystroke does not hit the index. */
    val completions: StateFlow<List<String>> =
        _query
            .debounce(DEBOUNCE_MS)
            .map { it.trim() }
            .distinctUntilChanged()
            .mapLatest { prefix ->
                if (prefix.isEmpty()) emptyList() else repository.complete(prefix, COMPLETION_LIMIT)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Recent searches, newest first, surfaced when the search field is empty. */
    val recentSearches: StateFlow<List<String>> =
        settingsRepository.settings
            .map { it.recentSearches }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private var lookupJob: Job? = null

    init {
        // On a cold start, restore the most recent search so the user lands back where they were;
        // on a first-ever launch with no history, open on a random word so the screen is never blank.
        viewModelScope.launch {
            val resumed =
                settingsRepository.settings
                    .first()
                    .recentSearches
                    .firstOrNull()
            if (resumed != null) navigate(resumed, record = false) else navigate(repository.randomWord())
        }
    }

    fun onQueryChange(text: String) {
        _query.value = text
    }

    fun clearQuery() {
        _query.value = ""
    }

    /** Look [term] up and make it the current word, recording it in history (and recents). */
    fun navigate(
        term: String,
        record: Boolean = true,
    ) {
        val normalised = term.trim()
        if (normalised.isEmpty()) return
        // Picking a word ends the search session: clear the field so reopening search starts empty.
        _query.value = ""
        lookupJob?.cancel()
        _detail.value = DetailState.Loading(normalised)
        lookupJob =
            viewModelScope.launch {
                val result = repository.lookup(normalised)
                if (result != null) {
                    val navigable = repository.navigableTerms(termsToCheck(result))
                    historyState.update { it.push(result.term) }
                    _detail.value = DetailState.Loaded(RenderedWord(result, navigable))
                    if (record) settingsRepository.addRecentSearch(result.term)
                } else {
                    val suggestions = repository.suggest(normalised, SUGGESTION_LIMIT)
                    _detail.value = DetailState.Missing(normalised, suggestions)
                }
            }
    }

    fun back() {
        historyState.update { it.back() }
        reloadCurrent()
    }

    fun forward() {
        historyState.update { it.forward() }
        reloadCurrent()
    }

    /** Jump to a random headword via the dice action. */
    fun shuffle() {
        viewModelScope.launch { navigate(repository.randomWord()) }
    }

    fun setTreeExpanded(expanded: Boolean) {
        viewModelScope.launch { settingsRepository.setTreeExpanded(expanded) }
    }

    private fun reloadCurrent() {
        val term = historyState.value.current ?: return
        lookupJob?.cancel()
        _detail.value = DetailState.Loading(term)
        lookupJob =
            viewModelScope.launch {
                val result = repository.lookup(term)
                _detail.value =
                    if (result != null) {
                        DetailState.Loaded(
                            RenderedWord(result, repository.navigableTerms(termsToCheck(result))),
                        )
                    } else {
                        DetailState.Missing(term, repository.suggest(term, SUGGESTION_LIMIT))
                    }
            }
    }

    private companion object {
        const val DEBOUNCE_MS = 180L
        const val COMPLETION_LIMIT = 40
        const val SUGGESTION_LIMIT = 8
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Every term that appears as a tappable chip or tree node, gathered for the navigability check. */
private fun termsToCheck(result: OnymResult): List<String> = buildList {
    for (section in result.sections) {
        when (section) {
            is OnymSection.Words -> {
                section.items.forEach { add(it.term) }
            }

            is OnymSection.Antonyms -> {
                section.items.forEach { antonym ->
                    add(antonym.term)
                    antonym.implications.forEach { add(it.term) }
                }
            }

            is OnymSection.Tree -> {
                collectTreeTerms(section.items, this)
            }

            // Definitions carry no navigable terms.
            is OnymSection.Definitions -> {}
        }
    }
}

private fun collectTreeTerms(
    nodes: List<OnymTreeNode>,
    out: MutableList<String>,
) {
    for (node in nodes) {
        node.terms.forEach { out.add(it) }
        collectTreeTerms(node.children, out)
    }
}
