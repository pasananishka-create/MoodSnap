# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class * implements androidx.room.TypeConverter { *; }
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}
-keep class com.moodcamera.data.model.** { *; }
-keep class com.moodcamera.data.local.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keepattributes RuntimeVisibleAnnotations
-keepattributes *Annotation*

# Hilt generated
-keep class **_Factory { *; }
-keep class **_MembersInjector { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class *.* { *; }

# CameraX
-keep class androidx.camera.** { *; }

# ML Kit
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_** { *; }

# Compose
-keep class androidx.compose.** { *; }

# DataStore
-keep class androidx.datastore.** { *; }

# Coil
-keep class coil.** { *; }

# ExifInterface
-keep class androidx.exifinterface.** { *; }

# Keep app models
-keep class com.moodcamera.domain.model.** { *; }
-keep class com.moodcamera.processing.** { *; }
-keep class com.moodcamera.ai.** { *; }
