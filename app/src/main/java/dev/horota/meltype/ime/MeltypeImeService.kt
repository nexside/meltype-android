// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.TextView
import dev.horota.meltype.core.CoreBridge
import dev.horota.meltype.core.CompositionView
import dev.horota.meltype.core.CoreOpenResult
import dev.horota.meltype.input.EditorPolicy
import dev.horota.meltype.input.KeyInput
import dev.horota.meltype.settings.MeltypePrefs
import java.io.File

/**
 * v0.1: 物理キーボード専用。画面キーボードは出さず、候補バーだけ出す。
 * Android 11 以降は、表示を頼まれていない IME の窓は見えないので、候補バーを入力用の窓 (input view) として出し、
 * 文字を打ち始めたら自分で表示を頼む (requestShowSelf)。
 */
class MeltypeImeService : InputMethodService() {
    private lateinit var prefs: MeltypePrefs
    private lateinit var bar: CandidateBarView
    private lateinit var controller: ImeController
    private var core: CoreBridge? = null
    /** 入力欄が教えてくれた、入力カーソルの位置 (教えてくれない入力欄では null)。 */
    private var cursorInfo: CursorAnchorInfo? = null
    private lateinit var floating: FloatingCandidates
    private var indicator: Dialog? = null
    private val hideIndicator = Runnable { dismissIndicator() }

    override fun onCreate() {
        super.onCreate()
        prefs = MeltypePrefs(this)
        bar = CandidateBarView(
            this,
            onSelect = { index -> controller.selectCandidate(index) },
            onConvert = { target -> controller.convert(target) },
        )
        val opened = CoreBridge.open(
            MeltypePrefs.dataDirectory(this),
            File(applicationInfo.nativeLibraryDir, "libmozc_helper.so"),
        )
        when (opened) {
            is CoreOpenResult.Ready -> {
                core = opened.core
                bar.setWarning(if (opened.mozcAvailable) null else "漢字変換を使えません")
            }
            is CoreOpenResult.Failed -> bar.setWarning("Meltype を初期化できませんでした")
        }
        floating = FloatingCandidates(
            this,
            anchor = ::composingAnchor,
            onSelect = { index -> controller.selectCandidate(index) },
            onConvert = { target -> controller.convert(target) },
        )
        // 候補は、変換中の文字の上に出す。入力欄が位置を教えてくれないとき (Termux など) だけ、画面の下の候補バーに出す
        val display = object : CandidateDisplay {
            override fun show(view: CompositionView, inlineReading: Boolean) {
                if (!inlineReading && floating.show(view)) {
                    bar.hide()
                } else {
                    floating.hide()
                    bar.show(view, inlineReading)
                }
            }

            override fun hide() {
                floating.hide()
                bar.hide()
            }
        }
        controller = ImeController(core, InputConnectionSink { currentInputConnection }, display, bar)
        bar.modeFlash = ::flashAtCursor
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        cursorInfo = null
        // カーソルが動くたびに位置を教えてもらう (対応していない入力欄では何も来ない)
        currentInputConnection?.requestCursorUpdates(InputConnection.CURSOR_UPDATE_MONITOR)
    }

    override fun onUpdateCursorAnchorInfo(cursorAnchorInfo: CursorAnchorInfo) {
        cursorInfo = cursorAnchorInfo
        floating.reposition()
    }

    /** 変換中の文字の左端と、その行の上端 / 下端 (画面の座標)。分からなければ null。 */
    private fun composingAnchor(): FloatingCandidates.Anchor? {
        val info = cursorInfo ?: return null
        if ((info.insertionMarkerFlags and CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION) == 0) return null
        var left = info.insertionMarkerHorizontal
        val top = info.insertionMarkerTop
        val bottom = info.insertionMarkerBottom
        if (left.isNaN() || top.isNaN() || bottom.isNaN()) return null
        val start = info.composingTextStart
        if (info.composingText != null && start >= 0) {
            try {
                info.getCharacterBounds(start)?.let { left = it.left }
            } catch (e: RuntimeException) {
                // 文字の位置を教えてくれない入力欄では、カーソルの位置を使う
            }
        }
        val points = floatArrayOf(left, top, left, bottom)
        info.matrix.mapPoints(points) // 入力欄の座標 → 画面の座標
        return FloatingCandidates.Anchor(points[0], points[1], points[3])
    }

    /** 「あ」/「A」の切り替えを、入力カーソルのすぐ下に短く出す。位置が分からない・出せないときは false。 */
    private fun flashAtCursor(text: String): Boolean {
        val info = cursorInfo ?: return false
        if ((info.insertionMarkerFlags and CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION) == 0) return false
        val x = info.insertionMarkerHorizontal
        val y = info.insertionMarkerBottom
        if (x.isNaN() || y.isNaN()) return false
        val point = floatArrayOf(x, y)
        info.matrix.mapPoints(point) // 入力欄の座標 → 画面の座標
        return try {
            dismissIndicator()
            val label = TextView(this).apply {
                this.text = text
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(Color.WHITE)
                setPadding(dp(10), dp(4), dp(10), dp(4))
                background = GradientDrawable().apply {
                    setColor(Color.argb(0xE6, 0x33, 0x33, 0x33))
                    cornerRadius = dp(8).toFloat()
                }
            }
            val dialog = Dialog(this)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(label)
            val window = dialog.window ?: return false
            val params = window.attributes
            // 入力メソッドの窓に付けて、入力メソッド用のダイアログとして出す (権限は要らない)
            params.token = this.window.window?.attributes?.token
            params.type = WindowManager.LayoutParams.TYPE_INPUT_METHOD_DIALOG
            params.gravity = Gravity.TOP or Gravity.START
            params.x = point[0].toInt()
            params.y = point[1].toInt() + dp(4)
            window.attributes = params
            window.setBackgroundDrawable(null)
            window.addFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            )
            dialog.show()
            indicator = dialog
            bar.postDelayed(hideIndicator, 1200)
            true
        } catch (e: RuntimeException) {
            dismissIndicator()
            false
        }
    }

    private fun dismissIndicator() {
        bar.removeCallbacks(hideIndicator)
        try {
            indicator?.dismiss()
        } catch (e: RuntimeException) {
            // 窓がもう無いときは何もしない
        }
        indicator = null
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    override fun onCreateInputView(): View {
        (bar.parent as? ViewGroup)?.removeView(bar)
        return bar
    }

    /** 物理キーボードがあっても候補バーを出す (既定では物理キーボードがあると出さない)。パスワード欄などでは出さない。 */
    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return !::controller.isInitialized || controller.showsBar
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        controller.options = prefs.bindingOptions
        controller.startInput(EditorPolicy.of(attribute.inputType, attribute.packageName, prefs.codeApps, attribute.imeOptions))
        updateInputViewShown()
    }

    override fun onFinishInput() {
        floating.hide()
        dismissIndicator()
        cursorInfo = null
        controller.finishInput()
        super.onFinishInput()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        controller.selectionChanged(newSelStart, newSelEnd, candidatesStart, candidatesEnd)
    }

    override fun onDestroy() {
        floating.hide()
        dismissIndicator()
        controller.shutdown()
        core?.close()
        core = null
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val key = KeyInput(keyCode, event.getUnicodeChar(event.metaState), event.getUnicodeChar(0), event.metaState)
        if (controller.keyDown(key)) {
            // 入力欄をタップせずに打ち始めたときは、まだ窓が出ていない
            if (!isInputViewShown) requestShowSelf(0)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        controller.keyUp(keyCode) || super.onKeyUp(keyCode, event)
}
