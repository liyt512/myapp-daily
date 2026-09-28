# 保持 WebView JS 接口
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 保持 Kotlin
-keep class kotlin.** { *; }
-dontwarn kotlin.**