package de.devsmoms.nctodo.data

import de.devsmoms.nctodo.dav.DavException
import java.net.URI

/** URL handling for the browser login (pure Kotlin, unit tested). */
object LoginFlowUrls {

    /**
     * Nextcloud base URL from what the user typed: adds https://, rejects http and strips
     * known sub paths, e.g. "cloud.example.de/remote.php/dav" → "https://cloud.example.de".
     * Installations in a sub folder ("https://example.de/nextcloud") keep that folder.
     */
    fun base(input: String): String {
        val withScheme = input.trim().let { if (!it.contains("://")) "https://$it" else it }
        val uri = runCatching { URI(withScheme) }.getOrNull()
        if (uri?.host.isNullOrBlank()) throw DavException("Ungültige Serveradresse")
        if (!uri!!.scheme.equals("https", ignoreCase = true)) throw DavException("Aus Sicherheitsgründen ist nur HTTPS erlaubt")
        var path = uri.rawPath.orEmpty()
        for (marker in listOf("/remote.php", "/index.php", "/apps/", "/login", "/.well-known")) {
            val i = path.indexOf(marker)
            if (i >= 0) path = path.substring(0, i)
        }
        val port = if (uri.port == -1 || uri.port == 443) "" else ":${uri.port}"
        return "https://${uri.host.lowercase()}$port${path.trimEnd('/')}"
    }

    /** True if [url] is https on the same host and port as [base] (no redirects to other servers). */
    fun sameServer(url: String, base: String): Boolean {
        val a = runCatching { URI(url) }.getOrNull() ?: return false
        val b = runCatching { URI(base) }.getOrNull() ?: return false
        fun port(u: URI) = if (u.port == -1) 443 else u.port
        return a.scheme.equals("https", ignoreCase = true) &&
            a.host != null && a.host.equals(b.host, ignoreCase = true) &&
            port(a) == port(b)
    }
}
