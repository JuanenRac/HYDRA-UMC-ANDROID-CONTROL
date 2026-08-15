plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hydraumc.control"
    // 34, not 33 - compose-bom 2024.02.00 below pulls in Compose/AndroidX
    // artifacts (material3 1.2.0, core-ktx 1.12.0, etc.) that require
    // compiling against API 34 (AAR metadata check), independent of
    // targetSdk - see build.gradle.kts's own AGP version comment.
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hydraumc.control"
        minSdk = 24
        // 34 (Android 14), not 33 - matches compileSdk above. Real-device
        // testing on Android 14 (project owner's own phone) is the actual
        // target platform, so there's no reason to sit one API level
        // behind it - see README.md's own Troubleshooting entry for why
        // this was left at 33 initially and what changed.
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        // Paired with the Kotlin plugin version in the root build.gradle.kts
        // (1.9.22 -> compiler 1.5.8, per Google's own compose-kotlin
        // compatibility map) - was 1.4.3/Kotlin 1.8.10, which only pulled in
        // an alpha-era Material3 (see compose-bom below) missing APIs this
        // app actually uses (e.g. TopAppBarDefaults.topAppBarColors()).
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    implementation("androidx.activity:activity-compose:1.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")
    // 2024.02.00 -> Material3 1.2.0, stable (the old 2023.03.00 was an early
    // alpha still missing/experimental-gating APIs this app relies on).
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-splashscreen:1.0.1")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.5.3")

    // Networking - GET/POST /api/settings, GET /api/hydra-info and /ws all
    // speak plain JSON (org.json, part of the Android framework) over OkHttp;
    // no Retrofit/Gson needed, and no REST surface is invented locally - see
    // network/HydraApiClient.kt's own header comment for why a raw JSON tree
    // is used instead of a typed schema.
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Preferences DataStore - persists the last IP/port (network/ConnectionPrefs.kt)
    implementation("androidx.datastore:datastore-preferences:1.0.0")
}
