# MediaPipe uses reflection and JNI.
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# MediaPipe's options and results are protobuf-lite messages, read reflectively by field name.
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-dontwarn com.google.protobuf.**
