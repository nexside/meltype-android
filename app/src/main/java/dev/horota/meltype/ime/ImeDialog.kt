// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import android.view.View

/**
 * 画面の座標で位置を指定する小さな浮かぶ表示 (候補・モード表示・絵文字パネル)。
 * 入力メソッドの窓に付けた、画面全体の透明な窓 (OverlayHost) の上に View として置く。
 * その窓はタップを受けない (下のアプリにそのまま届く)。
 */
class ImeDialog(
    private val owner: () -> OverlayOwner,
    private val content: View,
    @Suppress("unused") private val touchable: Boolean,
) {
    /** 画面の (x, y) に、幅 w・高さ h で出す。出せなければ false。 */
    fun show(x: Int, y: Int, w: Int, h: Int): Boolean {
        val o = owner()
        if (!o.ensureOverlay()) return false
        o.overlay.place(content, false, x, y, w, h)
        return true
    }

    fun dismiss() {
        val o = owner()
        o.overlay.remove(content)
        o.releaseOverlayIfEmpty()
    }
}
