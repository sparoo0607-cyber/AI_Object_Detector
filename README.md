# 👁️ AI Accessibility Voice Object Detector (Android)

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
