// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.core

/*
 * Pure string helpers shared by the engine and the lemma index: the query and display form
 * conversions and the bounded edit distance used for spelling suggestions. They depend on nothing,
 * so the unit tests exercise them without any WordNet data, mirroring Onym's wn-index.c helpers.
 */

/** The query form: trimmed, with spaces turned to underscores. `"  ice cream "` becomes `"ice_cream"`. */
fun toQueryForm(input: String): String = input.trim().replace(' ', '_')

/** The display form: underscores turned to spaces. `"ice_cream"` becomes `"ice cream"`. */
fun toDisplayForm(raw: String): String = raw.replace('_', ' ')

/**
 * The Levenshtein edit distance between [a] and [b], with unit insert, delete, and substitute
 * costs. A two-row dynamic program, matching Onym's `onym_edit_distance`.
 */
fun editDistance(
    a: String,
    b: String,
): Int {
    val la = a.length
    val lb = b.length
    if (la == 0) return lb
    if (lb == 0) return la

    var prev = IntArray(lb + 1) { it }
    var cur = IntArray(lb + 1)
    for (i in 1..la) {
        cur[0] = i
        for (j in 1..lb) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
        }
        val swap = prev
        prev = cur
        cur = swap
    }
    return prev[lb]
}
