# R8 rules for the release build.
# libadb-android: reflection on its connection manager and protocol classes.
-keep class io.github.muntashirakon.adb.** { *; }
-dontwarn io.github.muntashirakon.adb.**
# SPAKE2 (native, JNI callbacks)
-keep class io.github.muntashirakon.crypto.** { *; }
-keep class io.github.muntashirakon.spake2.** { *; }
# BouncyCastle: JCA provider classes are loaded reflectively by name.
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
# Conscrypt: provider + JNI.
-keep class org.conscrypt.** { *; }
-dontwarn org.conscrypt.**
-dontwarn com.android.org.conscrypt.**
-dontwarn org.apache.harmony.xnet.provider.jsse.**
-dontwarn sun.security.**
-dontwarn javax.naming.**
-keepclasseswithmembernames class * { native <methods>; }
# Device admin receiver is referenced from the manifest and by the dpm component string.
-keep class app.enough.admin.EnoughAdminReceiver
