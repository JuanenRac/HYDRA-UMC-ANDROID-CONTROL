<p align="center">
  <img src="images/HYDRA_UMC_ANDROID_CONTROL_BANNER.jpg" alt="HYDRA-UMC Android Control Banner" width="100%">
</p>

# 📱 HYDRA-UMC CONTROL

Eine native Android-App (Kotlin + Jetpack Compose), die einen Roboter auf der [HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC)-Plattform über Wi-Fi oder Bluetooth steuert und dabei genau denselben [`REMOTE_API.md`](https://github.com/JuanenRac/HYDRA-UMC-STUDIO/blob/main/docs/REMOTE_API.md)-Vertrag spricht, den auch [HYDRA-UMC SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE) verwendet - Discovery, vollständiges Lesen/Schreiben des Zustands und Live-Synchronisation per WebSocket gegen einen laufenden [HYDRA-UMC STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO)-Server. Direktes Android-Gegenstück zu [HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL). Siehe [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) für das vollständige Design.

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
- **Atomare Befehlssynchronisation** (das eigene `sendAtomicCommand()` von `viewmodel/RobotViewModel.kt`) - Jeder Schreibvorgang (enable/disable/play/pause/stop/jog/valve/pump/speed/vision) sendet einen kleinen, atomaren Befehl für einen einzelnen Roboter statt des gesamten Einstellungsbaums - der Server berechnet, welche kombinierten Roboter ebenfalls betroffen sind, speichert auf der Festplatte und sendet von sich aus an jeden anderen verbundenen Client. Enable/Disable wird auf die eigenen `combinedWith`-Geschwister eines Roboters genauso propagiert wie Play/Pause/Stop, da alle dieselbe Berechnung der betroffenen Roboter teilen.
- **Notfallmanagement-Widget** (`widget/GlobalStopWidget.kt`) - Dediziertes **Homescreen-Widget** für kritische Sicherheit. Bietet eine gut sichtbare, sofort zugängliche **globale E-STOP**-Taste, um alle Roboteroperationen im Schwarm einzufrieren, ohne die App öffnen zu müssen - wartet zuverlässig darauf, dass die Roboterliste tatsächlich geladen ist, bevor gehandelt wird, selbst nach einem vollständigen Kaltstart (Prozess läuft noch nicht).
- **Industrielle Haptik & Sicherheit** (`ui/ControlScreen.kt`) - Fortschrittliches sensorisches Feedback-System. Verfügt über einen echten **Schutz vor langem Drücken** an den Tasten E-STOP und STOP (ein schnelles Antippen bewirkt nichts außer einem kurzen Brummen + Hinweis; nur ein echtes Halten sendet den Befehl) sowie differenzierte haptische Signaturen (Erfolgs-, Fehler- und Notfall-Impulse), um dem Bediener in lauten Umgebungen eine physische Bestätigung zu geben.
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

## 📲 Testen gegen HYDRA-UMC STUDIO

1. Server ausführen: `cd HYDRA-UMC-STUDIO && npm run dev` (Port 3000).
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
│   └── src/main/
│       ├── AndroidManifest.xml   # Berechtigungen, Activity-/Receiver-Deklarationen, usesCleartextTraffic (reiner HTTP-LAN-Server, kein TLS)
│       ├── java/com/hydraumc/control/
│       │   ├── MainActivity.kt          # Einstiegspunkt - Splash, Login-/Hauptbildschirm-Zugriffssteuerung, kaltstartsichere globale E-STOP-Behandlung
│       │   ├── MainScreen.kt            # Unteres Navigations-Grundgerüst, obere Leiste (Serverauswahl, Profil, Telemetrie, Einstellungen)
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
│       │   │   ├── LoginScreen.kt          # Eingabe von Benutzername/Passwort + IP/Port, biometrischer Login
│       │   │   ├── MjpegPlayer.kt          # MJPEG-Stream-Parser + Canvas-Renderer
│       │   │   ├── NativeThreeDScreen.kt   # Nativer 3D-Viewer mit Google Filament - noch nicht in die Navigation eingebunden, keine .glb-Pipeline
│       │   │   ├── SettingsScreen.kt       # UI für Wi-Fi-/Bluetooth-Scan, Verbindungseinstellungen
│       │   │   ├── SplashScreen.kt         # Benutzerdefinierter Compose-Splashscreen
│       │   │   ├── TelemetryScreen.kt      # Terminal-artiger Viewer für Ereignis-/Synchronisationsprotokolle
│       │   │   ├── ThreeDScreen.kt         # Echtes 3D-Viewport - WebView, die die eigene headless 3D-Szene von STUDIO einbettet
│       │   │   ├── UserProfileDialog.kt    # Dialog zur Profilbearbeitung + biometrischer Schalter
│       │   │   └── theme/
│       │   │       ├── Color.kt, Theme.kt, Typography.kt   # Material-3-Farbschema, Theme-Wrapper, Typografie-Skala
│       │   │       └── HydraButton.kt, IndustrialComponents.kt, IndustrialStyle.kt   # Gemeinsame UI-Bausteine im Industriestil
│       │   ├── util/
│       │   │   ├── BiometricHelper.kt      # Wrapper für den androidx.biometric-Prompt
│       │   │   └── NotificationHelper.kt   # Push-Benachrichtigungen zu abgeschlossenen Jobs/Sicherheit
│       │   ├── viewmodel/
│       │   │   └── RobotViewModel.kt   # Gemeinsames ViewModel - Netzwerk, Authentifizierung, Discovery, Versand atomarer Befehle, gesamter UI-Zustand
│       │   └── widget/
│       │       └── GlobalStopWidget.kt # Homescreen-Widget für einen globalen E-STOP ohne Öffnen der App
│       └── res/
│           ├── drawable/, layout/, mipmap*/, xml/   # Icons, Widget-Layout, Launcher-Icons, Backup-/Datenextraktionsregeln
│           └── values/, values-es/, values-de/, values-fr/, values-it/   # Zeichenketten in 5 Sprachen, Farben, Theme
├── docs/
│   └── ARCHITECTURE.md           # Design-/Architekturnotizen
├── images/                       # Quellressourcen für das README-Banner + den Splashscreen
├── build-android.bat / .sh       # Komfortskripte für Build + adb-Installation in einem Schritt
├── gradlew, gradlew.bat          # Gradle-Wrapper
├── build.gradle.kts, settings.gradle.kts, gradle.properties   # Root-Konfiguration des Gradle-Projekts
├── local.properties              # Lokaler Pfad des Android SDK (maschinenspezifisch, nicht eingecheckt)
├── .env.example                  # Beispiel für Umgebungsvariablen
├── README.md                     # Diese Datei
├── README_spa.md / README_ita.md / README_fra.md / README_deu.md   # Übersetzungen
└── LICENSE                       # GPL-3.0
```

## 🔗 Verwandte Projekte

Dieses Projekt ist Teil eines größeren Robotik-Ökosystems desselben Autors (JuanenRac / Electro Hobby 3D). Gut zu wissen, da eine Anfrage sich eigentlich auf eines dieser Projekte statt auf dieses Repository beziehen könnte:

**HYDRA-UMC-Plattform** — die Multi-Roboter-Mikrofabrikzelle
- **[HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC)** — das Motherboard selbst: Raspberry-Pi-CM5-Host + dualer STM32H745-Echtzeit-Co-Prozessor, der bis zu 8 verteilte Roboterarme über CAN-OTA/SPI-OTA orchestriert. Eigene Hardware + Firmware, GPL-3.0/CERN-OHL-S v2/CC BY-SA 4.0.
- **[HYDRA-UMC STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO)** — webbasiertes Steuerungs-Dashboard für HYDRA-UMC: Multi-Roboter-3D-Visualisierung, Kinematik-/Trajektorienaufzeichnung, CAN-OTA-Flashing und -Tests für die gesamte Plattform. React + Vite + Three.js.
- **HYDRA-UMC-ANDROID-CONTROL** *(dieses Repository)* — Android-Steuerungs-App für HYDRA-UMC über Wi-Fi/Bluetooth. Echte, funktionierende App - vollständiger Funktionsumfang zur Fernsteuerung, JWT-Authentifizierung, verschlüsselte Anmeldedatenspeicherung.
- **[HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL)** — iOS-/iPadOS-Steuerungs-App für HYDRA-UMC über Wi-Fi, entwickelt in Flutter (plattformübergreifend, unter Windows ohne Mac überprüfbar; die endgültige `.ipa`-Paketierung benötigt weiterhin Xcode). Echte, funktionierende App - derselbe Funktionsumfang wie die Android-App.
- **[HYDRA-UMC-SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE)** — Desktop-Kommandozentrale (Python/PySide6) für den Schwarm: Netzwerkerkennung mehrerer Controller, bidirektionale Live-Synchronisation, echtes 3D-Roboter-Viewport, andockbarer Arbeitsbereich im Photoshop-Stil. Echt und funktionsfähig, kein Platzhalter.
- **[HYDRA-UMC-EDITOR-URDF](https://github.com/JuanenRac/HYDRA-UMC-EDITOR-URDF)** — grafischer Desktop-URDF-Ersteller/-Editor (Python/PySide6) für den eigenen Modellkatalog dieses Projekts: zieht Quelldateien von GitHub oder einem lokalen Ordner, validiert die Machbarkeit der Freiheitsgrade (DOF), bearbeitet Farbe/Skalierung/Kinematik mit einer Live-3D-Vorschau und überträgt das fertige Ergebnis an einen laufenden STUDIO-Server. Echt und funktionsfähig, kein Platzhalter.
- **[HYDRA-UMC-DSI](https://github.com/JuanenRac/HYDRA-UMC-DSI)** — geplant: eine native Touch-Oberfläche für den eigenen 5"/7"-DSI-Touchscreen (1280×720, gleiche Auflösung bei beiden Größen) von HYDRA-UMC auf der Compute Module 5, der diesen selben Server direkt von der Platine aus steuert. Noch nicht begonnen.

**URTC-Plattform** — der Werkzeugkopf-Controller, den jeder HYDRA-UMC-Roboterarm mitführt
- **[URTC](https://github.com/JuanenRac/URTC)** — Universal Robot Tool Controller: STM32F303-basierter CAN-Bus-Werkzeugkopf-Controller, 25 vollständig implementierte Werkzeugprofile, CAN-OTA-Firmware-Update.
- **[URTC Flasher](https://github.com/JuanenRac/URTC-FLASHER)** — Desktop-Tool für CAN-OTA- + Full-Chip-SWD/JTAG-Flashing für URTC-Boards (Windows/Linux).
- **[URTC Tester](https://github.com/JuanenRac/URTC-TESTER)** — Desktop-Tool für Live-CAN-Bus-Diagnose von URTC-Boards, ein Panel pro Werkzeugprofil (Windows/Linux).
- **[URTC Web Studio](https://github.com/JuanenRac/URTC-WEB-STUDIO)** — browserbasierte Alternative zu den 2 oben genannten Desktop-Tools (Web Serial API + SLCAN), keine lokale Installation erforderlich.

## 👤 Autor

**JuanenRac** (Electro Hobby 3D)
📧 electrohobby3d@gmail.com
📺 youtube.com/@electrohobby3d

## 📜 Lizenz

**GNU General Public License v3.0 (GPL-3.0)** für den Quellcode - siehe [`LICENSE`](LICENSE).

Diese Dokumentation (dieses README und seine eigenen Übersetzungen - `README_spa.md`, `README_ita.md`, `README_fra.md`, `README_deu.md`) steht unter der **Creative Commons Attribution-ShareAlike 4.0 International (CC BY-SA 4.0)**-Lizenz. Vollständiger Text unter https://creativecommons.org/licenses/by-sa/4.0/.
