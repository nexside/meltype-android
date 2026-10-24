// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.TextView
import dev.horota.meltype.core.CompositionView
import dev.horota.meltype.core.CoreBridge
import dev.horota.meltype.core.CoreOpenResult
import dev.horota.meltype.input.EditorPolicy
import dev.horota.meltype.input.KeyInput
import dev.horota.meltype.settings.DictionaryFiles
import dev.horota.meltype.settings.MeltypePrefs
import dev.horota.meltype.settings.WordRegisterActivity
import java.io.File

/**
 * 物理キーボード専用。画面キーボードも、画面の下の候補バーも出さない。
 * 候補・モードの表示・絵文字の一覧は、入力カーソルの近くに浮かべる小さな窓 (ImeDialog) に出す。
 * Android 11 以降は、表示を頼まれていない IME の窓は見えないので、1px の入力用の窓 (input view) を出し、
 * 文字を打ち始めたら自分で表示を頼む (requestShowSelf)。
 */
class MeltypeImeService : InputMethodService() {
    private lateinit var prefs: MeltypePrefs
    private lateinit var controller: ImeController
    private var core: CoreBridge? = null
    private val handler = Handler(Looper.getMainLooper())

    /** 入力欄が教えてくれた、入力カーソルの位置 (教えてくれない入力欄では null)。 */
    private var cursorInfo: CursorAnchorInfo? = null
    private lateinit var floating: FloatingCandidates
    private lateinit var emojiPanel: EmojiPanel

    /** 一覧のキーで使ったキー (離したときも入力に回さない)。 */
    private val panelConsumed = mutableSetOf<Int>()

    /** 起動時に見せる知らせ (Mozc が使えないなど)。一度だけ。 */
    private var startupNotice: String? = null

    /** 読み込んだときの userdict.txt の更新時刻。変わっていたら、入力の本体を作り直して辞書を読み直す。 */
    private var userDictStamp = 0L

    private val indicatorLabel by lazy {
        TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.WHITE)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = GradientDrawable().apply {
                setColor(Color.argb(0xE6, 0x33, 0x33, 0x33))
                cornerRadius = dp(8).toFloat()
            }
        }
    }
    private val indicatorWindow by lazy { ImeDialog(this, indicatorLabel, touchable = false) }
    private val hideIndicator = Runnable { indicatorWindow.dismiss() }

    private val display = object : CandidateDisplay {
        override fun show(view: CompositionView, inlineReading: Boolean) {
            floating.show(view, inlineReading)
        }

        override fun hide() {
            floating.hide()
        }
    }

    /** 「あ」/「A」の表示。切り替えたときだけ、入力カーソルの近くに短く出す。 */
    private val status = object : StatusDisplay {
        private var last: String? = null

        override fun showMode(mode: InputMode) {
            val text = when (mode) {
                InputMode.JAPANESE -> "あ"
                InputMode.DIRECT -> "A"
                InputMode.PAUSED -> "⏸"
                InputMode.UNAVAILABLE -> "⚠"
            }
            val changed = last != null && last != text
            last = text
            if (changed) showIndicator(text, 1200)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = MeltypePrefs(this)
        openCore()
        floating = FloatingCandidates(
            this,
            anchor = ::composingAnchor,
            recent = { prefs.recentEmoji },
            onSelect = { index -> controller.selectCandidate(index) },
            onConvert = { target -> controller.convert(target) },
            onRegister = ::openRegistration,
        )
        emojiPanel = EmojiPanel(
            this,
            anchor = ::composingAnchor,
            recent = { prefs.recentEmoji },
            onPick = ::commitEmoji,
        )
        controller = newController()
        applyTheme()
    }

    private fun openCore() {
        when (val opened = CoreBridge.open(MeltypePrefs.dataDirectory(this), File(applicationInfo.nativeLibraryDir, "libmozc_helper.so"))) {
            is CoreOpenResult.Ready -> {
                core = opened.core
                if (!opened.mozcAvailable) startupNotice = "漢字変換を使えません"
            }
            is CoreOpenResult.Failed -> {
                core = null
                startupNotice = "Meltype を初期化できませんでした"
            }
        }
        userDictStamp = DictionaryFiles.stamp(MeltypePrefs.dataDirectory(this))
    }

    private fun newController(): ImeController =
        ImeController(core, InputConnectionSink({ currentInputConnection }, ::onCommitted), display, status).also {
            it.onOpenEmojiPanel = ::openEmojiPanel
        }


    /** ユーザー辞書が変わっていたら (設定画面・単語登録・一括登録)、入力の本体を作り直して読み直す。 */
    private fun reloadIfUserDictionaryChanged() {
        if (DictionaryFiles.stamp(MeltypePrefs.dataDirectory(this)) == userDictStamp) return
        floating.hide()
        controller.shutdown()
        core?.close()
        core = null
        openCore()
        controller = newController()
    }

    /** 候補の配色 (設定。「システムに合わせる」なら端末のダークモード) を反映する。 */
    private fun applyTheme() {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val theme = CandidateTheme.of(prefs.themeMode, night)
        floating.applyTheme(theme)
        emojiPanel.applyTheme(theme)
    }

    /** 確定した文字。絵文字なら「最近使った絵文字」に入れる (候補から選んだときも)。 */
    private fun onCommitted(text: String) {
        if (text.length <= MAX_EMOJI_LENGTH && EmojiData.isEmoji(text)) rememberEmoji(text)
    }

    private fun rememberEmoji(emoji: String) {
        prefs.recentEmoji = EmojiData.pushRecent(prefs.recentEmoji, emoji)
    }

    private fun openEmojiPanel() {
        emojiPanel.show()
    }

    private fun commitEmoji(emoji: String) {
        rememberEmoji(emoji)
        currentInputConnection?.commitText(emoji, 1)
    }

    /** 変換した語をユーザー辞書に登録する画面を開く (読みは、変換する前に打っていた文字)。 */
    private fun openRegistration(view: CompositionView) {
        val intent = Intent(this, WordRegisterActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(WordRegisterActivity.EXTRA_READING, controller.lastReading.orEmpty())
            .putExtra(WordRegisterActivity.EXTRA_WORD, view.text)
        startActivity(intent)
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
        // 入力欄が (0, 0) のような意味のない位置を返すことがある。画面の外・高さ 0 のときは位置不明として扱う
        val metrics = resources.displayMetrics
        val valid = points[3] > points[1] && points[1] > 0f && points[3] < metrics.heightPixels &&
            points[0] >= 0f && points[0] < metrics.widthPixels
        if (!valid) return null
        return FloatingCandidates.Anchor(points[0], points[1], points[3])
    }

    /** 小さな知らせ (「あ」/「A」・警告) を、入力カーソルのすぐ下に出す。位置が分からないときは画面の下の少し上。 */
    private fun showIndicator(text: String, millis: Long) {
        val metrics = resources.displayMetrics
        indicatorLabel.text = text
        indicatorLabel.measure(
            View.MeasureSpec.makeMeasureSpec(metrics.widthPixels - dp(32), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val w = indicatorLabel.measuredWidth
        val h = indicatorLabel.measuredHeight
        val anchor = composingAnchor()
        val x = if (anchor != null) anchor.x.toInt().coerceIn(0, maxOf(0, metrics.widthPixels - w)) else maxOf(0, (metrics.widthPixels - w) / 2)
        val y = if (anchor != null) anchor.bottom.toInt() + dp(4) else maxOf(0, metrics.heightPixels - h - dp(64))
        if (!indicatorWindow.show(x, y, w, h)) return
        handler.removeCallbacks(hideIndicator)
        handler.postDelayed(hideIndicator, millis)
    }

    private fun dismissIndicator() {
        handler.removeCallbacks(hideIndicator)
        indicatorWindow.dismiss()
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    /** 1px の透明な窓。候補は浮かべる窓に出すので、画面の下にバーは出さない。 */
    override fun onCreateInputView(): View =
        View(this).apply { layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1) }

    /** 物理キーボードがあっても入力用の窓を出す (既定では物理キーボードがあると出さない)。パスワード欄などでは出さない。 */
    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return !::controller.isInitialized || controller.showsBar
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    /** 入力の窓は 1px で何も見せないので、アプリの画面を押し上げたり縮めたりしない。 */
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        val bottom = window.window?.decorView?.height ?: return
        if (bottom <= 0) return
        outInsets.contentTopInsets = bottom
        outInsets.visibleTopInsets = bottom
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        reloadIfUserDictionaryChanged()
        controller.options = prefs.bindingOptions
        controller.cursorBetweenBrackets = prefs.cursorBetweenBrackets
        applyTheme()
        currentInputConnection?.requestCursorUpdates(InputConnection.CURSOR_UPDATE_MONITOR)
        controller.startInput(EditorPolicy.of(attribute.inputType, attribute.packageName, prefs.codeApps, attribute.imeOptions))
        updateInputViewShown()
        startupNotice?.let {
            startupNotice = null
            showIndicator(it, 4000)
        }
    }

    override fun onFinishInput() {
        emojiPanel.hide()
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
        emojiPanel.hide()
        floating.hide()
        dismissIndicator()
        controller.shutdown()
        core?.close()
        core = null
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (emojiPanel.isShowing && emojiPanel.onKey(keyCode, event)) {
            panelConsumed += keyCode
            return true
        }
        val key = KeyInput(keyCode, event.getUnicodeChar(event.metaState), event.getUnicodeChar(0), event.metaState)
        if (controller.keyDown(key)) {
            // 入力欄をタップせずに打ち始めたときは、まだ窓が出ていない
            if (!isInputViewShown) requestShowSelf(0)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        panelConsumed.remove(keyCode) || controller.keyUp(keyCode) || super.onKeyUp(keyCode, event)

    private companion object {
        /** 「最近使った絵文字」に入れる、確定した文字の長さの上限 (旗や家族の絵文字は 10 文字を超えることがある)。 */
        const val MAX_EMOJI_LENGTH = 16
    }
}
