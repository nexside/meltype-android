// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.settings

/** 自分で作るジャンルの名前の整え方。名前はそのままファイル名 (genres/名前.txt) になる。 */
object GenreNames {
    const val MAX_LENGTH = 40

    /** ファイル名に使えない文字を _ にして、前後の空白と先頭の . を取り、長さを切る。空になったら空の文字列。 */
    fun sanitize(raw: String): String =
        raw.trim()
            .map { if (it in FORBIDDEN || it < ' ') '_' else it }
            .joinToString("")
            .trimStart('.', ' ')
            .take(MAX_LENGTH)
            .trim()

    private const val FORBIDDEN = "\\/:*?\"<>|"
}
