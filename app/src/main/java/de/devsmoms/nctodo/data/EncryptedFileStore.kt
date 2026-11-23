package de.devsmoms.nctodo.data

import android.content.Context
import android.util.AtomicFile
import de.devsmoms.nctodo.dav.ShareEntry
import de.devsmoms.nctodo.ical.Ics
import de.devsmoms.nctodo.ical.TodoMapper
import de.devsmoms.nctodo.sync.LocalData
import de.devsmoms.nctodo.sync.LocalPersistence
import de.devsmoms.nctodo.sync.Pending
import de.devsmoms.nctodo.sync.PendingKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Offline cache: all lists, tasks and unsent changes in one AES-256-GCM encrypted file
 * inside the app's private storage. Writes are atomic, so a crash never leaves a broken file.
 */
class EncryptedFileStore(context: Context) : LocalPersistence {
    private val file = AtomicFile(File(context.applicationContext.noBackupFilesDir, "offline-cache.bin"))
    private val cipher = KeystoreCipher("nctodo_cache_v1", "nctodo-cache-v1".toByteArray())

    override suspend fun load(): LocalData? = withContext(Dispatchers.IO) {
        if (!file.baseFile.exists()) return@withContext null
        try {
            fromJson(JSONObject(String(cipher.decrypt(file.readFully()), Charsets.UTF_8)))
        } catch (_: Exception) {
            // Undecryptable or corrupt: start fresh, the server has the data.
            file.delete()
            null
        }
    }

    override suspend fun save(data: LocalData) = withContext(Dispatchers.IO) {
        val blob = cipher.encrypt(toJson(data).toString().toByteArray(Charsets.UTF_8))
        val out = file.startWrite()
        try {
            out.write(blob)
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        file.delete()
        cipher.deleteKey()
    }

    // ---------------------------------------------------------------- JSON mapping

    private fun toJson(d: LocalData) = JSONObject().apply {
        put("version", 1)
        d.lastSync?.let { put("lastSync", it) }
        put("lists", JSONArray().apply {
            d.lists.forEach { l ->
                put(JSONObject().apply {
                    put("url", l.url); put("name", l.name); put("color", l.color)
                    put("canWrite", l.canWrite); put("isOwner", l.isOwner)
                    l.ownerName?.let { put("ownerName", it) }
                    l.ctag?.let { put("ctag", it) }
                    l.description?.let { put("description", it) }
                    l.ownerPrincipal?.let { put("ownerPrincipal", it) }
                    put("shares", JSONArray().apply {
                        l.shares.forEach { s ->
                            put(JSONObject().put("principal", s.principal).put("name", s.displayName).put("write", s.canWrite))
                        }
                    })
                })
            }
        })
        put("tasks", JSONArray().apply {
            d.tasks.values.flatten().forEach { t ->
                put(JSONObject().apply {
                    put("url", t.url); put("listUrl", t.listUrl)
                    t.etag?.let { put("etag", it) }
                    put("ics", Ics.serialize(t.calendar))
                })
            }
        })
        put("pending", JSONArray().apply {
            d.pending.forEach { (url, p) ->
                put(JSONObject().apply {
                    put("url", url); put("kind", p.kind.name); put("listUrl", p.listUrl); put("rev", p.rev)
                    p.etag?.let { put("etag", it) }
                    p.fields?.let { f -> put("fields", JSONObject().apply { f.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) } }) }
                })
            }
        })
    }

    private fun fromJson(o: JSONObject): LocalData {
        val lists = o.optJSONArray("lists").objects().map { l ->
            TaskList(
                url = l.getString("url"),
                name = l.getString("name"),
                color = l.optString("color", CalDavRepository.COLORS.first()),
                canWrite = l.optBoolean("canWrite", true),
                isOwner = l.optBoolean("isOwner", true),
                ownerName = l.optStringOrNull("ownerName"),
                shares = l.optJSONArray("shares").objects().map {
                    ShareEntry(it.getString("principal"), it.optString("name"), it.optBoolean("write"))
                },
                ctag = l.optStringOrNull("ctag"),
                description = l.optStringOrNull("description"),
                ownerPrincipal = l.optStringOrNull("ownerPrincipal"),
            )
        }
        val tasks = o.optJSONArray("tasks").objects().mapNotNull { t ->
            val cal = runCatching { Ics.parse(t.getString("ics")) }.getOrNull() ?: return@mapNotNull null
            val todo = TodoMapper.read(cal) ?: return@mapNotNull null
            TaskItem(t.getString("url"), t.optStringOrNull("etag"), t.getString("listUrl"), todo, cal)
        }.groupBy { it.listUrl }.let { grouped -> lists.associate { it.url to grouped[it.url].orEmpty() } }
        val pending = o.optJSONArray("pending").objects().associate { p ->
            val fields = p.optJSONObject("fields")?.let { f -> f.keys().asSequence().associateWith { k -> if (f.isNull(k)) null else f.getString(k) } }
            p.getString("url") to Pending(
                kind = PendingKind.valueOf(p.getString("kind")),
                listUrl = p.getString("listUrl"),
                rev = p.getLong("rev"),
                fields = fields,
                etag = p.optStringOrNull("etag"),
            )
        }
        return LocalData(lists, tasks, pending, if (o.has("lastSync")) o.getLong("lastSync") else null)
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONObject.optStringOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
}
