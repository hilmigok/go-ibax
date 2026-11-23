package com.ornek.yzgercekci

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/** Önizleme sesini döngüde çalar (önizleme görüntüsüyle aynı anda başlatılır). */
class AudioPlayer {

    @Volatile private var running = false
    private var thread: Thread? = null

    /** [pcm]'yi [fromSample]. örnekten başlayarak döngüde çalar. */
    fun play(pcm: ShortArray, fromSample: Int = 0) {
        stop()
        if (pcm.isEmpty()) return
        val startAt = fromSample.coerceIn(0, pcm.size - 1)
        running = true
        thread = Thread({
            val min = AudioTrack.getMinBufferSize(Soundtrack.SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(Soundtrack.SR)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(maxOf(min, 8192))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            try {
                track.play()
                val chunk = 2048
                var first = true
                while (running) {
                    var pos = if (first) startAt else 0
                    first = false
                    while (running && pos < pcm.size) {
                        val n = minOf(chunk, pcm.size - pos)
                        val w = track.write(pcm, pos, n)
                        if (w < 0) return@Thread
                        pos += w
                    }
                }
            } finally {
                runCatching { track.pause(); track.flush() }
                track.release()
            }
        }, "onizleme-ses").apply { start() }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }
}
