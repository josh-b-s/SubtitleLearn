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
import com.example.subtitlelearn.KnownWordsStore

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
            Text("No new words tracked this session.")
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
            Text("Quiz complete — ${words.size} words reviewed.")
            Spacer(Modifier.height(16.dp))
            Button(onClick = onFinish) { Text("Done") }
        }
        return
    }

    val (word, count) = words[index]
    val pinyin = Dictionary.getPinyin(word)
    val meaning = Dictionary.getMeaning(word)

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Word ${index + 1} / ${words.size}", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        Text("Heard $count time(s) this session", style = MaterialTheme.typography.labelSmall)
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

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = {
                    KnownWordsStore.markKnown(context, word)
                    revealed = false
                    index++
                }) { Text("I knew it") }

                Button(onClick = {
                    revealed = false
                    index++
                }) { Text("Didn't know it") }
            }
        }
    }
}