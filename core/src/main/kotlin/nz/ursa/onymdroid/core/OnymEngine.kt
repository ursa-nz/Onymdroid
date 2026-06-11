// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.core

import java.io.File
import java.io.IOException
import kotlin.random.Random

/**
 * The engine's public face. It resolves a word to the [OnymResult] model, completes a typed prefix,
 * and suggests near-misses for a missed word. It is the only type the app needs: behind it sits the
 * shared onym-engine Rust core, reached through [NativeEngine], one JNI call and one decoded buffer
 * per operation.
 */
class OnymEngine private constructor(
    private val handle: Long,
) {
    /**
     * Look [word] up. Returns null when the word is simply not in WordNet (distinct from a missing
     * database, which surfaces as an exception when the engine is opened).
     */
    fun lookup(word: String): OnymResult? = NativeEngine.lookup(handle, word)?.let { OnymDecoder(it).entry() }

    /** Headwords beginning with [prefix], capped at [max] (0 means no cap). */
    fun complete(
        prefix: String,
        max: Int,
    ): List<String> = OnymDecoder(NativeEngine.complete(handle, prefix, max)).strings()

    /** Spelling suggestions for a missed [word], capped at [max] (0 means no cap). */
    fun suggest(
        word: String,
        max: Int,
    ): List<String> = OnymDecoder(NativeEngine.suggest(handle, word, max)).strings()

    /** A random WordNet headword, for the dice "surprise me" action. */
    fun randomWord(): String {
        val count = NativeEngine.lemmaCount(handle).toInt()
        if (count == 0) return ""
        val lemma = NativeEngine.lemmaAt(handle, Random.Default.nextInt(count).toLong())
        return if (lemma == null) "" else String(lemma, Charsets.UTF_8)
    }

    /**
     * Render [word]'s entry in the onym-cli `--dump` text format. This exists so the engine can be
     * diffed, byte for byte, against the golden oracle; the app renders the model directly instead.
     */
    fun dump(word: String): String = String(NativeEngine.dump(handle, word), Charsets.UTF_8)

    /**
     * Free the native engine. The app never calls this (one engine lives for the process); tests
     * that open engines do, to keep native memory bounded.
     */
    fun close() = NativeEngine.close(handle)

    companion object {
        /**
         * Open an engine over the WordNet database in [dataDir]. The directory is read in place,
         * read-only; no copy and no writable requirement. A missing or unreadable database
         * surfaces as an [IOException] naming the file that failed.
         */
        fun open(dataDir: File): OnymEngine = OnymEngine(OnymDecoder(NativeEngine.open(dataDir.absolutePath)).openHandle())
    }
}
