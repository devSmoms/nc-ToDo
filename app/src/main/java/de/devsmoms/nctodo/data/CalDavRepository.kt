package de.devsmoms.nctodo.data

import de.devsmoms.nctodo.dav.DavClient
import de.devsmoms.nctodo.dav.DavException
import de.devsmoms.nctodo.dav.DavXml
import de.devsmoms.nctodo.dav.Ns
import de.devsmoms.nctodo.dav.Principals
import de.devsmoms.nctodo.dav.NotFoundException
import de.devsmoms.nctodo.ical.Ics
import de.devsmoms.nctodo.ical.TodoMapper
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID

class CalDavRepository(private val creds: Credentials) : Remote {
    private val client = DavClient(creds.serverUrl, creds.username, creds.password)

    // ---------------------------------------------------------------- lists

    override suspend fun lists(): List<TaskList> {
        val res = client.propfind(creds.calendarHomeUrl, 1, DavXml.PROPFIND_CALENDARS)
        return DavXml.parseMultistatus(res.body, creds.calendarHomeUrl)
            .filter { DavXml.isCalendar(it) && client.isSameOrigin(it.url) }
            .filter { DavXml.supportedComponents(it)?.contains("VTODO") ?: true }
            .map { r ->
                val privileges = DavXml.privileges(r)
                val ownerHref = DavXml.hrefIn(r.prop(Ns.OC, "owner-principal"))
                    ?: r.text(Ns.OC, "owner-principal")
                val isOwner = ownerHref == null || Principals.samePrincipal(ownerHref, creds.principalUrl)
                TaskList(
                    url = r.url,
                    name = r.text(Ns.DAV, "displayname") ?: r.url.trimEnd('/').substringAfterLast('/'),
                    color = normalizeColor(r.text(Ns.APPLE, "calendar-color")),
                    canWrite = privileges == null || privileges.any { it in setOf("write", "write-content", "all") },
                    isOwner = isOwner,
                    ownerName = if (isOwner) null else r.text(Ns.NC, "owner-displayname"),
                    // Group shares made elsewhere are still listed so they can be removed here.
                    shares = if (isOwner) DavXml.shares(r).filter { Principals.isUserOrGroup(it.principal) } else emptyList(),
                    ctag = r.text(Ns.CS, "getctag"),
                    description = r.text(Ns.CALDAV, "calendar-description"),
                    group = r.text(Ns.NCTODO, "group")?.take(MAX_GROUP_LENGTH),
                    order = r.text(Ns.NCTODO, "order")?.toIntOrNull(),
                    ownerPrincipal = ownerHref?.let { h -> h.substringAfter("principals/", "").takeIf { it.isNotEmpty() }?.let { "principal:principals/" + it.trimEnd('/') } },
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    override suspend fun createList(name: String, color: String, description: String): String {
        val url = creds.calendarHomeUrl.trimEnd('/') + "/" + slug(name) + "-" + UUID.randomUUID().toString().take(8) + "/"
        client.mkcol(url, DavXml.mkcalendar(name.trim(), color, description.trim()))
        return url
    }

    override suspend fun updateList(list: TaskList, name: String, color: String, description: String) {
        client.proppatch(list.url, DavXml.proppatch(name.trim(), color, description.trim()))
    }

    override suspend fun setListMeta(list: TaskList, group: String?, order: Int?) {
        val res = client.proppatch(list.url, DavXml.setListMeta(group, order))
        // PROPPATCH answers 207 even when a property is refused: check the property's own status.
        val ok = runCatching { DavXml.parseMultistatus(res.body, list.url) }.getOrNull()
            ?.any { it.prop(Ns.NCTODO, "group") != null || it.prop(Ns.NCTODO, "order") != null } ?: true
        if (!ok) throw DavException("Der Server speichert keine Listengruppen")
    }

    /** For own lists this deletes the list; for shared lists Nextcloud removes the share for this user. */
    override suspend fun deleteList(list: TaskList) {
        client.delete(list.url, null)
    }

    // ---------------------------------------------------------------- sharing (Nextcloud users only)

    override suspend fun share(list: TaskList, principal: String, canWrite: Boolean) {
        require(Principals.isUser(principal)) { "Es kann nur mit Nextcloud-Benutzern geteilt werden" }
        require(list.isOwner) { "Nur der Besitzer kann eine Liste teilen" }
        client.post(list.url, DavXml.share(principal, canWrite))
    }

    override suspend fun unshare(list: TaskList, principal: String) {
        client.post(list.url, DavXml.unshare(principal))
    }

    override suspend fun searchUsers(term: String): List<PrincipalHit> {
        if (term.trim().length < 2) return emptyList()
        val root = Principals.davRoot(creds.principalUrl) ?: return emptyList()
        val ownHref = Principals.shareHref(creds.principalUrl)
        val res = client.report(root, 0, DavXml.principalSearch(term.trim()))
        return DavXml.parseMultistatus(res.body, root).mapNotNull { r ->
            val href = Principals.shareHref(r.url) ?: return@mapNotNull null
            if (!Principals.isUser(href) || href == ownHref) return@mapNotNull null
            PrincipalHit(
                shareHref = href,
                displayName = r.text(Ns.DAV, "displayname") ?: href.substringAfterLast('/'),
                email = r.text(Ns.SABRE, "email-address"),
            )
        }.distinctBy { it.shareHref }.take(20)
    }

    override suspend fun userInfo(principal: String): PrincipalHit? {
        if (!Principals.isUser(principal)) return null
        val root = Principals.davRoot(creds.principalUrl) ?: return null
        val url = root + principal.removePrefix("principal:") + "/"
        return runCatching {
            val r = DavXml.parseMultistatus(client.propfind(url, 0, DavXml.PROPFIND_USER).body, url).firstOrNull() ?: return null
            PrincipalHit(principal, r.text(Ns.DAV, "displayname") ?: principal.substringAfterLast('/'), r.text(Ns.SABRE, "email-address"))
        }.getOrNull()
    }

    // ---------------------------------------------------------------- trash bin (Nextcloud 22+)

    private val trashUrl get() = creds.calendarHomeUrl.trimEnd('/') + "/trashbin/"

    override suspend fun trash(): TrashContent {
        val root = DavXml.parseMultistatus(client.propfind(trashUrl, 1, DavXml.PROPFIND_TRASHBIN).body, trashUrl)
        val retention = root.firstOrNull { it.url.trimEnd('/') == trashUrl.trimEnd('/') }
            ?.text(Ns.NC, "trash-bin-retention-duration")?.toLongOrNull()?.let { (it / 86_400).toInt() }
        val lists = root.filter { DavXml.isCalendar(it) && client.isSameOrigin(it.url) }
            .filter { DavXml.supportedComponents(it)?.contains("VTODO") ?: true }
            .map {
                TrashedList(
                    url = it.url,
                    name = it.text(Ns.DAV, "displayname") ?: it.url.trimEnd('/').substringAfterLast('/'),
                    color = normalizeColor(it.text(Ns.APPLE, "calendar-color")),
                    deletedAt = parseDeletedAt(it.text(Ns.NC, "deleted-at")),
                )
            }
        val objectsUrl = trashUrl + "objects/"
        val tasks = DavXml.parseMultistatus(client.report(objectsUrl, 1, DavXml.REPORT_TRASH_OBJECTS).body, objectsUrl).mapNotNull { r ->
            if (!client.isSameOrigin(r.url)) return@mapNotNull null
            val data = r.prop(Ns.CALDAV, "calendar-data")?.textContent ?: return@mapNotNull null
            val todo = runCatching { TodoMapper.read(Ics.parse(data)) }.getOrNull() ?: return@mapNotNull null
            TrashedTask(r.url, todo.summary, r.text(Ns.NC, "calendar-uri"), parseDeletedAt(r.text(Ns.NC, "deleted-at")))
        }
        return TrashContent(tasks.sortedByDescending { it.deletedAt }, lists.sortedByDescending { it.deletedAt }, retention)
    }

    /** Restoring = MOVE into the special "restore" collection of the trash bin. */
    override suspend fun restoreFromTrash(url: String) {
        require(url.startsWith(trashUrl)) { "Kein Papierkorb-Eintrag" }
        val name = url.trimEnd('/').substringAfterLast('/')
        client.move(url, trashUrl + "restore/" + name)
    }

    override suspend fun deleteFromTrash(url: String) {
        require(url.startsWith(trashUrl)) { "Kein Papierkorb-Eintrag" }
        client.delete(url, null)
    }

    private fun parseDeletedAt(v: String?): java.time.Instant? = v?.let {
        runCatching { java.time.OffsetDateTime.parse(it).toInstant() }.getOrNull()
            ?: runCatching { java.time.Instant.parse(it) }.getOrNull()
            ?: it.toLongOrNull()?.let { s -> java.time.Instant.ofEpochSecond(s) }
    }

    // ---------------------------------------------------------------- tasks

    override suspend fun tasks(list: TaskList): List<TaskItem> {
        val res = client.report(list.url, 1, DavXml.REPORT_TODOS)
        return DavXml.parseMultistatus(res.body, list.url).mapNotNull { r ->
            if (!client.isSameOrigin(r.url)) return@mapNotNull null
            val data = r.prop(Ns.CALDAV, "calendar-data")?.textContent ?: return@mapNotNull null
            // A single broken object must not break the whole list.
            val cal = runCatching { Ics.parse(data) }.getOrNull() ?: return@mapNotNull null
            val todo = TodoMapper.read(cal) ?: return@mapNotNull null
            TaskItem(r.url, r.text(Ns.DAV, "getetag"), list.url, todo, cal)
        }
    }

    override suspend fun get(url: String, listUrl: String): TaskItem {
        val res = client.get(url)
        val cal = Ics.parse(res.body)
        val todo = TodoMapper.read(cal) ?: throw DavException("Ungültige Aufgabe auf dem Server")
        return TaskItem(url, res.etag ?: fetchEtag(url), listUrl, todo, cal)
    }

    override suspend fun putNew(url: String, ics: String): String? =
        client.put(url, ics, ifMatch = null, ifNoneMatch = true).etag ?: fetchEtag(url)

    override suspend fun putExisting(url: String, ics: String, etag: String?): String? =
        client.put(url, ics, ifMatch = etag, ifNoneMatch = false).etag ?: fetchEtag(url)

    override suspend fun delete(url: String, etag: String?) {
        try {
            client.delete(url, etag)
        } catch (_: NotFoundException) {
            // Already gone – that is what we wanted.
        }
    }

    private suspend fun fetchEtag(url: String): String? = runCatching {
        val body = """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:getetag/></d:prop></d:propfind>"""
        DavXml.parseMultistatus(client.propfind(url, 0, body).body, url).firstOrNull()?.text(Ns.DAV, "getetag")
    }.getOrNull()

    companion object {
        const val MAX_GROUP_LENGTH = 60
        val COLORS = listOf("#0082C9", "#2E7D32", "#C62828", "#6A1B9A", "#EF6C00", "#00838F", "#AD1457", "#455A64")

        fun normalizeColor(raw: String?): String {
            val c = raw?.trim().orEmpty()
            // Servers may send #RRGGBBAA; only accept well-formed hex colors.
            return when {
                Regex("^#[0-9A-Fa-f]{6}$").matches(c) -> c.uppercase()
                Regex("^#[0-9A-Fa-f]{8}$").matches(c) -> c.substring(0, 7).uppercase()
                else -> COLORS.first()
            }
        }

        private fun slug(name: String): String =
            name.lowercase().map { if (it in 'a'..'z' || it in '0'..'9') it else '-' }
                .joinToString("").trim('-').replace(Regex("-+"), "-").take(30).ifEmpty { "liste" }

        /** Human-readable reason why the server could not be used. */
        fun explain(e: Exception?): String = when (e) {
            null -> "Server antwortet nicht als CalDAV-Server."
            is javax.net.ssl.SSLPeerUnverifiedException ->
                "Das Zertifikat passt nicht zur Domain."
            is javax.net.ssl.SSLHandshakeException, is java.security.cert.CertificateException ->
                "Android vertraut dem Zertifikat nicht – meist fehlt auf dem Server das Zwischenzertifikat " +
                    "(fullchain.pem statt cert.pem verwenden) oder es ist selbst signiert."
            is javax.net.ssl.SSLException -> "Sichere Verbindung fehlgeschlagen (${e.javaClass.simpleName})."
            is java.net.UnknownHostException -> "Domain nicht gefunden (DNS)."
            is java.net.SocketTimeoutException -> "Zeitüberschreitung – Server oder Firewall antwortet nicht."
            is java.net.ConnectException -> "Verbindung abgelehnt – Port 443 erreichbar?"
            is de.devsmoms.nctodo.dav.DavXmlException -> "Antwort ist kein gültiges WebDAV-XML (${e.message}${e.cause?.let { ": " + it.javaClass.simpleName + " " + it.message.orEmpty().take(80) } ?: ""})."
            is IllegalStateException -> "Interner Fehler: ${e.message}"
            is DavException -> if (e.code == 0) (e.message ?: "Unbekannte Antwort") else when (e.code) {
                403, 405 -> "Server oder vorgeschalteter Proxy blockiert WebDAV-Anfragen (HTTP ${e.code})."
                404 -> "WebDAV-Pfad nicht gefunden (HTTP 404)."
                in 500..599 -> "Serverfehler (HTTP ${e.code})."
                else -> e.message ?: "HTTP ${e.code}"
            }
            else -> e.message ?: e.javaClass.simpleName
        }

        /**
         * Finds principal and calendar home for a login. Only HTTPS is accepted.
         * Tries the URL as entered, the Nextcloud DAV endpoint and /.well-known/caldav.
         */
        suspend fun discover(serverInput: String, username: String, password: String): Credentials {
            val input = serverInput.trim().let { if (!it.contains("://")) "https://$it" else it }
            val url = input.toHttpUrlOrNull() ?: throw DavException("Ungültige Serveradresse")
            if (!url.isHttps) throw DavException("Aus Sicherheitsgründen ist nur HTTPS erlaubt")
            val origin = "https://${url.host}" + if (url.port != 443) ":${url.port}" else ""
            val client = DavClient(origin, username, password)

            val base = url.toString().trimEnd('/')
            val candidates = buildList {
                if (url.encodedPath.length > 1) add("$base/")
                val ncRoot = if (base.contains("/remote.php")) base.substringBefore("/remote.php") else base
                add("$ncRoot/remote.php/dav/")
                add("$origin/.well-known/caldav")
                add("$origin/remote.php/dav/")
            }.distinct()

            var principal: String? = null
            val attempts = mutableListOf<String>()
            for (candidate in candidates) {
                val path = candidate.substringAfter(url.host).ifEmpty { "/" }
                try {
                    val res = client.propfind(candidate, 0, DavXml.PROPFIND_PRINCIPAL)
                    val responses = DavXml.parseMultistatus(res.body, candidate)
                    val prop = responses.firstNotNullOfOrNull { it.prop(Ns.DAV, "current-user-principal") }
                    val href = DavXml.hrefIn(prop)
                    if (href != null) {
                        principal = DavXml.resolve(candidate, href)
                        break
                    }
                    attempts += path + ": " + when {
                        prop?.getElementsByTagNameNS(Ns.DAV, "unauthenticated")?.length?.let { it > 0 } == true ->
                            "Anmeldung nicht akzeptiert (unauthenticated)"
                        responses.isEmpty() -> "leere WebDAV-Antwort"
                        else -> "kein Benutzer-Principal"
                    }
                } catch (e: de.devsmoms.nctodo.dav.AuthException) {
                    throw e
                } catch (e: Exception) {
                    attempts += "$path: ${explain(e)}"
                }
            }
            val principalUrl = principal
                ?: throw DavException("Kein CalDAV-Server gefunden.\n" + attempts.joinToString("\n") { "• $it" })
            if (!client.isSameOrigin(principalUrl)) throw DavException("Server verweist auf fremde Adresse")

            val res = client.propfind(principalUrl, 0, DavXml.PROPFIND_HOME)
            val r = DavXml.parseMultistatus(res.body, principalUrl).firstOrNull()
                ?: throw DavException("Keine Kalender gefunden")
            val home = DavXml.hrefIn(r.prop(Ns.CALDAV, "calendar-home-set"))?.let { DavXml.resolve(principalUrl, it) }
                ?: throw DavException("Kein Kalender-Verzeichnis gefunden")
            if (!client.isSameOrigin(home)) throw DavException("Server verweist auf fremde Adresse")

            return Credentials(
                serverUrl = origin,
                username = username,
                password = password,
                principalUrl = principalUrl,
                calendarHomeUrl = home,
                displayName = r.text(Ns.DAV, "displayname") ?: username,
            )
        }
    }
}
