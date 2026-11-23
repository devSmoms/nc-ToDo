package de.devsmoms.nctodo.sync

import de.devsmoms.nctodo.dav.AuthException
import de.devsmoms.nctodo.dav.DavException
import de.devsmoms.nctodo.dav.NotFoundException
import de.devsmoms.nctodo.dav.PreconditionFailedException
import de.devsmoms.nctodo.data.PrincipalHit
import de.devsmoms.nctodo.data.Remote
import de.devsmoms.nctodo.data.TaskItem
import de.devsmoms.nctodo.data.TaskList
import de.devsmoms.nctodo.data.TrashContent
import de.devsmoms.nctodo.ical.Ics
import de.devsmoms.nctodo.ical.IcsComponent
import de.devsmoms.nctodo.ical.Recurrence
import de.devsmoms.nctodo.ical.TodoMapper
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class OfflineException : IOException("Nur online möglich – keine Verbindung zum Server")

/**
 * Offline-first task store.
 *
 * Every edit is applied locally and persisted immediately, then queued as a [Pending] change.
 * [sync] first pushes the queue, then pulls the server state. Pull results never overwrite
 * tasks with unsent local changes.
 */
class TaskRepository(
    private val persistence: LocalPersistence,
    private val remoteProvider: () -> Remote?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dataLock = Mutex()
    private val syncLock = Mutex()
    private var rev = 0L

    private val _data = MutableStateFlow(LocalData())
    val data: StateFlow<LocalData> = _data

    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status

    /** Called after every local change, e.g. to schedule an immediate upload. */
    @Volatile var onLocalChange: (() -> Unit)? = null

    suspend fun load() = dataLock.withLock {
        val stored = runCatching { persistence.load() }.getOrNull() ?: return@withLock
        _data.value = stored
        rev = stored.pending.values.maxOfOrNull { it.rev } ?: 0L
        _status.update { it.copy(lastSuccess = stored.lastSync) }
    }

    suspend fun clear() = dataLock.withLock {
        _data.value = LocalData()
        _status.value = SyncStatus()
        persistence.clear()
    }

    private suspend fun commit(newData: LocalData) {
        _data.value = newData
        persistence.save(newData)
    }

    private suspend fun mutate(notify: Boolean = true, block: (LocalData) -> LocalData) {
        dataLock.withLock { commit(block(_data.value)) }
        if (notify) onLocalChange?.invoke()
    }

    // ================================================================ local edits (work offline)

    suspend fun createTask(listUrl: String, cal: IcsComponent): TaskItem {
        val todo = TodoMapper.read(cal) ?: throw IllegalArgumentException("Ungültige Aufgabe")
        val list = _data.value.list(listUrl) ?: throw IllegalArgumentException("Liste nicht gefunden")
        require(list.canWrite) { "Diese Liste ist schreibgeschützt" }
        val item = TaskItem(taskUrl(listUrl, todo.uid), null, listUrl, todo, cal)
        mutate { d ->
            d.copy(
                tasks = d.tasks + (listUrl to d.tasks[listUrl].orEmpty() + item),
                pending = d.pending + (item.url to Pending(PendingKind.CREATE, listUrl, ++rev)),
            )
        }
        return item
    }

    suspend fun updateTask(url: String, fields: Map<String, String?>) {
        mutate { d ->
            val item = d.task(url) ?: return@mutate d
            if (d.list(item.listUrl)?.canWrite == false) return@mutate d
            val copy = copyOf(item.calendar).also { TaskFields.apply(it, fields) }
            val todo = TodoMapper.read(copy) ?: return@mutate d
            val updated = item.copy(calendar = copy, todo = todo)
            val old = d.pending[url]
            val pending = when {
                old?.kind == PendingKind.CREATE -> old.copy(rev = ++rev)
                old?.kind == PendingKind.UPDATE -> old.copy(rev = ++rev, fields = old.fields?.plus(fields))
                else -> Pending(PendingKind.UPDATE, item.listUrl, ++rev, fields = fields)
            }
            d.copy(tasks = d.tasks.replaceItem(updated), pending = d.pending + (url to pending))
        }
    }

    /**
     * Checks a task off. A repeating task is not finished but moved to its next date (and its
     * steps are reset); a completed, non-repeating copy records this occurrence – like in
     * Microsoft To Do. When the series has ended, the task is completed normally.
     */
    suspend fun setCompleted(url: String, done: Boolean) {
        val item = _data.value.task(url) ?: return
        val repeat = Recurrence.parse(item.todo.rrule)
        if (done && repeat != null && !item.todo.completed) {
            val today = Instant.ofEpochMilli(clock()).atZone(ZoneId.systemDefault()).toLocalDate()
            val next = Recurrence.next(repeat, item.todo.due ?: today)
            if (next != null) {
                val record = copyOf(item.calendar).also { TodoMapper.makeCompletedOccurrence(it) }
                createTask(item.listUrl, record)
                val fields = TaskFields.due(next, item.todo.dueTime).toMutableMap()
                if (repeat.count != null) fields[TaskFields.RRULE] = Recurrence.format(Recurrence.afterOneOccurrence(repeat))
                updateTask(url, fields)
                _data.value.tasks[item.listUrl].orEmpty()
                    .filter { it.todo.parentUid == item.todo.uid && it.todo.completed }
                    .forEach { updateTask(it.url, TaskFields.completed(false)) }
                return
            }
        }
        updateTask(url, TaskFields.completed(done))
    }

    /** Deletes a task together with its steps (sub-tasks). */
    suspend fun deleteTask(url: String) {
        mutate { d ->
            val item = d.task(url) ?: return@mutate d
            if (d.list(item.listUrl)?.canWrite == false) return@mutate d
            val victims = d.tasks[item.listUrl].orEmpty().filter { it.url == url || it.todo.parentUid == item.todo.uid }
            var pending = d.pending
            for (v in victims) {
                pending = if (pending[v.url]?.kind == PendingKind.CREATE) {
                    pending - v.url // never reached the server – just forget it
                } else {
                    pending + (v.url to Pending(PendingKind.DELETE, v.listUrl, ++rev, etag = v.etag))
                }
            }
            val gone = victims.map { it.url }.toSet()
            d.copy(
                tasks = d.tasks + (item.listUrl to d.tasks[item.listUrl].orEmpty().filterNot { it.url in gone }),
                pending = pending,
            )
        }
    }

    // ================================================================ list operations (online only)

    private fun remoteOrThrow(): Remote = remoteProvider() ?: throw OfflineException()

    suspend fun createList(name: String, color: String, description: String = ""): String {
        val url = remoteOrThrow().createList(name, color, description)
        sync()
        return url
    }

    suspend fun updateList(list: TaskList, name: String, color: String, description: String = "") {
        remoteOrThrow().updateList(list, name, color, description); sync()
    }

    suspend fun deleteList(list: TaskList) {
        remoteOrThrow().deleteList(list); sync()
    }

    suspend fun share(list: TaskList, principal: String, canWrite: Boolean) {
        remoteOrThrow().share(list, principal, canWrite); sync()
    }

    suspend fun unshare(list: TaskList, principal: String) {
        remoteOrThrow().unshare(list, principal); sync()
    }

    suspend fun searchUsers(term: String): List<PrincipalHit> = remoteOrThrow().searchUsers(term)

    suspend fun userInfo(principal: String): PrincipalHit? = remoteProvider()?.let { runCatching { it.userInfo(principal) }.getOrNull() }

    // ---------------------------------------------------------------- trash bin (online only)

    suspend fun trash(): TrashContent = remoteOrThrow().trash()

    suspend fun restoreFromTrash(url: String) {
        remoteOrThrow().restoreFromTrash(url)
        sync()
    }

    suspend fun deleteFromTrash(url: String) = remoteOrThrow().deleteFromTrash(url)

    // ================================================================ sync

    /**
     * Uploads pending changes and (unless [pushOnly]) downloads the current server state.
     * Returns changes other people made in shared lists. Network errors are reported via
     * [status]; authentication errors are rethrown.
     */
    suspend fun sync(pushOnly: Boolean = false): List<SharedChange> = syncLock.withLock {
        val remote = remoteProvider() ?: return@withLock emptyList()
        _status.update { it.copy(running = true) }
        try {
            push(remote)
            val changes = if (pushOnly) emptyList() else pull(remote)
            val now = clock()
            if (!pushOnly) mutate(notify = false) { it.copy(lastSync = now) }
            _status.update { SyncStatus(running = false, lastSuccess = if (pushOnly) it.lastSuccess else now) }
            changes
        } catch (e: AuthException) {
            _status.update { it.copy(running = false, offline = false, error = e.message) }
            throw e
        } catch (e: DavException) {
            _status.update { it.copy(running = false, offline = false, error = e.message) }
            emptyList()
        } catch (e: IOException) {
            _status.update { it.copy(running = false, offline = true, error = null) }
            emptyList()
        } catch (e: Exception) {
            _status.update { it.copy(running = false, error = "Synchronisierung fehlgeschlagen") }
            emptyList()
        }
    }

    private suspend fun push(remote: Remote) {
        val queue = _data.value.pending.entries.sortedBy { it.value.rev }
        for ((url, p) in queue) {
            try {
                when (p.kind) {
                    PendingKind.CREATE -> pushCreate(remote, url, p)
                    PendingKind.UPDATE -> pushUpdate(remote, url, p)
                    PendingKind.DELETE -> {
                        try {
                            remote.delete(url, p.etag)
                        } catch (_: PreconditionFailedException) {
                            // Changed elsewhere in the meantime; the user still wants it gone.
                            remote.delete(url, null)
                        }
                        mutate(notify = false) { d -> d.copy(pending = d.pending.removeIfRev(url, p.rev)) }
                    }
                }
            } catch (e: DavException) {
                if (e is AuthException) throw e
                // Rejected by the server (e.g. no write permission anymore): drop the change,
                // the next pull restores the server version.
                mutate(notify = false) { d -> d.copy(pending = d.pending.removeIfRev(url, p.rev)) }
                _status.update { it.copy(error = e.message) }
            }
        }
    }

    private suspend fun pushCreate(remote: Remote, url: String, p: Pending) {
        val item = _data.value.task(url) ?: run {
            mutate(notify = false) { d -> d.copy(pending = d.pending.removeIfRev(url, p.rev)) }
            return
        }
        val ics = Ics.serialize(item.calendar)
        val etag = try {
            remote.putNew(url, ics)
        } catch (_: PreconditionFailedException) {
            // Already uploaded earlier (e.g. the answer got lost) – it is our own object.
            remote.putExisting(url, ics, null)
        }
        finish(url, p.rev, etag, uploaded = item.calendar)
    }

    private suspend fun pushUpdate(remote: Remote, url: String, p: Pending) {
        val item = _data.value.task(url) ?: run {
            mutate(notify = false) { d -> d.copy(pending = d.pending.removeIfRev(url, p.rev)) }
            return
        }
        try {
            try {
                val etag = remote.putExisting(url, Ics.serialize(item.calendar), if (p.fields == null) null else item.etag)
                finish(url, p.rev, etag, uploaded = item.calendar)
            } catch (_: PreconditionFailedException) {
                // Someone else changed (or deleted) the task: re-apply only our changed fields on their version.
                val server = remote.get(url, item.listUrl)
                val merged = copyOf(server.calendar).also { TaskFields.apply(it, p.fields.orEmpty()) }
                val etag = remote.putExisting(url, Ics.serialize(merged), server.etag)
                finish(url, p.rev, etag, uploaded = merged, mergedFromServer = true)
            }
        } catch (_: NotFoundException) {
            // Deleted on the server by someone else: the deletion wins.
            mutate(notify = false) { d ->
                d.copy(tasks = d.tasks.removeItem(url), pending = d.pending - url)
            }
        }
    }

    /** Records a successful upload. Edits made while uploading stay queued. */
    private suspend fun finish(url: String, pushedRev: Long, etag: String?, uploaded: IcsComponent, mergedFromServer: Boolean = false) {
        mutate(notify = false) { d ->
            val item = d.task(url) ?: return@mutate d.copy(pending = d.pending.removeIfRev(url, pushedRev))
            val current = d.pending[url]
            if (current == null || current.rev == pushedRev) {
                val cal = if (mergedFromServer) uploaded else item.calendar
                val todo = TodoMapper.read(cal) ?: item.todo
                d.copy(tasks = d.tasks.replaceItem(item.copy(etag = etag, calendar = cal, todo = todo)), pending = d.pending - url)
            } else {
                // Edited again during the upload: keep the newer local version queued, on top of the new ETag.
                val next = if (current.kind == PendingKind.CREATE) current.copy(kind = PendingKind.UPDATE, fields = null) else current
                d.copy(tasks = d.tasks.replaceItem(item.copy(etag = etag)), pending = d.pending + (url to next))
            }
        }
    }

    private suspend fun pull(remote: Remote): List<SharedChange> = coroutineScope {
        val lists = remote.lists()
        val before = _data.value
        // Only download lists that changed (CTag) or that have local changes.
        val failed = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val fetched: Map<String, List<TaskItem>?> = lists.map { list ->
            async {
                val old = before.list(list.url)
                val unchanged = old != null && list.ctag != null && old.ctag == list.ctag &&
                    before.tasks.containsKey(list.url) && before.pending.values.none { it.listUrl == list.url }
                val result = if (unchanged) null else runCatching { remote.tasks(list) }.getOrElse { failed += list.url; null }
                list.url to result
            }
        }.awaitAll().toMap()

        val changes = mutableListOf<SharedChange>()
        dataLock.withLock {
            val d = _data.value
            val pendingNow = d.pending
            val newTasks = HashMap<String, List<TaskItem>>()
            val newLists = lists.map { list ->
                val server = fetched[list.url]
                if (server == null) {
                    newTasks[list.url] = d.tasks[list.url].orEmpty()
                    // Download failed: keep the old CTag so the list is fetched again next time.
                    return@map if (list.url in failed) list.copy(ctag = d.list(list.url)?.ctag) else list
                }
                val local = d.tasks[list.url].orEmpty()
                val serverUrls = server.map { it.url }.toSet()
                val merged = server.mapNotNull { s ->
                    when (pendingNow[s.url]?.kind) {
                        PendingKind.DELETE -> null
                        PendingKind.UPDATE, PendingKind.CREATE -> local.firstOrNull { it.url == s.url } ?: s
                        null -> s
                    }
                } + local.filter { it.url !in serverUrls && pendingNow[it.url]?.kind == PendingKind.CREATE }
                newTasks[list.url] = merged

                val oldList = before.list(list.url)
                if (list.isShared && oldList != null && before.tasks.containsKey(list.url)) {
                    val oldByUid = local.associateBy { it.todo.uid }
                    val added = server.filter { it.todo.uid !in oldByUid && it.todo.parentUid == null }.map { it.todo.summary }
                    val completed = server.filter { s ->
                        val o = oldByUid[s.todo.uid]
                        o != null && !o.todo.completed && s.todo.completed && pendingNow[s.url] == null
                    }.map { it.todo.summary }
                    if (added.isNotEmpty() || completed.isNotEmpty()) changes += SharedChange(list.name, added, completed)
                }
                list
            }
            val listUrls = newLists.map { it.url }.toSet()
            commit(
                d.copy(
                    lists = newLists,
                    tasks = newTasks,
                    // Changes for lists that disappeared (deleted or unshared) can never be uploaded.
                    pending = pendingNow.filterValues { it.listUrl in listUrls },
                ),
            )
        }
        changes
    }

    companion object {
        fun copyOf(cal: IcsComponent): IcsComponent = Ics.parse(Ics.serialize(cal))

        /** Object URL for a new task. Only safe characters end up in the path. */
        fun taskUrl(listUrl: String, uid: String): String {
            val name = if (Regex("^[A-Za-z0-9._-]{1,100}$").matches(uid) && uid != "." && uid != "..") uid else UUID.randomUUID().toString()
            return listUrl.trimEnd('/') + "/" + name + ".ics"
        }

        private fun Map<String, List<TaskItem>>.replaceItem(item: TaskItem) =
            this + (item.listUrl to this[item.listUrl].orEmpty().map { if (it.url == item.url) item else it })

        private fun Map<String, List<TaskItem>>.removeItem(url: String) = mapValues { (_, l) -> l.filterNot { it.url == url } }

        private fun Map<String, Pending>.removeIfRev(url: String, rev: Long) =
            if (this[url]?.rev == rev) this - url else this
    }
}
