// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

/**
 * 確定した文字が「かっこの組」(例: `()` `「」`) そのものか。そうなら、カーソルを間に置く (中に書き始められるように)。
 * 引用符 (" ') と不等号 (< >) は、片方だけ使うことが多いので数えない。
 */
object BracketPairs {
    private val pairs: Map<Char, Char> = mapOf(
        '(' to ')', '（' to '）',
        '[' to ']', '［' to '］',
        '{' to '}', '｛' to '｝',
        '「' to '」', '『' to '』',
        '【' to '】', '〈' to '〉', '《' to '》', '〔' to '〕',
    )

    /** text がちょうど「開く+閉じる」の 2 文字なら、その 2 つ。そうでなければ null。 */
    fun split(text: String): Pair<String, String>? {
        if (text.length != 2) return null
        return if (pairs[text[0]] == text[1]) text.substring(0, 1) to text.substring(1) else null
    }
}
