# Add project specific ProGuard rules here.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Keep Gson serialized data models
-keep class io.github.ramusriram.stopwake.data.** { *; }
-keep class io.github.ramusriram.stopwake.viewmodel.GeocodingResult { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# OSMDroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**