package com.example.subtitlelearn.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import com.example.subtitlelearn.AppRepository
import com.example.subtitlelearn.Dictionary
import com.example.subtitlelearn.Dictionary.segment
import com.example.subtitlelearn.KnownWordsStore
import com.example.subtitlelearn.SuppressionSettings
import com.example.subtitlelearn.WordTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Manages the overlay window lifecycle and routes transcription text to PinyinOverlayView.
 * Collects from AppRepository.transcription — no direct coupling to CaptureService.
 */
class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var overlayView: PinyinOverlayView

    // Main dispatcher so setText() is always called on the UI thread
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var downX = 0f
    private var downY = 0f
    private var dragged = false

    companion object {
        private const val DRAG_THRESHOLD_PX = 12f
    }

    override fun onCreate() {
        super.onCreate()

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = PinyinOverlayView(this).apply { setPadding(24, 16, 24, 16) }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        wm.addView(overlayView, params)
        setupTouchHandler()
        collectTranscription()
    }

    private fun setupTouchHandler() {
        overlayView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    dragged = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragged && (abs(dx) > DRAG_THRESHOLD_PX || abs(dy) > DRAG_THRESHOLD_PX)) {
                        dragged = true
                    }
                    if (dragged) {
                        params.x += dx.toInt()
                        params.y += dy.toInt()
                        downX = event.rawX
                        downY = event.rawY
                        wm.updateViewLayout(overlayView, params)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!dragged) overlayView.performClick()
                    true
                }

                else -> false
            }
        }
    }

    private fun collectTranscription() {
        scope.launch {
            AppRepository.transcription.collect { text ->
                val words = segment(text).filter { it.isNotBlank() }
                words.forEach { WordTracker.record(it) }
                val suppressionOn = SuppressionSettings.isEnabled(this@OverlayService)
                val meanings = words.associateWith { word ->
                    if (suppressionOn && KnownWordsStore.isKnown(this@OverlayService, word)) ""
                    else Dictionary.getMeaning(word)
                }
                overlayView.setText(words, meanings)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        wm.removeView(overlayView)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}