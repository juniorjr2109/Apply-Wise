# kotlinx.serialization: keep generated serializers for DTOs and navigation routes.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.webwizards.applywise.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.webwizards.applywise.**$$serializer { *; }
# Retrofit uses generic signatures of suspend functions.
-keepattributes Signature, Exceptions
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
