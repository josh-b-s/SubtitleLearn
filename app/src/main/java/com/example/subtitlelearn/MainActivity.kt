package com.example.subtitlelearn

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.subtitlelearn.overlay.OverlayService
import com.example.subtitlelearn.screens.DictionaryScreen
import com.example.subtitlelearn.screens.KnownWordsScreen
import com.example.subtitlelearn.screens.QuizScreen
import com.example.subtitlelearn.screens.RecordingScreen
import com.example.subtitlelearn.screens.SessionStats
import com.example.subtitlelearn.screens.SessionSummaryScreen
import com.example.subtitlelearn.screens.StatsScreen
import com.example.subtitlelearn.screens.TranslateScreen

// Set to false before shipping
const val DEBUG = true

class MainActivity : ComponentActivity() {


    private val projectionManager by lazy {
        getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private val captureRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                WordTracker.reset()
                startService(Intent(this, OverlayService::class.java))
                ContextCompat.startForegroundService(
                    this,
                    Intent(this, CaptureService::class.java).apply {
                        putExtra("resultCode", result.resultCode)
                        putExtra("data", result.data)
                    }
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Dictionary.load(this)

        setContent {
            MaterialTheme( colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                var isRecording by remember { mutableStateOf(false) }
                var sessionStats by remember { mutableStateOf<SessionStats?>(null) }
                var quizWords by remember { mutableStateOf<List<Pair<String, Int>>?>(null) }

                when {
                    quizWords != null -> QuizScreen(
                        words = quizWords!!,
                        onFinish = { quizWords = null }
                    )

                    sessionStats != null -> SessionSummaryScreen(
                        stats = sessionStats!!,
                        onStartReview = { sessionStats = null },
                        onSkip = {
                            sessionStats = null
                            quizWords = null
                        }
                    )

                    else -> AppScaffold(
                        isRecording = isRecording,
                        onStart = {
                            if (!Settings.canDrawOverlays(this)) {
                                startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        "package:$packageName".toUri()
                                    )
                                )
                            } else {
                                isRecording = true
                                captureRequest.launch(projectionManager.createScreenCaptureIntent())
                            }
                        },
                        onStop = {
                            isRecording = false
                            stopService(Intent(this, CaptureService::class.java))
                            stopService(Intent(this, OverlayService::class.java))

                            val allSeen = WordTracker.allSeenWords()
                            val trackedInSrs = SrsStore.allTracked(this).toSet()
                            val dueSet = SrsStore.dueWords(this).toSet()

                            val alreadyKnown = allSeen.count { KnownWordsStore.isKnown(this, it) }
                            val newWords = allSeen.count { it !in trackedInSrs }
                            val dueReviews = allSeen.count { it in dueSet }

                            val topWords = WordTracker.topWords(this, n = 15)
                            AudioClipStore.persistWords(this, topWords.map { it.first })

                            val due = dueSet
                            val sessionWordSet = topWords.map { it.first }.toSet()
                            val quizSet = (due.intersect(sessionWordSet) + sessionWordSet)
                                .take(15)
                                .map { word -> word to (topWords.toMap()[word] ?: 0) }

                            quizWords = quizSet
                            sessionStats = SessionStats(
                                alreadyKnown = alreadyKnown,
                                newWords = newWords,
                                dueReviews = dueReviews,
                                topWords = topWords.take(8),
                                quizCount = quizSet.size
                            )
                            WordTracker.reset()
                        }
                    )
                }
            }
        }
    }

    private val micPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                // show a message / toast — permission denied
            }
        }

    private fun ensureMicPermission(): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) micPermissionRequest.launch(Manifest.permission.RECORD_AUDIO)
        return granted
    }
}

@Composable
fun AppScaffold(isRecording: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.ScreenShare, contentDescription = "Record") },
                    label = { Text("Record") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Mic, contentDescription = "Translate") },
                    label = { Text("Translate") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2, onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.MenuBook, contentDescription = "Dictionary") },
                    label = { Text("Dictionary") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3, onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Bookmark, contentDescription = "Known") },
                    label = { Text("Known") }
                )
                NavigationBarItem(
                    selected = selectedTab == 4, onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Default.BarChart, contentDescription = "Stats") },
                    label = { Text("Stats") }
                )
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> RecordingScreen(Modifier.padding(padding), isRecording, onStart, onStop)
            1 -> TranslateScreen(Modifier.padding(padding))
            2 -> DictionaryScreen(Modifier.padding(padding))
            3 -> KnownWordsScreen(Modifier.padding(padding))
            4 -> StatsScreen(Modifier.padding(padding))
        }
    }
}