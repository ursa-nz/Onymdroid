// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

package nz.ursa.onymdroid.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/*
 * A pure decoder guard for the translations section kind (wire byte 5), needing neither the native
 * library nor a WordNet database: it hand-builds an entry buffer in the little-endian, length-
 * prefixed format the JNI codec writes and asserts the decoder rebuilds it as an
 * OnymSection.Translations of per-sense, language-grouped words. The native round-trip is covered by
 * the parity suite once the engine .so is built; this pins the kind-5 mapping on its own.
 */
internal class TranslationsDecodeTest {
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
    fun translationsSectionDecodesSenseByLanguage() {
        // An entry: term, section count, then one section as title, kind byte 5, and one sense block:
        // pos present byte and string, gloss, language count, each a name and a words list.
        string("dog")
        u32(1)
        string("Translations")
        u8(5)
        u32(1) // one sense block
        u8(1) // pos present
        string("noun")
        string("a member of the genus Canis")
        u32(2) // two languages
        string("Italian")
        stringList(listOf("cane", "Canis familiaris"))
        string("Portuguese")
        stringList(listOf("cão", "cachorro"))

        val result = OnymDecoder(out.toByteArray()).entry()

        assertEquals("dog", result.term)
        val expected = OnymSection.Translations(
            "Translations",
            listOf(
                OnymSenseTranslations(
                    pos = "noun",
                    gloss = "a member of the genus Canis",
                    languages = listOf(
                        OnymLanguageWords("Italian", listOf("cane", "Canis familiaris")),
                        OnymLanguageWords("Portuguese", listOf("cão", "cachorro")),
                    ),
                ),
            ),
        )
        assertEquals(expected, result.sections.single())
    }

    @Test
    fun aSenseWithNoPartOfSpeechDecodes() {
        out.reset()
        string("x")
        u32(1)
        string("Translations")
        u8(5)
        u32(1)
        u8(0) // pos absent
        string("a gloss")
        u32(1)
        string("Italian")
        stringList(listOf("parola"))

        val result = OnymDecoder(out.toByteArray()).entry()

        val section = result.sections.single() as OnymSection.Translations
        assertEquals(null, section.items.single().pos)
        assertEquals("parola", section.items.single().languages.single().words.single())
    }
}
