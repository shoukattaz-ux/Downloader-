package com.stashly.app.core

import java.util.Locale

object Formatters {
    fun bytes(value: Long?): String {
        if (value == null || value < 0) return "—"
        if (value < 1024) return "$value B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = value.toDouble()
        var i = -1
        while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
        return String.format(Locale.US, if (v >= 100) "%.0f %s" else "%.1f %s", v, units[i])
    }

    fun speed(bytesPerSecond: Long): String =
        if (bytesPerSecond <= 0) "—" else "${bytes(bytesPerSecond)}/s"

    /** m:ss or h:mm:ss */
    fun duration(totalSeconds: Long?): String {
        if (totalSeconds == null || totalSeconds <= 0) return "—"
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun eta(seconds: Long?): String? {
        if (seconds == null || seconds < 0) return null
        return when {
            seconds < 60 -> "${seconds}s left"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s left"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m left"
        }
    }

    fun percent(done: Long, total: Long): Int =
        if (total <= 0) 0 else ((done * 100) / total).toInt().coerceIn(0, 100)
}
