# ===================================================================
# R8 / ProGuard Obfuscation & Security Rules for Jarvis AI
# ===================================================================

# Aggressive package flattening & class renaming
-repackageclasses 'o'
-allowaccessmodification
-renamesourcefileattribute ""

# Strip debugging symbols & line numbers
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Strip Log statements from release binaries to prevent token/state leakage
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}

# Keep classes annotated with @Keep (e.g. data models, serialized fields)
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# Keep Android view binding classes generated at compile time
-keep class com.jarvis.assistant.databinding.** { *; }

# OkHttp & Okio rules
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# JSON
-keep class org.json.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Firebase & Google Play Services
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

