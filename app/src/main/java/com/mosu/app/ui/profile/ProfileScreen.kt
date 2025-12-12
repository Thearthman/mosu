package com.mosu.app.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mosu.app.R
import com.mosu.app.data.SettingsManager
import com.mosu.app.data.TokenManager
import com.mosu.app.data.api.model.OsuUserCompact
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.repository.OsuRepository
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    accessToken: String?,
    repository: OsuRepository,
    db: AppDatabase,
    tokenManager: TokenManager,
    settingsManager: SettingsManager,
    onLoginClick: () -> Unit,
    onLogout: () -> Unit
) {
    var userInfo by remember { mutableStateOf<OsuUserCompact?>(null) }
    var totalDownloaded by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    
    // Settings State
    var showSettingsDialog by remember { mutableStateOf(false) }
    val clientId by settingsManager.clientId.collectAsState(initial = "")
    val clientSecret by settingsManager.clientSecret.collectAsState(initial = "")
    val playedFilterMode by settingsManager.playedFilterMode.collectAsState(initial = "url")
    val defaultSearchView by settingsManager.defaultSearchView.collectAsState(initial = "played")
    val searchAnyEnabled by settingsManager.searchAnyEnabled.collectAsState(initial = false)
    val language by settingsManager.language.collectAsState(initial = "en")
    val infoCoverEnabled by settingsManager.infoCoverEnabled.collectAsState(initial = true)
    var languageMenuExpanded by remember { mutableStateOf(false) }
    val languageOptions = listOf(
        "en" to stringResource(R.string.language_english),
        "zh-CN" to stringResource(R.string.language_simplified_chinese),
        "zh-TW" to stringResource(R.string.language_traditional_chinese)
    )
    
    val scope = rememberCoroutineScope()

    // Fetch user info only once when logged in (not on every navigation)
    LaunchedEffect(accessToken) {
        if (accessToken != null && userInfo == null && !isLoading) {
            isLoading = true
            try {
                userInfo = repository.getMe(accessToken)
            } catch (e: Exception) {
                // Handle error
            } finally {
                isLoading = false
            }
        } else if (accessToken == null) {
            // Reset on logout
            userInfo = null
        }
    }
    
    // Fetch downloaded count (separate effect)
    LaunchedEffect(Unit) {
        db.beatmapDao().getAllBeatmaps().collect { maps ->
            totalDownloaded = maps.groupBy { it.beatmapSetId }.size
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.profile_title),
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
        )

        if (accessToken == null) {
            // Not logged in - Show Settings and Login
            
            // OAuth Settings Card - 重新设计为文本在左，按钮在右
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.profile_oauth_settings_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        val clientIdStatus = if (clientId.isNotEmpty()) stringResource(R.string.profile_client_id_configured) else stringResource(R.string.profile_client_id_not_set)
                        Text(stringResource(R.string.profile_client_id_label, clientIdStatus), style = MaterialTheme.typography.bodyMedium)
                        val clientSecretStatus = if (clientSecret.isNotEmpty()) stringResource(R.string.profile_client_secret_configured) else stringResource(R.string.profile_client_secret_not_set)
                        Text(stringResource(R.string.profile_client_secret_label, clientSecretStatus), style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.width(140.dp)
                    ) {
                        Text(stringResource(R.string.profile_configure_credentials))
                    }
                }
            }
            
            // Info Cover Toggle Card (已符合设计)
            InfoCoverToggleCard(infoCoverEnabled = infoCoverEnabled) { checked ->
                scope.launch { settingsManager.saveInfoCoverEnabled(checked) }
            }

            // Language Card - 重新设计为文本在左，下拉菜单在右
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.profile_language_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.profile_language_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Box {
                        OutlinedButton(
                            onClick = { languageMenuExpanded = true },
                            modifier = Modifier.width(120.dp)
                        ) {
                            Text(languageLabel(language), maxLines = 1)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select language")
                        }
                        DropdownMenu(
                            expanded = languageMenuExpanded,
                            onDismissRequest = { languageMenuExpanded = false }
                        ) {
                            languageOptions.forEach { (code, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        scope.launch { settingsManager.saveLanguage(code) }
                                        languageMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            // Login Button
            if (clientId.isNotEmpty() && clientSecret.isNotEmpty()) {
                Button(
                    onClick = onLoginClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.profile_login_button))
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        stringResource(R.string.profile_login_prompt),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        } else {
            // User Info Card (保持不变)
            userInfo?.let { user ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = user.avatarUrl,
                            contentDescription = "Avatar",
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Column(modifier = Modifier.padding(start = 16.dp)) {
                            Text(text = user.username, style = MaterialTheme.typography.titleLarge)
                            Text(text = stringResource(R.string.profile_id_prefix, user.id.toString()), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // Stats Card (保持不变)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.profile_statistics_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.profile_downloaded_songs_label, totalDownloaded), style = MaterialTheme.typography.bodyLarge)
                }
            }

            // Settings Card (Logged in version) - 重新设计为文本在左，按钮在右
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.profile_oauth_settings_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        val clientIdDisplay = if (clientId.isNotEmpty()) "${clientId.take(8)}..." else stringResource(R.string.profile_client_id_not_set)
                        Text(stringResource(R.string.profile_client_id_label, clientIdDisplay), style = MaterialTheme.typography.bodyMedium)
                        val clientSecretDisplay = if (clientSecret.isNotEmpty()) "••••••••" else stringResource(R.string.profile_client_secret_not_set)
                        Text(stringResource(R.string.profile_client_secret_label, clientSecretDisplay), style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.width(140.dp)
                    ) {
                        Text(stringResource(R.string.profile_update_credentials))
                    }
                }
            }

            // Info Cover Toggle Card (已符合设计，使用InfoCoverToggleCard)
            InfoCoverToggleCard(infoCoverEnabled = infoCoverEnabled) { checked ->
                scope.launch { settingsManager.saveInfoCoverEnabled(checked) }
            }
            
            // Default Search View - 重新设计为文本在左，交互元素在右
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            stringResource(R.string.profile_default_search_view_title), 
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.profile_default_search_view_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    
                    val isSupporter = userInfo?.isSupporter ?: false
                    val options = if (isSupporter) {
                        listOf("played", "recent", "favorite", "most_played", "all")
                    } else {
                        listOf("recent", "favorite", "most_played", "all")
                    }
                    var menuExpanded by remember { mutableStateOf(false) }
                    val label = when (defaultSearchView) {
                        "played" -> stringResource(R.string.search_filter_played)
                        "recent" -> stringResource(R.string.search_filter_recent)
                        "favorite" -> stringResource(R.string.search_filter_favorite)
                        "most_played" -> stringResource(R.string.search_filter_most_played)
                        else -> stringResource(R.string.search_filter_all)
                    }
                    
                    Box {
                        OutlinedButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.width(120.dp)
                        ) {
                            Text(label, maxLines = 1)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select default")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            options.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(
                                        when (option) {
                                            "played" -> stringResource(R.string.search_filter_played)
                                            "recent" -> stringResource(R.string.search_filter_recent)
                                            "favorite" -> stringResource(R.string.search_filter_favorite)
                                            "most_played" -> stringResource(R.string.search_filter_most_played)
                                            else -> stringResource(R.string.search_filter_all)
                                        }
                                    ) },
                                    onClick = {
                                        menuExpanded = false
                                        scope.launch { settingsManager.saveDefaultSearchView(option) }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Include Unranked/Loved/Any Status - 重新设计为文本在左，开关在右
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.profile_include_unranked_title), 
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.profile_include_unranked_desc),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            checked = searchAnyEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch { settingsManager.saveSearchAnyEnabled(enabled) }
                            }
                        )
                    }
                }
            }

            // Language Card - 重新设计为文本在左，下拉菜单在右
//            Card(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(bottom = 16.dp),
//            ) {
//                Row(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .padding(16.dp),
//                    horizontalArrangement = Arrangement.SpaceBetween,
//                    verticalAlignment = Alignment.CenterVertically
//                ) {
//                    Column(modifier = Modifier.weight(1f)) {
//                        Text(stringResource(R.string.profile_language_title), style = MaterialTheme.typography.titleMedium)
//                        Spacer(modifier = Modifier.height(4.dp))
//                        Text(
//                            stringResource(R.string.profile_language_desc),
//                            style = MaterialTheme.typography.bodySmall,
//                            color = MaterialTheme.colorScheme.secondary
//                        )
//                    }
//                    Spacer(modifier = Modifier.width(16.dp))
//                    Box {
//                        OutlinedButton(
//                            onClick = { languageMenuExpanded = true },
//                            modifier = Modifier.width(120.dp)
//                        ) {
//                            Text(languageLabel(language), maxLines = 1)
//                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select language")
//                        }
//                        DropdownMenu(
//                            expanded = languageMenuExpanded,
//                            onDismissRequest = { languageMenuExpanded = false }
//                        ) {
//                            languageOptions.forEach { (code, label) ->
//                                DropdownMenuItem(
//                                    text = { Text(label) },
//                                    onClick = {
//                                        scope.launch { settingsManager.saveLanguage(code) }
//                                        languageMenuExpanded = false
//                                    }
//                                )
//                            }
//                        }
//                    }
//                }
//            }

            // Placeholder Cards
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.profile_equalizer_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.profile_coming_soon), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.profile_recent_activity_heatmap_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.profile_coming_soon), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                }
            }

            // Logout Button
            Button(
                onClick = {
                    scope.launch {
                        tokenManager.clearToken()
                        onLogout()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFFFF3B30), // Bright red
                    contentColor = androidx.compose.ui.graphics.Color.White
                )
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = stringResource(R.string.profile_logout_button))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.profile_logout_button))
            }
        }
    }
    
    // Settings Dialog
    if (showSettingsDialog) {
        var inputClientId by remember { mutableStateOf(clientId) }
        var inputClientSecret by remember { mutableStateOf(clientSecret) }
        
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text(stringResource(R.string.profile_oauth_dialog_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.profile_oauth_dialog_message), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputClientId,
                        onValueChange = { inputClientId = it },
                        label = { Text(stringResource(R.string.profile_oauth_dialog_client_id_hint)) },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputClientSecret,
                        onValueChange = { inputClientSecret = it },
                        label = { Text(stringResource(R.string.profile_oauth_dialog_client_secret_hint)) },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.profile_oauth_dialog_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            settingsManager.saveCredentials(inputClientId, inputClientSecret)
                            showSettingsDialog = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.profile_oauth_dialog_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text(stringResource(R.string.profile_oauth_dialog_cancel))
                }
            }
        )
    }
}

@Composable
private fun InfoCoverToggleCard(
    infoCoverEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(stringResource(id = R.string.info_cover_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(id = R.string.info_cover_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Switch(
                checked = infoCoverEnabled,
                onCheckedChange = onToggle
            )
        }
    }
}
@Composable
private fun languageLabel(code: String): String = when (code) {
    "zh-CN" -> stringResource(R.string.language_simplified_chinese)
    "zh-TW" -> stringResource(R.string.language_traditional_chinese)
    else -> stringResource(R.string.language_english)
}

