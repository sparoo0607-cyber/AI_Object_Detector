package com.accessibility.detector.ui

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.accessibility.detector.R
import com.accessibility.detector.vision.gemini.GeminiConfig
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * Settings and customization dashboard for SAHEY.
 * Includes dynamic Gemini API Key management and AI module toggles.
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        val prefs = getSharedPreferences("sahey_prefs", MODE_PRIVATE)

        // Gemini API Key management
        val etApiKey = findViewById<EditText>(R.id.etApiKey)
        val btnSaveApiKey = findViewById<MaterialButton>(R.id.btnSaveApiKey)
        val tvApiKeyStatus = findViewById<TextView>(R.id.tvApiKeyStatus)

        val currentKey = GeminiConfig.getApiKey(this)
        if (currentKey.isNotBlank()) {
            etApiKey.setText(currentKey)
            tvApiKeyStatus.text = "✓ Configured"
            tvApiKeyStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
        } else {
            tvApiKeyStatus.text = "Not configured"
            tvApiKeyStatus.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
        }

        btnSaveApiKey.setOnClickListener {
            val enteredKey = etApiKey.text.toString().trim()
            if (enteredKey.isNotBlank()) {
                GeminiConfig.setApiKey(this, enteredKey)
                tvApiKeyStatus.text = "✓ Configured"
                tvApiKeyStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
                Toast.makeText(this, "Gemini API Key saved successfully!", Toast.LENGTH_SHORT).show()
            } else {
                GeminiConfig.setApiKey(this, "")
                tvApiKeyStatus.text = "Not configured"
                tvApiKeyStatus.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
                Toast.makeText(this, "Gemini API Key removed.", Toast.LENGTH_SHORT).show()
            }
        }

        // Toggles
        val switchObj = findViewById<SwitchMaterial>(R.id.switchObjectDetection)
        val switchDanger = findViewById<SwitchMaterial>(R.id.switchDangerDetection)
        val switchOcr = findViewById<SwitchMaterial>(R.id.switchOcr)
        val switchSign = findViewById<SwitchMaterial>(R.id.switchSignLanguage)
        val switchSound = findViewById<SwitchMaterial>(R.id.switchSoundAwareness)
        val switchHaptics = findViewById<SwitchMaterial>(R.id.switchHaptics)
        val switchPreempt = findViewById<SwitchMaterial>(R.id.switchPreemptSpeech)

        switchObj.isChecked = prefs.getBoolean("mod_obj", true)
        switchDanger.isChecked = prefs.getBoolean("mod_danger", true)
        switchOcr.isChecked = prefs.getBoolean("mod_ocr", true)
        switchSign.isChecked = prefs.getBoolean("mod_sign", true)
        switchSound.isChecked = prefs.getBoolean("mod_sound", true)
        switchHaptics.isChecked = prefs.getBoolean("haptics", true)
        switchPreempt.isChecked = prefs.getBoolean("preempt", true)

        switchObj.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("mod_obj", isChecked).apply() }
        switchDanger.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("mod_danger", isChecked).apply() }
        switchOcr.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("mod_ocr", isChecked).apply() }
        switchSign.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("mod_sign", isChecked).apply() }
        switchSound.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("mod_sound", isChecked).apply() }
        switchHaptics.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("haptics", isChecked).apply() }
        switchPreempt.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("preempt", isChecked).apply() }
    }
}
