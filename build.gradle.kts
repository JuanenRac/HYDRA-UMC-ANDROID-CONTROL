// Root Gradle build. The wrapper (gradlew/gradlew.bat/gradle/wrapper/*) was
// missing from this repo even though build-android.sh/.bat and
// .vscode/tasks.json both call it - restored pinned to Gradle 8.2, the
// version AGP 8.1.0 (below) is validated against.
plugins {
    id("com.android.application") version "8.1.0" apply false
    id("org.jetbrains.kotlin.android") version "1.8.10" apply false
}
