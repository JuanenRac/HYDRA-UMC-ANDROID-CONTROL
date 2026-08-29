# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# OkHttp references these optional TLS-provider integrations defensively
# (BouncyCastle/Conscrypt/OpenJSSE) - none of the three is an actual
# dependency of this app (only the platform's built-in TLS stack is used),
# so R8 cannot resolve them and fails minifyReleaseWithR8 without these
# standard, OkHttp-documented -dontwarn rules. Exact text AGP itself
# generated into app/build/outputs/mapping/release/missing_rules.txt on
# the first real release build (first time minification was exercised
# end-to-end for this app).
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE

# Google Tink (androidx.security.crypto's own EncryptedSharedPreferences/
# MasterKey, used by AuthPrefs.kt to store the login session - read at app
# startup, in RobotViewModel's own init) registers its crypto primitives
# via reflection at runtime (Class.forName()-style lookups R8's static
# reachability analysis can't see), so R8 silently strips almost the
# entire com.google.crypto.tink.** tree as "unreachable" dead code without
# this - confirmed against the first real release build's own
# app/build/outputs/mapping/release/usage.txt, which listed ~4390 lines of
# removed Tink classes. That crashed the app before any UI ever rendered
# (a stripped class MasterKey.Builder needs at runtime, not a compile-time
# error, since R8 has no way to know these are still needed). Standard,
# widely-documented keep rule for this exact library.
-keep class com.google.crypto.tink.** { *; }
-keep interface com.google.crypto.tink.** { *; }
-keepclassmembers class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# androidx.work (WorkManager) is a TRANSITIVE dependency only - nothing in
# this app's own code calls WorkManager, but it self-registers via
# androidx.startup.InitializationProvider (a ContentProvider that runs
# BEFORE Application.onCreate, before any UI) regardless, so it still
# crashes app startup if R8 strips something it needs. Confirmed live via
# a real adb logcat on a real device (v0.3.2, which already had the Tink
# fix below and still crashed identically): "Unable to get provider
# androidx.startup.InitializationProvider: Failed to create an instance
# of androidx.work.impl.WorkDatabase" - WorkManager's own Room database,
# which R8 had stripped (confirmed against usage.txt: ~627 removed
# androidx.work.** lines). Same class of bug, same standard fix as Tink.
-keep class androidx.work.** { *; }
-keep interface androidx.work.** { *; }
-dontwarn androidx.work.**
