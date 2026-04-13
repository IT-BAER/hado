-keepattributes Signature
-keepattributes *Annotation*

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# Gson — data model classes
-keep class com.baer.hado.data.model.** { *; }
-keep class com.baer.hado.data.local.LocalTodoStore$* { *; }

# Gson — widget data classes serialized to/from Glance state
-keep class com.baer.hado.widget.WidgetListData { *; }
-keep class com.baer.hado.widget.WidgetSettings { *; }
-keep class com.baer.hado.widget.WidgetSettings$** { *; }

# Gson — inner data classes used for token refresh JSON parsing
-keep class com.baer.hado.widget.WidgetHttpClient$RefreshTokenResponse { *; }

# Gson — preserve @SerializedName on all fields (R8 full mode)
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Gson TypeToken — preserve generic signatures for R8 full mode
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Glance
-keep class androidx.glance.** { *; }

# OkHttp (prevent R8 from stripping platform classes)
-dontwarn okhttp3.internal.platform.**

# Strip debug/verbose log calls in release builds
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

# Google Tink (EncryptedSharedPreferences)
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
