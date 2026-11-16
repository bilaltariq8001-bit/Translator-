# R8 rules for the release build. Libraries in this app (Room, OkHttp, Firebase AI, ML Kit, CameraX, Compose, DataStore)
# ship their own consumer rules; only what they leave out is listed here.

# OkHttp optional TLS providers that are not on Android.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Keep stack traces readable and generic signatures intact for reflection-based libraries.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
