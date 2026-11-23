package com.stashly.app.domain.provider

import com.stashly.app.domain.model.MediaFormat
import com.stashly.app.domain.model.MediaKind

/** Picks the best *existing* format for the user's defaults. Never fabricates options. */
object FormatSelector {
    /** [quality] is "BEST", "LOWEST" or a target height such as "720". */
    fun pick(formats: List<MediaFormat>, preferredKind: MediaKind, quality: String): MediaFormat? {
        val primary = formats.filter { it.kind == preferredKind }
        val pool = primary.ifEmpty { formats }
        if (pool.isEmpty()) return null
        fun score(f: MediaFormat) = f.height ?: f.bitrateKbps ?: 0
        return when (quality) {
            "LOWEST" -> pool.minByOrNull { score(it) }
            "BEST" -> pool.maxByOrNull { score(it) }
            else -> {
                val target = quality.toIntOrNull() ?: return pool.maxByOrNull { score(it) }
                val atOrBelow = pool.filter { score(it) in 1..target }
                atOrBelow.maxByOrNull { score(it) } ?: pool.minByOrNull { score(it) }
            }
        }
    }
}
