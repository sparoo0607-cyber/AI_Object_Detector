package com.accessibility.detector.admin

import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import com.accessibility.detector.core.SahayConfig
import java.text.SimpleDateFormat
import java.util.*

/**
 * SAHAY ADMIN — Control Center for this device. Same SahayConfig
 * (SharedPreferences) the SEE/LISTEN screens read, so every change
 * here takes effect the next time SEE or LISTEN runs.
 */
class AdminActivity : AppCompatActivity() {

    private val EVENT_TYPES = listOf(
        "object_detected", "text_detected", "currency_detected", "speech_detected",
        "sound_soft", "sound_sustained", "sound_impulsive", "low_confidence"
    )

    // Cool Midnight & Slate Color Palette
    private val bg = 0xFF0A0D14.toInt()
    private val card = 0xFF161D2B.toInt()
    private val border = 0xFF26334D.toInt()
    private val mint = 0xFF10B981.toInt()
    private val cyan = 0xFF0EA5E9.toInt()
    private val cream = 0xFFF8FAFC.toInt()
    private val muted = 0xFF94A3B8.toInt()
    private val amber = 0xFFF59E0B.toInt()
    private val red = 0xFFF43F5E.toInt()

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
            setTextColor(cream)
            textSize = 24f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val note = TextView(this).apply {
            text = "Device Configuration & System Calibration\nDemo passcode: sahay-admin"
            setTextColor(muted)
            textSize = 12.5f
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 32)
        }
        val input = EditText(this).apply {
            hint = "Admin passcode"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(cream)
            setHintTextColor(muted)
            background = makeCardDrawable(card, border, 12f)
            setPadding(32, 28, 32, 28)
        }
        val error = TextView(this).apply {
            text = "Incorrect passcode."
            setTextColor(red)
            visibility = View.GONE
            setPadding(0, 12, 0, 0)
        }
        val btn = Button(this).apply {
            text = "Unlock"
            background = makeCardDrawable(mint, mint, 14f)
            setTextColor(bg)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
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
        root.addView(btn, LinearLayout.LayoutParams(600, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 24 })
        setContentView(root)
    }

    // ---------------- dashboard ----------------
    private fun showDashboard() {
        val scroll = NestedScrollView(this).apply { setBackgroundColor(bg) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 44, 36, 80)
        }
        scroll.addView(col)

        col.addView(sectionTitle("SAHAY Admin — Control Center"))
        col.addView(bodyText(configVersionLine()))

        col.addView(cardTitle("System Status"))
        col.addView(statusCard())

        col.addView(cardTitle("Language Preferences"))
        col.addView(languageCard())

        col.addView(cardTitle("Feature Controls"))
        col.addView(featureFlagsCard())

        col.addView(cardTitle("AI & Perception Models"))
        col.addView(modelsCard())

        col.addView(cardTitle("Confidence Thresholds"))
        col.addView(thresholdsCard())

        col.addView(cardTitle("Priority & Cooldown Rules"))
        col.addView(attentionCard())

        col.addView(cardTitle("Offline Readiness Status"))
        col.addView(offlineCard())

        col.addView(cardTitle("Audit Log"))
        col.addView(auditCard())

        val resetBtn = Button(this).apply {
            text = "Reset All Settings to Default"
            background = makeCardDrawable(card, border, 14f)
            setTextColor(muted)
            setOnClickListener { SahayConfig.resetToDefaults(); showDashboard() }
        }
        col.addView(resetBtn, marginParams(top = 28))

        setContentView(scroll)
    }

    private fun configVersionLine() = "Realtime local configuration · Persisted in SharedPreferences"

    // ---- helpers to build the modern card look ----
    private fun makeCardDrawable(fillColor: Int, strokeColor: Int, radiusDp: Float): GradientDrawable {
        val density = resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fillColor)
            setStroke((1.5f * density).toInt(), strokeColor)
            cornerRadius = radiusDp * density
        }
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(cream)
        textSize = 20f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private fun bodyText(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(muted)
        textSize = 11.5f
        setPadding(0, 4, 0, 16)
    }

    private fun cardTitle(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(mint)
        textSize = 13f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setPadding(0, 20, 0, 8)
    }

    private fun marginParams(top: Int = 0) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = top }

    private fun cardBox(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = makeCardDrawable(card, border, 18f)
        setPadding(32, 28, 32, 28)
    }

    private fun rowLabel(text: String) = TextView(this).apply { this.text = text; setTextColor(cream); textSize = 13f }
    private fun smallMuted(text: String) = TextView(this).apply { this.text = text; setTextColor(muted); textSize = 11f }

    // ---------------- status ----------------
    private fun statusCard(): View {
        val box = cardBox()
        val pm = packageManager
        val hasCamera = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        val hasMic = pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
        listOf(
            "SAHAY Core Engine" to "Online · Active",
            "Camera Sensor" to if (hasCamera) "Ready" else "Unavailable",
            "Microphone Sensor" to if (hasMic) "Ready" else "Unavailable",
            "Object Detection (TFLite)" to "Bundled · Offline",
            "OCR (ML Kit)" to "Bundled · Offline (EN + HI)",
            "Live Captions (Whisper)" to "Bundled · Offline Primary",
        ).forEach { (k, v) -> box.addView(statusRow(k, v)) }
        return box
    }

    private fun statusRow(label: String, value: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, 8, 0, 8)
        addView(rowLabel(label).apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        addView(TextView(this@AdminActivity).apply { text = value; setTextColor(cyan); textSize = 12f })
    }

    // ---------------- language ----------------
    private fun languageCard(): View {
        val box = cardBox()
        box.addView(smallMuted("Default Voice & Text Language"))
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
        box.addView(group, marginParams(top = 10))
        box.addView(smallMuted("On-device OCR supports English and Hindi; Telugu uses local speech synthesizer if installed.").also { it.setPadding(0, 10, 0, 0) })
        return box
    }

    // ---------------- feature flags ----------------
    private fun featureFlagsCard(): View {
        val box = cardBox()
        val flags = listOf(
            "objects" to "Object Detection",
            "ocr" to "Signboard OCR",
            "currency" to "Currency Recognition",
            "captions" to "Live Speech Captions",
            "soundAlerts" to "Sound Event Alerts"
        )
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

        val configured = com.accessibility.detector.BuildConfig.GEMINI_API_KEY.isNotBlank()
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 8) }
        row.addView(rowLabel(if (configured) "Online Scene Enhancement (Gemini)" else "Online Enhancement (No API Key)")
            .apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        row.addView(Switch(this).apply {
            isEnabled = configured
            isChecked = configured && SahayConfig.isFeatureEnabled("onlineEnhancement")
            setOnCheckedChangeListener { _, checked -> SahayConfig.setFeatureEnabled("onlineEnhancement", checked) }
        })
        box.addView(row)
        box.addView(smallMuted("Optional online multimodal reasoning via Gemini 2.0 Flash for richer scene context."))
        return box
    }

    // ---------------- models ----------------
    private fun modelsCard(): View {
        val box = cardBox()
        listOf(
            "Object Detector — SSD MobileNet" to "READY · 80 Categories · Scene-Composed · Offline",
            "OCR Engine — ML Kit Text Recognition" to "READY · English + Hindi · Offline",
            "Currency Classifier — MobileNet" to "READY · ₹50 / 100 / 200 / 500 / 2000 · Offline",
            "Acoustics — YAMNet Audio Classifier" to "READY · 521 Sound Classes · Siren / Horn Alerts · Offline",
            "Speech Captioning — Whisper Tiny Multilingual" to "READY · On-Device Neural Speech Engine · Offline",
            "Scene Intelligence — Gemini 2.0 Flash" to "ONLINE · Deep Visual & Spatial Reasoning",
        ).forEach { (name, meta) ->
            box.addView(TextView(this).apply { text = name; setTextColor(cream); textSize = 13f; setPadding(0, 8, 0, 2); typeface = android.graphics.Typeface.DEFAULT_BOLD })
            box.addView(smallMuted(meta))
        }
        return box
    }

    // ---------------- thresholds ----------------
    private fun thresholdsCard(): View {
        val box = cardBox()
        val rows = listOf(
            "ocr_low" to "OCR — Low Confidence Floor",
            "ocr_medium" to "OCR — Medium Confidence Floor",
            "currency_low" to "Currency — Confidence Floor",
            "sound_low" to "Sound — Ambient Floor",
            "sound_medium" to "Sound — Alert Floor",
        )
        rows.forEach { (key, label) ->
            val valueLabel = TextView(this).apply { setTextColor(mint); textSize = 12f }
            fun refresh(v: Int) { valueLabel.text = "$v%" }
            refresh((SahayConfig.getThreshold(key) * 100).toInt())
            val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 12, 0, 4) }
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
        box.addView(smallMuted("Priority ranks interruption (higher speaks first). Cooldown suppresses duplicate notices."))
        EVENT_TYPES.forEach { type ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 10, 0, 6); gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply { text = type; setTextColor(cream); textSize = 11.5f; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })

            val priorityInput = EditText(this).apply {
                setText(SahayConfig.getPriority(type).toString())
                inputType = InputType.TYPE_CLASS_NUMBER
                setTextColor(cream)
                background = makeCardDrawable(bg, border, 8f)
                setPadding(16, 12, 16, 12)
                layoutParams = LinearLayout.LayoutParams(100, LinearLayout.LayoutParams.WRAP_CONTENT)
                hint = "pri"
            }
            val cooldownInput = EditText(this).apply {
                setText(SahayConfig.getCooldownMs(type).toString())
                inputType = InputType.TYPE_CLASS_NUMBER
                setTextColor(cream)
                background = makeCardDrawable(bg, border, 8f)
                setPadding(16, 12, 16, 12)
                layoutParams = LinearLayout.LayoutParams(130, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = 8 }
                hint = "ms"
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
            "Object Detection" to "FULLY OFFLINE",
            "Signboard OCR (English/Hindi)" to "FULLY OFFLINE",
            "Currency Recognition" to "FULLY OFFLINE",
            "Text-to-Speech" to "FULLY OFFLINE",
            "Sound Alerts & Acoustics" to "FULLY OFFLINE",
            "Live Captions (Whisper AI)" to "FULLY OFFLINE",
        ).forEach { (name, tag) ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 6, 0, 6) }
            row.addView(rowLabel(name).apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
            row.addView(TextView(this).apply {
                text = tag; textSize = 10.5f; typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(if (tag.startsWith("FULLY")) mint else red)
            })
            box.addView(row)
        }
        return box
    }

    // ---------------- audit ----------------
    private fun auditCard(): View {
        val box = cardBox()
        val log = SahayConfig.getAudit()
        if (log.isEmpty()) { box.addView(smallMuted("No configuration changes recorded.")); return box }
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
