# Nexus

Nexus is a mobile-first AI development environment for building, modifying, testing, debugging, and shipping software from Android, with GitHub as the cloud development/build backbone.

## V1 foundation in this branch

- Kotlin + Jetpack Compose + Material 3.
- API 36 target/compile configuration.
- Persistent project metadata using DataStore.
- Project/workspace domain primitives.
- Cloud-first build architecture: no Android SDK, NDK, JDK, or Gradle build infrastructure is bundled into the APK.
- GitHub Actions CI with Android 36 SDK setup.
- Successful CI uploads a downloadable debug APK artifact and test reports.

## Build pipeline

`Android source -> GitHub Actions -> JDK 17 -> Android SDK 36 -> Gradle -> tests -> debug APK artifact`

Artifacts are retained for 14 days and can be downloaded from the successful GitHub Actions run under **Artifacts**.

## Roadmap implementation order

Foundation -> Workspace -> File System -> Editor -> Search/Indexing -> Git -> GitHub -> Cloud Build -> Terminal -> Testing -> AI -> Context -> Tools -> Patch/Edit -> Tasks -> Agents -> Debugging -> Parallel AI -> Memory -> GitHub engineering -> advanced builds -> ecosystem.

The application is being rebuilt as a native Android product rather than extending the old Acode plugin architecture.
