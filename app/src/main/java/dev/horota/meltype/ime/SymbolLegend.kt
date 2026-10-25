// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

/**
 * Q25 の Alt / Sym 面。端末の Q25_keyboard.kcm の alt と同じ並び。
 * 大きい文字が Alt で入る記号、小さい文字がどのキーか。
 */
class SymbolLegend(context: Context) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        setBackgroundColor(DECK)
        addView(View(context).apply {
            setBackgroundColor(RAIL)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(1))
        })
        val pad = dp(3)
        setPadding(dp(2), pad, dp(2), pad)
        addView(row(TOP, side = 0f))
        addView(row(HOME, side = 0.5f))
        addView(row(BOTTOM, side = 1.5f))
    }

    private fun row(keys: List<Pair<String, String>>, side: Float): LinearLayout {
        val line = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
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
    }

    private class Cap(context: Context, private val letter: String, private val symbol: String) : View(context) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }
        private val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0xC8, 0xC8, 0xC8)
            textAlign = Paint.Align.CENTER
        }

        override fun onDraw(canvas: Canvas) {
            val inset = dp(1).toFloat()
            fill.shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                0xFF3A3A3A.toInt(), 0xFF1A1A1A.toInt(), Shader.TileMode.CLAMP,
            )
            canvas.drawRoundRect(inset, inset, width - inset, height - inset, dp(2).toFloat(), dp(2).toFloat(), fill)
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
        const val DECK = 0xFF121212.toInt()
        const val RAIL = 0xFFB5B5B5.toInt()
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
