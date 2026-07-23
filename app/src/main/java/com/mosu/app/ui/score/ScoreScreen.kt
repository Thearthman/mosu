package com.mosu.app.ui.score

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.db.RecentPlayEntity
import com.mosu.app.data.repository.OsuRepository
import com.mosu.app.domain.model.createGradientStops
import com.mosu.app.domain.model.formatPlayedAtTimestamp
import com.mosu.app.domain.model.getGradientColorsForRange
import com.mosu.app.domain.model.getStarRatingColor
import com.mosu.app.domain.model.modeLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

private const val SCORE_SCREEN_TAG = "ScoreScreen"

@Composable
fun ScoreScreen(
    accessToken: String?,
    repository: OsuRepository,
    db: AppDatabase
) {
    var scores by remember { mutableStateOf<List<RecentPlayEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var refreshRequest by remember { mutableStateOf(0) }

    LaunchedEffect(accessToken, refreshRequest) {
        isLoading = true
        errorMessage = null
        try {
            scores = withContext(Dispatchers.IO) {
                val local = db.recentPlayDao().getRecentPlays()
                val incompleteRecentCount = local.count { it.needsScoreMetricRefresh() && it.isWithinRecentScoreWindow() }
                val shouldRefresh = accessToken != null &&
                    (refreshRequest > 0 || local.isEmpty() || incompleteRecentCount > 0)
                Log.d(
                    SCORE_SCREEN_TAG,
                    "loadScores local=${local.size}, manualRefresh=${refreshRequest > 0}, " +
                        "incompleteRecent=$incompleteRecentCount, shouldRefresh=$shouldRefresh, " +
                        "hasToken=${accessToken != null}"
                )
                if (shouldRefresh && accessToken != null) {
                    val user = repository.getMe()
                    val fresh = repository.fetchRecentPlays(accessToken, user.id.toString(), limit = 100)
                    Log.d(SCORE_SCREEN_TAG, "freshScores fetched=${fresh.size}, complete=${fresh.count { it.hasScoreMetrics() }}")
                    db.recentPlayDao().mergeNewPlays(fresh)
                }
                db.recentPlayDao().getRecentPlays().also { refreshed ->
                    Log.d(
                        SCORE_SCREEN_TAG,
                        "cachedScores total=${refreshed.size}, passed=${refreshed.passedScores().size}, " +
                            "withRank=${refreshed.count { it.rank != null }}, " +
                            "withStars=${refreshed.count { it.starRating != null }}, " +
                            "withPp=${refreshed.count { it.pp != null }}, " +
                            "withBeatmapId=${refreshed.count { it.beatmapId != null }}"
                    )
                }
            }
        } catch (error: Exception) {
            Log.e(SCORE_SCREEN_TAG, "Failed to load scores", error)
            errorMessage = error.message ?: "Unable to load scores"
            scores = withContext(Dispatchers.IO) { db.recentPlayDao().getRecentPlays() }
        } finally {
            isLoading = false
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Scores", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${scores.size} recent score${if (scores.size == 1) "" else "s"} cached",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(
                        onClick = {
                            refreshRequest++
                        },
                        enabled = accessToken != null
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh scores")
                    }
                }
            }
            if (errorMessage != null) {
                Text(
                    errorMessage ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        if (scores.isEmpty()) {
            item {
                EmptyScoreState(accessToken = accessToken)
            }
        } else {
            item { AverageStarsPanel(scores) }
            item { RecentPassedPanel(scores) }
            item { AchievementsPanel(scores) }
        }
    }
}

@Composable
private fun EmptyScoreState(accessToken: String?) {
    ScorePanel(title = "No score data", icon = Icons.Default.Star) {
        Text(
            if (accessToken == null) {
                "Log in from Profile to sync recent osu! scores."
            } else {
                "Refresh after playing osu! to cache recent scores."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AverageStarsPanel(scores: List<RecentPlayEntity>) {
    val averages = remember(scores) {
        scores.passedScores()
            .filter { it.rank != null && it.starRating != null }
            .groupBy { displayGrade(it.rank!!) }
            .mapValues { (_, group) -> group.mapNotNull { it.starRating }.average().toFloat() }
            .toList()
            .sortedBy { (grade, _) -> gradeSortIndex(grade) }
    }

    ScorePanel(title = "Average Stars by Grade", icon = Icons.Default.Star) {
        if (averages.isEmpty()) {
            MissingMetricsText("No ranked passed scores with star ratings yet.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                averages.forEach { (grade, averageStars) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GradeBadge(grade)
                        Spacer(Modifier.width(10.dp))
                        StarDifficultyProgressBar(
                            stars = averageStars,
                            modifier = Modifier
                                .weight(1f)
                                .height(8.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "%.1f stars".format(Locale.US, averageStars),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StarDifficultyProgressBar(
    stars: Float,
    modifier: Modifier = Modifier
) {
    val progress = (stars / 10f).coerceIn(0f, 1f)
    val baseGradientColors = getGradientColorsForRange(0f, 10f)
    val lastGradientIndex = baseGradientColors.lastIndex.coerceAtLeast(1)
    val gradientColors = baseGradientColors.mapIndexed { index, color ->
        val boost = (index.toFloat() / lastGradientIndex).coerceIn(0f, 1f)
        color.brighten(0.42f * boost)
    }

    Canvas(modifier = modifier.clip(RoundedCornerShape(4.dp))) {
        val clipRight = size.width * progress
        val completedSize = Size(clipRight, size.height)
        val cutoffRadius = CornerRadius(size.height / 2f, size.height / 2f)
        if (gradientColors.size >= 2) {
            val brush = Brush.horizontalGradient(
                *createGradientStops(gradientColors),
                startX = 0f,
                endX = size.width
            )
            drawRect(brush = brush, alpha = 0.28f)
            if (clipRight > 0f) {
                drawRoundRect(
                    brush = brush,
                    size = completedSize,
                    cornerRadius = cutoffRadius
                )
            }
        } else {
            val color = gradientColors.firstOrNull() ?: getStarRatingColor(stars)
            drawRect(color = color, alpha = 0.28f)
            if (clipRight > 0f) {
                drawRoundRect(
                    color = color,
                    size = completedSize,
                    cornerRadius = cutoffRadius
                )
            }
        }
    }
}

@Composable
private fun RecentPassedPanel(scores: List<RecentPlayEntity>) {
    val recentPassed = remember(scores) {
        scores.passedScores()
            .sortedByDescending { it.playedAt }
            .take(30)
    }

    ScorePanel(title = "Recently Passed", icon = Icons.Default.Star) {
        if (recentPassed.isEmpty()) {
            MissingMetricsText("No passed scores are cached yet.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                recentPassed.forEach { score ->
                    RecentScoreRow(score)
                }
            }
        }
    }
}

@Composable
private fun AchievementsPanel(scores: List<RecentPlayEntity>) {
    val passed = remember(scores) { scores.passedScores() }
    val achievements = remember(passed) { buildAchievements(passed) }

    ScorePanel(title = "Achievements", icon = Icons.Default.EmojiEvents) {
        if (achievements.isEmpty()) {
            MissingMetricsText("More recent-score metrics are needed for achievements.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                achievements.forEach { achievement ->
                    AchievementRow(achievement)
                }
            }
        }
    }
}

@Composable
private fun RecentScoreRow(score: RecentPlayEntity) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = score.coverUrl,
            contentDescription = score.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                score.rank?.let { GradeBadge(it) }
                if (score.mode != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        modeLabel(score.mode),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                score.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(score.artist, score.difficultyName).joinToString(" - "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                score.pp?.let { "%.0fpp".format(Locale.US, it) } ?: "--pp",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StarRatingBadge(score.starRating)
                Text(
                    "/ ${formatLength(score.lengthSeconds)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StarRatingBadge(stars: Float?) {
    val color = stars?.let { getStarRatingColor(it) } ?: MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (stars == null) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else if (color.luminance() > 0.55f) {
        Color.Black
    } else {
        Color.White
    }

    Surface(
        shape = RoundedCornerShape(5.dp),
        color = color,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = stars?.let { "%.1f".format(Locale.US, it) } ?: "--",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Star rating",
                modifier = Modifier.size(10.dp)
            )
        }
    }
}

@Composable
private fun AchievementRow(achievement: ScoreAchievement) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(achievement.icon, contentDescription = achievement.label, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(achievement.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                achievement.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            achievement.value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ScorePanel(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                content()
            }
        }
    }
}

@Composable
private fun GradeBadge(grade: String) {
    val color = gradeColor(grade)
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color,
        contentColor = if (color.luminance() > 0.55f) Color.Black else Color.White
    ) {
        Text(
            text = grade.uppercase(Locale.US),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MissingMetricsText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private data class ScoreAchievement(
    val label: String,
    val value: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private fun buildAchievements(scores: List<RecentPlayEntity>): List<ScoreAchievement> {
    val achievements = mutableListOf<ScoreAchievement>()
    scores.maxByOrNull { it.lengthSeconds ?: -1 }?.takeIf { it.lengthSeconds != null }?.let {
        achievements += ScoreAchievement(
            label = "Longest map played",
            value = formatLength(it.lengthSeconds),
            subtitle = "${it.title} - ${it.difficultyName ?: it.artist}",
            icon = Icons.Default.Timeline
        )
    }
    scores.maxByOrNull { it.bpm ?: -1f }?.takeIf { it.bpm != null }?.let {
        achievements += ScoreAchievement(
            label = "Fastest map played",
            value = "%.0f BPM".format(Locale.US, it.bpm),
            subtitle = "${it.title} - ${it.difficultyName ?: it.artist}",
            icon = Icons.Default.Speed
        )
    }
    scores.minByOrNull { it.ar ?: Float.MAX_VALUE }?.takeIf { it.ar != null }?.let {
        achievements += ScoreAchievement(
            label = "Lowest AR played",
            value = "AR %.1f".format(Locale.US, it.ar),
            subtitle = "${it.title} - ${it.difficultyName ?: it.artist}",
            icon = Icons.Default.VisibilityOff
        )
    }
    scores.maxByOrNull { it.cs ?: -1f }?.takeIf { it.cs != null }?.let {
        achievements += ScoreAchievement(
            label = "Highest CS played",
            value = "CS %.1f".format(Locale.US, it.cs),
            subtitle = "${it.title} - ${it.difficultyName ?: it.artist}",
            icon = Icons.Default.CenterFocusStrong
        )
    }
    scores.maxByOrNull { it.pp ?: -1f }?.takeIf { it.pp != null }?.let {
        val heldDays = daysSince(it.playedAt)
        achievements += ScoreAchievement(
            label = "PP record",
            value = "%.0fpp".format(Locale.US, it.pp),
            subtitle = "Record has stood for $heldDays day${if (heldDays == 1L) "" else "s"} since ${formatPlayedAtTimestamp(it.playedAt)}",
            icon = Icons.Default.EmojiEvents
        )
    }
    return achievements
}

private fun List<RecentPlayEntity>.passedScores(): List<RecentPlayEntity> {
    return filter { it.passed == true || (it.rank != null && !it.rank.equals("F", ignoreCase = true)) }
}

private fun RecentPlayEntity.hasScoreMetrics(): Boolean {
    return rank != null || passed != null || pp != null || starRating != null || beatmapId != null
}

private fun RecentPlayEntity.needsScoreMetricRefresh(): Boolean {
    return scoreId != null && (!hasScoreMetrics() || rank == null || starRating == null || beatmapId == null)
}

private fun RecentPlayEntity.isWithinRecentScoreWindow(): Boolean {
    val ageMillis = System.currentTimeMillis() - playedAt
    return ageMillis in 0..(24L * 60L * 60L * 1000L)
}

private fun gradeSortIndex(grade: String): Int {
    return listOf("SS+", "SS", "S+", "S", "A", "B", "C", "D").indexOf(displayGrade(grade)).let {
        if (it == -1) Int.MAX_VALUE else it
    }
}

private fun gradeColor(grade: String): Color {
    return when (displayGrade(grade)) {
        "SS+", "SS" -> Color(0xFFFFD54F)
        "S+", "S" -> Color(0xFFFFF176)
        "A" -> Color(0xFF66BB6A)
        "B" -> Color(0xFF42A5F5)
        "C" -> Color(0xFFAB47BC)
        "D" -> Color(0xFFEF5350)
        else -> Color(0xFF90A4AE)
    }
}

private fun displayGrade(rank: String): String {
    return when (rank.uppercase(Locale.US)) {
        "XH" -> "SS+"
        "X" -> "SS"
        "SH" -> "S+"
        else -> rank.uppercase(Locale.US)
    }
}

private fun Color.luminance(): Float {
    return (0.299f * red + 0.587f * green + 0.114f * blue)
}

private fun Color.brighten(amount: Float): Color {
    val clamped = amount.coerceIn(0f, 1f)
    return Color(
        red = red + (1f - red) * clamped,
        green = green + (1f - green) * clamped,
        blue = blue + (1f - blue) * clamped,
        alpha = alpha
    )
}

private fun formatLength(seconds: Int?): String {
    if (seconds == null || seconds <= 0) return "--:--"
    val minutes = seconds / 60
    val remaining = seconds % 60
    return "%d:%02d".format(Locale.US, minutes, remaining)
}

private fun daysSince(timestamp: Long): Long {
    val playedDate = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    return ChronoUnit.DAYS.between(playedDate, LocalDate.now()).coerceAtLeast(0)
}
