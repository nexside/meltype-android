// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class GenreNamesTest {
    @Test
    fun keepsOrdinaryNames() {
        assertEquals("料理", GenreNames.sanitize("料理"))
        assertEquals("鉄道 用語", GenreNames.sanitize("  鉄道 用語  "))
    }

    @Test
    fun replacesCharactersThatCannotBeInAFileName() {
        assertEquals("a_b_c", GenreNames.sanitize("a/b:c"))
        assertEquals("x_y", GenreNames.sanitize("x\ty"))
    }

    @Test
    fun removesLeadingDotsSoTheFileIsNotHidden() {
        assertEquals("hidden", GenreNames.sanitize("..hidden"))
    }

    @Test
    fun blankBecomesEmpty() {
        assertEquals("", GenreNames.sanitize("   "))
        assertEquals("", GenreNames.sanitize("..."))
    }

    @Test
    fun cutsLongNames() {
        assertEquals(GenreNames.MAX_LENGTH, GenreNames.sanitize("あ".repeat(100)).length)
    }
}
