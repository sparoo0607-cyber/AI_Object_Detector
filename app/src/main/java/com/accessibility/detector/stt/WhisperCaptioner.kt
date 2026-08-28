package com.accessibility.detector.stt

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.concurrent.thread

/**
 * LISTEN / Live captions — REAL offline speech-to-text via OpenAI's
 * Whisper (tiny, multilingual), running fully on-device through a
 * plain TFLite Interpreter (see WhisperUtil.kt). This is what closes
 * the one gap disclosed everywhere else in this app: live captions no
 * longer need a data connection on devices where this model runs.
 *
 * The multilingual model auto-detects the spoken language from the
 * audio itself — it was not fine-tuned per-language, so no language
 * has to be selected up front; English, Hindi and Telugu all come
 * from the same model and the same code path.
 *
 * Kept alongside SttHelper (Android SpeechRecognizer) as a fallback:
 * if Whisper's model files fail to load on a very low-end device,
 * ListenActivity falls back to the cloud recognizer automatically
 * rather than going silent.
 */
class WhisperCaptioner(private val context: Context) {

    interface Listener {
        fun onPartialTranscript(text: String)
        fun onReady()
        fun onError(message: String)
    }
    var listener: Listener? = null

    private val whisperUtil = WhisperUtil()
    private var interpreter: Interpreter? = null
    @Volatile private var ready = false
    @Volatile private var running = false

    private var audioRecord: AudioRecord? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val fullWindowSamples = WhisperUtil.SAMPLE_RATE * WhisperUtil.CHUNK_SIZE // 30s @ 16kHz
    private val inferenceEveryMs = 4000L // caption refresh cadence

    fun initialize() {
        thread(name = "WhisperInit") {
            try {
                val modelFile = ensureCopiedToFiles("whisper-tiny.tflite")
                val vocabFile = ensureCopiedToFiles("filters_vocab_multilingual.bin")

                val fis = java.io.FileInputStream(modelFile)
                val buffer = fis.channel.map(FileChannel.MapMode.READ_ONLY, 0, modelFile.length())
                interpreter = Interpreter(buffer, Interpreter.Options().apply {
                    setNumThreads(Runtime.getRuntime().availableProcessors().coerceAtMost(4))
                })

                val ok = whisperUtil.loadFiltersAndVocab(true, vocabFile.absolutePath)
                ready = ok
                mainHandler.post { if (ok) listener?.onReady() else listener?.onError("Whisper vocab load failed") }
            } catch (e: Exception) {
                Log.e(TAG, "Whisper init failed", e)
                ready = false
                mainHandler.post { listener?.onError("Whisper model unavailable: ${e.message}") }
            }
        }
    }

    private fun ensureCopiedToFiles(assetName: String): File {
        val out = File(context.filesDir, assetName)
        if (out.exists() && out.length() > 0) return out
        context.assets.open(assetName).use { input: InputStream ->
            FileOutputStream(out).use { output -> input.copyTo(output, 1 shl 16) }
        }
        return out
    }

    fun isReady() = ready

    fun start() {
        if (!ready || running) return
        val sampleRate = WhisperUtil.SAMPLE_RATE
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC, sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT,
                (minBuf * 4).coerceAtLeast(8192)
            )
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord init failed", e)
            listener?.onError("Microphone unavailable for captions")
            return
        }
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            listener?.onError("Microphone unavailable for captions")
            return
        }
        audioRecord?.startRecording()
        running = true
        thread(name = "WhisperCaptionLoop") { captureLoop() }
    }

    private fun captureLoop() {
        // Rolling ring buffer of the last 30s of audio, matching the
        // model's fixed input window; we run inference on it every
        // few seconds so captions keep updating.
        val ring = FloatArray(fullWindowSamples)
        var writePos = 0
        var filled = 0
        val chunk = FloatArray(4096)
        var lastInference = 0L

        while (running) {
            val record = audioRecord ?: break
            val n = record.read(chunk, 0, chunk.size, AudioRecord.READ_BLOCKING)
            if (n > 0) {
                for (i in 0 until n) {
                    ring[writePos] = chunk[i]
                    writePos = (writePos + 1) % ring.size
                }
                filled = (filled + n).coerceAtMost(ring.size)
            }

            val now = System.currentTimeMillis()
            if (running && filled > WhisperUtil.SAMPLE_RATE * 2 && now - lastInference >= inferenceEveryMs) {
                lastInference = now
                val ordered = FloatArray(ring.size)
                // unwrap ring buffer into chronological order, zero-padded at the front if not full yet
                val start = if (filled < ring.size) 0 else writePos
                for (i in 0 until filled) ordered[ordered.size - filled + i] = ring[(start + i) % ring.size]
                runInference(ordered)
            }
        }
    }

    private fun runInference(samples: FloatArray) {
        val interp = interpreter ?: return
        try {
            val melData = whisperUtil.melSpectrogram(samples, samples.size)

            val inputBuf = ByteBuffer.allocateDirect(melData.size * 4).order(ByteOrder.nativeOrder())
            for (v in melData) inputBuf.putFloat(v)
            inputBuf.rewind()

            val outputTensor = interp.getOutputTensor(0)
            val outCount = outputTensor.shape().fold(1) { a, b -> a * b }
            val outputBuf = ByteBuffer.allocateDirect(outCount * 4).order(ByteOrder.nativeOrder())

            interp.run(inputBuf, outputBuf)
            outputBuf.rewind()

            val sb = StringBuilder()
            for (i in 0 until outCount) {
                val token = outputBuf.int
                if (token == whisperUtil.tokenEOT) break
                if (token < whisperUtil.tokenEOT) {
                    sb.append(whisperUtil.wordForToken(token) ?: "")
                }
            }
            val text = sb.toString().trim()
            if (text.isNotEmpty()) mainHandler.post { listener?.onPartialTranscript(text) }
        } catch (e: Exception) {
            Log.w(TAG, "Whisper inference error", e)
        }
    }

    fun stop() {
        running = false
        try { audioRecord?.stop(); audioRecord?.release() } catch (e: Exception) { /* ignore */ }
        audioRecord = null
    }

    fun close() {
        stop()
        interpreter?.close()
        interpreter = null
    }

    companion object { private const val TAG = "WhisperCaptioner" }
}
