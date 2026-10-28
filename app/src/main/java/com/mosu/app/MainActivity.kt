package com.mosu.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.mosu.app.ui.player.FullPlayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.mosu.app.ui.theme.MosuTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.ui.semantics.Role
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
import com.mosu.app.ui.components.LandscapeMiniPlayer
import com.mosu.app.ui.components.LANDSCAPE_MINI_PLAYER_PROGRESS_SPACE_DP
import com.mosu.app.ui.player.LandscapePlayerDragPosition
import com.mosu.app.ui.player.LandscapePlayerMorph
import com.mosu.app.ui.player.advanceLandscapePlayerDrag
import com.mosu.app.ui.player.landscapePlayerMorphBounds
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.lifecycleScope
import com.mosu.app.data.api.RetrofitClient
import androidx.core.net.toUri

private const val REGION_RECHECK_INTERVAL_MS = 30L * 24L * 60L * 60L * 1000L
private const val LANDSCAPE_DOCK_WIDTH_DP = 80
private const val LANDSCAPE_DOCK_TITLE_ROW_HEIGHT_DP = 112
private const val LANDSCAPE_CONTENT_TOP_MARGIN_DP = 16
private const val LANDSCAPE_PLAYER_HEIGHT_FRACTION = 0.75f
private const val LANDSCAPE_MINI_PLAYER_WIDTH_DP = 72
private const val LANDSCAPE_MINI_PLAYER_BACKGROUND_BLEED_DP = 100
private const val LANDSCAPE_COMPACT_PLAYER_WIDTH_TO_HEIGHT = 2f / 3f
private const val LANDSCAPE_SEARCH_BAR_HEIGHT_DP = 48
private const val LANDSCAPE_EXPANSION_DRAG_GAIN = 1.2f

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
private fun LandscapeTopScreenAction(
    currentRoute: String?,
    refreshEnabled: Boolean,
    onPlaylistCreate: () -> Unit,
    onScoreRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        when (currentRoute) {
            "playlists" -> {
                LandscapeSidebarIconButton(onClick = onPlaylistCreate) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.playlist_cd_create_playlist)
                    )
                }
            }
            "score" -> {
                LandscapeSidebarIconButton(
                    onClick = onScoreRefresh,
                    enabled = refreshEnabled
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.score_cd_refresh)
                    )
                }
            }
        }
    }
}

@Composable
private fun LandscapeBottomScreenAction(
    currentRoute: String?,
    hasNowPlaying: Boolean,
    activeDownloadCount: Int,
    onLibraryFindCurrent: () -> Unit,
    onPlaylistImport: () -> Unit,
    onSearchTasks: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        when (currentRoute) {
            "library" -> {
                if (hasNowPlaying) {
                    LandscapeSidebarIconButton(onClick = onLibraryFindCurrent) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = stringResource(R.string.library_find_current_cd)
                        )
                    }
                }
            }
            "playlists" -> {
                LandscapeSidebarIconButton(onClick = onPlaylistImport) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = stringResource(R.string.playlist_cd_import_playlist)
                    )
                }
            }
            "search" -> {
                LandscapeSidebarIconButton(onClick = onSearchTasks) {
                    BadgedBox(
                        badge = {
                            if (activeDownloadCount > 0) {
                                Badge {
                                    Text(activeDownloadCount.toString())
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = stringResource(R.string.search_cd_download)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LandscapeSidebarIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxSize()
        ) {
            content()
        }
    }
}

@Composable
private fun LandscapeDockTitle(
    currentRoute: String?,
    modifier: Modifier = Modifier
) {
    val title = when (currentRoute) {
        "library" -> stringResource(R.string.library_title)
        "playlists" -> stringResource(R.string.playlist_title)
        "score" -> stringResource(R.string.score_title)
        "search" -> stringResource(R.string.search_title)
        "profile" -> stringResource(R.string.profile_title)
        else -> return
    }
    val isChinese = LocalConfiguration.current.locales[0].language == "zh"
    val chineseTitleStyle = MaterialTheme.typography.titleLarge.copy(
        fontSize = 26.sp,
        lineHeight = 26.sp
    )
    val rotatedTitleStyle = MaterialTheme.typography.titleLarge.copy(
        fontSize = 24.sp,
        lineHeight = 24.sp
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(LANDSCAPE_DOCK_TITLE_ROW_HEIGHT_DP.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isChinese) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                title.filterNot(Char::isWhitespace).forEach { character ->
                    Text(
                        text = character.toString(),
                        style = chineseTitleStyle,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Text(
                text = title,
                modifier = Modifier
                    .requiredWidth((LANDSCAPE_DOCK_TITLE_ROW_HEIGHT_DP - 8).dp)
                    .rotate(90f),
                style = rotatedTitleStyle,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LandscapeLibrarySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .widthIn(min = 220.dp, max = 440.dp)
            .height(LANDSCAPE_SEARCH_BAR_HEIGHT_DP.dp)
            .focusRequester(focusRequester),
        placeholder = {
            Text(
                text = stringResource(R.string.library_search_placeholder),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Clear,
                        contentDescription = stringResource(R.string.search_cd_clear),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {}),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        ),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun SpaceEvenlyNavigationItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    val colors = NavigationBarItemDefaults.colors()
    val iconColor by animateColorAsState(
        targetValue = if (selected) colors.selectedIconColor else colors.unselectedIconColor,
        label = "navigationIconColor"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) colors.selectedTextColor else colors.unselectedTextColor,
        label = "navigationTextColor"
    )

    Column(
        modifier = modifier
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab
            )
            .widthIn(min = 56.dp)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (selected) colors.selectedIndicatorColor else Color.Transparent
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            maxLines = 1
        )
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
    val focusManager = LocalFocusManager.current

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
    val genreBarEnabled by settingsManager.genreBarEnabled.collectAsState(initial = true)
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

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight
        var landscapeDragging by remember { mutableStateOf(false) }
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
        val isSheetFullyOpen by remember {
            derivedStateOf { sheetOffset.value <= 0f }
        }
        
        val nowPlaying by musicController.nowPlaying.collectAsState()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        val landscapeDownloadTasks by downloadManager.tasks.collectAsState()
        val landscapeActiveDownloadCount = remember(landscapeDownloadTasks) {
            landscapeDownloadTasks.values.count {
                it.status == com.mosu.app.domain.download.DownloadStatus.Downloading ||
                    it.status == com.mosu.app.domain.download.DownloadStatus.Queued ||
                    it.status == com.mosu.app.domain.download.DownloadStatus.Extracting ||
                    it.status == com.mosu.app.domain.download.DownloadStatus.Paused
            }
        }
        var librarySearchQuery by remember { mutableStateOf("") }
        var librarySearchExpanded by remember { mutableStateOf(false) }
        val librarySearchFocusRequester = remember { FocusRequester() }
        var libraryFindCurrentRequest by remember { mutableStateOf(0) }
        var playlistCreateRequest by remember { mutableStateOf(0) }
        var playlistImportRequest by remember { mutableStateOf(0) }
        var searchTaskViewRequest by remember { mutableStateOf(0) }
        var scoreRefreshRequest by remember { mutableStateOf(0) }
        val landscapeLibrarySearchVisible =
            currentRoute == "library" &&
                (librarySearchExpanded || librarySearchQuery.isNotEmpty())

        LaunchedEffect(
            isLandscape,
            currentRoute,
            librarySearchExpanded
        ) {
            if (isLandscape && currentRoute == "library" && librarySearchExpanded) {
                delay(50)
                runCatching { librarySearchFocusRequester.requestFocus() }
            }
        }
        LaunchedEffect(isLandscape, currentRoute) {
            if (!isLandscape || currentRoute != "library") {
                librarySearchExpanded = false
            }
        }

        val contentBottomPadding = when {
            isLandscape -> 0.dp
            nowPlaying != null -> 144.dp
            else -> 80.dp
        }
        val landscapePlayerHeight = maxHeight * LANDSCAPE_PLAYER_HEIGHT_FRACTION
        val landscapeMiniPlayerHeight = landscapePlayerHeight
        val landscapeCompactPlayerHeight = landscapePlayerHeight
        val landscapeCompactPlayerWidth =
            landscapeCompactPlayerHeight * LANDSCAPE_COMPACT_PLAYER_WIDTH_TO_HEIGHT
        val landscapeFullscreenSize = DpSize(maxWidth, maxHeight)
        val landscapeActionSlotHeight =
            (maxHeight - landscapeMiniPlayerHeight) / 2f
        val landscapePlayerCollapsedOffset =
            with(density) { landscapeCompactPlayerWidth.toPx() }
        val landscapeExpansionDistancePx = with(density) {
            (maxWidth - landscapeCompactPlayerWidth).toPx().coerceAtLeast(1f)
        }
        val landscapeSheetOffset = remember(landscapePlayerCollapsedOffset) {
            Animatable(landscapePlayerCollapsedOffset)
        }
        val landscapeExpansionProgress = remember { Animatable(0f) }

        fun applyLandscapeDrag(delta: Float) {
            scope.launch {
                val next = advanceLandscapePlayerDrag(
                    position = LandscapePlayerDragPosition(
                        sheetOffsetPx = landscapeSheetOffset.value,
                        expansionProgress = landscapeExpansionProgress.value
                    ),
                    deltaPx = delta,
                    collapsedOffsetPx = landscapePlayerCollapsedOffset,
                    expansionDistancePx = landscapeExpansionDistancePx,
                    expansionGain = LANDSCAPE_EXPANSION_DRAG_GAIN
                )
                landscapeSheetOffset.snapTo(next.sheetOffsetPx)
                landscapeExpansionProgress.snapTo(next.expansionProgress)
            }
        }

        val landscapeDraggableState = rememberDraggableState { delta ->
            applyLandscapeDrag(delta)
        }

        fun settleLandscapePlayer(velocity: Float) {
            scope.launch {
                val progress = landscapeExpansionProgress.value
                val currentSheetOffset = landscapeSheetOffset.value
                if (
                    progress > 0f ||
                    (currentSheetOffset <= 0f && velocity < -1000f)
                ) {
                    val target = if (
                        velocity < -1000f ||
                        (velocity <= 1000f && progress >= 0.5f)
                    ) {
                        1f
                    } else {
                        0f
                    }
                    landscapeExpansionProgress.animateTo(
                        target,
                        animationSpec = tween(300)
                    )
                } else {
                    val target = if (
                        velocity < -1000f ||
                        (
                            velocity <= 1000f &&
                                currentSheetOffset < landscapePlayerCollapsedOffset / 2f
                            )
                    ) {
                        0f
                    } else {
                        landscapePlayerCollapsedOffset
                    }
                    landscapeSheetOffset.animateTo(target, animationSpec = tween(300))
                }
            }
        }

        val isLandscapePlayerOpen by remember {
            derivedStateOf {
                landscapeSheetOffset.value < landscapePlayerCollapsedOffset
            }
        }
        val isLandscapePlayerFullyOpen by remember {
            derivedStateOf { landscapeSheetOffset.value <= 0f }
        }

        LaunchedEffect(nowPlaying) {
            if (nowPlaying == null) {
                landscapeExpansionProgress.snapTo(0f)
                landscapeSheetOffset.animateTo(
                    landscapePlayerCollapsedOffset,
                    animationSpec = tween(300)
                )
            }
        }
        BackHandler(
            enabled = isLandscape &&
                (landscapeExpansionProgress.value > 0f || isLandscapePlayerOpen)
        ) {
            scope.launch {
                if (landscapeExpansionProgress.value > 0f) {
                    landscapeExpansionProgress.animateTo(
                        0f,
                        animationSpec = tween(300)
                    )
                } else {
                    landscapeSheetOffset.animateTo(
                        landscapePlayerCollapsedOffset,
                        animationSpec = tween(300)
                    )
                }
            }
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
                        .padding(start = if (isLandscape) LANDSCAPE_DOCK_WIDTH_DP.dp else 0.dp)
                        .padding(
                            end = if (isLandscape) LANDSCAPE_MINI_PLAYER_WIDTH_DP.dp else 0.dp
                        )
                        .padding(
                            top = if (isLandscape) LANDSCAPE_CONTENT_TOP_MARGIN_DP.dp else 0.dp
                        )
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
                            deferredActionViewModel = deferredActionViewModel,
                            isLandscape = isLandscape,
                            genreBarEnabled = genreBarEnabled,
                            librarySearchQuery = librarySearchQuery,
                            onLibrarySearchQueryChange = { librarySearchQuery = it },
                            landscapeFindCurrentRequest = libraryFindCurrentRequest,
                            onLandscapeFindCurrentRequestConsumed = {
                                libraryFindCurrentRequest = 0
                            }
                        )
                    }
                    composable("playlists") {
                        PlaylistScreen(
                            db = db,
                            musicController = musicController,
                            repository = repository,
                            downloadManager = downloadManager,
                            accessToken = accessToken,
                            snackbarHostState = snackbarHostState,
                            deferredActionViewModel = deferredActionViewModel,
                            isLandscape = isLandscape,
                            landscapeCreateRequest = playlistCreateRequest,
                            onLandscapeCreateRequestConsumed = { playlistCreateRequest = 0 },
                            landscapeImportRequest = playlistImportRequest,
                            onLandscapeImportRequestConsumed = {
                                playlistImportRequest = 0
                            }
                        )
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
                            snackbarHostState = snackbarHostState,
                            isLandscape = isLandscape,
                            genreBarEnabled = genreBarEnabled,
                            landscapeTaskViewRequest = searchTaskViewRequest,
                            onLandscapeTaskViewRequestConsumed = { searchTaskViewRequest = 0 }
                        )
                    }
                    composable("score") {
                        ScoreScreen(
                            accessToken = accessToken,
                            repository = repository,
                            db = db,
                            settingsManager = settingsManager,
                            isLandscape = isLandscape,
                            landscapeRefreshRequest = scoreRefreshRequest,
                            onLandscapeRefreshRequestConsumed = {
                                scoreRefreshRequest = 0
                            }
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
                            isLandscape = isLandscape,
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
        if (!isLandscape) {
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
                    settingsManager = settingsManager,
                    db = db,
                    isExpanded = isSheetOpen,
                    onCollapse = {
                        scope.launch { sheetOffset.animateTo(collapsedOffset, tween(300)) }
                    }
                )
            }
        }

        // 3. Navigation (Top Layer)
        if (isLandscape) {
            Surface(
                modifier = Modifier
                    .zIndex(2f)
                    .align(Alignment.CenterStart)
                    .width(LANDSCAPE_DOCK_WIDTH_DP.dp)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp
            ) {
                NavigationRail(
                    containerColor = Color.Transparent,
                    windowInsets = WindowInsets(0),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        LandscapeDockTitle(currentRoute = currentRoute)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.SpaceEvenly
                        ) {
                            NavigationRailItem(
                                icon = { Icon(Icons.Default.Home, contentDescription = "Library") },
                                modifier = Modifier.height(48.dp),
                                selected = currentRoute == "library",
                                onClick = {
                                    val currentTime = System.currentTimeMillis()
                                    if (
                                        currentRoute == "library" &&
                                        (currentTime - lastLibraryTapTime) < 500
                                    ) {
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
                            NavigationRailItem(
                                icon = {
                                    Icon(
                                        Icons.Default.LibraryMusic,
                                        contentDescription = "Playlists"
                                    )
                                },
                                modifier = Modifier.height(48.dp),
                                selected = currentRoute == "playlists",
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
                            NavigationRailItem(
                                icon = { Icon(Icons.Default.Star, contentDescription = "Scores") },
                                modifier = Modifier.height(48.dp),
                                selected = currentRoute == "score",
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
                            NavigationRailItem(
                                icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                                modifier = Modifier.height(48.dp),
                                selected = currentRoute == "search",
                                onClick = {
                                    val currentTime = System.currentTimeMillis()
                                    if (
                                        currentRoute == "search" &&
                                        (currentTime - lastSearchTapTime) < 500
                                    ) {
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
                            NavigationRailItem(
                                icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                                modifier = Modifier.height(48.dp),
                                selected = currentRoute == "profile",
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
        } else Surface(
            modifier = Modifier
                .zIndex(2f)
                .align(Alignment.BottomCenter)
                .offset {
                    val currentProgress = 1f - (sheetOffset.value / collapsedOffset).coerceIn(0f, 1f)
                    val translationY = currentProgress * (300f + navigationBarHeightPx)
                    IntOffset(0, translationY.roundToInt())
                },
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .heightIn(min = 80.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SpaceEvenlyNavigationItem(
                    selected = currentRoute == "library",
                    onClick = {
                        val currentTime = System.currentTimeMillis()
                        if (currentRoute == "library" && (currentTime - lastLibraryTapTime) < 500) {
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
                    },
                    icon = Icons.Default.Home,
                    label = "Library"
                )
                SpaceEvenlyNavigationItem(
                    selected = currentRoute == "playlists",
                    onClick = {
                        navController.navigate("playlists") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = Icons.Default.LibraryMusic,
                    label = "Playlists"
                )
                SpaceEvenlyNavigationItem(
                    selected = currentRoute == "score",
                    onClick = {
                        navController.navigate("score") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = Icons.Default.Star,
                    label = "Scores"
                )
                SpaceEvenlyNavigationItem(
                    selected = currentRoute == "search",
                    onClick = {
                        val currentTime = System.currentTimeMillis()
                        if (currentRoute == "search" && (currentTime - lastSearchTapTime) < 500) {
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
                    },
                    icon = Icons.Default.Search,
                    label = "Search"
                )
                SpaceEvenlyNavigationItem(
                    selected = currentRoute == "profile",
                    onClick = {
                        navController.navigate("profile") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = Icons.Default.Person,
                    label = "Profile"
                )
            }
        }

        if (isLandscape) {
            val expansionProgress = landscapeExpansionProgress.value.coerceIn(0f, 1f)
            val morphBounds = landscapePlayerMorphBounds(
                compactSize = DpSize(
                    landscapeCompactPlayerWidth,
                    landscapePlayerHeight
                ),
                fullscreenSize = landscapeFullscreenSize,
                progress = expansionProgress
            )
            val leftCornerRadius = lerp(18.dp, 0.dp, expansionProgress)
            val morphedPlayerShape = AbsoluteRoundedCornerShape(
                topLeft = leftCornerRadius,
                bottomLeft = leftCornerRadius,
                topRight = 0.dp,
                bottomRight = 0.dp
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(5f)
            ) {
                // The right-hand action slots belong to the dock, not to the mini-player.
                // Keep them anchored while the compact player slides in; the expanding
                // player card is drawn above them and covers them in fullscreen.
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(LANDSCAPE_MINI_PLAYER_WIDTH_DP.dp)
                        .fillMaxHeight()
                ) {
                    LandscapeTopScreenAction(
                        currentRoute = currentRoute,
                        refreshEnabled = accessToken != null,
                        onPlaylistCreate = { playlistCreateRequest++ },
                        onScoreRefresh = { scoreRefreshRequest++ },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .fillMaxWidth()
                            .height(landscapeActionSlotHeight)
                    )
                    LandscapeBottomScreenAction(
                        currentRoute = currentRoute,
                        hasNowPlaying = nowPlaying != null,
                        activeDownloadCount = landscapeActiveDownloadCount,
                        onLibraryFindCurrent = {
                            libraryFindCurrentRequest++
                        },
                        onPlaylistImport = {
                            playlistImportRequest++
                        },
                        onSearchTasks = { searchTaskViewRequest++ },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .fillMaxWidth()
                            .height(landscapeActionSlotHeight)
                    )
                    if (currentRoute == "library") {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(
                                    top = (
                                        (landscapeActionSlotHeight -
                                            LANDSCAPE_SEARCH_BAR_HEIGHT_DP.dp) / 2
                                        ).coerceAtLeast(0.dp),
                                    end = (
                                        LANDSCAPE_MINI_PLAYER_WIDTH_DP.dp - 40.dp
                                        ) / 2
                                )
                                .zIndex(2f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnimatedVisibility(
                                visible = landscapeLibrarySearchVisible,
                                enter = expandHorizontally(
                                    expandFrom = Alignment.End,
                                    animationSpec = tween(220)
                                ) + fadeIn(tween(120)),
                                exit = shrinkHorizontally(
                                    shrinkTowards = Alignment.End,
                                    animationSpec = tween(180)
                                ) + fadeOut(tween(100)),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                LandscapeLibrarySearchBar(
                                    query = librarySearchQuery,
                                    onQueryChange = {
                                        librarySearchQuery = it
                                        if (it.isNotEmpty()) {
                                            librarySearchExpanded = true
                                        }
                                    },
                                    focusRequester = librarySearchFocusRequester
                                )
                            }
                            LandscapeSidebarIconButton(
                                onClick = {
                                    if (
                                        librarySearchExpanded &&
                                        librarySearchQuery.isEmpty()
                                    ) {
                                        librarySearchExpanded = false
                                        focusManager.clearFocus()
                                    } else {
                                        librarySearchExpanded = true
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = stringResource(R.string.search_cd_search)
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(morphBounds.width)
                        .height(morphBounds.height)
                        .offset {
                            IntOffset(landscapeSheetOffset.value.roundToInt(), 0)
                        }
                        .graphicsLayer {
                            alpha = if (
                                landscapeSheetOffset.value >= landscapePlayerCollapsedOffset
                            ) {
                                0f
                            } else {
                                1f
                            }
                        }
                        .draggable(
                            state = landscapeDraggableState,
                            orientation = Orientation.Horizontal,
                            enabled = nowPlaying != null,
                            onDragStarted = { landscapeDragging = true },
                            onDragStopped = { velocity ->
                                landscapeDragging = false
                                settleLandscapePlayer(velocity)
                            }
                        )
                        .clip(morphedPlayerShape)
                ) {
                    FullPlayer(
                        musicController = musicController,
                        settingsManager = settingsManager,
                        db = db,
                        isExpanded = isLandscapePlayerOpen,
                        onCollapse = {
                            scope.launch {
                                if (landscapeExpansionProgress.value > 0f) {
                                    landscapeExpansionProgress.animateTo(
                                        0f,
                                        animationSpec = tween(300)
                                    )
                                } else {
                                    landscapeSheetOffset.animateTo(
                                        landscapePlayerCollapsedOffset,
                                        animationSpec = tween(300)
                                    )
                                }
                            }
                        },
                        landscapeMorph = LandscapePlayerMorph(
                            progress = expansionProgress,
                            compactSize = DpSize(
                                landscapeCompactPlayerWidth,
                                landscapeCompactPlayerHeight
                            ),
                            fullscreenSize = landscapeFullscreenSize
                        ),
                        onEnterFullscreen = {
                            scope.launch {
                                if (landscapeSheetOffset.value > 0f) {
                                    landscapeSheetOffset.animateTo(
                                        0f,
                                        animationSpec = tween(180)
                                    )
                                }
                                landscapeExpansionProgress.animateTo(
                                    1f,
                                    animationSpec = tween(300)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Keep this pointer-input node alive for the duration of a drag. A swipe
                // that started on the mini-player can therefore continue from compact
                // into fullscreen after the mini-player visual has disappeared.
                if (!isLandscapePlayerFullyOpen || landscapeDragging) {
                    // Draw the overscan as its own layer above the compact player. Only
                    // the background is widened; the mini-player content stays 72dp wide.
                    if (nowPlaying != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .width(
                                    (
                                        LANDSCAPE_MINI_PLAYER_WIDTH_DP +
                                            LANDSCAPE_MINI_PLAYER_PROGRESS_SPACE_DP +
                                            LANDSCAPE_MINI_PLAYER_BACKGROUND_BLEED_DP
                                        ).dp
                                )
                                .height(landscapeMiniPlayerHeight)
                                .offset {
                                    IntOffset(
                                        LANDSCAPE_MINI_PLAYER_BACKGROUND_BLEED_DP.dp.roundToPx() +
                                            (
                                                (
                                                    landscapeSheetOffset.value -
                                                        landscapePlayerCollapsedOffset
                                                    ) * 0.3f
                                                ).roundToInt(),
                                        0
                                    )
                                }
                                .graphicsLayer {
                                    val currentProgress = 1f - (
                                        landscapeSheetOffset.value /
                                            landscapePlayerCollapsedOffset
                                        ).coerceIn(0f, 1f)
                                    val alphaValue =
                                        (1f - currentProgress * 4f).coerceIn(0f, 1f)
                                    alpha = if (alphaValue < 0.1f) 0f else alphaValue
                                }
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                shape = AbsoluteRoundedCornerShape(
                                    topLeft = 18.dp,
                                    bottomLeft = 18.dp,
                                    topRight = 0.dp,
                                    bottomRight = 0.dp
                                ),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                tonalElevation = 8.dp
                            ) {}
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(LANDSCAPE_MINI_PLAYER_WIDTH_DP.dp)
                            .height(landscapeMiniPlayerHeight)
                            .offset {
                                IntOffset(
                                    (
                                        (
                                            landscapeSheetOffset.value -
                                                landscapePlayerCollapsedOffset
                                            ) * 0.3f
                                        ).roundToInt(),
                                    0
                                )
                            }
                            .graphicsLayer {
                                val currentProgress = 1f - (
                                    landscapeSheetOffset.value /
                                        landscapePlayerCollapsedOffset
                                    ).coerceIn(0f, 1f)
                                val alphaValue =
                                    (1f - currentProgress * 4f).coerceIn(0f, 1f)
                                alpha = if (alphaValue < 0.1f) 0f else alphaValue
                            }
                            .draggable(
                                state = landscapeDraggableState,
                                orientation = Orientation.Horizontal,
                                enabled = nowPlaying != null,
                                onDragStarted = { landscapeDragging = true },
                                onDragStopped = { velocity ->
                                    landscapeDragging = false
                                    settleLandscapePlayer(velocity)
                                }
                            )
                    ) {
                        if (nowPlaying != null) {
                            LandscapeMiniPlayer(
                                musicController = musicController,
                                onClick = {
                                    scope.launch {
                                        landscapeSheetOffset.animateTo(
                                            0f,
                                            animationSpec = tween(300)
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        // 4. Portrait MiniPlayer (Top Layer) - Moves UP with Sheet
        if (!isLandscape && nowPlaying != null && !isSheetFullyOpen) {
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
