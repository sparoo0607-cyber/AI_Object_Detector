package com.accessibility.detector

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.accessibility.detector.core.ActionEngine
import com.accessibility.detector.core.DecisionEngine
import com.accessibility.detector.core.SahayConfig
import com.accessibility.detector.core.SahayEvent
import com.accessibility.detector.currency.CurrencyClassifier
import com.accessibility.detector.enhance.GeminiEnhancer
import com.accessibility.detector.databinding.ActivityMainBinding
import com.accessibility.detector.ml.DetectionResult
import com.accessibility.detector.ml.ObjectDetectorHelper
import com.accessibility.detector.ml.SceneComposer
import com.accessibility.detector.ocr.OcrHelper
import com.accessibility.detector.tts.TtsManager
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), ObjectDetectorHelper.DetectorListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var objectDetectorHelper: ObjectDetectorHelper
    private lateinit var ttsManager: TtsManager
    private lateinit var cameraExecutor: ExecutorService

    // SEE — automatic signboard/currency perception (no capture button):
    // a separate SAHAY Core pipeline instance from the continuous
    // object-detection TTS above, so the proven always-on detector is
    // never touched. Runs on a timer against the latest camera frame,
    // exactly like object detection already runs continuously.
    private lateinit var ocrHelper: OcrHelper
    private lateinit var currencyClassifier: CurrencyClassifier
    private lateinit var seeActionEngine: ActionEngine
    @Volatile private var latestBitmap: Bitmap? = null
    // Latest results from each local model — collected silently,
    // passed to Gemini as context every 8s. No local TTS.
    @Volatile private var latestDetectionLabels: List<String> = emptyList()
    @Volatile private var latestOcrText: String? = null
    @Volatile private var latestCurrencyLabel: String? = null
    private var seeBusy = false
    private val seeHandler = Handler(Looper.getMainLooper())
    private val seeIntervalMs = 1300L
    private val seeLoop = object : Runnable {
        override fun run() {
            runSee()
            seeHandler.postDelayed(this, seeIntervalMs)
        }
    }

    // Central SAHAY Intelligence (Gemini 2.0 Flash Multimodal Brain)
    // Runs every 4 seconds against the latest camera frame + tool signals.
    private val enhanceIntervalMs = 4000L
    private val enhanceLoop = object : Runnable {
        override fun run() {
            runOnlineEnhancement()
            seeHandler.postDelayed(this, enhanceIntervalMs)
        }
    }

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

        SahayConfig.init(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        initTts()
        initObjectDetector()
        initSee()
        setupListeners()

        checkAndRequestPermissions()
    }

    private fun initSee() {
        ocrHelper = OcrHelper()
        currencyClassifier = CurrencyClassifier(this)
        seeActionEngine = ActionEngine(this)
        seeActionEngine.listener = object : ActionEngine.Listener {
            override fun onCaption(text: String, interim: Boolean) { /* SEE has no caption surface */ }
            override fun onVisual(decision: com.accessibility.detector.core.SahayDecision) {
                renderSeeResult(decision)
            }
        }
        // Start SAHAY Brain loop after 2 seconds
        seeHandler.postDelayed(enhanceLoop, 2000L)
    }

    /** SAHAY Multimodal Intelligence pass — collects all local model tool results
     * into a LocalContext and sends them to Gemini 2.0 Flash with system instructions. */
    private fun runOnlineEnhancement() {
        if (!GeminiEnhancer.isAvailable()) {
            Log.w("MainActivity", "Gemini not available — API key blank or not configured")
            return
        }
        val bmp = latestBitmap ?: return
        val ctx = GeminiEnhancer.LocalContext(
            detectedObjects = latestDetectionLabels,
            ocrText        = latestOcrText,
            currencyLabel  = latestCurrencyLabel
        )

        // Determine if device TTS engine supports Telugu; if not, use English so TTS audio is guaranteed to speak
        val teSupported = seeActionEngine.applyLanguage("te")
        val targetLang = if (teSupported) "te" else "en"

        GeminiEnhancer.describeScene(bmp, ctx, targetLang) { description ->
            seeActionEngine.speak(description, targetLang, priority = 5)
            binding.tvLiveLabel.text = description
            binding.tvLiveLabel.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            renderSeeResultRaw("SAHAY AI BRAIN", "Gemini 2.0 Flash · Realtime", true, description)
        }
    }

    /** Runs OCR + currency classifier on latest frame and stores
     * results silently — Gemini speaks them every 8s via LocalContext. */
    private fun runSee() {
        if (seeBusy) return
        val bmp = latestBitmap ?: return
        seeBusy = true

        val currency = currencyClassifier.classify(bmp)
        ocrHelper.recognize(bmp, SahayConfig.defaultLanguage) { ocrResult ->
            runOnUiThread {
                // Store whichever is more confident; Gemini will speak it
                if (ocrResult.confidence >= currency.confidence && ocrResult.text.isNotBlank()) {
                    latestOcrText = ocrResult.text
                    latestCurrencyLabel = null
                } else {
                    latestCurrencyLabel = currency.label
                    latestOcrText = null
                }
                seeBusy = false
            }
        }
    }

    private fun renderSeeResult(decision: com.accessibility.detector.core.SahayDecision) {
        val tag = if (decision.event.type == "text_detected") "SIGNBOARD TEXT" else "CURRENCY"
        if (decision.confidenceBand == "low") {
            renderSeeResultRaw(tag, getString(R.string.not_confident), false)
        } else {
            val pct = (decision.event.confidence * 100).toInt()
            renderSeeResultRaw(tag, "Confidence: $pct%", true, decision.event.content)
        }
    }
    /** RGBA_8888 ImageAnalysis frames -> Bitmap, without closing the
     * proxy (ObjectDetectorHelper still needs it after this call). */
    private fun captureLatestBitmap(imageProxy: ImageProxy) {
        try {
            val plane = imageProxy.planes[0]
            val buffer = plane.buffer.duplicate()
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * imageProxy.width

            val bitmap = Bitmap.createBitmap(
                imageProxy.width + rowPadding / pixelStride, imageProxy.height, Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            val cropped = if (rowPadding == 0) bitmap else Bitmap.createBitmap(bitmap, 0, 0, imageProxy.width, imageProxy.height)

            val rotation = imageProxy.imageInfo.rotationDegrees
            latestBitmap = if (rotation != 0) {
                val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true)
            } else cropped
        } catch (e: Exception) {
            Log.w(TAG, "Frame capture for SEE failed", e)
        }
    }

    private fun renderSeeResultRaw(tag: String, meta: String, ok: Boolean, text: String = "—") {
        binding.seeResultCard.visibility = View.VISIBLE
        binding.tvSeeResultTag.text = tag
        binding.tvSeeResultText.text = text
        binding.tvSeeResultMeta.text = meta
        binding.tvSeeResultMeta.setTextColor(
            ContextCompat.getColor(this, if (ok) R.color.accent_green else R.color.accent_yellow)
        )
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
                            captureLatestBitmap(imageProxy)
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
            // Update overlay with all detected boxes (silent — UI only).
            // Local TTS disabled; Gemini is the sole voice output.
            binding.overlayView.setResults(results, imageHeight, imageWidth)
            binding.tvFpsText.text = "Inference: ${inferenceTime}ms | Gemini-only mode"

            if (results.isNotEmpty()) {
                val primaryDetection = results.maxByOrNull { it.score }
                if (primaryDetection != null) {
                    // Store detection labels silently for Gemini LocalContext.
                    // No local TTS — Gemini speaks everything every 8s.
                    latestDetectionLabels = results.map { it.label }
                    binding.tvDetailText.text = "${results.size} object(s) detected | Gemini speaks every 8s"
                    binding.tvLiveLabel.text = getString(R.string.status_scanning)
                    binding.tvLiveLabel.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
                }
            } else {
                binding.overlayView.clear()
                binding.tvLiveLabel.text = getString(R.string.status_scanning)
                binding.tvLiveLabel.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
                binding.tvDetailText.text = "Waiting for Gemini to speak..."
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
        seeHandler.removeCallbacks(seeLoop)
        seeHandler.removeCallbacks(enhanceLoop)
        cameraExecutor.shutdown()
        objectDetectorHelper.close()
        ttsManager.shutdown()
        ocrHelper.close()
        currencyClassifier.close()
        seeActionEngine.shutdown()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
