-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.gemalto.jp2.**

# Readable crash stack traces in Play Console (with the uploaded mapping.txt)
-keepattributes SourceFile,LineNumberTable,Signature,*Annotation*,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# ML Kit Document Scanner (Google Play services). It hands Parcelables and options across
# processes and loads some classes by name; R8 full mode can strip/rename them, which crashes
# the scanner only in release builds.
-keep class com.google.mlkit.vision.documentscanner.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_document_scanner.** { *; }
-keep class com.google.mlkit.common.** { *; }
-keep class com.google.android.gms.common.internal.safeparcel.** { *; }
-keepclassmembers class * implements android.os.Parcelable { public static final ** CREATOR; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**
