package com.accessibility.detector.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.accessibility.detector.communication.CommunicationManager
import com.accessibility.detector.communication.SupportedLanguage
import com.accessibility.detector.communication.TtsManager
import com.accessibility.detector.databinding.ActivityCommunicationBinding
import com.accessibility.detector.sound.LiveSpeechListener
import com.accessibility.detector.sound.SpeechRecognitionEngine

/**
 * Category 3: Speak & Translate Assist Activity.
 * Designed for non-verbal users or communication assistance (NO CAMERA).
 * Provides Type-to-Speak, Quick Accessible Phrases, Multilingual Translation, and Two-Way Conversation.
 */
class CommunicationActivity : AppCompatActivity(), LiveSpeechListener {

    private lateinit var binding: ActivityCommunicationBinding
    private lateinit var communicationManager: CommunicationManager
    private lateinit var ttsManager: TtsManager
    private lateinit var speechEngine: SpeechRecognitionEngine

    private var targetLanguage = SupportedLanguage.TELUGU

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommunicationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ttsManager = TtsManager(this)
        communicationManager = CommunicationManager(this, ttsManager)
        speechEngine = SpeechRecognitionEngine(this, this)

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
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

        // 4. Translate & Speak
        binding.btnTranslateAndSpeak.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotBlank()) {
                val result = communicationManager.translateAndSpeak(text, targetLanguage)
                binding.tvTranslatedOutput.text = "🌍 ${result.translatedText} (${result.targetLanguage.displayName})"
            } else {
                val sampleText = "Where is the bus station?"
                val result = communicationManager.translateAndSpeak(sampleText, targetLanguage)
                binding.etMessage.setText(sampleText)
                binding.tvTranslatedOutput.text = "🌍 ${result.translatedText} (${result.targetLanguage.displayName})"
            }
        }

        // 5. One-Tap Quick Phrase Buttons
        binding.btnPhraseHelp.setOnClickListener {
            binding.etMessage.setText("I need help.")
            communicationManager.speakText("I need help.")
        }

        binding.btnPhraseWater.setOnClickListener {
            binding.etMessage.setText("I need some water.")
            communicationManager.speakText("I need some water.")
        }

        binding.btnPhraseFood.setOnClickListener {
            binding.etMessage.setText("I need food.")
            communicationManager.speakText("I need food.")
        }

        binding.btnPhraseHospital.setOnClickListener {
            binding.etMessage.setText("Please take me to the hospital.")
            communicationManager.speakText("Please take me to the hospital.")
        }

        binding.btnPhraseThanks.setOnClickListener {
            binding.etMessage.setText("Thank you.")
            communicationManager.speakText("Thank you.")
        }

        binding.btnPhraseYesNo.setOnClickListener {
            val current = binding.etMessage.text.toString().trim()
            if (current == "Yes.") {
                binding.etMessage.setText("No.")
                communicationManager.speakText("No.")
            } else {
                binding.etMessage.setText("Yes.")
                communicationManager.speakText("Yes.")
            }
        }

        // 6. Two-Way Conversation Microphone Input
        binding.btnListenOtherPerson.setOnClickListener {
            if (speechEngine.isListening) {
                speechEngine.stopListening()
                binding.btnListenOtherPerson.text = "🎤 Listen to Other Person's Response"
            } else {
                speechEngine.startContinuousListening()
                binding.btnListenOtherPerson.text = "🔴 Listening... Speak now"
                binding.tvOtherPersonResponse.text = "Listening..."
            }
        }
    }

    // --- LiveSpeechListener ---
    override fun onSpeechRecognized(text: String) {
        runOnUiThread {
            binding.btnListenOtherPerson.text = "🎤 Listen to Other Person's Response"
            val translation = communicationManager.translationEngine.translate(
                text = text,
                sourceLang = SupportedLanguage.ENGLISH,
                targetLang = SupportedLanguage.TELUGU
            )
            binding.tvOtherPersonResponse.text = "Other Person Said: \"$text\"\nTranslated: ${translation.translatedText}"
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
                binding.btnListenOtherPerson.text = "🎤 Listen to Other Person's Response"
            }
        }
    }

    override fun onSpeechError(errorMessage: String) {
        runOnUiThread {
            binding.btnListenOtherPerson.text = "🎤 Listen to Other Person's Response"
            binding.tvOtherPersonResponse.text = "Speech input error: $errorMessage"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsManager.shutdown()
        speechEngine.shutdown()
    }

    companion object {
        private const val TAG = "CommunicationActivity"
    }
}
