# ProGuard rules for ML Kit, Firebase, CameraX, and Room
-keep class com.google.mlkit.** { *; }
-keep class com.google.firebase.** { *; }
-keep class androidx.camera.** { *; }
-keep class androidx.room.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.firebase.**
-dontwarn androidx.camera.**
-dontwarn androidx.room.**

# Prevent stripping ML Kit vision classes
-keep class com.google.mlkit.vision.** { *; }

# Prevent stripping Firebase classes
-keep class com.google.firebase.ml.modeldownloader.** { *; }