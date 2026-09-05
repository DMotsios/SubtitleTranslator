package com.example.subtitletranslator

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {
    private val captureCode = 4411
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val status = findViewById<TextView>(R.id.status)
        findViewById<Button>(R.id.startButton).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                return@setOnClickListener
            }
            val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(mgr.createScreenCaptureIntent(), captureCode)
        }
        findViewById<Button>(R.id.stopButton).setOnClickListener {
            stopService(Intent(this, CaptureService::class.java)); status.text = "Stopped"
        }
    }
    override fun onActivityResult(requestCode:Int, resultCode:Int, data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if (requestCode == captureCode && resultCode == RESULT_OK && data != null) {
            val i=Intent(this,CaptureService::class.java).apply { putExtra("resultCode",resultCode); putExtra("data",data) }
            startForegroundService(i)
            findViewById<TextView>(R.id.status).text="Running — open Stremio"
        }
    }
}
