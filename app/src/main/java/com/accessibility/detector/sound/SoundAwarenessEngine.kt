package com.accessibility.detector.sound

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean

interface SoundAwarenessListener {
    fun onSoundEvent(event: SoundEvent)
    fun onSoundEngineState(isActive: Boolean, message: String)
}

/**
 * Continuous environmental-sound listener for Category 2: Sound & Language Assist.
 *
 * Prefers **YAMNet** ([EnvironmentalSoundClassifier]). Falls back to the legacy
 * zero-crossing heuristic ([SoundClassifier]) only when `yamnet.tflite` is not bundled,
 * and clearly reports which mode is active.
 */
class SoundAwarenessEngine(
    private val context: Context,
    private val listener: SoundAwarenessListener,
    private val heuristicClassifier: SoundClassifier = SoundClassifier()
) {

    private val yamnet = EnvironmentalSoundClassifier(context)

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private val isRecording = AtomicBoolean(false)

    // Heuristic-mode capture format
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val heuristicBufferSize = maxOf(
        AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat),
        sampleRate / 2
    )

    val activeModeLabel: String
        get() = if (yamnet.isReady) "YAMNet AI sound model" else "Heuristic sound estimator"

    fun startListening() {
        if (isRecording.get()) return

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            listener.onSoundEngineState(false, "Microphone permission required")
            return
        }

        if (yamnet.isReady) startYamnet() else startHeuristic()
    }

    @Suppress("MissingPermission") // RECORD_AUDIO verified in startListening()
    private fun startYamnet() {
        try {
            val record = yamnet.createAudioRecord()
            if (record == null) {
                Log.w(TAG, "YAMNet record creation failed; using heuristic mode")
                startHeuristic()
                return
            }
            audioRecord = record
            record.startRecording()
            isRecording.set(true)
            listener.onSoundEngineState(true, "Listening (YAMNet AI model)")

            recordingThread = Thread({
                // YAMNet needs ~0.975 s of audio; classify roughly twice a second.
                while (isRecording.get()) {
                    val event = yamnet.classify(record)
                    if (event != null) listener.onSoundEvent(event)
                    try {
                        Thread.sleep(500)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }, "Sahey-YamnetThread").apply {
                priority = Thread.MIN_PRIORITY + 1
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "YAMNet start failed: ${e.message}", e)
            startHeuristic()
        }
    }

    private fun startHeuristic() {
        try {
            @Suppress("MissingPermission")
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate, channelConfig, audioFormat, heuristicBufferSize
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                listener.onSoundEngineState(false, "AudioRecord init failed")
                return
            }
            audioRecord = record
            record.startRecording()
            isRecording.set(true)
            listener.onSoundEngineState(true, "Listening (heuristic estimator — bundle yamnet.tflite for AI mode)")

            recordingThread = Thread({
                val buffer = ShortArray(heuristicBufferSize)
                while (isRecording.get()) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        heuristicClassifier.classifyAudioBuffer(buffer, read, sampleRate)
                            ?.let { listener.onSoundEvent(it) }
                    }
                }
            }, "Sahey-SoundHeuristicThread").apply {
                priority = Thread.MIN_PRIORITY + 1
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Heuristic sound start failed: ${e.message}", e)
            listener.onSoundEngineState(false, "Sound error: ${e.localizedMessage}")
        }
    }

    fun stopListening() {
        if (!isRecording.getAndSet(false)) return
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            recordingThread?.interrupt()
            recordingThread = null
            listener.onSoundEngineState(false, "Sound listening stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping sound listener: ${e.message}")
        }
    }

    fun shutdown() {
        stopListening()
        yamnet.close()
    }

    companion object {
        private const val TAG = "SoundAwarenessEngine"
    }
}
