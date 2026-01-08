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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.core.os.LocaleListCompat
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.Alignment
import com.mosu.app.data.AccountManager
import com.mosu.app.data.SettingsManager
import com.mosu.app.data.TokenManager
import com.mosu.app.utils.RegionUtils
import com.mosu.app.utils.RegionInfo
import com.mosu.app.data.api.TokenAuthenticator
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.repository.OsuRepository
import com.mosu.app.domain.download.BeatmapDownloader
import com.mosu.app.data.work.RecentPlaysSyncWorker
import com.mosu.app.player.MusicController
import com.mosu.app.ui.components.MiniPlayer
import com.mosu.app.ui.library.LibraryScreen
import com.mosu.app.ui.playlist.PlaylistScreen
import com.mosu.app.ui.profile.ProfileScreen
import com.mosu.app.ui.search.SearchScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.lifecycleScope
import com.mosu.app.data.api.RetrofitClient
import kotlinx.coroutines.runBlocking

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
        val settingsManager = SettingsManager(this)
        val beatmapDownloader = BeatmapDownloader(this, settingsManager)
        val redirectUri = "mosu://callback"
        val tokenManager = TokenManager(this)
        val accountManager = AccountManager(this, tokenManager)


        setContent {
            MosuTheme {
                MainScreen(
                    initialAuthCode = code,
                    repository = repository,
                    db = db,
                    tokenManager = tokenManager,
                    accountManager = accountManager,
                    settingsManager = settingsManager,
                    beatmapDownloader = beatmapDownloader,
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
    accountManager: AccountManager,
    settingsManager: SettingsManager,
    beatmapDownloader: BeatmapDownloader,
    redirectUri: String
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    // Configure API authentication for automatic token refresh
    // Do this synchronously to ensure it's set up before any API calls
    RetrofitClient.configureAuthentication(
        TokenAuthenticator(context, tokenManager, settingsManager),
        tokenManager,
        settingsManager
    )

    // Music Controller stays alive at MainScreen level
    val musicController = remember { MusicController(context, settingsManager) }
    
    // Access Token State (loaded from TokenManager or from OAuth)
    // Initialize synchronously to prevent race condition with API calls
    var accessToken by remember {
        mutableStateOf(runBlocking {
            val token = tokenManager.getCurrentAccessToken()

            // If token exists, check if it's expired and try to refresh
            if (token != null) {
                val isExpired = tokenManager.isTokenExpired().first()

                if (isExpired) {
                        val clientId = settingsManager.clientId.first()
                        val clientSecret = settingsManager.clientSecret.first()

                        if (clientId.isNotEmpty() && clientSecret.isNotEmpty()) {
                            val refreshSuccess = tokenManager.refreshTokenIfNeeded(clientId, clientSecret)

                        if (refreshSuccess) {
                            // Get the refreshed token
                            tokenManager.getCurrentAccessToken()
                            } else {
                                tokenManager.clearCurrentAccountToken()
                                null
                            }
                        } else {
                            token
                        }
                    } else {
                        token
                    }
                } else {
                    null
                }
        })
    }
    
    // Scroll to top trigger for Search screen
    var scrollSearchToTop by remember { mutableStateOf(false) }
    var lastSearchTapTime by remember { mutableStateOf(0L) }
    
    // OAuth Credentials from current account
    var clientId by remember { mutableStateOf("") }
    var clientSecret by remember { mutableStateOf("") }

    // Initialize accounts and migrate settings on first launch
    LaunchedEffect(Unit) {
        // Region check for automatic API switching
        launch {
            val isChecked = settingsManager.regionChecked.first()
            val storedRegion = settingsManager.detectedRegion.first()
            
            // Re-check if never checked OR if previous check failed (unknown)
            if (!isChecked || storedRegion == null) {
                val region = RegionUtils.getDeviceRegion()
                if (region != null) {
                    settingsManager.setDetectedRegion(region.countryCode)
                    if (region.countryCode == "CN") {
                        settingsManager.setApiSource("sayobot")
                        android.util.Log.d("MainActivity", "Region detected as CN, switched to Sayobot API")
                    } else {
                        settingsManager.setApiSource("osu")
                        android.util.Log.d("MainActivity", "Region detected as ${region.countryCode}, using official API")
                    }
                }
                settingsManager.setRegionChecked(true)
            }
        }

        val availableAccountIds = tokenManager.getAvailableAccountIds()

        // If no accounts exist but settings have credentials, migrate to first account
        if (availableAccountIds.isEmpty()) {
            val settingsClientId = settingsManager.clientId.first()
            val settingsClientSecret = settingsManager.clientSecret.first()

            if (settingsClientId.isNotEmpty() && settingsClientSecret.isNotEmpty()) {
                accountManager.createAccount("account1", settingsClientId, settingsClientSecret)
                tokenManager.setCurrentAccount("account1")
            }
        }

        // Refresh supporter status for all accounts on startup
        launch(Dispatchers.IO) {
            accountManager.refreshSupporterStatusForAllAccounts()
        }
    }

    // Update credentials when account changes
    LaunchedEffect(Unit) {
        tokenManager.currentAccountId.collect { accountId ->
            accountId?.let {
                val (cid, csecret) = tokenManager.getAccountCredentials(it)

                // If account has no credentials, fall back to settings for backward compatibility
                if (cid.isNullOrEmpty() || csecret.isNullOrEmpty()) {
                    val settingsClientId = settingsManager.clientId.first()
                    val settingsClientSecret = settingsManager.clientSecret.first()
                    clientId = settingsClientId
                    clientSecret = settingsClientSecret
                } else {
                    clientId = cid
                    clientSecret = csecret
                }
            }
        }
    }
    val language by settingsManager.language.collectAsState(initial = "en")
    val apiSource by settingsManager.apiSource.collectAsState(initial = "osu")

    // Update RetrofitClient when apiSource changes
    LaunchedEffect(apiSource) {
        RetrofitClient.setApiSource(apiSource)
    }
    
    
    // Listen for token changes (e.g., after OAuth login or account switch)
    val storedToken by tokenManager.accessToken.collectAsState(initial = accessToken)
    val currentAccountId by tokenManager.currentAccountId.collectAsState(initial = null)

    LaunchedEffect(storedToken) {
        if (storedToken != accessToken) {
            accessToken = storedToken
        }
    }

    // Force accessToken update when account changes
    LaunchedEffect(currentAccountId) {
        val token = tokenManager.getCurrentAccessToken()
        accessToken = token
    }
    
    // Handle OAuth callback - Process login code globally
    LaunchedEffect(initialAuthCode) {
        if (initialAuthCode != null) {
            // Get current account credentials, fall back to settings if not available
            val nullableAccountId = tokenManager.getCurrentAccountId()
            if (nullableAccountId == null) {
                android.util.Log.e("MainActivity", "OAuth callback received but no current account set")
                return@LaunchedEffect
            }
            val accountId = nullableAccountId // Smart cast to non-null
            val (currentClientId, currentClientSecret) = tokenManager.getAccountCredentials(accountId)

            val effectiveClientId = if (currentClientId.isNullOrEmpty()) runBlocking { settingsManager.clientId.first() } else currentClientId
            val effectiveClientSecret = if (currentClientSecret.isNullOrEmpty()) runBlocking { settingsManager.clientSecret.first() } else currentClientSecret

            if (effectiveClientId.isNullOrEmpty() || effectiveClientSecret.isNullOrEmpty()) {
                android.util.Log.w("MainActivity", "OAuth callback received but no credentials configured for account: $currentAccountId")
            } else {
                try {
                    // If account doesn't have stored credentials, save them first
                    if (currentClientId.isNullOrEmpty() || currentClientSecret.isNullOrEmpty()) {
                        tokenManager.saveAccountCredentials(accountId, effectiveClientId, effectiveClientSecret)
                    }

                    val success = accountManager.loginToAccount(accountId, initialAuthCode, redirectUri)
                    if (success) {
                        // Refresh the access token state
                        val savedToken = tokenManager.accessToken.first()
                        accessToken = savedToken
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Login failed for account: $currentAccountId", e)
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

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val navbarHeightPx = 280f
    val collapsedOffset = screenHeightPx - navbarHeightPx

    Box(modifier = Modifier.fillMaxSize()) {
        val screenHeight = configuration.screenHeightDp.dp
        val screenHeightPxLocal = screenHeightPx // Using the value calculated above
        
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
        
        val navigationBarInsets = WindowInsets.navigationBars.asPaddingValues()
        val navigationBarHeight = navigationBarInsets.calculateBottomPadding()
        val navigationBarHeightPx = with(LocalDensity.current) { navigationBarHeight.toPx() }

        // Animation Progress: 0f (Collapsed) -> 1f (Expanded)
        val progress = 1f - (sheetOffset.value / collapsedOffset).coerceIn(0f, 1f)
        val miniPlayerAlpha = (1f - progress*4f).coerceIn(0f,1f)
        val nowPlaying by musicController.nowPlaying.collectAsState()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        // Move Nav Bar down as sheet expands. 200f is an arbitrary sufficient offset.
        val navBarTranslationY = progress * (300f + navigationBarHeightPx)
        val contentBottomPadding = when {
            currentRoute == "profile" -> 80.dp
            nowPlaying != null -> 144.dp
            else -> 80.dp
        }

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
                        LibraryScreen(db, musicController, repository, beatmapDownloader)
                    }
                    composable("playlists") {
                        PlaylistScreen(db, musicController, repository, beatmapDownloader)
                    }
                    composable("search") {
                        SearchScreen(
                            repository = repository,
                            db = db,
                            accessToken = accessToken,
                            accountManager = accountManager,
                            settingsManager = settingsManager,
                            musicController = musicController,
                            beatmapDownloader = beatmapDownloader,
                            scrollToTop = scrollSearchToTop,
                            onScrolledToTop = { scrollSearchToTop = false }
                        )
                    }
                    composable("profile") {
                        ProfileScreen(
                            accessToken = accessToken,
                            currentAccountId = currentAccountId,
                            repository = repository,
                            db = db,
                            tokenManager = tokenManager,
                            accountManager = accountManager,
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
                                // Clear token from both UI state and persistent storage
                                accessToken = null
                                scope.launch {
                                    accountManager.logoutFromCurrentAccount()
                                }
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
                    .statusBarsPadding() // Always add status bar padding to prevent drawing under notification bar
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
                        .padding(bottom = 80.dp + navigationBarHeight) // Initial position above NavBar
                        .offset { IntOffset(0, ((sheetOffset.value - collapsedOffset)*0.3).roundToInt()) }
                        .graphicsLayer {
                            alpha = miniPlayerAlpha
                            // Optimize rendering by only updating transform when alpha changes significantly
                            if (miniPlayerAlpha < 0.1f) {
                                alpha = 0f // Fully hide when nearly invisible
                            }
                        }
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
                val navBackStackEntryLocal by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntryLocal?.destination?.route

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
