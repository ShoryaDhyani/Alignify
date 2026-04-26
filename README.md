# Alignify

Alignify is an Android application (Kotlin / Gradle) contained in this repository. This README provides quick instructions to build, run, and contribute to the project.

[![License: MIT](https://img.shields.io/badge/license-MIT-2196F3?style=flat-square)](LICENSE)

## Project status

This repository contains an Android app module (app/) with a standard Gradle setup. Use Android Studio or the Gradle CLI to build and run the app.

## Quick start

Prerequisites

- Java JDK 11 or newer
- Android SDK (platform tools + an Android platform)
- Android Studio (recommended) or Git + Gradle CLI

Using Android Studio

1. Open Android Studio
2. Choose "Open" and select the repository root
3. Allow Gradle to sync and download dependencies
4. Run the app on an emulator or connected device

Using the command line

Clone the repo:

```bash
git clone https://github.com/ShoryaDhyani/Alignify.git
cd Alignify
```

Build the app (assemble the debug APK):

```bash
./gradlew :app:assembleDebug
```

Install to a connected device or emulator:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Run instrumentation tests (if present):

```bash
./gradlew :app:connectedAndroidTest
```

## Project structure

- app/ — Android app module (source code, manifest, resources, Gradle config)
- build.gradle.kts, settings.gradle.kts, gradle.properties — repository build files
- gradle/ and gradlew — Gradle wrapper and helper scripts

## Contributing

Contributions are welcome. Typical workflow:

1. Fork the repository
2. Create a feature branch: `git checkout -b feat/my-feature`
3. Make changes, add tests, and verify the app builds
4. Commit with a clear message and open a Pull Request

Please follow Kotlin/Android best practices and include a description of what you changed and why.

## Reporting issues

Open issues for bugs, feature requests, or questions: https://github.com/ShoryaDhyani/Alignify/issues

## License

This project is licensed under the MIT License — see the LICENSE file for details.

## Maintainer

Shorya Dhyani — https://github.com/ShoryaDhyani
