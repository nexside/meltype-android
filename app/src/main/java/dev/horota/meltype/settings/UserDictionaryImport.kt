// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.settings

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * ユーザー辞書 (userdict.txt、1 行に「読み[Tab]単語」) への一括登録。
 * 貼り付けたテキストやファイルから「読み」と「単語」を取り出し、今の内容に足す。
 *
 * 読める書式 (1 行 1 語。! と # で始まる行・空行は飛ばす):
 *  - 読み[Tab]単語[Tab]品詞…  (Microsoft IME の一覧の出力・Google 日本語入力のエクスポート・Meltype の userdict.txt)
 *  - 読み,単語                 (カンマ区切り)
 *  - 読み 単語                 (空白区切り。単語の中の空白はそのまま残す)
 * 読みのカタカナはひらがなにする。本家 (UserDictionaryFile) と同じく、読みは 2 文字以上のかな・英数字だけ。
 */
object UserDictionaryImport {
    /** 本家 UserDictionary.MinReadingLength と同じ。1 文字の読みは巻き込みが大きいので登録しない。 */
    const val MIN_READING_LENGTH = 2

    /** ファイルから取り込むときの上限 (大きすぎるファイルでメモリを使い切らないように)。 */
    const val MAX_FILE_BYTES = 5 * 1024 * 1024

    data class Entry(val reading: String, val word: String)

    /** entries: 読み取れた語。skipped: 読み取れなかった行の番号 (1 始まり)。 */
    data class ParseResult(val entries: List<Entry>, val skipped: List<Int>)

    /** text: 追加したあとの userdict.txt の中身。added: 足した数。duplicates: 既にあって飛ばした数。 */
    data class MergeResult(val text: String, val added: Int, val duplicates: Int)

    fun parse(text: String): ParseResult {
        val entries = mutableListOf<Entry>()
        val skipped = mutableListOf<Int>()
        text.lines().forEachIndexed { index, raw ->
            val line = raw.trimEnd('\r')
            if (line.isBlank() || line.startsWith("!") || line.startsWith("#")) return@forEachIndexed
            val entry = parseLine(line)
            if (entry == null) skipped += index + 1 else entries += entry
        }
        return ParseResult(entries, skipped)
    }

    /** 1 行を (読み, 単語) にする。読み取れなければ null。 */
    fun parseLine(line: String): Entry? {
        val (rawReading, rawWord) = split(line) ?: return null
        val reading = normalizeReading(rawReading)
        val word = rawWord.trim()
        return if (isValidEntry(reading, word)) Entry(reading, word) else null
    }

    /** 1 語の検証 (読みは正規化済みであること)。 */
    fun isValidEntry(reading: String, word: String): Boolean =
        reading.length >= MIN_READING_LENGTH && reading.all(::isReadingChar) &&
            word.isNotEmpty() && word.none { it == '\t' || it == '\r' || it == '\n' }

    /** 読みをひらがなにそろえて、前後の空白を取る。 */
    fun normalizeReading(reading: String): String =
        reading.trim().map { if (it in 'ァ'..'ヶ') (it.code - 0x60).toChar() else it }.joinToString("")

    /**
     * existing (今の userdict.txt の中身) に entries を足す。「読みと単語が同じ」ものが既にあれば足さない。
     * 今の内容 (コメントや並び) はそのまま残し、足す分を末尾に付ける。
     */
    fun merge(existing: String, entries: List<Entry>): MergeResult {
        val known = HashSet<Entry>()
        for (line in existing.lines()) {
            val fields = line.trimEnd('\r').split('\t')
            if (fields.size >= 2 && !line.startsWith("#") && !line.startsWith("!")) {
                known += Entry(fields[0].trim(), fields[1].trim())
            }
        }
        val builder = StringBuilder(existing)
        var added = 0
        var duplicates = 0
        for (entry in entries) {
            if (!known.add(entry)) {
                duplicates++
                continue
            }
            if (builder.isNotEmpty() && builder.last() != '\n') builder.append('\n')
            builder.append(entry.reading).append('\t').append(entry.word).append('\n')
            added++
        }
        return MergeResult(builder.toString(), added, duplicates)
    }

    /**
     * ファイルの中身を文字列にする。BOM つきの UTF-8 / UTF-16、BOM の無い UTF-8、BOM の無い UTF-16 (LE) を読む。
     * どれでも読めないとき (Shift_JIS など) は IllegalArgumentException。
     */
    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        try {
            val text = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
            // 英字だけの BOM の無い UTF-16 は UTF-8 としても読めてしまうが、0 の文字が入る
            if (!text.contains('\u0000')) return text
            return String(bytes, Charsets.UTF_16LE)
        } catch (e: CharacterCodingException) {
            // BOM の無い UTF-16 (タブ・改行の後ろに 0 が入る)。UTF-8 の文には 0 は入らない
            if (bytes.size % 2 == 0 && bytes.indices.any { it % 2 == 1 && bytes[it] == 0.toByte() }) {
                return String(bytes, Charsets.UTF_16LE)
            }
            throw IllegalArgumentException(
                "文字コードを読めませんでした。UTF-8 か UTF-16 で保存したファイルを選んでください " +
                    "(Shift_JIS のファイルは、UTF-8 で保存し直すと取り込めます)。",
            )
        }
    }

    private fun split(line: String): Pair<String, String>? {
        if ('\t' in line) {
            val fields = line.split('\t')
            return if (fields.size >= 2) fields[0] to fields[1] else null
        }
        val comma = line.indexOf(',')
        if (comma >= 0) return line.substring(0, comma) to line.substring(comma + 1)
        val space = line.indexOfFirst { it == ' ' || it == '　' }
        if (space >= 0) return line.substring(0, space) to line.substring(space + 1)
        return null
    }

    private fun isReadingChar(c: Char): Boolean =
        c in 'ぁ'..'ゖ' || c == 'ー' || c == 'ゔ' || c == '・' || c in '0'..'9' || c in 'a'..'z' || c in 'A'..'Z'
}
