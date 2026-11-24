package de.devsmoms.nctodo.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import de.devsmoms.nctodo.NcTodoApp
import de.devsmoms.nctodo.sync.LocalData
import de.devsmoms.nctodo.ui.AccountAvatar
import de.devsmoms.nctodo.ui.ListIcons
import de.devsmoms.nctodo.ui.Selection
import de.devsmoms.nctodo.ui.SmartList
import de.devsmoms.nctodo.ui.forDisplay
import de.devsmoms.nctodo.ui.serverLabel
import de.devsmoms.nctodo.ui.theme.NcTodoTheme
import de.devsmoms.nctodo.ui.theme.parseColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Shown when a widget is placed (and when it is reconfigured): choose the list it shows. */
@OptIn(ExperimentalMaterial3Api::class)
class WidgetConfigActivity : ComponentActivity() {

    private var openAppOnTap by mutableStateOf(false)
    private var current by mutableStateOf<Selection?>(null)
    /** Account the widget shows; null until chosen (then the active account is used). */
    private var account by mutableStateOf<String?>(null)
    private var currentAccount: String? = null
    private val noData = MutableStateFlow(LocalData())

    private data class Option(val selection: Selection, val label: String, val icon: ImageVector, val tint: Color)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appWidgetId = intent.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Cancelling (back) must not leave a half-configured widget.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val c = (application as NcTodoApp).container

        // Reconfiguring an existing widget: start from its current settings.
        lifecycleScope.launch {
            c.awaitLoaded()
            runCatching {
                val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
                val prefs = getAppWidgetState(this@WidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId)
                openAppOnTap = prefs[WidgetOpenAppKey] ?: false
                current = Selection.decode(prefs[WidgetSelectionKey])
                currentAccount = prefs[WidgetAccountKey] ?: c.sessions.value.firstOrNull()?.id
                if (account == null) account = currentAccount
            }
        }

        setContent {
            val sessions by c.sessions.collectAsState()
            val active by c.active.collectAsState()
            val session = sessions.firstOrNull { it.id == account } ?: active
            val settings by (session?.settings ?: c.settings).collectAsState()
            val allData by (session?.repository?.data ?: noData).collectAsState()
            val data = allData.forDisplay(settings.hideDeckLists)
            val options = listOf(
                Option(Selection.Smart(SmartList.MY_DAY), SmartList.MY_DAY.title, Icons.Outlined.WbSunny, Color(0xFF9E7B00)),
                Option(Selection.Smart(SmartList.IMPORTANT), SmartList.IMPORTANT.title, Icons.Outlined.StarBorder, Color(0xFFC2185B)),
                Option(Selection.Smart(SmartList.PLANNED), SmartList.PLANNED.title, Icons.Outlined.CalendarMonth, Color(0xFF00897B)),
                Option(Selection.Smart(SmartList.ALL), SmartList.ALL.title, Icons.Outlined.Home, Color(0xFF2564CF)),
            ) + data.lists.map { Option(Selection.OfList(it.url), ListIcons.label(it.name), ListIcons.iconOf(it.name) ?: ListIcons.DEFAULT, parseColor(it.color)) }

            NcTodoTheme(settings.theme) {
                Scaffold(topBar = { TopAppBar(title = { Text("Widget einrichten") }) }) { padding ->
                    LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                        item {
                            ListItem(
                                headlineContent = { Text("Tippen öffnet immer die App") },
                                supportingContent = {
                                    Text("Ein Tipp irgendwo auf das Widget öffnet die Liste in der App – ohne Abhaken und „+“ im Widget")
                                },
                                leadingContent = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = MaterialTheme.colorScheme.primary) },
                                trailingContent = { Switch(checked = openAppOnTap, onCheckedChange = { openAppOnTap = it }) },
                                modifier = Modifier.clickable { openAppOnTap = !openAppOnTap },
                            )
                            HorizontalDivider()
                            // With several accounts the widget can show any of them.
                            if (sessions.size > 1) {
                                Text(
                                    "Konto",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                                )
                                sessions.forEach { acc ->
                                    val creds = acc.credentials.value
                                    ListItem(
                                        headlineContent = { Text(acc.displayName()) },
                                        supportingContent = { Text("${creds.username} · ${serverLabel(creds.serverUrl)}") },
                                        leadingContent = { AccountAvatar(acc.displayName(), acc.id, 36.dp) },
                                        trailingContent = { RadioButton(selected = acc.id == session?.id, onClick = { account = acc.id }) },
                                        modifier = Modifier.clickable { account = acc.id },
                                    )
                                }
                                HorizontalDivider()
                            }
                            Text(
                                "Liste wählen – speichert das Widget",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                            )
                        }
                        items(options) { o ->
                            ListItem(
                                headlineContent = { Text(o.label) },
                                leadingContent = { Icon(o.icon, null, tint = o.tint) },
                                trailingContent = {
                                    if (o.selection == current && session?.id == currentAccount) Icon(Icons.Outlined.Check, "Aktuell", tint = o.tint)
                                },
                                modifier = Modifier.clickable { choose(appWidgetId, session?.id, o.selection) },
                            )
                            if (o.selection == Selection.Smart(SmartList.ALL) && data.lists.isNotEmpty()) {
                                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                            }
                        }
                        if (data.lists.isEmpty()) {
                            item {
                                Text(
                                    "Eigene Listen erscheinen hier, sobald du in der App angemeldet bist.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun choose(appWidgetId: Int, accountId: String?, selection: Selection) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@WidgetConfigActivity, glanceId) {
                if (accountId != null) it[WidgetAccountKey] = accountId else it.remove(WidgetAccountKey)
                it[WidgetSelectionKey] = selection.encode()
                it[WidgetOpenAppKey] = openAppOnTap
            }
            TaskWidget().update(this@WidgetConfigActivity, glanceId)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }
}
