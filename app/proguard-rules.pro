# 顺场 Android 壳的 R8 规则。
# Sparkling / Lynx 大量通过反射、JNI 和类字面量创建实例，按包整体保留最稳妥。

# 宿主入口与桥接：Activity、Application、LynxModule 都是按类名实例化的。
-keep class com.shunchang.app.** { *; }
-keepclassmembers class com.shunchang.app.** { *; }

# Sparkling 容器：Behavior、TemplateProvider、IDL Method 由配置注册后反射调用。
-keep class com.tiktok.sparkling.** { *; }
-keep interface com.tiktok.sparkling.** { *; }
-keepclassmembers class com.tiktok.sparkling.** { *; }

# Lynx 运行时：native 方法、@LynxMethod / @LynxProp 均按名称查找。
-keep class com.lynx.** { *; }
-keep interface com.lynx.** { *; }
-keep class org.lynxsdk.** { *; }
-dontwarn com.lynx.**
-dontwarn org.lynxsdk.**

# 反射读取注解与泛型签名。
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# manifest 中登记的 FileProvider 及其路径元数据。
-keep class androidx.core.content.FileProvider { *; }
-keep class androidx.core.content.FileProvider$* { *; }

# Sparkling 由 Kotlin 实现，保留元数据供反射/协程使用。
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
