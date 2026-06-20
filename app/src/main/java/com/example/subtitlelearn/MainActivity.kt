package com.example.subtitlelearn

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.subtitlelearn.overlay.OverlayService
import com.example.subtitlelearn.screens.ManageWordsScreen
import com.example.subtitlelearn.screens.QuizScreen
import com.example.subtitlelearn.screens.RecordingScreen
import com.example.subtitlelearn.screens.TranslateScreen

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
            MaterialTheme(colorScheme = darkColorScheme()) {
                var quizWords by remember { mutableStateOf<List<Pair<String, Int>>?>(null) }

                if (quizWords != null) {
                    QuizScreen(words = quizWords!!, onFinish = {
                        WordTracker.reset()
                        quizWords = null
                    })
                } else {
                    AppScaffold(
                        onStart = {
                            if (!Settings.canDrawOverlays(this)) {
                                startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        "package:$packageName".toUri()
                                    )
                                )
                            } else {
                                captureRequest.launch(projectionManager.createScreenCaptureIntent())
                            }
                        },
                        onStop = {
                            stopService(Intent(this, CaptureService::class.java))
                            stopService(Intent(this, OverlayService::class.java))

                            val due = com.example.subtitlelearn.SrsStore.dueWords(this).toSet()
                            val sessionTop = WordTracker.topWords(this, n = 20)
                                .map { it.first }
                                .toSet()

                            val quizSet = (due + sessionTop).toList()
                            quizWords = quizSet.map { word ->
                                word to (WordTracker.topWords(this, n = 1000).toMap()[word] ?: 0)
                            }.take(15)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AppScaffold(onStart: () -> Unit, onStop: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Record") },
                    label = { Text("Record") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Create, contentDescription = "Translate") },
                    label = { Text("Translate") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.List, contentDescription = "Manage Words") },
                    label = { Text("Words") }
                )
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> RecordingScreen(Modifier.padding(padding), onStart, onStop)
            1 -> TranslateScreen(Modifier.padding(padding))
            2 -> ManageWordsScreen(Modifier.padding(padding))
        }
    }
}