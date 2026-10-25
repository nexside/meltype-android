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
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.LinearLayout
import dev.horota.meltype.input.DigitPadKind

/**
 * 画面の一番下に出す、Q25 の物理キーと同じ見た目の数字行。
 * 黒い台に、細い銀の筋と、ほとんど角が丸くないキーキャップを一列に並べる。
 */
class DigitPad(
    context: Context,
    private val onInsert: (String) -> Unit,
    private val onBackspace: () -> Unit,
) : LinearLayout(context) {
    private var kind = DigitPadKind.NONE

    val isActive: Boolean
        get() = kind != DigitPadKind.NONE

    init {
        orientation = VERTICAL
        setBackgroundColor(DECK)
        val rail = View(context).apply {
            setBackgroundColor(RAIL)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(1))
        }
        addView(rail)
    }

    fun hide() = setKind(DigitPadKind.NONE)

    fun setKind(next: DigitPadKind) {
        if (next == kind && (next == DigitPadKind.NONE || childCount > 1)) return
        kind = next
        while (childCount > 1) removeViewAt(childCount - 1)
        if (next == DigitPadKind.NONE) {
            visibility = GONE
            requestLayout()
            return
        }
        visibility = VISIBLE
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(4), dp(2), dp(3))
        }
        for (digit in '1'..'9') row.addView(key(digit.toString()))
        val plus = if (next == DigitPadKind.PHONE) "+" else null
        row.addView(key("0", alt = plus, longPress = plus))
        if (next == DigitPadKind.DECIMAL) row.addView(key("."))
        row.addView(key("⌫", backspace = true))
        addView(row)
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (!isActive) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), 1)
            return
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun key(label: String, alt: String? = null, longPress: String? = null, backspace: Boolean = false) =
        Keycap(context, label, alt).apply {
            layoutParams = LayoutParams(0, dp(52), 1f).apply {
                marginStart = dp(1)
                marginEnd = dp(1)
            }
            contentDescription = when {
                backspace -> "削除"
                longPress != null -> "$label、長押しで $longPress"
                else -> label
            }
            setOnClickListener {
                if (consumeLong) return@setOnClickListener
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                if (backspace) onBackspace() else onInsert(label)
            }
            if (longPress != null) {
                setOnLongClickListener {
                    consumeLong = true
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onInsert(longPress)
                    true
                }
            }
        }

    private class Keycap(context: Context, private val label: String, private val alt: String?) : View(context) {
        var consumeLong = false
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }
        private val altPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0xC8, 0xC8, 0xC8)
            textAlign = Paint.Align.CENTER
        }

        override fun setPressed(pressed: Boolean) {
            super.setPressed(pressed)
            // 長押しのあとのクリックは、指を離した直後に届く。フラグはそれから消す。
            if (!pressed && consumeLong) post { consumeLong = false }
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val inset = dp(1).toFloat()
            val top = if (isPressed) 0xFF4A4A4A.toInt() else 0xFF3A3A3A.toInt()
            val bottom = if (isPressed) 0xFF2A2A2A.toInt() else 0xFF1A1A1A.toInt()
            fill.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), top, bottom, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(inset, inset, width - inset, height - inset, dp(2).toFloat(), dp(2).toFloat(), fill)
            glyph.textSize = dp(18).toFloat()
            val baseline = height / 2f - (glyph.ascent() + glyph.descent()) / 2f + if (alt != null) dp(3) else 0
            canvas.drawText(label, width / 2f, baseline, glyph)
            if (alt != null) {
                altPaint.textSize = dp(9).toFloat()
                canvas.drawText(alt, width / 2f, dp(12).toFloat(), altPaint)
            }
        }

        private fun dp(value: Int) =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
    }

    private fun dp(value: Int) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private companion object {
        /** キーボードの黒い台。 */
        const val DECK = 0xFF121212.toInt()
        /** 画面とキーボードの境の銀の筋。 */
        const val RAIL = 0xFFB5B5B5.toInt()
    }
}
