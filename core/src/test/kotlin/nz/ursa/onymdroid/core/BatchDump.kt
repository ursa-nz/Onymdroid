// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.core

import java.io.File

/*
 * Streams dumps for a word list through one engine, the JNI side of the onym-engine total
 * cross-diff: every WordNet headword is dumped through the loaded native engine and by
 * `onym-dump --batch` reading the same list on stdin, and the two outputs must be byte-identical,
 * which proves the JNI boundary and the codec, not just the core underneath. Each dump is
 * preceded by a `==> word <==` marker line so a diff names the word it belongs to; no dump line
 * ever starts with `=`, so the marker is unambiguous.
 *
 * Run with: ./gradlew :core:batchDump --args="WORDLIST OUTFILE"
 */
fun main(args: Array<String>) {
    require(args.size == 2) { "usage: BatchDump WORDLIST OUTFILE" }
    val words = File(args[0])
    require(words.isFile) { "no word list at $words" }
    require(TestWordNet.available) { "WordNet data not installed" }

    val engine = OnymEngine.open(TestWordNet.directory)
    var dumps = 0
    File(args[1]).outputStream().buffered().use { out ->
        // The dictionary is ISO-8859-1 and the cross-diff compares bytes, so the word list and
        // the output both use that encoding, never the platform default.
        words.bufferedReader(Charsets.ISO_8859_1).useLines { lines ->
            for (line in lines) {
                if (line.isEmpty()) continue
                out.write("==> $line <==\n".toByteArray(Charsets.ISO_8859_1))
                out.write(engine.dump(line).toByteArray(Charsets.ISO_8859_1))
                dumps++
                if (dumps % 10_000 == 0) System.err.println("$dumps words dumped")
            }
        }
    }
    println("batch dump: $dumps words")
}
