// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.core

import java.io.File

/**
 * Locates the system WordNet database for tests. The native engine reads the directory in place,
 * read-only, so no copy is made. Use [available] to skip tests when no database is installed;
 * override the location with `-Donym.wordnet.dir=/path`.
 */
internal object TestWordNet {
    private val dataDir: String = System.getProperty("onym.wordnet.dir", "/usr/share/wordnet")

    val directory: File get() = File(dataDir)

    val available: Boolean get() = File(directory, "index.noun").exists()
}
