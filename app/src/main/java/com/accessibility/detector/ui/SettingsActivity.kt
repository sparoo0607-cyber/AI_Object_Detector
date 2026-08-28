package com.accessibility.detector.ui

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.accessibility.detector.R
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * Settings and customization dashboard for SAHEY.
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        val prefs = getSharedPreferences("sahey_prefs", MODE_PRIVATE)

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
