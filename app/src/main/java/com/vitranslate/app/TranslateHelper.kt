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
     * Dịch văn bản. Lần đầu tiên với mỗi cặp ngôn ngữ, ML Kit sẽ tự tải
     * mô hình (~30MB) rồi dịch offline mãi mãi về sau.
     */
    fun translate(
        text: String,
        srcCode: String,
        dstCode: String,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val translator = get(srcCode, dstCode)
        val conditions = DownloadConditions.Builder().build() // cho phép tải qua cả 4G
        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
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
        LanguageIdentification.getClient()
            .identifyLanguage(text)
            .addOnSuccessListener { onResult(it) }
            .addOnFailureListener { onResult("und") }
    }
}
