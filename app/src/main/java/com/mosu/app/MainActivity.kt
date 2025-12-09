package com.mosu.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mosu.app.data.db.AppDatabase
import com.mosu.app.data.db.BeatmapEntity
import com.mosu.app.data.repository.OsuRepository
import com.mosu.app.domain.download.BeatmapDownloader
import com.mosu.app.domain.download.DownloadState
import com.mosu.app.domain.download.ZipExtractor
import com.mosu.app.player.MusicController
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    private val repository = OsuRepository()
    // REPLACE THIS WITH YOUR CLIENT ID for the login URL
    private val clientId = "46495"
    private val redirectUri = "mosu://callback"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Handle Deep Link
        val data: Uri? = intent?.data
        val code = data?.getQueryParameter("code")

        // Initialize DB
        val db = AppDatabase.getDatabase(this)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LoginScreen(
                        authCode = code,
                        onLoginClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://osu.ppy.sh/oauth/authorize?client_id=$clientId&redirect_uri=$redirectUri&response_type=code&scope=public+identify")
                            )
                            startActivity(intent)
                        },
                        repository = repository,
                        db = db
                    )
                }
            }
        }
    }
}

@Composable
fun LoginScreen(
    authCode: String?, 
    onLoginClick: () -> Unit,
    repository: OsuRepository,
    db: AppDatabase
) {
    var statusText by remember { mutableStateOf("Ready to Login") }
    // State to hold the token
    var accessToken by remember { mutableStateOf<String?>(null) }
    // State to hold the first beatmap ID found
    var firstBeatmapId by remember { mutableStateOf<Long?>(null) }
    // Hold full beatmapset info for the first map to save to DB
    var firstBeatmapTitle by remember { mutableStateOf("") }
    var firstBeatmapArtist by remember { mutableStateOf("") }
    var firstBeatmapCreator by remember { mutableStateOf("") }
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val downloader = remember { BeatmapDownloader(context) }
    val extractor = remember { ZipExtractor(context) }
    
    // Music Controller
    val musicController = remember { MusicController(context) }
    
    // Clean up controller
    DisposableEffect(Unit) {
        onDispose {
            musicController.release()
        }
    }

    // DB Observer
    val downloadedMaps by db.beatmapDao().getAllBeatmaps().collectAsState(initial = emptyList())

    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "Mosu Music Player", style = MaterialTheme.typography.headlineMedium)
        
        Button(
            onClick = onLoginClick,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("Login with osu!")
        }

        // Test Download Button (Only visible if we have an ID)
        if (firstBeatmapId != null) {
            Button(
                onClick = {
                    scope.launch {
                        statusText += "\nStarting download for ID: $firstBeatmapId..."
                        downloader.downloadBeatmap(firstBeatmapId!!, accessToken).collect { state ->
                            when (state) {
                                is DownloadState.Downloading -> {
                                    statusText = "Downloading from ${state.source}\nProgress: ${state.progress}%"
                                }
                                is DownloadState.Downloaded -> {
                                    statusText = "Downloaded .osz! Extracting..."
                                    try {
                                        val outputDir = extractor.extractBeatmap(state.file, firstBeatmapId!!)
                                        statusText = "Success! Extracted to:\n${outputDir.absolutePath}"
                                        
                                        // Save to DB
                                        val audioFile = File(outputDir, "audio.mp3") // Simplified assumption from extractor
                                        val coverFile = File(outputDir, "cover.jpg") 
                                        
                                        val entity = BeatmapEntity(
                                            id = firstBeatmapId!!,
                                            title = firstBeatmapTitle,
                                            artist = firstBeatmapArtist,
                                            creator = firstBeatmapCreator,
                                            audioPath = audioFile.absolutePath,
                                            coverPath = coverFile.absolutePath
                                        )
                                        db.beatmapDao().insertBeatmap(entity)
                                        statusText += "\nSaved to Database!"
                                        
                                    } catch (e: Exception) {
                                        statusText = "Extraction/DB Failed: ${e.message}"
                                        e.printStackTrace()
                                    }
                                }
                                is DownloadState.Error -> {
                                    statusText = "Download Failed: ${state.message}"
                                }
                                else -> {}
                            }
                        }
                    }
                },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Test Download (Map #$firstBeatmapId)")
            }
        }

        Text(
            text = statusText,
            modifier = Modifier.padding(top = 16.dp)
        )
        
        Divider(modifier = Modifier.padding(vertical = 16.dp))
        
        Text(text = "Downloaded Songs (${downloadedMaps.size}):", style = MaterialTheme.typography.titleMedium)
        LazyColumn {
            items(downloadedMaps) { map ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { musicController.playSong(map) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play")
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(text = map.title, style = MaterialTheme.typography.bodyLarge)
                        Text(text = map.artist, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        LaunchedEffect(authCode) {
            if (authCode != null) {
                statusText = "Got Code! Exchanging for token..."
                try {
                    val tokenResponse = repository.exchangeCodeForToken(authCode)
                    accessToken = tokenResponse.accessToken
                    statusText = "Success! Token: ${tokenResponse.accessToken.take(10)}..."
                    
                    // Fetch Current User
                    val me = repository.getMe(tokenResponse.accessToken)
                    statusText += "\nLogged in as: ${me.username} (ID: ${me.id})"
                    
                    // Fetch User's Beatmaps
                    val beatmaps = repository.getUserMostPlayed(tokenResponse.accessToken, me.id.toString())
                    statusText += "\nFound ${beatmaps.size} most played maps."
                    
                    // Save the first ID for testing
                    if (beatmaps.isNotEmpty()) {
                        val map = beatmaps[0].beatmapset
                        firstBeatmapId = map.id
                        firstBeatmapTitle = map.title
                        firstBeatmapArtist = map.artist
                        firstBeatmapCreator = map.creator
                        
                        statusText += "\nSelected Map ID: $firstBeatmapId (${map.title})"
                    }
                    
                } catch (e: Exception) {
                    statusText = "Error: ${e.message}"
                    e.printStackTrace()
                }
            }
        }
    }
}
