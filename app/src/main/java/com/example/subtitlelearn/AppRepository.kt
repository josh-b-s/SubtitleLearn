package com.example.subtitlelearn

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Single source of truth for transcription output.
 * CaptureService emits here; OverlayService collects here.
 * extraBufferCapacity prevents dropping emissions if the collector is briefly busy.
 */
object AppRepository {
    private val _transcription = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val transcription = _transcription.asSharedFlow()

    fun emitTranscription(text: String) {
        _transcription.tryEmit(text)
    }
}