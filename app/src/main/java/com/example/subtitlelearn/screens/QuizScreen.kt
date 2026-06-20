package com.example.subtitlelearn.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.subtitlelearn.Dictionary
import com.example.subtitlelearn.SrsStore

@Composable
fun QuizScreen(words: List<Pair<String, Int>>, onFinish: () -> Unit) {
    val context = LocalContext.current
    var index by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(false) }

    if (words.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("No words due for review this session.")
            Spacer(Modifier.height(16.dp))
            Button(onClick = onFinish) { Text("Done") }
        }
        return
    }

    if (index >= words.size) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Review complete — ${words.size} words graded.")
            Spacer(Modifier.height(16.dp))
            Button(onClick = onFinish) { Text("Done") }
        }
        return
    }

    val (word, count) = words[index]
    val pinyin = Dictionary.getPinyin(word)
    val meaning = Dictionary.getMeaning(word)
    val state = SrsStore.getState(context, word)

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Word ${index + 1} / ${words.size}", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        val statusText = if (state == null) "New word" else "Seen ${state.repetitions} time(s) before"
        Text(statusText, style = MaterialTheme.typography.labelSmall)
        if (count > 0) {
            Text("Heard $count time(s) this session", style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(24.dp))

        Text(word, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
        if (pinyin.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(pinyin, style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(24.dp))

        if (!revealed) {
            Text("What does this mean?")
            Spacer(Modifier.height(16.dp))
            Button(onClick = { revealed = true }) { Text("Reveal meaning") }
        } else {
            Text(meaning, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(24.dp))
            Text("How well did you know it?", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradeButton("Forgot", onClick = {
                    SrsStore.review(context, word, quality = 1)
                    revealed = false; index++
                })
                GradeButton("Hard", onClick = {
                    SrsStore.review(context, word, quality = 3)
                    revealed = false; index++
                })
                GradeButton("Good", onClick = {
                    SrsStore.review(context, word, quality = 4)
                    revealed = false; index++
                })
                GradeButton("Easy", onClick = {
                    SrsStore.review(context, word, quality = 5)
                    revealed = false; index++
                })
            }
        }
    }
}

@Composable
private fun GradeButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) { Text(label) }
}