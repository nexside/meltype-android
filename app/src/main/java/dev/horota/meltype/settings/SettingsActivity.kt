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

/** 設定。画面の色は端末のダークモードに従う。 */
class SettingsActivity : Activity() {
    private lateinit var prefs: MeltypePrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = MeltypePrefs(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(24))
        }

        root.addView(heading("はじめに"))
        root.addView(note("一度だけ、上から順に設定します。"))
        root.addView(button("1. Meltype を有効にする") { open(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) })
        root.addView(button("2. Meltype を選ぶ") { getSystemService(InputMethodManager::class.java).showInputMethodPicker() })
        root.addView(button("3. キーボード配列") { open(Intent(Settings.ACTION_HARD_KEYBOARD_SETTINGS)) })
        root.addView(note("JIS キーボードは、配列を「日本語」にします。"))

        root.addView(heading("切り替え"))
        root.addView(note("半角/全角はいつでも使えます。Ctrl+半角/全角 で一時停止します。"))
        root.addView(switch("Shift+Space で切り替える", prefs.shiftSpaceToggles) { prefs.shiftSpaceToggles = it })
        root.addView(note("Zinwa Q25 向け。"))
        root.addView(switch("Ctrl+Space で切り替える", prefs.ctrlSpaceToggles) { prefs.ctrlSpaceToggles = it })

        root.addView(heading("入力"))
        root.addView(switch("確定したかっこの中にカーソルを置く", prefs.cursorBetweenBrackets) { prefs.cursorBetweenBrackets = it })
        root.addView(note("() や 「」 を確定したとき。"))
        root.addView(note("絵文字は Ctrl+. (Ctrl+; と Win+. も)。Ctrl の無い Q25 は Alt+Enter。"))

        root.addView(heading("表示"))
        root.addView(note("候補の色。この設定画面自体は、端末のダークモードに従います。"))
        root.addView(Spinner(this).apply {
            val names = listOf("システムに合わせる", "ライト", "ダーク")
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, names)
            setSelection(prefs.themeMode.ordinal)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    prefs.themeMode = ThemeMode.values()[position]
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        })

        root.addView(heading("コード入力"))
        root.addView(note("コード入力モードにするアプリ。パッケージ名を 1 行に 1 つ。"))
        val codeApps = EditText(this).apply {
            setText(CodeAppList.format(prefs.codeApps))
            minLines = 3
            setHint("com.example.app")
        }
        root.addView(codeApps)
        root.addView(button("パッケージ名を保存") {
            prefs.codeApps = CodeAppList.parse(codeApps.text.toString())
            codeApps.setText(CodeAppList.format(prefs.codeApps))
            Toast.makeText(this, "保存しました", Toast.LENGTH_SHORT).show()
        })

        root.addView(heading("辞書"))
        root.addView(button("ユーザー辞書") { startActivity(Intent(this, UserDictionaryActivity::class.java)) })
        root.addView(button("ジャンル別の辞書") { startActivity(Intent(this, GenreDictionaryActivity::class.java)) })
        root.addView(button("学習データを消す") { confirmClearLearning() })
        root.addView(note("覚えた変換だけ消します。ユーザー辞書と設定は残ります。"))

        root.addView(heading("その他"))
        root.addView(button("診断ログ") { startActivity(Intent(this, DiagLogActivity::class.java)) })
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
        setPadding(0, dp(20), 0, dp(4))
    }

    private fun note(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setPadding(0, dp(2), 0, dp(6))
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun switch(text: String, checked: Boolean, onChange: (Boolean) -> Unit) = Switch(this).apply {
        this.text = text
        isChecked = checked
        setPadding(0, dp(6), 0, dp(6))
        setOnCheckedChangeListener { _, value -> onChange(value) }
    }

    private fun dp(value: Int) =
        (value * resources.displayMetrics.density).toInt()
}
