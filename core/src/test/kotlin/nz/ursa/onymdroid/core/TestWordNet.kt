// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.core

import net.sf.extjwnl.dictionary.Dictionary
import java.io.File
import java.nio.file.Files

/**
 * Opens the system WordNet database for tests. extJWNL opens the database read-write, so the data is
 * copied to a writable temp directory (as the app does on the device) and given the empty cntlist
 * extJWNL expects. Use [available] to skip tests when no database is installed; override the location
 * with `-Donym.wordnet.dir=/path`.
 */
internal object TestWordNet {
    private val dataDir: String = System.getProperty("onym.wordnet.dir", "/usr/share/wordnet")

    /** The read-only system database directory, for components that only read it (like the lemma index). */
    val directory: File get() = File(dataDir)

    val available: Boolean get() = File(directory, "index.noun").exists()

    /** A fresh writable copy of the database, with the empty cntlist extJWNL expects. */
    fun preparedDir(): File {
        val work = Files.createTempDirectory("onym-wordnet").toFile()
        directory.listFiles()?.forEach { file ->
            if (file.isFile) file.copyTo(File(work, file.name), overwrite = true)
        }
        File(work, "cntlist").createNewFile()
        return work
    }

    fun openDictionary(): Dictionary = Dictionary.getFileBackedInstance(preparedDir().absolutePath)

    fun openSource(): WordNetSource {
        val dir = preparedDir()
        return ExtjwnlWordNetSource(Dictionary.getFileBackedInstance(dir.absolutePath), dir)
    }
}
