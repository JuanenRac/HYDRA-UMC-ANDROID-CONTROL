<p align="center">
  <img src="images/HYDRA_UMC_BANNER.svg" alt="HYDRA-UMC-ANDROID-CONTROL banner" width="100%">
</p>

# 📱 HYDRA-UMC CONTROL

<p align="center">
  <a href="README.md">🇺🇸 English</a> |
  <a href="README_spa.md">🇪🇸 Español</a> |
  <a href="README_fra.md">🇫🇷 Français</a> |
  <a href="README_ita.md">🇮🇹 Italiano</a> |
  <a href="README_deu.md">🇩🇪 Deutsch</a> |
  <a href="README_zho.md">🇨🇳 简体中文</a> |
  <a href="README_jpn.md">🇯🇵 日本語</a> |
  🇵🇹 <b>Português</b>
</p>


<p align="center">
  <img src="https://img.shields.io/badge/License-GPL%203.0--or--later-blue.svg" alt="GPL 3.0 or later">
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF.svg" alt="Kotlin">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg" alt="Compose">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84.svg" alt="Android">
</p>


Uma aplicação Android nativa (Kotlin + Jetpack Compose) que controla um robô da plataforma [HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC) por Wi-Fi ou Bluetooth, falando exatamente o mesmo contrato [`REMOTE_API.md`](https://github.com/JuanenRac/HYDRA-UMC-SERVER/blob/main/docs/REMOTE_API.md) que o [HYDRA-UMC SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE) usa - descoberta, leitura/escrita do estado completo e sincronização WebSocket em direto contra um back-end [HYDRA-UMC-SERVER](https://github.com/JuanenRac/HYDRA-UMC-SERVER) em execução (o mesmo com que o painel web do próprio [HYDRA-UMC STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO) fala). Contraparte Android direta do [HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL). Consulte o [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) para o desenho completo.

> **Verificação de honestidade - o que realmente funciona hoje:** a lógica de negócio central - a cinemática do Parol6 (`kinematics/Parol6Kinematics.kt`), o contrato de estado da árvore de definições (`model/HydraState.kt`), o despacho atómico de comandos e o relé de turnos de voz (`viewmodel/RobotViewModel.kt`), o tratamento de religação do WebSocket (`network/HydraWebSocket.kt`), a análise de fotogramas MJPEG (`ui/MjpegPlayer.kt`), o canal de atualização na própria aplicação (`update/GitHubReleaseUpdater.kt`, `ReleaseMetadataParser.kt`, `SemanticVersion.kt`) e a contabilidade de pedidos do relé de voz do Wear (`wear/BoundedRequestScope.kt`, `WatchCompanionProtocol.kt`) são reais e cobertos por 90 testes unitários JVM/Robolectric a passar (`./gradlew testDebugUnitTest`). O que esses testes estruturalmente não alcançam - e que este ambiente de desenvolvimento só para Windows, sem dispositivo Android físico, não consegue verificar - é o `EncryptedSharedPreferences` real apoiado no Keystore do `network/AuthPrefs.kt`, o pedido biométrico de impressão digital/rosto do `androidx.biometric`, o transporte GATT Bluetooth do `network/HydraBleClient.kt` e o caminho de hardware `ImageDecoder` da API 28+ no `MjpegStreamParser`: código real e compilado que não foi reverificado em hardware desde que foi escrito. O arranque/início de sessão básicos *foram* confirmados em direto num dispositivo real em versões anteriores (ver as entradas `v0.3.2`/`v0.3.3` do `CHANGELOG.md`), mas isso é anterior ao Bluetooth, à biometria e ao caminho de descodificação por hardware. Consulte o `CHANGELOG.md` para saber exatamente o que foi entregue, incluindo as suas notas por entrada sobre o que continua por verificar em hardware.

> **Estado de segurança (safety e security):** este ecossistema não é um sistema de segurança certificado. Ainda não existe um caminho de E-STOP por hardware, nem limites ou interlocks impostos pelo firmware, nem um comando de movimento verificado; as imagens de firmware são verificadas com CRC32 e um HMAC-SHA256 de chave partilhada, não com uma assinatura assimétrica; TLS e TLS mútuo são opcionais e estão desativados por defeito; nenhuma placa própria foi ainda montada ou testada. Leia a [base de segurança](https://github.com/JuanenRac/HYDRA-UMC-DOCS/blob/main/docs/SECURITY_BASELINE.md) e o [modelo de ameaças](https://github.com/JuanenRac/HYDRA-UMC-DOCS/blob/main/docs/THREAT_MODEL.md) antes de confiar em qualquer afirmação aqui para a segurança de uma pessoa.

## 📋 Num relance

| | |
|---|---|
| Versão | `0.7.1` |
| Maturidade | Funcional |
| Testes | 98 (`./gradlew testDebugUnitTest`) |
| Tecnologia | Kotlin / Jetpack / biometric auth / WebSocket client |
| Destino | Um telemóvel ou tablet Android |
| Em hardware real | Ainda não: não há registo de execução em hardware real para esta versão |

## 🏗️ O que está implementado

- **Controlo de acesso e biometria** (`ui/LoginScreen.kt`, `util/BiometricHelper.kt`) - sistema profissional de início de sessão com suporte a **impressão digital e desbloqueio facial** (`androidx.biometric`), mais campos de IP/porta no mesmo ecrã para que se possa apontar a um servidor sem uma ida separada às Definições. Inclui a funcionalidade «Lembrar-me», um mecanismo seguro de **Terminar sessão** e está totalmente localizada em **8 idiomas**. O utilizador/palavra-passe/token em cache (`network/AuthPrefs.kt`) vivem em **EncryptedSharedPreferences** apoiadas no Keystore (AES256-GCM), não em texto simples - um servidor de desenvolvimento (`NODE_ENV` por definir) cria uma conta `admin`/`admin` por defeito no primeiro arranque; um servidor de produção (`NODE_ENV=production`) exige em vez disso `HYDRA_UMC_BOOTSTRAP_ADMIN_USERNAME`/`HYDRA_UMC_BOOTSTRAP_ADMIN_PASSWORD` e nunca recorre em silêncio a `admin`/`admin` - ver o README do próprio HYDRA-UMC-SERVER para a configuração de produção completa. Contas adicionais de **operador**, com menos privilégios, podem ser criadas no servidor em Config > Users de qualquer das formas.
- **Modo offline e cache de estado** (`network/StateCache.kt`) - motor de persistência integrado que usa **DataStore**. A aplicação guarda automaticamente em cache o último estado conhecido do sistema, permitindo ver o painel e auditar a configuração de imediato mesmo sem uma ligação Wi-Fi ativa.
- **Notificações e alertas de missão** (`util/NotificationHelper.kt`) - sistema de alertas. Envia notificações push de alta prioridade quando um robô conclui uma sequência de trabalho ou se ocorrerem eventos críticos de hardware, garantindo que o operador é informado mesmo com a aplicação em segundo plano.
- **Terminal de telemetria industrial** (`ui/TelemetryScreen.kt`) - um visualizador de registos em tempo real dedicado, com interface ao estilo de terminal. Acompanha eventos do sistema e a sincronização REST/WebSocket e dá diagnósticos com código de cores (verde Matrix para êxito, vermelho industrial para erros).
- **Painel avançado** (`ui/DashboardScreen.kt`) - **carrossel horizontal 3D** de alta fidelidade com efeitos de deslize em perspetiva. Mostra metadados enriquecidos do robô: **fabricante** (Source Robotics, Annin, Universal Robots, AgileX, etc.), **papel do robô** (CNC, Laser, PnP) e uma **matriz de módulos industriais** com o estado em direto dos módulos CAM, XY, ATC, PNP, CNC, LSR, BED, VAC e RCK.
- **Monitor de saúde do sistema** (`ui/DashboardScreen.kt`) - métricas em tempo real do Compute Module 5 ligado, incluindo **nome do anfitrião**, **tempo ativo formatado** (p. ex. «2d 4h 15m») e contagens ativas de controladores e robôs.
- **Controlo manual melhorado** (`ui/ControlScreen.kt`) - apresenta uma disposição vertical profissional com **botões do joystick 50% maiores** para máxima precisão.
- **Painel de reprodução** (`ui/ControlScreen.kt`, `ui/PlaybackConsole.kt`) - barra de controlo fixa na parte inferior que aloja o botão vermelho **STOP** (o STOP global abaixo) e os botões **Iniciar**, **Pausa** e **Parar** (apenas o robô selecionado). Estes controlos estão sempre visíveis e oferecem **retorno háptico** para confirmação sensorial física.
- **STOP global** (`ui/GlobalStop.kt`, `network/HydraApiClient.kt`, `viewmodel/RobotViewModel.kt`) - um botão vermelho **STOP** (legenda "Parar tudo", traduzida nos 8 idiomas) na barra superior de todos os ecrãs, na consola do ecrã de Controlo e na vista 3D (vertical e em ecrã inteiro). Um toque, sem manter premido e sem confirmação: envia `POST /api/stop-all` de imediato e pára todos os robôs, mesmo reservados por outra conta. Mostra o resultado (que robôs pararam, quais não, quais estão reservados por outra pessoa e um aviso se o servidor não conseguiu guardar o estado de paragem), tenta novamente uma vez se ninguém responder, pára os robôs um a um se o servidor limitar a chamada (429) e mantém um aviso persistente **STOP NÃO CONFIRMADO** até que um STOP posterior resulte. É a paragem por software do servidor: não é um E-STOP de hardware e não corta a alimentação de nada.
- **Vista 3D** (`ui/ThreeDScreen.kt`) - embebe a vista 3D em tempo real do próprio HYDRA-UMC STUDIO numa WebView (`?hideUI=true&robotId=&token=`), em vez de um renderizador nativo. A abordagem da WebView dá de graça a cena 3D real e atualmente distribuída do STUDIO (cada malha/cinemática de robô real) em vez de a reimplementar de forma nativa - o compromisso é a sobrecarga de renderização da WebView, não ideal para a bateria, mas funcionalmente completa hoje.
- **Visão octal em tempo real** (`ui/CameraScreen.kt`, `ui/MjpegPlayer.kt`) - **streamer MJPEG nativo**. Tem um analisador em segundo plano e um renderizador baseado em Canvas para vídeo de baixa latência, um estado claro «Câmara desativada» (em vez de uma imagem em branco silenciosa) quando o sistema de visão de um robô está desligado, e um interruptor para ligar/desligar a câmara de um robô diretamente a partir do servidor. Suporta sobreposições automáticas de **Picture-in-Picture (PIP)** no ecrã de controlo manual, associadas a robôs específicos através da configuração de câmaras do servidor. Na API 28+, cada fotograma é descodificado pelo próprio caminho `ImageDecoder` da plataforma, acelerado por hardware (um bitmap residente na GPU, evitando a descodificação/carregamento do lado do CPU), com um recurso automático e autorrecuperável para a descodificação original por CPU.
- **Descoberta e conectividade inteligentes** (`network/Discovery.kt`, `network/HydraApiClient.kt`, `network/HydraWebSocket.kt`) - análise concorrente de sub-rede contra cada anfitrião candidato no /24 do próprio telemóvel (incluindo o IP de LAN do próprio telemóvel e o localhost, não apenas os outros anfitriões), sondando `GET /api/hydra-info` e identificando um servidor real apenas pela presença de `remoteApiVersion` - a mesma verificação que o caminho de IP manual usa, pelo que um servidor cujo dono o renomeou para longe da cadeia de produto por defeito continua a ser encontrado. Um ouvinte **NsdManager** (mDNS/Bonjour) corre ao lado - o servidor anuncia-se de facto como `_hydra._tcp` (`bonjour-service`), e a `MainActivity` pede de início a permissão de execução de localização/dispositivos próximos de que isto precisa (uma declaração no manifesto, por si só, nunca a concede na API 23+) - mas a análise de sub-rede continua a ser o caminho principal, já que a alcançabilidade por multicast no Wi-Fi é inerentemente menos fiável do que uma simples sonda HTTP. A aplicação ativa automaticamente o WiFi ao arrancar, analisa a rede local da fábrica e faz uma **ligação automática sem cliques** ao primeiro servidor HYDRA-UMC disponível.
- **Acesso com JWT** (`network/HydraApiClient.kt`, `ui/LoginScreen.kt`) - início de sessão com **JWT (JSON Web Tokens)**. Cada comando de controlo (Jog, Play e a paragem global do servidor) é validado pelo servidor com tokens assinados, enviado pelo endpoint atómico `POST /api/robot/:id/command` (ver Sincronização atómica de comandos abaixo) - funciona para o papel `admin` ou `operator`, ao contrário de uma escrita completa `POST /api/settings` (só administrador, do lado do servidor). Cada pedido leva também um cabeçalho `X-Hydra-Client: android` para que o separador Config > Remote Access do próprio servidor possa permitir/bloquear esta aplicação independentemente do SUITE/iOS. Integrado sem costuras com **autenticação biométrica** (impressão digital/rosto) para a renovação segura de tokens. Um WebSocket fechado com o código `1008` (token inválido/expirado) é tratado como «inicie sessão de novo», e não repetido num ciclo de religação (`network/HydraWebSocket.kt`).
- **Sincronização atómica de comandos** (o `sendAtomicCommand()` do `viewmodel/RobotViewModel.kt`) - cada escrita (play/pause/stop/jog/jogStep/válvula/bomba/velocidade/visão/ferramenta) envia um pequeno comando atómico de um só robô em vez da árvore de definições inteira - o servidor calcula que robôs combinados também são afetados, persiste em disco e difunde para todos os outros clientes ligados por si. O Enable/Disable propaga-se aos irmãos `combinedWith` de um robô tal como o Play/Pause/Stop, já que todos partilham o mesmo cálculo de robôs afetados.
- **Widget de STOP global** (`widget/GlobalStopWidget.kt`) - **widget de ecrã inicial** dedicado ao acesso rápido. Oferece um botão **STOP global** muito visível e de acesso imediato - a mesma chamada de cima: o toque traz a aplicação para primeiro plano e envia a paragem assim que existe ligação ao servidor, mesmo a partir de um arranque a frio completo (processo ainda não iniciado), e mostra STOP NÃO CONFIRMADO se não conseguir chegar ao servidor. A ação da notificação faz o mesmo. É a paragem por software do servidor: não é um E-STOP de hardware e não corta a alimentação de nada.
- **Resposta háptica** (`ui/ControlScreen.kt`) - sistema avançado de retorno sensorial. O STOP global responde a um toque com uma vibração dupla imediata; o botão Parar de cada robô mantém a sua **proteção de pressão longa** (um toque rápido dá apenas uma vibração curta + uma dica; só uma pressão mantida envia o comando). Assinaturas hápticas diferenciadas (impulsos de Sucesso, Erro e Emergência) dão confirmação física ao operador em ambientes ruidosos.
- **Canal de atualização na própria aplicação** (`update/GitHubReleaseUpdater.kt`, `update/ReleaseMetadataParser.kt`, `update/SemanticVersion.kt`) - consulta a Release estável mais recente do GitHub ao arrancar e em **Definições → Atualizações**; descarrega apenas o ativo exato `HYDRA-UMC-ANDROID-CONTROL-release.apk` de uma etiqueta que não seja rascunho nem pré-lançamento, nunca instala automaticamente - o próprio instalador de pacotes do Android pede o consentimento final. Contrato completo da release: [`docs/GITHUB_RELEASE_UPDATES.md`](docs/GITHUB_RELEASE_UPDATES.md).
- **Companheiro Wear OS emparelhado e relé de voz** (`wear/WatchVoiceRelayService.kt`, `wear/WatchCompanionProtocol.kt`) - retransmite um turno de voz já reconhecido e iniciado pelo utilizador, vindo da aplicação [HYDRA-UMC-WATCH](https://github.com/JuanenRac/HYDRA-UMC-WATCH) emparelhada, através da própria sessão autenticada desta aplicação com o Server até ao `HYDRA-UMC-VOICE-UI`, devolvendo uma resposta tipada ao Watch; nem este relé nem o cartão de estado do sistema do relógio emitem alguma vez um comando de robô nem tocam diretamente no `HydraState` - uma resposta relacionada com movimento é explicitamente marcada `requiresConfirmation` e tem de passar por uma interface de controlo principal. Contrato completo: [`docs/WATCH_VOICE_RELAY.md`](docs/WATCH_VOICE_RELAY.md).
- **Assistente de voz na aplicação** (`ui/VoiceAssistantDialog.kt`) - um ícone de microfone na barra superior abre uma caixa de diálogo para escrever uma pergunta/ordem, ou usar o reconhecimento de fala no próprio dispositivo deste telemóvel (`RecognizerIntent`, o mesmo padrão real do botão de voz do próprio Watch). Envia a transcrição reconhecida pela mesma rota `POST /api/voice/turn` que o relé do Watch usa, mas como tráfego normal deste próprio telemóvel (`HydraApiClient.postVoiceTurn()`) em vez de um relé - para que o Config > Remote Access possa permitir/bloquear este botão independentemente do Watch emparelhado. Nunca envia áudio, e uma resposta nunca invoca diretamente um comando de robô.
- **Cadeia de ferramentas e qualidade do projeto** - AGP 9.3.1, Kotlin 2.2.10, Gradle 9.7.0, compileSdk 36, **JDK 21** (`compileOptions`, `gradle-daemon-jvm.properties` e os ficheiros de projeto de `.idea/`/`.vscode/` visam todos de facto esta versão, não apenas esta linha de documentação). Saída de compilação limpa sem avisos, variantes de produção R8 otimizadas e testes de capturas de ecrã **Roborazzi** avançados.
- **Texto JSON nulo tratado a sério** (`util/JsonText.kt`) - o `JSONObject.optString` do Android devolve as quatro letras `null` para um campo presente mas `null` em JSON, pelo que uma release sem notas mostrava `null`, um robô sem nome chamava-se `null` e um início de sessão com `"token": null` guardava o token `null`. O auxiliar `optStr` trata um `null` JSON como uma chave em falta e dá o valor de recurso; todos os 32 pontos de utilização o usam. *(implementado)*

**Estado: Wi-Fi, Bluetooth, biometria e notificações implementados.** A aplicação é uma consola industrial de operador; o Bluetooth ainda é experimental e nenhum ecrã substitui uma paragem de emergência física.

## 🚀 Compilação

Requer **um JDK 21 especificamente** e o Android SDK.

1. Instale o [Android Studio](https://developer.android.com/studio).
2. Abra a raiz do projeto e deixe a sincronização do Gradle terminar.
3. Ligue um dispositivo e carregue em ▶️ Run, ou use os scripts abaixo.

### 🛠️ Scripts de compilação + instalação

O caminho mais rápido a partir de um terminal na raiz do repositório - compila o APK de depuração, lista os dispositivos ligados através do `adb` e instala-o de uma só vez:

```bash
./build-android.sh     # Linux/macOS
build-android.bat      # Windows
```

Se o `adb` não estiver no `PATH`, o script termina na mesma a compilação e imprime onde o APK ficou, para o instalar à mão.

### ⚙️ Compilação manual

Passos equivalentes sem os scripts, para o CI ou um terminal simples:

```bash
./gradlew assembleDebug        # Linux/macOS
gradlew.bat assembleDebug      # Windows
```

O APK fica em `app/build/outputs/apk/debug/app-debug.apk`. Instale-o com `adb install -r -d app/build/outputs/apk/debug/app-debug.apk`, ou transfira-o para o dispositivo manualmente. Troque `assembleDebug` por `assembleRelease` para uma compilação de lançamento - assina atualmente com a chave de depuração (o bloco `release` do próprio `app/build.gradle.kts`, mantido assim para facilitar os testes), pelo que instala bem mas não está pronta para distribuição tal como está.

## 🔢 Versionamento

Este repositório segue uma política de todo o ecossistema: a versão sobe automaticamente em **cada compilação real**, sem edição manual do `versionName`/`versionCode` do `app/build.gradle.kts`. O `app/version.properties` guarda o `versionMajor`/`versionMinor`/`versionPatch`/`versionCode` atuais; o `build-android.bat`/`.sh` executam o `bump_manifest_version.py` (versão nativa + manifesto `hydra-umc.project.json` + `CHANGELOG.md`, mantidos em sincronia) e o `bump_version_code.py` (o `versionCode` do Android, separado e sempre monótono) *antes* de invocar o Gradle. O próprio `app/build.gradle.kts` só *lê* agora o `version.properties` no momento da configuração - costumava escrevê-lo também em qualquer tarefa real do Gradle, mas uma simples execução de verificação `compileDebugKotlin` feita dessa forma fazia avançar em silêncio o `versionPatch`/`versionCode` sem que o manifesto se movesse (um erro real de deriva, descoberto em direto e corrigido - ver o `CHANGELOG.md`), pelo que os dois scripts de subida são hoje a única fonte real de uma subida. Passar pelo `build-android.bat`/`.sh` (ou pelos scripts de lançamento equivalentes) produz sempre uma versão estritamente mais recente do que a última:

- **Patch, estilo odómetro (base 10):** +1 em cada compilação; quando passaria de 9 volta a 0 e é o minor que ganha +1 - p. ex. `0.0.9` -> `0.1.0`. O major nunca é tocado automaticamente.
- **`versionCode`:** um contador monótono simples, +1 em cada compilação, sem transporte - o Android exige que aumente estritamente em cada compilação que alguma vez seja distribuída.

Um simples `./gradlew assembleDebug` (o caminho de «Compilação manual» acima, sem que nenhum dos scripts de subida tenha corrido antes) reutiliza o que o `version.properties` tinha por último - não sobe nada por si. A versão em execução é visível em direto na caixa **Acerca de** (`BuildConfig.VERSION_NAME`, que lê o `versionName` que o `version.properties` tiver por último). Consulte o [CHANGELOG.md](CHANGELOG.md) para o histórico de versões.

## 📲 Testar contra um servidor em direto

1. Execute o back-end: `cd HYDRA-UMC-SERVER && npm run dev` (porta 3000) - esta é a API REST/WS real com que esta aplicação fala (ver Projetos relacionados abaixo); o `npm run dev` do próprio `HYDRA-UMC-STUDIO` apenas arranca o seu servidor de desenvolvimento Vite de frontend (porta 5173) contra esse mesmo back-end, não é o servidor da API em si.
2. Ligue o seu dispositivo Android à mesma rede Wi-Fi.
3. Use o **Seletor global de servidor** ou introduza o IP manualmente no cabeçalho.
4. **Biometria:** ative «Início de sessão biométrico» no seu Perfil do utilizador para saltar o ecrã de palavra-passe no próximo arranque.

## 🩺 Resolução de problemas

| Sintoma | Causa | Solução |
|---|---|---|
| Sem notificações | Permissão negada | Conceda a permissão «Notificações» nas definições do Android para esta aplicação |
| Sem biometria | Hardware não configurado | Garanta que tem uma impressão digital/rosto registado na Segurança do sistema Android |
| O robô não se move | Sessão expirada, robô reservado por outra conta ou trajetória em curso | Leia a mensagem na barra de estado; o servidor reproduz as trajetórias por si e não é preciso nenhum separador do navegador |
| Bluetooth desativado | Chip físico desligado | Use o botão 3D «ENABLE SYSTEM BT» na aplicação |

## 📂 Estrutura do repositório

```text
HYDRA-UMC-ANDROID-CONTROL/
├── app/
│   ├── build.gradle.kts          # Configuração Gradle do módulo da aplicação - versões AGP/Kotlin/Compose, dependências, tipo de compilação release assinado com a chave de depuração
│   ├── version.properties        # Versão da aplicação em odómetro + versionCode do Android, mantidos em sincronia pelo bump_manifest_version.py/bump_version_code.py
│   ├── proguard-rules.pro        # Regras de redução/ofuscação de código da compilação de lançamento
│   └── src/main/
│       ├── AndroidManifest.xml   # Permissões, declarações de atividades/recetores, usesCleartextTraffic (servidor LAN em HTTP simples, sem TLS)
│       ├── java/com/hydraumc/control/
│       │   ├── MainActivity.kt          # Ponto de entrada - splash, controlo de acesso ao ecrã de login/principal, tratamento do E-STOP global seguro a frio
│       │   ├── MainScreen.kt            # Esqueleto de navegação inferior, barra superior (seletor de servidor, perfil, telemetria, definições)
│       │   ├── kinematics/
│       │   │   └── Parol6Kinematics.kt   # Cinemática direta/inversa específica do Parol6
│       │   ├── model/
│       │   │   ├── BleDevice.kt          # Classe de dados do resultado de uma análise Bluetooth LE
│       │   │   └── HydraState.kt         # Espelho campo a campo do settings.json (RobotView/ControllerView/JobView) + modelo de descoberta ServerInfo
│       │   ├── network/
│       │   │   ├── AuthPrefs.kt           # Armazenamento cifrado (AES256-GCM) de credenciais/sessão
│       │   │   ├── ConnectionPrefs.kt     # IP/porta do servidor persistidos (DataStore Preferences)
│       │   │   ├── Discovery.kt           # Análise concorrente de sub-rede /24 (principal) + ouvinte NSD/mDNS (secundário) para encontrar um servidor na LAN
│       │   │   ├── HydraApiClient.kt      # Cliente REST - login, leitura/escrita de definições, comandos atómicos de robô, métricas do sistema
│       │   │   ├── HydraBleClient.kt      # Cliente GATT Bluetooth, transporte alternativo ao Wi-Fi
│       │   │   ├── HydraWebSocket.kt      # Envio em direto de deltas de estado por WS, tratamento de reconexão
│       │   │   ├── StateCache.kt          # Cache do último estado conhecido (DataStore) para ver o painel offline
│       │   │   └── StopAllResult.kt       # Resultado do STOP global (resposta por robô, estado)
│       │   ├── ui/
│       │   │   ├── AboutDialog.kt          # Caixa de informação da aplicação/versão
│       │   │   ├── CameraScreen.kt         # Sinal de câmara MJPEG por robô + interruptor de visão ligada/desligada
│       │   │   ├── ControlScreen.kt        # Controlos manuais de jog, E-STOP/play/pause/stop com proteção de pressão longa
│       │   │   ├── DashboardScreen.kt      # Seletor de robôs em carrossel 3D + saúde do sistema + matriz de módulos
│       │   │   ├── GlobalStop.kt           # Botão STOP global + aviso de resultado (um toque, todos os robôs)
│       │   │   ├── Joystick3D.kt           # Componente reutilizável de joystick de 2 eixos
│       │   │   ├── LoginScreen.kt          # Utilizador/palavra-passe + IP/porta, início de sessão biométrico
│       │   │   ├── MjpegPlayer.kt          # Analisador de fluxo MJPEG + renderizador Canvas
│       │   │   ├── PlaybackConsole.kt      # Consola flutuante partilhada de STOP/play/pause/stop
│       │   │   ├── SettingsScreen.kt       # Interface de análise Wi-Fi/Bluetooth, definições de ligação
│       │   │   ├── SplashScreen.kt         # Ecrã de abertura Compose personalizado
│       │   │   ├── TelemetryScreen.kt      # Visualizador de registos de eventos/sincronização ao estilo de terminal
│       │   │   ├── ThreeDScreen.kt         # Vista 3D real - WebView que embebe a cena 3D sem cabeça do próprio STUDIO
│       │   │   ├── UserProfileDialog.kt    # Edição de perfil + caixa de alternância biométrica
│       │   │   ├── VoiceAssistantDialog.kt # Botão de microfone/texto na aplicação para o HYDRA-UMC-VOICE-UI
│       │   │   └── theme/
│       │   │       ├── Color.kt, Theme.kt, Typography.kt   # Esquema de cores Material 3, invólucro de tema, escala tipográfica
│       │   │       └── HydraButton.kt, IndustrialComponents.kt, IndustrialStyle.kt   # Blocos de interface partilhados com estilo industrial
│       │   ├── update/
│       │   │   ├── GitHubReleaseUpdater.kt   # Cliente seguro de atualização por Release do GitHub
│       │   │   ├── ReleaseMetadataParser.kt  # Analisador seguro de metadados de Release do GitHub
│       │   │   └── SemanticVersion.kt        # Analisador estrito de versão semântica para atualizações
│       │   ├── util/
│       │   │   ├── BiometricHelper.kt      # Invólucro do pedido androidx.biometric
│       │   │   ├── JsonText.kt             # optStr: um null JSON é o valor de recurso, nunca o texto «null»
│       │   │   ├── NotificationHelper.kt   # Notificações push de trabalho concluído/segurança
│       │   │   └── NotificationPrefs.kt    # Armazenamento persistente do interruptor de notificações na aplicação
│       │   ├── viewmodel/
│       │   │   ├── AppUpdateViewModel.kt   # Estado de atualização da aplicação ciente do ciclo de vida
│       │   │   └── RobotViewModel.kt   # ViewModel partilhado - rede, autenticação, descoberta, despacho atómico de comandos, todo o estado da interface
│       │   ├── wear/
│       │   │   ├── BoundedRequestScope.kt     # Âmbito de trabalhos em curso por requestId; uma tentativa substituída é cancelada, cancelAll() ao destruir o serviço
│       │   │   ├── WatchCompanionProtocol.kt    # Contrato de rede do estado de versão do companheiro Watch
│       │   │   ├── WatchVoiceRelayContract.kt   # Contrato de rede autenticado do relé de voz do Watch
│       │   │   └── WatchVoiceRelayService.kt    # Serviço de relé de voz Wear OS
│       │   └── widget/
│       │       └── GlobalStopWidget.kt # Widget de ecrã inicial para um E-STOP global
│       └── res/
│           ├── drawable/, layout/, mipmap*/, xml/   # Ícones, disposição do widget, ícones de arranque, regras de cópia de segurança/extração de dados
│           └── values/, values-es/, values-de/, values-fr/, values-it/, values-ja/, values-zh/, values-pt/   # Cadeias em 8 idiomas, cores, tema
├── docs/
│   ├── ARCHITECTURE.md              # Notas de desenho/arquitetura
│   ├── GITHUB_RELEASE_UPDATES.md    # Fluxo de verificação/descarga/instalação da atualização na aplicação
│   └── WATCH_VOICE_RELAY.md         # Contrato do relé de voz Watch-telemóvel-servidor
├── images/                       # Faixa do README + ativos de origem do ecrã de abertura
├── tools/
│   ├── build_test.py             # Verificação de compilação sem subir versão
│   └── ci_validate.py            # Validação de manifesto/CHANGELOG/docs usada pelo CI
├── dist/                         # Saída do APK de lançamento assinado (ignorado pelo git)
├── build-android.bat / .sh       # Scripts de conveniência de compilação num só passo + instalação por adb
├── build-test.bat / .sh          # Verificação de compilação sem subir versão
├── prepare-github-release.bat / .sh  # Constrói um APK de lançamento estável, assinado em privado, sem subir a versão
├── publish-github-release.ps1 / .sh  # Só local: publica o APK de dist/ como uma Release do GitHub
├── bump_manifest_version.py      # Sincroniza a versão do hydra-umc.project.json com a nativa (--sync)
├── bump_version_code.py          # Incrementa o contador versionCode do próprio Android no app/version.properties
├── gradlew, gradlew.bat          # Wrapper do Gradle
├── build.gradle.kts, settings.gradle.kts, gradle.properties   # Configuração Gradle do projeto raiz
├── local.properties              # Caminho local do Android SDK (específico da máquina, não é enviado)
├── keystore.properties.example   # Modelo da configuração privada de assinatura de lançamento
├── .env.example                  # Exemplo de variáveis de ambiente
├── metadata.json                 # Metadados da ficha da loja de aplicações (nome/descrição)
├── README.md                     # Este ficheiro
├── README_spa.md / README_ita.md / README_fra.md / README_deu.md / README_zho.md / README_jpn.md / README_por.md   # Traduções
└── LICENSE                       # GPL-3.0-or-later
```

---

## 🔗 Projetos relacionados

Este projeto faz parte do ecossistema de robótica HYDRA-UMC do mesmo autor (JuanenRac / Electro Hobby 3D). Vale a pena conhecê-los, porque um pedido pode ser, na verdade, sobre um destes e não sobre este repositório.

**Projeto-mãe**
- **[HYDRA-UMC-SERVER](https://github.com/JuanenRac/HYDRA-UMC-SERVER)** — o back-end headless real (REST/WebSocket) com que todos os clientes de controlo realmente comunicam; o back-end contra o qual correm a descoberta, a autenticação e a sincronização WebSocket desta aplicação.

**Projetos irmãos** — também falam com a API do próprio HYDRA-UMC-SERVER, cada um como cliente próprio
- **[HYDRA-UMC-STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO)** — painel web de controlo com visualização 3D em tempo real de vários robôs; a sua própria vista 3D está embebida diretamente no ecrã de Vista 3D desta aplicação através de uma WebView.
- **[HYDRA-UMC-SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE)** — centro de comando de enxame para desktop (PySide6) para vários servidores ao mesmo tempo, empacotado como executável independente; fala exatamente o mesmo contrato `REMOTE_API.md` que esta aplicação.
- **[HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL)** — aplicação de controlo para iOS/iPadOS (Flutter) com sincronização WebSocket em tempo real; a contraparte direta desta aplicação em iOS/iPadOS, com as mesmas funcionalidades.
- **[HYDRA-UMC-DSI](https://github.com/JuanenRac/HYDRA-UMC-DSI)** — interface tátil nativa para o ecrã tátil DSI de 7" integrado, executada no próprio CM5.
- **[HYDRA-UMC-BRIDGE-AMR](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-AMR)** — fronteira de coordenação para frotas AGV/AMR através de um publicador MQTT VDA 5050 (testado apenas contra uma frota emulada).
- **[HYDRA-UMC-BRIDGE-CNC](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-CNC)** — coordenador de células CNC de alto nível com acesso ao estado GRBL e aos bytes de controlo (testado apenas contra um emulador).
- **[HYDRA-UMC-BRIDGE-DROIDS](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-DROIDS)** — fronteira de coordenação para droides com patas/humanoides, com um emissor de comandos para o Boston Dynamics Spot (testado apenas contra um emulador).
- **[HYDRA-UMC-BRIDGE-LASER](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-LASER)** — coordenador de células laser que lê 3 sinais GPIO (chave, caixa e interlock) (testado apenas contra um chip simulado; não pode armar nem disparar um laser).
- **[HYDRA-UMC-BRIDGE-OPENPNP](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-OPENPNP)** — coordenador seguro de fluxo de placa de alto nível para pick-and-place com OpenPnP.
- **[HYDRA-UMC-BRIDGE-PRINTER3D](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-PRINTER3D)** — fronteira de coordenação segura para impressoras 3D Moonraker/Klipper, com comandos de trabalho reais protegidos por portas.
- **[HYDRA-UMC-BRIDGE-ROS2](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-ROS2)** — coordenador com um transporte ROS 2 baseado em rclpy importado sob demanda (ainda não executado contra uma instalação ROS 2).
- **[HYDRA-UMC-BRIDGE-UAV](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-UAV)** — fronteira de coordenação para UAV com câmara, com um emissor de comandos MAVLink (testado apenas contra um emulador).

**Diretamente relacionados**
- **[HYDRA-UMC-WATCH](https://github.com/JuanenRac/HYDRA-UMC-WATCH)** — aplicação companheira WearOS com padrões de alerta tátil e um relé de voz para o telemóvel emparelhado (ainda não executada num relógio físico); o companheiro WearOS desta aplicação, para ver o estado do robô e controlá-lo de relance a partir do pulso.
- **[HYDRA-UMC-HIL-BRIDGE](https://github.com/JuanenRac/HYDRA-UMC-HIL-BRIDGE)** — encaminhador hardware-in-the-loop com um interlock de software de colisão entre a simulação e o hardware real (testado sem hardware real); permite o controlo remoto do gémeo digital diretamente a partir desta aplicação.

**Também fazem parte do ecossistema**

*Hardware e plataforma centrais*
- **[HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC)** — o desenho da placa-mãe do braço robótico: anfitrião CM5 + STM32H745 de dois núcleos, pensado para orquestrar até 8 braços de ferramenta por CAN-OTA/SPI-OTA (firmware numa fase inicial; ainda sem placa montada).
- **[HYDRA-UMC-OS](https://github.com/JuanenRac/HYDRA-UMC-OS)** — camada de produto reproduzível do Raspberry Pi OS para o CM5: agente só de leitura, configuração/perfis validados, aprovisionamento Wi-Fi de primeiro contacto.
- **[HYDRA-UMC-SDK](https://github.com/JuanenRac/HYDRA-UMC-SDK)** — o contrato JSON-Schema partilhado e a fronteira de software de controlo de trabalhos contra a qual cada bridge valida os seus comandos (não é uma função de segurança certificada).
- **[HYDRA-UMC-CONNECTOR-HUB](https://github.com/JuanenRac/HYDRA-UMC-CONNECTOR-HUB)** — registo declarativo de manifestos de adaptador e validador para conectores de máquinas externas; estende a ideia de contrato do SDK a máquinas externas sem substituir os projetos de gateway industrial.

*Backend central e clientes*
- **[HYDRA-UMC-EDITOR-URDF](https://github.com/JuanenRac/HYDRA-UMC-EDITOR-URDF)** — criador/editor gráfico de URDF para desktop que envia os modelos concluídos para o catálogo do STUDIO.
- **[HYDRA-UMC-EDITOR-STL](https://github.com/JuanenRac/HYDRA-UMC-EDITOR-STL)** — editor de modelos STL para desktop que transforma/substitui/remove/adiciona peças reais no mesmo catálogo de modelos que o HYDRA-UMC-EDITOR-URDF também edita.

*Plataforma de ferramentas URTC*
- **[URTC](https://github.com/JuanenRac/URTC)** — firmware da placa física Universal Robot Tool Controller, mais de 25 perfis de ferramenta por barramento CAN.
- **[URTC-FLASHER](https://github.com/JuanenRac/URTC-FLASHER)** — ferramenta gráfica de gravação para desktop das placas URTC, por CAN-OTA e também SWD/JTAG de chip completo.
- **[URTC-TESTER](https://github.com/JuanenRac/URTC-TESTER)** — ferramenta de diagnóstico CAN em tempo real para desktop das placas URTC, com um painel por perfil de ferramenta.
- **[URTC-WEB-STUDIO](https://github.com/JuanenRac/URTC-WEB-STUDIO)** — alternativa no navegador ao URTC-TESTER através da Web Serial API, sem instalação local.

*Nó de IA de visão (Hailo-8)*
- **[HYDRA-UMC-VISION-NODE](https://github.com/JuanenRac/HYDRA-UMC-VISION-NODE)** — centro de integração do pipeline de visão Hailo-8, com uma verificação real de prontidão de hardware por fase.
- **[HYDRA-UMC-DETECTION-HEF](https://github.com/JuanenRac/HYDRA-UMC-DETECTION-HEF)** — registo real de modelos compilados com verificação segura de carga por arquitetura Hailo e soma de verificação.
- **[HYDRA-UMC-VISION-STREAMER](https://github.com/JuanenRac/HYDRA-UMC-VISION-STREAMER)** — pipeline GStreamer real e gerador de configuração MediaMTX com uma fronteira de integração HailoRT real.
- **[HYDRA-UMC-VISUAL-SERVOING-API](https://github.com/JuanenRac/HYDRA-UMC-VISUAL-SERVOING-API)** — lei de correção de servovisão baseada em posição com autorização condicionada ao estado da zona; ainda sem estimativa de pose por câmara.
- **[HYDRA-UMC-SAFETY-ZONES](https://github.com/JuanenRac/HYDRA-UMC-SAFETY-ZONES)** — lógica v0 de violação de zonas, atualidade da calibração e pedido de E-STOP que lê as posições dos objetos de um ficheiro; nenhum detetor a alimenta e ainda não há E-STOP físico ligado.

*Nó de IA cognitiva (Hailo-10)*
- **[HYDRA-UMC-COGNITIVE-NODE](https://github.com/JuanenRac/HYDRA-UMC-COGNITIVE-NODE)** — centro de integração do pipeline cognitivo do Hailo-10 (orquestração de LLM/VLA/voz).
- **[HYDRA-UMC-VLA-ENGINE](https://github.com/JuanenRac/HYDRA-UMC-VLA-ENGINE)** — codificação/descodificação real de tokens de ação e geração de trajetórias para um modelo Visão-Linguagem-Ação.
- **[HYDRA-UMC-VOICE-UI](https://github.com/JuanenRac/HYDRA-UMC-VOICE-UI)** — front-end de voz real (VAD + analisador de intenções) com um relé para o relógio limitado e protegido por confirmação.
- **[HYDRA-UMC-SEMANTIC-PLANNER](https://github.com/JuanenRac/HYDRA-UMC-SEMANTIC-PLANNER)** — decomposição real de tarefas baseada em regras e recuperação semântica de erros sobre os códigos de erro do MCU.
- **[HYDRA-UMC-DOCS-QA](https://github.com/JuanenRac/HYDRA-UMC-DOCS-QA)** — pesquisa real de documentos TF-IDF só com a biblioteca padrão sobre a documentação Markdown deste ecossistema.
- **[HYDRA-UMC-LOCAL-TECHNICIAN](https://github.com/JuanenRac/HYDRA-UMC-LOCAL-TECHNICIAN)** — técnico de manutenção de IA local, protegido por políticas, para o próprio ecossistema: observa, diagnostica e propõe correções; os dois níveis de maior risco não estão implementados de propósito.

*Orquestração e enxame*
- **[HYDRA-UMC-ORCHESTRATOR](https://github.com/JuanenRac/HYDRA-UMC-ORCHESTRATOR)** — centro de integração com um contrato real de relatório de saúde gRPC/Protobuf e uma máquina de estados de missões.
- **[HYDRA-UMC-JOB-DISPATCHER](https://github.com/JuanenRac/HYDRA-UMC-JOB-DISPATCHER)** — fila real de trabalhos por prioridade com deduplicação, sobre uma API HTTP real.
- **[HYDRA-UMC-NODE-HEALING](https://github.com/JuanenRac/HYDRA-UMC-NODE-HEALING)** — vigilante real de saúde da frota por gRPC com repetição/recuo e deteção de identidade incoerente.
- **[HYDRA-UMC-PATH-PLANNER-3D](https://github.com/JuanenRac/HYDRA-UMC-PATH-PLANNER-3D)** — planeador real de trajetórias 3D baseado em RRT com validação real de colisões com obstáculos e espaço de trabalho.
- **[HYDRA-UMC-SWARM-SYNC](https://github.com/JuanenRac/HYDRA-UMC-SWARM-SYNC)** — sincronização real de estado CRDT LWW-Element-Map, testada por propriedades para a convergência de várias células.

*Gémeo digital e simulação*
- **[HYDRA-UMC-TWIN](https://github.com/JuanenRac/HYDRA-UMC-TWIN)** — centro de integração do motor de gémeo digital, com um contrato real de sincronização de compatibilidade de versões.
- **[HYDRA-UMC-PHYSICS-REPLICA](https://github.com/JuanenRac/HYDRA-UMC-PHYSICS-REPLICA)** — cinemática direta real e validação de limites de juntas sobre um subconjunto real de URDF.
- **[HYDRA-UMC-SYNTHETIC-DATA-GEN](https://github.com/JuanenRac/HYDRA-UMC-SYNTHETIC-DATA-GEN)** — gerador procedural real de cenas 2D com exportação de anotações YOLO/COCO.

*Dados e análise*
- **[HYDRA-UMC-DATALAKE](https://github.com/JuanenRac/HYDRA-UMC-DATALAKE)** — armazém real de séries temporais sobre sqlite3 com uma API HTTP real de ingestão/consulta.
- **[HYDRA-UMC-ANOMALY-DETECTOR](https://github.com/JuanenRac/HYDRA-UMC-ANOMALY-DETECTOR)** — detetor real de anomalias por FFT e linha de base estatística, com monitorização de deriva.
- **[HYDRA-UMC-PRODUCTION-REPORTS](https://github.com/JuanenRac/HYDRA-UMC-PRODUCTION-REPORTS)** — cálculo real de OEE/disponibilidade sobre o histórico do DATALAKE, com exportação CSV reprodutível.
- **[HYDRA-UMC-TELEMETRY-COLLECTOR](https://github.com/JuanenRac/HYDRA-UMC-TELEMETRY-COLLECTOR)** — pipeline real de ingestão CAN/WebSocket para o DATALAKE, com deduplicação por sequência.

*Gateway industrial*
- **[HYDRA-UMC-GATEWAY-INDUSTRIAL](https://github.com/JuanenRac/HYDRA-UMC-GATEWAY-INDUSTRIAL)** — centro de integração que retransmite para protocolos industriais, com uma camada real de lista de comandos permitidos e contrapressão.
- **[HYDRA-UMC-OPCUA-SERVER](https://github.com/JuanenRac/HYDRA-UMC-OPCUA-SERVER)** — espaço de endereços OPC-UA real, verificado com uma sessão de cliente real do protocolo binário.
- **[HYDRA-UMC-MQTT-BROKER](https://github.com/JuanenRac/HYDRA-UMC-MQTT-BROKER)** — broker MQTT real com autenticação opcional por cliente e ACL de tópicos.
- **[HYDRA-UMC-MTCONNECT-ADAPTER](https://github.com/JuanenRac/HYDRA-UMC-MTCONNECT-ADAPTER)** — endpoints XML MTConnect `/probe` e `/current` reais com saída em modo degradado.

*Ferramentas complementares e operações do ecossistema*
- **[HYDRA-UMC-DASHBOARD-AI](https://github.com/JuanenRac/HYDRA-UMC-DASHBOARD-AI)** — painéis de Resumos Inteligentes e Realce de Anomalias sobre o DATALAKE/ANOMALY-DETECTOR, com um recurso estatístico honesto.
- **[HYDRA-UMC-TOOL-CLI](https://github.com/JuanenRac/HYDRA-UMC-TOOL-CLI)** — CLI da frota com um contrato de códigos de saída real e estável, um cliente vivo genuíno da API do HYDRA-UMC-SERVER.
- **[URTC-SMART-RACK](https://github.com/JuanenRac/URTC-SMART-RACK)** — firmware de um rack de montagem de placas com descodificação real do ID da ferramenta e lógica de pré-aquecimento Smart Idle.
- **[URTC-VISION-TOOL](https://github.com/JuanenRac/URTC-VISION-TOOL)** — firmware mais um companheiro de visão em Python real para uma cabeça de ferramenta de inspeção térmica/RGB.
- **[HYDRA-UMC-UPDATER](https://github.com/JuanenRac/HYDRA-UMC-UPDATER)** — ferramenta administrativa de desktop que descobre, clona e atualiza todos os repositórios deste ecossistema.
- **[HYDRA-UMC-OS-REBUILDER](https://github.com/JuanenRac/HYDRA-UMC-OS-REBUILDER)** — ferramenta de desktop para Windows/Linux que constrói uma imagem do CM5 pronta a gravar com as versões mais recentes do ecossistema, com configuração de primeiro arranque de Wi-Fi/utilizador/SSH ao estilo do Raspberry Pi Imager.
- **[HYDRA-UMC-OPS-AGENT](https://github.com/JuanenRac/HYDRA-UMC-OPS-AGENT)** — coordenador de incidentes de manutenção: um papel de borda de poucos privilégios recolhe um inventário/estado higienizado, um papel de plano de controlo mostra-o só de leitura e pede a um fornecedor de IA que sugira um diagnóstico; nunca aplica um patch nem implanta nada.
- **[HYDRA-UMC-DEV-SERVER](https://github.com/JuanenRac/HYDRA-UMC-DEV-SERVER)** — anfitrião de desenvolvimento reproduzível (Raspberry Pi 5 / CM5) que guarda o código-fonte do ecossistema e executa tarefas limitadas de compilação/teste numa fila durável; um papel de desenvolvimento dedicado, explicitamente não um CM5 operacional.

---

## 📚 Documentação e comunidade

- **[CONTRIBUTING.md](CONTRIBUTING.md)** — pilha tecnológica e diretrizes de código para um pull request.
- **[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)** — as normas de comportamento esperadas nesta comunidade.
- **[SECURITY.md](SECURITY.md)** — como comunicar uma vulnerabilidade e as áreas de segurança em que este projeto se concentra.
- **[SUPPORT.md](SUPPORT.md)** — onde fazer perguntas e comunicar erros.
- **[LICENSE.md](LICENSE.md)** — a licença deste projeto.

## 👤 AUTOR
**JuanenRac** (Electro Hobby 3D)
📧 electrohobby3d@gmail.com
📺 [youtube.com/@electrohobby3d](https://youtube.com/@electrohobby3d)

## 📜 LICENÇA

**GNU General Public License v3.0 or later (GPL-3.0-or-later)** para o código-fonte - veja [`LICENSE`](LICENSE).

Esta documentação (este README e as suas próprias traduções - `README_spa.md`, `README_ita.md`, `README_fra.md`, `README_deu.md`, `README_zho.md`, `README_jpn.md`, `README_por.md`) está disponível sob a **Creative Commons Attribution-ShareAlike 4.0 International (CC BY-SA 4.0)**. Texto completo em https://creativecommons.org/licenses/by-sa/4.0/.

