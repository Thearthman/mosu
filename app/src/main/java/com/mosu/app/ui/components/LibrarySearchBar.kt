package com.mosu.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mosu.app.R

/**
 * The collapsible Library search field used by both layouts.
 *
 * The field starts as a square search button and grows to [expandedWidth], so when
 * it is anchored to the right it opens leftward. The search icon fades out while
 * the field grows and reappears as it shrinks back to the button.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibrarySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    expandedWidth: Dp,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    collapsedWidth: Dp = 50.dp,
    height: Dp = 50.dp
) {
    val focusManager = LocalFocusManager.current
    val currentExpanded by rememberUpdatedState(expanded)
    val currentOnExpandedChange by rememberUpdatedState(onExpandedChange)
    val searchBarWidth by animateDpAsState(
        targetValue = if (expanded || query.isNotEmpty()) expandedWidth else collapsedWidth,
        animationSpec = tween(durationMillis = 300),
        label = "librarySearchBarWidth"
    )

    fun dismissSearch() {
        currentOnExpandedChange(false)
        focusManager.clearFocus()
    }

    // Match portrait Library search behavior: dismissing the IME collapses the
    // empty search field. Deliberately key this effect on IME/query (not the
    // expanded flag) so opening the field is not immediately undone before the
    // keyboard becomes visible.
    val isKeyboardVisible = WindowInsets.isImeVisible
    LaunchedEffect(isKeyboardVisible, query) {
        if (!isKeyboardVisible && query.isEmpty() && currentExpanded) {
            dismissSearch()
        }
    }

    BackHandler(enabled = expanded) {
        dismissSearch()
    }

    Box(
        modifier = modifier
            .width(searchBarWidth)
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        BasicTextField(
            value = query,
            onValueChange = {
                onQueryChange(it)
                if (it.isNotEmpty()) {
                    onExpandedChange(true)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        onExpandedChange(true)
                    }
                }
                .focusRequester(focusRequester)
                .padding(
                    start = if (searchBarWidth >= 120.dp) 18.dp else 10.dp,
                    end = 30.dp,
                    bottom = 1.dp
                ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {}),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.isEmpty() && searchBarWidth >= 125.dp) {
                        Text(
                            text = stringResource(id = R.string.library_search_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
            }
        )

        if (searchBarWidth < 120.dp) {
            val iconAlpha = 1f - (
                (searchBarWidth - collapsedWidth) / (120.dp - collapsedWidth)
                ).coerceIn(0f, 1f)
            IconButton(
                onClick = {
                    if (!expanded) {
                        onExpandedChange(true)
                        focusRequester.requestFocus()
                    }
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(24.dp)
                    .zIndex(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.search_cd_search),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = iconAlpha)
                )
            }
        }

        if (query.isNotEmpty()) {
            IconButton(
                onClick = { onQueryChange("") },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp)
                    .size(24.dp)
                    .zIndex(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = stringResource(id = R.string.search_cd_clear),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
