package com.accessibility.detector.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.accessibility.detector.R
import com.accessibility.detector.communication.SupportedLanguage
import com.accessibility.detector.communication.TranslationResult
import com.accessibility.detector.core.EventPriority
import com.accessibility.detector.core.PermissionManager
import com.accessibility.detector.databinding.ActivitySoundAssistBinding
import com.accessibility.detector.sound.SoundEvent
import com.accessibility.detector.sound.SoundOrchestrator
import com.accessibility.detector.sound.SoundOrchestratorCallback
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Category 2: Sound & Language Assist Activity.
 * Purely Audio / Microphone based (NO CAMERA).
 * Provides Live Human Voice Transcription, Sound Classification, Vibration, and Live Translation.
 */
class SoundAssistActivity : AppCompatActivity(), SoundOrchestratorCallback {

    private lateinit var binding: ActivitySoundAssistBinding
    private lateinit var soundOrchestrator: SoundOrchestrator
    private val soundHistoryList = mutableListOf<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (PermissionManager.hasSoundPermissions(this)) {
            soundOrchestrator.startSoundAssist()
        } else {
            Toast.makeText(this, "Microphone permission required for Sound Assist", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySoundAssistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        soundOrchestrator = SoundOrchestrator(this, this)
        soundOrchestrator.targetLanguage = SupportedLanguage.TELUGU

        setupListeners()
        checkPermissionsAndStart()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Toggle Mic Listening
        binding.btnToggleMic.setOnClickListener {
            if (soundOrchestrator.speechEngine.shouldKeepListening) {
                soundOrchestrator.stopSoundAssist()
                binding.btnToggleMic.setIconResource(R.drawable.ic_speaker_off)
                binding.btnToggleMic.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.accent_red)
                )
            } else {
                soundOrchestrator.startSoundAssist()
                binding.btnToggleMic.setIconResource(R.drawable.ic_speaker)
                binding.btnToggleMic.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.accent_green)
                )
            }
        }

        // Test / Simulation buttons for judges and demonstration
        binding.btnSimulateSiren.setOnClickListener {
            val sirenEvent = SoundEvent(
                label = "Emergency Siren",
                icon = "🚑",
                description = "Ambulance / Police siren heard nearby!",
                confidence = 0.92f,
                priority = EventPriority.DANGER
            )
            soundOrchestrator.onSoundEvent(sirenEvent)
        }

        binding.btnSimulateHorn.setOnClickListener {
            val hornEvent = SoundEvent(
                label = "Car Horn",
                icon = "🚗",
                description = "Vehicle horn honked nearby!",
                confidence = 0.88f,
                priority = EventPriority.DANGER
            )
            soundOrchestrator.onSoundEvent(hornEvent)
        }

        binding.btnSimulateDoorbell.setOnClickListener {
            val bellEvent = SoundEvent(
                label = "Doorbell",
                icon = "🔔",
                description = "Doorbell ringing at entrance.",
                confidence = 0.85f,
                priority = EventPriority.NAVIGATION
            )
            soundOrchestrator.onSoundEvent(bellEvent)
        }
    }

    private fun checkPermissionsAndStart() {
        if (PermissionManager.hasSoundPermissions(this)) {
            soundOrchestrator.startSoundAssist()
        } else {
            permissionLauncher.launch(PermissionManager.SOUND_PERMISSIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        if (PermissionManager.hasSoundPermissions(this) && !soundOrchestrator.speechEngine.shouldKeepListening) {
            soundOrchestrator.startSoundAssist()
        }
    }

    // --- SoundOrchestratorCallback ---
    override fun onNewSoundEvent(event: SoundEvent) {
        runOnUiThread {
            // 1. Update Latest Banner
            binding.tvLatestSoundIcon.text = event.icon
            binding.tvLatestSoundTitle.text = event.label
            binding.tvLatestSoundDesc.text = "${event.description} (${timeFormat.format(Date())})"

            val cardColor = when (event.priority) {
                EventPriority.CRITICAL -> ContextCompat.getColor(this, R.color.accent_red)
                EventPriority.DANGER -> ContextCompat.getColor(this, R.color.accent_blue)
                else -> ContextCompat.getColor(this, R.color.accent_yellow)
            }
            binding.cardLatestSound.strokeColor = cardColor

            // 2. Append to History Feed
            val historyEntry = "${timeFormat.format(Date(event.timestamp))} ${event.icon} ${event.label} — ${event.description}"
            soundHistoryList.add(0, historyEntry)
            if (soundHistoryList.size > 8) soundHistoryList.removeLast()

            binding.tvSoundHistory.text = "Recent Sound Alerts:\n" + soundHistoryList.joinToString("\n• ", prefix = "• ")
        }
    }

    override fun onLiveCaption(text: String, isPartial: Boolean) {
        runOnUiThread {
            binding.tvLiveTranscription.text = if (isPartial) "\"$text...\"" else "\"$text\""
        }
    }

    override fun onTranslation(result: TranslationResult) {
        runOnUiThread {
            binding.tvLiveTranslation.text = "🌍 ${result.translatedText} (${result.targetLanguage.displayName})"
        }
    }

    override fun onListeningStatus(isListening: Boolean, message: String) {
        runOnUiThread {
            binding.tvSoundStatus.text = if (isListening) "🎙️ Active • $message" else "⏸️ $message"
        }
    }

    override fun onAudioWaveLevel(level: Float) {
        runOnUiThread {
            // Convert rmsdB (-2..12) to percentage progress (0..100)
            val normalized = ((level + 2f) / 14f * 100f).toInt().coerceIn(5, 100)
            binding.pbAudioLevel.progress = normalized
            if (normalized > 35) {
                binding.tvMicIndicator.text = "🎙️ Voice Speaking"
                binding.tvMicIndicator.setTextColor(ContextCompat.getColor(this, R.color.accent_cyan))
            } else {
                binding.tvMicIndicator.text = "🎙️ Mic Active"
                binding.tvMicIndicator.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        soundOrchestrator.shutdown()
    }

    companion object {
        private const val TAG = "SoundAssistActivity"
    }
}
