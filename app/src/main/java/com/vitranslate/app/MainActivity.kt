package com.vitranslate.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.concurrent.thread

/**
 * MÀN HÌNH HỘI THOẠI:
 *  - Nút XANH: bấm → ghi âm bạn nói tiếng Việt → dịch sang ngoại ngữ đã chọn
 *    → hiện 2 dòng (gốc + dịch) trong ô hội thoại → đọc to bản dịch.
 *  - Nút ĐỎ: người kia nói ngoại ngữ → dịch sang tiếng Việt → hiện + đọc to.
 *  - Nút 🎵: chọn file ghi âm trong máy → nhận dạng bằng Vosk (offline)
 *    → dịch → hiện phụ đề + đọc to.
 *  - Nội dung hội thoại LƯU VĨNH VIỄN, chỉ mất khi bấm 🗑 Xóa.
 */
class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

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

    /** Mô hình Vosk (nhận dạng offline cho FILE ghi âm) theo mã ML Kit */
    private val voskModels = mapOf(
        "vi" to "vosk-model-small-vn-0.4",
        "en" to "vosk-model-small-en-us-0.15",
        "zh" to "vosk-model-small-cn-0.22",
        "ja" to "vosk-model-small-ja-0.22",
        "ko" to "vosk-model-small-ko-0.22",
        "fr" to "vosk-model-small-fr-0.22",
        "de" to "vosk-model-small-de-0.15",
        "es" to "vosk-model-small-es-0.42",
        "ru" to "vosk-model-small-ru-0.22",
        "it" to "vosk-model-small-it-0.22",
        "pt" to "vosk-model-small-pt-0.3",
        "nl" to "vosk-model-small-nl-0.22",
        "tr" to "vosk-model-small-tr-0.3",
        "hi" to "vosk-model-small-hi-0.22",
        "pl" to "vosk-model-small-pl-0.22",
        "uk" to "vosk-model-small-uk-v3-small",
        "cs" to "vosk-model-small-cs-0.4-rhasspy",
        "fa" to "vosk-model-small-fa-0.5",
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
    private var triedDefaultEngine = false

    private val ui = android.os.Handler(android.os.Looper.getMainLooper())
    private val selected get() = langs[spinner.selectedItemPosition]

    // ================================================================
    //                          KHỞI TẠO
    // ================================================================

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
        // LÀM NÓNG mô hình dịch 2 chiều ngay khi mở app / đổi ngôn ngữ:
        // đến lúc bấm nút nói thì mô hình đã sẵn, dịch tức thì.
        // (Lần đầu mỗi cặp ngôn ngữ cần mạng để tải ~30MB, sau đó offline mãi mãi)
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long
            ) = warmUpModels()
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }

        // TTS: ưu tiên Google (giọng Việt + ngoại ngữ chuẩn), lỗi thì dùng mặc định
        tts = try {
            TextToSpeech(this, this, "com.google.android.tts")
        } catch (e: Exception) {
            TextToSpeech(this, this)
        }

        findViewById<Button>(R.id.btnVi).setOnClickListener { startListening(true) }
        findViewById<Button>(R.id.btnForeign).setOnClickListener { startListening(false) }
        findViewById<Button>(R.id.btnFile).setOnClickListener { pickFileFlow() }
        findViewById<Button>(R.id.btnMovie).setOnClickListener {
            startActivity(Intent(this, MovieActivity::class.java))
        }

        // Nội dung hội thoại LƯU VĨNH VIỄN — chỉ mất khi bấm nút này
        findViewById<Button>(R.id.btnClearLog).setOnClickListener {
            logBuf.setLength(0)
            tvLog.text = ""
            getSharedPreferences("vitranslate", MODE_PRIVATE)
                .edit().remove("conv_log").apply()
            toast("Đã xóa nội dung hội thoại")
        }
        val saved = getSharedPreferences("vitranslate", MODE_PRIVATE)
            .getString("conv_log", "") ?: ""
        if (saved.isNotEmpty()) {
            logBuf.append(saved)
            tvLog.text = Html.fromHtml(saved, Html.FROM_HTML_MODE_LEGACY)
            scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }

        switchBt.setOnCheckedChangeListener { _, on ->
            if (on) {
                val ok = BluetoothHelper.enableHeadsetMic(this)
                if (!ok) {
                    toast("Không tìm thấy tai nghe Bluetooth đã kết nối")
                    switchBt.isChecked = false
                } else toast("Đang dùng micro tai nghe Bluetooth")
            } else BluetoothHelper.disableHeadsetMic(this)
        }

        // ⏱ THANH CHỈNH ĐỘ NHẠY NGẮT CÂU (0,5 – 2,0 giây):
        // Kéo TRÁI  = ngắt câu nhanh  → dịch phản hồi nhanh, nhưng ngập ngừng
        //             giữa câu dễ bị cắt làm đôi.
        // Kéo PHẢI = chờ lâu hơn      → nói được câu dài, chính xác hơn,
        //             nhưng phải đợi lâu hơn sau khi ngừng nói.
        // Giá trị được LƯU lại, lần sau mở app vẫn giữ nguyên.
        val tvSilence = findViewById<TextView>(R.id.tvSilence)
        val seekSilence = findViewById<android.widget.SeekBar>(R.id.seekSilence)
        silenceMs = getSharedPreferences("vitranslate", MODE_PRIVATE)
            .getInt("silence_ms", 1000)
        seekSilence.progress = ((silenceMs - 500) / 100).coerceIn(0, 15)
        tvSilence.text = silenceLabel()
        seekSilence.setOnSeekBarChangeListener(object :
            android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, user: Boolean) {
                silenceMs = 500 + p * 100
                tvSilence.text = silenceLabel()
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {
                getSharedPreferences("vitranslate", MODE_PRIVATE)
                    .edit().putInt("silence_ms", silenceMs).apply()
                toast("Ngắt câu sau ${silenceMs / 1000.0} giây im lặng")
            }
        })

        requestPermissions()
    }

    /** Thời gian im lặng (ms) để coi là hết câu — chỉnh bằng thanh trượt */
    private var silenceMs = 1000

    /** Chuẩn bị sẵn mô hình dịch Việt ↔ ngôn ngữ đang chọn (chạy nền, im lặng) */
    private var warnedNoNetwork = false
    private fun warmUpModels() {
        val fo = selected.mlkit
        TranslateHelper.translate("xin chào", "vi", fo,
            onResult = { warnedNoNetwork = false },
            onError = {
                if (!warnedNoNetwork) {
                    warnedNoNetwork = true
                    runOnUiThread {
                        appendSystemLog(
                            "⚠️ Chưa tải được mô hình dịch ${selected.label} — cần MẠNG " +
                            "cho lần đầu (~30MB). Bật Wi-Fi/4G rồi chọn lại ngôn ngữ."
                        )
                    }
                }
            })
        TranslateHelper.translate("hello", fo, "vi", onResult = {}, onError = {})
    }

    private fun silenceLabel(): String {
        val s = String.format(Locale.US, "%.1f", silenceMs / 1000.0).replace('.', ',')
        val hint = when {
            silenceMs <= 800 -> "nhanh"
            silenceMs >= 1500 -> "chính xác"
            else -> "cân bằng"
        }
        return "⏱ Độ nhạy ngắt câu: $s giây ($hint)"
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

    // ================================================================
    //           🎤 HAI NÚT: BẤM → GHI ÂM → DỊCH → ĐỌC TO
    // ================================================================

    private var sttEngineIdx = 0
    private var manualRetries = 0

    /** Danh sách dịch vụ nhận dạng, ưu tiên dịch vụ ONLINE của app Google */
    private fun sttCandidates(): List<android.content.ComponentName?> {
        val list = mutableListOf<android.content.ComponentName?>()
        try {
            val services = packageManager.queryIntentServices(
                Intent(android.speech.RecognitionService.SERVICE_INTERFACE), 0
            )
            fun add(cn: android.content.ComponentName?) { if (!list.contains(cn)) list.add(cn) }
            services.firstOrNull {
                it.serviceInfo.packageName == "com.google.android.googlequicksearchbox" &&
                        it.serviceInfo.name.contains("GoogleRecognitionService")
            }?.let { add(android.content.ComponentName(it.serviceInfo.packageName, it.serviceInfo.name)) }
            services.filter { it.serviceInfo.packageName == "com.google.android.googlequicksearchbox" }
                .forEach { add(android.content.ComponentName(it.serviceInfo.packageName, it.serviceInfo.name)) }
            add(null) // mặc định của máy
            services.filter { it.serviceInfo.packageName.startsWith("com.google.android") }
                .forEach { add(android.content.ComponentName(it.serviceInfo.packageName, it.serviceInfo.name)) }
        } catch (e: Exception) { list.add(null) }
        if (list.isEmpty()) list.add(null)
        return list
    }

    private fun createRecognizer(): SpeechRecognizer {
        val cands = sttCandidates()
        val cn = cands[sttEngineIdx % cands.size]
        return try {
            if (cn != null) SpeechRecognizer.createSpeechRecognizer(this, cn)
            else SpeechRecognizer.createSpeechRecognizer(this)
        } catch (e: Exception) {
            SpeechRecognizer.createSpeechRecognizer(this)
        }
    }

    private fun errMsg(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Không nghe rõ, hãy bấm nút và nói lại"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Chưa cấp quyền micro"
        1, 2 -> "Lỗi mạng — nhận dạng cần Internet, kiểm tra Wi-Fi/4G"
        11 -> "Mất kết nối dịch vụ nhận dạng (lỗi 11)"
        12, 13 -> "Thiếu gói ngôn ngữ nhận dạng (lỗi $error)"
        else -> "Lỗi nhận dạng ($error), bấm nút thử lại"
    }

    private fun startListening(viToForeign: Boolean, isRetry: Boolean = false) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("Máy chưa có dịch vụ nhận dạng giọng nói (cần app Google)")
            return
        }
        if (!isRetry) manualRetries = 0

        recognizer?.destroy()
        recognizer = createRecognizer()

        val sttLang = if (viToForeign) "vi-VN" else selected.stt
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, sttLang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, sttLang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Ngắt câu theo THANH TRƯỢT độ nhạy (0,5–2,0 giây im lặng)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, silenceMs)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, silenceMs)
            // Ép nhận dạng ONLINE (tránh lỗi 13 thiếu gói offline trên Android 13+)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }

        tvStatus.text = "🎙 Đang ghi âm… " +
                if (viToForeign) "(nói TIẾNG VIỆT)" else "(${selected.label})"

        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val text = results
                    .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (text.isNullOrBlank()) {
                    tvStatus.text = "Không nghe thấy gì, bấm nút nói lại"
                    return
                }
                // HIỆN NGAY câu nghe được — dù khâu dịch có chậm/kẹt thì
                // phụ đề cuộc trao đổi vẫn luôn xuất hiện tức thì
                appendSystemLog("🎙 Nghe được: \"$text\" — đang dịch…")
                tvStatus.text = "Đang dịch…"
                handleRecognized(text, viToForeign)
            }

            override fun onPartialResults(partial: Bundle) {
                val t = partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!t.isNullOrBlank()) tvStatus.text = "🎙 $t"
            }

            override fun onError(error: Int) {
                // Lỗi dịch vụ: xoay sang bộ nhận dạng kế tiếp, tự thử lại tối đa 2 lần
                if (error in intArrayOf(4, 5, 8, 11, 12, 13) && manualRetries < 2) {
                    manualRetries++
                    sttEngineIdx = (sttEngineIdx + 1) % sttCandidates().size
                    tvStatus.text = "Đang đổi bộ nhận dạng (lỗi $error), nghe lại — hãy nói…"
                    ui.postDelayed({ startListening(viToForeign, isRetry = true) }, 500)
                    return
                }
                tvStatus.text = errMsg(error)
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

    /** Đếm lượt dịch — dùng cho bộ giám sát chống treo */
    private var translateTurnId = 0

    /**
     * CHIỀU DỊCH XÁC ĐỊNH THẲNG THEO NÚT BẤM (không qua bước nhận diện
     * ngôn ngữ trung gian nữa — bước đó là điểm treo trên một số máy):
     *  - Nút "Tôi nói"       → dịch Tiếng Việt → ngoại ngữ đã chọn
     *  - Nút "Người kia nói" → dịch ngoại ngữ → Tiếng Việt
     * Đường dịch này giống hệt chế độ dịch phim (đã chạy ổn định):
     * gọi thẳng TranslateHelper.translate rồi hiện phụ đề + đọc to.
     */
    private fun handleRecognized(text: String, viToForeignPressed: Boolean) {
        val isVietnamese = viToForeignPressed
        val src = if (isVietnamese) "vi" else selected.mlkit
        val dst = if (isVietnamese) selected.mlkit else "vi"

        val myTurn = ++translateTurnId
        var turnDone = false
        ui.postDelayed({
            if (myTurn == translateTurnId && !turnDone) {
                turnDone = true
                appendSystemLog(
                    "⚠️ Dịch quá lâu — lần đầu với mỗi cặp ngôn ngữ cần MẠNG để tải " +
                    "mô hình dịch (~30MB). Hãy kiểm tra Wi-Fi/4G rồi bấm nút nói lại. " +
                    "Sau lần tải đầu sẽ dịch offline tức thì."
                )
                tvStatus.text = "Sẵn sàng"
            }
        }, 12000)

        TranslateHelper.translate(text, src, dst,
            onResult = { translated ->
                runOnUiThread {
                    val late = turnDone
                    turnDone = true
                    // Luôn hiện phụ đề (câu gốc + bản dịch) và đọc to bản dịch,
                    // kể cả khi bản dịch về muộn sau cảnh báo — không mất nội dung
                    appendLog(isVietnamese, text, translated)
                    if (!late) tvStatus.text = "Sẵn sàng"
                    if (switchSpeak.isChecked) {
                        speak(translated, if (isVietnamese) selected.tts else Locale("vi", "VN"))
                    }
                }
            },
            onError = { err ->
                runOnUiThread {
                    turnDone = true
                    tvStatus.text = err
                    appendSystemLog("❌ $err — kiểm tra kết nối mạng rồi thử lại")
                }
            }
        )
    }

    // ================================================================
    //        🎵 DỊCH FILE GHI ÂM (nhận dạng offline bằng Vosk)
    // ================================================================

    private var fileLangCode = "en" // ngôn ngữ trong file đang xử lý

    private val filePicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) processAudioFile(uri, fileLangCode)
        }

    /** Bước 1: hỏi file ghi âm là tiếng gì → đảm bảo có mô hình → mở chọn file */
    private fun pickFileFlow() {
        val options = arrayOf("🟢 Tiếng Việt", "🔴 ${selected.label}")
        AlertDialog.Builder(this)
            .setTitle("File ghi âm là tiếng gì?")
            .setItems(options) { _, which ->
                val code = if (which == 0) "vi" else selected.mlkit
                if (!voskModels.containsKey(code)) {
                    toast("Chưa hỗ trợ nhận dạng file cho ${selected.label}")
                    return@setItems
                }
                fileLangCode = code
                ensureVoskModel(code) { filePicker.launch("audio/*") }
            }
            .show()
    }

    private fun modelDir(code: String) = File(filesDir, "models/${voskModels[code]}")

    /** Bước 2: mô hình chưa có thì hỏi tải (~40–70MB, 1 lần) rồi chạy tiếp */
    private fun ensureVoskModel(code: String, onReady: () -> Unit) {
        if (modelDir(code).exists()) { onReady(); return }
        AlertDialog.Builder(this)
            .setTitle("Cần tải mô hình nhận dạng")
            .setMessage("Lần đầu dịch file ghi âm ngôn ngữ này cần tải mô hình (~40–70MB). Tải ngay?")
            .setPositiveButton("Tải") { _, _ -> downloadVoskModel(code, onReady) }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun downloadVoskModel(code: String, onReady: () -> Unit) {
        val name = voskModels[code] ?: return
        appendSystemLog("⏬ Đang tải mô hình nhận dạng $name…")
        thread {
            try {
                val zipFile = File(cacheDir, "$name.zip")
                val conn = URL("https://alphacephei.com/vosk/models/$name.zip")
                    .openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.inputStream.use { inp ->
                    FileOutputStream(zipFile).use { out -> inp.copyTo(out, 64 * 1024) }
                }
                val outRoot = File(filesDir, "models").apply { mkdirs() }
                ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
                    var e = zis.nextEntry
                    while (e != null) {
                        val f = File(outRoot, e.name)
                        if (f.canonicalPath.startsWith(outRoot.canonicalPath)) {
                            if (e.isDirectory) f.mkdirs()
                            else { f.parentFile?.mkdirs(); FileOutputStream(f).use { zis.copyTo(it) } }
                        }
                        e = zis.nextEntry
                    }
                }
                zipFile.delete()
                runOnUiThread {
                    appendSystemLog("✅ Đã tải xong mô hình. Hãy chọn file ghi âm.")
                    onReady()
                }
            } catch (e: Exception) {
                runOnUiThread { appendSystemLog("❌ Lỗi tải mô hình: ${e.message}") }
            }
        }
    }

    /** Bước 3: giải mã file → Vosk nhận dạng → dịch từng đoạn → hiện + đọc to */
    private fun processAudioFile(uri: Uri, langCode: String) {
        appendSystemLog("🎵 Đang xử lý file ghi âm (${if (langCode == "vi") "Tiếng Việt" else selected.label})…")
        tvStatus.text = "Đang nhận dạng file ghi âm…"
        thread {
            try {
                val pcm = decodeToPcm16Mono16k(uri)
                if (pcm.isEmpty()) throw Exception("File không có âm thanh đọc được")

                val model = Model(modelDir(langCode).absolutePath)
                val rec = Recognizer(model, 16000f)
                val segments = mutableListOf<String>()

                var i = 0
                val chunk = ByteArray(8000) // 0,25 giây mỗi lần
                while (i < pcm.size) {
                    val n = minOf(chunk.size, pcm.size - i)
                    System.arraycopy(pcm, i, chunk, 0, n)
                    if (rec.acceptWaveForm(chunk, n)) {
                        val t = JSONObject(rec.result).optString("text")
                        if (t.isNotBlank()) segments.add(t)
                    }
                    i += n
                }
                JSONObject(rec.finalResult).optString("text")
                    .takeIf { it.isNotBlank() }?.let { segments.add(it) }
                rec.close(); model.close()

                if (segments.isEmpty()) {
                    runOnUiThread {
                        appendSystemLog("⚠️ Không nhận dạng được lời nói nào trong file")
                        tvStatus.text = "Sẵn sàng"
                    }
                    return@thread
                }

                // Dịch từng đoạn, hiện cặp câu; đọc to toàn bộ bản dịch khi xong
                val src = langCode
                val dst = if (langCode == "vi") selected.mlkit else "vi"
                val translatedAll = StringBuilder()
                var done = 0
                for (seg in segments) {
                    TranslateHelper.translate(seg, src, dst,
                        onResult = { tr ->
                            runOnUiThread {
                                appendLog(langCode == "vi", seg, tr)
                                translatedAll.append(tr).append(". ")
                                if (++done == segments.size) {
                                    tvStatus.text = "Sẵn sàng"
                                    if (switchSpeak.isChecked) {
                                        val loc = if (langCode == "vi") selected.tts
                                                  else Locale("vi", "VN")
                                        speak(translatedAll.toString(), loc)
                                    }
                                }
                            }
                        },
                        onError = { err ->
                            runOnUiThread {
                                appendSystemLog("❌ $err")
                                if (++done == segments.size) tvStatus.text = "Sẵn sàng"
                            }
                        })
                }
            } catch (e: Exception) {
                runOnUiThread {
                    appendSystemLog("❌ Lỗi xử lý file: ${e.message}")
                    tvStatus.text = "Sẵn sàng"
                }
            }
        }
    }

    /**
     * Giải mã file âm thanh bất kỳ (mp3/m4a/wav/amr/ogg…) thành PCM 16-bit
     * mono 16kHz cho Vosk, dùng MediaExtractor + MediaCodec của Android.
     */
    private fun decodeToPcm16Mono16k(uri: Uri): ByteArray {
        val extractor = MediaExtractor()
        extractor.setDataSource(this, uri, null)
        var trackIdx = -1
        var format: MediaFormat? = null
        for (t in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(t)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                trackIdx = t; format = f; break
            }
        }
        if (trackIdx < 0 || format == null) throw Exception("Không tìm thấy luồng âm thanh")
        extractor.selectTrack(trackIdx)

        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val out = ByteArrayOutputStream()
        var srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val inIdx = codec.dequeueInputBuffer(10000)
                if (inIdx >= 0) {
                    val buf = codec.getInputBuffer(inIdx)!!
                    val n = extractor.readSampleData(buf, 0)
                    if (n < 0) {
                        codec.queueInputBuffer(inIdx, 0, 0, 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIdx, 0, n, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIdx = codec.dequeueOutputBuffer(info, 10000)
            when {
                outIdx >= 0 -> {
                    val buf = codec.getOutputBuffer(outIdx)!!
                    val bytes = ByteArray(info.size)
                    buf.get(bytes); buf.clear()
                    out.write(bytes)
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val nf = codec.outputFormat
                    srcRate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
            }
        }
        codec.stop(); codec.release(); extractor.release()

        // PCM16 nhiều kênh / tần số khác → mono 16kHz (trộn kênh + nội suy tuyến tính)
        val raw = out.toByteArray()
        val totalSamples = raw.size / 2
        val frames = totalSamples / channels
        val mono = ShortArray(frames)
        for (f in 0 until frames) {
            var sum = 0
            for (c in 0 until channels) {
                val idx = (f * channels + c) * 2
                sum += ((raw[idx + 1].toInt() shl 8) or (raw[idx].toInt() and 0xFF)).toShort().toInt()
            }
            mono[f] = (sum / channels).toShort()
        }
        val outFrames = (frames.toLong() * 16000 / srcRate).toInt()
        val res = ByteArray(outFrames * 2)
        for (f in 0 until outFrames) {
            val pos = f.toLong() * srcRate / 16000
            val p0 = pos.toInt().coerceAtMost(frames - 1)
            val p1 = (p0 + 1).coerceAtMost(frames - 1)
            val frac = (f.toLong() * srcRate % 16000).toFloat() / 16000f
            val s = (mono[p0] * (1 - frac) + mono[p1] * frac).toInt()
            res[f * 2] = (s and 0xFF).toByte()
            res[f * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return res
    }

    // ================================================================
    //                    Ô HỘI THOẠI (lưu vĩnh viễn)
    // ================================================================

    private val logBuf = StringBuilder()

    private fun persistLog(html: String) {
        logBuf.append(html)
        if (logBuf.length > 200_000) logBuf.delete(0, logBuf.length - 150_000)
        getSharedPreferences("vitranslate", MODE_PRIVATE)
            .edit().putString("conv_log", logBuf.toString()).apply()
    }

    private fun appendSystemLog(msg: String) {
        val html = "<i>$msg</i><br><br>"
        persistLog(html)
        tvLog.append(Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY))
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun appendLog(speakerVi: Boolean, original: String, translated: String) {
        val who = if (speakerVi) "🟢 Bạn" else "🔴 Khách"
        val html = "<b>$who:</b> $original<br><i>→ $translated</i><br><br>"
        persistLog(html)
        tvLog.append(Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY))
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    // ================================================================
    //                        ĐỌC TO (TTS)
    // ================================================================

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (!ttsReady) {
            if (!triedDefaultEngine) {
                triedDefaultEngine = true
                tts = TextToSpeech(this, this)
            } else {
                toast("Không khởi động được Text-to-Speech. Hãy cài 'Speech Services by Google' từ Play Store.")
            }
        }
    }

    private fun reinitTts() {
        ttsReady = false
        triedDefaultEngine = false
        try { tts?.shutdown() } catch (_: Exception) {}
        tts = try {
            TextToSpeech(this, this, "com.google.android.tts")
        } catch (e: Exception) {
            TextToSpeech(this, this)
        }
    }

    private fun speak(text: String, locale: Locale) {
        if (!ttsReady) {
            appendSystemLog("⚠️ Giọng đọc chưa sẵn sàng — đang khởi động lại, câu sau sẽ có tiếng…")
            reinitTts()
            return
        }
        val res = tts?.setLanguage(locale)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            appendSystemLog(
                "❌ Máy thiếu giọng đọc ${locale.displayLanguage}. " +
                "Cài 'Speech Services by Google' từ Play Store rồi vào Cài đặt > " +
                "Chuyển văn bản thành giọng nói > chọn Google."
            )
            return
        }
        val params = android.os.Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        val rc = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "vt_${System.nanoTime()}")
            ?: TextToSpeech.ERROR
        if (rc != TextToSpeech.SUCCESS) {
            appendSystemLog("❌ Giọng đọc bị lỗi (mã $rc) — đang khởi động lại, câu sau sẽ có tiếng…")
            reinitTts()
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.shutdown()
        BluetoothHelper.disableHeadsetMic(this)
        super.onDestroy()
    }
}
