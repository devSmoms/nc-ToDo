package de.devsmoms.nctodo.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.devsmoms.nctodo.data.CalDavRepository
import de.devsmoms.nctodo.data.Credentials
import de.devsmoms.nctodo.data.LoginFlow
import de.devsmoms.nctodo.data.Remote
import de.devsmoms.nctodo.data.TaskItem
import de.devsmoms.nctodo.data.TaskList
import de.devsmoms.nctodo.dav.AuthException
import de.devsmoms.nctodo.dav.DavException
import de.devsmoms.nctodo.ical.TodoMapper
import de.devsmoms.nctodo.sync.OfflineException
import de.devsmoms.nctodo.sync.TaskFields
import de.devsmoms.nctodo.sync.TaskRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.net.URI
import java.time.LocalDate

/** One message for the snackbar, optionally with an "undo" action. */
data class UiMessage(val text: String, val undo: (() -> Unit)? = null, val onTimeout: (() -> Unit)? = null)

/**
 * State and actions of the desktop app (one account for now). The offline-first sync engine
 * from :core does the real work; this class wires it to the window.
 */
class AppState(private val scope: CoroutineScope) {
    private val secrets: SecretStore = SecretStore.forThisOs()
    private val credentialStore = DesktopCredentialStore(secrets)
    private val settingsStore = DesktopSettingsStore(DesktopPaths.dataDir.resolve("settings.properties"))

    var settings by mutableStateOf(settingsStore.load()); private set

    fun updateSettings(change: (DesktopSettings) -> DesktopSettings) {
        val old = settings
        settings = change(old)
        runCatching { settingsStore.save(settings) }
        if (old.syncIntervalSec != settings.syncIntervalSec) restartTicker()
    }

    /** Settings page shown instead of the task list. */
    var showSettings by mutableStateOf(false)

    var ready by mutableStateOf(false); private set
    var credentials by mutableStateOf<Credentials?>(null); private set
    var repository by mutableStateOf<TaskRepository?>(null); private set

    var selection by mutableStateOf<Selection>(Selection.Smart(SmartList.MY_DAY))
    var selectedTask by mutableStateOf<String?>(null)
    var search by mutableStateOf("")
    /** Tasks deleted with "undo" still possible. */
    var hidden by mutableStateOf(setOf<String>()); private set

    var loginBusy by mutableStateOf(false); private set
    var loginError by mutableStateOf<String?>(null); private set
    var loginFlowUrl by mutableStateOf<String?>(null); private set

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages

    @Volatile private var remote: Remote? = null
    private var ticker: Job? = null
    private var loginJob: Job? = null

    init {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { credentialStore.load() }?.let { openSession(it) }
            } catch (e: Exception) {
                loginError = friendly(e)
            }
            ready = true
        }
    }

    // ---------------------------------------------------------------- session

    private suspend fun openSession(c: Credentials) {
        remote = CalDavRepository(c)
        val repo = TaskRepository(DesktopPersistence(secrets, DesktopPaths.dataDir.resolve("offline-cache.bin")), { remote })
        repo.load()
        repo.onLocalChange = { scope.launch { safeSync(pushOnly = true) } }
        repository = repo
        credentials = c
        restartTicker()
    }

    /** Syncs now and then in the configured interval while the app is open. */
    private fun restartTicker() {
        if (repository == null) return
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                safeSync()
                val interval = settings.syncIntervalSec
                if (interval <= 0) break
                delay(interval * 1000L)
            }
        }
    }

    /** Name shown for the account: own name from the settings, else the one from Nextcloud. */
    val displayName: String get() = settings.accountName.trim().ifEmpty { credentials?.displayName.orEmpty() }

    fun syncNow() {
        scope.launch { safeSync() }
    }

    private suspend fun safeSync(pushOnly: Boolean = false) {
        val repo = repository ?: return
        try {
            repo.sync(pushOnly)
        } catch (_: AuthException) {
            message("Anmeldung abgelehnt – App-Passwort widerrufen? Bitte abmelden und neu anmelden.")
        }
    }

    /** Manual login with username and app password. */
    fun login(server: String, user: String, password: String) {
        if (server.isBlank() || user.isBlank() || password.isEmpty()) {
            loginError = "Bitte alle Felder ausfüllen"
            return
        }
        loginBusy = true
        loginError = null
        loginJob = scope.launch {
            try {
                finishLogin(server, user.trim(), password)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loginError = friendly(e)
            } finally {
                loginBusy = false
            }
        }
    }

    /** Login Flow v2: login in the browser, Nextcloud creates an app password for this computer. */
    fun startBrowserLogin(server: String) {
        if (server.isBlank()) {
            loginError = "Bitte die Adresse deiner Nextcloud eingeben"
            return
        }
        loginJob?.cancel()
        loginBusy = true
        loginError = null
        loginJob = scope.launch {
            try {
                val flow = LoginFlow.start(server, DesktopPaths.deviceName())
                loginFlowUrl = flow.loginUrl
                openInBrowser(flow.loginUrl)
                val result = flow.await() ?: throw DavException("Die Anmeldung ist abgelaufen – bitte erneut starten")
                loginFlowUrl = null
                finishLogin(result.server, result.loginName, result.appPassword)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loginError = friendly(e)
            } finally {
                loginFlowUrl = null
                loginBusy = false
            }
        }
    }

    fun cancelBrowserLogin() {
        loginJob?.cancel()
        loginJob = null
        loginFlowUrl = null
        loginBusy = false
    }

    private suspend fun finishLogin(server: String, user: String, password: String) {
        val c = CalDavRepository.discover(server, user, password)
        withContext(Dispatchers.IO) { credentialStore.save(c) }
        openSession(c)
    }

    /** Logs out and deletes everything stored for the account on this computer. */
    fun logout() {
        scope.launch {
            ticker?.cancel()
            remote = null
            repository?.clear()
            withContext(Dispatchers.IO) {
                runCatching { credentialStore.clear() }
                runCatching { settingsStore.delete() }
            }
            LinkPreviews.clear()
            settings = DesktopSettings()
            repository = null
            credentials = null
            selectedTask = null
            showSettings = false
            hidden = emptySet()
            selection = Selection.Smart(SmartList.MY_DAY)
        }
    }

    // ---------------------------------------------------------------- tasks

    /** Adds a task to the selected list (in smart views to the default list, with matching flags). */
    fun addTask(title: String) {
        val repo = repository ?: return
        val text = title.trim().ifEmpty { return }
        val data = repo.data.value
        val sel = selection
        val target = when (sel) {
            is Selection.OfList -> data.list(sel.url)
            is Selection.Smart -> data.forDisplay(settings.hideDeckLists).lists.defaultTarget()
        } ?: return message("Keine Liste zum Hinzufügen vorhanden")
        val smart = (sel as? Selection.Smart)?.kind
        launchSafe {
            repo.createTask(
                target.url,
                TodoMapper.newCalendar(
                    text,
                    due = if (smart == SmartList.MY_DAY || smart == SmartList.PLANNED) LocalDate.now() else null,
                    starred = smart == SmartList.IMPORTANT,
                ),
            )
        }
    }

    fun addStep(parent: TaskItem, title: String) {
        val repo = repository ?: return
        val text = title.trim().ifEmpty { return }
        launchSafe { repo.createTask(parent.listUrl, TodoMapper.newCalendar(text, parentUid = parent.todo.uid)) }
    }

    fun toggleDone(item: TaskItem) = launchSafe { repository?.setCompleted(item.url, !item.todo.completed) }
    fun toggleStar(item: TaskItem) = launchSafe { repository?.updateTask(item.url, TaskFields.starred(!item.todo.starred)) }
    fun rename(item: TaskItem, title: String) {
        if (title.isNotBlank() && title != item.todo.summary) launchSafe { repository?.updateTask(item.url, TaskFields.summary(title.trim())) }
    }
    fun setNotes(item: TaskItem, notes: String) {
        if (notes != item.todo.description) launchSafe { repository?.updateTask(item.url, TaskFields.description(notes)) }
    }
    fun setDue(item: TaskItem, date: LocalDate?) = launchSafe { repository?.updateTask(item.url, TaskFields.due(date, null)) }

    /** Deletes after a few seconds unless "Rückgängig" is clicked. */
    fun delete(item: TaskItem) {
        hidden = hidden + item.url
        if (selectedTask == item.url) selectedTask = null
        val title = item.todo.summary.ifBlank { "Aufgabe" }.let { if (it.length > 40) it.take(40) + "…" else it }
        _messages.tryEmit(
            UiMessage(
                "„$title“ gelöscht",
                undo = { hidden = hidden - item.url },
                onTimeout = {
                    launchSafe {
                        try {
                            repository?.deleteTask(item.url)
                        } finally {
                            hidden = hidden - item.url
                        }
                    }
                },
            ),
        )
    }

    /** Creates a list (optionally directly inside [group]) and opens it. */
    fun createList(name: String, color: String, group: String? = null) {
        val repo = repository ?: return
        if (name.isBlank()) return
        launchSafe {
            val url = repo.createList(name.trim(), color)
            showSettings = false
            search = ""
            selection = Selection.OfList(url)
            if (group != null) {
                updateSettings { it.copy(emptyGroups = it.emptyGroups - group) }
                repo.data.value.list(url)?.let { repo.setListGroup(listOf(it), group) }
            }
        }
    }

    fun addLink(item: TaskItem, url: String) {
        val link = url.trim()
        if (!TodoMapper.isWebLink(link)) return message("Bitte eine Adresse mit https:// eingeben")
        if (link in item.todo.links) return
        launchSafe { repository?.updateTask(item.url, TaskFields.links(item.todo.links + link)) }
    }

    fun removeLink(item: TaskItem, url: String) =
        launchSafe { repository?.updateTask(item.url, TaskFields.links(item.todo.links - url)) }

    // ---------------------------------------------------------------- list groups

    fun toggleGroup(name: String) = updateSettings { s ->
        s.copy(collapsedGroups = if (name in s.collapsedGroups) s.collapsedGroups - name else s.collapsedGroups + name)
    }

    /** Empty groups exist only here until the first list is moved into them. */
    fun createGroup(name: String) {
        val g = name.trim().take(CalDavRepository.MAX_GROUP_LENGTH)
        if (g.isEmpty()) return
        val lists = repository?.data?.value?.lists.orEmpty()
        if (lists.any { it.group == g } || g in settings.emptyGroups) return message("Die Gruppe „$g“ gibt es schon")
        updateSettings { it.copy(emptyGroups = it.emptyGroups + g, collapsedGroups = it.collapsedGroups - g) }
    }

    fun moveListToGroup(list: TaskList, group: String?) {
        val g = group?.trim()?.takeIf { it.isNotEmpty() }
        if (g != null) updateSettings { it.copy(emptyGroups = it.emptyGroups - g) }
        launchSafe {
            repository?.setListGroup(listOf(list), g)
            message(if (g == null) "Aus der Gruppe entfernt" else "In „$g“ verschoben")
        }
    }

    fun renameGroup(old: String, new: String) {
        val target = new.trim()
        if (target.isEmpty() || target == old) return
        updateSettings { s ->
            s.copy(
                collapsedGroups = s.collapsedGroups.map { if (it == old) target else it }.toSet(),
                emptyGroups = if (old in s.emptyGroups) s.emptyGroups - old + target else s.emptyGroups,
            )
        }
        val members = repository?.data?.value?.lists.orEmpty().filter { it.group == old }
        if (members.isNotEmpty()) launchSafe { repository?.setListGroup(members, target) }
    }

    fun dissolveGroup(name: String) {
        updateSettings { s -> s.copy(collapsedGroups = s.collapsedGroups - name, emptyGroups = s.emptyGroups - name) }
        val members = repository?.data?.value?.lists.orEmpty().filter { it.group == name }
        if (members.isNotEmpty()) launchSafe { repository?.setListGroup(members, null) }
    }

    // ---------------------------------------------------------------- helpers

    private fun launchSafe(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message(friendly(e))
            }
        }
    }

    private fun message(text: String) {
        _messages.tryEmit(UiMessage(text))
    }

    private fun friendly(e: Exception): String = when (e) {
        is OfflineException -> e.message ?: "Keine Verbindung"
        is AuthException -> e.message ?: "Anmeldung abgelehnt"
        is DavException -> e.message ?: "Serverfehler"
        is SecretStoreException -> e.message ?: "Schlüsselbund nicht erreichbar"
        is javax.net.ssl.SSLException -> "Sichere Verbindung fehlgeschlagen (Zertifikat prüfen)"
        is java.net.UnknownHostException -> "Server nicht erreichbar"
        is java.io.IOException -> "Keine Verbindung – nur online möglich"
        is IllegalArgumentException -> e.message ?: "Ungültige Eingabe"
        else -> "Unerwarteter Fehler"
    }

    companion object {
        fun openInBrowser(url: String) {
            runCatching {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(URI(url))
                } else {
                    // Fallback for Linux desktops where AWT has no browser integration.
                    ProcessBuilder("xdg-open", url).start()
                }
            }
        }
    }
}
