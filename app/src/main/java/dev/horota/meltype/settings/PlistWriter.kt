// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.settings

/**
 * 語の一覧を、本家 Core が読める .plist (macOS の「ユーザ辞書」の書き出しと同じ形: plist > array > dict、shortcut = 読み、phrase = 語) にする。
 * 本家は dictionaries/ フォルダの .plist を、入力欄を作るたびに読み直す。ジャンル別辞書はこの形で渡す。
 */
object PlistWriter {
    fun write(entries: List<UserDictionaryImport.Entry>): String {
        val builder = StringBuilder()
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        builder.append("<plist version=\"1.0\">\n<array>\n")
        for (entry in entries) {
            builder.append("<dict><key>shortcut</key><string>").append(escape(entry.reading))
            builder.append("</string><key>phrase</key><string>").append(escape(entry.word))
            builder.append("</string></dict>\n")
        }
        builder.append("</array>\n</plist>\n")
        return builder.toString()
    }

    /** XML の特別な文字と、XML 1.0 で使えない制御文字を片づける。 */
    fun escape(text: String): String {
        val builder = StringBuilder(text.length)
        for (c in text) {
            when {
                c == '&' -> builder.append("&amp;")
                c == '<' -> builder.append("&lt;")
                c == '>' -> builder.append("&gt;")
                c == '"' -> builder.append("&quot;")
                c == '\'' -> builder.append("&apos;")
                c < ' ' -> Unit // タブ・改行も含めて捨てる (1 語は 1 行のはず)
                else -> builder.append(c)
            }
        }
        return builder.toString()
    }
}
