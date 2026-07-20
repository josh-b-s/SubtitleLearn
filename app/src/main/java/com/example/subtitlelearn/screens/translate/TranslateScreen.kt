package com.example.subtitlelearn.screens.translate

import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.subtitlelearn.dictionary.Dictionary
import com.example.subtitlelearn.srs.KnownWordsStore
import com.example.subtitlelearn.translate.MicTranscriber
import com.example.subtitlelearn.srs.SuppressionSettings

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TranslateScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // TextFieldValue so we can control cursor position for auto-scroll
    var inputValue by remember { mutableStateOf(TextFieldValue("")) }
    var dictVersion by remember { mutableIntStateOf(0) }
    var micActive by remember { mutableStateOf(false) }

    val transcriber = remember { MicTranscriber(context) }

    // Clean up transcriber if screen leaves composition
    DisposableEffect(Unit) {
        onDispose { transcriber.stop() }
    }

    // When mic produces text, replace input and move cursor to end → triggers auto-scroll
    val onMicText: (String) -> Unit = { text ->
        inputValue = TextFieldValue(text = text, selection = TextRange(text.length))
    }

    val availableDicts = remember { Dictionary.listAvailable(context) }
    var selectedDict by remember { mutableStateOf(Dictionary.currentFile) }
    var dictExpanded by remember { mutableStateOf(false) }

    val input = inputValue.text
    val words = remember(input, dictVersion) {
        Dictionary.segment(input).filter { it.isNotBlank() }
    }

    val flowScrollState = rememberScrollState()
    LaunchedEffect(words.size) {
        // Scroll word flow to bottom when new words appear
        flowScrollState.animateScrollTo(flowScrollState.maxValue)
    }

    fun startMic() {
        val started = transcriber.start(
            onPartial = onMicText,
            onError = { micActive = false },
            onConcurrentWarning = {
                Toast.makeText(
                    context,
                    "Screen-capture recording is also running — performance may be reduced",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
        micActive = started
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startMic() else micActive = false
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {

        // ── Dictionary dropdown ───────────────────────────────────────────────
        ExposedDropdownMenuBox(expanded = dictExpanded, onExpandedChange = { dictExpanded = it }) {
            OutlinedTextField(
                value = selectedDict,
                onValueChange = {},
                readOnly = true,
                label = { Text("Dictionary") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dictExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = dictExpanded, onDismissRequest = { dictExpanded = false }) {
                availableDicts.forEach { fileName ->
                    DropdownMenuItem(text = { Text(fileName) }, onClick = {
                        selectedDict = fileName
                        dictExpanded = false
                        Dictionary.switchTo(context, fileName)
                        dictVersion++
                    })
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Text input + mic button ───────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputValue,
                onValueChange = { inputValue = it },
                label = { Text("Type or speak Chinese…") },
                modifier = Modifier.weight(1f),
                minLines = 2,
                maxLines = 5
            )

            // Mic toggle button
            val micColor = if (micActive) Color(0xFFB00020) else MaterialTheme.colorScheme.primary
            Box(
                Modifier
                    .size(52.dp)
                    .background(micColor.copy(alpha = 0.12f), CircleShape)
                    .border(1.5.dp, micColor.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = {
                    if (micActive) {
                        transcriber.stop()
                        micActive = false
                    } else {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context, android.Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasPermission) {
                            startMic()
                        } else {
                            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }) {
                    Icon(
                        if (micActive) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = if (micActive) "Stop mic" else "Start mic",
                        tint = micColor
                    )
                }
            }
        }

        if (micActive) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Listening… tap mic to stop",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFB00020)
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Word flow — scrolls to bottom automatically ───────────────────────
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(flowScrollState),
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
    val suppressionOn = SuppressionSettings.isEnabled(context)
    val isKnown = suppressionOn && KnownWordsStore.isKnown(context, word)
    val pinyin = Dictionary.getPinyin(word)
    val meaning = if (isKnown) "" else Dictionary.getMeaning(word)
    val breakdown = Dictionary.breakdown(word)

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