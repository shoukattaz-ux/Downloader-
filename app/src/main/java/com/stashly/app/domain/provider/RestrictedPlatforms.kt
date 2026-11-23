package com.stashly.app.domain.provider

import com.stashly.app.core.UrlRules
import okhttp3.HttpUrl

/**
 * Platforms whose terms do not permit third-party downloading. Stashly never claims to support them;
 * instead it offers a compliant alternative (open in the official app / browser).
 */
object RestrictedPlatforms {
    private val platforms = mapOf(
        "YouTube" to listOf("youtube.com", "youtu.be", "youtube-nocookie.com"),
        "Instagram" to listOf("instagram.com"),
        "Facebook" to listOf("facebook.com", "fb.watch", "fb.com"),
        "TikTok" to listOf("tiktok.com"),
        "X (Twitter)" to listOf("twitter.com", "x.com", "t.co"),
        "Netflix" to listOf("netflix.com"),
        "Spotify" to listOf("spotify.com", "spotify.link"),
        "Vimeo" to listOf("vimeo.com"),
        "Twitch" to listOf("twitch.tv"),
        "Disney+" to listOf("disneyplus.com"),
        "Amazon Prime Video" to listOf("primevideo.com"),
        "SoundCloud" to listOf("soundcloud.com"),
        "Dailymotion" to listOf("dailymotion.com", "dai.ly"),
    )

    fun detect(url: HttpUrl): String? =
        platforms.entries.firstOrNull { (_, hosts) -> hosts.any { UrlRules.hostMatches(url.host, it) } }?.key
}
