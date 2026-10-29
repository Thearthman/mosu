package com.mosu.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.mosu.app.R
import kotlin.math.roundToInt

data class PillMenuItem<T>(
    val value: T,
    val label: String? = null,
    val contentDescription: String = label.orEmpty(),
    val imageVector: ImageVector? = null,
    val iconRes: Int? = null
)

private val DefaultTriggerSize = 48.dp
private val MinPillMenuItemWidth = 36.dp
private val MaxPillMenuItemWidth = 48.dp
private val PillMenuGap = 1.dp
private const val ExpandDurationMillis = 180
private const val CollapseDurationMillis = 140

/**
 * A pill trigger that expands into a selectable slide-out menu.
 *
 * Selecting items updates [onSelectionChanged] without dismissing the menu. Clearing the
 * selection updates the filter and closes the popup. An empty [selectedValues] set is a
 * convenient way to represent "no filter".
 */
@Composable
fun <T> PillSlideOutMenu(
    items: List<PillMenuItem<T>>,
    selectedValues: Set<T>,
    onSelectionChanged: (Set<T>) -> Unit,
    modifier: Modifier = Modifier,
    collapsedContent: @Composable () -> Unit,
    multiSelect: Boolean = true,
    showClearAction: Boolean = true,
    triggerAlignment: Alignment = Alignment.Center,
    triggerTouchSize: Dp = DefaultTriggerSize,
    triggerShape: Shape = CircleShape,
    maxExpandedWidth: Dp? = null,
    expandedWindowInset: Dp = 0.dp,
    expandedContainerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    onExpandedChange: (Boolean) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        onExpandedChange(expanded)
    }
    val expandedState = remember { MutableTransitionState(false) }
    expandedState.targetState = expanded
    val expandedDescription = stringResource(R.string.pill_menu_expanded)
    val collapsedDescription = stringResource(R.string.pill_menu_collapsed)

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val screenWidthPx = with(LocalDensity.current) {
        screenWidth.roundToPx()
    }
    val density = LocalDensity.current
    val expandedWindowInsetPx = with(density) { expandedWindowInset.roundToPx() }
    val layoutDirection = LocalLayoutDirection.current
    var expandsFromStart by remember { mutableStateOf(true) }
    var triggerLeftPx by remember { mutableStateOf(0) }
    var triggerRightPx by remember { mutableStateOf(0) }
    val screenMaxWidth = (screenWidth - 24.dp).coerceAtLeast(triggerTouchSize)
    val menuItemWidth = triggerTouchSize.coerceIn(
        minimumValue = MinPillMenuItemWidth,
        maximumValue = MaxPillMenuItemWidth
    )
    val anchorMaxWidth = if (triggerRightPx > triggerLeftPx) {
        val availableWidthPx = if (expandsFromStart) {
            screenWidthPx - triggerLeftPx - expandedWindowInsetPx
        } else {
            triggerRightPx - expandedWindowInsetPx
        }
        with(density) { availableWidthPx.coerceAtLeast(0).toDp() }
    } else {
        screenMaxWidth
    }
    val horizontalMenuWidth = pillMenuExpandedWidth(
        triggerSize = triggerTouchSize,
        itemCount = items.size,
        showClearAction = showClearAction
    )
        .coerceAtMost(maxExpandedWidth?.coerceAtMost(screenMaxWidth) ?: screenMaxWidth)
        .coerceAtMost(anchorMaxWidth)
        .coerceAtLeast(triggerTouchSize)
    Box(modifier = modifier, contentAlignment = triggerAlignment) {
        Box(
            modifier = Modifier.onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                triggerLeftPx = bounds.left.roundToInt()
                triggerRightPx = bounds.right.roundToInt()
                val isPhysicallyOnLeft =
                    bounds.center.x < screenWidthPx / 2
                expandsFromStart = if (layoutDirection == LayoutDirection.Ltr) {
                    isPhysicallyOnLeft
                } else {
                    !isPhysicallyOnLeft
                }
            },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(triggerShape)
                    .clickable(
                        role = Role.Button,
                        onClick = { expanded = !expanded }
                    )
                    .size(triggerTouchSize)
                    .semantics(mergeDescendants = true) {
                        stateDescription = if (expanded) expandedDescription else collapsedDescription
                    },
                contentAlignment = Alignment.Center
            ) {
                collapsedContent()
            }

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
                                val x = if (anchorBounds.center.x < windowSize.width / 2) {
                                    anchorBounds.left
                                } else {
                                    anchorBounds.right - popupContentSize.width
                                }.coerceIn(
                                    0,
                                    (windowSize.width - popupContentSize.width).coerceAtLeast(0)
                                )
                                val y = (anchorBounds.center.y - popupContentSize.height / 2)
                                    .coerceIn(
                                    0,
                                    (windowSize.height - popupContentSize.height).coerceAtLeast(0)
                                )
                                return IntOffset(x, y)
                            }
                        }
                    },
                    onDismissRequest = { expanded = false },
                    properties = PopupProperties(focusable = true)
                ) {
                    val horizontalExpandFrom: Alignment.Horizontal =
                        if (expandsFromStart) Alignment.Start else Alignment.End
                    Box(
                        modifier = Modifier
                            .width(horizontalMenuWidth)
                            .height(triggerTouchSize),
                        contentAlignment = if (expandsFromStart) {
                            Alignment.CenterStart
                        } else {
                            Alignment.CenterEnd
                        }
                    ) {
                        AnimatedVisibility(
                            visibleState = expandedState,
                            enter = expandHorizontally(
                                expandFrom = horizontalExpandFrom,
                                animationSpec = tween(ExpandDurationMillis)
                            ),
                            exit = shrinkHorizontally(
                                shrinkTowards = horizontalExpandFrom,
                                animationSpec = tween(CollapseDurationMillis)
                            )
                        ) {
                            PillMenuRow(
                                items = items,
                                selectedValues = selectedValues,
                                onSelectionChanged = onSelectionChanged,
                                multiSelect = multiSelect,
                                showClearAction = showClearAction,
                                expandsFromStart = expandsFromStart,
                                itemWidth = menuItemWidth,
                                height = triggerTouchSize,
                                containerColor = expandedContainerColor,
                                onClear = {
                                    onSelectionChanged(emptySet())
                                    expanded = false
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun pillMenuExpandedWidth(
    triggerSize: Dp,
    itemCount: Int,
    showClearAction: Boolean = true
): Dp {
    val itemWidth = triggerSize.coerceIn(
        minimumValue = MinPillMenuItemWidth,
        maximumValue = MaxPillMenuItemWidth
    )
    return itemWidth * itemCount +
        PillMenuGap * (itemCount - 1).coerceAtLeast(0) +
        if (showClearAction) triggerSize + PillMenuGap else 0.dp
}

@Composable
internal fun <T> PillMenuRow(
    items: List<PillMenuItem<T>>,
    selectedValues: Set<T>,
    onSelectionChanged: (Set<T>) -> Unit,
    multiSelect: Boolean,
    showClearAction: Boolean,
    expandsFromStart: Boolean,
    itemWidth: Dp,
    height: Dp,
    containerColor: Color,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    BoxWithConstraints(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(containerColor)
    ) {
        val clearWidth = if (showClearAction) height + PillMenuGap else 0.dp
        val itemRowWidth = (maxWidth - clearWidth).coerceAtLeast(0.dp)
        val itemContentWidth =
            itemWidth * items.size + PillMenuGap * (items.size - 1).coerceAtLeast(0)
        val distributeEvenly = itemContentWidth <= itemRowWidth

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PillMenuGap)
        ) {
            if (showClearAction && expandsFromStart) {
                PillMenuControl(
                    selected = false,
                    onClick = onClear,
                    contentDescription = stringResource(R.string.pill_menu_clear),
                    width = height,
                    height = height
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (distributeEvenly) {
                            Modifier
                        } else {
                            Modifier.horizontalScroll(scrollState)
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (distributeEvenly) {
                    Arrangement.SpaceEvenly
                } else {
                    Arrangement.spacedBy(
                        PillMenuGap,
                        if (expandsFromStart) Alignment.Start else Alignment.End
                    )
                }
            ) {
                items.forEach { item ->
                    PillMenuControl(
                        selected = item.value in selectedValues,
                        onClick = {
                            onSelectionChanged(
                                nextSelection(
                                    item = item.value,
                                    selectedValues = selectedValues,
                                    multiSelect = multiSelect
                                )
                            )
                        },
                        contentDescription = item.contentDescription,
                        width = itemWidth,
                        height = height
                    ) {
                        PillMenuItemContent(item)
                    }
                }
            }

            if (showClearAction && !expandsFromStart) {
                PillMenuControl(
                    selected = false,
                    onClick = onClear,
                    contentDescription = stringResource(R.string.pill_menu_clear),
                    width = height,
                    height = height
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> PillMenuItemContent(item: PillMenuItem<T>) {
    when {
        item.imageVector != null -> Icon(
            imageVector = item.imageVector,
            contentDescription = null,
            modifier = Modifier.size(22.dp)
        )
        item.iconRes != null -> Icon(
            painter = painterResource(item.iconRes),
            contentDescription = null,
            modifier = Modifier.size(22.dp)
        )
        item.label != null -> Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
private fun PillMenuControl(
    selected: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    width: Dp = MaxPillMenuItemWidth,
    height: Dp = DefaultTriggerSize,
    content: @Composable () -> Unit
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .size(width = width, height = height)
            .clip(CircleShape)
            .semantics {
                this.contentDescription = contentDescription
                this.selected = selected
            }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(minOf(width, height))
                .clip(CircleShape)
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    } else {
                        Color.Transparent
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                content()
            }
        }
    }
}

private fun <T> nextSelection(
    item: T,
    selectedValues: Set<T>,
    multiSelect: Boolean
): Set<T> {
    if (!multiSelect) {
        return if (item in selectedValues) emptySet() else setOf(item)
    }
    return if (item in selectedValues) {
        selectedValues - item
    } else {
        selectedValues + item
    }
}
