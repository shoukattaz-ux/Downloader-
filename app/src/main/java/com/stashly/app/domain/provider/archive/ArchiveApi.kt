package com.stashly.app.domain.provider.archive

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.http.GET
import retrofit2.http.Path

/** Internet Archive's documented public metadata API: https://archive.org/developers/md-read.html */
interface ArchiveApi {
    @GET("metadata/{id}")
    suspend fun metadata(@Path("id") id: String): ArchiveMetadata
}

@Serializable
data class ArchiveMetadata(
    val metadata: ArchiveItemMeta? = null,
    val files: List<ArchiveFile> = emptyList(),
    val is_dark: Boolean? = null,
)

@Serializable
data class ArchiveItemMeta(
    val title: JsonElement? = null,
    val mediatype: JsonElement? = null,
    val licenseurl: JsonElement? = null,
    @kotlinx.serialization.SerialName("access-restricted-item") val accessRestricted: JsonElement? = null,
)

@Serializable
data class ArchiveFile(
    val name: String,
    val format: String? = null,
    val size: String? = null,
    val length: String? = null,
    val height: String? = null,
    val bitrate: String? = null,
    val source: String? = null,
    @kotlinx.serialization.SerialName("private") val isPrivate: String? = null,
)
