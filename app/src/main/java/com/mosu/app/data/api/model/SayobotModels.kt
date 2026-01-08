package com.mosu.app.data.api.model

import com.google.gson.annotations.SerializedName

data class SayobotSearchResponse(
    @SerializedName("status") val status: Int,
    @SerializedName("data") val data: List<SayobotBeatmapset>?,
    @SerializedName("endid") val endid: Int?
)

data class SayobotBeatmapset(
    @SerializedName("sid") val sid: Long,
    @SerializedName("title") val title: String,
    @SerializedName("artist") val artist: String,
    @SerializedName("creator") val creator: String,
    @SerializedName("approved") val approved: Int,
    @SerializedName("modes") val modes: Int,
    @SerializedName("genre") val genre: Int,
    @SerializedName("language") val language: Int
) {
    fun toBeatmapsetCompact(): BeatmapsetCompact {
        return BeatmapsetCompact(
            id = sid,
            title = title,
            artist = artist,
            creator = creator,
            status = when (approved) {
                1 -> "ranked"
                2 -> "approved"
                3 -> "qualified"
                4 -> "loved"
                else -> "ranked" // Fallback
            },
            covers = Covers(
                coverUrl = "https://cdn.sayobot.cn:25225/beatmaps/$sid/covers/cover.jpg",
                listUrl = "https://cdn.sayobot.cn:25225/beatmaps/$sid/covers/list.jpg"
            ),
            genreId = genre,
            beatmaps = emptyList() // We don't have diffs in list view
        )
    }
}

data class SayobotDetailResponse(
    @SerializedName("data") val data: SayobotDetailData
)

data class SayobotDetailData(
    @SerializedName("sid") val sid: Long,
    @SerializedName("title") val title: String,
    @SerializedName("artist") val artist: String,
    @SerializedName("creator") val creator: String,
    @SerializedName("approved") val approved: Int,
    @SerializedName("bid_data") val bidData: List<SayobotBeatmap>
) {
    fun toBeatmapsetDetail(): BeatmapsetDetail {
        val statusStr = when (approved) {
            1 -> "ranked"
            2 -> "approved"
            3 -> "qualified"
            4 -> "loved"
            else -> "ranked"
        }
        return BeatmapsetDetail(
            id = sid,
            title = title,
            artist = artist,
            creator = creator,
            status = statusStr,
            covers = Covers(
                coverUrl = "https://cdn.sayobot.cn:25225/beatmaps/$sid/covers/cover.jpg",
                listUrl = "https://cdn.sayobot.cn:25225/beatmaps/$sid/covers/list.jpg"
            ),
            beatmaps = bidData.map { it.toBeatmapDetail(sid, statusStr) }
        )
    }
}

data class SayobotBeatmap(
    @SerializedName("bid") val bid: Long,
    @SerializedName("version") val version: String,
    @SerializedName("star") val star: Double,
    @SerializedName("AR") val ar: Double,
    @SerializedName("CS") val cs: Double,
    @SerializedName("HP") val hp: Double,
    @SerializedName("OD") val od: Double,
    @SerializedName("mode") val mode: Int,
    @SerializedName("audio") val audio: String? = null,
    @SerializedName("bg") val bg: String? = null,
    @SerializedName("playcount") val playcount: Int = 0
) {
    fun toBeatmapDetail(setId: Long, setStatus: String): BeatmapDetail {
        return BeatmapDetail(
            id = bid,
            version = version,
            difficultyRating = star.toFloat(),
            url = "https://osu.ppy.sh/beatmaps/$bid",
            beatmapsetId = setId,
            playCount = playcount,
            status = setStatus,
            mode = when (mode) {
                0 -> "osu"
                1 -> "taiko"
                2 -> "fruits"
                3 -> "mania"
                else -> "osu"
            }
        )
    }
}
