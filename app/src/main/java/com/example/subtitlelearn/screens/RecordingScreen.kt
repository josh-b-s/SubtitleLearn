package com.example.subtitlelearn.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RecordingScreen(modifier: Modifier = Modifier, onStart: () -> Unit, onStop: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text("Start Capture")
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
            Text("Stop")
        }
    }
}