// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/**
 * 本家の userdict.txt (「読み[Tab]単語」) の編集と、テキストの一括登録。
 * 反映は、次に入力欄を開いたとき (Meltype が入力の本体を作り直す)。
 */
class UserDictionaryActivity : Activity() {
    private lateinit var file: File
    private var genre: String? = null
    private lateinit var editor: EditText
    private lateinit var bulk: EditText
    private lateinit var result: TextView
    private lateinit var count: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        genre = intent.getStringExtra(EXTRA_GENRE)
        file = if (genre != null) GenreStore(this).userGenreFile(genre) else File(MeltypePrefs.dataDirectory(this), "userdict.txt")
        title = genre?.let { "ジャンル: $it" } ?: "ユーザー辞書"

        count = TextView(this)
        result = TextView(this).apply { gravity = Gravity.START }

        bulk = EditText(this).apply {
            hint = "めるたいぷ\tMeltype\nきごうとう,記号等\nにほん 日本"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 6
            gravity = Gravity.TOP or Gravity.START
        }
        val addBulk = Button(this).apply {
            text = "貼り付けた内容を追加"
            setOnClickListener { addFromText(bulk.text.toString(), clearInput = true) }
        }
        val pick = Button(this).apply {
            text = "ファイルから取り込む"
            setOnClickListener { pickFile() }
        }

        editor = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 10
            gravity = Gravity.TOP or Gravity.START
        }
        val save = Button(this).apply {
            text = "保存"
            setOnClickListener {
                val text = editor.text.toString()
                val invalid = UserDictionaryText.invalidLines(text)
                if (invalid.isNotEmpty()) {
                    Toast.makeText(context, "形式が違う行: ${invalid.joinToString()} (読み[Tab]単語)", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                writeDictionary(text)
                refresh()
                Toast.makeText(context, "保存しました。次に入力欄を開いたときに反映されます", Toast.LENGTH_LONG).show()
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            addView(heading("一括登録"))
            addView(
                TextView(context).apply {
                    text = "1 行に 1 語。「読み[Tab]単語」「読み,単語」「読み 単語」のどれでも読めます。" +
                        "Microsoft IME の一覧の出力や Google 日本語入力のエクスポート (UTF-8 / UTF-16) もそのまま取り込めます。" +
                        "! と # で始まる行は飛ばし、読みのカタカナはひらがなにします。同じ読みと単語は二重に登録しません。"
                },
            )
            addView(bulk)
            addView(addBulk)
            addView(pick)
            addView(result)
            addView(heading("登録済みの内容 (直接編集)"))
            addView(TextView(context).apply { text = "1 行に「読み[Tab]単語」。# と ! で始まる行はコメント。" })
            addView(count)
            addView(editor)
            addView(save)
        }
        setContentView(ScrollView(this).apply { addView(root) })
        refresh()
    }

    private fun pickFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, REQUEST_PICK)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PICK || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val text = try {
            readFile(uri)
        } catch (e: IOException) {
            showResult("読めませんでした: ${e.message}")
            return
        } catch (e: IllegalArgumentException) {
            showResult(e.message.orEmpty())
            return
        }
        addFromText(text, clearInput = false)
    }

    private fun readFile(uri: Uri): String {
        val bytes = contentResolver.openInputStream(uri)?.use { input ->
            val limit = UserDictionaryImport.MAX_FILE_BYTES
            val out = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            var total = 0
            while (true) {
                val n = input.read(chunk)
                if (n < 0) break
                total += n
                if (total > limit) throw IllegalArgumentException("ファイルが大きすぎます (${limit / 1024 / 1024} MB まで)")
                out.write(chunk, 0, n)
            }
            out.toByteArray()
        } ?: throw IOException("ファイルを開けませんでした")
        return UserDictionaryImport.decode(bytes)
    }

    private fun addFromText(text: String, clearInput: Boolean) {
        if (text.isBlank()) {
            showResult("追加する内容がありません")
            return
        }
        val parsed = UserDictionaryImport.parse(text)
        val existing = if (file.exists()) file.readText() else ""
        val merged = UserDictionaryImport.merge(existing, parsed.entries)
        if (merged.added > 0) writeDictionary(merged.text)
        if (clearInput && parsed.skipped.isEmpty()) bulk.setText("")
        refresh()
        showResult(summary(merged.added, merged.duplicates, parsed.skipped))
    }

    private fun summary(added: Int, duplicates: Int, skipped: List<Int>): String {
        val parts = mutableListOf("${added} 語を追加しました")
        if (duplicates > 0) parts += "登録済みで飛ばした: $duplicates"
        if (skipped.isNotEmpty()) {
            val lines = skipped.take(MAX_SHOWN_LINES).joinToString() + if (skipped.size > MAX_SHOWN_LINES) " …" else ""
            parts += "読み取れなかった行 (${skipped.size}): $lines"
        }
        if (added > 0) parts += "次に入力欄を開いたときに反映されます"
        return parts.joinToString("\n")
    }

    private fun writeDictionary(text: String) {
        file.parentFile?.mkdirs()
        file.writeText(text)
        if (genre != null) GenreStore(this).sync() // オンのジャンルなら辞書のファイルを作り直す
    }

    private fun refresh() {
        val text = if (file.exists()) file.readText() else ""
        editor.setText(text)
        val words = text.lines().count { line ->
            val fields = line.split('\t')
            !line.startsWith("#") && !line.startsWith("!") && fields.size >= 2 && fields[0].isNotBlank() && fields[1].isNotBlank()
        }
        count.text = "登録数: $words"
    }

    private fun showResult(text: String) {
        result.text = text
    }

    private fun heading(text: String) = TextView(this).apply {
        this.text = text
        textSize = 18f
        setPadding(0, 32, 0, 8)
    }

    private companion object {
        const val EXTRA_GENRE = "genre"
        const val REQUEST_PICK = 1
        const val MAX_SHOWN_LINES = 10
    }
}
