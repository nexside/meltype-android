// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import android.view.KeyEvent
import dev.horota.meltype.core.CompositionView
import dev.horota.meltype.core.CoreApi
import dev.horota.meltype.core.SessionResult
import dev.horota.meltype.input.BindingAction
import dev.horota.meltype.input.DigitPadKind
import dev.horota.meltype.input.BindingOptions
import dev.horota.meltype.input.FieldPolicy
import dev.horota.meltype.input.KeyBindings
import dev.horota.meltype.input.KeyInput
import dev.horota.meltype.input.KeyNormalizer

enum class InputMode { JAPANESE, DIRECT, PAUSED, UNAVAILABLE }

/** 候補バーのボタンで入力中の文字を変える先。vk は Core に渡すキー (F7 / F10)。 */
enum class ConvertTarget(val vk: Int) { KATAKANA(0x76), ALPHANUMERIC(0x79) }

/** 英 / 日の状態の表示 (候補バーの左端)。 */
interface StatusDisplay {
    fun showMode(mode: InputMode)
}

/**
 * キー入力の流れ: KeyBindings → KeyNormalizer → Core → ResultApplier。Android に依存しない (JVM でテストする)。
 * core が null なら (読み込めなかった) キーをすべてアプリに渡す。
 */
class ImeController(
    private val core: CoreApi?,
    private val sink: EditorSink,
    private val display: CandidateDisplay,
    private val status: StatusDisplay,
) {
    private companion object {
        const val SURROUNDING_LENGTH = 64
    }

    private val applier = ResultApplier(sink, display)
    private var policy = FieldPolicy(bypass = false, startDirect = false, codeInput = false)
    /** ユーザーが最後に選んだ英数 / 日本語 (入力欄をまたいで覚える)。 */
    private var userDirect = false
    private var direct = false
    private var paused = false
    /** Meltype が使った KeyDown。対応する KeyUp もアプリに渡さない。 */
    private val consumedDown = mutableSetOf<Int>()

    /** 確定済みの文に出す「？」。自動では入れない。null なら出していない。 */
    private var punctuation: String? = null

    /** 直前に確定した語。直後の Backspace だけで、読みに戻す。 */
    private var undoText: String? = null
    private var undoReading: String? = null

    var options: BindingOptions = BindingOptions()

    /** 確定した文字がかっこの組なら、カーソルを間に置く。 */
    var cursorBetweenBrackets: Boolean
        get() = applier.cursorBetweenBrackets
        set(value) {
            applier.cursorBetweenBrackets = value
        }

    /** 絵文字の一覧を開くキーが押された。呼ぶ前に、入力中の文字は確定してある。 */
    var onOpenEmojiPanel: (() -> Unit)? = null

    /** 直近の、変換する前の入力 (単語登録の読みに使う)。 */
    val lastReading: String?
        get() = applier.lastReading

    /** 入力中の文字をそのまま確定する (一覧を開くときなど)。 */
    fun commitComposition() {
        commitPending()
    }

    /** 候補バーを出すか。Meltype を通さない入力欄 (パスワードなど) では出さない。 */
    val showsBar: Boolean
        get() = !policy.bypass

    /** 入力の窓を出すか。数字行は、変換を通さない暗証番号欄でも出す。 */
    val showsWindow: Boolean
        get() = showsBar || policy.digitPad != DigitPadKind.NONE

    val digitPadKind: DigitPadKind
        get() = policy.digitPad

    val mode: InputMode
        get() = when {
            core == null -> InputMode.UNAVAILABLE
            paused -> InputMode.PAUSED
            direct -> InputMode.DIRECT
            else -> InputMode.JAPANESE
        }

    fun startInput(policy: FieldPolicy) {
        this.policy = policy
        applier.reset()
        hidePunctuation()
        clearUndo()
        applier.inlineReading = !policy.surroundingText
        consumedDown.clear()
        // 周りの文字を返せない入力欄では、確定済みの文字を確かめて消せないので、確定し直しをさせない
        core?.setCanDelete(policy.surroundingText)
        core?.setCodeInput(policy.codeInput)
        core?.setPrivate(policy.noLearning)
        direct = policy.startDirect || userDirect
        core?.setDirect(direct)
        status.showMode(mode)
    }

    fun finishInput() {
        commitPending()
    }

    /** IME が終わるとき (onDestroy)。未確定の内容を確定する。 */
    fun shutdown() {
        commitPending()
    }

    /**
     * 入力欄のカーソルが動いた (onUpdateSelection)。candidatesStart / candidatesEnd は未確定の文字の範囲 (無ければ -1)。
     * 入力中にカーソルが未確定の文字の末尾以外へ動いたら (タップなど)、未確定の文字はその場で確定する。
     * 自分の書き換え以外でカーソルが動いたら (アプリに渡したキー・タップ)、前に確定した語を確定し直さない。
     */
    fun selectionChanged(newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        val selfEdit = applier.takeSelfEdit()
        val api = core ?: return
        if (applier.composing) {
            val caretAtCompositionEnd = candidatesStart >= 0 && newSelStart == newSelEnd && newSelEnd == candidatesEnd
            if (caretAtCompositionEnd) return
            // 入力欄の文字はそのまま確定し、Core の確定結果は入れ直さない (二重になるため)
            sink.finishComposing()
            applier.reset()
            api.commit()
            api.forgetLastCommit()
            display.hide()
        } else if (!selfEdit) {
            api.forgetLastCommit()
            hidePunctuation()
            clearUndo()
        }
    }

    fun keyDown(key: KeyInput): Boolean {
        val binding = KeyBindings.match(key.keyCode, key.metaState, options)
        if (binding == BindingAction.TogglePause) {
            hidePunctuation()
            clearUndo()
            return consume(key.keyCode) { togglePause() }
        }
        val api = core ?: return false
        if (paused || policy.bypass) {
            hidePunctuation()
            clearUndo()
            return false
        }
        if (undoBackspace(key)) return consume(key.keyCode) { revertLastCommit() }
        clearUndo()
        if (binding != null) {
            hidePunctuation()
            return consume(key.keyCode) { handle(binding, api) }
        }
        if (punctuation != null && PunctuationPrediction.accepts(key)) {
            return consume(key.keyCode) { acceptPunctuation() }
        }
        hidePunctuation()

        val normalized = KeyNormalizer.normalize(key) ?: return false
        // 入力中と、周りの文字を返せない入力欄では渡さない (Core が自分で覚えている行を使う)
        val readSurrounding = !applier.composing && policy.surroundingText
        val before = if (readSurrounding) sink.textBeforeCursor(SURROUNDING_LENGTH) else null
        val after = if (readSurrounding) sink.textAfterCursor(SURROUNDING_LENGTH) else null
        val result = api.handleKey(normalized.vk, normalized.ch, normalized.modifiers, before, after) ?: return false
        applier.apply(result)
        if (result.view == null) {
            refreshPunctuation()
            rememberUndo(result)
        } else {
            punctuation = null
            clearUndo()
        }
        if (result.consumed) consumedDown += key.keyCode
        return result.consumed
    }

    fun keyUp(keyCode: Int): Boolean = consumedDown.remove(keyCode)

    fun selectCandidate(index: Int) {
        if (punctuation != null) {
            acceptPunctuation()
            return
        }
        core?.selectCandidate(index)?.let(applier::apply)
    }

    /** 候補バーの「カナ」「英数」。入力中の文字をカタカナ / 英数にする (本家の F7 / F10)。 */
    fun convert(target: ConvertTarget) {
        val api = core ?: return
        if (paused || policy.bypass) return
        api.handleKey(target.vk, 0, 0, null, null)?.let(applier::apply)
    }

    private inline fun consume(keyCode: Int, action: () -> Unit): Boolean {
        action()
        consumedDown += keyCode
        return true
    }

    private fun handle(binding: BindingAction, api: CoreApi) {
        when (binding) {
            BindingAction.ToggleDirect -> setDirect(!direct)
            is BindingAction.SetDirect -> setDirect(binding.direct)
            is BindingAction.RoleKey -> {
                val result = if (direct) null else api.roleKey(binding.vk)
                if (result != null) applier.apply(result) else setDirect(binding.fallbackDirect)
            }
            BindingAction.TogglePause -> togglePause()
            BindingAction.OpenEmojiPanel -> {
                commitPending()
                onOpenEmojiPanel?.invoke()
            }
        }
    }

    private fun setDirect(value: Boolean) {
        commitPending()
        direct = value
        userDirect = value
        core?.setDirect(value)
        status.showMode(mode)
    }

    private fun togglePause() {
        commitPending()
        paused = !paused
        status.showMode(mode)
    }

    private fun commitPending() {
        core?.commit()?.let(applier::apply)
        hidePunctuation()
    }

    /** 変換が終わったあと、前文が疑問なら「？」だけを候補にする。 */
    private fun refreshPunctuation() {
        if (applier.composing || paused || direct || policy.bypass || policy.codeInput || !policy.surroundingText) {
            hidePunctuation()
            return
        }
        val mark = PunctuationPrediction.questionMark(
            sink.textBeforeCursor(SURROUNDING_LENGTH),
            sink.textAfterCursor(SURROUNDING_LENGTH),
        )
        if (mark == null) {
            hidePunctuation()
            return
        }
        punctuation = mark
        display.show(CompositionView(candidates = listOf(mark)), inlineReading = false)
    }

    private fun acceptPunctuation() {
        val mark = punctuation ?: return
        punctuation = null
        sink.beginBatch()
        try {
            sink.commit(mark)
        } finally {
            sink.endBatch()
        }
        display.hide()
        // 入れた「？」を、次の語の文脈で Core が消さないようにする
        core?.forgetLastCommit()
    }

    private fun hidePunctuation() {
        if (punctuation == null) return
        punctuation = null
        if (!applier.composing) display.hide()
    }

    /** 変換で確定した直後だけ覚える。直接入力や、読みと同じ文字は戻さない。 */
    private fun rememberUndo(result: SessionResult) {
        if (direct || applier.composing || !policy.surroundingText) {
            clearUndo()
            return
        }
        val committed = result.commits.lastOrNull { it.text.isNotEmpty() }?.text
        val reading = applier.lastReading
        if (committed != null && !reading.isNullOrEmpty() && reading != committed) {
            undoText = committed
            undoReading = reading
        } else if (result.commits.isNotEmpty()) {
            clearUndo()
        }
    }

    private fun undoBackspace(key: KeyInput): Boolean {
        if (undoText == null || key.keyCode != KeyEvent.KEYCODE_DEL) return false
        if (key.metaState and (KeyEvent.META_ALT_ON or KeyEvent.META_CTRL_ON or KeyEvent.META_META_ON) != 0) return false
        if (applier.composing) return false
        return sink.textBeforeCursor(undoText!!.length) == undoText
    }

    private fun revertLastCommit() {
        val committed = undoText ?: return
        val reading = undoReading ?: return
        clearUndo()
        sink.beginBatch()
        try {
            sink.deleteBefore(committed.length)
            sink.commit(reading)
        } finally {
            sink.endBatch()
        }
        core?.forgetLastCommit()
    }

    private fun clearUndo() {
        undoText = null
        undoReading = null
    }
}
