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
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Dịch vụ dịch phim chạy nền, có 2 chế độ:
 *
 *  📖 MODE_SUBTITLE: hiện phụ đề tiếng Việt nổi. Mỗi câu được giữ trên màn hình
 *     đủ lâu để đọc kịp (thời gian tỉ lệ với độ dài câu). Câu mới xếp hàng đợi,
 *     không đè mất câu đang đọc — giống "chạy chữ chậm".
 *
 *  🔊 MODE_VOICE: đọc to bản dịch bằng giọng tiếng Việt (Google TTS).
 *     Giọng phát qua kênh MEDIA bình thường → chắc chắn ra loa hoặc tai nghe
 *     Bluetooth, chỉnh bằng nút âm lượng. Chống vòng lặp bằng cách tạm ngừng
 *     nhận dạng trong lúc giọng Việt đang đọc (cờ ttsSpeaking).
 */
class MovieTranslateService : Service() {

    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val MODE_SUBTITLE = "sub"
        const val MODE_VOICE = "voice"
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
    private var mode = MODE_SUBTITLE

    // ----- TTS cho chế độ đọc tiếng -----
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val pendingUtterances = AtomicInteger(0)

    // Cờ báo "giọng Việt đang đọc" → tạm ngừng đưa âm thanh vào bộ nhận dạng
    // để máy không nghe nhầm lại chính giọng dịch của mình (vòng lặp)
    @Volatile private var ttsSpeaking = false

    // Các câu dịch xong TRƯỚC khi TTS khởi động kịp → giữ lại đọc sau
    private val earlyQueue = ArrayDeque<String>()

    // ----- Hàng đợi phụ đề (hiện chậm, đọc kịp) -----
    private val subQueue = ArrayDeque<String>()
    private var subShowing = false

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
        mode = intent.getStringExtra("mode") ?: MODE_SUBTITLE

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

        if (mode == MODE_VOICE) initTts()
        else showOverlay("⏳ Đang nạp mô hình nhận dạng…")

        thread {
            try {
                model = Model(modelPath)
                recognizer = Recognizer(model, SAMPLE_RATE.toFloat())
                startAudioCapture()
            } catch (e: Exception) {
                ui.post {
                    if (mode == MODE_SUBTITLE) showOverlay("❌ Lỗi nạp mô hình: ${e.message}")
                }
            }
        }
    }

    private fun initTts() {
        // Ưu tiên Google TTS (có giọng tiếng Việt chuẩn). Máy Xiaomi hay đặt
        // engine TTS mặc định của hãng — engine đó KHÔNG có tiếng Việt
        // nên app sẽ im lặng. Nếu máy không có Google TTS thì dùng engine mặc định.
        val googleEngine = "com.google.android.tts"
        val hasGoogle = try {
            packageManager.getPackageInfo(googleEngine, 0); true
        } catch (e: Exception) { false }

        val listener = TextToSpeech.OnInitListener { status ->
            if (status != TextToSpeech.SUCCESS) {
                notifyUser("❌ Không khởi động được Text-to-Speech")
                return@OnInitListener
            }
            val res = tts?.setLanguage(Locale("vi", "VN"))
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                notifyUser(
                    "❌ Máy chưa có giọng đọc tiếng Việt.\n" +
                    "Cài app 'Google Text-to-Speech' từ Play Store, rồi vào " +
                    "Cài đặt > Hệ thống > Chuyển văn bản thành giọng nói > chọn Google."
                )
                return@OnInitListener
            }
            tts?.setSpeechRate(1.1f) // đọc nhanh hơn một chút để theo kịp phim

            // Dùng kênh MEDIA bình thường: chắc chắn phát ra loa hoặc tai nghe
            // Bluetooth, chỉnh âm lượng bằng nút volume như nhạc/phim.
            // (Kênh "navigation" trước đây bị nhiều máy Xiaomi chặn/tắt tiếng.)
            tts?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )

            // Chống vòng lặp: trong lúc giọng Việt đang đọc, tạm NGỪNG đưa
            // âm thanh vào bộ nhận dạng (xem cờ ttsSpeaking ở vòng lặp ghi âm)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { ttsSpeaking = true }
                override fun onDone(id: String?) {
                    pendingUtterances.decrementAndGet()
                    // chờ thêm 300ms cho dư âm tan hết rồi mới nghe tiếp
                    ui.postDelayed({ if (pendingUtterances.get() <= 0) ttsSpeaking = false }, 300)
                }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    pendingUtterances.decrementAndGet()
                    ttsSpeaking = false
                }
            })

            ttsReady = true
            // Câu chào kiểm tra: nghe thấy câu này = TTS hoạt động tốt
            pendingUtterances.incrementAndGet()
            tts?.speak(
                "Chế độ dịch bằng tiếng đã sẵn sàng",
                TextToSpeech.QUEUE_FLUSH, null, "movie_hello"
            )
            // Đọc nốt các câu đã dịch xong trong lúc TTS còn đang khởi động
            ui.post {
                while (earlyQueue.isNotEmpty()) speakVi(earlyQueue.removeFirst())
            }
        }

        tts = if (hasGoogle) TextToSpeech(this, listener, googleEngine)
              else TextToSpeech(this, listener)
    }

    /** Hiện thông báo cho người dùng dù đang ở app khác (Toast từ service) */
    private fun notifyUser(msg: String) {
        ui.post {
            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
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
        ui.post {
            if (mode == MODE_SUBTITLE) showOverlay("🎬 Sẵn sàng. Hãy phát phim/video…")
        }

        val buf = ByteArray(4096)
        while (running) {
            val n = audioRecord?.read(buf, 0, buf.size) ?: -1
            if (n <= 0) continue
            // Khi giọng Việt đang đọc (chế độ đọc tiếng): vẫn đọc dữ liệu để
            // xả bộ đệm nhưng KHÔNG đưa vào bộ nhận dạng — tránh máy nghe
            // nhầm lại giọng dịch của chính mình
            if (mode == MODE_VOICE && ttsSpeaking) continue
            val rec = recognizer ?: break
            if (rec.acceptWaveForm(buf, n)) {
                // Câu hoàn chỉnh → dịch
                val text = JSONObject(rec.result).optString("text")
                if (text.isNotBlank()) translateFinal(text)
            } else if (mode == MODE_SUBTITLE) {
                // Câu đang nói dở: chỉ hiện khi không có câu nào đang chờ đọc
                val partial = JSONObject(rec.partialResult).optString("partial")
                if (partial.isNotBlank()) {
                    ui.post {
                        if (!subShowing && subQueue.isEmpty()) {
                            overlayView?.text = "… $partial"
                        }
                    }
                }
            }
        }
    }

    private fun translateFinal(text: String) {
        TranslateHelper.translate(text, srcLang, "vi",
            onResult = { vi ->
                if (mode == MODE_VOICE) speakVi(vi)
                else ui.post { enqueueSubtitle(vi) }
            },
            onError = { err ->
                if (mode == MODE_SUBTITLE) ui.post { showOverlay(err) }
                else notifyUser(err)
            }
        )
    }

    // ---------------- 🔊 CHẾ ĐỘ ĐỌC TIẾNG ----------------

    private fun speakVi(text: String) {
        if (!ttsReady) {
            // TTS chưa khởi động xong → giữ lại, đọc ngay khi sẵn sàng
            earlyQueue.addLast(text)
            while (earlyQueue.size > 3) earlyQueue.removeFirst()
            return
        }
        // Nếu giọng đọc bị tồn đọng quá 2 câu (phim nói nhanh) → bỏ câu cũ,
        // đọc câu mới nhất để không bị trễ so với hình
        val queueMode = if (pendingUtterances.get() >= 2) {
            pendingUtterances.set(0)
            TextToSpeech.QUEUE_FLUSH
        } else TextToSpeech.QUEUE_ADD
        pendingUtterances.incrementAndGet()
        val params = android.os.Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f) // âm lượng tối đa
        }
        tts?.speak(text, queueMode, params, "movie_${System.nanoTime()}")
    }

    // ---------------- 📖 CHẾ ĐỘ PHỤ ĐỀ (hiện chậm) ----------------

    /**
     * Mỗi câu phụ đề được giữ trên màn hình tối thiểu:
     *   2,2 giây + 75ms cho mỗi ký tự (tối đa 9 giây)
     * → câu dài hiện lâu hơn, người xem đọc kịp.
     * Câu mới trong lúc đó được xếp hàng đợi; nếu dồn quá 3 câu
     * (phim nói quá nhanh) thì bỏ bớt câu cũ nhất để không bị trễ.
     */
    private fun enqueueSubtitle(text: String) {
        subQueue.addLast(text)
        while (subQueue.size > 3) subQueue.removeFirst()
        pumpSubtitle()
    }

    private fun pumpSubtitle() {
        if (subShowing) return
        val next = subQueue.removeFirstOrNull() ?: return
        subShowing = true
        showOverlay(next)
        val durMs = (2200L + next.length * 75L).coerceAtMost(9000L)
        ui.postDelayed({
            subShowing = false
            pumpSubtitle()
        }, durMs)
    }

    private fun showOverlay(text: String) {
        if (overlayView == null) {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            overlayView = TextView(this).apply {
                setBackgroundColor(Color.argb(170, 0, 0, 0))
                setTextColor(Color.WHITE)
                textSize = 19f
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
        tts?.stop(); tts?.shutdown(); tts = null
        ui.post {
            subQueue.clear()
            subShowing = false
            removeOverlay()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        removeOverlay()
        tts?.shutdown()
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
            .setContentText(
                if (mode == MODE_VOICE) "Đang đọc bản dịch tiếng Việt qua loa/tai nghe"
                else "Đang hiện phụ đề tiếng Việt trên màn hình"
            )
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
