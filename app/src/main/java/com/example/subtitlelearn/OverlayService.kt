package com.example.subtitlelearn

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

class OverlayService : Service() {

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private lateinit var view: TextView

    override fun onCreate() {
        super.onCreate()

        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        view = TextView(this).apply {
            text = "Listening..."
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(0x88000000.toInt())
            setPadding(24, 16, 24, 16)
            maxWidth = resources.displayMetrics.widthPixels - 100
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        params.y = 120

        wm.addView(view, params)

        // Bridge hookup
        OverlayBridge.update = { text ->
            mainHandler.post {
                view.text = text
            }
        }

        Log.i("OVERLAY", "OverlayService ready")
    }

    override fun onDestroy() {
        OverlayBridge.update = null
        wm.removeView(view)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
