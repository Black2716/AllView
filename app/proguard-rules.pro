# commonmark 为纯 Java 库，无反射调用，R8 可正常裁剪；这里只保留公有 API 名以便堆栈可读
-keepnames class org.commonmark.** { *; }
-dontwarn org.commonmark.**

# R8 全模式下 Compose 的 CompositionLocal 提供者会被内联掉，导致
# "CompositionLocal LocalLifecycleOwner not present"，必须显式保留
-keep class androidx.compose.ui.platform.AndroidCompositionLocals_androidKt { *; }
-keep class androidx.lifecycle.compose.** { *; }
-keep class androidx.compose.runtime.CompositionLocalKt { *; }
-keepclassmembers class androidx.compose.runtime.** { *; }
