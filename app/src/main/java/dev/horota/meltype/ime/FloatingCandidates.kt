// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

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
 * 変換中の文字のすぐ上 (上に入らなければ下) に浮かべる候補の窓。画面の下にバーは出さない。
 * 入力欄が入力カーソルの位置を教えてくれないとき (Termux など) は、画面の下の少し上に浮かべる。
 * 出すのは、候補 (または予測) があるときと、入力欄が未確定の文字を描かないとき (読みを窓に出す) だけ。
 *
 * 右端のボタン: カナ / 英数 (入力中)、絵文字 (候補に絵文字があるとき。絵文字だけに絞る)、登録 (変換中。ユーザー辞書に登録)。
 */
class FloatingCandidates(
    private val service: InputMethodService,
    private val anchor: () -> Anchor?,
    private val recent: () -> List<String>,
    private val onSelect: (Int) -> Unit,
    private val onConvert: (ConvertTarget) -> Unit,
    private val onRegister: (CompositionView) -> Unit,
) {
    /** 画面の座標。x は変換中の文字の左端、top / bottom はその行の上端 / 下端。 */
    data class Anchor(val x: Float, val top: Float, val bottom: Float)

    private var theme = CandidateTheme.LIGHT

    /** 絵文字だけに絞って見せているか。 */
    private var emojiOnly = false
    private var lastView: CompositionView? = null
    private var lastInlineReading = false

    private val readingLabel = label().apply {
        paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
        visibility = View.GONE
    }
    private val list = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL }
    private val convertButtons = LinearLayout(service).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(actionButton("カナ") { onConvert(ConvertTarget.KATAKANA) })
        addView(actionButton("英数") { onConvert(ConvertTarget.ALPHANUMERIC) })
    }
    private val emojiButton = actionButton("絵文字") {
        emojiOnly = !emojiOnly
        lastView?.let { show(it, lastInlineReading) }
    }
    private val registerButton = actionButton("登録") { lastView?.let(onRegister) }
    private val row = LinearLayout(service).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = rowBackground()
        addView(readingLabel)
        addView(HorizontalScrollView(service).apply {
            isHorizontalScrollBarEnabled = false
            addView(list)
        })
        addView(convertButtons)
        addView(emojiButton)
        addView(registerButton)
    }

    private val window = ImeDialog(service, row, touchable = true)

    /** 候補を出す。出す物が無ければ窓を閉じる。窓を出せなかったら false。 */
    fun show(view: CompositionView, inlineReading: Boolean): Boolean {
        val items = view.candidates.ifEmpty { view.predictions }
        val reading = if (inlineReading) view.text else ""
        if (items.isEmpty() && reading.isEmpty()) {
            hide()
            return true
        }
        lastView = view
        lastInlineReading = inlineReading

        val hasEmoji = view.candidates.any(EmojiData::isEmoji)
        if (!hasEmoji) emojiOnly = false

        readingLabel.text = reading
        readingLabel.visibility = if (reading.isEmpty()) View.GONE else View.VISIBLE

        list.removeAllViews()
        val selected = if (view.candidates.isNotEmpty()) view.selectedIndex else view.selectedPrediction
        val order = if (emojiOnly) EmojiData.emojiOrder(view.candidates, recent()) else items.indices.toList()
        for (index in order) {
            list.addView(label().apply {
                text = items[index]
                if (index == selected) {
                    setBackgroundColor(theme.selectedBackground)
                    setTextColor(theme.selectedText)
                }
                if (view.candidates.isNotEmpty()) setOnClickListener { onSelect(index) }
            })
        }

        convertButtons.visibility = if (view.text.isNotEmpty()) View.VISIBLE else View.GONE
        emojiButton.visibility = if (hasEmoji) View.VISIBLE else View.GONE
        emojiButton.text = if (emojiOnly) "全部" else "絵文字"
        registerButton.visibility = if (view.converting) View.VISIBLE else View.GONE
        if (place()) return true
        hide()
        return false
    }

    /** 入力欄が入力カーソルの位置を新しく教えてくれたとき。出ていれば、その位置に合わせ直す。 */
    fun reposition() {
        if (lastView == null) return
        if (!place()) hide()
    }

    fun hide() {
        lastView = null
        emojiOnly = false
        window.dismiss()
    }

    /** 配色を変える。いま出ている候補は、次に出し直すときから新しい色になる。 */
    fun applyTheme(newTheme: CandidateTheme) {
        theme = newTheme
        row.background = rowBackground()
        readingLabel.setTextColor(newTheme.text)
        for (button in listOf(emojiButton, registerButton)) button.setTextColor(newTheme.accent)
        for (i in 0 until convertButtons.childCount) (convertButtons.getChildAt(i) as? TextView)?.setTextColor(newTheme.accent)
    }

    private fun rowBackground() = GradientDrawable().apply {
        setColor(theme.background)
        cornerRadius = dp(8).toFloat()
        setStroke(dp(1), theme.border)
    }

    private fun place(): Boolean {
        val metrics = service.resources.displayMetrics
        row.measure(
            View.MeasureSpec.makeMeasureSpec(metrics.widthPixels - dp(8), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.AT_MOST),
        )
        // 候補は 1 行。大きく測れてしまっても、画面を覆わないように切る
        val w = minOf(row.measuredWidth, metrics.widthPixels - dp(8))
        val h = minOf(row.measuredHeight, dp(48))
        val a = anchor()
        val x: Int
        val y: Int
        if (a != null) {
            x = a.x.toInt().coerceIn(0, maxOf(0, metrics.widthPixels - w))
            val above = (a.top - h - dp(4)).toInt()
            y = if (above >= 0) above else (a.bottom + dp(4)).toInt()
        } else {
            // カーソルの位置が分からない入力欄では、画面の下の少し上に浮かべる
            x = maxOf(0, (metrics.widthPixels - w) / 2)
            y = maxOf(0, metrics.heightPixels - h - dp(64))
        }
        return window.show(x, y, w, h)
    }

    private fun actionButton(text: String, onClick: () -> Unit) = label().apply {
        this.text = text
        setTextColor(theme.accent)
        setOnClickListener { onClick() }
    }

    private fun label() = TextView(service).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(theme.text)
        setPadding(dp(10), dp(4), dp(10), dp(4))
        setSingleLine()
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), service.resources.displayMetrics).toInt()
}
