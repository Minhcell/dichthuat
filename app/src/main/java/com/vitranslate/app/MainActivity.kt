package com.vitranslate.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.Html
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.Locale

/**
 * CHẾ ĐỘ HỘI THOẠI 2 CHIỀU:
 *  - Bấm nút xanh  → bạn nói tiếng Việt → app dịch sang ngoại ngữ đã chọn + đọc to.
 *  - Bấm nút đỏ    → người nước ngoài nói → app dịch sang tiếng Việt + đọc to.
 *  - Có ML Kit Language ID: nếu bấm nhầm nút, app tự phát hiện ngôn ngữ
 *    thật của câu nói và đảo chiều dịch cho đúng.
 *  - Bật công tắc Bluetooth để dùng micro của tai nghe Bluetooth.
 */
class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    // Danh sách ngoại ngữ: (Tên hiển thị, mã STT BCP-47, mã ML Kit, Locale TTS)
    data class Lang(val label: String, val stt: String, val mlkit: String, val tts: Locale)

    private val langs = listOf(
        Lang("🇬🇧 Tiếng Anh", "en-US", "en", Locale.US),
        Lang("🇨🇳 Tiếng Trung", "zh-CN", "zh", Locale.SIMPLIFIED_CHINESE),
        Lang("🇯🇵 Tiếng Nhật", "ja-JP", "ja", Locale.JAPAN),
        Lang("🇰🇷 Tiếng Hàn", "ko-KR", "ko", Locale.KOREA),
        Lang("🇫🇷 Tiếng Pháp", "fr-FR", "fr", Locale.FRANCE),
        Lang("🇩🇪 Tiếng Đức", "de-DE", "de", Locale.GERMANY),
        Lang("🇪🇸 Tiếng Tây Ban Nha", "es-ES", "es", Locale("es", "ES")),
        Lang("🇷🇺 Tiếng Nga", "ru-RU", "ru", Locale("ru", "RU")),
        Lang("🇹🇭 Tiếng Thái", "th-TH", "th", Locale("th", "TH")),
        Lang("🇮🇹 Tiếng Ý", "it-IT", "it", Locale.ITALY),
        Lang("🇵🇹 Tiếng Bồ Đào Nha", "pt-PT", "pt", Locale("pt", "PT")),
        Lang("🇧🇷 Bồ Đào Nha (Brazil)", "pt-BR", "pt", Locale("pt", "BR")),
        Lang("🇳🇱 Tiếng Hà Lan", "nl-NL", "nl", Locale("nl", "NL")),
        Lang("🇹🇷 Tiếng Thổ Nhĩ Kỳ", "tr-TR", "tr", Locale("tr", "TR")),
        Lang("🇮🇳 Tiếng Hindi (Ấn Độ)", "hi-IN", "hi", Locale("hi", "IN")),
        Lang("🇮🇩 Tiếng Indonesia", "id-ID", "id", Locale("id", "ID")),
        Lang("🇲🇾 Tiếng Mã Lai", "ms-MY", "ms", Locale("ms", "MY")),
        Lang("🇵🇭 Tiếng Philippines (Tagalog)", "fil-PH", "tl", Locale("fil", "PH")),
        Lang("🇸🇦 Tiếng Ả Rập", "ar-SA", "ar", Locale("ar", "SA")),
        Lang("🇮🇷 Tiếng Ba Tư (Iran)", "fa-IR", "fa", Locale("fa", "IR")),
        Lang("🇮🇱 Tiếng Do Thái (Hebrew)", "he-IL", "he", Locale("he", "IL")),
        Lang("🇵🇱 Tiếng Ba Lan", "pl-PL", "pl", Locale("pl", "PL")),
        Lang("🇺🇦 Tiếng Ukraina", "uk-UA", "uk", Locale("uk", "UA")),
        Lang("🇨🇿 Tiếng Séc", "cs-CZ", "cs", Locale("cs", "CZ")),
        Lang("🇸🇪 Tiếng Thụy Điển", "sv-SE", "sv", Locale("sv", "SE")),
        Lang("🇳🇴 Tiếng Na Uy", "nb-NO", "no", Locale("nb", "NO")),
        Lang("🇩🇰 Tiếng Đan Mạch", "da-DK", "da", Locale("da", "DK")),
        Lang("🇫🇮 Tiếng Phần Lan", "fi-FI", "fi", Locale("fi", "FI")),
        Lang("🇬🇷 Tiếng Hy Lạp", "el-GR", "el", Locale("el", "GR")),
        Lang("🇭🇺 Tiếng Hungary", "hu-HU", "hu", Locale("hu", "HU")),
        Lang("🇷🇴 Tiếng Romania", "ro-RO", "ro", Locale("ro", "RO")),
        Lang("🇧🇬 Tiếng Bulgaria", "bg-BG", "bg", Locale("bg", "BG")),
        Lang("🇮🇳 Tiếng Bengal", "bn-IN", "bn", Locale("bn", "IN")),
        Lang("🇮🇳 Tiếng Tamil", "ta-IN", "ta", Locale("ta", "IN")),
        Lang("🇵🇰 Tiếng Urdu", "ur-PK", "ur", Locale("ur", "PK")),
        Lang("🇰🇪 Tiếng Swahili", "sw-KE", "sw", Locale("sw", "KE")),
    )

    private lateinit var spinner: Spinner
    private lateinit var tvLog: TextView
    private lateinit var tvStatus: TextView
    private lateinit var scroll: ScrollView
    private lateinit var switchBt: SwitchMaterial
    private lateinit var switchSpeak: SwitchMaterial

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val selected get() = langs[spinner.selectedItemPosition]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        spinner = findViewById(R.id.spinnerLang)
        tvLog = findViewById(R.id.tvLog)
        tvStatus = findViewById(R.id.tvStatus)
        scroll = findViewById(R.id.scroll)
        switchBt = findViewById(R.id.switchBluetooth)
        switchSpeak = findViewById(R.id.switchSpeak)

        spinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, langs.map { it.label }
        )

        // Ưu tiên Google TTS (có giọng tiếng Việt và nhiều ngoại ngữ chuẩn);
        // nếu máy không có/bị tắt thì onInit sẽ tự chuyển sang engine mặc định
        tts = try {
            TextToSpeech(this, this, "com.google.android.tts")
        } catch (e: Exception) {
            TextToSpeech(this, this)
        }

        findViewById<Button>(R.id.btnVi).setOnClickListener {
            stopAutoMode()
            startListening(viToForeign = true)
        }
        findViewById<Button>(R.id.btnForeign).setOnClickListener {
            stopAutoMode()
            startListening(viToForeign = false)
        }
        findViewById<Button>(R.id.btnAuto).setOnClickListener {
            if (autoMode) stopAutoMode() else startAutoMode()
        }
        findViewById<Button>(R.id.btnMovie).setOnClickListener {
            stopAutoMode()
            startActivity(Intent(this, MovieActivity::class.java))
        }

        switchBt.setOnCheckedChangeListener { _, on ->
            if (on) {
                val ok = BluetoothHelper.enableHeadsetMic(this)
                if (!ok) {
                    toast("Không tìm thấy tai nghe Bluetooth đã kết nối")
                    switchBt.isChecked = false
                } else toast("Đang dùng micro tai nghe Bluetooth")
            } else {
                BluetoothHelper.disableHeadsetMic(this)
            }
        }

        requestPermissions()
    }

    private fun requestPermissions() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 31) perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        val need = perms.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (need.isNotEmpty()) ActivityCompat.requestPermissions(this, need.toTypedArray(), 1)
    }

    // ---------------- NHẬN DẠNG GIỌNG NÓI ----------------

    private fun startListening(viToForeign: Boolean) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("Máy chưa có dịch vụ nhận dạng giọng nói (cần app Google)")
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)

        val sttLang = if (viToForeign) "vi-VN" else selected.stt
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, sttLang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, sttLang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        tvStatus.text = getString(R.string.listening) +
                if (viToForeign) " (Tiếng Việt)" else " (${selected.label})"

        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val text = results
                    .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: return
                tvStatus.text = "Đã nghe xong, đang dịch…"
                handleRecognized(text, viToForeign)
            }

            override fun onPartialResults(partial: Bundle) {
                val text = partial
                    .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) tvStatus.text = "🎙 $text"
            }

            override fun onError(error: Int) {
                tvStatus.text = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Không nghe rõ, hãy thử lại"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Chưa cấp quyền micro"
                    else -> "Lỗi nhận dạng ($error), thử lại"
                }
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { tvStatus.text = "Đang xử lý…" }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer?.startListening(intent)
    }

    /**
     * Xử lý câu vừa nghe được:
     * 1. Dùng ML Kit Language ID để TỰ PHÁT HIỆN ngôn ngữ thật của câu.
     * 2. Nếu phát hiện tiếng Việt → dịch VI → ngoại ngữ.
     *    Nếu phát hiện ngoại ngữ (hoặc không rõ) → dịch → tiếng Việt.
     *    (Nhờ vậy dù bấm nhầm nút, chiều dịch vẫn đúng.)
     */
    private fun handleRecognized(text: String, viToForeignPressed: Boolean) {
        TranslateHelper.identifyLanguage(text) { detected ->
            val isVietnamese = when (detected) {
                "vi" -> true
                "und" -> viToForeignPressed // không rõ → tin theo nút đã bấm
                else -> false
            }
            val src = if (isVietnamese) "vi" else selected.mlkit
            val dst = if (isVietnamese) selected.mlkit else "vi"

            TranslateHelper.translate(text, src, dst,
                onResult = { translated ->
                    runOnUiThread {
                        appendLog(
                            speakerVi = isVietnamese,
                            original = text,
                            translated = translated
                        )
                        tvStatus.text = "Sẵn sàng"
                        if (switchSpeak.isChecked || autoMode) {
                            speak(translated, if (isVietnamese) selected.tts else Locale("vi", "VN")) {
                                // Đọc xong bản dịch → nếu đang ở chế độ tự động thì
                                // chuyển lượt nghe cho NGƯỜI KIA và nghe tiếp
                                if (autoMode) {
                                    autoListenVi = !isVietnamese
                                    ui.postDelayed({ autoListenLoop() }, 350)
                                }
                            }
                        } else if (autoMode) {
                            autoListenVi = !isVietnamese
                            ui.postDelayed({ autoListenLoop() }, 350)
                        }
                    }
                },
                onError = { err ->
                    runOnUiThread {
                        tvStatus.text = err
                        if (autoMode) ui.postDelayed({ autoListenLoop() }, 800)
                    }
                }
            )
        }
    }

    /* ================================================================
       🔁 HỘI THOẠI TỰ ĐỘNG (RẢNH TAY) — kiểu tai nghe phiên dịch:
       Điện thoại đặt giữa 2 người (hoặc dùng micro tai nghe Bluetooth,
       mỗi người đeo 1 bên tai của cặp tai nghe TWS).

       Cách hoạt động — vòng lặp "ping-pong":
       1. Nghe tiếng Việt trước. Người 1 nói → dịch → đọc to ngoại ngữ.
       2. Đọc xong tự chuyển sang NGHE NGOẠI NGỮ (đến lượt người 2).
          Người 2 nói → dịch → đọc to tiếng Việt → chuyển về nghe tiếng Việt.
       3. Nếu chờ mà không ai nói (hết giờ) → tự ĐẢO lượt nghe, phòng khi
          người kia chưa nói mà người này nói tiếp.
       4. ML Kit Language ID kiểm tra lại từng câu: nói "trái lượt" vẫn
          được dịch đúng chiều.
       Không ai phải bấm nút — như hai người nói chuyện trực tiếp.
       ================================================================ */

    private var autoMode = false
    private var autoListenVi = true // lượt hiện tại: true = đang chờ tiếng Việt
    private val ui = android.os.Handler(android.os.Looper.getMainLooper())

    private fun startAutoMode() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("Máy chưa có dịch vụ nhận dạng giọng nói (cần app Google)")
            return
        }
        autoMode = true
        autoListenVi = true
        findViewById<Button>(R.id.btnAuto).text = "⏹ Dừng hội thoại tự động"
        appendSystemLog("🔁 Bắt đầu hội thoại tự động: Việt ↔ ${selected.label}. Cứ nói tự nhiên, máy tự dịch và đọc to.")
        autoListenLoop()
    }

    private fun stopAutoMode() {
        if (!autoMode) return
        autoMode = false
        ui.removeCallbacksAndMessages(null)
        recognizer?.destroy(); recognizer = null
        tts?.stop()
        findViewById<Button>(R.id.btnAuto).text =
            "🔁 Hội thoại tự động (rảnh tay — mỗi người 1 tai nghe)"
        tvStatus.text = "Đã dừng hội thoại tự động"
    }

    /** Một vòng nghe của chế độ tự động */
    private fun autoListenLoop() {
        if (!autoMode) return
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)

        val sttLang = if (autoListenVi) "vi-VN" else selected.stt
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, sttLang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, sttLang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        tvStatus.text = if (autoListenVi)
            "🟢 Đang chờ TIẾNG VIỆT… (nói tự nhiên)"
        else
            "🔴 Đang chờ ${selected.label}… (speak now)"

        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                if (!autoMode) return
                val text = results
                    .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (text.isNullOrBlank()) {
                    ui.postDelayed({ autoListenLoop() }, 300)
                    return
                }
                tvStatus.text = "Đang dịch…"
                // handleRecognized sẽ tự đọc to và gọi lại autoListenLoop khi xong
                handleRecognized(text, autoListenVi)
            }

            override fun onPartialResults(partial: Bundle) {
                val t = partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!t.isNullOrBlank()) tvStatus.text = "🎙 $t"
            }

            override fun onError(error: Int) {
                if (!autoMode) return
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        // Không ai nói ở lượt này → đảo lượt nghe rồi nghe tiếp
                        autoListenVi = !autoListenVi
                        ui.postDelayed({ autoListenLoop() }, 250)
                    }
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        tvStatus.text = "Chưa cấp quyền micro"
                        stopAutoMode()
                    }
                    else -> ui.postDelayed({ autoListenLoop() }, 700)
                }
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer?.startListening(intent)
    }

    private fun appendSystemLog(msg: String) {
        tvLog.append(Html.fromHtml("<i>$msg</i><br><br>", Html.FROM_HTML_MODE_LEGACY))
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun appendLog(speakerVi: Boolean, original: String, translated: String) {
        val who = if (speakerVi) "🟢 Bạn" else "🔴 Khách"
        val html = "<b>$who:</b> $original<br><i>→ $translated</i><br><br>"
        tvLog.append(Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY))
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    // ---------------- ĐỌC TO (TTS) ----------------

    private var triedDefaultEngine = false

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (!ttsReady) {
            if (!triedDefaultEngine) {
                // Google TTS không có / bị tắt → thử engine mặc định của máy
                triedDefaultEngine = true
                tts = TextToSpeech(this, this)
            } else {
                toast("Không khởi động được Text-to-Speech. Hãy cài 'Speech Services by Google' từ Play Store.")
            }
        }
    }

    /** Callback chờ TTS đọc xong (dùng cho chế độ hội thoại tự động) */
    private var pendingTtsDone: (() -> Unit)? = null
    private var ttsListenerSet = false

    private fun speak(text: String, locale: Locale, onDone: (() -> Unit)? = null) {
        if (!ttsReady) { onDone?.invoke(); return }
        val res = tts?.setLanguage(locale)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            toast("TTS chưa hỗ trợ giọng ${locale.displayLanguage}. Vào Cài đặt > Chuyển văn bản thành giọng nói để tải thêm.")
            onDone?.invoke()
            return
        }
        if (!ttsListenerSet) {
            ttsListenerSet = true
            tts?.setOnUtteranceProgressListener(object :
                android.speech.tts.UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) {
                    runOnUiThread { pendingTtsDone?.invoke(); pendingTtsDone = null }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    runOnUiThread { pendingTtsDone?.invoke(); pendingTtsDone = null }
                }
            })
        }
        pendingTtsDone = onDone
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vt_${System.nanoTime()}")
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        stopAutoMode()
        recognizer?.destroy()
        tts?.shutdown()
        BluetoothHelper.disableHeadsetMic(this)
        super.onDestroy()
    }
}
