package com.accessibility.detector

import android.Manifest
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
import com.accessibility.detector.databinding.ActivityMainBinding
import com.accessibility.detector.ml.DetectionResult
import com.accessibility.detector.ml.ObjectDetectorHelper
import com.accessibility.detector.tts.TtsManager
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), ObjectDetectorHelper.DetectorListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var objectDetectorHelper: ObjectDetectorHelper
    private lateinit var ttsManager: TtsManager
    private lateinit var cameraExecutor: ExecutorService

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            hidePermissionPrompt()
            startCamera()
        } else {
            showPermissionPrompt()
            Toast.makeText(this, R.string.permission_denied_message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        initTts()
        initObjectDetector()
        setupListeners()

        checkAndRequestPermissions()
    }

    private fun initTts() {
        ttsManager = TtsManager(this) { _ ->
            runOnUiThread {
                if (ttsManager.isMuted) {
                    binding.tvLiveLabel.text = getString(R.string.status_muted)
                    binding.tvLiveLabel.setTextColor(ContextCompat.getColor(this, R.color.accent_red))
                }
            }
        }
    }

    private fun initObjectDetector() {
        objectDetectorHelper = ObjectDetectorHelper(
            context = this,
            threshold = 0.50f,
            maxResults = 4,
            numThreads = 2,
            detectorListener = this
        )
    }

    private fun setupListeners() {
        binding.btnMuteToggle.setOnClickListener {
            val newMuteState = !ttsManager.isMuted
            ttsManager.isMuted = newMuteState
            updateMuteButtonUi(newMuteState)
        }

        binding.btnGrantPermission.setOnClickListener {
            checkAndRequestPermissions()
        }
    }

    private fun updateMuteButtonUi(isMuted: Boolean) {
        if (isMuted) {
            binding.btnMuteToggle.text = getString(R.string.unmute)
            binding.btnMuteToggle.setIconResource(R.drawable.ic_speaker_off)
            binding.btnMuteToggle.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.accent_red)
            )
            binding.btnMuteToggle.contentDescription = getString(R.string.unmute)
            binding.tvLiveLabel.text = getString(R.string.status_muted)
            binding.tvLiveLabel.setTextColor(ContextCompat.getColor(this, R.color.accent_red))
        } else {
            binding.btnMuteToggle.text = getString(R.string.mute)
            binding.btnMuteToggle.setIconResource(R.drawable.ic_speaker)
            binding.btnMuteToggle.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.accent_green)
            )
            binding.btnMuteToggle.contentDescription = getString(R.string.mute)
            binding.tvLiveLabel.text = getString(R.string.status_scanning)
            binding.tvLiveLabel.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
        }
    }

    private fun checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            hidePermissionPrompt()
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
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

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                // Preview use-case
                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
                    }

                // ImageAnalysis use-case
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                            objectDetectorHelper.detectObjects(imageProxy)
                        }
                    }

                // Default to REAR camera
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

    override fun onResults(
        results: List<DetectionResult>,
        inferenceTime: Long,
        imageHeight: Int,
        imageWidth: Int
    ) {
        runOnUiThread {
            // Update overlay with all detected boxes
            binding.overlayView.setResults(results, imageHeight, imageWidth)

            // Update FPS & inference info
            binding.tvFpsText.text = "Inference: ${inferenceTime}ms | Real-time On-Device"

            if (results.isNotEmpty()) {
                // Find primary / highest-confidence detected object
                val primaryDetection = results.maxByOrNull { it.score }
                if (primaryDetection != null) {
                    val label = primaryDetection.label
                    val scorePct = (primaryDetection.score * 100).toInt()

                    if (!ttsManager.isMuted) {
                        binding.tvLiveLabel.text = getString(R.string.status_speaking, label)
                        binding.tvLiveLabel.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
                    }

                    binding.tvDetailText.text = "Confidence: $scorePct% | ${results.size} object(s) in view"

                    // Feed to TTS Debouncing / Cooldown engine
                    ttsManager.considerSpeaking(label, primaryDetection.score)
                }
            } else {
                binding.overlayView.clear()
                ttsManager.onNoObjectsDetected()

                if (!ttsManager.isMuted) {
                    binding.tvLiveLabel.text = getString(R.string.status_scanning)
                    binding.tvLiveLabel.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
                    binding.tvDetailText.text = "Point camera towards objects to hear their names."
                }
            }
        }
    }

    override fun onError(error: String) {
        runOnUiThread {
            Log.e(TAG, "Detector error: $error")
            binding.tvLiveLabel.text = "⚠️ Error: $error"
            binding.tvLiveLabel.setTextColor(ContextCompat.getColor(this, R.color.accent_red))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        objectDetectorHelper.close()
        ttsManager.shutdown()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
