package com.stashly.app.core

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Strict, side-effect free validation of user supplied links. */
object UrlRules {
    private val ipv4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    /**
     * Parses [raw] into a safe [HttpUrl] or throws [AppError]. Only https is accepted unless
     * [allowHttp] (used by tests). Credentials in the URL, local/private hosts are rejected.
     */
    fun parse(raw: String, allowHttp: Boolean = false): HttpUrl {
        var text = raw.trim()
        if (text.isEmpty()) throw AppError(ErrorCode.INVALID_URL, "Paste a link to get started.")
        if (text.any { it.isWhitespace() }) throw AppError(ErrorCode.INVALID_URL, "That doesn't look like a valid link.")
        if (!text.contains("://") && text.contains('.')) text = "https://$text"
        val url = text.toHttpUrlOrNull()
            ?: throw AppError(ErrorCode.INVALID_URL, "That doesn't look like a valid link.")
        if (url.scheme == "http" && !allowHttp)
            throw AppError(ErrorCode.INVALID_URL, "Only secure links (https://) are supported.")
        if (url.username.isNotEmpty() || url.password.isNotEmpty())
            throw AppError(ErrorCode.INVALID_URL, "Links containing a username or password aren't supported.")
        if (isLocalHost(url.host) && !allowHttp)
            throw AppError(ErrorCode.INVALID_URL, "Links to local or private network addresses aren't supported.")
        return url
    }

    fun isLocalHost(host: String): Boolean {
        val h = host.lowercase().trim('[', ']')
        if (h == "localhost" || h.endsWith(".localhost") || h.endsWith(".local") || h.endsWith(".internal")) return true
        if (h == "::1" || h == "::" || h.startsWith("fc") && h.contains(':') || h.startsWith("fd") && h.contains(':') || h.startsWith("fe80")) return true
        if (ipv4.matches(h)) {
            val p = h.split('.').map { it.toInt() }
            return p[0] == 10 || p[0] == 127 || p[0] == 0 ||
                (p[0] == 172 && p[1] in 16..31) || (p[0] == 192 && p[1] == 168) || (p[0] == 169 && p[1] == 254)
        }
        return false
    }

    fun hostMatches(host: String, domain: String): Boolean =
        host.equals(domain, true) || host.endsWith(".$domain", true)

    fun fileNameFromPath(url: HttpUrl): String? =
        url.pathSegments.lastOrNull()?.takeIf { it.isNotBlank() }?.let {
            runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it)
        }
}
