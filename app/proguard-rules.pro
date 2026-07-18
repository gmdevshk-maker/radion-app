# kotlinx-serialization: @Serializable 클래스의 Companion/serializer가 리플렉션으로 조회되므로 보존
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers @kotlinx.serialization.Serializable class com.radion.app.data.** {
    *** Companion;
    *** serializer(...);
}
-keepclasseswithmembers class com.radion.app.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
