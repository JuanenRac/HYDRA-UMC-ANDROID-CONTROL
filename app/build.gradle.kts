plugins {
    id("com.android.application")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.hydraumc.control"
    // 34, not 33 - compose-bom 2024.02.00 below pulls in Compose/AndroidX
    // artifacts (material3 1.2.0, core-ktx 1.12.0, etc.) that require
    // compiling against API 34 (AAR metadata check), independent of
    // targetSdk - see build.gradle.kts's own AGP version comment.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hydraumc.control"
        minSdk = 24
        // 34 (Android 14), not 33 - matches compileSdk above. Real-device
        // testing on Android 14 (project owner's own phone) is the actual
        // target platform, so there's no reason to sit one API level
        // behind it - see README.md's own Troubleshooting entry for why
        // this was left at 33 initially and what changed.
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation("androidx.core:core-splashscreen:1.0.1")
    
    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Networking
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)

    // Preferences DataStore
    implementation(libs.androidx.datastore.preferences)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
