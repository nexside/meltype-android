// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import dev.horota.meltype.core.CompositionView

/**
 * 変換中の文字のすぐ上 (上に入らなければ下) に出す候補の窓。画面の下の候補バーの代わり。
 * 入力欄が入力カーソルの位置を教えてくれないとき (anchor が null) は何もせず、呼んだ側が候補バーを使う。
 * 出すのは候補 (または予測) があるときだけ。入力しているだけのときは出さない。
 */
class FloatingCandidates(
    private val service: InputMethodService,
    private val anchor: () -> Anchor?,
    private val onSelect: (Int) -> Unit,
    private val onConvert: (ConvertTarget) -> Unit,
) {
    /** 画面の座標。x は変換中の文字の左端、top / bottom はその行の上端 / 下端。 */
    data class Anchor(val x: Float, val top: Float, val bottom: Float)

    private var theme = CandidateTheme.LIGHT

    private val list = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL }
    private val buttons = LinearLayout(service).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(button("カナ", ConvertTarget.KATAKANA))
        addView(button("英数", ConvertTarget.ALPHANUMERIC))
    }
    private val row = LinearLayout(service).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = rowBackground()
        addView(HorizontalScrollView(service).apply {
            isHorizontalScrollBarEnabled = false
            addView(list)
        })
        addView(buttons)
    }

    private val window = ImeDialog(service, row, touchable = true)
    private var lastView: CompositionView? = null

    /** 候補を出す。入力カーソルの位置が分からない・窓を出せないときは false (候補バーを使う)。 */
    fun show(view: CompositionView): Boolean {
        val a = anchor() ?: return false
        val items = view.candidates.ifEmpty { view.predictions }
        if (items.isEmpty()) {
            hide()
            return true
        }
        lastView = view
        list.removeAllViews()
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
        buttons.visibility = if (view.text.isNotEmpty()) View.VISIBLE else View.GONE
        if (place(a)) return true
        hide()
        return false
    }

    /** 入力欄が入力カーソルの位置を新しく教えてくれたとき。出ていれば、その位置に合わせ直す。 */
    fun reposition() {
        if (lastView == null) return
        val a = anchor() ?: return
        if (!place(a)) hide()
    }

    fun hide() {
        lastView = null
        window.dismiss()
    }

    /** 配色を変える。いま出ている候補は、次に出し直すときから新しい色になる。 */
    fun applyTheme(newTheme: CandidateTheme) {
        theme = newTheme
        row.background = rowBackground()
        for (i in 0 until buttons.childCount) (buttons.getChildAt(i) as? TextView)?.setTextColor(newTheme.accent)
    }

    private fun rowBackground() = GradientDrawable().apply {
        setColor(theme.background)
        cornerRadius = dp(8).toFloat()
        setStroke(dp(1), theme.border)
    }

    private fun place(a: Anchor): Boolean {
        val metrics = service.resources.displayMetrics
        row.measure(
            View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.AT_MOST),
        )
        val w = row.measuredWidth
        val h = row.measuredHeight
        val x = a.x.toInt().coerceIn(0, maxOf(0, metrics.widthPixels - w))
        var y = (a.top - h - dp(4)).toInt()
        if (y < 0) y = (a.bottom + dp(4)).toInt()
        return window.show(x, y, w, h)
    }

    private fun button(text: String, target: ConvertTarget) = label().apply {
        this.text = text
        setTextColor(theme.accent)
        setOnClickListener { onConvert(target) }
    }

    private fun label() = TextView(service).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(theme.text)
        setPadding(dp(10), dp(4), dp(10), dp(4))
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), service.resources.displayMetrics).toInt()
}
