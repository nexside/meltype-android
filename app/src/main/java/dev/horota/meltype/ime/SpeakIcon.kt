// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** 音声入力中に出す、人が話している印。声の波は順に濃くなる。 */
class SpeakIcon(context: Context) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wave = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val box = RectF()
    private var phase = 0
    private val tick: Runnable = object : Runnable {
        override fun run() {
            phase = (phase + 1) % 3
            invalidate()
            postDelayed(this, 260)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        removeCallbacks(tick)
        post(tick)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) / 2f
        fill.color = Color.WHITE
        canvas.drawCircle(cx, cy, radius * 0.96f, fill)
        fill.color = RING
        fill.style = Paint.Style.STROKE
        fill.strokeWidth = radius * 0.035f
        canvas.drawCircle(cx, cy, radius * 0.94f, fill)
        fill.style = Paint.Style.FILL
        fill.color = INK

        val personX = cx - radius * 0.16f
        val headR = radius * 0.15f
        val headY = cy - radius * 0.12f
        canvas.drawCircle(personX, headY, headR, fill)

        val shoulderW = radius * 0.30f
        val shoulderTop = headY + headR + radius * 0.05f
        box.set(personX - shoulderW, shoulderTop, personX + shoulderW, shoulderTop + radius * 0.42f)
        canvas.drawArc(box, 200f, 140f, true, fill)

        val mouthX = personX + headR * 0.55f
        val mouthY = headY + headR * 0.15f
        wave.strokeWidth = radius * 0.045f
        for (i in 0..2) {
            val reach = radius * (0.16f + i * 0.11f)
            wave.color = if (i <= phase) INK else Color.argb(0x55, 0x1A, 0x1A, 0x1A)
            box.set(mouthX, mouthY - reach, mouthX + reach * 2f, mouthY + reach)
            canvas.drawArc(box, -50f, 100f, false, wave)
        }
    }

    private companion object {
        const val INK = 0xFF1A1A1A.toInt()
        const val RING = 0xFFBDBDBD.toInt()
    }
}
