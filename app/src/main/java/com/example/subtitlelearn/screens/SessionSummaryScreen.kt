package com.example.subtitlelearn.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SessionStats(
    val alreadyKnown: Int,    // seen this session but suppressed — user already knows them
    val newWords: Int,         // never seen before in SRS
    val dueReviews: Int,       // in SRS and due — came up again today
    val topWords: List<Pair<String, Int>>,
    val quizCount: Int         // how many words will be in the quiz
)

private val colorKnown   = Color(0xFF2E7D32)   // green
private val colorNew     = Color(0xFF1565C0)   // blue
private val colorDue     = Color(0xFFE65100)   // orange

@Composable
fun SessionSummaryScreen(stats: SessionStats, onStartReview: () -> Unit, onSkip: () -> Unit) {
    val total = (stats.alreadyKnown + stats.newWords + stats.dueReviews).coerceAtLeast(1)

    // Animate the bar in on entry
    var barVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { barVisible = true }
    val barProgress by animateFloatAsState(
        targetValue = if (barVisible) 1f else 0f,
        animationSpec = tween(800),
        label = "bar"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            Text("Session complete", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "${stats.newWords + stats.dueReviews + stats.alreadyKnown} unique words heard",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(32.dp))

            // ── Stacked bar ───────────────────────────────────────────────────
            Text(
                "Word breakdown",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth(barProgress)
                        .fillMaxHeight()
                ) {
                    val knownW  = stats.alreadyKnown.toFloat() / total
                    val newW    = stats.newWords.toFloat() / total
                    val dueW    = stats.dueReviews.toFloat() / total

                    if (stats.alreadyKnown > 0)
                        Box(Modifier.weight(knownW).fillMaxHeight().background(colorKnown))
                    if (stats.newWords > 0)
                        Box(Modifier.weight(newW).fillMaxHeight().background(colorNew))
                    if (stats.dueReviews > 0)
                        Box(Modifier.weight(dueW).fillMaxHeight().background(colorDue))
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Legend ────────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LegendItem(colorKnown, "Already known", stats.alreadyKnown)
                LegendItem(colorNew,   "New words",     stats.newWords)
                LegendItem(colorDue,   "Due reviews",   stats.dueReviews)
            }

            Spacer(Modifier.height(32.dp))
            Divider()
            Spacer(Modifier.height(24.dp))

            // ── Top words ─────────────────────────────────────────────────────
            if (stats.topWords.isNotEmpty()) {
                Text(
                    "Most heard this session",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))

                val maxCount = stats.topWords.maxOf { it.second }.coerceAtLeast(1)
                stats.topWords.take(8).forEach { (word, count) ->
                    TopWordRow(word, count, maxCount)
                    Spacer(Modifier.height(6.dp))
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── Actions ───────────────────────────────────────────────────────
            if (stats.quizCount > 0) {
                Button(
                    onClick = onStartReview,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start review  ·  ${stats.quizCount} word${if (stats.quizCount != 1) "s" else ""}")
                }
                Spacer(Modifier.height(8.dp))
            }

            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Text(if (stats.quizCount > 0) "Skip review" else "Done")
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Column {
            Text(count.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TopWordRow(word: String, count: Int, maxCount: Int) {
    val animProgress by animateFloatAsState(
        targetValue = count.toFloat() / maxCount,
        animationSpec = tween(600),
        label = "word_bar_$word"
    )
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            word,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(56.dp)
        )
        Box(
            Modifier
                .weight(1f)
                .height(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(9.dp))
                    .background(colorNew)
            )
        }
        Text(
            "×$count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp)
        )
    }
}