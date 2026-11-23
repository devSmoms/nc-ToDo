package de.devsmoms.nctodo

import de.devsmoms.nctodo.data.PrincipalHit
import de.devsmoms.nctodo.data.Remote
import de.devsmoms.nctodo.data.TaskItem
import de.devsmoms.nctodo.data.TaskList
import de.devsmoms.nctodo.dav.NotFoundException
import de.devsmoms.nctodo.dav.PreconditionFailedException
import de.devsmoms.nctodo.dav.ShareEntry
import de.devsmoms.nctodo.ical.Ics
import de.devsmoms.nctodo.ical.TodoMapper
import de.devsmoms.nctodo.sync.LocalData
import de.devsmoms.nctodo.sync.LocalPersistence
import de.devsmoms.nctodo.sync.TaskFields
import de.devsmoms.nctodo.sync.TaskRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** In-memory CalDAV server. */
class FakeRemote : Remote {
    var online = true
    var tasksRequests = 0
    val lists = mutableListOf<TaskList>()
    val objects = LinkedHashMap<String, Pair<String, String>>() // url -> (ics, etag)
    private var etagCounter = 0
    private var ctagCounter = 0

    private fun net() { if (!online) throw IOException("offline") }
    private fun nextEtag() = "\"e${++etagCounter}\""
    private fun touch(url: String) {
        val idx = lists.indexOfFirst { url.startsWith(it.url) }
        if (idx >= 0) lists[idx] = lists[idx].copy(ctag = "c${++ctagCounter}")
    }

    fun addList(url: String, name: String, shared: Boolean = false) {
        lists += TaskList(url, name, "#0082C9", true, true, null,
            if (shared) listOf(ShareEntry("principal:principals/users/lisa", "Lisa", true)) else emptyList(), "c${++ctagCounter}")
    }

    /** Simulates another client writing directly to the server. */
    fun serverWrite(url: String, ics: String) { objects[url] = ics to nextEtag(); touch(url) }
    fun serverTodo(url: String) = objects[url]?.let { TodoMapper.read(Ics.parse(it.first)) }

    override suspend fun lists(): List<TaskList> { net(); return lists.toList() }
    override suspend fun tasks(list: TaskList): List<TaskItem> {
        net(); tasksRequests++
        return objects.filterKeys { it.startsWith(list.url) }.map { (url, v) ->
            val cal = Ics.parse(v.first); TaskItem(url, v.second, list.url, TodoMapper.read(cal)!!, cal)
        }
    }
    override suspend fun get(url: String, listUrl: String): TaskItem {
        net(); val v = objects[url] ?: throw NotFoundException()
        val cal = Ics.parse(v.first); return TaskItem(url, v.second, listUrl, TodoMapper.read(cal)!!, cal)
    }
    override suspend fun putNew(url: String, ics: String): String? {
        net(); if (objects.containsKey(url)) throw PreconditionFailedException()
        val e = nextEtag(); objects[url] = ics to e; touch(url); return e
    }
    override suspend fun putExisting(url: String, ics: String, etag: String?): String? {
        net(); val cur = objects[url]
        if (etag != null && (cur == null || cur.second != etag)) throw PreconditionFailedException()
        val e = nextEtag(); objects[url] = ics to e; touch(url); return e
    }
    override suspend fun delete(url: String, etag: String?) {
        net(); val cur = objects[url] ?: return
        if (etag != null && cur.second != etag) throw PreconditionFailedException()
        objects.remove(url); touch(url)
    }
    override suspend fun createList(name: String, color: String, description: String) = error("unused")
    override suspend fun updateList(list: TaskList, name: String, color: String, description: String) = error("unused")
    override suspend fun deleteList(list: TaskList) = error("unused")
    override suspend fun share(list: TaskList, principal: String, canWrite: Boolean) = error("unused")
    override suspend fun unshare(list: TaskList, principal: String) = error("unused")
    override suspend fun searchUsers(term: String): List<PrincipalHit> = emptyList()
    override suspend fun userInfo(principal: String): PrincipalHit? = null
    override suspend fun trash() = de.devsmoms.nctodo.data.TrashContent(emptyList(), emptyList(), null)
    override suspend fun restoreFromTrash(url: String) = error("unused")
    override suspend fun deleteFromTrash(url: String) = error("unused")
}

class MemoryPersistence : LocalPersistence {
    var stored: LocalData? = null
    override suspend fun load() = stored
    override suspend fun save(data: LocalData) { stored = data }
    override suspend fun clear() { stored = null }
}

class SyncTest {
    private val list = "https://cloud.example.de/remote.php/dav/calendars/alice/einkauf/"

    private fun setup(shared: Boolean = false): Triple<FakeRemote, TaskRepository, MemoryPersistence> {
        val remote = FakeRemote().apply { addList(list, "Einkauf", shared) }
        val store = MemoryPersistence()
        val repo = TaskRepository(store, { remote })
        runBlocking { repo.sync() }
        return Triple(remote, repo, store)
    }

    @Test
    fun offlineCreateIsUploadedLater() = runBlocking {
        val (remote, repo, store) = setup()
        repo.sync()
        remote.online = false
        val item = repo.createTask(list, TodoMapper.newCalendar("Milch"))
        repo.sync()
        assertTrue(repo.status.value.offline)
        assertEquals(1, repo.data.value.pending.size)
        assertEquals(1, store.stored!!.pending.size, "queue must be persisted")
        remote.online = true
        repo.sync()
        assertTrue(repo.data.value.pending.isEmpty())
        assertEquals("Milch", remote.serverTodo(item.url)!!.summary)
        assertEquals(remote.objects[item.url]!!.second, repo.data.value.task(item.url)!!.etag)
    }

    @Test
    fun conflictKeepsBothChanges() = runBlocking {
        val (remote, repo, _) = setup()
        val item = repo.createTask(list, TodoMapper.newCalendar("Milch"))
        repo.sync()
        // Another person renames the task on the server…
        val other = Ics.parse(remote.objects[item.url]!!.first).also { TodoMapper.setSummary(it, "Hafermilch") }
        remote.serverWrite(item.url, Ics.serialize(other))
        // …while we star it offline.
        remote.online = false
        repo.updateTask(item.url, TaskFields.starred(true))
        remote.online = true
        repo.sync()
        val server = remote.serverTodo(item.url)!!
        assertEquals("Hafermilch", server.summary)
        assertTrue(server.starred)
        assertEquals("Hafermilch", repo.data.value.task(item.url)!!.todo.summary)
        assertTrue(repo.data.value.pending.isEmpty())
    }

    @Test
    fun offlineDeleteAndCreateDeleteNeverUploaded() = runBlocking {
        val (remote, repo, _) = setup()
        val a = repo.createTask(list, TodoMapper.newCalendar("A"))
        repo.sync()
        remote.online = false
        repo.deleteTask(a.url)
        val b = repo.createTask(list, TodoMapper.newCalendar("B"))
        repo.deleteTask(b.url)
        assertEquals(1, repo.data.value.pending.size)
        remote.online = true
        repo.sync()
        assertTrue(remote.objects.isEmpty())
        assertTrue(repo.data.value.tasks[list].orEmpty().isEmpty())
    }

    @Test
    fun deletingTaskDeletesSteps() = runBlocking {
        val (remote, repo, _) = setup()
        val parent = repo.createTask(list, TodoMapper.newCalendar("Plätzchen"))
        repo.createTask(list, TodoMapper.newCalendar("Mehl", parentUid = parent.todo.uid))
        repo.sync()
        assertEquals(2, remote.objects.size)
        repo.deleteTask(parent.url)
        repo.sync()
        assertTrue(remote.objects.isEmpty())
    }

    @Test
    fun remoteDeletionWinsOverLocalEdit() = runBlocking {
        val (remote, repo, _) = setup()
        val item = repo.createTask(list, TodoMapper.newCalendar("X"))
        repo.sync()
        remote.objects.remove(item.url)
        repo.updateTask(item.url, TaskFields.summary("Y"))
        repo.sync()
        assertNull(repo.data.value.task(item.url))
        assertTrue(repo.data.value.pending.isEmpty())
    }

    @Test
    fun detectsChangesByOthersInSharedLists() = runBlocking {
        val (remote, repo, _) = setup(shared = true)
        val item = repo.createTask(list, TodoMapper.newCalendar("Brot"))
        repo.sync()
        remote.serverWrite("${list}x.ics", Ics.serialize(TodoMapper.newCalendar("Käse")))
        val done = Ics.parse(remote.objects[item.url]!!.first).also { TodoMapper.setCompleted(it, true) }
        remote.serverWrite(item.url, Ics.serialize(done))
        val changes = repo.sync()
        assertEquals(1, changes.size)
        assertEquals(listOf("Käse"), changes[0].added)
        assertEquals(listOf("Brot"), changes[0].completed)
        assertTrue(repo.data.value.task(item.url)!!.todo.completed)
        // Own changes are not reported.
        repo.updateTask(item.url, TaskFields.completed(false))
        assertTrue(repo.sync().isEmpty())
    }

    @Test
    fun unchangedListsAreNotDownloadedAgain() = runBlocking {
        val (remote, repo, _) = setup()
        repo.sync()
        val before = remote.tasksRequests
        repo.sync()
        assertEquals(before, remote.tasksRequests)
        remote.serverWrite("${list}y.ics", Ics.serialize(TodoMapper.newCalendar("Neu")))
        repo.sync()
        assertEquals(before + 1, remote.tasksRequests)
        assertEquals(1, repo.data.value.tasks[list]!!.size)
    }

    @Test
    fun restoresFromPersistence() = runBlocking {
        val (remote, repo, store) = setup()
        repo.sync()
        remote.online = false
        repo.createTask(list, TodoMapper.newCalendar("Offline"))
        val repo2 = TaskRepository(store, { remote })
        repo2.load()
        assertEquals(1, repo2.data.value.pending.size)
        assertFalse(repo2.data.value.tasks[list].isNullOrEmpty())
        remote.online = true
        repo2.sync()
        assertEquals(1, remote.objects.size)
    }

    @Test
    fun completingRepeatingTaskMovesItForward() = runBlocking {
        val (remote, repo, _) = setup()
        val cal = TodoMapper.newCalendar("Müll rausbringen", due = java.time.LocalDate.of(2026, 11, 23))
        de.devsmoms.nctodo.ical.TodoMapper.setRRule(cal, "FREQ=WEEKLY;BYDAY=MO;COUNT=3")
        val item = repo.createTask(list, cal)
        repo.createTask(list, TodoMapper.newCalendar("Gelbe Tonne", parentUid = item.todo.uid).also { TodoMapper.setCompleted(it, true) })
        repo.sync()
        repo.setCompleted(item.url, true)
        repo.sync()
        val series = repo.data.value.task(item.url)!!.todo
        assertFalse(series.completed)
        assertEquals(java.time.LocalDate.of(2026, 11, 30), series.due)
        assertEquals("FREQ=WEEKLY;BYDAY=MO;COUNT=2", series.rrule)
        val records = repo.data.value.tasks[list]!!.filter { it.todo.summary == "Müll rausbringen" && it.url != item.url }
        assertEquals(1, records.size)
        assertTrue(records[0].todo.completed)
        assertNull(records[0].todo.rrule)
        assertFalse(repo.data.value.tasks[list]!!.first { it.todo.summary == "Gelbe Tonne" }.todo.completed, "steps are reset")
        assertEquals(3, remote.objects.size)
    }
}
