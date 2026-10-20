# Plexiplay : règles de conservation pour la version obfusquée (R8).
# Les lignes ci-dessous empêchent R8 de renommer ou retirer ce qui est appelé par réflexion, par le code natif ou par la sérialisation.

# FFmpeg (décodeurs logiciels) : chargé par nom de classe (DeviceCodecs), facultatif.
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-dontwarn androidx.media3.decoder.ffmpeg.**

# mpv (libmpvkt) : appelé par le code natif (JNI).
-keep class is.xyz.mpv.** { *; }
-keep class io.github.yuroyami.libmpvkt.** { *; }
-keepclasseswithmembernames class * { native <methods>; }

# kotlinx.serialization : modèles JSON de Plex (tv.plexiplay.**).
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod, RuntimeVisibleAnnotations
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class tv.plexiplay.**$$serializer { *; }
-keepclassmembers class tv.plexiplay.** { *** Companion; }
-keepclasseswithmembers class tv.plexiplay.** { kotlinx.serialization.KSerializer serializer(...); }

# Bibliothèques réseau : classes facultatives absentes d'Android.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
