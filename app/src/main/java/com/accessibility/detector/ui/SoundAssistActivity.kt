package com.accessibility.detector.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
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
import com.accessibility.detector.sound.OfflineSpeechModelManager
import com.accessibility.detector.sound.SpeechModelStatus
import com.accessibility.detector.sound.SpeechRecognitionMode
import com.accessibility.detector.sound.SoundEvent
import com.accessibility.detector.sound.SoundOrchestrator
import com.accessibility.detector.sound.SoundOrchestratorCallback
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Category 2: Sound & Language Assist Activity.
 * Purely Audio / Microphone based (NO CAMERA).
 * Provides Offline Telugu Speech Recognition, Live Continuous Captions,
 * Environmental Sound Classification (sirens, car horns, alarms), Haptics, and Live Translation.
 */
class SoundAssistActivity : AppCompatActivity(), SoundOrchestratorCallback {

    private lateinit var binding: ActivitySoundAssistBinding
    private lateinit var soundOrchestrator: SoundOrchestrator
    private lateinit var speechModelManager: OfflineSpeechModelManager
    private val soundHistoryList = mutableListOf<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val speechLanguages = listOf(
        SupportedLanguage.TELUGU,
        SupportedLanguage.ENGLISH,
        SupportedLanguage.HINDI,
        SupportedLanguage.TAMIL
    )

    private val recognitionModes = listOf(
        SpeechRecognitionMode.PREFER_OFFLINE to "⚡ Prefer Offline",
        SpeechRecognitionMode.OFFLINE_ONLY to "📴 Offline Only",
        SpeechRecognitionMode.ONLINE_FALLBACK to "🌐 Online Mode"
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (PermissionManager.hasSoundPermissions(this)) {
            startOrchestrator()
        } else {
            Toast.makeText(this, "Microphone permission required for Sound Assist", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySoundAssistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        soundOrchestrator = SoundOrchestrator(this, this)
        soundOrchestrator.inputSpeechLanguage = SupportedLanguage.TELUGU
        soundOrchestrator.targetLanguage = SupportedLanguage.ENGLISH

        speechModelManager = OfflineSpeechModelManager(this)

        setupSpinners()
        setupListeners()
        checkOfflineAsrStatus()
        checkPermissionsAndStart()
    }

    private fun setupSpinners() {
        // 1. Language Selector
        val langAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            speechLanguages.map { it.displayName }
        )
        binding.spnSpeechLanguage.adapter = langAdapter
        binding.spnSpeechLanguage.setSelection(0) // Default: Telugu

        binding.spnSpeechLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = speechLanguages[position]
                soundOrchestrator.inputSpeechLanguage = selected
                soundOrchestrator.targetLanguage = if (selected == SupportedLanguage.ENGLISH) SupportedLanguage.TELUGU else SupportedLanguage.ENGLISH

                checkOfflineAsrStatus()

                if (soundOrchestrator.speechEngine.shouldKeepListening) {
                    soundOrchestrator.startSoundAssist(selected, soundOrchestrator.speechRecognitionMode)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 2. Mode Selector
        val modeAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            recognitionModes.map { it.second }
        )
        binding.spnRecognitionMode.adapter = modeAdapter
        binding.spnRecognitionMode.setSelection(0) // Default: Prefer Offline

        binding.spnRecognitionMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedMode = recognitionModes[position].first
                soundOrchestrator.speechRecognitionMode = selectedMode

                if (soundOrchestrator.speechEngine.shouldKeepListening) {
                    soundOrchestrator.startSoundAssist(soundOrchestrator.inputSpeechLanguage, selectedMode)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Download / Install system offline voice model button
        binding.btnDownloadVoiceModel.setOnClickListener {
            speechModelManager.openVoiceModelDownloadSettings(this)
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
                startOrchestrator()
                binding.btnToggleMic.setIconResource(R.drawable.ic_speaker)
                binding.btnToggleMic.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.accent_green)
                )
            }
        }

        // Test / Simulation buttons for demonstrations
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

    private fun checkOfflineAsrStatus() {
        speechModelManager.checkTeluguOfflineStatus { status, message ->
            runOnUiThread {
                binding.tvOfflineAsrBadge.text = message
                when (status) {
                    SpeechModelStatus.OFFLINE_READY -> {
                        binding.tvOfflineAsrBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
                        binding.btnDownloadVoiceModel.visibility = View.GONE
                    }
                    SpeechModelStatus.ONLINE_AVAILABLE -> {
                        binding.tvOfflineAsrBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_cyan))
                        binding.btnDownloadVoiceModel.visibility = View.VISIBLE
                    }
                    SpeechModelStatus.MODEL_NOT_INSTALLED -> {
                        binding.tvOfflineAsrBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_yellow))
                        binding.btnDownloadVoiceModel.visibility = View.VISIBLE
                    }
                    SpeechModelStatus.UNAVAILABLE -> {
                        binding.tvOfflineAsrBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_red))
                        binding.btnDownloadVoiceModel.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun checkPermissionsAndStart() {
        if (PermissionManager.hasSoundPermissions(this)) {
            startOrchestrator()
        } else {
            permissionLauncher.launch(PermissionManager.SOUND_PERMISSIONS)
        }
    }

    private fun startOrchestrator() {
        soundOrchestrator.startSoundAssist(
            speechLang = soundOrchestrator.inputSpeechLanguage,
            mode = soundOrchestrator.speechRecognitionMode
        )
    }

    override fun onResume() {
        super.onResume()
        checkOfflineAsrStatus()
        if (PermissionManager.hasSoundPermissions(this) && !soundOrchestrator.speechEngine.shouldKeepListening) {
            startOrchestrator()
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
            val mode = if (result.isOffline) "🟢 Offline" else "🌐 Online"
            binding.tvLiveTranslation.text = "$mode Translation: \"${result.translatedText}\" (${result.targetLanguage.displayName})"
        }
    }

    override fun onListeningStatus(isListening: Boolean, message: String) {
        runOnUiThread {
            binding.tvSoundStatus.text = if (isListening) "🎙️ Active • $message" else "⏸️ $message"
        }
    }

    override fun onAudioWaveLevel(level: Float) {
        runOnUiThread {
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
