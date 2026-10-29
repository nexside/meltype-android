// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

/**
 * Q25 の Alt / Sym 面。端末の Q25_keyboard.kcm の alt と同じ並び。
 * 大きい文字が Alt で入る記号、小さい文字がどのキーか。
 */
class SymbolLegend(context: Context) : LinearLayout(context) {
    /** キーがタップされたとき (文字キー, 記号)。サービスが物理キーと同じ入口へ流す。 */
    var onSymbol: ((String, String) -> Unit)? = null

    private lateinit var railTop: View
    private lateinit var railBottom: View

    private val rows = mutableListOf<LinearLayout>()
    private val frets = mutableListOf<View>()

    /** 見た目のプリセットに合わせて、土台と帯の色を付け替える (キーは描画時に色を読む)。 */
    fun restyle() {
        setBackgroundColor(KeycapStyle.DECK)
        if (::railTop.isInitialized) railTop.setBackgroundColor(KeycapStyle.RAIL)
        if (::railBottom.isInitialized) railBottom.setBackgroundColor(KeycapStyle.RAIL)
        // フレットは行と行の間の細い線だけ。行の地 (キーの台) は実機と同じく暗くする。
        for (line in rows) line.setBackgroundColor(KeycapStyle.DECK)
        for (fret in frets) fret.setBackgroundColor(KeycapStyle.RAIL)
    }

    private fun rail() = View(context).apply {
        setBackgroundColor(KeycapStyle.RAIL)
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(2)).apply {
            marginStart = dp(2)
            marginEnd = dp(2)
        }
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(KeycapStyle.DECK)
        val railT = rail()
        railTop = railT
        addView(railT)
        val pad = dp(3)
        setPadding(dp(2), pad, dp(2), pad)
        addView(row(TOP, side = 0f))
        addView(fret())
        addView(row(HOME, side = 0.5f))
        addView(fret())
        addView(row(BOTTOM, side = 1.5f))
        val railB = rail()
        railBottom = railB
        addView(railB)
    }

    /** 行と行の間のフレット (細い銀の線)。 */
    private fun fret(): View {
        val strip = rail()
        frets.add(strip)
        return strip
    }

    private fun row(keys: List<Pair<String, String>>, side: Float): LinearLayout {
        val line = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(KeycapStyle.DECK)
        }
        rows.add(line)
        if (side > 0f) line.addView(spacer(side))
        for ((letter, symbol) in keys) line.addView(cap(letter, symbol))
        if (side > 0f) line.addView(spacer(side))
        return line
    }

    private fun spacer(weight: Float) = View(context).apply {
        layoutParams = LayoutParams(0, 0, weight)
    }

    private fun cap(letter: String, symbol: String) = Cap(context, letter, symbol).apply {
        layoutParams = LayoutParams(0, dp(44), 1f).apply {
            topMargin = dp(2)
            bottomMargin = dp(2)
            marginStart = dp(1)
            marginEnd = dp(1)
        }
        setOnClickListener {
            onSymbol?.invoke(letter, symbol)
        }
    }

    private class Cap(context: Context, private val letter: String, private val symbol: String) : View(context) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        private val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0xC8, 0xC8, 0xC8)
            textAlign = Paint.Align.CENTER
        }

        override fun onDraw(canvas: Canvas) {
            val inset = dp(1).toFloat()
            fill.shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                if (isPressed) KeycapStyle.KEY_TOP_ON else KeycapStyle.KEY_TOP,
                if (isPressed) KeycapStyle.KEY_BOTTOM_ON else KeycapStyle.KEY_BOTTOM,
                Shader.TileMode.CLAMP,
            )
            canvas.drawRoundRect(inset, inset, width - inset, height - inset, dp(2).toFloat(), dp(2).toFloat(), fill)
            letterPaint.color = KeycapStyle.GLYPH_SUB
            symbolPaint.color = KeycapStyle.GLYPH
            letterPaint.textSize = dp(9).toFloat()
            canvas.drawText(letter, width / 2f, dp(12).toFloat(), letterPaint)
            symbolPaint.textSize = dp(18).toFloat()
            val baseline = height / 2f - (symbolPaint.ascent() + symbolPaint.descent()) / 2f + dp(4)
            canvas.drawText(symbol, width / 2f, baseline, symbolPaint)
        }

        private fun dp(value: Int) =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
    }

    private fun dp(value: Int) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private companion object {
        val TOP = listOf(
            "q" to "#", "w" to "1", "e" to "2", "r" to "3", "t" to "(",
            "y" to ")", "u" to "_", "i" to "-", "o" to "+", "p" to "@",
        )
        val HOME = listOf(
            "a" to "*", "s" to "4", "d" to "5", "f" to "6", "g" to "/",
            "h" to ":", "j" to ";", "k" to "'", "l" to "\"",
        )
        val BOTTOM = listOf(
            "z" to "7", "x" to "8", "c" to "9", "v" to "?", "b" to "!", "n" to ",", "m" to ".",
        )
    }
}
