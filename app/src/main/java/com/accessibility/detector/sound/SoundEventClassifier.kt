package com.accessibility.detector.sound

import android.content.Context
import android.media.AudioRecord
import android.util.Log
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifierResult
import com.google.mediapipe.tasks.audio.core.RunningMode
import com.google.mediapipe.tasks.components.containers.AudioData
import com.google.mediapipe.tasks.core.BaseOptions
import kotlin.concurrent.thread

/**
 * LISTEN / Sound alerts — REAL acoustic event classification using
 * Google's MediaPipe Audio Classifier Task running the pretrained
 * YAMNet model (`assets/yamnet.tflite`, 521 AudioSet classes),
 * fully on-device. This replaces the earlier amplitude-pattern
 * heuristic: SAHAY now recognizes an actual vehicle horn, siren,
 * alarm, or doorbell by sound, not by "loud vs not loud".
 */
class SoundEventClassifier(private val context: Context) {

    interface Listener {
        fun onLevel(pct: Float)
        fun onSoundEvent(type: String, label: String, confidence: Float)
    }
    var listener: Listener? = null

    // Real-world sound -> SAHAY attention category. Sirens/alarms are an
    // ongoing danger signal (sustained, higher priority); horns/doorbells
    // are a momentary alert (impulsive).
    private val sustainedLabels = setOf(
        "Alarm", "Alarm clock", "Siren", "Civil defense siren", "Buzzer",
        "Smoke detector, smoke alarm", "Fire alarm", "Police car (siren)",
        "Ambulance (siren)", "Fire engine, fire truck (siren)", "Car alarm",
    )
    private val impulsiveLabels = setOf(
        "Vehicle horn, car horn, honking", "Air horn, truck horn", "Train horn",
        "Doorbell", "Foghorn", "Knock",
    )

    private var classifier: AudioClassifier? = null
    private var audioRecord: AudioRecord? = null
    @Volatile private var running = false

    private val sampleRate = 16000
    private val sampleCountsPerFrame = 15600 // ~0.975s — one YAMNet window

    fun start() {
        if (running) return
        try {
            val baseOptions = BaseOptions.builder().setModelAssetPath("yamnet.tflite").build()
            val options = AudioClassifier.AudioClassifierOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.AUDIO_STREAM)
                .setMaxResults(3)
                .setScoreThreshold(0.35f)
                .setResultListener(::handleResult)
                .setErrorListener { e: RuntimeException -> Log.e(TAG, "AudioClassifier error", e) }
                .build()
            classifier = AudioClassifier.createFromOptions(context, options)

            val record = classifier!!.createAudioRecord(1, sampleRate, sampleCountsPerFrame)
            audioRecord = record
            record.startRecording()
            running = true
            thread(name = "SahayYamnet") { loop() }
        } catch (e: Exception) {
            Log.e(TAG, "SoundEventClassifier failed to start", e)
            running = false
        }
    }

    private fun loop() {
        val record = audioRecord ?: return
        var ts = 0L
        val meterBuf = ShortArray(1600)
        while (running) {
            try {
                // live meter feedback (cheap amplitude read, cosmetic only —
                // classification below is what actually decides events)
                val n = record.read(meterBuf, 0, meterBuf.size)
                if (n > 0) {
                    var sumSq = 0.0
                    for (i in 0 until n) sumSq += (meterBuf[i].toDouble() * meterBuf[i].toDouble())
                    val rms = Math.sqrt(sumSq / n)
                    listener?.onLevel((rms / 9000.0).coerceIn(0.0, 1.0).toFloat())
                }

                val audioData = AudioData.create(
                    AudioData.AudioDataFormat.create(record.format), sampleCountsPerFrame
                )
                audioData.load(record)
                classifier?.classifyAsync(audioData, ts)
                ts += 975
            } catch (e: Exception) {
                Log.w(TAG, "Classification loop error", e)
            }
        }
    }

    private fun handleResult(result: AudioClassifierResult) {
        val categories = result.classificationResults().firstOrNull()
            ?.classifications()?.firstOrNull()?.categories() ?: return
        val top = categories.maxByOrNull { it.score() } ?: return
        val label = top.categoryName()
        val score = top.score()

        when {
            sustainedLabels.contains(label) -> listener?.onSoundEvent("sound_sustained", label, score)
            impulsiveLabels.contains(label) -> listener?.onSoundEvent("sound_impulsive", label, score)
            // Speech/silence/music/etc. are not alert-worthy — deliberately
            // not forwarded, matching "don't create meaningless alerts".
        }
    }

    fun stop() {
        running = false
        try { audioRecord?.stop(); audioRecord?.release() } catch (e: Exception) { /* ignore */ }
        try { classifier?.close() } catch (e: Exception) { /* ignore */ }
        audioRecord = null
        classifier = null
    }

    companion object { private const val TAG = "SoundEventClassifier" }
}
