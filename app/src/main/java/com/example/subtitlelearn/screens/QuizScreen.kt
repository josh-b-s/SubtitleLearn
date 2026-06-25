package com.example.subtitlelearn.screens

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.subtitlelearn.AudioClipStore
import com.example.subtitlelearn.Dictionary
import com.example.subtitlelearn.SrsStore
import com.example.subtitlelearn.WordTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val grades = listOf(
    1 to ("No idea"  to Color(0xFFB00020)),
    3 to ("Almost"   to Color(0xFFE65100)),
    4 to ("Got it"   to Color(0xFF2E7D32)),
    5 to ("Too easy" to Color(0xFF1565C0))
)

@Composable
fun QuizScreen(words: List<Pair<String, Int>>, onFinish: () -> Unit) {
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when {
            words.isEmpty() -> QuizEndState(
                message = "No words due for review this session.",
                onFinish = onFinish
            )
            else -> ActiveQuiz(words = words, context = context, onFinish = onFinish)
        }
    }
}

@Composable
private fun QuizEndState(message: String, onFinish: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onFinish) { Text("Done") }
    }
}

@Composable
private fun ActiveQuiz(
    words: List<Pair<String, Int>>,
    context: Context,
    onFinish: () -> Unit
) {
    var index by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (index >= words.size) {
        QuizEndState(
            message = "Review complete — ${words.size} words graded.",
            onFinish = onFinish
        )
        return
    }

    val (word, count) = words[index]
    val pinyin = Dictionary.getPinyin(word)
    val meaning = Dictionary.getMeaning(word)
    val breakdown = if (word.length > 1) {
        word.map { ch ->
            val m = Dictionary.getMeaning(ch.toString())
            if (m.isNotEmpty()) "$ch·$m" else ch.toString()
        }.joinToString("  ")
    } else ""
    val state = SrsStore.getState(context, word)
    val hasClip = AudioClipStore.hasClip(context, word)
    Log.d("QUIZ", "index=$index word='$word' hasClip=$hasClip")

    LaunchedEffect(index) {
        revealed = false
        isPlaying = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Word ${index + 1} / ${words.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state != null && state.repetitions > 0) {
                Text(
                    "Reviewed ${state.repetitions}× · next in ${state.intervalDays}d",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "New word",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (count > 0) {
                Text(
                    "Heard ${count}× this session",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (pinyin.isNotEmpty()) {
                    Text(pinyin, style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                }
                Text(word, fontSize = 64.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (breakdown.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(breakdown, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
                if (revealed && meaning.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Divider()
                    Spacer(Modifier.height(16.dp))
                    Text(meaning, style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (hasClip) {
                FilledTonalButton(
                    onClick = {
                        if (!isPlaying) {
                            isPlaying = true
                            scope.launch(Dispatchers.IO) {
                                playClip(context, word)
                                isPlaying = false
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isPlaying) "Playing…" else "Replay audio")
                }
            }

            if (!revealed) {
                Button(
                    onClick = { revealed = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Show meaning")
                }
            } else {
                Text(
                    "How well did you know it?",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    grades.forEach { (quality, pair) ->
                        val (label, color) = pair
                        Button(
                            onClick = {
                                SrsStore.review(context, word, quality)
                                index++
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = color)
                        ) {
                            Text(label, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

private fun playClip(context: Context, word: String) {
    val samples = AudioClipStore.loadClip(context, word) ?: return
    Log.d("PLAY", "loaded ${samples.size} samples first10=${samples.take(10).toList()}")

    val track = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(16000)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setBufferSizeInBytes(samples.size * 2)
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build()

    try {
        track.write(samples, 0, samples.size)

        val latch = java.util.concurrent.CountDownLatch(1)
        track.notificationMarkerPosition = samples.size - 1
        track.setPlaybackPositionUpdateListener(
            object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack) {
                    Log.d("PLAY", "marker reached for word='$word'")
                    latch.countDown()
                }
                override fun onPeriodicNotification(t: AudioTrack) = Unit
            }
        )

        track.play()
        Log.d("PLAY", "track.play() called, waiting for marker")

        val clipDurationMs = samples.size * 1000L / 16000L
        val awaited = latch.await(clipDurationMs + 2000L, java.util.concurrent.TimeUnit.MILLISECONDS)
        Log.d("PLAY", "latch done awaited=$awaited for word='$word'")

    } finally {
        track.release()
    }
}