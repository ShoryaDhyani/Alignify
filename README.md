# MediaPose Android App

Exercise pose detection app using MediaPipe and TensorFlow Lite.

## Features

- **Real-time pose detection** using MediaPipe Pose Landmarker
- **Exercise form analysis** for 4 exercises:
  - Bicep Curl (rep counting, lean back detection)
  - Squat (rep counting, feet/knee placement checking)
  - Lunge (rep counting, knee over toe detection)
  - Plank (hold time tracking, hip alignment checking)
- **Visual feedback** with skeleton overlay and form corrections

## Requirements

- Android SDK 24+ (Android 7.0)
- Camera permission

## Setup

1. **Download MediaPipe Pose Model**
   
   Download `pose_landmarker_lite.task` from:
   https://developers.google.com/mediapipe/solutions/vision/pose_landmarker#models
   
   Place it in `app/src/main/assets/`

2. **Convert and Add TFLite Models**
   
   Run the conversion script from the project root:
   ```bash
   python scripts/convert_to_tflite.py --output-dir android/app/src/main/assets
   ```

3. **Build the App**
   ```bash
   cd android
   ./gradlew assembleDebug
   ```

4. **Install on Device**
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

## Project Structure

```
android/
├── app/
│   ├── src/main/
│   │   ├── java/com/medipose/
│   │   │   ├── MainActivity.kt           # Exercise selection
│   │   │   ├── ExerciseActivity.kt       # Camera & detection
│   │   │   ├── PoseLandmarkerHelper.kt   # MediaPipe wrapper
│   │   │   ├── OverlayView.kt            # Skeleton drawing
│   │   │   ├── exercises/
│   │   │   │   ├── ExerciseDetector.kt   # Base class
│   │   │   │   ├── BicepCurlDetector.kt
│   │   │   │   ├── SquatDetector.kt
│   │   │   │   ├── LungeDetector.kt
│   │   │   │   └── PlankDetector.kt
│   │   │   └── utils/
│   │   │       ├── LandmarkUtils.kt      # Angle/distance calculations
│   │   │       └── TFLiteInterpreter.kt  # Model inference
│   │   ├── assets/                       # Model files go here
│   │   └── res/                          # Layouts & resources
```

## Dependencies

- MediaPipe Tasks Vision: 0.10.9
- TensorFlow Lite: 2.14.0
- CameraX: 1.3.1
