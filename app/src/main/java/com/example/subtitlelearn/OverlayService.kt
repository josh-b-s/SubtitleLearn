package com.example.subtitlelearn

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.HorizontalScrollView
import android.widget.FrameLayout
import com.example.subtitlelearn.ui.theme.segment
import com.github.promeg.pinyinhelper.Pinyin

class OverlayService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private lateinit var scrollView: HorizontalScrollView
    private lateinit var overlayView: PinyinOverlayView

    override fun onCreate() {
        super.onCreate()

        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        // HorizontalScrollView to avoid wrapping
        scrollView = HorizontalScrollView(this)
        scrollView.isHorizontalScrollBarEnabled = false

        overlayView = PinyinOverlayView(this).apply {
            setBackgroundColor(0x88000000.toInt())
            setPadding(24, 16, 24, 16)
        }

        scrollView.addView(overlayView)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        params.y = 120

        wm.addView(scrollView, params)

        OverlayBridge.update = { text ->
            val raw = text.replace(" ", "")
            val words = segment(raw).filter { it.isNotBlank() }

            val meanings = words.associateWith { word ->
                CedictDictionary.getMeaning(word)
            }

            mainHandler.post {
                overlayView.setText(words, meanings)
                scrollView.fullScroll(HorizontalScrollView.FOCUS_RIGHT)
            }
        }

        Log.i("OVERLAY", "OverlayService ready")
    }

    override fun onDestroy() {
        OverlayBridge.update = null
        wm.removeView(scrollView)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
