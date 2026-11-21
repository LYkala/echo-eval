package com.k2fsa.sherpa.onnx

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/** Always-available lab fallback: deterministic PCM wav from text. */
object OfflineToneTts {
    fun synthesizeToFile(text: String, outFile: File, sampleRate: Int = 16000): Boolean {
        val chars = text.trim().ifEmpty { "ok" }.take(80)
        val samples = ArrayList<Short>()
        repeat((0.05 * sampleRate).toInt()) { samples.add(0) }
        for (ch in chars) {
            val freq = 220.0 + (ch.code % 40) * 12.0
            val n = (0.07 * sampleRate).toInt()
            for (i in 0 until n) {
                val env = 0.35 * sin(PI * i / (n - 1).coerceAtLeast(1))
                val v = (10000 * env * sin(2.0 * PI * freq * i / sampleRate)).toInt()
                samples.add(v.coerceIn(-32767, 32767).toShort())
            }
            repeat((0.025 * sampleRate).toInt()) { samples.add(0) }
        }
        repeat((0.1 * sampleRate).toInt()) { samples.add(0) }

        val dataSize = samples.size * 2
        val hdr = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        hdr.put("RIFF".toByteArray())
        hdr.putInt(36 + dataSize)
        hdr.put("WAVE".toByteArray())
        hdr.put("fmt ".toByteArray())
        hdr.putInt(16)
        hdr.putShort(1)
        hdr.putShort(1)
        hdr.putInt(sampleRate)
        hdr.putInt(sampleRate * 2)
        hdr.putShort(2)
        hdr.putShort(16)
        hdr.put("data".toByteArray())
        hdr.putInt(dataSize)

        FileOutputStream(outFile).use { fos ->
            fos.write(hdr.array())
            val buf = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            for (s in samples) buf.putShort(s)
            fos.write(buf.array())
        }
        return outFile.isFile && outFile.length() > 64L
    }
}
