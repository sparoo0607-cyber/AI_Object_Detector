package com.accessibility.detector.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.accessibility.detector.R
import com.accessibility.detector.core.OrchestratorUiCallback
import com.accessibility.detector.core.SaheyAIOrchestrator
import com.accessibility.detector.core.SubsystemStatus
import com.accessibility.detector.databinding.ActivityMainBinding
import com.accessibility.detector.detection.DetectionResult
import com.accessibility.detector.detection.PerceptionEvent
import com.accessibility.detector.detection.PerceptionType
import com.accessibility.detector.ocr.ExtractedTextBlock
import com.accessibility.detector.sign.SignDetection
import com.accessibility.detector.translation.SupportedLanguage
import com.accessibility.detector.translation.TranslationResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), OrchestratorUiCallback {

    private lateinit var binding: ActivityMainBinding
    private lateinit var orchestrator: SaheyAIOrchestrator
    private lateinit var cameraExecutor: ExecutorService

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false

        if (cameraGranted) {
            hidePermissionPrompt()
            startCamera()
        } else {
            showPermissionPrompt()
            Toast.makeText(this, R.string.permission_denied_message, Toast.LENGTH_LONG).show()
        }

        if (audioGranted) {
            orchestrator.startSoundAwareness()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()
        orchestrator = SaheyAIOrchestrator(this, this)

        setupListeners()
        checkFirstLaunch()
        checkAndRequestPermissions()
    }

    private fun setupListeners() {
        // Mute / Unmute
        binding.btnMuteToggle.setOnClickListener {
            val isMuted = !orchestrator.announcementManager.isMuted
            orchestrator.setMute(isMuted)
            updateMuteButtonUi(isMuted)
        }

        // Safety Shield (Emergency Mode)
        binding.btnSafetyShield.setOnClickListener {
            val isActive = orchestrator.toggleSafetyShieldMode()
            updateSafetyShieldUi(isActive)
        }

        // Read Text Now (OCR)
        binding.btnReadText.setOnClickListener {
            orchestrator.inferenceScheduler.forceRunOcr()
            orchestrator.hapticManager.playTextCapturePulse()
            binding.tvLiveLabel.text = "📖 Scanning for printed text…"
            binding.tvLiveLabel.setTextColor(ContextCompat.getColor(this, R.color.accent_yellow))
            Toast.makeText(this, "Scanning for visible text…", Toast.LENGTH_SHORT).show()
        }

        // Speech Recognition (Ask / Command)
        binding.btnListenSpeech.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                if (orchestrator.speechEngine.isListening) {
                    orchestrator.speechEngine.stopListening()
                } else {
                    orchestrator.speechEngine.startListening()
                    binding.tvLiveLabel.text = getString(R.string.speech_listening)
                    binding.tvLiveLabel.setTextColor(ContextCompat.getColor(this, R.color.accent_blue))
                }
            } else {
                permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            }
        }

        // Live Translate
        binding.btnTranslate.setOnClickListener {
            val samplePhrase = "Where is the exit?"
            val translation = orchestrator.translationEngine.translate(
                text = samplePhrase,
                sourceLang = SupportedLanguage.ENGLISH,
                targetLang = SupportedLanguage.TELUGU
            )
            onTranslationResult(translation)
            orchestrator.announcementManager.postEvent(
                PerceptionEvent(
                    type = PerceptionType.TRANSLATION,
                    label = "Translate: ${translation.translatedText}",
                    spokenText = "${translation.originalText} in Telugu is ${translation.translatedText}",
                    confidence = 1.0f,
                    priority = 70
                )
            )
        }

        // Demo Mode (Hackathon)
        binding.btnDemoMode.setOnClickListener {
            DemoDialog(this, orchestrator).show()
        }

        // Settings
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Grant Permission
        binding.btnGrantPermission.setOnClickListener {
            checkAndRequestPermissions()
        }
    }

    private fun checkFirstLaunch() {
        val prefs = getSharedPreferences("sahey_prefs", MODE_PRIVATE)
        val isFirstLaunch = prefs.getBoolean("is_first_launch", true)
        if (isFirstLaunch) {
            OnboardingDialog(this) {
                prefs.edit().putBoolean("is_first_launch", false).apply()
            }.show()
        }
    }

    private fun checkAndRequestPermissions() {
        val allGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            hidePermissionPrompt()
            startCamera()
            orchestrator.startSoundAwareness()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    private fun showPermissionPrompt() {
        binding.permissionContainer.visibility = View.VISIBLE
        binding.cameraPreview.visibility = View.GONE
        binding.overlayView.visibility = View.GONE
    }

    private fun hidePermissionPrompt() {
        binding.permissionContainer.visibility = View.GONE
        binding.cameraPreview.visibility = View.VISIBLE
        binding.overlayView.visibility = View.VISIBLE
    }

    private fun updateMuteButtonUi(isMuted: Boolean) {
        if (isMuted) {
            binding.btnMuteToggle.setIconResource(R.drawable.ic_speaker_off)
            binding.btnMuteToggle.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.accent_red)
            )
            binding.tvLiveLabel.text = getString(R.string.status_muted)
            binding.tvLiveLabel.setTextColor(ContextCompat.getColor(this, R.color.accent_red))
        } else {
            binding.btnMuteToggle.setIconResource(R.drawable.ic_speaker)
            binding.btnMuteToggle.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.accent_green)
            )
            binding.tvLiveLabel.text = getString(R.string.status_scanning)
            binding.tvLiveLabel.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
        }
    }

    private fun updateSafetyShieldUi(isActive: Boolean) {
        if (isActive) {
            binding.btnSafetyShield.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.accent_red)
            )
            binding.btnSafetyShield.iconTint = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.white)
            )
            binding.tvLiveLabel.text = getString(R.string.status_safety_mode)
            binding.tvLiveLabel.setTextColor(ContextCompat.getColor(this, R.color.accent_red))
        } else {
            binding.btnSafetyShield.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.surface_card)
            )
            binding.btnSafetyShield.iconTint = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.accent_red)
            )
            binding.tvLiveLabel.text = getString(R.string.status_scanning)
            binding.tvLiveLabel.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
                }

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                            orchestrator.processCameraFrame(imageProxy)
                        }
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
                Log.d(TAG, "CameraX successfully bound to rear camera")
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
                Toast.makeText(this, "Camera error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // --- Orchestrator UI Callbacks ---
    override fun onVisionResultsUpdated(
        objects: List<DetectionResult>,
        ocrBlocks: List<ExtractedTextBlock>,
        activeSign: SignDetection?,
        imageWidth: Int,
        imageHeight: Int
    ) {
        runOnUiThread {
            binding.overlayView.setPerceptionResults(
                detectedObjects = objects,
                detectedOcr = ocrBlocks,
                detectedSign = activeSign,
                imgWidth = imageWidth,
                imgHeight = imageHeight
            )
        }
    }

    override fun onLiveAnnouncement(event: PerceptionEvent) {
        runOnUiThread {
            if (orchestrator.announcementManager.isMuted) return@runOnUiThread

            val color = when (event.type) {
                PerceptionType.DANGER -> ContextCompat.getColor(this, R.color.accent_red)
                PerceptionType.TEXT -> ContextCompat.getColor(this, R.color.accent_yellow)
                PerceptionType.SIGN -> ContextCompat.getColor(this, R.color.accent_cyan)
                PerceptionType.SOUND -> ContextCompat.getColor(this, R.color.accent_blue)
                PerceptionType.TRANSLATION -> ContextCompat.getColor(this, R.color.accent_purple)
                else -> ContextCompat.getColor(this, R.color.accent_green)
            }

            val icon = when (event.type) {
                PerceptionType.DANGER -> "⚠️"
                PerceptionType.TEXT -> "📖"
                PerceptionType.SIGN -> "🤟"
                PerceptionType.SOUND -> "🔊"
                PerceptionType.TRANSLATION -> "🌍"
                else -> "👁️"
            }

            binding.tvLiveLabel.text = "$icon ${event.spokenText}"
            binding.tvLiveLabel.setTextColor(color)
            binding.tvDetailText.text = "Perception: ${event.type.name} • Confidence: ${(event.confidence * 100).toInt()}% • Priority: ${event.priority}"
        }
    }

    override fun onSpeechStatus(isListening: Boolean, text: String?) {
        runOnUiThread {
            if (isListening) {
                binding.tvLiveLabel.text = if (!text.isNullOrBlank()) "🎤 \"$text\"" else "🎤 Listening…"
                binding.tvLiveLabel.setTextColor(ContextCompat.getColor(this, R.color.accent_blue))
            } else if (!text.isNullOrBlank()) {
                binding.tvLiveLabel.text = "🗣️ Spoken: \"$text\""
                binding.tvLiveLabel.setTextColor(ContextCompat.getColor(this, R.color.accent_blue))
            }
        }
    }

    override fun onTranslationResult(result: TranslationResult) {
        runOnUiThread {
            binding.tvLiveLabel.text = "🌍 ${result.translatedText}"
            binding.tvLiveLabel.setTextColor(ContextCompat.getColor(this, R.color.accent_purple))
            binding.tvDetailText.text = "Original: \"${result.originalText}\" (${result.sourceLanguage.displayName} -> ${result.targetLanguage.displayName})"
        }
    }

    override fun onSubsystemStatusChanged(module: String, status: SubsystemStatus) {
        runOnUiThread {
            Log.d(TAG, "Subsystem updated: $module -> $status")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        orchestrator.shutdown()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
