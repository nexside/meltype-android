// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import android.view.View

/**
 * 画面の座標で位置を指定する小さな浮かぶ表示 (候補・モード表示・絵文字パネル)。
 * 別の窓 (ダイアログ) は出さず、入力メソッドの窓 (OverlayHost) の上に View として置く。
 * touchable が false の表示は、タップを素通しする。
 */
class ImeDialog(
    private val host: () -> OverlayHost,
    private val content: View,
    private val touchable: Boolean,
) {
    /** 画面の (x, y) に、幅 w・高さ h で出す。出せなければ false。 */
    fun show(x: Int, y: Int, w: Int, h: Int): Boolean {
        val overlay = host()
        if (!overlay.isAttachedToWindow) return false
        overlay.place(content, touchable, x, y, w, h)
        return true
    }

    fun dismiss() {
        host().remove(content)
    }
}
