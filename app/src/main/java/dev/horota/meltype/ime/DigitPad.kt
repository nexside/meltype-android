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

    private lateinit var railTop: View
    private lateinit var railBottom: View

    /** 見た目のプリセットが変わったときに、土台と帯の色を付け替える (キーは描画時に色を読む)。 */
    fun restyle() {
        setBackgroundColor(KeycapStyle.DECK)
        if (::railTop.isInitialized) railTop.setBackgroundColor(KeycapStyle.RAIL)
        if (::railBottom.isInitialized) railBottom.setBackgroundColor(KeycapStyle.RAIL)
        // 行の地 (キーの台) は実機と同じく暗くする。銀は上下の帯だけ。
        for (i in 0 until childCount) {
            (getChildAt(i) as? LinearLayout)?.setBackgroundColor(KeycapStyle.DECK)
        }
    }

    init {
        orientation = VERTICAL
        setPadding(0, 0, 0, dp(4))
        setBackgroundColor(KeycapStyle.DECK)
        val rail = View(context).apply {
            setBackgroundColor(KeycapStyle.RAIL)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(2)).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            }
        }
        railTop = rail
        addView(rail)
        val railB = View(context).apply {
            setBackgroundColor(KeycapStyle.RAIL)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(2)).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            }
        }
        railBottom = railB
        addView(railB)
    }

    fun hide() = setKind(DigitPadKind.NONE)

    fun setKind(next: DigitPadKind) {
        if (next == kind && (next == DigitPadKind.NONE || childCount > 2)) return
        kind = next
        while (childCount > 2) removeViewAt(1)
        if (next == DigitPadKind.NONE) {
            visibility = GONE
            requestLayout()
            return
        }
        visibility = VISIBLE
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(KeycapStyle.DECK)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            }
        }
        for (digit in '1'..'9') row.addView(key(digit.toString()))
        val plus = if (next == DigitPadKind.PHONE) "+" else null
        row.addView(key("0", alt = plus, longPress = plus))
        if (next == DigitPadKind.DECIMAL) row.addView(key("."))
        row.addView(key("⌫", backspace = true))
        addView(row, childCount - 1)
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
                if (backspace) onBackspace() else onInsert(label)
            }
            if (longPress != null) {
                setOnLongClickListener {
                    consumeLong = true
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
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
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
            val top = if (isPressed) KeycapStyle.KEY_TOP_ON else KeycapStyle.KEY_TOP
            val bottom = if (isPressed) KeycapStyle.KEY_BOTTOM_ON else KeycapStyle.KEY_BOTTOM
            fill.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), top, bottom, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(inset, inset, width - inset, height - inset, dp(2).toFloat(), dp(2).toFloat(), fill)
            glyph.color = KeycapStyle.GLYPH
            altPaint.color = KeycapStyle.GLYPH_SUB
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
}
