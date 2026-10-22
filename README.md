# Plexiplay Android

Version tablette et smartphone de Plexiplay, lecteur léger pour un serveur Plex. Projet personnel, non affilié à Plex Inc.

Identifiant : `tv.plexiplay.mobile` (peut coexister avec la version TV `tv.plexiplay.app`). Version 1.5.6.

Cette version reprend le cœur de la version TV (connexion Plex, bibliothèques, fiches, lecture directe ou transcodée, mpv, langues et sous-titres, mises à jour) avec une interface pensée pour le tactile.

APK signé, installable : dernière version dans les [releases](https://github.com/Kahenis/plexiplay-android/releases). Les nouvelles versions de test sont publiées en pré-release.

Pour recompiler :

```
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:assembleRelease
```
