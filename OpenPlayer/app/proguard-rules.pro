# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Reglas para BassNative (JNI)
# Preserva los métodos nativos para que R8 no los elimine
-keep class com.openplayer.music.native.BassNative {
    native <methods>;
}

# Reglas para BASS y BASSmix (JNI)
# Preserva todas las clases del paquete com.un4seen.bass porque los símbolos
# JNI en libbass.so y libbassmix.so codifican el nombre exacto del paquete.
# Si R8 renombra estas clases, el enlace nativo se rompe en runtime.
-keep class com.un4seen.bass.** { *; }
-keepclassmembers class com.un4seen.bass.** { *; }