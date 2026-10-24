// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiDataTest {
    @Test
    fun detectsEmojiButNotOrdinarySymbolsOrText() {
        assertTrue(EmojiData.isEmoji("😊"))
        assertTrue(EmojiData.isEmoji("🇯🇵"))
        assertTrue(EmojiData.isEmoji("❤️"))
        assertTrue(EmojiData.isEmoji("☀️"))
        assertTrue(EmojiData.isEmoji("✨"))
        assertFalse(EmojiData.isEmoji("今日"))
        assertFalse(EmojiData.isEmoji("きょう"))
        assertFalse(EmojiData.isEmoji("abc"))
        assertFalse(EmojiData.isEmoji("★"))
        assertFalse(EmojiData.isEmoji("♪"))
        assertFalse(EmojiData.isEmoji("(=^・^=)"))
        assertFalse(EmojiData.isEmoji(""))
    }

    @Test
    fun everyCategoryItemIsAnEmoji() {
        for (category in EmojiData.categories) {
            assertTrue("${category.name} が空", category.items.isNotEmpty())
            for (item in category.items) {
                assertTrue("$item (${category.name}) が絵文字として読めない", EmojiData.isEmoji(item))
                assertFalse("$item に空白が入っている", item.any { it.isWhitespace() })
            }
        }
    }

    @Test
    fun pushRecentMovesToFrontWithoutDuplicates() {
        assertEquals(listOf("😊", "👍", "🎉"), EmojiData.pushRecent(listOf("👍", "😊", "🎉"), "😊"))
        assertEquals(listOf("🔥", "👍"), EmojiData.pushRecent(listOf("👍"), "🔥"))
    }

    @Test
    fun pushRecentKeepsOnlyTheNewest() {
        assertEquals(listOf("c", "b"), EmojiData.pushRecent(listOf("b", "a"), "c", max = 2))
    }

    @Test
    fun recentRoundTrips() {
        val recent = listOf("😊", "🇯🇵", "❤️")
        assertEquals(recent, EmojiData.parseRecent(EmojiData.formatRecent(recent)))
        assertEquals(emptyList<String>(), EmojiData.parseRecent(null))
        assertEquals(emptyList<String>(), EmojiData.parseRecent("  "))
    }

    @Test
    fun emojiOrderPicksOnlyEmojiAndPutsRecentFirst() {
        val candidates = listOf("笑顔", "😊", "😄", "えがお", "(^^)", "😁")
        assertEquals(listOf(1, 2, 5), EmojiData.emojiOrder(candidates, emptyList()))
        assertEquals(listOf(5, 1, 2), EmojiData.emojiOrder(candidates, listOf("😁")))
        assertEquals(listOf(2, 5, 1), EmojiData.emojiOrder(candidates, listOf("😄", "😁")))
    }

    @Test
    fun panelStartsAtTheFirstCategoryWhenThereAreNoRecents() {
        val state = EmojiPanelState(emptyList())
        assertEquals("顔", state.tabs.first().name)
        assertEquals(EmojiData.categories.first().items.first(), state.current())
    }

    @Test
    fun panelPutsRecentsInTheFirstTab() {
        val state = EmojiPanelState(listOf("🔥", "😊"))
        assertEquals("最近", state.tabs.first().name)
        assertEquals("🔥", state.current())
        assertEquals(EmojiData.categories.size + 1, state.tabs.size)
    }

    @Test
    fun panelMovesInsideTheGridAndStopsAtEdges() {
        val state = EmojiPanelState(emptyList(), columns = 4)
        state.moveLeft()
        assertEquals(0, state.index)
        state.moveUp()
        assertEquals(0, state.index)
        state.moveRight()
        state.moveRight()
        assertEquals(2, state.index)
        state.moveDown()
        assertEquals(6, state.index)
        state.moveUp()
        assertEquals(2, state.index)
    }

    @Test
    fun panelMovingDownFromAFullRowIntoAShortLastRowGoesToItsEnd() {
        val state = EmojiPanelState(listOf("a", "b", "c", "d", "e", "f"), columns = 4) // 最近のタブ: 4 + 2
        state.select(0, 3)
        state.moveDown()
        assertEquals(5, state.index) // 真下 (7) は無いので最後
        state.moveDown()
        assertEquals(5, state.index) // 最後の行ではそのまま
    }

    @Test
    fun panelSwitchesTabsAndWraps() {
        val state = EmojiPanelState(emptyList())
        val last = state.tabs.size - 1
        state.previousTab()
        assertEquals(last, state.tab)
        state.nextTab()
        assertEquals(0, state.tab)
        state.moveRight()
        state.nextTab()
        assertEquals(1, state.tab)
        assertEquals(0, state.index) // タブを変えたら先頭に戻る
    }

    @Test
    fun panelSelectClampsIndexes() {
        val state = EmojiPanelState(emptyList())
        state.select(1000, 1000)
        assertEquals(state.tabs.size - 1, state.tab)
        assertEquals(state.items.size - 1, state.index)
        assertNull(EmojiPanelState(emptyList()).also { it.select(0, 0) }.items.getOrNull(9999))
    }
}
