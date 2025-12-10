package com.mosu.app.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
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
    var languageMenuExpanded by remember { mutableStateOf(false) }
    val languageOptions = listOf(
        "en" to "English",
        "zh-CN" to "简体中文",
        "zh-TW" to "繁体中文"
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
            text = "Profile",
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
        )

        if (accessToken == null) {
            // Not logged in - Show Settings and Login
            
            // Settings Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("osu! OAuth Settings", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Client ID: ${if (clientId.isNotEmpty()) "Configured ✓" else "Not Set"}", style = MaterialTheme.typography.bodyMedium)
                    Text("Client Secret: ${if (clientSecret.isNotEmpty()) "Configured ✓" else "Not Set"}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { showSettingsDialog = true }) {
                        Text("Configure Credentials")
                    }
                }
            }

            // Language
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Language", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box {
                        OutlinedButton(onClick = { languageMenuExpanded = true }) {
                            Text(languageLabel(language))
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
                    Text("Login with osu!")
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        "Please configure your OAuth credentials above to login",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        } else {
            // User Info Card
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
                            Text(text = "ID: ${user.id}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // Stats Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Statistics", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Downloaded Songs: $totalDownloaded", style = MaterialTheme.typography.bodyLarge)
                }
            }

            // Settings Card (Logged in version)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("osu! OAuth Settings", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Client ID: ${if (clientId.isNotEmpty()) clientId.take(8) + "..." else "Not Set"}", style = MaterialTheme.typography.bodyMedium)
                    Text("Client Secret: ${if (clientSecret.isNotEmpty()) "••••••••" else "Not Set"}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { showSettingsDialog = true }) {
                        Text("Update Credentials")
                    }
                }
            }
            
            // Default Search View
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Default Search View", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    val isSupporter = userInfo?.isSupporter ?: false
                    val options = if (isSupporter) {
                        listOf("played", "recent", "favorite", "most_played", "all")
                    } else {
                        listOf("recent", "favorite", "most_played", "all")
                    }
                    var menuExpanded by remember { mutableStateOf(false) }
                    val label = when (defaultSearchView) {
                        "played" -> "Played"
                        "recent" -> "Recent"
                        "favorite" -> "Favorite"
                        "most_played" -> "Most Played"
                        else -> "All"
                    }
                    OutlinedButton(onClick = { menuExpanded = true }) {
                        Text(label)
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
                                        "played" -> "Played"
                                        "recent" -> "Recent"
                                        "favorite" -> "Favorite"
                                        "most_played" -> "Most Played"
                                        else -> "All"
                                    }
                                ) },
                                onClick = {
                                    menuExpanded = false
                                    scope.launch { settingsManager.saveDefaultSearchView(option) }
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "This controls which view Search opens with.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // Include Unranked/Loved/Any Status
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Include unranked maps", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = searchAnyEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch { settingsManager.saveSearchAnyEnabled(enabled) }
                            }
                        )
                        Spacer(modifier = Modifier.width(15.dp))
                        Text(
                            text = "Allow searching all statuses (may conflict with Favorite).",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Favorite filter takes priority if both apply.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // Language
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Language", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box {
                        OutlinedButton(onClick = { languageMenuExpanded = true }) {
                            Text(languageLabel(language))
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

            // Placeholder Cards
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Equalizer", style = MaterialTheme.typography.titleMedium)
                    Text("Coming Soon", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Recent Activity Heatmap", style = MaterialTheme.typography.titleMedium)
                    Text("Coming Soon", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
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
                Icon(Icons.Default.ExitToApp, contentDescription = "Logout")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Logout")
            }
        }
    }
    
    // Settings Dialog
    if (showSettingsDialog) {
        var inputClientId by remember { mutableStateOf(clientId) }
        var inputClientSecret by remember { mutableStateOf(clientSecret) }
        
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("OAuth Credentials") },
            text = {
                Column {
                    Text("Enter your osu! OAuth Application credentials:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputClientId,
                        onValueChange = { inputClientId = it },
                        label = { Text("Client ID") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputClientSecret,
                        onValueChange = { inputClientSecret = it },
                        label = { Text("Client Secret") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Get these from: osu.ppy.sh/home/account/edit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
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
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun languageLabel(code: String): String = when (code) {
    "zh-CN" -> "简体中文"
    "zh-TW" -> "繁体中文"
    else -> "English"
}

