package com.example.subtitlelearn.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.subtitlelearn.Dictionary
import com.example.subtitlelearn.SrsStore

@Composable
fun AddKnownWordScreen(onDone: () -> Unit) {
    BackHandler(onBack = onDone)

    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var selectedWord by remember { mutableStateOf<String?>(null) }
    var customWord by remember { mutableStateOf("") }

    val searchResults = remember(query) { if (query.isBlank()) emptyList() else Dictionary.search(query) }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(16.dp)
    ) {

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Add a word", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onDone) { Text("Cancel") }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it; selectedWord = null },
            label = { Text("Search dictionary") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f, fill = false).heightIn(max = 200.dp)) {
            items(searchResults) { (word, meaning, pinyin) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("$word${if (pinyin.isNotEmpty()) " ($pinyin)" else ""}")
                        Text(meaning, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { selectedWord = word; customWord = "" }) { Text("Select") }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Divider()
        Spacer(Modifier.height(16.dp))

        Text("Or type a word not in the dictionary", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = customWord,
            onValueChange = { customWord = it; selectedWord = null },
            label = { Text("Word") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))

        val finalWord = selectedWord ?: customWord.trim().ifBlank { null }

        if (finalWord != null) {
            Text("Selected: $finalWord", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Text("Initial grade", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1 to "Forgot", 3 to "Hard", 4 to "Good", 5 to "Easy").forEach { (quality, label) ->
                    OutlinedButton(onClick = {
                        SrsStore.review(context, finalWord, quality)
                        onDone()
                    }) { Text(label) }
                }
            }
        }
    }
}