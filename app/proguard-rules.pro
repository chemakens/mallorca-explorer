# Keep @Serializable classes — kotlinx.serialization accesses them by reflection
-keep @kotlinx.serialization.Serializable class com.mallorca.explorer.** { *; }

# Keep Room entities and DAOs — referenced by generated Room code
-keep class com.mallorca.explorer.core.data.database.entity.** { *; }
-keep class com.mallorca.explorer.core.data.database.dao.** { *; }

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# Keep our Retrofit API service interfaces — Retrofit creates dynamic proxies via reflection
# and needs the interface methods and their annotations to remain intact after R8 minification
-keep interface com.mallorca.explorer.core.data.network.** { *; }

# Keep qualifier annotations used for Dagger/Hilt multi-bindings — R8 must not strip them at runtime
-keep @javax.inject.Qualifier @interface *

# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Hilt
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }

# MapLibre
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Coil
-dontwarn coil.**
