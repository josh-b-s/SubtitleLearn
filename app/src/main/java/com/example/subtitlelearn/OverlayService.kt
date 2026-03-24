package com.example.subtitlelearn

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import com.example.subtitlelearn.ui.theme.segment

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var overlayView: PinyinOverlayView

    private var lastX = 0f
    private var lastY = 0f

    override fun onCreate() {
        super.onCreate()

        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        overlayView = PinyinOverlayView(this).apply {
            setBackgroundColor(0x88000000.toInt())
            setPadding(24, 16, 24, 16)
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        wm.addView(overlayView, params)

        // DRAG HANDLING (BEST PLACE)
        overlayView.setOnTouchListener { _, event ->

            when (event.action) {

                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX
                    lastY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - lastX).toInt()
                    val dy = (event.rawY - lastY).toInt()

                    lastX = event.rawX
                    lastY = event.rawY

                    params.x += dx
                    params.y += dy
                    wm.updateViewLayout(overlayView, params)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    overlayView.performClick()
                    true
                }

                else -> false
            }
        }

        // TEXT UPDATE PIPELINE
        OverlayBridge.update = { text ->
            val raw = text.replace(" ", "")
            val words = segment(raw).filter { it.isNotBlank() }

            val meanings = words.associateWith { word ->
                Dictionary.getMeaning(word)
            }

            overlayView.post {
                overlayView.setText(words, meanings)
            }
        }
    }

    override fun onDestroy() {
        OverlayBridge.update = null
        wm.removeView(overlayView)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}