package com.example.subtitlelearn

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.subtitlelearn.overlay.OverlayService

class MainActivity : AppCompatActivity() {

    private val projectionManager by lazy {
        getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private val captureRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                startService(Intent(this, OverlayService::class.java))
                ContextCompat.startForegroundService(
                    this,
                    Intent(this, CaptureService::class.java).apply {
                        putExtra("resultCode", result.resultCode)
                        putExtra("data", result.data)
                    }
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Dictionary.load(this)

        findViewById<Button>(R.id.startBtn).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        "package:$packageName".toUri()
                    )
                )
                return@setOnClickListener
            }
            captureRequest.launch(projectionManager.createScreenCaptureIntent())
        }

        findViewById<Button>(R.id.stopBtn).setOnClickListener {
            stopService(Intent(this, CaptureService::class.java))
            stopService(Intent(this, OverlayService::class.java))
        }
    }
}