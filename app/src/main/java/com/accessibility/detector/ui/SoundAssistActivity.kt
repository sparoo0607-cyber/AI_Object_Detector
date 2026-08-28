package com.accessibility.detector.ui

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
 * Provides Live Sound Classification, Sound-to-Vibration, Live Speech Transcription, and Live Translation.
 */
class SoundAssistActivity : AppCompatActivity(), SoundOrchestratorCallback {

    private lateinit var binding: ActivitySoundAssistBinding
    private lateinit var soundOrchestrator: SoundOrchestrator
    private val soundHistoryList = mutableListOf<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
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
        soundOrchestrator.targetLanguage = SupportedLanguage.ENGLISH

        setupListeners()
        checkPermissionsAndStart()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
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
            binding.tvLiveTranslation.text = "${result.translatedText} (${result.sourceLanguage.displayName} -> ${result.targetLanguage.displayName})"
        }
    }

    override fun onListeningStatus(isActive: Boolean, message: String) {
        runOnUiThread {
            binding.tvSoundStatus.text = if (isActive) "🎙️ Active • $message" else "⏸️ $message"
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
