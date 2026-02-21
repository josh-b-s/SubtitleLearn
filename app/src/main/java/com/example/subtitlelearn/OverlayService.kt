package com.example.subtitlelearn

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.annotation.RequiresApi

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var view: TextView
    private val receiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            val text = intent?.getStringExtra("text") ?: return
            view.text = text
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate() {
        super.onCreate()
        registerReceiver(
            receiver,
            android.content.IntentFilter("TRANSCRIPTION_UPDATE"),
            RECEIVER_NOT_EXPORTED
        )

        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        view = TextView(this).apply {
            text = "Listening..."
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(0x88000000.toInt())
            setPadding(24, 16, 24, 16)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        params.y = 120

        wm.addView(view, params)
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        wm.removeView(view)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
