package com.mosu.app.data.api.model

import com.google.gson.annotations.SerializedName

data class OsuTokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("expires_in") val expiresIn: Long,
    @SerializedName("token_type") val tokenType: String
)

data class OsuUserCompact(
    @SerializedName("id") val id: Long,
    @SerializedName("username") val username: String,
    @SerializedName("avatar_url") val avatarUrl: String,
    @SerializedName("is_supporter") val isSupporter: Boolean = false
)

data class BeatmapPlaycount(
    @SerializedName("beatmap_id") val beatmapId: Long,
    @SerializedName("count") val count: Int,
    @SerializedName("beatmapset") val beatmapset: BeatmapsetCompact
)

data class RecentScore(
    @SerializedName("id") val scoreId: Long? = null,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("beatmap") val beatmap: RecentBeatmap?,
    @SerializedName("beatmapset") val beatmapset: BeatmapsetCompact?  // Primary source: beatmapset at score level
)

data class RecentBeatmap(
    @SerializedName("id") val id: Long,
    @SerializedName("beatmapset_id") val beatmapsetId: Long?  // Just the ID, not the full object
)

data class BeatmapsetCompact(
    @SerializedName("id") val id: Long,
    @SerializedName("title") val title: String,
    @SerializedName("artist") val artist: String,
    @SerializedName("creator") val creator: String,
    @SerializedName("covers") val covers: Covers,
    @SerializedName("genre_id") val genreId: Int? = null
)

data class Covers(
    @SerializedName("cover") val coverUrl: String,
    @SerializedName("list") val listUrl: String
)

data class BeatmapDetail(
    @SerializedName("id") val id: Long,
    @SerializedName("version") val version: String,
    @SerializedName("mode") val mode: String,
    @SerializedName("difficulty_rating") val difficultyRating: Float,
    @SerializedName("url") val url: String?,
    @SerializedName("beatmapset_id") val beatmapsetId: Long
)

data class BeatmapsetDetail(
    @SerializedName("id") val id: Long,
    @SerializedName("title") val title: String,
    @SerializedName("artist") val artist: String,
    @SerializedName("creator") val creator: String,
    @SerializedName("covers") val covers: Covers,
    @SerializedName("beatmaps") val beatmaps: List<BeatmapDetail> = emptyList()
)

data class SearchResponse(
    @SerializedName("beatmapsets") val beatmapsets: List<BeatmapsetCompact>,
    @SerializedName("total") val total: Int,
    @SerializedName("cursor_string") val cursorString: String? = null
)
