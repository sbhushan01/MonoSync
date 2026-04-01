# ========================
# Retrofit
# ========================
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# ========================
# Gson
# ========================
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ========================
# Room
# ========================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Entity class *
-keep class * extends androidx.room.migration.Migration
-dontwarn androidx.room.paging.**

# ========================
# OkHttp
# ========================
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ========================
# Media3 (Optimized Shrinking)
# ========================
# Only keep necessary Media3 classes rather than all of ** to avoid bloat
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.session.** { *; }
-keep class androidx.media3.ui.** { *; }
-keep class androidx.media3.datasource.** { *; }
-keep class androidx.media3.extractor.** { *; }
-dontwarn androidx.media3.**

# ========================
# Coroutines
# ========================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** { volatile <fields>; }

# ========================
# MonoSync Data Models
# ========================
# Essential to prevent Network + Database deserialization crashes
-keep class com.monosync.model.** { *; }
-keep class com.monosync.data.db.** { *; }
-keep class com.monosync.data.remote.** { *; }

# ========================
# Hilt / Dagger
# ========================
-keep,allowobfuscation,allowshrinking @dagger.Module class *
-keep,allowobfuscation,allowshrinking @dagger.Provides class *
-dontwarn dagger.internal.codegen.**
