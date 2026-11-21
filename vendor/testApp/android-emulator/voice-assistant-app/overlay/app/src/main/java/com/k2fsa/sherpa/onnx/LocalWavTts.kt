package com.k2fsa.sherpa.onnx

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

object LocalWavTts {
    fun synthesizeToFile(text: String, outFile: File, sampleRate: Int = 16000): Boolean {
        val trimmed = text.trim().ifEmpty { "ok" }.take(160)
        val samples = ArrayList<Short>(sampleRate)
        repeat((0.05 * sampleRate).toInt()) { samples.add(0) }
        for (ch in trimmed) {
            val freq = 220.0 + (ch.code % 40) * 12.0
            val n = (0.07 * sampleRate).toInt()
            for (i in 0 until n) {
                val env = 0.35 * sin(PI * i / (n - 1).coerceAtLeast(1))
                val v = (12000.0 * env * sin(2.0 * PI * freq * i / sampleRate)).toInt()
                samples.add(v.coerceIn(-32767, 32767).toShort())
            }
            repeat((0.025 * sampleRate).toInt()) { samples.add(0) }
        }
        repeat((0.15 * sampleRate).toInt()) { samples.add(0) }

        val dataSize = samples.size * 2
        val out = ByteArrayOutputStream(44 + dataSize)
        fun writeStr(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun writeInt(v: Int) {
            out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array())
        }
        fun writeShort(v: Int) {
            out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v.toShort()).array())
        }
        writeStr("RIFF")
        writeInt(36 + dataSize)
        writeStr("WAVE")
        writeStr("fmt ")
        writeInt(16)
        writeShort(1)
        writeShort(1)
        writeInt(sampleRate)
        writeInt(sampleRate * 2)
        writeShort(2)
        writeShort(16)
        writeStr("data")
        writeInt(dataSize)
        val pcm = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) {
            pcm.putShort(s)
        }
        out.write(pcm.array())
        FileOutputStream(outFile).use { it.write(out.toByteArray()) }
        return outFile.isFile && outFile.length() > 64L
    }
}
