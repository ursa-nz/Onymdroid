// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.core

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The decode side of the [NativeEngine] wire format, rebuilding the model classes from one
 * buffer. The format is little-endian and length-prefixed: a u32 is the only length and count
 * type, a string is a u32 byte length and UTF-8 bytes, and a string list is a u32 count of
 * strings. Both ends live in one repository each and change together, so a malformed buffer is
 * a build error somewhere, never input to tolerate; the decoder fails loudly instead of
 * guessing.
 */
internal class OnymDecoder(bytes: ByteArray) {
    private val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    /** Decode an `open` answer: the handle on success, or the carried error as [IOException]. */
    fun openHandle(): Long {
        val ok = u8() == 1
        if (!ok) throw IOException(string())
        val handle = buffer.long
        finish()
        return handle
    }

    /** Decode a whole entry. */
    fun entry(): OnymResult {
        val term = string()
        val sections = List(count()) { section() }
        finish()
        return OnymResult(term, sections)
    }

    /** Decode a completion or suggestion answer. */
    fun strings(): List<String> {
        val items = stringList()
        finish()
        return items
    }

    private fun section(): OnymSection {
        val title = string()
        return when (val kind = u8()) {
            0 -> OnymSection.Definitions(title, List(count()) { definition() })
            1 -> OnymSection.Words(title, stringList().map(::OnymWord))
            2 -> OnymSection.Antonyms(title, List(count()) { antonym() })
            3 -> OnymSection.Tree(title, treeNodes())
            else -> error("unknown section kind $kind")
        }
    }

    private fun definition(): OnymDefinition {
        val pos = if (u8() == 1) string() else null
        return OnymDefinition(pos, string(), stringList())
    }

    // Kotlin evaluates arguments left to right, which is exactly the wire order: term, direct
    // byte, implications.
    private fun antonym(): OnymAntonym = OnymAntonym(string(), u8() == 1, stringList().map(::OnymWord))

    // A node is its terms then its children, recursively, depth-first.
    private fun treeNodes(): List<OnymTreeNode> = List(count()) { OnymTreeNode(stringList(), treeNodes()) }

    private fun stringList(): List<String> = List(count()) { string() }

    private fun string(): String {
        val bytes = ByteArray(count())
        buffer.get(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun count(): Int {
        val value = buffer.int
        check(value >= 0) { "count out of range: $value" }
        return value
    }

    private fun u8(): Int = buffer.get().toInt() and 0xff

    private fun finish() = check(!buffer.hasRemaining()) { "${buffer.remaining()} bytes left undecoded" }
}
