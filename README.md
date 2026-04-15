<div align="center">

# Alignify

**Android fitness application** for tracking workouts and visualizing activity, with pose estimation support and map-based features.

</div>

---

## About
Alignify is an Android app focused on fitness and activity tracking. This repository contains the Android project built with **Gradle (Kotlin DSL)** and targets **Android SDK 34**.

### Tech stack
- **Kotlin + AndroidX** (AppCompat, Material, ConstraintLayout)
- **Firebase** (Auth, Firestore, Storage)
- **Google Sign-In**
- **Mapbox Maps SDK (v11)**
- **CameraX**
- **MediaPipe Tasks (Pose Landmarker)**
- **TensorFlow Lite**
- **Room** (local database)
- **Health Connect**
- **MPAndroidChart**
- **OkHttp + Gson** (networking / JSON)

---

## Requirements
- **Android Studio** (recommended: latest stable)
- **JDK 17** (Android Studio default)
- Android device/emulator with **minSdk 26**

---

## Setup

### 1) Clone
```bash
git clone https://github.com/ShoryaDhyani/Alignify.git
cd Alignify
```

### 2) Configure secrets
This project reads keys from `local.properties` for local builds, with environment variable fallback for CI.

Create a `local.properties` file in the project root (do **not** commit it) and add:
```properties
# Mapbox
MAPBOX_ACCESS_TOKEN=YOUR_TOKEN
# Optional: defaults to the style in build.gradle.kts if blank
MAPBOX_STYLE_URI=mapbox://styles/your-user/your-style
```

> Note: The repository also configures the Mapbox Maven repo using `MAPBOX_DOWNLOADS_TOKEN` (Gradle property). If you hit dependency download/auth errors, set it in `~/.gradle/gradle.properties`:
```properties
MAPBOX_DOWNLOADS_TOKEN=YOUR_TOKEN
```

### 3) Firebase
Make sure you have a Firebase project configured and add the `google-services.json` into:
- `app/google-services.json`

This project uses Firebase **Auth**, **Firestore**, and **Storage**.

---

## Build & Run

### Android Studio
1. Open the project in Android Studio
2. Let Gradle sync
3. Select an emulator/device
4. Click **Run**

### Command line
```bash
# Debug build
./gradlew :app:assembleDebug

# Run unit tests
./gradlew test
```

On Windows:
```powershell
.\gradlew.bat :app:assembleDebug
```

---

## Project structure (high-level)
- `app/` — Android app module
- `app/src/` — application source
- `gradle/` — Gradle wrapper + version catalogs

---

## Contributing
Contributions are welcome. If you plan a larger change, please open an issue first describing:
- the problem
- your proposed solution
- screenshots/logs if applicable

---

## License
No license file is currently detected in the repository. If you want, I can add an MIT/Apache-2.0 license file and update this section accordingly.
