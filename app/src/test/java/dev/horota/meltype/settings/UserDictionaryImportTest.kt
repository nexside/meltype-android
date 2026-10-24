// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.settings

import dev.horota.meltype.settings.UserDictionaryImport.Entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UserDictionaryImportTest {
    @Test
    fun parsesTabSeparatedLinesAndIgnoresTheRestOfTheColumns() {
        val result = UserDictionaryImport.parse("めるたいぷ\tMeltype\t名詞\tコメント\nきごうとう\t記号等")
        assertEquals(listOf(Entry("めるたいぷ", "Meltype"), Entry("きごうとう", "記号等")), result.entries)
        assertEquals(emptyList<Int>(), result.skipped)
    }

    @Test
    fun parsesCommaAndSpaceSeparatedLines() {
        val result = UserDictionaryImport.parse("めるたいぷ,Meltype\nきごうとう 記号 等\nにほん　日本")
        assertEquals(
            listOf(Entry("めるたいぷ", "Meltype"), Entry("きごうとう", "記号 等"), Entry("にほん", "日本")),
            result.entries,
        )
    }

    @Test
    fun skipsCommentsAndBlankLinesWithoutCountingThemAsErrors() {
        val result = UserDictionaryImport.parse("!Microsoft IME Dictionary Tool\n# メモ\n\nめるたいぷ\tMeltype\r\n")
        assertEquals(listOf(Entry("めるたいぷ", "Meltype")), result.entries)
        assertEquals(emptyList<Int>(), result.skipped)
    }

    @Test
    fun convertsKatakanaReadingsToHiragana() {
        assertEquals(listOf(Entry("めるたいぷ", "Meltype")), UserDictionaryImport.parse("メルタイプ\tMeltype").entries)
    }

    @Test
    fun reportsLineNumbersOfLinesItCannotRead() {
        val result = UserDictionaryImport.parse(
            "めるたいぷ\tMeltype\n" + // 1 OK
                "あ\t亜\n" + // 2 読みが 1 文字
                "ひとつだけ\n" + // 3 区切りが無い
                "漢字\t読みが漢字\n" + // 4 読みがかなでない
                "くうご\t\n" + // 5 単語が空
                "ふつう\t普通", // 6 OK
        )
        assertEquals(listOf(Entry("めるたいぷ", "Meltype"), Entry("ふつう", "普通")), result.entries)
        assertEquals(listOf(2, 3, 4, 5), result.skipped)
    }

    @Test
    fun acceptsAsciiLettersAndDigitsInReadings() {
        assertEquals(listOf(Entry("abc12", "ABC12")), UserDictionaryImport.parse("abc12\tABC12").entries)
    }

    @Test
    fun mergeAppendsNewEntriesAndKeepsExistingTextAsIs() {
        val existing = "# 自分の辞書\nめるたいぷ\tMeltype"
        val result = UserDictionaryImport.merge(existing, listOf(Entry("きごうとう", "記号等")))
        assertEquals("# 自分の辞書\nめるたいぷ\tMeltype\nきごうとう\t記号等\n", result.text)
        assertEquals(1, result.added)
        assertEquals(0, result.duplicates)
    }

    @Test
    fun mergeSkipsEntriesAlreadyThereAndDuplicatesInTheInput() {
        val existing = "めるたいぷ\tMeltype\n"
        val result = UserDictionaryImport.merge(
            existing,
            listOf(Entry("めるたいぷ", "Meltype"), Entry("ふつう", "普通"), Entry("ふつう", "普通")),
        )
        assertEquals("めるたいぷ\tMeltype\nふつう\t普通\n", result.text)
        assertEquals(1, result.added)
        assertEquals(2, result.duplicates)
    }

    @Test
    fun mergeIntoEmptyDictionary() {
        val result = UserDictionaryImport.merge("", listOf(Entry("めるたいぷ", "Meltype")))
        assertEquals("めるたいぷ\tMeltype\n", result.text)
    }

    @Test
    fun sameReadingWithDifferentWordIsAdded() {
        val result = UserDictionaryImport.merge("かみ\t神\n", listOf(Entry("かみ", "紙")))
        assertEquals("かみ\t神\nかみ\t紙\n", result.text)
        assertEquals(1, result.added)
    }

    @Test
    fun mergedTextPassesTheExistingFormatCheck() {
        val parsed = UserDictionaryImport.parse("めるたいぷ,Meltype\nきごうとう 記号等")
        val merged = UserDictionaryImport.merge("", parsed.entries)
        assertEquals(emptyList<Int>(), UserDictionaryText.invalidLines(merged.text))
    }

    @Test
    fun decodesUtf8WithAndWithoutBom() {
        val text = "めるたいぷ\tMeltype"
        assertEquals(text, UserDictionaryImport.decode(text.toByteArray(Charsets.UTF_8)))
        assertEquals(text, UserDictionaryImport.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun decodesUtf16WithBomLikeMicrosoftImeExports() {
        val text = "!Microsoft IME Dictionary Tool\r\nめるたいぷ\tMeltype\t名詞\r\n"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        assertEquals(text, UserDictionaryImport.decode(bytes))
    }

    @Test
    fun decodesUtf16WithoutBom() {
        val text = "めるたいぷ\tMeltype\n"
        assertEquals(text, UserDictionaryImport.decode(text.toByteArray(Charsets.UTF_16LE)))
    }

    @Test
    fun rejectsShiftJisFiles() {
        val bytes = "めるたいぷ\tMeltype".toByteArray(charset("Shift_JIS"))
        assertThrows(IllegalArgumentException::class.java) { UserDictionaryImport.decode(bytes) }
    }
}
