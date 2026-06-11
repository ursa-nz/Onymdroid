// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.core

/**
 * The native face of the engine: the onym-engine-jni library, loaded once per process. Every
 * function crosses the JNI boundary exactly once and answers with one encoded buffer; the wire
 * format is documented in onym-engine's `crates/onym-engine-jni/src/lib.rs`, and [OnymDecoder]
 * is its only reader. The two sides change together, so the format carries no version.
 */
internal object NativeEngine {
    init {
        // On Android the library ships in the APK's jniLibs; on the desktop the Gradle test and
        // tool tasks put onym-engine's target/release directory on java.library.path, so the
        // parity suite exercises the same native code the app ships.
        System.loadLibrary("onym_engine_jni")
    }

    /** Tag byte 1 and a handle on success, tag byte 0 and an error message on failure. */
    external fun open(path: String): ByteArray

    /** Free the engine. Zero is ignored. Callers serialise this against in-flight calls. */
    external fun close(handle: Long)

    /** An encoded entry, or null when the word is simply not in WordNet. */
    external fun lookup(
        handle: Long,
        word: String,
    ): ByteArray?

    /** An encoded string list of headwords beginning with [prefix]; a max of 0 means no cap. */
    external fun complete(
        handle: Long,
        prefix: String,
        max: Int,
    ): ByteArray

    /** An encoded string list of spelling suggestions; a max of 0 means no cap. */
    external fun suggest(
        handle: Long,
        word: String,
        max: Int,
    ): ByteArray

    /** The `--dump` text of the word's entry as raw UTF-8 bytes. */
    external fun dump(
        handle: Long,
        word: String,
    ): ByteArray

    /** How many headwords the lemma index holds. */
    external fun lemmaCount(handle: Long): Long

    /** The headword at [index] in sorted order as raw UTF-8 bytes, or null out of range. */
    external fun lemmaAt(
        handle: Long,
        index: Long,
    ): ByteArray?
}
