// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.settings

import dev.horota.meltype.settings.UserDictionaryImport.Entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlistWriterTest {
    @Test
    fun writesShortcutAndPhraseForEveryEntry() {
        val xml = PlistWriter.write(listOf(Entry("めるたいぷ", "Meltype"), Entry("きごうとう", "記号等")))
        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue("<key>shortcut</key><string>めるたいぷ</string><key>phrase</key><string>Meltype</string>" in xml)
        assertTrue("<key>shortcut</key><string>きごうとう</string><key>phrase</key><string>記号等</string>" in xml)
        assertEquals(2, Regex("<dict>").findAll(xml).count())
        assertTrue(xml.trimEnd().endsWith("</plist>"))
    }

    @Test
    fun escapesXmlSpecialCharacters() {
        val xml = PlistWriter.write(listOf(Entry("あんぱさんど", "A&B <C> \"D\" 'E'")))
        assertTrue("<string>A&amp;B &lt;C&gt; &quot;D&quot; &apos;E&apos;</string>" in xml)
    }

    @Test
    fun dropsControlCharacters() {
        assertEquals("ab", PlistWriter.escape("a\u0000b"))
        assertEquals("ab", PlistWriter.escape("a\tb"))
    }

    @Test
    fun emptyListIsStillAnArray() {
        val xml = PlistWriter.write(emptyList())
        assertTrue("<array>" in xml && "</array>" in xml)
        assertEquals(0, Regex("<dict>").findAll(xml).count())
    }
}
