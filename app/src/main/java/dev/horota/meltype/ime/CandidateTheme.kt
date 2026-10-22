// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import android.graphics.Color

/** 候補の配色の選び方。SYSTEM は端末のダークモードに合わせる。 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 候補バー・候補の窓の色。 */
data class CandidateTheme(
    val background: Int,
    val text: Int,
    val selectedBackground: Int,
    val selectedText: Int,
    val accent: Int,
    val border: Int,
    val warning: Int,
) {
    companion object {
        val LIGHT = CandidateTheme(
            background = Color.rgb(0xF2, 0xF2, 0xF2),
            text = Color.BLACK,
            selectedBackground = Color.rgb(0x4C, 0xA0, 0xFF),
            selectedText = Color.WHITE,
            accent = Color.rgb(0x1A, 0x5F, 0xB4),
            border = Color.rgb(0xBB, 0xBB, 0xBB),
            warning = Color.rgb(0xC6, 0x28, 0x28),
        )

        /** 黒地に白文字。 */
        val DARK = CandidateTheme(
            background = Color.BLACK,
            text = Color.WHITE,
            selectedBackground = Color.rgb(0x2F, 0x6F, 0xD0),
            selectedText = Color.WHITE,
            accent = Color.rgb(0x8A, 0xB4, 0xF8),
            border = Color.rgb(0x66, 0x66, 0x66),
            warning = Color.rgb(0xFF, 0x8A, 0x80),
        )

        fun of(mode: ThemeMode, systemIsDark: Boolean): CandidateTheme = when (mode) {
            ThemeMode.LIGHT -> LIGHT
            ThemeMode.DARK -> DARK
            ThemeMode.SYSTEM -> if (systemIsDark) DARK else LIGHT
        }
    }
}
