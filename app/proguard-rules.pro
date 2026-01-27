# Add project specific ProGuard rules here.

# Keep TensorFlow Lite classes
-keep class org.tensorflow.** { *; }
-keep class com.google.mediapipe.** { *; }

# Keep exercise detector classes
-keep class com.medipose.exercises.** { *; }
