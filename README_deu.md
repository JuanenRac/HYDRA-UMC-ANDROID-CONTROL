<!-- =============================================================================
HYDRA-UMC-ANDROID-CONTROL - Android operator console for a HYDRA-UMC server: control, cameras, 3D view and a global STOP
Copyright (C) JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
GPL-3.0-or-later - see LICENSE
============================================================================= -->

<p align="center">
  <img src="images/HYDRA_UMC_BANNER.svg" alt="HYDRA-UMC-ANDROID-CONTROL banner" width="100%">
</p>

# 📱 HYDRA-UMC-ANDROID-CONTROL

<p align="center"><a href="README.md">🇺🇸 English</a> | <a href="README_spa.md">🇪🇸 Español</a> | <a href="README_fra.md">🇫🇷 Français</a> | <a href="README_ita.md">🇮🇹 Italiano</a> | 🇩🇪 <b>Deutsch</b> | <a href="README_zho.md">🇨🇳 简体中文</a> | <a href="README_jpn.md">🇯🇵 日本語</a> | <a href="README_por.md">🇵🇹 Português</a></p>

### Ihre HYDRA-UMC-Roboter in der Tasche: den Server finden, die Roboter bewegen und beobachten und mit einem roten Knopf alles stoppen.

<p align="center">
  <img src="https://img.shields.io/badge/License-GPL%203.0--or--later-blue.svg" alt="GPL 3.0 or later">
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF.svg" alt="Language">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg" alt="UI">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84.svg" alt="Platform">
  <img src="https://img.shields.io/badge/Tests-303%20passing-2ea44f.svg" alt="Tests">
  <img src="https://img.shields.io/badge/Version-0.7.2-0f6cbd.svg" alt="Version">
  <img src="https://img.shields.io/badge/Maturity-functional-0F6CBD.svg" alt="Maturity">
</p>

---

Eine native Android-App (Kotlin + Jetpack Compose), die einen Roboter auf der [HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC)-Plattform über Wi-Fi oder Bluetooth steuert und dabei genau denselben [`REMOTE_API.md`](https://github.com/JuanenRac/HYDRA-UMC-SERVER/blob/main/docs/REMOTE_API.md)-Vertrag spricht, den auch [HYDRA-UMC SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE) verwendet - Discovery, vollständiges Lesen/Schreiben des Zustands und Live-Synchronisation per WebSocket gegen ein laufendes [HYDRA-UMC-SERVER](https://github.com/JuanenRac/HYDRA-UMC-SERVER)-Backend (dasselbe, mit dem auch das eigene Web-Dashboard von [HYDRA-UMC STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO) spricht). Direktes Android-Gegenstück zu [HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL). Siehe [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) für das vollständige Design.

> **Ehrlichkeitscheck - was heute wirklich läuft:** die zentrale Geschäftslogik - Parol6-Kinematik (`kinematics/Parol6Kinematics.kt`), der Settings-Baum-Zustandsvertrag (`model/HydraState.kt`), atomare Befehlszustellung und das Sprach-Relay (`viewmodel/RobotViewModel.kt`), WebSocket-Reconnect-Handling (`network/HydraWebSocket.kt`), MJPEG-Frame-Parsing (`ui/MjpegPlayer.kt`), der In-App-Update-Kanal (`update/GitHubReleaseUpdater.kt`, `ReleaseMetadataParser.kt`, `SemanticVersion.kt`) und die Wear-Sprach-Relay-Anfrageverwaltung (`wear/BoundedRequestScope.kt`, `WatchCompanionProtocol.kt`) sind real und durch 303 bestehende JVM/Robolectric-Unit-Tests abgedeckt (`./gradlew testDebugUnitTest`). Was diese Tests strukturell nicht erreichen können - und was diese reine Windows-Entwicklungsumgebung mangels physischem Android-Gerät nicht verifizieren kann - ist das echte Keystore-gestützte `EncryptedSharedPreferences` von `network/AuthPrefs.kt`, der `androidx.biometric`-Fingerabdruck-/Gesichts-Prompt, der Bluetooth-GATT-Transport von `network/HydraBleClient.kt`, und der API-28+-Hardware-`ImageDecoder`-Pfad in `MjpegStreamParser`: echter, kompilierter Code, der seit seiner Erstellung nicht erneut auf echter Hardware verifiziert wurde. Grundlegendes Starten/Login *wurde* für frühere Releases live auf einem echten Gerät bestätigt (siehe die Einträge `v0.3.2`/`v0.3.3` in `CHANGELOG.md`), das liegt aber vor Bluetooth, Biometrie und dem Hardware-Decode-Pfad. Siehe `CHANGELOG.md` für genau das, was bereits ausgeliefert wurde, samt eigener Hinweise pro Eintrag, was hardwareseitig noch unverifiziert bleibt.

> **Stand von Sicherheit (Safety) und Security:** Dieses Ökosystem ist kein zertifiziertes Sicherheitssystem. Es gibt noch keinen Hardware-E-STOP-Pfad, keine von der Firmware durchgesetzten Grenzen oder Verriegelungen und keinen verifizierten Bewegungsbefehl; Firmware-Images werden mit CRC32 und einem HMAC-SHA256 mit gemeinsamem Schlüssel geprüft, nicht mit einer asymmetrischen Signatur; TLS und gegenseitiges TLS sind optional und standardmäßig aus; noch keine eigene Platine wurde bestückt oder getestet. Lesen Sie die [Sicherheitsbasis](https://github.com/JuanenRac/HYDRA-UMC-DOCS/blob/main/docs/SECURITY_BASELINE.md) und das [Bedrohungsmodell](https://github.com/JuanenRac/HYDRA-UMC-DOCS/blob/main/docs/THREAT_MODEL.md), bevor Sie sich für die Sicherheit einer Person auf eine Aussage hier verlassen.

---

## 📋 Auf einen Blick

| | |
|---|---|
| Version | `0.7.2` |
| Reifegrad | Funktionsfähig |
| Tests | 303 (`./gradlew testDebugUnitTest`) |
| Technik | Kotlin / Jetpack / biometric auth / WebSocket client |
| Ziel | Ein Android-Telefon oder -Tablet |
| Auf echter Hardware | Noch nicht: für diese Version ist kein Lauf auf echter Hardware vermerkt |

**Springe zu:** [Überblick](#-überblick) · [Architektur](#-architektur) · [Funktionen](#-funktionen) · [Sicherheit und Schutz](#-sicherheit-und-schutz) · [Schnittstellen](#-schnittstellen) · [Konfiguration](#-konfiguration) · [Schnellstart](#-schnellstart) · [Tests](#-tests) · [Fehlerbehebung](#-fehlerbehebung) · [Struktur des Repositorys](#-struktur-des-repositorys) · [Verwandte Projekte](#-verwandte-projekte)

## 🚦 Projektstatus

Jede Zeile ist ein Teil dieses Repositorys, und die Ampel sagt, wie weit er heute ist. Sie beruht auf dem, was das Repository zeigen kann (Tests, Builds, Läufe), nicht auf dem, was geplant ist.

🟢 Fertig und überprüft · 🟡 Funktioniert, mit bekannten Grenzen · 🟠 Teilweise fertig oder noch nicht überprüft · 🔴 Nicht gemacht oder funktioniert nicht

| Bereich | Stand | Was das hier bedeutet |
|---|---|---|
| Software | 🟡 | Funktioniert für seinen Hauptzweck; die Ehrlichkeitsprüfung oben nennt, was noch fehlt |
| Tests | 🟢 | 303 automatische Tests, bei jeder Änderung ausgeführt |
| Echte Hardware | 🔴 | Noch nicht: für diese Version ist kein Lauf auf echter Hardware vermerkt |
| Dokumentation | 🟢 | README in acht Sprachen, bei jeder Änderung gegen das Manifest geprüft |

## 🎯 Überblick

Ansichten der App: **Anmeldung** (Serveradresse, Passwort, Fingerabdruck oder Gesicht), **Übersicht** (ein 3D-Karussell der Roboter mit Modulmatrix und Zustand des Rechenmoduls), **Steuerung** (Joysticks, Start, Pause, Stopp, Reservierung des Roboters), **Kameras** (MJPEG live mit Bild-in-Bild), **3D-Ansicht** (die echte STUDIO-Szene), **Telemetrie** (ein Protokoll im Terminalstil) und **Einstellungen** (Verbindung, Sicherheit, Benachrichtigungen, Updates). Ein roter Knopf **Alles stoppen** steht in der oberen Leiste jeder Ansicht und auf einem Startbildschirm-Widget.

## 🧩 Rolle in HYDRA-UMC

**HYDRA-UMC-ANDROID-CONTROL** ist die native Android-Bedienkonsole von HYDRA-UMC: Sie findet einen HYDRA-UMC-SERVER im lokalen Netz, meldet sich an und zeigt dann die Roboter, bewegt sie, überwacht ihre Kameras und eine 3D-Ansicht und stoppt mit einem Tipp alles. Sie spricht denselben Fernsteuerungsvertrag wie die SUITE und die iOS-App und behält eine Kopie des letzten Zustands für den Fall ohne WLAN. Sie ist kein Sicherheitsgerät.

## 🏛️ Architektur

<p align="center">
  <img src="images/architecture.svg" alt="HYDRA-UMC-ANDROID-CONTROL architecture" width="100%">
</p>

## ✨ Funktionen

- **Zugriffskontrolle & Biometrie** (`ui/LoginScreen.kt`, `util/BiometricHelper.kt`) - Professionelles Login-System mit Unterstützung für **Fingerabdruck und Face Unlock** (`androidx.biometric`), plus IP-/Port-Felder direkt auf demselben Bildschirm, damit ein Server angesteuert werden kann, ohne zuerst einen separaten Umweg über die Einstellungen zu nehmen. Enthält eine "Angemeldet bleiben"-Funktion, einen sicheren **Logout**-Mechanismus und ist vollständig in **8 Sprachen** lokalisiert. Der zwischengespeicherte Benutzername/das Passwort/Token (`network/AuthPrefs.kt`) liegen in Keystore-gestützten **EncryptedSharedPreferences** (AES256-GCM), nicht im Klartext - ein Entwicklungsserver (`NODE_ENV` nicht gesetzt) legt beim ersten Start ein Standardkonto `admin`/`admin` an; ein Produktionsserver (`NODE_ENV=production`) verlangt stattdessen `HYDRA_UMC_BOOTSTRAP_ADMIN_USERNAME`/`HYDRA_UMC_BOOTSTRAP_ADMIN_PASSWORD` und greift nie stillschweigend auf `admin`/`admin` zurück - siehe das eigene README von HYDRA-UMC-SERVER für die vollständige Produktionskonfiguration. Zusätzliche, weniger privilegierte **operator**-Konten lassen sich in beiden Fällen serverseitig unter Config > Users erstellen.
- **Offline-Modus & Zustands-Cache** (`network/StateCache.kt`) - Integrierte Persistenz-Engine mit **DataStore**. Die App speichert den zuletzt bekannten Systemzustand automatisch zwischen und ermöglicht so eine sofortige Dashboard-Ansicht und Konfigurationsprüfungen auch ohne aktive Wi-Fi-Verbindung.
- **Missionsbenachrichtigungen & Warnungen** (`util/NotificationHelper.kt`) - Alarmsystem. Sendet Push-Benachrichtigungen mit hoher Priorität, wenn ein Roboter eine Auftragssequenz abschließt oder wenn kritische Hardware-Ereignisse auftreten, sodass der Bediener auch dann informiert wird, wenn sich die App im Hintergrund befindet.
- **Industrielles Telemetrie-Terminal** (`ui/TelemetryScreen.kt`) - Ein eigener Echtzeit-Log-Viewer mit Terminal-artiger Oberfläche. Verfolgt Systemereignisse, REST/WebSocket-Synchronisation und liefert farblich codierte Diagnosen (Matrix-Grün für Erfolg, Industrie-Rot für Fehler).
- **Erweitertes Dashboard** (`ui/DashboardScreen.kt`) - Hochauflösendes **horizontales 3D-Karussell** mit Perspektiv-Wischeffekten. Zeigt angereicherte Robotermetadaten: **Hersteller** (Source Robotics, Annin, Universal Robots, AgileX usw.), **Roboterrolle** (CNC, Laser, PnP) und eine **industrielle Modulmatrix** mit Live-Status für die Module CAM, XY, ATC, PNP, CNC, LSR, BED, VAC und RCK.
- **Systemzustandsmonitor** (`ui/DashboardScreen.kt`) - Echtzeitmetriken für die verbundene Compute Module 5, einschließlich **Hostname**, **formatierter Betriebszeit** (z. B. "2d 4h 15m") und aktiver Anzahl von Controllern und Robotern.
- **Erweiterte manuelle Steuerung** (`ui/ControlScreen.kt`) - Bietet ein professionelles vertikales Layout mit **50 % größeren Joystick-Tasten** für maximale Präzision.
- **Wiedergabepanel** (`ui/ControlScreen.kt`, `ui/PlaybackConsole.kt`) - Feste untere Steuerleiste mit der roten **STOP**-Taste (dem globalen STOP unten) sowie den Tasten **Start**, **Pause** und **Stopp** (nur der gewählte Roboter). Diese Bedienelemente sind immer sichtbar und bieten **haptisches Feedback** zur physischen Bestätigung.
- **Globaler STOP** (`ui/GlobalStop.kt`, `network/HydraApiClient.kt`, `viewmodel/RobotViewModel.kt`) - Eine rote **STOP**-Taste (Beschriftung "Alle stoppen", in 8 Sprachen übersetzt) in der oberen Leiste jedes Bildschirms, in der Konsole des Steuerungsbildschirms und in der 3D-Ansicht (Hochformat und Vollbild). Ein Tipp, ohne Halten und ohne Bestätigung: Er sendet sofort `POST /api/stop-all` und hält alle Roboter an, auch wenn ein anderes Konto sie reserviert hat. Er zeigt das Ergebnis (welche Roboter angehalten wurden, welche nicht, welche von jemand anderem reserviert sind, und eine Warnung, wenn der Server den Stopp-Zustand nicht speichern konnte), versucht es einmal erneut, wenn niemand antwortet, hält die Roboter einzeln an, wenn der Server den Aufruf begrenzt (429), und zeigt ein dauerhaftes **STOP NICHT BESTÄTIGT**-Banner, bis ein späterer STOP gelingt. Das ist der Software-Stopp des Servers: kein Hardware-E-STOP, und er schaltet nichts spannungsfrei.
- **Reservierungen und Sicherheitsgrenzen** (`ui/AccessPanel.kt`, `network/AccessModels.kt`, `network/HydraApiClient.kt`, `viewmodel/RobotViewModel.kt`) - Der Control-Bildschirm zeigt, wer den gewählten Roboter hält (niemand, dieses Telefon, dasselbe Konto auf einem anderen Gerät oder ein anderes Konto), und bietet **Reservieren** und **Freigeben** sowie die Liste aller Reservierungen des Servers (`GET /api/claims`). Das Telefon behält pro Installation eine Controller-ID und sendet sie bei jeder Reservierung, Freigabe, Jog-Schaltung und jedem Befehl mit (Body und Header `X-Controller-Id`); die Reservierung gehört also dem Konto und diesem Telefon, nicht dem ganzen Konto. Die Ablehnungen des Servers werden unterschieden: von einem anderen Konto reserviert, von deinem Konto auf einem anderen Gerät oder Fenster, Controller-ID erforderlich und Jog nicht scharf. Die Grenzen des Robotermodells kommen von `GET /api/safety-limits`: Ein Jog-Schritt über dem Limit wird nicht gesendet, und die Warnungen des Servers werden angezeigt. Verlangt der Administrator es, muss **Jog scharfschalten** (mit Countdown) gedrückt werden, bevor der Joystick den Roboter bewegt; die Schaltung endet von selbst, mit STOP oder bei der Freigabe. Reservierungen sind optional: Ein Roboter, den niemand reserviert hat, wird wie bisher gesteuert. Nur für einen **Administrator** erscheint bei einem Roboter, den jemand anderes hält, eine Schaltfläche **Steuerung übernehmen**; sie fragt nach einer Bestätigung und reserviert den Roboter dann mit `force: true` (der Server akzeptiert das nur von Administratoren und prüft die Rolle erneut). Schreibzugriffe sind bedingt: jeder trägt die Revision des Roboters, die diese App zuletzt gesehen hat (`If-Match`, `network/ContractV2.kt`); hat inzwischen jemand anderes den Roboter geändert, antwortet der Server mit 412, nichts wird ausgeführt, der Roboter wird neu gelesen und die Meldung sagt es (Standard prüft Reservierungen, Tisch und einmalige Befehle; Streng prüft alles außer STOP; Aus prüft nichts; die Wahl steht in der Liste der Reservierungen; STOP wird nie geprüft). Der Jog des XY-Tischs ist ein einziger atomarer Befehl `table` (ein älterer Server bekommt die Jogs, die er versteht), der Server schickt die Reservierungen über den WebSocket, sodass der Control-Bildschirm nicht abfragt, solange sie ankommen, und Administratoren lesen das Audit-Protokoll (`ui/AuditDialog.kt`) mit zweifach geprüfter SHA-256-Kette, vom Server und auf dem Telefon, und können dessen Zeilen als `.jsonl` teilen. PLAY fragt vorher nach (Roboter, aufgezeichnete Punkte, Geschwindigkeit und wer ihn reserviert hat), solange man das bei den Reservierungen nicht abschaltet.
- **3D-Ansicht** (`ui/ThreeDScreen.kt`) - Bettet das eigene Echtzeit-3D-Viewport von HYDRA-UMC STUDIO in eine WebView ein (`?hideUI=true&robotId=#token=`), statt eines nativen Renderers. Der WebView-Ansatz erhält die echte, aktuell ausgelieferte STUDIO-3D-Szene (jedes echte Roboter-Mesh/jede echte Kinematik) kostenlos, statt sie nativ neu zu implementieren - der Kompromiss ist der WebView-Rendering-Overhead, nicht batterieoptimal, aber heute funktional vollständig.
- **Echtzeit-Oktal-Vision** (`ui/CameraScreen.kt`, `ui/MjpegPlayer.kt`) - **Nativer MJPEG-Streamer**. Verfügt über einen Hintergrund-Parser und einen Canvas-basierten Renderer für Video mit geringer Latenz, einen klaren "Kamera deaktiviert"-Zustand (statt eines stillschweigend leeren Feeds), wenn das Vision-System eines Roboters ausgeschaltet ist, sowie einen Schalter, um die Kamera eines Roboters direkt vom Server aus ein-/auszuschalten. Unterstützt automatische **Picture-in-Picture (PIP)**-Overlays im Bildschirm für manuelle Steuerung, die über die Kamerakonfiguration des Servers bestimmten Robotern zugeordnet werden. Ab API 28 wird jedes Bild über den eigenen hardwarebeschleunigten `ImageDecoder`-Pfad der Plattform dekodiert (ein GPU-residentes Bitmap, ohne die CPU-seitige Dekodierung/Übertragung), mit einem automatischen, selbstheilenden Fallback auf die ursprüngliche CPU-Dekodierung.
- **Intelligente Erkennung & Konnektivität** (`network/Discovery.kt`, `network/HydraApiClient.kt`, `network/HydraWebSocket.kt`) - Gleichzeitiger Subnetz-Scan gegen jeden Kandidaten-Host im eigenen /24 des Telefons (einschließlich der eigenen LAN-IP des Telefons und localhost, nicht nur der anderen Hosts), der `GET /api/hydra-info` abfragt und einen echten Server ausschließlich anhand des Vorhandenseins von `remoteApiVersion` identifiziert - dieselbe Prüfung, die auch der manuelle IP-Pfad verwendet, sodass ein Server, dessen Besitzer ihn von der Standard-Produktzeichenkette umbenannt hat, trotzdem gefunden wird. Ein **NsdManager**-Listener (mDNS/Bonjour) läuft parallel dazu - der Server kündigt sich tatsächlich als `_hydra._tcp` (`bonjour-service`) an, und `MainActivity` fordert die dafür nötige Laufzeit-Berechtigung für Standort/Geräte in der Nähe von vornherein an (eine Manifest-Deklaration allein gewährt sie ab API 23 nie) - aber der Subnetz-Scan bleibt der primäre Pfad, da die Multicast-Erreichbarkeit über Wi-Fi von Natur aus weniger zuverlässig ist als eine einfache HTTP-Abfrage. Die App aktiviert beim Start automatisch WiFi, scannt das lokale Fabriknetzwerk und führt eine **Zero-Click-Auto-Verbindung** zum ersten verfügbaren HYDRA-UMC-Server durch.
- **JWT-Zugriff** (`network/HydraApiClient.kt`, `ui/LoginScreen.kt`) - Anmeldung mit **JWT (JSON Web Tokens)**. Jeder Steuerbefehl (Jog, Play und der globale Stopp des Servers) wird vom Server anhand signierter Tokens validiert, gesendet über den atomaren Endpunkt `POST /api/robot/:id/command` (siehe Atomare Befehlssynchronisation weiter unten) - funktioniert sowohl mit der Rolle `admin` als auch `operator`, im Gegensatz zu einem vollständigen Schreibvorgang auf `POST /api/settings` (serverseitig nur für Admins). Jede Anfrage trägt außerdem einen `X-Hydra-Client: android`-Header, damit der eigene Config > Remote Access-Tab des Servers diese App unabhängig von SUITE/iOS erlauben/blockieren kann. Nahtlos integriert mit **biometrischer Authentifizierung** (Fingerabdruck/Gesicht) für die sichere Token-Erneuerung. Ein WebSocket, der mit dem Code `1008` (ungültiges/abgelaufenes Token) geschlossen wird, wird als "erneut anmelden" behandelt und nicht in einer Wiederverbindungsschleife erneut versucht (`network/HydraWebSocket.kt`).
- **Atomare Befehlssynchronisation** (das eigene `sendAtomicCommand()` von `viewmodel/RobotViewModel.kt`) - Jeder Schreibvorgang (play/pause/stop/jog/jogStep/valve/pump/speed/vision/tool) sendet einen kleinen, atomaren Befehl für einen einzelnen Roboter statt des gesamten Einstellungsbaums - der Server berechnet, welche kombinierten Roboter ebenfalls betroffen sind, speichert auf der Festplatte und sendet von sich aus an jeden anderen verbundenen Client. Enable/Disable wird auf die eigenen `combinedWith`-Geschwister eines Roboters genauso propagiert wie Play/Pause/Stop, da alle dieselbe Berechnung der betroffenen Roboter teilen.
- **Widget für den globalen STOPP** (`widget/GlobalStopWidget.kt`) - Dediziertes **Homescreen-Widget** für schnellen Zugriff. Bietet eine gut sichtbare Sofortzugriffs-Taste für den **globalen STOP** - derselbe Aufruf wie oben: Der Tipp bringt die App in den Vordergrund und sendet den Stopp, sobald die Serververbindung besteht, auch bei einem vollständigen Kaltstart (Prozess läuft noch nicht), und zeigt STOP NICHT BESTÄTIGT, wenn der Server nicht erreichbar ist. Die Benachrichtigungsaktion tut dasselbe. Das ist der Software-Stopp des Servers: kein Hardware-E-STOP, und er schaltet nichts spannungsfrei.
- **Haptisches Feedback** (`ui/ControlScreen.kt`) - Fortschrittliches sensorisches Feedback-System. Der globale STOP antwortet auf einen Tipp mit einer sofortigen doppelten Vibration; die Stopp-Taste des einzelnen Roboters behält ihren **Schutz vor langem Drücken** (ein kurzer Tipp gibt nur eine kurze Vibration + einen Hinweis; nur ein echtes Halten sendet den Befehl). Differenzierte haptische Signaturen (Erfolgs-, Fehler- und Notfallimpulse) geben dem Bediener in lauten Umgebungen eine physische Bestätigung.
- **In-App-Update-Kanal** (`update/GitHubReleaseUpdater.kt`, `update/ReleaseMetadataParser.kt`, `update/SemanticVersion.kt`) - Prüft beim Start und unter **Einstellungen → Updates** auf die neueste stabile GitHub Release; lädt ausschließlich das exakte Asset `HYDRA-UMC-ANDROID-CONTROL-release.apk` von einem Nicht-Entwurfs-/Nicht-Vorab-Tag herunter und installiert nie automatisch - Androids eigener Paketinstaller fordert die endgültige Zustimmung an. Vollständiger Vertrag: [`docs/GITHUB_RELEASE_UPDATES.md`](docs/GITHUB_RELEASE_UPDATES.md).
- **Bluetooth LE (GATT)** (`network/HydraBleClient.kt`, `network/BleContract.kt`, `network/AndroidBleGatt.kt`) - Ein echter GATT-Client nach einem dokumentierten Vertrag ([`docs/BLE_CONTRACT.md`](docs/BLE_CONTRACT.md)): Suche nach dem HYDRA-Dienst gefiltert, Verbindung mit MTU-Aushandlung, Dienstprüfung, Benachrichtigungen, in Rahmen zerlegte Nachrichten in beide Richtungen, jeweils ein Schreibvorgang, automatische Wiederverbindung mit wachsenden Wartezeiten, Berechtigungen ab Android 12 (`BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT`) und eine klare Meldung in der Sprache des Nutzers bei jedem Fehler. **Noch kein HYDRA-UMC-Gerät implementiert die Peripherieseite:** Der Client ist nur gegen ein simuliertes GATT getestet, der Android-Teil lief nie auf einem Telefon, und Roboterbefehle werden nicht über Bluetooth gesendet.
- **Optionales TLS** (`network/TlsSettings.kt`, `network/HydraHttp.kt`, `ui/SecuritySettings.kt`) - **Einstellungen → Sicherheit** wählt Automatisch (Standard, das bisherige Verhalten), TLS erzwingen oder Nur unverschlüsselt und kann den Fingerabdruck (SHA-256) des Serverzertifikats festlegen oder einer eigenen CA vertrauen. Eine unverschlüsselte Sitzung zu einem Rechner außerhalb des lokalen Netzes zeigt über jedem Bildschirm eine rote Warnung. Die eingebettete 3D-Ansicht folgt diesen Regeln noch nicht. Getestet mit echten Handshakes gegen einen lokalen Server; nie gegen einen echten HYDRA-UMC-Server mit TLS.
- **Kein unverschlüsselter Verkehr außerhalb des lokalen Netzes** (`network/TlsSettings.kt`, `PlainRemoteGuard`) - die App lehnt unverschlüsseltes HTTP/WS zu einem Server außerhalb des lokalen Netzes ab, außer **Einstellungen → Sicherheit → Unverschlüsselten Verkehr außerhalb des lokalen Netzes erlauben** ist eingeschaltet (standardmäßig aus). Die eingebettete 3D-Ansicht erhält das Sitzungstoken im Adressfragment (`#token=`), nie in der Abfrage, sodass kein Server- oder Proxy-Log es aufzeichnet. Automatische Sicherungen der App-Daten sind ausgeschaltet.
- **Gekoppelter Wear-OS-Begleiter & Sprach-Relay** (`wear/WatchVoiceRelayService.kt`, `wear/WatchCompanionProtocol.kt`) - Leitet einen bereits erkannten, vom Nutzer initiierten Sprachdurchgang von der gekoppelten [HYDRA-UMC-WATCH](https://github.com/JuanenRac/HYDRA-UMC-WATCH)-App über die eigene authentifizierte Server-Sitzung dieser App an `HYDRA-UMC-VOICE-UI` weiter und sendet eine typisierte Antwort an die Uhr zurück; weder dieses Relay noch die Systemstatus-Karte der Uhr lösen jemals einen Roboterbefehl aus oder greifen direkt auf `HydraState` zu - eine bewegungsbezogene Antwort ist explizit als `requiresConfirmation` markiert und muss über eine primäre Steuerungs-UI bestätigt werden. Vollständiger Vertrag: [`docs/WATCH_VOICE_RELAY.md`](docs/WATCH_VOICE_RELAY.md).
- **Sprachassistent in der App** (`ui/VoiceAssistantDialog.kt`) - Ein Mikrofonsymbol in der oberen Leiste öffnet einen Dialog zum Eintippen einer Frage/eines Befehls oder zur Nutzung der eigenen Spracherkennung dieses Telefons (`RecognizerIntent`, dasselbe reale Muster, das die Sprachtaste der Uhr bereits verwendet). Sendet die erkannte Transkription über dieselbe `POST /api/voice/turn`-Route wie das Uhr-Relay, jedoch als gewöhnlichen Datenverkehr dieses Telefons (`HydraApiClient.postVoiceTurn()`) statt als Relay - so kann Config > Fernzugriff diese Taste unabhängig von der gekoppelten Uhr erlauben/blockieren. Lädt niemals Audio hoch, und eine Antwort löst nie direkt einen Roboterbefehl aus.
- **Toolchain & Projektqualität** - AGP 9.3.1, Kotlin 2.2.10, Gradle 9.7.0, compileSdk 36, **JDK 21** (`compileOptions`, `gradle-daemon-jvm.properties` sowie sowohl die `.idea/`- als auch die `.vscode/`-Projektdateien zielen tatsächlich darauf ab, nicht nur diese Dokumentationszeile). Saubere Build-Ausgabe ohne Warnungen, optimierte R8-Produktionsvarianten und fortgeschrittene **Roborazzi**-Screenshot-Tests.

**Status: Wi-Fi, Bluetooth, Biometrie und Benachrichtigungen implementiert.** Die App ist eine industrielle Bedienkonsole; Bluetooth ist noch experimentell, und kein Bildschirm ersetzt einen physischen Not-Aus.

## 🔒 Sicherheit und Schutz

| Bedrohung | Abwehr | Wo |
|---|---|---|
| Ein vom Telefon gestohlenes Token | Das Anmelde- und das Erneuerungstoken liegen in EncryptedSharedPreferences (AES-256-GCM pro Wert, Hauptschlüssel im Android-Keystore); die automatische Sicherung der App-Daten ist aus (`allowBackup=false`) | `network/AuthPrefs.kt`, `AndroidManifest.xml` |
| Unverschlüsselter Verkehr zu einem Server außerhalb des lokalen Netzes | `PlainRemoteGuard` weist unverschlüsseltes HTTP und WebSocket zu einem solchen Server ab, außer Einstellungen > Sicherheit > Unverschlüsselten Verkehr außerhalb des lokalen Netzes erlauben ist an (standardmäßig aus); eine unverschlüsselte Sitzung zeigt eine rote Warnung über jeder Ansicht | `network/TlsSettings.kt`, `ui/SecuritySettings.kt` |
| Ein gefälschter Server | Optionales TLS mit drei Modi (Automatisch, TLS verlangen, Nur Klartext); mit festgelegtem SHA-256-Fingerabdruck wird nur ein Zertifikat mit genau diesem Fingerabdruck akzeptiert, alternativ kann einer eigenen CA vertraut werden | `network/TlsSettings.kt`, `network/HydraHttp.kt` |
| Ein gefaelschtes Update | Akzeptiert wird nur das Asset `HYDRA-UMC-ANDROID-CONTROL-release.apk` der neuesten stabilen Release, die kein Entwurf ist; die App installiert nie selbst, der Installer von Android fragt um Zustimmung | `update/ReleaseMetadataParser.kt` |
| Ein Roboter, den zwei Personen bewegen, oder ein Stopp, der nicht ankommt | Der Roboter wird vor dem Verfahren reserviert und der Bildschirm zeigt, wer ihn hält; der globale STOPP wird mit kurzen Zeitlimits gesendet, einmal wiederholt, fällt auf einen Stopp je Roboter zurück und behält einen dauerhaften Zustand „STOPP NICHT BESTÄTIGT“, wenn er nicht zugestellt werden konnte | `ui/GlobalStop.kt`, `network/AccessModels.kt`, `network/StopAllResult.kt` |

**Was sie nie tut:** einen physischen Not-Halt ersetzen. Der STOPP ist der Software-Stopp des Servers und trennt nichts von der Stromversorgung.

## 🔌 Schnittstellen

| Topic oder Route | Was es trägt |
|---|---|
| `POST /api/login`, `/api/logout`, `/api/refresh` | Anmelden, Abmelden und Sitzung erneuern |
| `GET /api/hydra-info` | erkennt einen echten Server an `remoteApiVersion`; genutzt vom Subnetz-Scan und der manuellen Adresse |
| `GET /api/settings`, `/api/ecosystem/status`, `/api/system/metrics`, `/api/safety-limits`, `/api/claims` | Zustand der Roboter, der Familie, des Rechenmoduls, die Sicherheitsgrenzen und die Reservierungen |
| `POST /api/robot/{id}/command`, `/claim`, `/release`, `/jog-arm` | ein kleiner Befehl für einen Roboter, seine Reservierung und das Scharfschalten einer Handbewegung |
| `POST /api/stop-all` | der globale STOPP: alle Roboter, auch die von jemand anderem reservierten |
| `GET /api/audit`, `/api/camera/ticket`, `POST /api/voice/turn`, `GET /api/watch/system-status` | das Audit-Protokoll (Administratoren), ein Ticket für den Kamerastrom, ein Sprachzug und die Statuskarte der Uhr |
| WebSocket `hydra.bearer` | Live-Zustandsänderungen vom Server; das Token steht im Subprotokoll und die App verbindet sich alle 3 Sekunden neu |
| Bluetooth LE GATT | der Vertrag aus `docs/BLE_CONTRACT.md`: gerahmte Nachrichten, ein Schreibvorgang zur Zeit, Android-12-Berechtigungen; kein HYDRA-UMC-Gerät setzt die Gegenseite bisher um |

## ⚙️ Konfiguration

Die App hat keine Konfigurationsdatei: Alles wird auf dem Bildschirm eingestellt und auf dem Telefon gespeichert. Standardwerte und Grenzen kommen aus dem Code.

| Variable | Standard | Was sie festlegt |
|---|---|---|
| Einstellungen > Sicherheit > TLS-Modus | Automatisch | Automatisch, TLS verlangen oder Nur Klartext; optional festgelegter SHA-256-Fingerabdruck oder eigene CA |
| Einstellungen > Sicherheit > Unverschlüsselten Verkehr außerhalb des lokalen Netzes erlauben | aus | erlaubt der App, unverschlüsseltes HTTP und WebSocket mit einem Server außerhalb des lokalen Netzes zu sprechen |
| Serveradresse und Port | `3000` | wird auf dem Anmeldebildschirm eingegeben oder vom Scan des eigenen /24-Subnetzes des Telefons (1,5 s je Host, 16 gleichzeitig) plus mDNS gefunden |
| Sitzungserneuerung | 20 s / 3 / 10 min | die App erneuert die Sitzung selbst höchstens 3-mal in 10 Minuten und mit mindestens 20 s Abstand; danach verlangt sie die Anmeldung erneut |
| Einstellungen > Benachrichtigungen | an | Meldungen, wenn ein Roboter einen Auftrag beendet oder ein kritisches Ereignis eintritt (zwei Kanäle: Aufträge und Sicherheit) |

## 🚀 Schnellstart

Benötigt JDK 21 und das Android-SDK (Android Studio bringt beides mit) sowie ein Telefon oder einen Emulator mit Android 7.0 (API 24) oder neuer.

```bash
./build-android.sh           # builds the debug APK and installs it by adb (build-android.bat on Windows)
./gradlew assembleDebug      # only builds; the APK lands in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest  # runs the unit tests (gradlew.bat on Windows)
```

## 🧪 Tests

303 automatische Tests laufen mit `./gradlew testDebugUnitTest`. Sie brauchen weder Hardware noch etwas außerhalb dieses Rechners.

Die CI führt `HYDRA_UMC_CI=1 python tools/ci_validate.py` aus; das prüft das Manifest, die Dokumente, die acht READMEs und die Bilder.

### Testen gegen einen echten Server

1. Backend ausführen: `cd HYDRA-UMC-SERVER && npm run dev` (Port 3000) - das ist die eigentliche REST/WS-API, mit der diese App spricht (siehe Verwandte Projekte weiter unten); das eigene `npm run dev` von `HYDRA-UMC-STUDIO` startet nur dessen Vite-Frontend-Entwicklungsserver (Port 5173) gegen dasselbe Backend, es ist nicht der API-Server selbst.
2. Verbinden Sie Ihr Android-Gerät mit demselben Wi-Fi.
3. Verwenden Sie den **globalen Serverauswähler** oder geben Sie die IP manuell in der Kopfzeile ein.
4. **Biometrie:** Aktivieren Sie "Biometric Login" in Ihrem Benutzerprofil, um beim nächsten Start den Passwortbildschirm zu überspringen.

## 🏗️ Build und Veröffentlichung

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

Die APK landet unter `app/build/outputs/apk/debug/app-debug.apk`. Installieren Sie sie mit `adb install -r -d app/build/outputs/apk/debug/app-debug.apk`, oder übertragen Sie sie manuell auf das Gerät. Tauschen Sie `assembleDebug` gegen `assembleRelease` für einen Release-Build - dieser signiert mit dem privaten Release-Schlüssel, wenn `keystore.properties` (oder die Umgebungsvariablen für die Release-Signatur) vorhanden ist, und fällt sonst auf den Debug-Schlüssel zurück, der sich zum Testen problemlos installieren lässt, aber nie verteilt werden darf.

### Versionierung

Dieses Repository folgt einer Richtlinie für das gesamte Ökosystem: Bauen ändert die Version nie. `app/version.properties` speichert die aktuellen Werte von `versionMajor`/`versionMinor`/`versionPatch`/`versionCode`; nur der explizite `bump`-Befehl erhöht sie (`python tools/hydra_project_tool.py bump`: `bump_manifest_version.py` für die native Version, das Manifest `hydra-umc.project.json` und `CHANGELOG.md`, und `bump_version_code.py` für den getrennten, stets monotonen Android-`versionCode`). `app/build.gradle.kts` *liest* `version.properties` nur und schreibt es nie.

- **`versionCode`:** ein einfacher monotoner Zähler, +1 bei jedem Bump, ohne Übertrag - Android verlangt, dass er bei jedem tatsächlich veröffentlichten Build strikt steigt.

Ein einfaches `./gradlew assembleDebug` (der Weg "Manuelles Erstellen" oben, ohne vorher eines der Bump-Skripte auszuführen) verwendet einfach weiter, was zuletzt in `version.properties` stand - es erhöht dabei von sich aus nichts. Die laufende Version ist live im **Info**-Dialog sichtbar (`BuildConfig.VERSION_NAME`, das den zuletzt in `version.properties` stehenden `versionName` liest). Siehe [CHANGELOG.md](CHANGELOG.md) für die Versionshistorie.

## 🩺 Fehlerbehebung

| Symptom | Ursache | Behebung |
|---|---|---|
| Keine Benachrichtigungen | Berechtigung verweigert | Erteilen Sie die Berechtigung "Benachrichtigungen" in den Android-Einstellungen für diese App |
| Keine Biometrie | Hardware nicht eingerichtet | Stellen Sie sicher, dass ein Fingerabdruck/Gesicht in Ihrer Android-Systemsicherheit registriert ist |
| Roboter bewegt sich nicht | Sitzung abgelaufen, Roboter von einem anderen Konto reserviert oder Trajektorie läuft | Lesen Sie die Meldung in der Statusleiste; der Server spielt Trajektorien selbst ab, ein Browser-Tab ist nicht nötig |
| Bluetooth deaktiviert | Physischer Chip aus | Verwenden Sie die 3D-Taste "ENABLE SYSTEM BT" in der App |

## 📂 Struktur des Repositorys

<p align="center">
  <img src="images/repository-structure.svg" alt="Repository structure of HYDRA-UMC-ANDROID-CONTROL" width="100%">
</p>

<details>
<summary>Textversion des Baums</summary>

```text
HYDRA-UMC-ANDROID-CONTROL/
├── app/src/main/java/com/hydraumc/control/
│   ├── MainActivity.kt                                          Einstiegspunkt - Splash, Login-/Hauptbildschirm-Zugriffssteuerung, kaltstartsichere globale E-STOP-Behandlung
│   ├── MainScreen.kt                                            Unteres Navigations-Grundgerüst, obere Leiste (Serverauswahl, Profil, Telemetrie, Einstellungen)
│   ├── kinematics/                                              Vorwärts- und Rückwärtskinematik des PAROL6-Arms
│   ├── model/                                                   der Zustand des Servers, wie die App ihn liest
│   ├── network/                                                 REST-Client, WebSocket und Erkennung
│   ├── ui/                                                      Ansichten und Widgets
│   ├── update/                                                  der App-eigene Update-Kanal
│   ├── util/                                                    reine Regeln der App
│   ├── viewmodel/                                               der gemeinsame Zustand, den jede Ansicht beobachtet
│   ├── wear/                                                    Sprach- und Status-Relay
│   └── widget/                                                  das STOPP-Widget des Startbildschirms
├── docs/                                                        Dokumente des Projekts
├── images/                                                      Quellressourcen für das README-Banner + den Splashscreen
├── tools/                                                       Projektwerkzeuge und CI-Prüfungen
├── .github/                                                     CI-Workflow und Issue-Vorlagen
├── gradle/                                                      Dateien des Gradle-Wrappers
├── bump_manifest_version.py                                     Synchronisiert die Version von hydra-umc.project.json mit der nativen (--sync)
├── bump_version_code.py                                         Erhöhung des Android-versionCode-Zählers (nur vom expliziten `bump`-Befehl ausgeführt)
├── gradlew / gradlew.bat                                        Gradle-Wrapper
├── build.gradle.kts / settings.gradle.kts / gradle.properties   Root-Konfiguration des Gradle-Projekts
├── keystore.properties.example                                  Vorlage für die private Release-Signierkonfiguration
├── .env.example                                                 Beispiel für Umgebungsvariablen
├── LICENSE                                                      GPL-3.0-or-later
├── CHANGELOG.md                                                 was sich in jeder Version geändert hat
├── build-test.sh                                                Build-Prüfung ohne Änderungen
├── hydra-umc.project.json                                       Manifest: Version, Reifegrad, Familie
├── test.sh                                                      Build-Prüfung ohne Änderungen
├── prepare-github-release.sh / prepare-github-release.bat       baut die privat signierte Release-APK; ändert keine Version
├── publish-github-release.sh / publish-github-release.ps1       veröffentlicht diese APK als GitHub-Release der aktuellen Version
├── app/version.properties                                       Version und versionCode
└── app/src/test/                                                JVM-Unit-Tests
```

</details>

## 🗺️ Roadmap und bekannte Grenzen

Bluetooth LE bleibt experimentell, bis ein Gerät die Peripherieseite von `docs/BLE_CONTRACT.md` umsetzt. TLS und der festgelegte Fingerabdruck wurden mit echten Handshakes gegen einen lokalen Testserver geprüft, nicht gegen einen HYDRA-UMC-Server mit TLS. Die eingebettete 3D-Ansicht folgt den TLS-Einstellungen noch nicht. Der Keystore-Speicher, die biometrische Abfrage und der Bluetooth-Transport wurden seit ihrer Entstehung nicht erneut auf Hardware geprüft.

## 🌍 Sprachen

Diese README und die Dokumente des Repositorys gibt es in acht Sprachen: Englisch, Spanisch, Deutsch, Französisch, Italienisch, Japanisch, Chinesisch und Portugiesisch (Portugal). Sie entstehen gemeinsam, und die CI prüft, dass die acht Dateien dieselben Abschnitte und dieselben Links haben.

## 🔗 Verwandte Projekte

Dieses Projekt ist Teil des HYDRA-UMC-Robotik-Ökosystems desselben Autors (JuanenRac / Electro Hobby 3D). Gut zu wissen, da eine Anfrage eigentlich eines dieser Projekte betreffen könnte statt dieses Repositorys.

**Übergeordnetes Projekt**
- **[HYDRA-UMC-SERVER](https://github.com/JuanenRac/HYDRA-UMC-SERVER)** — das reale Headless-Backend (REST/WebSocket), mit dem jeder Steuerungsclient tatsächlich spricht; das Backend, gegen das die eigene Erkennung, Authentifizierung und WebSocket-Synchronisierung dieser App laufen.

**Geschwisterprojekte** — sprechen ebenfalls mit der eigenen API von HYDRA-UMC-SERVER, jeweils als eigener Client
- **[HYDRA-UMC-STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO)** — Web-Steuerungs-Dashboard mit Echtzeit-3D-Visualisierung mehrerer Roboter; sein eigener 3D-Viewport ist direkt im 3D-Ansicht-Bildschirm dieser App per WebView eingebettet.
- **[HYDRA-UMC-SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE)** — Desktop-Schwarmleitstand (PySide6) für mehrere Server gleichzeitig, verpackt als eigenständige ausführbare Datei; spricht genau denselben `REMOTE_API.md`-Vertrag wie diese App.
- **[HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL)** — iOS/iPadOS-Steuerungs-App (Flutter) mit Echtzeit-WebSocket-Synchronisierung; das direkte iOS/iPadOS-Gegenstück dieser App, mit demselben Funktionsumfang.
- **[HYDRA-UMC-DSI](https://github.com/JuanenRac/HYDRA-UMC-DSI)** — native Touch-UI für das eingebaute 7"-DSI-Touchscreen, direkt auf dem CM5 eingebettet.
- **[HYDRA-UMC-BRIDGE-AMR](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-AMR)** — Koordinationsschranke für AGV-/AMR-Flotten über einen VDA-5050-MQTT-Publisher (nur gegen eine emulierte Flotte getestet).
- **[HYDRA-UMC-BRIDGE-CNC](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-CNC)** — High-Level-Koordinator für CNC-Zellen mit GRBL-Status-/Steuerbyte-Zugriff (nur gegen einen Emulator getestet).
- **[HYDRA-UMC-BRIDGE-DROIDS](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-DROIDS)** — Koordinationsschranke für laufende/humanoide Droiden, mit einem Boston-Dynamics-Spot-Befehlssender (nur gegen einen Emulator getestet).
- **[HYDRA-UMC-BRIDGE-LASER](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-LASER)** — Koordinator für Laserzellen, liest 3 Schlüssel-/Gehäuse-/Verriegelungs-GPIO-Signale (nur gegen einen simulierten Chip getestet; kann keinen Laser scharfschalten oder auslösen).
- **[HYDRA-UMC-BRIDGE-OPENPNP](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-OPENPNP)** — sicherer High-Level-Koordinator für den Leiterplattenfluss von OpenPnP Pick-and-Place.
- **[HYDRA-UMC-BRIDGE-PRINTER3D](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-PRINTER3D)** — sichere Koordinationsschranke für Moonraker/Klipper-3D-Drucker, mit echten gesicherten Job-Befehlen.
- **[HYDRA-UMC-BRIDGE-ROS2](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-ROS2)** — Koordinator mit einem träge importierten rclpy-ROS-2-Transport (noch nicht gegen eine ROS-2-Installation ausgeführt).
- **[HYDRA-UMC-BRIDGE-UAV](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-UAV)** — Koordinationsschranke für kameraausgestattete UAVs, mit einem MAVLink-Befehlssender (nur gegen einen Emulator getestet).

**Direkt verwandt**
- **[HYDRA-UMC-WATCH](https://github.com/JuanenRac/HYDRA-UMC-WATCH)** — WearOS-Begleit-App mit haptischen Alarmmustern und einem Sprach-Relay zum gekoppelten Telefon (noch nicht auf einer physischen Uhr ausgeführt); die WearOS-Begleit-App zu dieser App, für Roboterstatus und -steuerung auf einen Blick vom Handgelenk aus.
- **[HYDRA-UMC-HIL-BRIDGE](https://github.com/JuanenRac/HYDRA-UMC-HIL-BRIDGE)** — Hardware-in-the-Loop-Router mit einer Software-Kollisionsverriegelung zwischen Simulation und echter Hardware (ohne echte Hardware getestet); ermöglicht die Fernsteuerung des digitalen Zwillings direkt aus dieser App.

**Ebenfalls Teil des Ökosystems**

*Kern-Hardware & Plattform*
- **[HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC)** — der Entwurf des Motherboards des Roboterarms: CM5-Host + Dual-Core-STM32H745, gedacht zur Koordination von bis zu 8 Werkzeugarmen über CAN-OTA/SPI-OTA (Firmware in einer frühen Phase; noch keine bestückte Platine).
- **[HYDRA-UMC-OS](https://github.com/JuanenRac/HYDRA-UMC-OS)** — reproduzierbare Raspberry-Pi-OS-Produktschicht für den CM5: schreibgeschützter Agent, validierte Konfiguration/Profile, WiFi-Ersteinrichtung.
- **[HYDRA-UMC-SDK](https://github.com/JuanenRac/HYDRA-UMC-SDK)** — der gemeinsame JSON-Schema-Vertrag und die Software-Auftragsschranke, gegen die jede Bridge ihre Befehle validiert (keine zertifizierte Sicherheitsfunktion).
- **[HYDRA-UMC-CONNECTOR-HUB](https://github.com/JuanenRac/HYDRA-UMC-CONNECTOR-HUB)** — deklaratives Adapter-Manifest-Register und Validator für Konnektoren externer Maschinen; erweitert die eigene Vertragsidee des SDK auf externe Maschinen, ohne die Industrie-Gateway-Projekte zu ersetzen.

*Kern-Backend & Clients*
- **[HYDRA-UMC-EDITOR-URDF](https://github.com/JuanenRac/HYDRA-UMC-EDITOR-URDF)** — grafischer Desktop-URDF-Ersteller/-Editor, der fertige Modelle in STUDIOs eigenen Katalog überträgt.
- **[HYDRA-UMC-EDITOR-STL](https://github.com/JuanenRac/HYDRA-UMC-EDITOR-STL)** — Desktop-STL-Modelleditor, der echte Teile im selben Modellkatalog transformiert/ersetzt/entfernt/hinzufügt, den auch HYDRA-UMC-EDITOR-URDF bearbeitet.

*URTC-Werkzeugplattform*
- **[URTC](https://github.com/JuanenRac/URTC)** — Firmware für die physische Universal-Robot-Tool-Controller-Platine, 25+ Werkzeugprofile über CAN-Bus.
- **[URTC-FLASHER](https://github.com/JuanenRac/URTC-FLASHER)** — Desktop-GUI-Flash-Tool für URTC-Platinen, CAN-OTA plus Full-Chip-SWD/JTAG.
- **[URTC-TESTER](https://github.com/JuanenRac/URTC-TESTER)** — Desktop-Live-CAN-Bus-Diagnosetool für URTC-Platinen, ein Panel pro Werkzeugprofil.
- **[URTC-WEB-STUDIO](https://github.com/JuanenRac/URTC-WEB-STUDIO)** — browserbasierte Alternative zu URTC-TESTER über die Web-Serial-API, ohne lokale Installation.
- **[URTC-GATEWAY](https://github.com/JuanenRac/URTC-GATEWAY)** — Gateway vor dem CAN-Bus von URTC: Liste erlaubter Kennungen, Ratenbegrenzung und Sitzungsschlüssel; Software auf dem Computer getestet, noch nicht an einem echten Bus.

*Vision-KI-Knoten (Hailo-8)*
- **[HYDRA-UMC-VISION-NODE](https://github.com/JuanenRac/HYDRA-UMC-VISION-NODE)** — Integrationsknoten für die Hailo-8-Vision-Pipeline, mit einer echten stufenweisen Hardware-Bereitschaftsprüfung.
- **[HYDRA-UMC-DETECTION-HEF](https://github.com/JuanenRac/HYDRA-UMC-DETECTION-HEF)** — echte Registry für kompilierte Modelle mit Hailo-Architektur-/Prüfsummen-Safe-Load-Verifizierung.
- **[HYDRA-UMC-VISION-STREAMER](https://github.com/JuanenRac/HYDRA-UMC-VISION-STREAMER)** — echter GStreamer-Pipeline- + MediaMTX-Konfigurationsgenerator mit einer echten HailoRT-Integrationsschranke.
- **[HYDRA-UMC-VISUAL-SERVOING-API](https://github.com/JuanenRac/HYDRA-UMC-VISUAL-SERVOING-API)** — Position-Based-Visual-Servoing-Korrekturgesetz mit Freigabe abhängig vom Zonenstatus; noch keine Posenschätzung per Kamera.
- **[HYDRA-UMC-SAFETY-ZONES](https://github.com/JuanenRac/HYDRA-UMC-SAFETY-ZONES)** — v0-Logik für Zonenverletzung, Kalibrierungsaktualität und E-STOP-Anforderung, die Objektpositionen aus einer Datei nimmt; kein Detektor speist sie, und noch kein physischer E-STOP ist angeschlossen.

*Kognitiver KI-Knoten (Hailo-10)*
- **[HYDRA-UMC-COGNITIVE-NODE](https://github.com/JuanenRac/HYDRA-UMC-COGNITIVE-NODE)** — Integrationsknoten für die Hailo-10-Cognitive-Pipeline (LLM-/VLA-/Sprach-Orchestrierung).
- **[HYDRA-UMC-VLA-ENGINE](https://github.com/JuanenRac/HYDRA-UMC-VLA-ENGINE)** — echte Aktions-Token-Kodierung/-Dekodierung und Trajektoriengenerierung für ein Vision-Language-Action-Modell.
- **[HYDRA-UMC-VOICE-UI](https://github.com/JuanenRac/HYDRA-UMC-VOICE-UI)** — echtes Sprach-Frontend (VAD + Intent-Parser) mit einem begrenzten, bestätigungsgesicherten Watch-Relay.
- **[HYDRA-UMC-SEMANTIC-PLANNER](https://github.com/JuanenRac/HYDRA-UMC-SEMANTIC-PLANNER)** — echte regelbasierte Aufgabenzerlegung und semantische Fehlerbehebung über MCU-Fehlercodes.
- **[HYDRA-UMC-DOCS-QA](https://github.com/JuanenRac/HYDRA-UMC-DOCS-QA)** — echte, nur auf der Standardbibliothek basierende TF-IDF-Dokumentensuche über die eigenen Markdown-Dokumente dieses Ökosystems.
- **[HYDRA-UMC-LOCAL-TECHNICIAN](https://github.com/JuanenRac/HYDRA-UMC-LOCAL-TECHNICIAN)** — lokaler, richtlinien-gesteuerter KI-Wartungstechniker für das Ökosystem selbst - beobachtet, diagnostiziert und schlägt Korrekturen vor; die zwei höchsten Risikostufen sind absichtlich noch nicht implementiert.

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
- **[HYDRA-UMC-OS-REBUILDER](https://github.com/JuanenRac/HYDRA-UMC-OS-REBUILDER)** — Windows/Linux-Desktop-Tool, das ein flashbereites CM5-Image baut, vorgeladen mit den aktuellsten Versionen des Ökosystems, mit Ersteinrichtungs-Konfiguration für WLAN/Benutzer/SSH im Stil von Raspberry Pi Imager.
- **[HYDRA-UMC-OPS-AGENT](https://github.com/JuanenRac/HYDRA-UMC-OPS-AGENT)** — Wartungsvorfall-Koordinator: eine Edge-Rolle mit niedrigem Privileg sammelt einen bereinigten Inventar-/Gesundheits-Snapshot, eine Control-Plane-Rolle rendert ihn schreibgeschützt und bittet einen KI-Anbieter um einen Diagnosevorschlag - wendet nie einen Patch an und stellt nie etwas bereit.
- **[HYDRA-UMC-DEV-SERVER](https://github.com/JuanenRac/HYDRA-UMC-DEV-SERVER)** — reproduzierbarer Entwicklungshost (Raspberry Pi 5 / CM5), der den Quellcode des Ökosystems vorhält und begrenzte Build-/Test-Aufgaben über eine dauerhafte Warteschlange ausführt; eine dedizierte Entwicklerrolle, ausdrücklich kein operativer CM5.

## 📚 Dokumentation

Hier steht mehr:

* [docs/SOURCE_MAP.md](docs/SOURCE_MAP.md) - jede Quelldatei der App mit einer Zeile dazu, was sie tut
* [Sicherheitsgrundlage des Ökosystems](https://github.com/JuanenRac/HYDRA-UMC-DOCS/blob/main/docs/SECURITY_BASELINE.md)
* [Bedrohungsmodell des Ökosystems](https://github.com/JuanenRac/HYDRA-UMC-DOCS/blob/main/docs/THREAT_MODEL.md)
* [Änderungsprotokoll dieses Repositorys](CHANGELOG.md)
* [Lizenz dieses Repositorys](LICENSE.md)

## 🤝 Mitwirken und Community

* Führe vor einem Pull Request `HYDRA_UMC_CI=1 py -3 tools/ci_validate.py` (oder `HYDRA_UMC_CI=1 python3 tools/ci_validate.py`) aus: Es muss `CI_VALIDATION=PASS` ausgeben. Führe dafür nicht `build.sh` oder `build.bat` aus; Bauen ist keine Prüfung.
* Die acht READMEs entstehen aus einer einzigen Quelle in HYDRA-UMC-DOCS; bearbeite sie nicht von Hand. Ändere die Quelle und schreibe sie mit `tools/make_readmes.py` neu.
* Behaupte in einer README nichts, was nicht gelaufen ist: Schreibe „simuliert“ oder „nicht auf Hardware erprobt“, wo das stimmt.
* Siehe [CONTRIBUTING.md](CONTRIBUTING.md), den [Verhaltenskodex](CODE_OF_CONDUCT.md), [SECURITY.md](SECURITY.md) und [SUPPORT.md](SUPPORT.md).

## 👤 AUTOR

**JuanenRac (Electro Hobby 3D)** · electrohobby3d@gmail.com

## 📜 LIZENZ

GPL-3.0-or-later - [LICENSE](LICENSE).
