package com.example.subtitletranslator

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.media.ImageReader
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.*
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class CaptureService : Service() {

    private lateinit var mediaProjection: MediaProjection
    private lateinit var imageReader: ImageReader
    private lateinit var recognizer: com.google.mlkit.vision.text.TextRecognizer
    private lateinit var translator: Translator

    private var overlay: TextView? = null
    private var lastText = ""

    override fun onCreate() {
        super.onCreate()

        recognizer = TextRecognition.getClient(
            TextRecognizerOptions.DEFAULT_OPTIONS
        )

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslationLanguage.ENGLISH)
            .setTargetLanguage(TranslationLanguage.GREEK)
            .build()

        translator = Translation.getClient(options)

        translator.downloadModelIfNeeded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val projectionData =
            intent?.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY

        startForeground(
            1,
            NotificationCompat.Builder(this, "subtitle")
                .setContentTitle("Subtitle Translator")
                .setContentText("Translating subtitles...")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .build()
        )

        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = manager.getMediaProjection(Activity.RESULT_OK, projectionData)

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        imageReader = ImageReader.newInstance(
            width,
            height,
            PixelFormat.RGBA_8888,
            2
        )

        mediaProjection.createVirtualDisplay(
            "SubtitleTranslator",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null,
            null
        )

        imageReader.setOnImageAvailableListener(
            { reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener

                try {
                    val inputImage = InputImage.fromMediaImage(
                        image,
                        0
                    )

                    recognizer.process(inputImage)
                        .addOnSuccessListener { result ->
                            val text = result.text
                                .lines()
                                .filter { it.isNotBlank() }
                                .joinToString(" ")

                            if (
                                text.isNotBlank() &&
                                text != lastText &&
                                text.length > 2
                            ) {
                                lastText = text

                                translator.translate(text)
                                    .addOnSuccessListener { translated ->
                                        showSubtitle(translated)
                                    }
                            }
                        }
                } finally {
                    image.close()
                }
            },
            Handler(Looper.getMainLooper())
        )

        return START_STICKY
    }

    private fun showSubtitle(text: String) {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        if (overlay == null) {
            overlay = TextView(this).apply {
                setTextColor(android.graphics.Color.WHITE)
                textSize = 20f
                setPadding(24, 12, 24, 12)
                setShadowLayer(5f, 0f, 2f, android.graphics.Color.BLACK)
                setBackgroundColor(0x99000000.toInt())
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            )

            params.gravity = Gravity.BOTTOM
            params.y = 100

            windowManager.addView(overlay, params)
        }

        overlay?.text = text
    }

    override fun onDestroy() {
        try {
            recognizer.close()
            translator.close()
            imageReader.close()
            mediaProjection.stop()
        } catch (_: Exception) {
        }

        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}

private object TranslationLanguage {
    const val ENGLISH = "en"
    const val GREEK = "el"
}
