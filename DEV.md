# Plexiplay Android : journal

## 1.5.0 : départ de la version tablette / smartphone

- Code de départ : version TV 1.4.1 (sans historique). Même cœur `core/plex`, `core/player`.
- Identifiant `tv.plexiplay.mobile`, nom d'appareil Plex « Plexiplay Android ». Le lanceur normal est ajouté ; `leanback` n'est plus obligatoire ; rotation libre.
- Mise à jour : canal unique, la dernière release GitHub de ce dépôt (voir 1.5.2).
- Pas d'obfuscation (R8 désactivé) tant que le dépôt est public. Modules verrouillés ouverts le temps du chantier tactile.

### Tactile (1.5.0)

- `ui/theme/Touch.kt` : `tap` et `tapOrHold` s'ajoutent à côté des touches de télécommande existantes (mêmes actions), `isTablet()` (≥ 600 dp).
- Rail : toujours déployé sur tablette ; sur téléphone, icônes seules, un toucher sur le logo déploie le rail. Onglets, affiches (appui long = « Retirer de Continuer à regarder »), roue des paramètres, saisons et épisodes (appui long = vu / non vu), distribution, résumé et fiche acteur (bouton « Fermer »).
- Recherche : champ de saisie avec le clavier du téléphone (le clavier à l'écran de la TV est retiré).
- Connexion : bouton « Se connecter dans le navigateur » (page Plex), mise en page en colonne sur téléphone.
- Fiches film et série : mise en page compacte (< 600 dp) : colonne qui défile, boutons qui passent à la ligne.
- Dialogues (confirmation, mise à jour, invitations, bibliothèques, volume) : lignes et boutons tactiles ; un fond `tap { }` empêche les toucher de traverser.
- Lecteur (ExoPlayer et mpv) : `ui/player/TouchControls.kt` — toucher = afficher/masquer les commandes, double toucher = ±10 s, glissement vertical = luminosité (gauche) / volume (droite), barre de progression tactile (toucher ou glisser, saut au relâchement), listes audio et sous-titres tactiles, écran en paysage et barres masquées pendant la lecture, pause quand l'appli passe en arrière-plan.
- `MainActivity.onStop` ne ferme plus l'appli (comportement TV retiré).

## 1.5.1 : réglage « Puissance » retiré

- Testé sur tablette : fonctionne bien dans l'ensemble.
- Paramètres : la ligne « Puissance » (SERVER / TV) est supprimée. `SessionStore.settings()` fixe `engine = Engine.TV` (l'appareil lit lui-même ce qu'il sait décoder, le serveur ne convertit que le reste), y compris pour un ancien choix SERVER. Le nom interne `TV` est conservé (partagé avec `core/player`).

## 1.5.2 : mise à jour par GitHub uniquement

- Constat : aucune mise à jour détectée. `guilard.com/dl/android/` ne répond pas, et le workflow (repris de la TV) publiait les versions en pre-release, que l'API « releases/latest » ignore : réponse 404.
- Canal unique : la dernière release normale de `Kahenis/plexiplay-android`. `AppUpdate.latest` interroge directement GitHub (plus de guilard.com, plus d'attente de 14 s quand le serveur ne répond pas). `UpdateOffer.fromServer` et le repli serveur → GitHub sont retirés. `checkApk` vérifie le paquet, la version annoncée par la release et qu'elle est plus récente.
- `apk.yml` : `gh release create … --latest` (plus `--prerelease`) ; si le tag existe déjà, l'APK est remplacé et la release repassée en normale et « latest ».
- La v1.5.1 reste en pre-release (pas modifiable depuis la session de Claude) : sans conséquence, la 1.5.2 passe devant.

## 1.5.3 : fiche film, boutons qui recouvraient la distribution

- Constat (tablette) : les boutons du bas (Lecture, Bandes-annonces, Résolution…) recouvraient les noms des acteurs. Cause : la barre est posée par-dessus la page, la colonne d'informations ne lui réservait qu'une marge fixe pensée pour la TV (88 dp, 200 dp sur téléphone).
- `MovieDetailScreen` : la hauteur réelle de la barre est mesurée (`onSizeChanged` → `barHeightPx`) ; la colonne d'informations s'arrête au-dessus et défile toujours (`verticalScroll`, tablette comprise). Les listes Résolution / Audio / Sous-titres s'ouvrent à `barHeight + 8 dp`.
- Tablette en portrait (`portrait` : ≥ 600 dp de large, plus haute que large) : colonne de texte à 90 % de la largeur (au lieu de 56 %) et boutons qui passent à la ligne (`FlowRow`, comme sur téléphone).
- S'applique aussi à la fiche d'un épisode (même écran).

## 1.5.4 : fiche film, boutons en deux lignes

- Constat (tablette en portrait) : la barre unique passait à la ligne n'importe où ; « 4K » restait à côté de « Marquer comme lu », Audio et Sous-titres en dessous.
- Quand les boutons passent à la ligne (`wrapBar` : téléphone, tablette en portrait) : deux `FlowRow` dans une colonne, actions (Lecture, Bandes-annonces, Marquer comme lu) en haut, choix (Résolution, Audio, Sous-titres) en dessous, alignés à gauche. La liste de choix s'ouvre à gauche, au-dessus de la barre. Tablette en paysage inchangée (une ligne, choix à droite, liste à droite).

## 1.5.5 : barre de navigation Android, fiche sur téléphone en paysage

- Constat (captures téléphone et pliant) : l'appli dessinait sous la barre de navigation d'Android (◁ ○ □) : boutons Résolution / Audio / Sous-titres à moitié cachés en portrait, bord des affiches recouvert en paysage (Android 15 impose l'affichage bord à bord avec `targetSdk 35`).
- `MainActivity.setContent` : `App` est placé dans une `Box` noire avec `windowInsetsPadding(WindowInsets.navigationBars)`. Pendant la lecture, `ImmersiveLandscape` masque les barres : la marge tombe à zéro, le lecteur reste plein écran.
- Fiche film sur téléphone en paysage : la ligne unique écrasait les boutons de choix (« Audio » écrit lettre par lettre), la barre prenait toute la hauteur et cachait les informations. `wrapBar` vaut désormais aussi pour une largeur < 1100 dp (deux lignes : actions, puis choix) ; colonne d'informations à 72 % de la largeur dans ce cas.

## 1.5.6 : volume des prerolls et courbe des génériques (repris de la TV 1.4.2 à 1.4.4)

- Reprise à l'identique des versions TV 1.4.2, 1.4.3 et 1.4.4 (dépôt privé `Plexiplay-backup`).
- Réglage « Volume des prerolls » (Paramètres) : 20 à 100 %, 75 % par défaut, clé `preroll_volume`. Pendant un `ClipKind.PREROLL` le lecteur passe à ce volume, puis 100 % pour le film et les bandes-annonces. ExoPlayer : gain `(pourcentage/100)²` (75 % ≈ −5 dB, 40 % ≈ −16 dB). mpv : propriété `volume` (déjà cubique), réglée par la boucle de suivi.
- Générique (fiches séries, saisons, épisodes, films) : même courbe `(pourcentage/100)²`, paliers 0 à 60 %, 40 % par défaut, nouvelle clé `theme_volume2` (l'ancien réglage linéaire n'est pas repris).
- `VolumeDialog` : liste de valeurs, titre et sous-titre en paramètres ; propre au mobile : la liste défile si l'écran est trop bas (téléphone en paysage).
- CI (repris de la TV) : l'envoi de l'artefact ne bloque plus la publication de l'APK quand le quota de stockage GitHub est atteint.

## 1.5.7 : biographies françaises (repris de la TV 1.4.5 et 1.4.7), fiche acteur tactile

- `PlexClient.person()` repris à l'identique de la TV : biographie Plex vide ou anglaise (`looksEnglish`) → article Wikipédia français (`fullWikiText` : texte complet ≈ 2 500 caractères sans titres ni sections de fin, repli sur le résumé `page/summary`). Essais « Nom », « (acteur) », « (actrice) », « (réalisateur) », « (réalisatrice) » ; garde-fous : page « standard », métier du spectacle, au moins 60 caractères. `PersonInfo.altBio` garde la biographie Plex, `fromWikipedia` indique la source. User-Agent Wikipédia « (Android) ». La 1.4.6 (bandes-annonces françaises) n'est pas reprise.
- `ActorDialog` (mobile) : sélecteur « Wikipédia | Plex » (`BioSwitch`) à côté du titre BIOGRAPHIE quand les deux biographies existent ; choix actif surligné, zones de toucher ≥ 40 dp, retour en haut du texte à chaque bascule (OK de la télécommande bascule aussi). Pas d'indications de télécommande.
- Œuvres de l'acteur : `FlowRow` (passent à la ligne selon la largeur) au lieu de rangées fixes de 6 qui débordaient en portrait.

---

# Notes de développement (Plexiplay clone)

Fichier de reprise : à lire en premier dans une nouvelle session, avant de relire le code.

## Dépôts

- Original : `Kahenis/Plexiplay` (public).
- Clone : `Kahenis/plexiplay-clone` (public). C'est lui qu'on modifie et dont l'appli se met à jour.
- Le jeton GitHub n'est jamais écrit dans le dépôt. Il est fourni par le propriétaire dans la conversation (fine-grained, limité à `plexiplay-clone`, droits Contents et Workflows en lecture/écriture, Actions en lecture).

## Reprendre le travail

1. Cloner : `git clone https://github.com/Kahenis/plexiplay-clone.git Plexiplay`
2. Pousser avec le jeton, sans l'enregistrer dans la config git :
   `git -c http.extraheader="Authorization: Basic $(printf 'x-access-token:%s' "$GH_TOKEN" | base64 -w0)" push origin main`
3. Pousser dès qu'une étape est terminée : le dossier local peut disparaître entre deux messages.

## Compiler

Pas de compilation locale (le SDK Android n'est pas joignable depuis l'environnement de Claude). Tout passe par GitHub Actions :

- `.github/workflows/apk.yml` : à chaque push sur `main`, compile `:app:assembleDebug`, publie l'APK en artefact et dans une release `vX.Y.Z` (fichier `PlexiPlay-clone-X.Y.Z-debug.apk`). Les erreurs de compilation sont recopiées dans les annotations du run.
- `.github/workflows/checks.yml` : vérifie les verrous et la spec preroll.
- Pour savoir si un build a réussi sans l'API GitHub : `git ls-remote --tags origin vX.Y.Z`. Le tag n'existe qu'une fois l'APK publié.

## Versions

- À chaque modification livrée, augmenter ensemble : `versionCode` et `versionName` dans `app/build.gradle.kts`, `VERSION` dans `core/plex/src/main/kotlin/tv/plexiplay/plex/PlexHeaders.kt`, et la version dans `README.md`.
- Sans nouvelle version, l'APK du tag existant est simplement remplacé et la TV ne propose aucune mise à jour.
- La mise à jour automatique (`app/.../update/AppUpdate.kt`) interroge `Kahenis/plexiplay-clone`, dernière release, tags au format `vX.Y.Z`.
- L'identifiant d'appli est `tv.plexiplay.app`, identique à l'original : les deux ne peuvent pas être installés ensemble.

## Modules verrouillés

- `tools/locks.json` liste les modules figés (`playback`, `players`). `tools/check_locks.py` refuse la compilation si l'un d'eux change.
- Ne pas toucher à un module verrouillé sans l'accord explicite du propriétaire. Avec son accord : retirer l'id de `locked`, modifier, remettre l'id, puis `python3 tools/check_locks.py --update`.
- Après toute modification d'un module non verrouillé : `python3 tools/check_locks.py --update`, puis `python3 tools/check_locks.py && python3 tools/verify_spec.py`.
- `playback` couvre notamment `buildQueue` et `trailerQueue` dans `MainActivity.kt`. `players` couvre `PlayerScreen.kt`, `MpvPlayerScreen.kt`, `core/player` et `DeviceCodecs.kt`.

## Où trouver quoi

| Sujet | Fichier |
| --- | --- |
| Écran principal, chargement des rangées, file de lecture | `app/.../MainActivity.kt` |
| Rail gauche, rangées, miniatures, popup d'appui long | `app/.../ui/shell/ShellScreen.kt` |
| Icônes du rail et étiquettes de 3 caractères | `app/.../ui/shell/RailIcons.kt` |
| Fiche film, menus résolution, audio, sous-titres | `app/.../ui/detail/MovieDetailScreen.kt`, `MovieFacts.kt` |
| Lecteur ExoPlayer | `app/.../ui/player/PlayerScreen.kt` (verrouillé) |
| Lecteur mpv | `app/.../ui/player/MpvPlayerScreen.kt` (verrouillé) |
| Paramètres | `app/.../ui/settings/SettingsScreen.kt` |
| Préférences enregistrées | `app/.../data/SessionStore.kt` |
| API Plex (rangées, marquer vu/non vu, URLs) | `core/plex/.../PlexClient.kt` |
| Modèles de données Plex | `core/plex/.../model/Models.kt` |
| URLs de flux, choix direct ou transcodage | `core/player/.../StreamUrlFactory.kt`, `CodecRegistry.kt` |
| Prerolls et bandes-annonces | `core/plex/.../preroll/` |

## Fonctions ajoutées dans le clone

- Choix de la résolution, de la langue audio et des sous-titres sur la fiche film, avant la lecture (ExoPlayer et mpv). Valeurs par défaut tirées des Paramètres.
- « Quoi de neuf » : appui de 3 s sur OK sur une miniature de « Continuer à regarder » ou « Regarder à nouveau » : popup Supprimer / Annuler. Supprimer appelle `setWatched(..., false)` côté serveur Plex.
- Rail gauche réduit : icône par catalogue (maison, claquette, écran, micro pour les spectacles, dossier sinon) et 3 caractères pour les noms inconnus. Étiquettes masquées quand le rail est déployé.

- Séries : `ui/detail/SeriesScreen.kt` (fiche série avec saisons, page saison avec épisodes). Fiche épisode = `MovieDetailScreen` avec `promoKey`/`themePath`/`remembered` (bande-annonce et thème de la série). Choix audio/sous-titres mémorisés par série (`SessionStore.seriesChoice`). `buildEpisodeQueue` (fin de `MainActivity.kt`, hors modules verrouillés) enchaîne l'épisode choisi et jusqu'à 5 suivants.
- Si une compilation échoue : `git fetch origin build-log` puis lire `errors.txt` / `tail.txt` (branche écrasée par le workflow en cas d'échec).

## Limites connues

- Confirmation Oui/Non (`ui/shell/ConfirmDialog.kt`) avant déconnexion et avant de quitter (Retour à la racine).
- Nouveautés des séries : plus de 4 épisodes consécutifs de la même saison = une vignette « N ajouts » (`groupAdditions` dans MainActivity.kt, champ local `Metadata.added`), ouvre la page de la saison.
- Bibliothèques affichées : `ui/settings/LibraryPicker.kt`, masquées mémorisées par serveur (`SessionStore.hiddenLibraries`). Format d'écran retiré des paramètres, toujours AUTO.
- Textes de l'appli : toujours au vouvoiement (usage en France).
- Recherche : ligne « Recherche » sous Accueil (`ui/shell/SearchScreen.kt`), clavier à l'écran uniquement, filtrage local dès 3 lettres sur le catalogue du serveur actif (films + séries des bibliothèques affichées).
- Veille : `FLAG_KEEP_SCREEN_ON` posé dans `MainActivity.kt` tant que `clips != null` (le code des lecteurs reste verrouillé).
- Connexion : `PlexClient.reach` essaie toutes les adresses d'un serveur (`ServerEndpoint.alternates`) et garde la première qui répond.
- Serveurs : `chooseServers` renvoie tous les serveurs (les siens puis ceux des amis) ; au démarrage le premier qui a des bibliothèques passe en tête.
- Séries : appui long sur OK sur une saison ou un épisode = vu / non vu. Badge de saison « Reste N ».
- Séries : pas de reprise à la position (il faudrait modifier les lecteurs verrouillés), pas de compte à rebours avant l'épisode suivant (enchaînement direct).

- Rien n'a été testé sur une vraie TV ni avec un vrai serveur Plex : seule la compilation est vérifiée.
- Pour un épisode « à suivre » jamais commencé, la remise à « non vu » peut ne pas le retirer de « Continuer à regarder ».

## Mode compatible (0.1.56)
- Réglage « Mode compatible » : Automatique / Activé / Désactivé (`compatMode`). `compat` est la valeur effective (SessionStore : réglage + apprentissage `compat_learned`).
- Actif : décodeur Dolby Vision écarté dans ExoPlayer (lecture de la couche HDR10), DTS/DTS-HD/TrueHD convertis par le serveur (`audioCodec` ac3 ou aac).
- Quand le son seul doit être converti, `directStream=1` : l'image est copiée, pas retranscodée (avant : tout le film était converti, trop lourd pour un NAS en 4K HDR).
- Automatique : si le film n'avance pas après 20 s ou si le lecteur signale une erreur, l'appli relance en mode compatible et le mémorise (`learnCompat`). « Activé » ou « Désactivé » efface cet apprentissage.
- Paramètres : ligne de diagnostic `DeviceCodecs.summary()` (décodeurs annoncés par la TV).
- Les modules `playback` et `players` ont été rouverts pour ce changement puis reverrouillés.

## Reprise de lecture (0.1.57)
- Fiche film/épisode : si `viewOffset` ≥ 30 s, boutons « Reprendre à 1 h 12 » (principal) et « Recommencer ». Page série : « Reprendre S2 E5 » pour l'épisode en cours.
- `ClipPlay.startMs` = `viewOffset` − 5 s (clip principal seulement). ExoPlayer : `setMediaItems(items, index, startMs)`. mpv : seek absolu à l'apparition des pistes. Le transcodage n'utilise pas `offset` : ExoPlayer se positionne lui-même, ce qui garde des positions de progression cohérentes.
- Pas de bandes-annonces avant une reprise (déjà le cas : `viewOffset <= 0` requis dans `buildQueue`).
- Le repli automatique du mode compatible reprend à la même position.
- Modules `playback` et `players` rouverts puis reverrouillés.

## Saisons et générique (0.1.58)
- Miniature de saison : « Saison N » (ou le titre Plex pour les spéciaux) puis « N épisodes » sous l'affiche. « Reste X » (orange) reste sur l'affiche pour une saison commencée ; une saison entièrement vue porte une coche teal en haut à droite.
- Le générique (`themePlayer`) est lu une seule fois par ouverture de fiche (`REPEAT_MODE_OFF`), sur les fiches série/saison et sur la fiche film/épisode.

## Format d'image et fluidité mpv (0.1.59)
- ExoPlayer : certains MKV déclarent une taille d'affichage erronée (ex. 16:9 pour une image 3840×1600) et ExoPlayer l'applique (visages allongés). En HD/UHD (largeur ≥ 1920) avec un rapport de pixels ≠ 1, on impose les proportions de l'image (`setAspectRatio(w/h)` après `onVideoSizeChanged`). L'info de lecture (« i ») montre la taille, le rapport de pixels et la correction.
- mpv : `hwdec=mediacodec,mediacodec-copy` (direct puis repli), `video-sync=display-resample`, `interpolation=no`. À valider sur une vraie TV : si l'image reste noire avec mpv, revenir à `HwdecMode.MediacodecCopy` seul.
- Modules `players` rouvert puis reverrouillé.

## Mode compatible plus strict (0.1.60)
- L'apprentissage automatique est désormais **par film** (`compat_titles`, clé = ratingKey), plus global. « Activé » reste global ; « Désactivé » ignore tout apprentissage. L'ancienne clé `compat_learned` n'est plus lue.
- Déclenchement du repli : erreur de décodeur ou de sortie audio uniquement, ou aucune image rendue après 25 s (`onRenderedFirstFrame`).
- Flux converti par le serveur : si les proportions du flux diffèrent de plus de 5 % de celles du fichier (`Media.width/height` → `ClipPlay.sourceAspect`), on impose celles du film. L'info « i » indique lecture directe / flux converti, taille, rapport de pixels.
- mpv : surface directe (`SurfaceType.Surface`) à la place de la texture ; l'info « i » affiche décodage matériel utilisé, cadence, images perdues (décodeur / affichage).
- Modules `playback` et `players` rouverts puis reverrouillés.

## Proportions verrouillées (0.1.61)
- Le rapport imposé (flux converti ou pixels non carrés) est mémorisé (`locked`) et réappliqué en boucle sur `exo_content_frame` (toutes les 0,4 s pendant 3 s après un changement, puis 1,5 s) avec `requestLayout`/`invalidate` : sur certaines TV l'ancien affichage restait figé jusqu'au prochain redessin (appui sur OK). Si cela ne suffit pas, passer ExoPlayer en surface « texture ».
- Modules `playback` et `players` rouverts puis reverrouillés.

## « OK invisible » (0.1.62)
- Diagnostic : en lecture directe, la fenêtre vidéo (SurfaceView) de certaines TV s'affiche en plein 16:9 (image étirée) tant que l'interface ne change pas ; l'appui sur OK la remet au bon format.
- Le rapport (largeur/hauteur × pixels) est désormais toujours verrouillé, pas seulement pour les flux convertis/pixels non carrés.
- Décalage d'un pixel de la vidéo (`nudge`, padding bas 1 dp) à 0,8 s, 2 s et 4 s après la première image, puis retour : équivalent invisible de l'appui sur OK.
- Si cela ne suffit pas : surface « texture » pour ExoPlayer (attention au HDR) ou masquer/réafficher la vue.
- Modules `playback` et `players` rouverts puis reverrouillés.
- Astuce validée sur TV Philips (0.1.62). Depuis la 0.1.65 elle est un réglage (`formatFix`, activé par défaut) : la TV de Jeff avait « 16/9 permanent » actif, la correction n'est peut-être pas nécessaire partout.

## Modes mpv (0.1.63)
- Constat (TV Philips, 4K) : décodeur `mediacodec`, 0 image perdue côté décodeur, mais saccades « de rendu » en 4K ; 720p fluide.
- Réglage « Mode mpv » (visible quand le lecteur est mpv) : **Normal** (réglage d'origine : texture + `mediacodec-copy`, profil fast), **Direct** (`vo=mediacodec_embed`, `hwdec=mediacodec`, surface directe : fluide en 4K mais **sans sous-titres**), **Allégé** (rendu simplifié : filtres bilinéaires, `tone-mapping=clip`, sans `hdr-compute-peak`/débanding/interpolation).
- Les options sont posées après `initialize` via `mpv.setString` ; chaque appel est protégé par `runCatching`. Le panneau « i » affiche le mode, le décodage, la cadence, les images perdues (décodeur/affichage), en retard (`vo-delayed-frame-count`) et la fréquence écran.
- Modules `playback` et `players` rouverts puis reverrouillés.

## Paramètres plus compacts (0.1.64)
- Écran des paramètres réduit d'environ 20 % : titre 14,5 sp, boutons 11 sp (marges 10 × 4 dp), explications 10,5 sp (compromis de lisibilité, -12 %), pied de page 10 sp, marges 16 × 13 dp, espacement 5 dp.

## Correction du format en option (0.1.65)
- Réglage « Correction du format » (ExoPlayer, activé par défaut) : coupe l'imposition du rapport et le décalage d'un pixel. L'info « i » affiche « correction activée/désactivée ».
- Modules `playback` et `players` rouverts puis reverrouillés.

## Simplifications et navigation (0.1.66)
- Langue de l'appli : ligne supprimée, français fixé (`uiLanguage = "fr"` dans SessionStore).
- mpv : plus de choix de mode, toujours « allégé » (`mpvMode = "light"`). Les modes normal/direct restent dans `MpvPlayerScreen` mais ne sont plus proposés.
- « Pistes avant le film » : ligne supprimée. `buildQueue` ne demande plus `cinemaQueue` : le film est lu seul (`trailerCount` toujours 0). Le bouton « Bandes-annonces » de la fiche film est conservé.
- Clic sur une bibliothèque : petit indicateur vert « Chargement… » en haut à droite jusqu'à ce que la page soit prête (secours : 8 s).
- Retour dans le menu de gauche : le focus revient sur la ligne de la bibliothèque ouverte (`focusIndex`), plus sur la première ligne.
- Modules `playback` et `players` rouverts puis reverrouillés.

## Version 0.2.0
- Version portée à 0.2.0.
- Indicateur « Chargement… » : reste affiché jusqu'à ce que la page soit prête **et** que les affiches visibles soient dessinées (`PosterStore.pendingVisible()` à 0 pendant 3 contrôles consécutifs de 150 ms) ; secours à 25 s.
- Mise à jour : le mot « Téléchargement… » du bouton de mise à jour est écrit en vert (comme l'indicateur de chargement).

## Bonus du serveur avant les films (0.2.1)
- Rétabli en permanence, sans réglage : `buildQueue` appelle `cinemaQueue(…, 0)` (même demande que le client officiel avec « lire la piste 1 avant le film », 0 bande-annonce de cinéma). Le serveur ajoute lui-même les bonus selon sa logique (au hasard ou à la suite).
- Uniquement pour un **film** jamais commencé ou vu en entier, et aussi avec « Recommencer ». Jamais pour une reprise (« Reprendre »), ni pour les épisodes, ni lors du repli automatique du mode compatible (`bonus = false`).
- Erreur de la 0.1.66 corrigée : la suppression de la ligne « Pistes avant le film » avait aussi coupé les bonus.
- Modules `playback` et `players` rouverts puis reverrouillés.


## Version 1.0.0-RC : easter egg du logo

- Appui prolongé (5 s) sur la roue dentée des paramètres : fenêtre avec le nom « Plexiplay » dont les lettres apparaissent, tournent autour du centre en accélérant, se resserrent avec un éclair orange et se fondent dans le logo (`logo.png`) ; la fenêtre se referme (fondu), puis les paramètres s'ouvrent normalement. Retour ferme aussi la fenêtre. Un appui court ouvre les paramètres comme avant.
- Fichiers : `ui/shell/EasterEgg.kt` (animation, une seule valeur de progression 0..1 sur 5,2 s) ; `GearButton` dans `ShellScreen.kt` (détection du maintien).
- Numéro de version « 1.0.0-RC » (release candidate). `AppUpdate.newer` : le suffixe après « - » est ignoré pour les nombres, et « 1.0.0 » est considérée plus récente que « 1.0.0-RC ».
- Aucun module verrouillé touché.


## Version 1.0.0-RC2 : rapport de lecture au serveur

- Symptôme : plus aucune session dans « Lecture en cours » du serveur ni dans Tautulli.
- Causes : ExoPlayer ne signalait que les changements lecture/pause et l'arrêt, sans point régulier, et rien après un bonus (la lecture ne s'interrompt pas, donc aucun changement d'état au démarrage du film) ; MPV ne signalait que commandes affichées et jamais l'arrêt.
- Correctif : chaque lecteur envoie l'état toutes les secondes pendant le film (`LaunchedEffect`), l'arrêt est signalé à la sortie (aussi pour MPV). `MainActivity` limite l'envoi : immédiat si l'état ou le film change, sinon un point toutes les 10 s, via un exécuteur unique (plus un thread par appel). Identifiant de session `X-Plex-Session-Identifier` propre à chaque lecture, `hasMDE=1`, durée et position jamais négatives (`PlexClient.timeline`).
- Numéros : `AppUpdate.newer` compare aussi le numéro de la candidate (RC < RC2 < version finale).
- Modules `playback` et `players` déverrouillés puis reverrouillés.


## Version 1.0.1-RC : numérotation

- Règle : les versions de test s'écrivent `x.x.x-RC` et le troisième chiffre monte à chaque publication (1.0.1-RC, 1.0.2-RC…), sans « RC2 ».
- 1.0.1-RC reprend exactement le code de la 1.0.0-RC2 (rapport de lecture au serveur) ; seul le numéro change.


## Version 1.0.2-RC : proposition de mise à jour au démarrage

- Au démarrage (une fois par lancement, après l'écran de chargement), l'appli interroge en arrière-plan la dernière release du dépôt (`AppUpdate.latest`). Sans nouveauté, ou en cas d'échec réseau, rien ne s'affiche.
- Si une version plus récente existe : fenêtre `UpdateDialog` (logo à gauche, « Plexiplay X est disponible (vous avez Y) », boutons « Installer » / « Plus tard »). Elle ne s'affiche jamais pendant une lecture. « Installer » : téléchargement (« Téléchargement… » en vert) puis installation Android ; « Plus tard » ou Retour : fermeture, la proposition revient au prochain démarrage. Le bouton des paramètres reste en place.
- Fichiers : `ui/shell/UpdateDialog.kt`, état `startupOffer` / `startupChecked` dans `MainActivity` (hors modules verrouillés).


## Version 1.0.3-RC : vidéos personnelles lues directement

- Bibliothèques « Vidéos personnelles » / « Plex Video Files » (type film, agent `…agents.none` ou scanner « Video Files » / « Personal Media », voir `LibrarySection.personal`) : un clic sur une vidéo lance la lecture sans passer par la fiche.
- Pas de bonus ni de bande-annonce ; reprise automatique si la vidéo est commencée. La bibliothèque est déterminée par `librarySectionID` de l'élément (nouveau champ de `Metadata`), à défaut par la bibliothèque affichée. Les films des autres bibliothèques ne changent pas.
- `openMovie` dans `MainActivity` (hors région verrouillée `playback`).


## Version 1.0.4-RC : retour automatique en fin de film

- À la fin complète d'un film ou d'une vidéo personnelle, l'écran de lecture se ferme et l'appli revient d'où la vidéo a été lancée (comme Retour). Le rapport « arrêté » part à la sortie, ce qui marque le film comme vu côté serveur.
- `ClipPlay.closeOnEnd` (vrai seulement pour un élément de type film) : les épisodes de séries ne changent pas. ExoPlayer : `onPlaybackStateChanged(STATE_ENDED)` sur le dernier élément. MPV : `eof-reached` / `idle-active` dans la boucle de rapport, avec la dernière position ≥ 90 % de la durée.
- MPV : la dernière position/durée connues ne sont mises à jour que si la durée est connue (évite d'écraser avec 0 à l'arrêt).
- Modules `playback` et `players` déverrouillés puis reverrouillés.


## Version 1.0.5-RC : fiche série resserrée et horloge

- Fiche série : espaces réduits entre « année · saisons · épisodes », genres, notation, résumé et boutons (gain d'environ une ligne) ; résumé limité à 5 lignes au lieu de 6, pour laisser apparaître les noms des saisons.
- Horloge (`ClockChip`, heure du système en 24 h, mise à jour toutes les 10 s) en haut à droite de l'écran, ajoutée dans `MainActivity` au-dessus des écrans ; absente pendant la lecture, sans focus.
- Thème secondaire « Néon » : maquette présentée, développement à venir sur accord.


## Version 1.0.6-RC : thème secondaire « Néon »

- Réglage « Thème » (Classique / Néon) dans les paramètres, mémorisé (`SessionStore.theme`). Le classique reste le défaut et n'est pas modifié.
- `ThemeMode.neon` (état Compose) : `Ink`, `Panel`, `Accent`, `Signal`, `FocusRing` deviennent des propriétés qui changent de valeur selon le thème, donc tout l'écran suit.
- `ui/theme/ThemeStyle.kt` : `themedButton` (classique : anneau + aplat comme avant ; Néon : bouton 3D avec socle, dégradé, reflet et halo doré au focus), `screenBackground` (dégradé nuit avec halos violet/turquoise), `panelBackground` (menu de gauche). Appliqués : onglets, boutons des fiches, boutons des paramètres, menu de gauche, boîtes Oui/Non et mise à jour, fonds d'écran. Affiches : agrandies de 7 % avec ombre dorée au focus. Horloge lumineuse.
- Pas de flou ni d'effet coûteux : seulement des dégradés et ombres, pour rester fluide sur les petits boîtiers.


## Version 1.0.7-RC : réglages du thème Néon et volume du générique

- Générique des séries : volume du lecteur de thème 0,7 → 0,525 (−25 %), page des saisons et fiche des épisodes.
- Néon, lisibilité : écriture sombre sur fond clair pour les onglets sélectionnés et la ligne sélectionnée du menu de gauche (le cas des boutons clairs à focus était déjà correct).
- Néon, fond plus foncé : base 0xFF070A1C, halos atténués, menu et boutons au repos assombris.
- Néon, halo des affiches : marge de 12 dp autour des rangées (`contentPadding`) et de la grille pour que le halo de la première affiche ne soit plus coupé. Le thème classique ne change pas.


## Version 1.1.0-RC : thème Néon, finitions

- Néon : le halo doré qui débordait autour des boutons et des affiches est supprimé ; on garde le contour jaune de sélection et l'agrandissement de 7 % des affiches. La marge autour des rangées est réduite à 6 dp (juste de quoi ne pas couper le contour de l'affiche agrandie).
- Néon : le titre de la ligne sélectionnée du menu de gauche (Accueil, Films…) passe en noir sur le fond clair.


## Version 1.1.1-RC : volume du générique réglable

- Paramètres : « Volume du générique », liste déroulante (`VolumeDialog`) de 0 à 70 % par paliers de 10, 50 % par défaut ; haut/bas pour choisir, OK pour valider, Retour pour fermer. Mémorisé dans `SessionStore.themeVolume`.
- S'applique à la musique de générique des fiches de saisons (`SeriesScreen`) et d'épisodes (`MovieDetailScreen`), en direct si le réglage change. (Le lecteur de la fiche d'épisode était auparavant à plein volume.)


## Version 1.1.2-RC : volume du générique 0 à 50 %

- Liste du réglage « Volume du générique » : 0, 10, 20, 30, 40, 50 % ; défaut 20 %. Une valeur déjà enregistrée au-dessus de 50 % est ramenée à 50 %.


## Version 1.1.3-RC : invitations Plex et connexion

- Paramètres, tout en bas : « Vérifier les invitations ». `PlexClient.invites` lit `https://plex.tv/api/invites/requests` (XML : compte, e-mail, drapeaux friend/home/server, serveur proposé) ; `acceptInvite` fait le PUT `…/requests/{id}?friend=&home=&server=`.
- `InviteDialog` : indique le compte qui demande en ami et le serveur qui partage (nombre de bibliothèques), boutons Accepter / Plus tard. Après acceptation : rechargement des serveurs (jusqu'à 6 essais, le partage peut tarder), bascule sur le serveur nouvellement apparu et ouverture du sélecteur de bibliothèques. Pas de fusion multi-serveurs dans la colonne de gauche (choix : option simple).
- Connexion : `PinAuthClient.createPin(strong)`, `webAuthUrl` (`https://app.plex.tv/auth#?clientID=…&code=…&context[device][product]=Plexiplay`), QR code dessiné avec zxing core 3.5.3 (Google, Apple ou e-mail sur le téléphone) ; le code à 4 caractères plex.tv/link reste en parallèle. Les deux codes sont sondés.
- Connexion e-mail : mot de passe masqué, `TwoFactorRequired` (401 « 1029 / verification ») fait apparaître le champ du code à 6 chiffres envoyé en `verificationCode`.


## Version 1.2.0 : version finale

- Passage de 1.1.3-RC à la version finale 1.2.0 (versionCode 83), sans suffixe RC. Aucun changement de code : contenu identique à 1.1.3-RC (invitations Plex, connexion e-mail avec 2FA, QR code).
- Les appareils en 1.x.x-RC reçoivent cette version à la mise à jour (la version finale passe devant toutes les candidates).


## Version 1.2.1 : Home ferme l'application

- `MainActivity.onStop` : si l'appli quitte l'écran (touche Home, applis récentes, veille) hors changement de configuration, `finishAndRemoveTask()` la ferme sans message ; la lecture s'arrête donc et la session disparaît du serveur.
- Exception : `KeepOpen.on` (posé par `AppUpdate.install`, remis à faux dans `onStart`) empêche la fermeture quand l'installateur Android ou l'écran d'autorisation passe devant pendant une mise à jour.


## Version 1.2.2 : bouton Recommencer en icône

- Fiche film : « Recommencer » devient une icône ronde « retour au début » (`RestartAction`, flèche circulaire dessinée en Canvas). Quand elle a le focus, une bulle « Recommencer » s'affiche au-dessus (`Popup`). La case « Sous-titres » n'est plus poussée hors de l'écran. Comportement inchangé (relance depuis 0:00).


## Version 1.2.3 : bouton FR dans le lecteur

- Contrôles du lecteur (ExoPlayer et mpv) : nouveau bouton « FR », à droite de « CC ». Il force la piste audio française sans quitter le lecteur.
- ExoPlayer : si une piste française lisible est dans le flux, bascule immédiate (`TrackSelectionOverride`). Sinon (flux converti par le serveur, ou piste que la TV ne décode pas) le flux est relancé avec `audioStreamID` français et conversion AAC, à la même position (`replaceMediaItem` + `seekTo`). `ClipPlay.frUrl/frStreamId/frCodec/partId` sont préparés par `buildQueue`. Les rapports « i » et la correction de proportions tiennent compte du flux relancé (`effective`).
- mpv : bascule immédiate de piste (`aid`) si le fichier contient une piste française ; sinon message « Aucune piste française dans ce flux » (pas de relance du flux en mpv).
- Sans piste française : message « Aucune piste française dans ce film ». Le bouton ne fait rien hors du film (bonus, bandes-annonces).
- `MovieFacts.frenchStream` : piste de langue « fr » (la sélectionnée du serveur, sinon celle qui a le plus de canaux), à défaut une piste dont le titre dit français (étiquette de langue absente ou fausse). Le choix de langue par défaut s'en sert aussi quand aucune piste n'est étiquetée « fr ».
- `PlexClient.setAudioStream` : PUT `/library/parts/{id}?audioStreamID=&allParts=1`, pour que le serveur et le client officiel gardent la piste choisie.


## Version 1.2.4 : bouton ST corrigé, bouton œil retiré

- Le bouton « CC » devient « ST » (sous-titres). Correction : la rotation cherchait le sous-titre actuel dans tous les réglages de pistes du lecteur, y compris le choix audio posé au lancement ; elle retombait donc toujours sur le premier sous-titre. Elle ne regarde plus que les sous-titres : Sans → chaque piste → Sans.
- Message à chaque appui (« Sous-titres : Français (forcés) », « désactivés »), et « Aucun sous-titre disponible dans ce flux » quand ExoPlayer n'a aucune piste lisible (flux converti, sous-titres image non lus par la TV). Même message côté mpv.
- Bouton « œil » (masquer les commandes) retiré des deux lecteurs : Retour fait la même chose. Le bouton Stop est conservé.


## Version 1.2.5 : liste des pistes audio (appui long sur FR)

- Appui maintenu 3 secondes sur « FR » : liste des pistes audio du film (`AudioMenu`, au centre de l'écran), libellé « Français · AC3 5.1 » (langue, codec, canaux), ✓ sur la piste en cours. Haut/bas pour choisir, OK pour valider, Retour pour fermer. Un appui bref sur FR garde son rôle (forcer le français). `PlayerIcon` gère l'appui long (`onLongPress`, 3 s ; relâchement avant = appui bref).
- `ClipPlay.audios` (`AudioChoice(id, label, url, current)`) est préparé par `buildQueue` : pour chaque piste, le flux converti correspondant (`StreamUrlFactory.transcode` avec `audioStreamID`).
- ExoPlayer : lecture directe avec autant de pistes que Plex et piste lisible → bascule immédiate dans le flux ; sinon flux relancé avec cette piste, à la même position (`relaunch`). mpv : la n-ième piste Plex est la n-ième piste audio de mpv (`aid`) ; si les nombres diffèrent, message « Choix de piste impossible dans ce flux ».
- Le choix est enregistré sur le serveur (`setAudioStream`). La liste ne s'ouvre que pendant le film.


## Version 1.2.6 : distribution des épisodes, nombre de séries

- Onglet « Bibliothèque » d'une bibliothèque de séries : le nombre de séries s'affiche en orange (couleur Signal) à côté du titre (`ShellScreen.count`, fourni par `MainActivity` = `items.size`).
- Fiche d'un épisode : rangée « Distribution » (`CastRow`, `ui/detail/Cast.kt`) sous le résumé (3 lignes au lieu de 6 quand il y a une distribution). Photos octogonales (`OctagonShape`), nom en blanc, personnage en orange, initiales si pas de photo. D'abord les acteurs principaux (rôles `Role` de la série), un trait, puis les invités (rôles de l'épisode absents des principaux). 15 + 15 au plus. Photos via `PosterStore` (cache disque), URL `https://…` directes sans jeton serveur.
- OK sur une pastille : `ActorDialog` (grand popup). Photo, nom, personnage, biographie, œuvres principales en pastilles carrées ; haut/bas font défiler ; Retour ferme (géré par la racine de `MovieDetailScreen`) et rend le focus à la pastille.
- Données : `Metadata.Role` (`RoleItem`: id, tag, tagKey, role, thumb) ; `PlexClient.person` interroge d'abord le service Discover de Plex (`https://discover.provider.plex.tv/library/people/{tagKey}` pour la biographie, `/media` pour les œuvres) avec le jeton du compte, en best-effort (non vérifié sur un vrai compte). À défaut, les œuvres viennent du serveur : `/library/sections/{clé}/all?actor={id du rôle}` dans chaque bibliothèque (intitulé « Dans votre bibliothèque »). Sans biographie : « Biographie indisponible pour cet acteur. ».


## Version 1.2.7 : mise à jour réparée, barre de progression

- Symptôme : « Téléchargement… » puis plus rien. Cause : `UpdateReceiver` ignorait tout résultat d'Android autre que « confirmation demandée » (échec, refus, absence de fenêtre) sans rien afficher.
- `UpdateReceiver` traite maintenant tous les statuts : confirmation (lance la fenêtre, message d'état), succès, et échec (message d'Android en bulle + dans Paramètres, « Installation refusée : … »). `UpdateBus` relaie ces messages à l'écran (`MainActivity`, `DisposableEffect`).
- Plan B : si Android n'a montré aucune fenêtre 8 s après l'envoi de la session, l'appli ouvre l'installateur classique (`ACTION_VIEW` sur l'APK via `FileProvider`, autorité `<appId>.updates`, `res/xml/update_paths.xml`).
- `KeepOpen.on` est posé dès le début du téléchargement (et plus seulement à l'installation). Le téléchargement a un délai de lecture de 30 s et vérifie la taille reçue (erreur lisible au lieu d'un blocage).
- Barre de progression fine (4 dp, verte) sous « Téléchargement… » dans la fenêtre de démarrage (`UpdateDialog`) et sous la pastille Mise à jour dans Paramètres (`ThinBar`). `AppUpdate.download` renvoie la progression.


## Version 1.2.8 : version de test de la mise à jour

- Seul changement visible : la pastille « Mise à jour : 1.2.8 » de Paramètres s'affiche en orange (couleur Signal) quand aucun message d'état n'est en cours. Version publiée pour tester la mise à jour 1.2.7 → 1.2.8.


## Version 1.2.9 : mise à jour chronométrée

- Diagnostic d'un téléchargement jugé très lent. `AppUpdate.fetchAndInstall` (téléchargement + remise à Android) remplace le code dupliqué de `MainActivity` (écran de démarrage et Paramètres).
- Pendant le téléchargement : texte « 23 / 72 Mo · 4,1 Mo/s » sous la barre verte ; puis « Préparation de l'installation… » pendant la session d'installation Android.
- À la fin, le message d'état de Paramètres porte les durées : « Confirmez l'installation à l'écran (reçu en X s, préparé en Y s) » (`UpdateBus.timing`).


## Version 1.2.91 : copie de la 1.2.9 (test de mise à jour chronométrée)

- Aucun changement de code. Version plus récente que la 1.2.9 (91 > 9 dans la comparaison numérique de `AppUpdate.newer`) pour tester la mise à jour 1.2.9 → 1.2.91 et lire les vitesses/durées affichées.
- Observation de l'utilisateur sur la 1.2.8 : le téléchargement démarre très vite puis ralentit de plus en plus à partir du premier quart.


## Version 1.2.92 : téléchargement de la mise à jour, client dédié et mesures

- Constat (1.2.9) : débit de 15 Mo/s au départ, puis 2, 1, 0,2 Mo/s ; ce n'est pas le disque ni le Wi-Fi (la TV mesure 80 Mo/s ailleurs, et les anciennes mises à jour étaient très rapides).
- `AppUpdate.download` utilise un client OkHttp dédié (`downloader`) : HTTP/1.1 seulement, pool de connexions et file d'attente propres (plus partagés avec les affiches et les bibliothèques), tampon de 256 Ko.
- `DownloadStats` : protocole, serveur final (après redirection), temps cumulé de lecture réseau et d'écriture disque, vitesse de pointe et vitesse sur les 2 dernières secondes.
- Texte de progression : « 23 / 72 Mo · 4,1 Mo/s (moyenne 5,0) · http/1.1 ». Message final de Paramètres : durées reçu/préparé, réseau/disque, pointe/fin, protocole, serveur, et « débit réseau limité côté serveur » si la fin est 4 fois plus lente que la pointe avec le réseau dominant.
- Plan B prévu si le ralentissement persiste : héberger l'APK sur guilard.com (URL fixe + fichier texte de version) ou télécharger en morceaux parallèles (en-têtes Range).


## Version 1.2.93 : copie de la 1.2.92 (test de mise à jour)

- Aucun changement de code. Sert à tester la mise à jour 1.2.92 → 1.2.93 avec le client de téléchargement dédié et ses mesures.


## Version 1.2.94 : mises à jour depuis guilard.com

- Constat : le téléchargement depuis GitHub est bridé chez l'utilisateur (0,3 Mo/s même sur son PC), ce qui n'est pas dû à l'appli.
- `AppUpdate.latest` lit d'abord `https://guilard.com/dl/version.txt` (une ligne, ex. `1.2.95`, avec horodatage anti-cache). Si une version plus récente est annoncée, l'APK est téléchargé sur `https://guilard.com/dl/plexiplay.apk` (`UpdateOffer.fromServer`). Si le serveur ne répond pas correctement (réseau, code HTTP, texte invalide), retombée sur l'API GitHub comme avant. Si le serveur répond « pas plus récent », on s'arrête là.
- Si le téléchargement depuis le serveur échoue, l'appli retente sur GitHub (message « Serveur indisponible, téléchargement depuis GitHub… »).
- `checkApk` : après téléchargement, le paquet est lu (`getPackageArchiveInfo`) ; refus si ce n'est pas `tv.plexiplay.app`, si la version ne correspond pas à `version.txt`, ou si elle n'est pas plus récente que la version installée (évite une boucle de mises à jour).
- Publication à chaque version : déposer d'abord `plexiplay.apk`, puis mettre à jour `version.txt` (une seule ligne, sans « v »).


## Version 1.2.95 : copie de la 1.2.94 (test de mise à jour depuis guilard.com)

- Aucun changement de code. Sert à tester la mise à jour 1.2.94 → 1.2.95 via `https://guilard.com/dl/version.txt` et `plexiplay.apk`.


## Version 1.3.0 : copie de la 1.2.95 (test de la routine de dépôt sur guilard.com)

- Aucun changement de code. Sert à tester la mise à jour 1.2.95 → 1.3.0 avec la routine manuelle : `plexiplay.apk` d'abord, `version.txt` (`1.3.0`) ensuite, dans `https://guilard.com/dl/`.


## Nouveau dépôt « plexiplay » (ligne de développement), à partir de la 1.3.0

- Ce dépôt reprend le code de `plexiplay-clone` 1.3.0 en un seul commit (sans historique). `plexiplay-clone` reste figé sur la version stable ; l'historique complet y est conservé.
- Seul changement de code : le secours GitHub de la mise à jour (`AppUpdate.githubLatest`) lit désormais `https://api.github.com/repos/Kahenis/plexiplay/releases/latest`.
- Les mises à jour automatiques passent d'abord par `https://guilard.com/dl/version.txt` (canal stable, dossier `/dl/`). Les APK de développement sont installés à la main ; pas de canal de développement sur le serveur.
- Même identifiant (`tv.plexiplay.app`) et même clé de signature (`signing/debug.keystore`, publique) : une version remplace l'autre sur la TV, elles ne coexistent pas.


## Version 1.3.1 : distribution (photos des acteurs) sur la fiche d'un film

- Même fonctionnement que pour les épisodes : rangée « Distribution » (`CastRow`) sous le résumé de la fiche film, photos octogonales, nom, personnage ; OK sur une pastille ouvre `ActorDialog` (biographie, œuvres), Retour referme.
- Film : les acteurs (`Metadata.Role` du film, 15 au plus) sont tous des « principaux » ; pas d'invités ni de séparateur. Le résumé passe à 3 lignes quand la distribution existe, comme pour un épisode.
- `MainActivity` : la fiche film reçoit maintenant `posters` (cache d'images) et `accountToken` (biographies Discover), comme la fiche épisode. Les bibliothèques de vidéos personnelles lancent la lecture sans fiche et ne sont pas concernées.


## Version 1.3.2 : résumé complet en popup

- Fiches film et épisode (`MovieDetailScreen`) : le texte du résumé est sélectionnable (cadre `FocusRing` au focus). OK ouvre `SummaryDialog` (`ui/detail/Cast.kt`) : grand popup au style de la fiche acteur, titre, sous-titre (série + S/E, ou accroche du film), résumé complet défilant (haut/bas). Retour referme le popup en premier (avant la fiche acteur et le menu), puis le focus revient au résumé.
- Toujours sélectionnable, même quand le résumé tient en 3 lignes (comportement prévisible). Fiches de séries (`SeriesScreen`) non modifiées.


## Version 1.3.3 : biographies des acteurs en français (plan A)

- Constat : les biographies (service Discover de Plex) arrivaient en anglais, l'appli ne demandait aucune langue.
- `PlexClient.person` : les requêtes Discover portent maintenant `X-Plex-Language: fr` et `Accept-Language: fr-FR,fr;q=0.9` (biographie et titres des œuvres). Non vérifié sur un vrai compte : si Plex n'a pas de biographie française pour un acteur, il renvoie sa langue par défaut et l'affichage reste inchangé.
- Plan B non réalisé (à voir si besoin) : repli sur le résumé de Wikipédia en français (`fr.wikipedia.org/api/rest_v1/page/summary/{nom}`) quand la biographie est anglaise ou vide.


## Version 1.3.4 : bouton ST refait (cycle + liste), appui long 2 s

- Modules `playback` et `players` déverrouillés pour cette version (buildQueue, PlayerScreen, MpvPlayerScreen, StreamUrlFactory), puis reverrouillés avec de nouveaux hash.
- Appui long : 2 secondes (au lieu de 3) sur FR (liste des pistes audio) et sur ST (liste des sous-titres). `PlayerIcon` : `delay(2000)`.
- Cause du bouton ST « qui ne marche pas » : il ne regardait que les pistes de sous-titres vues par le lecteur dans le flux en cours. Flux converti (sous-titres absents), sous-titres image (PGS) non lus par la TV, fichiers externes non chargés : aucune piste trouvée.
- Nouveau : `ClipPlay.subs` (`SubChoice`: id Plex, libellé, langue, forcé, codec, externe, `url` du fichier pour un externe textuel), `subStart` (choix de départ), `audioStart`, `streamFor(audio, sous-titre)` (fabrique de flux converti ; sous-titre > 0 ⇒ `subtitles=burn`, ajouté à `StreamUrlFactory.transcode(burnSubtitles)`). Rempli par `buildQueue`.
- ExoPlayer, un clic sur ST : sous-titre suivant (Sans → chaque piste Plex → Sans), message immédiat, application 0,9 s après le dernier clic (évite de relancer le flux à chaque clic). `chooseSub` : lecture directe sans relance → coupe le texte (Sans) ; fichier externe textuel → l'élément est rechargé avec le fichier (`reloadWithSidecar`, même position) ; piste intégrée lisible → `TrackSelectionOverride` ; sinon (flux converti ou sous-titre image) → flux relancé avec `subtitleStreamID` + `subtitles=burn` (sous-titre gravé dans l'image par le serveur, même position). `effective()` tient compte du choix. Le changement de piste audio (FR ou liste) garde le sous-titre choisi (`audioUrl`, incrusté).
- Appui long 2 s sur ST : liste « Sous-titres » (`AudioMenu` avec titre, « Sans » en premier, ✓ sur le choix en cours).
- mpv : même interface. Fichier externe : `sub-add` ; piste intégrée : n-ième piste Plex = n-ième piste mpv quand les nombres concordent, sinon langue + forcé. Flux converti en mpv : message « Choix de sous-titres impossible dans ce flux » (pas de relance en mpv).
- Non vérifié sur une vraie TV. Le choix de sous-titres n'est pas enregistré sur le serveur (seul l'audio l'est).


## Version 1.3.5 : bulle « Maintenir pour lister » sur FR et ST

- Module `players` déverrouillé pour cette version (`PlayerScreen.kt`, composant `PlayerIcon`), puis reverrouillé avec de nouveaux hash.
- Quand le focus est sur FR ou ST (les boutons à appui long, `onLongPress != null`), une bulle claire « Maintenir pour lister » s'affiche au-dessus du bouton (`Popup`, centrée et bornée aux bords de l'écran, même style que « Recommencer » de la fiche film). Elle disparaît quand le focus part. Commun aux lecteurs ExoPlayer et mpv.


## Version 1.3.6 : style des sous-titres fixé, bouton FR = langue en cours

- Modules `players` et `playback` (AudioChoice dans `buildQueue`) déverrouillés pour cette version, puis reverrouillés avec de nouveaux hash.
- Sous-titres, permanents et sans réglage : texte blanc sur un bandeau noir semi-transparent (65 %), léger contour noir. ExoPlayer : `SubtitleView.setStyle(CaptionStyleCompat(...))` avec `setApplyEmbeddedStyles(false)`. mpv : `sub-color`, `sub-back-color=#A6000000`, `sub-border-style=background-box`, `sub-border-color`, `sub-border-size=1`, `sub-ass-override=force`. Les sous-titres image (PGS) et ceux gravés par le serveur Plex gardent le style du serveur.
- Bouton FR : affiche le code de la langue audio lue (FR, EN, DE…). `AudioChoice.language` (code Plex) ; `langTag()`. ExoPlayer : `audioTag` recalculé (`refreshAudioTag`) au changement de piste, après un clic FR ou un choix dans la liste : choix fait, sinon piste réellement sélectionnée, sinon réglage de départ, FR par défaut. mpv : étiquette dérivée de `pickedAudio` (état observable) et du réglage de départ. Le clic simple force toujours le français et le bouton repasse à FR ; l'appui long de 2 s ouvre la liste.

## Version 1.3.7 : « Sans » sous-titres imposé au serveur, liste ST par version
- Cause suspectée (chez une amie, sous-titres impossibles à couper sur un film converti) : les modes automatiques du compte Plex (« langue étrangère », « automatique ») regravent des sous-titres malgré `subtitleStreamID=0`.
- `StreamUrlFactory.transcode` : quand `subtitleStreamId == 0`, ajoute `subtitles=none` (lancement et changement via ST).
- `buildQueue` : la liste ST ne contient que les pistes de la version (Media) réellement lue.
- Modules `playback` et `players` déverrouillés le temps de la modification, hash mis à jour, reverrouillés.
- Sauvegarde du source 1.3.6 : branche `backup-1.3.6` (et tag `v1.3.6`).

## Version 1.3.8 : diagnostic des sous-titres dans le panneau « i »
- Cas suivi : sous-titres impossibles à couper sur un film converti (résolution réduite), compte Plex en mode « manuellement ».
- Le bouton « i » affiche en plus un bloc en haut à droite : version de l'appli et moteur, flux direct/converti/relancé, paramètres envoyés à Plex (`subtitleStreamID`, `subtitles`, `videoResolution`, `mediaIndex`, `audioStreamID`…), état du bouton ST (départ, choisi, incrusté, annexe, mode, taille de la liste, dernier message), pistes texte reçues par ExoPlayer (langue, type, indicateurs, sélectionnée, non lue), versions du film et pistes de sous-titres vues par Plex (id, codec, langue, forcé, sélectionné, externe).
- `ClipPlay.diag` rempli dans `buildQueue` ; composable `SubDiagnostic` (PlayerScreen/MpvPlayerScreen). Aucun jeton n'est affiché.
- Modules `playback` et `players` déverrouillés puis reverrouillés, hash mis à jour.

## Version 1.3.9 : le choix de sous-titres est enregistré sur le serveur
- Cause trouvée avec le diagnostic 1.3.8 (photo « i » chez une amie, même serveur et même appli) : la piste de sous-titres « sélectionnée » côté Plex pour le compte (ici l'anglais) était ajoutée au flux converti (piste CEA-608 dans la vidéo) malgré `subtitleStreamID=0` et `subtitles=none`.
- `PlexClient.setSubtitleStream` : `PUT /library/parts/{id}?subtitleStreamID=…&allParts=1` (0 = aucune), comme `setAudioStream` pour l'audio.
- `buildQueue` : avant le lancement, si la piste sélectionnée sur le serveur diffère du choix de départ de l'appli, elle est enregistrée (le panneau « i » indique « sélection serveur : #a → #b »).
- Lecteurs ExoPlayer et mpv : chaque choix ST (Sans, piste, fichier externe) est enregistré ; pour un flux converti, l'enregistrement précède la relance du flux.
- Effet voulu : la sélection de sous-titres du compte pour ce fichier est celle de l'appli, comme avec les apps Plex officielles.
- Modules `playback` et `players` déverrouillés puis reverrouillés, hash mis à jour.

## Version 1.3.91 : pré-release pour les versions de test, diagnostic vidéo dans « i »
- Cas suivi : saccades et image abîmée (blocs, dominante bleue) quand l'appli force une conversion 1080p (8 Mbit/s demandés, source 4K HEVC).
- `.github/workflows/apk.yml` : les releases créées par la compilation sont des **pre-release**. Le repli de mise à jour (`releases/latest` de GitHub) ignore les pre-release : il ne sert que la version marquée stable à la main, celle de guilard.com/dl. Pour valider une version : la déposer sur guilard.com (`plexiplay.apk` puis `version.txt`) et la passer en release normale dans GitHub.
- Panneau « i » (ExoPlayer) : format vidéo reçu (type, profil et niveau, taille, images/s, débit, transfert et profondeur de couleur), décodeur choisi par la TV (matériel ou logiciel), images affichées / perdues / ignorées, coupures de lecture (tampon vide), tampon d'avance.
- Module `players` déverrouillé (PlayerScreen) puis reverrouillé, hash mis à jour.

## Version 1.3.92 : baisse de résolution en HEVC (tout sur la carte graphique)
- Cause établie par la comparaison des lignes de commande du transcodeur Plex (QNAP, carte NVIDIA T400, film 4K HEVC HDR, 1080p / 8 Mbit/s) :
  - appli officielle : NVDEC + `scale_cuda` (p010, HDR 10 bits conservé) + `hevc_nvenc` — tout sur la carte, fluide ;
  - Plexiplay (`videoCodec=h264`) : `scale` + `tonemap=hable` + `libx264 -preset veryfast` — tout sur le processeur, plus lent que le temps réel : tampon à 0 s, coupures, blocs dans l'image.
- `StreamUrlFactory.transcode` : nouveau paramètre `hevcOutput` (ajoute l'autorisation de profil `append-transcode-target-codec(... h264,hevc)` comme pour le flux direct).
- `buildQueue` : pour une baisse de résolution, si le film est en HEVC, que le lecteur est ExoPlayer et que la TV décode le HEVC, `videoCodec=hevc` (+ autorisation de profil) ; sinon `videoCodec=h264` comme avant. Valable pour le flux de départ et pour les relances (`streamFor`).
- Le panneau « i » indique la sortie demandée (« sortie HEVC » ou « H.264 »). Le son (AAC stéréo) est inchangé.
- À vérifier : la ligne de commande du transcodeur doit montrer `hevc_nvenc` et `scale_cuda` ; l'image doit rester en HDR.
- Modules `players` et `playback` déverrouillés puis reverrouillés, hash mis à jour.

## Version 1.3.93 : son 5.1 dans les flux convertis, fond d'écran des collections
- Son : la conversion complète (ExoPlayer) demande jusqu'à 6 canaux (`maxAudioChannels=6`), comme l'appli officielle (AAC 5.1 à 555 kbit/s au lieu d'un mélange stéréo à 138 kbit/s). La ligne « Demande Plex » du panneau « i » affiche `maxAudioChannels`. Pas d'effet sur mpv ni sur la lecture directe.
- Collections : quand le contenu d'une collection est affiché, le fond d'écran est celui que Plex prévoit pour la collection (`art`, lu dans la liste puis dans la fiche si absent) ; à défaut, le fond du premier film de la collection qui en a un ; à défaut, le fond uni habituel. L'image (1280×720, via `PosterStore`) est assombrie par un dégradé pour garder les affiches lisibles. `ShellScreen` reçoit le paramètre `backdrop` ; le fond de thème est dessiné dans un calque à part.
- Modules `playback` et `players` déverrouillés puis reverrouillés, hash mis à jour.

## Version 1.3.94 : changement de langue / sous-titres en cours de lecture transcodée
- Cas suivi : sur un film transcodé, un changement de langue ou de sous-titres par les boutons FR / ST ne changeait rien (écran noir puis même flux). Cause constatée sur le serveur : après un changement vers l'anglais (`audioStreamID=11494` bien demandé), le transcodeur relancé lisait encore la piste française. Depuis la page du film, tout fonctionne parce que le film repart de zéro avec les pistes déjà enregistrées.
- Séquence suivie maintenant pour un flux converti (ExoPlayer) : mémoriser la position → pause → `PlexClient.stopTranscode` (arrêt de l'ancienne session, `/video/:/transcode/universal/stop?session=`) → enregistrement de la piste audio (`setAudioStream`) et des sous-titres (`setSubtitleStream`) sur le serveur, en attendant leur fin (avant, l'enregistrement audio partait en parallèle de la relance) → nouveau flux (session neuve) à la position mémorisée. Callback `onTrackSync` (MainActivity) ; `syncedRelaunch` (PlayerScreen).
- Vérification : après la relance, la langue de la piste audio lue (ExoPlayer) doit être la langue demandée (codes à trois lettres ramenés à deux, `lang2`). Si elle diffère, si le flux plante ou s'il n'est pas prêt après 30 s : retour au flux précédent à la même position (choix et enregistrement serveur restaurés) et message de 9 s « Impossible en transcodant. Veuillez choisir vos préférences sur la page de présentation. »
- Lecture directe : changement instantané dans le flux, inchangé. mpv : inchangé.
- Panneau « i » : lignes « Audio : #id=langue… · demandé=#… · lu=… » et « Dernier changement : … (en cours / OK après N s / ÉCHEC (raison)) ».
- À vérifier : sur un film transcodé, FR puis EN par les boutons ; la ligne de commande du transcodeur doit changer de piste (`-map [0:2]`).
- Non traité : son transcodé en stéréo malgré `maxAudioChannels=6` (vu sur ce film DTS).
- Module `players` déverrouillé (PlayerScreen) puis reverrouillé, hash mis à jour.

## Version 1.3.95 : test de diagnostic du changement de piste
- En cas d'échec de la vérification (voir 1.3.94), le nouveau flux n'est plus remplacé par le flux précédent : le message reste affiché et le flux demandé continue, pour lire sur le serveur (`ps` du transcodeur Plex) la piste réellement choisie par Plex. Le panneau « i » garde « Dernier changement : … ÉCHEC (raison) ».
- Version de test : le retour automatique au flux précédent sera rétabli une fois la cause trouvée.
- Module `players` déverrouillé (PlayerScreen) puis reverrouillé, hash mis à jour.

## Version 1.3.96 : changement de piste en flux converti = relance complète du film
- Constats (tests en 1080p transcodé, film DTS 5.1 FR / DTS 7.1 EN, « i » en 1.3.95) : l'appli envoie bien `audioStreamID` de la piste voulue, mais le transcodeur Plex lit la piste **sélectionnée sur le serveur** (`PUT /library/parts/{id}?audioStreamID=…`). Ce choix n'est pris en compte qu'à un nouveau lancement du film : changer en cours de lecture (même avec session neuve et arrêt de l'ancienne) donne toujours l'ancienne piste ; couper puis relancer donne la nouvelle. Depuis la page de présentation, la piste audio n'était pas enregistrée avant le lancement, d'où une langue inchangée en transcodé. En 4K direct, la TV choisit la piste dans le flux : tout fonctionne.
- `buildQueue` : pour un flux converti, la piste audio voulue est enregistrée sur le serveur avant le lancement si elle diffère de la sélectionnée (comme pour les sous-titres) ; le « i » indique « sélection serveur audio : #a → #b ».
- PlayerScreen : un changement de piste (FR / menu audio / ST) sur un flux converti appelle `onReload` : MainActivity arrête l'ancienne session de transcodage, enregistre l'audio et le sous-titre, envoie le rapport « arrêté » avec la position, attend 1,5 s, relit la fiche du film, reconstruit la file avec `buildQueue` (sans bonus) et reprend à la même position (écran noir 2 à 3 s). Si la relance complète est impossible (épisode, erreur), repli sur l'ancienne méthode (nouveau flux dans le même lecteur, 1.3.94).
- Vérification : `ClipPlay.expectAudio` ; au démarrage, le lecteur compare la langue jouée à la langue demandée et affiche sinon le popup « Impossible en transcodant. Veuillez choisir vos préférences sur la page de présentation. ». Le retour automatique au flux précédent est rétabli pour le repli (retiré dans 1.3.95 pour le test).
- Le panneau « i » indique « Dernier changement : relance complète · enregistrement serveur : audio #… OK · ST #… OK » puis le résultat de la vérification.
- Effet de bord voulu : le choix de langue ou de sous-titres modifie la sélection du fichier sur le serveur (comme les apps Plex officielles).
- À vérifier : FR → EN et EN → FR par le lecteur en 1080p ; depuis la page de présentation, l'anglais en 1080p doit être entendu ; sous-titres.
- Modules `playback` (`buildQueue`) et `players` (PlayerScreen) déverrouillés puis reverrouillés, hash mis à jour.

## Version 1.3.97 : diagnostic de la relance complète (écran noir)
- Cas suivi : après un changement de langue en 1080p transcodé (1.3.96), écran noir ; panneau « i » : aucune image reçue (décodeur « ? », tampon 0 s), aucun « Plex Transcoder » actif sur le NAS (`ps`), plan 4K sans baisse de résolution. La surveillance de 25 s avait basculé le film en mode compatible, ce qui masquait le flux relancé.
- PlayerScreen : plus de bascule automatique en mode compatible pour un film relancé après un changement de piste (`expectAudio` renseigné).
- Panneau « i » : ligne « Lecteur : état · lecture voulue · image reçue · erreur : code + message » ; ligne « Test du flux » : sans image après 8 s, l'adresse du flux est interrogée directement (HTTP code, durée, début de la réponse de Plex) ; rafraîchi chaque seconde.
- « Dernier changement : relance complète · demandé : résolution, audio, ST, reprise à N s · enregistrement serveur : … » (réglages réellement envoyés à la relance).
- Attente après l'arrêt de l'ancien transcodage : 1,5 s → 5 s.
- Module `players` déverrouillé (PlayerScreen) puis reverrouillé, hash mis à jour. MainActivity : callback `onReload` (hors modules verrouillés).

## Version 1.3.98 : relance complète sans rapport « arrêté », test du flux étendu
- Panneau « i » de la 1.3.97 : relance correcte (1080p, audio #11494, ST #0, reprise à 2830 s, enregistrement serveur OK), liste maître HTTP 200, mais le lecteur s'arrête sur `ERROR_CODE_IO_BAD_HTTP_STATUS` et aucun « Plex Transcoder » n'est actif.
- Hypothèse : le rapport « arrêté » envoyé au serveur avant la relance (1.3.96) fait couper la nouvelle session par Plex. Il est retiré (la position est transmise par `startMs`). L'arrêt de l'ancienne session de transcodage, l'enregistrement des pistes et l'attente de 5 s restent.
- Panneau « i » : l'erreur du lecteur indique le code HTTP et le chemin de la demande refusée ; « Test du flux » interroge la liste maître, la liste des segments qu'elle annonce et le premier segment (code, durée, message de Plex en cas de refus).
- Numérotation : on reste en 1.3.x (1.3.100 après 1.3.99) ; la comparaison de versions de `AppUpdate` est numérique par nombre.
- Module `players` déverrouillé (PlayerScreen) puis reverrouillé, hash mis à jour.

## Version 1.3.99 : nouvelles tentatives sur refus 404 du segment de reprise
- Panneau « i » et `ps` de la 1.3.98 : relance complète correcte et **piste anglaise bien lue** (`-map [0:2]`, `language=eng`) ; mais le lecteur reçoit `404` sur `/video/:/transcode/universal/session/…/base/02841.ts` (segment de la position de reprise) et s'arrête ; le transcodeur de la session est parti de 0 (`-segment_start_number 0`, pas de `-ss`). Le test du flux (maître, liste, segment 0) répond 200 : le serveur n'avait pas encore de transcodage positionné quand le lecteur a demandé le segment 2841.
- PlayerScreen : sur `ERROR_CODE_IO_BAD_HTTP_STATUS` avec code 404, avant la première image d'un film, attente de 3 s puis `prepare()` à la même position, jusqu'à 5 tentatives. « Dernier changement » affiche « nouvelle tentative n/5 après refus 404 ». Vaut aussi pour « Reprendre à… » depuis la page de présentation.
- Si insuffisant : demander à Plex de démarrer le flux à la position (`offset`), ce qui oblige à décaler la durée et la position affichées.
- Module `players` déverrouillé (PlayerScreen) puis reverrouillé, hash mis à jour.

## Version 1.3.100 : message « Basculement… », ST ouvre la liste
- Essais : en mode automatique, le changement de langue FR ↔ EN en 1080p transcodé fonctionne (1.3.99).
- Message centré « Basculement, veuillez patienter quelques secondes… » (fond noir semi-transparent) : affiché dès la validation d'un changement de piste en flux converti (`switching`, ancien lecteur figé puis nouveau lecteur noir), retiré à la première image en lecture, à l'échec définitif (popup) ou après 90 s. Un film relancé (`expectAudio`) démarre avec le message.
- Bouton ST (ExoPlayer) : l'appui simple ouvre la liste des sous-titres du film (« Sans » + pistes) quand le film en a ; l'appui long fait de même. Sans liste (bonus, film sans sous-titres), l'ancien défilement reste. `openSubMenu(fromLongPress)` : le relâchement de la touche n'est ignoré qu'après un appui long. Bouton FR/EN et lecteur mpv inchangés.
- Numérotation : suite en 1.3.x (1.3.100). `AppUpdate` compare les versions nombre par nombre.
- Module `players` déverrouillé (PlayerScreen) puis reverrouillé, hash mis à jour.

## Version 1.4.0 : version de référence (code de la 1.3.100)
- Aucun changement de code par rapport à la 1.3.100 : passage au numéro 1.4.0 une fois validés sur la TV le changement de langue et de sous-titres en lecture transcodée (relance complète, nouvelles tentatives sur 404), le message « Basculement… » et la liste des sous-titres sur ST.
- Récapitulatif de la série 1.3.9 → 1.3.100 : sous-titres imposés par le serveur corrigés (1.3.9), pré-release pour les versions de test (1.3.91), baisse de résolution en HEVC sur la carte graphique (1.3.92), son 5.1 et fonds des collections (1.3.93), changement de piste en flux converti : arrêt, enregistrement serveur, relance complète à la même position (1.3.94 à 1.3.99), message de bascule et liste ST (1.3.100).
- `AppUpdate` compare les versions nombre par nombre : 1.4.0 est plus récente que 1.3.100.
- Modules `playback` et `players` inchangés (hash identiques).

## Version 1.4.1 : version obfusquée (R8), dépôt privé
- Le code source vit désormais dans le dépôt privé `Kahenis/plexiplay-clone` (branche `main`). Sauvegardes : `old-main-1.3.0` (ancienne `main` de ce dépôt), `source-1.4.0` (code en clair de la 1.4.0 avec l'historique), `backup-1.3.6`. Le dépôt public `plexiplay` ne garde que les téléchargements (releases).
- Compilation `assembleRelease` : `isMinifyEnabled` + `isShrinkResources`, `isDebuggable = false`, signée avec la même clé (`signing/debug.keystore`) que les versions précédentes pour que la mise à jour s'installe par-dessus ; numéro interne 119 (versionName 1.4.1).
- `app/proguard-rules.pro` : conservation de FFmpeg (chargé par `Class.forName`), mpv/libmpvkt (JNI), modèles kotlinx.serialization de `tv.plexiplay.**` ; `-dontwarn` pour les classes réseau facultatives.
- Workflow `apk.yml` : compilation release, artefact (APK + `mapping.txt` pour lire les erreurs obfusquées, à garder privé), release dans le dépôt privé ; l'APK est ensuite copié à la main dans une pré-release de `plexiplay` (public).
- À vérifier sur la TV : tout ce qui s'appuie sur le JSON de Plex (bibliothèques, fiches, lecture), mpv, mise à jour automatique, relance complète sur changement de piste, l'écran « Réglages ».
