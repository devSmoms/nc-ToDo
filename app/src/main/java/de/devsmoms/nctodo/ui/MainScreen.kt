package de.devsmoms.nctodo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.devsmoms.nctodo.data.TaskItem
import de.devsmoms.nctodo.data.TaskList
import de.devsmoms.nctodo.ui.theme.parseColor
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AppRoot(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messageFlow.collect { snackbar.showSnackbar(it) } }
    // A new swipe replaces the previous undo offer; the previous deletion is then carried out.
    LaunchedEffect(Unit) {
        vm.undoFlow.collectLatest { ev ->
            var undone = false
            try {
                undone = snackbar.showSnackbar(ev.text, actionLabel = "Rückgängig", duration = SnackbarDuration.Short) ==
                    SnackbarResult.ActionPerformed
            } finally {
                if (undone) vm.undoDelete(ev.taskUrl) else vm.commitDelete(ev.taskUrl)
            }
        }
    }

    when {
        !state.ready -> Box(Modifier.fillMaxSize())
        state.credentials == null -> LoginScreen(state.loginBusy, state.loginError, vm::login)
        else -> when (val s = state.screen) {
            is Screen.Main -> MainScreen(state, vm, snackbar)
            is Screen.Detail -> {
                BackHandler { vm.back() }
                val task = state.task(s.taskUrl)
                if (task == null) LaunchedEffect(Unit) { vm.back() } else TaskDetailScreen(state, task, vm, snackbar)
            }
            is Screen.Share -> {
                BackHandler { vm.back() }
                val list = state.list(s.listUrl)
                if (list == null) LaunchedEffect(Unit) { vm.back() } else ShareScreen(state, list, vm, snackbar)
            }
            is Screen.Settings -> {
                BackHandler { vm.back() }
                SettingsScreen(state, vm, snackbar)
            }
            is Screen.Trash -> {
                BackHandler { vm.back() }
                TrashScreen(state, vm, snackbar)
            }
        }
    }
}

/** Tasks visible in the current selection, top-level only (steps are shown inside their task). */
fun visibleTasks(state: UiState): List<TaskItem> = tasksForSelection(state.selection, state.tasks, state.hidden)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(state: UiState, vm: AppViewModel, snackbar: SnackbarHostState) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var listDialog by remember { mutableStateOf<ListDialogMode?>(null) }
    var confirmDelete by remember { mutableStateOf<TaskList?>(null) }

    val sel = state.selection
    val currentList = (sel as? Selection.OfList)?.let { state.list(it.url) }
    val title = currentList?.let { ListIcons.label(it.name) } ?: (sel as? Selection.Smart)?.kind?.title.orEmpty()
    val accent = currentList?.let { parseColor(it.color) } ?: MaterialTheme.colorScheme.primary

    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            Drawer(state, onSelect = { vm.select(it); scope.launch { drawer.close() } }, onNewList = {
                listDialog = ListDialogMode.Create
                scope.launch { drawer.close() }
            }, onSettings = {
                vm.open(Screen.Settings)
                scope.launch { drawer.close() }
            }, onTrash = {
                vm.open(Screen.Trash)
                vm.loadTrash()
                scope.launch { drawer.close() }
            })
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = {},
                    navigationIcon = { IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Outlined.Menu, "Listen") } },
                    actions = {
                        if (currentList != null && currentList.isOwner) {
                            IconButton(onClick = { vm.open(Screen.Share(currentList.url)) }) { Icon(Icons.Outlined.PersonAdd, "Teilen") }
                        }
                        if (currentList != null) {
                            var menu by remember { mutableStateOf(false) }
                            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Mehr") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                if (currentList.isOwner) {
                                    DropdownMenuItem(text = { Text("Liste bearbeiten") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = {
                                        menu = false; listDialog = ListDialogMode.Edit(currentList)
                                    })
                                }
                                DropdownMenuItem(
                                    text = { Text(if (currentList.isOwner) "Liste löschen" else "Freigabe verlassen") },
                                    onClick = { menu = false; confirmDelete = currentList },
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            },
            bottomBar = {
                val canAdd = currentList?.canWrite ?: (vm.defaultList() != null)
                if (canAdd) AddTaskBar(accent, (sel as? Selection.Smart)?.kind, state.settings.allDayDefault, onAdd = vm::addTask)
            },
        ) { padding ->
            PullToRefreshBox(isRefreshing = state.syncing, onRefresh = vm::refresh, modifier = Modifier.padding(padding).fillMaxSize()) {
                TaskListContent(state, title, accent, currentList, vm)
            }
        }
    }

    listDialog?.let { mode ->
        ListDialog(mode, onDismiss = { listDialog = null }) { name, color, description ->
            listDialog = null
            when (mode) {
                is ListDialogMode.Create -> vm.createList(name, color, description)
                is ListDialogMode.Edit -> vm.updateList(mode.list, name, color, description)
            }
        }
    }
    confirmDelete?.let { list ->
        ConfirmDialog(
            title = if (list.isOwner) "Liste „${ListIcons.label(list.name)}“ löschen?" else "Freigabe „${ListIcons.label(list.name)}“ verlassen?",
            text = if (list.isOwner) "Alle Aufgaben darin werden auch für alle, mit denen die Liste geteilt ist, gelöscht."
            else "Die Liste verschwindet nur bei dir.",
            confirm = if (list.isOwner) "Löschen" else "Verlassen",
            onDismiss = { confirmDelete = null },
        ) { confirmDelete = null; vm.deleteList(list) }
    }
}

@Composable
private fun TaskListContent(state: UiState, title: String, accent: Color, list: TaskList?, vm: AppViewModel) {
    val tasks = visibleTasks(state)
    val open = tasks.filter { !it.todo.completed }
    val done = tasks.filter { it.todo.completed }.sortedByDescending { it.todo.completedAt }
    var showDone by rememberSaveable { mutableStateOf(false) }
    val smart = (state.selection as? Selection.Smart)?.kind
    val today = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN).format(LocalDate.now())

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val headIcon = list?.let { ListIcons.iconOf(it.name) }
                    if (headIcon != null) {
                        Icon(headIcon, null, Modifier.size(30.dp), tint = accent)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = accent)
                }
                if (smart == SmartList.MY_DAY) Text(today, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                list?.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                }
                if (list != null && !list.isOwner) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(if (list.canWrite) Icons.Outlined.People else Icons.Outlined.Lock, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Geteilt von ${list.ownerName ?: "anderem Nutzer"}" + if (list.canWrite) "" else " · nur lesen",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (list != null && list.isOwner && list.shares.isNotEmpty()) {
                    Text(
                        "Geteilt mit ${list.shares.joinToString { it.displayName }}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        if (state.offline) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                        Icon(Icons.Outlined.CloudOff, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (state.pendingCount > 0) "Offline – ${state.pendingCount} Änderung(en) werden übertragen, sobald eine Verbindung besteht."
                            else "Offline – du siehst den zuletzt gespeicherten Stand.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        if (open.isEmpty() && done.isEmpty() && !state.syncing) {
            item {
                Text(
                    if (smart == SmartList.MY_DAY) "Nichts für heute geplant. Neue Aufgaben hier landen automatisch in „Mein Tag“."
                    else "Noch keine Aufgaben.",
                    modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(open, key = { it.url }) { SwipeToDelete(it, state, vm) { TaskRow(it, state, accent, vm) } }
        if (done.isNotEmpty()) {
            item {
                TextButton(onClick = { showDone = !showDone }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Icon(if (showDone) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Erledigt (${done.size})")
                }
            }
            if (showDone) items(done, key = { it.url }) { SwipeToDelete(it, state, vm) { TaskRow(it, state, accent, vm) } }
        }
    }
}

/** Share of the row width that has to be swiped before a task is deleted. */
private const val SWIPE_DELETE_FRACTION = 0.5f

/** Swiping a task to the left or right deletes it (with undo); disabled in read-only lists. */
@Composable
private fun SwipeToDelete(item: TaskItem, state: UiState, vm: AppViewModel, content: @Composable () -> Unit) {
    val writable = state.list(item.listUrl)?.canWrite == true
    // The state is needed inside its own confirm callback to check how far the row was moved.
    val holder = remember { arrayOfNulls<SwipeToDismissBoxState>(1) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            // Delete only if the row was dragged past half of its width – a quick flick is not enough.
            val farEnough = (holder[0]?.progress ?: 0f) >= SWIPE_DELETE_FRACTION
            if (value != SwipeToDismissBoxValue.Settled && farEnough) {
                vm.swipeDelete(item)
                true
            } else {
                false
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * SWIPE_DELETE_FRACTION },
    )
    holder[0] = dismissState
    val armed = dismissState.targetValue != SwipeToDismissBoxValue.Settled && dismissState.progress >= SWIPE_DELETE_FRACTION
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = writable,
        enableDismissFromEndToStart = writable,
        backgroundContent = {
            val fromStart = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Box(
                Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 3.dp)
                    .clip(RoundedCornerShape(8.dp))
                    // Turns strong red once releasing would delete.
                    .background(if (armed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (fromStart) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                val fg = if (armed) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onErrorContainer
                val label = if (armed) "Loslassen zum Löschen" else "Löschen"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!fromStart) Text(label, color = fg, style = MaterialTheme.typography.labelLarge)
                    if (!fromStart) Spacer(Modifier.width(8.dp))
                    Icon(Icons.Outlined.Delete, null, tint = fg)
                    if (fromStart) Spacer(Modifier.width(8.dp))
                    if (fromStart) Text(label, color = fg, style = MaterialTheme.typography.labelLarge)
                }
            }
        },
    ) { content() }
}

@Composable
fun TaskRow(item: TaskItem, state: UiState, accent: Color, vm: AppViewModel) {
    val list = state.list(item.listUrl)
    val writable = list?.canWrite == true
    val steps = state.tasks[item.listUrl].orEmpty().filter { it.todo.parentUid == item.todo.uid }
    val t = item.todo
    Surface(
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp).clip(RoundedCornerShape(8.dp))
            .clickable { vm.open(Screen.Detail(item.url)) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            IconButton(onClick = { vm.setCompleted(item, !t.completed) }, enabled = writable) {
                if (t.completed) Icon(Icons.Filled.CheckCircle, "Als offen markieren", tint = accent)
                else Icon(Icons.Outlined.RadioButtonUnchecked, "Erledigen", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    t.summary.ifBlank { "(ohne Titel)" },
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (t.completed) TextDecoration.LineThrough else null,
                    color = if (t.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                val meta = buildList {
                    if (state.selection is Selection.Smart && list != null) add(ListIcons.label(list.name))
                    if (steps.isNotEmpty()) add("${steps.count { it.todo.completed }} von ${steps.size}")
                }
                val due = t.due
                val dueText = due?.let { formatDue(it, t.dueTime) }
                val muted = MaterialTheme.colorScheme.onSurfaceVariant
                if (meta.isNotEmpty() || dueText != null || t.description.isNotBlank() || t.recurring || t.assignee != null || t.links.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1)
                        if (dueText != null) {
                            val color = if (isOverdue(due, t.dueTime, t.completed)) MaterialTheme.colorScheme.error else muted
                            Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(14.dp), tint = color)
                            Text(dueText, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1)
                        }
                        if (t.recurring) Icon(Icons.Outlined.Repeat, "Wiederholt sich", Modifier.size(14.dp), tint = muted)
                        t.assignee?.let { a ->
                            Icon(Icons.Outlined.Person, "Zuständig", Modifier.size(14.dp), tint = muted)
                            Text(a.name.substringBefore(' '), style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1)
                        }
                        if (t.links.isNotEmpty()) Icon(Icons.Outlined.Link, "Links", Modifier.size(14.dp), tint = muted)
                        if (t.description.isNotBlank()) Icon(Icons.AutoMirrored.Outlined.Notes, "Notiz", Modifier.size(14.dp), tint = muted)
                    }
                }
            }
            IconButton(onClick = { vm.setStarred(item, !t.starred) }, enabled = writable) {
                if (t.starred) Icon(Icons.Filled.Star, "Wichtig entfernen", tint = accent)
                else Icon(Icons.Outlined.StarBorder, "Als wichtig markieren", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Input bar at the bottom. While it is focused (or something is entered) small quick options
 * appear: "Mein Tag", due date and "Wichtig" – like in Microsoft To Do.
 */
@Composable
private fun AddTaskBar(accent: Color, smart: SmartList?, allDayDefault: Boolean, onAdd: (String, DueValue?, Boolean) -> Unit) {
    val today = LocalDate.now()
    // Smart views pre-select what they imply; the user can still change it.
    val defaultDue = if (smart == SmartList.MY_DAY || smart == SmartList.PLANNED) DueValue(today) else null
    val defaultStar = smart == SmartList.IMPORTANT

    var text by rememberSaveable { mutableStateOf("") }
    var due by remember(smart) { mutableStateOf(defaultDue) }
    var starred by remember(smart) { mutableStateOf(defaultStar) }
    var focused by remember { mutableStateOf(false) }
    var popupOpen by remember { mutableStateOf(false) }

    fun submit() {
        if (text.isNotBlank()) onAdd(text, due, starred)
        text = ""
        due = defaultDue
        starred = defaultStar
    }

    val showOptions = focused || popupOpen || text.isNotEmpty() || due != defaultDue || starred != defaultStar

    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
        Column {
            TextField(
                value = text, onValueChange = { text = it.replace("\n", " ") },
                placeholder = { Text("Aufgabe hinzufügen") },
                leadingIcon = { Icon(Icons.Outlined.Add, null, tint = accent) },
                trailingIcon = {
                    if (text.isNotBlank()) IconButton(onClick = { submit() }) {
                        Icon(Icons.AutoMirrored.Outlined.Send, "Hinzufügen", tint = accent)
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                    .onFocusChanged { focused = it.isFocused },
            )
            AnimatedVisibility(showOptions) {
                TaskQuickOptions(
                    due = due, starred = starred,
                    onDue = { due = it }, onStarred = { starred = it },
                    allDayDefault = allDayDefault,
                    onPopupChange = { popupOpen = it },
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Drawer(state: UiState, onSelect: (Selection) -> Unit, onNewList: () -> Unit, onSettings: () -> Unit, onTrash: () -> Unit) {
    fun count(kind: SmartList) = visibleTasks(state.copy(selection = Selection.Smart(kind))).count { !it.todo.completed }
    ModalDrawerSheet {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) { Text(state.credentials?.displayName?.take(1)?.uppercase().orEmpty(), fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(state.credentials?.displayName.orEmpty(), fontWeight = FontWeight.SemiBold)
                        Text(state.credentials?.serverUrl?.removePrefix("https://").orEmpty(), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Einstellungen") }
                }
            }
            val smart = listOf(
                Triple(SmartList.MY_DAY, Icons.Outlined.WbSunny, Color(0xFF9E7B00)),
                Triple(SmartList.IMPORTANT, Icons.Outlined.StarBorder, Color(0xFFC2185B)),
                Triple(SmartList.PLANNED, Icons.Outlined.CalendarMonth, Color(0xFF00897B)),
                Triple(SmartList.ALL, Icons.Outlined.Home, Color(0xFF2564CF)),
            )
            items(smart) { (kind, icon, tint) ->
                DrawerEntry(kind.title, icon, tint, count(kind), state.selection == Selection.Smart(kind)) { onSelect(Selection.Smart(kind)) }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 16.dp)) }
            items(state.lists, key = { it.url }) { list ->
                val open = state.tasks[list.url].orEmpty().count { !it.todo.completed && it.todo.parentUid == null && it.url !in state.hidden }
                val status = when {
                    !list.isOwner && !list.canWrite -> Icons.Outlined.Lock
                    !list.isOwner || list.shares.isNotEmpty() -> Icons.Outlined.People
                    else -> null
                }
                // Own icon if set; otherwise the sharing state is the icon, as before.
                val custom = ListIcons.iconOf(list.name)
                DrawerEntry(
                    ListIcons.label(list.name), custom ?: status ?: ListIcons.DEFAULT, parseColor(list.color), open,
                    state.selection == Selection.OfList(list.url), statusIcon = if (custom != null) status else null,
                ) {
                    onSelect(Selection.OfList(list.url))
                }
            }
            item {
                NavigationDrawerItem(
                    label = { Text("Neue Liste") },
                    icon = { Icon(Icons.Outlined.Add, null) },
                    selected = false, onClick = onNewList,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 16.dp))
                NavigationDrawerItem(
                    label = { Text("Papierkorb") },
                    icon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    selected = false, onClick = onTrash,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DrawerEntry(
    label: String,
    icon: ImageVector,
    tint: Color,
    count: Int,
    selected: Boolean,
    statusIcon: ImageVector? = null,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (statusIcon != null) {
                    Spacer(Modifier.width(6.dp))
                    Icon(statusIcon, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        icon = { Icon(icon, null, tint = tint) },
        badge = { if (count > 0) Text(count.toString()) },
        selected = selected, onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}
