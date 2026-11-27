// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - Android application Gradle configuration
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
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
// build-android.bat/.sh never change it either: the explicit bump command
// (python tools/hydra_project_tool.py bump) runs bump_manifest_version.py
// (native version + manifest + CHANGELOG) and bump_version_code.py (the
// separate, always-monotonic Android versionCode) - see bump_version_code.py's
// own docstring.
//
// A plain dev build (assembleDebug, installDebug, compileDebugKotlin, ...)
// therefore never advances the native version without a matching
// manifest/CHANGELOG update, which is the version-mirror drift this
// ecosystem's convention exists to prevent. HYDRA-UMC-WATCH's own
// build.gradle.kts follows the same rule.
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

// Versions from 0.8.0 on carry a fourth component, kept in its own optional
// `versionBuild` line.
val appVersionBuild = Regex("(?m)^versionBuild=(\\d+)\\s*$").find(versionPropsText)?.groupValues?.get(1)?.toInt()
val appVersionName = "$appVersionMajor.$appVersionMinor.$appVersionPatch" +
    if (appVersionBuild != null && appVersionMajor * 10000 + appVersionMinor * 100 + appVersionPatch >= 800) ".$appVersionBuild" else ""

// =============================================================================
// Push messages (Firebase)
// =============================================================================
// The Firebase configuration file is NOT kept in the repository: the build takes it from the environment variable
// GOOGLE_SERVICES_JSON (the secret of the CI) or from app/google-services.json (ignored by Git), and turns the four values
// the app needs into build constants. Without either, the constants are empty and the app is built with push off. Nothing of
// the file is ever printed, not even when it is wrong. See docs/PUSH_NOTIFICATIONS.md.
val firebaseConfiguration: Map<String, String> = run {
    val text = providers.environmentVariable("GOOGLE_SERVICES_JSON").orNull?.takeIf { it.isNotBlank() }
        ?: file("google-services.json").takeIf { it.isFile }?.readText()
        ?: return@run emptyMap<String, String>()
    val values = runCatching {
        fun Any?.obj() = this as Map<*, *>
        val root = groovy.json.JsonSlurper().parseText(text).obj()
        val info = root["project_info"].obj()
        val client = (root["client"] as List<*>).map { it.obj() }
            .first { it["client_info"].obj()["android_client_info"].obj()["package_name"] == "com.hydraumc.control" }
        mapOf(
            "project" to info["project_id"] as String,
            "sender" to info["project_number"] as String,
            "app" to client["client_info"].obj()["mobilesdk_app_id"] as String,
            "key" to (client["api_key"] as List<*>).first().obj()["current_key"] as String,
        )
    }.getOrNull()
    // the cause is left out on purpose: the message of a parse error quotes the file
    if (values == null || values.values.any { !it.matches(Regex("[A-Za-z0-9:_.-]+")) }) {
        throw GradleException("The Firebase configuration is not usable: it must be the google-services.json of an Android app registered as com.hydraumc.control")
    }
    values
}
logger.lifecycle(if (firebaseConfiguration.isEmpty()) "PUSH_CONFIG=OFF (no Firebase configuration: the app is built with push off)" else "PUSH_CONFIG=ON")

android {
    namespace = "com.hydraumc.control"
    // 37 required by androidx.core 1.19 and the other current androidx releases
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hydraumc.control"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        minSdk = 24
        // The same level as compileSdk: the behavior changes of Android 16 and 17 were reviewed (see the CHANGELOG).
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "FCM_PROJECT_ID", "\"${firebaseConfiguration["project"] ?: ""}\"")
        buildConfigField("String", "FCM_SENDER_ID", "\"${firebaseConfiguration["sender"] ?: ""}\"")
        buildConfigField("String", "FCM_APP_ID", "\"${firebaseConfiguration["app"] ?: ""}\"")
        buildConfigField("String", "FCM_API_KEY", "\"${firebaseConfiguration["key"] ?: ""}\"")
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
            // Robolectric loads the classes of the app through its own class loader, which the coverage agent does not see unless it is told to
            // include classes without a source location; without this the view model and the screens show as never run.
            all { test ->
                // An exception that no coroutine handles is only reported by whichever later test starts a runTest, and fails that one. Every such
                // exception is written down (UncaughtCoroutineRecorder), printed after the run and fails it, with the stack of its real source.
                val uncaught = layout.buildDirectory.file("uncaught-coroutines-${test.name}.log").get().asFile
                test.systemProperty("hydraUmc.uncaughtCoroutineLog", uncaught.absolutePath)
                test.doFirst { uncaught.delete() }
                test.addTestListener(object : org.gradle.api.tasks.testing.TestListener {
                    override fun beforeSuite(suite: org.gradle.api.tasks.testing.TestDescriptor) {}
                    override fun beforeTest(testDescriptor: org.gradle.api.tasks.testing.TestDescriptor) {}
                    override fun afterTest(testDescriptor: org.gradle.api.tasks.testing.TestDescriptor, result: org.gradle.api.tasks.testing.TestResult) {}
                    override fun afterSuite(suite: org.gradle.api.tasks.testing.TestDescriptor, result: org.gradle.api.tasks.testing.TestResult) {
                        if (suite.parent == null && uncaught.length() > 0) println("Exceptions that no coroutine handled:\n" + uncaught.readText().take(12000))
                    }
                })
                test.doLast {
                    if (uncaught.length() > 0) throw GradleException("An exception that no coroutine handled escaped a unit test; see the list printed above and ${uncaught.path}")
                }
                // A failed test prints its whole cause chain in the log of the CI, so a failure that only shows on a runner can be read there.
                test.testLogging {
                    events("failed")
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                    showCauses = true
                    showStackTraces = true
                }
                test.extensions.findByType(org.gradle.testing.jacoco.plugins.JacocoTaskExtension::class.java)?.apply {
                    isIncludeNoLocationClasses = true
                    excludes = listOf("jdk.internal.*")
                }
            }
        }
    }

    buildTypes {
        debug {
            // Line coverage of the unit tests, measured only when asked for (-PhydraUmcCoverage=true): the instrumentation slows the tests down,
            // so the ordinary run stays plain. The "Android coverage" workflow asks for it.
            enableUnitTestCoverage = providers.gradleProperty("hydraUmcCoverage").isPresent
        }
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

    // Reads the pairing QR code (ui/PairingScanner.kt): the scanner screen of Google Play services, so the app needs no camera permission.
    implementation(libs.play.services.code.scanner)

    // Push messages of the server (see push/FirebasePush.kt). The app starts Firebase itself from the build constants, so
    // the Google Services plugin is not needed.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    // Found while auditing the code: real test
    // coverage for RobotViewModel.sendAtomicCommand()'s own optimistic-
    // mutate/rollback fan-out needs a real (if local) HTTP server to POST
    // against, and viewModelScope's coroutines need a real, deterministic
    // test dispatcher instead of the main-thread one Android normally
    // provides - neither was wired in before this real coverage needed them.
    testImplementation(libs.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
