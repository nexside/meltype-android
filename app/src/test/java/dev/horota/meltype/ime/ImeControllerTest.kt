// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import android.view.KeyEvent
import dev.horota.meltype.core.CompositionView
import dev.horota.meltype.core.SessionResult
import dev.horota.meltype.core.TextEdit
import dev.horota.meltype.input.FieldPolicy
import dev.horota.meltype.input.KeyInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeControllerTest {
    private val core = FakeCore()
    private val editor = FakeEditor()
    private val modes = mutableListOf<InputMode>()
    private val status = object : StatusDisplay { override fun showMode(mode: InputMode) { modes += mode } }
    private val controller = ImeController(core, editor, editor, status)
    private val text = FieldPolicy(bypass = false, startDirect = false, codeInput = false)

    private fun letter(c: Char) = KeyInput(KeyEvent.KEYCODE_A + (c - 'a'), c.code, c.code, 0)

    @Test
    fun startInputConfiguresCore() {
        controller.startInput(FieldPolicy(bypass = false, startDirect = true, codeInput = true))
        assertTrue(core.calls.containsAll(listOf("canDelete:true", "code:true", "direct:true")))
        assertEquals(InputMode.DIRECT, modes.last())
    }

    @Test
    fun fieldWithoutSurroundingTextGetsNoneAndNoRecommit() {
        // Termux (TYPE_NULL): 周りの文字は常に空なので渡さず、Core に行を覚えさせる。確定し直しもしない
        controller.startInput(FieldPolicy(bypass = false, startDirect = false, codeInput = true, surroundingText = false))
        assertTrue("canDelete:false" in core.calls)
        core.calls.clear()
        editor.before = ""
        controller.keyDown(letter('m'))
        assertEquals(listOf("key:77:109:0:null:null"), core.calls)
    }

    @Test
    fun tapOutsideCompositionFinalizesInPlace() {
        controller.startInput(text)
        core.next = SessionResult(true, emptyList(), CompositionView(text = "めも"))
        controller.keyDown(letter('m'))
        editor.ops.clear()
        core.calls.clear()
        core.commitResult = SessionResult(true, listOf(TextEdit(0, "めも")), null)

        controller.selectionChanged(newSelStart = 0, newSelEnd = 0, candidatesStart = 5, candidatesEnd = 7)

        // 入力欄の文字はその場で確定し、Core の確定結果は入れ直さない (二重にしない)
        assertTrue("finish" in editor.ops)
        assertFalse("commit:めも" in editor.ops)
        assertEquals(listOf("commit", "forget"), core.calls)
        core.calls.clear()
        controller.keyDown(letter('a'))
        assertEquals("before と after を読み直す", listOf("key:65:97:0:${editor.before}:${editor.after}"), core.calls)
    }

    @Test
    fun caretAtCompositionEndIsOurOwnUpdate() {
        controller.startInput(text)
        core.next = SessionResult(true, emptyList(), CompositionView(text = "めも"))
        controller.keyDown(letter('m'))
        core.calls.clear()
        editor.ops.clear()
        controller.selectionChanged(newSelStart = 7, newSelEnd = 7, candidatesStart = 5, candidatesEnd = 7)
        assertEquals(emptyList<String>(), core.calls)
        assertFalse("finish" in editor.ops)
    }

    @Test
    fun selectionUpdateFromOurCommitKeepsLastCommit() {
        controller.startInput(text)
        core.next = SessionResult(true, listOf(TextEdit(0, "今日")), null)
        controller.keyDown(letter('a'))
        core.calls.clear()
        controller.selectionChanged(newSelStart = 2, newSelEnd = 2, candidatesStart = -1, candidatesEnd = -1)
        assertEquals(emptyList<String>(), core.calls)
    }

    @Test
    fun caretMovedByAppOrUserForgetsLastCommit() {
        controller.startInput(text)
        core.calls.clear()
        controller.selectionChanged(newSelStart = 0, newSelEnd = 0, candidatesStart = -1, candidatesEnd = -1)
        assertEquals(listOf("forget"), core.calls)
    }

    @Test
    fun shutdownCommitsPending() {
        controller.startInput(text)
        core.commitResult = SessionResult(true, listOf(TextEdit(0, "今日")), null)
        controller.shutdown()
        assertTrue("commit:今日" in editor.ops)
    }

    @Test
    fun privateFieldTellsCoreNotToLearn() {
        controller.startInput(FieldPolicy(bypass = false, startDirect = false, codeInput = false, noLearning = true))
        assertTrue("private:true" in core.calls)
        core.calls.clear()
        controller.startInput(text)
        assertTrue("private:false" in core.calls)
    }

    @Test
    fun letterGoesToCoreWithSurroundingText() {
        controller.startInput(text)
        editor.before = "hello "
        editor.after = "!"
        core.calls.clear()
        assertTrue(controller.keyDown(letter('k')))
        assertEquals(listOf("key:75:107:0:hello :!"), core.calls)
    }

    @Test
    fun surroundingTextIsNullWhileComposing() {
        controller.startInput(text)
        core.next = SessionResult(true, emptyList(), CompositionView(text = "k"))
        controller.keyDown(letter('k'))
        core.calls.clear()
        controller.keyDown(letter('y'))
        assertEquals(listOf("key:89:121:0:null:null"), core.calls)
    }

    @Test
    fun notConsumedKeyGoesToAppAfterCommits() {
        controller.startInput(text)
        core.next = SessionResult(false, listOf(TextEdit(0, "abc")), null)
        assertFalse(controller.keyDown(letter('a')))
        assertTrue("commit:abc" in editor.ops)
    }

    @Test
    fun passwordFieldBypassesCore() {
        controller.startInput(FieldPolicy(bypass = true, startDirect = true, codeInput = false))
        core.calls.clear()
        assertFalse(controller.keyDown(letter('a')))
        assertEquals(emptyList<String>(), core.calls)
    }

    @Test
    fun withoutCoreEverythingPassesThrough() {
        val noCore = ImeController(null, editor, editor, status)
        noCore.startInput(text)
        assertFalse(noCore.keyDown(letter('a')))
        assertEquals(InputMode.UNAVAILABLE, modes.last())
    }

    @Test
    fun keyUpOfConsumedKeyIsConsumed() {
        controller.startInput(text)
        controller.keyDown(letter('a'))
        assertTrue(controller.keyUp(KeyEvent.KEYCODE_A))
        assertFalse(controller.keyUp(KeyEvent.KEYCODE_A))
    }

    @Test
    fun keyUpOfPassedKeyIsNotConsumed() {
        controller.startInput(text)
        core.next = SessionResult(consumed = false)
        controller.keyDown(letter('a'))
        assertFalse(controller.keyUp(KeyEvent.KEYCODE_A))
    }

    @Test
    fun toggleCommitsPendingThenSwitches() {
        controller.startInput(text)
        core.calls.clear()
        assertTrue(controller.keyDown(KeyInput(KeyEvent.KEYCODE_ZENKAKU_HANKAKU, 0, 0, 0)))
        assertEquals(listOf("commit", "direct:true"), core.calls)
        assertEquals(InputMode.DIRECT, controller.mode)
    }

    @Test
    fun roleKeyUsesCoreWhileComposing() {
        controller.startInput(text)
        core.roleResult = SessionResult(true, emptyList(), CompositionView(text = "カ"))
        core.calls.clear()
        assertTrue(controller.keyDown(KeyInput(KeyEvent.KEYCODE_HENKAN, 0, 0, 0)))
        assertEquals(listOf("role:28"), core.calls)
    }

    @Test
    fun roleKeyFallsBackToSwitching() {
        controller.startInput(text)
        core.roleResult = null
        core.calls.clear()
        controller.keyDown(KeyInput(KeyEvent.KEYCODE_MUHENKAN, 0, 0, 0))
        assertEquals(listOf("role:29", "commit", "direct:true"), core.calls)
    }

    @Test
    fun barIsHiddenInBypassedFields() {
        controller.startInput(text)
        assertTrue(controller.showsBar)
        controller.startInput(FieldPolicy(bypass = true, startDirect = false, codeInput = false))
        assertFalse(controller.showsBar)
    }

    @Test
    fun convertButtonsSendF7AndF10() {
        controller.startInput(text)
        core.next = SessionResult(true, emptyList(), CompositionView(text = "キョウ"))
        core.calls.clear()
        controller.convert(ConvertTarget.KATAKANA)
        controller.convert(ConvertTarget.ALPHANUMERIC)
        assertEquals(listOf("key:118:0:0:null:null", "key:121:0:0:null:null"), core.calls)
        assertTrue(editor.ops.any { it.contains("キョウ") })
    }

    @Test
    fun convertDoesNothingWhilePaused() {
        controller.startInput(text)
        controller.keyDown(KeyInput(KeyEvent.KEYCODE_ZENKAKU_HANKAKU, 0, 0, KeyEvent.META_CTRL_ON))
        core.calls.clear()
        controller.convert(ConvertTarget.KATAKANA)
        assertEquals(emptyList<String>(), core.calls)
    }

    @Test
    fun pauseLetsKeysThroughUntilResumed() {
        controller.startInput(text)
        val pause = KeyInput(KeyEvent.KEYCODE_ZENKAKU_HANKAKU, 0, 0, KeyEvent.META_CTRL_ON)
        assertTrue(controller.keyDown(pause))
        assertEquals(InputMode.PAUSED, controller.mode)
        core.calls.clear()
        assertFalse(controller.keyDown(letter('a')))
        assertEquals(emptyList<String>(), core.calls)
        controller.keyDown(pause)
        assertEquals(InputMode.JAPANESE, controller.mode)
    }

    @Test
    fun finishInputCommitsPending() {
        controller.startInput(text)
        core.commitResult = SessionResult(true, listOf(TextEdit(0, "今日")), null)
        controller.finishInput()
        assertTrue("commit:今日" in editor.ops)
    }

    @Test
    fun selectCandidateAppliesResult() {
        controller.startInput(text)
        core.next = SessionResult(true, listOf(TextEdit(0, "京")), null)
        controller.selectCandidate(1)
        assertTrue("select:1" in core.calls)
        assertTrue("commit:京" in editor.ops)
    }

    @Test
    fun emojiPanelKeyCommitsWhatIsBeingTypedThenOpensThePanel() {
        controller.startInput(text)
        var opened = 0
        controller.onOpenEmojiPanel = { opened++ }
        core.calls.clear()

        val consumed = controller.keyDown(KeyInput(KeyEvent.KEYCODE_PERIOD, '.'.code, '.'.code, KeyEvent.META_CTRL_ON))

        assertTrue(consumed)
        assertEquals(1, opened)
        assertEquals(listOf("commit"), core.calls) // 文字は Core に渡さない
    }

    @Test
    fun committedBracketPairPutsTheCaretBetweenWhenEnabled() {
        controller.startInput(text)
        controller.cursorBetweenBrackets = true
        core.next = SessionResult(true, listOf(TextEdit(0, "()")), null)
        editor.ops.clear()

        controller.keyDown(letter('a'))

        assertTrue("inside:()" in editor.ops)
        assertFalse("commit:()" in editor.ops)
    }

    @Test
    fun bracketPairIsCommittedAsIsWhenTheSettingIsOff() {
        controller.startInput(text)
        controller.cursorBetweenBrackets = false
        core.next = SessionResult(true, listOf(TextEdit(0, "()")), null)
        editor.ops.clear()

        controller.keyDown(letter('a'))

        assertTrue("commit:()" in editor.ops)
        assertFalse("inside:()" in editor.ops)
    }

    @Test
    fun otherTextsAreNeverSplit() {
        controller.startInput(text)
        controller.cursorBetweenBrackets = true
        core.next = SessionResult(true, listOf(TextEdit(0, "print()")), null)
        editor.ops.clear()

        controller.keyDown(letter('a'))

        assertTrue("commit:print()" in editor.ops)
    }

    @Test
    fun remembersTheReadingBeforeConversion() {
        controller.startInput(text)
        core.next = SessionResult(true, emptyList(), CompositionView(text = "きょう"))
        controller.keyDown(letter('k'))
        core.next = SessionResult(true, emptyList(), CompositionView(text = "今日", converting = true, candidates = listOf("今日", "京")))
        controller.keyDown(KeyInput(KeyEvent.KEYCODE_SPACE, ' '.code, ' '.code, 0))

        assertEquals("きょう", controller.lastReading)
    }

    @Test
    fun questionMarkIsOfferedAfterACommittedQuestion() {
        controller.startInput(text)
        editor.before = "明日行きますか"
        core.next = SessionResult(consumed = true)
        controller.keyDown(letter('a'))
        assertTrue(editor.ops.contains("show:？"))
    }

    @Test
    fun questionMarkIsNotOfferedForWordsThatJustEndWithKa() {
        controller.startInput(text)
        editor.before = "どこか"
        core.next = SessionResult(consumed = true)
        controller.keyDown(letter('a'))
        assertFalse(editor.ops.contains("show:？"))
    }

    @Test
    fun spaceInsertsTheQuestionMarkAndDoesNotSendTheKeyToCore() {
        controller.startInput(text)
        editor.before = "そうかな"
        core.next = SessionResult(consumed = true)
        controller.keyDown(letter('a'))
        editor.ops.clear()
        core.calls.clear()

        assertTrue(controller.keyDown(KeyInput(KeyEvent.KEYCODE_SPACE, ' '.code, ' '.code, 0)))

        assertTrue("commit:？" in editor.ops)
        assertTrue("forget" in core.calls)
        assertFalse(core.calls.any { it.startsWith("key:") })
    }

    @Test
    fun anotherLetterDismissesTheQuestionMark() {
        controller.startInput(text)
        editor.before = "明日行きますか"
        core.next = SessionResult(consumed = true)
        controller.keyDown(letter('a'))
        editor.ops.clear()
        core.calls.clear()
        editor.before = "明日行きますか"

        assertTrue(controller.keyDown(letter('b')))

        assertFalse("commit:？" in editor.ops)
        assertTrue(core.calls.any { it.startsWith("key:") })
    }

    @Test
    fun questionMarkIsNotOfferedWhileComposing() {
        controller.startInput(text)
        editor.before = "明日行きますか"
        core.next = SessionResult(true, emptyList(), CompositionView(text = "あ", candidates = listOf("あ")))
        controller.keyDown(letter('a'))
        assertTrue(editor.ops.contains("show:あ"))
        assertFalse(editor.ops.contains("show:？"))
    }

    @Test
    fun backspaceRightAfterACommitRestoresTheReading() {
        controller.startInput(text)
        core.next = SessionResult(true, emptyList(), CompositionView(text = "きょう"))
        controller.keyDown(letter('k'))
        core.next = SessionResult(true, listOf(TextEdit(0, "今日")), null)
        editor.before = "今日"
        controller.keyDown(letter('a'))
        editor.ops.clear()
        core.calls.clear()
        editor.before = "今日"

        assertTrue(controller.keyDown(KeyInput(KeyEvent.KEYCODE_DEL, 0, 0, 0)))

        assertTrue("delete:2" in editor.ops)
        assertTrue("commit:きょう" in editor.ops)
        assertTrue("forget" in core.calls)
        assertFalse(core.calls.any { it.startsWith("key:") })
    }

    @Test
    fun backspaceAfterAnotherKeyDeletesNormally() {
        controller.startInput(text)
        core.next = SessionResult(true, emptyList(), CompositionView(text = "きょう"))
        controller.keyDown(letter('k'))
        core.next = SessionResult(true, listOf(TextEdit(0, "今日")), null)
        editor.before = "今日"
        controller.keyDown(letter('a'))
        core.next = SessionResult(consumed = true)
        controller.keyDown(letter('b'))
        editor.ops.clear()
        core.calls.clear()
        editor.before = "今日b"

        assertTrue(controller.keyDown(KeyInput(KeyEvent.KEYCODE_DEL, 0, 0, 0)))

        assertFalse("commit:きょう" in editor.ops)
        assertTrue(core.calls.any { it.startsWith("key:") })
    }
}
