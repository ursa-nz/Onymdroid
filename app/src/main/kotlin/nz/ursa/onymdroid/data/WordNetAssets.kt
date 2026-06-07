// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.data

import android.content.Context
import java.io.File

/**
 * Unpacks the bundled WordNet database from assets into the app's files directory.
 *
 * extJWNL opens the database read-write, but assets are read-only, so the data has to be copied
 * to writable storage before it can be opened. A version marker means the copy happens once, and
 * happens again only when the bundled data changes (bump [VERSION] then).
 */
object WordNetAssets {
    private const val ASSET_DIR = "wordnet"
    private const val TARGET_DIR = "wordnet"

    /** The bundled-data revision; bump when the assets under `wordnet/` change. */
    private const val VERSION = 1

    /**
     * Ensure the database is present on writable storage and return its directory.
     *
     * @param context any context; its files directory is used as the destination.
     * @return the directory holding the unpacked WordNet files, ready for the reader.
     */
    fun ensureUnpacked(context: Context): File {
        val target = File(context.filesDir, TARGET_DIR)
        val marker = File(target, ".unpacked.v$VERSION")
        if (marker.exists()) return target

        target.deleteRecursively()
        target.mkdirs()

        val assets = context.assets
        for (name in assets.list(ASSET_DIR).orEmpty()) {
            assets.open("$ASSET_DIR/$name").use { input ->
                File(target, name).outputStream().use { output -> input.copyTo(output) }
            }
        }

        // extJWNL expects a plain cntlist file; the Debian database ships only cntlist.rev, so
        // provide an empty one rather than leave the reader unable to open the database.
        File(target, "cntlist").createNewFile()
        marker.createNewFile()
        return target
    }
}
