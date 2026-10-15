# ScreenRecorderAndroid

An Android screen recorder MVP focused on freely adjustable crop regions and local MP4 output.

## Build APK
GitHub Actions builds a debug APK on pushes to `main` and via manual workflow dispatch. Download it from the workflow run's **Artifacts** section.

## Current implementation status
This repository is the project scaffold. Region selection, Android MediaProjection permission flow, real crop rendering, and MP4 muxing must all be verified on a physical device before calling this production-ready.

## Requirements
- Android Studio / Android Gradle Plugin
- JDK 17
- Android SDK 35

No server or paid API is required.