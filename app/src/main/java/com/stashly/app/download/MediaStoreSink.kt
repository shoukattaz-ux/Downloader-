package com.stashly.app.download

import android.content.ContentResolver
import android.net.Uri
import java.io.OutputStream

/** Streams straight into a pending MediaStore entry, so there's no second copy of a large file. */
class MediaStoreSink(private val resolver: ContentResolver, private val uri: Uri) : DownloadSink {
    override fun length(): Long = try {
        resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
    } catch (_: Exception) { 0L }

    override fun openOutput(append: Boolean): OutputStream =
        resolver.openOutputStream(uri, if (append) "wa" else "wt")
            ?: throw java.io.IOException("Cannot open output stream for $uri")
}
