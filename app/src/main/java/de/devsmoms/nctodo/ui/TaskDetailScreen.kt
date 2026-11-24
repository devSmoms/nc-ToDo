package de.devsmoms.nctodo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import de.devsmoms.nctodo.ical.Recurrence
import de.devsmoms.nctodo.ical.Repeat
import de.devsmoms.nctodo.ical.TodoMapper
import java.time.format.TextStyle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import de.devsmoms.nctodo.data.TaskItem
import de.devsmoms.nctodo.ui.theme.parseColor
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(state: UiState, item: TaskItem, vm: AppViewModel, snackbar: SnackbarHostState) {
    val list = state.list(item.listUrl)
    val writable = list?.canWrite == true
    val accent = list?.let { parseColor(it.color) } ?: MaterialTheme.colorScheme.primary
    val t = item.todo
    val steps = state.tasks[item.listUrl].orEmpty().filter { it.todo.parentUid == t.uid }
        .sortedWith(compareBy<TaskItem> { it.todo.completed }.thenBy { it.todo.created })

    var title by remember(item.url) { mutableStateOf(t.summary) }
    var notes by remember(item.url) { mutableStateOf(t.description) }
    var pickDate by remember { mutableStateOf(false) }
    var repeatMenu by remember { mutableStateOf(false) }
    var customRepeat by remember { mutableStateOf(false) }
    var assigneeMenu by remember { mutableStateOf(false) }
    var addLink by remember { mutableStateOf(false) }
    // Removing things asks first.
    var confirm by remember { mutableStateOf<Confirmation?>(null) }
    // The question can be switched off in the settings.
    fun ask(c: Confirmation) {
        if (state.settings.confirmRemovals) confirm = c else c.action()
    }
    fun confirmRemoveDue() {
        ask(Confirmation(
            "Fälligkeit entfernen?",
            "Die Aufgabe hat danach kein Fälligkeitsdatum mehr" + (if (item.todo.recurring) " – die Wiederholung bleibt bestehen." else " und verschwindet aus „Mein Tag“."),
            "Entfernen",
        ) { vm.setDue(item, null) })
    }
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    // Save pending text edits when leaving the screen.
    val latest by rememberUpdatedState(item)
    val latestTitle by rememberUpdatedState(title)
    val latestNotes by rememberUpdatedState(notes)
    DisposableEffect(item.url) {
        onDispose {
            if (writable) {
                vm.setSummary(latest, latestTitle)
                vm.setDescription(latest, latestNotes)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(list?.let { ListIcons.label(it.name) }.orEmpty(), color = accent) },
                navigationIcon = { IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück") } },
                actions = {
                    if (writable) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Aufgabe löschen") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        ) {
            Surface(shape = RoundedCornerShape(8.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { vm.setCompleted(item, !t.completed) }, enabled = writable) {
                            if (t.completed) Icon(Icons.Filled.CheckCircle, "Als offen markieren", tint = accent)
                            else Icon(Icons.Outlined.RadioButtonUnchecked, "Erledigen")
                        }
                        TextField(
                            value = title, onValueChange = { title = it.replace("\n", " ") },
                            enabled = writable,
                            textStyle = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = if (t.completed) TextDecoration.LineThrough else null,
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { vm.setSummary(item, title) }),
                            colors = transparentFieldColors(),
                            modifier = Modifier.weight(1f).onFocusChanged { if (!it.isFocused && writable) vm.setSummary(latest, latestTitle) },
                        )
                        IconButton(onClick = { vm.setStarred(item, !t.starred) }, enabled = writable) {
                            if (t.starred) Icon(Icons.Filled.Star, "Wichtig entfernen", tint = accent) else Icon(Icons.Outlined.StarBorder, "Wichtig")
                        }
                    }
                    steps.forEach { step ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                            IconButton(onClick = { vm.setCompleted(step, !step.todo.completed) }, enabled = writable) {
                                if (step.todo.completed) Icon(Icons.Filled.CheckCircle, "Schritt offen", tint = accent)
                                else Icon(Icons.Outlined.RadioButtonUnchecked, "Schritt erledigen")
                            }
                            Text(
                                step.todo.summary, modifier = Modifier.weight(1f),
                                textDecoration = if (step.todo.completed) TextDecoration.LineThrough else null,
                            )
                            if (writable) IconButton(onClick = {
                                ask(Confirmation("Schritt löschen?", "„${step.todo.summary}“ wird gelöscht.", "Löschen") { vm.deleteTask(step) })
                            }) { Icon(Icons.Outlined.Close, "Schritt entfernen") }
                        }
                    }
                    if (writable) {
                        var newStep by remember { mutableStateOf("") }
                        TextField(
                            value = newStep, onValueChange = { newStep = it.replace("\n", " ") },
                            placeholder = { Text(if (steps.isEmpty()) "Schritt hinzufügen" else "Nächster Schritt", color = accent) },
                            leadingIcon = { Icon(Icons.Outlined.Add, null, tint = accent) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { vm.addStep(item, newStep); newStep = "" }),
                            colors = transparentFieldColors(),
                            modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))
            Surface(shape = RoundedCornerShape(8.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column {
                    val today = LocalDate.now()
                    val inMyDay = t.due?.let { !it.isAfter(today) } == true
                    DetailRow(
                        icon = { Icon(Icons.Outlined.WbSunny, null, tint = if (inMyDay) accent else MaterialTheme.colorScheme.onSurfaceVariant) },
                        text = if (inMyDay) "In „Mein Tag“" else "Zu „Mein Tag“ hinzufügen",
                        highlighted = inMyDay, accent = accent,
                        onClick = if (writable) ({ if (inMyDay) confirmRemoveDue() else vm.setDue(item, DueValue(today)) }) else null,
                        onClear = if (writable && inMyDay) ({ confirmRemoveDue() }) else null,
                    )
                    HorizontalDivider()
                    val overdue = isOverdue(t.due, t.dueTime, t.completed)
                    DetailRow(
                        icon = { Icon(Icons.Outlined.CalendarMonth, null, tint = if (overdue) MaterialTheme.colorScheme.error else if (t.due != null) accent else MaterialTheme.colorScheme.onSurfaceVariant) },
                        text = t.dueValue?.let { "Fällig: " + formatDue(it) + if (it.time == null) " (ganztägig)" else "" } ?: "Fälligkeitsdatum hinzufügen",
                        highlighted = t.due != null, accent = if (overdue) MaterialTheme.colorScheme.error else accent,
                        onClick = if (writable) ({ pickDate = true }) else null,
                        onClear = if (writable && t.due != null) ({ confirmRemoveDue() }) else null,
                    )
                    HorizontalDivider()
                    // Repeat
                    val repeat = Recurrence.parse(t.rrule)
                    Box {
                        DetailRow(
                            icon = { Icon(Icons.Outlined.Repeat, null, tint = if (repeat != null) accent else MaterialTheme.colorScheme.onSurfaceVariant) },
                            text = repeat?.let { Recurrence.describe(it) } ?: if (t.rrule != null) "Wiederholt sich (eigene Regel)" else "Wiederholen",
                            highlighted = t.rrule != null, accent = accent,
                            onClick = if (writable) ({ repeatMenu = true }) else null,
                            onClear = if (writable && t.rrule != null) ({ vm.setRepeat(item, null) }) else null,
                        )
                        DropdownMenu(expanded = repeatMenu, onDismissRequest = { repeatMenu = false }) {
                            val weekday = (t.due ?: today).dayOfWeek
                            listOf(
                                "Täglich" to Recurrence.daily(),
                                "Werktags (Mo–Fr)" to Recurrence.weekdays(),
                                "Wöchentlich (" + weekday.getDisplayName(TextStyle.FULL, Locale.GERMAN) + ")" to Recurrence.weekly(weekday),
                                "Monatlich" to Recurrence.monthly(),
                                "Jährlich" to Recurrence.yearly(),
                            ).forEach { (label, rule) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = { repeatMenu = false; vm.setRepeat(item, Recurrence.format(rule)) })
                            }
                            DropdownMenuItem(text = { Text("Benutzerdefiniert …") }, onClick = { repeatMenu = false; customRepeat = true })
                        }
                    }
                    HorizontalDivider()
                    // Assignee
                    Box {
                        DetailRow(
                            icon = { Icon(Icons.Outlined.Person, null, tint = if (t.assignee != null) accent else MaterialTheme.colorScheme.onSurfaceVariant) },
                            text = t.assignee?.let { "Zuständig: " + it.name } ?: "Zuweisen",
                            highlighted = t.assignee != null, accent = accent,
                            onClick = if (writable) ({ assigneeMenu = true }) else null,
                            onClear = if (writable && t.assignee != null) ({ vm.setAssignee(item, null) }) else null,
                        )
                        DropdownMenu(expanded = assigneeMenu, onDismissRequest = { assigneeMenu = false }) {
                            val candidates = vm.assigneeCandidates(list)
                            candidates.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p.displayName) },
                                    leadingIcon = { Icon(Icons.Outlined.Person, null) },
                                    onClick = { assigneeMenu = false; vm.setAssignee(item, p) },
                                )
                            }
                            if (candidates.size <= 1) {
                                DropdownMenuItem(
                                    text = { Text("Liste teilen, um anderen etwas zuzuweisen", style = MaterialTheme.typography.bodySmall) },
                                    enabled = false, onClick = {},
                                )
                            }
                        }
                    }
                }
            }

            // Links with previews
            if (t.links.isNotEmpty() || writable) {
                Text(
                    "Links", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 6.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    t.links.forEach { link ->
                        LinkCard(
                            url = link, loadPreview = state.settings.linkPreviews,
                            onOpen = { openLink(context, link) },
                            onRemove = if (writable) ({
                                ask(Confirmation("Link entfernen?", "Der Link zu ${LinkPreviews.siteOf(link)} wird aus der Aufgabe entfernt.", "Entfernen") {
                                    vm.removeLink(item, link)
                                })
                            }) else null,
                        )
                    }
                    if (writable) {
                        TextButton(onClick = { addLink = true }) {
                            Icon(Icons.Outlined.AddLink, null, tint = accent)
                            Spacer(Modifier.width(8.dp))
                            Text("Link hinzufügen", color = accent)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                enabled = writable,
                placeholder = { Text("Notiz hinzufügen") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 120.dp)
                    .onFocusChanged { if (!it.isFocused && writable) vm.setDescription(latest, latestNotes) },
            )

            val created = t.created?.atZone(ZoneId.systemDefault())?.let {
                "Erstellt am " + DateTimeFormatter.ofPattern("EEE, d. MMMM yyyy", Locale.GERMAN).format(it)
            }
            Text(
                listOfNotNull(created, if (!writable) "Nur lesen" else null).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth(),
            )
        }
    }

    if (pickDate) {
        DueDialog(t.dueValue, state.settings.allDayDefault, onDismiss = { pickDate = false }) {
            vm.setDue(item, it)
            pickDate = false
        }
    }
    if (customRepeat) {
        CustomRepeatDialog(onDismiss = { customRepeat = false }) { rule ->
            customRepeat = false
            vm.setRepeat(item, Recurrence.format(rule))
        }
    }
    confirm?.let { c ->
        ConfirmDialog(title = c.title, text = c.text, confirm = c.confirmLabel, onDismiss = { confirm = null }) {
            confirm = null
            c.action()
        }
    }
    if (addLink) {
        AddLinkDialog(onDismiss = { addLink = false }) { url -> if (vm.addLink(item, url)) addLink = false }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "„${t.summary}“ löschen?",
            text = "Die Aufgabe wird dauerhaft gelöscht" + if (steps.isNotEmpty()) ", inklusive ${steps.size} Schritten." else ".",
            confirm = "Löschen",
            onDismiss = { confirmDelete = false },
        ) { confirmDelete = false; vm.deleteTask(item) }
    }
}

@Composable
private fun DetailRow(
    icon: @Composable () -> Unit,
    text: String,
    highlighted: Boolean,
    accent: Color,
    onClick: (() -> Unit)?,
    onClear: (() -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(enabled = onClick != null) { onClick?.invoke() }.padding(start = 12.dp).heightIn(min = 52.dp),
    ) {
        icon()
        Spacer(Modifier.width(16.dp))
        Text(text, modifier = Modifier.weight(1f), color = if (highlighted) accent else MaterialTheme.colorScheme.onSurfaceVariant)
        if (onClear != null) IconButton(onClick = onClear) { Icon(Icons.Outlined.Close, "Entfernen") }
    }
}

@Composable
fun transparentFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent,
    disabledTextColor = MaterialTheme.colorScheme.onSurface,
)

/** "Alle N Tage/Wochen/Monate/Jahre" */
@Composable
private fun CustomRepeatDialog(onDismiss: () -> Unit, onConfirm: (Repeat) -> Unit) {
    var interval by remember { mutableStateOf("2") }
    var freq by remember { mutableStateOf(Repeat.Freq.WEEKLY) }
    var menu by remember { mutableStateOf(false) }
    val labels = mapOf(Repeat.Freq.DAILY to "Tage", Repeat.Freq.WEEKLY to "Wochen", Repeat.Freq.MONTHLY to "Monate", Repeat.Freq.YEARLY to "Jahre")
    val n = interval.toIntOrNull()?.takeIf { it in 1..999 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wiederholen") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Alle")
                OutlinedTextField(
                    value = interval, onValueChange = { v -> interval = v.filter { it.isDigit() }.take(3) },
                    singleLine = true, modifier = Modifier.width(80.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Box {
                    TextButton(onClick = { menu = true }) { Text(labels.getValue(freq)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        labels.forEach { (f, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { freq = f; menu = false }) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { n?.let { onConfirm(Repeat(freq, interval = it)) } }, enabled = n != null) { Text("Übernehmen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** Enter or paste a link; a link on the clipboard is suggested. */
@Composable
private fun AddLinkDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    val suggestion = remember { clipboard.getText()?.text?.trim()?.takeIf { TodoMapper.isWebLink(it) }.orEmpty() }
    var url by remember { mutableStateOf(suggestion) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link hinzufügen") },
        text = {
            OutlinedTextField(
                value = url, onValueChange = { url = it.trim(); error = false },
                label = { Text("Adresse (https://…)") }, singleLine = true, isError = error,
                supportingText = if (error) ({ Text("Nur http- und https-Links") }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onDone = { onAdd(url); error = true }),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onAdd(url); error = true }, enabled = url.isNotBlank()) { Text("Hinzufügen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** A pending "really remove?" question. */
private class Confirmation(val title: String, val text: String, val confirmLabel: String, val action: () -> Unit)
