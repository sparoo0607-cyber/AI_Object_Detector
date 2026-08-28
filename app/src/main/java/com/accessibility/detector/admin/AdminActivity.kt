package com.accessibility.detector.admin

import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import com.accessibility.detector.R
import com.accessibility.detector.core.SahayConfig
import java.text.SimpleDateFormat
import java.util.*

/**
 * SAHAY ADMIN — Control Center for this device. Same SahayConfig
 * (SharedPreferences) the SEE/LISTEN screens read, so every change
 * here takes effect the next time SEE or LISTEN runs — no separate
 * sync step, no backend, matching the web prototype's "config-driven,
 * not hardcoded" principle. Built programmatically (no XML) to keep
 * this dense settings screen fast to build and easy to extend.
 *
 * Prototype-level passcode gate only, per the directive's own
 * guidance not to over-engineer auth within a 24-hour build.
 */
class AdminActivity : AppCompatActivity() {

    private val EVENT_TYPES = listOf(
        "object_detected", "text_detected", "currency_detected", "speech_detected",
        "sound_soft", "sound_sustained", "sound_impulsive", "low_confidence"
    )
    private val bg = 0xFF1B1B2F.toInt()
    private val card = 0xFF272740.toInt()
    private val amber = 0xFFE8A33D.toInt()
    private val cream = 0xFFF5F1E8.toInt()
    private val muted = 0xFF9A94AE.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SahayConfig.init(this)
        showLockScreen()
    }

    // ---------------- lock screen ----------------
    private fun showLockScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(bg)
            setPadding(48, 48, 48, 48)
        }
        val title = TextView(this).apply {
            text = "SAHAY ADMIN"
            setTextColor(amber)
            textSize = 24f
            gravity = Gravity.CENTER
        }
        val note = TextView(this).apply {
            text = "Control center for organizations, NGOs and schools deploying SAHAY.\nDemo passcode: sahay-admin"
            setTextColor(muted)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 32)
        }
        val input = EditText(this).apply {
            hint = "Admin passcode"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(cream)
            setHintTextColor(muted)
        }
        val error = TextView(this).apply {
            text = "Incorrect passcode."
            setTextColor(0xFFE2796B.toInt())
            visibility = View.GONE
            setPadding(0, 12, 0, 0)
        }
        val btn = Button(this).apply {
            text = "Unlock"
            setBackgroundColor(amber)
            setTextColor(bg)
            setPadding(0, 24, 0, 24)
        }
        btn.setOnClickListener {
            if (input.text.toString().trim() == "sahay-admin") showDashboard()
            else error.visibility = View.VISIBLE
        }
        root.addView(title)
        root.addView(note)
        root.addView(input, LinearLayout.LayoutParams(600, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(error)
        root.addView(btn, LinearLayout.LayoutParams(600, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 20 })
        setContentView(root)
    }

    // ---------------- dashboard ----------------
    private fun showDashboard() {
        val scroll = NestedScrollView(this).apply { setBackgroundColor(bg) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 48, 36, 80)
        }
        scroll.addView(col)

        col.addView(sectionTitle("SAHAY Admin — Control Center"))
        col.addView(bodyText(configVersionLine()))

        col.addView(cardTitle("System status"))
        col.addView(statusCard())

        col.addView(cardTitle("Language"))
        col.addView(languageCard())

        col.addView(cardTitle("Feature flags"))
        col.addView(featureFlagsCard())

        col.addView(cardTitle("AI models"))
        col.addView(modelsCard())

        col.addView(cardTitle("Confidence thresholds"))
        col.addView(thresholdsCard())

        col.addView(cardTitle("Attention rules — priority & cooldown"))
        col.addView(attentionCard())

        col.addView(cardTitle("Offline readiness"))
        col.addView(offlineCard())

        col.addView(cardTitle("Audit log"))
        col.addView(auditCard())

        val resetBtn = Button(this).apply {
            text = "Reset to defaults"
            setBackgroundColor(card)
            setTextColor(muted)
            setOnClickListener { SahayConfig.resetToDefaults(); showDashboard() }
        }
        col.addView(resetBtn, marginParams(top = 24))

        setContentView(scroll)
    }

    private fun configVersionLine() = "SharedPreferences-backed · applies the next time SEE/LISTEN runs"

    // ---- helpers to build the card look ----
    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text; setTextColor(0xFFFFFFFF.toInt()); textSize = 20f; setTypeface(typeface, android.graphics.Typeface.BOLD)
    }
    private fun bodyText(text: String) = TextView(this).apply {
        this.text = text; setTextColor(muted); textSize = 11f; setPadding(0, 4, 0, 20)
    }
    private fun cardTitle(text: String) = TextView(this).apply {
        this.text = text; setTextColor(amber); textSize = 13f; setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, 20, 0, 8)
    }
    private fun marginParams(top: Int = 0) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = top }
    private fun cardBox(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(card)
        setPadding(28, 24, 28, 24)
    }
    private fun rowLabel(text: String) = TextView(this).apply { this.text = text; setTextColor(cream); textSize = 13f }
    private fun smallMuted(text: String) = TextView(this).apply { this.text = text; setTextColor(muted); textSize = 10.5f }

    // ---------------- status ----------------
    private fun statusCard(): View {
        val box = cardBox()
        val pm = packageManager
        val hasCamera = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        val hasMic = pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
        listOf(
            "SAHAY Core" to "Online",
            "Camera" to if (hasCamera) "Detected" else "Not detected",
            "Microphone" to if (hasMic) "Detected" else "Not detected",
            "Object detection (TFLite)" to "Bundled — offline",
            "OCR (ML Kit)" to "Bundled — offline (English + Hindi)",
            "Live captions (Whisper)" to "Bundled — offline, primary (cloud SpeechRecognizer is the fallback)",
        ).forEach { (k, v) -> box.addView(statusRow(k, v)) }
        return box
    }
    private fun statusRow(label: String, value: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, 6, 0, 6)
        addView(rowLabel(label).apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        addView(TextView(this@AdminActivity).apply { text = value; setTextColor(muted); textSize = 12f })
    }

    // ---------------- language ----------------
    private fun languageCard(): View {
        val box = cardBox()
        box.addView(smallMuted("Default language"))
        val group = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val langs = listOf("en" to "English", "hi" to "Hindi", "te" to "Telugu")
        langs.forEach { (code, label) ->
            val rb = RadioButton(this).apply {
                text = label; setTextColor(cream)
                isChecked = SahayConfig.defaultLanguage == code
                setOnClickListener { SahayConfig.defaultLanguage = code }
            }
            group.addView(rb)
        }
        box.addView(group, marginParams(top = 8))
        box.addView(smallMuted("Telugu on-device OCR isn't available yet (ML Kit has no Telugu script model) — Telugu TTS/voice works if the device has the voice pack installed.").also { it.setPadding(0, 12, 0, 0) })
        return box
    }

    // ---------------- feature flags ----------------
    private fun featureFlagsCard(): View {
        val box = cardBox()
        val flags = listOf("objects" to "Object detection", "ocr" to "Signboard OCR", "currency" to "Currency recognition", "captions" to "Live captions", "soundAlerts" to "Sound alerts")
        flags.forEach { (key, label) ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 8) }
            row.addView(rowLabel(label).apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
            val sw = Switch(this).apply {
                isChecked = SahayConfig.isFeatureEnabled(key)
                setOnCheckedChangeListener { _, checked -> SahayConfig.setFeatureEnabled(key, checked) }
            }
            row.addView(sw)
            box.addView(row)
        }

        // Online enhancement is off by default, on a separate row with
        // its own explanation — this is not offline, not free, and not
        // required, unlike everything above it.
        val configured = com.accessibility.detector.BuildConfig.GEMINI_API_KEY.isNotBlank()
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 8) }
        row.addView(rowLabel(if (configured) "Online enhancement (Gemini)" else "Online enhancement (no API key set)")
            .apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        row.addView(Switch(this).apply {
            isEnabled = configured
            isChecked = configured && SahayConfig.isFeatureEnabled("onlineEnhancement")
            setOnCheckedChangeListener { _, checked -> SahayConfig.setFeatureEnabled("onlineEnhancement", checked) }
        })
        box.addView(row)
        box.addView(smallMuted("Off by default — every other capability above is local, offline and free. This one sends a downsized camera frame to Gemini every ~8s (only while SEE is open) for a richer spoken description; it needs internet and a configured key, and never blocks or replaces local detection."))
        return box
    }

    // ---------------- models ----------------
    private fun modelsCard(): View {
        val box = cardBox()
        listOf(
            "Object Detector — SSD MobileNet (TFLite)" to "READY · offline · real trained model · 80 COCO categories · scene-composed (\"cup on the bench\"), not isolated labels",
            "OCR — ML Kit Text Recognition" to "READY · offline · real trained model · English + Hindi (Devanagari); Telugu not yet supported on-device",
            "Currency — MobileNet classifier (TFLite)" to "READY · offline · real trained model · ₹50/100/200/500/2000",
            "Sound events — YAMNet (TFLite, via MediaPipe)" to "READY · offline · real trained model · 521 AudioSet classes, mapped to horn/siren/alarm/doorbell alerts",
            "Live captions — Whisper tiny multilingual (TFLite)" to "READY · offline, primary · real trained model · auto-detects English/Hindi/Telugu from audio",
            "Live captions — Android SpeechRecognizer" to "CLOUD · automatic fallback only, used if Whisper's model hasn't finished loading yet",
            "Scene enhancement — Gemini 2.0 Flash" to "ONLINE, OPT-IN · off by default · richer natural-language descriptions on top of local detection, never a replacement",
        ).forEach { (name, meta) ->
            box.addView(TextView(this).apply { text = name; setTextColor(cream); textSize = 13f; setPadding(0, 10, 0, 2); setTypeface(typeface, android.graphics.Typeface.BOLD) })
            box.addView(smallMuted(meta))
        }
        return box
    }

    // ---------------- thresholds ----------------
    private fun thresholdsCard(): View {
        val box = cardBox()
        val rows = listOf(
            "ocr_low" to "OCR — low confidence floor",
            "ocr_medium" to "OCR — medium confidence floor",
            "currency_low" to "Currency — confidence floor",
            "sound_low" to "Sound — ambient floor",
            "sound_medium" to "Sound — alert floor",
        )
        rows.forEach { (key, label) ->
            val valueLabel = TextView(this).apply { setTextColor(amber); textSize = 12f }
            fun refresh(v: Int) { valueLabel.text = "$v%" }
            refresh((SahayConfig.getThreshold(key) * 100).toInt())
            val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 14, 0, 4) }
            header.addView(rowLabel(label).apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
            header.addView(valueLabel)
            box.addView(header)
            val seek = SeekBar(this).apply {
                max = 100
                progress = (SahayConfig.getThreshold(key) * 100).toInt()
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) { refresh(progress) }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) { SahayConfig.setThreshold(key, (sb?.progress ?: 0) / 100f) }
                })
            }
            box.addView(seek)
        }
        return box
    }

    // ---------------- attention rules ----------------
    private fun attentionCard(): View {
        val box = cardBox()
        box.addView(smallMuted("Priority ranks interruption (higher speaks over lower). Cooldown suppresses repeat announcements of the same content."))
        EVENT_TYPES.forEach { type ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 14, 0, 6); gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply { text = type; setTextColor(cream); textSize = 11.5f; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })

            val priorityInput = EditText(this).apply {
                setText(SahayConfig.getPriority(type).toString())
                inputType = InputType.TYPE_CLASS_NUMBER
                setTextColor(cream)
                layoutParams = LinearLayout.LayoutParams(90, LinearLayout.LayoutParams.WRAP_CONTENT)
                hint = "priority"
            }
            val cooldownInput = EditText(this).apply {
                setText(SahayConfig.getCooldownMs(type).toString())
                inputType = InputType.TYPE_CLASS_NUMBER
                setTextColor(cream)
                layoutParams = LinearLayout.LayoutParams(110, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = 8 }
                hint = "cooldown ms"
            }
            priorityInput.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) priorityInput.text.toString().toIntOrNull()?.let { SahayConfig.setPriority(type, it.coerceIn(1, 10)) }
            }
            cooldownInput.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) cooldownInput.text.toString().toLongOrNull()?.let { SahayConfig.setCooldownMs(type, it.coerceAtLeast(0)) }
            }
            row.addView(priorityInput)
            row.addView(cooldownInput)
            box.addView(row)
        }
        return box
    }

    // ---------------- offline readiness ----------------
    private fun offlineCard(): View {
        val box = cardBox()
        listOf(
            "Object detection" to "FULLY OFFLINE",
            "Signboard OCR (English/Hindi)" to "FULLY OFFLINE",
            "Currency recognition" to "FULLY OFFLINE",
            "Text-to-speech" to "FULLY OFFLINE",
            "Sound alerts" to "FULLY OFFLINE",
            "Live captions (Whisper, primary)" to "FULLY OFFLINE",
        ).forEach { (name, tag) ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 6, 0, 6) }
            row.addView(rowLabel(name).apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
            row.addView(TextView(this).apply {
                text = tag; textSize = 10f
                setTextColor(if (tag.startsWith("FULLY")) 0xFF5FBE8A.toInt() else 0xFFE2796B.toInt())
            })
            box.addView(row)
        }
        return box
    }

    // ---------------- audit ----------------
    private fun auditCard(): View {
        val box = cardBox()
        val log = SahayConfig.getAudit()
        if (log.isEmpty()) { box.addView(smallMuted("No configuration changes yet.")); return box }
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        log.take(20).forEach { entry ->
            box.addView(TextView(this).apply {
                text = "${fmt.format(Date(entry.ts))}  ${entry.message}"
                setTextColor(cream); textSize = 11.5f; setPadding(0, 5, 0, 5)
            })
        }
        return box
    }
}
