package com.example.subtitlelearn.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.subtitlelearn.Dictionary
import com.example.subtitlelearn.KnownWordsStore
import com.example.subtitlelearn.SuppressionSettings

@Composable
fun ManageWordsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    var suppressionEnabled by remember { mutableStateOf(SuppressionSettings.isEnabled(context)) }

    val searchResults = remember(query, refreshTrigger) { Dictionary.search(query) }
    val knownWords = remember(refreshTrigger) { KnownWordsStore.allKnown(context) }

    var newWord by remember { mutableStateOf("") }
    var newMeaning by remember { mutableStateOf("") }
    var newPinyin by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Hide known-word meanings", style = MaterialTheme.typography.titleMedium)
                Text(
                    "When off, all meanings show regardless of known list",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = suppressionEnabled,
                onCheckedChange = {
                    suppressionEnabled = it
                    SuppressionSettings.setEnabled(context, it)
                }
            )
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text("Add custom word", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(newWord, { newWord = it }, label = { Text("Word") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(newMeaning, { newMeaning = it }, label = { Text("Meaning") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(newPinyin, { newPinyin = it }, label = { Text("Pinyin (optional)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                Dictionary.addCustomEntry(newWord.trim(), newMeaning.trim(), newPinyin.trim())
                newWord = ""; newMeaning = ""; newPinyin = ""
                refreshTrigger++
            },
            enabled = newWord.isNotBlank() && newMeaning.isNotBlank()
        ) { Text("Add to dictionary") }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text("Search dictionary", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search word or meaning") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f, fill = false).heightIn(max = 240.dp)) {
            items(searchResults) { (word, meaning, pinyin) ->
                val isKnown = knownWords.contains(word)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("$word${if (pinyin.isNotEmpty()) " ($pinyin)" else ""}")
                        Text(meaning, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = {
                        if (isKnown) KnownWordsStore.markUnknown(context, word)
                        else KnownWordsStore.markKnown(context, word)
                        refreshTrigger++
                    }) {
                        Text(if (isKnown) "Unmark known" else "Mark known")
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text("Known words (${knownWords.size})", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(knownWords) { word ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(word)
                    TextButton(onClick = {
                        KnownWordsStore.markUnknown(context, word)
                        refreshTrigger++
                    }) { Text("Remove") }
                }
            }
        }
    }
}