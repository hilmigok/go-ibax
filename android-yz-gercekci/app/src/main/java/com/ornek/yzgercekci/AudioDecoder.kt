package com.ornek.yzgercekci

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.IOException
import java.nio.ByteOrder

/** MP3/AAC/OGG gibi ses dosyalarını cihazın kod çözücüsüyle mono PCM'e (44,1 kHz) çevirir. */
object AudioDecoder {

    fun decode(file: File, maxSeconds: Float = 45f): FloatArray {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            var track = -1
            for (i in 0 until extractor.trackCount) {
                if (extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    track = i
                    break
                }
            }
            if (track < 0) throw IOException("Dosyada ses yok")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val samples = ArrayList<FloatArray>()
            var total = 0
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            try {
                while (true) {
                    if (!inputDone) {
                        val inIdx = codec.dequeueInputBuffer(10_000)
                        if (inIdx >= 0) {
                            val buf = codec.getInputBuffer(inIdx)!!
                            val size = extractor.readSampleData(buf, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                    if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        rate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    } else if (outIdx >= 0) {
                        val buf = codec.getOutputBuffer(outIdx)!!
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        val sb = buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val frames = sb.remaining() / channels.coerceAtLeast(1)
                        val mono = FloatArray(frames)
                        for (f in 0 until frames) {
                            var s = 0f
                            for (c in 0 until channels) s += sb.get()
                            mono[f] = s / channels / 32768f
                        }
                        samples += mono
                        total += frames
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                        if (total > maxSeconds * rate) break
                    }
                }
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
            val all = FloatArray(total)
            var p = 0
            for (a in samples) { a.copyInto(all, p); p += a.size }
            if (all.isEmpty()) throw IOException("Ses çözülemedi")
            return Soundtrack.resample(all, rate)
        } finally {
            extractor.release()
        }
    }
}
