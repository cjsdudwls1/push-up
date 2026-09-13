# MediaPipe Tasks reaches its native/proto layer reflectively; keep it whole.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**
-dontwarn autovalue.shaded.**
-dontwarn com.google.auto.value.**

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
