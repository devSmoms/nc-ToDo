package de.devsmoms.nctodo

import de.devsmoms.nctodo.data.ListIcon
import de.devsmoms.nctodo.dav.DavXml
import de.devsmoms.nctodo.dav.DavXmlException
import de.devsmoms.nctodo.dav.Ns
import de.devsmoms.nctodo.dav.Principals
import de.devsmoms.nctodo.ical.Assignee
import de.devsmoms.nctodo.ical.Ics
import de.devsmoms.nctodo.ical.Recurrence
import de.devsmoms.nctodo.sync.TaskFields
import de.devsmoms.nctodo.ical.TodoMapper
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreTest {
    private val sample = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Nextcloud Tasks v0.16\r\n" +
        "BEGIN:VTODO\r\nUID:abc-123\r\nCREATED:20261101T100000Z\r\nSUMMARY:Milch\\, Brot\\; Käse\r\n" +
        "DESCRIPTION:Zeile 1\\nZeile 2 mit einem sehr langen Text der auf jeden Fall gefaltet werden mus\r\n" +
        " s weil er so lang ist\r\nDUE;VALUE=DATE:20261124\r\nPRIORITY:1\r\nX-CUSTOM;FOO=\"a:b;c\":keep me\r\n" +
        "BEGIN:VALARM\r\nACTION:DISPLAY\r\nTRIGGER:-PT15M\r\nEND:VALARM\r\nEND:VTODO\r\nEND:VCALENDAR\r\n"

    @Test
    fun parsesTodo() {
        val todo = TodoMapper.read(Ics.parse(sample))!!
        assertEquals("abc-123", todo.uid)
        assertEquals("Milch, Brot; Käse", todo.summary)
        assertEquals("Zeile 1\nZeile 2 mit einem sehr langen Text der auf jeden Fall gefaltet werden muss weil er so lang ist", todo.description)
        assertEquals(LocalDate.of(2026, 11, 24), todo.due)
        assertTrue(todo.starred)
        assertFalse(todo.completed)
        assertNull(todo.parentUid)
    }

    @Test
    fun roundTripKeepsUnknownData() {
        val cal = Ics.parse(sample)
        TodoMapper.setCompleted(cal, true, Instant.parse("2026-11-23T12:00:00Z"))
        val out = Ics.serialize(cal)
        assertTrue(out.contains("X-CUSTOM;FOO=\"a:b;c\":keep me"))
        assertTrue(out.contains("BEGIN:VALARM"))
        assertTrue(out.contains("STATUS:COMPLETED"))
        assertTrue(out.contains("COMPLETED:20261123T120000Z"))
        out.split("\r\n").forEach { assertTrue(it.toByteArray().size <= 75, "line too long: $it") }
        val again = TodoMapper.read(Ics.parse(out))!!
        assertTrue(again.completed)
        assertEquals("Milch, Brot; Käse", again.summary)
        TodoMapper.setCompleted(cal, false)
        assertFalse(TodoMapper.read(cal)!!.completed)
    }

    @Test
    fun foldingDoesNotSplitMultibyteChars() {
        val cal = TodoMapper.newCalendar("ä".repeat(200))
        val out = Ics.serialize(cal)
        out.split("\r\n").forEach { assertTrue(it.toByteArray().size <= 75) }
        assertEquals("ä".repeat(200), TodoMapper.read(Ics.parse(out))!!.summary)
    }

    @Test
    fun newlinesCannotInjectProperties() {
        val cal = TodoMapper.newCalendar("Harmlos\r\nEND:VTODO\r\nBEGIN:VEVENT")
        val out = Ics.serialize(cal)
        assertFalse(out.contains("\r\nBEGIN:VEVENT"))
        val parsed = Ics.parse(out)
        assertEquals(1, parsed.components.size)
    }

    @Test
    fun subtaskAndDue() {
        val cal = TodoMapper.newCalendar("Schritt", parentUid = "parent-1", due = LocalDate.of(2026, 12, 1), starred = true)
        val t = TodoMapper.read(Ics.parse(Ics.serialize(cal)))!!
        assertEquals("parent-1", t.parentUid)
        assertEquals(LocalDate.of(2026, 12, 1), t.due)
        assertTrue(t.starred)
        TodoMapper.setDue(cal, null)
        TodoMapper.setStarred(cal, false)
        val t2 = TodoMapper.read(cal)!!
        assertNull(t2.due)
        assertFalse(t2.starred)
    }

    @Test
    fun rejectsBrokenIcs() {
        assertFailsWith<Exception> { Ics.parse("BEGIN:VCALENDAR\r\nBEGIN:VTODO\r\n") }
        assertFailsWith<Exception> { Ics.parse("BEGIN:VCALENDAR\r\nEND:VTODO\r\n") }
    }

    private val calendarsXml = """
        <?xml version="1.0"?>
        <d:multistatus xmlns:d="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav" xmlns:oc="http://owncloud.org/ns" xmlns:x1="http://apple.com/ns/ical/">
          <d:response>
            <d:href>/remote.php/dav/calendars/alice/</d:href>
            <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
          </d:response>
          <d:response>
            <d:href>/remote.php/dav/calendars/alice/einkauf/</d:href>
            <d:propstat><d:prop>
              <d:resourcetype><d:collection/><cal:calendar/></d:resourcetype>
              <d:displayname>Einkauf &amp; Co</d:displayname>
              <x1:calendar-color>#0082C9</x1:calendar-color>
              <cal:supported-calendar-component-set><cal:comp name="VTODO"/></cal:supported-calendar-component-set>
              <d:current-user-privilege-set><d:privilege><d:read/></d:privilege><d:privilege><d:write/></d:privilege></d:current-user-privilege-set>
              <oc:invite>
                <oc:user><d:href>principal:principals/users/bob</d:href><oc:common-name>Bob</oc:common-name><oc:invite-accepted/><oc:access><oc:read-write/></oc:access></oc:user>
                <oc:user><d:href>principal:principals/groups/familie</d:href><oc:invite-accepted/><oc:access><oc:read/></oc:access></oc:user>
              </oc:invite>
            </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
            <d:propstat><d:prop><oc:owner-principal/></d:prop><d:status>HTTP/1.1 404 Not Found</d:status></d:propstat>
          </d:response>
        </d:multistatus>
    """.trimIndent()

    @Test
    fun parsesCalendars() {
        val rs = DavXml.parseMultistatus(calendarsXml, "https://cloud.example.de/remote.php/dav/calendars/alice/")
        assertEquals(2, rs.size)
        assertFalse(DavXml.isCalendar(rs[0]))
        val c = rs[1]
        assertTrue(DavXml.isCalendar(c))
        assertEquals("https://cloud.example.de/remote.php/dav/calendars/alice/einkauf/", c.url)
        assertEquals("Einkauf & Co", c.text(Ns.DAV, "displayname"))
        assertEquals(setOf("VTODO"), DavXml.supportedComponents(c))
        assertTrue(DavXml.privileges(c)!!.contains("write"))
        assertNull(c.prop(Ns.OC, "owner-principal"))
        val shares = DavXml.shares(c)
        assertEquals(2, shares.size)
        assertTrue(shares[0].canWrite)
        assertEquals("Bob", shares[0].displayName)
        assertFalse(shares[1].canWrite)
        assertEquals("familie", shares[1].displayName)
    }

    @Test
    fun rejectsXxe() {
        val evil = """<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><d:multistatus xmlns:d="DAV:">&e;</d:multistatus>"""
        assertFailsWith<DavXmlException> { DavXml.parseMultistatus(evil, "https://a/") }
    }

    @Test
    fun escapesXml() {
        val body = DavXml.mkcalendar("<script>&\"", "#FF0000")
        assertTrue(body.contains("&lt;script&gt;&amp;&quot;"))
        DavXml.parseDocument(body) // must still be well-formed
        DavXml.parseDocument(DavXml.share("principal:principals/users/a&b", true))
    }

    @Test
    fun principals() {
        val p = "https://cloud.example.de/remote.php/dav/principals/users/alice/"
        assertEquals("https://cloud.example.de/remote.php/dav/", Principals.davRoot(p))
        assertEquals("principal:principals/users/alice", Principals.shareHref(p))
        assertEquals("principal:principals/users/bob", Principals.fromUsername(" bob "))
        assertNull(Principals.fromUsername("../groups/admin"))
        assertNull(Principals.fromUsername("a/b"))
        assertNotNull(Principals.fromUsername("max.mustermann@example.de"))
        assertTrue(Principals.samePrincipal("principals/users/alice", p))
        assertFalse(Principals.samePrincipal("principals/users/bob", p))
        assertTrue(Principals.samePath("https://x/remote.php/dav/principals/users/a/", "/remote.php/dav/principals/users/a"))
    }

    @Test
    fun listIcons() {
        assertEquals("🛒" to "Einkauf", ListIcon.split("🛒 Einkauf"))
        assertEquals("✈️" to "Urlaub 2027", ListIcon.split("✈️ Urlaub 2027"))
        assertEquals("👨‍👩‍👧" to "Familie", ListIcon.split("👨‍👩‍👧 Familie"))
        assertEquals(null to "Arbeit", ListIcon.split("Arbeit"))
        assertEquals(null to "3 Dinge", ListIcon.split("3 Dinge"))
        assertEquals("Einkauf", ListIcon.plainName("🛒 Einkauf"))
        assertEquals("🛒", ListIcon.plainName("🛒"))
        assertEquals("🛒 Einkauf", ListIcon.join("🛒", " Einkauf "))
        assertEquals("Einkauf", ListIcon.join(null, "Einkauf"))
    }

    @Test
    fun dueWithTimeAndAllDay() {
        val cal = TodoMapper.newCalendar("Arzt", due = LocalDate.of(2026, 12, 3), dueTime = java.time.LocalTime.of(14, 30))
        val t = TodoMapper.read(Ics.parse(Ics.serialize(cal)))!!
        assertEquals(LocalDate.of(2026, 12, 3), t.due)
        assertEquals(java.time.LocalTime.of(14, 30), t.dueTime)
        assertTrue(Ics.serialize(cal).contains(Regex("DUE:\\d{8}T\\d{6}Z")))
        TodoMapper.setDue(cal, LocalDate.of(2026, 12, 4))
        val t2 = TodoMapper.read(cal)!!
        assertNull(t2.dueTime)
        assertTrue(Ics.serialize(cal).contains("DUE;VALUE=DATE:20261204"))
    }

    @Test
    fun dtstartFollowsDueValueType() {
        val ics = "BEGIN:VCALENDAR\r\nBEGIN:VTODO\r\nUID:x\r\nDTSTART;VALUE=DATE:20261201\r\nEND:VTODO\r\nEND:VCALENDAR\r\n"
        val cal = Ics.parse(ics)
        TodoMapper.setDue(cal, LocalDate.of(2026, 12, 5), time = java.time.LocalTime.of(9, 0))
        val out = Ics.serialize(cal)
        assertFalse(out.contains("DTSTART;VALUE=DATE"), out)
        assertTrue(out.contains(Regex("DTSTART:\\d{8}T\\d{6}Z")))
    }

    @Test
    fun recurrence() {
        val weekdays = Recurrence.weekdays()
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", Recurrence.format(weekdays))
        // Friday -> Monday
        assertEquals(LocalDate.of(2026, 11, 30), Recurrence.next(weekdays, LocalDate.of(2026, 11, 27)))
        assertEquals(LocalDate.of(2026, 11, 24), Recurrence.next(Recurrence.daily(), LocalDate.of(2026, 11, 23)))
        assertEquals(LocalDate.of(2027, 2, 28), Recurrence.next(Recurrence.monthly(), LocalDate.of(2027, 1, 31)))
        val biweekly = Recurrence.parse("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO")!!
        assertEquals(LocalDate.of(2026, 12, 7), Recurrence.next(biweekly, LocalDate.of(2026, 11, 23)))
        val counted = Recurrence.parse("FREQ=DAILY;COUNT=2;X-FOO=1")!!
        assertEquals("FREQ=DAILY;COUNT=1;X-FOO=1", Recurrence.format(Recurrence.afterOneOccurrence(counted)))
        assertNull(Recurrence.next(Recurrence.afterOneOccurrence(counted), LocalDate.of(2026, 1, 1)))
        val until = Recurrence.parse("FREQ=DAILY;UNTIL=20261124T000000Z")!!
        assertNull(Recurrence.next(until, LocalDate.of(2026, 11, 24)))
        assertEquals("Werktags", Recurrence.describe(weekdays))
        assertEquals("Alle 2 Wochen (Mo)", Recurrence.describe(biweekly))
    }

    @Test
    fun linksAndAssignee() {
        val cal = TodoMapper.newCalendar("Plakat")
        TodoMapper.setLinks(cal, listOf("https://example.de/a?b=1,2", "javascript:alert(1)", "https://example.de/a?b=1,2", "http://x.de/p"))
        var t = TodoMapper.read(Ics.parse(Ics.serialize(cal)))!!
        assertEquals(listOf("https://example.de/a?b=1,2", "http://x.de/p"), t.links)
        TodoMapper.setLinks(cal, listOf("http://x.de/p"))
        assertEquals(listOf("http://x.de/p"), TodoMapper.read(cal)!!.links)

        TodoMapper.setAssignee(cal, Assignee("lisa", "Lisa Müller, Familie", "lisa@example.de"))
        val out = Ics.serialize(cal)
        assertFalse(out.contains("ORGANIZER"), "no ORGANIZER -> no invitation mails")
        t = TodoMapper.read(Ics.parse(out))!!
        assertEquals(Assignee("lisa", "Lisa Müller, Familie", "lisa@example.de"), t.assignee)
        TodoMapper.setAssignee(cal, Assignee("max", "Max", null))
        assertEquals("max", TodoMapper.read(Ics.parse(Ics.serialize(cal)))!!.assignee!!.username)
        TodoMapper.setAssignee(cal, null)
        assertNull(TodoMapper.read(cal)!!.assignee)
    }

    @Test
    fun taskFieldsEncoding() {
        assertEquals("2026-11-24T09:05", TaskFields.encodeDue(LocalDate.of(2026, 11, 24), java.time.LocalTime.of(9, 5)))
        assertEquals(LocalDate.of(2026, 11, 24) to java.time.LocalTime.of(9, 5), TaskFields.decodeDue("2026-11-24T09:05"))
        assertEquals(LocalDate.of(2026, 11, 24) to null, TaskFields.decodeDue("2026-11-24"))
        val cal = TodoMapper.newCalendar("x")
        TaskFields.apply(cal, TaskFields.assignee(Assignee("bob", "Bob", null)) + TaskFields.links(listOf("https://a.de/x")) + TaskFields.rrule("FREQ=DAILY"))
        val t = TodoMapper.read(cal)!!
        assertEquals("bob", t.assignee!!.username)
        assertEquals(listOf("https://a.de/x"), t.links)
        assertEquals("FREQ=DAILY", t.rrule)
    }

    @Test
    fun listDescriptionXml() {
        val withDesc = DavXml.proppatch("Einkauf", "#00FF00", "Für <alle> & jeden")
        DavXml.parseDocument(withDesc)
        assertTrue(withDesc.contains("<c:calendar-description>Für &lt;alle&gt; &amp; jeden</c:calendar-description>"))
        val without = DavXml.proppatch("Einkauf", "#00FF00", "")
        DavXml.parseDocument(without)
        assertTrue(without.contains("<d:remove><d:prop><c:calendar-description/></d:prop></d:remove>"))
        DavXml.parseDocument(DavXml.mkcalendar("A", "#000000", "B"))
        DavXml.parseDocument(DavXml.REPORT_TRASH_OBJECTS)
        DavXml.parseDocument(DavXml.PROPFIND_TRASHBIN)
    }
}
