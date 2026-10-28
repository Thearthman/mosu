package com.mosu.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

enum class PillMenuDirection {
    Horizontal,
    Vertical
}

data class PillMenuItem<T>(
    val value: T,
    val label: String? = null,
    val contentDescription: String = label.orEmpty(),
    val imageVector: ImageVector? = null,
    val iconRes: Int? = null
)

private val PillMenuItemTouchSize = 48.dp
private val PillMenuItemVisualSize = 36.dp
private val PillMenuGap = 2.dp
private val PillMenuPadding = 8.dp
private val HorizontalPillMenuHeight = 60.dp
private val VerticalPillMenuWidth = 56.dp
private val VerticalPillMenuCornerRadius = 28.dp
private const val ExpandDurationMillis = 180
private const val CollapseDurationMillis = 140

/**
 * A pill trigger that expands into a selectable slide-out menu.
 *
 * Selecting or clearing items updates [onSelectionChanged] without dismissing the menu.
 * The popup closes only when it is dismissed by an outside click or the system back action.
 * An empty [selectedValues] set is a convenient way to represent "no filter".
 */
@Composable
fun <T> PillSlideOutMenu(
    items: List<PillMenuItem<T>>,
    selectedValues: Set<T>,
    onSelectionChanged: (Set<T>) -> Unit,
    modifier: Modifier = Modifier,
    collapsedContent: @Composable () -> Unit,
    direction: PillMenuDirection = PillMenuDirection.Horizontal,
    multiSelect: Boolean = true,
    expandedMenuWidth: Dp? = null,
    showClearAction: Boolean = true,
    triggerAlignment: Alignment = Alignment.Center
) {
    var expanded by remember { mutableStateOf(false) }
    val expandedState = remember { MutableTransitionState(false) }
    expandedState.targetState = expanded
    val expandedDescription = stringResource(R.string.pill_menu_expanded)
    val collapsedDescription = stringResource(R.string.pill_menu_collapsed)

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val screenWidthPx = with(LocalDensity.current) {
        screenWidth.roundToPx()
    }
    val layoutDirection = LocalLayoutDirection.current
    var expandsFromStart by remember { mutableStateOf(true) }
    val horizontalMenuWidth = (
        PillMenuItemTouchSize * items.size +
            PillMenuGap * (items.size - 1).coerceAtLeast(0) +
            if (showClearAction) PillMenuItemTouchSize + PillMenuGap else 0.dp
        ).coerceAtMost((screenWidth - 24.dp).coerceAtLeast(PillMenuItemTouchSize))
    val menuWidth = when (direction) {
        PillMenuDirection.Horizontal -> horizontalMenuWidth
        PillMenuDirection.Vertical -> expandedMenuWidth ?: VerticalPillMenuWidth
    }
    val maxVerticalMenuHeight = (screenHeight * 0.78f).coerceAtLeast(120.dp)

    Box(modifier = modifier, contentAlignment = triggerAlignment) {
        Box(
            modifier = Modifier.onGloballyPositioned { coordinates ->
                val isPhysicallyOnLeft =
                    coordinates.boundsInWindow().center.x < screenWidthPx / 2
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
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable(
                        role = Role.Button,
                        onClick = { expanded = !expanded }
                    )
                    .sizeIn(minWidth = PillMenuItemTouchSize, minHeight = PillMenuItemTouchSize)
                    .semantics(mergeDescendants = true) {
                        stateDescription = if (expanded) expandedDescription else collapsedDescription
                    },
                contentAlignment = Alignment.Center
            ) {
                collapsedContent()
            }

            if (expandedState.currentState || expandedState.targetState) {
                Popup(
                    popupPositionProvider = remember(direction) {
                        object : PopupPositionProvider {
                            override fun calculatePosition(
                                anchorBounds: IntRect,
                                windowSize: IntSize,
                                layoutDirection: LayoutDirection,
                                popupContentSize: IntSize
                            ): IntOffset {
                                val x = when (direction) {
                                    PillMenuDirection.Horizontal -> {
                                        if (anchorBounds.center.x < windowSize.width / 2) {
                                            anchorBounds.left
                                        } else {
                                            anchorBounds.right - popupContentSize.width
                                        }
                                    }
                                    PillMenuDirection.Vertical -> {
                                        if (layoutDirection == LayoutDirection.Rtl) {
                                            anchorBounds.left - popupContentSize.width - 4
                                        } else {
                                            anchorBounds.right + 4
                                        }
                                    }
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
                            .width(menuWidth)
                            .then(
                                if (direction == PillMenuDirection.Horizontal) {
                                    Modifier.height(HorizontalPillMenuHeight)
                                } else {
                                    Modifier.heightIn(max = maxVerticalMenuHeight)
                                }
                            ),
                        contentAlignment = when (direction) {
                            PillMenuDirection.Horizontal -> {
                                if (expandsFromStart) Alignment.CenterStart else Alignment.CenterEnd
                            }
                            PillMenuDirection.Vertical -> Alignment.CenterStart
                        }
                    ) {
                        AnimatedVisibility(
                            visibleState = expandedState,
                            enter = when (direction) {
                                PillMenuDirection.Horizontal -> expandHorizontally(
                                    expandFrom = horizontalExpandFrom,
                                    animationSpec = tween(ExpandDurationMillis)
                                )
                                PillMenuDirection.Vertical -> expandVertically(
                                    expandFrom = Alignment.CenterVertically,
                                    animationSpec = tween(ExpandDurationMillis)
                                )
                            },
                            exit = when (direction) {
                                PillMenuDirection.Horizontal -> shrinkHorizontally(
                                    shrinkTowards = horizontalExpandFrom,
                                    animationSpec = tween(CollapseDurationMillis)
                                )
                                PillMenuDirection.Vertical -> shrinkVertically(
                                    shrinkTowards = Alignment.CenterVertically,
                                    animationSpec = tween(CollapseDurationMillis)
                                )
                            }
                        ) {
                            when (direction) {
                                PillMenuDirection.Horizontal -> HorizontalMenu(
                                    items = items,
                                    selectedValues = selectedValues,
                                    onSelectionChanged = onSelectionChanged,
                                    multiSelect = multiSelect,
                                    showClearAction = showClearAction
                                )
                                PillMenuDirection.Vertical -> VerticalMenu(
                                    items = items,
                                    selectedValues = selectedValues,
                                    onSelectionChanged = onSelectionChanged,
                                    multiSelect = multiSelect,
                                    showClearAction = showClearAction,
                                    maxHeight = maxVerticalMenuHeight
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
private fun <T> HorizontalMenu(
    items: List<PillMenuItem<T>>,
    selectedValues: Set<T>,
    onSelectionChanged: (Set<T>) -> Unit,
    multiSelect: Boolean,
    showClearAction: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(HorizontalPillMenuHeight)
            .clip(RoundedCornerShape(HorizontalPillMenuHeight / 2))
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = PillMenuPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PillMenuGap)
    ) {
        if (showClearAction) {
            PillMenuControl(
                selected = false,
                onClick = { onSelectionChanged(emptySet()) },
                contentDescription = stringResource(R.string.pill_menu_clear)
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
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
                contentDescription = item.contentDescription
            ) {
                PillMenuItemContent(item)
            }
        }
    }
}

@Composable
private fun <T> VerticalMenu(
    items: List<PillMenuItem<T>>,
    selectedValues: Set<T>,
    onSelectionChanged: (Set<T>) -> Unit,
    multiSelect: Boolean,
    showClearAction: Boolean,
    maxHeight: Dp
) {
    Column(
        modifier = Modifier
            .heightIn(max = maxHeight)
            .clip(RoundedCornerShape(VerticalPillMenuCornerRadius))
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PillMenuGap)
    ) {
        if (showClearAction) {
            PillMenuControl(
                selected = false,
                onClick = { onSelectionChanged(emptySet()) },
                contentDescription = stringResource(R.string.pill_menu_clear),
                fillWidth = true
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
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
                fillWidth = true
            ) {
                PillMenuItemContent(item)
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
    fillWidth: Boolean = false,
    content: @Composable () -> Unit
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .then(
                if (fillWidth) {
                    Modifier.fillMaxWidth().height(PillMenuItemTouchSize)
                } else {
                    Modifier.size(PillMenuItemTouchSize)
                }
            )
            .clip(if (fillWidth) RoundedCornerShape(24.dp) else CircleShape)
            .semantics {
                this.contentDescription = contentDescription
                this.selected = selected
            }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .then(
                    if (fillWidth) {
                        Modifier.width(PillMenuItemVisualSize).height(PillMenuItemVisualSize)
                    } else {
                        Modifier.size(PillMenuItemVisualSize)
                    }
                )
                .clip(if (fillWidth) RoundedCornerShape(18.dp) else CircleShape)
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
