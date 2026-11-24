package de.devsmoms.nctodo.data

import de.devsmoms.nctodo.dav.DavException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Nextcloud Login Flow v2: the user signs in in the browser (incl. 2FA / SSO) and grants access;
 * Nextcloud then creates a dedicated app password for this device and hands it to the app.
 * The app never sees the account password.
 *
 * 1. POST <server>/index.php/login/v2 → login URL (opened in the browser) + poll endpoint/token
 * 2. POST token to the poll endpoint until it answers 200 with server, loginName, appPassword
 */
class LoginFlow private constructor(
    /** Page to open in the browser. */
    val loginUrl: String,
    private val pollUrl: String,
    private val token: String,
    private val base: String,
    private val userAgent: String,
) {
    data class Result(val server: String, val loginName: String, val appPassword: String)

    /** Waits until access was granted in the browser; null if the flow expired (20 min). */
    suspend fun await(): Result? = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            ensureActive()
            val request = Request.Builder().url(pollUrl).header("User-Agent", userAgent)
                .post(FormBody.Builder().add("token", token).build()).build()
            // 404 = not granted yet; network errors are retried (e.g. while switching apps).
            val result = runCatching {
                http.newCall(request).execute().use { res ->
                    if (res.code == 200) parseResult(res.body?.string().orEmpty()) else null
                }
            }.getOrElse { e -> if (e is DavException) throw e else null }
            if (result != null) return@withContext result
            delay(POLL_MS)
        }
        null
    }

    private fun parseResult(body: String): Result {
        val json = MiniJson.parseObject(body) ?: throw DavException("Ungültige Antwort des Servers")
        val server = MiniJson.string(json, "server").trimEnd('/')
        val login = MiniJson.string(json, "loginName")
        val password = MiniJson.string(json, "appPassword")
        if (login.isBlank() || password.isBlank()) throw DavException("Ungültige Antwort des Servers")
        // The credentials may only be used for the server the user entered.
        if (!LoginFlowUrls.sameServer(server, base)) {
            throw DavException("Der Server hat auf eine andere Adresse verwiesen ($server) – Anmeldung abgebrochen")
        }
        return Result(server, login, password)
    }

    companion object {
        private const val POLL_MS = 2_000L
        private const val TIMEOUT_MS = 20 * 60_000L

        private val http = OkHttpClient.Builder()
            .connectionSpecs(listOf(ConnectionSpec.RESTRICTED_TLS, ConnectionSpec.MODERN_TLS))
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        /**
         * Starts a login; throws [DavException] with a readable message on failure.
         * [deviceName] (e.g. phone model or computer name) becomes part of the app password's
         * name that Nextcloud shows under "Devices & sessions".
         */
        suspend fun start(serverInput: String, deviceName: String): LoginFlow = withContext(Dispatchers.IO) {
            val base = LoginFlowUrls.base(serverInput)
            val userAgent = "NC Todo ($deviceName)".take(100)
            val request = Request.Builder().url("$base/index.php/login/v2").header("User-Agent", userAgent)
                .post(ByteArray(0).toRequestBody()).build()
            val body = try {
                http.newCall(request).execute().use { res ->
                    when (res.code) {
                        200 -> res.body?.string().orEmpty()
                        404 -> throw DavException("Unter dieser Adresse wurde keine Nextcloud gefunden (Login im Browser braucht Nextcloud 16 oder neuer)")
                        in 300..399 -> throw DavException("Der Server leitet weiter – bitte die genaue Nextcloud-Adresse eingeben")
                        else -> throw DavException("Server antwortet mit Fehler ${res.code}")
                    }
                }
            } catch (e: DavException) {
                throw e
            } catch (e: Exception) {
                throw DavException("Server nicht erreichbar: ${e.message ?: e.javaClass.simpleName}")
            }
            val json = MiniJson.parseObject(body) ?: throw DavException("Unter dieser Adresse wurde keine Nextcloud gefunden")
            val poll = json["poll"] as? Map<*, *> ?: throw DavException("Ungültige Antwort des Servers")
            val login = MiniJson.string(json, "login")
            val endpoint = MiniJson.string(poll, "endpoint")
            val token = MiniJson.string(poll, "token")
            if (token.isBlank() || !LoginFlowUrls.sameServer(login, base) || !LoginFlowUrls.sameServer(endpoint, base)) {
                throw DavException("Ungültige Antwort des Servers")
            }
            LoginFlow(login, endpoint, token, base, userAgent)
        }
    }
}
