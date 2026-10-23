# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Enable code optimization and class obfuscation for maximum APK size reduction.
# We remove -dontobfuscate and -dontoptimize to allow the R8 full-mode optimizer
# to inline, merge classes, and compress code.

# Keep critical attributes for generics (Signature) and runtime annotations
-keepattributes *Annotation*,Signature

# Preserve Android Components and Entry Points
-keep class com.example.MainActivity { *; }
-keep class com.example.CalendarApplication { *; }
-keep class com.example.widget.CalendarWidgetProvider { *; }
-keep class com.example.widget.WidgetBootReceiver { *; }

# Keep Room Database definitions, DAOs, and Entity mappings fully intact
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers class * {
    @androidx.room.Database *;
}

# Keep data models in data package
-keep class com.example.data.** { *; }

