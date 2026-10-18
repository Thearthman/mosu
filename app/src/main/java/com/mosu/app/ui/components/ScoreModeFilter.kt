package com.mosu.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mosu.app.R

private val ScoreModeIconSize = 36.dp

private data class ScoreModeOption(
    val value: String,
    val labelRes: Int,
    val iconRes: Int? = null,
    val imageVector: ImageVector? = null
)

private val scoreModeOptions = listOf(
    ScoreModeOption("osu", R.string.score_mode_osu, iconRes = R.drawable.std_icon),
    ScoreModeOption("taiko", R.string.score_mode_taiko, iconRes = R.drawable.taiko_icon),
    ScoreModeOption("fruits", R.string.score_mode_catch, iconRes = R.drawable.cth_icon),
    ScoreModeOption("mania", R.string.score_mode_mania, iconRes = R.drawable.mania_icon),
    ScoreModeOption("all", R.string.score_mode_all, imageVector = Icons.Default.Apps)
)

@Composable
fun ScoreModeFilterIcons(
    selectedMode: String,
    onModeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        scoreModeOptions.forEach { option ->
            val selected = option.value == selectedMode
            val tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Box(
                modifier = Modifier
                    .size(ScoreModeIconSize)
                    .clip(CircleShape)
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        } else {
                            Color.Transparent
                        }
                    )
                    .clickable { onModeSelected(option.value) },
                contentAlignment = Alignment.Center
            ) {
                when {
                    option.iconRes != null -> Icon(
                        painter = painterResource(option.iconRes),
                        contentDescription = stringResource(option.labelRes),
                        tint = tint,
                        modifier = Modifier.size(22.dp)
                    )
                    option.imageVector != null -> Icon(
                        imageVector = option.imageVector,
                        contentDescription = stringResource(option.labelRes),
                        tint = tint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
