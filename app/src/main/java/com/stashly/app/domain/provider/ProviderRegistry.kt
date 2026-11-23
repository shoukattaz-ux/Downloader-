package com.stashly.app.domain.provider

import com.stashly.app.core.AppError
import com.stashly.app.core.ErrorCode
import com.stashly.app.core.UrlRules
import com.stashly.app.domain.model.AnalyzeOutcome

/** Validates a pasted link, rejects restricted platforms, and routes to the first matching provider. */
class ProviderRegistry(
    private val providers: List<MediaSourceProvider>,
    private val allowHttp: Boolean = false,
) {
    suspend fun analyze(raw: String): AnalyzeOutcome {
        val url = UrlRules.parse(raw, allowHttp)
        RestrictedPlatforms.detect(url)?.let { return AnalyzeOutcome.Restricted(it, url.toString()) }
        val provider = providers.firstOrNull { it.supports(url) }
            ?: throw AppError(ErrorCode.UNSUPPORTED_SOURCE, "This source isn't supported.")
        val info = provider.resolve(url)
        if (info.formats.isEmpty()) throw AppError(ErrorCode.NO_MEDIA, "No downloadable media was found at this link.")
        return AnalyzeOutcome.Success(info)
    }

    /** Offline, instant classification used for live feedback while the user types. */
    fun hint(raw: String): LinkHint {
        if (raw.isBlank()) return LinkHint.Empty
        val url = try { UrlRules.parse(raw, allowHttp) } catch (e: AppError) { return LinkHint.Invalid(e.userMessage) }
        RestrictedPlatforms.detect(url)?.let { return LinkHint.Restricted(it) }
        val provider = providers.firstOrNull { it.supports(url) } ?: return LinkHint.Invalid("This source isn't supported.")
        return LinkHint.Supported(provider.displayName)
    }

    val sourceNames: List<String> get() = providers.map { it.displayName }
}

sealed interface LinkHint {
    data object Empty : LinkHint
    data class Invalid(val message: String) : LinkHint
    data class Restricted(val platform: String) : LinkHint
    data class Supported(val sourceName: String) : LinkHint
}
