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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
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
    val data = remember(raw) { raw.forDisplay() }
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
            TaskPane(state, data, status.offline, status.running, addFocus, Modifier.weight(1f).fillMaxHeight())
            val selected = state.selectedTask?.let { data.task(it) }
            if (selected != null) {
                VerticalDivider()
                DetailPane(state, selected, data, Modifier.width(380.dp).fillMaxHeight())
            }
        }
    }
}

// ---------------------------------------------------------------- sidebar

@Composable
private fun Sidebar(state: AppState, data: LocalData, searchFocus: FocusRequester, modifier: Modifier) {
    var accountMenu by remember { mutableStateOf(false) }
    var newList by remember { mutableStateOf(false) }
    val creds = state.credentials ?: return
    fun count(kind: SmartList) = tasksFor(Selection.Smart(kind), data, state.hidden).count { !it.todo.completed }

    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        // Account (pinned at the top)
        Box(Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { accountMenu = true }.padding(8.dp),
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(AccentBlue.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) { Text(creds.displayName.take(1).uppercase(), color = AccentBlue, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(creds.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        creds.serverUrl.removePrefix("https://").trimEnd('/'),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(Icons.Outlined.UnfoldMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Jetzt synchronisieren (Strg+R)") }, leadingIcon = { Icon(Icons.Outlined.Sync, null) },
                    onClick = { accountMenu = false; state.syncNow() },
                )
                DropdownMenuItem(
                    text = { Text("Abmelden") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, null) },
                    onClick = { accountMenu = false; state.logout() },
                )
            }
        }
        // Search
        OutlinedTextField(
            value = state.search, onValueChange = { state.search = it },
            placeholder = { Text("Suchen (Strg+F)") }, singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = { if (state.search.isNotEmpty()) IconButton(onClick = { state.search = "" }) { Icon(Icons.Outlined.Close, "Suche leeren") } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).focusRequester(searchFocus),
        )
        Spacer(Modifier.height(8.dp))
        // Smart lists, lists and groups (scrolling)
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(SmartList.entries) { kind ->
                MenuEntry(kind.title, kind.icon(), kind.color(), count(kind), state.selection == Selection.Smart(kind)) {
                    state.search = ""
                    state.selection = Selection.Smart(kind)
                }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 16.dp)) }
            val (ungrouped, groups) = groupedLists(data.lists)
            items(ungrouped, key = { it.url }) { ListEntry(state, data, it, indent = false) }
            groups.forEach { (name, lists) ->
                val collapsed = name in state.collapsedGroups
                item(key = "group:$name") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(24.dp))
                            .clickable { state.toggleGroup(name) }.height(44.dp).padding(horizontal = 16.dp),
                    ) {
                        Icon(if (collapsed) Icons.Outlined.Folder else Icons.Outlined.FolderOpen, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Text(name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(
                            if (collapsed) Icons.AutoMirrored.Outlined.KeyboardArrowRight else Icons.Outlined.ExpandMore, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!collapsed) items(lists, key = { it.url }) { ListEntry(state, data, it, indent = true) }
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        NavigationDrawerItem(
            label = { Text("Neue Liste") }, icon = { Icon(Icons.Outlined.Add, null) },
            selected = false, onClick = { newList = true }, modifier = Modifier.padding(12.dp),
        )
    }
    if (newList) NewListDialog(onDismiss = { newList = false }) { name, color ->
        newList = false
        state.createList(name, color)
    }
}

@Composable
private fun ListEntry(state: AppState, data: LocalData, list: TaskList, indent: Boolean) {
    val open = tasksFor(Selection.OfList(list.url), data, state.hidden).count { !it.todo.completed }
    val selected = state.selection == Selection.OfList(list.url)
    Box(Modifier.padding(start = if (indent) 24.dp else 0.dp)) {
        MenuEntry(list.label(), Icons.AutoMirrored.Outlined.List, parseColor(list.color), open, selected) {
            state.search = ""
            state.selection = Selection.OfList(list.url)
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

@Composable
private fun NewListDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(CalDavRepository.COLORS.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neue Liste") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true,
                    keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onCreate(name, color) }),
                    keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
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
                    if (meta.isNotEmpty()) {
                        val overdue = t.due?.isBefore(LocalDate.now()) == true && !t.completed
                        Text(
                            meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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

    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
        // Notes
        OutlinedTextField(
            value = notes, onValueChange = { notes = it }, enabled = writable,
            label = { Text("Notiz") }, minLines = 4,
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
        )
        Spacer(Modifier.weight(1f))
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
