package de.devsmoms.nctodo.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.ViewKanban
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.devsmoms.nctodo.BuildConfig
import de.devsmoms.nctodo.data.OpenSync
import de.devsmoms.nctodo.data.Settings
import de.devsmoms.nctodo.data.ThemeMode
import de.devsmoms.nctodo.ui.theme.parseColor
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val SOURCE_URL = "https://github.com/devSmoms/nc-ToDo"
private const val LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"
private const val APACHE_URL = "https://www.apache.org/licenses/LICENSE-2.0"
private val LIBRARIES = listOf(
    "AndroidX (Core, Activity, Lifecycle, DataStore)",
    "Jetpack Compose & Material 3",
    "Material Icons",
    "Jetpack Glance (Widgets)",
    "WorkManager",
    "Kotlin & kotlinx.coroutines",
    "OkHttp & Okio",
)

/** Same accent colors as the smart lists in the drawer. */
private val SunColor = Color(0xFF9E7B00)
private val StarColor = Color(0xFFC2185B)
private val PlannedColor = Color(0xFF00897B)
private val HomeColor = Color(0xFF2564CF)

/** One entry of the start screen picker. */
private data class StartOption(val value: String, val label: String, val icon: ImageVector, val tint: Color?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: UiState, vm: AppViewModel, snackbar: SnackbarHostState) {
    val s = state.settings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = MaterialTheme.colorScheme.primary
    var pickTime by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var showLicense by remember { mutableStateOf(false) }
    var showLibraries by remember { mutableStateOf(false) }

    // Turning on a notification type first asks for the Android 13+ permission.
    var pendingEnable by remember { mutableStateOf<((Settings) -> Settings)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val change = pendingEnable
        pendingEnable = null
        if (granted && change != null) vm.updateSettings(change)
        else if (!granted) scope.launch { snackbar.showSnackbar("Ohne Berechtigung kann die App keine Benachrichtigungen anzeigen") }
    }
    fun enableNotification(change: (Settings) -> Settings) {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingEnable = change
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.updateSettings(change)
        }
    }

    // Start screen choices: last opened, the smart lists, then every list.
    val startOptions = buildList {
        add(StartOption(Settings.START_LAST, "Zuletzt geöffnet", Icons.Outlined.History, null))
        add(StartOption(Selection.Smart(SmartList.MY_DAY).encode(), SmartList.MY_DAY.title, Icons.Outlined.WbSunny, SunColor))
        add(StartOption(Selection.Smart(SmartList.IMPORTANT).encode(), SmartList.IMPORTANT.title, Icons.Outlined.StarBorder, StarColor))
        add(StartOption(Selection.Smart(SmartList.PLANNED).encode(), SmartList.PLANNED.title, Icons.Outlined.CalendarMonth, PlannedColor))
        add(StartOption(Selection.Smart(SmartList.ALL).encode(), SmartList.ALL.title, Icons.Outlined.Home, HomeColor))
        state.lists.forEach {
            add(StartOption(Selection.OfList(it.url).encode(), ListIcons.label(it.name), ListIcons.iconOf(it.name) ?: ListIcons.DEFAULT, parseColor(it.color)))
        }
    }
    val currentStart = startOptions.firstOrNull { it.value == s.startScreen }
        ?: StartOption(s.startScreen, "Liste nicht mehr vorhanden", ListIcons.DEFAULT, null)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                // Same big header as the task lists.
                Text(
                    "Einstellungen",
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = accent,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }

            // ------------------------------------------------ start & appearance
            item { SectionLabel("Allgemein") }
            item {
                SettingsCard {
                    PickerRow(
                        icon = currentStart.icon, tint = currentStart.tint ?: accent,
                        title = "Startbildschirm",
                        subtitle = "Womit sich die App öffnet",
                        value = currentStart.label,
                        options = startOptions.map { Triple(it.label, it.icon, it.tint ?: accent) to it.value },
                    ) { v -> vm.updateSettings { it.copy(startScreen = v) } }
                    RowDivider()
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
                        Icon(Icons.Outlined.Palette, null, tint = accent)
                        Spacer(Modifier.width(16.dp))
                        SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                            ThemeMode.entries.forEachIndexed { i, mode ->
                                SegmentedButton(
                                    selected = s.theme == mode,
                                    onClick = { vm.updateSettings { it.copy(theme = mode) } },
                                    shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                                ) { Text(mode.label, maxLines = 1) }
                            }
                        }
                    }
                }
            }

            // ------------------------------------------------ tasks
            item { SectionLabel("Aufgaben") }
            item {
                SettingsCard {
                    SettingRow(
                        icon = Icons.Outlined.CalendarMonth, tint = PlannedColor,
                        title = "Fälligkeit standardmäßig ganztägig",
                        subtitle = if (s.allDayDefault) "Uhrzeit nur, wenn du „Ganztägig“ ausschaltest" else "Nach dem Datum wird immer nach einer Uhrzeit gefragt",
                        onClick = { vm.updateSettings { it.copy(allDayDefault = !it.allDayDefault) } },
                        trailing = { Switch(checked = s.allDayDefault, onCheckedChange = { on -> vm.updateSettings { it.copy(allDayDefault = on) } }) },
                    )
                    RowDivider()
                    SettingRow(
                        icon = Icons.AutoMirrored.Outlined.HelpOutline, tint = accent,
                        title = "Vor dem Entfernen nachfragen",
                        subtitle = "Rückfrage beim Entfernen von Links, Fälligkeit und Schritten",
                        onClick = { vm.updateSettings { it.copy(confirmRemovals = !it.confirmRemovals) } },
                        trailing = { Switch(checked = s.confirmRemovals, onCheckedChange = { on -> vm.updateSettings { it.copy(confirmRemovals = on) } }) },
                    )
                    RowDivider()
                    SettingRow(
                        icon = Icons.Outlined.ViewKanban, tint = accent,
                        title = "Deck-Listen ausblenden",
                        subtitle = "Listen, deren Name mit „Deck:“ beginnt (Boards aus Nextcloud Deck)",
                        onClick = { vm.updateSettings { it.copy(hideDeckLists = !it.hideDeckLists) } },
                        trailing = { Switch(checked = s.hideDeckLists, onCheckedChange = { on -> vm.updateSettings { it.copy(hideDeckLists = on) } }) },
                    )
                    RowDivider()
                    SettingRow(
                        icon = Icons.Outlined.Link, tint = accent,
                        title = "Link-Vorschau laden",
                        subtitle = "Titel und Bild der verlinkten Seite anzeigen – ruft dazu die Webseite auf",
                        onClick = { vm.updateSettings { it.copy(linkPreviews = !it.linkPreviews) } },
                        trailing = { Switch(checked = s.linkPreviews, onCheckedChange = { on -> vm.updateSettings { it.copy(linkPreviews = on) } }) },
                    )
                }
            }

            // ------------------------------------------------ sync
            item { SectionLabel("Synchronisierung") }
            item {
                SettingsCard {
                    PickerRow(
                        icon = Icons.Outlined.PhoneAndroid, tint = accent,
                        title = "Beim Öffnen der App",
                        value = s.openSync.label,
                        options = OpenSync.entries.map { Triple(it.label, null, null) to it },
                    ) { v -> vm.updateSettings { it.copy(openSync = v) } }
                    if (s.openSync == OpenSync.AFTER_INACTIVITY) {
                        RowDivider()
                        PickerRow(
                            icon = Icons.Outlined.Bedtime, tint = accent,
                            title = "Pause ab",
                            subtitle = "Synchronisiert, wenn die App so lange nicht benutzt wurde",
                            value = Settings.minutes(s.inactivityMin),
                            options = Settings.INACTIVITY_OPTIONS.map { Triple(Settings.minutes(it), null, null) to it },
                        ) { v -> vm.updateSettings { it.copy(inactivityMin = v) } }
                    }
                    RowDivider()
                    PickerRow(
                        icon = Icons.Outlined.Timer, tint = accent,
                        title = "Während die App geöffnet ist",
                        subtitle = "Regelmäßig nach Änderungen anderer suchen",
                        value = Settings.seconds(s.foregroundIntervalSec),
                        options = Settings.FOREGROUND_OPTIONS.map { Triple(Settings.seconds(it), null, null) to it },
                    ) { v -> vm.updateSettings { it.copy(foregroundIntervalSec = v) } }
                    RowDivider()
                    PickerRow(
                        icon = Icons.Outlined.CloudSync, tint = accent,
                        title = "Im Hintergrund",
                        subtitle = "Android erlaubt höchstens alle 15 Minuten",
                        value = Settings.minutes(s.backgroundIntervalMin),
                        options = Settings.BACKGROUND_OPTIONS.map { Triple(Settings.minutes(it), null, null) to it },
                    ) { v -> vm.updateSettings { it.copy(backgroundIntervalMin = v) } }
                    RowDivider()
                    val last = state.lastSync?.let {
                        DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm").format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
                    }
                    SettingRow(
                        icon = Icons.Outlined.Sync, tint = accent,
                        title = if (state.syncing) "Synchronisiere …" else "Zuletzt: ${last ?: "noch nie"}",
                        subtitle = when {
                            state.pendingCount > 0 -> "${state.pendingCount} Änderung(en) warten auf Übertragung" + if (state.offline) " (offline)" else ""
                            state.offline -> "Server nicht erreichbar – Offline-Daten werden angezeigt"
                            else -> "Alles übertragen"
                        },
                        trailing = { TextButton(onClick = vm::refresh, enabled = !state.syncing) { Text("Jetzt") } },
                    )
                }
                Hint("Eigene Änderungen werden immer sofort übertragen – ohne Verbindung automatisch, sobald wieder Netz da ist.")
            }

            // ------------------------------------------------ notifications
            item { SectionLabel("Benachrichtigungen") }
            item {
                val time = "%02d:%02d".format(s.dueTimeMinutes / 60, s.dueTimeMinutes % 60)
                SettingsCard {
                    SettingRow(
                        icon = Icons.Outlined.Notifications, tint = SunColor,
                        title = "Fällige Aufgaben",
                        subtitle = "Täglich an heute fällige Aufgaben erinnern",
                        onClick = { if (!s.notifyDue) enableNotification { it.copy(notifyDue = true) } else vm.updateSettings { it.copy(notifyDue = false) } },
                        trailing = {
                            Switch(checked = s.notifyDue, onCheckedChange = { on ->
                                if (on) enableNotification { it.copy(notifyDue = true) } else vm.updateSettings { it.copy(notifyDue = false) }
                            })
                        },
                    )
                    if (s.notifyDue) {
                        RowDivider()
                        SettingRow(
                            icon = Icons.Outlined.Schedule, tint = accent,
                            title = "Uhrzeit", onClick = { pickTime = true },
                            trailing = { ValueText("$time Uhr") },
                        )
                    }
                    RowDivider()
                    SettingRow(
                        icon = Icons.Outlined.People, tint = PlannedColor,
                        title = "Änderungen in geteilten Listen",
                        subtitle = "Wenn andere Aufgaben hinzufügen oder erledigen",
                        onClick = {
                            if (!s.notifySharedChanges) enableNotification { it.copy(notifySharedChanges = true) }
                            else vm.updateSettings { it.copy(notifySharedChanges = false) }
                        },
                        trailing = {
                            Switch(checked = s.notifySharedChanges, onCheckedChange = { on ->
                                if (on) enableNotification { it.copy(notifySharedChanges = true) } else vm.updateSettings { it.copy(notifySharedChanges = false) }
                            })
                        },
                    )
                    RowDivider()
                    SettingRow(
                        icon = Icons.AutoMirrored.Outlined.OpenInNew, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = "Systemeinstellungen",
                        subtitle = "Ton, Vibration und Sperrbildschirm",
                        onClick = {
                            val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                            runCatching { context.startActivity(intent) }
                        },
                    )
                }
            }

            // ------------------------------------------------ account
            item { SectionLabel("Konto") }
            item {
                SettingsCard {
                    SettingRow(
                        icon = Icons.Outlined.Person, tint = accent,
                        title = state.credentials?.displayName.orEmpty(),
                        subtitle = "${state.credentials?.username} · ${state.credentials?.serverUrl?.removePrefix("https://")}",
                    )
                    RowDivider()
                    SettingRow(
                        icon = Icons.Outlined.Lock, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = "Verschlüsselt gespeichert",
                        subtitle = "Zugangsdaten und Offline-Daten sind mit einem Hardware-Schlüssel dieses Geräts geschützt",
                    )
                    RowDivider()
                    SettingRow(
                        icon = Icons.AutoMirrored.Outlined.Logout, tint = MaterialTheme.colorScheme.error,
                        title = "Abmelden", titleColor = MaterialTheme.colorScheme.error,
                        onClick = { confirmLogout = true },
                    )
                }
            }

            // ------------------------------------------------ about (GPL: show license and source)
            item { SectionLabel("Über NC Todo") }
            item {
                SettingsCard {
                    SettingRow(
                        icon = Icons.Outlined.Info, tint = accent,
                        title = "Version",
                        trailing = { ValueText("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") },
                    )
                    RowDivider()
                    SettingRow(
                        icon = Icons.Outlined.Code, tint = accent,
                        title = "Quellcode auf GitHub",
                        subtitle = SOURCE_URL.removePrefix("https://"),
                        onClick = { openLink(context, SOURCE_URL) },
                    )
                    RowDivider()
                    SettingRow(
                        icon = Icons.Outlined.Gavel, tint = accent,
                        title = "Lizenz: GNU GPL v3",
                        subtitle = "Freie Software – Bedingungen anzeigen",
                        onClick = { showLicense = true },
                    )
                    RowDivider()
                    SettingRow(
                        icon = Icons.Outlined.Widgets, tint = accent,
                        title = "Verwendete Open-Source-Bibliotheken",
                        onClick = { showLibraries = true },
                    )
                }
                Hint("Copyright © 2026 devSmoms · Inoffizielle App, kein Produkt der Nextcloud GmbH.")
            }
        }
    }

    if (showLicense) {
        AlertDialog(
            onDismissRequest = { showLicense = false },
            title = { Text("Lizenz") },
            text = {
                Text(
                    "NC Todo ist freie Software: Du kannst sie unter den Bedingungen der GNU General Public License, " +
                        "Version 3 oder (nach deiner Wahl) jeder späteren Version, weitergeben und/oder verändern.\n\n" +
                        "Die App wird OHNE JEDE GEWÄHRLEISTUNG bereitgestellt, sogar ohne die implizite Gewährleistung " +
                        "der MARKTFÄHIGKEIT oder EIGNUNG FÜR EINEN BESTIMMTEN ZWECK.\n\n" +
                        "Der vollständige Lizenztext liegt dem Quellcode bei (Datei LICENSE).",
                )
            },
            confirmButton = { TextButton(onClick = { showLicense = false }) { Text("Schließen") } },
            dismissButton = { TextButton(onClick = { openLink(context, LICENSE_URL) }) { Text("Volltext öffnen") } },
        )
    }
    if (showLibraries) {
        AlertDialog(
            onDismissRequest = { showLibraries = false },
            title = { Text("Open-Source-Bibliotheken") },
            text = {
                Text(
                    LIBRARIES.joinToString("\n") { "• $it" } + "\n\nAlle stehen unter der Apache License 2.0.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = { TextButton(onClick = { showLibraries = false }) { Text("Schließen") } },
            dismissButton = { TextButton(onClick = { openLink(context, APACHE_URL) }) { Text("Apache 2.0 öffnen") } },
        )
    }
    if (pickTime) {
        val tp = rememberTimePickerState(initialHour = s.dueTimeMinutes / 60, initialMinute = s.dueTimeMinutes % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text("Uhrzeit der Erinnerung") },
            text = { TimePicker(tp) },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateSettings { it.copy(dueTimeMinutes = tp.hour * 60 + tp.minute) }
                    pickTime = false
                }) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Abbrechen") } },
        )
    }
    if (confirmLogout) {
        ConfirmDialog(
            title = "Abmelden?",
            text = (if (state.pendingCount > 0) "Achtung: ${state.pendingCount} Änderung(en) wurden noch nicht übertragen und gehen verloren. " else "") +
                "Zugangsdaten und Offline-Daten werden von diesem Gerät gelöscht.",
            confirm = "Abmelden",
            onDismiss = { confirmLogout = false },
        ) { confirmLogout = false; vm.logout() }
    }
}

// ---------------------------------------------------------------- building blocks

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp),
    )
}

/** Rounded card like the task rows of the lists. */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    ) { Column(content = content) }
}

@Composable
private fun RowDivider() = HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

@Composable
private fun Hint(text: String) {
    Text(
        text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun ValueText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .heightIn(min = 56.dp).padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Icon(icon, null, tint = tint)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = titleColor)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** Row that opens a dropdown; options are (label, optional icon, icon tint) to value. */
@Composable
private fun <T> PickerRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    value: String,
    options: List<Pair<Triple<String, ImageVector?, Color?>, T>>,
    subtitle: String? = null,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        SettingRow(icon = icon, tint = tint, title = title, subtitle = subtitle, onClick = { open = true }, trailing = { ValueText(value) })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 420.dp)) {
            options.forEach { (label, v) ->
                val optionIcon = label.second
                val optionTint = label.third ?: MaterialTheme.colorScheme.onSurfaceVariant
                DropdownMenuItem(
                    text = { Text(label.first) },
                    leadingIcon = if (optionIcon != null) ({ Icon(optionIcon, null, Modifier.size(22.dp), tint = optionTint) }) else null,
                    onClick = { open = false; onSelect(v) },
                )
            }
        }
    }
}
