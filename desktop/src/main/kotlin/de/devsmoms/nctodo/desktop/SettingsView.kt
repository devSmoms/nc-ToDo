package de.devsmoms.nctodo.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val REPO_URL = "https://github.com/devSmoms/nc-ToDo"

/** Settings of this computer, shown in place of the task list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(state: AppState, modifier: Modifier) {
    val s = state.settings
    val creds = state.credentials ?: return
    val status = state.repository?.status?.collectAsState()?.value
    var editName by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var licenses by remember { mutableStateOf(false) }

    Column(modifier.verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 32.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)) {
            Text("Einstellungen", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = { state.showSettings = false }) { Icon(Icons.Outlined.Close, "Schließen") }
        }
        Column(
            Modifier.widthIn(max = 720.dp).padding(horizontal = 32.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // ---------------------------------------------------------------- account
            Section("Konto") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(AccentBlue.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) { Text(state.displayName.take(1).uppercase(), color = AccentBlue, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(state.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${creds.username} · ${creds.serverUrl.removePrefix("https://").trimEnd('/')}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                HorizontalDivider()
                ClickRow(
                    "Anzeigename",
                    if (s.accountName.isBlank()) "Name aus Nextcloud: ${creds.displayName}" else s.accountName,
                ) { editName = true }
                HorizontalDivider()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { confirmLogout = true }.heightIn(min = 56.dp).padding(horizontal = 16.dp),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(16.dp))
                    Text("Abmelden", color = MaterialTheme.colorScheme.error)
                }
            }

            // ---------------------------------------------------------------- appearance
            Section("Darstellung") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 64.dp).padding(horizontal = 16.dp)) {
                    Text("Design", modifier = Modifier.weight(1f))
                    SingleChoiceSegmentedButtonRow {
                        ThemeMode.entries.forEachIndexed { i, mode ->
                            SegmentedButton(
                                selected = s.theme == mode,
                                onClick = { state.updateSettings { it.copy(theme = mode) } },
                                shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                            ) { Text(mode.label) }
                        }
                    }
                }
            }

            // ---------------------------------------------------------------- tasks
            Section("Listen und Aufgaben") {
                SwitchRow("Deck-Listen ausblenden", "Listen der Nextcloud-App Deck erscheinen nicht im Menü.", s.hideDeckLists) { v ->
                    state.updateSettings { it.copy(hideDeckLists = v) }
                }
                HorizontalDivider()
                SwitchRow("Link-Vorschau", "Lädt Titel und Bild verlinkter Seiten direkt von der jeweiligen Webseite.", s.linkPreviews) { v ->
                    if (!v) LinkPreviews.clear()
                    state.updateSettings { it.copy(linkPreviews = v) }
                }
                HorizontalDivider()
                SwitchRow("Vor dem Entfernen nachfragen", "Gilt für Links. Gelöschte Aufgaben lassen sich kurz rückgängig machen.", s.confirmRemovals) { v ->
                    state.updateSettings { it.copy(confirmRemovals = v) }
                }
            }

            // ---------------------------------------------------------------- sync
            Section("Synchronisation") {
                var menu by remember { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 64.dp).padding(horizontal = 16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Automatisch synchronisieren")
                        Text("Solange das Fenster offen ist; Änderungen werden immer sofort gesendet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box {
                        TextButton(onClick = { menu = true }) {
                            Text(DesktopSettings.syncLabel(s.syncIntervalSec))
                            Icon(Icons.Outlined.ArrowDropDown, null)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DesktopSettings.SYNC_OPTIONS.forEach { sec ->
                                DropdownMenuItem(
                                    text = { Text(DesktopSettings.syncLabel(sec)) },
                                    onClick = { menu = false; state.updateSettings { it.copy(syncIntervalSec = sec) } },
                                )
                            }
                        }
                    }
                }
                HorizontalDivider()
                ClickRow(
                    "Jetzt synchronisieren",
                    when {
                        status?.running == true -> "Wird synchronisiert …"
                        status?.offline == true -> "Server nicht erreichbar – Änderungen werden später gesendet"
                        else -> status?.lastSuccess?.let { "Zuletzt: " + lastSyncFormat.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) } ?: "Strg+R"
                    },
                ) { state.syncNow() }
            }

            // ---------------------------------------------------------------- about
            Section("Über NC Todo") {
                ClickRow("Version", appVersion) {}
                HorizontalDivider()
                ClickRow("Quellcode und Fehler melden", "github.com/devSmoms/nc-ToDo", trailingLink = true) { AppState.openInBrowser(REPO_URL) }
                HorizontalDivider()
                ClickRow("Lizenzen", "NC Todo steht unter der GPL-3.0; verwendete Bibliotheken") { licenses = true }
            }
            Spacer(Modifier.heightIn(min = 16.dp))
        }
    }

    if (editName) {
        NameDialog(
            title = "Anzeigename",
            initial = s.accountName,
            confirm = "Speichern",
            label = "Name",
            hint = "Leer lassen, um den Namen aus Nextcloud zu verwenden (${creds.displayName}).",
            allowEmpty = true,
            maxLength = 60,
            onDismiss = { editName = false },
        ) { name ->
            editName = false
            state.updateSettings { it.copy(accountName = name) }
        }
    }
    if (confirmLogout) {
        ConfirmDialog(
            title = "Abmelden?",
            text = "Zugangsdaten, Offline-Daten und Einstellungen werden von diesem Computer gelöscht. Deine Aufgaben auf der Nextcloud bleiben erhalten.",
            confirm = "Abmelden",
            onDismiss = { confirmLogout = false },
        ) {
            confirmLogout = false
            state.logout()
        }
    }
    if (licenses) LicensesDialog { licenses = false }
}

private val lastSyncFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm 'Uhr'")

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 1.dp, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column { content() }
        }
    }
}

@Composable
private fun ClickRow(title: String, subtitle: String, trailingLink: Boolean = false, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailingLink) Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        // Material switches are sized for touch; scaled down to fit a desktop window.
        Box(Modifier.size(width = 38.dp, height = 24.dp), contentAlignment = Alignment.Center) {
            Switch(
                checked = checked, onCheckedChange = onChange,
                modifier = Modifier.wrapContentSize(unbounded = true).scale(0.72f),
            )
        }
    }
}

/** Libraries of the desktop app and their licence texts (bundled in the resources). */
@Composable
private fun LicensesDialog(onDismiss: () -> Unit) {
    val libraries = listOf(
        "NC Todo" to "GPL-3.0",
        "Compose Multiplatform, Skiko (JetBrains)" to "Apache-2.0",
        "Kotlin, kotlinx.coroutines" to "Apache-2.0",
        "OkHttp, Okio (Square)" to "Apache-2.0",
        "JNA (Java Native Access)" to "Apache-2.0",
        "Material Icons (Google)" to "Apache-2.0",
    )
    var shown by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(shown ?: "Lizenzen") },
        text = {
            Column(Modifier.widthIn(min = 420.dp).heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                val id = shown
                if (id == null) {
                    libraries.forEach { (name, license) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { shown = license }.padding(8.dp),
                        ) {
                            Text(name, modifier = Modifier.weight(1f))
                            Text(license, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else {
                    val text = remember(id) {
                        Thread.currentThread().contextClassLoader.getResourceAsStream("licenses/$id.txt")?.use { it.readBytes().decodeToString() }
                            ?: "Lizenztext nicht gefunden."
                    }
                    SelectionContainer { Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
        dismissButton = { if (shown != null) TextButton(onClick = { shown = null }) { Text("Zurück") } },
    )
}
