package de.devsmoms.nctodo

import de.devsmoms.nctodo.dav.ShareEntry
import de.devsmoms.nctodo.data.MiniJson
import de.devsmoms.nctodo.data.TaskItem
import de.devsmoms.nctodo.data.TaskList
import de.devsmoms.nctodo.ical.Ics
import de.devsmoms.nctodo.ical.TodoMapper
import de.devsmoms.nctodo.sync.LocalData
import de.devsmoms.nctodo.sync.LocalDataCodec
import de.devsmoms.nctodo.sync.Pending
import de.devsmoms.nctodo.sync.PendingKind
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class LocalDataCodecTest {

    @Test
    fun roundTrip() {
        val list = TaskList(
            "https://cloud.example.de/remote.php/dav/calendars/alice/einkauf/", "🛒 Einkauf \"neu\"", "#2E7D32",
            canWrite = true, isOwner = false, ownerName = "Bob",
            shares = listOf(ShareEntry("principal:principals/users/carol", "Carol", true)),
            ctag = "c1", description = "Zeile 1\nZeile 2", group = "Zuhause", order = 20,
        )
        val cal = TodoMapper.newCalendar("Milch \\ Brot, Käse", due = LocalDate.of(2026, 12, 1), starred = true)
        val todo = TodoMapper.read(cal)!!
        val task = TaskItem(list.url + "${todo.uid}.ics", "\"etag-1\"", list.url, todo, cal)
        val data = LocalData(
            lists = listOf(list),
            tasks = mapOf(list.url to listOf(task)),
            pending = mapOf(task.url to Pending(PendingKind.UPDATE, list.url, 3, mapOf("summary" to "x", "due" to null), "\"e\"")),
            lastSync = 1_795_543_580_127,
        )
        val back = LocalDataCodec.decode(LocalDataCodec.encode(data))!!
        assertEquals(data.lists, back.lists)
        assertEquals(data.pending, back.pending)
        assertEquals(data.lastSync, back.lastSync)
        val t = back.tasks.getValue(list.url).single()
        assertEquals(task.todo, t.todo)
        assertEquals(task.etag, t.etag)
        assertEquals(Ics.serialize(task.calendar), Ics.serialize(t.calendar))
    }

    @Test
    fun invalidInputGivesNull() {
        assertNull(LocalDataCodec.decode("kaputt"))
        assertNull(LocalDataCodec.decode("""{"lists":[{"name":"ohne url"}]}"""))
        // Writer escapes control characters so the reader accepts them again.
        assertEquals("a\u0001b", MiniJson.string(MiniJson.parseObject(MiniJson.write(mapOf("k" to "a\u0001b")))!!, "k"))
    }
}
