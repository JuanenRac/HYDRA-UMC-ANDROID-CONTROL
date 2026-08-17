plugins {
    id("com.android.application")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.hydraumc.control"
    // 35, not 36 - Android 15 is the current stable target.
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hydraumc.control"
        minSdk = 24
        // 35 (Android 15), matches compileSdk above.
        targetSdk = 35
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

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug") // Use debug key for now for easy testing
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
        }
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
    implementation(libs.splashscreen)
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.media)
    
    // Filament 3D engine
    implementation(libs.filament.android)
    implementation(libs.filament.gltfio)
    implementation(libs.filament.filamat)
    implementation(libs.filament.utils)

    // Glance Widgets
    implementation(libs.glance.appwidget)

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
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
