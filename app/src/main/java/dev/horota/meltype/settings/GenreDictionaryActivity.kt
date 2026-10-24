// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * ジャンル別の辞書 (専門分野) をオン / オフする画面。同梱のジャンルと、自分で作るジャンル (語はテキストで一括登録できる)。
 * 反映は次に入力欄を開いたとき。
 */
class GenreDictionaryActivity : Activity() {
    private lateinit var store: GenreStore
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = GenreStore(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        root.removeAllViews()
        root.addView(heading("ジャンル別の辞書"))
        root.addView(
            TextView(this).apply {
                text = "使う分野をオンにすると、その分野の用語が変換で出ます。入力欄を開き直すと反映されます。"
            },
        )
        root.addView(heading("同梱のジャンル"))
        for (genre in GenreCatalog.builtIns) {
            val count = store.entries(genre.key).size
            root.addView(switch(genre.key, "${genre.name} ($count 語)\n${genre.description}"))
        }
        root.addView(heading("自分のジャンル"))
        val mine = store.userGenres()
        if (mine.isEmpty()) root.addView(TextView(this).apply { text = "まだありません。" })
        for (name in mine) {
            val key = "user:$name"
            root.addView(switch(key, "$name (${store.entries(key).size} 語)"))
            root.addView(
                LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(smallButton("語を登録・編集") {
                        startActivity(Intent(context, UserDictionaryActivity::class.java).putExtra(UserDictionaryActivity.EXTRA_GENRE, name))
                    })
                    addView(smallButton("削除") { confirmDelete(name) })
                },
            )
        }
        root.addView(smallButton("ジャンルを作る") { askName() })
    }

    private fun switch(key: String, label: String) = CheckBox(this).apply {
        text = label
        isChecked = store.isEnabled(key)
        setOnCheckedChangeListener { _, checked -> store.setEnabled(key, checked) }
    }

    private fun askName() {
        val input = EditText(this).apply {
            hint = "例: 料理、鉄道"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle("ジャンルの名前")
            .setView(input)
            .setPositiveButton("作る") { _, _ ->
                val error = store.create(input.text.toString())
                if (error != null) Toast.makeText(this, error, Toast.LENGTH_LONG).show() else render()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun confirmDelete(name: String) {
        AlertDialog.Builder(this)
            .setMessage("ジャンル「$name」とその語を削除しますか?")
            .setPositiveButton("削除") { _, _ ->
                store.delete(name)
                render()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun heading(text: String) = TextView(this).apply {
        this.text = text
        textSize = 18f
        setPadding(0, 32, 0, 8)
    }

    private fun smallButton(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        setOnClickListener { onClick() }
    }
}
