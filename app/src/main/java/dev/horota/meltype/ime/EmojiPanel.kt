// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * 絵文字の一覧パネル。タブ (最近・カテゴリ) と、絵文字の格子。入力カーソルのすぐ下 (入らなければ上、位置が分からなければ画面の下) に出す。
 * キー: 矢印 (Q25 のトラックパッドも) で選び、Enter で入力、Tab / PageUp・PageDown でカテゴリを切り替え、Esc で閉じる。
 * タップでも選べる。ほかのキーを押すとパネルを閉じて、そのキーはそのまま入力に回る。
 */
class EmojiPanel(
    private val service: InputMethodService,
    private val anchor: () -> FloatingCandidates.Anchor?,
    private val recent: () -> List<String>,
    private val onPick: (String) -> Unit,
) {
    private var theme = CandidateTheme.LIGHT
    private var state: EmojiPanelState? = null

    private val tabRow = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL }
    private val grid = GridLayout(service).apply { columnCount = EmojiPanelState.COLUMNS }
    private val scroll = ScrollView(service).apply {
        isVerticalScrollBarEnabled = false
        addView(grid)
    }
    private val root = LinearLayout(service).apply {
        orientation = LinearLayout.VERTICAL
        background = panelBackground()
        addView(
            HorizontalScrollView(service).apply {
                isHorizontalScrollBarEnabled = false
                addView(tabRow)
            },
        )
        addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(CELL_DP * VISIBLE_ROWS)))
    }
    private val window = ImeDialog({ (service as OverlayOwner).overlay }, root, touchable = true)

    val isShowing: Boolean
        get() = state != null

    fun show() {
        state = EmojiPanelState(recent())
        render()
    }

    fun hide() {
        state = null
        window.dismiss()
    }

    fun applyTheme(newTheme: CandidateTheme) {
        theme = newTheme
        root.background = panelBackground()
        if (state != null) render()
    }

    /** パネルが出ている間のキー。使ったら true (そのキーは入力に回さない)。 */
    fun onKey(keyCode: Int, event: KeyEvent): Boolean {
        val s = state ?: return false
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> s.moveLeft()
            KeyEvent.KEYCODE_DPAD_RIGHT -> s.moveRight()
            KeyEvent.KEYCODE_DPAD_UP -> s.moveUp()
            KeyEvent.KEYCODE_DPAD_DOWN -> s.moveDown()
            KeyEvent.KEYCODE_TAB -> if (event.isShiftPressed) s.previousTab() else s.nextTab()
            KeyEvent.KEYCODE_PAGE_UP -> s.previousTab()
            KeyEvent.KEYCODE_PAGE_DOWN -> s.nextTab()
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                s.current()?.let(::pick)
                return true
            }
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK -> {
                hide()
                return true
            }
            // 修飾キーだけのときは、パネルを閉じない (Shift+Tab など)
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT,
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT,
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT,
            KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT,
            KeyEvent.KEYCODE_CAPS_LOCK,
            -> return false
            else -> {
                hide()
                return false
            }
        }
        render()
        return true
    }

    private fun pick(emoji: String) {
        hide()
        onPick(emoji)
    }

    private fun render() {
        val s = state ?: return
        tabRow.removeAllViews()
        s.tabs.forEachIndexed { index, tab ->
            tabRow.addView(
                cell(tab.name, 14f, index == s.tab).apply {
                    setOnClickListener {
                        s.selectTab(index)
                        render()
                    }
                },
            )
        }
        grid.removeAllViews()
        s.items.forEachIndexed { index, emoji ->
            grid.addView(
                cell(emoji, 24f, index == s.index).apply {
                    gravity = Gravity.CENTER
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = dp(CELL_DP)
                        height = dp(CELL_DP)
                    }
                    setOnClickListener { pick(emoji) }
                },
            )
        }
        place()
        scroll.post { keepSelectionVisible(s.index) }
    }

    private fun keepSelectionVisible(index: Int) {
        val child = grid.getChildAt(index) ?: return
        if (child.top < scroll.scrollY) {
            scroll.scrollTo(0, child.top)
        } else if (child.bottom > scroll.scrollY + scroll.height) {
            scroll.scrollTo(0, child.bottom - scroll.height)
        }
    }

    private fun place() {
        val metrics = service.resources.displayMetrics
        val maxWidth = minOf(metrics.widthPixels - dp(16), dp(CELL_DP * EmojiPanelState.COLUMNS + 8))
        root.measure(
            View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.AT_MOST),
        )
        val w = root.measuredWidth
        val h = root.measuredHeight
        val a = anchor()
        val x: Int
        val y: Int
        if (a != null) {
            x = a.x.toInt().coerceIn(0, maxOf(0, metrics.widthPixels - w))
            y = if (a.bottom + dp(4) + h <= metrics.heightPixels) (a.bottom + dp(4)).toInt() else maxOf(0, (a.top - h - dp(4)).toInt())
        } else {
            x = (metrics.widthPixels - w) / 2
            y = maxOf(0, metrics.heightPixels - h - dp(64))
        }
        window.show(x, y, w, h)
    }

    private fun cell(text: String, sp: Float, selected: Boolean) = TextView(service).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(if (selected) theme.selectedText else theme.text)
        setPadding(dp(10), dp(4), dp(10), dp(4))
        if (selected) setBackgroundColor(theme.selectedBackground)
    }

    private fun panelBackground() = GradientDrawable().apply {
        setColor(theme.background)
        cornerRadius = dp(8).toFloat()
        setStroke(dp(1), theme.border)
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), service.resources.displayMetrics).toInt()

    private companion object {
        const val CELL_DP = 40
        const val VISIBLE_ROWS = 5
    }
}
