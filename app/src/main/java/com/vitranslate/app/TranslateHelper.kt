package com.vitranslate.app

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

/**
 * Bao bọc ML Kit Translate (dịch offline sau khi tải mô hình lần đầu)
 * và ML Kit Language ID (tự phát hiện ngôn ngữ của câu nói).
 */
object TranslateHelper {

    // Cache translator theo cặp ngôn ngữ để không tạo lại liên tục
    private val cache = HashMap<String, Translator>()

    // Các cặp ngôn ngữ ĐÃ XÁC NHẬN có mô hình trên máy → những câu sau
    // dịch THẲNG, bỏ qua bước kiểm tra mô hình (tiết kiệm 20–100ms MỖI câu)
    private val readyPairs = HashSet<String>()

    // Một client nhận diện ngôn ngữ dùng chung (trước đây tạo mới mỗi câu)
    private val langIdClient by lazy { LanguageIdentification.getClient() }

    private fun get(src: String, dst: String): Translator {
        val key = "$src>$dst"
        return cache.getOrPut(key) {
            Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(src)
                    .setTargetLanguage(dst)
                    .build()
            )
        }
    }

    /**
     * Dịch văn bản. Lần đầu với mỗi cặp ngôn ngữ: kiểm tra + tải mô hình
     * (~30MB) nếu chưa có. TỪ CÂU THỨ HAI: dịch thẳng offline, không còn
     * bước kiểm tra trung gian → phản hồi gần như tức thì.
     */
    fun translate(
        text: String,
        srcCode: String,
        dstCode: String,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val key = "$srcCode>$dstCode"
        val translator = get(srcCode, dstCode)

        // Đường NHANH: mô hình đã xác nhận có → dịch ngay
        if (readyPairs.contains(key)) {
            translator.translate(text)
                .addOnSuccessListener { onResult(it) }
                .addOnFailureListener { onError("Lỗi dịch: ${it.message}") }
            return
        }

        // Đường CHẬM (chỉ lần đầu mỗi cặp): kiểm tra/tải mô hình rồi dịch
        val conditions = DownloadConditions.Builder().build() // cho phép tải qua cả 4G
        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                readyPairs.add(key) // từ giờ đi đường nhanh
                translator.translate(text)
                    .addOnSuccessListener { onResult(it) }
                    .addOnFailureListener { onError("Lỗi dịch: ${it.message}") }
            }
            .addOnFailureListener { onError("Lỗi tải mô hình dịch: ${it.message}") }
    }

    /**
     * Tự phát hiện ngôn ngữ của đoạn văn bản.
     * Trả về mã BCP-47 ("vi", "en", "ja"...) hoặc "und" nếu không xác định.
     */
    fun identifyLanguage(text: String, onResult: (String) -> Unit) {
        langIdClient
            .identifyLanguage(text)
            .addOnSuccessListener { onResult(it) }
            .addOnFailureListener { onResult("und") }
    }
}
