package com.stashly.app.core

import com.stashly.app.domain.model.MediaKind

/**
 * Allow-list based file safety rules. Anything not listed is rejected: executables, scripts,
 * installers, archives and HTML (which could be a phishing or script payload) are never saved.
 */
object FileRules {
    data class Resolved(val extension: String, val mimeType: String, val kind: MediaKind)

    private val table: Map<String, Pair<String, MediaKind>> = buildMap {
        for (e in listOf("mp4" to "video/mp4", "m4v" to "video/x-m4v", "webm" to "video/webm", "mkv" to "video/x-matroska",
            "mov" to "video/quicktime", "3gp" to "video/3gpp", "ogv" to "video/ogg"))
            put(e.first, e.second to MediaKind.VIDEO)
        for (e in listOf("mp3" to "audio/mpeg", "m4a" to "audio/mp4", "aac" to "audio/aac", "ogg" to "audio/ogg",
            "oga" to "audio/ogg", "opus" to "audio/ogg", "wav" to "audio/wav", "flac" to "audio/flac"))
            put(e.first, e.second to MediaKind.AUDIO)
        for (e in listOf("jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "gif" to "image/gif", "webp" to "image/webp"))
            put(e.first, e.second to MediaKind.IMAGE)
        for (e in listOf("pdf" to "application/pdf", "txt" to "text/plain", "epub" to "application/epub+zip",
            "csv" to "text/csv", "rtf" to "application/rtf",
            "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "odt" to "application/vnd.oasis.opendocument.text"))
            put(e.first, e.second to MediaKind.DOCUMENT)
    }

    private val blocked = setOf(
        "apk", "xapk", "aab", "exe", "msi", "bat", "cmd", "com", "scr", "sh", "bash", "js", "jar", "dex", "so",
        "vbs", "ps1", "html", "htm", "xhtml", "svg", "php", "py", "dll", "bin", "dmg", "iso", "lnk", "zip", "rar", "7z",
    )

    private val mimeToExt: Map<String, String> = buildMap {
        // first entry wins for duplicates, which keeps canonical extensions
        table.forEach { (ext, v) -> putIfAbsent(v.first, ext) }
        put("audio/mp3", "mp3"); put("audio/x-m4a", "m4a"); put("video/mpeg", "mp4")
    }

    fun extensionOf(name: String): String? =
        name.substringAfterLast('.', "").lowercase().takeIf { it.isNotEmpty() && it.length <= 5 && !name.endsWith(".") }

    fun isBlocked(ext: String?) = ext != null && ext in blocked
    fun classify(ext: String?): Resolved? = ext?.let { e -> table[e]?.let { Resolved(e, it.first, it.second) } }
    fun kindOfMime(mime: String?): MediaKind? = when {
        mime == null -> null
        mime.startsWith("video/") -> MediaKind.VIDEO
        mime.startsWith("audio/") -> MediaKind.AUDIO
        mime.startsWith("image/") -> MediaKind.IMAGE
        else -> null
    }

    /**
     * Decides whether a file with [fileName] served as [contentType] may be downloaded.
     * Throws [AppError] with a user readable reason otherwise.
     */
    fun resolve(fileName: String?, contentType: String?): Resolved {
        val mime = contentType?.substringBefore(';')?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val ext = fileName?.let(::extensionOf)
        if (isBlocked(ext)) throw AppError(ErrorCode.UNSAFE_FILE,
            "Files of type .$ext can't be downloaded for your safety.")
        val isWebPage = mime != null && (mime == "text/html" || mime == "application/xhtml+xml" ||
            mime == "application/json" || mime == "application/javascript" || mime == "text/javascript" ||
            (mime.startsWith("text/") && ext != "txt" && ext != "csv"))
        if (isWebPage) throw AppError(ErrorCode.NO_MEDIA,
            "This link opens a web page, not a downloadable media file.")
        if (mime in setOf("application/x-msdownload", "application/vnd.android.package-archive", "application/x-sh",
                "application/x-dosexec", "application/java-archive"))
            throw AppError(ErrorCode.UNSAFE_FILE, "This looks like an app or program file, which can't be downloaded.")

        val byExt = classify(ext)
        if (byExt != null) {
            val mimeKind = kindOfMime(mime)
            if (mimeKind != null && mimeKind != byExt.kind)
                throw AppError(ErrorCode.UNSAFE_FILE, "The file type doesn't match its name, so it was blocked.")
            return byExt
        }
        val fromMime = mime?.let { mimeToExt[it] }?.let(::classify)
        if (fromMime != null && (ext == null || ext !in blocked)) return fromMime
        throw AppError(ErrorCode.UNSUPPORTED_FORMAT, "This file type isn't supported.")
    }

    /** Verifies magic bytes for common formats. Returns true if consistent or not verifiable. */
    fun signatureMatches(head: ByteArray, ext: String): Boolean {
        fun at(off: Int, s: String) = head.size >= off + s.length &&
            s.indices.all { head[off + it] == s[it].code.toByte() }
        fun b(vararg v: Int) = head.size >= v.size && v.indices.all { (head[it].toInt() and 0xFF) == v[it] }
        return when (ext) {
            "mp4", "m4v", "m4a", "mov", "3gp" -> at(4, "ftyp") || at(4, "moov") || at(4, "mdat") || at(4, "free") || at(4, "wide")
            "webm", "mkv" -> b(0x1A, 0x45, 0xDF, 0xA3)
            "mp3" -> at(0, "ID3") || (head.size >= 2 && (head[0].toInt() and 0xFF) == 0xFF && (head[1].toInt() and 0xE0) == 0xE0)
            "ogg", "oga", "ogv", "opus" -> at(0, "OggS")
            "wav" -> at(0, "RIFF") && at(8, "WAVE")
            "flac" -> at(0, "fLaC")
            "aac" -> (head.size >= 2 && (head[0].toInt() and 0xFF) == 0xFF && (head[1].toInt() and 0xF0) == 0xF0) || at(0, "ID3")
            "png" -> b(0x89, 0x50, 0x4E, 0x47)
            "jpg", "jpeg" -> b(0xFF, 0xD8, 0xFF)
            "gif" -> at(0, "GIF8")
            "webp" -> at(0, "RIFF") && at(8, "WEBP")
            "pdf" -> at(0, "%PDF") || head.indexOfSubsequence("%PDF") in 0..1024
            "docx", "xlsx", "pptx", "odt", "epub" -> at(0, "PK")
            else -> true
        }
    }

    private fun ByteArray.indexOfSubsequence(s: String): Int {
        outer@ for (i in 0..size - s.length) {
            for (j in s.indices) if (this[i + j] != s[j].code.toByte()) continue@outer
            return i
        }
        return -1
    }

    fun sanitizeFileName(name: String, fallback: String = "download"): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ")
            .replace(Regex("\\s+"), " ").trim().trimStart('.').trim()
        if (cleaned.isEmpty()) return fallback
        if (cleaned.length <= 120) return cleaned
        val ext = extensionOf(cleaned)
        return if (ext != null) cleaned.substring(0, 119 - ext.length).trimEnd() + "." + ext else cleaned.take(120)
    }
}
