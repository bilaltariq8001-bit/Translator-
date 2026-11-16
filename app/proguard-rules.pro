# R8 rules for the release build. Libraries in this app (Room, OkHttp, Firebase AI, ML Kit, CameraX, Compose, DataStore)
# ship their own consumer rules; only what they leave out is listed here.

# OkHttp optional TLS providers that are not on Android.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Keep stack traces readable and generic signatures intact for reflection-based libraries.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# The AI error handling recognises Firebase AI failures (quota, overloaded, blocked ...) by their class name, so a library
# update never breaks compilation. R8 renames classes in release builds, which made every AI failure look "unknown" there:
# no switch to the backup model on quota/overload and the wrong message. Keep those names.
-keepnames class com.google.firebase.ai.type.** extends java.lang.Throwable
-keepnames class kotlinx.coroutines.TimeoutCancellationException
