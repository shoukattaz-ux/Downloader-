package com.stashly.app.domain.provider

import com.stashly.app.domain.model.MediaInfo
import okhttp3.HttpUrl

/**
 * Contract for a compliant media source. Add a new integration by implementing this interface and
 * registering it in `ProviderModule`; no other code changes are required.
 *
 * Implementations MUST only use official/documented APIs or direct, publicly served files. They must
 * never bypass DRM, authentication, or platform download restrictions.
 */
interface MediaSourceProvider {
    val id: String
    val displayName: String

    /** Cheap, offline check: can this provider handle [url]? */
    fun supports(url: HttpUrl): Boolean

    /**
     * Retrieves metadata and the formats the source *actually* offers.
     * @throws com.stashly.app.core.AppError with a user-readable message on any failure.
     */
    suspend fun resolve(url: HttpUrl): MediaInfo
}
