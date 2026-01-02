package com.mosu.app.ui.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mosu.app.R
import com.mosu.app.data.AccountManager
import com.mosu.app.data.SettingsManager
import com.mosu.app.data.TokenManager
import com.mosu.app.data.api.model.OsuUserCompact
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.repository.OsuRepository
import com.mosu.app.domain.download.BeatmapDownloader
import com.mosu.app.domain.download.ZipExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

suspend fun performRestore(
    context: Context,
    repository: OsuRepository,
    db: AppDatabase,
    tokenManager: TokenManager,
    updateProgress: (Int, String) -> Unit,
    updateRestoring: (Boolean) -> Unit
) {
    try {
        updateRestoring(true)
        updateProgress(0, "Starting restore...")

        val preservedSetIds = db.preservedBeatmapSetIdDao().getAllPreservedSetIds().firstOrNull() ?: emptyList()
        if (preservedSetIds.isEmpty()) {
            android.widget.Toast.makeText(context, "No beatmaps to restore", android.widget.Toast.LENGTH_SHORT).show()
            updateRestoring(false)
            return
        }

        val downloader = BeatmapDownloader(context)
        val extractor = ZipExtractor(context)
        val accessToken = tokenManager.getCurrentAccessToken()

        var completed = 0
        val total = preservedSetIds.size

        for (preservedSetId in preservedSetIds) {
            try {
                updateProgress(((completed * 100) / total), "Downloading beatmap ${preservedSetId.beatmapSetId}...")

                downloader.downloadBeatmap(preservedSetId.beatmapSetId, accessToken)
                    .collect { state ->
                        when (state) {
                            is com.mosu.app.domain.download.DownloadState.Downloading -> {
                                // Update progress within current beatmap
                            }
                            is com.mosu.app.domain.download.DownloadState.Downloaded -> {
                                updateProgress(((completed * 100) / total), "Extracting beatmap ${preservedSetId.beatmapSetId}...")
                                try {
                                    val extractedTracks = extractor.extractBeatmap(state.file, preservedSetId.beatmapSetId)

                                    // For each track, create beatmap entity and save
                                    extractedTracks.forEach { track ->
                                        val entity = com.mosu.app.data.db.BeatmapEntity(
                                            beatmapSetId = preservedSetId.beatmapSetId,
                                            title = track.title,
                                            artist = track.artist,
                                            creator = "", // We don't have creator info from preserved data
                                            difficultyName = track.difficultyName,
                                            audioPath = track.audioFile.absolutePath,
                                            coverPath = track.coverFile?.absolutePath ?: "",
                                            genreId = null // We don't have genre info from preserved data
                                        )
                                        db.beatmapDao().insertBeatmap(entity)
                                    }

                                    completed++
                                    val progress = (completed * 100) / total
                                    updateProgress(progress, "Restored $completed/$total beatmaps")

                                } catch (e: Exception) {
                                    android.util.Log.e("ProfileScreen", "Failed to extract beatmap ${preservedSetId.beatmapSetId}", e)
                                }
                            }
                            is com.mosu.app.domain.download.DownloadState.Error -> {
                                android.util.Log.e("ProfileScreen", "Failed to download beatmap ${preservedSetId.beatmapSetId}: ${state.message}")
                            }
                            else -> {}
                        }
                    }
            } catch (e: Exception) {
                android.util.Log.e("ProfileScreen", "Failed to restore beatmap ${preservedSetId.beatmapSetId}", e)
            }
        }

        updateProgress(100, "Restore completed! Restored $completed/$total beatmaps")
        android.widget.Toast.makeText(context, "Restore completed! Restored $completed beatmaps", android.widget.Toast.LENGTH_LONG).show()

    } catch (e: Exception) {
        android.util.Log.e("ProfileScreen", "Restore failed", e)
        updateProgress(0, "Restore failed: ${e.message}")
        android.widget.Toast.makeText(context, "Restore failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
    } finally {
        updateRestoring(false)
    }
}

@Composable
fun ProfileScreen(
    accessToken: String?,
    currentAccountId: String,
    repository: OsuRepository,
    db: AppDatabase,
    tokenManager: TokenManager,
    accountManager: AccountManager,
    settingsManager: SettingsManager,
    onLoginClick: () -> Unit,
    onLogout: () -> Unit
) {
    var userInfo by remember { mutableStateOf<OsuUserCompact?>(null) }
    var totalDownloaded by remember { mutableStateOf(0) }
    var preservedCount by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var restoreProgress by remember { mutableStateOf(0) }
    var restoreMessage by remember { mutableStateOf("") }
    
    // Settings State
    var showSettingsDialog by remember { mutableStateOf(false) }
    val clientId by settingsManager.clientId.collectAsState(initial = "")
    val clientSecret by settingsManager.clientSecret.collectAsState(initial = "")
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
    
    // Token debug dialog state
    var showTokenDialog by remember { mutableStateOf(false) }
    var currentToken by remember { mutableStateOf<String?>(null) }

    // Account management state
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var showAccountSwitcher by remember { mutableStateOf(false) }
    var newClientId by remember { mutableStateOf("") }
    var newClientSecret by remember { mutableStateOf("") }

    // Available accounts
    val availableAccounts by accountManager.accounts.collectAsState(initial = emptyList())
    
    val scope = rememberCoroutineScope()

    // Load cached user info immediately, update in background
    LaunchedEffect(currentAccountId) {
        if (accessToken != null) {
            // First, try to get cached user info immediately
            val cachedInfo = accountManager.getCachedUserInfo(currentAccountId)
            if (cachedInfo != null) {
                userInfo = cachedInfo
                // Update in background
                withContext(Dispatchers.IO) {
                    try {
                        val freshInfo = repository.getMe()
                        // Only update UI if data actually changed
                        if (freshInfo.username != cachedInfo.username ||
                            freshInfo.avatarUrl != cachedInfo.avatarUrl) {
                            accountManager.saveUserInfo(currentAccountId, freshInfo)
                            // Update UI on main thread
                            withContext(Dispatchers.Main) {
                                userInfo = freshInfo
                            }
                        }
                    } catch (e: Exception) {
                        // Background update failed, keep cached data
                    }
                }
            } else {
                // No cache, fetch fresh data
                isLoading = true
                try {
                    val freshInfo = repository.getMe()
                    accountManager.saveUserInfo(currentAccountId, freshInfo)
                    userInfo = freshInfo
                } catch (e: Exception) {
                    android.util.Log.e("ProfileScreen", "Failed to fetch user info", e)
                    userInfo = null
                } finally {
                    isLoading = false
                }
            }
        } else {
            userInfo = null
        }
    }
    
    // Fetch downloaded count (separate effect)
    LaunchedEffect(Unit) {
        db.beatmapDao().getAllBeatmaps().collect { maps ->
            totalDownloaded = maps.groupBy { it.beatmapSetId }.size
        }
    }

    val context = LocalContext.current

    // Fetch preserved count and sync with SharedPreferences backup (separate effect)
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("preserved_beatmaps", Context.MODE_PRIVATE)
        val preservedSetIdsKey = "preserved_set_ids"

        // Load preserved IDs from SharedPreferences (survives database migrations)
        val preservedSetIdsFromPrefs = prefs.getStringSet(preservedSetIdsKey, emptySet()) ?: emptySet()
        val preservedSetIdsLong = preservedSetIdsFromPrefs.mapNotNull { it.toLongOrNull() }

        // Check if preserved list has been initialized before
        val initializedKey = "preserved_list_initialized"
        val isInitialized = prefs.getBoolean(initializedKey, false)

        // Sync database with SharedPreferences backup (only add missing items, never remove)
        val currentPreserved = db.preservedBeatmapSetIdDao().getAllPreservedSetIds().firstOrNull() ?: emptyList()
        val currentPreservedIds = currentPreserved.map { it.beatmapSetId }.toSet()

        // If database is missing some preserved IDs from prefs, restore them (conservative add-only)
        val missingIds = preservedSetIdsLong.filter { it !in currentPreservedIds }
        missingIds.forEach { setId ->
            db.preservedBeatmapSetIdDao().insertPreservedSetId(
                com.mosu.app.data.db.PreservedBeatmapSetIdEntity(beatmapSetId = setId)
            )
        }

        // Only initialize on TRUE first launch (never overwrite existing data)
        if (!isInitialized && preservedSetIdsLong.isEmpty()) {
            val currentBeatmaps = db.beatmapDao().getAllBeatmaps().firstOrNull() ?: emptyList()
            if (currentBeatmaps.isNotEmpty()) {
                val currentSetIds = currentBeatmaps.map { it.beatmapSetId }.distinct()
                val setIdsString = currentSetIds.map { it.toString() }.toSet()

                // Save to SharedPreferences
                prefs.edit()
                    .putStringSet(preservedSetIdsKey, setIdsString)
                    .putBoolean(initializedKey, true)
                    .apply()

                // Save to database
                currentSetIds.forEach { setId ->
                    db.preservedBeatmapSetIdDao().insertPreservedSetId(
                        com.mosu.app.data.db.PreservedBeatmapSetIdEntity(beatmapSetId = setId)
                    )
                }
            }
        }

        db.preservedBeatmapSetIdDao().getPreservedCount().collect { count ->
            preservedCount = count
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
            
            // OAuth Settings Card - Fixed layout with consistent max width for text
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(0.7f)
                    ) {
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Text(
                            stringResource(R.string.profile_configure_credentials),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            
            // Info Cover Toggle Card (已符合设计)
            InfoCoverToggleCard(infoCoverEnabled = infoCoverEnabled) { checked ->
                scope.launch { settingsManager.saveInfoCoverEnabled(checked) }
            }

            // Language Card - Fixed layout with consistent max width for text
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(0.7f)
                    ) {
                        Text(stringResource(R.string.profile_language_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.profile_language_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        OutlinedButton(
                            onClick = { languageMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(languageLabel(language), maxLines = 1, modifier = Modifier.weight(1f))
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
            // User Info Card (clickable for token debug)
            userInfo?.let { user ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .clickable {
                            scope.launch {
                                currentToken = tokenManager.getCurrentAccessToken()
                                showTokenDialog = true
                            }
                        }
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
                                .clip(CircleShape)
                                .clickable {
                                    if (availableAccounts.size > 1) {
                                        showAccountSwitcher = true
                                    }
                                },
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


            // Settings Card (Logged in version) - Fixed layout with consistent max width for text
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(0.7f)
                    ) {
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Text(
                            stringResource(R.string.profile_update_credentials),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Info Cover Toggle Card (已符合设计，使用InfoCoverToggleCard)
            InfoCoverToggleCard(infoCoverEnabled = infoCoverEnabled) { checked ->
                scope.launch { settingsManager.saveInfoCoverEnabled(checked) }
            }
            
            // Default Search View - Fixed layout with consistent max width for text
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(0.7f)
                    ) {
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
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        OutlinedButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(label, maxLines = 1, modifier = Modifier.weight(1f))
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

            // Include Unranked/Loved/Any Status - Fixed layout with consistent max width for text
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(0.7f)
                    ) {
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

            // Restore Downloads Card (only show if there are preserved beatmapSetIds)
            if (preservedCount > 0) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(0.7f)
                        ) {
                            Text(stringResource(R.string.profile_restore_downloads_title), style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append("$preservedCount")
                                    }
                                    append(" previously downloaded beatmaps after database reset")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(
                            onClick = {
                                if (!isRestoring) {
                                    scope.launch {
                                        performRestore(
                                            context,
                                            repository,
                                            db,
                                            tokenManager,
                                            { progress, message ->
                                                restoreProgress = progress
                                                restoreMessage = message
                                            },
                                            { restoring -> isRestoring = restoring }
                                        )
                                    }
                                }
                            },
                            enabled = !isRestoring,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            if (isRestoring) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("$restoreProgress%")
                            } else {
                                Text(stringResource(R.string.profile_restore_button))
                            }
                        }
                    }
                }
            }

            // Logout Button
            Button(
                onClick = {
                    scope.launch {
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

            // Add new account button
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { showAddAccountDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add New Account")
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
    
    // Token Debug Dialog
    if (showTokenDialog) {
        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            title = { Text(stringResource(R.string.profile_token_dialog_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.profile_token_dialog_description),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = currentToken ?: stringResource(R.string.profile_token_dialog_no_token),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            ),
                            modifier = Modifier.padding(12.dp),
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            confirmButton = {
                if (currentToken != null) {
                    Button(
                        onClick = {
                            // Copy token to clipboard
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Bearer Token", currentToken)
                            clipboard.setPrimaryClip(clip)
                            
                            // Show toast (simplest feedback)
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.profile_token_dialog_copied),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            
                            showTokenDialog = false
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.profile_token_dialog_copy))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showTokenDialog = false }) {
                    Text(stringResource(R.string.profile_token_dialog_close))
                }
            }
        )
    }

    // Account switcher dialog
    if (showAccountSwitcher) {
        AlertDialog(
            onDismissRequest = { showAccountSwitcher = false },
            title = { Text("Switch Account") },
            text = {
                Column {
                    availableAccounts.forEach { account ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    scope.launch {
                                        accountManager.switchToAccount(account.id)
                                        showAccountSwitcher = false
                                    }
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (account.id == currentAccountId)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (account.userInfo != null) {
                                    AsyncImage(
                                        model = account.userInfo.avatarUrl,
                                        contentDescription = "Avatar",
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                    Column(modifier = Modifier.padding(start = 12.dp)) {
                                        Text(
                                            text = account.userInfo.username,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = "Account: ${account.userInfo.id}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "Account: ${account.id} (Not logged in)",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAccountSwitcher = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Add new account dialog
    if (showAddAccountDialog) {
        AlertDialog(
            onDismissRequest = { showAddAccountDialog = false },
            title = { Text("Add New Account") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newClientId,
                        onValueChange = { newClientId = it },
                        label = { Text("Client ID") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newClientSecret,
                        onValueChange = { newClientSecret = it },
                        label = { Text("Client Secret") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newClientId.isNotBlank() && newClientSecret.isNotBlank()) {
                            scope.launch {
                                // Auto-generate account ID
                                val accountId = "account${availableAccounts.size + 1}"
                                accountManager.createAccount(accountId, newClientId, newClientSecret)
                                // Switch to the new account
                                accountManager.switchToAccount(accountId)
                                // Close dialog (keep form values for next time)
                                showAddAccountDialog = false
                                // Trigger login flow for the new account
                                onLoginClick()
                            }
                        }
                    },
                    enabled = newClientId.isNotBlank() && newClientSecret.isNotBlank()
                ) {
                    Text("Add Account")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddAccountDialog = false }) {
                    Text("Cancel")
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(0.7f)
            ) {
                Text(stringResource(id = R.string.info_cover_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(id = R.string.info_cover_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
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

