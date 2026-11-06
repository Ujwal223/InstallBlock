# Add project specific ProGuard rules here.
# Keep apksig signing classes: R8 would otherwise strip them as "unused" because they are
# only called reflectively through JCE provider lookup.
-keep class com.android.apksig.** { *; }
-keep class com.android.tools.build.apksig.** { *; }

# Keep Shizuku binder stubs.
-keep class rikka.shizuku.** { *; }

# Keep kotlinx.serialization infrastructure.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.ujwal.installblock.**$$serializer { *; }
-keepclassmembers class com.ujwal.installblock.** {
    *** Companion;
}
-keepclasseswithmembers class com.ujwal.installblock.** {
    kotlinx.serialization.KSerializer serializer(...);
}

