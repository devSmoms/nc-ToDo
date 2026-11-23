package de.devsmoms.nctodo.data

import de.devsmoms.nctodo.dav.ShareEntry
import de.devsmoms.nctodo.ical.IcsComponent
import de.devsmoms.nctodo.ical.Todo

data class TaskList(
    val url: String,
    val name: String,
    val color: String,
    val canWrite: Boolean,
    val isOwner: Boolean,
    val ownerName: String?,
    val shares: List<ShareEntry>,
    /** Collection tag; changes whenever anything in the list changes. Used to skip unchanged lists. */
    val ctag: String? = null,
    /** CalDAV calendar-description. */
    val description: String? = null,
    /** Owner as "principal:principals/users/<name>" (for shared lists), null if unknown. */
    val ownerPrincipal: String? = null,
) {
    /** Lists whose changes can come from other people. */
    val isShared: Boolean get() = !isOwner || shares.isNotEmpty()
}

data class TaskItem(
    val url: String,
    val etag: String?,
    val listUrl: String,
    val todo: Todo,
    /** Parsed iCalendar object. Treat as immutable – edits work on a copy. */
    val calendar: IcsComponent,
)

data class PrincipalHit(val shareHref: String, val displayName: String, val email: String?)

/** A task in the Nextcloud trash bin. */
data class TrashedTask(val url: String, val summary: String, val listUri: String?, val deletedAt: java.time.Instant?)

/** A deleted list in the Nextcloud trash bin. */
data class TrashedList(val url: String, val name: String, val color: String, val deletedAt: java.time.Instant?)

data class TrashContent(val tasks: List<TrashedTask>, val lists: List<TrashedList>, val retentionDays: Int?)

/** Server operations the sync engine needs. Implemented by [CalDavRepository], faked in tests. */
interface Remote {
    suspend fun lists(): List<TaskList>
    suspend fun tasks(list: TaskList): List<TaskItem>

    /** Throws NotFoundException if the object no longer exists. */
    suspend fun get(url: String, listUrl: String): TaskItem

    /** Creates a new object; throws PreconditionFailedException if it already exists. Returns the new ETag if known. */
    suspend fun putNew(url: String, ics: String): String?

    /** Overwrites an object. With [etag] the write only succeeds if nobody changed it meanwhile. */
    suspend fun putExisting(url: String, ics: String, etag: String?): String?

    /** Deletes an object; a missing object counts as success. */
    suspend fun delete(url: String, etag: String?)

    suspend fun createList(name: String, color: String, description: String = ""): String
    suspend fun updateList(list: TaskList, name: String, color: String, description: String = "")
    suspend fun deleteList(list: TaskList)
    suspend fun share(list: TaskList, principal: String, canWrite: Boolean)
    suspend fun unshare(list: TaskList, principal: String)
    suspend fun searchUsers(term: String): List<PrincipalHit>

    /** Display name and e-mail of a user ("principal:principals/users/<name>"), null if unknown. */
    suspend fun userInfo(principal: String): PrincipalHit?

    /** Nextcloud trash bin (deleted tasks and lists). */
    suspend fun trash(): TrashContent
    suspend fun restoreFromTrash(url: String)
    suspend fun deleteFromTrash(url: String)
}
