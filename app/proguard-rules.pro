# libVLC calls back into its Java classes from native code (JNI); keep them intact.
-keep class org.videolan.libvlc.** { *; }
-dontwarn org.videolan.**
