// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import dev.horota.meltype.core.CompositionView

/**
 * 1 行の候補バー (Q25 の 720×720 の画面でも邪魔にならない高さ)。左端に英 / 日の状態、右に候補。
 * 候補をタップすると onSelect(index)。入力中は右端に「カナ」「英数」を出し、タップすると onConvert。
 * 何も入力していないときは隠す (高さ 0)。出すのは、入力中・警告があるとき・(カーソルの位置が分からないときの)「あ」/「A」の切り替え直後だけ。
 */
class CandidateBarView(
    context: Context,
    private val onSelect: (Int) -> Unit,
    private val onConvert: (ConvertTarget) -> Unit,
) :
    LinearLayout(context), CandidateDisplay, StatusDisplay {

    private var theme = CandidateTheme.LIGHT

    /** バーの中身。隠すときは content ごと GONE にして、全体の高さを 0 にする。 */
    private val content = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(theme.background)
        minimumHeight = dp(36)
        visibility = GONE
    }
    /**
     * 「あ」/「A」を切り替えたとき、入力カーソルの横に出す先 (サービスが設定する)。出せたら true。
     * false (カーソルの位置が分からない入力欄など) のときは、バーを短く出して見せる。
     */
    var modeFlash: ((String) -> Boolean)? = null
    private var composing = false
    private var flashing = false
    private val endFlash = Runnable {
        flashing = false
        refresh()
    }

    private val modeLabel = label().apply { setTypeface(typeface, Typeface.BOLD) }
    private val warningLabel = label().apply { setTextColor(theme.warning) }
    /** 入力欄が未確定の文字を描かないときの読み (Termux など)。 */
    private val readingLabel = label().apply { paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG }
    private val list = LinearLayout(context).apply { orientation = HORIZONTAL }
    private val convertButtons = LinearLayout(context).apply {
        orientation = HORIZONTAL
        visibility = GONE
        addView(button("カナ", ConvertTarget.KATAKANA))
        addView(button("英数", ConvertTarget.ALPHANUMERIC))
    }

    init {
        orientation = HORIZONTAL
        // 隠している間も窓が高さ 0 にならないように、1px だけ残す (0 だと端末によっては窓が出なくなる)
        minimumHeight = 1
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        content.addView(modeLabel)
        content.addView(warningLabel)
        content.addView(readingLabel)
        content.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(list)
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        content.addView(convertButtons)
    }

    /** 配色を変える。いま出ている候補は、次に出し直すときから新しい色になる。 */
    fun applyTheme(newTheme: CandidateTheme) {
        theme = newTheme
        content.setBackgroundColor(newTheme.background)
        modeLabel.setTextColor(newTheme.text)
        warningLabel.setTextColor(newTheme.warning)
        readingLabel.setTextColor(newTheme.text)
        for (i in 0 until convertButtons.childCount) (convertButtons.getChildAt(i) as? TextView)?.setTextColor(newTheme.accent)
    }

    private fun refresh() {
        content.visibility = if (composing || flashing || warningLabel.text.isNotEmpty()) VISIBLE else GONE
    }

    override fun show(view: CompositionView, inlineReading: Boolean) {
        readingLabel.text = if (inlineReading) view.text else ""
        convertButtons.visibility = if (view.text.isNotEmpty()) VISIBLE else GONE
        list.removeAllViews()
        val items = view.candidates.ifEmpty { view.predictions }
        composing = view.text.isNotEmpty() || items.isNotEmpty()
        refresh()
        val selected = if (view.candidates.isNotEmpty()) view.selectedIndex else view.selectedPrediction
        items.forEachIndexed { index, text ->
            list.addView(label().apply {
                this.text = text
                if (index == selected) {
                    setBackgroundColor(theme.selectedBackground)
                    setTextColor(theme.selectedText)
                }
                if (view.candidates.isNotEmpty()) setOnClickListener { onSelect(index) }
            })
        }
    }

    override fun hide() {
        composing = false
        refresh()
        readingLabel.text = ""
        convertButtons.visibility = GONE
        list.removeAllViews()
    }

    override fun showMode(mode: InputMode) {
        val newText = when (mode) {
            InputMode.JAPANESE -> "あ"
            InputMode.DIRECT -> "A"
            InputMode.PAUSED -> "⏸"
            InputMode.UNAVAILABLE -> "⚠"
        }
        // 切り替えたときだけ、短く見せる (入力欄を移っただけのときや最初は見せない)
        val changed = modeLabel.text.isNotEmpty() && modeLabel.text.toString() != newText
        modeLabel.text = newText
        if (changed && modeFlash?.invoke(newText) != true) {
            flashing = true
            removeCallbacks(endFlash)
            postDelayed(endFlash, 1500)
        }
        refresh()
    }

    /** null で消す。 */
    fun setWarning(text: String?) {
        warningLabel.text = text.orEmpty()
        refresh()
    }

    private fun button(text: String, target: ConvertTarget) = label().apply {
        this.text = text
        setTextColor(theme.accent)
        setOnClickListener { onConvert(target) }
    }

    private fun label() = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(theme.text)
        setPadding(dp(10), dp(4), dp(10), dp(4))
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
}
