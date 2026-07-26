# Keep kotlinx.serialization models
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.jarvis.assistant.**$$serializer { *; }
-keepclassmembers class com.jarvis.assistant.** {
    *** Companion;
}
-keepclasseswithmembers class com.jarvis.assistant.** {
    kotlinx.serialization.KSerializer serializer(...);
}
