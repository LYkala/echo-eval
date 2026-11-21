package com.k2fsa.sherpa.onnx

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

private const val TAG = "voice-assistant"
private const val REQUEST_RECORD_AUDIO_PERMISSION = 200

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private val permissions = arrayOf(Manifest.permission.RECORD_AUDIO)

    private lateinit var recognizer: OnlineRecognizer
    private var audioRecord: AudioRecord? = null
    private lateinit var recordButton: Button
    private lateinit var demoButton: Button
    private lateinit var fileButton: Button
    private lateinit var asrTextView: TextView
    private lateinit var llmTextView: TextView
    private lateinit var statusTextView: TextView
    private var recordingThread: Thread? = null
    private var tts: TextToSpeech? = null
    @Volatile
    private var ttsReady = false
    private val ttsInitLatch = CountDownLatch(1)

    private val audioSource = MediaRecorder.AudioSource.MIC
    private val sampleRateInHz = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    @Volatile
    private var isRecording = false

    private var liveAsrText = ""
    private var finalAsrText = ""

    private fun voiceLabDir(): File {
        val base = getExternalFilesDir(null) ?: filesDir
        return File(base, "voice_lab").apply { mkdirs() }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION)
        }

        asrTextView = findViewById(R.id.asr_text)
        llmTextView = findViewById(R.id.llm_text)
        statusTextView = findViewById(R.id.status_text)
        recordButton = findViewById(R.id.record_button)
        demoButton = findViewById(R.id.demo_button)
        fileButton = findViewById(R.id.file_button)

        asrTextView.movementMethod = ScrollingMovementMethod()
        llmTextView.movementMethod = ScrollingMovementMethod()

        tts = TextToSpeech(this, this, "com.google.android.tts")

        setStatus("正在加载 Sherpa 模型…")
        thread(name = "init-model") {
            try {
                initModel()
                runOnUiThread {
                    setStatus("就绪。说话 / 问天气 / 读文件。")
                    recordButton.isEnabled = true
                    demoButton.isEnabled = true
                    fileButton.isEnabled = true
                }
            } catch (ex: Exception) {
                Log.e(TAG, "initModel failed", ex)
                runOnUiThread {
                    setStatus("模型加载失败: ${ex.message}")
                    Toast.makeText(this, ex.message ?: "模型加载失败", Toast.LENGTH_LONG).show()
                }
            }
        }

        recordButton.isEnabled = false
        demoButton.isEnabled = false
        fileButton.isEnabled = false
        recordButton.setOnClickListener { toggleRecording() }
        demoButton.setOnClickListener { askDemoWeather() }
        fileButton.setOnClickListener { recognizeFromFile() }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.SIMPLIFIED_CHINESE
            ttsReady = true
        } else {
            Log.e(TAG, "TTS init failed: $status")
        }
        ttsInitLatch.countDown()
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            if (grantResults.isNotEmpty() &&
                grantResults[0] != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(this, "需要麦克风权限", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun toggleRecording() {
        if (!isRecording) {
            if (!initMicrophone()) {
                Toast.makeText(this, "无法打开麦克风", Toast.LENGTH_SHORT).show()
                return
            }
            liveAsrText = ""
            finalAsrText = ""
            asrTextView.text = ""
            llmTextView.text = ""
            audioRecord!!.startRecording()
            recordButton.setText(R.string.stop)
            isRecording = true
            setStatus("正在聆听…(等待 VirtualMic 注入)")
            recordingThread = thread(name = "asr-record") { processSamples() }
        } else {
            isRecording = false
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            recordButton.setText(R.string.start)
            setStatus("识别完成，正在请求 Kimi…")
            val text = finalAsrText.ifBlank { liveAsrText }.trim()
            if (text.isNotEmpty()) {
                asrTextView.text = text
                requestKimiAndSpeak(text)
            } else {
                setStatus("未识别到有效语音，请重试。")
            }
        }
    }

    private fun askDemoWeather() {
        if (isRecording) {
            Toast.makeText(this, "请先停止录音", Toast.LENGTH_SHORT).show()
            return
        }
        val text = "今天天气怎么样"
        asrTextView.text = text
        llmTextView.text = ""
        setStatus("示例：正在请求 Kimi…")
        requestKimiAndSpeak(text)
    }

    private fun recognizeFromFile() {
        if (isRecording) {
            Toast.makeText(this, "请先停止录音", Toast.LENGTH_SHORT).show()
            return
        }
        val candidates = listOf(
            File(voiceLabDir(), "input.wav"),
            File(voiceLabDir(), "weather_question_16k.wav"),
            File("/data/local/tmp/voice_lab/input.wav"),
            File("/sdcard/voice_lab/input.wav"),
        )
        val wav = candidates.firstOrNull { f ->
            try {
                f.isFile && f.length() > 44L && f.canRead() && f.inputStream().use { it.read() >= 0 }
            } catch (_: Exception) {
                false
            }
        }
        if (wav == null) {
            setStatus("未找到 /sdcard/voice_lab/input.wav")
            Toast.makeText(this, "请先 adb push 到 /sdcard/voice_lab/input.wav", Toast.LENGTH_LONG).show()
            return
        }

        recordButton.isEnabled = false
        demoButton.isEnabled = false
        fileButton.isEnabled = false
        setStatus("正在识别文件: ${wav.name}…")
        llmTextView.text = ""
        asrTextView.text = ""

        thread(name = "asr-file") {
            try {
                val text = recognizeWavFile(wav).trim()
                runOnUiThread {
                    if (text.isEmpty()) {
                        setStatus("文件未识别到有效语音")
                        recordButton.isEnabled = true
                        demoButton.isEnabled = true
                        fileButton.isEnabled = true
                    } else {
                        asrTextView.text = text
                        setStatus("文件识别完成，正在请求 Kimi…")
                        requestKimiAndSpeak(text)
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "recognizeFromFile failed", ex)
                runOnUiThread {
                    setStatus("文件识别失败: ${ex.message}")
                    recordButton.isEnabled = true
                    demoButton.isEnabled = true
                    fileButton.isEnabled = true
                }
            }
        }
    }

    private fun requestKimiAndSpeak(userText: String) {
        recordButton.isEnabled = false
        demoButton.isEnabled = false
        fileButton.isEnabled = false
        thread(name = "kimi-request") {
            try {
                val reply = KimiClient.chat(userText)
                writeTextArtifact("reply.txt", reply)
                runOnUiThread {
                    llmTextView.text = reply
                    setStatus("回复已生成，正在播报…")
                    recordButton.isEnabled = true
                    demoButton.isEnabled = true
                    fileButton.isEnabled = true
                }
                speak(reply)
            } catch (ex: Exception) {
                Log.e(TAG, "Kimi failed", ex)
                runOnUiThread {
                    llmTextView.text = ""
                    setStatus("Kimi 失败: ${ex.message}")
                    Toast.makeText(this, ex.message ?: "Kimi 失败", Toast.LENGTH_LONG).show()
                    recordButton.isEnabled = true
                    demoButton.isEnabled = true
                    fileButton.isEnabled = true
                }
            }
        }
    }

    private fun speak(text: String) {
        val voiceDir = voiceLabDir()
        val wavFile = File(voiceDir, "output.wav")
        val mp3File = File(voiceDir, "output.mp3")

        // Prefer Google TTS file synth when voices are available.
        var saved: File? = null
        if (!ttsReady) {
            ttsInitLatch.await(10, TimeUnit.SECONDS)
        }
        if (ttsReady) {
            saved = tryGoogleSynth(text, wavFile)
        }

        if (saved == null) {
            runOnUiThread { setStatus("Google TTS 不可用，改用 HTTP TTS…") }
            wavFile.delete()
            if (HttpTtsClient.synthesizeToFile(text, wavFile)) {
                saved = wavFile
            }
        }
        if (saved == null) {
            runOnUiThread { setStatus("HTTP TTS unavailable, local WAV...") }
            wavFile.delete()
            if (LocalWavTts.synthesizeToFile(text, wavFile)) {
                saved = wavFile
            }
        }

        if (saved != null) {
            publishArtifact(saved)
            runOnUiThread {
                setStatus("就绪。回复音频已保存 (${saved.name})")
            }
            playFile(saved)
            return
        }

        // Last resort: try engine speak without saving.
        runOnUiThread {
            setStatus("TTS 保存失败，尝试引擎播报…")
            if (ttsReady) {
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voice-assistant-reply-play")
            }
        }
    }

    private fun tryGoogleSynth(text: String, outFile: File): File? {
        val utteranceId = "voice-assistant-reply"
        val latch = CountDownLatch(1)
        var synthOk = false
        outFile.delete()

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(id: String?) {
                if (id == utteranceId) {
                    synthOk = true
                    latch.countDown()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId) {
                    latch.countDown()
                }
            }
        })

        val synthResult = tts?.synthesizeToFile(text, Bundle(), outFile, utteranceId)
        if (synthResult == TextToSpeech.SUCCESS) {
            latch.await(20, TimeUnit.SECONDS)
        }
        return if (synthOk && outFile.isFile && outFile.length() > 0L) {
            Log.i(TAG, "TTS wav saved: ${outFile.absolutePath}")
            outFile
        } else {
            Log.w(TAG, "TTS synthesizeToFile failed or empty: $synthResult")
            null
        }
    }

    private fun playFile(file: File) {
        try {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { it.release() }
                setOnErrorListener { mp, _, _ ->
                    mp.release()
                    true
                }
                prepare()
                start()
            }
        } catch (ex: Exception) {
            Log.w(TAG, "MediaPlayer failed: ${ex.message}")
        }
    }

    private fun publishArtifact(file: File) {
        try {
            val dir = voiceLabDir()
            val target = if (file.extension.equals("mp3", ignoreCase = true)) {
                File(dir, "output.mp3")
            } else {
                File(dir, "output.wav")
            }
            if (file.canonicalPath != target.canonicalPath) {
                file.copyTo(target, overwrite = true)
            }
            // Also mirror to /data/local/tmp for easy adb pull without scoped-storage pain.
            try {
                val tmp = File("/data/local/tmp/voice_lab").apply { mkdirs() }
                file.copyTo(File(tmp, file.name), overwrite = true)
                File(dir, "output.wav").takeIf { it.isFile }?.copyTo(File(tmp, "output.wav"), overwrite = true)
                File(dir, "reply.txt").takeIf { it.isFile }?.copyTo(File(tmp, "reply.txt"), overwrite = true)
            } catch (ex: Exception) {
                Log.w(TAG, "mirror to /data/local/tmp/voice_lab failed", ex)
            }
        } catch (ex: Exception) {
            Log.w(TAG, "publishArtifact failed", ex)
        }
    }

    private fun writeTextArtifact(name: String, text: String) {
        try {
            val dir = voiceLabDir()
            File(dir, name).writeText(text, Charsets.UTF_8)
            try {
                val tmp = File("/data/local/tmp/voice_lab").apply { mkdirs() }
                File(tmp, name).writeText(text, Charsets.UTF_8)
            } catch (ex: Exception) {
                Log.w(TAG, "tmp writeTextArtifact failed", ex)
            }
        } catch (ex: Exception) {
            Log.w(TAG, "writeTextArtifact failed", ex)
        }
    }

    private fun setStatus(text: String) {
        runOnUiThread { statusTextView.text = text }
    }

    private fun processSamples() {
        val stream = recognizer.createStream()
        val interval = 0.1
        val bufferSize = (interval * sampleRateInHz).toInt()
        val buffer = ShortArray(bufferSize)
        val voiceDir = voiceLabDir()
        VirtualMicBridge.clearInject(voiceDir)

        // File inject is the reliable path on this aarch64 qemu (redir/reverse broken).
        // TCP accept is best-effort only.
        var bridgeIn: java.io.InputStream? = null
        var injectFile: File? = null
        VirtualMicBridge.startServer()
        val acceptThread = thread(name = "vmic-accept") {
            bridgeIn = VirtualMicBridge.acceptClient()
            if (bridgeIn != null) {
                runOnUiThread { setStatus("正在聆听…(VirtualMic TCP)") }
            }
        }
        val injectThread = thread(name = "vmic-file") {
            injectFile = VirtualMicBridge.waitInjectFile(voiceDir, 22000L)
            if (injectFile != null) {
                runOnUiThread { setStatus("正在聆听…(VirtualMic 文件注入)") }
            }
        }

        val waitDeadline = System.currentTimeMillis() + 22000L
        while (isRecording && System.currentTimeMillis() < waitDeadline) {
            if (injectFile != null || bridgeIn != null) break
            try {
                Thread.sleep(40)
            } catch (_: InterruptedException) {
                break
            }
        }
        try {
            acceptThread.join(200)
        } catch (_: Exception) {
        }
        try {
            injectThread.join(200)
        } catch (_: Exception) {
        }

        try {
            val pcmFile = injectFile
            if (pcmFile != null) {
                Log.i(TAG, "consuming VirtualMic inject ${pcmFile.length()} bytes")
                val pcm = VirtualMicBridge.readPcm16File(pcmFile)
                val frame = bufferSize.coerceAtLeast(160)
                var offset = 0
                while (offset < pcm.size) {
                    val end = (offset + frame).coerceAtMost(pcm.size)
                    acceptPcm(stream, pcm.copyOfRange(offset, end), end - offset)
                    offset = end
                }
                val pad = ShortArray((0.8 * sampleRateInHz).toInt())
                acceptPcm(stream, pad, pad.size)
                VirtualMicBridge.clearInject(voiceDir)
                while (isRecording) {
                    try {
                        Thread.sleep(40)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
                return
            }

            while (isRecording) {
                val input = bridgeIn
                val ret = if (input != null) {
                    val n = VirtualMicBridge.readShorts(input, buffer, bufferSize)
                    if (n <= 0) {
                        Thread.sleep(20)
                        continue
                    } else {
                        n
                    }
                } else {
                    audioRecord?.read(buffer, 0, buffer.size) ?: break
                }
                if (ret <= 0) continue
                acceptPcm(stream, buffer, ret)
            }
        } finally {
            VirtualMicBridge.stop()
            stream.release()
        }
    }

    private fun recognizeWavFile(wav: File): String {
        val pcm = readWavPcm16Mono(wav)
        if (pcm.isEmpty()) return ""

        val stream = recognizer.createStream()
        try {
            val frame = (0.1 * sampleRateInHz).toInt().coerceAtLeast(160)
            var offset = 0
            while (offset < pcm.size) {
                val end = (offset + frame).coerceAtMost(pcm.size)
                val chunk = pcm.copyOfRange(offset, end)
                acceptPcm(stream, chunk, chunk.size)
                offset = end
            }
            // Flush endpoint with silence padding.
            val pad = ShortArray((0.8 * sampleRateInHz).toInt())
            acceptPcm(stream, pad, pad.size)
            while (recognizer.isReady(stream)) {
                recognizer.decode(stream)
            }
            return recognizer.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    private fun acceptPcm(stream: OnlineStream, buffer: ShortArray, size: Int) {
        val samples = FloatArray(size) { buffer[it] / 32768.0f }
        stream.acceptWaveform(samples, sampleRate = sampleRateInHz)
        while (recognizer.isReady(stream)) {
            recognizer.decode(stream)
        }

        val isEndpoint = recognizer.isEndpoint(stream)
        var text = recognizer.getResult(stream).text

        if (isEndpoint && recognizer.config.modelConfig.paraformer.encoder.isNotBlank()) {
            val tailPaddings = FloatArray((0.8 * sampleRateInHz).toInt())
            stream.acceptWaveform(tailPaddings, sampleRate = sampleRateInHz)
            while (recognizer.isReady(stream)) {
                recognizer.decode(stream)
            }
            text = recognizer.getResult(stream).text
        }

        if (text.isNotBlank()) {
            liveAsrText = text
        }

        if (isEndpoint) {
            recognizer.reset(stream)
            if (text.isNotBlank()) {
                finalAsrText = if (finalAsrText.isBlank()) text else "$finalAsrText $text"
                liveAsrText = finalAsrText
            }
        }

        val display = liveAsrText
        runOnUiThread {
            if (display.isNotBlank()) {
                asrTextView.text = display
            }
        }
    }

    private fun readWavPcm16Mono(file: File): ShortArray {
        RandomAccessFile(file, "r").use { raf ->
            val riff = ByteArray(4)
            raf.readFully(riff)
            require(String(riff) == "RIFF") { "not RIFF" }
            raf.skipBytes(4)
            val wave = ByteArray(4)
            raf.readFully(wave)
            require(String(wave) == "WAVE") { "not WAVE" }

            var audioFormat = 1
            var channels = 1
            var sampleRate = sampleRateInHz
            var bitsPerSample = 16
            var dataSize = 0
            var dataOffset = -1L

            while (raf.filePointer < raf.length()) {
                val chunkId = ByteArray(4)
                if (raf.read(chunkId) < 4) break
                val chunkSizeBuf = ByteArray(4)
                raf.readFully(chunkSizeBuf)
                val chunkSize = ByteBuffer.wrap(chunkSizeBuf).order(ByteOrder.LITTLE_ENDIAN).int
                val id = String(chunkId)
                val chunkStart = raf.filePointer
                when (id) {
                    "fmt " -> {
                        val fmt = ByteArray(chunkSize)
                        raf.readFully(fmt)
                        val bb = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                        audioFormat = bb.short.toInt() and 0xffff
                        channels = bb.short.toInt() and 0xffff
                        sampleRate = bb.int
                        bb.int // byte rate
                        bb.short // block align
                        bitsPerSample = bb.short.toInt() and 0xffff
                    }
                    "data" -> {
                        dataOffset = raf.filePointer
                        dataSize = chunkSize
                        raf.seek(chunkStart + chunkSize)
                    }
                    else -> raf.seek(chunkStart + chunkSize)
                }
            }

            require(audioFormat == 1) { "only PCM wav supported, got $audioFormat" }
            require(bitsPerSample == 16) { "only 16-bit pcm supported" }
            require(dataOffset >= 0) { "missing data chunk" }

            raf.seek(dataOffset)
            val bytes = ByteArray(dataSize)
            raf.readFully(bytes)
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val totalShorts = dataSize / 2
            val raw = ShortArray(totalShorts)
            for (i in 0 until totalShorts) {
                raw[i] = bb.short
            }

            val mono = if (channels == 1) {
                raw
            } else {
                val frames = totalShorts / channels
                ShortArray(frames) { i ->
                    var sum = 0
                    for (c in 0 until channels) {
                        sum += raw[i * channels + c].toInt()
                    }
                    (sum / channels).toShort()
                }
            }

            if (sampleRate == sampleRateInHz) {
                return mono
            }
            // Naive linear resample to 16 kHz.
            val outLen = ((mono.size.toLong() * sampleRateInHz) / sampleRate).toInt().coerceAtLeast(1)
            val out = ShortArray(outLen)
            for (i in 0 until outLen) {
                val src = i.toDouble() * sampleRate / sampleRateInHz
                val i0 = src.toInt().coerceIn(0, mono.size - 1)
                val i1 = (i0 + 1).coerceIn(0, mono.size - 1)
                val frac = src - i0
                out[i] = ((mono[i0] * (1 - frac) + mono[i1] * frac)).toInt().toShort()
            }
            return out
        }
    }

    private fun initMicrophone(): Boolean {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION)
            return false
        }

        val numBytes = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)
        audioRecord = AudioRecord(
            audioSource,
            sampleRateInHz,
            channelConfig,
            audioFormat,
            numBytes * 2,
        )
        return audioRecord?.state == AudioRecord.STATE_INITIALIZED
    }

    private fun initModel() {
        val config = OnlineRecognizerConfig(
            featConfig = getFeatureConfig(sampleRate = sampleRateInHz, featureDim = 80),
            modelConfig = getModelConfig(type = 8)!!,
            endpointConfig = getEndpointConfig(),
            enableEndpoint = true,
        )
        recognizer = OnlineRecognizer(assetManager = application.assets, config = config)
    }
}
