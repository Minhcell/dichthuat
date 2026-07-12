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
 *     - Giọng phát qua kênh MEDIA → ra loa hoặc tai nghe Bluetooth.
 *     - Khi đọc, app phát phim bị "duck" (tự giảm âm lượng) → giọng dịch
 *       luôn to rõ hơn tiếng phim.
 *     - Capture loại trừ chính app (excludeUid) → nghe phim LIÊN TỤC,
 *       không bỏ sót thoại kể cả trong lúc giọng Việt đang đọc.
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

    // Audio focus: khi giọng Việt đọc, yêu cầu focus "duck" → app phát phim
    // (YouTube, trình phát video…) TỰ GIẢM ÂM LƯỢNG, giọng dịch nổi lên rõ
    private var focusRequest: android.media.AudioFocusRequest? = null

    // Các câu dịch xong TRƯỚC khi TTS khởi động kịp → giữ lại đọc sau
    private val earlyQueue = ArrayDeque<String>()


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

    /**
     * Khởi động TTS với CHUỖI DỰ PHÒNG:
     *  1. Thử Google TTS (com.google.android.tts — có giọng Việt chuẩn)
     *  2. Google lỗi hoặc không có giọng Việt → thử engine MẶC ĐỊNH của máy
     *  3. Cả hai đều không được → hướng dẫn cài Google TTS
     * (Trước đây nếu gắn cứng engine Google mà máy chưa cài/bị tắt thì
     *  init trả về ERROR ngay → báo "Không khởi động được Text-to-Speech")
     */
    private fun initTts(tryGoogleFirst: Boolean = true) {
        val googleEngine = "com.google.android.tts"

        val listener = TextToSpeech.OnInitListener { status ->
            if (status != TextToSpeech.SUCCESS) {
                if (tryGoogleFirst) {
                    // Google TTS không có / bị tắt → dùng engine mặc định của máy
                    initTts(tryGoogleFirst = false)
                } else {
                    notifyUser(
                        "❌ Không khởi động được Text-to-Speech.\n" +
                        "Hãy cài app 'Google Text-to-Speech' (hoặc 'Speech Services by Google') " +
                        "từ Play Store rồi thử lại."
                    )
                }
                return@OnInitListener
            }
            val res = tts?.setLanguage(Locale("vi", "VN"))
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                if (tryGoogleFirst) {
                    // Engine Google chạy nhưng thiếu giọng Việt (hiếm) → thử engine mặc định
                    tts?.shutdown()
                    initTts(tryGoogleFirst = false)
                } else {
                    notifyUser(
                        "❌ Máy chưa có giọng đọc tiếng Việt.\n" +
                        "Cài 'Speech Services by Google' từ Play Store, mở app đó tải giọng " +
                        "tiếng Việt, rồi vào Cài đặt > Trợ năng > Chuyển văn bản thành " +
                        "giọng nói > chọn Google."
                    )
                }
                return@OnInitListener
            }
            tts?.setSpeechRate(1.25f) // đọc nhanh để theo kịp phim, giảm độ trễ

            // Kênh MEDIA: chắc chắn phát ra loa hoặc tai nghe Bluetooth,
            // chỉnh âm lượng bằng nút volume như nhạc/phim
            tts?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )

            // LÀM GIỌNG DỊCH TO HƠN TIẾNG PHIM: khi bắt đầu đọc, xin audio
            // focus kiểu "duck" → app phát phim tự hạ âm lượng xuống thấp;
            // đọc xong trả focus → tiếng phim to lại như cũ
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { duckMovieAudio(true) }
                override fun onDone(id: String?) {
                    pendingUtterances.decrementAndGet()
                    // chờ 400ms: nếu không còn câu nào chờ đọc thì trả lại âm lượng phim
                    ui.postDelayed({
                        if (pendingUtterances.get() <= 0) duckMovieAudio(false)
                    }, 400)
                }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    pendingUtterances.decrementAndGet()
                    duckMovieAudio(false)
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

        tts = try {
            if (tryGoogleFirst) TextToSpeech(this, listener, googleEngine)
            else TextToSpeech(this, listener)
        } catch (e: Exception) {
            TextToSpeech(this, listener)
        }
    }

    /** Hiện thông báo cho người dùng dù đang ở app khác (Toast từ service) */
    private fun notifyUser(msg: String) {
        ui.post {
            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun startAudioCapture() {
        // QUAN TRỌNG (giảm trễ + không sót lời thoại):
        // Thay vì lọc theo loại âm thanh (MEDIA/GAME) rồi phải TẠM DỪNG nghe
        // khi giọng Việt đọc, giờ ta loại trừ CHÍNH APP MÌNH (excludeUid).
        // → Giọng dịch của app không bao giờ bị ghi lại, còn tiếng phim thì
        //   được nghe LIÊN TỤC — kể cả trong lúc giọng Việt đang đọc.
        //   Không còn khoảng "điếc" nên không bỏ sót câu thoại nào.
        val config = AudioPlaybackCaptureConfiguration.Builder(projection!!)
            .excludeUid(android.os.Process.myUid())
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
                // Câu hoàn chỉnh → dịch
                val text = JSONObject(rec.result).optString("text")
                if (text.isNotBlank()) translateFinal(text)
            } else {
                // Câu đang nói dở: chỉ hiện khi CHƯA có phụ đề nào trên màn hình
                // (không đè mất các cặp câu song ngữ người xem đang đọc)
                val partial = JSONObject(rec.partialResult).optString("partial")
                if (partial.isNotBlank()) {
                    ui.post {
                        if (visiblePairs.isEmpty()) {
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
                // CẢ HAI CHẾ ĐỘ đều hiện phụ đề song ngữ (gốc + Việt).
                // Phụ đề hiện NGAY khi dịch xong — ở chế độ đọc tiếng, chữ hiện
                // trước cả khi giọng đọc bắt đầu, giúp theo kịp thoại dễ hơn.
                ui.post { enqueueSubtitle(text, vi) }
                if (mode == MODE_VOICE) speakVi(vi)
            },
            onError = { err ->
                if (mode == MODE_SUBTITLE) ui.post { showOverlay(err) }
                else notifyUser(err)
            }
        )
    }

    // ---------------- 🔊 CHẾ ĐỘ ĐỌC TIẾNG ----------------

    /**
     * Nén / trả lại âm lượng phim bằng audio focus:
     *  duck = true  → xin focus GAIN_TRANSIENT_MAY_DUCK: app phát phim
     *                 tự giảm âm lượng (YouTube giảm còn ~20%), giọng dịch nổi rõ
     *  duck = false → trả focus: tiếng phim to lại bình thường
     */
    private fun duckMovieAudio(duck: Boolean) {
        val am = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        if (duck) {
            if (focusRequest == null) {
                focusRequest = android.media.AudioFocusRequest.Builder(
                    android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
            }
            am.requestAudioFocus(focusRequest!!)
        } else {
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
        }
    }

    private fun speakVi(text: String) {
        if (!ttsReady) {
            // TTS chưa khởi động xong → giữ lại, đọc ngay khi sẵn sàng
            earlyQueue.addLast(text)
            while (earlyQueue.size > 3) earlyQueue.removeFirst()
            return
        }
        // Chỉ khi tồn đọng quá 3 câu (phim nói rất nhanh) mới bỏ câu cũ —
        // nới ngưỡng so với trước để dịch ĐẦY ĐỦ hơn, ít bỏ sót câu
        val queueMode = if (pendingUtterances.get() >= 3) {
            pendingUtterances.set(0)
            TextToSpeech.QUEUE_FLUSH
        } else TextToSpeech.QUEUE_ADD
        pendingUtterances.incrementAndGet()
        val params = android.os.Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f) // âm lượng tối đa
        }
        tts?.speak(text, queueMode, params, "movie_${System.nanoTime()}")
    }

    // ---------------- 📖 PHỤ ĐỀ SONG NGỮ (hiện tức thì) ----------------

    /** Một cặp phụ đề: câu gốc tiếng nước ngoài + bản dịch tiếng Việt */
    private data class SubPair(val orig: String, val vi: String)

    /** Các cặp đang hiện trên màn hình (tối đa 2 cặp = 4 dòng) */
    private val visiblePairs = ArrayDeque<SubPair>()

    /**
     * HIỆN NGAY khi người trong phim nói xong câu nào:
     *  - Câu vừa nói hiện lập tức 2 dòng: tiếng gốc (vàng) + tiếng Việt (trắng đậm).
     *  - Câu TRƯỚC ĐÓ vẫn giữ trên màn hình (mờ hơn, phía trên) để đọc kịp;
     *    khi câu thứ 3 xuất hiện, câu đầu tiên mới trôi đi.
     *  → Không còn hàng đợi giữ chậm: người A nói xong hiện liền cặp câu của A;
     *    người B nói tiếp là hiện liền cặp câu của B ngay bên dưới.
     */
    private fun enqueueSubtitle(orig: String, vi: String) {
        visiblePairs.addLast(SubPair(orig, vi))
        while (visiblePairs.size > 2) visiblePairs.removeFirst() // câu cũ nhất trôi đi
        renderPairs()
    }

    /** Vẽ các cặp câu: tiếng gốc màu vàng, tiếng Việt màu trắng; cặp cũ mờ hơn */
    private fun renderPairs() {
        val sb = android.text.SpannableStringBuilder()
        visiblePairs.forEachIndexed { i, p ->
            val old = visiblePairs.size == 2 && i == 0
            val cOrig = if (old) Color.argb(255, 180, 160, 80) else Color.rgb(255, 213, 79)
            val cVi = if (old) Color.argb(255, 190, 190, 190) else Color.WHITE
            var s = sb.length
            sb.append(p.orig)
            sb.setSpan(android.text.style.ForegroundColorSpan(cOrig), s, sb.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(android.text.style.RelativeSizeSpan(0.85f), s, sb.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append("\n")
            s = sb.length
            sb.append(p.vi)
            sb.setSpan(android.text.style.ForegroundColorSpan(cVi), s, sb.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), s, sb.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (i < visiblePairs.size - 1) sb.append("\n\n")
        }
        ensureOverlay()
        overlayView?.text = sb
    }

    /** Tạo cửa sổ phụ đề nổi nếu chưa có */
    private fun ensureOverlay() {
        if (overlayView != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = TextView(this).apply {
            setBackgroundColor(Color.argb(170, 0, 0, 0))
            setTextColor(Color.WHITE)
            textSize = 17f
            setLineSpacing(4f, 1.05f)
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
        }
    }

    private fun showOverlay(text: String) {
        ensureOverlay()
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
        duckMovieAudio(false) // trả lại âm lượng phim
        ui.post {
            visiblePairs.clear()
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
