// Root Gradle build. The wrapper (gradlew/gradlew.bat/gradle/wrapper/*) is
// pinned to Gradle 9.7.0, which AGP 9.3.1 (below) requires.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
