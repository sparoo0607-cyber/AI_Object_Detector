# 👁️ SAHEY — AI Multimodal Accessibility Assistant

> **"See it. Hear it. Understand it. Communicate."**

SAHEY is an on-device, real-time AI multimodal accessibility assistant structured into **3 clearly separated categories** with complete mode isolation:

---

## 🌟 3 Main Accessibility Categories

### 👁️ Category 1 — VISION ASSIST (`VisionAssistActivity`)
* **Target Audience**: Visually impaired users
* **Primary Input**: **CAMERA ONLY** (Rear camera via CameraX)
* **Features**:
  1. **Real-time Object Detection**: SSD MobileNet COCO 80 classes with intelligent priority cooldown.
  2. **⚠️ AI Danger & Hazard Radar**: Vehicle warnings (*"Vehicle on your left."*), obstacle alerts, fire detection, and slippery floor warnings (*"Warning. Possible slippery floor ahead."*).
  3. **🤟 Sign Language Interpretation**: Real-time hand gesture interpreter with temporal smoothing (*"Hello"*, *"Thank you"*, *"Yes"*, *"No"*, *"Help"*, *"Stop"*, *"Water"*, *"Food"*).
  4. **📖 Image Text Reading (OCR)**: Google ML Kit on-device OCR with interactive **Voice Confirmation flow** (*"Text detected. Would you like me to read it?"* -> answers *"Yes"* / *"Read"*).

---

### 🔊 Category 2 — SOUND & LANGUAGE ASSIST (`SoundAssistActivity`)
* **Target Audience**: Deaf & hard-of-hearing users
* **Primary Input**: **MICROPHONE ONLY** (**NO CAMERA**)
* **Features**:
  1. **🎧 Environmental Sound Awareness**: Real-time acoustic classifier for Car Horns, Sirens, Smoke Alarms, Doorbells, Knocks, Glass Breaks.
  2. **🚗 Sound → Multi-Pattern Vibration**: Distinct haptic vibration alerts for sirens, horns, alarms, doorbells.
  3. **Live Speech Transcription**: Real-time live captions of surrounding spoken words.
  4. **Live Translation**: Instant translation of spoken speech into target language (English, Telugu, Hindi, Tamil, Kannada, Malayalam, Spanish).

---

### 🗣️ Category 3 — SPEAK & TRANSLATION ASSIST (`CommunicationActivity`)
* **Target Audience**: Non-verbal users / communication assistance
* **Primary Input**: **TEXT / TTS / TRANSLATION / MIC** (**NO CAMERA**)
* **Features**:
  1. **User Types → SAHEY Speaks**: Large text input with Play, Repeat, Stop, Clear.
  2. **One-Tap Quick Phrases**: Large accessible buttons for *HELP*, *WATER*, *FOOD*, *HOSPITAL*, *THANK YOU*, *YES/NO*.
  3. **Text Translation + Speech**: Instant multilingual translation + voice synthesis.
  4. **Two-Way Conversation**: Type ↔ Speak ↔ Mic Listen ↔ Translate ↔ Read dialog.

---

## 📦 APK Installation

* **🚀 Signed Universal Release APK**: [**`SAHEY-Release.apk`**](file:///d:/Hackathon/DETECTOR/SAHEY-Release.apk) (`95.1 MB`)

### Install via ADB:
```bash
adb install -r "d:\Hackathon\DETECTOR\SAHEY-Release.apk"
```

### Wi-Fi Direct Download:
Open this URL in your phone's browser on the same Wi-Fi network:
```
http://10.10.84.90:8080/SAHEY-Release.apk
```
