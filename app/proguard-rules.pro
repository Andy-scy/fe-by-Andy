# Keep Shizuku reflection entry points (rikka.shizuku.* is loaded reflectively).
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }

# Keep our JSON model field names stable for debugging readability.
-keepclassmembers class com.andy.etsviewer.data.model.** { <fields>; }

# Compose / Kotlin metadata noise
-dontwarn org.jetbrains.annotations.**
