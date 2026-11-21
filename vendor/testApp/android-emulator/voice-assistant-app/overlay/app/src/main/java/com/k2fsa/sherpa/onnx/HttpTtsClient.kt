package com.k2fsa.sherpa.onnx

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.net.URLEncoder

object HttpTtsClient {
    private const val TAG = "voice-assistant-http-tts"
    private val ENDPOINTS = listOf(
        "http://10.0.2.2:19099/tts",
    )

    fun synthesizeToFile(text: String, outFile: File): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val chunk = if (trimmed.length > 400) trimmed.substring(0, 400) else trimmed
        val q = URLEncoder.encode(chunk, "UTF-8")
        for (base in ENDPOINTS) {
            val url = base + "?text=" + q + "&fmt=wav"
            try {
                if (download(url, outFile)) {
                    Log.i(TAG, "HTTP TTS saved " + outFile.length() + " bytes via " + base)
                    return true
                }
            } catch (ex: Exception) {
                Log.w(TAG, "HTTP TTS failed for " + base + ": " + ex.message)
            }
        }
        return false
    }

    private fun download(url: String, outFile: File): Boolean {
        val conn = (URL(url).openConnection(Proxy.NO_PROXY) as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "*/*")
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            Log.w(TAG, "HTTP TTS status=" + code + " url=" + url)
            return false
        }
        conn.inputStream.use { input ->
            FileOutputStream(outFile).use { output -> input.copyTo(output) }
        }
        return outFile.isFile && outFile.length() > 64L
    }
}
