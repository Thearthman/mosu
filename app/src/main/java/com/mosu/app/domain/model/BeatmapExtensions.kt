package com.mosu.app.domain.model

import androidx.compose.ui.graphics.Color
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

fun getStarRatingColor(stars: Float): Color {
    return when {
        stars < 1.5f -> Color(0xFF4FC0FF) // Light Blue - 1.50* and below
        stars < 2.0f -> Color(0xFF4FFFD5) // Light Green/Teal - 2.00*
        stars < 2.5f -> Color(0xFF7CFF4F) // Green - 2.50*
        stars < 3.25f -> Color(0xFFF6F05C) // Yellow - 3.25*
        stars < 4.5f -> Color(0xFFFF8068) // Orange/Red - 4.50*
        stars < 6.0f -> Color(0xFFFF3C71) // Pink/Red - 6.00*
        stars < 7.0f -> Color(0xFF6563DE) // Purple - 7.00*
        stars < 8.0f -> Color(0xFF18158E) // Dark Blue - right below 8.00*
        else -> Color(0xFF000000) // Black - 8.00* and above
    }
}

fun getStarRatingColors(): List<Color> {
    return listOf(
        Color(0xFF4FC0FF), // < 1.5★: Light Blue
        Color(0xFF4FFFD5), // 1.5-2.0★: Light Green/Teal
        Color(0xFF7CFF4F), // 2.0-2.5★: Green
        Color(0xFFF6F05C), // 2.5-3.25★: Yellow
        Color(0xFFFF8068), // 3.25-4.5★: Orange/Red
        Color(0xFFFF3C71), // 4.5-6.0★: Pink/Red
        Color(0xFF6563DE), // 6.0-7.0★: Purple
        Color(0xFF18158E), // 7.0-8.0★: Dark Blue
        Color(0xFF000000)  // 8.0★+: Black
    )
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
