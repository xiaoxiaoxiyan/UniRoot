# UniRoot ProGuard Rules

# Keep JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep NativeBridge
-keep class com.uniroot.native.NativeBridge

# Keep data classes
-keep class com.uniroot.provider.RootState
-keep class com.uniroot.patch.PatchResult

# Compose
-dontwarn androidx.compose.**

# Hilt
-dontwarn dagger.hilt.**

# Kotlin
-dontwarn kotlin.**
