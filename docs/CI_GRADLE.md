# CI Gradle provisioning

GitHub Actions provisions Gradle 8.13 with `gradle/actions/setup-gradle@v4` because the repository does not commit a binary Gradle wrapper JAR. The Android CI invokes the provisioned `gradle` executable directly.
