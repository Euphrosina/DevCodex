# kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class ai.devcodex.agent.**$$serializer { *; }
-keepclassmembers class ai.devcodex.agent.** { *** Companion; }
-keepclasseswithmembers class ai.devcodex.agent.** { kotlinx.serialization.KSerializer serializer(...); }
# Shizuku（UserService 类必须保留原名——Shizuku 端进程需要反射加载）
-keep class rikka.shizuku.** { *; }
-keep class a.** { *; }
-keep class ai.devcodex.agent.shell.** { *; }
-dontwarn rikka.shizuku.**

# jlatexmath（反射加载字体/XML 资源）
-keep class ai.devcodex.agent.latex.** { *; }
-dontwarn org.scilab.forge.**
