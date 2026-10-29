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
    private var angle = 0f
    private var ticks = 0
    private val tick: Runnable = object : Runnable {
        override fun run() {
            // 光の弧は滑らかに回し、声の波はゆっくり切り替える。
            angle = (angle + 12f) % 360f
            ticks++
            if (ticks % 6 == 0) phase = (phase + 1) % 3
            invalidate()
            postDelayed(this, 40)
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
        // 色はキー行と同じプリセットに合わせる (円盤=台、輪=銀の帯、人と波=文字色)。
        fill.color = KeycapStyle.DECK
        canvas.drawCircle(cx, cy, radius * 0.96f, fill)
        fill.color = KeycapStyle.RAIL
        fill.style = Paint.Style.STROKE
        fill.strokeWidth = radius * 0.035f
        canvas.drawCircle(cx, cy, radius * 0.94f, fill)
        fill.style = Paint.Style.FILL

        // 光る弧が円盤の縁を回る (にじみ + 本体の二重描きで光って見せる)。
        val glow = KeycapStyle.GLYPH_MARKED
        val ringR = radius * 0.94f
        box.set(cx - ringR, cy - ringR, cx + ringR, cy + ringR)
        wave.color = Color.argb(0x40, Color.red(glow), Color.green(glow), Color.blue(glow))
        wave.strokeWidth = radius * 0.11f
        canvas.drawArc(box, angle, 110f, false, wave)
        wave.color = glow
        wave.strokeWidth = radius * 0.05f
        canvas.drawArc(box, angle, 110f, false, wave)
        fill.color = KeycapStyle.GLYPH

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
        val glyph = KeycapStyle.GLYPH
        val dim = Color.argb(0x55, Color.red(glyph), Color.green(glyph), Color.blue(glyph))
        for (i in 0..2) {
            val reach = radius * (0.16f + i * 0.11f)
            wave.color = if (i <= phase) glyph else dim
            box.set(mouthX, mouthY - reach, mouthX + reach * 2f, mouthY + reach)
            canvas.drawArc(box, -50f, 100f, false, wave)
        }
    }
}
