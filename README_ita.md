<p align="center">
  <img src="images/HYDRA_UMC_ANDROID_CONTROL_BANNER.jpg" alt="HYDRA-UMC Android Control Banner" width="100%">
</p>

# 📱 HYDRA-UMC CONTROL

Un'app Android nativa (Kotlin + Jetpack Compose) che controlla un robot sulla piattaforma [HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC) via Wi-Fi o Bluetooth, parlando esattamente lo stesso contratto [`REMOTE_API.md`](https://github.com/JuanenRac/HYDRA-UMC-STUDIO/blob/main/docs/REMOTE_API.md) usato da [HYDRA-UMC SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE) - discovery, lettura/scrittura dello stato completo, e sincronizzazione live via WebSocket con un server [HYDRA-UMC STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO) in esecuzione. Controparte Android diretta di [HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL). Vedi [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) per il design completo.

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

- **Patch, stile contachilometri (base 10):** +1 ad ogni build; se supererebbe 9, si azzera e la minor sale di +1 - esempio: `1.0.9` -> `1.1.0`. La major non viene mai toccata automaticamente.
- **`versionCode`:** un contatore monotono semplice, +1 ad ogni build, senza riporto - Android richiede che aumenti sempre ad ogni build che viene effettivamente distribuita.

La versione in esecuzione è visibile in tempo reale nella finestra **Informazioni** (`BuildConfig.VERSION_NAME`, che legge lo stesso `versionName` appena calcolato da Gradle). Vedi [CHANGELOG.md](CHANGELOG.md) per la cronologia delle versioni.

## 📲 Test contro HYDRA-UMC STUDIO

1. Avvia il server: `cd HYDRA-UMC-STUDIO && npm run dev` (Porta 3000).
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
│   └── src/main/
│       ├── AndroidManifest.xml   # Permessi, dichiarazioni activity/receiver, usesCleartextTraffic (server LAN HTTP semplice, senza TLS)
│       ├── java/com/hydraumc/control/
│       │   ├── MainActivity.kt          # Punto di ingresso - splash, gating login/schermata principale, gestione dell'E-STOP globale sicura in avvio a freddo
│       │   ├── MainScreen.kt            # Struttura di navigazione inferiore, top bar (selettore server, profilo, telemetria, impostazioni)
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
│       │   │   ├── LoginScreen.kt          # Inserimento username/password + IP/porta, login biometrico
│       │   │   ├── MjpegPlayer.kt          # Parser dello stream MJPEG + renderer Canvas
│       │   │   ├── NativeThreeDScreen.kt   # Visore 3D nativo con Google Filament - non ancora collegato alla navigazione, nessuna pipeline .glb
│       │   │   ├── SettingsScreen.kt       # UI di scansione Wi-Fi/Bluetooth, impostazioni di connessione
│       │   │   ├── SplashScreen.kt         # Schermata di avvio Compose personalizzata
│       │   │   ├── TelemetryScreen.kt      # Visualizzatore di log eventi/sincronizzazione in stile terminale
│       │   │   ├── ThreeDScreen.kt         # Viewport 3D reale - WebView che incorpora la scena 3D headless propria di STUDIO
│       │   │   ├── UserProfileDialog.kt    # Dialogo di modifica profilo + interruttore biometrico
│       │   │   └── theme/
│       │   │       ├── Color.kt, Theme.kt, Typography.kt   # Schema colori Material 3, wrapper del tema, scala tipografica
│       │   │       └── HydraButton.kt, IndustrialComponents.kt, IndustrialStyle.kt   # Blocchi UI condivisi con stile industriale
│       │   ├── util/
│       │   │   ├── BiometricHelper.kt      # Wrapper del prompt di androidx.biometric
│       │   │   └── NotificationHelper.kt   # Notifiche push di lavoro completato/sicurezza
│       │   ├── viewmodel/
│       │   │   └── RobotViewModel.kt   # ViewModel condiviso - rete, autenticazione, discovery, dispatch dei comandi atomici, tutto lo stato UI
│       │   └── widget/
│       │       └── GlobalStopWidget.kt # Widget della schermata home per un E-STOP globale senza aprire l'app
│       └── res/
│           ├── drawable/, layout/, mipmap*/, xml/   # Icone, layout del widget, icone del launcher, regole di backup/estrazione dati
│           └── values/, values-es/, values-de/, values-fr/, values-it/   # Stringhe in 5 lingue, colori, tema
├── docs/
│   └── ARCHITECTURE.md           # Note di design/architettura
├── images/                       # Asset sorgente del banner del README + schermata di avvio
├── build-android.bat / .sh       # Script di comodità build + installazione adb in un unico passaggio
├── gradlew, gradlew.bat          # Wrapper di Gradle
├── build.gradle.kts, settings.gradle.kts, gradle.properties   # Configurazione root del progetto Gradle
├── local.properties              # Percorso locale dell'Android SDK (specifico della macchina, non committato)
├── .env.example                  # Esempio di variabili d'ambiente
├── README.md                     # Questo file
├── README_spa.md / README_ita.md / README_fra.md / README_deu.md   # Traduzioni
└── LICENSE                       # GPL-3.0
```

## 🔗 Progetti Correlati

Questo progetto fa parte di un ecosistema robotico più ampio dello stesso autore (JuanenRac / Electro Hobby 3D). Vale la pena conoscerlo, poiché una richiesta potrebbe in realtà riguardare uno di questi anziché questo repository:

**Piattaforma HYDRA-UMC** — la cella di micro-fabbrica multi-robot
- **[HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC)** — la scheda madre stessa: host Raspberry Pi CM5 + coprocessore real-time STM32H745 dual-core, che orchestra fino a 8 bracci robotici distribuiti via CAN-OTA/SPI-OTA. Hardware + firmware propri, GPL-3.0/CERN-OHL-S v2/CC BY-SA 4.0.
- **[HYDRA-UMC STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO)** — dashboard di controllo web per HYDRA-UMC: visualizzazione 3D multi-robot, registrazione di cinematica/traiettorie, flashing e test CAN-OTA per l'intera piattaforma. React + Vite + Three.js.
- **HYDRA-UMC-ANDROID-CONTROL** *(questo repository)* — app di controllo Android per HYDRA-UMC via Wi-Fi/Bluetooth. App reale e funzionante - set completo di funzionalità di controllo remoto, autenticazione JWT, archiviazione cifrata delle credenziali.
- **[HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL)** — app di controllo iOS/iPadOS per HYDRA-UMC via Wi-Fi, realizzata in Flutter (multipiattaforma, verificabile su Windows senza un Mac; il packaging finale `.ipa` richiede comunque Xcode). App reale e funzionante - stesso set di funzionalità dell'app Android.
- **[HYDRA-UMC-SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE)** — centro di comando desktop (Python/PySide6) per lo sciame: discovery di rete multi-controller, sincronizzazione bidirezionale live, viewport 3D reale del robot, workspace agganciabile in stile Photoshop. Reale e funzionante, non un placeholder.
- **[HYDRA-UMC-EDITOR-URDF](https://github.com/JuanenRac/HYDRA-UMC-EDITOR-URDF)** — creatore/editor grafico desktop (Python/PySide6) di URDF per il catalogo di modelli proprio di questo progetto: recupera file sorgente da GitHub o da una cartella locale, valida la fattibilità dei gradi di libertà (DOF), modifica colore/scala/cinematica con un'anteprima 3D live, e pubblica il risultato finale su un server STUDIO in esecuzione. Reale e funzionante, non un placeholder.
- **[HYDRA-UMC-DSI](https://github.com/JuanenRac/HYDRA-UMC-DSI)** — UI touch nativa in Flutter per il touchscreen DSI da 5"/7" proprio di HYDRA-UMC (1280×720, stessa risoluzione in entrambe le dimensioni) sul Compute Module 5, che controlla questo stesso server direttamente dalla scheda. Scaffold reale e funzionante con tutte le 6 schermate del catalogo (dashboard, controllo manuale, camera, vista 3D semplificata, metriche di sistema, login) collegate al server live; la build reale del target Linux non è ancora stata eseguita su hardware reale (ambiente di lavoro finora solo Windows - vedere il README di quel progetto).

**Piattaforma URTC** — il controller della testa utensile che ogni braccio robotico HYDRA-UMC porta con sé
- **[URTC](https://github.com/JuanenRac/URTC)** — Universal Robot Tool Controller: controller di testa utensile su bus CAN basato su STM32F303, 25 profili utensile completamente implementati, aggiornamento firmware CAN-OTA.
- **[URTC Flasher](https://github.com/JuanenRac/URTC-FLASHER)** — strumento desktop di flashing CAN-OTA + chip completo via SWD/JTAG per schede URTC (Windows/Linux).
- **[URTC Tester](https://github.com/JuanenRac/URTC-TESTER)** — strumento desktop di diagnostica live su bus CAN per schede URTC, un pannello per ogni profilo utensile (Windows/Linux).
- **[URTC Web Studio](https://github.com/JuanenRac/URTC-WEB-STUDIO)** — alternativa basata su browser ai 2 strumenti desktop sopra (Web Serial API + SLCAN), senza necessità di installazione locale.

## 👤 Autore

**JuanenRac** (Electro Hobby 3D)
📧 electrohobby3d@gmail.com
📺 youtube.com/@electrohobby3d

## 📜 Licenza

**GNU General Public License v3.0 (GPL-3.0)** per il codice sorgente - vedi [`LICENSE`](LICENSE).

Questa documentazione (questo README e le sue traduzioni - `README_spa.md`, `README_ita.md`, `README_fra.md`, `README_deu.md`) è disponibile sotto **Creative Commons Attribution-ShareAlike 4.0 International (CC BY-SA 4.0)**. Testo completo su https://creativecommons.org/licenses/by-sa/4.0/.
