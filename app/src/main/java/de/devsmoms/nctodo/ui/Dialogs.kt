package de.devsmoms.nctodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import de.devsmoms.nctodo.data.ListIcon
import de.devsmoms.nctodo.data.CalDavRepository
import de.devsmoms.nctodo.data.TaskList
import de.devsmoms.nctodo.ui.theme.parseColor

sealed interface ListDialogMode {
    /** [group]: the new list is put into this group (from the group menu). */
    data class Create(val group: String? = null) : ListDialogMode
    data class Edit(val list: TaskList) : ListDialogMode
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListDialog(mode: ListDialogMode, onDismiss: () -> Unit, onSave: (name: String, color: String, description: String) -> Unit) {
    val existing = (mode as? ListDialogMode.Edit)?.list
    val (existingEmoji, existingTitle) = ListIcon.split(existing?.name.orEmpty())
    var name by remember { mutableStateOf(existingTitle) }
    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    // Emojis without a monochrome icon (e.g. set in the Nextcloud web UI) are dropped on save.
    var emoji by remember { mutableStateOf(existingEmoji?.takeIf { ListIcons.iconFor(it) != null }) }
    var color by remember { mutableStateOf(existing?.color ?: CalDavRepository.COLORS.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Neue Liste" else "Liste bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.replace("\n", "").take(100) },
                    label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                OutlinedTextField(
                    value = description, onValueChange = { description = it.take(500) },
                    label = { Text("Beschreibung (optional)") }, minLines = 1, maxLines = 3, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                Text("Symbol", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Single-color icons in the list color; stored as emoji in the name so they sync.
                val tint = parseColor(color)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    (listOf<Pair<String?, ImageVector>>(null to ListIcons.DEFAULT) + ListIcons.ALL).forEach { (e, icon) ->
                        val selected = e == emoji
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(if (selected) tint.copy(alpha = 0.18f) else Color.Transparent)
                                .border(if (selected) 2.dp else 0.dp, if (selected) tint else Color.Transparent, CircleShape)
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(icon, if (e == null) "Standard-Symbol" else null, Modifier.size(22.dp), tint = if (selected || e != null) tint else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text("Farbe", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CalDavRepository.COLORS.forEach { c ->
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(parseColor(c))
                                .border(2.dp, if (c == color) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                                .clickable { color = c },
                            contentAlignment = Alignment.Center,
                        ) { if (c == color) Icon(Icons.Outlined.Check, "Ausgewählt", tint = Color.White) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(ListIcon.join(emoji, name), color, description.trim()) }, enabled = name.isNotBlank()) {
                Text(if (existing == null) "Erstellen" else "Speichern")
            }
        },
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
