# SAHEY — Multimodal AI Accessibility Platform 🌐👁️👂🗣️

> **See it. Hear it. Understand it.**  
> An intelligent, multi-category native Android accessibility suite combining **On-Device Edge AI** with **Google Gemini Multimodal Cloud Intelligence** to empower Blind, Visually Impaired, Deaf, Hard-of-Hearing, and Speech-Impaired individuals.

---

## 🌟 Core Feature Modules

### 1. 👁️ Category 1: Vision Assist (See & Perceive)
*Empowering blind and visually impaired users to navigate, read, and converse with their surroundings.*
- **🤖 Google Gemini Multimodal Visual Q&A**: Live conversational visual assistance (*"Ask AI"*, *"What do you see?"*, *"Explain what is in front of me"*).
- **⚡ On-Device Edge Object Detection**: Real-time object localization powered by TensorFlow Lite SSD MobileNet with spatial position mapping (Left / Center / Right / Proximity).
- **⚠️ Hazard Radar & Collision Preemption**: Instant audible and tactile SOS warnings for critical dangers (smoke, fire, approaching vehicles, staircase drops).
- **📖 Interactive OCR Text Reader**: Automatic signboard and document reading with smart voice confirmation flow (*"Text detected. Would you like me to read it?"*).
- **🤟 Sign Language Sentence Builder**: Real-time ASL alphabet and gesture recognition combining MediaPipe 42-feature landmark classifier and 24-class CNN to assemble and vocalize complete sentences.

---

### 2. 👂 Category 2: Sound Assist (Hear & Monitor)
*Empowering deaf and hard-of-hearing users with real-time acoustic awareness.*
- **📊 Real-Time Decibel (dB) Sound Radar**: Dynamic audio metering visualizer tracking environmental sound intensity.
- **🚨 Emergency Siren & Horn Detection**: Low-latency threshold detection for emergency sirens, car horns, smoke alarms, and ambient danger.
- **🎙️ Live Continuous Speech Captions**: Multi-language live speech-to-text transcription streaming on-device with zero latency.

---

### 3. 🗣️ Category 3: Speak & Translate (Communicate)
*Bridging language, speech, and hearing barriers.*
- **🌍 100% Offline Neural Translation**: Zero-latency on-device translation powered by Google ML Kit across 6 major Indian and international languages:
  - **English (EN)**
  - **Telugu (TE - తెలుగు)**
  - **Hindi (HI - हिन्दी)**
  - **Tamil (TA - தமிழ்)**
  - **Kannada (KN - ಕನ್ನಡ)**
  - **Malayalam (ML - മലയാളം)**
- **⚡ Automatic Language Identification**: Instant on-device classification of spoken and written languages.
- **💬 One-Tap Tactile Accessibility Phrase Grids**: Quick emergency, medical, and conversational phrase buttons with TTS vocalization.
- **🔄 Two-Way Speech Translation Loop**: Seamless two-way spoken conversation translation.

---

## 🛠️ Technical Stack & Architecture

| Layer | Technologies & Frameworks |
|---|---|
| **Platform** | Native Android (Kotlin 1.9+, Min SDK 26, Target SDK 34) |
| **Multimodal Cloud AI** | Google Gemini 1.5 Flash / 2.0 Flash REST API (`generativelanguage.googleapis.com`) |
| **Edge AI & Computer Vision** | TensorFlow Lite (2.13.0), Google ML Kit Text Recognition, MediaPipe Landmarks |
| **On-Device NLP & Translation** | Google ML Kit On-Device Translation (`com.google.mlkit:translate`), ML Kit Language ID |
| **Audio & Speech Engine** | Android `SpeechRecognizer` (Continuous Loop) + `AudioRecord` dB Analyzer + Android `TextToSpeech` (`UtteranceProgressListener`) |
| **Tactile Feedback** | Multi-Waveform `VibrationEffect` Haptic Engine (SOS, Double Pulse, Staccato, Light Click) |
| **UI & Typography** | Google Material Design 3, ViewBinding, High-Contrast Typography System |

---

## 🏗️ Project Architecture

```
app/src/main/java/com/accessibility/detector/
├── core/                       # Core Architecture & Orchestration
│   ├── AnnouncementManager.kt  # Priority Queuing & Speech Preemption Engine
│   ├── HapticManager.kt        # Multi-Pattern Tactile Vibration System
│   ├── PriorityManager.kt      # Event Prioritization (Critical > Danger > Text > Objects)
│   ├── ModelManager.kt         # TFLite Asset & Lifecycle Controller
│   └── Models.kt               # Domain Data Models (PerceptionEvent, DetectionResult)
├── vision/                     # Category 1: Vision Assist & AI
│   ├── VisionOrchestrator.kt   # Central CameraX, ML & Voice Coordinator
│   ├── ObjectDetectionEngine.kt# SSD MobileNet TFLite Inference
│   ├── DangerDetectionEngine.kt# Real-Time Spatial Hazard Reasoning
│   ├── TextReaderEngine.kt     # ML Kit OCR Text Processing
│   ├── SignLanguageEngine.kt   # Sign Gesture Classifier (MediaPipe & CNN)
│   ├── SignSentenceBuilder.kt  # Continuous Sign-to-Sentence Assembly
│   └── gemini/                 # Google Gemini Multimodal Cloud AI
│       ├── GeminiConfig.kt     # System Prompts & Key Configuration
│       ├── GeminiVisionService.kt # Multi-Model Resilient Network Engine
│       └── GeminiVisionEngine.kt  # Image Preprocessing & Spatial Reasoning
├── sound/                      # Category 2: Sound Assist & Captions
│   ├── SoundRadarEngine.kt     # Real-Time Decibel & Frequency Metering
│   ├── SpeechRecognitionEngine.kt # Continuous Multi-Language STT Engine
│   └── LiveSpeechListener.kt   # Speech Streaming Callbacks
├── communication/              # Category 3: Speak & Translate
│   ├── CommunicationManager.kt # Offline ML Kit Translation & Language ID
│   ├── OfflineLanguageManager.kt# On-Device Language Pack Management
│   ├── SupportedLanguage.kt    # Language Enums & Locale Mappings
│   └── TtsManager.kt           # Multi-Language Neural Voice Synthesizer
└── ui/                         # Activities & High-Contrast Design System
    ├── HomeActivity.kt         # 3-Category Navigation Hub
    ├── VisionAssistActivity.kt # Vision Assist HUD & Real-Time Camera Screen
    ├── SoundAssistActivity.kt  # Sound Radar & Live Captioning Screen
    ├── CommunicationActivity.kt# Speak & Translate Two-Way Screen
    ├── SettingsActivity.kt     # Accessibility Preferences & Voice Controls
    └── SplashActivity.kt       # Fast Cold-Start Splash Screen
```

---

## 🚀 Building & Running from Source

### Prerequisites
- **Android Studio** (Ladybug / Koala / Iguana or newer)
- **JDK 17**
- **Android SDK** API 26 through 34+

### Build Signed Release APK
```bash
# Clone the repository
git clone https://github.com/sparoo0607-cyber/AI_Object_Detector.git
cd AI_Object_Detector

# Build the signed Release APK
./gradlew assembleRelease
```
The output APK is generated at:
`app/build/outputs/apk/release/app-release.apk`

---

## 🔒 Privacy & Permissions

SAHEY is designed with a **Privacy-First Architecture**:
- **Zero Continuous Cloud Upload**: Camera and audio data are processed 100% on-device by default.
- **On-Demand Gemini AI**: Visual frames are only dispatched to Google Gemini when the user explicitly issues an AI voice question or taps *"Ask AI"*.
- **Offline Reliability**: Object detection, sign language recognition, emergency sirens, and multi-language translations operate completely without internet connectivity.

---

## 📄 License
This project is licensed under the Apache License 2.0.
