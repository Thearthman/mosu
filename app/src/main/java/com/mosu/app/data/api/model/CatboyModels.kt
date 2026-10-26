package com.mosu.app.data.api.model

import com.google.gson.annotations.SerializedName

/**
 * Response model for Catboy API v5.0.1's `GET /b/{id}` endpoint.
 *
 * All fields are optional because the published schema does not mark any as
 * required; array fields default to empty to keep partially populated responses
 * safe to consume.
 */
data class CatboyBeatmap(
    @SerializedName("beatmapSetID") val beatmapSetId: Long? = null,
    @SerializedName("checksum") val checksum: String? = null,
    @SerializedName("data") val data: List<Int> = emptyList(),
    @SerializedName("deletedAt") val deletedAt: String? = null,
    @SerializedName("id") val id: Long? = null,
    @SerializedName("nextUpdate") val nextUpdate: String? = null,
    @SerializedName("setData") val setData: List<Int> = emptyList()
)

/**
 * Response model for Catboy API v5.0.1's `GET /s/{id}` endpoint.
 */
data class CatboyBeatmapSet(
    @SerializedName("data") val data: List<Int> = emptyList(),
    @SerializedName("deleted_at") val deletedAt: String? = null,
    @SerializedName("id") val id: Long? = null,
    @SerializedName("next_update") val nextUpdate: String? = null
)
