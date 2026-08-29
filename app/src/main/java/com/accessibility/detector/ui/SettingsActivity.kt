package com.accessibility.detector.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.accessibility.detector.R
import com.accessibility.detector.communication.OfflineLanguageManager
import com.accessibility.detector.communication.SupportedLanguage
import com.accessibility.detector.databinding.ActivitySettingsBinding
import com.accessibility.detector.vision.gemini.GeminiConfig
import com.google.android.material.button.MaterialButton

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var offlineLanguageManager: OfflineLanguageManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        offlineLanguageManager = OfflineLanguageManager(this)

        setupListeners()
        refreshAllLanguagePackStatuses()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Offline Language Pack Buttons
        setupLanguagePackControl(SupportedLanguage.ENGLISH, binding.tvStatusEn, binding.btnDownloadEn)
        setupLanguagePackControl(SupportedLanguage.TELUGU, binding.tvStatusTe, binding.btnDownloadTe)
        setupLanguagePackControl(SupportedLanguage.HINDI, binding.tvStatusHi, binding.btnDownloadHi)
        setupLanguagePackControl(SupportedLanguage.TAMIL, binding.tvStatusTa, binding.btnDownloadTa)
        setupLanguagePackControl(SupportedLanguage.KANNADA, binding.tvStatusKn, binding.btnDownloadKn)
        setupLanguagePackControl(SupportedLanguage.MALAYALAM, binding.tvStatusMl, binding.btnDownloadMl)
    }

    private fun setupLanguagePackControl(
        language: SupportedLanguage,
        tvStatus: TextView,
        btnAction: MaterialButton
    ) {
        btnAction.setOnClickListener {
            offlineLanguageManager.checkModelDownloaded(language) { isDownloaded ->
                runOnUiThread {
                    if (isDownloaded) {
                        // Offer delete
                        btnAction.isEnabled = false
                        offlineLanguageManager.deleteModel(
                            language = language,
                            onSuccess = {
                                runOnUiThread {
                                    btnAction.isEnabled = true
                                    Toast.makeText(this, "${language.displayName} model removed.", Toast.LENGTH_SHORT).show()
                                    updateSinglePackUi(language, tvStatus, btnAction)
                                }
                            },
                            onError = { err ->
                                runOnUiThread {
                                    btnAction.isEnabled = true
                                    Toast.makeText(this, err, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    } else {
                        // Download
                        btnAction.isEnabled = false
                        btnAction.text = "⏳ ..."
                        tvStatus.text = "Downloading language pack (~30MB)..."
                        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_yellow))

                        offlineLanguageManager.downloadModel(
                            language = language,
                            onProgress = { msg ->
                                runOnUiThread {
                                    tvStatus.text = msg
                                }
                            },
                            onSuccess = {
                                runOnUiThread {
                                    btnAction.isEnabled = true
                                    Toast.makeText(this, "${language.displayName} pack ready for offline use!", Toast.LENGTH_SHORT).show()
                                    updateSinglePackUi(language, tvStatus, btnAction)
                                }
                            },
                            onError = { err ->
                                runOnUiThread {
                                    btnAction.isEnabled = true
                                    Toast.makeText(this, err, Toast.LENGTH_LONG).show()
                                    updateSinglePackUi(language, tvStatus, btnAction)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun refreshAllLanguagePackStatuses() {
        updateSinglePackUi(SupportedLanguage.ENGLISH, binding.tvStatusEn, binding.btnDownloadEn)
        updateSinglePackUi(SupportedLanguage.TELUGU, binding.tvStatusTe, binding.btnDownloadTe)
        updateSinglePackUi(SupportedLanguage.HINDI, binding.tvStatusHi, binding.btnDownloadHi)
        updateSinglePackUi(SupportedLanguage.TAMIL, binding.tvStatusTa, binding.btnDownloadTa)
        updateSinglePackUi(SupportedLanguage.KANNADA, binding.tvStatusKn, binding.btnDownloadKn)
        updateSinglePackUi(SupportedLanguage.MALAYALAM, binding.tvStatusMl, binding.btnDownloadMl)
    }

    private fun updateSinglePackUi(
        language: SupportedLanguage,
        tvStatus: TextView,
        btnAction: MaterialButton
    ) {
        offlineLanguageManager.checkModelDownloaded(language) { isDownloaded ->
            runOnUiThread {
                if (isDownloaded) {
                    tvStatus.text = "✓ Ready for offline translation"
                    tvStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
                    btnAction.text = "Delete"
                    btnAction.setBackgroundColor(ContextCompat.getColor(this, R.color.surface_dark))
                    btnAction.setTextColor(ContextCompat.getColor(this, R.color.accent_red))
                } else {
                    tvStatus.text = "↓ Not downloaded (~30MB)"
                    tvStatus.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
                    btnAction.text = "↓ Download"
                    btnAction.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_green))
                    btnAction.setTextColor(ContextCompat.getColor(this, R.color.black))
                }
            }
        }
    }
}
