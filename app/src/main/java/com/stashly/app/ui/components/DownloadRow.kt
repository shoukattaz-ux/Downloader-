package com.stashly.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.stashly.app.core.Formatters
import com.stashly.app.data.db.DownloadEntity
import com.stashly.app.domain.model.DownloadStatus
import com.stashly.app.domain.model.MediaKind

data class DownloadActions(
    val onPause: (DownloadEntity) -> Unit,
    val onResume: (DownloadEntity) -> Unit,
    val onCancel: (DownloadEntity) -> Unit,
    val onRetry: (DownloadEntity) -> Unit,
    val onRemove: (DownloadEntity) -> Unit,
    val onOpen: (DownloadEntity) -> Unit,
    val onShare: (DownloadEntity) -> Unit,
)

@Composable
fun DownloadRow(item: DownloadEntity, fileMissing: Boolean, actions: DownloadActions, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    val completed = item.status == DownloadStatus.COMPLETED
    Column(modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                if (item.thumbnailUrl != null && item.kind != MediaKind.AUDIO.name)
                    AsyncImage(item.thumbnailUrl, null, Modifier.size(56.dp), contentScale = ContentScale.Crop)
                else Icon(when (MediaKind.valueOf(item.kind)) {
                    MediaKind.VIDEO -> Icons.Rounded.Videocam; MediaKind.AUDIO -> Icons.Rounded.MusicNote
                    MediaKind.IMAGE -> Icons.Rounded.Image; MediaKind.DOCUMENT -> Icons.Rounded.Description
                }, null, tint = cs.primary)
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text("${item.format} · ${item.quality} · ${item.sourceName}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                val (text, isError) = statusText(item, fileMissing)
                Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = if (isError) cs.error else cs.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                DropdownMenu(menu, { menu = false }) {
                    if (completed && !fileMissing) {
                        DropdownMenuItem({ Text("Open") }, { menu = false; actions.onOpen(item) }, leadingIcon = { Icon(Icons.Rounded.FolderOpen, null) })
                        DropdownMenuItem({ Text("Share") }, { menu = false; actions.onShare(item) }, leadingIcon = { Icon(Icons.Rounded.Share, null) })
                    }
                    if (item.status.isActive || item.status == DownloadStatus.PAUSED)
                        DropdownMenuItem({ Text("Cancel download") }, { menu = false; actions.onCancel(item) }, leadingIcon = { Icon(Icons.Rounded.Close, null) })
                    DropdownMenuItem({ Text("Remove from history") }, { menu = false; actions.onRemove(item) }, leadingIcon = { Icon(Icons.Rounded.Delete, null) })
                }
            }
        }
        if (item.status.isActive || item.status == DownloadStatus.PAUSED) {
            Spacer(Modifier.height(8.dp))
            if (item.totalBytes > 0 && item.status != DownloadStatus.QUEUED || item.downloadedBytes > 0 && item.totalBytes > 0)
                LinearProgressIndicator(progress = { item.progressPercent / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
            else LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            when {
                item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.QUEUED -> {
                    TextButton({ actions.onPause(item) }) { Icon(Icons.Rounded.Pause, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Pause") }
                    TextButton({ actions.onCancel(item) }) { Text("Cancel") }
                }
                item.status == DownloadStatus.PAUSED -> {
                    TextButton({ actions.onResume(item) }) { Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Resume") }
                    TextButton({ actions.onCancel(item) }) { Text("Cancel") }
                }
                item.status == DownloadStatus.FAILED || item.status == DownloadStatus.CANCELED || (completed && fileMissing) ->
                    TextButton({ actions.onRetry(item) }) { Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text(if (completed) "Download again" else "Retry") }
                completed -> {
                    TextButton({ actions.onShare(item) }) { Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Share") }
                    TextButton({ actions.onOpen(item) }) { Icon(Icons.Rounded.FolderOpen, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Open file") }
                }
            }
        }
    }
}

private fun statusText(e: DownloadEntity, fileMissing: Boolean): Pair<String, Boolean> = when (e.status) {
    DownloadStatus.QUEUED -> "Waiting to start…" to false
    DownloadStatus.DOWNLOADING -> {
        val sizes = if (e.totalBytes > 0) "${Formatters.bytes(e.downloadedBytes)} / ${Formatters.bytes(e.totalBytes)}" else Formatters.bytes(e.downloadedBytes)
        val parts = listOfNotNull(
            if (e.totalBytes > 0) "${e.progressPercent}%" else null, sizes,
            Formatters.speed(e.speedBps).takeIf { e.speedBps > 0 }, Formatters.eta(e.etaSeconds))
        parts.joinToString(" · ") to false
    }
    DownloadStatus.PAUSED -> "Paused · ${Formatters.bytes(e.downloadedBytes)}${if (e.totalBytes > 0) " / ${Formatters.bytes(e.totalBytes)}" else ""}" to false
    DownloadStatus.COMPLETED ->
        if (fileMissing) "File was moved or deleted" to true else "Completed · ${Formatters.bytes(e.totalBytes)}" to false
    DownloadStatus.FAILED -> (e.errorMessage ?: "Download failed") to true
    DownloadStatus.CANCELED -> "Canceled" to false
}
