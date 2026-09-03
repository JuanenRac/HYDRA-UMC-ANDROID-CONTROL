<p align="center">
  <img src="images/HYDRA_UMC_BANNER.svg" alt="HYDRA-UMC-ANDROID-CONTROL banner" width="100%">
</p>

# 📱 HYDRA-UMC CONTROL

<p align="center">
  <a href="README.md">🇺🇸 English</a> |
  <a href="README_spa.md">🇪🇸 Español</a> |
  <a href="README_fra.md">🇫🇷 Français</a> |
  🇮🇹 <b>Italiano</b> |
  <a href="README_deu.md">🇩🇪 Deutsch</a> |
  <a href="README_zho.md">🇨🇳 简体中文</a> |
  <a href="README_jpn.md">🇯🇵 日本語</a>
</p>


<p align="left">
  <img src="https://img.shields.io/badge/Licenza-GPL%203.0-blue.svg" alt="GPL 3.0">
  <img src="https://img.shields.io/badge/Linguaggio-Kotlin-7F52FF.svg" alt="Kotlin">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg" alt="Compose">
  <img src="https://img.shields.io/badge/Piattaforma-Android-3DDC84.svg" alt="Android">
</p>


Un'app Android nativa (Kotlin + Jetpack Compose) che controlla un robot sulla piattaforma [HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC) via Wi-Fi o Bluetooth, parlando esattamente lo stesso contratto [`REMOTE_API.md`](https://github.com/JuanenRac/HYDRA-UMC-SERVER/blob/main/docs/REMOTE_API.md) usato da [HYDRA-UMC SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE) - discovery, lettura/scrittura dello stato completo, e sincronizzazione live via WebSocket con un backend [HYDRA-UMC-SERVER](https://github.com/JuanenRac/HYDRA-UMC-SERVER) in esecuzione (lo stesso con cui parla la dashboard web di [HYDRA-UMC STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO)). Controparte Android diretta di [HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL). Vedi [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) per il design completo.

## 🏗️ Cosa è implementato

- **Controllo Accessi e Biometria** (`ui/LoginScreen.kt`, `util/BiometricHelper.kt`) - Sistema di login professionale con supporto per **Impronta digitale e Sblocco con il Volto** (`androidx.biometric`), più campi IP/porta direttamente nella stessa schermata così un server può essere selezionato senza passare prima dalle Impostazioni. Include la funzione "Ricordami", un meccanismo di **Logout** sicuro, ed è completamente localizzato in **5 lingue**. Nome utente/password/token in cache (`network/AuthPrefs.kt`) risiedono in **EncryptedSharedPreferences** basate su Keystore (AES256-GCM), non in chiaro - ogni server di questo ecosistema crea un account predefinito `admin`/`admin` al primo avvio, con account aggiuntivi a privilegio inferiore di tipo **operator** creabili lato server da Config > Users.
- **Modalità Offline e Cache dello Stato** (`network/StateCache.kt`) - Motore di persistenza integrato che usa **DataStore**. L'app mette automaticamente in cache l'ultimo stato noto del sistema, consentendo la visualizzazione istantanea della dashboard e audit di configurazione anche senza una connessione Wi-Fi attiva.
- **Notifiche e Avvisi di Missione** (`util/NotificationHelper.kt`) - Sistema di allerta di livello industriale. Invia notifiche push ad alta priorità quando un robot completa una sequenza di lavoro o in caso di eventi hardware critici, garantendo che l'operatore sia informato anche quando l'app è in background.
- **Terminale di Telemetria Industriale** (`ui/TelemetryScreen.kt`) - Un visualizzatore di log in tempo reale dedicato, con un'interfaccia in stile terminale. Traccia gli eventi di sistema, la sincronizzazione REST/WebSocket, e fornisce diagnostica a colori (Verde Matrix per il successo, Rosso Industriale per gli errori).
- **Dashboard Avanzata** (`ui/DashboardScreen.kt`) - **Carosello Orizzontale 3D** ad alta fedeltà con effetti di scorrimento prospettico. Mostra metadati del robot arricchiti: **Produttore** (Source Robotics, Annin, Universal Robots, AgileX, ecc.), **Ruolo del Robot** (CNC, Laser, PnP), e una **Matrice dei Moduli Industriale** con stato live per i moduli CAM, XY, ATC, PNP, CNC, LSR, BED, VAC e RCK.
- **Monitor di Salute del Sistema** (`ui/DashboardScreen.kt`) - Metriche in tempo reale per la Compute Module 5 collegata, incluso **Hostname**, **Uptime Formattato** (es. "2d 4h 15m"), e conteggi attivi di controller e robot.
- **Controllo Manuale Avanzato** (`ui/ControlScreen.kt`) - Presenta un layout verticale professionale con **pulsanti Joystick il 50% più grandi** per la massima precisione. Include un **Selettore Job/Traiettoria** per sfogliare ed eseguire file direttamente dal server.
- **Pannello di Sicurezza e Riproduzione** (`ui/ControlScreen.kt`) - Barra di controllo inferiore fissa che ospita i pulsanti **E-STOP (Arresto di Emergenza)**, **Start**, **Pause** e **Stop**. Questi controlli sono sempre visibili e dispongono di **Feedback Aptico** per la conferma sensoriale fisica.
- **Vista 3D** (`ui/ThreeDScreen.kt`) - Incorpora il viewport 3D in tempo reale proprio di HYDRA-UMC STUDIO in una WebView (`?hideUI=true&robotId=&token=`), anziché un renderer nativo - `ui/NativeThreeDScreen.kt` è un esperimento incompiuto con Google Filament (dichiarato ma non collegato alla navigazione, e ancora privo del caricamento di asset `.glb`), mantenuto nell'albero per chi lo riprenderà più avanti ma non è il percorso di codice attivo. L'approccio WebView ottiene gratuitamente la scena 3D reale e attualmente in produzione di STUDIO (ogni mesh/cinematica reale di ogni robot) invece di reimplementarla nativamente - il compromesso è l'overhead di rendering della WebView, non ottimale per la batteria, ma funzionalmente completo oggi.
- **Visione Ottale in Tempo Reale** (`ui/CameraScreen.kt`, `ui/MjpegPlayer.kt`) - **Streamer MJPEG Nativo** di livello industriale. Dispone di un parser autonomo in background e di un renderer basato su Canvas per telemetria video a latenza zero, uno stato chiaro "Camera Disabilitata" (invece di un feed vuoto silenzioso) quando il sistema di visione di un robot è spento, e un interruttore per accendere/spegnere la camera di un robot direttamente dal server. Supporta overlay automatici **Picture-in-Picture (PIP)** nella schermata di controllo manuale, mappati su robot specifici tramite la configurazione delle camere del server.
- **Discovery e Connettività Intelligente** (`network/Discovery.kt`, `network/HydraApiClient.kt`, `network/HydraWebSocket.kt`) - Scansione concorrente della sottorete contro ogni host candidato nella /24 del telefono stesso (includendo l'IP LAN del telefono stesso e localhost, non solo gli altri host), interrogando `GET /api/hydra-info` e identificando un server reale unicamente tramite la presenza di `remoteApiVersion` - lo stesso controllo usato dal percorso IP manuale, così un server il cui proprietario lo ha rinominato rispetto alla stringa di prodotto predefinita viene comunque trovato. Un listener **NsdManager** (mDNS/Bonjour) gira in parallelo - il server si annuncia effettivamente come `_hydra._tcp` (`bonjour-service`), e `MainActivity` richiede in anticipo il permesso runtime di posizione/dispositivi vicini necessario per questo (una dichiarazione nel manifest da sola non lo concede mai su API 23+) - ma la scansione della sottorete resta il percorso primario poiché la raggiungibilità multicast su Wi-Fi è intrinsecamente meno affidabile di una semplice sonda HTTP. L'app attiva automaticamente il WiFi all'avvio, scansiona la rete locale della fabbrica, ed esegue un **Auto-connessione Zero-Click** al primo server HYDRA-UMC disponibile.
- **Accesso Industriale Sicuro** (`network/HydraApiClient.kt`, `ui/LoginScreen.kt`) - Livello di sicurezza professionale che usa **JWT (JSON Web Tokens)**. Ogni comando di controllo (Jog, Play, E-STOP) viene validato dal server tramite token firmati, inviati sull'endpoint atomico `POST /api/robot/:id/command` (vedi Sincronizzazione dei Comandi Atomici più sotto) - funziona sia con il ruolo `admin` che `operator`, a differenza di una scrittura completa su `POST /api/settings` (solo admin lato server). Ogni richiesta porta anche un header `X-Hydra-Client: android` così la scheda Config > Remote Access del server stesso può consentire/bloccare questa app indipendentemente da SUITE/iOS. Integrato senza soluzione di continuità con l'**Autenticazione Biometrica** (Impronta/Volto) per il rinnovo sicuro dei token. Un WebSocket chiuso con il codice `1008` (token non valido/scaduto) viene trattato come "accedi di nuovo", senza essere ritentato in un ciclo di riconnessione (`network/HydraWebSocket.kt`).
- **Sincronizzazione dei Comandi Atomici** (il `sendAtomicCommand()` proprio di `viewmodel/RobotViewModel.kt`) - Ogni scrittura (enable/disable/play/pause/stop/jog/valve/pump/speed/vision) invia un piccolo comando atomico per un singolo robot invece dell'intero albero delle impostazioni - il server calcola quali robot combinati sono anch'essi coinvolti, persiste su disco, e trasmette autonomamente a ogni altro client connesso. Enable/Disable si propaga ai fratelli `combinedWith` di un robot nello stesso modo in cui lo fanno Play/Pause/Stop, poiché tutti condividono lo stesso calcolo dei robot coinvolti.
- **Widget di Gestione delle Emergenze** (`widget/GlobalStopWidget.kt`) - **Widget per la Schermata Home** dedicato per la sicurezza critica. Fornisce un pulsante **E-STOP Globale** ad alta visibilità e accesso istantaneo per bloccare tutte le operazioni robotiche dello sciame senza dover aprire l'app - attende in modo affidabile che l'elenco dei robot venga effettivamente caricato prima di agire, anche da un avvio a freddo completo (processo non ancora in esecuzione).
- **Aptica e Sicurezza Industriale** (`ui/ControlScreen.kt`) - Sistema avanzato di feedback sensoriale. Dispone di una vera **Protezione da Pressione Prolungata** sui pulsanti E-STOP e STOP (un tocco rapido non fa nulla se non un breve vibrazione + suggerimento; solo una pressione prolungata genuina invia il comando) e firme aptiche differenziate (impulsi di Successo, Errore ed Emergenza) per fornire conferma fisica all'operatore in ambienti rumorosi.
- **Toolchain e Qualità del Progetto** - AGP 9.3.1, Kotlin 2.2.10, Gradle 9.7.0, compileSdk 36, **JDK 21** (`compileOptions`, `gradle-daemon-jvm.properties`, e sia i file di progetto `.idea/` sia `.vscode/` puntano effettivamente ad esso, non solo questa riga di documentazione). Output di build pulito senza warning, varianti di produzione R8 ottimizzate, e test avanzati di screenshot con **Roborazzi**.

**Stato: Wi-Fi, Bluetooth, Biometria e Notifiche implementati.** L'app è una console industriale di alto livello pronta per operazioni robotiche di missione critica.

## 🚀 Compilazione

Richiede **specificamente un JDK 21** e l'Android SDK.

1. Installa [Android Studio](https://developer.android.com/studio).
2. Apri la root del progetto e attendi il completamento della sincronizzazione Gradle.
3. Collega un dispositivo e premi ▶️ Run, oppure usa gli script sottostanti.

### 🛠️ Script di Build + Installazione

Il percorso più rapido da un terminale nella root del repository - compila l'APK di debug, elenca i dispositivi collegati via `adb`, e lo installa in un unico passaggio:

```bash
./build-android.sh     # Linux/macOS
build-android.bat      # Windows
```

Se `adb` non è nel `PATH`, lo script completa comunque la build e stampa dove è finito l'APK così può essere installato manualmente.

### ⚙️ Build Manuale

Passaggi equivalenti senza gli script, per CI o un terminale semplice:

```bash
./gradlew assembleDebug        # Linux/macOS
gradlew.bat assembleDebug      # Windows
```

L'APK finisce in `app/build/outputs/apk/debug/app-debug.apk`. Installalo con `adb install -r -d app/build/outputs/apk/debug/app-debug.apk`, oppure trasferiscilo manualmente sul dispositivo. Sostituisci `assembleDebug` con `assembleRelease` per una build di release - attualmente firma con la chiave di debug (il blocco `release` proprio di `app/build.gradle.kts`, mantenuto così per facilitare i test), quindi si installa correttamente ma non è pronta per la distribuzione così com'è.

## 🔢 Versionamento

Questo repository segue una politica a livello di ecosistema: la versione aumenta automaticamente ad **ogni build reale**, senza modifiche manuali a `versionName`/`versionCode` in `app/build.gradle.kts`. `app/version.properties` conserva i valori attuali di `versionMajor`/`versionMinor`/`versionPatch`/`versionCode`; `app/build.gradle.kts` li legge, li incrementa e riscrive il file al momento della **configurazione** di Gradle - che avviene ad ogni build reale (`assembleDebug`, `compileDebugKotlin`, una sincronizzazione dell'IDE, ...) - così l'APK prodotto porta sempre un numero strettamente superiore all'ultimo:

- **Patch, stile contachilometri (base 10):** +1 ad ogni build; se supererebbe 9, si azzera e la minor sale di +1 - esempio: `0.0.9` -> `0.1.0`. La major non viene mai toccata automaticamente.
- **`versionCode`:** un contatore monotono semplice, +1 ad ogni build, senza riporto - Android richiede che aumenti sempre ad ogni build che viene effettivamente distribuita.

La versione in esecuzione è visibile in tempo reale nella finestra **Informazioni** (`BuildConfig.VERSION_NAME`, che legge lo stesso `versionName` appena calcolato da Gradle). Vedi [CHANGELOG.md](CHANGELOG.md) per la cronologia delle versioni.

## 📲 Test contro un server reale

1. Avvia il backend: `cd HYDRA-UMC-SERVER && npm run dev` (Porta 3000) - questa è la vera API REST/WS con cui parla l'app (vedi Progetti Correlati più sotto); il comando `npm run dev` di `HYDRA-UMC-STUDIO` avvia solo il suo dev server Vite del frontend (porta 5173) contro questo stesso backend, non è il server dell'API in sé.
2. Collega il tuo dispositivo Android alla stessa Wi-Fi.
3. Usa il **Selettore Globale del Server** oppure inserisci l'IP manualmente nell'header.
4. **Biometria:** Attiva "Biometric Login" nel tuo Profilo Utente per saltare la schermata della password al prossimo avvio.

## 🩺 Risoluzione dei Problemi

| Sintomo | Causa | Soluzione |
|---|---|---|
| Nessuna Notifica | Permesso negato | Concedi il permesso "Notifiche" nelle impostazioni Android per questa app |
| Nessuna Biometria | Hardware non impostato | Assicurati di avere un'Impronta/Volto registrato nella Sicurezza di Sistema Android |
| Il robot non si muove | Collegamento cerebrale del browser | Tieni aperta una scheda del browser con HYDRA-UMC STUDIO per l'elaborazione IK |
| Bluetooth disattivato | Chip fisico spento | Usa il pulsante 3D "ENABLE SYSTEM BT" nell'app |

## 📂 Struttura del Repository

```text
HYDRA-UMC-ANDROID-CONTROL/
├── app/
│   ├── build.gradle.kts          # Configurazione Gradle del modulo app - versioni AGP/Kotlin/Compose, dipendenze, tipo di build release firmato con la chiave di debug
│   ├── version.properties        # Versione app stile contachilometri + versionCode Android, sincronizzati da bump_manifest_version.py/bump_version_code.py
│   ├── proguard-rules.pro        # Regole di riduzione/offuscamento del codice per il build di release
│   └── src/main/
│       ├── AndroidManifest.xml   # Permessi, dichiarazioni activity/receiver, usesCleartextTraffic (server LAN HTTP semplice, senza TLS)
│       ├── java/com/hydraumc/control/
│       │   ├── MainActivity.kt          # Punto di ingresso - splash, gating login/schermata principale, gestione dell'E-STOP globale sicura in avvio a freddo
│       │   ├── MainScreen.kt            # Struttura di navigazione inferiore, top bar (selettore server, profilo, telemetria, impostazioni)
│       │   ├── kinematics/
│       │   │   └── Parol6Kinematics.kt   # Cinematica diretta/inversa specifica del Parol6
│       │   ├── model/
│       │   │   ├── BleDevice.kt          # Data class per il risultato della scansione Bluetooth LE
│       │   │   └── HydraState.kt         # Specchio campo per campo di settings.json (RobotView/ControllerView/JobView) + modello di discovery ServerInfo
│       │   ├── network/
│       │   │   ├── AuthPrefs.kt           # Archiviazione cifrata (AES256-GCM) di credenziali/sessione
│       │   │   ├── ConnectionPrefs.kt     # IP/porta del server persistiti (DataStore Preferences)
│       │   │   ├── Discovery.kt           # Scansione concorrente della sottorete /24 (primaria) + listener NSD/mDNS (secondario) per trovare un server sulla LAN
│       │   │   ├── HydraApiClient.kt      # Client REST - login, lettura/scrittura impostazioni, comandi atomici robot, metriche di sistema
│       │   │   ├── HydraBleClient.kt      # Client GATT Bluetooth, trasporto alternativo al Wi-Fi
│       │   │   ├── HydraWebSocket.kt      # Invio live dei delta di stato via WS, gestione riconnessione
│       │   │   └── StateCache.kt          # Cache dell'ultimo stato noto (DataStore) per la visualizzazione della dashboard offline
│       │   ├── ui/
│       │   │   ├── AboutDialog.kt          # Dialogo con info app/versione
│       │   │   ├── CameraScreen.kt         # Feed camera MJPEG per robot + interruttore visione on/off
│       │   │   ├── ControlScreen.kt        # Controlli jog manuali, E-STOP/play/pause/stop con protezione da pressione prolungata
│       │   │   ├── DashboardScreen.kt      # Selettore robot a carosello 3D + salute del sistema + matrice dei moduli
│       │   │   ├── Joystick3D.kt           # Componente joystick riutilizzabile a 2 assi
│       │   │   ├── LoginScreen.kt          # Inserimento username/password + IP/porta, login biometrico
│       │   │   ├── MjpegPlayer.kt          # Parser dello stream MJPEG + renderer Canvas
│       │   │   ├── NativeThreeDScreen.kt   # Visore 3D nativo con Google Filament - non ancora collegato alla navigazione, nessuna pipeline .glb
│       │   │   ├── PlaybackConsole.kt      # Console flottante condivisa di E-STOP/play/pause/stop
│       │   │   ├── SettingsScreen.kt       # UI di scansione Wi-Fi/Bluetooth, impostazioni di connessione
│       │   │   ├── SplashScreen.kt         # Schermata di avvio Compose personalizzata
│       │   │   ├── TelemetryScreen.kt      # Visualizzatore di log eventi/sincronizzazione in stile terminale
│       │   │   ├── ThreeDScreen.kt         # Viewport 3D reale - WebView che incorpora la scena 3D headless propria di STUDIO
│       │   │   ├── UserProfileDialog.kt    # Dialogo di modifica profilo + interruttore biometrico
│       │   │   └── theme/
│       │   │       ├── Color.kt, Theme.kt, Typography.kt   # Schema colori Material 3, wrapper del tema, scala tipografica
│       │   │       └── HydraButton.kt, IndustrialComponents.kt, IndustrialStyle.kt   # Blocchi UI condivisi con stile industriale
│       │   ├── update/
│       │   │   ├── GitHubReleaseUpdater.kt   # Client sicuro di aggiornamento tramite GitHub Release
│       │   │   ├── ReleaseMetadataParser.kt  # Parser sicuro dei metadati di GitHub Release
│       │   │   └── SemanticVersion.kt        # Parser rigoroso della versione semantica per gli aggiornamenti
│       │   ├── util/
│       │   │   ├── BiometricHelper.kt      # Wrapper del prompt di androidx.biometric
│       │   │   ├── NotificationHelper.kt   # Notifiche push di lavoro completato/sicurezza
│       │   │   └── NotificationPrefs.kt    # Archiviazione persistente dell'interruttore delle notifiche in-app
│       │   ├── viewmodel/
│       │   │   ├── AppUpdateViewModel.kt   # Stato di aggiornamento dell'app consapevole del ciclo di vita
│       │   │   └── RobotViewModel.kt   # ViewModel condiviso - rete, autenticazione, discovery, dispatch dei comandi atomici, tutto lo stato UI
│       │   ├── wear/
│       │   │   ├── WatchCompanionProtocol.kt    # Contratto wire dello stato versione del Watch companion
│       │   │   ├── WatchVoiceRelayContract.kt   # Contratto wire autenticato del relay vocale del Watch
│       │   │   └── WatchVoiceRelayService.kt    # Servizio relay vocale per Wear OS
│       │   └── widget/
│       │       └── GlobalStopWidget.kt # Widget della schermata home per un E-STOP globale senza aprire l'app
│       └── res/
│           ├── drawable/, layout/, mipmap*/, xml/   # Icone, layout del widget, icone del launcher, regole di backup/estrazione dati
│           └── values/, values-es/, values-de/, values-fr/, values-it/, values-ja/, values-zh/   # Stringhe in 7 lingue, colori, tema
├── docs/
│   ├── ARCHITECTURE.md              # Note di design/architettura
│   ├── GITHUB_RELEASE_UPDATES.md    # Flusso in-app di verifica/download/installazione aggiornamenti
│   └── WATCH_VOICE_RELAY.md         # Contratto del relay vocale watch-telefono-server
├── images/                       # Asset sorgente del banner del README + schermata di avvio
├── tools/
│   ├── build_test.py             # Controllo build/compilazione senza incremento di versione
│   └── ci_validate.py            # Validazione manifest/CHANGELOG/docs usata dalla CI
├── dist/                         # Output dell'APK di release firmato (ignorato da git)
├── build-android.bat / .sh       # Script di comodità build + installazione adb in un unico passaggio
├── build-test.bat / .sh          # Controllo build/compilazione senza incremento di versione
├── prepare-github-release.bat / .sh  # Compila un APK di release firmato privatamente e stabile, senza incrementare la versione
├── publish-github-release.ps1 / .sh  # Solo locale: pubblica l'APK di dist/ come GitHub Release
├── bump_manifest_version.py      # Sincronizza la versione di hydra-umc.project.json con quella nativa (--sync)
├── bump_version_code.py          # Incrementa il contatore versionCode proprio di Android in app/version.properties
├── gradlew, gradlew.bat          # Wrapper di Gradle
├── build.gradle.kts, settings.gradle.kts, gradle.properties   # Configurazione root del progetto Gradle
├── local.properties              # Percorso locale dell'Android SDK (specifico della macchina, non committato)
├── keystore.properties.example   # Template di configurazione privata per la firma di release
├── .env.example                  # Esempio di variabili d'ambiente
├── metadata.json                 # Metadati della scheda store (nome/descrizione)
├── README.md                     # Questo file
├── README_spa.md / README_ita.md / README_fra.md / README_deu.md / README_zho.md / README_jpn.md   # Traduzioni
└── LICENSE                       # GPL-3.0
```

## 🔗 Progetti Correlati

Questo progetto fa parte dell'ecosistema robotico HYDRA-UMC dello stesso autore (JuanenRac / Electro Hobby 3D). Vale la pena conoscerlo, poiché una richiesta potrebbe in realtà riguardare uno di questi invece di questo repository.

**Progetto Padre**
- **[HYDRA-UMC-SERVER](https://github.com/JuanenRac/HYDRA-UMC-SERVER)** — il vero backend headless (REST/WebSocket) con cui parla davvero ogni client di controllo; il backend contro cui girano la scoperta, l'autenticazione e la sincronizzazione WebSocket proprie di questa app.

**Progetti Fratelli** — parlano anch'essi con la stessa API di HYDRA-UMC-SERVER, ciascuno come proprio client
- **[HYDRA-UMC-STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO)** — dashboard di controllo web con visualizzazione 3D multi-robot in tempo reale; il proprio viewport 3D è incorporato direttamente nella schermata Vista 3D di questa app tramite WebView.
- **[HYDRA-UMC-SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE)** — centro di comando sciame desktop (PySide6) per più server contemporaneamente, pacchettizzato come eseguibile standalone; parla esattamente lo stesso contratto `REMOTE_API.md` di questa app.
- **[HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL)** — app di controllo per iOS/iPadOS (Flutter) con sincronizzazione WebSocket in tempo reale; la controparte diretta iOS/iPadOS di questa app, con lo stesso set di funzionalità.
- **[HYDRA-UMC-DSI](https://github.com/JuanenRac/HYDRA-UMC-DSI)** — interfaccia touch nativa per il touchscreen DSI da 7" a bordo, incorporata direttamente nel CM5.
- **[HYDRA-UMC-BRIDGE-AMR](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-AMR)** — barriera di coordinamento per flotte AGV/AMR tramite un publisher MQTT VDA 5050 reale.
- **[HYDRA-UMC-BRIDGE-CNC](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-CNC)** — coordinatore ad alto livello per celle CNC con accesso reale a stato/byte di controllo GRBL.
- **[HYDRA-UMC-BRIDGE-DROIDS](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-DROIDS)** — barriera di coordinamento per droidi con zampe/umanoidi, con un vero mittente di comandi per Boston Dynamics Spot.
- **[HYDRA-UMC-BRIDGE-LASER](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-LASER)** — coordinatore di sicurezza per celle laser che legge 3 salvaguardie GPIO reali di chiave/involucro/interblocco.
- **[HYDRA-UMC-BRIDGE-OPENPNP](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-OPENPNP)** — coordinatore ad alto livello sicuro per il flusso schede del pick-and-place OpenPnP.
- **[HYDRA-UMC-BRIDGE-PRINTER3D](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-PRINTER3D)** — barriera di coordinamento sicura per stampanti 3D Moonraker/Klipper, con comandi di lavoro reali e controllati.
- **[HYDRA-UMC-BRIDGE-ROS2](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-ROS2)** — coordinatore di sicurezza con un vero trasporto ROS 2 rclpy, importato in modo lazy.
- **[HYDRA-UMC-BRIDGE-UAV](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-UAV)** — barriera di coordinamento per UAV dotati di fotocamera, con un vero mittente di comandi MAVLink.

**Direttamente Correlati**
- **[HYDRA-UMC-WATCH](https://github.com/JuanenRac/HYDRA-UMC-WATCH)** — app companion WearOS con avvisi aptici reali e un relay vocale verso il telefono abbinato; la companion WearOS di questa app, per lo stato del robot a colpo d'occhio e il controllo dal polso.
- **[HYDRA-UMC-HIL-BRIDGE](https://github.com/JuanenRac/HYDRA-UMC-HIL-BRIDGE)** — vero interblocco di sicurezza hardware-in-the-loop che instrada i comandi tra simulazione e hardware reale; consente il controllo remoto del gemello digitale direttamente da questa app.

**Fa Anche Parte dell'Ecosistema**

*Hardware e Piattaforma di Base*
- **[HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC)** — la scheda madre fisica del braccio robotico: host CM5 + coprocessore STM32H745 dual-core, che coordina fino a 8 bracci utensile via CAN-OTA/SPI-OTA.
- **[HYDRA-UMC-OS](https://github.com/JuanenRac/HYDRA-UMC-OS)** — livello prodotto riproducibile su Raspberry Pi OS per il CM5: agente in sola lettura, config/profili validati, provisioning WiFi al primo contatto.
- **[HYDRA-UMC-SDK](https://github.com/JuanenRac/HYDRA-UMC-SDK)** — il contratto JSON-Schema condiviso e la barriera di sicurezza contro cui ogni bridge valida i propri comandi.

*Backend Centrale e Client*
- **[HYDRA-UMC-EDITOR-URDF](https://github.com/JuanenRac/HYDRA-UMC-EDITOR-URDF)** — creatore/editor grafico desktop di URDF che invia i modelli finiti al catalogo di STUDIO.

*Piattaforma Strumenti URTC*
- **[URTC](https://github.com/JuanenRac/URTC)** — firmware per la scheda fisica dell'Universal Robot Tool Controller, oltre 25 profili utensile su bus CAN.
- **[URTC-FLASHER](https://github.com/JuanenRac/URTC-FLASHER)** — strumento desktop con GUI per il flashing delle schede URTC, CAN-OTA più SWD/JTAG a chip intero.
- **[URTC-TESTER](https://github.com/JuanenRac/URTC-TESTER)** — strumento desktop di diagnostica CAN-bus dal vivo per schede URTC, un pannello per profilo utensile.
- **[URTC-WEB-STUDIO](https://github.com/JuanenRac/URTC-WEB-STUDIO)** — alternativa basata su browser a URTC-TESTER tramite la Web Serial API, senza installazione locale.

*Nodo IA Visione (Hailo-8)*
- **[HYDRA-UMC-VISION-NODE](https://github.com/JuanenRac/HYDRA-UMC-VISION-NODE)** — hub di integrazione per la pipeline di visione Hailo-8, con un vero controllo di prontezza hardware per fase.
- **[HYDRA-UMC-DETECTION-HEF](https://github.com/JuanenRac/HYDRA-UMC-DETECTION-HEF)** — registro reale di modelli compilati con verifica di caricamento sicuro per architettura Hailo/checksum.
- **[HYDRA-UMC-VISION-STREAMER](https://github.com/JuanenRac/HYDRA-UMC-VISION-STREAMER)** — generatore reale di pipeline GStreamer + config MediaMTX, con una vera barriera di integrazione HailoRT.
- **[HYDRA-UMC-VISUAL-SERVOING-API](https://github.com/JuanenRac/HYDRA-UMC-VISUAL-SERVOING-API)** — vera legge di correzione Position-Based Visual Servoing, con cancello di sicurezza sullo stato di zona a monte.
- **[HYDRA-UMC-SAFETY-ZONES](https://github.com/JuanenRac/HYDRA-UMC-SAFETY-ZONES)** — vero controllo di violazione zona e richiesta E-STOP, con imposizione della freschezza di calibrazione.

*Nodo IA Cognitivo (Hailo-10)*
- **[HYDRA-UMC-COGNITIVE-NODE](https://github.com/JuanenRac/HYDRA-UMC-COGNITIVE-NODE)** — hub di integrazione per la pipeline cognitiva Hailo-10 (orchestrazione LLM/VLA/voce).
- **[HYDRA-UMC-VLA-ENGINE](https://github.com/JuanenRac/HYDRA-UMC-VLA-ENGINE)** — vera codifica/decodifica di token d'azione e generazione di traiettoria per un modello Vision-Language-Action.
- **[HYDRA-UMC-VOICE-UI](https://github.com/JuanenRac/HYDRA-UMC-VOICE-UI)** — vero front-end vocale (VAD + parser di intenti) con un relay verso Watch limitato e soggetto a conferma.
- **[HYDRA-UMC-SEMANTIC-PLANNER](https://github.com/JuanenRac/HYDRA-UMC-SEMANTIC-PLANNER)** — vera scomposizione dei task basata su regole e recupero semantico degli errori sui codici errore MCU.
- **[HYDRA-UMC-DOCS-QA](https://github.com/JuanenRac/HYDRA-UMC-DOCS-QA)** — vera ricerca documentale TF-IDF (solo libreria standard) sui documenti Markdown di questo ecosistema.

*Orchestrazione e Sciame*
- **[HYDRA-UMC-ORCHESTRATOR](https://github.com/JuanenRac/HYDRA-UMC-ORCHESTRATOR)** — hub di integrazione con un vero contratto di health-report gRPC/Protobuf e una macchina a stati di missione.
- **[HYDRA-UMC-JOB-DISPATCHER](https://github.com/JuanenRac/HYDRA-UMC-JOB-DISPATCHER)** — vera coda di lavori basata su priorità con deduplicazione, su una vera API HTTP.
- **[HYDRA-UMC-NODE-HEALING](https://github.com/JuanenRac/HYDRA-UMC-NODE-HEALING)** — vero watchdog di salute della flotta basato su gRPC, con retry/backoff e rilevamento di discrepanza d'identità.
- **[HYDRA-UMC-PATH-PLANNER-3D](https://github.com/JuanenRac/HYDRA-UMC-PATH-PLANNER-3D)** — vero pianificatore di percorsi 3D basato su RRT, con vera validazione delle collisioni ostacolo/spazio di lavoro.
- **[HYDRA-UMC-SWARM-SYNC](https://github.com/JuanenRac/HYDRA-UMC-SWARM-SYNC)** — vera sincronizzazione di stato CRDT LWW-Element-Map, con property test per la convergenza multi-cella.

*Gemello Digitale e Simulazione*
- **[HYDRA-UMC-TWIN](https://github.com/JuanenRac/HYDRA-UMC-TWIN)** — hub di integrazione per il motore di gemello digitale, con un vero contratto di sincronizzazione per compatibilità di versione.
- **[HYDRA-UMC-PHYSICS-REPLICA](https://github.com/JuanenRac/HYDRA-UMC-PHYSICS-REPLICA)** — vera cinematica diretta e validazione dei limiti articolari su un vero sottoinsieme URDF.
- **[HYDRA-UMC-SYNTHETIC-DATA-GEN](https://github.com/JuanenRac/HYDRA-UMC-SYNTHETIC-DATA-GEN)** — vero generatore procedurale di scene 2D con esportazione di annotazioni YOLO/COCO.

*Dati e Analisi*
- **[HYDRA-UMC-DATALAKE](https://github.com/JuanenRac/HYDRA-UMC-DATALAKE)** — vero archivio di serie temporali basato su sqlite3, con una vera API HTTP di ingestione/query.
- **[HYDRA-UMC-ANOMALY-DETECTOR](https://github.com/JuanenRac/HYDRA-UMC-ANOMALY-DETECTOR)** — vero rilevatore di anomalie FFT + baseline statistica, con monitoraggio della deriva.
- **[HYDRA-UMC-PRODUCTION-REPORTS](https://github.com/JuanenRac/HYDRA-UMC-PRODUCTION-REPORTS)** — vero calcolo OEE/disponibilità sullo storico di DATALAKE, con esportazione CSV riproducibile.
- **[HYDRA-UMC-TELEMETRY-COLLECTOR](https://github.com/JuanenRac/HYDRA-UMC-TELEMETRY-COLLECTOR)** — vera pipeline di ingestione CAN/WebSocket verso DATALAKE, con deduplicazione per sequenza.

*Gateway Industriale*
- **[HYDRA-UMC-GATEWAY-INDUSTRIAL](https://github.com/JuanenRac/HYDRA-UMC-GATEWAY-INDUSTRIAL)** — hub di integrazione che inoltra ai protocolli industriali, con un vero livello di allowlist dei comandi/backpressure.
- **[HYDRA-UMC-OPCUA-SERVER](https://github.com/JuanenRac/HYDRA-UMC-OPCUA-SERVER)** — vero spazio di indirizzi OPC-UA, verificato con una vera sessione client del protocollo binario.
- **[HYDRA-UMC-MQTT-BROKER](https://github.com/JuanenRac/HYDRA-UMC-MQTT-BROKER)** — vero broker MQTT con autenticazione opzionale per client e ACL sui topic.
- **[HYDRA-UMC-MTCONNECT-ADAPTER](https://github.com/JuanenRac/HYDRA-UMC-MTCONNECT-ADAPTER)** — veri endpoint XML `/probe` e `/current` di MTConnect, con output in modalità degradata.

*Strumenti Complementari e Operazioni dell'Ecosistema*
- **[HYDRA-UMC-DASHBOARD-AI](https://github.com/JuanenRac/HYDRA-UMC-DASHBOARD-AI)** — pannelli Smart Summaries e Anomaly Highlighting su DATALAKE/ANOMALY-DETECTOR, con un fallback statistico onesto.
- **[HYDRA-UMC-TOOL-CLI](https://github.com/JuanenRac/HYDRA-UMC-TOOL-CLI)** — CLI di flotta con un vero e stabile contratto di exit-code, un client live reale della stessa API di HYDRA-UMC-SERVER.
- **[URTC-SMART-RACK](https://github.com/JuanenRac/URTC-SMART-RACK)** — firmware per un rack di montaggio schede con decodifica reale dell'ID utensile e logica di preriscaldamento Smart Idle.
- **[URTC-VISION-TOOL](https://github.com/JuanenRac/URTC-VISION-TOOL)** — firmware più un vero companion di visione Python per una testa utensile di ispezione termica/RGB.
- **[HYDRA-UMC-UPDATER](https://github.com/JuanenRac/HYDRA-UMC-UPDATER)** — strumento amministrativo desktop che scopre, clona e aggiorna ogni repository di questo ecosistema.

## 👤 AUTORE
**JuanenRac** (Electro Hobby 3D)
📧 electrohobby3d@gmail.com
📺 [youtube.com/@electrohobby3d](https://youtube.com/@electrohobby3d)

## 📜 LICENZA

**GNU General Public License v3.0 (GPL-3.0)** per il codice sorgente - vedi [`LICENSE`](LICENSE).

Questa documentazione (questo README e le sue traduzioni - `README_spa.md`, `README_ita.md`, `README_fra.md`, `README_deu.md`, `README_zho.md`, `README_jpn.md`) è disponibile sotto **Creative Commons Attribution-ShareAlike 4.0 International (CC BY-SA 4.0)**. Testo completo su https://creativecommons.org/licenses/by-sa/4.0/.
