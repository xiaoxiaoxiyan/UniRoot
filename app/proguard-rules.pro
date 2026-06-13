# UniRoot ProGuard Rules

# Keep JNI methods
-keepclasswithmembernames class * {
    native <methods>;
}

# Keep NativeBridge
-keep class com.uniroot.native.NativeBridge { *; }

# Keep provider enums
-keep class com.uniroot.provider.RootProvider { *; }
-keep class com.uniroot.provider.RootCategory { *; }

# Keep serializable data classes
-keep class com.uniroot.provider.RootState { *; }
-keep class com.uniroot.patch.PatchResult { *; }
-keep class com.uniroot.patch.AK3Flasher$FlashResult { *; }
-keep class com.uniroot.patch.KPMManager$KPMModule { *; }

# Compose
-dontwarn androidx.compose.**
