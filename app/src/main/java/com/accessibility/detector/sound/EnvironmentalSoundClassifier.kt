package com.accessibility.detector.sound

import android.content.Context
import android.media.AudioRecord
import android.util.Log
import com.accessibility.detector.core.EventPriority
import org.tensorflow.lite.support.audio.TensorAudio
import org.tensorflow.lite.task.audio.classifier.AudioClassifier

/**
 * Real environmental-sound recognition using **YAMNet** (Google AudioSet, 521 classes)
 * via the TensorFlow Lite Task Audio library.
 *
 * Replaces the previous zero-crossing-rate + RMS heuristic ([SoundClassifier]), which is
 * kept only as a last-resort fallback when the model asset is absent.
 *
 * Requires the model asset:  app/src/main/assets/yamnet.tflite
 *   Download (~4 MB, with metadata):
 *   https://storage.googleapis.com/mediapipe-models/audio_classifier/yamnet/float32/1/yamnet.tflite
 *   (or TF Hub: https://tfhub.dev/google/lite-model/yamnet/classification/tflite/1)
 *
 * If the asset is missing, [isReady] is false and the caller falls back to the heuristic.
 */
class EnvironmentalSoundClassifier(context: Context) {

    private var classifier: AudioClassifier? = null
    private var tensor: TensorAudio? = null

    val isReady: Boolean get() = classifier != null

    /** Debounce so the same alert is not spoken repeatedly. */
    private val lastFiredAt = HashMap<String, Long>()
    private val perLabelCooldownMs = 4000L

    init {
        try {
            val options = AudioClassifier.AudioClassifierOptions.builder()
                .setScoreThreshold(MIN_SCORE)
                .setMaxResults(5)
                .build()
            classifier = AudioClassifier.createFromFileAndOptions(context, MODEL_ASSET, options)
            tensor = classifier?.createInputTensorAudio()
            Log.d(TAG, "YAMNet AudioClassifier initialised from $MODEL_ASSET")
        } catch (e: Throwable) {
            Log.w(
                TAG,
                "YAMNet unavailable (${e.message}). Place $MODEL_ASSET in app/src/main/assets/ " +
                    "to enable real sound recognition."
            )
            classifier = null
        }
    }

    /** Creates an [AudioRecord] matching YAMNet's required format (16 kHz mono float). */
    fun createAudioRecord(): AudioRecord? = classifier?.createAudioRecord()

    /**
     * Loads the latest audio window from [record] and runs one classification pass.
     * Returns a mapped [SoundEvent] when a recognised alert sound is confidently present.
     */
    fun classify(record: AudioRecord): SoundEvent? {
        val clf = classifier ?: return null
        val ta = tensor ?: return null
        return try {
            ta.load(record)
            val results = clf.classify(ta)
            val categories = results.firstOrNull()?.categories ?: return null

            // Highest-scoring category that maps to an alert we care about.
            val best = categories
                .sortedByDescending { it.score }
                .firstNotNullOfOrNull { cat -> ALERT_MAP[cat.label.lowercase()]?.let { it to cat.score } }
                ?: return null

            val (spec, score) = best
            if (score < spec.minScore) return null

            val now = System.currentTimeMillis()
            val last = lastFiredAt[spec.label] ?: 0L
            if (now - last < perLabelCooldownMs) return null
            lastFiredAt[spec.label] = now

            SoundEvent(
                label = spec.label,
                icon = spec.icon,
                description = spec.description,
                confidence = score,
                priority = spec.priority
            )
        } catch (e: Throwable) {
            Log.w(TAG, "YAMNet classify failed: ${e.message}")
            null
        }
    }

    fun close() {
        try {
            classifier?.close()
        } catch (_: Throwable) {
        } finally {
            classifier = null
            tensor = null
        }
    }

    private data class AlertSpec(
        val label: String,
        val icon: String,
        val description: String,
        val priority: Int,
        val minScore: Float
    )

    companion object {
        private const val TAG = "EnvSoundClassifier"
        const val MODEL_ASSET = "yamnet.tflite"
        private const val MIN_SCORE = 0.30f

        private val SIREN = AlertSpec("Emergency Siren", "🚑", "Ambulance / police / fire siren nearby.", EventPriority.DANGER, 0.35f)
        private val HORN = AlertSpec("Vehicle Horn", "🚗", "A vehicle horn was honked nearby.", EventPriority.DANGER, 0.40f)
        private val FIRE_ALARM = AlertSpec("Fire / Smoke Alarm", "🚨", "Smoke or fire alarm is sounding.", EventPriority.CRITICAL, 0.35f)
        private val ALARM = AlertSpec("Alarm", "⏰", "An alarm is sounding nearby.", EventPriority.DANGER, 0.45f)
        private val DOORBELL = AlertSpec("Doorbell", "🔔", "Someone rang the doorbell.", EventPriority.NAVIGATION, 0.40f)
        private val KNOCK = AlertSpec("Door Knock", "🚪", "Knocking on a door.", EventPriority.NAVIGATION, 0.45f)
        private val GLASS = AlertSpec("Glass Break", "💥", "Breaking glass or a loud shatter.", EventPriority.DANGER, 0.45f)
        private val BABY = AlertSpec("Baby Crying", "👶", "A baby is crying nearby.", EventPriority.NAVIGATION, 0.50f)
        private val DOG = AlertSpec("Dog Barking", "🐕", "A dog is barking nearby.", EventPriority.NAVIGATION, 0.55f)

        /** AudioSet display name (lower-cased) -> alert spec. */
        private val ALERT_MAP: Map<String, AlertSpec> = buildMap {
            listOf(
                "siren", "civil defense siren", "ambulance (siren)", "police car (siren)",
                "fire engine, fire truck (siren)", "emergency vehicle"
            ).forEach { put(it, SIREN) }

            listOf(
                "vehicle horn, car horn, honking", "air horn, truck horn", "car alarm", "toot"
            ).forEach { put(it, HORN) }

            listOf("smoke detector, smoke alarm", "fire alarm").forEach { put(it, FIRE_ALARM) }
            listOf("alarm", "buzzer", "beep, bleep").forEach { put(it, ALARM) }
            listOf("doorbell", "ding-dong", "chime").forEach { put(it, DOORBELL) }
            listOf("knock", "tap").forEach { put(it, KNOCK) }
            listOf("glass", "shatter", "breaking").forEach { put(it, GLASS) }
            listOf("baby cry, infant cry", "crying, sobbing").forEach { put(it, BABY) }
            listOf("dog", "bark", "bow-wow").forEach { put(it, DOG) }
        }
    }
}
