// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.settings

import android.content.Context
import dev.horota.meltype.ime.EmojiData
import dev.horota.meltype.ime.ThemeMode
import dev.horota.meltype.input.BindingOptions
import dev.horota.meltype.input.EditorPolicy
import java.io.File

/** Android 版だけの設定 (SharedPreferences)。判定・変換の細かい設定は本家と同じ config.json にある。 */
class MeltypePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("meltype", Context.MODE_PRIVATE)

    var codeApps: Set<String>
        get() = prefs.getStringSet(KEY_CODE_APPS, null)?.toSet() ?: EditorPolicy.DEFAULT_CODE_APPS
        set(value) = prefs.edit().putStringSet(KEY_CODE_APPS, value).apply()

    var shiftSpaceToggles: Boolean
        get() = prefs.getBoolean(KEY_SHIFT_SPACE, true)
        set(value) = prefs.edit().putBoolean(KEY_SHIFT_SPACE, value).apply()

    var ctrlSpaceToggles: Boolean
        get() = prefs.getBoolean(KEY_CTRL_SPACE, true)
        set(value) = prefs.edit().putBoolean(KEY_CTRL_SPACE, value).apply()

    /** 確定した文字が「()」のようなかっこの組なら、カーソルを間に置く。 */
    var cursorBetweenBrackets: Boolean
        get() = prefs.getBoolean(KEY_BRACKETS, true)
        set(value) = prefs.edit().putBoolean(KEY_BRACKETS, value).apply()

    /** 最近使った絵文字 (新しい順)。 */
    var recentEmoji: List<String>
        get() = EmojiData.parseRecent(prefs.getString(KEY_RECENT_EMOJI, null))
        set(value) = prefs.edit().putString(KEY_RECENT_EMOJI, EmojiData.formatRecent(value)).apply()

    /** オンにしているジャンル別辞書 ("builtin:it"、"user:名前")。 */
    var enabledGenres: Set<String>
        get() = prefs.getStringSet(KEY_GENRES, null)?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_GENRES, value).apply()

    /** 候補の配色。 */
    var themeMode: ThemeMode
        get() = ThemeMode.values().firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: ThemeMode.SYSTEM
        set(value) = prefs.edit().putString(KEY_THEME, value.name).apply()

    val bindingOptions: BindingOptions
        get() = BindingOptions(shiftSpaceToggles = shiftSpaceToggles, ctrlSpaceToggles = ctrlSpaceToggles)

    companion object {
        private const val KEY_CODE_APPS = "code_apps"
        private const val KEY_SHIFT_SPACE = "shift_space_toggles"
        private const val KEY_CTRL_SPACE = "ctrl_space_toggles"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_BRACKETS = "cursor_between_brackets"
        private const val KEY_RECENT_EMOJI = "recent_emoji"
        private const val KEY_GENRES = "enabled_genres"

        /** 本家の MELTYPE_DATA_DIR に渡す場所 (設定・学習・ユーザー辞書・ログ)。 */
        fun dataDirectory(context: Context): File = File(context.filesDir, "Meltype")
    }
}
