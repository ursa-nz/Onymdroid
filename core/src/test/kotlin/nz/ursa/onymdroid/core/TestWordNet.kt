// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.core

import java.io.File

/**
 * Locates the WordNet base for tests. The build prepares it from the onym-data submodule and passes
 * the path as `-Donym.wordnet.dir`; the native engine reads the directory in place, read-only, so no
 * copy is made. Use [available] to skip tests when the property is unset, which is the case on a bare
 * checkout where the submodule is not present.
 */
internal object TestWordNet {
    private val dataDir: String? = System.getProperty("onym.wordnet.dir")

    val directory: File get() = File(requireNotNull(dataDir) { "onym.wordnet.dir is not set" })

    val available: Boolean get() = dataDir != null && File(dataDir, "index.noun").exists()
}
