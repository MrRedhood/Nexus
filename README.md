# Nexus

Nexus is a mobile-first AI development environment for building, modifying, testing, debugging, and shipping software from Android, with GitHub as the cloud development/build backbone.

## V1 principles

- Kotlin + Jetpack Compose Android client.
- Cloud-first build execution through GitHub Actions.
- No Android SDK, NDK, JDK, or Gradle build infrastructure bundled into the app.
- Project/workspace/editor/Git/GitHub/CI/AI capabilities are built around reusable primitives.
- Build artifacts are uploaded by CI and retained as downloadable GitHub Actions artifacts.

## Current foundation

This repository contains the initial Android application foundation, domain models, project architecture, security/storage/networking interfaces, and a GitHub Actions Android CI pipeline that produces downloadable debug APK and test artifacts.

## Build locally

```bash
./gradlew assembleDebug
```

CI is the canonical build environment for V1.
