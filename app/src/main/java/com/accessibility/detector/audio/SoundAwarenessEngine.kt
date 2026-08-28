package com.accessibility.detector.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.accessibility.detector.detection.PerceptionEvent
import com.accessibility.detector.detection.PerceptionType
import java.util.concurrent.atomic.AtomicBoolean

interface SoundAwarenessListener {
    fun onSoundDetected(event: PerceptionEvent)
    fun onSoundEngineStatus(isActive: Boolean, message: String)
}

/**
 * Environmental sound listening and awareness engine running in a lightweight background thread.
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
            listener.onSoundEngineStatus(false, "Microphone permission required")
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
                listener.onSoundEngineStatus(false, "AudioRecord init failed")
                return
            }

            audioRecord?.startRecording()
            isRecording.set(true)
            listener.onSoundEngineStatus(true, "Listening for environmental sounds")

            recordingThread = Thread({
                val buffer = ShortArray(bufferSize)
                while (isRecording.get()) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        val detection = soundClassifier.classifyAudioBuffer(buffer, read, sampleRate)
                        if (detection != null) {
                            val event = PerceptionEvent(
                                type = PerceptionType.SOUND,
                                label = detection.label,
                                spokenText = detection.spokenWarning,
                                confidence = detection.confidence,
                                priority = detection.priority
                            )
                            listener.onSoundDetected(event)
                        }
                    }
                }
            }, "Sahey-SoundAwarenessThread").apply {
                priority = Thread.MIN_PRIORITY + 1
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start sound recording: ${e.message}", e)
            listener.onSoundEngineStatus(false, "Sound recording error: ${e.localizedMessage}")
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
            listener.onSoundEngineStatus(false, "Sound awareness stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping sound listener: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "SoundAwarenessEngine"
    }
}
