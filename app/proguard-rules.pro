# Add project specific ProGuard rules here.

# Keep TensorFlow Lite classes
-keep class org.tensorflow.** { *; }

# Keep only MediaPipe task APIs used by the app. Keeping all framework classes can
# pull in optional graph/proto types that are not packaged in runtime artifacts.
-keep class com.google.mediapipe.tasks.** { *; }

# Ignore optional compile-time/annotation and proto classes referenced by bundled libs.
-dontwarn com.google.mediapipe.proto.**
-dontwarn javax.lang.model.**

# Keep exercise detector classes
-keep class com.alignify.exercises.** { *; }
