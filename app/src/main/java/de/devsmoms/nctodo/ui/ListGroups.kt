package de.devsmoms.nctodo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.devsmoms.nctodo.data.CalDavRepository
import de.devsmoms.nctodo.data.TaskList

/** Groups of the visible lists, sorted by name; lists keep their order inside a group. */
fun groupedLists(lists: List<TaskList>): Pair<List<TaskList>, List<Pair<String, List<TaskList>>>> {
    val ungrouped = lists.filter { it.group == null }
    val groups = lists.filter { it.group != null }
        .groupBy { it.group!! }
        .toList()
        .sortedBy { it.first.lowercase() }
    return ungrouped to groups
}

/** Group header in the menu: tap toggles, ⋮ offers new list, rename and dissolve. */
@Composable
fun GroupHeader(
    name: String,
    openCount: Int,
    collapsed: Boolean,
    containsSelection: Boolean,
    onToggle: () -> Unit,
    onNewList: () -> Unit,
    onRename: () -> Unit,
    onDissolve: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    // A collapsed group hiding the open list is highlighted so the selection never "disappears".
    val tint = if (collapsed && containsSelection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onToggle).heightIn(min = 52.dp).padding(start = 16.dp, end = 4.dp),
    ) {
        Icon(if (collapsed) Icons.Outlined.Folder else Icons.Outlined.FolderOpen, null, tint = tint)
        Spacer(Modifier.width(12.dp))
        Text(
            name, fontWeight = FontWeight.SemiBold, color = if (collapsed && containsSelection) tint else MaterialTheme.colorScheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        if (collapsed && openCount > 0) {
            Text(openCount.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Gruppe bearbeiten", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Neue Liste in der Gruppe") }, leadingIcon = { Icon(Icons.Outlined.Add, null) },
                    onClick = { menu = false; onNewList() },
                )
                DropdownMenuItem(
                    text = { Text("Gruppe umbenennen") }, leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) },
                    onClick = { menu = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text("Gruppe auflösen") }, leadingIcon = { Icon(Icons.Outlined.FolderOff, null) },
                    onClick = { menu = false; onDissolve() },
                )
            }
        }
        Icon(
            if (collapsed) Icons.AutoMirrored.Outlined.KeyboardArrowRight else Icons.Outlined.ExpandMore,
            if (collapsed) "Aufklappen" else "Zuklappen",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp),
        )
    }
}

/** Indents a list inside a group and draws the vertical guide line on its left. */
@Composable
fun GroupedEntry(content: @Composable () -> Unit) {
    val line = MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier.padding(start = 30.dp).drawBehind {
            drawLine(line, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 2.dp.toPx())
        },
    ) { content() }
}

/** Picks the group of a list: none, an existing group or a new one. */
@Composable
fun GroupPickerDialog(
    listName: String,
    current: String?,
    groups: List<String>,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
) {
    var choice by remember { mutableStateOf(current) }
    var newName by remember { mutableStateOf("") }
    val result = newName.trim().takeIf { it.isNotEmpty() } ?: choice
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gruppe für „$listName“") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                (listOf<String?>(null) + groups).forEach { value ->
                    GroupOption(value ?: "Keine Gruppe", selected = newName.isBlank() && choice == value) {
                        choice = value
                        newName = ""
                    }
                }
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(CalDavRepository.MAX_GROUP_LENGTH) },
                    label = { Text("Neue Gruppe") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(result) }, enabled = result != current) { Text("Verschieben") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun GroupOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Renames a group (all its lists move to the new name). */
@Composable
fun RenameGroupDialog(name: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var text by remember { mutableStateOf(name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gruppe umbenennen") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(CalDavRepository.MAX_GROUP_LENGTH) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(text.trim()) }, enabled = text.isNotBlank() && text.trim() != name) { Text("Umbenennen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
