# SQLCipher ProGuard rules to prevent JNI methods from being stripped
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-keep class net.sqlcipher.database.SQLiteDatabase { *; }

# Keep kotlinx.serialization classes and metadata
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keep class * {
    @kotlinx.serialization.Serializable class *;
}

# General optimization & obfuscation
-allowaccessmodification
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# GSON rules
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.** { *; }

# Google APIs Client Library rules
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.** { *; }
-keep class com.google.api.services.calendar.** { *; }
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.j2objc.** { *; }
-dontwarn com.google.api.client.**
-dontwarn com.google.api.services.**
-dontwarn javax.annotation.**

# Room Database rules
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

# Hilt/Dagger rules
-keep class dagger.hilt.** { *; }
-keep class com.example.geminimultimodalliveapi.di.** { *; }
-keep class * {
    @dagger.hilt.android.AndroidEntryPoint class *;
    @dagger.hilt.android.HiltAndroidApp class *;
}
-dontwarn dagger.hilt.**

# Dontwarn for missing dependencies in optional features of PDFBox, Tink, SLF4J, and Play Services
-dontwarn com.gemalto.jp2.**
-dontwarn org.joda.time.**
-dontwarn org.slf4j.impl.**
-dontwarn com.google.android.gms.internal.location.**