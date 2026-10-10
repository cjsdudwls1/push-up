# MediaPipe Tasks reaches its native/proto layer reflectively; keep it whole.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**
-dontwarn autovalue.shaded.**
-dontwarn com.google.auto.value.**

# MediaPipe logs through Flogger, which finds its backend by name:
# Class.forName("com.google.common.flogger.backend.system.DefaultPlatform"). Renamed, that lookup
# fails, the first MediaPipe class with a logger (Graph) cannot initialise, and creating the pose
# landmarker throws an ExceptionInInitializerError. The Play build crashed the moment the camera
# permission was granted (2026-10-10), while every debug build worked.
-keep class com.google.common.flogger.** { *; }
-dontwarn com.google.common.flogger.**

# Room generates implementations that are looked up by name.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# kotlinx.serialization keeps generated serializers on the companion.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.pushuprpg.** {
    *** Companion;
}
-keepclasseswithmembers class com.pushuprpg.** {
    kotlinx.serialization.KSerializer serializer(...);
}
