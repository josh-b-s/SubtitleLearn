package com.example.subtitlelearn.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RecordingScreen(
    modifier: Modifier = Modifier,
    isRecording: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (isRecording) "Recording…" else "Ready",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(40.dp))

        BigCircleButton(
            isRecording = isRecording,
            onClick = { if (isRecording) onStop() else onStart() }
        )

        Spacer(Modifier.height(24.dp))

        Text(
            if (isRecording) "Tap to stop and review" else "Tap to start capturing",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BigCircleButton(isRecording: Boolean, onClick: () -> Unit) {
    // Pulse animation only while recording
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Outer glow ring when recording
    if (isRecording) {
        Box(
            Modifier
                .size(200.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(Color(0xFFB00020).copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            CoreButton(isRecording, onClick)
        }
    } else {
        CoreButton(isRecording, onClick)
    }
}

@Composable
private fun CoreButton(isRecording: Boolean, onClick: () -> Unit) {
    val bgColor = if (isRecording) Color(0xFFB00020) else MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .size(160.dp)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            onClick = onClick,
            color = Color.Transparent,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (isRecording) "Stop" else "Start",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (isRecording) {
                    Text(
                        "& Review",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}