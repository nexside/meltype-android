// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Switch
import dev.horota.meltype.ime.ThemeMode
import android.widget.TextView
import android.widget.Toast

/** v0.1 の最小限の設定。細かい設定は本家と同じ config.json (<filesDir>/Meltype) にある。 */
class SettingsActivity : Activity() {
    private lateinit var prefs: MeltypePrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = MeltypePrefs(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        root.addView(heading("はじめに"))
        root.addView(button("1. Meltype を有効にする") { open(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) })
        root.addView(button("2. Meltype を選ぶ") { getSystemService(InputMethodManager::class.java).showInputMethodPicker() })
        root.addView(button("3. 物理キーボードの配列 (JIS キーボードなら「日本語」)") {
            open(Intent(Settings.ACTION_HARD_KEYBOARD_SETTINGS))
        })

        root.addView(heading("英 / 日の切り替え"))
        root.addView(TextView(this).apply { text = "半角/全角キーはいつでも使えます。Ctrl+半角/全角 で一時停止します。" })
        root.addView(switch("Shift+Space で切り替える (Q25 向け)", prefs.shiftSpaceToggles) { prefs.shiftSpaceToggles = it })
        root.addView(switch("Ctrl+Space で切り替える", prefs.ctrlSpaceToggles) { prefs.ctrlSpaceToggles = it })

        root.addView(heading("入力"))
        root.addView(switch("かっこの組 ( () ・ 「」 など) を確定したら、カーソルを間に置く", prefs.cursorBetweenBrackets) { prefs.cursorBetweenBrackets = it })
        root.addView(TextView(this).apply {
            text = "絵文字の一覧は Ctrl+. (Ctrl+; ・ Win+. でも)、Ctrl の無い Q25 では Alt+Enter で開きます。矢印で選んで Enter、Tab でカテゴリを切り替え、Esc で閉じます。" +
                "「えがお」「ねこ」「えもじ」などの読みで変換しても、候補の最後に絵文字が出ます (候補の「絵文字」ボタンで絞り込めます)。"
        })

        root.addView(heading("候補の配色"))
        root.addView(Spinner(this).apply {
            val names = listOf("システムに合わせる", "ライト (白地に黒文字)", "ダーク (黒地に白文字)")
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, names)
            setSelection(prefs.themeMode.ordinal)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    prefs.themeMode = ThemeMode.values()[position]
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        })

        root.addView(heading("コード入力モードにするアプリ (1 行に 1 つ)"))
        val codeApps = EditText(this).apply { setText(CodeAppList.format(prefs.codeApps)); minLines = 3 }
        root.addView(codeApps)
        root.addView(button("保存") {
            prefs.codeApps = CodeAppList.parse(codeApps.text.toString())
            codeApps.setText(CodeAppList.format(prefs.codeApps))
            Toast.makeText(this, "保存しました", Toast.LENGTH_SHORT).show()
        })

        root.addView(heading("辞書と学習"))
        root.addView(button("ユーザー辞書を編集する") { startActivity(Intent(this, UserDictionaryActivity::class.java)) })
        root.addView(button("ジャンル別の辞書") { startActivity(Intent(this, GenreDictionaryActivity::class.java)) })
        root.addView(button("学習データを消す") { confirmClearLearning() })

        root.addView(heading("このアプリについて"))
        root.addView(button("診断ログ (表示の不具合の調査用)") { startActivity(Intent(this, DiagLogActivity::class.java)) })
        root.addView(button("ライセンス") { showNotices() })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun confirmClearLearning() {
        AlertDialog.Builder(this)
            .setMessage("覚えた変換・英語 / 日本語の判定を消します (ユーザー辞書と設定は残ります)。Meltype を再起動します。")
            .setPositiveButton("消す") { _, _ ->
                LearningData.requestClear(MeltypePrefs.dataDirectory(this))
                // 同じプロセスの IME ごと終了する。次に IME が起動したとき (CoreBridge.open) に消える。
                Process.killProcess(Process.myPid())
            }
            .setNegativeButton("やめる", null)
            .show()
    }

    private fun showNotices() {
        val text = Notices.compose { name ->
            try {
                assets.open("notices/$name").bufferedReader().use { it.readText() }
            } catch (e: java.io.IOException) {
                null
            }
        }
        AlertDialog.Builder(this)
            .setTitle("ライセンス")
            .setView(ScrollView(this).apply {
                addView(TextView(context).apply {
                    this.text = text
                    textSize = 11f
                    setTextIsSelectable(true)
                    setPadding(32, 16, 32, 16)
                })
            })
            .setPositiveButton("閉じる", null)
            .show()
    }

    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
    }

    private fun heading(text: String) = TextView(this).apply {
        this.text = text
        textSize = 18f
        setPadding(0, 32, 0, 8)
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        setOnClickListener { onClick() }
    }

    private fun switch(text: String, checked: Boolean, onChange: (Boolean) -> Unit) = Switch(this).apply {
        this.text = text
        isChecked = checked
        setOnCheckedChangeListener { _, value -> onChange(value) }
    }
}
