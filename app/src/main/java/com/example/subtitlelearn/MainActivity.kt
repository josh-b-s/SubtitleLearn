package com.example.subtitlelearn

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri


class MainActivity : AppCompatActivity() {
    private lateinit var projectionManager: MediaProjectionManager
    private val REQ_CAPTURE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        projectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

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

            startActivityForResult(
                projectionManager.createScreenCaptureIntent(),
                REQ_CAPTURE
            )
        }

        findViewById<Button>(R.id.stopBtn).setOnClickListener {
            stopService(Intent(this, CaptureService::class.java))
            stopService(Intent(this, OverlayService::class.java))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQ_CAPTURE && resultCode == RESULT_OK && data != null) {
            startService(Intent(this, OverlayService::class.java))

            startService(
                Intent(this, CaptureService::class.java).apply {
                    putExtra("code", resultCode)
                    putExtra("data", data)
                }
            )
        }
    }
}
