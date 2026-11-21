package com.k2fsa.sherpa.onnx

import android.util.Log
import java.io.File
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference

/**
 * VirtualMic injection for aarch64 emulator where QEMU OSS mic / redir / adb reverse
 * do not deliver audio into the guest.
 *
 * Preferred path (reliable): host captures Pulse VirtualMic.monitor → adb push
 *   voice_lab/vmic_inject.pcm + voice_lab/vmic_inject.ready
 *
 * Optional path: TCP server on :19200 (needs working emulator redir host→guest).
 */
object VirtualMicBridge {
    private const val TAG = "voice-assistant-vmic"
    const val DEFAULT_PORT = 19200
    const val INJECT_PCM = "vmic_inject.pcm"
    const val INJECT_READY = "vmic_inject.ready"

    private val serverRef = AtomicReference<ServerSocket?>(null)
    private val clientRef = AtomicReference<Socket?>(null)

    fun injectPcmFile(dir: File): File = File(dir, INJECT_PCM)

    fun injectReadyFile(dir: File): File = File(dir, INJECT_READY)

    fun clearInject(dir: File) {
        try {
            injectPcmFile(dir).delete()
        } catch (_: Exception) {
        }
        try {
            injectReadyFile(dir).delete()
        } catch (_: Exception) {
        }
    }

    /** Wait until host pushed PCM + ready marker. */
    fun waitInjectFile(dir: File, timeoutMs: Long = 20000L): File? {
        val pcm = injectPcmFile(dir)
        val ready = injectReadyFile(dir)
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (ready.isFile && pcm.isFile && pcm.length() >= 3200L) {
                Log.i(TAG, "VirtualMic inject file ready bytes=${pcm.length()}")
                return pcm
            }
            try {
                Thread.sleep(40)
            } catch (_: InterruptedException) {
                break
            }
        }
        return null
    }

    fun readPcm16File(file: File): ShortArray {
        val bytes = file.readBytes()
        val n = bytes.size / 2
        val out = ShortArray(n)
        var i = 0
        while (i < n) {
            val lo = bytes[i * 2].toInt() and 0xff
            val hi = bytes[i * 2 + 1].toInt()
            out[i] = ((hi shl 8) or lo).toShort()
            i++
        }
        return out
    }

    fun startServer(port: Int = DEFAULT_PORT): Boolean {
        stopServerOnly()
        return try {
            val ss = ServerSocket()
            ss.reuseAddress = true
            ss.bind(InetSocketAddress("0.0.0.0", port))
            ss.soTimeout = 8000
            serverRef.set(ss)
            Log.i(TAG, "listening 0.0.0.0:$port for VirtualMic stream")
            true
        } catch (ex: Exception) {
            Log.e(TAG, "startServer failed: ${ex.message}")
            false
        }
    }

    fun acceptClient(): InputStream? {
        val ss = serverRef.get() ?: return null
        return try {
            val sock = ss.accept()
            sock.tcpNoDelay = true
            sock.soTimeout = 250
            clientRef.set(sock)
            Log.i(TAG, "VirtualMic client connected ${sock.remoteSocketAddress}")
            sock.getInputStream()
        } catch (ex: Exception) {
            Log.w(TAG, "accept failed/timeout: ${ex.message}")
            null
        }
    }

    fun stop() {
        try {
            clientRef.getAndSet(null)?.close()
        } catch (_: Exception) {
        }
        stopServerOnly()
    }

    private fun stopServerOnly() {
        try {
            serverRef.getAndSet(null)?.close()
        } catch (_: Exception) {
        }
    }

    fun readShorts(input: InputStream, out: ShortArray, max: Int): Int {
        val bytes = ByteArray(max * 2)
        var got = 0
        while (got < bytes.size) {
            val n = try {
                input.read(bytes, got, bytes.size - got)
            } catch (_: Exception) {
                break
            }
            if (n <= 0) break
            got += n
            if (got >= 2 && got % 2 == 0 && got >= (max * 2) / 4) break
        }
        val samples = got / 2
        var i = 0
        while (i < samples) {
            val lo = bytes[i * 2].toInt() and 0xff
            val hi = bytes[i * 2 + 1].toInt()
            out[i] = ((hi shl 8) or lo).toShort()
            i++
        }
        return samples
    }
}
