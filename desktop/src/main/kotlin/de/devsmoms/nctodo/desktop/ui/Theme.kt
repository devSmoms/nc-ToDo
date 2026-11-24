package de.devsmoms.nctodo.desktop.ui

import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.FlatLightLaf
import de.devsmoms.nctodo.desktop.ThemeMode
import java.awt.Color
import java.awt.Font
import javax.swing.UIManager

/**
 * Colours and fonts of the Swing UI. Text is drawn by Java2D with the system's font
 * smoothing (ClearType on Windows) and whole-pixel glyph positions, so it is as sharp as
 * in other Windows programs.
 */
object Theme {
    var dark = false
        private set

    /** Text size chosen in the settings, relative to the 14 px the layout was designed for. */
    var scale = 15f / 14f
        private set

    /** A layout size (px) scaled with the text size. */
    fun px(n: Int): Int = Math.round(n * scale)

    // Light: neutral greys like Windows apps; dark: like the dark mode of Microsoft To Do.
    val accent get() = if (dark) Color(0x8AB4F8) else Color(0x2564CF)
    val background get() = if (dark) Color(0x1C1C1C) else Color(0xFAFAFA)
    val sidebar get() = if (dark) Color(0x202020) else Color(0xF3F3F3)
    val card get() = if (dark) Color(0x2B2B2B) else Color(0xFFFFFF)
    val cardHover get() = if (dark) Color(0x323232) else Color(0xF5F7FB)
    val cardSelected get() = if (dark) Color(0x2F3B52) else Color(0xE3EAF8)
    val navHover get() = if (dark) Color(0x2A2A2A) else Color(0xE9E9E9)
    val navSelected get() = if (dark) Color(0x2F3B52) else Color(0xDCE6F8)
    val field get() = if (dark) Color(0x2B2B2B) else Color(0xE9E9E9)
    val text get() = if (dark) Color(0xFFFFFF) else Color(0x111111)
    val subtext get() = if (dark) Color(0xC8C8C8) else Color(0x555555)
    val divider get() = if (dark) Color(0x3A3A3A) else Color(0xDDDDDD)
    val error get() = if (dark) Color(0xFFB4AB) else Color(0xB3261E)

    val sun = Color(0x9E7B00)
    val star = Color(0xC2185B)
    val planned = Color(0x00897B)
    val blue = Color(0x2564CF)

    /** Installs FlatLaf in light or dark; call [FlatLaf.updateUI] afterwards when switching at runtime. */
    fun install(mode: ThemeMode, fontSize: Int) {
        scale = fontSize / 14f
        dark = when (mode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> SystemTheme.isDark()
        }
        FlatLaf.setGlobalExtraDefaults(
            mapOf(
                "@accentColor" to hex(accent),
                // Absolute size in px (FlatLaf's default follows the 9 pt system font, i.e. 12 px).
                "defaultFont" to fontSize.toString(),
                "Component.arc" to "8",
                "Button.arc" to "8",
                "TextComponent.arc" to "8",
                "ScrollBar.thumbArc" to "999",
                "ScrollBar.thumbInsets" to "2,2,2,2",
                "ScrollBar.width" to "10",
                "PopupMenu.borderCornerRadius" to "8",
                "MenuItem.selectionArc" to "6",
                "Panel.background" to hex(background),
            ),
        )
        if (dark) FlatDarkLaf.setup() else FlatLightLaf.setup()
    }

    private fun hex(c: Color) = "#%02X%02X%02X".format(c.red, c.green, c.blue)

    private fun base(): Font = UIManager.getFont("defaultFont") ?: Font(Font.SANS_SERIF, Font.PLAIN, 14)

    /** [size] is given for 14 px text and scaled with the chosen text size. */
    fun font(size: Float = 14f, bold: Boolean = false): Font =
        if (bold) semibold(size) else base().deriveFont(Font.PLAIN, size * scale)

    /** Semibold where the system has it (Segoe UI Semibold on Windows), otherwise bold. */
    fun semibold(size: Float): Font = (UIManager.getFont("semibold.font") ?: base().deriveFont(Font.BOLD)).deriveFont(size * scale)

    fun heading(size: Float): Font = base().deriveFont(Font.BOLD, size * scale)
}

/** Reads whether the operating system uses dark mode. */
object SystemTheme {
    fun isDark(): Boolean = runCatching {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        when {
            os.contains("win") -> {
                val out = run("reg", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", "/v", "AppsUseLightTheme")
                Regex("AppsUseLightTheme\\s+REG_DWORD\\s+0x0+\\b").containsMatchIn(out)
            }
            os.contains("linux") -> {
                run("gsettings", "get", "org.gnome.desktop.interface", "color-scheme").contains("dark") ||
                    run("gsettings", "get", "org.gnome.desktop.interface", "gtk-theme").contains("dark", ignoreCase = true)
            }
            else -> false
        }
    }.getOrDefault(false)

    private fun run(vararg cmd: String): String {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        return out
    }
}

fun parseColor(hex: String?): Color = runCatching {
    Color(hex!!.removePrefix("#").take(6).toInt(16))
}.getOrDefault(Theme.blue)
