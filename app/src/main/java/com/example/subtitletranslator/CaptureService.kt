package com.example.subtitletranslator

import android.app.*
import android.content.*
import android.graphics.*
import android.hardware.display.DisplayManager
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.*
import android.widget.TextView
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.translate.Translation
import com.google.mlkit.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class CaptureService: Service() {
    private var projection: MediaProjection?=null; private var reader: ImageReader?=null; private var imageView: TextView?=null
    private var last=""; private val handler=Handler(Looper.getMainLooper()); private var busy=false
    private val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val translator=Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.ENGLISH).setTargetLanguage(TranslateLanguage.GREEK).build())
    override fun onCreate(){ super.onCreate(); createChannel(); translator.downloadModelIfNeeded(com.google.mlkit.common.model.DownloadConditions.Builder().build()) }
    override fun onStartCommand(i:Intent?,flags:Int,id:Int):Int {
        startForeground(7,notification()); val code=i?.getIntExtra("resultCode",0)?:0; val data=i?.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY
        val mgr=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection=mgr.getMediaProjection(code,data)
        val dm=resources.displayMetrics; reader=ImageReader.newInstance(dm.widthPixels,dm.heightPixels,PixelFormat.RGBA_8888,2)
        projection?.createVirtualDisplay("SubtitleTranslator",dm.widthPixels,dm.heightPixels,dm.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader?.surface,null,null)
        showOverlay(); reader?.setOnImageAvailableListener({r->process(r)},handler); return START_STICKY
    }
    private fun process(r:ImageReader){ if(busy)return; val im=try{r.acquireLatestImage()}catch(_:Exception){null}?:return; busy=true
        val w=im.width; val h=im.height; val p=im.planes[0]; val b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888); b.copyPixelsFromBuffer(p.buffer); im.close()
        // Subtitle region: lower ~35% of screen. This keeps OCR fast and reduces false positives.
        val top=(h*0.62f).toInt(); val crop=Bitmap.createBitmap(b,0,top,w,h-top)
        recognizer.process(InputImage.fromBitmap(crop,0)).addOnSuccessListener { result ->
            val text=result.text.lines.joinToString(" ").trim(); if(text.isNotBlank() && text!=last && text.length>1){ last=text; translator.translate(text).addOnSuccessListener{show(it)}.addOnFailureListener{busy=false} }
            else busy=false
        }.addOnFailureListener{busy=false}
    }
    private fun showOverlay(){ val wm=getSystemService(WINDOW_SERVICE) as WindowManager; imageView=TextView(this).apply{setTextColor(Color.WHITE);setTextSize(20f);setShadowLayer(6f,0f,0f,Color.BLACK);setPadding(18,8,18,8);setBackgroundColor(0x88000000.toInt());gravity=Gravity.CENTER}
        val type=if(Build.VERSION.SDK_INT>=26)WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val lp=WindowManager.LayoutParams(-1,WindowManager.LayoutParams.WRAP_CONTENT,type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,PixelFormat.TRANSLUCENT); lp.gravity=Gravity.BOTTOM; lp.y=70; wm.addView(imageView,lp)
    }
    private fun show(t:String){handler.post{imageView?.text=t;busy=false; handler.postDelayed({if(imageView?.text==t)imageView?.text=""},3500)}}
    private fun notification():Notification=Notification.Builder(this,"subtitle").setContentTitle("Subtitle Translator").setContentText("Translating subtitles").setSmallIcon(android.R.drawable.ic_menu_info_details).build()
    private fun createChannel(){if(Build.VERSION.SDK_INT>=26)(getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel("subtitle","Subtitle Translator",NotificationManager.IMPORTANCE_LOW))}
    override fun onBind(i:Intent?)=null
    override fun onDestroy(){projection?.stop();reader?.close();recognizer.close();translator.close();imageView?.let{(getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it)};super.onDestroy()}
}
