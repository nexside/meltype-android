// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.settings

import java.io.File

/** 本家 Core が入力欄を作るときに読む辞書のファイル (userdict.txt と dictionaries フォルダの .plist) の変化を見分ける。 */
object DictionaryFiles {
    /** 辞書のファイルが変わると値が変わる。IME はこれが変わったら、入力の本体を作り直して辞書を読み直す。 */
    fun stamp(dataDirectory: File): Long {
        var value = File(dataDirectory, "userdict.txt").lastModified()
        val plists = File(dataDirectory, "dictionaries").listFiles { f -> f.isFile && f.name.endsWith(".plist") }
        plists?.sortedBy { it.name }?.forEach { file ->
            value = value * 31 + file.lastModified()
            value = value * 31 + file.length()
            value = value * 31 + file.name.hashCode()
        }
        return value
    }
}
