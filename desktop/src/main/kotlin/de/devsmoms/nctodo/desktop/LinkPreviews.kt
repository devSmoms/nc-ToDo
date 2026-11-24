package de.devsmoms.nctodo.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import de.devsmoms.nctodo.ical.TodoMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Image
import java.util.Collections
import java.util.concurrent.TimeUnit

/** Title, website and picture of a link. */
data class LinkInfo(val title: String?, val site: String, val image: ImageBitmap?)

/**
 * Loads link previews with its own HTTP client: no Nextcloud credentials, HTTPS only,
 * size limited. Same approach as the Android app.
 */
object LinkPreviews {
    private const val MAX_HTML = 256L * 1024
    private const val MAX_IMAGE = 2L * 1024 * 1024

    private val cache: MutableMap<String, LinkInfo> = Collections.synchronizedMap(
        object : LinkedHashMap<String, LinkInfo>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LinkInfo>?) = size > 64
        },
    )

    private val http = OkHttpClient.Builder()
        .connectionSpecs(listOf(ConnectionSpec.RESTRICTED_TLS, ConnectionSpec.MODERN_TLS))
        .followSslRedirects(false)
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    fun clear() = cache.clear()

    fun siteOf(url: String): String = url.toHttpUrlOrNull()?.host?.removePrefix("www.") ?: url

    suspend fun load(url: String): LinkInfo = withContext(Dispatchers.IO) {
        cache[url] ?: fetch(url).also { cache[url] = it }
    }

    private fun fetch(url: String): LinkInfo {
        val plain = LinkInfo(null, siteOf(url), null)
        val parsed = url.toHttpUrlOrNull()?.takeIf { it.isHttps } ?: return plain
        return runCatching {
            val request = Request.Builder().url(parsed)
                .header("Accept", "text/html,image/*;q=0.8")
                .header("User-Agent", "Mozilla/5.0 (Desktop; NC Todo link preview)")
                .build()
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return plain
                val type = resp.header("Content-Type").orEmpty().lowercase()
                val finalUrl = resp.request.url
                if (type.startsWith("image/")) {
                    val bytes = resp.body?.source()?.let { src -> if (src.request(MAX_IMAGE + 1)) null else src.readByteArray() }
                    return LinkInfo(null, siteOf(url), bytes?.let(::decode))
                }
                if (!type.contains("html")) return plain
                val source = resp.body?.source() ?: return plain
                source.request(MAX_HTML)
                val html = source.buffer.snapshot().utf8().take(MAX_HTML.toInt())
                val title = meta(html, "og:title") ?: Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    .find(html)?.groupValues?.get(1)
                val site = meta(html, "og:site_name") ?: siteOf(url)
                val imageUrl = (meta(html, "og:image") ?: iconHref(html) ?: "/favicon.ico")
                    .let { finalUrl.resolve(unescape(it))?.takeIf { u -> u.isHttps } }
                LinkInfo(
                    title?.let { unescape(it).trim().take(150) }?.takeIf { it.isNotBlank() },
                    unescape(site).take(60),
                    imageUrl?.let { loadImage(it.toString()) },
                )
            }
        }.getOrDefault(plain)
    }

    private fun loadImage(url: String): ImageBitmap? = runCatching {
        http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val src = resp.body?.source() ?: return null
            if (src.request(MAX_IMAGE + 1)) return null // too large
            decode(src.readByteArray())
        }
    }.getOrNull()

    private fun decode(bytes: ByteArray): ImageBitmap? = runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

    private fun meta(html: String, property: String): String? {
        val p = Regex.escape(property)
        val a = Regex("<meta[^>]+(?:property|name)=[\"']$p[\"'][^>]*content=[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
        val b = Regex("<meta[^>]+content=[\"']([^\"']*)[\"'][^>]*(?:property|name)=[\"']$p[\"']", RegexOption.IGNORE_CASE)
        return (a.find(html) ?: b.find(html))?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    }

    private fun iconHref(html: String): String? =
        Regex("<link[^>]+rel=[\"'](?:apple-touch-icon|icon|shortcut icon)[\"'][^>]*href=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)

    private fun unescape(s: String): String = s
        .replace(Regex("&#(\\d{1,6});")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it.coerceIn(32, 0x10FFFF))) } ?: "" }
        .replace(Regex("&#x([0-9a-fA-F]{1,6});")) { m -> m.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it.coerceIn(32, 0x10FFFF))) } ?: "" }
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").replace("&amp;", "&")

    /** Opens a link in the browser – only http(s). */
    fun open(url: String) {
        if (TodoMapper.isWebLink(url)) AppState.openInBrowser(url)
    }
}
