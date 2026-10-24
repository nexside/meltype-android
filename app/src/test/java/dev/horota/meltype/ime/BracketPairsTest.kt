// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BracketPairsTest {
    @Test
    fun splitsKnownPairs() {
        assertEquals("(" to ")", BracketPairs.split("()"))
        assertEquals("（" to "）", BracketPairs.split("（）"))
        assertEquals("「" to "」", BracketPairs.split("「」"))
        assertEquals("[" to "]", BracketPairs.split("[]"))
        assertEquals("{" to "}", BracketPairs.split("{}"))
        assertEquals("【" to "】", BracketPairs.split("【】"))
    }

    @Test
    fun ignoresEverythingElse() {
        assertNull(BracketPairs.split(""))
        assertNull(BracketPairs.split("("))
        assertNull(BracketPairs.split("(a)"))
        assertNull(BracketPairs.split("print()"))
        assertNull(BracketPairs.split(")("))
        assertNull(BracketPairs.split("(]")) // 種類が違う
        assertNull(BracketPairs.split("\"\"")) // 引用符は数えない
        assertNull(BracketPairs.split("<>"))
        assertNull(BracketPairs.split("()()"))
    }
}
