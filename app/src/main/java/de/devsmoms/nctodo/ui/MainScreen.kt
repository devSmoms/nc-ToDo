package de.devsmoms.nctodo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.outlined.UnfoldMore
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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.abs
import kotlin.math.sign
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
                if (undone) vm.undoDelete(ev.taskUrl) else vm.commitDelete(ev.taskUrl, ev.account)
            }
        }
    }

    when {
        !state.ready -> Box(Modifier.fillMaxSize())
        state.credentials == null || state.addingAccount -> {
            // "Konto hinzufügen" on top of an existing account can be cancelled.
            val cancel: (() -> Unit)? = if (state.credentials != null) vm::cancelAddAccount else null
            if (cancel != null) BackHandler { cancel() }
            LoginScreen(
                busy = state.loginBusy,
                error = state.loginError,
                flowUrl = state.loginFlowUrl,
                onCancel = cancel,
                onBrowserLogin = vm::startBrowserLogin,
                onCancelBrowserLogin = vm::cancelBrowserLogin,
                onLogin = vm::login,
            )
        }
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
    var accountDialog by remember { mutableStateOf(false) }
    var groupPicker by remember { mutableStateOf<TaskList?>(null) }
    var renameGroup by remember { mutableStateOf<String?>(null) }
    var newGroup by remember { mutableStateOf(false) }
    var dissolveGroup by remember { mutableStateOf<String?>(null) }

    val sel = state.selection
    val currentList = (sel as? Selection.OfList)?.let { state.list(it.url) }
    val title = currentList?.let { ListIcons.label(it.name) } ?: (sel as? Selection.Smart)?.kind?.title.orEmpty()
    val accent = currentList?.let { parseColor(it.color) } ?: MaterialTheme.colorScheme.primary

    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            Drawer(state, onSelect = { vm.select(it); scope.launch { drawer.close() } }, onNewList = {
                listDialog = ListDialogMode.Create()
                scope.launch { drawer.close() }
            }, onSettings = {
                vm.open(Screen.Settings)
                scope.launch { drawer.close() }
            }, onTrash = {
                vm.open(Screen.Trash)
                vm.loadTrash()
                scope.launch { drawer.close() }
            }, onAccounts = { accountDialog = true }, onGroupAction = { action ->
                when (action) {
                    is GroupAction.Toggle -> vm.toggleGroup(action.name)
                    is GroupAction.NewList -> {
                        listDialog = ListDialogMode.Create(group = action.name)
                        scope.launch { drawer.close() }
                    }
                    is GroupAction.Rename -> renameGroup = action.name
                    is GroupAction.Dissolve -> dissolveGroup = action.name
                }
            }, onNewGroup = { newGroup = true }, onArrange = vm::arrangeLists)
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
                                    text = { Text(if (currentList.group == null) "In Gruppe verschieben" else "Gruppe ändern") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, null) },
                                    onClick = { menu = false; groupPicker = currentList },
                                )
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

    if (accountDialog) {
        AccountDialog(
            state = state,
            onSwitch = { vm.switchAccount(it); scope.launch { drawer.close() } },
            onAdd = { vm.addAccount(); scope.launch { drawer.close() } },
            onLogout = vm::logout,
            pendingOf = vm::pendingCount,
            onDismiss = { accountDialog = false },
        )
    }
    listDialog?.let { mode ->
        ListDialog(mode, onDismiss = { listDialog = null }) { name, color, description ->
            listDialog = null
            when (mode) {
                is ListDialogMode.Create -> vm.createList(name, color, description, mode.group)
                is ListDialogMode.Edit -> vm.updateList(mode.list, name, color, description)
            }
        }
    }
    groupPicker?.let { list ->
        GroupPickerDialog(
            listName = ListIcons.label(list.name),
            current = list.group,
            groups = groupedLists(state.lists, state.settings.emptyGroups).second.map { it.first },
            onDismiss = { groupPicker = null },
        ) { group ->
            groupPicker = null
            vm.moveListToGroup(list, group)
        }
    }
    renameGroup?.let { name ->
        GroupNameDialog(name, onDismiss = { renameGroup = null }) { new ->
            renameGroup = null
            vm.renameGroup(name, new)
        }
    }
    if (newGroup) {
        GroupNameDialog("", onDismiss = { newGroup = false }) { name ->
            newGroup = false
            vm.createGroup(name)
        }
    }
    dissolveGroup?.let { name ->
        ConfirmDialog(
            title = "Gruppe „$name“ auflösen?",
            text = "Die Listen bleiben erhalten und stehen danach ohne Gruppe im Menü.",
            confirm = "Auflösen",
            onDismiss = { dissolveGroup = null },
        ) { dissolveGroup = null; vm.dissolveGroup(name) }
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
        items(open, key = { it.url }) { SwipeToDelete(it, state, vm, taskRowAnimation()) { TaskRow(it, state, accent, vm) } }
        if (done.isNotEmpty()) {
            item {
                TextButton(onClick = { showDone = !showDone }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Icon(if (showDone) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Erledigt (${done.size})")
                }
            }
            if (showDone) items(done, key = { it.url }) { SwipeToDelete(it, state, vm, taskRowAnimation()) { TaskRow(it, state, accent, vm) } }
        }
    }
}

/** Share of the row width that has to be swiped before a task is deleted. */
private const val SWIPE_DELETE_FRACTION = 0.5f

/** Fly-out after a swipe: starts slowly and accelerates off the screen. */
private const val SWIPE_FLY_OUT_MS = 220

/** Placement animation of the rows below when a task disappears (or comes back via undo). */
private fun LazyItemScope.taskRowAnimation(): Modifier = Modifier.animateItem(
    fadeInSpec = tween(200),
    placementSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
    fadeOutSpec = null,
)

/**
 * Swiping a task to the left or right deletes it (with undo); disabled in read-only lists.
 * Released past half of the width, the row accelerates out to that side, then it is removed and
 * the rows below slide up. Released earlier, it springs back. A quick flick never deletes.
 */
@Composable
private fun SwipeToDelete(
    item: TaskItem,
    state: UiState,
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val writable = state.list(item.listUrl)?.canWrite == true
    val haptic = LocalHapticFeedback.current
    // Plain state, written synchronously while dragging: no coroutine can arrive late and
    // cancel the fly-out animation (that left rows stuck half-way before).
    var offset by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(0) }
    var leaving by remember { mutableStateOf(false) }
    val threshold = width * SWIPE_DELETE_FRACTION
    val armed = width > 0 && abs(offset) >= threshold
    // Short vibration when crossing the threshold in either direction.
    LaunchedEffect(armed) { if (armed && !leaving) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }

    val dragState = rememberDraggableState { delta ->
        if (!leaving) offset = (offset + delta).coerceIn(-width.toFloat(), width.toFloat())
    }
    Box(
        modifier.fillMaxWidth().onSizeChanged { width = it.width }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                enabled = writable && !leaving,
                onDragStopped = {
                    if (width > 0 && abs(offset) >= threshold) {
                        leaving = true
                        val target = sign(offset) * width * 1.05f
                        // Accelerating ease-in: slow at the finger, fast at the edge.
                        val remaining = abs(target - offset) / (width * 1.05f)
                        try {
                            animate(
                                initialValue = offset, targetValue = target,
                                animationSpec = tween((SWIPE_FLY_OUT_MS * remaining).toInt().coerceAtLeast(90), easing = FastOutLinearInEasing),
                            ) { value, _ -> offset = value }
                        } finally {
                            // Delete even if the animation was interrupted; removing the row lets
                            // the rows below slide up (animateItem).
                            vm.swipeDelete(item)
                        }
                    } else {
                        animate(
                            initialValue = offset, targetValue = 0f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        ) { value, _ -> offset = value }
                    }
                },
            ),
    ) {
        if (offset != 0f) {
            val fromStart = offset > 0
            Box(
                Modifier.matchParentSize().padding(horizontal = 12.dp, vertical = 3.dp)
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
        }
        Box(Modifier.graphicsLayer { translationX = offset }) { content() }
    }
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
private fun Drawer(
    state: UiState,
    onSelect: (Selection) -> Unit,
    onNewList: () -> Unit,
    onSettings: () -> Unit,
    onTrash: () -> Unit,
    onAccounts: () -> Unit,
    onGroupAction: (GroupAction) -> Unit,
    onNewGroup: () -> Unit,
    onArrange: (List<Pair<String, String?>>) -> Unit,
) {
    fun count(kind: SmartList) = visibleTasks(state.copy(selection = Selection.Smart(kind))).count { !it.todo.completed }
    val listState = rememberLazyListState()
    val settings = state.settings
    val haptic = LocalHapticFeedback.current

    // Rows as stored; while dragging, [dragRows] holds the live order instead.
    val baseRows = remember(state.lists, settings.emptyGroups, settings.collapsedGroups) {
        drawerRows(state.lists, settings.emptyGroups, settings.collapsedGroups)
    }
    val members = remember(state.lists, settings.emptyGroups) { groupedLists(state.lists, settings.emptyGroups).second.toMap() }
    var dragRows by remember { mutableStateOf<List<DrawerRow>?>(null) }
    var dropped by remember { mutableStateOf(false) }
    val rows = dragRows ?: baseRows
    val inGroup = remember(rows) {
        var seenHeader = false
        rows.map { row -> if (row is DrawerRow.Header) seenHeader = true; seenHeader }
    }
    // Keep the dropped order until the new arrangement arrives (or give up after a moment, e.g. offline).
    LaunchedEffect(baseRows, dropped) {
        if (dropped) {
            if (dragRows?.map { it.key } == baseRows.map { it.key }) {
                dragRows = null
                dropped = false
            } else {
                delay(2000)
                dragRows = null
                dropped = false
            }
        }
    }
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val current = dragRows ?: baseRows
        val fromIdx = current.indexOfFirst { it.key == from.key }
        val toIdx = current.indexOfFirst { it.key == to.key }
        if (fromIdx >= 0 && toIdx >= 0 && fromIdx != toIdx) {
            val moved = if (current[fromIdx] is DrawerRow.Header) {
                // A group header carries its whole group along.
                moveGroup(current, from.key.toString(), to.key.toString())
            } else {
                current.toMutableList().apply { add(toIdx, removeAt(fromIdx)) }
            }
            if (moved != null) {
                dragRows = moved
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }
    fun finishDrag() {
        val final = dragRows ?: return
        val arrangement = arrangementOf(final, state.lists, settings.collapsedGroups)
        if (arrangement == arrangementOf(baseRows, state.lists, settings.collapsedGroups)) {
            dragRows = null
            return
        }
        dropped = true
        onArrange(arrangement)
    }
    // Account header (top) and trash (bottom) stay pinned; only the lists in between scroll.
    ModalDrawerSheet {
        Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            // Avatar and name open the account switcher.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onAccounts).padding(8.dp),
            ) {
                AccountAvatar(state.credentials?.displayName.orEmpty(), state.activeAccount)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.credentials?.displayName.orEmpty(), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        state.credentials?.serverUrl?.let(::serverLabel).orEmpty() +
                            if (state.accounts.size > 1) " · ${state.accounts.size} Konten" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(Icons.Outlined.UnfoldMore, "Konto wechseln", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Einstellungen") }
        }
        // Separator lines only appear while content is scrolled underneath them.
        EdgeDivider(visible = listState.canScrollBackward)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState) {
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
            itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                ReorderableItem(reorderState, key = row.key) { isDragging ->
                    when (row) {
                        is DrawerRow.Header -> {
                            val members = members[row.name].orEmpty()
                            val collapsed = row.name in settings.collapsedGroups
                            val interaction = remember { MutableInteractionSource() }
                            val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp, label = "dragGroup")
                            Surface(
                                shape = RoundedCornerShape(28.dp),
                                shadowElevation = elevation,
                                color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                                modifier = Modifier.padding(horizontal = if (isDragging) 8.dp else 0.dp),
                            ) {
                                GroupHeader(
                                    name = row.name,
                                    openCount = members.sumOf { openCount(state, it) },
                                    collapsed = collapsed,
                                    empty = members.isEmpty(),
                                    containsSelection = members.any { state.selection == Selection.OfList(it.url) },
                                    onToggle = { onGroupAction(GroupAction.Toggle(row.name)) },
                                    onNewList = { onGroupAction(GroupAction.NewList(row.name)) },
                                    onRename = { onGroupAction(GroupAction.Rename(row.name)) },
                                    onDissolve = { onGroupAction(GroupAction.Dissolve(row.name)) },
                                    // Long press drags the whole group; a tap still opens or closes it.
                                    modifier = Modifier.longPressDraggableHandle(
                                        interactionSource = interaction,
                                        onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                        onDragStopped = { finishDrag() },
                                    ),
                                    interactionSource = interaction,
                                )
                            }
                        }
                        is DrawerRow.Item -> {
                            val interaction = remember { MutableInteractionSource() }
                            // Long press anywhere on the row starts dragging; a normal tap still opens the list.
                            val handle = Modifier.longPressDraggableHandle(
                                interactionSource = interaction,
                                onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                onDragStopped = { finishDrag() },
                            )
                            val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp, label = "drag")
                            val entry = @Composable {
                                Surface(
                                    shape = RoundedCornerShape(28.dp),
                                    shadowElevation = elevation,
                                    color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                                    modifier = Modifier.padding(horizontal = if (isDragging) 8.dp else 0.dp),
                                ) {
                                    DrawerListEntry(state, row.list, onSelect, interaction, handle)
                                }
                            }
                            // Indentation follows the live position, so dropping into a group is visible while dragging.
                            if (inGroup[index]) GroupedEntry(entry) else entry()
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
                    NavigationDrawerItem(
                        label = { Text("Neue Liste") },
                        icon = { Icon(Icons.Outlined.Add, null) },
                        selected = false, onClick = onNewList,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                    IconButton(onClick = onNewGroup) {
                        Icon(Icons.Outlined.CreateNewFolder, "Neue Gruppe", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        EdgeDivider(visible = true)
        NavigationDrawerItem(
            label = { Text("Papierkorb") },
            icon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            selected = false, onClick = onTrash,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/** Open top-level tasks of a list, as shown in the menu. */
private fun openCount(state: UiState, list: TaskList): Int =
    state.tasks[list.url].orEmpty().count { !it.todo.completed && it.todo.parentUid == null && it.url !in state.hidden }

/** Actions from a group header in the menu. */
sealed interface GroupAction {
    val name: String
    data class Toggle(override val name: String) : GroupAction
    data class NewList(override val name: String) : GroupAction
    data class Rename(override val name: String) : GroupAction
    data class Dissolve(override val name: String) : GroupAction
}

@Composable
private fun DrawerListEntry(
    state: UiState,
    list: TaskList,
    onSelect: (Selection) -> Unit,
    interactionSource: MutableInteractionSource? = null,
    modifier: Modifier = Modifier,
) {
    val status = when {
        !list.isOwner && !list.canWrite -> Icons.Outlined.Lock
        !list.isOwner || list.shares.isNotEmpty() -> Icons.Outlined.People
        else -> null
    }
    // Own icon if set; otherwise the sharing state is the icon, as before.
    val custom = ListIcons.iconOf(list.name)
    DrawerEntry(
        ListIcons.label(list.name), custom ?: status ?: ListIcons.DEFAULT, parseColor(list.color), openCount(state, list),
        state.selection == Selection.OfList(list.url), statusIcon = if (custom != null) status else null,
        interactionSource = interactionSource, modifier = modifier,
    ) {
        onSelect(Selection.OfList(list.url))
    }
}

/** Thin line between a pinned area and the scrolling list; invisible keeps the layout from jumping. */
@Composable
private fun EdgeDivider(visible: Boolean) {
    HorizontalDivider(
        Modifier.padding(horizontal = 16.dp),
        color = if (visible) MaterialTheme.colorScheme.outlineVariant else Color.Transparent,
    )
}

@Composable
private fun DrawerEntry(
    label: String,
    icon: ImageVector,
    tint: Color,
    count: Int,
    selected: Boolean,
    statusIcon: ImageVector? = null,
    interactionSource: MutableInteractionSource? = null,
    modifier: Modifier = Modifier,
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
        modifier = modifier.padding(horizontal = 12.dp),
        interactionSource = interactionSource,
    )
}
