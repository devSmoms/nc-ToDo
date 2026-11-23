package de.devsmoms.nctodo.ical

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Person a task is assigned to (stored as ATTENDEE, without ORGANIZER, so no invitations are sent). */
data class Assignee(val username: String, val name: String, val email: String?)

/**
 * Read-only view of a VTODO plus the parsed calendar it lives in.
 * All edits go through [TodoMapper] which mutates the underlying component tree,
 * so properties this app does not know about survive round trips.
 */
data class Todo(
    val uid: String,
    val summary: String,
    val description: String,
    val completed: Boolean,
    val completedAt: Instant?,
    val starred: Boolean,
    val due: LocalDate?,
    /** Null = all-day due date. */
    val dueTime: LocalTime? = null,
    val parentUid: String?,
    val created: Instant?,
    /** Raw RRULE value, null if the task does not repeat. */
    val rrule: String? = null,
    val links: List<String> = emptyList(),
    val assignee: Assignee? = null,
) {
    val recurring: Boolean get() = rrule != null
}

object TodoMapper {
    private val UTC_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    private val DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val LOCAL_DT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private const val NC_USER_PARAM = "X-NC-USER"

    /** The master VTODO (the one without RECURRENCE-ID). */
    fun master(calendar: IcsComponent): IcsComponent? {
        val todos = calendar.components.filter { it.name == "VTODO" }
        return todos.firstOrNull { it.prop("RECURRENCE-ID") == null } ?: todos.firstOrNull()
    }

    fun read(calendar: IcsComponent): Todo? {
        val t = master(calendar) ?: return null
        val uid = t.prop("UID")?.text?.takeIf { it.isNotBlank() } ?: return null
        val status = t.prop("STATUS")?.rawValue?.trim()?.uppercase()
        val percent = t.prop("PERCENT-COMPLETE")?.rawValue?.trim()?.toIntOrNull()
        val completedAt = t.prop("COMPLETED")?.let { parseInstant(it) }
        val completed = status == "COMPLETED" || percent == 100 || (status == null && completedAt != null)
        val priority = t.prop("PRIORITY")?.rawValue?.trim()?.toIntOrNull() ?: 0
        val parent = t.props("RELATED-TO").firstOrNull {
            val type = it.param("RELTYPE")?.uppercase()
            type == null || type == "PARENT"
        }?.text?.takeIf { it.isNotBlank() }
        val dueProp = t.prop("DUE")
        val dueDateTime = dueProp?.let { parseLocal(it) }
        return Todo(
            uid = uid,
            summary = t.prop("SUMMARY")?.text.orEmpty(),
            description = t.prop("DESCRIPTION")?.text.orEmpty(),
            completed = completed,
            completedAt = completedAt,
            // Nextcloud Tasks treats priority 1-4 as "high" and shows it as a star.
            starred = priority in 1..4,
            due = dueDateTime?.first,
            dueTime = dueDateTime?.second,
            parentUid = parent,
            created = t.prop("CREATED")?.let { parseInstant(it) },
            rrule = t.prop("RRULE")?.rawValue?.trim()?.takeIf { it.isNotEmpty() },
            links = readLinks(t),
            assignee = t.props("ATTENDEE").firstOrNull()?.let { readAssignee(it) },
        )
    }

    fun newCalendar(
        summary: String,
        parentUid: String? = null,
        due: LocalDate? = null,
        starred: Boolean = false,
        now: Instant = Instant.now(),
        dueTime: LocalTime? = null,
    ): IcsComponent {
        val cal = IcsComponent("VCALENDAR")
        cal.setProp("VERSION", "2.0")
        cal.setProp("PRODID", "-//devsmoms//NC Todo//DE")
        val todo = IcsComponent("VTODO")
        cal.components.add(todo)
        val stamp = UTC_STAMP.format(now)
        todo.setText("UID", UUID.randomUUID().toString())
        todo.setProp("CREATED", stamp)
        todo.setProp("DTSTAMP", stamp)
        todo.setProp("LAST-MODIFIED", stamp)
        todo.setText("SUMMARY", summary)
        todo.setProp("STATUS", "NEEDS-ACTION")
        if (parentUid != null) todo.setText("RELATED-TO", parentUid)
        if (due != null) setDue(cal, due, now, dueTime)
        if (starred) setStarred(cal, true, now)
        return cal
    }

    /**
     * Turns a copy of a recurring task into a finished, non-repeating record of this
     * occurrence (new UID), like Microsoft To Do keeps completed occurrences.
     */
    fun makeCompletedOccurrence(cal: IcsComponent, now: Instant = Instant.now()) {
        val t = master(cal) ?: return
        cal.components.removeAll { it.name == "VTODO" && it !== t } // drop exceptions of the series
        t.setText("UID", UUID.randomUUID().toString())
        t.removeProp("RRULE")
        t.removeProp("RDATE")
        t.removeProp("EXDATE")
        t.removeProp("RECURRENCE-ID")
        t.components.removeAll { it.name == "VALARM" }
        val stamp = UTC_STAMP.format(now)
        t.setProp("CREATED", stamp)
        setCompleted(cal, true, now)
    }

    fun setSummary(cal: IcsComponent, value: String, now: Instant = Instant.now()) = edit(cal, now) { it.setText("SUMMARY", value) }

    fun setDescription(cal: IcsComponent, value: String, now: Instant = Instant.now()) = edit(cal, now) {
        if (value.isBlank()) it.removeProp("DESCRIPTION") else it.setText("DESCRIPTION", value)
    }

    fun setCompleted(cal: IcsComponent, done: Boolean, now: Instant = Instant.now()) = edit(cal, now) {
        if (done) {
            it.setProp("STATUS", "COMPLETED")
            it.setProp("COMPLETED", UTC_STAMP.format(now))
            it.setProp("PERCENT-COMPLETE", "100")
        } else {
            it.setProp("STATUS", "NEEDS-ACTION")
            it.removeProp("COMPLETED")
            it.removeProp("PERCENT-COMPLETE")
        }
    }

    fun setStarred(cal: IcsComponent, starred: Boolean, now: Instant = Instant.now()) = edit(cal, now) {
        if (starred) it.setProp("PRIORITY", "1") else it.removeProp("PRIORITY")
    }

    /**
     * Sets the due date. Without [time] it is an all-day date (VALUE=DATE); with a time it is
     * stored in UTC. DTSTART is converted to the same value type, as RFC 5545 requires.
     */
    fun setDue(cal: IcsComponent, date: LocalDate?, now: Instant = Instant.now(), time: LocalTime? = null) = edit(cal, now) {
        if (date == null) {
            it.removeProp("DUE")
            return@edit
        }
        val dueInstant = if (time != null) date.atTime(time).atZone(ZoneId.systemDefault()).toInstant() else null
        if (dueInstant == null) {
            it.setProp("DUE", DATE.format(date), listOf(IcsParam("VALUE", "DATE")))
        } else {
            it.setProp("DUE", UTC_STAMP.format(dueInstant))
        }
        val startProp = it.prop("DTSTART") ?: return@edit
        val start = parseLocal(startProp)
        if (start == null) {
            it.removeProp("DTSTART")
            return@edit
        }
        val (startDate, startTime) = start
        val startIsAfter = if (dueInstant == null) startDate.isAfter(date)
        else startDate.atTime(startTime ?: LocalTime.MIDNIGHT).atZone(ZoneId.systemDefault()).toInstant().isAfter(dueInstant)
        when {
            // DUE must not be before DTSTART; drop a conflicting start.
            startIsAfter -> it.removeProp("DTSTART")
            dueInstant == null && startTime != null -> it.setProp("DTSTART", DATE.format(startDate), listOf(IcsParam("VALUE", "DATE")))
            dueInstant != null && startTime == null ->
                it.setProp("DTSTART", UTC_STAMP.format(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant()))
        }
    }

    fun setRRule(cal: IcsComponent, rrule: String?, now: Instant = Instant.now()) = edit(cal, now) {
        val clean = rrule?.trim()?.uppercase()?.filter { ch -> ch.isLetterOrDigit() || ch in "=;,-+" }
        if (clean.isNullOrEmpty()) it.removeProp("RRULE") else it.setProp("RRULE", clean)
    }

    /** Replaces the web links. Stored as ATTACH;VALUE=URI (repeatable); a single URL property is kept if still wanted. */
    fun setLinks(cal: IcsComponent, links: List<String>, now: Instant = Instant.now()) = edit(cal, now) { t ->
        val wanted = links.map { it.trim() }.filter { isWebLink(it) }.distinct()
        val keepUrl = t.prop("URL")?.rawValue?.trim()?.takeIf { it in wanted }
        t.removeProp("URL")
        t.properties.removeAll { it.name.equals("ATTACH", true) && isWebLink(it.rawValue.trim()) && it.param("ENCODING") == null }
        if (keepUrl != null) t.setProp("URL", keepUrl, listOf(IcsParam("VALUE", "URI")))
        wanted.filter { it != keepUrl }.forEach { link ->
            t.properties.add(IcsProperty("ATTACH", mutableListOf(IcsParam("VALUE", "URI")), link))
        }
    }

    fun setAssignee(cal: IcsComponent, assignee: Assignee?, now: Instant = Instant.now()) = edit(cal, now) { t ->
        t.removeProp("ATTENDEE")
        if (assignee == null) return@edit
        val p = IcsProperty("ATTENDEE", mutableListOf(), calAddress(assignee))
        p.setParam("CN", assignee.name.ifBlank { assignee.username })
        p.setParam(NC_USER_PARAM, assignee.username)
        t.properties.add(p)
    }

    private fun calAddress(a: Assignee): String =
        if (!a.email.isNullOrBlank() && a.email.matches(Regex("^[^\\s:;,]+@[^\\s:;,]+$"))) "mailto:${a.email}"
        else "urn:x-nextcloud:user:" + a.username.filter { it.isLetterOrDigit() || it in "._-@" }

    private fun readAssignee(p: IcsProperty): Assignee? {
        val value = p.rawValue.trim()
        val email = value.takeIf { it.startsWith("mailto:", true) }?.substring(7)
        val user = p.param(NC_USER_PARAM)
            ?: value.takeIf { it.startsWith("urn:x-nextcloud:user:") }?.substringAfter("urn:x-nextcloud:user:")
            ?: email?.substringBefore('@')
            ?: return null
        return Assignee(user, p.param("CN") ?: user, email)
    }

    private fun readLinks(t: IcsComponent): List<String> {
        val url = t.prop("URL")?.rawValue?.trim()?.takeIf { isWebLink(it) }
        val attachments = t.props("ATTACH").filter { it.param("ENCODING") == null }.map { it.rawValue.trim() }.filter { isWebLink(it) }
        return (listOfNotNull(url) + attachments).distinct()
    }

    /** Only http(s) links are shown and opened – never file:, intent:, javascript: … */
    fun isWebLink(s: String): Boolean =
        (s.startsWith("https://", true) || s.startsWith("http://", true)) && s.length in 10..2000 && s.none { it.isWhitespace() }

    private inline fun edit(cal: IcsComponent, now: Instant, block: (IcsComponent) -> Unit) {
        val t = master(cal) ?: throw IcsParseException("No VTODO in calendar object")
        block(t)
        val stamp = UTC_STAMP.format(now)
        t.setProp("LAST-MODIFIED", stamp)
        t.setProp("DTSTAMP", stamp)
        t.prop("SEQUENCE")?.let { seq -> seq.rawValue = ((seq.rawValue.trim().toIntOrNull() ?: 0) + 1).toString() }
    }

    /** Date and (for DATE-TIME values) local time of a date property. */
    fun parseLocal(p: IcsProperty): Pair<LocalDate, LocalTime?>? {
        val v = p.rawValue.trim()
        return try {
            when {
                v.length == 8 -> LocalDate.parse(v, DATE) to null
                v.endsWith("Z") -> LocalDateTime.parse(v.dropLast(1), LOCAL_DT).atOffset(ZoneOffset.UTC)
                    .atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime().let { it.toLocalDate() to it.toLocalTime().truncatedTo(ChronoUnit.MINUTES) }
                else -> {
                    val ldt = LocalDateTime.parse(v.take(15), LOCAL_DT)
                    val zone = p.param("TZID")?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
                    ldt.atZone(zone).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
                        .let { it.toLocalDate() to it.toLocalTime().truncatedTo(ChronoUnit.MINUTES) }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun parseDate(p: IcsProperty): LocalDate? = parseLocal(p)?.first

    fun parseInstant(p: IcsProperty): Instant? {
        val v = p.rawValue.trim()
        return try {
            when {
                v.length == 8 -> LocalDate.parse(v, DATE).atStartOfDay(ZoneId.systemDefault()).toInstant()
                v.endsWith("Z") -> LocalDateTime.parse(v.dropLast(1), LOCAL_DT).toInstant(ZoneOffset.UTC)
                else -> {
                    val zone = p.param("TZID")?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
                    LocalDateTime.parse(v.take(15), LOCAL_DT).atZone(zone).toInstant()
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
