# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.**

# MediaPipe LLM Inference (on-device Gemma)
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
-keep class com.google.flatbuffers.** { *; }
-dontwarn com.google.flatbuffers.**

# Jetpack Compose — Compose compiler already handles most, keep navigation-safe
-dontwarn androidx.compose.**

# DataStore protobuf internals
-keep class androidx.datastore.*.** { *; }

# Keep ViewModels (reflection by ViewModelProvider on public constructors)
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class * extends androidx.lifecycle.AndroidViewModel {
    <init>(...);
}

# AutoValue / JavaPoet (transitively pulled in by MediaPipe tasks-genai).
# These reference javax.lang.model.* and javax.annotation.processing.* which
# are compile-time-only and not present on Android. Safe to suppress.
-dontwarn javax.lang.model.**
-dontwarn javax.annotation.processing.**
-dontwarn autovalue.shaded.**
-dontwarn com.google.auto.value.**

# AR Lens — JNI / reflection-friendly classes
-keep class com.app.ttsreader.ocr.SpatialWord { *; }
-keep class com.app.ttsreader.ocr.NativeOcrEngine { *; }
