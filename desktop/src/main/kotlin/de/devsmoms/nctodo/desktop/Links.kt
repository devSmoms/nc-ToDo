package de.devsmoms.nctodo.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.devsmoms.nctodo.data.TaskItem
import de.devsmoms.nctodo.ical.TodoMapper

/** Links of a task as cards (title, website, picture) plus "Link hinzufügen". */
@Composable
fun LinksSection(state: AppState, item: TaskItem, writable: Boolean, accent: Color) {
    val links = item.todo.links
    var adding by remember(item.url) { mutableStateOf(false) }
    var removing by remember(item.url) { mutableStateOf<String?>(null) }
    if (links.isEmpty() && !writable) return

    Surface(shape = RoundedCornerShape(8.dp), tonalElevation = 1.dp) {
        Column {
            links.forEachIndexed { i, url ->
                if (i > 0) HorizontalDivider()
                LinkCard(
                    url = url,
                    preview = state.settings.linkPreviews,
                    onRemove = if (writable) ({
                        if (state.settings.confirmRemovals) removing = url else state.removeLink(item, url)
                    }) else null,
                )
            }
            if (writable) {
                if (links.isNotEmpty()) HorizontalDivider()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { adding = true }.heightIn(min = 48.dp).padding(horizontal = 16.dp),
                ) {
                    Icon(Icons.Outlined.Link, null, tint = accent)
                    Spacer(Modifier.width(16.dp))
                    Text("Link hinzufügen", color = accent)
                }
            }
        }
    }

    if (adding) {
        NameDialog(
            title = "Link hinzufügen",
            initial = "",
            confirm = "Hinzufügen",
            label = "Adresse (https://…)",
            hint = "Der Link wird in der Aufgabe gespeichert und ist auch in der Android-App sichtbar.",
            maxLength = 2000,
            onDismiss = { adding = false },
        ) { url ->
            adding = false
            state.addLink(item, if ("://" in url) url else "https://$url")
        }
    }
    removing?.let { url ->
        ConfirmDialog(
            title = "Link entfernen?",
            text = url,
            confirm = "Entfernen",
            onDismiss = { removing = null },
        ) {
            removing = null
            state.removeLink(item, url)
        }
    }
}

@Composable
private fun LinkCard(url: String, preview: Boolean, onRemove: (() -> Unit)?) {
    var info by remember(url, preview) { mutableStateOf(LinkInfo(null, LinkPreviews.siteOf(url), null)) }
    LaunchedEffect(url, preview) { if (preview) info = LinkPreviews.load(url) }
    val web = TodoMapper.isWebLink(url)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .clickable(enabled = web) { LinkPreviews.open(url) }
            .pointerHoverIcon(if (web) PointerIcon.Hand else PointerIcon.Default)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            val img = info.image
            if (img != null) {
                Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp))
            } else {
                Icon(Icons.Outlined.Link, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                info.title ?: url, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                info.site, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (web) Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Im Browser öffnen", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onRemove != null) {
            IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, "Link entfernen", Modifier.size(18.dp)) }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}
