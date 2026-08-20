<p align="center">
  <img src="images/HYDRA_UMC_ANDROID_CONTROL_BANNER.jpg" alt="HYDRA-UMC Android Control Banner" width="100%">
</p>

# 📱 HYDRA-UMC CONTROL

Une application Android native (Kotlin + Jetpack Compose) qui contrôle un robot sur la plateforme [HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC) via Wi-Fi ou Bluetooth, en parlant exactement le même contrat [`REMOTE_API.md`](https://github.com/JuanenRac/HYDRA-UMC-STUDIO/blob/main/docs/REMOTE_API.md) que celui utilisé par [HYDRA-UMC SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE) - découverte, lecture/écriture de l'état complet, et synchronisation live par WebSocket avec un serveur [HYDRA-UMC STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO) en cours d'exécution. Contrepartie Android directe de [HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL). Voir [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) pour la conception complète.

## 🏗️ Ce qui est implémenté

- **Contrôle d'Accès et Biométrie** (`ui/LoginScreen.kt`, `util/BiometricHelper.kt`) - Système de connexion professionnel avec prise en charge de l'**empreinte digitale et du déverrouillage facial** (`androidx.biometric`), plus des champs IP/port directement sur le même écran pour pouvoir cibler un serveur sans passer d'abord par les Réglages. Comprend une fonction "Se souvenir de moi", un mécanisme de **déconnexion** sécurisé, et est entièrement localisé en **5 langues**. Le nom d'utilisateur/mot de passe/jeton mis en cache (`network/AuthPrefs.kt`) résident dans des **EncryptedSharedPreferences** adossées au Keystore (AES256-GCM), pas en texte clair - chaque serveur de cet écosystème initialise un compte `admin`/`admin` par défaut au premier démarrage, avec des comptes supplémentaires à privilège réduit de type **operator** pouvant être créés côté serveur depuis Config > Users.
- **Mode Hors Ligne et Cache d'État** (`network/StateCache.kt`) - Moteur de persistance intégré utilisant **DataStore**. L'application met automatiquement en cache le dernier état système connu, permettant un affichage instantané du tableau de bord et des audits de configuration même sans connexion Wi-Fi active.
- **Notifications et Alertes de Mission** (`util/NotificationHelper.kt`) - Système d'alerte de niveau industriel. Envoie des notifications push haute priorité lorsqu'un robot termine une séquence de travail ou en cas d'événements matériels critiques, garantissant que l'opérateur est informé même lorsque l'application est en arrière-plan.
- **Terminal de Télémétrie Industrielle** (`ui/TelemetryScreen.kt`) - Un visualiseur de journaux en temps réel dédié avec une interface de style terminal. Suit les événements système, la synchronisation REST/WebSocket, et fournit des diagnostics codés par couleur (Vert Matrix pour le succès, Rouge Industriel pour les erreurs).
- **Tableau de Bord Avancé** (`ui/DashboardScreen.kt`) - **Carrousel Horizontal 3D** haute fidélité avec effets de balayage en perspective. Affiche des métadonnées enrichies du robot : **Fabricant** (Source Robotics, Annin, Universal Robots, AgileX, etc.), **Rôle du Robot** (CNC, Laser, PnP), et une **Matrice de Modules Industrielle** avec état en direct pour les modules CAM, XY, ATC, PNP, CNC, LSR, BED, VAC et RCK.
- **Moniteur de Santé du Système** (`ui/DashboardScreen.kt`) - Métriques en temps réel pour la Compute Module 5 connectée, y compris le **nom d'hôte**, le **temps de disponibilité formaté** (par ex. « 2d 4h 15m »), et les décomptes actifs de contrôleurs et de robots.
- **Contrôle Manuel Amélioré** (`ui/ControlScreen.kt`) - Propose une disposition verticale professionnelle avec des **boutons de joystick 50 % plus grands** pour une précision maximale. Comprend un **sélecteur de tâche/trajectoire** pour parcourir et exécuter des fichiers directement depuis le serveur.
- **Panneau de Sécurité et de Lecture** (`ui/ControlScreen.kt`) - Barre de contrôle inférieure fixe abritant les boutons **E-STOP (arrêt d'urgence)**, **Start**, **Pause** et **Stop**. Ces contrôles sont toujours visibles et disposent d'un **retour haptique** pour une confirmation sensorielle physique.
- **Vue 3D** (`ui/ThreeDScreen.kt`) - Intègre le propre viewport 3D en temps réel de HYDRA-UMC STUDIO dans une WebView (`?hideUI=true&robotId=&token=`), plutôt qu'un moteur de rendu natif - `ui/NativeThreeDScreen.kt` est une expérience inachevée avec Google Filament (déclarée mais non reliée à la navigation, et sans chargement d'assets `.glb` pour l'instant), conservée dans l'arborescence pour quiconque la reprendra plus tard, mais ce n'est pas le chemin de code actif. L'approche WebView obtient gratuitement la véritable scène 3D de STUDIO actuellement en production (chaque maillage/cinématique réel de chaque robot) au lieu de la réimplémenter nativement - le compromis est la surcharge de rendu de la WebView, non optimale pour la batterie, mais fonctionnellement complète aujourd'hui.
- **Vision Octale en Temps Réel** (`ui/CameraScreen.kt`, `ui/MjpegPlayer.kt`) - **Streamer MJPEG natif** de niveau industriel. Dispose d'un analyseur autonome en arrière-plan et d'un moteur de rendu basé sur Canvas pour une télémétrie vidéo à latence nulle, un état clair « Caméra désactivée » (au lieu d'un flux vide silencieux) lorsque le système de vision d'un robot est éteint, et un interrupteur pour activer/désactiver la caméra d'un robot directement depuis le serveur. Prend en charge les incrustations automatiques **Picture-in-Picture (PIP)** dans l'écran de contrôle manuel, associées à des robots spécifiques via la configuration de caméra du serveur.
- **Découverte et Connectivité Intelligentes** (`network/Discovery.kt`, `network/HydraApiClient.kt`, `network/HydraWebSocket.kt`) - Analyse concurrente du sous-réseau sur chaque hôte candidat du propre /24 du téléphone (y compris l'IP LAN propre du téléphone et localhost, pas seulement les autres hôtes), sondant `GET /api/hydra-info` et identifiant un vrai serveur uniquement par la présence de `remoteApiVersion` - la même vérification que celle utilisée par le chemin IP manuel, de sorte qu'un serveur dont le propriétaire l'a renommé par rapport à la chaîne de produit par défaut est quand même trouvé. Un écouteur **NsdManager** (mDNS/Bonjour) tourne en parallèle - le serveur s'annonce bien comme `_hydra._tcp` (`bonjour-service`), et `MainActivity` demande à l'avance l'autorisation d'exécution de localisation/appareils à proximité nécessaire pour cela (une déclaration dans le manifeste seule ne l'accorde jamais sur API 23+) - mais l'analyse du sous-réseau reste le chemin principal car l'accessibilité multicast en Wi-Fi est intrinsèquement moins fiable qu'une simple sonde HTTP. L'application active automatiquement le WiFi au démarrage, scanne le réseau local de l'usine, et effectue une **connexion automatique sans clic** au premier serveur HYDRA-UMC disponible.
- **Accès Industriel Sécurisé** (`network/HydraApiClient.kt`, `ui/LoginScreen.kt`) - Couche de sécurité professionnelle utilisant **JWT (JSON Web Tokens)**. Chaque commande de contrôle (Jog, Play, E-STOP) est validée par le serveur à l'aide de jetons signés, envoyés via le point de terminaison atomique `POST /api/robot/:id/command` (voir Synchronisation des Commandes Atomiques ci-dessous) - fonctionne aussi bien avec le rôle `admin` qu'`operator`, contrairement à une écriture complète sur `POST /api/settings` (réservée à l'admin côté serveur). Chaque requête porte également un en-tête `X-Hydra-Client: android` afin que l'onglet Config > Remote Access du serveur puisse autoriser/bloquer cette application indépendamment de SUITE/iOS. Intégré sans accroc avec l'**authentification biométrique** (empreinte/visage) pour le renouvellement sécurisé des jetons. Un WebSocket fermé avec le code `1008` (jeton invalide/expiré) est traité comme « reconnectez-vous », sans être retenté dans une boucle de reconnexion (`network/HydraWebSocket.kt`).
- **Synchronisation des Commandes Atomiques** (le propre `sendAtomicCommand()` de `viewmodel/RobotViewModel.kt`) - Chaque écriture (enable/disable/play/pause/stop/jog/valve/pump/speed/vision) envoie une petite commande atomique concernant un seul robot au lieu de l'arbre de configuration entier - le serveur calcule quels robots combinés sont également affectés, persiste sur disque, et diffuse de lui-même à tous les autres clients connectés. Enable/Disable se propage aux propres frères `combinedWith` d'un robot de la même façon que le font Play/Pause/Stop, puisqu'ils partagent tous le même calcul de robots affectés.
- **Widget de Gestion des Urgences** (`widget/GlobalStopWidget.kt`) - **Widget d'écran d'accueil** dédié pour la sécurité critique. Fournit un bouton **E-STOP Global** à haute visibilité et accès instantané pour figer toutes les opérations robotiques de l'essaim sans avoir besoin d'ouvrir l'application - attend de manière fiable que la liste des robots soit réellement chargée avant d'agir, même après un démarrage à froid complet (processus pas encore en cours d'exécution).
- **Haptique et Sécurité Industrielle** (`ui/ControlScreen.kt`) - Système avancé de retour sensoriel. Dispose d'une véritable **protection par pression longue** sur les boutons E-STOP et STOP (un appui rapide ne fait rien à part un bref bourdonnement + une indication ; seul un maintien authentique envoie la commande) et de signatures haptiques différenciées (impulsions de succès, d'erreur et d'urgence) pour fournir une confirmation physique à l'opérateur dans des environnements bruyants.
- **Chaîne d'Outils et Qualité du Projet** - AGP 9.3.1, Kotlin 2.2.10, Gradle 9.7.0, compileSdk 36, **JDK 21** (`compileOptions`, `gradle-daemon-jvm.properties`, et les fichiers de projet `.idea/` comme `.vscode/` le ciblent réellement, pas seulement cette ligne de documentation). Sortie de build propre sans avertissement, variantes de production R8 optimisées, et tests de captures d'écran avancés avec **Roborazzi**.

**État : Wi-Fi, Bluetooth, biométrie et notifications implémentés.** L'application est une console industrielle haut de gamme prête pour des opérations robotiques à mission critique.

## 🚀 Compilation

Nécessite **spécifiquement un JDK 21** et le Android SDK.

1. Installez [Android Studio](https://developer.android.com/studio).
2. Ouvrez la racine du projet et laissez la synchronisation Gradle se terminer.
3. Connectez un appareil et appuyez sur ▶️ Run, ou utilisez les scripts ci-dessous.

### 🛠️ Scripts de compilation + installation

Le chemin le plus rapide depuis un terminal à la racine du dépôt - compile l'APK de débogage, liste les appareils connectés via `adb`, et l'installe en une seule fois :

```bash
./build-android.sh     # Linux/macOS
build-android.bat      # Windows
```

Si `adb` n'est pas dans le `PATH`, le script termine quand même la compilation et affiche où l'APK a atterri afin qu'il puisse être installé manuellement.

### ⚙️ Compilation manuelle

Étapes équivalentes sans les scripts, pour l'intégration continue ou un simple terminal :

```bash
./gradlew assembleDebug        # Linux/macOS
gradlew.bat assembleDebug      # Windows
```

L'APK atterrit dans `app/build/outputs/apk/debug/app-debug.apk`. Installez-le avec `adb install -r -d app/build/outputs/apk/debug/app-debug.apk`, ou transférez-le manuellement sur l'appareil. Remplacez `assembleDebug` par `assembleRelease` pour une compilation de release - elle signe actuellement avec la clé de débogage (le propre bloc `release` de `app/build.gradle.kts`, conservé ainsi pour faciliter les tests), donc elle s'installe correctement mais n'est pas prête pour la distribution telle quelle.

## 📲 Tests contre HYDRA-UMC STUDIO

1. Lancez le serveur : `cd HYDRA-UMC-STUDIO && npm run dev` (Port 3000).
2. Connectez votre appareil Android au même Wi-Fi.
3. Utilisez le **sélecteur de serveur global** ou saisissez l'IP manuellement dans l'en-tête.
4. **Biométrie :** activez « Biometric Login » dans votre profil utilisateur pour sauter l'écran de mot de passe au prochain lancement.

## 🩺 Dépannage

| Symptôme | Cause | Solution |
|---|---|---|
| Aucune notification | Autorisation refusée | Accordez l'autorisation « Notifications » dans les réglages Android pour cette application |
| Aucune biométrie | Matériel non configuré | Assurez-vous d'avoir une empreinte/un visage enregistré dans la sécurité système Android |
| Le robot ne bouge pas | Lien cérébral du navigateur | Gardez un onglet de navigateur HYDRA-UMC STUDIO ouvert pour le traitement de la cinématique inverse (IK) |
| Bluetooth désactivé | Puce physique éteinte | Utilisez le bouton 3D « ENABLE SYSTEM BT » dans l'application |

## 📂 Structure du dépôt

```text
HYDRA-UMC-ANDROID-CONTROL/
├── app/
│   ├── build.gradle.kts          # Configuration Gradle du module app - versions AGP/Kotlin/Compose, dépendances, type de build release signé avec la clé de débogage
│   └── src/main/
│       ├── AndroidManifest.xml   # Permissions, déclarations d'activity/receiver, usesCleartextTraffic (serveur LAN HTTP simple, sans TLS)
│       ├── java/com/hydraumc/control/
│       │   ├── MainActivity.kt          # Point d'entrée - écran de démarrage, contrôle d'accès login/écran principal, gestion sûre de l'E-STOP global au démarrage à froid
│       │   ├── MainScreen.kt            # Structure de navigation inférieure, barre supérieure (sélecteur de serveur, profil, télémétrie, réglages)
│       │   ├── model/
│       │   │   ├── BleDevice.kt          # Data class pour le résultat de scan Bluetooth LE
│       │   │   └── HydraState.kt         # Miroir champ par champ de settings.json (RobotView/ControllerView/JobView) + modèle de découverte ServerInfo
│       │   ├── network/
│       │   │   ├── AuthPrefs.kt           # Stockage chiffré (AES256-GCM) des identifiants/session
│       │   │   ├── ConnectionPrefs.kt     # IP/port du serveur persistés (DataStore Preferences)
│       │   │   ├── Discovery.kt           # Analyse concurrente du sous-réseau /24 (principale) + écouteur NSD/mDNS (secondaire) pour trouver un serveur sur le LAN
│       │   │   ├── HydraApiClient.kt      # Client REST - connexion, lecture/écriture des réglages, commandes atomiques de robot, métriques système
│       │   │   ├── HydraBleClient.kt      # Client GATT Bluetooth, transport alternatif au Wi-Fi
│       │   │   ├── HydraWebSocket.kt      # Envoi live des deltas d'état via WS, gestion de la reconnexion
│       │   │   └── StateCache.kt          # Cache du dernier état connu (DataStore) pour l'affichage du tableau de bord hors ligne
│       │   ├── ui/
│       │   │   ├── AboutDialog.kt          # Boîte de dialogue d'informations app/version
│       │   │   ├── CameraScreen.kt         # Flux caméra MJPEG par robot + interrupteur vision on/off
│       │   │   ├── ControlScreen.kt        # Contrôles de jog manuels, E-STOP/play/pause/stop avec protection par pression longue
│       │   │   ├── DashboardScreen.kt      # Sélecteur de robot à carrousel 3D + santé du système + matrice de modules
│       │   │   ├── LoginScreen.kt          # Saisie nom d'utilisateur/mot de passe + IP/port, connexion biométrique
│       │   │   ├── MjpegPlayer.kt          # Analyseur de flux MJPEG + moteur de rendu Canvas
│       │   │   ├── NativeThreeDScreen.kt   # Visualiseur 3D natif Google Filament - pas encore relié à la navigation, aucun pipeline .glb
│       │   │   ├── SettingsScreen.kt       # Interface de scan Wi-Fi/Bluetooth, réglages de connexion
│       │   │   ├── SplashScreen.kt         # Écran de démarrage Compose personnalisé
│       │   │   ├── TelemetryScreen.kt      # Visualiseur de journal d'événements/synchronisation en style terminal
│       │   │   ├── ThreeDScreen.kt         # Viewport 3D réel - WebView intégrant la propre scène 3D headless de STUDIO
│       │   │   ├── UserProfileDialog.kt    # Boîte de dialogue d'édition de profil + interrupteur biométrique
│       │   │   └── theme/
│       │   │       ├── Color.kt, Theme.kt, Typography.kt   # Schéma de couleurs Material 3, enrobage de thème, échelle typographique
│       │   │       └── HydraButton.kt, IndustrialComponents.kt, IndustrialStyle.kt   # Blocs d'interface partagés au style industriel
│       │   ├── util/
│       │   │   ├── BiometricHelper.kt      # Enrobage de l'invite androidx.biometric
│       │   │   └── NotificationHelper.kt   # Notifications push de travail terminé/sécurité
│       │   ├── viewmodel/
│       │   │   └── RobotViewModel.kt   # ViewModel partagé - réseau, authentification, découverte, envoi des commandes atomiques, tout l'état de l'interface
│       │   └── widget/
│       │       └── GlobalStopWidget.kt # Widget d'écran d'accueil pour un E-STOP global sans ouvrir l'application
│       └── res/
│           ├── drawable/, layout/, mipmap*/, xml/   # Icônes, disposition du widget, icônes de lanceur, règles de sauvegarde/extraction de données
│           └── values/, values-es/, values-de/, values-fr/, values-it/   # Chaînes en 5 langues, couleurs, thème
├── docs/
│   └── ARCHITECTURE.md           # Notes de conception/architecture
├── images/                       # Ressources sources de la bannière README + écran de démarrage
├── build-android.bat / .sh       # Scripts de commodité pour compiler + installer via adb en une seule fois
├── gradlew, gradlew.bat          # Wrapper Gradle
├── build.gradle.kts, settings.gradle.kts, gradle.properties   # Configuration racine du projet Gradle
├── local.properties              # Chemin local du SDK Android (spécifique à la machine, non commité)
├── .env.example                  # Exemple de variables d'environnement
├── README.md                     # Ce fichier
├── README_spa.md / README_ita.md / README_fra.md / README_deu.md   # Traductions
└── LICENSE                       # GPL-3.0
```

## 🔗 Projets connexes

Ce projet fait partie d'un écosystème robotique plus large du même auteur (JuanenRac / Electro Hobby 3D). Bon à savoir, car une demande pourrait en réalité concerner l'un de ceux-ci plutôt que ce dépôt :

**Plateforme HYDRA-UMC** — la cellule de micro-usine multi-robots
- **[HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC)** — la carte mère elle-même : hôte Raspberry Pi CM5 + coprocesseur temps réel STM32H745 double cœur, orchestrant jusqu'à 8 bras robotiques distribués via CAN-OTA/SPI-OTA. Matériel + micrologiciel propres, GPL-3.0/CERN-OHL-S v2/CC BY-SA 4.0.
- **[HYDRA-UMC STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO)** — tableau de bord de contrôle web pour HYDRA-UMC : visualisation 3D multi-robots, enregistrement de cinématique/trajectoires, flashage et tests CAN-OTA pour toute la plateforme. React + Vite + Three.js.
- **HYDRA-UMC-ANDROID-CONTROL** *(ce dépôt)* — application de contrôle Android pour HYDRA-UMC via Wi-Fi/Bluetooth. Application réelle et fonctionnelle - jeu complet de fonctionnalités de contrôle à distance, authentification JWT, stockage chiffré des identifiants.
- **[HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL)** — application de contrôle iOS/iPadOS pour HYDRA-UMC via Wi-Fi, construite en Flutter (multiplateforme, vérifiable sous Windows sans Mac ; l'empaquetage final `.ipa` nécessite quand même Xcode). Application réelle et fonctionnelle - mêmes fonctionnalités que l'application Android.
- **[HYDRA-UMC-SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE)** — centre de commande de bureau (Python/PySide6) pour l'essaim : découverte réseau multi-contrôleurs, synchronisation bidirectionnelle en direct, viewport 3D réel du robot, espace de travail ancrable façon Photoshop. Réel et fonctionnel, pas un placeholder.
- **[HYDRA-UMC-EDITOR-URDF](https://github.com/JuanenRac/HYDRA-UMC-EDITOR-URDF)** — créateur/éditeur graphique de bureau (Python/PySide6) de fichiers URDF pour le propre catalogue de modèles de ce projet : récupère les fichiers source depuis GitHub ou un dossier local, valide la faisabilité des degrés de liberté (DOF), modifie couleur/échelle/cinématique avec un aperçu 3D en direct, et publie le résultat final vers un serveur STUDIO en cours d'exécution. Réel et fonctionnel, pas un placeholder.
- **[HYDRA-UMC-DSI](https://github.com/JuanenRac/HYDRA-UMC-DSI)** — prévu : une interface tactile native pour le propre écran tactile DSI 7" (1280×800) de HYDRA-UMC sur la Compute Module 5, contrôlant ce même serveur directement depuis la carte. Pas encore commencé.

**Plateforme URTC** — le contrôleur de tête d'outil que porte chaque bras robotique HYDRA-UMC
- **[URTC](https://github.com/JuanenRac/URTC)** — Universal Robot Tool Controller : contrôleur de tête d'outil sur bus CAN basé sur STM32F303, 25 profils d'outils entièrement implémentés, mise à jour de firmware CAN-OTA.
- **[URTC Flasher](https://github.com/JuanenRac/URTC-FLASHER)** — outil de bureau de flashage CAN-OTA + puce complète via SWD/JTAG pour les cartes URTC (Windows/Linux).
- **[URTC Tester](https://github.com/JuanenRac/URTC-TESTER)** — outil de bureau de diagnostic en direct sur bus CAN pour les cartes URTC, un panneau par profil d'outil (Windows/Linux).
- **[URTC Web Studio](https://github.com/JuanenRac/URTC-WEB-STUDIO)** — alternative basée sur navigateur aux 2 outils de bureau ci-dessus (Web Serial API + SLCAN), sans installation locale nécessaire.

## 👤 Auteur

**JuanenRac** (Electro Hobby 3D)
📧 electrohobby3d@gmail.com
📺 youtube.com/@electrohobby3d

## 📜 Licence

**GNU General Public License v3.0 (GPL-3.0)** pour le code source - voir [`LICENSE`](LICENSE).

Cette documentation (ce README et ses propres traductions - `README_spa.md`, `README_ita.md`, `README_fra.md`, `README_deu.md`) est disponible sous **Creative Commons Attribution-ShareAlike 4.0 International (CC BY-SA 4.0)**. Texte complet sur https://creativecommons.org/licenses/by-sa/4.0/.
