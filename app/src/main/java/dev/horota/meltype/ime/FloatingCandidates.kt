// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.util.TypedValue
import android.view.Gravity
import android.view.View
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
    /** 候補を候補の区切りで折り返して並べる箱 (縦に最大 MAX_ROWS 行)。 */
    private val listBox = LinearLayout(service).apply { orientation = LinearLayout.VERTICAL }
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
        addView(listBox)
        addView(convertButtons)
        addView(emojiButton)
        addView(registerButton)
    }

    /** 従来の置き場所 (表示専用で触れない)。専用窓が出せないときの予備。 */
    private val window = ImeDialog({ service as OverlayOwner }, row, touchable = true)

    /** 候補の範囲だけの専用窓 (中身が押せる)。IME の窓に付けるダイアログで、窓の外のタップは下のアプリに届く。 */
    private var ownDialog: android.app.Dialog? = null
    private var ownShown = false

    private companion object {
        /** 候補の折り返しの最大行数 (変換中の窓)。 */
        const val MAX_ROWS = 2
    }

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

        convertButtons.visibility = if (view.text.isNotEmpty()) View.VISIBLE else View.GONE
        emojiButton.visibility = if (hasEmoji) View.VISIBLE else View.GONE
        emojiButton.text = if (emojiOnly) "全部" else "絵文字"
        registerButton.visibility = if (view.converting) View.VISIBLE else View.GONE

        buildCandidateRows(view, items)
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
        dismissOwn()
        window.dismiss()
    }

    /** 配色を変える。いま出ている候補は、次に出し直すときから新しい色になる。 */
    fun applyTheme(newTheme: CandidateTheme) {
        theme = newTheme
        row.background = rowBackground()
        readingLabel.setTextColor(newTheme.text)
        for (button in listOf(emojiButton, registerButton)) styleActionButton(button)
        for (i in 0 until convertButtons.childCount) (convertButtons.getChildAt(i) as? TextView)?.let { styleActionButton(it) }
    }

    /** 押せる文字ボタンの見た目 (枠つきの地、押したときは反転。文字色も連動させる)。 */
    private fun styleActionButton(button: TextView) {
        button.setTextColor(
            android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
                intArrayOf(theme.selectedText, theme.accent),
            ),
        )
        button.background = android.graphics.drawable.StateListDrawable().apply {
            val pressed = GradientDrawable().apply {
                setColor(theme.selectedBackground)
                cornerRadius = dp(6).toFloat()
            }
            val normal = GradientDrawable().apply {
                setColor(theme.background)
                cornerRadius = dp(6).toFloat()
                setStroke(dp(1), theme.border)
            }
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    private fun rowBackground() = GradientDrawable().apply {
        setColor(theme.background)
        cornerRadius = dp(8).toFloat()
        setStroke(dp(1), theme.border)
    }

    /**
     * 候補を横 1 列に押し込まず、候補の区切りで折り返して最大 MAX_ROWS 行で並べる。
     * 全部が入り切らないときは、選んでいる候補の行が必ず見える範囲だけを出す
     * (選択が移れば show が呼ばれ直して表示範囲も移る)。
     */
    private fun buildCandidateRows(view: CompositionView, items: List<String>) {
        listBox.removeAllViews()
        val selected = if (view.candidates.isNotEmpty()) view.selectedIndex else view.selectedPrediction
        val order = if (emojiOnly) EmojiData.emojiOrder(view.candidates, recent()) else items.indices.toList()
        if (order.isEmpty()) return

        // 候補の並びに使える幅 = 窓の最大幅 - (読みラベルと右側のボタンの幅)
        val metrics = service.resources.displayMetrics
        var reserved = 0
        val unspec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        if (readingLabel.visibility == View.VISIBLE) {
            readingLabel.measure(unspec, unspec)
            reserved += readingLabel.measuredWidth
        }
        if (convertButtons.visibility == View.VISIBLE) {
            convertButtons.measure(unspec, unspec)
            reserved += convertButtons.measuredWidth
        }
        if (emojiButton.visibility == View.VISIBLE) {
            emojiButton.measure(unspec, unspec)
            reserved += emojiButton.measuredWidth
        }
        if (registerButton.visibility == View.VISIBLE) {
            registerButton.measure(unspec, unspec)
            reserved += registerButton.measuredWidth
        }
        val available = (metrics.widthPixels - dp(8) - reserved).coerceAtLeast(dp(80))

        // 幅を測って行に割る (ラベルの実寸は label() の文字サイズと余白から)
        val probe = label()
        val widths = order.map { probe.paint.measureText(items[it]).toInt() + dp(20) }
        val rows = mutableListOf<MutableList<Int>>()
        var current = mutableListOf<Int>()
        var currentWidth = 0
        for (pos in order.indices) {
            val w = widths[pos]
            if (current.isNotEmpty() && currentWidth + w > available) {
                rows += current
                current = mutableListOf()
                currentWidth = 0
            }
            current += pos
            currentWidth += w
        }
        if (current.isNotEmpty()) rows += current

        // 見せる行の範囲: 選んでいる候補の行を含み、最大 MAX_ROWS 行
        val selectedPos = order.indexOf(selected).coerceAtLeast(0)
        val selectedRow = rows.indexOfFirst { selectedPos in it }.coerceAtLeast(0)
        val startRow = minOf(selectedRow, (rows.size - MAX_ROWS).coerceAtLeast(0))
        val endRow = minOf(startRow + MAX_ROWS, rows.size)
        for (r in startRow until endRow) {
            val rowLayout = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL }
            for (pos in rows[r]) {
                val index = order[pos]
                rowLayout.addView(label().apply {
                    text = items[index]
                    if (index == selected) {
                        setBackgroundColor(theme.selectedBackground)
                        setTextColor(theme.selectedText)
                    }
                    if (view.candidates.isNotEmpty()) setOnClickListener { (service as? MeltypeImeService)?.vibrateVirtualKey(); onSelect(index) }
                })
            }
            listBox.addView(rowLayout)
        }
    }

    private fun place(): Boolean {
        val metrics = service.resources.displayMetrics
        row.measure(
            View.MeasureSpec.makeMeasureSpec(metrics.widthPixels - dp(8), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.AT_MOST),
        )
        // 候補は最大 MAX_ROWS 行。大きく測れてしまっても、画面を覆わないように切る
        val w = minOf(row.measuredWidth, metrics.widthPixels - dp(8))
        val h = minOf(row.measuredHeight, dp(100))
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
        return showAt(x, y, w, h)
    }

    /** 専用窓で出す。IME の窓のトークンが取れないときだけ、従来の置き場所 (触れない) に逃がす。 */
    private fun showAt(x: Int, y: Int, w: Int, h: Int): Boolean {
        if (showOwn(x, y, w, h)) return true
        dismissOwn()
        return window.show(x, y, w, h)
    }

    private fun showOwn(x: Int, y: Int, w: Int, h: Int): Boolean {
        val token = try {
            service.window?.window?.decorView?.windowToken
        } catch (e: RuntimeException) {
            null
        } ?: return false
        return try {
            window.dismiss()
            (row.parent as? android.view.ViewGroup)?.removeView(row)
            val d = ownDialog ?: android.app.Dialog(service).also {
                it.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
                ownDialog = it
            }
            d.setContentView(row)
            val win = d.window ?: return false
            val params = win.attributes
            params.type = android.view.WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
            params.token = token
            params.gravity = Gravity.TOP or Gravity.START
            params.x = x
            params.y = y
            params.width = w
            params.height = h
            params.dimAmount = 0f
            win.attributes = params
            win.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            win.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            win.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            )
            if (!d.isShowing) d.show()
            ownShown = true
            true
        } catch (e: Throwable) {
            false
        }
    }

    private fun dismissOwn() {
        if (!ownShown && ownDialog?.isShowing != true) return
        try {
            ownDialog?.dismiss()
        } catch (e: RuntimeException) {
            // 窓がもう無いときは何もしない
        }
        ownShown = false
    }

    private fun actionButton(text: String, onClick: () -> Unit) = label().apply {
        this.text = text
        styleActionButton(this)
        setOnClickListener {
            (service as? MeltypeImeService)?.vibrateVirtualKey()
            onClick()
        }
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
