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
 * Continuous environmental sound listener for Category 2: Sound & Language Assist.
 */
class SoundAwarenessEngine(
    private val context: Context,
    private val listener: SoundAwarenessListener,
    private val soundClassifier: SoundClassifier = SoundClassifier()
) {

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private val isRecording = AtomicBoolean(false)

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = maxOf(
        AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat),
        sampleRate / 2 // 500ms chunk
    )

    fun startListening() {
        if (isRecording.get()) return

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            listener.onSoundEngineState(false, "Microphone permission required")
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                listener.onSoundEngineState(false, "AudioRecord init failed")
                return
            }

            audioRecord?.startRecording()
            isRecording.set(true)
            listener.onSoundEngineState(true, "Listening for environmental sounds")

            recordingThread = Thread({
                val buffer = ShortArray(bufferSize)
                while (isRecording.get()) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        val soundEvent = soundClassifier.classifyAudioBuffer(buffer, read, sampleRate)
                        if (soundEvent != null) {
                            listener.onSoundEvent(soundEvent)
                        }
                    }
                }
            }, "Sahey-SoundAssistThread").apply {
                priority = Thread.MIN_PRIORITY + 1
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start sound recording: ${e.message}", e)
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

    companion object {
        private const val TAG = "SoundAwarenessEngine"
    }
}
