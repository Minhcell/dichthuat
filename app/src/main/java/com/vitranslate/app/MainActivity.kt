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
            if (autoMode) stopAutoMode() else chooseStartLanguage()
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

    /**
     * SỬA LỖI 11/13 trên Xiaomi/HyperOS: máy gắn app vào dịch vụ nhận dạng
     * MẶC ĐỊNH CỦA HÃNG (không hỗ trợ tiếng Việt) → lỗi 13 (ngôn ngữ không có)
     * và 11 (mất kết nối dịch vụ). Hàm này tìm và KẾT NỐI THẲNG vào dịch vụ
     * nhận dạng CỦA GOOGLE (app Google / Speech Services) thay vì dùng mặc định.
     */
    private var sttEngineIdx = 0

    /**
     * Danh sách ứng viên dịch vụ nhận dạng, xếp theo độ ưu tiên:
     *  1. Dịch vụ ONLINE của app Google (GoogleRecognitionService) — chuẩn nhất,
     *     hỗ trợ mọi ngôn ngữ, không cần tải gói offline
     *  2. Các dịch vụ khác trong app Google
     *  3. Dịch vụ mặc định của máy
     *  4. Các dịch vụ Google khác (vd: bộ on-device của Android System Intelligence)
     * Khi gặp lỗi 11/12/13, app XOAY sang ứng viên kế tiếp và thử lại.
     */
    private fun sttCandidates(): List<android.content.ComponentName?> {
        val list = mutableListOf<android.content.ComponentName?>()
        try {
            val services = packageManager.queryIntentServices(
                Intent(android.speech.RecognitionService.SERVICE_INTERFACE), 0
            )
            fun add(cn: android.content.ComponentName?) {
                if (!list.contains(cn)) list.add(cn)
            }
            // (1) Dịch vụ online của app Google
            services.firstOrNull {
                it.serviceInfo.packageName == "com.google.android.googlequicksearchbox" &&
                        it.serviceInfo.name.contains("GoogleRecognitionService")
            }?.let { add(android.content.ComponentName(it.serviceInfo.packageName, it.serviceInfo.name)) }
            // (2) Các dịch vụ khác trong app Google
            services.filter { it.serviceInfo.packageName == "com.google.android.googlequicksearchbox" }
                .forEach { add(android.content.ComponentName(it.serviceInfo.packageName, it.serviceInfo.name)) }
            // (3) Mặc định của máy
            add(null)
            // (4) Các dịch vụ Google còn lại
            services.filter { it.serviceInfo.packageName.startsWith("com.google.android") }
                .forEach { add(android.content.ComponentName(it.serviceInfo.packageName, it.serviceInfo.name)) }
        } catch (e: Exception) {
            list.add(null)
        }
        if (list.isEmpty()) list.add(null)
        return list
    }

    private fun createBestRecognizer(): SpeechRecognizer {
        val cands = sttCandidates()
        val cn = cands[sttEngineIdx % cands.size]
        return try {
            if (cn != null) SpeechRecognizer.createSpeechRecognizer(this, cn)
            else SpeechRecognizer.createSpeechRecognizer(this)
        } catch (e: Exception) {
            SpeechRecognizer.createSpeechRecognizer(this)
        }
    }

    /** Xoay sang dịch vụ nhận dạng kế tiếp (gọi khi gặp lỗi 11/12/13) */
    private fun rotateSttEngine() {
        sttEngineIdx = (sttEngineIdx + 1) % sttCandidates().size
    }

    /** Diễn giải mã lỗi nhận dạng thành thông báo dễ hiểu */
    private fun sttErrorMsg(error: Int): String = when (error) {
        1, 2 -> "Lỗi mạng khi nhận dạng — kiểm tra Internet rồi thử lại"
        3 -> "Lỗi micro — kiểm tra quyền micro / tai nghe"
        4, 11 -> "Dịch vụ nhận dạng bị ngắt (lỗi $error) — đang thử lại…"
        5 -> "Lỗi trình nhận dạng (5) — thử lại"
        8 -> "Trình nhận dạng đang bận (8) — đang thử lại…"
        9 -> "Chưa cấp quyền micro"
        10 -> "Quá nhiều yêu cầu (10) — chờ chút rồi thử lại"
        12, 13 -> "Ngôn ngữ chưa có trong dịch vụ nhận dạng (lỗi $error). " +
                "Hãy mở Play Store cập nhật app Google, rồi vào Cài đặt > " +
                "Quản lý ứng dụng > Google > bật, và thử lại."
        else -> "Lỗi nhận dạng ($error), thử lại"
    }

    /** Cho phép tự thử lại tối đa 2 lần (xoay engine) ở nút bấm thủ công */
    private var manualRetries = 0

    private fun startListening(viToForeign: Boolean, isRetry: Boolean = false) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("Máy chưa có dịch vụ nhận dạng giọng nói (cần app Google)")
            return
        }
        if (!isRetry) manualRetries = 0
        recognizer?.destroy()
        recognizer = createBestRecognizer()

        val sttLang = if (viToForeign) "vi-VN" else selected.stt
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, sttLang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, sttLang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // QUAN TRỌNG (sửa lỗi 13): ÉP nhận dạng ONLINE — Android 13+
            // mặc định ưu tiên bộ on-device vốn đòi tải gói ngôn ngữ riêng
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
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
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        tvStatus.text = "Không nghe rõ, hãy thử lại"
                    // Lỗi dịch vụ (bận/ngắt/thiếu ngôn ngữ): XOAY sang dịch vụ
                    // nhận dạng kế tiếp rồi tự thử lại, tối đa 2 lần
                    4, 5, 8, 11, 12, 13 -> {
                        if (manualRetries < 2) {
                            manualRetries++
                            rotateSttEngine()
                            tvStatus.text = "Đang đổi bộ nhận dạng (lỗi $error), nghe lại…"
                            ui.postDelayed({ startListening(viToForeign, isRetry = true) }, 500)
                        } else {
                            tvStatus.text = sttErrorMsg(error)
                        }
                    }
                    else -> tvStatus.text = sttErrorMsg(error)
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

    /** Đếm lượt của chế độ tự động — dùng cho bộ giám sát chống treo */
    private var autoTurnId = 0

    /**
     * Xử lý câu vừa nghe được:
     * 1. HIỆN NGAY câu gốc vào ô hội thoại (để luôn kiểm tra được máy nghe gì).
     * 2. ML Kit Language ID tự phát hiện ngôn ngữ thật của câu.
     * 3. Dịch: tiếng Việt → ngoại ngữ, ngoại ngữ → tiếng Việt.
     * 4. Hiện cặp câu (gốc + dịch) và ĐỌC TO bản dịch.
     * 5. Bộ giám sát 15 giây: nếu dịch bị treo (mạng chậm, đang tải mô hình)
     *    thì báo lỗi vào ô hội thoại và cho vòng lặp tự động chạy tiếp,
     *    không bao giờ đứng im.
     */
    private fun handleRecognized(text: String, viToForeignPressed: Boolean) {
        // (1) Hiện ngay câu nghe được — chưa cần biết dịch có thành công không
        appendSystemLog("🎙 Nghe được: \"$text\" — đang dịch…")

        // (5) Bộ giám sát chống treo cho chế độ tự động
        val myTurn = ++autoTurnId
        var turnDone = false
        if (autoMode) {
            ui.postDelayed({
                if (autoMode && myTurn == autoTurnId && !turnDone) {
                    turnDone = true
                    appendSystemLog("⚠️ Dịch/đọc quá lâu — tự chuyển lượt và nghe tiếp…")
                    // Chuyển lượt cho người kia (không kẹt mãi một thứ tiếng)
                    autoListenVi = !autoListenVi
                    beep()
                    autoListenLoop()
                }
            }, 15000)
        }

        TranslateHelper.identifyLanguage(text) { detected ->
            val isVietnamese = when (detected) {
                "vi" -> true
                "und" -> viToForeignPressed // không rõ → tin theo nút/lượt hiện tại
                else -> false
            }
            val src = if (isVietnamese) "vi" else selected.mlkit
            val dst = if (isVietnamese) selected.mlkit else "vi"

            TranslateHelper.translate(text, src, dst,
                onResult = { translated ->
                    runOnUiThread {
                        if (turnDone) return@runOnUiThread // giám sát đã cho chạy tiếp rồi
                        appendLog(
                            speakerVi = isVietnamese,
                            original = text,
                            translated = translated
                        )
                        tvStatus.text = "Sẵn sàng"
                        if (!ttsReady && autoMode) {
                            appendSystemLog("⚠️ Giọng đọc (TTS) chưa sẵn sàng — chỉ hiện chữ, chưa phát tiếng được")
                        }
                        if (switchSpeak.isChecked || autoMode) {
                            speak(translated, if (isVietnamese) selected.tts else Locale("vi", "VN")) {
                                // Đọc xong bản dịch → nếu đang ở chế độ tự động thì
                                // BÍP báo lượt và chuyển sang nghe NGƯỜI KIA
                                if (autoMode && !turnDone) {
                                    turnDone = true
                                    autoListenVi = !isVietnamese
                                    beep()
                                    ui.postDelayed({ autoListenLoop() }, 350)
                                }
                            }
                        } else if (autoMode && !turnDone) {
                            turnDone = true
                            autoListenVi = !isVietnamese
                            beep()
                            ui.postDelayed({ autoListenLoop() }, 350)
                        }
                    }
                },
                onError = { err ->
                    runOnUiThread {
                        tvStatus.text = err
                        appendSystemLog("❌ $err")
                        if (autoMode && !turnDone) {
                            turnDone = true
                            ui.postDelayed({ autoListenLoop() }, 800)
                        }
                    }
                }
            )
        }
    }

    /* ================================================================
       🔁 HỘI THOẠI TỰ ĐỘNG (RẢNH TAY) — kiểu tai nghe phiên dịch:
       Dùng loa ngoài (đặt điện thoại giữa 2 người) HOẶC tai nghe
       Bluetooth (bật công tắc Bluetooth, mỗi người đeo 1 bên tai).

       Cách hoạt động — vòng lặp "ping-pong":
       1. Chọn thứ tiếng bắt đầu (Việt hoặc ngoại ngữ) khi bấm nút.
       2. Nghe → nhận câu → hiện CẢ 2 DÒNG (câu gốc + bản dịch) →
          ĐỌC TO bản dịch cho người kia nghe.
       3. Đọc xong: BÍP một tiếng + tự chuyển sang nghe thứ tiếng còn lại.
       4. Không ai nói trong lượt → bíp + tự đảo lượt nghe.
       5. ML Kit Language ID kiểm lại từng câu: nói "trái lượt" vẫn dịch đúng.

       Kỹ thuật ổn định (sửa lỗi "không nhận được giọng nói"):
       - Dùng MỘT SpeechRecognizer duy nhất cho cả phiên, không hủy/tạo lại
         liên tục (tạo lại mỗi vòng gây lỗi ERROR_BUSY/CLIENT trên nhiều máy
         → máy im lặng không nghe gì).
       - Gặp lỗi BUSY/CLIENT: cancel() rồi nghe lại; lỗi 4 lần liên tiếp
         mới hủy tạo recognizer mới.
       ================================================================ */

    private var autoMode = false
    private var autoListenVi = true // lượt hiện tại: true = đang chờ tiếng Việt
    private var autoRec: SpeechRecognizer? = null
    private var autoErrStreak = 0
    private var silentCount = 0 // số lần im lặng liên tiếp trong một lượt
    private val ui = android.os.Handler(android.os.Looper.getMainLooper())
    private var beeper: android.media.ToneGenerator? = null

    /** Hỏi thứ tiếng bắt đầu rồi mới chạy hội thoại tự động */
    private fun chooseStartLanguage() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Bắt đầu nghe thứ tiếng nào trước?")
            .setItems(arrayOf("🟢 Tiếng Việt nói trước", "🔴 ${selected.label} nói trước")) { _, which ->
                startAutoMode(startWithVi = which == 0)
            }
            .show()
    }

    private fun startAutoMode(startWithVi: Boolean) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("Máy chưa có dịch vụ nhận dạng giọng nói (cần app Google)")
            return
        }
        // Dừng nghe thủ công nếu đang chạy, tránh tranh chấp micro
        recognizer?.destroy(); recognizer = null

        autoMode = true
        autoListenVi = startWithVi
        autoErrStreak = 0
        silentCount = 0
        beeper = try {
            android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 85)
        } catch (e: Exception) { null }

        autoRec = createBestRecognizer() // kết nối thẳng dịch vụ Google (sửa lỗi 11/13)
        autoRec?.setRecognitionListener(autoListener)

        findViewById<Button>(R.id.btnAuto).text = "⏹ Dừng hội thoại tự động"
        appendSystemLog(
            "🔁 Hội thoại tự động: Việt ↔ ${selected.label}. " +
            "Nghe ${if (startWithVi) "TIẾNG VIỆT" else selected.label} trước. " +
            "Sau mỗi tiếng BÍP là đến lượt người kia nói."
        )
        // Tải sẵn mô hình dịch CẢ 2 CHIỀU ngay từ đầu (lần đầu ~30MB/chiều),
        // để câu đầu tiên không bị treo chờ tải
        appendSystemLog("⏬ Đang chuẩn bị mô hình dịch 2 chiều (lần đầu có thể mất 1–2 phút, cần mạng)…")
        TranslateHelper.translate("xin chào", "vi", selected.mlkit,
            onResult = { runOnUiThread { appendSystemLog("✅ Sẵn sàng dịch Việt → ${selected.label}") } },
            onError = { e -> runOnUiThread { appendSystemLog("❌ $e") } })
        TranslateHelper.translate("hello", selected.mlkit, "vi",
            onResult = { runOnUiThread { appendSystemLog("✅ Sẵn sàng dịch ${selected.label} → Việt") } },
            onError = { e -> runOnUiThread { appendSystemLog("❌ $e") } })
        autoListenLoop()
    }

    private fun stopAutoMode() {
        if (!autoMode) return
        autoMode = false
        ui.removeCallbacksAndMessages(null)
        autoRec?.destroy(); autoRec = null
        beeper?.release(); beeper = null
        tts?.stop()
        findViewById<Button>(R.id.btnAuto).text =
            "🔁 Hội thoại tự động (rảnh tay — mỗi người 1 tai nghe)"
        tvStatus.text = "Đã dừng hội thoại tự động"
    }

    /** Bíp ngắn báo "đến lượt nói" */
    private fun beep() {
        beeper?.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 130)
    }

    /** Listener dùng chung cho mọi vòng nghe của chế độ tự động */
    private val autoListener = object : RecognitionListener {
        override fun onResults(results: Bundle) {
            if (!autoMode) return
            autoErrStreak = 0
            silentCount = 0
            val text = results
                .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (text.isNullOrBlank()) {
                ui.postDelayed({ autoListenLoop() }, 250)
                return
            }
            tvStatus.text = "Đang dịch…"
            // handleRecognized hiện 2 dòng (gốc + dịch), đọc to bản dịch,
            // rồi tự gọi lại autoListenLoop cho lượt tiếp theo
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
                    // Không ai nói ở lượt này. Chờ đủ 2 lần im lặng liên tiếp
                    // (~10 giây) mới đảo lượt — để người nói có thời gian
                    // suy nghĩ/mở lời, không bị "cướp lượt" giữa chừng
                    autoErrStreak = 0
                    silentCount++
                    if (silentCount >= 2) {
                        silentCount = 0
                        autoListenVi = !autoListenVi
                        beep()
                    }
                    ui.postDelayed({ autoListenLoop() }, 350)
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    tvStatus.text = "Chưa cấp quyền micro"
                    stopAutoMode()
                }
                4, 11, 12, 13 -> {
                    // Dịch vụ nhận dạng lỗi / thiếu ngôn ngữ → XOAY sang dịch vụ
                    // kế tiếp trong danh sách rồi thử lại; hết danh sách vẫn lỗi
                    // (thử tối đa 4 lần) thì báo hướng dẫn và dừng
                    autoErrStreak++
                    if (autoErrStreak <= 4) {
                        rotateSttEngine()
                        appendSystemLog("🔄 Lỗi $error — đổi bộ nhận dạng, thử lại (${autoErrStreak}/4)…")
                        autoRec?.destroy()
                        autoRec = createBestRecognizer()
                        autoRec?.setRecognitionListener(this)
                        ui.postDelayed({ autoListenLoop() }, 600)
                    } else {
                        appendSystemLog("❌ " + sttErrorMsg(error))
                        tvStatus.text = sttErrorMsg(error)
                        stopAutoMode()
                    }
                }
                else -> {
                    // BUSY / CLIENT / AUDIO…: KHÔNG đảo lượt, thử nghe lại.
                    // Lỗi 4 lần liên tiếp → tạo recognizer mới cho chắc.
                    autoErrStreak++
                    try { autoRec?.cancel() } catch (e: Exception) {}
                    if (autoErrStreak >= 4) {
                        autoErrStreak = 0
                        autoRec?.destroy()
                        autoRec = createBestRecognizer()
                        autoRec?.setRecognitionListener(this)
                    }
                    ui.postDelayed({ autoListenLoop() }, 500)
                }
            }
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { tvStatus.text = "Đang xử lý…" }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /** Một vòng nghe của chế độ tự động (dùng lại recognizer, không tạo mới) */
    private fun autoListenLoop() {
        if (!autoMode) return
        val rec = autoRec ?: return

        val sttLang = if (autoListenVi) "vi-VN" else selected.stt
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, sttLang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, sttLang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Ngắt câu nhanh hơn: im lặng 1 giây coi như nói xong → dịch ngay
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000)
            // QUAN TRỌNG (sửa lỗi 13): ÉP nhận dạng ONLINE
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }

        tvStatus.text = if (autoListenVi)
            "🟢 MỜI NÓI TIẾNG VIỆT…"
        else
            "🔴 ${selected.label} — SPEAK NOW…"

        try {
            rec.startListening(intent)
        } catch (e: Exception) {
            ui.postDelayed({ autoListenLoop() }, 600)
        }
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

    private fun speak(text: String, locale: Locale, onDone: (() -> Unit)? = null) {
        if (!ttsReady) { onDone?.invoke(); return }
        val res = tts?.setLanguage(locale)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            toast("TTS chưa hỗ trợ giọng ${locale.displayLanguage}. Vào Cài đặt > Chuyển văn bản thành giọng nói để tải thêm.")
            onDone?.invoke()
            return
        }
        // SỬA LỖI QUAN TRỌNG (hội thoại tự động kẹt ở tiếng Việt):
        // Gắn bộ báo "đọc xong" MỖI LẦN đọc — vì đối tượng TTS có thể đã bị
        // thay bằng engine dự phòng, cờ "đã gắn 1 lần" khiến engine mới
        // KHÔNG có bộ báo → không bao giờ chuyển lượt sang ngoại ngữ.
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
        pendingTtsDone = onDone
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vt_${System.nanoTime()}")

        // LƯỚI AN TOÀN: nếu vì bất kỳ lý do gì bộ báo không nổ (engine lạ),
        // tự coi là đọc xong sau thời lượng ước tính (90ms/ký tự + 2 giây)
        // → vòng lặp hội thoại tự động KHÔNG BAO GIỜ kẹt lượt nữa
        if (onDone != null) {
            val estMs = (text.length * 90L + 2000L).coerceAtMost(15000L)
            ui.postDelayed({
                if (pendingTtsDone === onDone) {
                    pendingTtsDone = null
                    onDone.invoke()
                }
            }, estMs)
        }
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
