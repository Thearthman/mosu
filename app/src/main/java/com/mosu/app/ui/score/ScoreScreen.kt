package com.mosu.app.ui.score

import android.content.Context
import android.util.Log
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mosu.app.R
import com.mosu.app.data.SettingsManager
import com.mosu.app.data.api.model.BeatmapsetCompact
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.db.RecentPlayEntity
import com.mosu.app.data.repository.OsuRepository
import com.mosu.app.domain.model.createGradientStops
import com.mosu.app.domain.model.formatPlayedAtTimestamp
import com.mosu.app.domain.model.getGradientColorsForRange
import com.mosu.app.domain.model.getStarRatingColor
import com.mosu.app.domain.search.BeatmapSearchService
import com.mosu.app.ui.components.BeatmapSetActions
import com.mosu.app.ui.components.BeatmapSetItem
import com.mosu.app.ui.components.GradeBadge
import com.mosu.app.ui.components.InfoPopup
import com.mosu.app.ui.components.InfoPopupConfig
import com.mosu.app.ui.components.ScoreModeFilterIcons
import com.mosu.app.ui.components.displayGrade
import com.mosu.app.ui.components.formatLength
import com.mosu.app.ui.components.toBeatmapSetData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private const val SCORE_SCREEN_TAG = "ScoreScreen"

@Composable
fun ScoreScreen(
    accessToken: String?,
    repository: OsuRepository,
    db: AppDatabase,
    settingsManager: SettingsManager
) {
    var scores by remember { mutableStateOf<List<RecentPlayEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var refreshRequest by remember { mutableStateOf(0) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scoreModeFilter by settingsManager.scoreModeFilter.collectAsState(initial = "osu")
    val searchService = remember(repository, db, context) {
        BeatmapSearchService(repository, db, context)
    }
    var infoDialogVisible by remember { mutableStateOf(false) }
    var infoTarget by remember { mutableStateOf<BeatmapsetCompact?>(null) }
    var infoSets by remember { mutableStateOf<List<BeatmapsetCompact>>(emptyList()) }
    var infoLoading by remember { mutableStateOf(false) }
    var infoError by remember { mutableStateOf<String?>(null) }
    val scoreActions = remember {
        BeatmapSetActions(
            onLongClick = { set ->
                infoTarget = set.toCompact()
                infoDialogVisible = true
            }
        )
    }

    // Universal entry point: every consumer of score data reads from this filtered
    // list, so the mode filter is applied before any panel-level filtering/calculation.
    val filteredScores = remember(scores, scoreModeFilter) {
        scores.filterByMode(scoreModeFilter)
    }

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
            errorMessage = error.message ?: context.getString(R.string.score_load_error)
            scores = withContext(Dispatchers.IO) { db.recentPlayDao().getRecentPlays() }
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(infoTarget, infoDialogVisible, scoreModeFilter) {
        val target = infoTarget
        if (!infoDialogVisible || target == null) return@LaunchedEffect

        infoLoading = true
        infoError = null
        infoSets = emptyList()
        searchService.loadInfoPopup(target.title, target.artist, scoreModeFilter)
            .onSuccess { infoSets = it }
            .onFailure { infoError = it.message ?: context.getString(R.string.search_info_load_error) }
        infoLoading = false
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.score_title),
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 16.dp),
                        style = MaterialTheme.typography.displayMedium
                    )
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(
                            onClick = {
                                refreshRequest++
                            },
                            enabled = accessToken != null
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.score_cd_refresh)
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            pluralStringResource(
                                R.plurals.score_recent_cached,
                                filteredScores.size,
                                filteredScores.size
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ScoreModeFilterIcons(
                        selectedMode = scoreModeFilter,
                        onModeSelected = { mode ->
                            scope.launch { settingsManager.saveScoreModeFilter(mode) }
                        }
                    )
                }
                if (errorMessage != null) {
                    Text(
                        errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        if (filteredScores.isEmpty()) {
            item {
                EmptyScoreState(
                    accessToken = accessToken,
                    filterHidesAll = scores.isNotEmpty() && scoreModeFilter != "all"
                )
            }
        } else {
            item { AverageStarsPanel(filteredScores) }
            item { RecentPassedPanel(filteredScores, scoreActions) }
            item { AchievementsPanel(filteredScores) }
        }
    }

    InfoPopup(
        visible = infoDialogVisible,
        onDismiss = { infoDialogVisible = false },
        target = infoTarget,
        sets = infoSets,
        loading = infoLoading,
        error = infoError,
        config = InfoPopupConfig(
            infoCoverEnabled = false,
            showConfirmButton = false
        )
    )
}

@Composable
private fun EmptyScoreState(
    accessToken: String?,
    filterHidesAll: Boolean = false
) {
    ScorePanel(title = stringResource(R.string.score_empty_title), icon = Icons.Default.Star) {
        Text(
            when {
                filterHidesAll -> stringResource(R.string.score_empty_mode)
                accessToken == null -> stringResource(R.string.score_empty_logged_out)
                else -> stringResource(R.string.score_empty_logged_in)
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

    ScorePanel(title = stringResource(R.string.score_average_stars_title), icon = Icons.Default.Star) {
        if (averages.isEmpty()) {
            MissingMetricsText(stringResource(R.string.score_average_stars_empty))
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
                            stringResource(R.string.score_stars_value, averageStars),
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
private fun RecentPassedPanel(
    scores: List<RecentPlayEntity>,
    actions: BeatmapSetActions
) {
    val recentPassed = remember(scores) {
        scores.passedScores()
            .sortedByDescending { it.playedAt }
            .take(30)
    }

    ScorePanel(title = stringResource(R.string.score_recent_passed_title), icon = Icons.Default.Star) {
        if (recentPassed.isEmpty()) {
            MissingMetricsText(stringResource(R.string.score_recent_passed_empty))
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                recentPassed.forEach { score ->
                    BeatmapSetItem(
                        set = score.toBeatmapSetData(),
                        actions = actions,
                        contentPadding = PaddingValues(0.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AchievementsPanel(scores: List<RecentPlayEntity>) {
    val passed = remember(scores) { scores.passedScores() }
    val context = LocalContext.current
    val achievements = remember(passed, context) { buildAchievements(passed, context) }

    ScorePanel(title = stringResource(R.string.score_achievements_title), icon = Icons.Default.EmojiEvents) {
        if (achievements.isEmpty()) {
            MissingMetricsText(stringResource(R.string.score_achievements_empty))
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
                    contentDescription = if (expanded) {
                        stringResource(R.string.score_cd_collapse, title)
                    } else {
                        stringResource(R.string.score_cd_expand, title)
                    },
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

private fun buildAchievements(scores: List<RecentPlayEntity>, context: Context): List<ScoreAchievement> {
    val achievements = mutableListOf<ScoreAchievement>()
    fun subtitle(track: RecentPlayEntity): String = context.getString(
        R.string.score_achievement_subtitle,
        track.title,
        track.difficultyName ?: track.artist
    )
    scores.maxByOrNull { it.lengthSeconds ?: -1 }?.takeIf { it.lengthSeconds != null }?.let {
        achievements += ScoreAchievement(
            label = context.getString(R.string.score_achievement_longest),
            value = formatLength(it.lengthSeconds),
            subtitle = subtitle(it),
            icon = Icons.Default.Timeline
        )
    }
    scores.maxByOrNull { it.bpm ?: -1f }?.takeIf { it.bpm != null }?.let {
        achievements += ScoreAchievement(
            label = context.getString(R.string.score_achievement_fastest),
            value = context.getString(R.string.score_bpm_value, it.bpm),
            subtitle = subtitle(it),
            icon = Icons.Default.Speed
        )
    }
    scores.minByOrNull { it.ar ?: Float.MAX_VALUE }?.takeIf { it.ar != null }?.let {
        achievements += ScoreAchievement(
            label = context.getString(R.string.score_achievement_lowest_ar),
            value = context.getString(R.string.score_ar_value, it.ar),
            subtitle = subtitle(it),
            icon = Icons.Default.VisibilityOff
        )
    }
    scores.maxByOrNull { it.cs ?: -1f }?.takeIf { it.cs != null }?.let {
        achievements += ScoreAchievement(
            label = context.getString(R.string.score_achievement_highest_cs),
            value = context.getString(R.string.score_cs_value, it.cs),
            subtitle = subtitle(it),
            icon = Icons.Default.CenterFocusStrong
        )
    }
    scores.maxByOrNull { it.pp ?: -1f }?.takeIf { it.pp != null }?.let {
        val heldDays = daysSince(it.playedAt)
        achievements += ScoreAchievement(
            label = context.getString(R.string.score_achievement_pp_record),
            value = context.getString(R.string.score_pp_value, it.pp),
            subtitle = context.resources.getQuantityString(
                R.plurals.score_pp_record_held,
                heldDays.toInt(),
                heldDays,
                formatPlayedAtTimestamp(it.playedAt)
            ),
            icon = Icons.Default.EmojiEvents
        )
    }
    return achievements
}

private fun List<RecentPlayEntity>.passedScores(): List<RecentPlayEntity> {
    return filter { it.passed == true || (it.rank != null && !it.rank.equals("F", ignoreCase = true)) }
}

private fun List<RecentPlayEntity>.filterByMode(modeFilter: String): List<RecentPlayEntity> {
    if (modeFilter == "all") return this
    return filter { entity ->
        when (modeFilter) {
            "osu" -> entity.mode?.equals("osu", ignoreCase = true) == true
            "taiko" -> entity.mode?.equals("taiko", ignoreCase = true) == true
            "fruits" -> entity.mode?.equals("fruits", ignoreCase = true) == true ||
                entity.mode?.equals("catch", ignoreCase = true) == true
            "mania" -> entity.mode?.equals("mania", ignoreCase = true) == true
            else -> true
        }
    }
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

private fun Color.brighten(amount: Float): Color {
    val clamped = amount.coerceIn(0f, 1f)
    return Color(
        red = red + (1f - red) * clamped,
        green = green + (1f - green) * clamped,
        blue = blue + (1f - blue) * clamped,
        alpha = alpha
    )
}

private fun daysSince(timestamp: Long): Long {
    val playedDate = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    return ChronoUnit.DAYS.between(playedDate, LocalDate.now()).coerceAtLeast(0)
}
