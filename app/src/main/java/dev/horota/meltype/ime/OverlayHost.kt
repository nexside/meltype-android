// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import android.content.Context
import android.graphics.Rect
import android.graphics.Region
import android.view.View
import android.widget.FrameLayout

/**
 * 入力メソッドの窓そのもの (画面と同じ大きさの透明な窓)。候補・絵文字・モード表示は、この上に子 View として画面の座標で置く。
 * ダイアログの窓を別に出さないので、端末ごとの窓の位置のずれが起きない。
 * 触れるのは「触れる」と指定した子 View の範囲だけ (それ以外のタップは下のアプリに届く)。
 */
class OverlayHost(context: Context) : FrameLayout(context) {
    private class Entry(val touchable: Boolean, var screenX: Int, var screenY: Int)

    private val entries = LinkedHashMap<View, Entry>()
    private val location = IntArray(2)

    init {
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> reapply() }
    }

    /** 画面の座標 (x, y) に、幅 w・高さ h で view を置く。 */
    fun place(view: View, touchable: Boolean, x: Int, y: Int, w: Int, h: Int) {
        entries[view] = Entry(touchable, x, y)
        val params = (view.layoutParams as? LayoutParams) ?: LayoutParams(w, h)
        params.width = w
        params.height = h
        if (view.parent !== this) {
            (view.parent as? android.view.ViewGroup)?.removeView(view)
            addView(view, params)
        } else {
            view.layoutParams = params
        }
        apply(view)
        view.visibility = VISIBLE
    }

    fun remove(view: View) {
        entries.remove(view)
        if (view.parent === this) removeView(view)
    }

    /** 触れる子 View の範囲 (この窓の座標) を region に入れる。 */
    fun fillTouchableRegion(region: Region) {
        region.setEmpty()
        val rect = Rect()
        for ((view, entry) in entries) {
            if (!entry.touchable || view.parent !== this || view.visibility != VISIBLE) continue
            rect.set(view.left, view.top, view.right, view.bottom)
            region.union(rect)
        }
    }

    private fun reapply() {
        for (view in entries.keys.toList()) apply(view)
    }

    private fun apply(view: View) {
        val entry = entries[view] ?: return
        getLocationOnScreen(location)
        val params = view.layoutParams as? LayoutParams ?: return
        val left = entry.screenX - location[0]
        val top = entry.screenY - location[1]
        if (params.leftMargin != left || params.topMargin != top || params.gravity != (android.view.Gravity.TOP or android.view.Gravity.START)) {
            params.gravity = android.view.Gravity.TOP or android.view.Gravity.START
            params.leftMargin = left
            params.topMargin = top
            view.layoutParams = params
        }
    }
}

/** OverlayHost を持っている入力メソッド。 */
interface OverlayOwner {
    val overlay: OverlayHost
}
