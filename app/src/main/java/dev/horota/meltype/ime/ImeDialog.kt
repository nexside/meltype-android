// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import kotlin.math.abs

/**
 * 入力メソッドの窓に付ける小さな窓 (候補・モード表示)。画面の座標で位置を指定する。
 * 付ける窓の種類は、AOSP の LatinIME がダイアログに使っている TYPE_APPLICATION_ATTACHED_DIALOG (権限は要らない)。
 * 位置の基準 (画面か、付けた窓か) は端末で違うことがあるので、出したあとに実際の位置を測って合わせる。
 */
class ImeDialog(
    private val service: InputMethodService,
    private val content: View,
    private val touchable: Boolean,
) {
    private var dialog: Dialog? = null
    private var offsetX = 0
    private var offsetY = 0
    private var width = 0
    private var height = 0

    /** 画面の (x, y) に、幅 w・高さ h で出す。出せなければ false。 */
    fun show(x: Int, y: Int, w: Int, h: Int): Boolean =
        try {
            val d = dialog ?: create() ?: return false
            width = w
            height = h
            layoutAt(d, x, y)
            if (!d.isShowing) d.show()
            content.post { calibrate(x, y) }
            true
        } catch (e: RuntimeException) {
            dismiss()
            false
        }

    fun dismiss() {
        try {
            dialog?.dismiss()
        } catch (e: RuntimeException) {
            // 窓がもう無いときは何もしない
        }
        dialog = null
    }

    private fun create(): Dialog? {
        (content.parent as? ViewGroup)?.removeView(content)
        val d = Dialog(service)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.setContentView(content)
        val window = d.window ?: return null
        val params = window.attributes
        params.token = service.window.window?.attributes?.token
        params.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
        params.gravity = Gravity.TOP or Gravity.START
        window.attributes = params
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        // 入力欄からフォーカスを奪わない。touchable ならタップは受ける (候補を選べるように)
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        window.addFlags(flags)
        dialog = d
        return d
    }

    private fun layoutAt(d: Dialog, x: Int, y: Int) {
        val window = d.window ?: return
        val params = window.attributes
        params.x = x + offsetX
        params.y = y + offsetY
        params.width = width
        params.height = height
        window.attributes = params
    }

    /** 実際に出た位置を測って、ずれていれば直す。 */
    private fun calibrate(x: Int, y: Int) {
        val d = dialog ?: return
        if (!d.isShowing) return
        val location = IntArray(2)
        content.getLocationOnScreen(location)
        val dx = x - location[0]
        val dy = y - location[1]
        if (abs(dx) > 1 || abs(dy) > 1) {
            offsetX += dx
            offsetY += dy
            layoutAt(d, x, y)
        }
    }
}
