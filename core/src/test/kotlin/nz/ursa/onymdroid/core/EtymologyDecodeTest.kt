// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/*
 * A pure decoder guard for the etymology section kind (wire byte 4), needing neither the native
 * library nor a WordNet database: it hand-builds an entry buffer in the little-endian, length-
 * prefixed format the JNI codec writes and asserts the decoder rebuilds it as an
 * OnymSection.Etymology of prose paragraphs. The native round-trip is covered by the parity suite
 * once the engine .so is built; this pins the kind-4 mapping on its own.
 */
internal class EtymologyDecodeTest {
    private val out = ByteArrayOutputStream()

    private fun u32(value: Int) =
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())

    private fun u8(value: Int) = out.write(value)

    private fun string(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        u32(bytes.size)
        out.write(bytes)
    }

    private fun stringList(items: List<String>) {
        u32(items.size)
        items.forEach(::string)
    }

    @Test
    fun etymologySectionDecodesAsParagraphs() {
        val paragraphs = listOf(
            "The animal: from Old English bera.",
            "To carry: from Old English beran.",
        )
        // An entry: term, section count, then one section as title, kind byte 4, and a string list.
        string("bear")
        u32(1)
        string("Etymology")
        u8(4)
        stringList(paragraphs)

        val result = OnymDecoder(out.toByteArray()).entry()

        assertEquals("bear", result.term)
        assertEquals(OnymSection.Etymology("Etymology", paragraphs), result.sections.single())
    }

    @Test
    fun utf8EtymologySurvivesTheWire() {
        out.reset()
        val greek = "From Ancient Greek άβαξ (abax)."
        string("abacus")
        u32(1)
        string("Etymology")
        u8(4)
        stringList(listOf(greek))

        val result = OnymDecoder(out.toByteArray()).entry()

        val etymology = result.sections.single() as OnymSection.Etymology
        assertEquals(listOf(greek), etymology.paragraphs)
    }
}
