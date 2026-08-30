// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - Android application Gradle configuration
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================

plugins {
    id("com.android.application")
    alias(libs.plugins.kotlin.compose)
}

import java.util.Properties

// A GitHub-release APK must be signed with a private, persistent certificate
// or Android will reject it as an update. Secrets live only in the ignored
// keystore.properties file (or CI environment variables), never in Git.
val releaseSigningProperties = Properties().also { properties ->
    val localSigningFile = rootProject.file("keystore.properties")
    if (localSigningFile.isFile) {
        localSigningFile.inputStream().use(properties::load)
    }
}

fun releaseSigningValue(key: String, environmentKey: String): String? =
    providers.gradleProperty(key).orNull
        ?: System.getenv(environmentKey)
        ?: releaseSigningProperties.getProperty(key)

val releaseStoreFilePath = releaseSigningValue(
    "hydraUmcReleaseStoreFile",
    "HYDRA_UMC_RELEASE_STORE_FILE",
)
val releaseStorePassword = releaseSigningValue(
    "hydraUmcReleaseStorePassword",
    "HYDRA_UMC_RELEASE_STORE_PASSWORD",
)
val releaseKeyAlias = releaseSigningValue(
    "hydraUmcReleaseKeyAlias",
    "HYDRA_UMC_RELEASE_KEY_ALIAS",
)
val releaseKeyPassword = releaseSigningValue(
    "hydraUmcReleaseKeyPassword",
    "HYDRA_UMC_RELEASE_KEY_PASSWORD",
)
val hasPrivateReleaseSigning = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

// =============================================================================
// Version source of truth
// =============================================================================
// Gradle always reads version.properties and never writes it.
// build-android.bat/.sh are the sole release flow: they run
// bump_manifest_version.py (native version + manifest + CHANGELOG) and
// bump_version_code.py (the separate, always-monotonic Android versionCode)
// first, then invoke Gradle - see bump_version_code.py's own docstring,
// which already documented this exact contract.
//
// This used to write version.properties itself at Gradle CONFIGURATION
// time on ANY real task (assembleDebug, installDebug, compileDebugKotlin,
// ...) unless -PhydraUmcReadOnly=true/HYDRA_UMC_CI=1 was passed - a plain
// dev build (e.g. a verification `compileDebugKotlin`) run without that
// flag silently advanced the native version with no matching manifest/
// CHANGELOG update. Found live: two verification compiles during this same
// session bumped versionPatch/versionCode twice with the manifest never
// moving - the exact version-mirror drift class this ecosystem's
// convention exists to prevent, same bug already fixed in
// HYDRA-UMC-WATCH's own build.gradle.kts.
val versionPropsFile = file("version.properties")
val versionPropsText = versionPropsFile.readText()

fun readIntProp(text: String, key: String): Int {
    val match = Regex("(?m)^$key=(\\d+)\\s*$").find(text)
        ?: throw GradleException("version.properties: missing '$key=<number>' line")
    return match.groupValues[1].toInt()
}

val appVersionMajor = readIntProp(versionPropsText, "versionMajor")
val appVersionMinor = readIntProp(versionPropsText, "versionMinor")
val appVersionPatch = readIntProp(versionPropsText, "versionPatch")
val appVersionCode = readIntProp(versionPropsText, "versionCode")

val appVersionName = "$appVersionMajor.$appVersionMinor.$appVersionPatch"

android {
    namespace = "com.hydraumc.control"
    // 36 required by androidx.core 1.18.0
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hydraumc.control"
        minSdk = 24
        // 35 (Android 15) for stable runtime behavior.
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        // Needed so BuildConfig.VERSION_NAME reflects the auto-bumped
        // versionName above at runtime (AboutDialog.kt reads it) - disabled
        // by default on AGP 8+.
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Robolectric must receive merged Android resources and the app
            // manifest; without them Context.getString and Compose activity
            // resolution fail in the same way as an unconfigured JVM test.
            isIncludeAndroidResources = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Local developer builds retain the debug key when no private
            // configuration exists. The publication scripts explicitly
            // refuse that fallback, so it can never be mistaken for a
            // GitHub update-channel artifact.
            signingConfig = if (hasPrivateReleaseSigning) {
                signingConfigs.maybeCreate("hydraUmcRelease").apply {
                    storeFile = file(requireNotNull(releaseStoreFilePath))
                    storePassword = requireNotNull(releaseStorePassword)
                    keyAlias = requireNotNull(releaseKeyAlias)
                    keyPassword = requireNotNull(releaseKeyPassword)
                }
            } else {
                signingConfigs.getByName("debug")
            }
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
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    // Forces a modern androidx.fragment resolution across the whole
    // dependency graph - some older transitive dependency was pinning it
    // below 1.3.0, which lint's own InvalidFragmentVersionForActivityResult
    // check fails a release build over (registerForActivityResult in
    // MainActivity.kt needs >= 1.3.0's onRequestPermissionsResult fix).
    // Not used directly; a direct declaration is the standard Gradle way
    // to win version resolution over a transitive one.
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // ProcessLifecycleOwner - lets RobotViewModel tell whether the app
    // process is actually in the foreground, so background polling (see
    // startMetricsLoop) can pause instead of running indefinitely just
    // because the ViewModel/WebSocket survive an Activity being backgrounded.
    implementation(libs.androidx.lifecycle.process)
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

    // Encrypted at-rest storage for the login credential cache (AuthPrefs.kt) -
    // Keystore-backed AES256-GCM, since the username/password/token it
    // holds are exactly the kind of secret plain DataStore/SharedPreferences
    // was never meant to store unencrypted.
    implementation(libs.androidx.security.crypto)

    // Private, end-to-end encrypted Watch <-> phone transport. Google Play
    // services accepts only the same applicationId signed with the same key.
    implementation(libs.play.services.wearable)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
