<p align="center">
  <img src="images/HYDRA_UMC_BANNER.svg" alt="HYDRA-UMC-ANDROID-CONTROL banner" width="100%">
</p>

# 📱 HYDRA-UMC CONTROL

<p align="center">
  <a href="README.md">🇺🇸 English</a> |
  🇪🇸 <b>Español</b> |
  <a href="README_fra.md">🇫🇷 Français</a> |
  <a href="README_ita.md">🇮🇹 Italiano</a> |
  <a href="README_deu.md">🇩🇪 Deutsch</a> |
  <a href="README_zho.md">🇨🇳 简体中文</a> |
  <a href="README_jpn.md">🇯🇵 日本語</a>
</p>


<p align="left">
  <img src="https://img.shields.io/badge/Licencia-GPL%203.0-blue.svg" alt="GPL 3.0">
  <img src="https://img.shields.io/badge/Lenguaje-Kotlin-7F52FF.svg" alt="Kotlin">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg" alt="Compose">
  <img src="https://img.shields.io/badge/Plataforma-Android-3DDC84.svg" alt="Android">
</p>


Una app Android nativa (Kotlin + Jetpack Compose) que controla un robot en la plataforma [HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC) por Wi-Fi o Bluetooth, hablando exactamente el mismo contrato [`REMOTE_API.md`](https://github.com/JuanenRac/HYDRA-UMC-SERVER/blob/main/docs/REMOTE_API.md) que usa [HYDRA-UMC SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE) - descubrimiento, lectura/escritura de estado completo, y sincronización en vivo por WebSocket contra un backend [HYDRA-UMC-SERVER](https://github.com/JuanenRac/HYDRA-UMC-SERVER) en ejecución (el mismo con el que habla el propio panel web de [HYDRA-UMC STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO)). Contraparte directa en Android de [HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL). Ver [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) para el diseño completo.

## 🏗️ Qué está implementado

- **Control de Acceso y Biometría** (`ui/LoginScreen.kt`, `util/BiometricHelper.kt`) - Sistema de login profesional con soporte de **Huella y Desbloqueo Facial** (`androidx.biometric`), más campos de IP/puerto justo en la misma pantalla para poder apuntar a un servidor sin pasar antes por Ajustes. Incluye la función "Recordarme", un mecanismo seguro de **Cerrar Sesión**, y está totalmente localizado en **5 idiomas**. El usuario/contraseña/token cacheados (`network/AuthPrefs.kt`) viven en **EncryptedSharedPreferences** respaldadas por Keystore (AES256-GCM), no en texto plano - todo servidor de este ecosistema siembra una cuenta `admin`/`admin` por defecto en el primer arranque, con cuentas adicionales de menor privilegio de tipo **operator** creables desde el lado del servidor en Config > Users.
- **Modo Sin Conexión y Caché de Estado** (`network/StateCache.kt`) - Motor de persistencia integrado usando **DataStore**. La app cachea automáticamente el último estado conocido del sistema, permitiendo la visualización instantánea del dashboard y auditorías de configuración incluso sin una conexión Wi-Fi activa.
- **Notificaciones y Alertas de Misión** (`util/NotificationHelper.kt`) - Sistema de alertas de nivel industrial. Envía notificaciones push de alta prioridad cuando un robot completa una secuencia de trabajo o si ocurren eventos críticos de hardware, garantizando que el operador esté informado incluso cuando la app está en segundo plano.
- **Terminal de Telemetría Industrial** (`ui/TelemetryScreen.kt`) - Un visor de registros en tiempo real dedicado con una interfaz de estilo terminal. Rastrea eventos del sistema, sincronización REST/WebSocket, y proporciona diagnósticos codificados por color (Verde Matrix para éxito, Rojo Industrial para errores).
- **Dashboard Avanzado** (`ui/DashboardScreen.kt`) - **Carrusel Horizontal 3D** de alta fidelidad con efectos de deslizamiento en perspectiva. Muestra metadatos enriquecidos del robot: **Fabricante** (Source Robotics, Annin, Universal Robots, AgileX, etc.), **Rol del Robot** (CNC, Láser, PnP), y una **Matriz de Módulos Industrial** con estado en vivo para los módulos CAM, XY, ATC, PNP, CNC, LSR, BED, VAC y RCK.
- **Monitor de Salud del Sistema** (`ui/DashboardScreen.kt`) - Métricas en tiempo real de la Compute Module 5 conectada, incluyendo **Nombre de Host**, **Tiempo Activo Formateado** (p. ej., "2d 4h 15m"), y recuentos activos de controladores y robots.
- **Control Manual Mejorado** (`ui/ControlScreen.kt`) - Cuenta con un diseño vertical profesional con **botones de Joystick 50% más grandes** para máxima precisión. Incluye un **Selector de Trabajo/Trayectoria** para explorar y ejecutar archivos directamente desde el servidor.
- **Panel de Seguridad y Reproducción** (`ui/ControlScreen.kt`) - Barra de control inferior fija que aloja los botones **E-STOP (Parada de Emergencia)**, **Start**, **Pause** y **Stop**. Estos controles siempre están visibles y cuentan con **Retroalimentación Háptica** para confirmación sensorial física.
- **Vista 3D** (`ui/ThreeDScreen.kt`) - Incrusta el propio visor 3D en tiempo real de HYDRA-UMC STUDIO en un WebView (`?hideUI=true&robotId=&token=`), en lugar de un renderizador nativo - `ui/NativeThreeDScreen.kt` es un experimento inacabado con Google Filament (declarado pero no enrutado desde la navegación, y todavía sin carga de recursos `.glb`), mantenido en el árbol para quien lo retome más adelante pero no es la ruta de código activa. El enfoque WebView obtiene gratis la escena 3D real y actualmente en producción de STUDIO (cada malla/cinemática real de cada robot) en vez de reimplementarla de forma nativa - la contrapartida es la sobrecarga de renderizado de WebView, no óptima en batería, pero funcionalmente completa hoy.
- **Visión Octal en Tiempo Real** (`ui/CameraScreen.kt`, `ui/MjpegPlayer.kt`) - **Streamer MJPEG Nativo** de nivel industrial. Cuenta con un parser autónomo en segundo plano y un renderizador basado en Canvas para telemetría de vídeo con latencia cero, un estado claro de "Cámara Desactivada" (en vez de un feed en blanco silencioso) cuando el sistema de visión de un robot está apagado, y un interruptor para encender/apagar la cámara de un robot directamente desde el servidor. Soporta overlays automáticos de **Picture-in-Picture (PIP)** en la pantalla de control manual, mapeados a robots específicos mediante la configuración de cámaras del servidor.
- **Descubrimiento y Conectividad Inteligente** (`network/Discovery.kt`, `network/HydraApiClient.kt`, `network/HydraWebSocket.kt`) - Escaneo concurrente de subred contra todo host candidato en la propia /24 del teléfono (incluyendo la propia IP LAN del teléfono y localhost, no solo los demás hosts), sondeando `GET /api/hydra-info` e identificando un servidor real únicamente por la presencia de `remoteApiVersion` - la misma comprobación que usa la ruta de IP manual, así que un servidor cuyo propietario le cambió el nombre respecto a la cadena de producto por defecto se sigue encontrando. Un listener de **NsdManager** (mDNS/Bonjour) corre en paralelo - el servidor sí se anuncia como `_hydra._tcp` (`bonjour-service`), y `MainActivity` solicita por adelantado el permiso de ubicación/dispositivos cercanos en tiempo de ejecución que esto necesita (una declaración en el manifest por sí sola nunca lo concede en API 23+) - pero el escaneo de subred sigue siendo la ruta principal ya que la alcanzabilidad multicast en Wi-Fi es inherentemente menos fiable que una simple sonda HTTP. La app activa automáticamente el WiFi al arrancar, escanea la red local de la fábrica, y realiza un **Auto-conectado de Cero Clics** al primer servidor HYDRA-UMC disponible.
- **Acceso Industrial Seguro** (`network/HydraApiClient.kt`, `ui/LoginScreen.kt`) - Capa de seguridad profesional usando **JWT (JSON Web Tokens)**. Cada comando de control (Jog, Play, E-STOP) es validado por el servidor usando tokens firmados, enviados a través del endpoint atómico `POST /api/robot/:id/command` (ver Sincronización de Comandos Atómicos abajo) - funciona tanto para el rol `admin` como `operator`, a diferencia de una escritura completa a `POST /api/settings` (solo-admin en el lado del servidor). Cada petición también lleva una cabecera `X-Hydra-Client: android` para que la propia pestaña Config > Remote Access del servidor pueda permitir/bloquear esta app de forma independiente de SUITE/iOS. Integrado sin fisuras con **Autenticación Biométrica** (Huella/Facial) para la renovación segura de tokens. Un WebSocket cerrado con el código `1008` (token inválido/caducado) se trata como "iniciar sesión de nuevo", sin reintentarse en un bucle de reconexión (`network/HydraWebSocket.kt`).
- **Sincronización de Comandos Atómicos** (el propio `sendAtomicCommand()` de `viewmodel/RobotViewModel.kt`) - Cada escritura (enable/disable/play/pause/stop/jog/valve/pump/speed/vision) envía un comando atómico pequeño, de un solo robot, en vez del árbol de ajustes completo - el servidor calcula qué robots combinados también se ven afectados, persiste a disco, y retransmite a cualquier otro cliente conectado por su cuenta. Enable/Disable se propaga a los propios hermanos `combinedWith` de un robot de la misma forma que lo hacen Play/Pause/Stop, ya que todos comparten el mismo cálculo de robots afectados.
- **Widget de Gestión de Emergencias** (`widget/GlobalStopWidget.kt`) - **Widget de Pantalla de Inicio** dedicado para seguridad crítica. Proporciona un botón de **E-STOP Global** de alta visibilidad y acceso instantáneo para congelar todas las operaciones robóticas del enjambre sin necesidad de abrir la app - espera de forma fiable a que el listado de robots realmente cargue antes de actuar, incluso desde un arranque en frío completo (proceso aún no en ejecución).
- **Háptica y Seguridad Industrial** (`ui/ControlScreen.kt`) - Sistema avanzado de retroalimentación sensorial. Cuenta con **Protección de Pulsación Larga** real en los botones E-STOP y STOP (un toque rápido no hace nada salvo un breve zumbido + sugerencia; solo una pulsación sostenida genuina envía el comando) y firmas hápticas diferenciadas (pulsos de Éxito, Error y Emergencia) para proporcionar confirmación física al operador en entornos ruidosos.
- **Toolchain y Calidad del Proyecto** - AGP 9.3.1, Kotlin 2.2.10, Gradle 9.7.0, compileSdk 36, **JDK 21** (`compileOptions`, `gradle-daemon-jvm.properties`, y tanto los archivos de proyecto `.idea/` como `.vscode/` apuntan realmente a él, no solo esta línea de documentación). Salida de compilación limpia sin advertencias, variantes de producción R8 optimizadas, y pruebas avanzadas de capturas de pantalla con **Roborazzi**.

**Estado: Wi-Fi, Bluetooth, Biometría y Notificaciones implementados.** La app es una consola industrial de alto nivel lista para la operación robótica de misión crítica.

## 🚀 Compilación

Requiere **específicamente un JDK 21** y el Android SDK.

1. Instala [Android Studio](https://developer.android.com/studio).
2. Abre la raíz del proyecto y deja que termine la sincronización de Gradle.
3. Conecta un dispositivo y pulsa ▶️ Run, o usa los scripts de abajo.

### 🛠️ Scripts de Compilación + Instalación

La ruta más rápida desde una terminal en la raíz del repositorio - compila el APK de depuración, lista los dispositivos conectados vía `adb`, y lo instala todo en un solo paso:

```bash
./build-android.sh     # Linux/macOS
build-android.bat      # Windows
```

Si `adb` no está en el `PATH`, el script igualmente termina la compilación e imprime dónde quedó el APK para poder instalarlo a mano.

### ⚙️ Compilación Manual

Pasos equivalentes sin los scripts, para CI o una terminal normal:

```bash
./gradlew assembleDebug        # Linux/macOS
gradlew.bat assembleDebug      # Windows
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`. Instálalo con `adb install -r -d app/build/outputs/apk/debug/app-debug.apk`, o transfiérelo al dispositivo manualmente. Cambia `assembleDebug` por `assembleRelease` para una compilación de release - actualmente firma con la clave de debug (el propio bloque `release` de `app/build.gradle.kts`, mantenido así para facilitar las pruebas), así que se instala sin problemas pero no está lista para distribución tal cual.

## 🔢 Versionado

Este repositorio sigue una política a nivel de ecosistema: la versión sube automáticamente en **cada build real**, sin edición manual de `versionName`/`versionCode` en `app/build.gradle.kts`. `app/version.properties` guarda los valores actuales de `versionMajor`/`versionMinor`/`versionPatch`/`versionCode`; `app/build.gradle.kts` los lee, los incrementa y reescribe el archivo en tiempo de **configuración** de Gradle - lo cual ocurre en cada build real (`assembleDebug`, `compileDebugKotlin`, una sincronización del IDE, ...) - de modo que el APK generado siempre lleva un número estrictamente superior al anterior:

- **Patch, estilo cuentakilómetros (base 10):** +1 en cada build; si superaría 9, se resetea a 0 y la minor sube +1 - por ejemplo, `0.0.9` -> `0.1.0`. La major nunca se toca automáticamente.
- **`versionCode`:** un contador monótono simple, +1 en cada build, sin acarreo - Android exige que siempre suba en cada build que llegue a publicarse.

La versión en ejecución se ve en vivo en el diálogo **Acerca de** (`BuildConfig.VERSION_NAME`, leyendo el mismo `versionName` que Gradle acaba de calcular). Ver [CHANGELOG.md](CHANGELOG.md) para el historial de versiones.

## 📲 Pruebas contra un servidor real

1. Ejecuta el backend: `cd HYDRA-UMC-SERVER && npm run dev` (Puerto 3000) - esta es la API REST/WS real con la que habla la app (ver Proyectos Relacionados más abajo); el propio `npm run dev` de `HYDRA-UMC-STUDIO` solo arranca su servidor de desarrollo Vite del frontend (puerto 5173) contra ese mismo backend, no es el servidor de la API en sí.
2. Conecta tu dispositivo Android a la misma Wi-Fi.
3. Usa el **Selector Global de Servidor** o introduce la IP manualmente en la cabecera.
4. **Biometría:** Activa "Biometric Login" en tu Perfil de Usuario para saltarte la pantalla de contraseña en el siguiente arranque.

## 🩺 Resolución de Problemas

| Síntoma | Causa | Solución |
|---|---|---|
| Sin Notificaciones | Permiso denegado | Concede el permiso de "Notificaciones" en los ajustes de Android para esta app |
| Sin Biometría | Hardware no configurado | Asegúrate de tener una Huella/Rostro registrado en la Seguridad del Sistema Android |
| El robot no se mueve | Enlace cerebral del navegador | Mantén abierta una pestaña del navegador con HYDRA-UMC STUDIO para el procesamiento de IK |
| Bluetooth desactivado | Chip físico apagado | Usa el botón 3D "ENABLE SYSTEM BT" en la app |

## 📂 Estructura del Repositorio

```text
HYDRA-UMC-ANDROID-CONTROL/
├── app/
│   ├── build.gradle.kts          # Configuración Gradle del módulo app - versiones de AGP/Kotlin/Compose, dependencias, tipo de build release firmado con debug
│   ├── version.properties        # Versión de la app tipo cuentakilómetros + versionCode de Android, sincronizados por bump_manifest_version.py/bump_version_code.py
│   ├── proguard-rules.pro        # Reglas de reducción/ofuscación de código para el build de release
│   └── src/main/
│       ├── AndroidManifest.xml   # Permisos, declaraciones de activity/receiver, usesCleartextTraffic (servidor LAN HTTP plano, sin TLS)
│       ├── java/com/hydraumc/control/
│       │   ├── MainActivity.kt          # Punto de entrada - splash, control de acceso a login/pantalla principal, manejo de E-STOP global seguro en arranque frío
│       │   ├── MainScreen.kt            # Estructura de navegación inferior, barra superior (selector de servidor, perfil, telemetría, ajustes)
│       │   ├── kinematics/
│       │   │   └── Parol6Kinematics.kt   # Cinemática directa/inversa específica del Parol6
│       │   ├── model/
│       │   │   ├── BleDevice.kt          # Clase de datos para resultado de escaneo Bluetooth LE
│       │   │   └── HydraState.kt         # Espejo campo a campo de settings.json (RobotView/ControllerView/JobView) + modelo de descubrimiento ServerInfo
│       │   ├── network/
│       │   │   ├── AuthPrefs.kt           # Almacenamiento cifrado (AES256-GCM) de credenciales/sesión
│       │   │   ├── ConnectionPrefs.kt     # IP/puerto de servidor persistidos (DataStore Preferences)
│       │   │   ├── Discovery.kt           # Escaneo concurrente de subred /24 (principal) + listener NSD/mDNS (secundario) para encontrar un servidor en la LAN
│       │   │   ├── HydraApiClient.kt      # Cliente REST - login, lectura/escritura de ajustes, comandos atómicos de robot, métricas del sistema
│       │   │   ├── HydraBleClient.kt      # Cliente GATT Bluetooth, transporte alternativo al Wi-Fi
│       │   │   ├── HydraWebSocket.kt      # Envío en vivo de deltas de estado por WS, manejo de reconexión
│       │   │   └── StateCache.kt          # Caché del último estado conocido (DataStore) para visualización del dashboard sin conexión
│       │   ├── ui/
│       │   │   ├── AboutDialog.kt          # Diálogo de información de app/versión
│       │   │   ├── CameraScreen.kt         # Feed MJPEG por robot + interruptor de visión encendido/apagado
│       │   │   ├── ControlScreen.kt        # Controles de jog manual, E-STOP/play/pause/stop con protección de pulsación larga
│       │   │   ├── DashboardScreen.kt      # Selector de robot en carrusel 3D + salud del sistema + matriz de módulos
│       │   │   ├── Joystick3D.kt           # Componente de joystick reutilizable de 2 ejes
│       │   │   ├── LoginScreen.kt          # Entrada de usuario/contraseña + IP/puerto, login biométrico
│       │   │   ├── MjpegPlayer.kt          # Parser de stream MJPEG + renderizador Canvas
│       │   │   ├── NativeThreeDScreen.kt   # Visor 3D nativo con Google Filament - todavía no conectado a la navegación, sin pipeline .glb
│       │   │   ├── PlaybackConsole.kt      # Consola flotante compartida de E-STOP/play/pause/stop
│       │   │   ├── SettingsScreen.kt       # UI de escaneo Wi-Fi/Bluetooth, ajustes de conexión
│       │   │   ├── SplashScreen.kt         # Pantalla de bienvenida Compose personalizada
│       │   │   ├── TelemetryScreen.kt      # Visor de registro de eventos/sincronización estilo terminal
│       │   │   ├── ThreeDScreen.kt         # Visor 3D real - WebView incrustando la propia escena 3D headless de STUDIO
│       │   │   ├── UserProfileDialog.kt    # Diálogo de edición de perfil + interruptor biométrico
│       │   │   └── theme/
│       │   │       ├── Color.kt, Theme.kt, Typography.kt   # Esquema de color Material 3, wrapper de tema, escala tipográfica
│       │   │       └── HydraButton.kt, IndustrialComponents.kt, IndustrialStyle.kt   # Bloques de UI compartidos con estilo industrial
│       │   ├── update/
│       │   │   ├── GitHubReleaseUpdater.kt   # Cliente seguro de actualización vía GitHub Release
│       │   │   ├── ReleaseMetadataParser.kt  # Parser seguro de metadatos de GitHub Release
│       │   │   └── SemanticVersion.kt        # Parser estricto de versión semántica para actualizaciones
│       │   ├── util/
│       │   │   ├── BiometricHelper.kt      # Wrapper del prompt de androidx.biometric
│       │   │   ├── NotificationHelper.kt   # Notificaciones push de trabajo completado/seguridad
│       │   │   └── NotificationPrefs.kt    # Almacenamiento persistente del interruptor de notificaciones en la app
│       │   ├── viewmodel/
│       │   │   ├── AppUpdateViewModel.kt   # Estado de actualización de la app consciente del ciclo de vida
│       │   │   └── RobotViewModel.kt   # ViewModel compartido - red, autenticación, descubrimiento, despacho de comandos atómicos, todo el estado de UI
│       │   ├── wear/
│       │   │   ├── WatchCompanionProtocol.kt    # Contrato de estado de versión del reloj complementario
│       │   │   ├── WatchVoiceRelayContract.kt   # Contrato autenticado del relé de voz del Watch
│       │   │   └── WatchVoiceRelayService.kt    # Servicio de relé de voz para Wear OS
│       │   └── widget/
│       │       └── GlobalStopWidget.kt # Widget de pantalla de inicio para un E-STOP global sin abrir la app
│       └── res/
│           ├── drawable/, layout/, mipmap*/, xml/   # Iconos, layout del widget, iconos de lanzador, reglas de backup/extracción de datos
│           └── values/, values-es/, values-de/, values-fr/, values-it/, values-ja/, values-zh/   # Cadenas en 7 idiomas, colores, tema
├── docs/
│   ├── ARCHITECTURE.md              # Notas de diseño/arquitectura
│   ├── GITHUB_RELEASE_UPDATES.md    # Flujo de comprobación/descarga/instalación de actualizaciones en la app
│   └── WATCH_VOICE_RELAY.md         # Contrato del relé de voz reloj-teléfono-servidor
├── images/                       # Recursos fuente del banner del README + pantalla de bienvenida
├── tools/
│   ├── build_test.py             # Comprobación de build/compilación sin subir versión
│   └── ci_validate.py            # Validación de manifest/CHANGELOG/docs usada por la CI
├── dist/                         # Salida del APK de release firmado (ignorado por git)
├── build-android.bat / .sh       # Scripts de conveniencia de compilación + instalación adb en un solo paso
├── build-test.bat / .sh          # Comprobación de build/compilación sin subir versión
├── prepare-github-release.bat / .sh  # Compila un APK de release firmado de forma privada y estable, sin subir versión
├── publish-github-release.ps1 / .sh  # Solo local: publica el APK de dist/ como GitHub Release
├── bump_manifest_version.py      # Sincroniza la versión de hydra-umc.project.json con la nativa (--sync)
├── bump_version_code.py          # Incrementa el contador versionCode propio de Android en app/version.properties
├── gradlew, gradlew.bat          # Wrapper de Gradle
├── build.gradle.kts, settings.gradle.kts, gradle.properties   # Configuración raíz del proyecto Gradle
├── local.properties              # Ruta local del Android SDK (específica de la máquina, no comiteada)
├── keystore.properties.example   # Plantilla de configuración privada de firma de release
├── .env.example                  # Ejemplo de variables de entorno
├── metadata.json                 # Metadatos de ficha de tienda de apps (nombre/descripción)
├── README.md                     # Este archivo
├── README_spa.md / README_ita.md / README_fra.md / README_deu.md / README_zho.md / README_jpn.md   # Traducciones
└── LICENSE                       # GPL-3.0
```

## 🔗 Proyectos Relacionados

Este proyecto es parte del ecosistema de robótica HYDRA-UMC del mismo autor (JuanenRac / Electro Hobby 3D). Vale la pena conocerlo, ya que una petición podría en realidad ser sobre alguno de estos en vez de sobre este repositorio.

**Proyecto Padre**
- **[HYDRA-UMC-SERVER](https://github.com/JuanenRac/HYDRA-UMC-SERVER)** — el backend headless real (REST/WebSocket) con el que habla de verdad cada cliente de control; el backend contra el que se ejecutan el descubrimiento, la autenticación y la sincronización WebSocket propios de esta app.

**Proyectos Hermanos** — también hablan con la propia API de HYDRA-UMC-SERVER, cada uno como su propio cliente
- **[HYDRA-UMC-STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO)** — panel de control web con visualización 3D multi-robot en tiempo real; su propio visor 3D se integra directamente en la pantalla de Vista 3D de esta app mediante WebView.
- **[HYDRA-UMC-SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE)** — centro de mando de enjambre de escritorio (PySide6) para varios servidores a la vez, empaquetado como ejecutable independiente; habla exactamente el mismo contrato `REMOTE_API.md` que esta app.
- **[HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL)** — app de control para iOS/iPadOS (Flutter) con sincronización en tiempo real por WebSocket; la contraparte directa para iOS/iPadOS de esta app, con el mismo conjunto de funciones.
- **[HYDRA-UMC-DSI](https://github.com/JuanenRac/HYDRA-UMC-DSI)** — interfaz táctil nativa para la pantalla táctil DSI de 7" a bordo, embebida en el propio CM5.
- **[HYDRA-UMC-BRIDGE-AMR](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-AMR)** — barrera de coordinación para flotas AGV/AMR mediante un publicador MQTT VDA 5050 real.
- **[HYDRA-UMC-BRIDGE-CNC](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-CNC)** — coordinador de alto nivel para celdas CNC con acceso real a estado/bytes de control GRBL.
- **[HYDRA-UMC-BRIDGE-DROIDS](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-DROIDS)** — barrera de coordinación para droides con patas/humanoides, con un emisor de comandos real para Boston Dynamics Spot.
- **[HYDRA-UMC-BRIDGE-LASER](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-LASER)** — coordinador de seguridad para celdas láser que lee 3 salvaguardas GPIO reales de llave/carcasa/enclavamiento.
- **[HYDRA-UMC-BRIDGE-OPENPNP](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-OPENPNP)** — coordinador de alto nivel seguro para el flujo de placas de pick-and-place OpenPnP.
- **[HYDRA-UMC-BRIDGE-PRINTER3D](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-PRINTER3D)** — barrera de coordinación segura para impresoras 3D Moonraker/Klipper, con comandos de trabajo reales y controlados.
- **[HYDRA-UMC-BRIDGE-ROS2](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-ROS2)** — coordinador de seguridad con un transporte ROS 2 rclpy real, importado de forma perezosa.
- **[HYDRA-UMC-BRIDGE-UAV](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-UAV)** — barrera de coordinación para UAV equipados con cámara, con un emisor de comandos MAVLink real.

**Directamente Relacionados**
- **[HYDRA-UMC-WATCH](https://github.com/JuanenRac/HYDRA-UMC-WATCH)** — app compañera de WearOS con alertas hápticas reales y un relé de voz al teléfono emparejado; la compañera WearOS de esta app, para ver de un vistazo el estado del robot y controlarlo desde la muñeca.
- **[HYDRA-UMC-HIL-BRIDGE](https://github.com/JuanenRac/HYDRA-UMC-HIL-BRIDGE)** — enclavamiento de seguridad real hardware-in-the-loop que enruta comandos entre simulación y hardware real; permite controlar el gemelo digital de forma remota directamente desde esta app.

**También Forma Parte del Ecosistema**

*Hardware y Plataforma Base*
- **[HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC)** — la placa madre física del brazo robótico: host CM5 + coprocesador STM32H745 de doble núcleo, coordinando hasta 8 brazos herramienta por CAN-OTA/SPI-OTA.
- **[HYDRA-UMC-OS](https://github.com/JuanenRac/HYDRA-UMC-OS)** — capa de producto reproducible sobre Raspberry Pi OS para el CM5: agente de solo lectura, config/perfiles validados, aprovisionamiento WiFi de primer contacto.
- **[HYDRA-UMC-SDK](https://github.com/JuanenRac/HYDRA-UMC-SDK)** — el contrato JSON-Schema compartido y la barrera de seguridad contra la que cada bridge valida sus comandos.

*Backend Central y Clientes*
- **[HYDRA-UMC-EDITOR-URDF](https://github.com/JuanenRac/HYDRA-UMC-EDITOR-URDF)** — creador/editor gráfico de URDF de escritorio que envía los modelos terminados al propio catálogo de STUDIO.

*Plataforma de Herramientas URTC*
- **[URTC](https://github.com/JuanenRac/URTC)** — firmware para la placa física del Universal Robot Tool Controller, más de 25 perfiles de herramienta por bus CAN.
- **[URTC-FLASHER](https://github.com/JuanenRac/URTC-FLASHER)** — herramienta de escritorio con GUI para flashear placas URTC, CAN-OTA más SWD/JTAG de chip completo.
- **[URTC-TESTER](https://github.com/JuanenRac/URTC-TESTER)** — herramienta de escritorio de diagnóstico CAN-bus en vivo para placas URTC, un panel por perfil de herramienta.
- **[URTC-WEB-STUDIO](https://github.com/JuanenRac/URTC-WEB-STUDIO)** — alternativa basada en navegador a URTC-TESTER mediante la Web Serial API, sin instalación local.

*Nodo IA de Visión (Hailo-8)*
- **[HYDRA-UMC-VISION-NODE](https://github.com/JuanenRac/HYDRA-UMC-VISION-NODE)** — nodo de integración para el pipeline de visión Hailo-8, con una comprobación real de disponibilidad de hardware por etapa.
- **[HYDRA-UMC-DETECTION-HEF](https://github.com/JuanenRac/HYDRA-UMC-DETECTION-HEF)** — registro real de modelos compilados con verificación de carga segura por arquitectura Hailo/checksum.
- **[HYDRA-UMC-VISION-STREAMER](https://github.com/JuanenRac/HYDRA-UMC-VISION-STREAMER)** — generador real de pipeline GStreamer + config MediaMTX, con una frontera de integración HailoRT real.
- **[HYDRA-UMC-VISUAL-SERVOING-API](https://github.com/JuanenRac/HYDRA-UMC-VISUAL-SERVOING-API)** — ley de corrección real de Position-Based Visual Servoing, con puerta de seguridad según el estado de zona previo.
- **[HYDRA-UMC-SAFETY-ZONES](https://github.com/JuanenRac/HYDRA-UMC-SAFETY-ZONES)** — comprobación real de invasión de zona y solicitud de E-STOP, con exigencia de vigencia de calibración.

*Nodo IA Cognitivo (Hailo-10)*
- **[HYDRA-UMC-COGNITIVE-NODE](https://github.com/JuanenRac/HYDRA-UMC-COGNITIVE-NODE)** — nodo de integración para el pipeline cognitivo Hailo-10 (orquestación de LLM/VLA/voz).
- **[HYDRA-UMC-VLA-ENGINE](https://github.com/JuanenRac/HYDRA-UMC-VLA-ENGINE)** — codificación/decodificación real de tokens de acción y generación de trayectoria para un modelo Vision-Language-Action.
- **[HYDRA-UMC-VOICE-UI](https://github.com/JuanenRac/HYDRA-UMC-VOICE-UI)** — front-end de voz real (VAD + analizador de intención) con un relé a Watch acotado y con confirmación.
- **[HYDRA-UMC-SEMANTIC-PLANNER](https://github.com/JuanenRac/HYDRA-UMC-SEMANTIC-PLANNER)** — descomposición real de tareas basada en reglas y recuperación semántica de errores sobre códigos de error del MCU.
- **[HYDRA-UMC-DOCS-QA](https://github.com/JuanenRac/HYDRA-UMC-DOCS-QA)** — búsqueda real de documentos TF-IDF (solo librería estándar) sobre los propios documentos Markdown de este ecosistema.

*Orquestación y Enjambre*
- **[HYDRA-UMC-ORCHESTRATOR](https://github.com/JuanenRac/HYDRA-UMC-ORCHESTRATOR)** — nodo de integración con un contrato real de informe de salud gRPC/Protobuf y una máquina de estados de misión.
- **[HYDRA-UMC-JOB-DISPATCHER](https://github.com/JuanenRac/HYDRA-UMC-JOB-DISPATCHER)** — cola de trabajos real basada en prioridad con deduplicación, sobre una API HTTP real.
- **[HYDRA-UMC-NODE-HEALING](https://github.com/JuanenRac/HYDRA-UMC-NODE-HEALING)** — watchdog de salud de flota real basado en gRPC, con reintento/backoff y detección de discrepancia de identidad.
- **[HYDRA-UMC-PATH-PLANNER-3D](https://github.com/JuanenRac/HYDRA-UMC-PATH-PLANNER-3D)** — planificador de rutas 3D real basado en RRT, con validación real de colisión de obstáculos/espacio de trabajo.
- **[HYDRA-UMC-SWARM-SYNC](https://github.com/JuanenRac/HYDRA-UMC-SWARM-SYNC)** — sincronización de estado real mediante CRDT LWW-Element-Map, con pruebas de propiedades para convergencia multi-celda.

*Gemelo Digital y Simulación*
- **[HYDRA-UMC-TWIN](https://github.com/JuanenRac/HYDRA-UMC-TWIN)** — nodo de integración para el motor de gemelo digital, con un contrato real de sincronización por compatibilidad de versión.
- **[HYDRA-UMC-PHYSICS-REPLICA](https://github.com/JuanenRac/HYDRA-UMC-PHYSICS-REPLICA)** — cinemática directa real y validación de límites articulares sobre un subconjunto real de URDF.
- **[HYDRA-UMC-SYNTHETIC-DATA-GEN](https://github.com/JuanenRac/HYDRA-UMC-SYNTHETIC-DATA-GEN)** — generador real de escenas 2D procedurales con exportación de anotaciones YOLO/COCO.

*Datos y Analítica*
- **[HYDRA-UMC-DATALAKE](https://github.com/JuanenRac/HYDRA-UMC-DATALAKE)** — almacén de series temporales real respaldado por sqlite3, con una API HTTP real de ingesta/consulta.
- **[HYDRA-UMC-ANOMALY-DETECTOR](https://github.com/JuanenRac/HYDRA-UMC-ANOMALY-DETECTOR)** — detector de anomalías real basado en FFT + línea base estadística, con monitorización de deriva.
- **[HYDRA-UMC-PRODUCTION-REPORTS](https://github.com/JuanenRac/HYDRA-UMC-PRODUCTION-REPORTS)** — cálculo real de OEE/disponibilidad sobre el histórico de DATALAKE, con exportación CSV reproducible.
- **[HYDRA-UMC-TELEMETRY-COLLECTOR](https://github.com/JuanenRac/HYDRA-UMC-TELEMETRY-COLLECTOR)** — pipeline real de ingesta CAN/WebSocket hacia DATALAKE, con deduplicación por secuencia.

*Pasarela Industrial*
- **[HYDRA-UMC-GATEWAY-INDUSTRIAL](https://github.com/JuanenRac/HYDRA-UMC-GATEWAY-INDUSTRIAL)** — nodo de integración que retransmite a protocolos industriales, con una capa real de lista blanca de comandos/contrapresión.
- **[HYDRA-UMC-OPCUA-SERVER](https://github.com/JuanenRac/HYDRA-UMC-OPCUA-SERVER)** — espacio de direcciones OPC-UA real, verificado con una sesión de cliente real del protocolo binario.
- **[HYDRA-UMC-MQTT-BROKER](https://github.com/JuanenRac/HYDRA-UMC-MQTT-BROKER)** — broker MQTT real con autenticación por cliente opcional y ACL de tópicos.
- **[HYDRA-UMC-MTCONNECT-ADAPTER](https://github.com/JuanenRac/HYDRA-UMC-MTCONNECT-ADAPTER)** — endpoints XML reales `/probe` y `/current` de MTConnect, con salida en modo degradado.

*Herramientas Complementarias y Operaciones del Ecosistema*
- **[HYDRA-UMC-DASHBOARD-AI](https://github.com/JuanenRac/HYDRA-UMC-DASHBOARD-AI)** — paneles de Resúmenes Inteligentes y Resaltado de Anomalías sobre DATALAKE/ANOMALY-DETECTOR, con un respaldo estadístico honesto.
- **[HYDRA-UMC-TOOL-CLI](https://github.com/JuanenRac/HYDRA-UMC-TOOL-CLI)** — CLI de flota con un contrato real y estable de códigos de salida, cliente real y en vivo de la propia API de HYDRA-UMC-SERVER.
- **[URTC-SMART-RACK](https://github.com/JuanenRac/URTC-SMART-RACK)** — firmware para un rack de montaje de placas con decodificación real de ID de herramienta y lógica de precalentamiento Smart Idle.
- **[URTC-VISION-TOOL](https://github.com/JuanenRac/URTC-VISION-TOOL)** — firmware más un compañero de visión real en Python para un cabezal de inspección térmica/RGB.
- **[HYDRA-UMC-UPDATER](https://github.com/JuanenRac/HYDRA-UMC-UPDATER)** — herramienta administrativa de escritorio que descubre, clona y actualiza cada repositorio de este ecosistema.

## 👤 AUTOR
**JuanenRac** (Electro Hobby 3D)
📧 electrohobby3d@gmail.com
📺 [youtube.com/@electrohobby3d](https://youtube.com/@electrohobby3d)

## 📜 LICENCIA

**GNU General Public License v3.0 (GPL-3.0)** para el código fuente - ver [`LICENSE`](LICENSE).

Esta documentación (este README y sus propias traducciones - `README_spa.md`, `README_ita.md`, `README_fra.md`, `README_deu.md`, `README_zho.md`, `README_jpn.md`) está disponible bajo **Creative Commons Attribution-ShareAlike 4.0 International (CC BY-SA 4.0)**. Texto completo en https://creativecommons.org/licenses/by-sa/4.0/.
