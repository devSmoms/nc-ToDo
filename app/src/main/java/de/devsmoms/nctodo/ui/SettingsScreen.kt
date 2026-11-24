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
import androidx.compose.material.icons.outlined.SwitchAccount
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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material3.OutlinedTextField
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
/** Third-party software shipped in the APK (incl. relevant transitive dependencies). */
private data class Library(val name: String, val copyright: String, val license: String)
private const val APACHE = "Apache-2.0"
private const val BSD3 = "BSD-3-Clause"
private val LIBRARIES = listOf(
    Library("AndroidX: Core, Activity, Lifecycle, WorkManager, Glance, DataStore", "The Android Open Source Project", APACHE),
    Library("Jetpack Compose, Material 3, Material Icons", "The Android Open Source Project / Google", APACHE),
    Library("Kotlin Standard Library, kotlinx.coroutines", "JetBrains s.r.o. and Kotlin contributors", APACHE),
    Library("OkHttp, Okio", "Square, Inc.", APACHE),
    Library("Guava ListenableFuture (in WorkManager)", "The Guava Authors", APACHE),
    Library("Reorderable", "Calvin Liang", APACHE),
    Library("Protocol Buffers Lite (in DataStore enthalten)", "Google Inc.", BSD3),
)

/** Same accent colors as the smart lists in the drawer. */
private val SunColor = Color(0xFF9E7B00)
private val StarColor = Color(0xFFC2185B)
private val PlannedColor = Color(0xFF00897B)
private val HomeColor = Color(0xFF2564CF)
private val PurpleColor = Color(0xFF6A1B9A)
private val GreyColor = Color(0xFF455A64)

/** One entry of the start screen picker. */
private data class StartOption(val value: String, val label: String, val icon: ImageVector, val tint: Color?)

/** Sub pages of the settings; each has its own colour like a list. */
private enum class SettingsPage(val title: String, val icon: ImageVector, val color: Color) {
    APPEARANCE("Darstellung", Icons.Outlined.Palette, PurpleColor),
    TASKS("Aufgaben", Icons.Outlined.TaskAlt, HomeColor),
    SYNC("Synchronisierung", Icons.Outlined.Sync, PlannedColor),
    NOTIFICATIONS("Benachrichtigungen", Icons.Outlined.Notifications, SunColor),
    ABOUT("Über NC Todo", Icons.Outlined.Info, GreyColor),
}

/**
 * Settings of the active account: an overview with the account card and one row per
 * category; each category opens its own page with the same look as a task list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: UiState, vm: AppViewModel, snackbar: SnackbarHostState) {
    val s = state.settings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pageName by rememberSaveable { mutableStateOf<String?>(null) }
    val page = pageName?.let { n -> SettingsPage.entries.firstOrNull { it.name == n } }
    var pickTime by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var accountDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(false) }
    var showLicense by remember { mutableStateOf(false) }
    var showLibraries by remember { mutableStateOf(false) }
    var licenseText by remember { mutableStateOf<String?>(null) }

    // Back on a sub page returns to the overview (registered after the screen's own handler, so it wins).
    BackHandler(enabled = page != null) { pageName = null }

    // Turning on a notification type first asks for the Android 13+ permission.
    var pendingEnable by remember { mutableStateOf<((Settings) -> Settings)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val change = pendingEnable
        pendingEnable = null
        if (granted && change != null) vm.updateSettings(change)
        else if (!granted) scope.launch { snackbar.showSnackbar("Ohne Berechtigung kann die App keine Benachrichtigungen anzeigen") }
    }
    fun setNotification(on: Boolean, change: (Settings, Boolean) -> Settings) {
        val needsPermission = on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingEnable = { change(it, true) }
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.updateSettings { change(it, on) }
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
    val reminderTime = "%02d:%02d".format(s.dueTimeMinutes / 60, s.dueTimeMinutes % 60)
    val lastSync = state.lastSync?.let {
        DateTimeFormatter.ofPattern("dd.MM., HH:mm").format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { if (page != null) pageName = null else vm.back() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                // Same big coloured header as the task lists.
                PageHeader(
                    title = page?.title ?: "Einstellungen",
                    color = page?.color ?: MaterialTheme.colorScheme.primary,
                    subtitle = if (page == null && state.accounts.size > 1) "Gelten nur für dieses Konto" else null,
                )
            }
            when (page) {
                null -> {
                    item {
                        AccountCard(state) { accountDialog = true }
                    }
                    item {
                        SettingsCard {
                            CategoryRow(SettingsPage.APPEARANCE, "${s.theme.label} · Start: ${currentStart.label}") { pageName = it.name }
                            RowDivider()
                            CategoryRow(SettingsPage.TASKS, "Fälligkeit, Deck-Listen, Links") { pageName = it.name }
                            RowDivider()
                            CategoryRow(
                                SettingsPage.SYNC,
                                when {
                                    state.syncing -> "Synchronisiere …"
                                    state.pendingCount > 0 -> "${state.pendingCount} Änderung(en) ausstehend"
                                    state.offline -> "Offline"
                                    else -> "Zuletzt ${lastSync ?: "noch nie"}"
                                },
                            ) { pageName = it.name }
                            RowDivider()
                            CategoryRow(
                                SettingsPage.NOTIFICATIONS,
                                listOfNotNull(
                                    if (s.notifyDue) "Erinnerung $reminderTime Uhr" else null,
                                    if (s.notifySharedChanges) "Geteilte Listen" else null,
                                ).joinToString(" · ").ifEmpty { "Aus" },
                            ) { pageName = it.name }
                        }
                    }
                    item { Spacer(Modifier.size(12.dp)) }
                    item {
                        SettingsCard {
                            CategoryRow(SettingsPage.ABOUT, "Version ${BuildConfig.VERSION_NAME} · GPL v3") { pageName = it.name }
                        }
                    }
                    item { Spacer(Modifier.size(12.dp)) }
                    item {
                        SettingsCard {
                            SettingRow(
                                icon = Icons.AutoMirrored.Outlined.Logout, tint = MaterialTheme.colorScheme.error,
                                title = "Abmelden", titleColor = MaterialTheme.colorScheme.error,
                                onClick = { confirmLogout = true },
                            )
                        }
                        Hint("Beim Abmelden werden alle Daten dieses Kontos von diesem Gerät gelöscht.")
                    }
                }

                SettingsPage.APPEARANCE -> {
                    val c = page.color
                    item { SectionLabel("Konto") }
                    item {
                        SettingsCard {
                            SettingRow(
                                icon = Icons.Outlined.Badge, tint = c,
                                title = "Anzeigename",
                                subtitle = if (s.accountName.isBlank()) "Name aus Nextcloud" else "Eigener Name",
                                onClick = { editName = true },
                                trailing = { ValueText(state.credentials?.displayName.orEmpty()) },
                            )
                        }
                        Hint("Nur für die Anzeige in NC Todo auf diesem Gerät – in Nextcloud ändert sich nichts.")
                    }
                    item { SectionLabel("Design") }
                    item {
                        SettingsCard {
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(12.dp)) {
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
                    item { SectionLabel("Beim Öffnen") }
                    item {
                        SettingsCard {
                            PickerRow(
                                icon = currentStart.icon, tint = currentStart.tint ?: c,
                                title = "Startbildschirm",
                                value = currentStart.label,
                                options = startOptions.map { Triple(it.label, it.icon, it.tint ?: c) to it.value },
                            ) { v -> vm.updateSettings { it.copy(startScreen = v) } }
                        }
                    }
                }

                SettingsPage.TASKS -> {
                    val c = page.color
                    item { SectionLabel("Neue Aufgaben") }
                    item {
                        SettingsCard {
                            SwitchRow(Icons.Outlined.CalendarMonth, c, "Fälligkeit ganztägig", "Ohne Uhrzeit, außer du wählst eine", s.allDayDefault) { on ->
                                vm.updateSettings { it.copy(allDayDefault = on) }
                            }
                        }
                    }
                    item { SectionLabel("Anzeige") }
                    item {
                        SettingsCard {
                            SwitchRow(Icons.Outlined.ViewKanban, c, "Deck-Listen ausblenden", "Listen, die mit „Deck:“ beginnen", s.hideDeckLists) { on ->
                                vm.updateSettings { it.copy(hideDeckLists = on) }
                            }
                            RowDivider()
                            SwitchRow(Icons.Outlined.Link, c, "Link-Vorschau", "Titel und Bild der Webseite laden", s.linkPreviews) { on ->
                                vm.updateSettings { it.copy(linkPreviews = on) }
                            }
                        }
                    }
                    item { SectionLabel("Bearbeiten") }
                    item {
                        SettingsCard {
                            SwitchRow(
                                Icons.AutoMirrored.Outlined.HelpOutline, c, "Vor dem Entfernen nachfragen",
                                "Bei Links, Fälligkeit und Schritten", s.confirmRemovals,
                            ) { on -> vm.updateSettings { it.copy(confirmRemovals = on) } }
                        }
                    }
                }

                SettingsPage.SYNC -> {
                    val c = page.color
                    item { SyncStatusCard(state, lastSync, c, vm::refresh) }
                    item { SectionLabel("Automatisch synchronisieren") }
                    item {
                        SettingsCard {
                            PickerRow(
                                icon = Icons.Outlined.PhoneAndroid, tint = c,
                                title = "Beim Öffnen",
                                value = s.openSync.label,
                                options = OpenSync.entries.map { Triple(it.label, null, null) to it },
                            ) { v -> vm.updateSettings { it.copy(openSync = v) } }
                            if (s.openSync == OpenSync.AFTER_INACTIVITY) {
                                RowDivider()
                                PickerRow(
                                    icon = Icons.Outlined.Bedtime, tint = c,
                                    title = "Pause ab",
                                    value = Settings.minutes(s.inactivityMin),
                                    options = Settings.INACTIVITY_OPTIONS.map { Triple(Settings.minutes(it), null, null) to it },
                                ) { v -> vm.updateSettings { it.copy(inactivityMin = v) } }
                            }
                            RowDivider()
                            PickerRow(
                                icon = Icons.Outlined.Timer, tint = c,
                                title = "App geöffnet",
                                value = Settings.seconds(s.foregroundIntervalSec),
                                options = Settings.FOREGROUND_OPTIONS.map { Triple(Settings.seconds(it), null, null) to it },
                            ) { v -> vm.updateSettings { it.copy(foregroundIntervalSec = v) } }
                            RowDivider()
                            PickerRow(
                                icon = Icons.Outlined.CloudSync, tint = c,
                                title = "Im Hintergrund",
                                value = Settings.minutes(s.backgroundIntervalMin),
                                options = Settings.BACKGROUND_OPTIONS.map { Triple(Settings.minutes(it), null, null) to it },
                            ) { v -> vm.updateSettings { it.copy(backgroundIntervalMin = v) } }
                        }
                        Hint(
                            "Eigene Änderungen werden immer sofort übertragen – ohne Netz automatisch, sobald wieder eine Verbindung besteht. " +
                                "Im Hintergrund erlaubt Android höchstens alle 15 Minuten.",
                        )
                    }
                }

                SettingsPage.NOTIFICATIONS -> {
                    val c = page.color
                    item { SectionLabel("Erinnern") }
                    item {
                        SettingsCard {
                            SwitchRow(Icons.Outlined.WbSunny, c, "Fällige Aufgaben", "Einmal täglich", s.notifyDue) { on ->
                                setNotification(on) { st, v -> st.copy(notifyDue = v) }
                            }
                            if (s.notifyDue) {
                                RowDivider()
                                SettingRow(
                                    icon = Icons.Outlined.Schedule, tint = c,
                                    title = "Uhrzeit", onClick = { pickTime = true },
                                    trailing = { ValueText("$reminderTime Uhr") },
                                )
                            }
                        }
                    }
                    item { SectionLabel("Geteilte Listen") }
                    item {
                        SettingsCard {
                            SwitchRow(Icons.Outlined.People, c, "Änderungen anderer", "Neue und erledigte Aufgaben", s.notifySharedChanges) { on ->
                                setNotification(on) { st, v -> st.copy(notifySharedChanges = v) }
                            }
                        }
                    }
                    item { Spacer(Modifier.size(12.dp)) }
                    item {
                        SettingsCard {
                            SettingRow(
                                icon = Icons.AutoMirrored.Outlined.OpenInNew, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                title = "Ton und Sperrbildschirm",
                                subtitle = "Android-Einstellungen öffnen",
                                onClick = {
                                    val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                                    runCatching { context.startActivity(intent) }
                                },
                            )
                        }
                        Hint("Auf dem Sperrbildschirm zeigt NC Todo nur die Anzahl, keine Aufgabentitel.")
                    }
                }

                SettingsPage.ABOUT -> {
                    val c = page.color
                    item {
                        SettingsCard {
                            SettingRow(
                                icon = Icons.Outlined.Info, tint = c, title = "Version",
                                trailing = { ValueText("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") },
                            )
                            RowDivider()
                            SettingRow(
                                icon = Icons.Outlined.Code, tint = c, title = "Quellcode auf GitHub",
                                onClick = { openLink(context, SOURCE_URL) },
                                trailing = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            )
                            RowDivider()
                            SettingRow(icon = Icons.Outlined.Gavel, tint = c, title = "Lizenz: GNU GPL v3", onClick = { showLicense = true }, trailing = { Chevron() })
                            RowDivider()
                            SettingRow(icon = Icons.Outlined.Widgets, tint = c, title = "Open-Source-Bibliotheken", onClick = { showLibraries = true }, trailing = { Chevron() })
                        }
                    }
                    item { SectionLabel("Sicherheit") }
                    item {
                        SettingsCard {
                            SettingRow(
                                icon = Icons.Outlined.Lock, tint = c,
                                title = "Verschlüsselt gespeichert",
                                subtitle = "Zugangsdaten und Offline-Daten sind mit einem Hardware-Schlüssel dieses Geräts geschützt",
                            )
                        }
                        Hint("Copyright © 2026 devSmoms · Inoffizielle App, kein Produkt der Nextcloud GmbH.")
                    }
                }
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
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    LIBRARIES.forEach { lib ->
                        Text(lib.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "© ${lib.copyright} · ${lib.license}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.size(10.dp))
                    }
                    Text(
                        "Apache-2.0 und BSD-3-Clause sind mit der GPL v3 von NC Todo vereinbar. Die vollständigen Lizenztexte liegen der App bei:",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showLibraries = false }) { Text("Schließen") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { licenseText = "licenses/Apache-2.0.txt" }) { Text("Apache 2.0") }
                    TextButton(onClick = { licenseText = "licenses/BSD-3-Clause.txt" }) { Text("BSD-3") }
                }
            },
        )
    }
    licenseText?.let { asset ->
        // Full license text bundled in the APK, readable offline.
        val text = remember(asset) {
            runCatching { context.assets.open(asset).bufferedReader().use { it.readText() } }.getOrElse { "Lizenztext nicht gefunden." }
        }
        AlertDialog(
            onDismissRequest = { licenseText = null },
            title = { Text(if (asset.contains("Apache")) "Apache License 2.0" else "BSD 3-Clause License (Protocol Buffers)") },
            text = { Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { licenseText = null }) { Text("Schließen") } },
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
            title = "${state.credentials?.displayName.orEmpty()} abmelden?",
            text = (if (state.pendingCount > 0) "Achtung: ${state.pendingCount} Änderung(en) wurden noch nicht übertragen und gehen verloren. " else "") +
                "Zugangsdaten, Offline-Daten und Einstellungen dieses Kontos werden von diesem Gerät gelöscht. Auf dem Server bleibt alles erhalten.",
            confirm = "Abmelden",
            onDismiss = { confirmLogout = false },
        ) {
            confirmLogout = false
            state.activeAccount?.let(vm::logout)
        }
    }
    if (editName) {
        AccountNameDialog(
            current = s.accountName,
            serverName = vm.serverAccountName(),
            onDismiss = { editName = false },
        ) { name ->
            editName = false
            vm.updateSettings { it.copy(accountName = name) }
        }
    }
    if (accountDialog) {
        AccountDialog(
            state = state,
            onSwitch = vm::switchAccount,
            onAdd = vm::addAccount,
            onLogout = vm::logout,
            pendingOf = vm::pendingCount,
            onDismiss = { accountDialog = false },
        )
    }
}

// ---------------------------------------------------------------- page parts

/** Own display name for the account; empty restores the name from Nextcloud. */
@Composable
private fun AccountNameDialog(current: String, serverName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current.ifBlank { serverName }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Anzeigename") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(40) },
                    label = { Text("Name in NC Todo") },
                    placeholder = { Text(serverName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "Name aus Nextcloud: $serverName",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            // Saving the Nextcloud name itself (or nothing) means "no own name".
            TextButton(onClick = { onSave(text.trim().takeIf { it != serverName }.orEmpty()) }) { Text("Speichern") }
        },
        dismissButton = {
            Row {
                if (current.isNotBlank()) TextButton(onClick = { onSave("") }) { Text("Zurücksetzen") }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
}

@Composable
private fun PageHeader(title: String, color: Color, subtitle: String?) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Active account at the top of the overview; tapping it opens the account switcher. */
@Composable
private fun AccountCard(state: UiState, onClick: () -> Unit) {
    val creds = state.credentials ?: return
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            AccountAvatar(creds.displayName, state.activeAccount, 48.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(creds.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${creds.username} · ${serverLabel(creds.serverUrl)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (state.accounts.size > 1) "Konto wechseln · ${state.accounts.size} Konten" else "Konto wechseln oder hinzufügen",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(Icons.Outlined.UnfoldMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Overview row: coloured icon badge, title, one-line summary of the current values. */
@Composable
private fun CategoryRow(page: SettingsPage, summary: String, onOpen: (SettingsPage) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onOpen(page) }
            .heightIn(min = 64.dp).padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(page.color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Icon(page.icon, null, Modifier.size(20.dp), tint = page.color) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(page.title)
            Text(
                summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Chevron()
    }
}

/** Sync state at the top of the sync page with a manual "sync now". */
@Composable
private fun SyncStatusCard(state: UiState, lastSync: String?, color: Color, onSync: () -> Unit) {
    val (icon, title) = when {
        state.syncing -> Icons.Outlined.Sync to "Synchronisiere …"
        state.offline -> Icons.Outlined.CloudOff to "Offline"
        state.pendingCount > 0 -> Icons.Outlined.CloudUpload to "${state.pendingCount} Änderung(en) ausstehend"
        else -> Icons.Outlined.CloudDone to "Alles synchronisiert"
    }
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                if (state.syncing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = color)
                else Icon(icon, null, tint = color)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    if (state.offline) "Server nicht erreichbar – Offline-Daten werden angezeigt" else "Zuletzt: ${lastSync ?: "noch nie"}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onSync, enabled = !state.syncing) { Text("Jetzt") }
        }
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, tint: Color, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(
        icon = icon, tint = tint, title = title, subtitle = subtitle,
        onClick = { onChange(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}

@Composable
private fun Chevron() {
    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ---------------------------------------------------------------- building blocks

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 6.dp),
    )
}

/** Rounded card like the task rows of the lists. */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
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
