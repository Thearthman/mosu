package com.mosu.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.mosu.app.ui.theme.MosuTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import com.mosu.app.data.AccountManager
import com.mosu.app.data.DataMigrations
import com.mosu.app.data.SettingsManager
import com.mosu.app.data.TokenManager
import com.mosu.app.data.media.MosuBackupService
import com.mosu.app.utils.RegionUtils
import com.mosu.app.data.api.TokenAuthenticator
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.repository.OsuRepository
import com.mosu.app.data.work.RecentPlaysSyncWorker
import com.mosu.app.player.MusicController
import com.mosu.app.ui.components.MiniPlayer
import com.mosu.app.ui.DeferredActionViewModel
import com.mosu.app.ui.library.LibraryScreen
import com.mosu.app.ui.playlist.PlaylistScreen
import com.mosu.app.ui.profile.ProfileScreen
import com.mosu.app.ui.score.ScoreScreen
import com.mosu.app.ui.search.SearchScreen
import com.mosu.app.ui.search.SearchViewModel
import com.mosu.app.ui.search.SearchViewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.lifecycleScope
import com.mosu.app.data.api.RetrofitClient
import androidx.core.net.toUri

private const val REGION_RECHECK_INTERVAL_MS = 30L * 24L * 60L * 60L * 1000L

class MainActivity : ComponentActivity() {
    private fun mediaRestorePermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_IMAGES
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun hasRequiredMediaRestorePermissions(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun restoreMediaStoreBackupWhenAllowed(db: AppDatabase) {
        if (hasRequiredMediaRestorePermissions()) {
            restoreMediaStoreBackup(db)
        } else {
            ActivityCompat.requestPermissions(this, mediaRestorePermissions(), MEDIA_RESTORE_PERMISSION_REQUEST)
        }
    }

    private fun restoreMediaStoreBackup(db: AppDatabase) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                MosuBackupService.restoreIfLibraryEmpty(this@MainActivity, db)
            } catch (e: Exception) {
                Log.w("MainActivity", "MediaStore restore failed", e)
            }
        }
    }

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
        // Applies postSplashScreenTheme (Theme.Mosu) and, below API 31, sets up
        // the compat splash. We deliberately do NOT keep the splash on screen:
        // on Android 12+ the status bar drawn over the system splash cannot be
        // hidden by the app, so holding it only prolongs a bar-visible frame.
        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyImmersiveMode()

        // Handle Deep Link
        val data: Uri? = intent?.data
        val code = data?.getQueryParameter("code")

        val db = AppDatabase.getDatabase(this)
        val repository = OsuRepository(db.searchCacheDao())
        val settingsManager = SettingsManager(this)
        val downloadManager = com.mosu.app.domain.download.DownloadManager.getInstance(this, db)
        val redirectUri = "mosu://callback"
        val tokenManager = TokenManager(this)
        val accountManager = AccountManager(this)

        restoreMediaStoreBackupWhenAllowed(db)

        setContent {
            MosuTheme {
                MainScreen(
                    initialAuthCode = code,
                    repository = repository,
                    db = db,
                    tokenManager = tokenManager,
                    accountManager = accountManager,
                    settingsManager = settingsManager,
                    downloadManager = downloadManager,
                    redirectUri = redirectUri
                )
            }
        }
        
        // Schedule the daily recent plays sync worker
        RecentPlaysSyncWorker.scheduleDaily(this)

        // Schedule periodic cache cleanup
        scheduleCacheCleanup()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Re-hide the system bars when the window regains focus (e.g. transient
        // swipe-to-reveal bars, or returning from another app). Guarded so we do
        // not re-issue a hide transition while the bars are already hidden, which
        // would re-trigger the inset animation on cold start.
        if (hasFocus && areSystemBarsVisible()) {
            hideSystemBars()
        }
    }

    private fun areSystemBarsVisible(): Boolean {
        val insets = ViewCompat.getRootWindowInsets(window.decorView) ?: return true
        return insets.isVisible(WindowInsetsCompat.Type.systemBars())
    }

    /**
     * Default the app to an immersive, edge-to-edge view: the status and
     * navigation bars start hidden and content fills the whole screen. A swipe
     * from the edge reveals them transiently, after which they hide again.
     */
    private fun applyImmersiveMode() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView)
            .hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MEDIA_RESTORE_PERMISSION_REQUEST && hasRequiredMediaRestorePermissions()) {
            restoreMediaStoreBackup(AppDatabase.getDatabase(this))
        }
    }

    companion object {
        private const val MEDIA_RESTORE_PERMISSION_REQUEST = 2001
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
    downloadManager: com.mosu.app.domain.download.DownloadManager,
    redirectUri: String
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    // Configure API authentication for automatic token refresh
    // Do this synchronously to ensure it's set up before any API calls
    RetrofitClient.configureAuthentication(
        TokenAuthenticator(context, tokenManager),
        tokenManager
    )

    // Music Controller stays alive at MainScreen level
    val musicController = remember { MusicController(context, settingsManager) }

    // SearchViewModel stays alive at MainScreen level to survive navigation
    val searchViewModel: SearchViewModel = viewModel(
        factory = SearchViewModelFactory(repository, db, context.applicationContext)
    )

    // DeferredActionViewModel to manage deletions across screens
    val deferredActionViewModel: DeferredActionViewModel = viewModel()
    
    // Access Token State (loaded from TokenManager or from OAuth)
    var accessToken by remember { mutableStateOf<String?>(null) }
    
    // Initialize accessToken asynchronously
    LaunchedEffect(Unit) {
        val token = tokenManager.getAccessToken()
        if (token != null) {
            val isExpired = tokenManager.isTokenExpired().first()
            if (isExpired) {
                val credentials = tokenManager.getCredentials()
                if (credentials.isConfigured) {
                    val refreshSuccess = tokenManager.refreshTokenIfNeeded(credentials.clientId, credentials.clientSecret)
                    accessToken = if (refreshSuccess) {
                        tokenManager.getAccessToken()
                    } else {
                        tokenManager.clearToken()
                        null
                    }
                } else {
                    accessToken = token
                }
            } else {
                accessToken = token
            }
        }
    }
    
    // Scroll to top trigger for screens
    var scrollSearchToTop by remember { mutableStateOf(false) }
    var lastSearchTapTime by remember { mutableStateOf(0L) }
    var scrollLibraryToTop by remember { mutableStateOf(false) }
    var lastLibraryTapTime by remember { mutableStateOf(0L) }
    
    // Migrate legacy storage and run the first-launch region check
    LaunchedEffect(Unit) {
        // Region check for automatic mirror selection
        launch {
            val isChecked = settingsManager.regionChecked.first()
            val storedRegion = settingsManager.detectedRegion.first()
            val checkedAt = settingsManager.regionCheckedAt.first()
            val mirrorManuallySelected = settingsManager.mirrorManuallySelected.first()
            val now = System.currentTimeMillis()
            val regionCheckIsStale = checkedAt == 0L || now - checkedAt > REGION_RECHECK_INTERVAL_MS

            if (!mirrorManuallySelected && (!isChecked || storedRegion.isNullOrBlank() || regionCheckIsStale)) {
                val region = RegionUtils.getDeviceRegion(context.applicationContext)
                if (region != null) {
                    val mirror = RegionUtils.preferredMirrorFor(region)
                    settingsManager.setDetectedRegion(region.countryCode, region.source)
                    settingsManager.setPreferredMirror(mirror, manual = false)
                    android.util.Log.d(
                        "MainActivity",
                        "Region detected as ${region.countryCode} via ${region.source}, using $mirror mirror"
                    )
                }
                settingsManager.setRegionChecked(true)
            }
        }

        // Fold storage left behind by earlier app versions (multi-account
        // sessions, Settings-owned credentials, legacy profile cache) into the
        // current single-account stores.
        DataMigrations.run(settingsManager, tokenManager, accountManager)
    }

    val language by settingsManager.language.collectAsState(initial = "en")
    val preferredMirror: String by settingsManager.preferredMirror.collectAsState(initial = "nerinyan")
    val clientId by tokenManager.clientId.collectAsState(initial = "")

    // Listen for token changes (e.g., after OAuth login)
    val storedToken by tokenManager.accessToken.collectAsState(initial = accessToken)

    LaunchedEffect(storedToken) {
        if (storedToken != accessToken) {
            accessToken = storedToken
        }
    }

    // Handle OAuth callback - Process login code globally
    LaunchedEffect(initialAuthCode) {
        if (initialAuthCode != null) {
            val credentials = tokenManager.getCredentials()
            val effectiveClientId = credentials.clientId
            val effectiveClientSecret = credentials.clientSecret

            if (effectiveClientId.isEmpty() || effectiveClientSecret.isEmpty()) {
                android.util.Log.w("MainActivity", "OAuth callback received but no OAuth credentials configured")
            } else {
                try {
                    val tokenResponse = RetrofitClient.api.getToken(
                        clientId = effectiveClientId,
                        clientSecret = effectiveClientSecret,
                        code = initialAuthCode,
                        grantType = "authorization_code",
                        redirectUri = redirectUri
                    )
                    tokenManager.saveTokens(
                        tokenResponse.accessToken,
                        tokenResponse.refreshToken ?: "",
                        tokenResponse.expiresIn
                    )
                    val userInfo = RetrofitClient.api.getMe("")
                    accountManager.saveUserInfo(userInfo)
                    accessToken = tokenManager.getAccessToken()
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Login failed", e)
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
        val snackbarHostState = remember { SnackbarHostState() }

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

        // Animation Progress and Visibility (using derivedStateOf to minimize recomposition)
        val isSheetOpen by remember {
            derivedStateOf { sheetOffset.value < collapsedOffset }
        }
        
        val nowPlaying by musicController.nowPlaying.collectAsState()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        val contentBottomPadding = when {
            currentRoute == "profile" -> 80.dp
            nowPlaying != null -> 144.dp
            else -> 80.dp
        }

        // 1. Content Layer (Scaffold without BottomBar)
        Scaffold(
            // snackbarHost removed from here
            // No bottomBar here
            // Immersive app: the system bars start hidden, so treat the system
            // bar insets as always zero. Reading the live insets here would
            // relayout the whole UI the moment the bars hide on launch, which is
            // the launch jitter we are avoiding.
            contentWindowInsets = WindowInsets(0),
        ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = "library",
                    modifier = Modifier
                        .padding(innerPadding)
                        .padding(bottom = contentBottomPadding) // Manual padding for MiniPlayer + NavBar
                ) {
                    composable("library") {
                        LibraryScreen(
                            db = db,
                            musicController = musicController,
                            repository = repository,
                            downloadManager = downloadManager,
                            accessToken = accessToken,
                            scrollToTop = scrollLibraryToTop,
                            onScrolledToTop = { scrollLibraryToTop = false },
                            snackbarHostState = snackbarHostState,
                            deferredActionViewModel = deferredActionViewModel
                        )
                    }
                    composable("playlists") {
                        PlaylistScreen(db, musicController, repository, downloadManager, accessToken, snackbarHostState, deferredActionViewModel)
                    }
                    composable("search") {
                        SearchScreen(
                            viewModel = searchViewModel,
                            deferredActionViewModel = deferredActionViewModel,
                            accessToken = accessToken,
                            accountManager = accountManager,
                            settingsManager = settingsManager,
                            musicController = musicController,
                            downloadManager = downloadManager,
                            scrollToTop = scrollSearchToTop,
                            onScrolledToTop = { scrollSearchToTop = false },
                            snackbarHostState = snackbarHostState
                        )
                    }
                    composable("score") {
                        ScoreScreen(
                            accessToken = accessToken,
                            repository = repository,
                            db = db
                        )
                    }
                    composable("profile") {
                        ProfileScreen(
                            accessToken = accessToken,
                            repository = repository,
                            db = db,
                            tokenManager = tokenManager,
                            accountManager = accountManager,
                            settingsManager = settingsManager,
                            downloadManager = downloadManager,
                            onLoginClick = {
                                if (clientId.isNotEmpty()) {
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        "https://osu.ppy.sh/oauth/authorize?client_id=$clientId&redirect_uri=$redirectUri&response_type=code&scope=public+identify".toUri()
                                    )
                                    context.startActivity(intent)
                                }
                            },
                            onLogout = {
                                // Clear token from both UI state and persistent storage
                                accessToken = null
                                scope.launch {
                                    tokenManager.clearToken()
                                    accountManager.clearUserInfo()
                                }
                            }
                        )
                    }
                }
            }

            // 1.5 Snackbar Layer (Positioned above MiniPlayer/NavBar)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = contentBottomPadding + 8.dp) // Above miniplayer/navbar
                    .zIndex(3f)
            ) {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) { data ->
                    androidx.compose.material3.Snackbar(
                        snackbarData = data,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        actionColor = MaterialTheme.colorScheme.primary
                    )
                }
            }

        // 2. Full Player Sheet (Middle Layer)
        // Keep in composition but hide when collapsed to prevent surface re-allocation issues
        Box(
            modifier = Modifier
                .zIndex(1f)
                .offset { IntOffset(0, sheetOffset.value.roundToInt()) }
                .graphicsLayer { 
                    alpha = if (sheetOffset.value >= collapsedOffset) 0f else 1f
                }
                .fillMaxSize()
                .statusBarsPadding()
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

        // 3. NavigationBar (Top Layer) - Moves DOWN
        NavigationBar(
            modifier = Modifier
                .zIndex(2f)
                .align(Alignment.BottomCenter)
                .offset { 
                    val currentProgress = 1f - (sheetOffset.value / collapsedOffset).coerceIn(0f, 1f)
                    val translationY = currentProgress * (300f + navigationBarHeightPx)
                    IntOffset(0, translationY.roundToInt()) 
                }
        ) {
            val navBackStackEntryLocal by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntryLocal?.destination?.route

            NavigationBarItem(
                icon = { Icon(Icons.Default.Home, contentDescription = "Library") },
                label = { Text("Library") },
                selected = currentDestination == "library",
                onClick = {
                    val currentTime = System.currentTimeMillis()
                    if (currentDestination == "library" && (currentTime - lastLibraryTapTime) < 500) {
                        scrollLibraryToTop = true
                    } else {
                        navController.navigate("library") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                    lastLibraryTapTime = currentTime
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
                icon = { Icon(Icons.Default.Star, contentDescription = "Scores") },
                label = { Text("Scores") },
                selected = currentDestination == "score",
                onClick = {
                    navController.navigate("score") {
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

        // 4. MiniPlayer (Top Layer) - Moves UP with Sheet
        if (nowPlaying != null && currentRoute != "profile") {
            Box(
                modifier = Modifier
                    .zIndex(3f)
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp + navigationBarHeight) // Initial position above NavBar
                    .offset { IntOffset(0, ((sheetOffset.value - collapsedOffset)*0.3).roundToInt()) }
                    .graphicsLayer {
                        val currentProgress = 1f - (sheetOffset.value / collapsedOffset).coerceIn(0f, 1f)
                        val alphaValue = (1f - currentProgress * 4f).coerceIn(0f, 1f)
                        alpha = if (alphaValue < 0.1f) 0f else alphaValue
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
    }
}
