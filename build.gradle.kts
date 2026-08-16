// Root Gradle build. The wrapper (gradlew/gradlew.bat/gradle/wrapper/*) was
// missing from this repo even though build-android.sh/.bat and
// .vscode/tasks.json both call it - restored pinned to Gradle 8.2, which
// AGP 8.2.0 (below) requires as its own exact minimum, per Google's own
// AGP 8.2.0 release notes.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
