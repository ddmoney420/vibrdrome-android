# Vibrdrome ProGuard Rules

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembernames class kotlinx.serialization.internal.** {
    *** INSTANCE;
}

# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-keep class kotlin.reflect.jvm.internal.** { *; }
-dontwarn org.slf4j.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Koin
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# Media3 / ExoPlayer
-dontwarn androidx.media3.**

# Cast SDK
-keep class com.vibrdrome.app.cast.CastOptionsProvider { *; }
-keep class com.google.android.gms.cast.** { *; }
-dontwarn com.google.android.gms.cast.**

# App models (serializable data classes)
-keep class com.vibrdrome.app.network.** { *; }
-keep class com.vibrdrome.app.persistence.** { *; }
-keep class com.vibrdrome.app.ui.SavedServer { *; }
-keep class com.vibrdrome.app.ui.navigation.** { *; }

# Compose Navigation type-safe routes
-keep @kotlinx.serialization.Serializable class * { *; }

# Last.fm API models
-keep class com.vibrdrome.app.network.LastFm** { *; }

# Update checker models
-keep class com.vibrdrome.app.util.GitHubRelease { *; }

# NowPlaying toolbar config
-keep class com.vibrdrome.app.ui.player.ToolbarAction { *; }
