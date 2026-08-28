# SAHEY model assets

Drop the model files listed below into this folder (`app/src/main/assets/`).
The app builds and runs without them, but the corresponding feature reports itself
**unavailable** instead of guessing.

| File | Feature | Required? | Size | Source |
|------|---------|-----------|------|--------|
| `mobilenet_ssd.tflite` | Object detection + hazard radar | already bundled | 4.2 MB | COCO SSD MobileNet v1 (TFLite) |
| `hand_landmarker.task` | Sign language (hand landmarks) | **download** | ~7.5 MB | `https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task` |
| `sign_language_model.tflite` | Sign language (42-feature landmark classifier, A/B/L) | already bundled | 23 KB | trained from the MediaPipe `create_dataset.py` pipeline |
| `sign_language_ml_alphabets.tflite` | Sign language (optional 63-feature A–Z classifier) | already bundled | 142 KB | optional secondary model |
| `yamnet.tflite` | Environmental sound recognition | **download** | ~4 MB | `https://storage.googleapis.com/mediapipe-models/audio_classifier/yamnet/float32/1/yamnet.tflite` (or TF Hub `google/lite-model/yamnet/classification/tflite/1`) |
| `sign_language_cnn.tflite` | (legacy, no longer used) | ignore | 1.3 MB | old 28×28 Sign-MNIST CNN — kept for reference only |

## Quick download (bash / Git Bash)

```bash
cd app/src/main/assets
curl -L -o hand_landmarker.task \
  https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task
curl -L -o yamnet.tflite \
  https://storage.googleapis.com/mediapipe-models/audio_classifier/yamnet/float32/1/yamnet.tflite
```

## What each feature does when its model is missing

| Missing file | Behaviour |
|--------------|-----------|
| `hand_landmarker.task` | Sign Language logs "disabled" once and produces no output (no fabricated letters). |
| `yamnet.tflite` | Sound Assist falls back to the low-confidence zero-crossing heuristic and says so in the status line. |
| Both sign `.tflite` models | Sign Language stays disabled even if `hand_landmarker.task` is present. |
