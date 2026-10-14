# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# JNI Keep Rules
-keepclasseswithmembernames class com.example.service.LlamaCppInferenceEngine { native <methods>; }

# Room Keep Rules
-keep class com.example.model.** { *; }
-keep class com.example.data.** { *; }

# Serialization Keep Rules
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keep class kotlinx.serialization.** { *; }
