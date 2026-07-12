package com.vitranslate.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import kotlin.concurrent.thread

/**
 * Dịch vụ chạy nền:
 * 1. Nhận MediaProjection → tạo AudioRecord với AudioPlaybackCaptureConfiguration
 *    (bắt âm thanh mà các app khác đang phát: YouTube, trình phát video…).
 * 2. Đưa luồng PCM 16kHz vào Vosk để nhận dạng lời thoại (offline).
 * 3. Dịch câu nhận được sang tiếng Việt bằng ML Kit (offline sau lần tải đầu).
 * 4. Hiển thị phụ đề trên cửa sổ nổi (overlay) đè lên mọi app.
 */
class MovieTranslateService : Service() {

    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val CHANNEL_ID = "movie_translate"
        const val SAMPLE_RATE = 16000
    }

    private var projection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    private var running = false

    private var model: Model? = null
    private var recognizer: Recognizer? = null

    private var overlayView: TextView? = null
    private var windowManager: WindowManager? = null
    private val ui = Handler(Looper.getMainLooper())

    private var srcLang = "en"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(intent)
            ACTION_STOP -> stopEverything()
        }
        return START_NOT_STICKY
    }

    private fun start(intent: Intent) {
        val resultCode = intent.getIntExtra("resultCode", 0)
        @Suppress("DEPRECATION")
        val data = intent.getParcelableExtra<Intent>("data") ?: return
        val modelPath = intent.getStringExtra("modelPath") ?: return
        srcLang = intent.getStringExtra("srcLang") ?: "en"

        // BẮT BUỘC: startForeground với type mediaProjection TRƯỚC khi lấy projection
        startForeground(
            1, buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(resultCode, data)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopEverything() }
        }, ui)

        showOverlay("⏳ Đang nạp mô hình nhận dạng…")

        thread {
            try {
                model = Model(modelPath)
                recognizer = Recognizer(model, SAMPLE_RATE.toFloat())
                startAudioCapture()
            } catch (e: Exception) {
                ui.post { showOverlay("❌ Lỗi nạp mô hình: ${e.message}") }
            }
        }
    }

    private fun startAudioCapture() {
        val config = AudioPlaybackCaptureConfiguration.Builder(projection!!)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(minBuf * 4)
            .setAudioPlaybackCaptureConfig(config)
            .build()

        audioRecord?.startRecording()
        running = true
        ui.post { showOverlay("🎬 Sẵn sàng. Hãy phát phim/video…") }

        val buf = ByteArray(4096)
        while (running) {
            val n = audioRecord?.read(buf, 0, buf.size) ?: -1
            if (n <= 0) continue
            val rec = recognizer ?: break
            if (rec.acceptWaveForm(buf, n)) {
                // Câu hoàn chỉnh
                val text = JSONObject(rec.result).optString("text")
                if (text.isNotBlank()) translateAndShow(text, final = true)
            } else {
                // Câu đang nói dở (partial) — hiển thị nguyên gốc cho đỡ trễ
                val partial = JSONObject(rec.partialResult).optString("partial")
                if (partial.isNotBlank()) {
                    ui.post { overlayView?.text = "… $partial" }
                }
            }
        }
    }

    private fun translateAndShow(text: String, final: Boolean) {
        TranslateHelper.translate(text, srcLang, "vi",
            onResult = { vi ->
                ui.post { showOverlay(if (final) vi else "… $vi") }
            },
            onError = { err -> ui.post { showOverlay(err) } }
        )
    }

    // ---------------- PHỤ ĐỀ NỔI ----------------

    private fun showOverlay(text: String) {
        if (overlayView == null) {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            overlayView = TextView(this).apply {
                setBackgroundColor(Color.argb(170, 0, 0, 0))
                setTextColor(Color.WHITE)
                textSize = 18f
                setPadding(28, 16, 28, 16)
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = 120
            }
            try {
                windowManager?.addView(overlayView, params)
            } catch (e: Exception) {
                overlayView = null
                return
            }
        }
        overlayView?.text = text
    }

    private fun removeOverlay() {
        overlayView?.let { v ->
            try { windowManager?.removeView(v) } catch (_: Exception) {}
        }
        overlayView = null
    }

    // ---------------- DỌN DẸP ----------------

    private fun stopEverything() {
        running = false
        try { audioRecord?.stop() } catch (_: Exception) {}
        audioRecord?.release(); audioRecord = null
        recognizer?.close(); recognizer = null
        model?.close(); model = null
        projection?.stop(); projection = null
        ui.post { removeOverlay() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        removeOverlay()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "Dịch phim",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val stopIntent = Intent(this, MovieTranslateService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("ViTranslate — đang dịch phim")
            .setContentText("Đang nghe âm thanh phát trên máy và dịch sang tiếng Việt")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(
                        this, android.R.drawable.ic_media_pause
                    ),
                    "Dừng", stopPending
                ).build()
            )
            .setOngoing(true)
            .build()
    }
}
