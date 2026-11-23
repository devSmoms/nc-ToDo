package de.devsmoms.nctodo.dav

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class DavResult(val code: Int, val body: String, val etag: String?)

/**
 * Thin WebDAV/CalDAV HTTP client.
 *
 * Security properties:
 *  - HTTPS only (TLS 1.2+), no cleartext, no HTTPS -> HTTP redirects.
 *  - The Authorization header is only attached to requests for the exact origin the user
 *    logged in to, so a malicious href or redirect can never leak the password elsewhere.
 *  - Response bodies are size limited.
 *  - Nothing is logged.
 */
class DavClient(origin: String, username: String, password: String) {
    private val originUrl: HttpUrl = origin.toHttpUrlOrNull()?.takeIf { it.isHttps }
        ?: throw IllegalArgumentException("Only https:// URLs are allowed")

    private val authHeader = Credentials.basic(username, password, Charsets.UTF_8)

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectionSpecs(listOf(ConnectionSpec.RESTRICTED_TLS, ConnectionSpec.MODERN_TLS))
        .followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(Interceptor { chain ->
            val req = chain.request()
            val sameOrigin = req.url.isHttps && req.url.host == originUrl.host && req.url.port == originUrl.port
            if (!req.url.isHttps) throw IOException("Refusing non-HTTPS request")
            chain.proceed(
                req.newBuilder()
                    .header("User-Agent", "devsmoms-NC-Todo-Android")
                    .apply { if (sameOrigin) header("Authorization", authHeader) }
                    .build(),
            )
        })
        .build()

    fun isSameOrigin(url: String): Boolean {
        val u = url.toHttpUrlOrNull() ?: return false
        return u.isHttps && u.host == originUrl.host && u.port == originUrl.port
    }

    suspend fun propfind(url: String, depth: Int, body: String) = xml("PROPFIND", url, body, depth)
    suspend fun report(url: String, depth: Int, body: String) = xml("REPORT", url, body, depth)
    suspend fun proppatch(url: String, body: String) = xml("PROPPATCH", url, body, null)
    suspend fun mkcol(url: String, body: String) = xml("MKCOL", url, body, null)
    suspend fun post(url: String, body: String) = xml("POST", url, body, null)

    suspend fun put(url: String, ics: String, ifMatch: String?, ifNoneMatch: Boolean): DavResult =
        execute(
            Request.Builder().url(checked(url))
                .put(ics.toRequestBody(ICS))
                .apply {
                    if (ifMatch != null) header("If-Match", ifMatch)
                    if (ifNoneMatch) header("If-None-Match", "*")
                }
                .build(),
        )

    /** WebDAV MOVE, used to restore items from the Nextcloud trash bin. Both URLs must be on the server. */
    suspend fun move(url: String, destination: String): DavResult {
        checked(destination)
        return execute(
            Request.Builder().url(checked(url))
                .method("MOVE", null)
                .header("Destination", destination)
                .header("Overwrite", "F")
                .build(),
        )
    }

    suspend fun get(url: String): DavResult =
        execute(Request.Builder().url(checked(url)).get().header("Accept", "text/calendar").build())

    suspend fun delete(url: String, ifMatch: String?): DavResult =
        execute(Request.Builder().url(checked(url)).delete().apply { if (ifMatch != null) header("If-Match", ifMatch) }.build())

    private suspend fun xml(method: String, url: String, body: String, depth: Int?): DavResult =
        execute(
            Request.Builder().url(checked(url))
                .method(method, body.toRequestBody(XML))
                .apply { if (depth != null) header("Depth", depth.toString()) }
                .build(),
        )

    /** Only URLs on the logged-in server are ever requested. */
    private fun checked(url: String): HttpUrl {
        val u = url.toHttpUrlOrNull() ?: throw DavException("Ungültige URL")
        if (!isSameOrigin(url)) throw DavException("Server verweist auf fremde Adresse – abgebrochen")
        return u
    }

    private suspend fun execute(request: Request): DavResult = withContext(Dispatchers.IO) {
        http.newCall(request).execute().use { resp ->
            val body = readLimited(resp)
            when {
                resp.code == 401 -> throw AuthException()
                resp.code == 412 -> throw PreconditionFailedException()
                resp.code == 404 || resp.code == 410 -> throw NotFoundException()
                resp.code in 200..299 -> {
                    checkNotHijacked(resp, body)
                    DavResult(resp.code, body, resp.header("ETag"))
                }
                else -> throw DavException(errorText(resp.code), resp.code)
            }
        }
    }

    /**
     * Detects answers that did not come from Nextcloud's WebDAV endpoint, e.g. a login page of a
     * forward-auth proxy (Authentik, Authelia …) or a captive portal, and explains it.
     */
    private fun checkNotHijacked(resp: Response, body: String) {
        val finalUrl = resp.request.url
        if (finalUrl.host != originUrl.host || finalUrl.port != originUrl.port) {
            throw DavException("Weitergeleitet zu ${finalUrl.host} – vermutlich eine vorgeschaltete Anmeldung (Forward-Auth/SSO) vor Nextcloud", 0)
        }
        val type = resp.header("Content-Type").orEmpty().lowercase()
        val expectsXml = request(resp).let { it == "PROPFIND" || it == "REPORT" || it == "PROPPATCH" }
        val looksHtml = type.startsWith("text/html") || body.trimStart().startsWith("<!DOCTYPE html", ignoreCase = true) ||
            body.trimStart().startsWith("<html", ignoreCase = true)
        if (expectsXml && looksHtml) {
            val title = Regex("<title>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(body)?.groupValues?.get(1)?.trim()?.take(80)
            throw DavException(
                "Server lieferte eine Webseite statt WebDAV-Daten (HTTP ${resp.code}, ${finalUrl.encodedPath}" +
                    (if (!title.isNullOrBlank()) ", Seite „$title“" else "") + ")",
                0,
            )
        }
    }

    private fun request(resp: Response) = resp.request.method

    private fun readLimited(resp: Response): String {
        val source = resp.body?.source() ?: return ""
        if (!source.request(MAX_BODY + 1)) return source.readUtf8()
        throw DavException("Antwort des Servers ist zu groß")
    }

    private fun errorText(code: Int) = when (code) {
        403 -> "Keine Berechtigung für diese Aktion"
        404 -> "Nicht gefunden"
        405 -> "Aktion vom Server nicht erlaubt"
        409 -> "Konflikt auf dem Server"
        423 -> "Ressource ist gesperrt"
        in 500..599 -> "Serverfehler ($code)"
        else -> "Unerwartete Serverantwort ($code)"
    }

    private companion object {
        const val MAX_BODY = 25L * 1024 * 1024
        val XML = "application/xml; charset=utf-8".toMediaType()
        val ICS = "text/calendar; charset=utf-8".toMediaType()
    }
}
