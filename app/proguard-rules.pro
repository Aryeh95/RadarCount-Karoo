# Varia Radar Pro ProGuard Rules

# Keep Karoo Extension classes
-keep class io.github.ykn.variaradarpro.VariaRadarExtension { *; }
-keep class io.github.ykn.variaradarpro.datatypes.** { *; }

# Keep data models for serialization
-keep class io.github.ykn.variaradarpro.data.models.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.ykn.variaradarpro.**$$serializer { *; }
-keepclassmembers class io.github.ykn.variaradarpro.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.ykn.variaradarpro.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Glance
-keep class androidx.glance.** { *; }
