# Prototype build — what changed

This pass made the project build-ready and replaced the three fake "AI" features with
real models (or honestly-labelled heuristics). Nothing here was compiled or run on-device
in this environment — see "Verification status" at the bottom.

## Build-readiness

| Change | File(s) |
|---|---|
| Added POSIX + Windows Gradle wrapper scripts | `gradlew`, `gradlew.bat` |
| Documented the missing `gradle-wrapper.jar` (Android Studio regenerates it) | `gradle/wrapper/README.md` |
| Release signing now reads `keystore.properties` / env vars, falls back to the bundled key so `assembleRelease` still works; `assembleDebug` needs nothing | `app/build.gradle.kts`, `keystore.properties.example` |
| JDK 17 source/target, core-library desugaring (MediaPipe needs `java.time`) | `app/build.gradle.kts` |
| `pickFirsts` for the duplicate TFLite/MediaPipe native libs (would otherwise fail packaging) | `app/build.gradle.kts` |
| Lint no longer aborts the build (still runs/reports) | `app/build.gradle.kts` |
| Legacy launcher icon for API 24–25 (vector fallback; adaptive icon still used on 26+) | `app/src/main/res/mipmap-anydpi/ic_launcher.xml`, `ic_launcher_round.xml` |
| `keystore.properties` + downloaded model blobs git-ignored | `.gitignore` |
| Honest feature table + build steps | `README.md` |

The layouts, resources, and `binding.*` references were audited against all activities —
they were already complete and consistent, so no UI files were changed.

## Sign language — real MediaPipe Hands

- **New** `vision/HandLandmarkerHelper.kt` — MediaPipe Tasks `HandLandmarker`, rotates the
  frame upright, returns 21 real normalised landmarks. Missing model → `isReady == false`.
- **Rewrote** `vision/SignClassifier.kt` — consumes real landmarks, builds the exact
  42-feature vector the Python training pipeline used, runs `sign_language_model.tflite`
  with **real soft-max confidence** (removed the `coerceIn(0.70, 0.99)` fake-confidence
  clamp), optional 63-feature A–Z secondary model. Deleted the skin-colour blob scan, the
  28×28 CNN crop path, and the aspect-ratio "geometric fallback" guesser.
- **Updated** `vision/SignLanguageEngine.kt` — owns the landmarker, reports the feature
  *unavailable* instead of emitting guesses when models are missing.
- Bundled 42-feature model only knows **A / B / L**; drop a fuller landmark model in
  `assets/` for full A–Z (see `app/src/main/assets/README.md`).

## Environmental sound — real YAMNet

- **New** `sound/EnvironmentalSoundClassifier.kt` — YAMNet (AudioSet 521-class) via TFLite
  Task Audio, maps recognised classes → alert categories with per-label confidence gates
  and debounce.
- **Rewrote** `sound/SoundAwarenessEngine.kt` — prefers YAMNet; falls back to the old
  zero-crossing `SoundClassifier` only when `yamnet.tflite` is absent, and says so.
- **Wired it in:** `sound/SoundOrchestrator.kt` now actually runs environmental sound
  recognition (it previously only had a raw loudness-spike heuristic and was never
  connected to `SoundAwarenessEngine`). The loudness-spike alert is now suppressed when
  YAMNet is active and downgraded to advisory otherwise.
- Mic-contention caveat documented (`isEnvironmentalSoundEnabled` toggle).

## Fire / smoke / slippery-floor — advisory, not assertion

- `vision/DangerDetectionEngine.kt` — the on-device colour filter no longer speaks. It only
  nominates a frame for `GeminiVisionEngine` verification.
- `vision/gemini/GeminiVisionEngine.kt` — with no Gemini key, fire/smoke fallback is now
  *"Possible fire ahead. Please verify."* at reduced priority (was *"Warning. Fire
  detected."* at CRITICAL). Gemini-**confirmed** fire is still definitive/CRITICAL.
- `vision/RiskAssessment.kt` — "slippery floor" → *"The floor ahead looks reflective — it
  may be wet. Please check."* at navigation priority; label-based fire → advisory.

## Verification status

- ❌ Not compiled (no Android SDK / Gradle in the authoring environment).
- ❌ Not run on a device.
- ✅ Layout/resource/`binding` cross-check done by hand.
- ⚠️ MediaPipe / TFLite Task API surface written to the documented 0.10.14 / 0.4.4 APIs;
  confirm on first `./gradlew assembleDebug` and adjust import/method names if an artifact
  version differs.
