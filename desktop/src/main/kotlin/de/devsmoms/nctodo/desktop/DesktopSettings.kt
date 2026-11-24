package de.devsmoms.nctodo.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Hell"), DARK("Dunkel") }

/** Settings of the desktop app; stored next to the offline cache (no secrets in here). */
data class DesktopSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Own name for the account shown in the app; blank = name from Nextcloud. */
    val accountName: String = "",
    val hideDeckLists: Boolean = true,
    val linkPreviews: Boolean = true,
    val confirmRemovals: Boolean = true,
    /** Sync interval while the app is open, in seconds; 0 = only manually / after changes. */
    val syncIntervalSec: Int = 60,
    /** Base text size in px (Windows apps use 14, browsers 15–16). */
    val fontSize: Int = 15,
    /** Greyscale smoothing like browsers instead of ClearType; applied at start. */
    val grayscaleText: Boolean = true,
    val collapsedGroups: Set<String> = emptySet(),
    /** Groups created here that contain no list yet (a group only exists on the server through its lists). */
    val emptyGroups: Set<String> = emptySet(),
) {
    companion object {
        val SYNC_OPTIONS = listOf(30, 60, 300, 900, 0)
        val FONT_SIZES = listOf(14, 15, 16, 17)

        fun fontLabel(px: Int) = when (px) {
            14 -> "Klein"
            15 -> "Normal"
            16 -> "Groß"
            else -> "Sehr groß"
        }

        fun syncLabel(sec: Int): String = when {
            sec == 0 -> "Nur manuell"
            sec < 60 -> "$sec Sekunden"
            sec == 60 -> "1 Minute"
            else -> "${sec / 60} Minuten"
        }
    }
}

class DesktopSettingsStore(private val file: Path) {
    fun load(): DesktopSettings {
        if (!Files.exists(file)) return DesktopSettings()
        val p = Properties()
        runCatching { Files.newInputStream(file).use { p.load(it) } }.onFailure { return DesktopSettings() }
        val d = DesktopSettings()
        fun bool(k: String, def: Boolean) = p.getProperty(k)?.toBooleanStrictOrNull() ?: def
        fun set(k: String) = p.getProperty(k).orEmpty().split('\u001F').filter { it.isNotBlank() }.toSet()
        return DesktopSettings(
            theme = p.getProperty("theme")?.let { v -> ThemeMode.entries.firstOrNull { it.name == v } } ?: d.theme,
            accountName = p.getProperty("accountName").orEmpty(),
            hideDeckLists = bool("hideDeckLists", d.hideDeckLists),
            linkPreviews = bool("linkPreviews", d.linkPreviews),
            confirmRemovals = bool("confirmRemovals", d.confirmRemovals),
            syncIntervalSec = p.getProperty("syncIntervalSec")?.toIntOrNull()?.takeIf { it in DesktopSettings.SYNC_OPTIONS } ?: d.syncIntervalSec,
            fontSize = p.getProperty("fontSize")?.toIntOrNull()?.takeIf { it in DesktopSettings.FONT_SIZES } ?: d.fontSize,
            grayscaleText = bool("grayscaleText", d.grayscaleText),
            collapsedGroups = set("collapsedGroups"),
            emptyGroups = set("emptyGroups"),
        )
    }

    fun save(s: DesktopSettings) {
        val p = Properties()
        p["theme"] = s.theme.name
        p["accountName"] = s.accountName.trim()
        p["hideDeckLists"] = s.hideDeckLists.toString()
        p["linkPreviews"] = s.linkPreviews.toString()
        p["confirmRemovals"] = s.confirmRemovals.toString()
        p["syncIntervalSec"] = s.syncIntervalSec.toString()
        p["fontSize"] = s.fontSize.toString()
        p["grayscaleText"] = s.grayscaleText.toString()
        p["collapsedGroups"] = s.collapsedGroups.joinToString("\u001F")
        p["emptyGroups"] = s.emptyGroups.joinToString("\u001F")
        val out = java.io.ByteArrayOutputStream()
        p.store(out, "NC Todo desktop settings")
        writeAtomically(file, out.toByteArray())
    }

    fun delete() {
        Files.deleteIfExists(file)
    }
}

/** App version, written into the resources by the build (see desktop/build.gradle.kts). */
val appVersion: String by lazy {
    Thread.currentThread().contextClassLoader.getResourceAsStream("nctodo-version.txt")?.use { it.readBytes().decodeToString().trim() }
        ?: System.getProperty("jpackage.app-version") ?: "dev"
}
