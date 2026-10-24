// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.settings

import java.io.File

/** 表示の不具合を調べるための小さなログ (<データ>/diag.txt)。設定の「診断ログ」で見られる。 */
object DiagLog {
    private const val MAX_LINES = 150

    @Synchronized
    fun add(dataDirectory: File, line: String) {
        try {
            val file = File(dataDirectory, "diag.txt")
            val time = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
            val lines = if (file.exists()) file.readLines() else emptyList()
            dataDirectory.mkdirs()
            file.writeText((lines + "$time $line").takeLast(MAX_LINES).joinToString("\n") + "\n")
        } catch (e: Exception) {
            // ログが書けなくても動作は続ける
        }
    }

    fun read(dataDirectory: File): String =
        try {
            File(dataDirectory, "diag.txt").takeIf { it.exists() }?.readText().orEmpty().ifEmpty { "(まだありません)" }
        } catch (e: Exception) {
            "(読めません)"
        }

    fun clear(dataDirectory: File) {
        File(dataDirectory, "diag.txt").delete()
    }
}
