# 👁️ SAHEY — AI Multimodal Accessibility Assistant (prototype)

> *"See it. Hear it. Understand it. Communicate."*

An on-device Android accessibility assistant with three isolated modes: **Vision Assist**
(camera), **Sound & Language Assist** (microphone), and **Speak & Translate** (text/TTS).

This is a **working prototype**, not a certified assistive device. The section
[Feature honesty](#feature-honesty) states exactly which features are model-backed and
which are best-effort heuristics. **Do not rely on any hazard, sound, or sign output for
physical safety.**

---

## Build & run

### Requirements
- Android Studio Hedgehog (2023.1) or newer, **or** CLI with JDK 17 + Android SDK 34
- The Gradle wrapper JAR is not committed — see [`gradle/wrapper/README.md`](gradle/wrapper/README.md)
  (Android Studio regenerates it automatically on first sync)
- A physical Android device (API 24+); camera + microphone features do not work on the emulator

### Steps
```bash
# 1. Get the wrapper jar (once) — see gradle/wrapper/README.md, or just open in Android Studio
# 2. Point the build at your SDK
echo "sdk.dir=/absolute/path/to/Android/sdk" > local.properties

# 3. (Optional) download the two large model assets — the app runs without them,
#    with the affected features disabled / degraded
cd app/src/main/assets
curl -L -o hand_landmarker.task https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task
curl -L -o yamnet.tflite        https://storage.googleapis.com/mediapipe-models/audio_classifier/yamnet/float32/1/yamnet.tflite
cd ../../../..

# 4. Build
./gradlew assembleDebug           # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug            # build + install on a connected device
```

`assembleRelease` also works: it signs with your `keystore.properties` (copy from
`keystore.properties.example`), or falls back to the bundled prototype key if that file
is absent. **The bundled key in `app/release.keystore` is for local prototype builds only —
never ship it.**

### Optional: Gemini visual verification
Vision Assist can escalate a suspicious frame to Gemini 1.5 Flash for a second opinion on
fire/smoke/hazards and for "what's around me?". Paste an API key in **Settings → Gemini API
Key**. Without a key the app stays fully on-device and fire/smoke alerts become advisory
("possible fire ahead, please verify") rather than definitive.

---

## Feature honesty

| Feature | How it actually works | Reliability |
|---|---|---|
| **Object detection** | SSD MobileNet (COCO 80-class) via TFLite Task Vision | Solid |
| **Spatial cue** ("on your left") | bounding-box thirds + area-based proximity | Solid |
| **OCR read-aloud** | Google ML Kit on-device text recognition + voice-confirmation flow | Solid |
| **Live captions** | Android `SpeechRecognizer` (on-device when the language pack is installed) | Good — depends on the OS speech engine |
| **Live / text translation** | Google ML Kit on-device translation (+ small phrase dictionary, + optional Gemini online) | Good when the language pack is downloaded |
| **Speak & Translate board** | TTS + quick phrases + two-way translated dialog | Solid |
| **Sign language** | **MediaPipe Hands** → 21 landmarks → landmark TFLite classifier. Reports *unavailable* if `hand_landmarker.task` or the sign model is missing. The bundled 42-feature model only knows **A / B / L**; add a broader landmark model for full A–Z. | Real pipeline, limited vocabulary |
| **Environmental sounds** | **YAMNet** (AudioSet, 521 classes) via TFLite Task Audio, mapped to alert categories with a confidence gate + debounce. Falls back to a labelled low-confidence zero-crossing heuristic if `yamnet.tflite` is absent. | Real model; mic is shared with speech recognition (see caveat) |
| **Fire / smoke radar** | on-device colour/HSV pre-filter that only *nominates* a frame; the announcement comes from Gemini (definitive if confirmed) or, with no key, an advisory phrase at reduced priority | Advisory unless Gemini-confirmed |
| **"Slippery floor"** | specular-highlight ratio on the lower frame — a weak cue; surfaced as "the floor looks reflective, please check" at navigation priority | Advisory only |

### Known caveats
- **Microphone contention:** YAMNet sound recognition and live speech recognition both open
  the mic. On some devices only one works at a time. Toggle
  `SoundOrchestrator.isEnvironmentalSoundEnabled = false` if captions stop.
- **Launcher icon:** adaptive icon on API 26+, vector fallback (`mipmap-anydpi`) on
  API 24–25. No rasterised PNG densities are shipped, so the icon is crisp but simple.
- **No automated tests.**
- Sign vocabulary is A/B/L until a fuller landmark model is added (see
  [`app/src/main/assets/README.md`](app/src/main/assets/README.md)).

---

## Architecture

```
ui/            Activities + custom OverlayView (per-mode, fully isolated)
core/          Models, priority queue, cooldowns, haptics, inference scheduler
vision/        VisionOrchestrator → ObjectDetection, DangerDetection, SignLanguage, TextReader
  gemini/      selective cloud visual-reasoning layer (optional)
sound/         SoundOrchestrator → SpeechRecognition, SoundAwareness (YAMNet), translation
communication/ TTS, ML Kit translation, offline language-pack manager, language detection
```

- `VisionOrchestrator` fans one camera frame out to all vision engines via listener
  interfaces, throttled per-model by `InferenceScheduler`.
- `AnnouncementManager` + `PriorityManager` enforce a priority queue with per-type
  cooldowns so a "vehicle approaching" alert preempts "chair ahead".

## Permissions
`CAMERA` (Vision only), `RECORD_AUDIO` (Sound + voice commands), `VIBRATE`. All requested at
runtime; each mode degrades gracefully if denied.
