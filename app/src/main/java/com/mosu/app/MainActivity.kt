package com.mosu.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.roundToInt
import com.mosu.app.ui.player.FullPlayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.mosu.app.ui.theme.MosuTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.core.os.LocaleListCompat
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mosu.app.data.SettingsManager
import com.mosu.app.data.TokenManager
import com.mosu.app.data.api.RetrofitClient
import com.mosu.app.data.api.TokenAuthenticator
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.repository.OsuRepository
import com.mosu.app.data.work.RecentPlaysSyncWorker
import com.mosu.app.player.MusicController
import com.mosu.app.ui.components.MiniPlayer
import com.mosu.app.ui.library.LibraryScreen
import com.mosu.app.ui.playlist.PlaylistScreen
import com.mosu.app.ui.profile.ProfileScreen
import com.mosu.app.ui.search.SearchScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.lifecycleScope
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private fun scheduleCacheCleanup() {
        // Run cache cleanup in background thread
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(this@MainActivity)
                val searchCacheDao = db.searchCacheDao()

                // Clear cache entries older than 30 days
                val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)
                searchCacheDao.clearExpired(thirtyDaysAgo)

                // Limit cache to 100 most recent entries to prevent unbounded growth
                searchCacheDao.limitCacheSize(100)

                android.util.Log.d("CacheCleanup", "Cleaned up expired and limited search cache entries")
            } catch (e: Exception) {
                android.util.Log.e("CacheCleanup", "Failed to clean cache", e)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle Deep Link
        val data: Uri? = intent?.data
        val code = data?.getQueryParameter("code")

        val db = AppDatabase.getDatabase(this)
        val repository = OsuRepository(db.searchCacheDao())
        val redirectUri = "mosu://callback"
        val tokenManager = TokenManager(this)
        val settingsManager = SettingsManager(this)


        setContent {
            MosuTheme {
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
        
        // Schedule the daily recent plays sync worker
        RecentPlaysSyncWorker.scheduleDaily(this)

        // Schedule periodic cache cleanup
        scheduleCacheCleanup()
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
//    musicController: MusicController
    ) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Configure API authenticator for automatic token refresh
    LaunchedEffect(Unit) {
        RetrofitClient.configureAuthenticator(
            TokenAuthenticator(context, tokenManager, settingsManager)
        )
    }

    // Music Controller stays alive at MainScreen level
    val musicController = remember { MusicController(context, settingsManager) }
    
    // Access Token State (loaded from TokenManager or from OAuth)
    val storedToken by tokenManager.accessToken.collectAsState(initial = null)
    var accessToken by remember { mutableStateOf<String?>(null) }
    
    // Scroll to top trigger for Search screen
    var scrollSearchToTop by remember { mutableStateOf(false) }
    var lastSearchTapTime by remember { mutableStateOf(0L) }
    
    // OAuth Credentials from Settings
    val clientId by settingsManager.clientId.collectAsState(initial = "")
    val clientSecret by settingsManager.clientSecret.collectAsState(initial = "")
    val language by settingsManager.language.collectAsState(initial = "en")
    
    // Login error state
    var loginError by remember { mutableStateOf<String?>(null) }
    
    // Initialize access token from storage
    LaunchedEffect(storedToken) {
        if (storedToken != null) {
            accessToken = storedToken
        }
    }
    
    // Handle OAuth callback - Process login code globally
    LaunchedEffect(initialAuthCode, clientId, clientSecret) {
        if (initialAuthCode != null && accessToken == null) {
            if (clientId.isEmpty() || clientSecret.isEmpty()) {
                android.util.Log.w("MainActivity", "OAuth callback received but credentials not configured")
                loginError = "Login failed: Please configure your Client ID and Secret in Profile settings first."
            } else {
                try {
                    loginError = null
                    android.util.Log.d("MainActivity", "Processing OAuth callback with code: ${initialAuthCode.take(10)}...")
                    android.util.Log.d("MainActivity", "Client ID configured: ${clientId.isNotEmpty()}")
                    val tokenResponse = repository.exchangeCodeForToken(initialAuthCode, clientId, clientSecret)
                    tokenManager.saveTokens(
                        tokenResponse.accessToken,
                        tokenResponse.refreshToken ?: "",
                        tokenResponse.expiresIn
                    )
                    accessToken = tokenResponse.accessToken
                    loginError = "Login successful!"
                    android.util.Log.d("MainActivity", "Login successful!")
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Login failed", e)
                    loginError = "Login failed: ${e.message}\n\nPlease check your Client ID and Secret in Profile settings."
                }
            }
        }
    }
    
    DisposableEffect(Unit) {
        onDispose { musicController.release() }
    }

    // Apply app language globally when preference changes
    LaunchedEffect(language) {
        val tag = if (language.isBlank()) "en" else language
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val navbarHeightPx = 280f
        val screenHeight = maxHeight
        val screenHeightPx = with(LocalDensity.current) { screenHeight.toPx() }
        val collapsedOffset = screenHeightPx - navbarHeightPx

        // Sheet Offset: collapsedOffset (Collapsed) -> 0f (Expanded)
        val sheetOffset = remember { Animatable(collapsedOffset) }
        val scope = rememberCoroutineScope()

        val draggableState = rememberDraggableState { delta ->
            scope.launch {
                val newOffset = (sheetOffset.value + delta).coerceIn(0f, collapsedOffset)
                sheetOffset.snapTo(newOffset)
            }
        }

        fun snapToTarget(velocity: Float) {
            scope.launch {
                val target = if (velocity < -1000 || (velocity <= 1000 && sheetOffset.value < collapsedOffset / 2)) {
                    0f // Expand
                } else {
                    collapsedOffset // Collapse
                }
                sheetOffset.animateTo(target, animationSpec = tween(300))
            }
        }
        
        // Animation Progress: 0f (Collapsed) -> 1f (Expanded)
        val progress = 1f - (sheetOffset.value / collapsedOffset).coerceIn(0f, 1f)
        val miniPlayerAlpha = (1f - progress*4f).coerceIn(0f,1f)
        val nowPlaying by musicController.nowPlaying.collectAsState()
        val isPlaying by musicController.isPlaying.collectAsState()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        // Move Nav Bar down as sheet expands. 200f is an arbitrary sufficient offset.
        val navBarTranslationY = progress * 300f
        val contentBottomPadding = when {
            currentRoute == "profile" -> 80.dp
            nowPlaying != null -> 144.dp
            else -> 80.dp
        }

        Box(modifier = Modifier.fillMaxSize()) {
            
            // 1. Content Layer (Scaffold without BottomBar)
            Scaffold(
                // No bottomBar here
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = "library",
                    modifier = Modifier
                        .padding(innerPadding)
                        .padding(bottom = contentBottomPadding) // Manual padding for MiniPlayer + NavBar
                ) {
                    composable("library") {
                        LibraryScreen(db, musicController)
                    }
                    composable("playlists") {
                        PlaylistScreen(db, musicController)
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
                                    // For refresh token flow, we only have access token
                                    // This is a fallback - ideally we'd get expires_in too
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

            // 2. Full Player Sheet (Middle Layer)
            Box(
                modifier = Modifier
                    .zIndex(1f)
                    .offset { IntOffset(0, sheetOffset.value.roundToInt()) }
                    .fillMaxSize()
                    .draggable(
                        state = draggableState,
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity -> snapToTarget(velocity) }
                    )
            ) {
                FullPlayer(
                    musicController = musicController,
                onCollapse = {
                    scope.launch { sheetOffset.animateTo(collapsedOffset, tween(300)) }
                }
                )
            }

            // 3. MiniPlayer (Top Layer) - Moves UP with Sheet
            if (nowPlaying != null && currentRoute != "profile") {
                Box(
                    modifier = Modifier
                        .zIndex(2f)
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 80.dp) // Initial position above NavBar
                        .offset { IntOffset(0, (sheetOffset.value - collapsedOffset).roundToInt()) }
                        .graphicsLayer { alpha = miniPlayerAlpha }
                        .draggable(
                            state = draggableState,
                            orientation = Orientation.Vertical,
                            onDragStopped = { velocity -> snapToTarget(velocity) }
                        )
                ) {
                    MiniPlayer(
                        musicController = musicController,
                        onClick = {
                            scope.launch { sheetOffset.animateTo(0f, tween(300)) }
                        }
                    )
                }
            }

            // 4. NavigationBar (Top Layer) - Moves DOWN
            NavigationBar(
                modifier = Modifier
                    .zIndex(2f)
                    .align(Alignment.BottomCenter)
                    .offset { IntOffset(0, navBarTranslationY.roundToInt()) }
            ) {
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
                    icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Playlists") },
                    label = { Text("Playlists") },
                    selected = currentDestination == "playlists",
                    onClick = {
                        navController.navigate("playlists") {
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
}
