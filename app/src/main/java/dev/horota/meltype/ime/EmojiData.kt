// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

/** 絵文字の一覧パネルの中身と、絵文字かどうかの判定、最近使った絵文字の並べ方。Android に依存しない (JVM でテストする)。 */
object EmojiData {
    /** 最近使った絵文字を覚えておく数。 */
    const val MAX_RECENT = 32

    data class Category(val name: String, val items: List<String>)

    val categories: List<Category> = listOf(
        Category("顔", tokens("😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 😉 😊 😇 🥰 😍 🤩 😘 😗 😚 😙 😋 😛 😜 🤪 😝 🤑 🤗 🤭 🤫 🤔 🤐 😐 😑 😶 😏 😒 🙄 😬 🤥 😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🤧 🥵 🥶 🥴 😵 🤯 🤠 🥳 😎 🤓 🧐 😕 😟 🙁 😮 😯 😲 😳 🥺 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈 👿 💀 💩 🤡 👹 👺 👻 👽 👾 🤖")),
        Category("手", tokens("👍 👎 👌 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ✋ 🤚 🖖 👋 🤝 👏 🙌 👐 🤲 🙏 💪 👀 👂 👃 👄 🧠")),
        Category("ハート", tokens("❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 💕 💞 💓 💗 💖 💘 💝 💟 💯 💢 💥 💫 💦 💨 💬 💭 💤 ✨ ⭐ 🌟 🔥 🎉 🎊")),
        Category("動物", tokens("🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐔 🐧 🐦 🐤 🦆 🦉 🐺 🐗 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐜 🐢 🐍 🦎 🐙 🦑 🦐 🦀 🐡 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐅 🐆 🦓 🐘 🦏 🐪 🐫 🦒 🐃 🐄 🐎 🐖 🐏 🐑 🐕 🐩 🐈 🐓 🦃 🐇 🐁 🐀 🦔")),
        Category("自然", tokens("🌸 💮 🌹 🌺 🌻 🌼 🌷 🌱 🌲 🌳 🌴 🌵 🍀 🍁 🍂 🍃 🌾 🍄 🌍 🌙 ☀️ ⛅ ☁️ ⛈️ 🌈 ❄️ ☃️ ⛄ 🌊 🌋 🗻")),
        Category("食べ物", tokens("🍎 🍏 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🍈 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🍆 🥑 🥦 🥕 🌽 🥔 🍠 🍞 🥐 🥖 🧀 🥚 🍳 🥞 🥓 🥩 🍗 🍖 🌭 🍔 🍟 🍕 🥪 🌮 🌯 🍝 🍜 🍲 🍛 🍣 🍱 🥟 🍤 🍙 🍚 🍘 🍥 🍢 🍡 🍧 🍨 🍦 🥧 🍰 🎂 🍮 🍭 🍬 🍫 🍿 🍩 🍪 ☕ 🍵 🍶 🍺 🍻 🥂 🍷 🥃 🍸 🍹 🍾")),
        Category("活動・物", tokens("⚽ 🏀 🏈 ⚾ 🎾 🏐 🏉 🎱 🏓 🏸 🥊 🎯 ⛳ 🎣 🎮 🎲 🎸 🎹 🥁 🎤 🎧 🎬 🎨 📚 📖 ✏️ 📝 💻 🖥️ ⌨️ 📱 ☎️ 📷 📹 📺 📻 ⏰ 💡 🔋 🔌 💰 💳 🎁 🎈 🔑 🔒 🔔 📢 📌 📎 ✂️ 🔍 🧪 💊")),
        Category("乗り物・場所", tokens("🚗 🚕 🚌 🚎 🚓 🚑 🚒 🚚 🚜 🚲 🛵 🚃 🚄 🚅 🚉 ✈️ 🚀 🛸 🚁 ⛵ 🚤 🚢 ⚓ 🏠 🏡 🏢 🏣 🏥 🏦 🏨 🏪 🏫 ⛪ 🗼 🗽 🎡 🎢 ⛲ ⛺ 🌃 🌆 🌇 🌉")),
        Category("記号", tokens("✅ ❌ ⭕ ❓ ❗ ⚠️ ⛔ 🚫 ♻️ ➕ ➖ ➗ ➡️ ⬅️ ⬆️ ⬇️ ↔️ ↕️ ↩️ ↪️ 🔄 🔃 🔙 🔚 🔛 🔜 🔝 ▶️ ⏸️ ⏹️ ⏭️ ⏮️ 🔀 🔁 🔂 ℹ️ 🆗 🆕 🆙 🆒 🆓 🔴 🟠 🟡 🟢 🔵 🟣 ⚫ ⚪ 🟥 🟦 🇯🇵 🇺🇸 🇬🇧 🇰🇷 🇨🇳")),
    )

    private fun tokens(text: String): List<String> = text.split(' ').filter { it.isNotEmpty() }

    /**
     * 絵文字の文字を含むか。候補の中から絵文字だけを選ぶのに使う。
     * Unicode の絵文字のうち、補助面 (U+1F000 以降) と、BMP の絵文字 (記号・ドリンクなど) を数える。
     * ★ ♪ のような、ふつうの記号の文字は数えない。
     */
    fun isEmoji(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (isEmojiCodePoint(cp)) return true
            i += Character.charCount(cp)
        }
        return false
    }

    private fun isEmojiCodePoint(cp: Int): Boolean {
        if (cp in 0x1F000..0x1FAFF) return true
        if (cp == 0xFE0F || cp == 0x20E3) return true // 絵文字表示の指定・囲み数字
        return BMP_EMOJI.any { cp in it }
    }

    private val BMP_EMOJI: List<IntRange> = listOf(
        0x203C..0x203C, 0x2049..0x2049, 0x2122..0x2122, 0x2139..0x2139, 0x2194..0x2199, 0x21A9..0x21AA,
        0x231A..0x231B, 0x2328..0x2328,
        0x23CF..0x23CF, 0x23E9..0x23F3, 0x23F8..0x23FA, 0x24C2..0x24C2, 0x25AA..0x25AB, 0x25B6..0x25B6,
        0x25C0..0x25C0, 0x25FB..0x25FE, 0x2600..0x2604, 0x260E..0x260E, 0x2611..0x2611, 0x2614..0x2615,
        0x2618..0x2618, 0x261D..0x261D, 0x2620..0x2620, 0x2622..0x2623, 0x2626..0x2626, 0x262A..0x262A,
        0x262E..0x262F, 0x2638..0x263A, 0x2640..0x2640, 0x2642..0x2642, 0x2648..0x2653, 0x265F..0x2660,
        0x2663..0x2663, 0x2665..0x2666, 0x2668..0x2668, 0x267B..0x267B, 0x267E..0x267F, 0x2692..0x2697,
        0x2699..0x2699, 0x269B..0x269C, 0x26A0..0x26A1, 0x26A7..0x26A7, 0x26AA..0x26AB, 0x26B0..0x26B1,
        0x26BD..0x26BE, 0x26C4..0x26C5, 0x26C8..0x26C8, 0x26CE..0x26CF, 0x26D1..0x26D1, 0x26D3..0x26D4,
        0x26E9..0x26EA, 0x26F0..0x26F5, 0x26F7..0x26FA, 0x26FD..0x26FD, 0x2702..0x2702, 0x2705..0x2705,
        0x2708..0x270D, 0x270F..0x270F, 0x2712..0x2712, 0x2714..0x2714, 0x2716..0x2716, 0x271D..0x271D,
        0x2721..0x2721, 0x2728..0x2728, 0x2733..0x2734, 0x2744..0x2744, 0x2747..0x2747, 0x274C..0x274C,
        0x274E..0x274E, 0x2753..0x2755, 0x2757..0x2757, 0x2763..0x2764, 0x2795..0x2797, 0x27A1..0x27A1,
        0x27B0..0x27B0, 0x27BF..0x27BF, 0x2934..0x2935, 0x2B05..0x2B07, 0x2B1B..0x2B1C, 0x2B50..0x2B50,
        0x2B55..0x2B55, 0x3030..0x3030, 0x303D..0x303D, 0x3297..0x3297, 0x3299..0x3299,
    )

    /** 最近使った絵文字の並びに emoji を足す (先頭に置き、前にあった分は消す)。 */
    fun pushRecent(recent: List<String>, emoji: String, max: Int = MAX_RECENT): List<String> =
        (listOf(emoji) + recent.filter { it != emoji }).take(max)

    /** 保存用の文字列 (空白区切り) と元の並び。絵文字には空白が入らない。 */
    fun formatRecent(recent: List<String>): String = recent.joinToString(" ")

    fun parseRecent(text: String?): List<String> = tokens(text.orEmpty()).take(MAX_RECENT)

    /** candidates のうち絵文字のものの番号を、最近使ったものが先になるように並べる (元の並びは崩さない)。 */
    fun emojiOrder(candidates: List<String>, recent: List<String>): List<Int> {
        val indices = candidates.indices.filter { isEmoji(candidates[it]) }
        return indices.sortedBy { index ->
            val rank = recent.indexOf(candidates[index])
            if (rank >= 0) rank else Int.MAX_VALUE
        }
    }
}

/**
 * 絵文字パネルの選択位置。タブ (最近・カテゴリ) と、その中の何番目か。キーの操作はここで決める。
 * columns は 1 行に並べる数。
 */
class EmojiPanelState(recent: List<String>, val columns: Int = COLUMNS) {
    class Tab(val name: String, val items: List<String>)

    val tabs: List<Tab> = buildList {
        if (recent.isNotEmpty()) add(Tab("最近", recent))
        EmojiData.categories.forEach { add(Tab(it.name, it.items)) }
    }

    var tab: Int = 0
        private set
    var index: Int = 0
        private set

    val items: List<String> get() = tabs[tab].items

    fun current(): String? = items.getOrNull(index)

    fun moveLeft() {
        index = maxOf(0, index - 1)
    }

    fun moveRight() {
        index = minOf(items.size - 1, index + 1)
    }

    fun moveUp() {
        if (index - columns >= 0) index -= columns
    }

    fun moveDown() {
        val next = index + columns
        index = when {
            next < items.size -> next
            index / columns < (items.size - 1) / columns -> items.size - 1 // 最後の行が欠けているとき、その行の最後へ
            else -> index
        }
    }

    fun nextTab() = selectTab((tab + 1) % tabs.size)

    fun previousTab() = selectTab((tab - 1 + tabs.size) % tabs.size)

    fun selectTab(newTab: Int) {
        tab = newTab.coerceIn(0, tabs.size - 1)
        index = 0
    }

    fun select(newTab: Int, newIndex: Int) {
        selectTab(newTab)
        index = newIndex.coerceIn(0, maxOf(0, items.size - 1))
    }

    companion object {
        const val COLUMNS = 8
    }
}
