// =============================================================================
// HYDRA-UMC Android Control - root Gradle build script
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
//
// Scaffolding only - no gradle-wrapper.jar is checked in (generating one
// correctly requires actually running Gradle, not something to fake in a
// scaffold). Run `gradle wrapper --gradle-version 8.10` once a real
// Android SDK + Gradle install is available locally to generate a real
// gradlew/gradlew.bat/gradle-wrapper.jar before building - see
// docs/ARCHITECTURE.md.
// =============================================================================
plugins {
    id("com.android.application") version "8.7.0" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
}
