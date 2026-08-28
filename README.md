# 👁️ SAHEY — AI Multimodal Accessibility Assistant

> **"See it. Hear it. Understand it."**

SAHEY is an on-device, real-time AI-powered multimodal accessibility assistant built for visually impaired and hearing-impaired users, providing environmental awareness, navigation safety, printed text reading, sign language recognition, acoustic hazard alerts, and multilingual speech translation.

---

## 🌟 7 Unified AI Perception Engines

1. **👁️ Real-Time On-Device Object Detection**:
   - Powered by **SSD MobileNet COCO** via TensorFlow Lite Task Vision.
   - Detects 80 common object categories (*Person, Chair, Laptop, Bottle, Backpack, Cell Phone, etc.*) offline with zero internet dependency.

2. **⚠️ Intelligent Danger & Hazard Radar**:
   - Analyzes mobility hazards: approaching vehicles (*Car, Bus, Truck, Motorcycle, Bicycle*), obstacles in walking line, pedestrian crosswalks.
   - Spatial risk reasoning with natural direction and proximity announcements (*"Warning! Car approaching on your left, very close!"*, *"Obstacle directly ahead"*).
   - Hazard announcements **preempt and interrupt** lower-priority speech.

3. **📖 Instant Printed Text Reader (OCR)**:
   - On-device OCR via Google ML Kit Text Recognition.
   - Reads street signs, notices, product packaging, menus, and documents.
   - Spatial prioritization of prominent/central text.

4. **🤟 Real-Time Sign Language Interpretation**:
   - Recognizes core sign gestures (*"Hello"*, *"Thank you"*, *"Yes"*, *"No"*, *"Help"*, *"Water"*, *"Food"*, *"Stop"*, *"I need help"*).
   - Requires temporal smoothing (held steadily for 300ms+) to prevent false positives.

5. **🔊 Environmental Sound Awareness**:
   - Real-time acoustic spectrum and energy analyzer listening for safety-critical sounds.
   - Detects emergency sirens (ambulances, police), car horns, smoke/fire alarms, glass breaking, knocks, and loud impacts.

6. **🗣️ Multilingual Speech Recognition**:
   - Converts spoken speech into text with support for English, Hindi, Telugu, and Spanish.

7. **🌍 Live Text & Speech Translation**:
   - Instant bi-directional translation for navigation and accessibility vocabulary across English, Telugu, Hindi, and Spanish.

---

## 🧠 Multimodal AI Orchestrator & Priority Hierarchy

All 7 engines feed into **`SaheyAIOrchestrator`**, which coordinates context, spatial orientation, and speech timing:

```
                  ┌─────────────────────────────────────┐
                  │          SAHEY PERCEPTION           │
                  └──────────────────┬──────────────────┘
                                     │
           ┌─────────────────────────┴─────────────────────────┐
           ▼                                                   ▼
     VISION STREAM                                       AUDIO STREAM
   ├── Object Detection (COCO)                        ├── Acoustic Classifier (Sirens, Horns, Alarms)
   ├── Danger Radar (Vehicles, Hazards)               └── Speech Recognition (Voice Input)
   ├── Sign Language (Gestures)
   └── OCR (Printed Text Reader)
           │                                                   │
           └─────────────────────────┬─────────────────────────┘
                                     │
                                     ▼
                      ┌──────────────────────────────┐
                      │    SaheyAIOrchestrator       │
                      │  • Context Fusion            │
                      │  • Spatial Reasoning         │
                      │  • Priority Manager (10-100) │
                      └──────────────┬───────────────┘
                                     │
                                     ▼
                      ┌──────────────────────────────┐
                      │    AnnouncementManager       │
                      │  • Speech Cooldown (2.5s)    │
                      │  • Danger Interruption       │
                      │  • Multi-Pattern Haptics     │
                      └──────────────────────────────┘
```

### Priority Hierarchy:
* **`CRITICAL` (100)**: Imminent vehicle collision, fire alarm.
* **`DANGER` (80)**: Approaching vehicles, navigation obstacles.
* **`NAVIGATION` (70)**: Stairs, crosswalks, doors, translation results.
* **`TEXT` / `SIGN` / `SOUND` (50)**: Recognized text, sign language, ambient sounds.
* **`OBJECT` (30)**: Normal objects (laptops, chairs, bottles).
* **`BACKGROUND` (10)**: Low-confidence observations.

---

## 📳 Tactile Haptic Language

* **Normal Object**: Single crisp pulse (40ms).
* **Important Object / Sign Gesture**: Double pulse (50ms - 50ms).
* **Text Captured**: Single click pulse (60ms).
* **Danger Alert**: Pulsing alarm pattern.
* **Critical Danger / Hazard**: Urgent SOS vibration pattern.

---

## 🛡️ One-Tap Safety Shield (Emergency Mode)

Tapping the **Safety Shield** enters maximum-priority hazard radar mode:
- Suppresses non-critical object chatter.
- Heightens hazard sensitivity for approaching vehicles, obstacles, and sirens.

---

## 🛠️ Hackathon Demo Panel

Tapping **Demo** opens the built-in demonstration sheet:
- Real-time health indicators for all 7 AI subsystems.
- 1-click test triggers for Danger warnings, Emergency sirens, Sign gestures, and Translations.

---

## 📦 APK Installation

* **🚀 Signed Universal Release APK**: [**`SAHEY-Release.apk`**](file:///d:/Hackathon/DETECTOR/SAHEY-Release.apk) (`95.1 MB`)
* **Gradle Output**: `app/build/outputs/apk/release/app-release.apk`

### Install via ADB:
```bash
adb install -r "d:\Hackathon\DETECTOR\SAHEY-Release.apk"
```

### Wi-Fi Direct Download:
Open this URL in your mobile phone browser on the same Wi-Fi network:
```
http://10.10.84.90:8080/SAHEY-Release.apk
```
