package com.k2fsa.sherpa.onnx

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object KimiClient {
    private const val DEFAULT_API_KEY = "sk-pPyuXbIpshuMg5m599PbGIssllrbvVFP7KIfhsxwjxqVQ2Sl"
    private const val API_HOST = "api.moonshot.cn"
    private const val API_IP = "8.147.223.37"
    private const val DEFAULT_BASE_URL = "https://" + API_IP + "/v1"
    private const val DEFAULT_MODEL = "kimi-k2.5"
    private const val DEFAULT_SYSTEM_PROMPT =
        "你是智能语音助手。用户可能询问天气、出行、日常问题等。" +
            "请根据语音识别文本，用简洁、自然的中文回复。" +
            "若用户问天气且你无法获取实时数据，可说明这一点并给出合理建议。" +
            "直接输出回复内容，不要加前缀说明。"

    @Volatile private var sslReady = false

    fun chat(userText: String, systemPrompt: String = DEFAULT_SYSTEM_PROMPT): String {
        val trimmed = userText.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("识别文本为空")
        }

        val apiKey = System.getenv("KIMI_API_KEY")?.trim().orEmpty().ifEmpty { DEFAULT_API_KEY }
        val baseUrl = System.getenv("KIMI_BASE_URL")?.trim().orEmpty().ifEmpty { DEFAULT_BASE_URL }
        val model = System.getenv("KIMI_MODEL")?.trim().orEmpty().ifEmpty { DEFAULT_MODEL }

        val body = JSONObject()
            .put("model", model)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt))
                    .put(JSONObject().put("role", "user").put("content", trimmed)),
            )
            .put("thinking", JSONObject().put("type", "disabled"))
            .put("max_tokens", 4096)

        val primary = request("${baseUrl}/chat/completions", apiKey, body)
        if (primary.code != 200) {
            throw RuntimeException("Kimi 请求失败 (${primary.code}): ${primary.body.take(800)}")
        }
        return parseBody(primary.body)
    }

    private data class HttpResult(val code: Int, val body: String)

    private fun ensureSsl() {
        if (sslReady) return
        synchronized(this) {
            if (sslReady) return
            val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, trustAll, SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(ctx.socketFactory)
            HttpsURLConnection.setDefaultHostnameVerifier(HostnameVerifier { _, _ -> true })
            sslReady = true
        }
    }

    private fun request(url: String, apiKey: String, body: JSONObject): HttpResult {
        ensureSsl()
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Host", API_HOST)
            setRequestProperty("Authorization", "Bearer ${apiKey}")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (this is HttpsURLConnection) {
                hostnameVerifier = HostnameVerifier { _, _ -> true }
            }
        }

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(body.toString())
        }

        val stream = if (conn.responseCode >= 400) conn.errorStream else conn.inputStream
        val text = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            reader.readText()
        }
        return HttpResult(conn.responseCode, text)
    }

    private fun parseBody(raw: String): String {
        val root = JSONObject(raw)
        val choices = root.optJSONArray("choices") ?: throw RuntimeException("Kimi 响应缺少 choices")
        if (choices.length() == 0) {
            throw RuntimeException("Kimi 响应 choices 为空")
        }
        val content = choices.getJSONObject(0)
            .optJSONObject("message")
            ?.optString("content")
            .orEmpty()
            .trim()
        if (content.isEmpty()) {
            throw RuntimeException("Kimi 回复为空")
        }
        return content
    }
}
