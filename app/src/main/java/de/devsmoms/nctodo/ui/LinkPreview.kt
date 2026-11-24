package de.devsmoms.nctodo.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.devsmoms.nctodo.ical.TodoMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class LinkInfo(val title: String?, val site: String, val image: ImageBitmap?)

/**
 * Loads title and image of a web page for the link cards.
 *
 * Privacy/security: uses its own HTTP client without any Nextcloud credentials or cookies,
 * only for https links, never follows redirects to http, limits download sizes and can be
 * switched off in the settings.
 */
object LinkPreviews {
    private const val MAX_HTML = 512 * 1024L
    private const val MAX_IMAGE = 1024 * 1024L
    private val cache = LruCache<String, LinkInfo>(64)

    /** Forgets all previews (on logout). */
    fun clear() = cache.evictAll()

    private val http = OkHttpClient.Builder()
        .connectionSpecs(listOf(ConnectionSpec.RESTRICTED_TLS, ConnectionSpec.MODERN_TLS))
        .followSslRedirects(false)
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    fun siteOf(url: String): String = Uri.parse(url).host?.removePrefix("www.") ?: url

    suspend fun load(url: String): LinkInfo = withContext(Dispatchers.IO) {
        cache.get(url) ?: fetch(url).also { cache.put(url, it) }
    }

    private fun fetch(url: String): LinkInfo {
        val plain = LinkInfo(null, siteOf(url), null)
        val parsed = url.toHttpUrlOrNull()?.takeIf { it.isHttps } ?: return plain
        return runCatching {
            val request = Request.Builder().url(parsed)
                .header("Accept", "text/html,image/*;q=0.8")
                .header("User-Agent", "Mozilla/5.0 (Android; NC Todo link preview)")
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
                LinkInfo(title?.let { unescape(it).trim().take(150) }?.takeIf { it.isNotBlank() }, unescape(site).take(60), imageUrl?.let { loadImage(it.toString()) })
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

    /** Decodes downscaled to about 256 px to keep memory small. */
    private fun decode(bytes: ByteArray): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 256 && bounds.outHeight / (sample * 2) >= 256) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
    }

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
}

/** Opens a link in the browser – only http(s), so stored data can never start other apps' intents. */
fun openLink(context: Context, url: String) {
    if (!TodoMapper.isWebLink(url)) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No browser installed.
    }
}

/** Link shown as a preview card (image, title, website). */
@Composable
fun LinkCard(url: String, loadPreview: Boolean, onOpen: () -> Unit, onRemove: (() -> Unit)?) {
    val info by produceState<LinkInfo?>(initialValue = null, url, loadPreview) {
        value = if (loadPreview) LinkPreviews.load(url) else null
    }
    Surface(shape = RoundedCornerShape(8.dp), tonalElevation = 2.dp, onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                val img = info?.image
                if (img != null) Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp))
                else Icon(Icons.Outlined.Link, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(info?.title ?: url, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text(info?.site ?: LinkPreviews.siteOf(url), maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onRemove != null) IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, "Link entfernen") }
        }
    }
}
