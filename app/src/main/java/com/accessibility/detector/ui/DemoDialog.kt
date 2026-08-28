package com.accessibility.detector.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Window
import android.widget.Button
import android.widget.TextView
import com.accessibility.detector.R
import com.accessibility.detector.communication.SupportedLanguage
import com.accessibility.detector.communication.TranslationEngine
import com.accessibility.detector.communication.TtsManager
import com.accessibility.detector.core.HapticManager

/**
 * Interactive Demo Panel for Hackathon demonstrations across all 3 accessibility categories.
 */
class DemoDialog(
    context: Context
) : Dialog(context) {

    private val hapticManager = HapticManager(context)
    private val ttsManager = TtsManager(context)
    private val translationEngine = TranslationEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_demo)
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvStatus = findViewById<TextView>(R.id.tvSubsystemStatus)
        val btnDanger = findViewById<Button>(R.id.btnDemoDanger)
        val btnSiren = findViewById<Button>(R.id.btnDemoSiren)
        val btnSign = findViewById<Button>(R.id.btnDemoSign)
        val btnTranslate = findViewById<Button>(R.id.btnDemoTranslate)
        val btnDismiss = findViewById<Button>(R.id.btnDismissDemo)

        tvStatus.text = "● Category 1 (Vision Assist): ACTIVE\n● Category 2 (Sound Assist): ACTIVE\n● Category 3 (Speak & Translate): ACTIVE"

        // 1. Test Danger
        btnDanger.setOnClickListener {
            hapticManager.playCriticalSosPattern()
            ttsManager.speak("Warning! Vehicle approaching on your left, very close!", interrupt = true)
            dismiss()
        }

        // 2. Test Siren Sound
        btnSiren.setOnClickListener {
            hapticManager.playSoundAlertPattern()
            ttsManager.speak("Emergency siren heard nearby!", interrupt = true)
            dismiss()
        }

        // 3. Test Sign Language
        btnSign.setOnClickListener {
            hapticManager.playSignConfirmation()
            ttsManager.speak("Sign recognized: Thank you.", interrupt = true)
            dismiss()
        }

        // 4. Test Translation
        btnTranslate.setOnClickListener {
            val result = translationEngine.translate(
                text = "Where is the exit?",
                sourceLang = SupportedLanguage.ENGLISH,
                targetLang = SupportedLanguage.TELUGU
            )
            hapticManager.playTranslationPulse()
            ttsManager.speak("In Telugu: ${result.translatedText}", interrupt = true)
            dismiss()
        }

        btnDismiss.setOnClickListener {
            dismiss()
        }
    }
}
