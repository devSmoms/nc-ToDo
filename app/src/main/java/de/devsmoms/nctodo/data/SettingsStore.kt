package de.devsmoms.nctodo.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Hell"), DARK("Dunkel") }

enum class OpenSync(val label: String) {
    ALWAYS("Immer beim Öffnen"),
    AFTER_INACTIVITY("Nach Pause"),
    NEVER("Nie"),
}

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Seconds between syncs while the app is visible. 0 = off. */
    val foregroundIntervalSec: Int = 60,
    /** Minutes between background syncs (WorkManager, at least 15). 0 = off. */
    val backgroundIntervalMin: Int = 30,
    val openSync: OpenSync = OpenSync.AFTER_INACTIVITY,
    /** Pause after which reopening the app triggers a sync (with [OpenSync.AFTER_INACTIVITY]). */
    val inactivityMin: Int = 5,
    val notifyDue: Boolean = false,
    /** Minutes after midnight for the daily "due today" reminder. */
    val dueTimeMinutes: Int = 8 * 60,
    val notifySharedChanges: Boolean = false,
    /** Screen shown when the app starts: "last", "smart:<SmartList>" or "list:<url>". */
    val startScreen: String = START_LAST,
    /** New due dates are all-day unless a time is picked explicitly. */
    val allDayDefault: Boolean = true,
    /** Load title/image previews for links (contacts the linked websites). */
    val linkPreviews: Boolean = true,
    /** Ask before removing a link, a due date or a step. */
    val confirmRemovals: Boolean = true,
    /** Hide the lists Nextcloud Deck creates for its boards ("Deck: …"). */
    val hideDeckLists: Boolean = true,
    /** List groups collapsed in the menu. */
    val collapsedGroups: Set<String> = emptySet(),
    /** Groups created in the menu that contain no list yet (only kept on this device). */
    val emptyGroups: Set<String> = emptySet(),
) {
    companion object {
        const val START_LAST = "last"
        val FOREGROUND_OPTIONS = listOf(0, 30, 60, 120, 300, 900)
        val BACKGROUND_OPTIONS = listOf(0, 15, 30, 60, 180, 360, 720)
        val INACTIVITY_OPTIONS = listOf(1, 5, 15, 30, 60)

        fun seconds(s: Int) = when {
            s == 0 -> "Aus"
            s < 60 -> "$s Sekunden"
            s == 60 -> "1 Minute"
            else -> "${s / 60} Minuten"
        }

        fun minutes(m: Int) = when {
            m == 0 -> "Aus"
            m < 60 -> "$m Minuten"
            m == 60 -> "1 Stunde"
            else -> "${m / 60} Stunden"
        }
    }
}

/** Non-sensitive preferences of one account. */
class SettingsStore(context: Context, private val prefsName: String) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings

    private fun read(): Settings {
        val d = Settings()
        return Settings(
            theme = enumOr(prefs.getString("theme", null), d.theme),
            foregroundIntervalSec = prefs.getInt("fgInterval", d.foregroundIntervalSec),
            backgroundIntervalMin = prefs.getInt("bgInterval", d.backgroundIntervalMin),
            openSync = enumOr(prefs.getString("openSync", null), d.openSync),
            inactivityMin = prefs.getInt("inactivity", d.inactivityMin),
            notifyDue = prefs.getBoolean("notifyDue", d.notifyDue),
            dueTimeMinutes = prefs.getInt("dueTime", d.dueTimeMinutes).coerceIn(0, 24 * 60 - 1),
            notifySharedChanges = prefs.getBoolean("notifyShared", d.notifySharedChanges),
            startScreen = prefs.getString("startScreen", null) ?: d.startScreen,
            allDayDefault = prefs.getBoolean("allDayDefault", d.allDayDefault),
            linkPreviews = prefs.getBoolean("linkPreviews", d.linkPreviews),
            confirmRemovals = prefs.getBoolean("confirmRemovals", d.confirmRemovals),
            hideDeckLists = prefs.getBoolean("hideDeckLists", d.hideDeckLists),
            collapsedGroups = prefs.getStringSet("collapsedGroups", null)?.toSet() ?: d.collapsedGroups,
            emptyGroups = prefs.getStringSet("emptyGroups", null)?.toSet() ?: d.emptyGroups,
        )
    }

    fun update(change: (Settings) -> Settings) {
        val s = change(_settings.value)
        prefs.edit()
            .putString("theme", s.theme.name)
            .putInt("fgInterval", s.foregroundIntervalSec)
            .putInt("bgInterval", s.backgroundIntervalMin)
            .putString("openSync", s.openSync.name)
            .putInt("inactivity", s.inactivityMin)
            .putBoolean("notifyDue", s.notifyDue)
            .putInt("dueTime", s.dueTimeMinutes)
            .putBoolean("notifyShared", s.notifySharedChanges)
            .putString("startScreen", s.startScreen)
            .putBoolean("allDayDefault", s.allDayDefault)
            .putBoolean("linkPreviews", s.linkPreviews)
            .putBoolean("confirmRemovals", s.confirmRemovals)
            .putBoolean("hideDeckLists", s.hideDeckLists)
            // Always a fresh set: SharedPreferences must not hold on to a set it handed out.
            .putStringSet("collapsedGroups", HashSet(s.collapsedGroups))
            .putStringSet("emptyGroups", HashSet(s.emptyGroups))
            .apply()
        _settings.value = s
    }

    /** Last opened list, kept outside [settings] so navigating does not reschedule background work. */
    var lastSelection: String?
        get() = prefs.getString("lastSelection", null)
        set(value) { prefs.edit().putString("lastSelection", value).apply() }

    /** Deletes all settings of this account including the preferences file. */
    fun delete() {
        prefs.edit().clear().commit()
        app.deleteSharedPreferences(prefsName)
        _settings.value = Settings()
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
