// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.data

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import nz.ursa.onymdroid.core.OnymEngine
import nz.ursa.onymdroid.core.OnymResult

/**
 * The app's seam onto the [OnymEngine]. It unpacks the bundled WordNet database and opens the engine
 * lazily on a background dispatcher — opening is heavy, so it happens once and every later call waits
 * on the same instance — and exposes the lookups the UI needs. Nothing here touches the main thread:
 * each method hops to [io] (engine open and reads do blocking file work) before calling the engine.
 */
class OnymRepository(
    private val appContext: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val openMutex = Mutex()

    @Volatile
    private var engine: OnymEngine? = null

    private suspend fun engine(): OnymEngine {
        engine?.let { return it }
        return openMutex.withLock {
            engine ?: withContext(io) {
                val dataDir = WordNetAssets.ensureUnpacked(appContext)
                OnymEngine.open(dataDir).also { engine = it }
            }
        }
    }

    /** Resolve [word] to its full entry, or null when it is not a WordNet headword. */
    suspend fun lookup(word: String): OnymResult? = withContext(io) { engine().lookup(word) }

    /** Headwords beginning with [prefix], capped at [max], for live completion. */
    suspend fun complete(
        prefix: String,
        max: Int,
    ): List<String> = withContext(io) { engine().complete(prefix, max) }

    /** Spelling suggestions for a missed [word], capped at [max], for the "did you mean" prompt. */
    suspend fun suggest(
        word: String,
        max: Int,
    ): List<String> = withContext(io) { engine().suggest(word, max) }

    /** A random headword, for the dice action. */
    suspend fun randomWord(): String = withContext(io) { engine().randomWord() }

    /**
     * The subset of [terms] that are themselves WordNet headwords. The UI uses this to mark a term
     * navigable (an arrow, tappable) or not (greyed). Membership is tested with the lemma index via
     * [OnymEngine.complete] — a binary search per term — rather than a full [OnymEngine.lookup], which
     * would build a whole result for each of the (often hundreds of) terms on a screen. A term is a
     * headword when the index holds an exact match for it; completion returns headwords in
     * display-lower form (underscores to spaces, lower-case), so the term is normalised the same way
     * before comparison. The returned set keeps the original term strings so callers can test the
     * labels they render directly. The work stays off the main thread on [io].
     */
    suspend fun navigableTerms(terms: Collection<String>): Set<String> = withContext(io) {
        val engine = engine()
        terms.distinct().filterTo(LinkedHashSet()) { term ->
            val normalised = term.replace('_', ' ').lowercase()
            normalised.isNotEmpty() && engine.complete(normalised, 1).firstOrNull() == normalised
        }
    }
}
