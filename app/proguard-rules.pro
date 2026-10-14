-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep,includedescriptorclasses class com.wonderful.launcher.**$$serializer { *; }
-keepclassmembers class com.wonderful.launcher.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
