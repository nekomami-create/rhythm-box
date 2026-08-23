# kotlinx.serialization が使うシリアライザを保持する。
-keepclassmembers class com.example.rhythmbox.core.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.rhythmbox.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}
