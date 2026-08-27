// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - Android application Gradle configuration
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================

plugins {
    id("com.android.application")
    alias(libs.plugins.kotlin.compose)
}

// =============================================================================
// Ecosystem-wide auto version bump ("odometer" rule, base 10) - see
// CHANGELOG.md and version.properties. This block runs at Gradle
// CONFIGURATION time, which happens on every real build (assembleDebug,
// installDebug, compileDebugKotlin, ...) - so version.properties is read,
// bumped, and rewritten with the new values BEFORE those values are used
// for versionCode/versionName below, meaning the APK produced by this exact
// invocation already carries the bumped number, never the previous one.
//
// Rule: versionPatch +1; if it would go above 9 it resets to 0 and
// versionMinor +1 instead (example: 0.0.9 -> 0.1.0). versionCode is a
// separate simple monotonic counter, always +1, no carry - Android requires
// versionCode to strictly increase across every build that ever ships.
// Same odometer convention already used by HYDRA-UMC-STUDIO/scripts/
// bump-version.mjs, HYDRA-UMC-SUITE/bump_version.py and
// HYDRA-UMC-IOS-CONTROL/tool/bump_version.dart - this is the Gradle-native
// equivalent for this repo, wired into the configuration phase instead of a
// separate pre-build script since that's the point in a Gradle build that
// reliably runs on every real build. CI sets HYDRA_UMC_CI=1 so verification
// tasks can use the declared version without mutating a checked-out source tree.
val versionPropsFile = file("version.properties")
val versionPropsText = versionPropsFile.readText()

fun readIntProp(text: String, key: String): Int {
    val match = Regex("(?m)^$key=(\\d+)\\s*$").find(text)
        ?: throw GradleException("version.properties: missing '$key=<number>' line")
    return match.groupValues[1].toInt()
}

fun replaceIntProp(text: String, key: String, value: Int): String =
    text.replace(Regex("(?m)^$key=\\d+\\s*$"), "$key=$value")

var appVersionMajor = readIntProp(versionPropsText, "versionMajor")
var appVersionMinor = readIntProp(versionPropsText, "versionMinor")
var appVersionPatch = readIntProp(versionPropsText, "versionPatch")
var appVersionCode = readIntProp(versionPropsText, "versionCode")

if (System.getenv("HYDRA_UMC_CI") != "1") {
    appVersionPatch += 1
    if (appVersionPatch > 9) {
        appVersionPatch = 0
        appVersionMinor += 1
    }
    appVersionCode += 1

    var newVersionPropsText = versionPropsText
    newVersionPropsText = replaceIntProp(newVersionPropsText, "versionMajor", appVersionMajor)
    newVersionPropsText = replaceIntProp(newVersionPropsText, "versionMinor", appVersionMinor)
    newVersionPropsText = replaceIntProp(newVersionPropsText, "versionPatch", appVersionPatch)
    newVersionPropsText = replaceIntProp(newVersionPropsText, "versionCode", appVersionCode)
    versionPropsFile.writeText(newVersionPropsText)
}

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
