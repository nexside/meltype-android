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
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout

/**
 * 画面の一番下に出す F1–F11 の行。物理キーボードの延長として使う。
 * 変換中だけ自動で出し、右端の画鋲のキーを押すと変換が終わっても出し続ける (ピン留め)。
 *
 * キーの見た目 (形・色) は [KeycapStyle] に集めてある。機種ごとに合わせる場合は
 * ここを差し替える (いまは数字行と同じ Q25 風)。
 */
class FKeyBar(
    context: Context,
    private val onFKey: (Int) -> Unit,
    private val onTogglePin: () -> Unit,
) : LinearLayout(context) {
    private var active = false

    /** 測る高さに含めるか。引っ込むアニメーションの間は含めたままにする。 */
    private var collapsed = true
    private var pinned = false
    private lateinit var pinKey: Keycap

    /** 調査用: タッチがバーまで届いているかを記録する (サービスが差す)。 */
    var diag: ((String) -> Unit)? = null

    val isActive: Boolean
        get() = active

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.action == android.view.MotionEvent.ACTION_DOWN) {
            diag?.invoke("fbar touch down x=${ev.x.toInt()} y=${ev.y.toInt()} bar=${width}x${height}")
        }
        return super.dispatchTouchEvent(ev)
    }

    private lateinit var railView: View
    private lateinit var railBottomView: View
    private lateinit var rowView: LinearLayout

    /** 見た目のプリセットが変わったときに、土台とキーの色を付け替える。 */
    fun restyle() {
        setBackgroundColor(KeycapStyle.DECK)
        if (::railView.isInitialized) railView.setBackgroundColor(KeycapStyle.RAIL)
        if (::railBottomView.isInitialized) railBottomView.setBackgroundColor(KeycapStyle.RAIL)
        if (::rowView.isInitialized) {
            // キーの間は実機のフレット (銀) なので、行の地も銀にする。
            rowView.setBackgroundColor(KeycapStyle.DECK)
            for (i in 0 until rowView.childCount) rowView.getChildAt(i).invalidate()
        }
        invalidate()
    }

    init {
        orientation = VERTICAL
        // 下端がシステムの領域に少し食われて下の帯が見切れる機種があるので、下に余白を持つ。
        setPadding(0, 0, 0, dp(4))
        setBackgroundColor(KeycapStyle.DECK)
        val rail = View(context).apply {
            setBackgroundColor(KeycapStyle.RAIL)
            // 実機のフレットに合わせて、帯の左右端をキーの描画端 (余白2+マージン1+内側1) に揃える。
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(2)).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            }
        }
        railView = rail
        addView(rail)
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(KeycapStyle.DECK)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            }
        }
        val codes = listOf(
            KeyEvent.KEYCODE_F1, KeyEvent.KEYCODE_F2, KeyEvent.KEYCODE_F3, KeyEvent.KEYCODE_F4,
            KeyEvent.KEYCODE_F5, KeyEvent.KEYCODE_F6, KeyEvent.KEYCODE_F7, KeyEvent.KEYCODE_F8,
            KeyEvent.KEYCODE_F9, KeyEvent.KEYCODE_F10, KeyEvent.KEYCODE_F11,
        )
        for ((i, code) in codes.withIndex()) {
            row.addView(key("F${i + 1}") {
                onFKey(code)
            })
        }
        pinKey = key("", icon = true) {
            onTogglePin()
        }
        row.addView(pinKey)
        rowView = row
        addView(row)
        // 実機のキーボードの上下にある銀の帯 (フレット) に合わせて、下にも帯を入れる。
        val railBottom = View(context).apply {
            setBackgroundColor(KeycapStyle.RAIL)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(2)).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            }
        }
        railBottomView = railBottom
        addView(railBottom)
        visibility = GONE
    }

    /** 出す/隠すと、固定の状態をまとめて反映する。出るときは下からせり上がり、引っ込むときは下へ滑る。 */
    fun setActive(show: Boolean, pinnedNow: Boolean) {
        pinned = pinnedNow
        pinKey.marked = pinnedNow
        pinKey.contentDescription = if (pinnedNow) "ピン留め中。押すと外す" else "ピン留めする"
        if (show == active) {
            pinKey.invalidate()
            return
        }
        active = show
        animate().cancel()
        if (show) {
            collapsed = false
            visibility = VISIBLE
            requestLayout()
            post {
                if (!active) return@post
                val h = height.toFloat()
                if (h <= 0f) return@post
                translationY = h
                animate()
                    .translationY(0f)
                    .setDuration(ENTER_MS)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
            }
        } else {
            val h = height.toFloat()
            if (h <= 0f) {
                collapsed = true
                visibility = GONE
                requestLayout()
                return
            }
            animate()
                .translationY(h)
                .setDuration(EXIT_MS)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction {
                    if (!active) {
                        collapsed = true
                        visibility = GONE
                        translationY = 0f
                        requestLayout()
                    }
                }
                .start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (collapsed) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), 1)
            return
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun key(label: String, icon: Boolean = false, onClick: () -> Unit) = Keycap(context, label, icon).apply {
        layoutParams = LayoutParams(0, dp(44), 1f).apply {
            marginStart = dp(1)
            marginEnd = dp(1)
        }
        contentDescription = label
        setOnClickListener { onClick() }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private companion object {
        const val ENTER_MS = 200L
        const val EXIT_MS = 150L
    }

    private class Keycap(context: Context, private val label: String, private val icon: Boolean = false) : View(context) {
        /** ピン留め中など、押しっぱなしに見せたいときの印。 */
        var marked = false

        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            // 実機の刻印に寄せて中太にする (通常体だと白抜きに、太字だと塗りつぶしに見える)。
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        override fun onDraw(canvas: Canvas) {
            val inset = dp(1).toFloat()
            val top: Int
            val bottom: Int
            if (isPressed || marked) {
                top = KeycapStyle.KEY_TOP_ON
                bottom = KeycapStyle.KEY_BOTTOM_ON
            } else {
                top = KeycapStyle.KEY_TOP
                bottom = KeycapStyle.KEY_BOTTOM
            }
            fill.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), top, bottom, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(inset, inset, width - inset, height - inset, dp(KeycapStyle.CORNER_DP).toFloat(), dp(KeycapStyle.CORNER_DP).toFloat(), fill)
            if (icon) {
                drawPin(canvas)
                return
            }
            glyph.color = if (marked) KeycapStyle.GLYPH_MARKED else KeycapStyle.GLYPH
            glyph.textSize = dp(if (label.length > 2) 11 else 13).toFloat()
            val baseline = height / 2f - (glyph.ascent() + glyph.descent()) / 2f
            canvas.drawText(label, width / 2f, baseline, glyph)
        }

        /**
         * 画鋲 (ピン留め) の絵。通常は受けの線の上に浮いていて、
         * ピン留め中は色が変わり、頭が受けに載るまで下がって、針は受けの中に埋まる。
         * ザクッと感は、頭の上の衝撃の印と、刺さった所で受けの線が少しへこむことで出す。
         */
        private fun drawPin(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            iconPaint.color = if (marked) KeycapStyle.GLYPH_MARKED else KeycapStyle.GLYPH
            if (marked) {
                // 針 (受けの線に隠れる部分も含めて先に描く)
                val needle = android.graphics.Path().apply {
                    moveTo(w * 0.44f, h * 0.68f)
                    lineTo(w * 0.56f, h * 0.68f)
                    lineTo(w * 0.50f, h * 0.88f)
                    close()
                }
                canvas.drawPath(needle, iconPaint)
                // 受けの線 (刺さった所が少しへこむ。太い線で折って描く)
                val board = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    color = iconPaint.color
                    strokeWidth = h * 0.055f
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                val boardPath = android.graphics.Path().apply {
                    moveTo(w * 0.32f, h * 0.765f)
                    lineTo(w * 0.465f, h * 0.765f)
                    lineTo(w * 0.50f, h * 0.815f)
                    lineTo(w * 0.535f, h * 0.765f)
                    lineTo(w * 0.68f, h * 0.765f)
                }
                canvas.drawPath(boardPath, board)
                // 頭 (受けに載る高さ、少し大きめ)
                canvas.drawRoundRect(w * 0.26f, h * 0.50f, w * 0.74f, h * 0.72f, dp(2).toFloat(), dp(2).toFloat(), iconPaint)
                // 衝撃の印 (頭の上3本)
                val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    color = iconPaint.color
                    strokeWidth = h * 0.045f
                    strokeCap = Paint.Cap.ROUND
                }
                canvas.drawLine(w * 0.50f, h * 0.40f, w * 0.50f, h * 0.26f, tick)
                canvas.drawLine(w * 0.33f, h * 0.44f, w * 0.26f, h * 0.32f, tick)
                canvas.drawLine(w * 0.67f, h * 0.44f, w * 0.74f, h * 0.32f, tick)
            } else {
                canvas.drawRoundRect(w * 0.30f, h * 0.10f, w * 0.70f, h * 0.28f, dp(2).toFloat(), dp(2).toFloat(), iconPaint)
                val needle = android.graphics.Path().apply {
                    moveTo(w * 0.42f, h * 0.28f)
                    lineTo(w * 0.58f, h * 0.28f)
                    lineTo(w * 0.50f, h * 0.55f)
                    close()
                }
                canvas.drawPath(needle, iconPaint)
                iconPaint.alpha = 140
                canvas.drawRoundRect(w * 0.32f, h * 0.74f, w * 0.68f, h * 0.80f, dp(1).toFloat(), dp(1).toFloat(), iconPaint)
                iconPaint.alpha = 255
            }
        }

        private fun dp(value: Int): Int =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
    }
}

/** キーキャップの見た目。プリセットを切り替えて機種のキーボードに寄せる。 */
object KeycapStyle {
    const val PRESET_DARK = 1
    const val PRESET_Q25_BLUE = 2

    var preset = PRESET_DARK
        private set

    var DECK = 0xFF121212.toInt()
        private set
    var RAIL = 0xFFB5B5B5.toInt()
        private set
    var KEY_TOP = 0xFF3A3A3A.toInt()
        private set
    var KEY_BOTTOM = 0xFF1A1A1A.toInt()
        private set
    var KEY_TOP_ON = 0xFF4A4A4A.toInt()
        private set
    var KEY_BOTTOM_ON = 0xFF2A2A2A.toInt()
        private set
    var GLYPH = Color.WHITE
        private set
    var GLYPH_MARKED = 0xFF8AB4F8.toInt()
        private set
    var GLYPH_SUB = 0xFFC8C8C8.toInt()
        private set
    const val CORNER_DP = 2

    /** プリセットを適用する。変わったら true。 */
    fun apply(newPreset: Int): Boolean {
        if (newPreset == preset) return false
        preset = newPreset
        when (newPreset) {
            PRESET_Q25_BLUE -> {
                // Q25 (青) のキーに寄せる: 沈んだ紺のキー、白文字、仕切りはフレットの銀。
                // 画面は発光するので、実物のキーと同じ見え方にするには一段暗めに振る。
                DECK = 0xFF030B1F.toInt()
                RAIL = 0xFFC9D2DE.toInt()
                KEY_TOP = 0xFF0C2A57.toInt()
                KEY_BOTTOM = 0xFF040F26.toInt()
                KEY_TOP_ON = 0xFF040F26.toInt()
                KEY_BOTTOM_ON = 0xFF14386E.toInt()
                GLYPH = Color.WHITE
                GLYPH_MARKED = 0xFFA9CDFF.toInt()
                GLYPH_SUB = 0xFFB9C8DE.toInt()
            }
            else -> {
                DECK = 0xFF121212.toInt()
                RAIL = 0xFFB5B5B5.toInt()
                KEY_TOP = 0xFF3A3A3A.toInt()
                KEY_BOTTOM = 0xFF1A1A1A.toInt()
                KEY_TOP_ON = 0xFF4A4A4A.toInt()
                KEY_BOTTOM_ON = 0xFF2A2A2A.toInt()
                GLYPH = Color.WHITE
                GLYPH_MARKED = 0xFF8AB4F8.toInt()
                GLYPH_SUB = 0xFFC8C8C8.toInt()
            }
        }
        return true
    }
}
