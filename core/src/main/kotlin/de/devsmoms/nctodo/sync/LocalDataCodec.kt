package de.devsmoms.nctodo.sync

import de.devsmoms.nctodo.dav.ShareEntry
import de.devsmoms.nctodo.data.MiniJson
import de.devsmoms.nctodo.data.TaskItem
import de.devsmoms.nctodo.data.TaskList
import de.devsmoms.nctodo.ical.Ics
import de.devsmoms.nctodo.ical.TodoMapper

/**
 * Offline data <-> JSON, the same format the Android app writes (version 1). Platforms only
 * add encryption and file handling around it. Tasks are stored as raw iCalendar so nothing
 * other apps put into a task gets lost.
 */
object LocalDataCodec {

    fun encode(d: LocalData): String = MiniJson.write(
        buildMap {
            put("version", 1)
            d.lastSync?.let { put("lastSync", it) }
            put("lists", d.lists.map { l ->
                buildMap {
                    put("url", l.url); put("name", l.name); put("color", l.color)
                    put("canWrite", l.canWrite); put("isOwner", l.isOwner)
                    l.ownerName?.let { put("ownerName", it) }
                    l.ctag?.let { put("ctag", it) }
                    l.description?.let { put("description", it) }
                    l.ownerPrincipal?.let { put("ownerPrincipal", it) }
                    l.group?.let { put("group", it) }
                    l.order?.let { put("order", it) }
                    put("shares", l.shares.map { s -> mapOf("principal" to s.principal, "name" to s.displayName, "write" to s.canWrite) })
                }
            })
            put("tasks", d.tasks.values.flatten().map { t ->
                buildMap {
                    put("url", t.url); put("listUrl", t.listUrl)
                    t.etag?.let { put("etag", it) }
                    put("ics", Ics.serialize(t.calendar))
                }
            })
            put("pending", d.pending.map { (url, p) ->
                buildMap {
                    put("url", url); put("kind", p.kind.name); put("listUrl", p.listUrl); put("rev", p.rev)
                    p.etag?.let { put("etag", it) }
                    p.fields?.let { put("fields", it) }
                }
            })
        },
    )

    /** Null if [json] is not a valid offline store (the caller then starts fresh). */
    fun decode(json: String): LocalData? {
        val o = MiniJson.parseObject(json) ?: return null
        return runCatching {
            val lists = objects(o["lists"]).map { l ->
                TaskList(
                    url = str(l, "url")!!,
                    name = str(l, "name")!!,
                    color = str(l, "color") ?: DEFAULT_COLOR,
                    canWrite = l["canWrite"] as? Boolean ?: true,
                    isOwner = l["isOwner"] as? Boolean ?: true,
                    ownerName = str(l, "ownerName"),
                    shares = objects(l["shares"]).map {
                        ShareEntry(str(it, "principal")!!, str(it, "name").orEmpty(), it["write"] as? Boolean ?: false)
                    },
                    ctag = str(l, "ctag"),
                    description = str(l, "description"),
                    ownerPrincipal = str(l, "ownerPrincipal"),
                    group = str(l, "group"),
                    order = (l["order"] as? Number)?.toInt(),
                )
            }
            val tasks = objects(o["tasks"]).mapNotNull { t ->
                val cal = runCatching { Ics.parse(str(t, "ics")!!) }.getOrNull() ?: return@mapNotNull null
                val todo = TodoMapper.read(cal) ?: return@mapNotNull null
                TaskItem(str(t, "url")!!, str(t, "etag"), str(t, "listUrl")!!, todo, cal)
            }.groupBy { it.listUrl }.let { grouped -> lists.associate { it.url to grouped[it.url].orEmpty() } }
            val pending = objects(o["pending"]).associate { p ->
                val fields = (p["fields"] as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to v as String? }
                str(p, "url")!! to Pending(
                    kind = PendingKind.valueOf(str(p, "kind")!!),
                    listUrl = str(p, "listUrl")!!,
                    rev = (p["rev"] as Number).toLong(),
                    fields = fields,
                    etag = str(p, "etag"),
                )
            }
            LocalData(lists, tasks, pending, (o["lastSync"] as? Number)?.toLong())
        }.getOrNull()
    }

    /** Same as the first entry of CalDavRepository.COLORS. */
    private const val DEFAULT_COLOR = "#0082C9"

    private fun objects(v: Any?): List<Map<*, *>> = (v as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
    private fun str(m: Map<*, *>, key: String): String? = m[key] as? String
}
