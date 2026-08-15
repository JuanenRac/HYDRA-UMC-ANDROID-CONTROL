// Root Gradle build. The wrapper (gradlew/gradlew.bat/gradle/wrapper/*) was
// missing from this repo even though build-android.sh/.bat and
// .vscode/tasks.json both call it - restored pinned to Gradle 8.2, which
// AGP 8.2.0 (below) requires as its own exact minimum, per Google's own
// AGP 8.2.0 release notes.
plugins {
    // 8.2.0, not 8.1.0 - AGP 8.1.0's max recommended compileSdk is 33, but
    // compose-bom 2024.02.00 (app/build.gradle.kts) pulls in Compose/AndroidX
    // artifacts that require compiling against API 34. 8.2.0 supports that
    // while still needing only Gradle 8.2 (the wrapper's own pinned version -
    // no wrapper change needed for this bump).
    id("com.android.application") version "8.2.0" apply false
    // 1.9.22, not 1.8.10 - see app/build.gradle.kts's own composeOptions
    // comment for why (the Compose Compiler/BOM versions this pairs with
    // are what actually matter for API availability).
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
