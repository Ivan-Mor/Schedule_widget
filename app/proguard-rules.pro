# Правила ProGuard/R8 (minify в MVP выключен, файл — задел на будущее).
# Jsoup и kotlinx.serialization чувствительны к обфускации.
-keep class org.jsoup.** { *; }
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keepclasseswithmembers class com.malinovka.widget.** {
    kotlinx.serialization.KSerializer serializer(...);
}
