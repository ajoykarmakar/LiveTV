# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep Compose metadata
-keepattributes *Annotation*
-keepclassmembers class ** {
    @androidx.compose.runtime.Composable *;
}

# Preserve serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,allowoptimization class kotlinx.serialization.** { *; }

# OkHttp/Coil rules
-dontwarn okhttp3.**
-dontwarn okio.**
