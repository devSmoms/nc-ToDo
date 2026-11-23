package de.devsmoms.nctodo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.devsmoms.nctodo.ui.theme.parseColor
import java.time.Duration
import java.time.Instant

/** Nextcloud trash bin: deleted tasks and lists can be restored or removed for good. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(state: UiState, vm: AppViewModel, snackbar: SnackbarHostState) {
    var confirmPurge by remember { mutableStateOf<Pair<String, String>?>(null) }
    val accent = MaterialTheme.colorScheme.primary
    val trash = state.trash

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück") } },
                actions = { IconButton(onClick = vm::loadTrash, enabled = !state.trashLoading) { Icon(Icons.Outlined.Refresh, "Neu laden") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text("Papierkorb", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = accent)
                    Text(
                        "Gelöschte Aufgaben und Listen bewahrt Nextcloud " +
                            (trash?.retentionDays?.let { "$it Tage" } ?: "eine Zeit lang") +
                            " auf, danach werden sie endgültig entfernt. Nur online verfügbar.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when {
                state.trashLoading && trash == null -> item {
                    Row(Modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.trashError != null -> item {
                    Text(state.trashError, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(20.dp))
                }
                trash != null && trash.tasks.isEmpty() && trash.lists.isEmpty() -> item {
                    Text("Der Papierkorb ist leer.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
                }
            }
            if (trash != null && trash.lists.isNotEmpty()) {
                item { TrashSection("Listen") }
                items(trash.lists, key = { it.url }) { l ->
                    TrashRow(
                        icon = ListIcons.iconOf(l.name) ?: ListIcons.DEFAULT,
                        tint = parseColor(l.color),
                        title = ListIcons.label(l.name),
                        subtitle = "Liste mit allen Aufgaben · " + ago(l.deletedAt),
                        onRestore = { vm.restoreFromTrash(l.url) },
                        onPurge = { confirmPurge = l.url to ListIcons.label(l.name) },
                    )
                }
            }
            if (trash != null && trash.tasks.isNotEmpty()) {
                item { TrashSection("Aufgaben") }
                items(trash.tasks, key = { it.url }) { t ->
                    // The trash only knows the list's URI segment; map it to a known list.
                    val list = state.lists.firstOrNull { l -> t.listUri != null && l.url.trimEnd('/').endsWith("/" + t.listUri) }
                    TrashRow(
                        icon = list?.let { ListIcons.iconOf(it.name) } ?: ListIcons.DEFAULT,
                        tint = list?.let { parseColor(it.color) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                        title = t.summary.ifBlank { "(ohne Titel)" },
                        subtitle = listOfNotNull(list?.let { ListIcons.label(it.name) }, ago(t.deletedAt)).joinToString(" · "),
                        onRestore = { vm.restoreFromTrash(t.url) },
                        onPurge = { confirmPurge = t.url to t.summary },
                    )
                }
            }
        }
    }

    confirmPurge?.let { (url, name) ->
        ConfirmDialog(
            title = "Endgültig löschen?",
            text = "„$name“ kann danach nicht mehr wiederhergestellt werden.",
            confirm = "Endgültig löschen",
            onDismiss = { confirmPurge = null },
        ) { confirmPurge = null; vm.deleteFromTrash(url) }
    }
}

@Composable
private fun TrashSection(title: String) {
    Text(
        title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun TrashRow(
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    onRestore: () -> Unit,
    onPurge: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp), tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 6.dp)) {
            Icon(icon, null, tint = tint)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onRestore) { Icon(Icons.Outlined.RestoreFromTrash, "Wiederherstellen", tint = MaterialTheme.colorScheme.primary) }
            IconButton(onClick = onPurge) { Icon(Icons.Outlined.DeleteForever, "Endgültig löschen", tint = MaterialTheme.colorScheme.error) }
        }
    }
}

private fun ago(at: Instant?): String {
    if (at == null) return "gelöscht"
    val d = Duration.between(at, Instant.now())
    return when {
        d.toMinutes() < 1 -> "gerade gelöscht"
        d.toHours() < 1 -> "vor ${d.toMinutes()} Min. gelöscht"
        d.toDays() < 1 -> "vor ${d.toHours()} Std. gelöscht"
        d.toDays() == 1L -> "gestern gelöscht"
        else -> "vor ${d.toDays()} Tagen gelöscht"
    }
}
