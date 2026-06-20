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
import com.example.subtitlelearn.SrsStore
import com.example.subtitlelearn.SuppressionSettings

private enum class StatusFilter { ALL, DUE, NOT_DUE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnownWordsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var showAddScreen by remember { mutableStateOf(false) }

    if (showAddScreen) {
        AddKnownWordScreen(
            onDone = {
                showAddScreen = false
                refreshTrigger++
            }
        )
        return
    }

    var suppressionEnabled by remember { mutableStateOf(SuppressionSettings.isEnabled(context)) }
    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf(StatusFilter.ALL) }
    var gradeFilter by remember { mutableStateOf("All") }
    var statusExpanded by remember { mutableStateOf(false) }
    var gradeExpanded by remember { mutableStateOf(false) }

    val allCards = remember(refreshTrigger) { SrsStore.allCards(context) }

    val filteredCards = remember(allCards, query, statusFilter, gradeFilter) {
        allCards.filter { card ->
            val matchesQuery = query.isBlank() || card.word.contains(query.trim())
            val matchesStatus = when (statusFilter) {
                StatusFilter.ALL -> true
                StatusFilter.DUE -> !SrsStore.isSuppressed(context, card.word)
                StatusFilter.NOT_DUE -> SrsStore.isSuppressed(context, card.word)
            }
            val matchesGrade = gradeFilter == "All" || SrsStore.gradeLabel(card.lastQuality) == gradeFilter
            matchesQuery && matchesStatus && matchesGrade
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Hide reviewed-word meanings", style = MaterialTheme.typography.titleMedium)
                Text("Off shows all meanings regardless of review state", style = MaterialTheme.typography.bodySmall)
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

        Button(onClick = { showAddScreen = true }, modifier = Modifier.fillMaxWidth()) {
            Text("+ Add word")
        }

        Spacer(Modifier.height(16.dp))
        Divider()
        Spacer(Modifier.height(16.dp))

        Text("Quiz history (${allCards.size})", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search word") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExposedDropdownMenuBox(
                expanded = statusExpanded,
                onExpandedChange = { statusExpanded = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = statusFilter.name.lowercase().replaceFirstChar { it.uppercase() },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Status") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) },
                    modifier = Modifier.menuAnchor()
                )
                ExposedDropdownMenu(expanded = statusExpanded, onDismissRequest = { statusExpanded = false }) {
                    StatusFilter.values().forEach { f ->
                        DropdownMenuItem(
                            text = { Text(f.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            onClick = { statusFilter = f; statusExpanded = false }
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = gradeExpanded,
                onExpandedChange = { gradeExpanded = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = gradeFilter,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Last grade") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = gradeExpanded) },
                    modifier = Modifier.menuAnchor()
                )
                ExposedDropdownMenu(expanded = gradeExpanded, onDismissRequest = { gradeExpanded = false }) {
                    listOf("All", "Forgot", "Hard", "Good", "Easy").forEach { g ->
                        DropdownMenuItem(text = { Text(g) }, onClick = { gradeFilter = g; gradeExpanded = false })
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(filteredCards) { card ->
                val meaning = Dictionary.getMeaning(card.word)
                val due = !SrsStore.isSuppressed(context, card.word)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(card.word, style = MaterialTheme.typography.titleMedium)
                        Text(meaning, style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                        AssistChip(
                            onClick = {},
                            label = { Text(SrsStore.gradeLabel(card.lastQuality)) }
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(if (due) "Due" else "Reviewed", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Divider()
            }
        }
    }
}