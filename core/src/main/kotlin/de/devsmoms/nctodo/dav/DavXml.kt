package de.devsmoms.nctodo.dav

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.StringReader
import java.net.URI
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

object Ns {
    const val DAV = "DAV:"
    const val CALDAV = "urn:ietf:params:xml:ns:caldav"
    const val CS = "http://calendarserver.org/ns/"
    const val APPLE = "http://apple.com/ns/ical/"
    const val OC = "http://owncloud.org/ns"
    const val NC = "http://nextcloud.com/ns"
    const val SABRE = "http://sabredav.org/ns"
    /** Own namespace for dead properties NC Todo stores on calendars (e.g. the list group). */
    const val NCTODO = "http://devsmoms.de/ns/nctodo"
}

class DavResponse(
    /** Absolute URL, resolved against the request URL. */
    val url: String,
    /** Properties reported with a 2xx status, keyed by "{namespace}localName". */
    val props: Map<String, Element>,
    val status: Int?,
) {
    fun prop(ns: String, name: String): Element? = props["{$ns}$name"]
    fun text(ns: String, name: String): String? = prop(ns, name)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
}

/** A user or group a calendar is shared with (Nextcloud "oc:invite"). */
data class ShareEntry(
    /** e.g. "principal:principals/users/bob" */
    val principal: String,
    val displayName: String,
    val canWrite: Boolean,
)

class DavXmlException(message: String, cause: Throwable? = null) : Exception(message, cause)

object DavXml {
    private const val MAX_XML_CHARS = 20_000_000

    // ---------------------------------------------------------------- parsing

    private fun secureFactory(): DocumentBuilderFactory {
        val f = DocumentBuilderFactory.newInstance()
        f.isNamespaceAware = true
        // Note: setXIncludeAware() is not called on purpose – Android's implementation throws
        // UnsupportedOperationException for it, and XInclude is off by default anyway.
        runCatching { f.isExpandEntityReferences = false }
        // Harden against XXE / entity expansion. Not every parser supports every feature,
        // so each one is set individually; DOCTYPEs are additionally rejected up front.
        listOf(
            "http://apache.org/xml/features/disallow-doctype-decl" to true,
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false,
            "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
        ).forEach { (feature, value) -> runCatching { f.setFeature(feature, value) } }
        runCatching { f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        return f
    }

    fun parseDocument(xml: String): Element {
        if (xml.length > MAX_XML_CHARS) throw DavXmlException("Response too large")
        if (xml.contains("<!DOCTYPE", ignoreCase = true) || xml.contains("<!ENTITY", ignoreCase = true)) {
            throw DavXmlException("DOCTYPE/ENTITY declarations are not accepted")
        }
        val builder = try {
            secureFactory().newDocumentBuilder()
        } catch (e: Exception) {
            // A local parser problem must not be reported as a server problem.
            throw IllegalStateException("XML parser unavailable: ${e.javaClass.simpleName}", e)
        }
        return try {
            builder.parse(InputSource(StringReader(xml))).documentElement
        } catch (e: Exception) {
            throw DavXmlException("Invalid XML from server", e)
        }
    }

    fun parseMultistatus(xml: String, requestUrl: String): List<DavResponse> {
        val root = parseDocument(xml)
        if (!root.isA(Ns.DAV, "multistatus")) throw DavXmlException("Expected DAV:multistatus")
        return root.childElements(Ns.DAV, "response").mapNotNull { resp ->
            val href = resp.childElements(Ns.DAV, "href").firstOrNull()?.textContent?.trim() ?: return@mapNotNull null
            val url = resolve(requestUrl, href) ?: return@mapNotNull null
            val props = LinkedHashMap<String, Element>()
            var status: Int? = parseStatus(resp.childElements(Ns.DAV, "status").firstOrNull()?.textContent)
            for (ps in resp.childElements(Ns.DAV, "propstat")) {
                val code = parseStatus(ps.childElements(Ns.DAV, "status").firstOrNull()?.textContent)
                if (code == null || code !in 200..299) continue
                status = code
                ps.childElements(Ns.DAV, "prop").forEach { prop ->
                    prop.childElements().forEach { props["{${it.namespaceURI.orEmpty()}}${it.localName}"] = it }
                }
            }
            DavResponse(url, props, status)
        }
    }

    private fun parseStatus(s: String?): Int? = s?.trim()?.split(' ')?.getOrNull(1)?.toIntOrNull()

    fun resolve(base: String, href: String): String? = try {
        URI(base).resolve(URI(href.replace(" ", "%20"))).normalize().toString()
    } catch (_: Exception) {
        null
    }

    fun Element.isA(ns: String, local: String) = namespaceURI == ns && localName == local

    fun Element.childElements(): List<Element> {
        val out = ArrayList<Element>()
        var n = firstChild
        while (n != null) {
            if (n.nodeType == Node.ELEMENT_NODE) out.add(n as Element)
            n = n.nextSibling
        }
        return out
    }

    fun Element.childElements(ns: String, local: String) = childElements().filter { it.isA(ns, local) }

    fun hrefIn(prop: Element?): String? = prop?.childElements(Ns.DAV, "href")?.firstOrNull()?.textContent?.trim()

    fun isCalendar(r: DavResponse): Boolean =
        r.prop(Ns.DAV, "resourcetype")?.childElements(Ns.CALDAV, "calendar")?.isNotEmpty() == true

    /** Null if the server did not report the property (then any component may be stored). */
    fun supportedComponents(r: DavResponse): Set<String>? =
        r.prop(Ns.CALDAV, "supported-calendar-component-set")
            ?.childElements(Ns.CALDAV, "comp")
            ?.map { it.getAttribute("name").uppercase() }
            ?.toSet()

    /** Null if not reported. */
    fun privileges(r: DavResponse): Set<String>? =
        r.prop(Ns.DAV, "current-user-privilege-set")
            ?.childElements(Ns.DAV, "privilege")
            ?.flatMap { p -> p.childElements().map { it.localName } }
            ?.toSet()

    fun shares(r: DavResponse): List<ShareEntry> {
        val invite = r.prop(Ns.OC, "invite") ?: return emptyList()
        return invite.childElements(Ns.OC, "user").mapNotNull { u ->
            val href = u.childElements(Ns.DAV, "href").firstOrNull()?.textContent?.trim() ?: return@mapNotNull null
            val name = u.childElements(Ns.OC, "common-name").firstOrNull()?.textContent?.trim()
            val access = u.childElements(Ns.OC, "access").firstOrNull()
            val write = access?.childElements(Ns.OC, "read-write")?.isNotEmpty() == true
            ShareEntry(href, name?.takeIf { it.isNotEmpty() } ?: href.substringAfterLast('/'), write)
        }
    }

    // ---------------------------------------------------------------- building

    fun esc(s: String): String = buildString(s.length) {
        for (ch in s) when {
            ch == '&' -> append("&amp;")
            ch == '<' -> append("&lt;")
            ch == '>' -> append("&gt;")
            ch == '"' -> append("&quot;")
            ch == '\'' -> append("&apos;")
            // Strip characters that are illegal in XML 1.0.
            ch < ' ' && ch != '\t' && ch != '\n' && ch != '\r' -> {}
            else -> append(ch)
        }
    }

    private const val HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"

    val PROPFIND_PRINCIPAL = HEADER + """
        <d:propfind xmlns:d="DAV:"><d:prop><d:current-user-principal/></d:prop></d:propfind>
    """.trimIndent()

    val PROPFIND_HOME = HEADER + """
        <d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
          <d:prop><c:calendar-home-set/><d:displayname/></d:prop>
        </d:propfind>
    """.trimIndent()

    val PROPFIND_CALENDARS = HEADER + """
        <d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav" xmlns:cs="http://calendarserver.org/ns/"
                    xmlns:a="http://apple.com/ns/ical/" xmlns:oc="http://owncloud.org/ns" xmlns:nc="http://nextcloud.com/ns"
                    xmlns:n="http://devsmoms.de/ns/nctodo">
          <d:prop>
            <d:resourcetype/><d:displayname/><cs:getctag/><a:calendar-color/>
            <c:supported-calendar-component-set/><d:current-user-privilege-set/><c:calendar-description/>
            <oc:invite/><oc:owner-principal/><nc:owner-displayname/><n:group/><n:order/>
          </d:prop>
        </d:propfind>
    """.trimIndent()

    val REPORT_TODOS = HEADER + """
        <c:calendar-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
          <d:prop><d:getetag/><c:calendar-data/></d:prop>
          <c:filter><c:comp-filter name="VCALENDAR"><c:comp-filter name="VTODO"/></c:comp-filter></c:filter>
        </c:calendar-query>
    """.trimIndent()

    fun mkcalendar(displayName: String, color: String, description: String = "") = HEADER + """
        <d:mkcol xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav" xmlns:a="http://apple.com/ns/ical/">
          <d:set><d:prop>
            <d:resourcetype><d:collection/><c:calendar/></d:resourcetype>
            <d:displayname>${esc(displayName)}</d:displayname>
            <a:calendar-color>${esc(color)}</a:calendar-color>${if (description.isNotBlank()) "\n            <c:calendar-description>${esc(description)}</c:calendar-description>" else ""}
            <c:supported-calendar-component-set><c:comp name="VTODO"/></c:supported-calendar-component-set>
          </d:prop></d:set>
        </d:mkcol>
    """.trimIndent()

    fun proppatch(displayName: String, color: String, description: String = "") = HEADER + """
        <d:propertyupdate xmlns:d="DAV:" xmlns:a="http://apple.com/ns/ical/" xmlns:c="urn:ietf:params:xml:ns:caldav">
          <d:set><d:prop>
            <d:displayname>${esc(displayName)}</d:displayname>
            <a:calendar-color>${esc(color)}</a:calendar-color>${if (description.isNotBlank()) "\n            <c:calendar-description>${esc(description)}</c:calendar-description>" else ""}
          </d:prop></d:set>${if (description.isBlank()) "\n          <d:remove><d:prop><c:calendar-description/></d:prop></d:remove>" else ""}
        </d:propertyupdate>
    """.trimIndent()

    /**
     * Sets group (null/blank removes it) and optionally the position of a list in the menu.
     * Stored by Nextcloud as dead properties, which other apps ignore.
     */
    fun setListMeta(group: String?, order: Int? = null): String {
        val set = buildString {
            if (!group.isNullOrBlank()) append("<n:group>${esc(group.trim())}</n:group>")
            if (order != null) append("<n:order>$order</n:order>")
        }
        return HEADER + buildString {
            append("<d:propertyupdate xmlns:d=\"DAV:\" xmlns:n=\"http://devsmoms.de/ns/nctodo\">")
            if (set.isNotEmpty()) append("<d:set><d:prop>").append(set).append("</d:prop></d:set>")
            if (group.isNullOrBlank()) append("<d:remove><d:prop><n:group/></d:prop></d:remove>")
            append("</d:propertyupdate>")
        }
    }

    /** Nextcloud trash bin: retention period and deleted calendars (lists). */
    val PROPFIND_TRASHBIN = HEADER + """
        <d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav" xmlns:nc="http://nextcloud.com/ns" xmlns:a="http://apple.com/ns/ical/">
          <d:prop>
            <d:resourcetype/><d:displayname/><a:calendar-color/><c:supported-calendar-component-set/>
            <nc:deleted-at/><nc:trash-bin-retention-duration/>
          </d:prop>
        </d:propfind>
    """.trimIndent()

    /** Deleted calendar objects in the Nextcloud trash bin. */
    val REPORT_TRASH_OBJECTS = HEADER + """
        <c:calendar-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav" xmlns:nc="http://nextcloud.com/ns">
          <d:prop><d:getetag/><c:calendar-data/><nc:calendar-uri/><nc:deleted-at/></d:prop>
          <c:filter><c:comp-filter name="VCALENDAR"><c:comp-filter name="VTODO"/></c:comp-filter></c:filter>
        </c:calendar-query>
    """.trimIndent()

    val PROPFIND_USER = HEADER + """
        <d:propfind xmlns:d="DAV:" xmlns:s="http://sabredav.org/ns"><d:prop><d:displayname/><s:email-address/></d:prop></d:propfind>
    """.trimIndent()

    /** Nextcloud/ownCloud calendar sharing (POST to the calendar collection). */
    fun share(principal: String, canWrite: Boolean) = HEADER + """
        <oc:share xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
          <oc:set><d:href>${esc(principal)}</d:href>${if (canWrite) "<oc:read-write/>" else ""}</oc:set>
        </oc:share>
    """.trimIndent()

    fun unshare(principal: String) = HEADER + """
        <oc:share xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
          <oc:remove><d:href>${esc(principal)}</d:href></oc:remove>
        </oc:share>
    """.trimIndent()

    fun principalSearch(term: String) = HEADER + """
        <d:principal-property-search xmlns:d="DAV:" xmlns:s="http://sabredav.org/ns" test="anyof">
          <d:property-search><d:prop><d:displayname/></d:prop><d:match>${esc(term)}</d:match></d:property-search>
          <d:property-search><d:prop><s:email-address/></d:prop><d:match>${esc(term)}</d:match></d:property-search>
          <d:prop><d:displayname/><s:email-address/></d:prop>
          <d:apply-to-principal-collection-set/>
        </d:principal-property-search>
    """.trimIndent()
}
