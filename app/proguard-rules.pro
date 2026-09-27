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

# Reglas para BASS core (JNI)
# Preserva el wrapper principal de BASS porque los símbolos JNI en libbass.so
# codifican el nombre exacto del paquete. Si R8 renombra esta clase,
# el enlace nativo se rompe en runtime.
-keep class com.un4seen.bass.BASS { *; }
-keepclassmembers class com.un4seen.bass.BASS { *; }

# Reglas para BASSmix (JNI)
# Preserva el wrapper de BASSmix porque los símbolos JNI en libbassmix.so
# codifican el nombre exacto del paquete. Si R8 renombra esta clase,
# el enlace nativo se rompe en runtime.
-keep class com.un4seen.bass.BASSmix { *; }
-keepclassmembers class com.un4seen.bass.BASSmix { *; }

# Reglas para BASS_AAC (JNI)
# Preserva el wrapper de BASS_AAC porque los símbolos JNI en libbass_aac.so
# codifican el nombre exacto del paquete. Si R8 renombra esta clase,
# el enlace nativo se rompe en runtime.
-keep class com.un4seen.bass.BASS_AAC { *; }
-keepclassmembers class com.un4seen.bass.BASS_AAC { *; }

# Reglas para BASSOPUS (JNI)
# Preserva el wrapper de BASSOPUS porque los símbolos JNI en libbassopus.so
# codifican el nombre exacto del paquete. Si R8 renombra esta clase,
# el enlace nativo se rompe en runtime.
-keep class com.un4seen.bass.BASSOPUS { *; }
-keepclassmembers class com.un4seen.bass.BASSOPUS { *; }

# Reglas para BASSFLAC (JNI)
# Preserva el wrapper de BASSFLAC porque los símbolos JNI en libbassflac.so
# codifican el nombre exacto del paquete. Si R8 renombra esta clase,
# el enlace nativo se rompe en runtime.
-keep class com.un4seen.bass.BASSFLAC { *; }
-keepclassmembers class com.un4seen.bass.BASSFLAC { *; }

# Reglas para BASSALAC (JNI)
# Preserva el wrapper de BASSALAC porque los símbolos JNI en libbassalac.so
# codifican el nombre exacto del paquete. Si R8 renombra esta clase,
# el enlace nativo se rompe en runtime.
-keep class com.un4seen.bass.BASSALAC { *; }
-keepclassmembers class com.un4seen.bass.BASSALAC { *; }

# Reglas para BASSAPE (JNI)
# Preserva el wrapper de BASSAPE porque los símbolos JNI en libbassape.so
# codifican el nombre exacto del paquete. Si R8 renombra esta clase,
# el enlace nativo se rompe en runtime.
-keep class com.un4seen.bass.BASSAPE { *; }
-keepclassmembers class com.un4seen.bass.BASSAPE { *; }

# Reglas para BASSWV (JNI)
# Preserva el wrapper de BASSWV porque los símbolos JNI en libbasswv.so
# codifican el nombre exacto del paquete. Si R8 renombra esta clase,
# el enlace nativo se rompe en runtime.
-keep class com.un4seen.bass.BASSWV { *; }
-keepclassmembers class com.un4seen.bass.BASSWV { *; }

# Reglas para BASSDSD (JNI)
# Preserva el wrapper de BASSDSD porque los símbolos JNI en libbassdsd.so
# codifican el nombre exacto del paquete. Si R8 renombra esta clase,
# el enlace nativo se rompe en runtime.
-keep class com.un4seen.bass.BASSDSD { *; }
-keepclassmembers class com.un4seen.bass.BASSDSD { *; }