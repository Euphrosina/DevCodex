# kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class ai.devcodex.agent.**$$serializer { *; }
-keepclassmembers class ai.devcodex.agent.** { *** Companion; }
-keepclasseswithmembers class ai.devcodex.agent.** { kotlinx.serialization.KSerializer serializer(...); }
# Shizuku
-keep class rikka.shizuku.** { *; }
-keep class a.** { *; }
-dontwarn rikka.shizuku.**
