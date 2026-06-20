package com.example.subtitlelearn.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.subtitlelearn.Dictionary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    val availableDicts = remember { Dictionary.listAvailable(context) }
    var selectedDict by remember { mutableStateOf(Dictionary.currentFile) }
    var dictExpanded by remember { mutableStateOf(false) }

    var newWord by remember { mutableStateOf("") }
    var newMeaning by remember { mutableStateOf("") }
    var newPinyin by remember { mutableStateOf("") }

    val searchResults = remember(query, refreshTrigger, selectedDict) { Dictionary.search(query) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {

        Text("Active dictionary", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        ExposedDropdownMenuBox(expanded = dictExpanded, onExpandedChange = { dictExpanded = it }) {
            OutlinedTextField(
                value = selectedDict,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dictExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = dictExpanded, onDismissRequest = { dictExpanded = false }) {
                availableDicts.forEach { fileName ->
                    DropdownMenuItem(
                        text = { Text(fileName) },
                        onClick = {
                            selectedDict = fileName
                            dictExpanded = false
                            Dictionary.switchTo(context, fileName)
                            refreshTrigger++
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Divider()
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
        Divider()
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

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(searchResults) { (word, meaning, pinyin) ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text("$word${if (pinyin.isNotEmpty()) " ($pinyin)" else ""}")
                    Text(meaning, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}