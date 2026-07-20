package com.example.subtitlelearn.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Single source of truth for transcription output.
 * CaptureService emits here; OverlayService collects here.
 * extraBufferCapacity prevents dropping emissions if the collector is briefly busy.
 *
 * NOTE: if you have an OverlayService not included in this refactor, update its import
 * to `com.example.subtitlelearn.core.AppRepository`.
 */
object AppRepository {
    private val _transcription = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val transcription = _transcription.asSharedFlow()

    fun emitTranscription(text: String) {
        _transcription.tryEmit(text)
    }
}
