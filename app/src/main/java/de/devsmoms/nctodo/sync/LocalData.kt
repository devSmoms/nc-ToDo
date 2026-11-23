package de.devsmoms.nctodo.sync

import de.devsmoms.nctodo.data.TaskItem
import de.devsmoms.nctodo.data.TaskList
import de.devsmoms.nctodo.ical.Assignee
import de.devsmoms.nctodo.ical.IcsComponent
import de.devsmoms.nctodo.ical.TodoMapper
import java.time.LocalDate
import java.time.LocalTime

enum class PendingKind { CREATE, UPDATE, DELETE }

/**
 * A local change that has not reached the server yet.
 *
 * UPDATE records *which fields* were changed ([fields]); on a conflict they are re-applied
 * on top of the server version, so concurrent edits of other fields by other people survive.
 * [fields] == null means "upload the whole local version".
 */
data class Pending(
    val kind: PendingKind,
    val listUrl: String,
    val rev: Long,
    val fields: Map<String, String?>? = null,
    /** For DELETE: the ETag of the deleted version. */
    val etag: String? = null,
)

data class LocalData(
    val lists: List<TaskList> = emptyList(),
    val tasks: Map<String, List<TaskItem>> = emptyMap(),
    val pending: Map<String, Pending> = emptyMap(),
    val lastSync: Long? = null,
) {
    fun task(url: String): TaskItem? = tasks.values.firstNotNullOfOrNull { l -> l.firstOrNull { it.url == url } }
    fun list(url: String): TaskList? = lists.firstOrNull { it.url == url }
}

/** Field names used in [Pending.fields]. Values are strings so the queue can be persisted as JSON. */
object TaskFields {
    const val SUMMARY = "summary"
    const val DESCRIPTION = "description"
    const val COMPLETED = "completed"
    const val STARRED = "starred"
    /** "2026-11-24" (all day) or "2026-11-24T14:30" (local time). */
    const val DUE = "due"
    const val RRULE = "rrule"
    /** Links separated by newlines. */
    const val LINKS = "links"
    /** username, name, email separated by U+001F. */
    const val ASSIGNEE = "assignee"

    private const val SEP = '\u001F'

    fun apply(cal: IcsComponent, fields: Map<String, String?>) {
        for ((key, value) in fields) when (key) {
            SUMMARY -> TodoMapper.setSummary(cal, value.orEmpty())
            DESCRIPTION -> TodoMapper.setDescription(cal, value.orEmpty())
            COMPLETED -> TodoMapper.setCompleted(cal, value == "1")
            STARRED -> TodoMapper.setStarred(cal, value == "1")
            DUE -> {
                val (date, time) = decodeDue(value)
                TodoMapper.setDue(cal, date, time = time)
            }
            RRULE -> TodoMapper.setRRule(cal, value)
            LINKS -> TodoMapper.setLinks(cal, value?.split('\n')?.filter { it.isNotBlank() }.orEmpty())
            ASSIGNEE -> TodoMapper.setAssignee(cal, decodeAssignee(value))
        }
    }

    fun completed(done: Boolean) = mapOf(COMPLETED to if (done) "1" else "0")
    fun starred(on: Boolean) = mapOf(STARRED to if (on) "1" else "0")
    fun due(date: LocalDate?, time: LocalTime? = null) = mapOf(DUE to encodeDue(date, time))
    fun summary(v: String) = mapOf(SUMMARY to v)
    fun description(v: String) = mapOf(DESCRIPTION to v)
    fun rrule(v: String?) = mapOf(RRULE to v)
    fun links(v: List<String>) = mapOf(LINKS to v.joinToString("\n"))
    fun assignee(a: Assignee?) = mapOf(ASSIGNEE to a?.let { listOf(it.username, it.name, it.email.orEmpty()).joinToString(SEP.toString()) })

    fun encodeDue(date: LocalDate?, time: LocalTime?): String? =
        date?.let { d -> if (time == null) d.toString() else "${d}T%02d:%02d".format(time.hour, time.minute) }

    fun decodeDue(value: String?): Pair<LocalDate?, LocalTime?> {
        if (value.isNullOrBlank()) return null to null
        val date = runCatching { LocalDate.parse(value.take(10)) }.getOrNull() ?: return null to null
        val time = if (value.length > 11) runCatching { LocalTime.parse(value.substring(11)) }.getOrNull() else null
        return date to time
    }

    private fun decodeAssignee(value: String?): Assignee? {
        val parts = value?.split(SEP) ?: return null
        val user = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
        return Assignee(user, parts.getOrNull(1).orEmpty().ifBlank { user }, parts.getOrNull(2)?.takeIf { it.isNotBlank() })
    }
}

/** Persistent storage for [LocalData]. On Android this is an encrypted file. */
interface LocalPersistence {
    suspend fun load(): LocalData?
    suspend fun save(data: LocalData)
    suspend fun clear()
}

data class SyncStatus(
    val running: Boolean = false,
    val lastSuccess: Long? = null,
    /** True if the last attempt failed because the server could not be reached. */
    val offline: Boolean = false,
    val error: String? = null,
)

/** Changes made by other people in shared lists, detected during a pull. */
data class SharedChange(val listName: String, val added: List<String>, val completed: List<String>)
