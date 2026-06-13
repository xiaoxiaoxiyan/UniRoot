# UniRoot ProGuard Rules

# Keep JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep NativeBridge
-keep class com.uniroot.native.NativeBridge { *; }

# Keep provider enums
-keepclassmembers enum class com.uniroot.provider.RootProvider {
    *;
}
-keepclassmembers enum class com.uniroot.provider.RootCategory {
    *;
}

# Keep data classes
-keep class com.uniroot.provider.RootState { *; }
-keep class com.uniroot.patch.PatchResult { *; }
-keep class com.uniroot.patch.AK3Flasher$FlashResult { *; }
-keep class com.uniroot.patch.KPMManager$KPMModule { *; }

# Compose
-dontwarn androidx.compose.**

# Hilt
-dontwarn dagger.hilt.**

# Kotlin
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
