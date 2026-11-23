package de.devsmoms.nctodo.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import de.devsmoms.nctodo.MainActivity
import de.devsmoms.nctodo.NcTodoApp
import de.devsmoms.nctodo.R
import de.devsmoms.nctodo.data.TaskItem
import de.devsmoms.nctodo.quickadd.QuickAddActivity
import de.devsmoms.nctodo.sync.LocalData
import de.devsmoms.nctodo.sync.TaskFields
import de.devsmoms.nctodo.ui.ListIcons
import de.devsmoms.nctodo.ui.Selection
import de.devsmoms.nctodo.ui.defaultTarget
import de.devsmoms.nctodo.ui.forDisplay
import de.devsmoms.nctodo.ui.formatDue
import de.devsmoms.nctodo.ui.isOverdue
import de.devsmoms.nctodo.ui.selectionTitle
import de.devsmoms.nctodo.ui.tasksForSelection
import java.time.LocalDate

/** Per-widget setting: which list or smart view it shows (encoded [Selection]). */
val WidgetSelectionKey = stringPreferencesKey("selection")

/** Per-widget setting: every tap opens the app (no checking off / quick add in the widget). */
val WidgetOpenAppKey = booleanPreferencesKey("openAppOnTap")
private val TaskUrlKey = ActionParameters.Key<String>("taskUrl")

/**
 * Home screen widget: open tasks of one list or smart view. Tasks can be checked off
 * directly, "+" opens the quick add window, tapping a task opens it in the app.
 * Data comes from the encrypted offline store, so the widget also works offline.
 */
class TaskWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val c = (context.applicationContext as NcTodoApp).container
        c.awaitLoaded()
        provideContent {
            val allData by c.repository.data.collectAsState()
            val settings by c.settings.collectAsState()
            val data = allData.forDisplay(settings.hideDeckLists)
            val account by c.account.collectAsState()
            val selection = Selection.decode(currentState(WidgetSelectionKey)) ?: Selection.DEFAULT
            val openAppOnTap = currentState(WidgetOpenAppKey) ?: false
            GlanceTheme {
                WidgetContent(context, account != null, selection, data, openAppOnTap)
            }
        }
    }

    companion object {
        /** Refreshes all placed widgets (no-op if there are none). */
        suspend fun refreshAll(context: Context) {
            val ids = GlanceAppWidgetManager(context).getGlanceIds(TaskWidget::class.java)
            if (ids.isNotEmpty()) TaskWidget().updateAll(context)
        }
    }
}

class TaskWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TaskWidget()
}

/** Checkbox in the widget: marks a task as done (offline-capable, synced afterwards). */
class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val url = parameters[TaskUrlKey] ?: return
        val c = (context.applicationContext as NcTodoApp).container
        c.awaitLoaded()
        val item = c.repository.data.value.task(url) ?: return
        c.repository.updateTask(url, TaskFields.completed(!item.todo.completed))
        TaskWidget().update(context, glanceId)
    }
}

private fun colorOf(hex: String?): ColorProvider? =
    hex?.let { runCatching { ColorProvider(Color(android.graphics.Color.parseColor(it))) }.getOrNull() }

@Composable
private fun WidgetContent(context: Context, loggedIn: Boolean, selection: Selection, data: LocalData, openAppOnTap: Boolean) {
    val list = (selection as? Selection.OfList)?.let { data.list(it.url) }
    val accent = colorOf(list?.color) ?: GlanceTheme.colors.primary
    val muted = GlanceTheme.colors.onSurfaceVariant
    val tasks = tasksForSelection(selection, data.tasks).filter { !it.todo.completed }.take(50)
    val target = if (list != null) list.takeIf { it.canWrite } else data.lists.defaultTarget()
    val openApp = actionStartActivity(MainActivity.intent(context, selection))

    // In "open app" mode the whole widget (and every row, since list rows swallow taps) opens the app.
    val rootModifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(20.dp)
        .let { if (openAppOnTap) it.clickable(openApp) else it }
        .padding(horizontal = 12.dp, vertical = 10.dp)
    Column(rootModifier) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                selectionTitle(selection, data.lists),
                maxLines = 1,
                style = TextStyle(color = accent, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.defaultWeight().clickable(openApp),
            )
            if (loggedIn && target != null && !openAppOnTap) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_add),
                    contentDescription = "Neue Aufgabe",
                    colorFilter = ColorFilter.tint(accent),
                    modifier = GlanceModifier.size(36.dp).padding(6.dp)
                        .clickable(actionStartActivity(QuickAddActivity.intent(context, list?.url))),
                )
            }
        }
        Spacer(GlanceModifier.height(4.dp))
        when {
            !loggedIn -> Text(
                "In NC Todo anmelden",
                style = TextStyle(color = muted, fontSize = 14.sp),
                modifier = GlanceModifier.clickable(openApp).padding(vertical = 8.dp),
            )
            tasks.isEmpty() -> Text(
                "Keine offenen Aufgaben",
                style = TextStyle(color = muted, fontSize = 14.sp),
                modifier = GlanceModifier.clickable(openApp).padding(vertical = 8.dp),
            )
            else -> LazyColumn(GlanceModifier.fillMaxSize()) {
                items(tasks, itemId = { it.url.hashCode().toLong() }) { task ->
                    WidgetTaskRow(context, task, selection, data, accent, if (openAppOnTap) openApp else null)
                }
            }
        }
    }
}

@Composable
private fun WidgetTaskRow(
    context: Context,
    task: TaskItem,
    selection: Selection,
    data: LocalData,
    accent: ColorProvider,
    /** If set, the whole row runs this action (open the app) instead of row-specific actions. */
    rowAction: Action?,
) {
    val writable = data.list(task.listUrl)?.canWrite == true
    val today = LocalDate.now()
    val due = task.todo.due
    val meta = buildList {
        if (selection is Selection.Smart) data.list(task.listUrl)?.let { add(ListIcons.label(it.name)) }
        if (due != null) add(formatDue(due, task.todo.dueTime))
    }.joinToString(" · ")
    val overdue = isOverdue(due, task.todo.dueTime)

    val rowModifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp).let { if (rowAction != null) it.clickable(rowAction) else it }
    Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
        if (rowAction == null) {
            CheckBox(
                checked = false,
                onCheckedChange = if (writable) actionRunCallback<ToggleTaskAction>(actionParametersOf(TaskUrlKey to task.url)) else null,
            )
        } else {
            // Static box: a real checkbox would toggle visually on tap.
            Image(
                ImageProvider(R.drawable.ic_widget_box), null,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
                modifier = GlanceModifier.size(36.dp).padding(9.dp),
            )
        }
        val textModifier = GlanceModifier.defaultWeight()
            .let { if (rowAction == null) it.clickable(actionStartActivity(MainActivity.intent(context, selection, task.url))) else it }
        Column(textModifier) {
            Text(task.todo.summary.ifBlank { "(ohne Titel)" }, maxLines = 2, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp))
            if (meta.isNotEmpty()) {
                Text(
                    meta, maxLines = 1,
                    style = TextStyle(
                        color = if (overdue) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                    ),
                )
            }
        }
        if (task.todo.starred) {
            Spacer(GlanceModifier.width(4.dp))
            Image(ImageProvider(R.drawable.ic_widget_star), "Wichtig", colorFilter = ColorFilter.tint(accent), modifier = GlanceModifier.size(16.dp))
        }
    }
}
