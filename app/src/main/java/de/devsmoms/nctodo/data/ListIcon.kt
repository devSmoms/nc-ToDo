package de.devsmoms.nctodo.data

/**
 * List icons are stored as a leading emoji in the list name (e.g. "🛒 Einkauf"), like
 * Microsoft To Do does. CalDAV has no icon property, but the display name syncs everywhere,
 * so the icon shows up on all devices and in the Nextcloud web interface.
 */
object ListIcon {
    private const val MAX_EMOJI_CHARS = 16

    /** Splits "🛒 Einkauf" into ("🛒", "Einkauf"). Returns (null, name) if there is no leading emoji. */
    fun split(name: String): Pair<String?, String> {
        val trimmed = name.trimStart()
        if (trimmed.isEmpty() || !isEmojiStart(trimmed.codePointAt(0))) return null to name.trim()
        val end = trimmed.indexOfFirst { it.isWhitespace() }.let { if (it < 0) trimmed.length else it }
        val token = trimmed.substring(0, end)
        if (token.length > MAX_EMOJI_CHARS || !token.codePoints().allMatch { isEmojiPart(it) }) return null to name.trim()
        val rest = trimmed.substring(end).trim()
        return token to rest
    }

    /** Name without the icon, for places where only text fits. */
    fun plainName(name: String): String = split(name).second.ifEmpty { name.trim() }

    fun join(emoji: String?, title: String): String =
        if (emoji.isNullOrBlank()) title.trim() else "$emoji ${title.trim()}"

    private fun isEmojiStart(cp: Int) =
        cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x2300..0x23FF || cp in 0x2B00..0x2BFF ||
            cp == 0x00A9 || cp == 0x00AE || cp == 0x203C || cp == 0x2049 || cp in 0x2190..0x21FF

    private fun isEmojiPart(cp: Int) =
        isEmojiStart(cp) || cp == 0x200D || cp == 0xFE0F || cp == 0xFE0E || cp == 0x20E3 || cp in 0xE0020..0xE007F
}
