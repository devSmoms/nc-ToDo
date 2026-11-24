package de.devsmoms.nctodo.desktop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.devsmoms.nctodo.data.CalDavRepository

/** Icon button with a tooltip on mouse hover. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TooltipIconButton(tooltip: String, icon: ImageVector, tint: Color = Color.Unspecified, onClick: () -> Unit) {
    TooltipArea(
        tooltip = {
            Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.inverseSurface) {
                Text(
                    tooltip, color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        },
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, tooltip, tint = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else tint)
        }
    }
}

/** Enter confirms in single line fields (handled before the text field sees it). */
private fun Modifier.onEnter(action: () -> Unit) = onPreviewKeyEvent { e ->
    if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) { action(); true } else false
}

@Composable
fun NewListDialog(group: String?, onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(CalDavRepository.COLORS.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (group == null) "Neue Liste" else "Neue Liste in „$group“") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.replace("\n", "") }, label = { Text("Name") }, singleLine = true,
                    modifier = Modifier.onEnter { if (name.isNotBlank()) onCreate(name, color) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CalDavRepository.COLORS.forEach { c ->
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(parseColor(c)).clickable { color = c },
                            contentAlignment = Alignment.Center,
                        ) { if (c == color) Icon(Icons.Outlined.CheckCircle, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, color) }, enabled = name.isNotBlank()) { Text("Erstellen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** Asks for a name (new group, renaming, own account name, …). */
@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirm: String,
    hint: String? = null,
    label: String = "Name",
    allowEmpty: Boolean = false,
    maxLength: Int = CalDavRepository.MAX_GROUP_LENGTH,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val valid = allowEmpty || text.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.replace("\n", "").take(maxLength) },
                    label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth().onEnter { if (valid) onSave(text.trim()) },
                )
                hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }, enabled = valid) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** Picks the group of a list: none, an existing group or a new one. */
@Composable
fun GroupPickerDialog(listName: String, current: String?, groups: List<String>, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    var choice by remember { mutableStateOf(current) }
    var newName by remember { mutableStateOf("") }
    val result = newName.trim().takeIf { it.isNotEmpty() } ?: choice
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gruppe für „$listName“") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                (listOf<String?>(null) + groups).forEach { value ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { choice = value; newName = "" },
                    ) {
                        RadioButton(selected = newName.isBlank() && choice == value, onClick = { choice = value; newName = "" })
                        Text(value ?: "Keine Gruppe", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it.replace("\n", "").take(CalDavRepository.MAX_GROUP_LENGTH) },
                    label = { Text("Neue Gruppe") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPick(result) }, enabled = result != current) { Text("Verschieben") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
