package de.devsmoms.nctodo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.devsmoms.nctodo.data.TaskList
import de.devsmoms.nctodo.dav.Principals
import de.devsmoms.nctodo.ui.theme.parseColor
import kotlinx.coroutines.delay

private data class PendingShare(val principal: String, val name: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(state: UiState, list: TaskList, vm: AppViewModel, snackbar: SnackbarHostState) {
    var query by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<PendingShare?>(null) }
    var removeShare by remember { mutableStateOf<PendingShare?>(null) }
    val accent = parseColor(list.color)

    // Debounced server-side user search.
    LaunchedEffect(query) {
        delay(300)
        vm.search(query)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("„${ListIcons.label(list.name)}“ teilen") },
                navigationIcon = { IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().imePadding()) {
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it.replace("\n", "") },
                    label = { Text("Nextcloud-Benutzer suchen") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
            items(state.searchResults, key = { "hit:" + it.shareHref }) { hit ->
                ListItem(
                    headlineContent = { Text(hit.displayName) },
                    supportingContent = { Text(hit.email ?: hit.shareHref.substringAfterLast('/')) },
                    leadingContent = { Icon(Icons.Outlined.Person, null) },
                    modifier = Modifier.clickable { pending = PendingShare(hit.shareHref, hit.displayName) },
                )
            }
            val typed = Principals.fromUsername(query)
            if (typed != null && state.searchResults.none { it.shareHref == typed }) {
                item {
                    TextButton(onClick = { pending = PendingShare(typed, query.trim()) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text("Mit Benutzer „${query.trim()}“ teilen")
                    }
                }
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    if (list.shares.isEmpty()) "Noch mit niemandem geteilt." else "Geteilt mit",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = accent,
                )
            }
            items(list.shares, key = { "share:" + it.principal }) { share ->
                ListItem(
                    headlineContent = { Text(share.displayName) },
                    supportingContent = {
                        AssistChip(
                            onClick = { if (Principals.isUser(share.principal)) vm.share(list, share.principal, !share.canWrite) },
                            label = { Text(if (share.canWrite) "Kann bearbeiten" else "Nur lesen") },
                            leadingIcon = { Icon(if (share.canWrite) Icons.Outlined.Edit else Icons.Outlined.Visibility, null) },
                        )
                    },
                    leadingContent = { Icon(if (Principals.isGroup(share.principal)) Icons.Outlined.Groups else Icons.Outlined.Person, null) },
                    trailingContent = {
                        IconButton(onClick = { removeShare = PendingShare(share.principal, share.displayName) }) {
                            Icon(Icons.Outlined.Close, "Freigabe entfernen")
                        }
                    },
                )
            }
            item {
                Text(
                    "Geteilt werden kann nur mit Benutzern dieses Nextcloud-Servers. " +
                        "Tippe auf die Berechtigung, um zwischen „Nur lesen“ und „Kann bearbeiten“ zu wechseln.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    pending?.let { p ->
        PermissionDialog(p.name, onDismiss = { pending = null }) { canWrite ->
            pending = null
            query = ""
            vm.share(list, p.principal, canWrite)
        }
    }
    removeShare?.let { p ->
        ConfirmDialog(
            title = "Freigabe entfernen?",
            text = "${p.name} sieht die Liste „${ListIcons.label(list.name)}“ danach nicht mehr.",
            confirm = "Entfernen",
            onDismiss = { removeShare = null },
        ) { removeShare = null; vm.unshare(list, p.principal) }
    }
}

@Composable
private fun PermissionDialog(name: String, onDismiss: () -> Unit, onChoose: (Boolean) -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mit $name teilen") },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text("Nur lesen") },
                    supportingContent = { Text("Sieht die Aufgaben, kann nichts ändern") },
                    leadingContent = { Icon(Icons.Outlined.Visibility, null) },
                    modifier = Modifier.clickable { onChoose(false) },
                )
                ListItem(
                    headlineContent = { Text("Kann bearbeiten") },
                    supportingContent = { Text("Darf Aufgaben anlegen, abhaken und löschen") },
                    leadingContent = { Icon(Icons.Outlined.Edit, null) },
                    modifier = Modifier.clickable { onChoose(true) },
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
