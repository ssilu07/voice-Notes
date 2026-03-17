# Add project specific ProGuard rules here.

# Preserve line numbers for debugging stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room Database - keep entity classes
-keep class com.royals.voicenotes.Note { *; }
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keepclasseswithmembers class * {
    @dagger.hilt.* <methods>;
}

# Keep all classes annotated with Hilt annotations
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keep @dagger.Module class * { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# AndroidX Lifecycle
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }

# Navigation component
-keepnames class * extends android.os.Parcelable
-keepnames class * extends java.io.Serializable

# Biometric
-keep class androidx.biometric.** { *; }

# Backup/Restore JSON parsing - keep Note fields for serialization
-keepclassmembers class com.royals.voicenotes.Note {
    <init>(...);
    <fields>;
}

# Jetpack Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# BroadcastReceivers
-keep class com.royals.voicenotes.ReminderReceiver { *; }
-keep class com.royals.voicenotes.BootReceiver { *; }

# Widget
-keep class com.royals.voicenotes.NoteWidgetProvider { *; }
-keep class com.royals.voicenotes.NoteWidgetService { *; }

# Keep R8 from stripping interfaces used by Hilt
-keep,allowobfuscation interface * extends dagger.hilt.internal.GeneratedComponent
