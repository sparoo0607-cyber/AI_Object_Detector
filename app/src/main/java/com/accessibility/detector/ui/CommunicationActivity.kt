package com.accessibility.detector.ui

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.accessibility.detector.R
import com.accessibility.detector.communication.CommunicationManager
import com.accessibility.detector.communication.OfflineLanguageManager
import com.accessibility.detector.communication.SupportedLanguage
import com.accessibility.detector.communication.TtsManager
import com.accessibility.detector.databinding.ActivityCommunicationBinding
import com.accessibility.detector.sound.LiveSpeechListener
import com.accessibility.detector.sound.SpeechRecognitionEngine
import java.util.Locale

/**
 * Category 3: Speak & Translate Assist Activity.
 * Provides on-device offline translation using Google ML Kit with automatic language detection,
 * language swapping, quick accessibility phrases, and two-way speech translation (NO CAMERA).
 */
class CommunicationActivity : AppCompatActivity(), LiveSpeechListener {

    private lateinit var binding: ActivityCommunicationBinding
    private lateinit var communicationManager: CommunicationManager
    private lateinit var ttsManager: TtsManager
    private lateinit var speechEngine: SpeechRecognitionEngine
    private lateinit var offlineLanguageManager: OfflineLanguageManager

    private var sourceLanguage = SupportedLanguage.ENGLISH
    private var targetLanguage = SupportedLanguage.TELUGU
    private val languageList = listOf(
        SupportedLanguage.ENGLISH,
        SupportedLanguage.TELUGU,
        SupportedLanguage.HINDI,
        SupportedLanguage.TAMIL,
        SupportedLanguage.KANNADA,
        SupportedLanguage.MALAYALAM,
        SupportedLanguage.SPANISH
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommunicationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ttsManager = TtsManager(this)
        ttsManager.onSpeakingChanged = { speaking ->
            runOnUiThread {
                binding.btnSpeak.isEnabled = !speaking
                binding.btnTranslateAndSpeak.isEnabled = !speaking
            }
        }
        communicationManager = CommunicationManager(this, ttsManager)
        speechEngine = SpeechRecognitionEngine(this, this)
        offlineLanguageManager = OfflineLanguageManager(this)

        setupSpinners()
        setupListeners()
        updateLanguageModelStatus()
    }

    private fun setupSpinners() {
        val sourceOptions = listOf(SupportedLanguage.AUTO) + languageList
        val sourceAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            sourceOptions.map { it.displayName }
        )
        binding.spnSourceLanguage.adapter = sourceAdapter
        binding.spnSourceLanguage.setSelection(1) // Default to English

        val targetAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            languageList.map { it.displayName }
        )
        binding.spnTargetLanguage.adapter = targetAdapter
        binding.spnTargetLanguage.setSelection(1) // Default to Telugu

        binding.spnSourceLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                sourceLanguage = sourceOptions[position]
                updateLanguageModelStatus()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.spnTargetLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                targetLanguage = languageList[position]
                updateLanguageModelStatus()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        // ⇄ Swap Languages
        binding.btnSwapLanguages.setOnClickListener {
            if (sourceLanguage != SupportedLanguage.AUTO) {
                val temp = sourceLanguage
                val newSourceIndex = (listOf(SupportedLanguage.AUTO) + languageList).indexOf(targetLanguage)
                val newTargetIndex = languageList.indexOf(temp)

                if (newSourceIndex >= 0) binding.spnSourceLanguage.setSelection(newSourceIndex)
                if (newTargetIndex >= 0) binding.spnTargetLanguage.setSelection(newTargetIndex)
            } else {
                Toast.makeText(this, "Select a specific source language to swap", Toast.LENGTH_SHORT).show()
            }
        }

        // Download missing language pack
        binding.btnDownloadLanguagePack.setOnClickListener {
            downloadRequiredLanguagePack()
        }

        // 1. Speak Typed Text
        binding.btnSpeak.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotBlank()) {
                communicationManager.speakText(text)
            } else {
                Toast.makeText(this, "Please type a message first", Toast.LENGTH_SHORT).show()
            }
        }

        // 2. Repeat
        binding.btnRepeat.setOnClickListener {
            communicationManager.repeatLastPhrase()
        }

        // 3. Clear
        binding.btnClear.setOnClickListener {
            binding.etMessage.setText("")
            communicationManager.stopSpeaking()
        }

        // 4. Translate & Speak (Async Offline ML Kit)
        binding.btnTranslateAndSpeak.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            val textToTranslate = if (text.isNotBlank()) text else "Where is the bus station?"
            if (text.isBlank()) binding.etMessage.setText(textToTranslate)

            binding.tvTranslatedOutput.text = "Translating..."
            communicationManager.translateAndSpeak(
                text = textToTranslate,
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage
            ) { result ->
                runOnUiThread {
                    if (result.isSuccessful) {
                        val modeIcon = if (result.isOffline) "🟢 Offline" else "🌐 Online"
                        binding.tvTranslatedOutput.text = "$modeIcon: \"${result.translatedText}\"\n(${result.targetLanguage.displayName})"
                    } else {
                        binding.tvTranslatedOutput.text = "⚠️ ${result.translatedText}"
                    }
                }
            }
        }

        // 5. One-Tap Quick Phrase Buttons
        binding.btnPhraseHelp.setOnClickListener {
            binding.etMessage.setText("I need help.")
            handleQuickPhrase("I need help.")
        }

        binding.btnPhraseWater.setOnClickListener {
            binding.etMessage.setText("I need some water.")
            handleQuickPhrase("I need some water.")
        }

        binding.btnPhraseFood.setOnClickListener {
            binding.etMessage.setText("I need food.")
            handleQuickPhrase("I need food.")
        }

        binding.btnPhraseHospital.setOnClickListener {
            binding.etMessage.setText("Please take me to the hospital.")
            handleQuickPhrase("Please take me to the hospital.")
        }

        binding.btnPhraseThanks.setOnClickListener {
            binding.etMessage.setText("Thank you.")
            handleQuickPhrase("Thank you.")
        }

        binding.btnPhraseYesNo.setOnClickListener {
            val current = binding.etMessage.text.toString().trim()
            val next = if (current == "Yes.") "No." else "Yes."
            binding.etMessage.setText(next)
            handleQuickPhrase(next)
        }

        // 6. Two-Way Conversation Microphone Input
        binding.btnListenOtherPerson.setOnClickListener {
            if (speechEngine.isListening) {
                speechEngine.stopListening()
                binding.btnListenOtherPerson.text = LISTEN_IDLE
            } else {
                // Listen in the source language the user picked (so recognition is accurate),
                // falling back to English for Auto-detect.
                val listenLocale = TtsManager.localeOf(sourceLanguage) ?: Locale.US
                speechEngine.startContinuousListening(listenLocale)
                binding.btnListenOtherPerson.text = LISTEN_ACTIVE
                binding.tvOtherPersonResponse.text = "Listening…"
            }
        }
    }

    private fun handleQuickPhrase(phrase: String) {
        if (targetLanguage != SupportedLanguage.ENGLISH) {
            communicationManager.translateAndSpeak(
                text = phrase,
                sourceLanguage = SupportedLanguage.ENGLISH,
                targetLanguage = targetLanguage
            ) { result ->
                runOnUiThread {
                    binding.tvTranslatedOutput.text = "🌍 ${result.translatedText} (${result.targetLanguage.displayName})"
                }
            }
        } else {
            communicationManager.speakText(phrase)
        }
    }

    private fun updateLanguageModelStatus() {
        val checkSource = if (sourceLanguage == SupportedLanguage.AUTO) SupportedLanguage.ENGLISH else sourceLanguage

        offlineLanguageManager.checkLanguagePairReady(checkSource, targetLanguage) { isReady ->
            runOnUiThread {
                if (isReady) {
                    binding.tvTranslationStatusBadge.text = "🟢 OFFLINE TRANSLATION READY"
                    binding.tvTranslationStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
                    binding.bannerDownloadPack.visibility = View.GONE
                } else {
                    val isOnline = offlineLanguageManager.isInternetAvailable(this)
                    if (isOnline) {
                        binding.tvTranslationStatusBadge.text = "🌐 ONLINE TRANSLATION AVAILABLE"
                        binding.tvTranslationStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_cyan))
                    } else {
                        binding.tvTranslationStatusBadge.text = "⚠️ LANGUAGE PACK NEEDED"
                        binding.tvTranslationStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_yellow))
                    }

                    binding.bannerDownloadPack.visibility = View.VISIBLE
                    binding.tvDownloadPrompt.text = "${targetLanguage.displayName} language pack required for offline use (~30MB)."
                }
            }
        }
    }

    private fun downloadRequiredLanguagePack() {
        binding.btnDownloadLanguagePack.isEnabled = false
        binding.btnDownloadLanguagePack.text = "⏳ Downloading..."

        offlineLanguageManager.downloadModel(
            language = targetLanguage,
            onProgress = { msg ->
                runOnUiThread {
                    binding.tvDownloadPrompt.text = msg
                }
            },
            onSuccess = {
                runOnUiThread {
                    binding.btnDownloadLanguagePack.isEnabled = true
                    binding.btnDownloadLanguagePack.text = "✓ Ready"
                    Toast.makeText(this, "Offline translation is ready.", Toast.LENGTH_SHORT).show()
                    updateLanguageModelStatus()
                }
            },
            onError = { err ->
                runOnUiThread {
                    binding.btnDownloadLanguagePack.isEnabled = true
                    binding.btnDownloadLanguagePack.text = "↓ Retry"
                    Toast.makeText(this, err, Toast.LENGTH_LONG).show()
                    updateLanguageModelStatus()
                }
            }
        )
    }

    // --- LiveSpeechListener ---
    override fun onSpeechRecognized(text: String) {
        runOnUiThread {
            binding.btnListenOtherPerson.text = LISTEN_IDLE
            communicationManager.translateAndSpeak(
                text = text,
                sourceLanguage = SupportedLanguage.AUTO,
                targetLanguage = targetLanguage
            ) { result ->
                runOnUiThread {
                    binding.tvOtherPersonResponse.text = "Other Person: \"$text\"\nTranslated: ${result.translatedText}"
                }
            }
        }
    }

    override fun onSpeechPartial(partialText: String) {
        runOnUiThread {
            binding.tvOtherPersonResponse.text = "Listening: \"$partialText...\""
        }
    }

    override fun onRmsAudioLevel(rmsdB: Float) {}

    override fun onListeningStateChanged(isListening: Boolean) {
        runOnUiThread {
            if (!isListening && !speechEngine.shouldKeepListening) {
                binding.btnListenOtherPerson.text = LISTEN_IDLE
            }
        }
    }

    override fun onSpeechError(errorMessage: String) {
        runOnUiThread {
            binding.btnListenOtherPerson.text = LISTEN_IDLE
            binding.tvOtherPersonResponse.text = "Speech input error: $errorMessage"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsManager.shutdown()
        speechEngine.shutdown()
        communicationManager.translationEngine.close()
    }

    companion object {
        private const val TAG = "CommunicationActivity"
        private const val LISTEN_IDLE = "Listen to the other person"
        private const val LISTEN_ACTIVE = "Listening… speak now"
    }
}
