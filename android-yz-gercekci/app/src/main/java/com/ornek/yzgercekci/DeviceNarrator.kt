package com.ornek.yzgercekci

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Telefonun kendi metin okuma motoruyla (ör. Google Konuşma Hizmetleri) Türkçe anlatım
 * üretir. Ücretsizdir, anahtar istemez; Türkçe ses paketi yüklüyse internetsiz çalışır.
 */
class DeviceNarrator(context: Context) {

    private val ready = CompletableDeferred<Int>()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val tts = TextToSpeech(context.applicationContext) { status -> ready.complete(status) }

    /** Motoru hazırlar. Sorun varsa Türkçe açıklama, yoksa null döndürür. */
    suspend fun open(): String? {
        val status = withTimeout(10_000) { ready.await() }
        if (status != TextToSpeech.SUCCESS) return "Cihazda metin okuma motoru yok."
        val r = tts.setLanguage(Locale("tr", "TR"))
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            return "Cihazda Türkçe ses paketi yok (Ayarlar › Metin okuma çıkışı)."
        }
        // En kaliteli, internet gerektirmeyen Türkçe sesi seç.
        runCatching {
            tts.voices?.filter { it.locale.language == "tr" && !it.isNetworkConnectionRequired }
                ?.maxByOrNull { it.quality }?.let { tts.voice = it }
        }
        tts.setSpeechRate(0.92f)   // belgesel anlatıcısı gibi biraz yavaş
        tts.setPitch(0.95f)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {}
            override fun onDone(utteranceId: String) { pending.remove(utteranceId)?.complete(true) }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) { pending.remove(utteranceId)?.complete(false) }
            override fun onError(utteranceId: String, errorCode: Int) { pending.remove(utteranceId)?.complete(false) }
        })
        return null
    }

    /** [text]'i seslendirir; mono, [Soundtrack.SR] Hz PCM döndürür. */
    suspend fun speak(text: String, dir: File): FloatArray {
        val id = "anlatim_${System.nanoTime()}"
        val file = File(dir, "$id.wav")
        val done = CompletableDeferred<Boolean>()
        pending[id] = done
        if (tts.synthesizeToFile(text, Bundle(), file, id) != TextToSpeech.SUCCESS) {
            pending.remove(id)
            throw IOException("Anlatım başlatılamadı")
        }
        val ok = withTimeout(60_000) { done.await() }
        try {
            if (!ok || !file.exists()) throw IOException("Anlatım üretilemedi")
            return Soundtrack.parseWav(file.readBytes())
        } finally {
            file.delete()
        }
    }

    fun close() {
        runCatching { tts.stop(); tts.shutdown() }
    }
}
