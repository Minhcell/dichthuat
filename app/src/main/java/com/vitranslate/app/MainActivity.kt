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

        tts = TextToSpeech(this, this)

        findViewById<Button>(R.id.btnVi).setOnClickListener {
            startListening(viToForeign = true)
        }
        findViewById<Button>(R.id.btnForeign).setOnClickListener {
            startListening(viToForeign = false)
        }
        findViewById<Button>(R.id.btnMovie).setOnClickListener {
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
                        if (switchSpeak.isChecked) {
                            speak(translated, if (isVietnamese) selected.tts else Locale("vi", "VN"))
                        }
                    }
                },
                onError = { err -> runOnUiThread { tvStatus.text = err } }
            )
        }
    }

    private fun appendLog(speakerVi: Boolean, original: String, translated: String) {
        val who = if (speakerVi) "🟢 Bạn" else "🔴 Khách"
        val html = "<b>$who:</b> $original<br><i>→ $translated</i><br><br>"
        tvLog.append(Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY))
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    // ---------------- ĐỌC TO (TTS) ----------------

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (!ttsReady) toast("Không khởi động được Text-to-Speech")
    }

    private fun speak(text: String, locale: Locale) {
        if (!ttsReady) return
        val res = tts?.setLanguage(locale)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            toast("TTS chưa hỗ trợ giọng ${locale.displayLanguage}. Vào Cài đặt > Chuyển văn bản thành giọng nói để tải thêm.")
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vt")
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.shutdown()
        BluetoothHelper.disableHeadsetMic(this)
        super.onDestroy()
    }
}
