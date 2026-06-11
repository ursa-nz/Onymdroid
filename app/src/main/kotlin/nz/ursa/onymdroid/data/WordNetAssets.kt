// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.data

import android.content.Context
import java.io.File

/**
 * Unpacks the bundled WordNet database from assets into the app's files directory.
 *
 * The engine reads a plain directory, but APK assets are not one, so the data is copied out once
 * and never touched again: the engine opens it in place, read-only, and each file is marked
 * read-only so an accidental write fails loudly. A version marker means the copy happens once,
 * and happens again only when the bundled data changes (bump [VERSION] then).
 */
object WordNetAssets {
    private const val ASSET_DIR = "wordnet"
    private const val TARGET_DIR = "wordnet"

    /** The bundled-data revision; bump when the assets under `wordnet/` change. */
    private const val VERSION = 2

    /**
     * Ensure the database is present on local storage and return its directory.
     *
     * @param context any context; its files directory is used as the destination.
     * @return the directory holding the unpacked WordNet files, ready for the engine.
     */
    fun ensureUnpacked(context: Context): File {
        val target = File(context.filesDir, TARGET_DIR)
        val marker = File(target, ".unpacked.v$VERSION")
        if (marker.exists()) return target

        target.deleteRecursively()
        target.mkdirs()

        val assets = context.assets
        for (name in assets.list(ASSET_DIR).orEmpty()) {
            val file = File(target, name)
            assets.open("$ASSET_DIR/$name").use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            file.setReadOnly()
        }

        marker.createNewFile()
        return target
    }
}
