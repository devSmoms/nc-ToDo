package de.devsmoms.nctodo.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.devsmoms.nctodo.NcTodoApp
import de.devsmoms.nctodo.data.Credentials
import de.devsmoms.nctodo.data.PrincipalHit
import de.devsmoms.nctodo.data.Settings
import de.devsmoms.nctodo.data.TaskItem
import de.devsmoms.nctodo.data.TaskList
import de.devsmoms.nctodo.dav.AuthException
import de.devsmoms.nctodo.dav.DavException
import de.devsmoms.nctodo.dav.NotFoundException
import de.devsmoms.nctodo.dav.PreconditionFailedException
import de.devsmoms.nctodo.dav.Principals
import de.devsmoms.nctodo.data.TrashContent
import de.devsmoms.nctodo.ical.Assignee
import de.devsmoms.nctodo.ical.TodoMapper
import de.devsmoms.nctodo.sync.OfflineException
import de.devsmoms.nctodo.sync.TaskFields
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate

enum class SmartList(val title: String) { MY_DAY("Mein Tag"), IMPORTANT("Wichtig"), PLANNED("Geplant"), ALL("Aufgaben") }

sealed interface Selection {
    data class Smart(val kind: SmartList) : Selection
    data class OfList(val url: String) : Selection

    fun encode(): String = when (this) {
        is Smart -> "smart:${kind.name}"
        is OfList -> "list:$url"
    }

    companion object {
        val DEFAULT: Selection = Smart(SmartList.MY_DAY)

        fun decode(value: String?): Selection? = when {
            value == null -> null
            value.startsWith("smart:") -> SmartList.entries.firstOrNull { it.name == value.removePrefix("smart:") }?.let { Smart(it) }
            value.startsWith("list:") -> value.removePrefix("list:").takeIf { it.startsWith("https://") }?.let { OfList(it) }
            else -> null
        }
    }
}

sealed interface Screen {
    data object Main : Screen
    data class Detail(val taskUrl: String) : Screen
    data class Share(val listUrl: String) : Screen
    data object Settings : Screen
    data object Trash : Screen
}

data class UiState(
    val ready: Boolean = false,
    val credentials: Credentials? = null,
    val loginBusy: Boolean = false,
    val loginError: String? = null,
    val syncing: Boolean = false,
    val offline: Boolean = false,
    val lastSync: Long? = null,
    val pendingCount: Int = 0,
    val lists: List<TaskList> = emptyList(),
    val tasks: Map<String, List<TaskItem>> = emptyMap(),
    val selection: Selection = Selection.Smart(SmartList.MY_DAY),
    val screen: Screen = Screen.Main,
    val searchResults: List<PrincipalHit> = emptyList(),
    val settings: Settings = Settings(),
    /** Tasks swiped away whose deletion can still be undone; hidden until then. */
    val hidden: Set<String> = emptySet(),
    val trash: TrashContent? = null,
    val trashLoading: Boolean = false,
    val trashError: String? = null,
) {
    val allTasks: List<TaskItem> get() = tasks.values.flatten()
    fun list(url: String) = lists.firstOrNull { it.url == url }
    fun task(url: String) = allTasks.firstOrNull { it.url == url }
}

/** Purely UI-local state (navigation, login form, search). */
private data class LocalUi(
    val loginBusy: Boolean = false,
    val loginError: String? = null,
    val selection: Selection = Selection.Smart(SmartList.MY_DAY),
    val screen: Screen = Screen.Main,
    val searchResults: List<PrincipalHit> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val trash: TrashContent? = null,
    val trashLoading: Boolean = false,
    val trashError: String? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as NcTodoApp).container
    private val repo = c.repository
    private val local = MutableStateFlow(LocalUi(selection = startSelection()))

    /** Screen to open with, according to the "Startbildschirm" setting. */
    private fun startSelection(): Selection {
        val start = c.settings.value.startScreen
        val wanted = if (start == Settings.START_LAST) c.lastSelection else start
        return Selection.decode(wanted) ?: Selection.DEFAULT
    }

    private val messages = Channel<String>(Channel.BUFFERED)
    val messageFlow = messages.receiveAsFlow()

    /** A swipe deletion waiting for "Rückgängig" or timeout. */
    data class UndoEvent(val text: String, val taskUrl: String)
    private val undoEvents = Channel<UndoEvent>(Channel.BUFFERED)
    val undoFlow = undoEvents.receiveAsFlow()

    val state: StateFlow<UiState> = combine(c.account, repo.data, repo.status, c.settings, local) { account, allData, status, settings, ui ->
        val data = allData.forDisplay(settings.hideDeckLists)
        val sel = ui.selection
        val validSel = if (sel is Selection.OfList && data.list(sel.url) == null && data.lists.isNotEmpty()) Selection.Smart(SmartList.MY_DAY) else sel
        UiState(
            ready = c.ready.value,
            credentials = account,
            loginBusy = ui.loginBusy,
            loginError = ui.loginError,
            syncing = status.running,
            offline = status.offline,
            lastSync = status.lastSuccess ?: data.lastSync,
            pendingCount = allData.pending.size,
            lists = data.lists,
            tasks = data.tasks,
            selection = validSel,
            screen = ui.screen,
            searchResults = ui.searchResults,
            settings = settings,
            hidden = ui.hidden,
            trash = ui.trash,
            trashLoading = ui.trashLoading,
            trashError = ui.trashError,
        )
    }.combine(c.ready) { s, ready -> s.copy(ready = ready) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    init {
        viewModelScope.launch { c.events.collect { messages.send(it) } }
    }

    // ---------------------------------------------------------------- session

    fun login(server: String, user: String, password: String) {
        if (server.isBlank() || user.isBlank() || password.isEmpty()) {
            local.update { it.copy(loginError = "Bitte alle Felder ausfüllen") }
            return
        }
        local.update { it.copy(loginBusy = true, loginError = null) }
        viewModelScope.launch {
            try {
                c.login(server, user.trim(), password)
                local.value = LocalUi(selection = startSelection())
            } catch (e: Exception) {
                local.update { it.copy(loginBusy = false, loginError = friendly(e)) }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            c.logout()
            c.lastSelection = null
            // A specific list of the old account makes no sense as start screen anymore.
            if (c.settings.value.startScreen.startsWith("list:")) c.updateSettings { it.copy(startScreen = Settings.START_LAST) }
            local.value = LocalUi()
        }
    }

    // ---------------------------------------------------------------- navigation

    fun select(sel: Selection) {
        local.update { it.copy(selection = sel, screen = Screen.Main) }
        c.lastSelection = sel.encode()
    }
    /** Deep link from the home screen widget. */
    fun openFromWidget(selection: Selection, taskUrl: String?) {
        select(selection)
        if (taskUrl != null) local.update { it.copy(screen = Screen.Detail(taskUrl)) }
    }

    fun open(screen: Screen) = local.update { it.copy(screen = screen, searchResults = emptyList()) }
    fun back() = local.update { it.copy(screen = Screen.Main) }

    fun refresh() = c.syncNow()

    fun updateSettings(change: (Settings) -> Settings) = c.updateSettings(change)

    // ---------------------------------------------------------------- tasks (work offline)

    /** The list new tasks from smart views go to: "Tasks"/"Aufgaben" if present, otherwise the first writable list. */
    fun defaultList(): TaskList? = state.value.lists.defaultTarget()

    /**
     * Creates a task in the current list (or the default list in smart views).
     * [due] and [starred] come from the quick options of the add bar; the smart view
     * adds its own implicit value (e.g. "Mein Tag" = due today) if nothing was chosen.
     */
    fun addTask(title: String, due: DueValue? = null, starred: Boolean = false) {
        val text = title.trim().ifEmpty { return }
        val sel = state.value.selection
        val list = when (sel) {
            is Selection.OfList -> state.value.list(sel.url)
            is Selection.Smart -> defaultList()
        }
        if (list == null || !list.canWrite) {
            message("Keine beschreibbare Liste vorhanden – bitte zuerst eine Liste anlegen")
            return
        }
        val smart = (sel as? Selection.Smart)?.kind
        val cal = TodoMapper.newCalendar(
            summary = text,
            due = due?.date ?: if (smart == SmartList.MY_DAY || smart == SmartList.PLANNED) LocalDate.now() else null,
            dueTime = due?.time,
            starred = starred || smart == SmartList.IMPORTANT,
        )
        launchSafe { repo.createTask(list.url, cal) }
    }

    fun addStep(parent: TaskItem, title: String) {
        val text = title.trim().ifEmpty { return }
        launchSafe { repo.createTask(parent.listUrl, TodoMapper.newCalendar(text, parentUid = parent.todo.uid)) }
    }

    /** Repeating tasks move to their next date instead of being finished. */
    fun setCompleted(item: TaskItem, done: Boolean) = launchSafe { repo.setCompleted(item.url, done) }
    fun setStarred(item: TaskItem, starred: Boolean) = edit(item, TaskFields.starred(starred))
    fun setDue(item: TaskItem, due: DueValue?) = edit(item, TaskFields.due(due?.date, due?.time))

    /** A repeating task needs a date; without one it starts today. */
    fun setRepeat(item: TaskItem, rrule: String?) {
        val fields = TaskFields.rrule(rrule).toMutableMap()
        if (rrule != null && item.todo.due == null) fields += TaskFields.due(LocalDate.now(), null)
        edit(item, fields)
    }

    fun addLink(item: TaskItem, url: String): Boolean {
        val clean = url.trim().let { if (!it.contains("://")) "https://$it" else it }
        if (!TodoMapper.isWebLink(clean)) return false
        val current = state.value.task(item.url)?.todo?.links ?: item.todo.links
        edit(item, TaskFields.links((current + clean).distinct()))
        return true
    }

    fun removeLink(item: TaskItem, url: String) {
        val current = state.value.task(item.url)?.todo?.links ?: item.todo.links
        edit(item, TaskFields.links(current - url))
    }

    /** People a task in [list] can be assigned to: me, the owner and everyone it is shared with. */
    fun assigneeCandidates(list: TaskList?): List<PrincipalHit> {
        val creds = state.value.credentials ?: return emptyList()
        val me = Principals.shareHref(creds.principalUrl)?.let { PrincipalHit(it, creds.displayName + " (ich)", null) }
        val owner = list?.ownerPrincipal?.takeIf { !list.isOwner }?.let { PrincipalHit(it, list.ownerName ?: it.substringAfterLast('/'), null) }
        val shared = list?.shares.orEmpty().filter { Principals.isUser(it.principal) }.map { PrincipalHit(it.principal, it.displayName, null) }
        return listOfNotNull(me, owner).plus(shared).distinctBy { it.shareHref }
    }

    /** Looks up the e-mail address when online; offline the name is enough. */
    fun setAssignee(item: TaskItem, who: PrincipalHit?) = launchSafe {
        val assignee = who?.let { p ->
            val info = repo.userInfo(p.shareHref)
            val username = p.shareHref.substringAfterLast('/')
            Assignee(username, (info?.displayName ?: p.displayName).removeSuffix(" (ich)"), info?.email)
        }
        repo.updateTask(item.url, TaskFields.assignee(assignee))
    }

    // ---------------------------------------------------------------- trash bin

    fun loadTrash() {
        local.update { it.copy(trashLoading = true, trashError = null) }
        viewModelScope.launch {
            try {
                val content = repo.trash()
                local.update { it.copy(trash = content, trashLoading = false) }
            } catch (e: Exception) {
                local.update { it.copy(trashLoading = false, trashError = if (e is NotFoundException) "Der Server hat keinen Papierkorb (ab Nextcloud 22)." else friendly(e)) }
            }
        }
    }

    fun restoreFromTrash(url: String) = launchSafe {
        repo.restoreFromTrash(url)
        message("Wiederhergestellt")
        loadTrash()
    }

    fun deleteFromTrash(url: String) = launchSafe {
        repo.deleteFromTrash(url)
        loadTrash()
    }

    fun setSummary(item: TaskItem, value: String) {
        val v = value.trim()
        val current = state.value.task(item.url) ?: return
        if (v.isEmpty() || v == current.todo.summary) return
        edit(item, TaskFields.summary(v))
    }

    fun setDescription(item: TaskItem, value: String) {
        val current = state.value.task(item.url) ?: return
        if (value == current.todo.description) return
        edit(item, TaskFields.description(value))
    }

    private fun edit(item: TaskItem, fields: Map<String, String?>) = launchSafe { repo.updateTask(item.url, fields) }

    fun deleteTask(item: TaskItem) {
        local.update { if ((it.screen as? Screen.Detail)?.taskUrl == item.url) it.copy(screen = Screen.Main) else it }
        launchSafe { repo.deleteTask(item.url) }
    }

    /** Swipe to delete: hide immediately, delete for real unless the user taps "Rückgängig". */
    fun swipeDelete(item: TaskItem) {
        if (state.value.list(item.listUrl)?.canWrite != true) return
        local.update { it.copy(hidden = it.hidden + item.url) }
        val title = item.todo.summary.ifBlank { "Aufgabe" }.let { if (it.length > 40) it.take(40) + "…" else it }
        undoEvents.trySend(UndoEvent("„$title“ gelöscht", item.url))
    }

    fun undoDelete(taskUrl: String) = local.update { it.copy(hidden = it.hidden - taskUrl) }

    fun commitDelete(taskUrl: String) {
        launchSafe {
            try {
                repo.deleteTask(taskUrl)
            } finally {
                local.update { it.copy(hidden = it.hidden - taskUrl) }
            }
        }
    }

    // ---------------------------------------------------------------- lists & sharing (online only)

    fun createList(name: String, color: String, description: String = "") = launchSafe {
        val url = repo.createList(name, color, description)
        select(Selection.OfList(url))
    }

    fun updateList(list: TaskList, name: String, color: String, description: String = "") = launchSafe { repo.updateList(list, name, color, description) }

    fun deleteList(list: TaskList) = launchSafe {
        repo.deleteList(list)
        local.update { it.copy(selection = Selection.Smart(SmartList.MY_DAY)) }
    }

    fun share(list: TaskList, principal: String, canWrite: Boolean) = launchSafe {
        repo.share(list, principal, canWrite)
        message(if (canWrite) "Freigegeben (Kann bearbeiten)" else "Freigegeben (Nur lesen)")
    }

    fun unshare(list: TaskList, principal: String) = launchSafe { repo.unshare(list, principal) }

    fun search(term: String) {
        if (term.trim().length < 2) {
            local.update { it.copy(searchResults = emptyList()) }
            return
        }
        viewModelScope.launch {
            val hits = runCatching { repo.searchUsers(term) }.getOrElse { emptyList() }
            local.update { it.copy(searchResults = hits) }
        }
    }

    // ---------------------------------------------------------------- errors

    private fun launchSafe(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                message(friendly(e))
            }
        }
    }

    private fun message(text: String) {
        messages.trySend(text)
    }

    private fun friendly(e: Exception): String = when (e) {
        is OfflineException -> e.message!!
        is AuthException -> e.message!!
        is PreconditionFailedException -> "Wurde zwischenzeitlich geändert – bitte erneut versuchen"
        is DavException -> e.message ?: "Serverfehler"
        is javax.net.ssl.SSLException -> "Sichere Verbindung fehlgeschlagen (Zertifikat prüfen)"
        is java.net.UnknownHostException -> "Server nicht erreichbar"
        is IOException -> "Keine Verbindung – nur online möglich"
        is IllegalArgumentException -> e.message ?: "Ungültige Eingabe"
        else -> "Unerwarteter Fehler"
    }
}
