package de.devsmoms.nctodo.desktop

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.devsmoms.nctodo.data.CalDavRepository
import de.devsmoms.nctodo.data.TaskItem
import de.devsmoms.nctodo.data.TaskList
import de.devsmoms.nctodo.sync.LocalData
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val SunColor = Color(0xFF9E7B00)
private val StarColor = Color(0xFFC2185B)
private val PlannedColor = Color(0xFF00897B)

private fun SmartList.icon(): ImageVector = when (this) {
    SmartList.MY_DAY -> Icons.Outlined.WbSunny
    SmartList.IMPORTANT -> Icons.Outlined.StarBorder
    SmartList.PLANNED -> Icons.Outlined.CalendarMonth
    SmartList.ALL -> Icons.Outlined.Home
}

private fun SmartList.color(): Color = when (this) {
    SmartList.MY_DAY -> SunColor
    SmartList.IMPORTANT -> StarColor
    SmartList.PLANNED -> PlannedColor
    SmartList.ALL -> AccentBlue
}

/** Three columns like Microsoft To Do: menu, task list, details of the selected task. */
@Composable
fun MainView(state: AppState, shortcuts: Shortcuts) {
    val repo = state.repository ?: return
    val raw by repo.data.collectAsState()
    val status by repo.status.collectAsState()
    val hideDeck = state.settings.hideDeckLists
    val data = remember(raw, hideDeck) { raw.forDisplay(hideDeck) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val addFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }

    shortcuts.newTask = { runCatching { addFocus.requestFocus() } }
    shortcuts.search = { runCatching { searchFocus.requestFocus() } }
    shortcuts.sync = state::syncNow

    // Messages and "undo" for deletions.
    LaunchedEffect(Unit) {
        state.messages.collect { m ->
            scope.launch {
                val result = snackbar.showSnackbar(
                    m.text,
                    actionLabel = if (m.undo != null) "Rückgängig" else null,
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) m.undo?.invoke() else m.onTimeout?.invoke()
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            Sidebar(state, data, searchFocus, Modifier.width(290.dp).fillMaxHeight())
            VerticalDivider()
            if (state.showSettings) {
                SettingsView(state, Modifier.weight(1f).fillMaxHeight())
            } else {
                TaskPane(state, data, status.offline, status.running, addFocus, Modifier.weight(1f).fillMaxHeight())
                val selected = state.selectedTask?.let { data.task(it) }
                if (selected != null) {
                    VerticalDivider()
                    DetailPane(state, selected, data, Modifier.width(380.dp).fillMaxHeight())
                }
            }
        }
    }
}

// ---------------------------------------------------------------- sidebar

@Composable
private fun Sidebar(state: AppState, data: LocalData, searchFocus: FocusRequester, modifier: Modifier) {
    var accountMenu by remember { mutableStateOf(false) }
    var newList by remember { mutableStateOf<String?>(null) } // group the new list goes into ("" = none)
    var groupDialog by remember { mutableStateOf<GroupDialog?>(null) }
    var moveList by remember { mutableStateOf<TaskList?>(null) }
    var dissolve by remember { mutableStateOf<String?>(null) }
    val creds = state.credentials ?: return
    val name = state.displayName
    fun count(kind: SmartList) = tasksFor(Selection.Smart(kind), data, state.hidden).count { !it.todo.completed }
    val (ungrouped, groups) = groupedLists(data.lists, state.settings.emptyGroups)

    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        // Account and settings (pinned at the top)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 8.dp)) {
            Box(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { accountMenu = true }.padding(8.dp),
                ) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(AccentBlue.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) { Text(name.take(1).uppercase(), color = AccentBlue, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            creds.serverUrl.removePrefix("https://").trimEnd('/'),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Jetzt synchronisieren (Strg+R)") }, leadingIcon = { Icon(Icons.Outlined.Sync, null) },
                        onClick = { accountMenu = false; state.syncNow() },
                    )
                    DropdownMenuItem(
                        text = { Text("Einstellungen") }, leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                        onClick = { accountMenu = false; state.showSettings = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Abmelden") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, null) },
                        onClick = { accountMenu = false; state.logout() },
                    )
                }
            }
            IconButton(onClick = { state.showSettings = !state.showSettings }) {
                Icon(Icons.Outlined.Settings, "Einstellungen", tint = if (state.showSettings) AccentBlue else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SearchBar(state, searchFocus)
        Spacer(Modifier.height(8.dp))
        // Smart lists, lists and groups (scrolling)
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(SmartList.entries) { kind ->
                MenuEntry(kind.title, kind.icon(), kind.color(), count(kind), !state.showSettings && state.search.isBlank() && state.selection == Selection.Smart(kind)) {
                    state.search = ""
                    state.showSettings = false
                    state.selection = Selection.Smart(kind)
                }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 16.dp)) }
            items(ungrouped, key = { it.url }) { ListEntry(state, data, it, indent = false, onMove = { moveList = it }) }
            groups.forEach { (group, lists) ->
                val collapsed = group in state.settings.collapsedGroups
                item(key = "group:$group") {
                    GroupHeader(
                        name = group, collapsed = collapsed, empty = lists.isEmpty(),
                        onToggle = { state.toggleGroup(group) },
                        onNewList = { newList = group },
                        onRename = { groupDialog = GroupDialog(group) },
                        onDissolve = { dissolve = group },
                    )
                }
                if (!collapsed) items(lists, key = { it.url }) { ListEntry(state, data, it, indent = true, onMove = { moveList = it }) }
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        // New list + new group (pinned at the bottom)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            NavigationDrawerItem(
                label = { Text("Neue Liste") }, icon = { Icon(Icons.Outlined.Add, null) },
                selected = false, onClick = { newList = "" }, modifier = Modifier.weight(1f),
            )
            TooltipIconButton("Neue Gruppe", Icons.Outlined.CreateNewFolder) { groupDialog = GroupDialog(null) }
        }
    }

    newList?.let { group ->
        NewListDialog(group.ifEmpty { null }, onDismiss = { newList = null }) { listName, color ->
            newList = null
            state.createList(listName, color, group.ifEmpty { null })
        }
    }
    groupDialog?.let { d ->
        NameDialog(
            title = if (d.oldName == null) "Neue Gruppe" else "Gruppe umbenennen",
            initial = d.oldName.orEmpty(),
            confirm = if (d.oldName == null) "Erstellen" else "Umbenennen",
            hint = if (d.oldName == null) "Danach Listen per Rechtsklick → „In Gruppe verschieben“ hinzufügen." else null,
            onDismiss = { groupDialog = null },
        ) { newName ->
            groupDialog = null
            if (d.oldName == null) state.createGroup(newName) else state.renameGroup(d.oldName, newName)
        }
    }
    moveList?.let { list ->
        GroupPickerDialog(
            listName = list.label(),
            current = list.group,
            groups = groups.map { it.first },
            onDismiss = { moveList = null },
        ) { group ->
            moveList = null
            state.moveListToGroup(list, group)
        }
    }
    dissolve?.let { group ->
        ConfirmDialog(
            title = "Gruppe „$group“ auflösen?",
            text = "Die Listen bleiben erhalten und stehen danach ohne Gruppe im Menü.",
            confirm = "Auflösen",
            onDismiss = { dissolve = null },
        ) {
            dissolve = null
            state.dissolveGroup(group)
        }
    }
}

private data class GroupDialog(val oldName: String?)

/** Rounded, filled search field like in Microsoft To Do. */
@Composable
private fun SearchBar(state: AppState, focus: FocusRequester) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(38.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, end = 4.dp)) {
            Icon(Icons.Outlined.Search, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (state.search.isEmpty()) {
                    Text("Suchen", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BasicTextField(
                    value = state.search,
                    onValueChange = { state.search = it.replace("\n", ""); if (it.isNotBlank()) state.showSettings = false },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { state.search = ""; true } else false
                    },
                )
            }
            if (state.search.isNotEmpty()) {
                IconButton(onClick = { state.search = "" }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Outlined.Close, "Suche leeren", Modifier.size(16.dp))
                }
            } else {
                Text("Strg+F", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
            }
        }
    }
}

@Composable
private fun GroupHeader(
    name: String,
    collapsed: Boolean,
    empty: Boolean,
    onToggle: () -> Unit,
    onNewList: () -> Unit,
    onRename: () -> Unit,
    onDissolve: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    ContextMenuArea(items = {
        listOf(
            ContextMenuItem("Neue Liste in der Gruppe", onNewList),
            ContextMenuItem("Gruppe umbenennen", onRename),
            ContextMenuItem("Gruppe auflösen", onDissolve),
        )
    }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(24.dp))
                .clickable(onClick = onToggle).heightIn(min = 44.dp).padding(start = 16.dp, end = 4.dp),
        ) {
            Icon(if (collapsed) Icons.Outlined.Folder else Icons.Outlined.FolderOpen, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (empty) {
                    Text(
                        "Rechtsklick auf eine Liste → In Gruppe verschieben",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.MoreVert, "Gruppe bearbeiten", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Neue Liste in der Gruppe") }, leadingIcon = { Icon(Icons.Outlined.Add, null) }, onClick = { menu = false; onNewList() })
                    DropdownMenuItem(text = { Text("Gruppe umbenennen") }, leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text("Gruppe auflösen") }, leadingIcon = { Icon(Icons.Outlined.FolderOff, null) }, onClick = { menu = false; onDissolve() })
                }
            }
            Icon(
                if (collapsed) Icons.AutoMirrored.Outlined.KeyboardArrowRight else Icons.Outlined.ExpandMore, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun ListEntry(state: AppState, data: LocalData, list: TaskList, indent: Boolean, onMove: (TaskList) -> Unit) {
    val open = tasksFor(Selection.OfList(list.url), data, state.hidden).count { !it.todo.completed }
    val selected = !state.showSettings && state.search.isBlank() && state.selection == Selection.OfList(list.url)
    ContextMenuArea(items = {
        listOf(ContextMenuItem(if (list.group == null) "In Gruppe verschieben …" else "Gruppe ändern …") { onMove(list) })
    }) {
        Box(Modifier.padding(start = if (indent) 24.dp else 0.dp)) {
            MenuEntry(list.label(), Icons.AutoMirrored.Outlined.List, parseColor(list.color), open, selected) {
                state.search = ""
                state.showSettings = false
                state.selection = Selection.OfList(list.url)
            }
        }
    }
}

@Composable
private fun MenuEntry(title: String, icon: ImageVector, tint: Color, count: Int, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        icon = { Icon(icon, null, tint = tint) },
        badge = { if (count > 0) Text(count.toString(), style = MaterialTheme.typography.labelMedium) },
        selected = selected, onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp).height(44.dp),
    )
}

// ---------------------------------------------------------------- task list

@Composable
private fun TaskPane(state: AppState, data: LocalData, offline: Boolean, syncing: Boolean, addFocus: FocusRequester, modifier: Modifier) {
    val sel = state.selection
    val searching = state.search.isNotBlank()
    val list = (sel as? Selection.OfList)?.let { data.list(it.url) }
    val title = when {
        searching -> "Suche: „${state.search.trim()}“"
        list != null -> list.label()
        sel is Selection.Smart -> sel.kind.title
        else -> "Liste"
    }
    val accent = when {
        searching -> MaterialTheme.colorScheme.primary
        list != null -> parseColor(list.color)
        sel is Selection.Smart -> sel.kind.color()
        else -> AccentBlue
    }
    val tasks = if (searching) searchTasks(state.search, data, state.hidden) else tasksFor(sel, data, state.hidden)
    val open = tasks.filter { !it.todo.completed }
    val done = tasks.filter { it.todo.completed }
    var showDone by remember(sel) { mutableStateOf(false) }
    val canAdd = !searching && (list?.canWrite ?: (sel is Selection.Smart))

    Column(modifier.padding(horizontal = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 20.dp, bottom = 12.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = accent, modifier = Modifier.weight(1f))
            if (offline) Icon(Icons.Outlined.CloudOff, "Offline", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = state::syncNow, enabled = !syncing) {
                Icon(Icons.Outlined.Sync, "Synchronisieren (Strg+R)", tint = if (syncing) accent else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(open, key = { it.url }) { TaskRow(state, data, it, accent, showList = sel is Selection.Smart || searching) }
            if (done.isNotEmpty()) {
                item(key = "done-header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 8.dp).clip(RoundedCornerShape(8.dp)).clickable { showDone = !showDone }.padding(8.dp),
                    ) {
                        Icon(if (showDone) Icons.Outlined.ExpandMore else Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Erledigt (${done.size})", fontWeight = FontWeight.SemiBold)
                    }
                }
                if (showDone) items(done, key = { it.url }) { TaskRow(state, data, it, accent, showList = sel is Selection.Smart || searching) }
            }
        }
        if (canAdd) AddTaskField(accent, addFocus) { state.addTask(it) }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun TaskRow(state: AppState, data: LocalData, item: TaskItem, accent: Color, showList: Boolean) {
    val t = item.todo
    val writable = data.list(item.listUrl)?.canWrite == true
    val selected = state.selectedTask == item.url
    val steps = stepsOf(item, data)
    val rowFocus = remember { FocusRequester() }
    val meta = buildList {
        if (showList) data.list(item.listUrl)?.let { add(it.label()) }
        if (steps.isNotEmpty()) add("${steps.count { it.todo.completed }} von ${steps.size}")
        t.due?.let { add(formatDate(it)) }
    }
    // Small symbols like in Microsoft To Do: repeats, has a link, has a note.
    val badges = buildList {
        if (t.recurring) add(Icons.Outlined.Repeat to "Wiederholt sich")
        if (t.links.isNotEmpty()) add(Icons.Outlined.Link to (if (t.links.size == 1) "1 Link" else "${t.links.size} Links"))
        if (t.description.isNotBlank()) add(Icons.AutoMirrored.Outlined.StickyNote2 to "Notiz")
    }
    ContextMenuArea(items = {
        if (!writable) emptyList() else listOf(
            ContextMenuItem(if (t.completed) "Als nicht erledigt markieren" else "Als erledigt markieren") { state.toggleDone(item) },
            ContextMenuItem(if (t.starred) "Wichtig entfernen" else "Als wichtig markieren") { state.toggleStar(item) },
            ContextMenuItem("Heute fällig") { state.setDue(item, LocalDate.now()) },
            ContextMenuItem("Löschen (Entf)") { state.delete(item) },
        )
    }) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            tonalElevation = if (selected) 4.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
                .focusRequester(rowFocus)
                .onKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.Delete && writable) { state.delete(item); true } else false
                }
                .focusable()
                .clickable {
                    state.selectedTask = item.url
                    // Focus the row so the Delete key works on it.
                    runCatching { rowFocus.requestFocus() }
                },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 52.dp).padding(horizontal = 8.dp)) {
                IconButton(onClick = { state.toggleDone(item) }, enabled = writable) {
                    Icon(
                        if (t.completed) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        if (t.completed) "Nicht erledigt" else "Erledigt", tint = accent,
                    )
                }
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(
                        t.summary,
                        textDecoration = if (t.completed) TextDecoration.LineThrough else null,
                        color = if (t.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    if (meta.isNotEmpty() || badges.isNotEmpty()) {
                        val overdue = t.due?.let { it.isBefore(LocalDate.now()) } == true && !t.completed
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (meta.isNotEmpty()) {
                                Text(
                                    meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                                )
                            }
                            badges.forEachIndexed { i, (icon, label) ->
                                if (i == 0 && meta.isNotEmpty()) Text("·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Icon(icon, label, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                IconButton(onClick = { state.toggleStar(item) }, enabled = writable) {
                    Icon(
                        if (t.starred) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                        if (t.starred) "Wichtig entfernen" else "Als wichtig markieren",
                        tint = if (t.starred) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AddTaskField(accent: Color, focus: FocusRequester, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    fun submit() {
        if (text.isNotBlank()) {
            onAdd(text)
            text = ""
        }
    }
    TextField(
        value = text, onValueChange = { text = it },
        placeholder = { Text("Aufgabe hinzufügen (Strg+N)", color = accent) },
        leadingIcon = { Icon(Icons.Outlined.Add, null, tint = accent) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        shape = RoundedCornerShape(6.dp),
        colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { e ->
            // Enter in a single line field: add the task.
            if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) { submit(); true } else false
        },
    )
}

// ---------------------------------------------------------------- details

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailPane(state: AppState, item: TaskItem, data: LocalData, modifier: Modifier) {
    val t = item.todo
    val list = data.list(item.listUrl)
    val writable = list?.canWrite == true
    val accent = list?.let { parseColor(it.color) } ?: AccentBlue
    var title by remember(item.url) { mutableStateOf(t.summary) }
    var notes by remember(item.url) { mutableStateOf(t.description) }
    var newStep by remember(item.url) { mutableStateOf("") }
    var pickDate by remember { mutableStateOf(false) }

    // Save title and notes shortly after typing stops.
    LaunchedEffect(item.url, title) { delay(600); state.rename(item, title) }
    LaunchedEffect(item.url, notes) { delay(800); state.setNotes(item, notes) }

    Column(
        modifier.background(MaterialTheme.colorScheme.surfaceContainerLow).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(list?.label().orEmpty(), color = accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = { state.selectedTask = null }) { Icon(Icons.Outlined.Close, "Schließen") }
        }
        Surface(shape = RoundedCornerShape(8.dp), tonalElevation = 1.dp) {
            Column(Modifier.padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { state.toggleDone(item) }, enabled = writable) {
                        Icon(if (t.completed) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null, tint = accent)
                    }
                    TextField(
                        value = title, onValueChange = { title = it.replace("\n", " ") }, enabled = writable,
                        textStyle = MaterialTheme.typography.titleMedium,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { state.toggleStar(item) }, enabled = writable) {
                        Icon(if (t.starred) Icons.Outlined.Star else Icons.Outlined.StarBorder, null, tint = if (t.starred) accent else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // Steps
                stepsOf(item, data).forEach { step ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                        IconButton(onClick = { state.toggleDone(step) }, enabled = writable, modifier = Modifier.size(36.dp)) {
                            Icon(
                                if (step.todo.completed) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
                                tint = accent, modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(
                            step.todo.summary, modifier = Modifier.weight(1f),
                            textDecoration = if (step.todo.completed) TextDecoration.LineThrough else null,
                        )
                        if (writable) IconButton(onClick = { state.delete(step) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.Close, "Schritt entfernen", modifier = Modifier.size(16.dp))
                        }
                    }
                }
                if (writable) {
                    TextField(
                        value = newStep, onValueChange = { newStep = it.replace("\n", "") },
                        placeholder = { Text("Nächster Schritt", color = accent) },
                        leadingIcon = { Icon(Icons.Outlined.Add, null, tint = accent) }, singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter) && newStep.isNotBlank()) {
                                state.addStep(item, newStep); newStep = ""; true
                            } else false
                        },
                    )
                }
            }
        }
        // My Day and due date
        Surface(shape = RoundedCornerShape(8.dp), tonalElevation = 1.dp) {
            Column {
                val today = LocalDate.now()
                val inMyDay = t.due?.let { !it.isAfter(today) } == true
                DetailRow(Icons.Outlined.WbSunny, if (inMyDay) "In „Mein Tag“" else "Zu „Mein Tag“ hinzufügen", inMyDay, accent, writable) {
                    state.setDue(item, if (inMyDay) null else today)
                }
                HorizontalDivider()
                DetailRow(
                    Icons.Outlined.CalendarMonth,
                    t.due?.let { "Fällig: " + formatDate(it) } ?: "Fälligkeitsdatum hinzufügen",
                    t.due != null, accent, writable,
                    onClear = if (writable && t.due != null) ({ state.setDue(item, null) }) else null,
                ) { pickDate = true }
            }
        }
        // Links
        LinksSection(state, item, writable, accent)
        // Notes
        OutlinedTextField(
            value = notes, onValueChange = { notes = it }, enabled = writable,
            label = { Text("Notiz") }, minLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                t.created?.let { "Erstellt " + DateTimeFormatter.ofPattern("dd.MM.yyyy").format(it.atZone(java.time.ZoneId.systemDefault())) }.orEmpty(),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
            )
            if (writable) IconButton(onClick = { state.delete(item) }) { Icon(Icons.Outlined.Delete, "Aufgabe löschen") }
        }
    }

    if (pickDate) {
        val initial = (t.due ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val picker = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { state.setDue(item, Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    pickDate = false
                }) { Text("Übernehmen") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Abbrechen") } },
        ) { DatePicker(picker) }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    text: String,
    highlighted: Boolean,
    accent: Color,
    enabled: Boolean,
    onClear: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 16.dp),
    ) {
        Icon(icon, null, tint = if (highlighted) accent else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(text, color = if (highlighted) accent else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        if (onClear != null) IconButton(onClick = onClear) { Icon(Icons.Outlined.Close, "Entfernen", modifier = Modifier.size(18.dp)) }
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("EE, dd.MM.yyyy", java.util.Locale.GERMAN)

private fun formatDate(d: LocalDate): String {
    val today = LocalDate.now()
    return when (d) {
        today -> "Heute"
        today.plusDays(1) -> "Morgen"
        today.minusDays(1) -> "Gestern"
        else -> dateFormat.format(d)
    }
}
