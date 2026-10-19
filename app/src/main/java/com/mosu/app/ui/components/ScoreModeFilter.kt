package com.mosu.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.mosu.app.R

private val ScoreModeIconSize = 36.dp
private val ScoreModeIconGap = 2.dp
private val ScoreModeExpandedMenuHeight = 60.dp
private const val ExpandDurationMillis = 180
private const val CollapseDurationMillis = 140

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
    modifier: Modifier = Modifier,
    minimised: Boolean = false
) {
    if (!minimised) {
        ScoreModeFilterRow(
            selectedMode = selectedMode,
            onModeSelected = onModeSelected,
            modifier = modifier
        )
        return
    }

    var expanded by remember { mutableStateOf(false) }
    val selectedOption = scoreModeOptions.firstOrNull { it.value == selectedMode }
        ?: scoreModeOptions.last()
    val expandedOptions = scoreModeOptions.filterNot { it.value == selectedMode } + selectedOption
    val expandedWidth = ScoreModeIconSize * scoreModeOptions.size +
        ScoreModeIconGap * (scoreModeOptions.size - 1)
    val expandedState = remember { MutableTransitionState(false) }
    expandedState.targetState = expanded

    Box(modifier = modifier) {
        ScoreModeOptionButton(
            option = selectedOption,
            selected = true,
            onClick = { expanded = true }
        )

        if (expandedState.currentState || expandedState.targetState) {
            Popup(
                popupPositionProvider = remember {
                    object : PopupPositionProvider {
                        override fun calculatePosition(
                            anchorBounds: IntRect,
                            windowSize: IntSize,
                            layoutDirection: LayoutDirection,
                            popupContentSize: IntSize
                        ): IntOffset {
                            val x = (anchorBounds.right - popupContentSize.width)
                                .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
                            val y = (anchorBounds.center.y - popupContentSize.height / 2)
                                .coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0))
                            return IntOffset(x, y)
                        }
                    }
                },
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                Box(
                    modifier = Modifier
                        .width(expandedWidth)
                        .height(ScoreModeExpandedMenuHeight),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                expanded = false
                            }
                    )
                    AnimatedVisibility(
                        visibleState = expandedState,
                        enter = expandHorizontally(
                            expandFrom = Alignment.End,
                            animationSpec = tween(ExpandDurationMillis)
                        ),
                        exit = shrinkHorizontally(
                            shrinkTowards = Alignment.End,
                            animationSpec = tween(CollapseDurationMillis)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .height(ScoreModeExpandedMenuHeight)
                                .clip(RoundedCornerShape(ScoreModeExpandedMenuHeight / 2))
                                .background(MaterialTheme.colorScheme.background),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ScoreModeIconGap)
                        ) {
                            expandedOptions.forEach { option ->
                                ScoreModeOptionButton(
                                    option = option,
                                    selected = option.value == selectedMode,
                                    onClick = {
                                        onModeSelected(option.value)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreModeFilterRow(
    selectedMode: String,
    onModeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScoreModeIconGap)
    ) {
        scoreModeOptions.forEach { option ->
            ScoreModeOptionButton(
                option = option,
                selected = option.value == selectedMode,
                onClick = { onModeSelected(option.value) }
            )
        }
    }
}

@Composable
private fun ScoreModeOptionButton(
    option: ScoreModeOption,
    selected: Boolean,
    onClick: () -> Unit
) {
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
            .clickable(onClick = onClick),
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
