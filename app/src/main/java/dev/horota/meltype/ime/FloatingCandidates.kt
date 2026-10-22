// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
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

    private val list = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL }
    private val buttons = LinearLayout(service).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(button("カナ", ConvertTarget.KATAKANA))
        addView(button("英数", ConvertTarget.ALPHANUMERIC))
    }
    private val row = LinearLayout(service).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable().apply {
            setColor(Color.rgb(0xF2, 0xF2, 0xF2))
            cornerRadius = dp(8).toFloat()
            setStroke(dp(1), Color.rgb(0xBB, 0xBB, 0xBB))
        }
        addView(HorizontalScrollView(service).apply {
            isHorizontalScrollBarEnabled = false
            addView(list)
        })
        addView(buttons)
    }

    private var dialog: Dialog? = null
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
                    setBackgroundColor(Color.rgb(0x4C, 0xA0, 0xFF))
                    setTextColor(Color.WHITE)
                }
                if (view.candidates.isNotEmpty()) setOnClickListener { onSelect(index) }
            })
        }
        buttons.visibility = if (view.text.isNotEmpty()) View.VISIBLE else View.GONE
        return try {
            val d = dialog ?: createDialog() ?: return false
            place(d, a)
            if (!d.isShowing) d.show()
            true
        } catch (e: RuntimeException) {
            hide()
            false
        }
    }

    /** 入力欄が入力カーソルの位置を新しく教えてくれたとき。出ていれば、その位置に合わせ直す。 */
    fun reposition() {
        val d = dialog ?: return
        if (lastView == null) return
        val a = anchor() ?: return
        try {
            place(d, a)
        } catch (e: RuntimeException) {
            hide()
        }
    }

    fun hide() {
        lastView = null
        try {
            dialog?.dismiss()
        } catch (e: RuntimeException) {
            // 窓がもう無いときは何もしない
        }
        dialog = null
    }

    private fun createDialog(): Dialog? {
        (row.parent as? android.view.ViewGroup)?.removeView(row)
        val d = Dialog(service)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.setContentView(row)
        val window = d.window ?: return null
        val params = window.attributes
        // 入力メソッドの窓に付けて、入力メソッド用のダイアログとして出す (権限は要らない)
        params.token = service.window.window?.attributes?.token
        params.type = WindowManager.LayoutParams.TYPE_INPUT_METHOD_DIALOG
        params.gravity = Gravity.TOP or Gravity.START
        params.width = WindowManager.LayoutParams.WRAP_CONTENT
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        window.attributes = params
        window.setBackgroundDrawable(null)
        // 入力欄からフォーカスを奪わない。タップは受ける (候補を選べるように)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        )
        dialog = d
        return d
    }

    private fun place(d: Dialog, a: Anchor) {
        val window = d.window ?: return
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
        val params = window.attributes
        params.x = x
        params.y = y
        params.width = w
        params.height = h
        window.attributes = params
    }

    private fun button(text: String, target: ConvertTarget) = label().apply {
        this.text = text
        setTextColor(Color.rgb(0x1A, 0x5F, 0xB4))
        setOnClickListener { onConvert(target) }
    }

    private fun label() = TextView(service).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(Color.BLACK)
        setPadding(dp(10), dp(4), dp(10), dp(4))
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), service.resources.displayMetrics).toInt()
}
