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
    @SerializedName("rank") val rank: String? = null,
    @SerializedName("pp") val pp: Float? = null,
    @SerializedName("accuracy") val accuracy: Float? = null,
    @SerializedName("max_combo") val maxCombo: Int? = null,
    @SerializedName("passed") val passed: Boolean? = null,
    @SerializedName("beatmap") val beatmap: RecentBeatmap?,
    @SerializedName("beatmapset") val beatmapset: BeatmapsetCompact?  // Primary source: beatmapset at score level
)

data class RecentBeatmap(
    @SerializedName("id") val id: Long,
    @SerializedName("beatmapset_id") val beatmapsetId: Long?,
    @SerializedName("version") val version: String? = null,
    @SerializedName("mode") val mode: String? = null,
    @SerializedName("difficulty_rating") val difficultyRating: Float? = null,
    @SerializedName("total_length") val totalLength: Int? = null,
    @SerializedName("hit_length") val hitLength: Int? = null,
    @SerializedName("bpm") val bpm: Float? = null,
    @SerializedName("ar") val ar: Float? = null,
    @SerializedName("cs") val cs: Float? = null,
    @SerializedName("drain") val drain: Float? = null
)

data class BeatmapsetCompact(
    @SerializedName("id") val id: Long,
    @SerializedName("title") val title: String,
    @SerializedName("artist") val artist: String,
    @SerializedName("creator") val creator: String,
    @SerializedName("covers") val covers: Covers,
    @SerializedName("genre_id") val genreId: Int? = null,
    @SerializedName("status") val status: String,
    @SerializedName("beatmaps") val beatmaps: List<BeatmapDetail> = emptyList()
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
    @SerializedName("beatmapset_id") val beatmapsetId: Long,
    @SerializedName("playcount") val playCount: Int,
    @SerializedName("status") val status: String,
    @SerializedName("total_length") val totalLength: Int? = null,
    @SerializedName("hit_length") val hitLength: Int? = null,
    @SerializedName("bpm") val bpm: Float? = null,
    @SerializedName("ar") val ar: Float? = null,
    @SerializedName("cs") val cs: Float? = null,
    @SerializedName("drain") val drain: Float? = null
)

data class BeatmapsetDetail(
    @SerializedName("id") val id: Long,
    @SerializedName("title") val title: String,
    @SerializedName("artist") val artist: String,
    @SerializedName("creator") val creator: String,
    @SerializedName("covers") val covers: Covers,
    @SerializedName("beatmaps") val beatmaps: List<BeatmapDetail> = emptyList(),
    @SerializedName("status") val status: String
)

data class SearchResponse(
    @SerializedName("beatmapsets") val beatmapsets: List<BeatmapsetCompact>,
    @SerializedName("total") val total: Int,
    @SerializedName("cursor_string") val cursorString: String? = null
)
