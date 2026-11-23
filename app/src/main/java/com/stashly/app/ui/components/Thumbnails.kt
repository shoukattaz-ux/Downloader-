package com.stashly.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stashly.app.domain.model.FileCategory
import com.stashly.app.ui.theme.Brand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Decodes small previews straight from the content URI (no extra libraries). Work is limited to 2
 * concurrent jobs and results are cached, which keeps scrolling smooth on low-end devices.
 */
object ThumbnailLoader {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 12).toInt().coerceAtMost(24 * 1024 * 1024)) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val gate = Semaphore(2)

    suspend fun load(context: Context, uri: String, category: FileCategory, px: Int): Bitmap? {
        val key = "$uri@$px"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            gate.withPermit {
                val bmp = runCatching {
                    when (category) {
                        FileCategory.IMAGE -> decodeImage(context, Uri.parse(uri), px)
                        FileCategory.VIDEO -> videoFrame(context, Uri.parse(uri), px)
                        FileCategory.AUDIO -> albumArt(context, Uri.parse(uri), px)
                        FileCategory.DOCUMENT -> null
                    }
                }.getOrNull()
                bmp?.also { cache.put(key, it) }
            }
        }
    }

    private fun decodeImage(ctx: Context, uri: Uri, px: Int): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { decoder, info, _ ->
            val scale = maxOf(1, minOf(info.size.width, info.size.height) / px)
            decoder.setTargetSampleSize(scale)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

    private fun videoFrame(ctx: Context, uri: Uri, px: Int): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, uri)
            r.getScaledFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, px, px)
                ?: r.getFrameAtTime(0)
        } finally { runCatching { r.release() } }
    }

    private fun albumArt(ctx: Context, uri: Uri, px: Int): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, uri)
            r.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size, BitmapFactory.Options().apply { inSampleSize = 2 }) }
        } finally { runCatching { r.release() } }
    }
}

fun categoryColor(c: FileCategory): Color = when (c) {
    FileCategory.VIDEO -> Brand.Violet
    FileCategory.AUDIO -> Brand.Teal
    FileCategory.IMAGE -> Brand.Amber
    FileCategory.DOCUMENT -> Brand.Sky
}

/** A preview for a library file with a generated gradient placeholder (e.g. audio without artwork). */
@Composable
fun FileThumb(uri: String, category: FileCategory, name: String, size: Dp, modifier: Modifier = Modifier, corner: Dp = 14.dp) {
    val context = LocalContext.current
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() }.coerceAtLeast(64)
    val bitmap by produceState<Bitmap?>(null, uri, px) { value = ThumbnailLoader.load(context, uri, category, px) }
    val shape = RoundedCornerShape(corner)
    val base = categoryColor(category)
    val hue = Color(base.red * 0.85f + (name.hashCode() and 0xFF) / 2550f, base.green, base.blue)
    Box(
        modifier.size(size).clip(shape).background(Brush.linearGradient(listOf(base.copy(alpha = 0.95f), hue.copy(alpha = 0.55f)))),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) {
            Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (category == FileCategory.VIDEO)
                Box(Modifier.size(size * 0.38f).clip(RoundedCornerShape(50)).background(Color.Black.copy(0.45f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(size * 0.26f))
                }
        } else {
            Icon(
                when (category) {
                    FileCategory.VIDEO -> Icons.Rounded.PlayArrow
                    FileCategory.AUDIO -> Icons.Rounded.MusicNote
                    FileCategory.IMAGE -> Icons.Rounded.Image
                    FileCategory.DOCUMENT -> Icons.Rounded.Description
                }, null, tint = Color.White, modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}
