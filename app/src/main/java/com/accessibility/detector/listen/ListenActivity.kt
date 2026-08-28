package com.accessibility.detector.listen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.accessibility.detector.R
import com.accessibility.detector.core.ActionEngine
import com.accessibility.detector.core.DecisionEngine
import com.accessibility.detector.core.SahayConfig
import com.accessibility.detector.core.SahayEvent
import com.accessibility.detector.databinding.ActivityListenBinding
import com.accessibility.detector.sound.SoundEventClassifier
import com.accessibility.detector.stt.SttHelper
import com.accessibility.detector.stt.WhisperCaptioner

/**
 * LISTEN — fully autonomous: opens, asks for mic permission once if
 * needed, then starts listening on its own. No "Start Listening"
 * button — SAHAY decides what to do with what it hears.
 *
 * Captions: Whisper (tiny, multilingual, on-device, offline — see
 * stt/WhisperCaptioner.kt) is the primary engine, auto-detecting
 * English/Hindi/Telugu from the audio itself. If the model hasn't
 * finished loading yet (first run: copying ~40MB out of assets) or
 * fails on a very low-end device, SAHAY falls back to the Android
 * cloud SpeechRecognizer automatically rather than going silent.
 *
 * Perception (Whisper/STT + real YAMNet acoustic classification) ->
 * Decision -> Action, same pipeline shape as SEE.
 */
class ListenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityListenBinding
    private lateinit var actionEngine: ActionEngine
    private lateinit var sttHelper: SttHelper
    private lateinit var whisperCaptioner: WhisperCaptioner
    private lateinit var soundClassifier: SoundEventClassifier
    private var listening = false
    private var usingWhisper = false

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) doStartListening() else Toast.makeText(this, "Microphone permission is required for LISTEN", Toast.LENGTH_LONG).show() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SahayConfig.init(this)
        binding = ActivityListenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        actionEngine = ActionEngine(this)
        actionEngine.listener = object : ActionEngine.Listener {
            override fun onCaption(text: String, interim: Boolean) {
                binding.tvCaption.text = text
                binding.tvCaption.setTextColor(ContextCompat.getColor(this@ListenActivity, R.color.white))
                binding.tvCaption.alpha = if (interim) 0.65f else 1f
            }
            override fun onVisual(decision: com.accessibility.detector.core.SahayDecision) {
                if (decision.event.type == "sound_sustained" || decision.event.type == "sound_impulsive") {
                    showSoundAlert(decision.event.content)
                }
            }
        }

        // Primary: Whisper, offline. Starts loading immediately so it's
        // usually ready by the time the mic-permission prompt is answered.
        whisperCaptioner = WhisperCaptioner(this)
        whisperCaptioner.listener = object : WhisperCaptioner.Listener {
            override fun onReady() { binding.tvListeningState.text = "● Listening automatically · offline" }
            override fun onPartialTranscript(text: String) = handleSpeech(text, interim = false)
            override fun onError(message: String) {
                android.util.Log.w("ListenActivity", "Whisper unavailable, falling back to cloud STT: $message")
            }
        }
        whisperCaptioner.initialize()

        // Fallback: Android SpeechRecognizer (cloud on most devices).
        sttHelper = SttHelper(this)
        sttHelper.listener = object : SttHelper.Listener {
            override fun onPartial(text: String) = handleSpeech(text, interim = true)
            override fun onFinal(text: String) = handleSpeech(text, interim = false)
            override fun onError(message: String) {
                if (message.isNotEmpty()) binding.tvSttNote.visibility = android.view.View.VISIBLE
            }
        }

        soundClassifier = SoundEventClassifier(this)
        soundClassifier.listener = object : SoundEventClassifier.Listener {
            override fun onLevel(pct: Float) { binding.meterBar.progress = (pct * 100).toInt() }
            override fun onSoundEvent(type: String, label: String, confidence: Float) {
                val event = SahayEvent(type = type, confidence = confidence, content = label, source = "microphone")
                val decision = DecisionEngine.decide(event)
                actionEngine.execute(decision)
            }
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.tvSttNote.text = getString(R.string.stt_net_note)

        requestMicAndStart()
    }

    private fun handleSpeech(text: String, interim: Boolean) {
        val event = SahayEvent(type = "speech_detected", confidence = 0.85f, content = text, source = "microphone", interim = interim)
        val decision = DecisionEngine.decide(event)
        actionEngine.execute(decision)
    }

    private fun requestMicAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            doStartListening()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun doStartListening() {
        if (listening) return
        binding.tvSttNote.visibility = android.view.View.GONE
        soundClassifier.start()

        if (whisperCaptioner.isReady()) {
            usingWhisper = true
            whisperCaptioner.start()
            binding.tvListeningState.text = "● Listening automatically · offline"
        } else {
            usingWhisper = false
            sttHelper.start(SahayConfig.defaultLanguage)
            binding.tvListeningState.text = "● Listening automatically"
        }
        listening = true
    }

    private fun stopListening() {
        if (!listening) return
        if (usingWhisper) whisperCaptioner.stop() else sttHelper.stop()
        soundClassifier.stop()
        listening = false
        binding.meterBar.progress = 0
        binding.tvListeningState.text = "Stopped"
    }

    private fun showSoundAlert(text: String) {
        binding.tvSoundAlert.text = "🔔 $text"
        binding.tvSoundAlert.visibility = android.view.View.VISIBLE
        binding.tvSoundAlert.postDelayed({ binding.tvSoundAlert.visibility = android.view.View.GONE }, 2200)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
        whisperCaptioner.close()
        actionEngine.shutdown()
    }
}
