<!-- =============================================================================
HYDRA-UMC-ANDROID-CONTROL - Android operator console for a HYDRA-UMC server: control, cameras, 3D view and a global STOP
Copyright (C) JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
GPL-3.0-or-later - see LICENSE
============================================================================= -->

<p align="center">
  <img src="images/HYDRA_UMC_BANNER.svg" alt="HYDRA-UMC-ANDROID-CONTROL banner" width="100%">
</p>

# 📱 HYDRA-UMC-ANDROID-CONTROL

<p align="center"><a href="README.md">🇺🇸 English</a> | <a href="README_spa.md">🇪🇸 Español</a> | <a href="README_fra.md">🇫🇷 Français</a> | 🇮🇹 <b>Italiano</b> | <a href="README_deu.md">🇩🇪 Deutsch</a> | <a href="README_zho.md">🇨🇳 简体中文</a> | <a href="README_jpn.md">🇯🇵 日本語</a> | <a href="README_por.md">🇵🇹 Português</a></p>

### I tuoi robot HYDRA-UMC in tasca: trova il server, muovi e guarda i robot e ferma tutto con un pulsante rosso.

<p align="center">
  <img src="https://img.shields.io/badge/License-GPL%203.0--or--later-blue.svg" alt="GPL 3.0 or later">
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF.svg" alt="Language">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg" alt="UI">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84.svg" alt="Platform">
  <img src="https://img.shields.io/badge/Tests-303%20passing-2ea44f.svg" alt="Tests">
  <img src="https://img.shields.io/badge/Version-0.7.4-0f6cbd.svg" alt="Version">
  <img src="https://img.shields.io/badge/Maturity-functional-0F6CBD.svg" alt="Maturity">
</p>

---

Un'app Android nativa (Kotlin + Jetpack Compose) che controlla un robot sulla piattaforma [HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC) via Wi-Fi o Bluetooth, parlando esattamente lo stesso contratto [`REMOTE_API.md`](https://github.com/JuanenRac/HYDRA-UMC-SERVER/blob/main/docs/REMOTE_API.md) usato da [HYDRA-UMC SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE) - discovery, lettura/scrittura dello stato completo, e sincronizzazione live via WebSocket con un backend [HYDRA-UMC-SERVER](https://github.com/JuanenRac/HYDRA-UMC-SERVER) in esecuzione (lo stesso con cui parla la dashboard web di [HYDRA-UMC STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO)). Controparte Android diretta di [HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL). Vedi [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) per il design completo.

> **Verifica di onestà - cosa funziona davvero oggi:** la logica di business principale - la cinematica Parol6 (`kinematics/Parol6Kinematics.kt`), il contratto di stato dell'albero di settings (`model/HydraState.kt`), l'invio di comandi atomici e il relay vocale (`viewmodel/RobotViewModel.kt`), la riconnessione WebSocket (`network/HydraWebSocket.kt`), il parsing dei frame MJPEG (`ui/MjpegPlayer.kt`), il canale di aggiornamento interno (`update/GitHubReleaseUpdater.kt`, `ReleaseMetadataParser.kt`, `SemanticVersion.kt`), e la gestione delle richieste del relay vocale Wear (`wear/BoundedRequestScope.kt`, `WatchCompanionProtocol.kt`) sono reali e coperti da 303 test unitari JVM/Robolectric che passano (`./gradlew testDebugUnitTest`). Ciò che questi test strutturalmente non possono raggiungere - e ciò che questo ambiente di sviluppo solo-Windows non ha un dispositivo Android fisico per verificare - è il vero `EncryptedSharedPreferences` basato su Keystore di `network/AuthPrefs.kt`, il prompt impronta/volto di `androidx.biometric`, il trasporto Bluetooth GATT di `network/HydraBleClient.kt`, e il percorso `ImageDecoder` hardware in API 28+ dentro `MjpegStreamParser`: codice reale e compilato che non è stato riverificato su hardware da quando è stato scritto. L'avvio/login di base *è stato* confermato dal vivo su un dispositivo reale per release precedenti (vedi le voci `v0.3.2`/`v0.3.3` di `CHANGELOG.md`), ma questo precede Bluetooth, biometria e il percorso di decodifica hardware. Vedi `CHANGELOG.md` per cosa è stato esattamente consegnato, incluse le sue stesse note per voce su cosa resta non verificato su hardware.

> **Stato di sicurezza (safety e security):** questo ecosistema non è un sistema di sicurezza certificato. Non esiste ancora un percorso E-STOP hardware, né limiti o interblocchi imposti dal firmware, né un comando di movimento verificato; le immagini firmware sono controllate con CRC-32 e una firma Ed25519, e una compilazione di rilascio rifiuta le chiavi di sviluppo pubblicate (le teste utensile e le schede di espansione dietro il relè usano ancora i bootloader di URTC, che accettano un HMAC-SHA256 a chiave condivisa invece di una firma); TLS e TLS reciproco sono opzionali e disattivati per impostazione predefinita; nessuna scheda propria è stata ancora assemblata o provata. Leggi la [base di sicurezza](https://github.com/JuanenRac/HYDRA-UMC-DOCS/blob/main/docs/SECURITY_BASELINE.md) e il [modello delle minacce](https://github.com/JuanenRac/HYDRA-UMC-DOCS/blob/main/docs/THREAT_MODEL.md) prima di fidarti di qualsiasi affermazione qui per la sicurezza di una persona.

---

## 📋 In breve

| | |
|---|---|
| Versione | `0.7.4` |
| Maturità | Funzionale |
| Test | 303 (`./gradlew testDebugUnitTest`) |
| Tecnologia | Kotlin / Jetpack / biometric auth / WebSocket client |
| Destinazione | Uno smartphone o tablet Android |
| Su hardware reale | Non ancora: per questa versione non risulta alcuna esecuzione su hardware reale |

**Vai a:** [Panoramica](#-panoramica) · [Architettura](#-architettura) · [Funzionalità](#-funzionalità) · [Sicurezza e protezione](#-sicurezza-e-protezione) · [Interfacce](#-interfacce) · [Configurazione](#-configurazione) · [Avvio rapido](#-avvio-rapido) · [Test](#-test) · [Risoluzione dei problemi](#-risoluzione-dei-problemi) · [Struttura del repository](#-struttura-del-repository) · [Progetti correlati](#-progetti-correlati)

## 🚦 Stato del progetto

Ogni riga è una parte di questo repository e il semaforo dice a che punto è oggi. È scritta in base a ciò che il repository può mostrare (test, compilazioni, esecuzioni), non a ciò che è previsto.

🟢 Fatto e verificato · 🟡 Funziona, con limiti noti · 🟠 Fatto a metà o non ancora verificato · 🔴 Non fatto o non funzionante

| Area | Stato | Cosa significa qui |
|---|---|---|
| Software | 🟡 | Funziona per il suo scopo principale; la verifica di onestà qui sopra elenca ciò che manca ancora |
| Test | 🟢 | 303 test automatici, eseguiti a ogni modifica |
| Hardware reale | 🔴 | Non ancora: per questa versione non risulta alcuna esecuzione su hardware reale |
| Documentazione | 🟢 | README in otto lingue, verificato rispetto al manifesto a ogni modifica |

## 🎯 Panoramica

Schermate dell'app: **accesso** (indirizzo del server, password, impronta o volto), **pannello** (un carosello 3D dei robot con la loro matrice di moduli e la salute del modulo di calcolo), **controllo** (joystick, avvia, pausa, ferma, prenotazione del robot), **telecamere** (MJPEG dal vivo con picture-in-picture), **vista 3D** (la scena reale di STUDIO), **telemetria** (un registro in stile terminale) e **impostazioni** (connessione, sicurezza, notifiche, aggiornamenti). Un pulsante rosso **Ferma tutto** è nella barra superiore di ogni schermata e in un widget della schermata iniziale.

## 🧩 Ruolo in HYDRA-UMC

**HYDRA-UMC-ANDROID-CONTROL** è la console operatore nativa Android di HYDRA-UMC: trova un HYDRA-UMC-SERVER sulla rete locale, accede, poi mostra i robot, li muove, guarda le loro telecamere e una vista 3D, e ferma tutto con un tocco. Parla lo stesso contratto remoto della SUITE e dell'app iOS e conserva una copia dell'ultimo stato per quando manca il Wi-Fi. Non è un dispositivo di sicurezza.

## 🏛️ Architettura

<p align="center">
  <img src="images/architecture.svg" alt="HYDRA-UMC-ANDROID-CONTROL architecture" width="100%">
</p>

## ✨ Funzionalità

- **Controllo Accessi e Biometria** (`ui/LoginScreen.kt`, `util/BiometricHelper.kt`) - Sistema di login professionale con supporto per **Impronta digitale e Sblocco con il Volto** (`androidx.biometric`), più campi IP/porta direttamente nella stessa schermata così un server può essere selezionato senza passare prima dalle Impostazioni. Include la funzione "Ricordami", un meccanismo di **Logout** sicuro, ed è completamente localizzato in **8 lingue**. Nome utente/password/token in cache (`network/AuthPrefs.kt`) risiedono in **EncryptedSharedPreferences** basate su Keystore (AES256-GCM), non in chiaro - un server di sviluppo (`NODE_ENV` non impostato) crea un account predefinito `admin`/`admin` al primo avvio; un server di produzione (`NODE_ENV=production`) richiede invece `HYDRA_UMC_BOOTSTRAP_ADMIN_USERNAME`/`HYDRA_UMC_BOOTSTRAP_ADMIN_PASSWORD` e non ricade mai silenziosamente su `admin`/`admin` - vedere il README di HYDRA-UMC-SERVER per la configurazione completa di produzione. Account aggiuntivi a privilegio inferiore di tipo **operator** restano creabili lato server da Config > Users in entrambi i casi.
- **Modalità Offline e Cache dello Stato** (`network/StateCache.kt`) - Motore di persistenza integrato che usa **DataStore**. L'app mette automaticamente in cache l'ultimo stato noto del sistema, consentendo la visualizzazione istantanea della dashboard e audit di configurazione anche senza una connessione Wi-Fi attiva.
- **Notifiche e Avvisi di Missione** (`util/NotificationHelper.kt`) - Sistema di allerta. Invia notifiche push ad alta priorità quando un robot completa una sequenza di lavoro o in caso di eventi hardware critici, garantendo che l'operatore sia informato anche quando l'app è in background.
- **Terminale di Telemetria Industriale** (`ui/TelemetryScreen.kt`) - Un visualizzatore di log in tempo reale dedicato, con un'interfaccia in stile terminale. Traccia gli eventi di sistema, la sincronizzazione REST/WebSocket, e fornisce diagnostica a colori (Verde Matrix per il successo, Rosso Industriale per gli errori).
- **Dashboard Avanzata** (`ui/DashboardScreen.kt`) - **Carosello Orizzontale 3D** ad alta fedeltà con effetti di scorrimento prospettico. Mostra metadati del robot arricchiti: **Produttore** (Source Robotics, Annin, Universal Robots, AgileX, ecc.), **Ruolo del Robot** (CNC, Laser, PnP), e una **Matrice dei Moduli Industriale** con stato live per i moduli CAM, XY, ATC, PNP, CNC, LSR, BED, VAC e RCK.
- **Monitor di Salute del Sistema** (`ui/DashboardScreen.kt`) - Metriche in tempo reale per la Compute Module 5 collegata, incluso **Hostname**, **Uptime Formattato** (es. "2d 4h 15m"), e conteggi attivi di controller e robot.
- **Controllo Manuale Avanzato** (`ui/ControlScreen.kt`) - Presenta un layout verticale professionale con **pulsanti Joystick il 50% più grandi** per la massima precisione.
- **Pannello di Riproduzione** (`ui/ControlScreen.kt`, `ui/PlaybackConsole.kt`) - Barra di controllo inferiore fissa che ospita il pulsante rosso **STOP** (lo STOP globale qui sotto) e i pulsanti **Avvia**, **Pausa** e **Ferma** (solo il robot selezionato). Questi controlli sono sempre visibili e offrono **Feedback Aptico** per la conferma sensoriale fisica.
- **STOP globale** (`ui/GlobalStop.kt`, `network/HydraApiClient.kt`, `viewmodel/RobotViewModel.kt`) - Un pulsante rosso **STOP** (didascalia "Ferma tutto", tradotta in 8 lingue) nella barra superiore di ogni schermata, nella console della schermata Controllo e nella vista 3D (verticale e a schermo intero). Un tocco, senza tenere premuto e senza conferma: invia subito `POST /api/stop-all` e ferma tutti i robot, anche se riservati da un altro account. Mostra il risultato (quali robot si sono fermati, quali no, quali sono riservati da qualcun altro e un avviso se il server non ha potuto salvare lo stato di arresto), riprova una volta se nessuno risponde, ferma i robot uno a uno se il server limita la chiamata (429) e mantiene un avviso persistente **STOP NON CONFERMATO** finché uno STOP successivo non riesce. È lo stop software del server: non è un E-STOP hardware e non toglie l'alimentazione a nulla.
- **Prenotazioni e limiti di sicurezza** (`ui/AccessPanel.kt`, `network/AccessModels.kt`, `network/HydraApiClient.kt`, `viewmodel/RobotViewModel.kt`) - La schermata Control mostra chi tiene il robot selezionato (nessuno, questo telefono, lo stesso account su un altro dispositivo o un altro account) e offre **Prenota** e **Rilascia**, oltre all’elenco di tutte le prenotazioni del server (`GET /api/claims`). Il telefono conserva un identificativo di controller per installazione e lo invia con ogni prenotazione, rilascio, armamento del jog e comando (corpo e intestazione `X-Controller-Id`): la prenotazione appartiene quindi all’account e a questo telefono, non a tutto l’account. I rifiuti del server sono distinti: prenotato da un altro account, dal tuo account su un altro dispositivo o finestra, identificativo di controller richiesto e jog non armato. I limiti del modello del robot si leggono da `GET /api/safety-limits`: un passo di jog oltre il limite non viene inviato e gli avvisi del server sono mostrati. Quando l’amministratore lo richiede, bisogna premere **Arma il jog** (con conto alla rovescia) prima che il joystick muova il robot; l’armamento termina da solo, con STOP o al rilascio. Le prenotazioni sono facoltative: un robot che nessuno ha prenotato si comanda come prima. Solo per un **amministratore** compare un pulsante **Prendi il controllo** su un robot detenuto da un altro; chiede conferma e poi prenota il robot con `force: true` (il server lo accetta solo dagli amministratori e ricontrolla il ruolo). Le scritture sono condizionali: ognuna porta la revisione del robot che questa app ha visto per ultima (`If-Match`, `network/ContractV2.kt`); se nel frattempo qualcun altro ha modificato il robot, il server risponde 412, non viene applicato nulla, il robot viene riletto e il messaggio lo dice (Standard verifica prenotazioni, tavola e comandi singoli; Rigoroso verifica tutto tranne STOP; Disattivato non verifica nulla; la scelta è nell'elenco delle prenotazioni; STOP non è mai verificato). Il jog della tavola XY è un unico comando atomico `table` (un server più vecchio riceve i jog che capisce), il server invia le prenotazioni sul WebSocket così la schermata Control smette di interrogare mentre arrivano, e gli amministratori leggono il registro di audit (`ui/AuditDialog.kt`) con la catena SHA-256 verificata due volte, dal server e sul telefono, e possono condividerne le righe come `.jsonl`. PLAY chiede prima conferma (il robot, i suoi punti registrati, la velocità e chi lo ha prenotato), a meno che la persona non lo disattivi tra le prenotazioni.
- **Vista 3D** (`ui/ThreeDScreen.kt`) - Incorpora il viewport 3D in tempo reale proprio di HYDRA-UMC STUDIO in una WebView (`?hideUI=true&robotId=#token=`), anziché un renderer nativo. L'approccio WebView ottiene gratuitamente la scena 3D reale e attualmente in produzione di STUDIO (ogni mesh/cinematica reale di ogni robot) invece di reimplementarla nativamente - il compromesso è l'overhead di rendering della WebView, non ottimale per la batteria, ma funzionalmente completo oggi.
- **Visione Ottale in Tempo Reale** (`ui/CameraScreen.kt`, `ui/MjpegPlayer.kt`) - **Streamer MJPEG Nativo**. Dispone di un parser in background e di un renderer basato su Canvas per video a bassa latenza, uno stato chiaro "Camera Disabilitata" (invece di un feed vuoto silenzioso) quando il sistema di visione di un robot è spento, e un interruttore per accendere/spegnere la camera di un robot direttamente dal server. Supporta overlay automatici **Picture-in-Picture (PIP)** nella schermata di controllo manuale, mappati su robot specifici tramite la configurazione delle camere del server. Su API 28+, ogni fotogramma viene decodificato tramite il proprio `ImageDecoder` accelerato via hardware della piattaforma (un bitmap residente in GPU, senza la decodifica/copia lato CPU), con un fallback automatico e auto-risanante alla decodifica CPU originale.
- **Discovery e Connettività Intelligente** (`network/Discovery.kt`, `network/HydraApiClient.kt`, `network/HydraWebSocket.kt`) - Scansione concorrente della sottorete contro ogni host candidato nella /24 del telefono stesso (includendo l'IP LAN del telefono stesso e localhost, non solo gli altri host), interrogando `GET /api/hydra-info` e identificando un server reale unicamente tramite la presenza di `remoteApiVersion` - lo stesso controllo usato dal percorso IP manuale, così un server il cui proprietario lo ha rinominato rispetto alla stringa di prodotto predefinita viene comunque trovato. Un listener **NsdManager** (mDNS/Bonjour) gira in parallelo - il server si annuncia effettivamente come `_hydra._tcp` (`bonjour-service`), e `MainActivity` richiede in anticipo il permesso runtime di posizione/dispositivi vicini necessario per questo (una dichiarazione nel manifest da sola non lo concede mai su API 23+) - ma la scansione della sottorete resta il percorso primario poiché la raggiungibilità multicast su Wi-Fi è intrinsecamente meno affidabile di una semplice sonda HTTP. L'app attiva automaticamente il WiFi all'avvio, scansiona la rete locale della fabbrica, ed esegue un **Auto-connessione Zero-Click** al primo server HYDRA-UMC disponibile.
- **Accesso con JWT** (`network/HydraApiClient.kt`, `ui/LoginScreen.kt`) - Accesso con **JWT (JSON Web Tokens)**. Ogni comando di controllo (Jog, Play e lo stop globale del server) viene validato dal server tramite token firmati, inviati sull'endpoint atomico `POST /api/robot/:id/command` (vedi Sincronizzazione dei Comandi Atomici più sotto) - funziona sia con il ruolo `admin` che `operator`, a differenza di una scrittura completa su `POST /api/settings` (solo admin lato server). Ogni richiesta porta anche un header `X-Hydra-Client: android` così la scheda Config > Remote Access del server stesso può consentire/bloccare questa app indipendentemente da SUITE/iOS. Integrato senza soluzione di continuità con l'**Autenticazione Biometrica** (Impronta/Volto) per il rinnovo sicuro dei token. Un WebSocket chiuso con il codice `1008` (token non valido/scaduto) viene trattato come "accedi di nuovo", senza essere ritentato in un ciclo di riconnessione (`network/HydraWebSocket.kt`).
- **Sincronizzazione dei Comandi Atomici** (il `sendAtomicCommand()` proprio di `viewmodel/RobotViewModel.kt`) - Ogni scrittura (play/pause/stop/jog/jogStep/valve/pump/speed/vision/tool) invia un piccolo comando atomico per un singolo robot invece dell'intero albero delle impostazioni - il server calcola quali robot combinati sono anch'essi coinvolti, persiste su disco, e trasmette autonomamente a ogni altro client connesso. Enable/Disable si propaga ai fratelli `combinedWith` di un robot nello stesso modo in cui lo fanno Play/Pause/Stop, poiché tutti condividono lo stesso calcolo dei robot coinvolti.
- **Widget STOP globale** (`widget/GlobalStopWidget.kt`) - **Widget per la Schermata Home** dedicato per l'accesso rapido. Fornisce un pulsante **STOP globale** molto visibile e ad accesso immediato - la stessa chiamata di sopra: il tocco porta l'app in primo piano e invia lo stop non appena esiste la connessione al server, anche da un avvio a freddo completo (processo non attivo), e mostra STOP NON CONFERMATO se non riesce a raggiungere il server. Lo stesso fa l'azione della notifica. È lo stop software del server: non è un E-STOP hardware e non toglie l'alimentazione a nulla.
- **Feedback aptico** (`ui/ControlScreen.kt`) - Sistema avanzato di feedback sensoriale. Lo STOP globale risponde a un tocco con una doppia vibrazione immediata; il pulsante Ferma di ogni robot mantiene la sua **Protezione da Pressione Prolungata** (un tocco rapido dà solo una breve vibrazione + un suggerimento; solo una pressione prolungata invia il comando). Firme aptiche differenziate (impulsi di Successo, Errore ed Emergenza) forniscono conferma fisica all'operatore in ambienti rumorosi.
- **Canale di Aggiornamento In-App** (`update/GitHubReleaseUpdater.kt`, `update/ReleaseMetadataParser.kt`, `update/SemanticVersion.kt`) - Controlla l'ultima GitHub Release stabile all'avvio e da **Impostazioni → Aggiornamenti**; scarica solo l'asset esatto `HYDRA-UMC-ANDROID-CONTROL-release.apk` da un tag non-bozza e non-prerelease, senza mai installare automaticamente - il package installer di Android chiede il consenso finale. Contratto completo: [`docs/GITHUB_RELEASE_UPDATES.md`](docs/GITHUB_RELEASE_UPDATES.md).
- **Bluetooth LE (GATT)** (`network/HydraBleClient.kt`, `network/BleContract.kt`, `network/AndroidBleGatt.kt`) - Un vero client GATT che segue un contratto documentato ([`docs/BLE_CONTRACT.md`](docs/BLE_CONTRACT.md)): ricerca filtrata sul servizio HYDRA, connessione con negoziazione dell'MTU, verifica del servizio, notifiche, messaggi suddivisi in frame nei due sensi, una scrittura alla volta, riconnessione automatica con attese crescenti, permessi di Android 12+ (`BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT`) e un messaggio chiaro nella lingua dell'utente per ogni errore. **Nessun dispositivo HYDRA-UMC implementa ancora il lato periferica:** il client è testato solo contro un GATT simulato, la parte Android non è mai stata eseguita su un telefono e i comandi al robot non passano dal Bluetooth.
- **TLS opzionale** (`network/TlsSettings.kt`, `network/HydraHttp.kt`, `ui/SecuritySettings.kt`) - **Impostazioni → Sicurezza** sceglie Automatico (predefinito, il comportamento di sempre), Richiedi TLS o Solo non cifrato, e può fissare l'impronta (SHA-256) del certificato del server o fidarsi di una CA propria. Una sessione non cifrata verso un host fuori dalla rete locale mostra un avviso rosso sopra tutte le schermate. La vista 3D incorporata non segue ancora queste regole. Provato con handshake reali contro un server locale; mai contro un vero server HYDRA-UMC con TLS.
- **Nessun traffico non cifrato fuori dalla rete locale** (`network/TlsSettings.kt`, `PlainRemoteGuard`) - l'app rifiuta HTTP/WS non cifrato verso un server che non è nella rete locale, a meno che **Impostazioni → Sicurezza → Consenti traffico non cifrato fuori dalla rete locale** sia attivo (disattivato per impostazione predefinita). La vista 3D incorporata riceve il token di sessione nel frammento dell'indirizzo (`#token=`), mai nella query, così nessun log di server o proxy lo registra. I backup automatici dei dati dell'app sono disattivati.
- **Companion Wear OS Abbinato e Relay Vocale** (`wear/WatchVoiceRelayService.kt`, `wear/WatchCompanionProtocol.kt`) - Inoltra un turno vocale già riconosciuto e avviato dall'utente dall'app abbinata [HYDRA-UMC-WATCH](https://github.com/JuanenRac/HYDRA-UMC-WATCH) tramite la propria sessione Server autenticata di questa app verso `HYDRA-UMC-VOICE-UI`, restituendo una risposta tipizzata all'orologio; né questo relay né la card di stato del sistema dell'orologio emettono mai un comando robot né toccano `HydraState` direttamente - una risposta relativa al movimento è esplicitamente marcata `requiresConfirmation` e deve passare attraverso una UI di controllo primaria. Contratto completo: [`docs/WATCH_VOICE_RELAY.md`](docs/WATCH_VOICE_RELAY.md).
- **Assistente Vocale in App** (`ui/VoiceAssistantDialog.kt`) - Un'icona del microfono nella barra superiore apre una finestra di dialogo per digitare una domanda/un ordine, o usare il riconoscimento vocale proprio di questo telefono (`RecognizerIntent`, lo stesso schema reale già usato dal pulsante vocale dell'orologio). Invia la trascrizione riconosciuta tramite la stessa rotta `POST /api/voice/turn` usata dal relay dell'orologio, ma come traffico ordinario di questo telefono (`HydraApiClient.postVoiceTurn()`) anziché un relay - così Config > Accesso Remoto può consentire/bloccare questo pulsante indipendentemente dall'orologio abbinato. Non carica mai l'audio, e una risposta non emette mai un comando robot direttamente.
- **Toolchain e Qualità del Progetto** - AGP 9.3.1, Kotlin 2.2.10, Gradle 9.7.0, compileSdk 36, **JDK 21** (`compileOptions`, `gradle-daemon-jvm.properties`, e sia i file di progetto `.idea/` sia `.vscode/` puntano effettivamente ad esso, non solo questa riga di documentazione). Output di build pulito senza warning, varianti di produzione R8 ottimizzate, e test avanzati di screenshot con **Roborazzi**.

**Stato: Wi-Fi, Bluetooth, Biometria e Notifiche implementati.** L'app è una console industriale per l'operatore; il Bluetooth è ancora sperimentale e nessuno schermo sostituisce un pulsante di emergenza fisico.

### Chi si connette e opzioni di accesso del server

- **Chi si connette al server:** un pulsante Connessioni del pannello di accesso apre un elenco semplice delle app che il server ha visto (tipo, utente, versione, rete locale o dall'esterno, sessioni aperte ora, accessi riusciti o rifiutati, quanto tempo fa) e degli ultimi accessi, accessi rifiutati, uscite e sessioni terminate, letti dalle route delle connessioni del server. Un amministratore vede tutte le voci, qualunque altro account solo le proprie; nessun elenco contiene password o token e un indirizzo compare solo se al server è stato detto di conservarlo. L'app lo dice chiaramente quando il server è troppo vecchio per offrirli.
- **Opzioni di accesso del server nella schermata di sicurezza (solo amministratori):** sotto la sicurezza di connessione dell'app, sette interruttori letti dalla configurazione del server: la route di salute senza accesso, l'annuncio dell'arresto globale, la conservazione dell'indirizzo di ogni client nella cronologia delle connessioni, le immagini della telecamera senza ticket, un token nell'indirizzo del WebSocket, l'oscuramento dei segreti nel registro e la firma di una conferma vocale. Ciascuno mostra da dove viene il suo valore (predefinito, salvato da una schermata o fissato dall'ambiente del server, che lo blocca), si invia solo ciò che è cambiato, ogni modifica che abbassa la protezione chiede prima conferma e un server che non offre un'opzione non la mostra.

## 🔒 Sicurezza e protezione

| Minaccia | Difesa | Dove |
|---|---|---|
| Un token rubato dal telefono | Il token di accesso e quello di rinnovo sono conservati con EncryptedSharedPreferences (AES-256-GCM per valore, chiave principale nel Keystore Android); il backup automatico dei dati dell'app è disattivato (`allowBackup=false`) | `network/AuthPrefs.kt`, `AndroidManifest.xml` |
| Traffico non cifrato verso un server fuori dalla rete locale | `PlainRemoteGuard` rifiuta HTTP e WebSocket non cifrati verso un tale server, salvo che Impostazioni > Sicurezza > Consenti traffico in chiaro fuori dalla rete locale sia attivo (disattivato per impostazione predefinita); una sessione non cifrata mostra un avviso rosso sopra ogni schermata | `network/TlsSettings.kt`, `ui/SecuritySettings.kt` |
| Un server falso | TLS facoltativo con tre modalità (Automatica, Richiedi TLS, Solo testo in chiaro); con un'impronta SHA-256 fissata si accetta solo un certificato con esattamente quell'impronta, e in alternativa si può considerare attendibile una CA personalizzata | `network/TlsSettings.kt`, `network/HydraHttp.kt` |
| Un aggiornamento falso | Si accetta solo l'asset chiamato `HYDRA-UMC-ANDROID-CONTROL-release.apk` dell'ultima release stabile che non sia una bozza; l'app non installa mai da sola, l'installer di Android chiede il consenso | `update/ReleaseMetadataParser.kt` |
| Un robot mosso da due persone, o uno stop che non arriva | Il robot viene prenotato prima di muoverlo e lo schermo mostra chi lo detiene; lo stop globale è inviato con timeout brevi, ritentato una volta, ricade su uno stop per robot e mantiene uno stato persistente «STOP NON CONFERMATO» se non è stato consegnato | `ui/GlobalStop.kt`, `network/AccessModels.kt`, `network/StopAllResult.kt` |

**Cosa non fa mai:** sostituire un arresto di emergenza fisico. Lo STOP è lo stop software del server e non toglie l'alimentazione a nulla.

## 🔌 Interfacce

| Topic o rotta | Cosa porta |
|---|---|
| `POST /api/login`, `/api/logout`, `/api/refresh` | accedere, uscire e rinnovare la sessione |
| `GET /api/hydra-info` | riconosce un vero server da `remoteApiVersion`; usato dalla scansione della sottorete e dall'indirizzo manuale |
| `GET /api/settings`, `/api/ecosystem/status`, `/api/system/metrics`, `/api/safety-limits`, `/api/claims` | lo stato dei robot, della famiglia, del modulo di calcolo, i limiti di sicurezza e le prenotazioni |
| `POST /api/robot/{id}/command`, `/claim`, `/release`, `/jog-arm` | un piccolo comando per un robot, la sua prenotazione e l'armamento di un movimento manuale |
| `POST /api/stop-all` | lo STOP globale: tutti i robot, anche quelli prenotati da altri |
| `GET /api/audit`, `/api/camera/ticket`, `POST /api/voice/turn`, `GET /api/watch/system-status` | il registro di audit (amministratori), un ticket per il flusso della telecamera, un turno vocale e la scheda di stato dell'orologio |
| WebSocket `hydra.bearer` | cambiamenti di stato in tempo reale inviati dal server; il token è nel sottoprotocollo e l'app si riconnette ogni 3 secondi |
| Bluetooth LE GATT | il contratto di `docs/BLE_CONTRACT.md`: messaggi incorniciati, una scrittura alla volta, permessi di Android 12; nessun dispositivo HYDRA-UMC implementa ancora l'altro lato |

## ⚙️ Configurazione

L'app non ha un file di configurazione: tutto si imposta sullo schermo e si salva sul telefono. I valori predefiniti e i limiti vengono dal codice.

| Variabile | Predefinito | Cosa imposta |
|---|---|---|
| Impostazioni > Sicurezza > Modalità TLS | Automatica | Automatica, Richiedi TLS o Solo testo in chiaro; impronta SHA-256 fissata o CA personalizzata opzionali |
| Impostazioni > Sicurezza > Consenti traffico in chiaro fuori dalla rete locale | disattivato | permette all'app di parlare HTTP e WebSocket non cifrati con un server fuori dalla rete locale |
| Indirizzo e porta del server | `3000` | digitato nella schermata di accesso, o trovato dalla scansione della sottorete /24 del telefono (1,5 s per host, 16 alla volta) più mDNS |
| Rinnovo della sessione | 20 s / 3 / 10 min | l'app rinnova la sessione da sola al massimo 3 volte in 10 minuti e a distanza di almeno 20 s; oltre chiede di nuovo l'accesso |
| Impostazioni > Notifiche | attivato | avvisi quando un robot termina un lavoro o si verifica un evento critico (due canali: missioni e sicurezza) |

## 🚀 Avvio rapido

Richiede JDK 21 e l'SDK Android (Android Studio li include entrambi), e un telefono o emulatore con Android 7.0 (API 24) o successivo.

```bash
./build-android.sh           # builds the debug APK and installs it by adb (build-android.bat on Windows)
./gradlew assembleDebug      # only builds; the APK lands in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest  # runs the unit tests (gradlew.bat on Windows)
```

## 🧪 Test

303 test automatici si eseguono con `./gradlew testDebugUnitTest`. Non richiedono hardware né nulla al di fuori di questa macchina.

La CI esegue `HYDRA_UMC_CI=1 python tools/ci_validate.py`, che controlla il manifesto, i documenti, gli otto README e le immagini.

### Test contro un server reale

1. Avvia il backend: `cd HYDRA-UMC-SERVER && npm run dev` (Porta 3000) - questa è la vera API REST/WS con cui parla l'app (vedi Progetti Correlati più sotto); il comando `npm run dev` di `HYDRA-UMC-STUDIO` avvia solo il suo dev server Vite del frontend (porta 5173) contro questo stesso backend, non è il server dell'API in sé.
2. Collega il tuo dispositivo Android alla stessa Wi-Fi.
3. Usa il **Selettore Globale del Server** oppure inserisci l'IP manualmente nell'header.
4. **Biometria:** Attiva "Biometric Login" nel tuo Profilo Utente per saltare la schermata della password al prossimo avvio.

## 🏗️ Compilazione e rilascio

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

L'APK finisce in `app/build/outputs/apk/debug/app-debug.apk`. Installalo con `adb install -r -d app/build/outputs/apk/debug/app-debug.apk`, oppure trasferiscilo manualmente sul dispositivo. Sostituisci `assembleDebug` con `assembleRelease` per una build di release - firma con la chiave di release privata quando `keystore.properties` (o le variabili d'ambiente di firma della release) è presente, altrimenti ripiega sulla chiave di debug, che si installa senza problemi per i test ma non va mai distribuita.

### Versionamento

Questo repository segue una politica a livello di ecosistema: compilare non cambia mai la versione. `app/version.properties` contiene i valori correnti di `versionMajor`/`versionMinor`/`versionPatch`/`versionCode`; solo il comando `bump` esplicito li alza (`python tools/hydra_project_tool.py bump`: `bump_manifest_version.py` per la versione nativa, il manifesto `hydra-umc.project.json` e `CHANGELOG.md`, e `bump_version_code.py` per il `versionCode` di Android, separato e sempre monotono). `app/build.gradle.kts` *legge* soltanto `version.properties` e non lo scrive mai.

- **Patch, stile contachilometri (base 10):** +1 a ogni bump; se supererebbe 9, si azzera e la minor sale di +1 - esempio: `0.0.9` -> `0.1.0`. La major non viene mai toccata automaticamente.
- **`versionCode`:** un contatore monotono semplice, +1 a ogni bump, senza riporto - Android richiede che aumenti sempre ad ogni build che viene effettivamente distribuita.

Un semplice `./gradlew assembleDebug` (il percorso "Compilazione Manuale" sopra, senza eseguire prima alcuno script di incremento) riutilizza quanto già presente in `version.properties` - non incrementa nulla da solo. La versione in esecuzione è visibile in tempo reale nella finestra **Informazioni** (`BuildConfig.VERSION_NAME`, che legge l'ultimo `versionName` presente in `version.properties`). Vedi [CHANGELOG.md](CHANGELOG.md) per la cronologia delle versioni.

## 🩺 Risoluzione dei problemi

| Sintomo | Causa | Soluzione |
|---|---|---|
| Nessuna Notifica | Permesso negato | Concedi il permesso "Notifiche" nelle impostazioni Android per questa app |
| Nessuna Biometria | Hardware non impostato | Assicurati di avere un'Impronta/Volto registrato nella Sicurezza di Sistema Android |
| Il robot non si muove | Sessione scaduta, robot riservato da un altro account o traiettoria in corso | Leggi il messaggio nella barra di stato; il server riproduce da solo le traiettorie e non serve nessuna scheda del browser |
| Bluetooth disattivato | Chip fisico spento | Usa il pulsante 3D "ENABLE SYSTEM BT" nell'app |

## 📂 Struttura del repository

<p align="center">
  <img src="images/repository-structure.svg" alt="Repository structure of HYDRA-UMC-ANDROID-CONTROL" width="100%">
</p>

<details>
<summary>Versione testuale dell'albero</summary>

```text
HYDRA-UMC-ANDROID-CONTROL/
├── app/src/main/java/com/hydraumc/control/
│   ├── MainActivity.kt                                          Punto di ingresso - splash, gating login/schermata principale, gestione dell'E-STOP globale sicura in avvio a freddo
│   ├── MainScreen.kt                                            Struttura di navigazione inferiore, top bar (selettore server, profilo, telemetria, impostazioni)
│   ├── kinematics/                                              cinematica diretta e inversa del braccio PAROL6
│   ├── model/                                                   lo stato del server come lo legge l'app
│   ├── network/                                                 client REST, WebSocket e individuazione
│   ├── ui/                                                      schermate e widget
│   ├── update/                                                  il canale di aggiornamento dell'app
│   ├── util/                                                    regole pure dell'app
│   ├── viewmodel/                                               lo stato condiviso che ogni schermata ascolta
│   ├── wear/                                                    relay di voce e stato
│   └── widget/                                                  il widget STOP della schermata iniziale
├── docs/                                                        documenti del progetto
├── images/                                                      Asset sorgente del banner del README + schermata di avvio
├── tools/                                                       strumenti del progetto e controlli della CI
├── .github/                                                     workflow di CI e modelli di segnalazione
├── gradle/                                                      file del wrapper Gradle
├── bump_manifest_version.py                                     Sincronizza la versione di hydra-umc.project.json con quella nativa (--sync)
├── bump_version_code.py                                         Incremento del contatore versionCode di Android (eseguito solo dal comando `bump` esplicito)
├── gradlew / gradlew.bat                                        Wrapper di Gradle
├── build.gradle.kts / settings.gradle.kts / gradle.properties   Configurazione root del progetto Gradle
├── keystore.properties.example                                  Template di configurazione privata per la firma di release
├── .env.example                                                 Esempio di variabili d'ambiente
├── LICENSE                                                      GPL-3.0-or-later
├── CHANGELOG.md                                                 cosa è cambiato in ogni versione
├── build-test.sh                                                controllo di build senza modifiche
├── hydra-umc.project.json                                       manifesto: versione, maturità, famiglia
├── test.sh                                                      controllo di build senza modifiche
├── prepare-github-release.sh / prepare-github-release.bat       compila l'APK di release firmato in privato; non cambia nessuna versione
├── publish-github-release.sh / publish-github-release.ps1       pubblica quell'APK come release GitHub della versione corrente
├── app/version.properties                                       versione e versionCode
└── app/src/test/                                                test unitari JVM
```

</details>

## 🗺️ Roadmap e limiti noti

Il Bluetooth LE resta sperimentale finché un dispositivo non implementa il lato periferico di `docs/BLE_CONTRACT.md`. TLS e l'impronta fissata sono stati provati con handshake reali contro un server di prova locale, non contro un server HYDRA-UMC con TLS. La vista 3D incorporata non segue ancora le impostazioni TLS. L'archiviazione nel Keystore, la richiesta biometrica e il trasporto Bluetooth non sono stati ricontrollati su hardware da quando sono stati scritti.

## 🌍 Lingue

Questo README e i documenti del repository esistono in otto lingue: inglese, spagnolo, tedesco, francese, italiano, giapponese, cinese e portoghese (Portogallo). Sono scritti insieme e la CI verifica che gli otto file abbiano le stesse sezioni e gli stessi link.

## 🔗 Progetti correlati

Questo progetto fa parte dell'ecosistema robotico HYDRA-UMC dello stesso autore (JuanenRac / Electro Hobby 3D). Vale la pena conoscerlo, poiché una richiesta potrebbe in realtà riguardare uno di questi invece di questo repository.

**Progetto Padre**
- **[HYDRA-UMC-SERVER](https://github.com/JuanenRac/HYDRA-UMC-SERVER)** — il vero backend headless (REST/WebSocket) con cui parla davvero ogni client di controllo; il backend contro cui girano la scoperta, l'autenticazione e la sincronizzazione WebSocket proprie di questa app.

**Progetti Fratelli** — parlano anch'essi con la stessa API di HYDRA-UMC-SERVER, ciascuno come proprio client
- **[HYDRA-UMC-STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO)** — dashboard di controllo web con visualizzazione 3D multi-robot in tempo reale; il proprio viewport 3D è incorporato direttamente nella schermata Vista 3D di questa app tramite WebView.
- **[HYDRA-UMC-SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE)** — centro di comando sciame desktop (PySide6) per più server contemporaneamente, pacchettizzato come eseguibile standalone; parla esattamente lo stesso contratto `REMOTE_API.md` di questa app.
- **[HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL)** — app di controllo per iOS/iPadOS (Flutter) con sincronizzazione WebSocket in tempo reale; la controparte diretta iOS/iPadOS di questa app, con lo stesso set di funzionalità.
- **[HYDRA-UMC-DSI](https://github.com/JuanenRac/HYDRA-UMC-DSI)** — interfaccia touch nativa per il touchscreen DSI da 7" a bordo, incorporata direttamente nel CM5.
- **[HYDRA-UMC-BRIDGE-AMR](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-AMR)** — barriera di coordinamento per flotte AGV/AMR tramite un publisher MQTT VDA 5050 (testato solo contro una flotta emulata).
- **[HYDRA-UMC-BRIDGE-CNC](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-CNC)** — coordinatore ad alto livello per celle CNC con accesso a stato/byte di controllo GRBL (testato solo contro un emulatore).
- **[HYDRA-UMC-BRIDGE-DROIDS](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-DROIDS)** — barriera di coordinamento per droidi con zampe/umanoidi, con un mittente di comandi per Boston Dynamics Spot (testato solo contro un emulatore).
- **[HYDRA-UMC-BRIDGE-LASER](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-LASER)** — coordinatore per celle laser che legge 3 segnali GPIO di chiave/involucro/interblocco (testato solo contro un chip simulato; non può armare né far partire un laser).
- **[HYDRA-UMC-BRIDGE-OPENPNP](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-OPENPNP)** — coordinatore ad alto livello sicuro per il flusso schede del pick-and-place OpenPnP.
- **[HYDRA-UMC-BRIDGE-PRINTER3D](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-PRINTER3D)** — barriera di coordinamento sicura per stampanti 3D Moonraker/Klipper, con comandi di lavoro reali e controllati.
- **[HYDRA-UMC-BRIDGE-ROS2](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-ROS2)** — coordinatore con un trasporto ROS 2 rclpy importato in modo lazy (non ancora eseguito contro un'installazione ROS 2).
- **[HYDRA-UMC-BRIDGE-UAV](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-UAV)** — barriera di coordinamento per UAV dotati di fotocamera, con un mittente di comandi MAVLink (testato solo contro un emulatore).

**Direttamente Correlati**
- **[HYDRA-UMC-WATCH](https://github.com/JuanenRac/HYDRA-UMC-WATCH)** — app companion WearOS con schemi di avviso aptico e un relay vocale verso il telefono abbinato (non ancora eseguita su un orologio fisico); la companion WearOS di questa app, per lo stato del robot a colpo d'occhio e il controllo dal polso.
- **[HYDRA-UMC-HIL-BRIDGE](https://github.com/JuanenRac/HYDRA-UMC-HIL-BRIDGE)** — router hardware-in-the-loop con un interblocco software di collisione tra simulazione e hardware reale (testato senza hardware reale); consente il controllo remoto del gemello digitale direttamente da questa app.

**Fa Anche Parte dell'Ecosistema**

*Hardware e Piattaforma di Base*
- **[HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC)** — il progetto della scheda madre del braccio robotico: host CM5 + coprocessore STM32H745 dual-core, pensato per coordinare fino a 8 bracci utensile via CAN-OTA/SPI-OTA (firmware in fase iniziale; nessuna scheda assemblata per ora).
- **[HYDRA-UMC-OS](https://github.com/JuanenRac/HYDRA-UMC-OS)** — livello prodotto riproducibile su Raspberry Pi OS per il CM5: agente in sola lettura, config/profili validati, provisioning WiFi al primo contatto.
- **[HYDRA-UMC-SDK](https://github.com/JuanenRac/HYDRA-UMC-SDK)** — il contratto JSON-Schema condiviso e la barriera software di controllo dei job contro cui ogni bridge valida i propri comandi (non è una funzione di sicurezza certificata).
- **[HYDRA-UMC-CONNECTOR-HUB](https://github.com/JuanenRac/HYDRA-UMC-CONNECTOR-HUB)** — registro dichiarativo e validatore di manifesti adattatore per connettori di macchine esterne; estende la propria idea di contratto dell'SDK alle macchine esterne senza sostituire i progetti di gateway industriale.

*Backend Centrale e Client*
- **[HYDRA-UMC-EDITOR-URDF](https://github.com/JuanenRac/HYDRA-UMC-EDITOR-URDF)** — creatore/editor grafico desktop di URDF che invia i modelli finiti al catalogo di STUDIO.
- **[HYDRA-UMC-EDITOR-STL](https://github.com/JuanenRac/HYDRA-UMC-EDITOR-STL)** — editor desktop di modelli STL, che trasforma/sostituisce/rimuove/aggiunge veri pezzi nello stesso catalogo di modelli modificato anche da HYDRA-UMC-EDITOR-URDF.

*Piattaforma Strumenti URTC*
- **[URTC](https://github.com/JuanenRac/URTC)** — firmware per la scheda fisica dell'Universal Robot Tool Controller, oltre 25 profili utensile su bus CAN.
- **[URTC-FLASHER](https://github.com/JuanenRac/URTC-FLASHER)** — strumento desktop con GUI per il flashing delle schede URTC, CAN-OTA più SWD/JTAG a chip intero.
- **[URTC-TESTER](https://github.com/JuanenRac/URTC-TESTER)** — strumento desktop di diagnostica CAN-bus dal vivo per schede URTC, un pannello per profilo utensile.
- **[URTC-WEB-STUDIO](https://github.com/JuanenRac/URTC-WEB-STUDIO)** — alternativa basata su browser a URTC-TESTER tramite la Web Serial API, senza installazione locale.
- **[URTC-GATEWAY](https://github.com/JuanenRac/URTC-GATEWAY)** — gateway davanti al bus CAN di URTC: elenco di identificatori consentiti, limite di frequenza e chiave di sessione; software provato su computer, non ancora su un bus reale.

*Nodo IA Visione (Hailo-8)*
- **[HYDRA-UMC-VISION-NODE](https://github.com/JuanenRac/HYDRA-UMC-VISION-NODE)** — hub di integrazione per la pipeline di visione Hailo-8, con un vero controllo di prontezza hardware per fase.
- **[HYDRA-UMC-DETECTION-HEF](https://github.com/JuanenRac/HYDRA-UMC-DETECTION-HEF)** — registro reale di modelli compilati con verifica di caricamento sicuro per architettura Hailo/checksum.
- **[HYDRA-UMC-VISION-STREAMER](https://github.com/JuanenRac/HYDRA-UMC-VISION-STREAMER)** — generatore reale di pipeline GStreamer + config MediaMTX, con una vera barriera di integrazione HailoRT.
- **[HYDRA-UMC-VISUAL-SERVOING-API](https://github.com/JuanenRac/HYDRA-UMC-VISUAL-SERVOING-API)** — legge di correzione Position-Based Visual Servoing con autorizzazione condizionata allo stato di zona; ancora nessuna stima di posa da telecamera.
- **[HYDRA-UMC-SAFETY-ZONES](https://github.com/JuanenRac/HYDRA-UMC-SAFETY-ZONES)** — logica v0 di violazione zona, freschezza di calibrazione e richiesta E-STOP che legge le posizioni degli oggetti da un file; nessun rilevatore la alimenta e nessun E-STOP fisico è ancora collegato.

*Nodo IA Cognitivo (Hailo-10)*
- **[HYDRA-UMC-COGNITIVE-NODE](https://github.com/JuanenRac/HYDRA-UMC-COGNITIVE-NODE)** — hub di integrazione per la pipeline cognitiva Hailo-10 (orchestrazione LLM/VLA/voce).
- **[HYDRA-UMC-VLA-ENGINE](https://github.com/JuanenRac/HYDRA-UMC-VLA-ENGINE)** — vera codifica/decodifica di token d'azione e generazione di traiettoria per un modello Vision-Language-Action.
- **[HYDRA-UMC-VOICE-UI](https://github.com/JuanenRac/HYDRA-UMC-VOICE-UI)** — vero front-end vocale (VAD + parser di intenti) con un relay verso Watch limitato e soggetto a conferma.
- **[HYDRA-UMC-SEMANTIC-PLANNER](https://github.com/JuanenRac/HYDRA-UMC-SEMANTIC-PLANNER)** — vera scomposizione dei task basata su regole e recupero semantico degli errori sui codici errore MCU.
- **[HYDRA-UMC-DOCS-QA](https://github.com/JuanenRac/HYDRA-UMC-DOCS-QA)** — vera ricerca documentale TF-IDF (solo libreria standard) sui documenti Markdown di questo ecosistema.
- **[HYDRA-UMC-LOCAL-TECHNICIAN](https://github.com/JuanenRac/HYDRA-UMC-LOCAL-TECHNICIAN)** — tecnico di manutenzione IA locale e con cancelli di policy per l'ecosistema stesso - osserva, diagnostica e propone correzioni; i due livelli di rischio più alti non sono deliberatamente ancora implementati.

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
- **[HYDRA-UMC-OS-REBUILDER](https://github.com/JuanenRac/HYDRA-UMC-OS-REBUILDER)** — strumento desktop Windows/Linux che costruisce un'immagine della CM5 pronta da scrivere, precaricata con le versioni più aggiornate dell'ecosistema, con configurazione di primo avvio Wi-Fi/utente/SSH in stile Raspberry Pi Imager.
- **[HYDRA-UMC-OPS-AGENT](https://github.com/JuanenRac/HYDRA-UMC-OPS-AGENT)** — coordinatore di incidenti di manutenzione: un ruolo edge a basso privilegio raccoglie uno snapshot di inventario/salute sanificato, un ruolo control-plane lo rende in sola lettura e chiede a un provider di IA di suggerire una diagnosi - non applica mai una patch né distribuisce nulla.
- **[HYDRA-UMC-DEV-SERVER](https://github.com/JuanenRac/HYDRA-UMC-DEV-SERVER)** — host di sviluppo riproducibile (Raspberry Pi 5 / CM5) che conserva il codice sorgente dell'ecosistema ed esegue attività delimitate di build/test tramite una coda durevole; un ruolo di sviluppo dedicato, esplicitamente distinto da un CM5 operativo.

## 📚 Documentazione

Dove leggere di più:

* [docs/SOURCE_MAP.md](docs/SOURCE_MAP.md) - ogni file sorgente dell'app con una riga su ciò che fa
* [Base di sicurezza dell'ecosistema](https://github.com/JuanenRac/HYDRA-UMC-DOCS/blob/main/docs/SECURITY_BASELINE.md)
* [Modello delle minacce dell'ecosistema](https://github.com/JuanenRac/HYDRA-UMC-DOCS/blob/main/docs/THREAT_MODEL.md)
* [Registro delle modifiche di questo repository](CHANGELOG.md)
* [Licenza di questo repository](LICENSE.md)

## 🤝 Contribuire e comunità

* Prima di una pull request esegui `HYDRA_UMC_CI=1 py -3 tools/ci_validate.py` (oppure `HYDRA_UMC_CI=1 python3 tools/ci_validate.py`): deve stampare `CI_VALIDATION=PASS`. Non eseguire `build.sh` né `build.bat` per questa verifica; compilare non è verificare.
* I otto README sono scritti da un'unica fonte in HYDRA-UMC-DOCS; non modificarli a mano. Cambia la fonte e riscrivili con `tools/make_readmes.py`.
* Non affermare in un README ciò che non è stato eseguito: scrivi «simulato» o «non provato su hardware» quando è la verità.
* Vedi [CONTRIBUTING.md](CONTRIBUTING.md), il [codice di condotta](CODE_OF_CONDUCT.md), [SECURITY.md](SECURITY.md) e [SUPPORT.md](SUPPORT.md).

## 👤 AUTORE

**JuanenRac (Electro Hobby 3D)** · electrohobby3d@gmail.com

## 📜 LICENZA

GPL-3.0-or-later - [LICENSE](LICENSE).
