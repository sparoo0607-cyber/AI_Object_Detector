package com.accessibility.detector.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
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
import com.accessibility.detector.core.DetectionResult
import com.accessibility.detector.core.PerceptionEvent
import com.accessibility.detector.core.PerceptionType
import com.accessibility.detector.core.PermissionManager
import com.accessibility.detector.databinding.ActivityVisionAssistBinding
import com.accessibility.detector.vision.ExtractedTextBlock
import com.accessibility.detector.vision.SignDetection
import com.accessibility.detector.vision.VisionOrchestrator
import com.accessibility.detector.vision.VisionUiCallback
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Category 1: Vision Assist Activity.
 * Rear camera stream with Hybrid Local AI (SSD Object Detection, Danger Radar, Sign Language, OCR)
 * + Gemini Multimodal Visual Reasoning Engine.
 * Supports Dedicated "Sign Mode" triggered by Voice Command, Double Volume Click, or UI Button.
 */
class VisionAssistActivity : AppCompatActivity(), VisionUiCallback {

    private lateinit var binding: ActivityVisionAssistBinding
    private lateinit var orchestrator: VisionOrchestrator
    private lateinit var cameraExecutor: ExecutorService

    private var lastVolumePressTimestamp: Long = 0L

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (PermissionManager.hasVisionPermissions(this)) {
            hidePermissionPrompt()
            startCamera()
        } else {
            showPermissionPrompt()
            Toast.makeText(this, "Camera & Audio permissions required for Vision Assist", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVisionAssistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()
        orchestrator = VisionOrchestrator(this, this)

        setupListeners()
        checkPermissionsAndStartCamera()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnMuteToggle.setOnClickListener {
            val isMuted = !orchestrator.announcementManager.isMuted
            orchestrator.setMute(isMuted)
            updateMuteButtonUi(isMuted)
        }

        binding.btnSafetyShield.setOnClickListener {
            val isActive = orchestrator.toggleSafetyShieldMode()
            updateSafetyShieldUi(isActive)
        }

        // Dedicated Sign Mode Toggle
        binding.btnSignModeToggle.setOnClickListener {
            orchestrator.toggleSignMode()
        }

        // Sign Mode Action Buttons
        binding.btnSpeakSentence.setOnClickListener {
            orchestrator.signSentenceBuilder.forceSpeakSentence()
        }

        binding.btnClearSentence.setOnClickListener {
            orchestrator.signSentenceBuilder.clearSentence()
        }

        binding.btnExitSignMode.setOnClickListener {
            orchestrator.setSignMode(false)
        }

        // Ask Gemini AI Button
        binding.btnAskGemini.setOnClickListener {
            orchestrator.hapticManager.playNormalPulse()
            orchestrator.askGeminiWhatIsAroundMe()
        }

        // Scan & Read Text Button
        binding.btnForceReadText.setOnClickListener {
            orchestrator.readTextImmediately()
        }

        binding.btnConfirmRead.setOnClickListener {
            orchestrator.readTextImmediately()
        }

        binding.btnCancelRead.setOnClickListener {
            orchestrator.cancelVoiceConfirmation()
        }

        binding.btnGrantPermission.setOnClickListener {
            checkPermissionsAndStartCamera()
        }
    }

    /**
     * Volume Key Double-Click Listener:
     * Pressing Volume Up or Volume Down 2 times within 500ms toggles Sign Language Mode!
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val now = SystemClock.uptimeMillis()
            if (now - lastVolumePressTimestamp < 500L) {
                // Double click confirmed -> Toggle Sign Mode!
                orchestrator.toggleSignMode()
                lastVolumePressTimestamp = 0L
                return true
            } else {
                lastVolumePressTimestamp = now
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun checkPermissionsAndStartCamera() {
        if (PermissionManager.hasVisionPermissions(this)) {
            hidePermissionPrompt()
            startCamera()
        } else {
            permissionLauncher.launch(PermissionManager.VISION_PERMISSIONS)
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
                Log.d(TAG, "CameraX successfully bound in Vision Assist")
            } catch (e: Exception) {
                Log.e(TAG, "CameraX binding failed: ${e.message}", e)
                Toast.makeText(this, "Camera error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // --- VisionUiCallback ---
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
                else -> ContextCompat.getColor(this, R.color.accent_green)
            }

            val icon = when (event.type) {
                PerceptionType.DANGER -> "⚠️"
                PerceptionType.TEXT -> "📖"
                PerceptionType.SIGN -> "🤟"
                else -> "👁️"
            }

            binding.tvLiveLabel.text = "$icon ${event.spokenText}"
            binding.tvLiveLabel.setTextColor(color)
            binding.tvDetailText.text = "Perception: ${event.type.name} • Pri: ${event.priority}"
        }
    }

    override fun onSignModeStateChanged(isActive: Boolean) {
        runOnUiThread {
            if (isActive) {
                binding.signSentenceCard.visibility = View.VISIBLE
                binding.tvModeTitle.text = "🤟 SIGN LANGUAGE MODE"
                binding.tvModeTitle.setTextColor(ContextCompat.getColor(this, R.color.accent_cyan))
                binding.btnSignModeToggle.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.accent_cyan)
                )
                binding.btnSignModeToggle.iconTint = ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.black)
                )
                binding.tvLiveLabel.text = "🤟 Sign Mode: Detecting gestures & assembling sentences..."
                binding.tvLiveLabel.setTextColor(ContextCompat.getColor(this, R.color.accent_cyan))
                binding.tvDetailText.text = "Detecting Sign Language Only • Volume x2 or 'Turn off sign mode' to exit"
            } else {
                binding.signSentenceCard.visibility = View.GONE
                binding.tvModeTitle.text = "👁️ VISION ASSIST"
                binding.tvModeTitle.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
                binding.btnSignModeToggle.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.surface_card)
                )
                binding.btnSignModeToggle.iconTint = ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.accent_cyan)
                )
                binding.tvLiveLabel.text = getString(R.string.status_scanning)
                binding.tvLiveLabel.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
                binding.tvDetailText.text = "Objects • Danger Radar • Sign Language • Image OCR"
            }
        }
    }

    override fun onSignSentenceUpdated(sentence: String, latestWord: String) {
        runOnUiThread {
            binding.tvCurrentSignBadge.text = if (latestWord.isNotBlank()) "Gesture: $latestWord" else "Ready"
            binding.tvAccumulatedSentence.text = if (sentence.isNotBlank()) "\"$sentence\"" else "Show sign gestures to the camera — words will assemble into sentences here..."
        }
    }

    override fun onVoiceConfirmationState(isWaitingForConfirmation: Boolean, prompt: String) {
        runOnUiThread {
            if (isWaitingForConfirmation && !orchestrator.signSentenceBuilder.isSignModeActive) {
                binding.voiceConfirmPromptCard.visibility = View.VISIBLE
                binding.tvVoiceConfirmPrompt.text = prompt
            } else {
                binding.voiceConfirmPromptCard.visibility = View.GONE
            }
        }
    }

    override fun onGeminiReasoningStatus(isAnalyzing: Boolean, statusMessage: String) {
        runOnUiThread {
            if (isAnalyzing) {
                binding.geminiStatusBanner.visibility = View.VISIBLE
                binding.tvGeminiStatus.text = statusMessage
            } else {
                binding.geminiStatusBanner.visibility = View.GONE
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        orchestrator.shutdown()
    }

    companion object {
        private const val TAG = "VisionAssistActivity"
    }
}
