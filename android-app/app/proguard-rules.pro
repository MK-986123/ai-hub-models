# ONNX Runtime — keep native interface classes
-keep class ai.onnxruntime.** { *; }
-keep class com.microsoft.onnxruntime.** { *; }

# QNN Runtime — keep JNI bindings
-keep class com.qualcomm.qti.** { *; }

# Room — keep entity classes
-keep class com.qualcomm.aihub.diffusionlab.data.models.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
