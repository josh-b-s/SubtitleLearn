package com.example.subtitlelearn.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.subtitlelearn.Dictionary
import com.example.subtitlelearn.KnownWordsStore

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TranslateScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var dictVersion by remember { mutableIntStateOf(0) } // bump to force re-segment after switch

    val availableDicts = remember { Dictionary.listAvailable(context) }
    var selectedDict by remember { mutableStateOf(Dictionary.currentFile) }
    var expanded by remember { mutableStateOf(false) }

    val words = remember(input, dictVersion) {
        Dictionary.segment(input).filter { it.isNotBlank() }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = selectedDict,
                onValueChange = {},
                readOnly = true,
                label = { Text("Dictionary") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                availableDicts.forEach { fileName ->
                    DropdownMenuItem(
                        text = { Text(fileName) },
                        onClick = {
                            selectedDict = fileName
                            expanded = false
                            Dictionary.switchTo(context, fileName)
                            dictVersion++ // triggers re-segmentation with new dictionary
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Type text to be translated...") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            words.forEach { word -> WordBox(word) }
        }
    }
}

@Composable
private fun WordBox(word: String) {
    val context = LocalContext.current
    val isKnown = remember(word) { KnownWordsStore.isKnown(context, word) }
    val pinyin = Dictionary.getPinyin(word)
    val meaning = if (isKnown) "" else Dictionary.getMeaning(word)
    val breakdown = if (word.length > 1) {
        word.map { ch ->
            val m = Dictionary.getMeaning(ch.toString())
            if (m.isNotEmpty()) "$ch·$m" else ch.toString()
        }.joinToString("  ")
    } else ""

    Column(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (pinyin.isNotEmpty()) Text(pinyin, color = Color.Yellow, fontSize = 14.sp)
        Text(word, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        if (breakdown.isNotEmpty()) Text(breakdown, color = Color(0xFFA0D2FF), fontSize = 11.sp)
        if (meaning.isNotEmpty()) Text(meaning, color = Color.White, fontSize = 14.sp)
    }
}