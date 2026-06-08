// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The persisted preferences the UI reads as one immutable snapshot. */
data class Settings(
    val treeExpandedByDefault: Boolean = false,
    val recentSearches: List<String> = emptyList(),
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "onym_settings")

/**
 * Reads and writes the app's two persisted things: the default tree-expansion preference and the
 * recent-search list. Everything is backed by Preferences DataStore, so reads are a [Flow] that
 * updates live and writes are suspending and atomic.
 */
class SettingsRepository(
    private val appContext: Context,
) {
    private val store = appContext.dataStore

    val settings: Flow<Settings> =
        store.data.map { prefs ->
            Settings(
                treeExpandedByDefault = prefs[TREE_EXPANDED] ?: false,
                recentSearches =
                    prefs[RECENT_SEARCHES]
                        ?.split(RECENT_DELIMITER)
                        ?.filter { it.isNotEmpty() }
                        .orEmpty(),
            )
        }

    /** Persist whether relation trees open expanded by default. */
    suspend fun setTreeExpanded(expanded: Boolean) {
        store.edit { it[TREE_EXPANDED] = expanded }
    }

    /** Record [term] as the most recent search, de-duplicated and capped at [RECENT_LIMIT]. */
    suspend fun addRecentSearch(term: String) {
        if (term.isEmpty()) return
        store.edit { prefs ->
            val current =
                prefs[RECENT_SEARCHES]
                    ?.split(RECENT_DELIMITER)
                    ?.filter { it.isNotEmpty() }
                    .orEmpty()
            val updated = (listOf(term) + current.filter { it != term }).take(RECENT_LIMIT)
            prefs[RECENT_SEARCHES] = updated.joinToString(RECENT_DELIMITER)
        }
    }

    private companion object {
        val TREE_EXPANDED = booleanPreferencesKey("tree_expanded")
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")

        // Newlines cannot occur in a single-line headword, so they safely separate the list.
        const val RECENT_DELIMITER = "\n"
        const val RECENT_LIMIT = 12
    }
}
