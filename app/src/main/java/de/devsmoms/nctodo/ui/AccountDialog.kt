package de.devsmoms.nctodo.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import de.devsmoms.nctodo.data.CalDavRepository
import de.devsmoms.nctodo.ui.theme.parseColor

/** Round avatar with the first letter of the name; the colour is stable per account. */
@Composable
fun AccountAvatar(name: String, accountId: String?, size: Dp = 40.dp) {
    val palette = CalDavRepository.COLORS
    val color = parseColor(palette[((accountId ?: name).hashCode() and 0x7FFFFFFF) % palette.size])
    Box(
        Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase().ifEmpty { "?" },
            color = color, fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.42f).sp,
        )
    }
}

/** Host of the server without scheme, for compact display. */
fun serverLabel(url: String): String = url.removePrefix("https://").removePrefix("http://").trimEnd('/')

/**
 * Account switcher: tap an account to switch, "Konto hinzufügen" to log in to another one,
 * the logout icon removes an account including all its data on this device.
 */
@Composable
fun AccountDialog(
    state: UiState,
    onSwitch: (String) -> Unit,
    onAdd: () -> Unit,
    onLogout: (String) -> Unit,
    pendingOf: (String) -> Int,
    onDismiss: () -> Unit,
) {
    var confirmLogout by remember { mutableStateOf<AccountInfo?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 6.dp) {
            Column(Modifier.padding(vertical = 20.dp)) {
                Text(
                    "Konten",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.size(12.dp))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(state.accounts, key = { it.id }) { acc ->
                        val active = acc.id == state.activeAccount
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onSwitch(acc.id); onDismiss() }
                                .background(if (active) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                                .padding(start = 24.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                        ) {
                            AccountAvatar(acc.credentials.displayName, acc.id)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        acc.credentials.displayName, fontWeight = FontWeight.SemiBold,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                                    )
                                    if (active) {
                                        Spacer(Modifier.width(6.dp))
                                        Icon(Icons.Outlined.CheckCircle, "Aktiv", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Text(
                                    "${acc.credentials.username} · ${serverLabel(acc.credentials.serverUrl)}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                if (acc.pendingCount > 0) {
                                    Text(
                                        "${acc.pendingCount} Änderung(en) noch nicht übertragen",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary,
                                    )
                                }
                            }
                            IconButton(onClick = { confirmLogout = acc }) {
                                Icon(Icons.AutoMirrored.Outlined.Logout, "Abmelden", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onAdd(); onDismiss() }.padding(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.PersonAdd, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(14.dp))
                    Text("Konto hinzufügen", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "Jedes Konto hat eigene Listen und Einstellungen. Beim Abmelden werden alle Daten des Kontos von diesem Gerät gelöscht.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Schließen") }
                }
            }
        }
    }

    confirmLogout?.let { acc ->
        val pending = pendingOf(acc.id)
        ConfirmDialog(
            title = "${acc.credentials.displayName} abmelden?",
            text = (if (pending > 0) "Achtung: $pending Änderung(en) wurden noch nicht übertragen und gehen verloren. " else "") +
                "Zugangsdaten, Offline-Daten und Einstellungen dieses Kontos werden von diesem Gerät gelöscht. " +
                "Auf dem Server bleibt alles erhalten.",
            confirm = "Abmelden",
            onDismiss = { confirmLogout = null },
        ) {
            confirmLogout = null
            onLogout(acc.id)
            // The last account gone: the login screen replaces everything anyway.
            if (state.accounts.size <= 1) onDismiss()
        }
    }
}
