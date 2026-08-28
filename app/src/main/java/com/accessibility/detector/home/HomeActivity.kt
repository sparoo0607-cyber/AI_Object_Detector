package com.accessibility.detector.home

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.accessibility.detector.MainActivity
import com.accessibility.detector.R
import com.accessibility.detector.admin.AdminActivity
import com.accessibility.detector.core.SahayConfig
import com.accessibility.detector.databinding.ActivityHomeBinding
import com.accessibility.detector.listen.ListenActivity

/**
 * SAHAY home — the whole app entry point. No manual "pick a tool"
 * menu: just SEE and LISTEN. SAHAY decides what's in front of the
 * camera or microphone once you're inside either screen.
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SahayConfig.init(this)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cardSee.setOnClickListener { startActivity(Intent(this, MainActivity::class.java)) }
        binding.cardListen.setOnClickListener { startActivity(Intent(this, ListenActivity::class.java)) }
        binding.btnAdmin.setOnClickListener { startActivity(Intent(this, AdminActivity::class.java)) }

        updateOfflineBadge()
    }

    override fun onResume() {
        super.onResume()
        updateOfflineBadge()
    }

    /**
     * Real offline-readiness signal, not a guess: object detection
     * ships with the APK (always ready), OCR/currency/TTS run
     * on-device once ML Kit's bundled model initializes, and live
     * captions are the one feature that genuinely needs a connection.
     */
    private fun updateOfflineBadge() {
        binding.tvOfflineBadge.text = getString(R.string.offline_ready_summary)
    }
}
