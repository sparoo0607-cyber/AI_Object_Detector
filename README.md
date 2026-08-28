# SAHAY — See it. Hear it. Understand it. (Android)

This started as a real-time on-device object detector for blind/low-vision
users (still fully intact — see below). It's now the SAHAY platform: **SEE**
and **LISTEN**, with SAHAY auto-routing what it perceives, plus a config-driven
**Admin Control Center**, mirroring the SAHAY web prototype's architecture:

```
PERCEPTION -> CONTEXT -> DECISION -> ACTION
```

## What's real vs. what's disclosed as a stand-in

Every capability below either runs a genuine on-device model, or is labelled
honestly as a heuristic/beta in `Admin > AI Models` — nothing is faked.

| Capability | How | Offline? |
|---|---|---|
| Object detection | SSD MobileNet, TFLite Task Vision (original app) — real trained model | ✅ fully |
| Signboard OCR | Google ML Kit Text Recognition — Latin + Devanagari (Hindi) — real trained model | ✅ fully |
| Currency | TFLite MobileNet classifier (`assets/currency_model.tflite`), ₹50/100/200/500/2000 — real trained model, not a heuristic | ✅ fully |
| Sound alerts | YAMNet (`assets/yamnet.tflite`) via MediaPipe Audio Classifier, 521 AudioSet classes mapped to horn/siren/alarm/doorbell — real trained model | ✅ fully |
| Text-to-speech | Android `TextToSpeech`, EN/HI/TE | ✅ fully (voice pack dependent) |
| Live captions | Whisper tiny multilingual (TFLite) — real trained model, auto-detects EN/HI/TE — primary; Android `SpeechRecognizer` (cloud) is the automatic fallback | ✅ fully offline (primary path) |

**Scene composition, not isolated labels.** SEE's object detector now
reasons about the real bounding-box geometry of what it sees to compose
relational descriptions — "coffee cup on the bench" — instead of announcing
"Cup." then "Bench." one at a time. See `ml/SceneComposer.kt`.

**Optional online enhancement (Gemini 2.0 Flash) — off by default.** Every
capability above is local/offline/free; Gemini adds a richer natural-language
scene description on top, only when Admin turns it on and a key is
configured (`local.properties` → `gemini.api.key`, never committed). See
`enhance/GeminiEnhancer.kt` and Admin > Feature flags.

**No manual "operate the AI" buttons.** SEE perceives continuously (like the
original object detector always did) — OCR and currency now run
automatically every ~1.3s against the live camera feed, no capture tap.
LISTEN starts automatically the moment the screen opens (after the one-time
mic permission prompt) and keeps listening until you leave — no Start/Stop
button. The only taps left are navigation (Home → SEE/LISTEN/Admin, Back)
and the object-detector's mute toggle.

**Known gap:** ML Kit has no on-device Telugu-script OCR model, so Telugu
signboards aren't read yet — Telugu TTS output still works. Disclosed in
Admin, not hidden.

## Architecture

```
app/src/main/java/com/accessibility/detector/
├── core/            SahayConfig, ContextEngine, DecisionEngine, ActionEngine — the pipeline
├── ocr/              OcrHelper (ML Kit)
├── currency/         CurrencyMatcher (color heuristic)
├── sound/            SoundClassifier (AudioRecord amplitude)
├── stt/               SttHelper (SpeechRecognizer)
├── home/             HomeActivity — SEE / LISTEN / Admin, no manual tool picking
├── listen/           ListenActivity — captions + sound alerts
├── admin/            AdminActivity — Control Center (SharedPreferences-backed, live-applies)
├── MainActivity.kt   SEE — continuous object detection (original) + on-demand
│                       signboard/currency capture, auto-routed by confidence
├── ml/                ObjectDetectorHelper (original)
├── tts/               TtsManager (original, continuous-detection speech)
└── ui/                OverlayView (original)
```

`SahayConfig` (SharedPreferences) is the single source of truth for language,
feature flags, confidence thresholds, and per-event priority/cooldown/output
channels — the same shape as `shared/config.js` in the web prototype. Admin
writes it; SEE/LISTEN read it live, no separate sync step.

**Verification note:** compiled clean with `gradle assembleDebug` (BUILD
SUCCESSFUL). Installed and launched on an Android emulator inside this
session — Home screen confirmed running with no crash, real screenshot
taken. The emulator in this sandbox is too resource-starved to reliably
drive SEE/LISTEN interaction (the whole OS was throwing ANRs on unrelated
system apps, not just this one) — **SEE, LISTEN, and Admin still need a
real-phone test before the demo**, especially the new real-model paths
(currency TFLite classifier, YAMNet sound events).

---

## Original object-detector README (still accurate for that feature)

An on-device, real-time AI object detection app built for visually impaired users. When pointed at objects, the app instantly identifies them using an on-device computer vision model and announces their names through the phone speaker using Text-to-Speech (TTS).

---

## 🚀 Key Features

* **⚡ Real-Time On-Device Object Detection**:
  * Powered by **SSD MobileNet COCO** via TensorFlow Lite Task Vision.
  * 100% offline — requires zero internet connection.
  * Detects 80 common categories (*Person, Laptop, Cell Phone, Chair, Bottle, Backpack, Cup, Book, Mouse, Keyboard, etc.*).
* **🔊 Intelligent Text-to-Speech (TTS) Voice Engine**:
  * **Anti-Repetition Cooldown (2.5s)**: Eliminates stutter and speech spam when looking steadily at an object.
  * **Fast Object Switching**: Announces new dominant objects immediately.
  * **Tactile Haptic Feedback**: Gentle vibration pulse on each spoken announcement.
  * **Mute / Unmute**: Dedicated accessible button with high-contrast visual and audio state indicators.
* **♿ High-Contrast Accessibility UI**:
  * Fullscreen rear-camera feed (`CameraX`).
  * Neon green / cyan bounding boxes with corner accents and large label tags (`Laptop 89%`).
  * Floating bottom status card.

---

## 🛠️ Tech Stack & Architecture

* **Language**: Kotlin
* **Camera API**: AndroidX CameraX (Core, Camera2, Lifecycle, View)
* **Inference Engine**: TensorFlow Lite Task Vision (`org.tensorflow:tensorflow-lite-task-vision:0.4.4`)
* **Audio**: Native Android `TextToSpeech` (`android.speech.tts.TextToSpeech`)
* **Target SDK**: Android 14 (API 34), Min SDK 24 (Android 7.0+)

---

## 📦 APK Installation

Directly installable APK builds are generated in:
* `AI-Voice-Detector-Release.apk` (Signed Universal Release Build)
* `app/build/outputs/apk/release/app-release.apk`

To install via ADB:
```bash
adb install -r AI-Voice-Detector-Release.apk
```

---

## 🏗️ Building from Source

1. Clone this repository:
   ```bash
   git clone https://github.com/sparoo0607-cyber/AI_Object_Detector.git
   ```
2. Open in **Android Studio** or compile using Gradle:
   ```bash
   ./gradlew assembleRelease
   ```
