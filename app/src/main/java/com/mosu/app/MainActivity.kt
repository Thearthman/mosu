package com.mosu.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mosu.app.data.SettingsManager
import com.mosu.app.data.TokenManager
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.repository.OsuRepository
import com.mosu.app.player.MusicController
import com.mosu.app.ui.components.MiniPlayer
import com.mosu.app.ui.library.LibraryScreen
import com.mosu.app.ui.profile.ProfileScreen
import com.mosu.app.ui.search.SearchScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Handle Deep Link
        val data: Uri? = intent?.data
        val code = data?.getQueryParameter("code")

        val db = AppDatabase.getDatabase(this)
        val repository = OsuRepository(db.searchCacheDao())
        val redirectUri = "mosu://callback"
        val tokenManager = TokenManager(this)
        val settingsManager = SettingsManager(this)

        setContent {
            MaterialTheme {
                MainScreen(
                    initialAuthCode = code,
                    repository = repository,
                    db = db,
                    tokenManager = tokenManager,
                    settingsManager = settingsManager,
                    redirectUri = redirectUri
                )
            }
        }
    }
}

@Composable
fun MainScreen(
    initialAuthCode: String?,
    repository: OsuRepository,
    db: AppDatabase,
    tokenManager: TokenManager,
    settingsManager: SettingsManager,
    redirectUri: String
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Music Controller stays alive at MainScreen level
    val musicController = remember { MusicController(context) }
    
    // Access Token State (loaded from TokenManager or from OAuth)
    val storedToken by tokenManager.accessToken.collectAsState(initial = null)
    var accessToken by remember { mutableStateOf<String?>(null) }
    
    // Scroll to top trigger for Search screen
    var scrollSearchToTop by remember { mutableStateOf(false) }
    var lastSearchTapTime by remember { mutableStateOf(0L) }
    
    // OAuth Credentials from Settings
    val clientId by settingsManager.clientId.collectAsState(initial = "")
    val clientSecret by settingsManager.clientSecret.collectAsState(initial = "")
    
    // Initialize access token from storage
    LaunchedEffect(storedToken) {
        if (storedToken != null) {
            accessToken = storedToken
        }
    }
    
    DisposableEffect(Unit) {
        onDispose { musicController.release() }
    }

    Scaffold(
        bottomBar = {
            Column {
                MiniPlayer(musicController = musicController)
                NavigationBar {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination?.route

                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Library") },
                    label = { Text("Library") },
                    selected = currentDestination == "library",
                    onClick = {
                        navController.navigate("library") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    label = { Text("Search") },
                    selected = currentDestination == "search",
                    onClick = {
                        val currentTime = System.currentTimeMillis()
                        if (currentDestination == "search" && (currentTime - lastSearchTapTime) < 500) {
                            // Double tap detected - scroll to top
                            scrollSearchToTop = true
                        } else {
                            navController.navigate("search") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                        lastSearchTapTime = currentTime
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                    label = { Text("Profile") },
                    selected = currentDestination == "profile",
                    onClick = {
                        navController.navigate("profile") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "library",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("library") {
                LibraryScreen(db, musicController)
            }
            composable("search") {
                SearchScreen(
                    authCode = initialAuthCode,
                    repository = repository,
                    db = db,
                    accessToken = accessToken,
                    clientId = clientId,
                    clientSecret = clientSecret,
                    settingsManager = settingsManager,
                    musicController = musicController,
                    onTokenReceived = { token ->
                        scope.launch {
                            accessToken = token
                            tokenManager.saveToken(token)
                        }
                    },
                    scrollToTop = scrollSearchToTop,
                    onScrolledToTop = { scrollSearchToTop = false }
                )
            }
            composable("profile") {
                ProfileScreen(
                    accessToken = accessToken,
                    repository = repository,
                    db = db,
                    tokenManager = tokenManager,
                    settingsManager = settingsManager,
                    onLoginClick = {
                        if (clientId.isNotEmpty()) {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://osu.ppy.sh/oauth/authorize?client_id=$clientId&redirect_uri=$redirectUri&response_type=code&scope=public+identify")
                            )
                            context.startActivity(intent)
                        }
                    },
                    onLogout = {
                        accessToken = null
                    }
                )
            }
        }
    }
}
