package com.mosu.app.domain.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.mosu.app.R
import androidx.compose.ui.graphics.Color as ComposeColor
import com.mosu.app.data.api.model.BeatmapsetCompact
import com.mosu.app.data.api.model.Covers
import com.mosu.app.data.db.RecentPlayEntity
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

fun RecentPlayEntity.toBeatmapset(): BeatmapsetCompact {
    val cover = coverUrl ?: ""
    return BeatmapsetCompact(
        id = beatmapSetId,
        title = title,
        artist = artist,
        creator = creator,
        covers = Covers(coverUrl = cover, listUrl = cover),
        genreId = genreId,
        status = "unknown",
        beatmaps = emptyList()
    )
}

fun modeLabel(mode: String): String {
    return when (mode.lowercase()) {
        "osu" -> "std"
        "mania" -> "mania"
        "taiko" -> "taiko"
        "fruits", "catch" -> "catch"
        else -> mode
    }
}

private data class StarRatingColorAnchor(
    val stars: Float,
    val color: Color
)

private val starRatingColorAnchors = listOf(
    StarRatingColorAnchor(0.0f, Color(0xFF4FC0FF)), // Light Blue
    StarRatingColorAnchor(1.5f, Color(0xFF4FFFD5)), // Light Green/Teal
    StarRatingColorAnchor(2.0f, Color(0xFF7CFF4F)), // Green
    StarRatingColorAnchor(2.5f, Color(0xFFF6F05C)), // Yellow
    StarRatingColorAnchor(3.25f, Color(0xFFFF8068)), // Orange/Red
    StarRatingColorAnchor(4.5f, Color(0xFFFF3C71)), // Pink/Red
    StarRatingColorAnchor(6.0f, Color(0xFF6563DE)), // Purple
    StarRatingColorAnchor(7.0f, Color(0xFF18158E)), // Dark Blue
    StarRatingColorAnchor(8.0f, Color(0xFF000000)) // Black
)

fun getStarRatingColor(stars: Float): Color {
    if (stars.isNaN()) return Color(0xFF000000)

    val firstAnchor = starRatingColorAnchors.first()
    val lastAnchor = starRatingColorAnchors.last()
    if (stars <= firstAnchor.stars) return firstAnchor.color
    if (stars >= lastAnchor.stars) return lastAnchor.color

    for (index in 0 until starRatingColorAnchors.lastIndex) {
        val start = starRatingColorAnchors[index]
        val end = starRatingColorAnchors[index + 1]
        if (stars <= end.stars) {
            val fraction = (stars - start.stars) / (end.stars - start.stars)
            return lerp(start.color, end.color, fraction)
        }
    }

    return lastAnchor.color
}

fun getStarRatingColors(): List<Color> {
    return starRatingColorAnchors.map { it.color }
}

fun getGradientColorsForRange(minStars: Float, maxStars: Float): List<Color> {
    val allColors = getStarRatingColors()
    val colorStops = mutableListOf<Color>()

    // Find the range of colors needed for this difficulty span
    val minIndex = when {
        minStars < 1.5f -> 0
        minStars < 2.0f -> 1
        minStars < 2.5f -> 2
        minStars < 3.25f -> 3
        minStars < 4.5f -> 4
        minStars < 6.0f -> 5
        minStars < 7.0f -> 6
        minStars < 8.0f -> 7
        else -> 8
    }

    val maxIndex = when {
        maxStars < 1.5f -> 0
        maxStars < 2.0f -> 1
        maxStars < 2.5f -> 2
        maxStars < 3.25f -> 3
        maxStars < 4.5f -> 4
        maxStars < 6.0f -> 5
        maxStars < 7.0f -> 6
        maxStars < 8.0f -> 7
        else -> 8
    }

    // Add colors from min to max index, ensuring no duplicates
    val addedColors = mutableSetOf<Color>()
    for (i in minIndex..maxIndex) {
        val color = allColors[i]
        if (addedColors.add(color)) {
            colorStops.add(color)
        }
    }

    return colorStops
}

fun createGradientStops(colors: List<Color>): Array<Pair<Float, ComposeColor>> {
    if (colors.size <= 1) return arrayOf(colors.firstOrNull()?.let { 0f to it } ?: (0f to Color.Black))

    val stops = mutableListOf<Pair<Float, ComposeColor>>()

    // Make start and end colors take up more space
    val totalColors = colors.size
    val startEndWeight = 0.30f // Start and end colors take 35% each

    for (i in colors.indices) {
        val position = when (i) {
            0 -> 0.0f // Start color at 0%
            colors.indices.last -> 1.0f // End color at 100%
            else -> {
                // Distribute middle colors evenly in the middle section
                val middleStart = startEndWeight
                val middleEnd = 0.85f - startEndWeight
                val middleRange = middleEnd - middleStart
                val middleColors = totalColors - 2
                val middleIndex = i - 1
                middleStart + (middleIndex * middleRange / (middleColors - 1).coerceAtLeast(1))
            }
        }
        stops.add(position to colors[i])
    }

    return stops.toTypedArray()
}

fun formatRecentPlayTimestamp(timestamp: Long, context: android.content.Context): String {
    val now = LocalDateTime.now()
    val playedTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())

    val daysDiff = ChronoUnit.DAYS.between(playedTime.toLocalDate(), now.toLocalDate())

    return when {
        daysDiff == 0L -> context.getString(R.string.timestamp_today)
        daysDiff == 1L -> context.getString(R.string.timestamp_yesterday)
        daysDiff <= 3L -> context.getString(R.string.timestamp_three_days_ago)
        daysDiff <= 7L -> context.getString(R.string.timestamp_last_week)
        daysDiff <= 30L -> context.getString(R.string.timestamp_one_month_ago)
        daysDiff <= 60L -> context.getString(R.string.timestamp_two_months_ago)
        daysDiff <= 90L -> context.getString(R.string.timestamp_three_months_ago)
        daysDiff <= 180L -> context.getString(R.string.timestamp_six_months_ago)
        daysDiff <= 365L -> context.getString(R.string.timestamp_one_year_ago)
        else -> {
            // Format as "Month Year" for older dates
            val formatter = DateTimeFormatter.ofPattern("MMMM yyyy")
            playedTime.format(formatter)
        }
    }
}

fun formatPlayedAtTimestamp(timestamp: Long): String {
    val playedTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())

    // Use different formatting based on system language
    val isChinese = Locale.getDefault().language.startsWith("zh")
    val datePattern = if (isChinese) "MMMdd" else "MMM dd"
    val formatter = DateTimeFormatter.ofPattern("$datePattern, yyyy '@' HH:mm")
    return playedTime.format(formatter)
}
