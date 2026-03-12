# Alignify — AI Fitness & Posture Coach

An Android app that uses real-time pose detection to correct exercise form, track GPS runs, count steps, and provide AI-powered coaching.

## Features

- **Real-time posture correction** using MediaPipe Pose Landmarker and custom TFLite models
- **4 guided exercises** with live form feedback:
  - Bicep Curl — rep counting, lean-back detection
  - Squat — rep counting, feet/knee placement checking
  - Lunge — rep counting, knee-over-toe detection
  - Plank — hold-time tracking, hip alignment checking
- **GPS Run/Walk tracker** with Mapbox map, pace, distance, and route recording
- **Step counter** with daily goal tracking
- **Firebase Auth** — email/password and Google Sign-In
- **Analytics dashboard** — weekly workout history via Firestore
- **AI Chatbot** — fitness coaching assistant

## Requirements

- Android SDK 24+ (Android 7.0 Marshmallow)
- Camera permission (for exercise detection)
- Location permission (for GPS run tracking)
- Activity recognition permission (for step counter)

## Setup

### 1. Firebase

1. Create a Firebase project at [console.firebase.google.com](https://console.firebase.google.com)
2. Add an Android app with package name `com.alignify`
3. Download `google-services.json` and place it in `app/`
4. Enable **Authentication** (Email/Password + Google) and **Firestore**

### 2. Mapbox

1. Create an account at [account.mapbox.com](https://account.mapbox.com)
2. Get your **public token** (`pk.*`) and **secret download token** (`sk.*`)
3. In `app/src/main/res/values/mapbox_access_token.xml`, replace `YOUR_MAPBOX_ACCESS_TOKEN` with your public token
4. In `local.properties` (never committed), add:
   ```
   MAPBOX_ACCESS_TOKEN=pk.your_token_here
   ```
5. In `gradle.properties`, update `MAPBOX_DOWNLOADS_TOKEN` with your secret token (or set in `~/.gradle/gradle.properties` for security)

### 3. MediaPipe Model

Download `pose_landmarker_lite.task` from the [MediaPipe Models page](https://developers.google.com/mediapipe/solutions/vision/pose_landmarker#models) and place it in `app/src/main/assets/`.

### 4. Build

```bash
./gradlew assembleDebug
# or
./gradlew.bat assembleDebug   # Windows
```

## Project Structure

```
app/src/main/java/com/alignify/
├── MainActivity.java               # Splash / entry point
├── HomeActivity.java               # Bottom nav + fragments
├── LoginActivity.java              # Login screen
├── SignupActivity.java             # Registration screen
├── ExerciseActivity.java           # Camera + pose detection screen
├── RunActivity.java                # GPS run tracker activity
├── StepActivity.java               # Step counter screen
├── exercises/
│   ├── ExerciseDetector.java       # Abstract base detector
│   ├── BicepCurlDetector.java
│   ├── SquatDetector.java
│   ├── LungeDetector.java
│   └── PlankDetector.java
├── engine/                         # Pose processing pipeline
├── ml/                             # TFLite interpreter wrapper
├── utils/                          # Landmark angle/distance utilities
├── chatbot/                        # AI chatbot integration
├── data/                           # Data models
└── service/                        # Background services
app/src/main/assets/
├── pose_landmarker_lite.task       # MediaPipe model
├── bicep_model.tflite
├── squat_model.tflite
├── lunge_model.tflite
└── plank_model.tflite
```

## Security Notes

> **Never commit real API keys.** The following files are gitignored and must be set up locally:
>
> - `local.properties` — `MAPS_API_KEY`, `MAPBOX_ACCESS_TOKEN`
> - `app/google-services.json` — Firebase config
> - `app/src/main/res/values/mapbox_access_token.xml` — replace placeholder with your token locally

## Dependencies

| Library                | Version       |
| ---------------------- | ------------- |
| MediaPipe Tasks Vision | 0.10.14       |
| TensorFlow Lite        | 2.14.0        |
| CameraX                | 1.3.1         |
| Mapbox Maps SDK        | 11.19.0       |
| Firebase Auth          | (BoM managed) |
| Firebase Firestore     | (BoM managed) |
| Glide                  | 4.16.0        |
