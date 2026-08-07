# Kotlin
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void check*(...);
    public static void throw*(...);
}
-assumenosideeffects class java.util.Objects {
    public static ** requireNonNull(...);
}

# Strip debug log
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# Xposed
-keep class de.robv.android.xposed.**

# PixelTaskbarEnabler - for debug and trace
-keep class dev.beyman.pixeltaskbarenabler.** { public protected private *; }

# AndroidX
-keepnames class androidx.compose.ui.**


# Keep the ConstraintLayout Motion class
-keep,allowoptimization,allowobfuscation class androidx.constraintlayout.motion.widget.** { *; }

# Keep Recycler View Stuff
-keep,allowoptimization,allowobfuscation class androidx.recyclerview.widget.** { *; }

# Keep Parcelable Creators
-keepnames class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Services
-keep interface **.I* { *; }
-keep class **.I*$Stub { *; }
-keep class **.I*$Stub$Proxy { *; }
-keep class dev.beyman.pixeltaskbarenabler.service.* { *; }

# Keep all inner classes and their names within the specified package
# but allow optimization of their internal code
-keep class dev.beyman.pixeltaskbarenabler.**$* {
    public protected private *;
}

# Allow optimization and shrinking for all classes
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
-keepattributes Exceptions,InnerClasses,Signature,Deprecated,*Annotation*,EnclosingMethod,SourceFile,LineNumberTable

# Keep all native method names
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep all annotation types
-keep @interface ** { *; }

# AndroidX Window
-dontwarn androidx.window.extensions.area.ExtensionWindowAreaPresentation
-dontwarn androidx.window.extensions.core.util.function.Consumer
-dontwarn androidx.window.extensions.core.util.function.Function
-dontwarn androidx.window.extensions.core.util.function.Predicate

# Fix R8 missing class errors for Guava / ErrorProne annotations
-dontwarn javax.lang.model.**
-dontwarn com.google.errorprone.annotations.**