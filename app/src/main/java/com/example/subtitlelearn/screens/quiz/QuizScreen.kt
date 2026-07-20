package com.example.subtitlelearn.screens.quiz

import androidx.compose.animation.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.subtitlelearn.audio.AudioClipStore
import com.example.subtitlelearn.audio.AudioPlayer
import com.example.subtitlelearn.dictionary.Dictionary
import com.example.subtitlelearn.srs.SrsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val grades = listOf(
    1 to ("No idea"  to Color(0xFFB00020)),   // red   — reset
    3 to ("Roughly"  to Color(0xFFE65100)),   // orange — slow growth
    5 to ("Got it"   to Color(0xFF2E7D32))    // green  — fast growth
)

private enum class CardDirection { RECOGNITION, PRODUCTION }

@Composable
fun QuizScreen(words: List<Pair<String, Int>>, onFinish: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (words.isEmpty()) {
            QuizEndState("No words due for review this session.", onFinish)
        } else {
            ActiveQuiz(words = words, onFinish = onFinish)
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
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onFinish) { Text("Done") }
    }
}

@Composable
private fun ActiveQuiz(words: List<Pair<String, Int>>, onFinish: () -> Unit) {
    val context = LocalContext.current

    // Assign each card a random direction upfront so it doesn't flip on recomposition
    val directions = remember(words) {
        words.map {
            if (Math.random() < 0.5) CardDirection.RECOGNITION else CardDirection.PRODUCTION
        }
    }

    var index by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (index >= words.size) {
        QuizEndState("Review complete — ${words.size} words graded.", onFinish)
        return
    }

    val (word, count) = words[index]
    val direction = directions[index]
    val pinyin = Dictionary.getPinyin(word)
    val meaning = Dictionary.getMeaning(word)
    val breakdown = Dictionary.breakdown(word)
    val state = SrsStore.getState(context, word)
    val hasClip = AudioClipStore.hasClip(context, word)

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
        // ── Meta row ──────────────────────────────────────────────────────────
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Word ${index + 1} / ${words.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DirectionChip(direction)
            }
            Spacer(Modifier.height(4.dp))
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

        // ── Card ──────────────────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (direction) {
                    CardDirection.RECOGNITION -> RecognitionFront(
                        word = word,
                        pinyin = pinyin,
                        breakdown = breakdown,
                        meaning = meaning,
                        revealed = revealed
                    )
                    CardDirection.PRODUCTION -> ProductionFront(
                        word = word,
                        pinyin = pinyin,
                        breakdown = breakdown,
                        meaning = meaning,
                        revealed = revealed
                    )
                }
            }
        }

        // ── Controls ──────────────────────────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Audio replay — only useful on recognition cards (hearing the word)
            if (hasClip && direction == CardDirection.RECOGNITION) {
                FilledTonalButton(onClick = {
                    if (!isPlaying) {
                        isPlaying = true
                        scope.launch(Dispatchers.IO) {
                            AudioPlayer.playClip(context, word)
                            isPlaying = false
                        }
                    }
                }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isPlaying) "Playing…" else "Replay audio")
                }
            }

            if (!revealed) {
                val prompt = when (direction) {
                    CardDirection.RECOGNITION -> "Show meaning"
                    CardDirection.PRODUCTION  -> "Show Chinese"
                }
                Button(onClick = { revealed = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(prompt)
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
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = color)
                        ) {
                            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

// ── Card face composables ─────────────────────────────────────────────────────

/**
 * Recognition: show Chinese/pinyin up front, reveal meaning on tap.
 */
@Composable
private fun RecognitionFront(
    word: String,
    pinyin: String,
    breakdown: String,
    meaning: String,
    revealed: Boolean
) {
    if (pinyin.isNotEmpty()) {
        Text(pinyin, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
    }
    Text(word, fontSize = 64.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (breakdown.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(
            breakdown,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
    AnimatedVisibility(visible = revealed) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(16.dp))
            Divider()
            Spacer(Modifier.height(16.dp))
            Text(
                meaning,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Production: show English meaning up front, reveal Chinese/pinyin on tap.
 * This forces the user to actively recall the word before seeing it.
 */
@Composable
private fun ProductionFront(
    word: String,
    pinyin: String,
    breakdown: String,
    meaning: String,
    revealed: Boolean
) {
    Text(
        "What's the Chinese word for…",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    )
    Spacer(Modifier.height(12.dp))
    Text(
        meaning,
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    AnimatedVisibility(visible = revealed) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(16.dp))
            Divider()
            Spacer(Modifier.height(16.dp))
            if (pinyin.isNotEmpty()) {
                Text(
                    pinyin,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                word,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (breakdown.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    breakdown,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ── Direction label ───────────────────────────────────────────────────────────

@Composable
private fun DirectionChip(direction: CardDirection) {
    val (label, color) = when (direction) {
        CardDirection.RECOGNITION -> "Reading"   to MaterialTheme.colorScheme.primaryContainer
        CardDirection.PRODUCTION  -> "Recall"    to MaterialTheme.colorScheme.secondaryContainer
    }
    Surface(
        color = color,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

// ── Playback ──────────────────────────────────────────────────────────────────
