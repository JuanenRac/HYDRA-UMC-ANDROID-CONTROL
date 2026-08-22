<p align="center">
  <img src="images/HYDRA_UMC_ANDROID_CONTROL_BANNER.jpg" alt="HYDRA-UMC Android Control Banner" width="100%">
</p>

# 📱 HYDRA-UMC CONTROL

<p align="center">
  <a href="README.md">🇺🇸 English</a> |
  🇪🇸 <b>Español</b> |
  <a href="README_fra.md">🇫🇷 Français</a> |
  <a href="README_ita.md">🇮🇹 Italiano</a> |
  <a href="README_deu.md">🇩🇪 Deutsch</a>
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

- **Patch, estilo cuentakilómetros (base 10):** +1 en cada build; si superaría 9, se resetea a 0 y la minor sube +1 - por ejemplo, `1.0.9` -> `1.1.0`. La major nunca se toca automáticamente.
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
│   └── src/main/
│       ├── AndroidManifest.xml   # Permisos, declaraciones de activity/receiver, usesCleartextTraffic (servidor LAN HTTP plano, sin TLS)
│       ├── java/com/hydraumc/control/
│       │   ├── MainActivity.kt          # Punto de entrada - splash, control de acceso a login/pantalla principal, manejo de E-STOP global seguro en arranque frío
│       │   ├── MainScreen.kt            # Estructura de navegación inferior, barra superior (selector de servidor, perfil, telemetría, ajustes)
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
│       │   │   ├── LoginScreen.kt          # Entrada de usuario/contraseña + IP/puerto, login biométrico
│       │   │   ├── MjpegPlayer.kt          # Parser de stream MJPEG + renderizador Canvas
│       │   │   ├── NativeThreeDScreen.kt   # Visor 3D nativo con Google Filament - todavía no conectado a la navegación, sin pipeline .glb
│       │   │   ├── SettingsScreen.kt       # UI de escaneo Wi-Fi/Bluetooth, ajustes de conexión
│       │   │   ├── SplashScreen.kt         # Pantalla de bienvenida Compose personalizada
│       │   │   ├── TelemetryScreen.kt      # Visor de registro de eventos/sincronización estilo terminal
│       │   │   ├── ThreeDScreen.kt         # Visor 3D real - WebView incrustando la propia escena 3D headless de STUDIO
│       │   │   ├── UserProfileDialog.kt    # Diálogo de edición de perfil + interruptor biométrico
│       │   │   └── theme/
│       │   │       ├── Color.kt, Theme.kt, Typography.kt   # Esquema de color Material 3, wrapper de tema, escala tipográfica
│       │   │       └── HydraButton.kt, IndustrialComponents.kt, IndustrialStyle.kt   # Bloques de UI compartidos con estilo industrial
│       │   ├── util/
│       │   │   ├── BiometricHelper.kt      # Wrapper del prompt de androidx.biometric
│       │   │   └── NotificationHelper.kt   # Notificaciones push de trabajo completado/seguridad
│       │   ├── viewmodel/
│       │   │   └── RobotViewModel.kt   # ViewModel compartido - red, autenticación, descubrimiento, despacho de comandos atómicos, todo el estado de UI
│       │   └── widget/
│       │       └── GlobalStopWidget.kt # Widget de pantalla de inicio para un E-STOP global sin abrir la app
│       └── res/
│           ├── drawable/, layout/, mipmap*/, xml/   # Iconos, layout del widget, iconos de lanzador, reglas de backup/extracción de datos
│           └── values/, values-es/, values-de/, values-fr/, values-it/   # Cadenas en 5 idiomas, colores, tema
├── docs/
│   └── ARCHITECTURE.md           # Notas de diseño/arquitectura
├── images/                       # Recursos fuente del banner del README + pantalla de bienvenida
├── build-android.bat / .sh       # Scripts de conveniencia de compilación + instalación adb en un solo paso
├── gradlew, gradlew.bat          # Wrapper de Gradle
├── build.gradle.kts, settings.gradle.kts, gradle.properties   # Configuración raíz del proyecto Gradle
├── local.properties              # Ruta local del Android SDK (específica de la máquina, no comiteada)
├── .env.example                  # Ejemplo de variables de entorno
├── README.md                     # Este archivo
├── README_spa.md / README_ita.md / README_fra.md / README_deu.md   # Traducciones
└── LICENSE                       # GPL-3.0
```

## 🔗 Proyectos Relacionados

Este proyecto forma parte de un ecosistema robótico más amplio del mismo autor (JuanenRac / Electro Hobby 3D). Vale la pena conocerlo, ya que una petición podría en realidad ser sobre uno de estos en vez de sobre este repositorio:

**Plataforma HYDRA-UMC** — la célula de microfábrica multi-robot
- **[HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC)** — la placa base en sí: host Raspberry Pi CM5 + coprocesador de tiempo real STM32H745 de doble núcleo, orquestando hasta 8 brazos robóticos distribuidos por CAN-OTA/SPI-OTA. Hardware + firmware propios, GPL-3.0/CERN-OHL-S v2/CC BY-SA 4.0.
- **[HYDRA-UMC STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO)** — panel de control web para HYDRA-UMC: visualización 3D multi-robot, grabación de cinemática/trayectorias, flasheo y pruebas CAN-OTA para toda la plataforma. React + Vite + Three.js.
- **[HYDRA-UMC-SERVER](https://github.com/JuanenRac/HYDRA-UMC-SERVER)** — el backend headless (Node/Express/WebSocket) que antes venía integrado en el propio proceso de HYDRA-UMC STUDIO. Contiene la API REST/WS de control de robots, la persistencia de settings.json, la autenticación JWT y el descubrimiento mDNS. HYDRA-UMC STUDIO es ahora un cliente frontend estático puro que se comunica con él por red.
- **HYDRA-UMC-ANDROID-CONTROL** *(este repositorio)* — app de control Android para HYDRA-UMC por Wi-Fi/Bluetooth. App real y funcional - conjunto completo de funciones de control remoto, autenticación JWT, almacenamiento cifrado de credenciales.
- **[HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL)** — app de control iOS/iPadOS para HYDRA-UMC por Wi-Fi, construida en Flutter (multiplataforma, verificable en Windows sin un Mac; el empaquetado final `.ipa` todavía necesita Xcode). App real y funcional - mismo conjunto de funciones que la app Android.
- **[HYDRA-UMC-SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE)** — centro de mando de escritorio (Python/PySide6) para el enjambre: descubrimiento de red multi-controlador, sincronización bidireccional en vivo, visor 3D de robot real, espacio de trabajo acoplable estilo Photoshop. Real y funcional, no un placeholder.
- **[HYDRA-UMC-EDITOR-URDF](https://github.com/JuanenRac/HYDRA-UMC-EDITOR-URDF)** — creador/editor gráfico de escritorio (Python/PySide6) de URDF para el propio catálogo de modelos de este proyecto: extrae archivos fuente de GitHub o de una carpeta local, valida la viabilidad de grados de libertad (DOF), edita color/escala/cinemática con una vista previa 3D en vivo, y publica el resultado final a un servidor STUDIO en ejecución. Real y funcional, no un placeholder.
- **[HYDRA-UMC-DSI](https://github.com/JuanenRac/HYDRA-UMC-DSI)** — UI táctil nativa en Flutter para la propia pantalla táctil DSI de 5"/7" de HYDRA-UMC (1280×720, misma resolución en ambos tamaños) en la Compute Module 5, controlando este mismo servidor directamente desde la placa. Scaffold real y funcional con las 6 pantallas del catálogo (dashboard, control manual, cámara, vista 3D simplificada, métricas de sistema, login) conectadas al servidor en vivo; el build real del target Linux aún no se ha ejecutado en hardware real (entorno de trabajo solo Windows hasta ahora - ver el README propio de ese proyecto).

**Plataforma URTC** — el controlador de cabezal de herramienta que lleva cada brazo robótico HYDRA-UMC
- **[URTC](https://github.com/JuanenRac/URTC)** — Universal Robot Tool Controller: controlador de cabezal de herramienta por bus CAN basado en STM32F303, 25 perfiles de herramienta totalmente implementados, actualización de firmware CAN-OTA.
- **[URTC Flasher](https://github.com/JuanenRac/URTC-FLASHER)** — herramienta de escritorio de flasheo CAN-OTA + chip completo por SWD/JTAG para placas URTC (Windows/Linux).
- **[URTC Tester](https://github.com/JuanenRac/URTC-TESTER)** — herramienta de escritorio de diagnóstico en vivo por bus CAN para placas URTC, un panel por perfil de herramienta (Windows/Linux).
- **[URTC Web Studio](https://github.com/JuanenRac/URTC-WEB-STUDIO)** — alternativa basada en navegador a las 2 herramientas de escritorio de arriba (Web Serial API + SLCAN), sin instalación local necesaria.

## 👤 Autor

**JuanenRac** (Electro Hobby 3D)
📧 electrohobby3d@gmail.com
📺 youtube.com/@electrohobby3d

## 📜 Licencia

**GNU General Public License v3.0 (GPL-3.0)** para el código fuente - ver [`LICENSE`](LICENSE).

Esta documentación (este README y sus propias traducciones - `README_spa.md`, `README_ita.md`, `README_fra.md`, `README_deu.md`) está disponible bajo **Creative Commons Attribution-ShareAlike 4.0 International (CC BY-SA 4.0)**. Texto completo en https://creativecommons.org/licenses/by-sa/4.0/.
