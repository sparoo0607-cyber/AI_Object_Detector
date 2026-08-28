package com.accessibility.detector.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Window
import android.widget.Button
import android.widget.TextView
import com.accessibility.detector.R
import com.accessibility.detector.core.SaheyAIOrchestrator
import com.accessibility.detector.detection.EventPriority
import com.accessibility.detector.detection.PerceptionEvent
import com.accessibility.detector.detection.PerceptionType
import com.accessibility.detector.detection.ProximityLevel
import com.accessibility.detector.detection.SpatialPosition

/**
 * Interactive Demo Panel for Hackathon demonstrations.
 */
class DemoDialog(
    context: Context,
    private val orchestrator: SaheyAIOrchestrator
) : Dialog(context) {

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

        val statuses = orchestrator.modelManager.getAllStatuses()
        val statusText = StringBuilder()
        statuses.forEach { (mod, status) ->
            statusText.append("● $mod: $status\n")
        }
        tvStatus.text = statusText.toString().trim()

        // 1. Test Danger
        btnDanger.setOnClickListener {
            val event = PerceptionEvent(
                type = PerceptionType.DANGER,
                label = "Approaching Car (Left)",
                spokenText = "Warning! Car approaching on your left, very close!",
                confidence = 0.95f,
                priority = EventPriority.CRITICAL,
                spatialPosition = SpatialPosition.LEFT,
                proximity = ProximityLevel.VERY_CLOSE
            )
            orchestrator.announcementManager.postEvent(event)
            dismiss()
        }

        // 2. Test Siren Sound
        btnSiren.setOnClickListener {
            val event = PerceptionEvent(
                type = PerceptionType.SOUND,
                label = "Emergency Siren",
                spokenText = "Emergency siren heard nearby!",
                confidence = 0.92f,
                priority = EventPriority.DANGER
            )
            orchestrator.announcementManager.postEvent(event)
            dismiss()
        }

        // 3. Test Sign Language
        btnSign.setOnClickListener {
            val event = PerceptionEvent(
                type = PerceptionType.SIGN,
                label = "Sign: Thank You",
                spokenText = "Sign language recognized: Thank you.",
                confidence = 0.91f,
                priority = EventPriority.SIGN
            )
            orchestrator.announcementManager.postEvent(event)
            dismiss()
        }

        // 4. Test Translation
        btnTranslate.setOnClickListener {
            val result = orchestrator.translationEngine.translate(
                text = "Salida",
                sourceLang = com.accessibility.detector.translation.SupportedLanguage.SPANISH,
                targetLang = com.accessibility.detector.translation.SupportedLanguage.ENGLISH
            )
            val event = PerceptionEvent(
                type = PerceptionType.TRANSLATION,
                label = "Translation: ${result.translatedText}",
                spokenText = "Translated: ${result.originalText} means ${result.translatedText}",
                confidence = 1.0f,
                priority = EventPriority.NAVIGATION
            )
            orchestrator.announcementManager.postEvent(event)
            dismiss()
        }

        btnDismiss.setOnClickListener {
            dismiss()
        }
    }
}
