package com.accessibility.detector.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.accessibility.detector.communication.TtsManager
import com.accessibility.detector.core.HapticManager
import com.accessibility.detector.databinding.ActivityHomeBinding

/**
 * SAHEY Home Screen presenting 3 distinct accessibility categories:
 * 1. 👁️ VISION ASSIST (Camera-based AI)
 * 2. 🔊 SOUND & LANGUAGE ASSIST (Mic/Audio only, No Camera)
 * 3. 🗣️ SPEAK & TRANSLATION ASSIST (Text/TTS/Translate, No Camera)
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var hapticManager: HapticManager
    private lateinit var ttsManager: TtsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        hapticManager = HapticManager(this)
        ttsManager = TtsManager(this)

        setupListeners()
    }

    private fun setupListeners() {
        // Category 1: Vision Assist
        binding.cardVisionAssist.setOnClickListener {
            hapticManager.playNormalPulse()
            startActivity(Intent(this, VisionAssistActivity::class.java))
        }

        // Category 2: Sound Assist
        binding.cardSoundAssist.setOnClickListener {
            hapticManager.playNormalPulse()
            startActivity(Intent(this, SoundAssistActivity::class.java))
        }

        // Category 3: Speak & Translate
        binding.cardCommunication.setOnClickListener {
            hapticManager.playNormalPulse()
            startActivity(Intent(this, CommunicationActivity::class.java))
        }

        // Hackathon Demo Dialog
        binding.btnDemo.setOnClickListener {
            hapticManager.playNormalPulse()
            DemoDialog(this).show()
        }

        // Settings
        binding.btnSettings.setOnClickListener {
            hapticManager.playNormalPulse()
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsManager.shutdown()
    }
}
