package de.devsmoms.nctodo.quickadd

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import de.devsmoms.nctodo.MainActivity
import de.devsmoms.nctodo.NcTodoApp
import de.devsmoms.nctodo.data.TaskList
import de.devsmoms.nctodo.ical.TodoMapper
import de.devsmoms.nctodo.ui.DueValue
import de.devsmoms.nctodo.ui.ListIcons
import de.devsmoms.nctodo.ui.TaskQuickOptions
import de.devsmoms.nctodo.ui.defaultTarget
import de.devsmoms.nctodo.ui.forDisplay
import de.devsmoms.nctodo.ui.theme.NcTodoTheme
import de.devsmoms.nctodo.ui.theme.parseColor
import kotlinx.coroutines.launch

/**
 * Small floating window to create a task without opening the app – started from the
 * Quick Settings tile and from the "+" of the home screen widget. Works offline.
 */
class QuickAddActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val c = (application as NcTodoApp).container
        val presetList = intent.getStringExtra(EXTRA_LIST_URL)

        setContent {
            val settings by c.settings.collectAsState()
            val account by c.account.collectAsState()
            val ready by c.ready.collectAsState()
            val data by c.repository.data.collectAsState()
            NcTodoTheme(settings.theme) {
                QuickAddContent(
                    allDayDefault = settings.allDayDefault,
                    ready = ready,
                    loggedIn = account != null,
                    lists = data.forDisplay(settings.hideDeckLists).lists.filter { it.canWrite },
                    presetListUrl = presetList,
                    onCancel = { finish() },
                    onOpenApp = {
                        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        finish()
                    },
                    onAdd = { title, list, due, starred ->
                        lifecycleScope.launch {
                            try {
                                c.repository.createTask(list.url, TodoMapper.newCalendar(title.trim(), due = due?.date, dueTime = due?.time, starred = starred))
                                Toast.makeText(this@QuickAddActivity, "Zu „${ListIcons.label(list.name)}“ hinzugefügt", Toast.LENGTH_SHORT).show()
                                finish()
                            } catch (e: Exception) {
                                Toast.makeText(this@QuickAddActivity, e.message ?: "Konnte nicht gespeichert werden", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                )
            }
        }
    }

    companion object {
        const val EXTRA_LIST_URL = "listUrl"

        fun intent(context: Context, listUrl: String? = null): Intent =
            Intent(context, QuickAddActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .apply { if (listUrl != null) putExtra(EXTRA_LIST_URL, listUrl) }
    }
}

@Composable
private fun QuickAddContent(
    allDayDefault: Boolean,
    ready: Boolean,
    loggedIn: Boolean,
    lists: List<TaskList>,
    presetListUrl: String?,
    onCancel: () -> Unit,
    onOpenApp: () -> Unit,
    onAdd: (String, TaskList, DueValue?, Boolean) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var due by remember { mutableStateOf<DueValue?>(null) }
    var starred by remember { mutableStateOf(false) }
    var listUrl by remember { mutableStateOf(presetListUrl) }
    var listMenu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    val list = lists.firstOrNull { it.url == listUrl } ?: lists.defaultTarget()
    val accent = list?.let { parseColor(it.color) } ?: MaterialTheme.colorScheme.primary
    fun submit() {
        if (text.isNotBlank() && list != null) onAdd(text, list, due, starred)
    }

    Box(Modifier.fillMaxSize()) {
        // Dimmed background: tapping it closes the window.
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCancel),
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().imePadding().padding(12.dp).fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Neue Aufgabe", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accent)
                when {
                    !ready -> Text("Lädt …")
                    !loggedIn -> {
                        Text("Bitte melde dich zuerst in NC Todo an.")
                        Button(onClick = onOpenApp) { Text("App öffnen") }
                    }
                    list == null -> {
                        Text("Es gibt noch keine beschreibbare Liste. Lege in der App zuerst eine Liste an.")
                        Button(onClick = onOpenApp) { Text("App öffnen") }
                    }
                    else -> {
                        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                        OutlinedTextField(
                            value = text, onValueChange = { text = it.replace("\n", " ") },
                            placeholder = { Text("Was ist zu tun?") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                        // List chooser
                        Box {
                            TextButton(onClick = { listMenu = true }) {
                                Icon(ListIcons.iconOf(list.name) ?: ListIcons.DEFAULT, null, Modifier.size(20.dp), tint = accent)
                                Spacer(Modifier.width(8.dp))
                                Text(ListIcons.label(list.name), color = accent)
                                Icon(Icons.Outlined.ExpandMore, null, tint = accent)
                            }
                            DropdownMenu(expanded = listMenu, onDismissRequest = { listMenu = false }) {
                                lists.forEach { l ->
                                    DropdownMenuItem(
                                        text = { Text(ListIcons.label(l.name)) },
                                        leadingIcon = { Icon(ListIcons.iconOf(l.name) ?: ListIcons.DEFAULT, null, tint = parseColor(l.color)) },
                                        onClick = { listUrl = l.url; listMenu = false },
                                    )
                                }
                            }
                        }
                        TaskQuickOptions(due = due, starred = starred, onDue = { due = it }, onStarred = { starred = it }, allDayDefault = allDayDefault)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = onCancel) { Text("Abbrechen") }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = { submit() }, enabled = text.isNotBlank()) { Text("Hinzufügen") }
                        }
                    }
                }
            }
        }
    }
}
