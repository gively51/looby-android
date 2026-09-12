# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.viple.looby.**$$serializer { *; }
-keepclassmembers class com.viple.looby.** { *** Companion; }
-keepclasseswithmembers class com.viple.looby.** { kotlinx.serialization.KSerializer serializer(...); }

# Retrofit
-keepattributes Signature, Exceptions
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# SignalR (Gson interne)
-keep class com.microsoft.signalr.** { *; }
-keep class com.google.gson.** { *; }
-dontwarn org.slf4j.**
-dontwarn io.reactivex.rxjava3.**

# AppAuth
-keep class net.openid.appauth.** { *; }
