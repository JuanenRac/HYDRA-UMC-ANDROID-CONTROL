<p align="center">
  <img src="images/HYDRA_UMC_BANNER.svg" alt="HYDRA-UMC-ANDROID-CONTROL banner" width="100%">
</p>

# 📱 HYDRA-UMC CONTROL

<p align="center">
  <a href="README.md">🇺🇸 English</a> |
  <a href="README_spa.md">🇪🇸 Español</a> |
  <a href="README_fra.md">🇫🇷 Français</a> |
  <a href="README_ita.md">🇮🇹 Italiano</a> |
  🇩🇪 <b>Deutsch</b> |
  <a href="README_zho.md">🇨🇳 简体中文</a> |
  <a href="README_jpn.md">🇯🇵 日本語</a>
</p>


<p align="left">
  <img src="https://img.shields.io/badge/Lizenz-GPL%203.0-blue.svg" alt="GPL 3.0">
  <img src="https://img.shields.io/badge/Sprache-Kotlin-7F52FF.svg" alt="Kotlin">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg" alt="Compose">
  <img src="https://img.shields.io/badge/Plattform-Android-3DDC84.svg" alt="Android">
</p>


Eine native Android-App (Kotlin + Jetpack Compose), die einen Roboter auf der [HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC)-Plattform über Wi-Fi oder Bluetooth steuert und dabei genau denselben [`REMOTE_API.md`](https://github.com/JuanenRac/HYDRA-UMC-SERVER/blob/main/docs/REMOTE_API.md)-Vertrag spricht, den auch [HYDRA-UMC SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE) verwendet - Discovery, vollständiges Lesen/Schreiben des Zustands und Live-Synchronisation per WebSocket gegen ein laufendes [HYDRA-UMC-SERVER](https://github.com/JuanenRac/HYDRA-UMC-SERVER)-Backend (dasselbe, mit dem auch das eigene Web-Dashboard von [HYDRA-UMC STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO) spricht). Direktes Android-Gegenstück zu [HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL). Siehe [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) für das vollständige Design.

## 🏗️ Was implementiert ist

- **Zugriffskontrolle & Biometrie** (`ui/LoginScreen.kt`, `util/BiometricHelper.kt`) - Professionelles Login-System mit Unterstützung für **Fingerabdruck und Face Unlock** (`androidx.biometric`), plus IP-/Port-Felder direkt auf demselben Bildschirm, damit ein Server angesteuert werden kann, ohne zuerst einen separaten Umweg über die Einstellungen zu nehmen. Enthält eine "Angemeldet bleiben"-Funktion, einen sicheren **Logout**-Mechanismus und ist vollständig in **5 Sprachen** lokalisiert. Der zwischengespeicherte Benutzername/das Passwort/Token (`network/AuthPrefs.kt`) liegen in Keystore-gestützten **EncryptedSharedPreferences** (AES256-GCM), nicht im Klartext - jeder Server in diesem Ökosystem legt beim ersten Start ein Standardkonto `admin`/`admin` an, mit zusätzlichen, weniger privilegierten **operator**-Konten, die serverseitig unter Config > Users erstellt werden können.
- **Offline-Modus & Zustands-Cache** (`network/StateCache.kt`) - Integrierte Persistenz-Engine mit **DataStore**. Die App speichert den zuletzt bekannten Systemzustand automatisch zwischen und ermöglicht so eine sofortige Dashboard-Ansicht und Konfigurationsprüfungen auch ohne aktive Wi-Fi-Verbindung.
- **Missionsbenachrichtigungen & Warnungen** (`util/NotificationHelper.kt`) - Alarmsystem auf Industrieniveau. Sendet Push-Benachrichtigungen mit hoher Priorität, wenn ein Roboter eine Auftragssequenz abschließt oder wenn kritische Hardware-Ereignisse auftreten, sodass der Bediener auch dann informiert wird, wenn sich die App im Hintergrund befindet.
- **Industrielles Telemetrie-Terminal** (`ui/TelemetryScreen.kt`) - Ein eigener Echtzeit-Log-Viewer mit Terminal-artiger Oberfläche. Verfolgt Systemereignisse, REST/WebSocket-Synchronisation und liefert farblich codierte Diagnosen (Matrix-Grün für Erfolg, Industrie-Rot für Fehler).
- **Erweitertes Dashboard** (`ui/DashboardScreen.kt`) - Hochauflösendes **horizontales 3D-Karussell** mit Perspektiv-Wischeffekten. Zeigt angereicherte Robotermetadaten: **Hersteller** (Source Robotics, Annin, Universal Robots, AgileX usw.), **Roboterrolle** (CNC, Laser, PnP) und eine **industrielle Modulmatrix** mit Live-Status für die Module CAM, XY, ATC, PNP, CNC, LSR, BED, VAC und RCK.
- **Systemzustandsmonitor** (`ui/DashboardScreen.kt`) - Echtzeitmetriken für die verbundene Compute Module 5, einschließlich **Hostname**, **formatierter Betriebszeit** (z. B. "2d 4h 15m") und aktiver Anzahl von Controllern und Robotern.
- **Erweiterte manuelle Steuerung** (`ui/ControlScreen.kt`) - Bietet ein professionelles vertikales Layout mit **50 % größeren Joystick-Tasten** für maximale Präzision. Enthält einen **Job-/Trajektorien-Selektor** zum Durchsuchen und Ausführen von Dateien direkt vom Server.
- **Sicherheits- und Wiedergabepanel** (`ui/ControlScreen.kt`) - Feste untere Steuerleiste mit den Tasten **E-STOP (Nothalt)**, **Start**, **Pause** und **Stop**. Diese Bedienelemente sind immer sichtbar und bieten **haptisches Feedback** zur physischen sensorischen Bestätigung.
- **3D-Ansicht** (`ui/ThreeDScreen.kt`) - Bettet das eigene Echtzeit-3D-Viewport von HYDRA-UMC STUDIO in eine WebView ein (`?hideUI=true&robotId=&token=`), statt eines nativen Renderers - `ui/NativeThreeDScreen.kt` ist ein unfertiges Google-Filament-Experiment (deklariert, aber nicht in die Navigation eingebunden, und noch ohne `.glb`-Asset-Laden), das im Baum verbleibt für alle, die es später wieder aufgreifen, aber nicht der aktive Codepfad ist. Der WebView-Ansatz erhält die echte, aktuell ausgelieferte STUDIO-3D-Szene (jedes echte Roboter-Mesh/jede echte Kinematik) kostenlos, statt sie nativ neu zu implementieren - der Kompromiss ist der WebView-Rendering-Overhead, nicht batterieoptimal, aber heute funktional vollständig.
- **Echtzeit-Oktal-Vision** (`ui/CameraScreen.kt`, `ui/MjpegPlayer.kt`) - **Nativer MJPEG-Streamer** auf Industrieniveau. Verfügt über einen autonomen Hintergrund-Parser und einen Canvas-basierten Renderer für Videotelemetrie mit null Latenz, einen klaren "Kamera deaktiviert"-Zustand (statt eines stillschweigend leeren Feeds), wenn das Vision-System eines Roboters ausgeschaltet ist, sowie einen Schalter, um die Kamera eines Roboters direkt vom Server aus ein-/auszuschalten. Unterstützt automatische **Picture-in-Picture (PIP)**-Overlays im Bildschirm für manuelle Steuerung, die über die Kamerakonfiguration des Servers bestimmten Robotern zugeordnet werden.
- **Intelligente Erkennung & Konnektivität** (`network/Discovery.kt`, `network/HydraApiClient.kt`, `network/HydraWebSocket.kt`) - Gleichzeitiger Subnetz-Scan gegen jeden Kandidaten-Host im eigenen /24 des Telefons (einschließlich der eigenen LAN-IP des Telefons und localhost, nicht nur der anderen Hosts), der `GET /api/hydra-info` abfragt und einen echten Server ausschließlich anhand des Vorhandenseins von `remoteApiVersion` identifiziert - dieselbe Prüfung, die auch der manuelle IP-Pfad verwendet, sodass ein Server, dessen Besitzer ihn von der Standard-Produktzeichenkette umbenannt hat, trotzdem gefunden wird. Ein **NsdManager**-Listener (mDNS/Bonjour) läuft parallel dazu - der Server kündigt sich tatsächlich als `_hydra._tcp` (`bonjour-service`) an, und `MainActivity` fordert die dafür nötige Laufzeit-Berechtigung für Standort/Geräte in der Nähe von vornherein an (eine Manifest-Deklaration allein gewährt sie ab API 23 nie) - aber der Subnetz-Scan bleibt der primäre Pfad, da die Multicast-Erreichbarkeit über Wi-Fi von Natur aus weniger zuverlässig ist als eine einfache HTTP-Abfrage. Die App aktiviert beim Start automatisch WiFi, scannt das lokale Fabriknetzwerk und führt eine **Zero-Click-Auto-Verbindung** zum ersten verfügbaren HYDRA-UMC-Server durch.
- **Sicherer industrieller Zugriff** (`network/HydraApiClient.kt`, `ui/LoginScreen.kt`) - Professionelle Sicherheitsschicht mit **JWT (JSON Web Tokens)**. Jeder Steuerbefehl (Jog, Play, E-STOP) wird vom Server anhand signierter Tokens validiert, gesendet über den atomaren Endpunkt `POST /api/robot/:id/command` (siehe Atomare Befehlssynchronisation weiter unten) - funktioniert sowohl mit der Rolle `admin` als auch `operator`, im Gegensatz zu einem vollständigen Schreibvorgang auf `POST /api/settings` (serverseitig nur für Admins). Jede Anfrage trägt außerdem einen `X-Hydra-Client: android`-Header, damit der eigene Config > Remote Access-Tab des Servers diese App unabhängig von SUITE/iOS erlauben/blockieren kann. Nahtlos integriert mit **biometrischer Authentifizierung** (Fingerabdruck/Gesicht) für die sichere Token-Erneuerung. Ein WebSocket, der mit dem Code `1008` (ungültiges/abgelaufenes Token) geschlossen wird, wird als "erneut anmelden" behandelt und nicht in einer Wiederverbindungsschleife erneut versucht (`network/HydraWebSocket.kt`).
- **Atomare Befehlssynchronisation** (das eigene `sendAtomicCommand()` von `viewmodel/RobotViewModel.kt`) - Jeder Schreibvorgang (enable/disable/play/pause/stop/jog/jogStep/valve/pump/speed/vision/tool) sendet einen kleinen, atomaren Befehl für einen einzelnen Roboter statt des gesamten Einstellungsbaums - der Server berechnet, welche kombinierten Roboter ebenfalls betroffen sind, speichert auf der Festplatte und sendet von sich aus an jeden anderen verbundenen Client. Enable/Disable wird auf die eigenen `combinedWith`-Geschwister eines Roboters genauso propagiert wie Play/Pause/Stop, da alle dieselbe Berechnung der betroffenen Roboter teilen.
- **Notfallmanagement-Widget** (`widget/GlobalStopWidget.kt`) - Dediziertes **Homescreen-Widget** für kritische Sicherheit. Bietet eine gut sichtbare, sofort zugängliche **globale E-STOP**-Taste, um alle Roboteroperationen im Schwarm einzufrieren, ohne die App öffnen zu müssen - wartet zuverlässig darauf, dass die Roboterliste tatsächlich geladen ist, bevor gehandelt wird, selbst nach einem vollständigen Kaltstart (Prozess läuft noch nicht).
- **Industrielle Haptik & Sicherheit** (`ui/ControlScreen.kt`) - Fortschrittliches sensorisches Feedback-System. Verfügt über einen echten **Schutz vor langem Drücken** an den Tasten E-STOP und STOP (ein schnelles Antippen bewirkt nichts außer einem kurzen Brummen + Hinweis; nur ein echtes Halten sendet den Befehl) sowie differenzierte haptische Signaturen (Erfolgs-, Fehler- und Notfall-Impulse), um dem Bediener in lauten Umgebungen eine physische Bestätigung zu geben.
- **In-App-Update-Kanal** (`update/GitHubReleaseUpdater.kt`, `update/ReleaseMetadataParser.kt`, `update/SemanticVersion.kt`) - Prüft beim Start und unter **Einstellungen → Updates** auf die neueste stabile GitHub Release; lädt ausschließlich das exakte Asset `HYDRA-UMC-ANDROID-CONTROL-release.apk` von einem Nicht-Entwurfs-/Nicht-Vorab-Tag herunter und installiert nie automatisch - Androids eigener Paketinstaller fordert die endgültige Zustimmung an. Vollständiger Vertrag: [`docs/GITHUB_RELEASE_UPDATES.md`](docs/GITHUB_RELEASE_UPDATES.md).
- **Gekoppelter Wear-OS-Begleiter & Sprach-Relay** (`wear/WatchVoiceRelayService.kt`, `wear/WatchCompanionProtocol.kt`) - Leitet einen bereits erkannten, vom Nutzer initiierten Sprachdurchgang von der gekoppelten [HYDRA-UMC-WATCH](https://github.com/JuanenRac/HYDRA-UMC-WATCH)-App über die eigene authentifizierte Server-Sitzung dieser App an `HYDRA-UMC-VOICE-UI` weiter und sendet eine typisierte Antwort an die Uhr zurück; weder dieses Relay noch die Systemstatus-Karte der Uhr lösen jemals einen Roboterbefehl aus oder greifen direkt auf `HydraState` zu - eine bewegungsbezogene Antwort ist explizit als `requiresConfirmation` markiert und muss über eine primäre Steuerungs-UI bestätigt werden. Vollständiger Vertrag: [`docs/WATCH_VOICE_RELAY.md`](docs/WATCH_VOICE_RELAY.md).
- **Toolchain & Projektqualität** - AGP 9.3.1, Kotlin 2.2.10, Gradle 9.7.0, compileSdk 36, **JDK 21** (`compileOptions`, `gradle-daemon-jvm.properties` sowie sowohl die `.idea/`- als auch die `.vscode/`-Projektdateien zielen tatsächlich darauf ab, nicht nur diese Dokumentationszeile). Saubere Build-Ausgabe ohne Warnungen, optimierte R8-Produktionsvarianten und fortgeschrittene **Roborazzi**-Screenshot-Tests.

**Status: Wi-Fi, Bluetooth, Biometrie und Benachrichtigungen implementiert.** Die App ist eine hochwertige Industriekonsole, bereit für den missionskritischen Roboterbetrieb.

## 🚀 Erstellen (Building)

Erfordert **speziell ein JDK 21** und das Android SDK.

1. Installieren Sie [Android Studio](https://developer.android.com/studio).
2. Öffnen Sie das Projekt-Root-Verzeichnis und lassen Sie die Gradle-Synchronisierung abschließen.
3. Schließen Sie ein Gerät an und drücken Sie ▶️ Run, oder verwenden Sie die untenstehenden Skripte.

### 🛠️ Build- + Installationsskripte

Der schnellste Weg von einem Terminal im Repository-Root aus - kompiliert die Debug-APK, listet verbundene Geräte über `adb` auf und installiert sie in einem Schritt:

```bash
./build-android.sh     # Linux/macOS
build-android.bat      # Windows
```

Wenn `adb` nicht im `PATH` ist, schließt das Skript den Build trotzdem ab und gibt aus, wo die APK gelandet ist, damit sie manuell installiert werden kann.

### ⚙️ Manueller Build

Äquivalente Schritte ohne die Skripte, für CI oder ein einfaches Terminal:

```bash
./gradlew assembleDebug        # Linux/macOS
gradlew.bat assembleDebug      # Windows
```

Die APK landet unter `app/build/outputs/apk/debug/app-debug.apk`. Installieren Sie sie mit `adb install -r -d app/build/outputs/apk/debug/app-debug.apk`, oder übertragen Sie sie manuell auf das Gerät. Tauschen Sie `assembleDebug` gegen `assembleRelease` für einen Release-Build - dieser signiert derzeit mit dem Debug-Schlüssel (der eigene `release`-Block von `app/build.gradle.kts`, absichtlich so belassen, um Tests zu erleichtern), sodass er problemlos installiert wird, aber so noch nicht vertriebsbereit ist.

## 🔢 Versionierung

Dieses Repository folgt einer Richtlinie für das gesamte Ökosystem: Die Version wird bei **jedem echten Build** automatisch erhöht, ohne manuelle Bearbeitung von `versionName`/`versionCode` in `app/build.gradle.kts`. `app/version.properties` speichert die aktuellen Werte von `versionMajor`/`versionMinor`/`versionPatch`/`versionCode`; `app/build.gradle.kts` liest sie ein, erhöht sie und schreibt die Datei zur **Konfigurationszeit** von Gradle neu - was bei jedem echten Build geschieht (`assembleDebug`, `compileDebugKotlin`, eine IDE-Synchronisierung, ...) - sodass die erzeugte APK immer eine Nummer trägt, die strikt höher ist als die vorherige:

- **Patch, im Kilometerzähler-Stil (Basis 10):** +1 bei jedem Build; würde er 9 überschreiten, wird er auf 0 zurückgesetzt und die Minor-Version um +1 erhöht - Beispiel: `0.0.9` -> `0.1.0`. Die Major-Version wird automatisch nie angefasst.
- **`versionCode`:** ein einfacher monotoner Zähler, +1 bei jedem Build, ohne Übertrag - Android verlangt, dass er bei jedem tatsächlich veröffentlichten Build strikt steigt.

Die laufende Version ist live im **Info**-Dialog sichtbar (`BuildConfig.VERSION_NAME`, das denselben `versionName` liest, den Gradle gerade berechnet hat). Siehe [CHANGELOG.md](CHANGELOG.md) für die Versionshistorie.

## 📲 Testen gegen einen echten Server

1. Backend ausführen: `cd HYDRA-UMC-SERVER && npm run dev` (Port 3000) - das ist die eigentliche REST/WS-API, mit der diese App spricht (siehe Verwandte Projekte weiter unten); das eigene `npm run dev` von `HYDRA-UMC-STUDIO` startet nur dessen Vite-Frontend-Entwicklungsserver (Port 5173) gegen dasselbe Backend, es ist nicht der API-Server selbst.
2. Verbinden Sie Ihr Android-Gerät mit demselben Wi-Fi.
3. Verwenden Sie den **globalen Serverauswähler** oder geben Sie die IP manuell in der Kopfzeile ein.
4. **Biometrie:** Aktivieren Sie "Biometric Login" in Ihrem Benutzerprofil, um beim nächsten Start den Passwortbildschirm zu überspringen.

## 🩺 Fehlerbehebung

| Symptom | Ursache | Behebung |
|---|---|---|
| Keine Benachrichtigungen | Berechtigung verweigert | Erteilen Sie die Berechtigung "Benachrichtigungen" in den Android-Einstellungen für diese App |
| Keine Biometrie | Hardware nicht eingerichtet | Stellen Sie sicher, dass ein Fingerabdruck/Gesicht in Ihrer Android-Systemsicherheit registriert ist |
| Roboter bewegt sich nicht | Browser-Gehirn-Verbindung | Halten Sie einen HYDRA-UMC STUDIO-Browser-Tab für die IK-Verarbeitung geöffnet |
| Bluetooth deaktiviert | Physischer Chip aus | Verwenden Sie die 3D-Taste "ENABLE SYSTEM BT" in der App |

## 📂 Repository-Struktur

```text
HYDRA-UMC-ANDROID-CONTROL/
├── app/
│   ├── build.gradle.kts          # Gradle-Konfiguration des App-Moduls - AGP-/Kotlin-/Compose-Versionen, Abhängigkeiten, mit dem Debug-Schlüssel signierter Release-Build-Typ
│   ├── version.properties        # App-Version im Kilometerzähler-Stil + Android versionCode, von bump_manifest_version.py/bump_version_code.py synchron gehalten
│   ├── proguard-rules.pro        # Regeln zur Codeverkleinerung/Verschleierung für den Release-Build
│   └── src/main/
│       ├── AndroidManifest.xml   # Berechtigungen, Activity-/Receiver-Deklarationen, usesCleartextTraffic (reiner HTTP-LAN-Server, kein TLS)
│       ├── java/com/hydraumc/control/
│       │   ├── MainActivity.kt          # Einstiegspunkt - Splash, Login-/Hauptbildschirm-Zugriffssteuerung, kaltstartsichere globale E-STOP-Behandlung
│       │   ├── MainScreen.kt            # Unteres Navigations-Grundgerüst, obere Leiste (Serverauswahl, Profil, Telemetrie, Einstellungen)
│       │   ├── kinematics/
│       │   │   └── Parol6Kinematics.kt   # Parol6-spezifische Vorwärts-/Rückwärtskinematik
│       │   ├── model/
│       │   │   ├── BleDevice.kt          # Datenklasse für das Bluetooth-LE-Scanergebnis
│       │   │   └── HydraState.kt         # Feld-für-Feld-Spiegel von settings.json (RobotView/ControllerView/JobView) + ServerInfo-Discovery-Modell
│       │   ├── network/
│       │   │   ├── AuthPrefs.kt           # Verschlüsselte (AES256-GCM) Speicherung von Anmeldedaten/Sitzung
│       │   │   ├── ConnectionPrefs.kt     # Persistierte Server-IP/Port (DataStore Preferences)
│       │   │   ├── Discovery.kt           # Gleichzeitiger /24-Subnetz-Scan (primär) + NSD/mDNS-Listener (sekundär) zum Auffinden eines Servers im LAN
│       │   │   ├── HydraApiClient.kt      # REST-Client - Login, Lesen/Schreiben von Einstellungen, atomare Roboterbefehle, Systemmetriken
│       │   │   ├── HydraBleClient.kt      # Bluetooth-GATT-Client, alternativer Transport zu Wi-Fi
│       │   │   ├── HydraWebSocket.kt      # Live-Push von Zustands-Deltas über WS, Wiederverbindungsbehandlung
│       │   │   └── StateCache.kt          # Cache des zuletzt bekannten Zustands (DataStore) für die Offline-Dashboard-Ansicht
│       │   ├── ui/
│       │   │   ├── AboutDialog.kt          # Dialog mit App-/Versionsinformationen
│       │   │   ├── CameraScreen.kt         # MJPEG-Kamerafeed pro Roboter + Vision-Ein-/Aus-Schalter
│       │   │   ├── ControlScreen.kt        # Manuelle Jog-Steuerung, E-STOP/Play/Pause/Stop mit Schutz vor langem Drücken
│       │   │   ├── DashboardScreen.kt      # Roboterauswahl im 3D-Karussell + Systemzustand + Modulmatrix
│       │   │   ├── Joystick3D.kt           # Wiederverwendbare 2-Achsen-Joystick-Komponente
│       │   │   ├── LoginScreen.kt          # Eingabe von Benutzername/Passwort + IP/Port, biometrischer Login
│       │   │   ├── MjpegPlayer.kt          # MJPEG-Stream-Parser + Canvas-Renderer
│       │   │   ├── NativeThreeDScreen.kt   # Nativer 3D-Viewer mit Google Filament - noch nicht in die Navigation eingebunden, keine .glb-Pipeline
│       │   │   ├── PlaybackConsole.kt      # Gemeinsame schwebende E-STOP/Play/Pause/Stop-Konsole
│       │   │   ├── SettingsScreen.kt       # UI für Wi-Fi-/Bluetooth-Scan, Verbindungseinstellungen
│       │   │   ├── SplashScreen.kt         # Benutzerdefinierter Compose-Splashscreen
│       │   │   ├── TelemetryScreen.kt      # Terminal-artiger Viewer für Ereignis-/Synchronisationsprotokolle
│       │   │   ├── ThreeDScreen.kt         # Echtes 3D-Viewport - WebView, die die eigene headless 3D-Szene von STUDIO einbettet
│       │   │   ├── UserProfileDialog.kt    # Dialog zur Profilbearbeitung + biometrischer Schalter
│       │   │   └── theme/
│       │   │       ├── Color.kt, Theme.kt, Typography.kt   # Material-3-Farbschema, Theme-Wrapper, Typografie-Skala
│       │   │       └── HydraButton.kt, IndustrialComponents.kt, IndustrialStyle.kt   # Gemeinsame UI-Bausteine im Industriestil
│       │   ├── update/
│       │   │   ├── GitHubReleaseUpdater.kt   # Sicherer GitHub-Release-Update-Client
│       │   │   ├── ReleaseMetadataParser.kt  # Sicherer Parser für GitHub-Release-Metadaten
│       │   │   └── SemanticVersion.kt        # Strikter Parser für semantische Versionen bei Updates
│       │   ├── util/
│       │   │   ├── BiometricHelper.kt      # Wrapper für den androidx.biometric-Prompt
│       │   │   ├── NotificationHelper.kt   # Push-Benachrichtigungen zu abgeschlossenen Jobs/Sicherheit
│       │   │   └── NotificationPrefs.kt    # Persistente Speicherung des In-App-Benachrichtigungsschalters
│       │   ├── viewmodel/
│       │   │   ├── AppUpdateViewModel.kt   # Lebenszyklusbewusster Zustand des App-Updates
│       │   │   └── RobotViewModel.kt   # Gemeinsames ViewModel - Netzwerk, Authentifizierung, Discovery, Versand atomarer Befehle, gesamter UI-Zustand
│       │   ├── wear/
│       │   │   ├── WatchCompanionProtocol.kt    # Wire-Vertrag für den Versionsstatus des Watch-Companions
│       │   │   ├── WatchVoiceRelayContract.kt   # Authentifizierter Wire-Vertrag für das Watch-Sprachrelais
│       │   │   └── WatchVoiceRelayService.kt    # Wear-OS-Sprachrelais-Dienst
│       │   └── widget/
│       │       └── GlobalStopWidget.kt # Homescreen-Widget für einen globalen E-STOP ohne Öffnen der App
│       └── res/
│           ├── drawable/, layout/, mipmap*/, xml/   # Icons, Widget-Layout, Launcher-Icons, Backup-/Datenextraktionsregeln
│           └── values/, values-es/, values-de/, values-fr/, values-it/, values-ja/, values-zh/   # Zeichenketten in 7 Sprachen, Farben, Theme
├── docs/
│   ├── ARCHITECTURE.md              # Design-/Architekturnotizen
│   ├── GITHUB_RELEASE_UPDATES.md    # In-App-Ablauf für Update-Prüfung/Download/Installation
│   └── WATCH_VOICE_RELAY.md         # Vertrag für das Watch-Telefon-Server-Sprachrelais
├── images/                       # Quellressourcen für das README-Banner + den Splashscreen
├── tools/
│   ├── build_test.py             # Build-/Kompilierprüfung ohne Versionserhöhung
│   └── ci_validate.py            # Manifest-/CHANGELOG-/Doku-Validierung, von der CI genutzt
├── dist/                         # Signierte Release-APK-Ausgabe (von git ignoriert)
├── build-android.bat / .sh       # Komfortskripte für Build + adb-Installation in einem Schritt
├── build-test.bat / .sh          # Build-/Kompilierprüfung ohne Versionserhöhung
├── prepare-github-release.bat / .sh  # Erstellt eine privat signierte, stabile Release-APK ohne Versionserhöhung
├── publish-github-release.ps1 / .sh  # Nur lokal: veröffentlicht die APK aus dist/ als GitHub Release
├── bump_manifest_version.py      # Synchronisiert die Version von hydra-umc.project.json mit der nativen (--sync)
├── bump_version_code.py          # Erhöht den eigenen versionCode-Zähler von Android in app/version.properties
├── gradlew, gradlew.bat          # Gradle-Wrapper
├── build.gradle.kts, settings.gradle.kts, gradle.properties   # Root-Konfiguration des Gradle-Projekts
├── local.properties              # Lokaler Pfad des Android SDK (maschinenspezifisch, nicht eingecheckt)
├── keystore.properties.example   # Vorlage für die private Release-Signierkonfiguration
├── .env.example                  # Beispiel für Umgebungsvariablen
├── metadata.json                 # App-Store-Eintragsmetadaten (Name/Beschreibung)
├── README.md                     # Diese Datei
├── README_spa.md / README_ita.md / README_fra.md / README_deu.md / README_zho.md / README_jpn.md   # Übersetzungen
└── LICENSE                       # GPL-3.0
```

## 🔗 Verwandte Projekte

Dieses Projekt ist Teil des HYDRA-UMC-Robotik-Ökosystems desselben Autors (JuanenRac / Electro Hobby 3D). Gut zu wissen, da eine Anfrage eigentlich eines dieser Projekte betreffen könnte statt dieses Repositorys.

**Übergeordnetes Projekt**
- **[HYDRA-UMC-SERVER](https://github.com/JuanenRac/HYDRA-UMC-SERVER)** — das reale Headless-Backend (REST/WebSocket), mit dem jeder Steuerungsclient tatsächlich spricht; das Backend, gegen das die eigene Erkennung, Authentifizierung und WebSocket-Synchronisierung dieser App laufen.

**Geschwisterprojekte** — sprechen ebenfalls mit der eigenen API von HYDRA-UMC-SERVER, jeweils als eigener Client
- **[HYDRA-UMC-STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO)** — Web-Steuerungs-Dashboard mit Echtzeit-3D-Visualisierung mehrerer Roboter; sein eigener 3D-Viewport ist direkt im 3D-Ansicht-Bildschirm dieser App per WebView eingebettet.
- **[HYDRA-UMC-SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE)** — Desktop-Schwarmleitstand (PySide6) für mehrere Server gleichzeitig, verpackt als eigenständige ausführbare Datei; spricht genau denselben `REMOTE_API.md`-Vertrag wie diese App.
- **[HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL)** — iOS/iPadOS-Steuerungs-App (Flutter) mit Echtzeit-WebSocket-Synchronisierung; das direkte iOS/iPadOS-Gegenstück dieser App, mit demselben Funktionsumfang.
- **[HYDRA-UMC-DSI](https://github.com/JuanenRac/HYDRA-UMC-DSI)** — native Touch-UI für das eingebaute 7"-DSI-Touchscreen, direkt auf dem CM5 eingebettet.
- **[HYDRA-UMC-BRIDGE-AMR](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-AMR)** — Koordinationsschranke für AGV-/AMR-Flotten über einen echten VDA-5050-MQTT-Publisher.
- **[HYDRA-UMC-BRIDGE-CNC](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-CNC)** — High-Level-Koordinator für CNC-Zellen mit echtem GRBL-Status-/Steuerbyte-Zugriff.
- **[HYDRA-UMC-BRIDGE-DROIDS](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-DROIDS)** — Koordinationsschranke für laufende/humanoide Droiden, mit einem echten Boston-Dynamics-Spot-Befehlssender.
- **[HYDRA-UMC-BRIDGE-LASER](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-LASER)** — Sicherheitskoordinator für Laserzellen, liest 3 echte Schlüssel-/Gehäuse-/Verriegelungs-GPIO-Sicherungen.
- **[HYDRA-UMC-BRIDGE-OPENPNP](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-OPENPNP)** — sicherer High-Level-Koordinator für den Leiterplattenfluss von OpenPnP Pick-and-Place.
- **[HYDRA-UMC-BRIDGE-PRINTER3D](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-PRINTER3D)** — sichere Koordinationsschranke für Moonraker/Klipper-3D-Drucker, mit echten gesicherten Job-Befehlen.
- **[HYDRA-UMC-BRIDGE-ROS2](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-ROS2)** — Sicherheitskoordinator mit einem echten, träge importierten rclpy-ROS-2-Transport.
- **[HYDRA-UMC-BRIDGE-UAV](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-UAV)** — Koordinationsschranke für kameraausgestattete UAVs, mit einem echten MAVLink-Befehlssender.

**Direkt verwandt**
- **[HYDRA-UMC-WATCH](https://github.com/JuanenRac/HYDRA-UMC-WATCH)** — WearOS-Begleit-App mit echten haptischen Alarmen und einem Sprach-Relay zum gekoppelten Telefon; die WearOS-Begleit-App zu dieser App, für Roboterstatus und -steuerung auf einen Blick vom Handgelenk aus.
- **[HYDRA-UMC-HIL-BRIDGE](https://github.com/JuanenRac/HYDRA-UMC-HIL-BRIDGE)** — echte Hardware-in-the-Loop-Sicherheitsverriegelung, die Befehle zwischen Simulation und echter Hardware routet; ermöglicht die Fernsteuerung des digitalen Zwillings direkt aus dieser App.

**Ebenfalls Teil des Ökosystems**

*Kern-Hardware & Plattform*
- **[HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC)** — das physische Motherboard des Roboterarms: CM5-Host + Dual-Core-STM32H745, koordiniert bis zu 8 Werkzeugarme über CAN-OTA/SPI-OTA.
- **[HYDRA-UMC-OS](https://github.com/JuanenRac/HYDRA-UMC-OS)** — reproduzierbare Raspberry-Pi-OS-Produktschicht für den CM5: schreibgeschützter Agent, validierte Konfiguration/Profile, WiFi-Ersteinrichtung.
- **[HYDRA-UMC-SDK](https://github.com/JuanenRac/HYDRA-UMC-SDK)** — der gemeinsame JSON-Schema-Vertrag und die Sicherheitsschranke, gegen die jede Bridge ihre Befehle validiert.

*Kern-Backend & Clients*
- **[HYDRA-UMC-EDITOR-URDF](https://github.com/JuanenRac/HYDRA-UMC-EDITOR-URDF)** — grafischer Desktop-URDF-Ersteller/-Editor, der fertige Modelle in STUDIOs eigenen Katalog überträgt.

*URTC-Werkzeugplattform*
- **[URTC](https://github.com/JuanenRac/URTC)** — Firmware für die physische Universal-Robot-Tool-Controller-Platine, 25+ Werkzeugprofile über CAN-Bus.
- **[URTC-FLASHER](https://github.com/JuanenRac/URTC-FLASHER)** — Desktop-GUI-Flash-Tool für URTC-Platinen, CAN-OTA plus Full-Chip-SWD/JTAG.
- **[URTC-TESTER](https://github.com/JuanenRac/URTC-TESTER)** — Desktop-Live-CAN-Bus-Diagnosetool für URTC-Platinen, ein Panel pro Werkzeugprofil.
- **[URTC-WEB-STUDIO](https://github.com/JuanenRac/URTC-WEB-STUDIO)** — browserbasierte Alternative zu URTC-TESTER über die Web-Serial-API, ohne lokale Installation.

*Vision-KI-Knoten (Hailo-8)*
- **[HYDRA-UMC-VISION-NODE](https://github.com/JuanenRac/HYDRA-UMC-VISION-NODE)** — Integrationsknoten für die Hailo-8-Vision-Pipeline, mit einer echten stufenweisen Hardware-Bereitschaftsprüfung.
- **[HYDRA-UMC-DETECTION-HEF](https://github.com/JuanenRac/HYDRA-UMC-DETECTION-HEF)** — echte Registry für kompilierte Modelle mit Hailo-Architektur-/Prüfsummen-Safe-Load-Verifizierung.
- **[HYDRA-UMC-VISION-STREAMER](https://github.com/JuanenRac/HYDRA-UMC-VISION-STREAMER)** — echter GStreamer-Pipeline- + MediaMTX-Konfigurationsgenerator mit einer echten HailoRT-Integrationsschranke.
- **[HYDRA-UMC-VISUAL-SERVOING-API](https://github.com/JuanenRac/HYDRA-UMC-VISUAL-SERVOING-API)** — echtes Position-Based-Visual-Servoing-Korrekturgesetz, sicherheitsgesteuert nach vorgelagertem Zonenstatus.
- **[HYDRA-UMC-SAFETY-ZONES](https://github.com/JuanenRac/HYDRA-UMC-SAFETY-ZONES)** — echte Zonenverletzungsprüfung und E-STOP-Anforderung, mit erzwungener Kalibrierungsaktualität.

*Kognitiver KI-Knoten (Hailo-10)*
- **[HYDRA-UMC-COGNITIVE-NODE](https://github.com/JuanenRac/HYDRA-UMC-COGNITIVE-NODE)** — Integrationsknoten für die Hailo-10-Cognitive-Pipeline (LLM-/VLA-/Sprach-Orchestrierung).
- **[HYDRA-UMC-VLA-ENGINE](https://github.com/JuanenRac/HYDRA-UMC-VLA-ENGINE)** — echte Aktions-Token-Kodierung/-Dekodierung und Trajektoriengenerierung für ein Vision-Language-Action-Modell.
- **[HYDRA-UMC-VOICE-UI](https://github.com/JuanenRac/HYDRA-UMC-VOICE-UI)** — echtes Sprach-Frontend (VAD + Intent-Parser) mit einem begrenzten, bestätigungsgesicherten Watch-Relay.
- **[HYDRA-UMC-SEMANTIC-PLANNER](https://github.com/JuanenRac/HYDRA-UMC-SEMANTIC-PLANNER)** — echte regelbasierte Aufgabenzerlegung und semantische Fehlerbehebung über MCU-Fehlercodes.
- **[HYDRA-UMC-DOCS-QA](https://github.com/JuanenRac/HYDRA-UMC-DOCS-QA)** — echte, nur auf der Standardbibliothek basierende TF-IDF-Dokumentensuche über die eigenen Markdown-Dokumente dieses Ökosystems.

*Orchestrierung & Schwarm*
- **[HYDRA-UMC-ORCHESTRATOR](https://github.com/JuanenRac/HYDRA-UMC-ORCHESTRATOR)** — Integrationsknoten mit einem echten gRPC/Protobuf-Health-Report-Vertrag und einer Missions-Zustandsmaschine.
- **[HYDRA-UMC-JOB-DISPATCHER](https://github.com/JuanenRac/HYDRA-UMC-JOB-DISPATCHER)** — echte prioritätsbasierte Job-Queue mit Deduplizierung, über eine echte HTTP-API.
- **[HYDRA-UMC-NODE-HEALING](https://github.com/JuanenRac/HYDRA-UMC-NODE-HEALING)** — echter gRPC-basierter Flotten-Health-Watchdog mit Retry/Backoff und Identitäts-Mismatch-Erkennung.
- **[HYDRA-UMC-PATH-PLANNER-3D](https://github.com/JuanenRac/HYDRA-UMC-PATH-PLANNER-3D)** — echter RRT-basierter 3D-Pfadplaner mit echter Hindernis-/Arbeitsraum-Kollisionsvalidierung.
- **[HYDRA-UMC-SWARM-SYNC](https://github.com/JuanenRac/HYDRA-UMC-SWARM-SYNC)** — echte CRDT-LWW-Element-Map-Zustandssynchronisation, eigenschaftsgetestet auf Multi-Zellen-Konvergenz.

*Digitaler Zwilling & Simulation*
- **[HYDRA-UMC-TWIN](https://github.com/JuanenRac/HYDRA-UMC-TWIN)** — Integrationsknoten für die Digital-Twin-Engine, mit einem echten Versionskompatibilitäts-Sync-Vertrag.
- **[HYDRA-UMC-PHYSICS-REPLICA](https://github.com/JuanenRac/HYDRA-UMC-PHYSICS-REPLICA)** — echte Vorwärtskinematik und Gelenkgrenzenvalidierung über eine echte URDF-Teilmenge.
- **[HYDRA-UMC-SYNTHETIC-DATA-GEN](https://github.com/JuanenRac/HYDRA-UMC-SYNTHETIC-DATA-GEN)** — echter prozeduraler 2D-Szenengenerator mit YOLO/COCO-Annotationsexport.

*Daten & Analytik*
- **[HYDRA-UMC-DATALAKE](https://github.com/JuanenRac/HYDRA-UMC-DATALAKE)** — echter sqlite3-gestützter Zeitreihenspeicher mit einer echten Ingest-/Abfrage-HTTP-API.
- **[HYDRA-UMC-ANOMALY-DETECTOR](https://github.com/JuanenRac/HYDRA-UMC-ANOMALY-DETECTOR)** — echter FFT- + statistischer Basislinien-Anomaliedetektor mit Drift-Überwachung.
- **[HYDRA-UMC-PRODUCTION-REPORTS](https://github.com/JuanenRac/HYDRA-UMC-PRODUCTION-REPORTS)** — echte OEE-/Verfügbarkeitsberechnung über den DATALAKE-Verlauf, mit reproduzierbarem CSV-Export.
- **[HYDRA-UMC-TELEMETRY-COLLECTOR](https://github.com/JuanenRac/HYDRA-UMC-TELEMETRY-COLLECTOR)** — echte CAN/WebSocket-Ingestion-Pipeline in DATALAKE, mit Sequenz-Deduplizierung.

*Industrie-Gateway*
- **[HYDRA-UMC-GATEWAY-INDUSTRIAL](https://github.com/JuanenRac/HYDRA-UMC-GATEWAY-INDUSTRIAL)** — Integrationsknoten, der zu Industrieprotokollen weiterleitet, mit einer echten Befehls-Allowlist-/Backpressure-Schicht.
- **[HYDRA-UMC-OPCUA-SERVER](https://github.com/JuanenRac/HYDRA-UMC-OPCUA-SERVER)** — echter OPC-UA-Adressraum, verifiziert mit einer echten Binärprotokoll-Client-Session.
- **[HYDRA-UMC-MQTT-BROKER](https://github.com/JuanenRac/HYDRA-UMC-MQTT-BROKER)** — echter MQTT-Broker mit optionaler Pro-Client-Authentifizierung und Topic-ACLs.
- **[HYDRA-UMC-MTCONNECT-ADAPTER](https://github.com/JuanenRac/HYDRA-UMC-MTCONNECT-ADAPTER)** — echte MTConnect-`/probe`- und `/current`-XML-Endpunkte mit Degraded-Mode-Ausgabe.

*Ergänzende Tools & Ökosystembetrieb*
- **[HYDRA-UMC-DASHBOARD-AI](https://github.com/JuanenRac/HYDRA-UMC-DASHBOARD-AI)** — Smart-Summaries- und Anomaly-Highlighting-Panels über DATALAKE/ANOMALY-DETECTOR, mit einem ehrlichen statistischen Fallback.
- **[HYDRA-UMC-TOOL-CLI](https://github.com/JuanenRac/HYDRA-UMC-TOOL-CLI)** — Flotten-CLI mit einem echten, stabilen Exit-Code-Vertrag, ein echter Live-Client der eigenen API von HYDRA-UMC-SERVER.
- **[URTC-SMART-RACK](https://github.com/JuanenRac/URTC-SMART-RACK)** — Firmware für ein Platinenmontagegestell mit echter Werkzeug-ID-Dekodierung und Smart-Idle-Vorheizlogik.
- **[URTC-VISION-TOOL](https://github.com/JuanenRac/URTC-VISION-TOOL)** — Firmware plus ein echter Python-Vision-Begleiter für einen Thermal-/RGB-Inspektionswerkzeugkopf.
- **[HYDRA-UMC-UPDATER](https://github.com/JuanenRac/HYDRA-UMC-UPDATER)** — administratives Desktop-Tool, das jedes Repository in diesem Ökosystem entdeckt, klont und aktualisiert.

---

## 📚 Dokumentation & Community

- **[CONTRIBUTING.md](CONTRIBUTING.md)** — Technologie-Stack und Coding-Richtlinien für einen Pull Request.
- **[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)** — die in dieser Community erwarteten Verhaltensstandards.
- **[SECURITY.md](SECURITY.md)** — wie man eine Schwachstelle meldet, und die echten Sicherheitsschwerpunkte dieses Projekts.
- **[SUPPORT.md](SUPPORT.md)** — wo man Fragen stellt und Fehler meldet.
- **[LICENSE.md](LICENSE.md)** — die eigene Lizenz dieses Projekts.

## 👤 AUTOR
**JuanenRac** (Electro Hobby 3D)
📧 electrohobby3d@gmail.com
📺 [youtube.com/@electrohobby3d](https://youtube.com/@electrohobby3d)

## 📜 LIZENZ

**GNU General Public License v3.0 (GPL-3.0)** für den Quellcode - siehe [`LICENSE`](LICENSE).

Diese Dokumentation (dieses README und seine eigenen Übersetzungen - `README_spa.md`, `README_ita.md`, `README_fra.md`, `README_deu.md`, `README_zho.md`, `README_jpn.md`) steht unter der **Creative Commons Attribution-ShareAlike 4.0 International (CC BY-SA 4.0)**-Lizenz. Vollständiger Text unter https://creativecommons.org/licenses/by-sa/4.0/.
