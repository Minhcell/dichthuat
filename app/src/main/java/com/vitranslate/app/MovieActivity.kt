package com.vitranslate.app

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * CHẾ ĐỘ DỊCH PHIM:
 * Ghi lại âm thanh đang phát trên máy (AudioPlaybackCapture, Android 10+),
 * nhận dạng lời thoại bằng Vosk (offline), dịch sang tiếng Việt bằng ML Kit,
 * và hiển thị phụ đề nổi trên màn hình.
 */
class MovieActivity : AppCompatActivity() {

    // (Tên hiển thị, mã ML Kit, tên mô hình Vosk)
    data class MovieLang(val label: String, val mlkit: String, val voskModel: String)

    private val movieLangs = listOf(
        MovieLang("🇬🇧 Tiếng Anh", "en", "vosk-model-small-en-us-0.15"),
        MovieLang("🇨🇳 Tiếng Trung", "zh", "vosk-model-small-cn-0.22"),
        MovieLang("🇯🇵 Tiếng Nhật", "ja", "vosk-model-small-ja-0.22"),
        MovieLang("🇰🇷 Tiếng Hàn", "ko", "vosk-model-small-ko-0.22"),
        MovieLang("🇫🇷 Tiếng Pháp", "fr", "vosk-model-small-fr-0.22"),
        MovieLang("🇩🇪 Tiếng Đức", "de", "vosk-model-small-de-0.15"),
        MovieLang("🇪🇸 Tiếng Tây Ban Nha", "es", "vosk-model-small-es-0.42"),
        MovieLang("🇷🇺 Tiếng Nga", "ru", "vosk-model-small-ru-0.22"),
    )

    private lateinit var spinner: Spinner
    private lateinit var tvStatus: TextView
    private lateinit var progress: ProgressBar

    private val selected get() = movieLangs[spinner.selectedItemPosition]

    private fun modelDir(m: MovieLang) = File(filesDir, "models/${m.voskModel}")

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val svc = Intent(this, MovieTranslateService::class.java).apply {
                    action = MovieTranslateService.ACTION_START
                    putExtra("resultCode", result.resultCode)
                    putExtra("data", result.data)
                    putExtra("modelPath", modelDir(selected).absolutePath)
                    putExtra("srcLang", selected.mlkit)
                }
                startForegroundService(svc)
                tvStatus.text = "✅ Đang dịch. Hãy mở phim/video. Phụ đề sẽ hiện nổi trên màn hình."
            } else {
                tvStatus.text = "Bạn chưa cho phép ghi âm thanh màn hình."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_movie)

        spinner = findViewById(R.id.spinnerMovieLang)
        tvStatus = findViewById(R.id.tvMovieStatus)
        progress = findViewById(R.id.progress)

        spinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, movieLangs.map { it.label }
        )

        findViewById<Button>(R.id.btnDownloadModel).setOnClickListener { downloadModel() }

        findViewById<Button>(R.id.btnOverlayPerm).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } else toast("Đã có quyền hiển thị nổi ✅")
        }

        findViewById<Button>(R.id.btnStartCapture).setOnClickListener { startCapture() }

        findViewById<Button>(R.id.btnStopCapture).setOnClickListener {
            startService(Intent(this, MovieTranslateService::class.java).apply {
                action = MovieTranslateService.ACTION_STOP
            })
            tvStatus.text = "Đã dừng."
        }
    }

    private fun startCapture() {
        if (!modelDir(selected).exists()) {
            toast("Hãy tải mô hình nhận dạng trước (nút ⬇️)")
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            toast("Hãy cấp quyền hiển thị phụ đề nổi trước (nút 🪟)")
            return
        }
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    // -------- Tải và giải nén mô hình Vosk (chỉ 1 lần cho mỗi ngôn ngữ) --------

    private fun downloadModel() {
        val lang = selected
        val dir = modelDir(lang)
        if (dir.exists()) {
            tvStatus.text = "Mô hình ${lang.label} đã có sẵn ✅"
            return
        }
        val url = "https://alphacephei.com/vosk/models/${lang.voskModel}.zip"
        progress.visibility = android.view.View.VISIBLE
        progress.isIndeterminate = false
        tvStatus.text = "Đang tải mô hình ${lang.label}…"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val zipFile = File(cacheDir, "${lang.voskModel}.zip")
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                val total = conn.contentLength
                conn.inputStream.use { input ->
                    FileOutputStream(zipFile).use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt()
                                withContext(Dispatchers.Main) { progress.progress = pct }
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) { tvStatus.text = "Đang giải nén…" }

                // Giải nén vào filesDir/models/ (zip chứa sẵn thư mục tên mô hình)
                val outRoot = File(filesDir, "models").apply { mkdirs() }
                ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val f = File(outRoot, entry.name)
                        // Chống zip-slip
                        if (!f.canonicalPath.startsWith(outRoot.canonicalPath)) {
                            entry = zis.nextEntry; continue
                        }
                        if (entry.isDirectory) f.mkdirs()
                        else {
                            f.parentFile?.mkdirs()
                            FileOutputStream(f).use { zis.copyTo(it) }
                        }
                        entry = zis.nextEntry
                    }
                }
                zipFile.delete()
                withContext(Dispatchers.Main) {
                    progress.visibility = android.view.View.GONE
                    tvStatus.text = "✅ Đã tải xong mô hình ${lang.label}. Có thể bắt đầu dịch phim."
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progress.visibility = android.view.View.GONE
                    tvStatus.text = "❌ Lỗi tải mô hình: ${e.message}"
                }
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
