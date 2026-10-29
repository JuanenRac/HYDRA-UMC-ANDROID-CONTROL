<!-- =============================================================================
HYDRA-UMC-ANDROID-CONTROL - Android operator console for a HYDRA-UMC server: control, cameras, 3D view and a global STOP
Copyright (C) JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
GPL-3.0-or-later - see LICENSE
============================================================================= -->

<p align="center">
  <img src="images/HYDRA_UMC_BANNER.svg" alt="HYDRA-UMC-ANDROID-CONTROL banner" width="100%">
</p>

# 📱 HYDRA-UMC-ANDROID-CONTROL

<p align="center"><a href="README.md">🇺🇸 English</a> | <a href="README_spa.md">🇪🇸 Español</a> | <a href="README_fra.md">🇫🇷 Français</a> | <a href="README_ita.md">🇮🇹 Italiano</a> | <a href="README_deu.md">🇩🇪 Deutsch</a> | <a href="README_zho.md">🇨🇳 简体中文</a> | <a href="README_jpn.md">🇯🇵 日本語</a> | 🇵🇹 <b>Português</b></p>

### Os seus robôs HYDRA-UMC no bolso: encontre o servidor, mova e vigie os robôs e pare tudo com um botão vermelho.

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

Uma aplicação Android nativa (Kotlin + Jetpack Compose) que controla um robô da plataforma [HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC) por Wi-Fi ou Bluetooth, falando exatamente o mesmo contrato [`REMOTE_API.md`](https://github.com/JuanenRac/HYDRA-UMC-SERVER/blob/main/docs/REMOTE_API.md) que o [HYDRA-UMC SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE) usa - descoberta, leitura/escrita do estado completo e sincronização WebSocket em direto contra um back-end [HYDRA-UMC-SERVER](https://github.com/JuanenRac/HYDRA-UMC-SERVER) em execução (o mesmo com que o painel web do próprio [HYDRA-UMC STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO) fala). Contraparte Android direta do [HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL). Consulte o [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) para o desenho completo.

> **Verificação de honestidade - o que realmente funciona hoje:** a lógica de negócio central - a cinemática do Parol6 (`kinematics/Parol6Kinematics.kt`), o contrato de estado da árvore de definições (`model/HydraState.kt`), o despacho atómico de comandos e o relé de turnos de voz (`viewmodel/RobotViewModel.kt`), o tratamento de religação do WebSocket (`network/HydraWebSocket.kt`), a análise de fotogramas MJPEG (`ui/MjpegPlayer.kt`), o canal de atualização na própria aplicação (`update/GitHubReleaseUpdater.kt`, `ReleaseMetadataParser.kt`, `SemanticVersion.kt`) e a contabilidade de pedidos do relé de voz do Wear (`wear/BoundedRequestScope.kt`, `WatchCompanionProtocol.kt`) são reais e cobertos por 303 testes unitários JVM/Robolectric a passar (`./gradlew testDebugUnitTest`). O que esses testes estruturalmente não alcançam - e que este ambiente de desenvolvimento só para Windows, sem dispositivo Android físico, não consegue verificar - é o `EncryptedSharedPreferences` real apoiado no Keystore do `network/AuthPrefs.kt`, o pedido biométrico de impressão digital/rosto do `androidx.biometric`, o transporte GATT Bluetooth do `network/HydraBleClient.kt` e o caminho de hardware `ImageDecoder` da API 28+ no `MjpegStreamParser`: código real e compilado que não foi reverificado em hardware desde que foi escrito. O arranque/início de sessão básicos *foram* confirmados em direto num dispositivo real em versões anteriores (ver as entradas `v0.3.2`/`v0.3.3` do `CHANGELOG.md`), mas isso é anterior ao Bluetooth, à biometria e ao caminho de descodificação por hardware. Consulte o `CHANGELOG.md` para saber exatamente o que foi entregue, incluindo as suas notas por entrada sobre o que continua por verificar em hardware.

> **Estado de segurança (safety e security):** este ecossistema não é um sistema de segurança certificado. Ainda não existe um caminho de E-STOP por hardware, nem limites ou interlocks impostos pelo firmware, nem um comando de movimento verificado; as imagens de firmware são verificadas com CRC-32 e uma assinatura Ed25519, e uma compilação de versão recusa as chaves de desenvolvimento publicadas (as cabeças de ferramenta e as placas de expansão atrás do relé ainda usam os bootloaders do URTC, que aceitam um HMAC-SHA256 de chave partilhada em vez de uma assinatura); TLS e TLS mútuo são opcionais e estão desativados por defeito; nenhuma placa própria foi ainda montada ou testada. Leia a [base de segurança](https://github.com/JuanenRac/HYDRA-UMC-DOCS/blob/main/docs/SECURITY_BASELINE.md) e o [modelo de ameaças](https://github.com/JuanenRac/HYDRA-UMC-DOCS/blob/main/docs/THREAT_MODEL.md) antes de confiar em qualquer afirmação aqui para a segurança de uma pessoa.

---

## 📋 Num relance

| | |
|---|---|
| Versão | `0.7.2` |
| Maturidade | Funcional |
| Testes | 303 (`./gradlew testDebugUnitTest`) |
| Tecnologia | Kotlin / Jetpack / biometric auth / WebSocket client |
| Destino | Um telemóvel ou tablet Android |
| Em hardware real | Ainda não: não há registo de execução em hardware real para esta versão |

**Ir para:** [Visão geral](#-visão-geral) · [Arquitetura](#-arquitetura) · [Funcionalidades](#-funcionalidades) · [Segurança e proteção](#-segurança-e-proteção) · [Interfaces](#-interfaces) · [Configuração](#-configuração) · [Início rápido](#-início-rápido) · [Testes](#-testes) · [Resolução de problemas](#-resolução-de-problemas) · [Estrutura do repositório](#-estrutura-do-repositório) · [Projetos relacionados](#-projetos-relacionados)

## 🚦 Estado do projeto

Cada linha é uma parte deste repositório e a luz diz até onde chega hoje. É escrita com o que o repositório consegue demonstrar (testes, compilações, execuções), não com o que está previsto.

🟢 Feito e verificado · 🟡 Funciona, com limites conhecidos · 🟠 Feito a meio ou ainda por verificar · 🔴 Por fazer ou a não funcionar

| Área | Estado | O que significa aqui |
|---|---|---|
| Software | 🟡 | Funciona para o seu propósito principal; a verificação de honestidade acima lista o que ainda falta |
| Testes | 🟢 | 303 testes automáticos, executados a cada alteração |
| Hardware real | 🔴 | Ainda não: não há registo de execução em hardware real para esta versão |
| Documentação | 🟢 | README em oito idiomas, verificado contra o manifesto a cada alteração |

## 🎯 Visão geral

Ecrãs da aplicação: **início de sessão** (endereço do servidor, palavra-passe, impressão digital ou rosto), **painel** (um carrossel 3D dos robôs com a sua matriz de módulos e a saúde do módulo de cálculo), **controlo** (joysticks, reproduzir, pausar, parar, reserva do robô), **câmaras** (MJPEG em direto com imagem em imagem), **vista 3D** (a cena real do STUDIO), **telemetria** (um registo ao estilo de terminal) e **definições** (ligação, segurança, notificações, atualizações). Um botão vermelho **Parar tudo** está na barra superior de cada ecrã e num widget do ecrã inicial.

## 🧩 Papel no HYDRA-UMC

A **HYDRA-UMC-ANDROID-CONTROL** é a consola nativa de operador Android do HYDRA-UMC: encontra um HYDRA-UMC-SERVER na rede local, inicia sessão e depois mostra os robôs, move-os, vigia as suas câmaras e uma vista 3D, e pára tudo com um toque. Fala o mesmo contrato remoto da SUITE e da aplicação iOS e guarda uma cópia do último estado para quando falta o Wi-Fi. Não é um dispositivo de segurança.

## 🏛️ Arquitetura

<p align="center">
  <img src="images/architecture.svg" alt="HYDRA-UMC-ANDROID-CONTROL architecture" width="100%">
</p>

## ✨ Funcionalidades

- **Controlo de acesso e biometria** (`ui/LoginScreen.kt`, `util/BiometricHelper.kt`) - sistema profissional de início de sessão com suporte a **impressão digital e desbloqueio facial** (`androidx.biometric`), mais campos de IP/porta no mesmo ecrã para que se possa apontar a um servidor sem uma ida separada às Definições. Inclui a funcionalidade «Lembrar-me», um mecanismo seguro de **Terminar sessão** e está totalmente localizada em **8 idiomas**. O utilizador/palavra-passe/token em cache (`network/AuthPrefs.kt`) vivem em **EncryptedSharedPreferences** apoiadas no Keystore (AES256-GCM), não em texto simples - um servidor de desenvolvimento (`NODE_ENV` por definir) cria uma conta `admin`/`admin` por defeito no primeiro arranque; um servidor de produção (`NODE_ENV=production`) exige em vez disso `HYDRA_UMC_BOOTSTRAP_ADMIN_USERNAME`/`HYDRA_UMC_BOOTSTRAP_ADMIN_PASSWORD` e nunca recorre em silêncio a `admin`/`admin` - ver o README do próprio HYDRA-UMC-SERVER para a configuração de produção completa. Contas adicionais de **operador**, com menos privilégios, podem ser criadas no servidor em Config > Users de qualquer das formas.
- **Modo offline e cache de estado** (`network/StateCache.kt`) - motor de persistência integrado que usa **DataStore**. A aplicação guarda automaticamente em cache o último estado conhecido do sistema, permitindo ver o painel e auditar a configuração de imediato mesmo sem uma ligação Wi-Fi ativa.
- **Notificações e alertas de missão** (`util/NotificationHelper.kt`) - sistema de alertas. Envia notificações push de alta prioridade quando um robô conclui uma sequência de trabalho ou se ocorrerem eventos críticos de hardware, garantindo que o operador é informado mesmo com a aplicação em segundo plano.
- **Terminal de telemetria industrial** (`ui/TelemetryScreen.kt`) - um visualizador de registos em tempo real dedicado, com interface ao estilo de terminal. Acompanha eventos do sistema e a sincronização REST/WebSocket e dá diagnósticos com código de cores (verde Matrix para êxito, vermelho industrial para erros).
- **Painel avançado** (`ui/DashboardScreen.kt`) - **carrossel horizontal 3D** de alta fidelidade com efeitos de deslize em perspetiva. Mostra metadados enriquecidos do robô: **fabricante** (Source Robotics, Annin, Universal Robots, AgileX, etc.), **papel do robô** (CNC, Laser, PnP) e uma **matriz de módulos industriais** com o estado em direto dos módulos CAM, XY, ATC, PNP, CNC, LSR, BED, VAC e RCK.
- **Monitor de saúde do sistema** (`ui/DashboardScreen.kt`) - métricas em tempo real do Compute Module 5 ligado, incluindo **nome do anfitrião**, **tempo ativo formatado** (p. ex. «2d 4h 15m») e contagens ativas de controladores e robôs.
- **Controlo manual melhorado** (`ui/ControlScreen.kt`) - apresenta uma disposição vertical profissional com **botões do joystick 50% maiores** para máxima precisão.
- **Painel de reprodução** (`ui/ControlScreen.kt`, `ui/PlaybackConsole.kt`) - barra de controlo fixa na parte inferior que aloja o botão vermelho **STOP** (o STOP global abaixo) e os botões **Iniciar**, **Pausa** e **Parar** (apenas o robô selecionado). Estes controlos estão sempre visíveis e oferecem **retorno háptico** para confirmação sensorial física.
- **STOP global** (`ui/GlobalStop.kt`, `network/HydraApiClient.kt`, `viewmodel/RobotViewModel.kt`) - um botão vermelho **STOP** (legenda "Parar tudo", traduzida nos 8 idiomas) na barra superior de todos os ecrãs, na consola do ecrã de Controlo e na vista 3D (vertical e em ecrã inteiro). Um toque, sem manter premido e sem confirmação: envia `POST /api/stop-all` de imediato e pára todos os robôs, mesmo reservados por outra conta. Mostra o resultado (que robôs pararam, quais não, quais estão reservados por outra pessoa e um aviso se o servidor não conseguiu guardar o estado de paragem), tenta novamente uma vez se ninguém responder, pára os robôs um a um se o servidor limitar a chamada (429) e mantém um aviso persistente **STOP NÃO CONFIRMADO** até que um STOP posterior resulte. É a paragem por software do servidor: não é um E-STOP de hardware e não corta a alimentação de nada.
- **Reservas e limites de segurança** (`ui/AccessPanel.kt`, `network/AccessModels.kt`, `network/HydraApiClient.kt`, `viewmodel/RobotViewModel.kt`) - O ecrã Control mostra quem detém o robô selecionado (ninguém, este telemóvel, a mesma conta noutro dispositivo ou outra conta) e oferece **Reservar** e **Libertar**, além da lista de todas as reservas do servidor (`GET /api/claims`). O telemóvel guarda um identificador de controlador por instalação e envia-o com cada reserva, libertação, armamento do jog e comando (no corpo e no cabeçalho `X-Controller-Id`), pelo que a reserva pertence à conta e a este telemóvel, não a toda a conta. As recusas do servidor distinguem-se: reservado por outra conta, pela sua conta noutro dispositivo ou janela, identificador de controlador obrigatório e jog não armado. Os limites do modelo do robô são lidos de `GET /api/safety-limits`: um passo de jog acima do limite não é enviado e os avisos do servidor são mostrados. Quando o administrador o exige, é preciso premir **Armar o jog** (com contagem decrescente) antes de o joystick mover o robô; o armamento termina sozinho, com STOP ou ao libertar. As reservas são opcionais: um robô que ninguém reservou é comandado como antes. Apenas para um **administrador**, aparece um botão **Assumir o controlo** num robô que outra pessoa tem; pede confirmação e depois reserva o robô com `force: true` (o servidor só o aceita de administradores e volta a verificar o papel). As escritas são condicionais: cada uma leva a revisão do robô que esta app viu pela última vez (`If-Match`, `network/ContractV2.kt`); se outra pessoa mudou o robô entretanto, o servidor responde 412, nada é aplicado, o robô é lido de novo e a mensagem di-lo (o modo Padrão verifica reservas, mesa e comandos pontuais; o Estrito verifica tudo exceto STOP; o Desligado não verifica nada; a escolha está na lista de reservas; o STOP nunca é verificado). O jog da mesa XY é um único comando atómico `table` (um servidor mais antigo recebe os jogs que entende), o servidor envia as reservas pelo WebSocket para o ecrã Control deixar de consultar enquanto chegam, e os administradores leem o registo de auditoria (`ui/AuditDialog.kt`) com a cadeia SHA-256 verificada duas vezes, pelo servidor e no telemóvel, e podem partilhar as suas linhas como `.jsonl`. O PLAY pergunta primeiro (o robô, os seus pontos gravados, a velocidade e quem o tem reservado), a menos que a pessoa o desative junto às reservas.
- **Vista 3D** (`ui/ThreeDScreen.kt`) - embebe a vista 3D em tempo real do próprio HYDRA-UMC STUDIO numa WebView (`?hideUI=true&robotId=#token=`), em vez de um renderizador nativo. A abordagem da WebView dá de graça a cena 3D real e atualmente distribuída do STUDIO (cada malha/cinemática de robô real) em vez de a reimplementar de forma nativa - o compromisso é a sobrecarga de renderização da WebView, não ideal para a bateria, mas funcionalmente completa hoje.
- **Visão octal em tempo real** (`ui/CameraScreen.kt`, `ui/MjpegPlayer.kt`) - **streamer MJPEG nativo**. Tem um analisador em segundo plano e um renderizador baseado em Canvas para vídeo de baixa latência, um estado claro «Câmara desativada» (em vez de uma imagem em branco silenciosa) quando o sistema de visão de um robô está desligado, e um interruptor para ligar/desligar a câmara de um robô diretamente a partir do servidor. Suporta sobreposições automáticas de **Picture-in-Picture (PIP)** no ecrã de controlo manual, associadas a robôs específicos através da configuração de câmaras do servidor. Na API 28+, cada fotograma é descodificado pelo próprio caminho `ImageDecoder` da plataforma, acelerado por hardware (um bitmap residente na GPU, evitando a descodificação/carregamento do lado do CPU), com um recurso automático e autorrecuperável para a descodificação original por CPU.
- **Descoberta e conectividade inteligentes** (`network/Discovery.kt`, `network/HydraApiClient.kt`, `network/HydraWebSocket.kt`) - análise concorrente de sub-rede contra cada anfitrião candidato no /24 do próprio telemóvel (incluindo o IP de LAN do próprio telemóvel e o localhost, não apenas os outros anfitriões), sondando `GET /api/hydra-info` e identificando um servidor real apenas pela presença de `remoteApiVersion` - a mesma verificação que o caminho de IP manual usa, pelo que um servidor cujo dono o renomeou para longe da cadeia de produto por defeito continua a ser encontrado. Um ouvinte **NsdManager** (mDNS/Bonjour) corre ao lado - o servidor anuncia-se de facto como `_hydra._tcp` (`bonjour-service`), e a `MainActivity` pede de início a permissão de execução de localização/dispositivos próximos de que isto precisa (uma declaração no manifesto, por si só, nunca a concede na API 23+) - mas a análise de sub-rede continua a ser o caminho principal, já que a alcançabilidade por multicast no Wi-Fi é inerentemente menos fiável do que uma simples sonda HTTP. A aplicação ativa automaticamente o WiFi ao arrancar, analisa a rede local da fábrica e faz uma **ligação automática sem cliques** ao primeiro servidor HYDRA-UMC disponível.
- **Acesso com JWT** (`network/HydraApiClient.kt`, `ui/LoginScreen.kt`) - início de sessão com **JWT (JSON Web Tokens)**. Cada comando de controlo (Jog, Play e a paragem global do servidor) é validado pelo servidor com tokens assinados, enviado pelo endpoint atómico `POST /api/robot/:id/command` (ver Sincronização atómica de comandos abaixo) - funciona para o papel `admin` ou `operator`, ao contrário de uma escrita completa `POST /api/settings` (só administrador, do lado do servidor). Cada pedido leva também um cabeçalho `X-Hydra-Client: android` para que o separador Config > Remote Access do próprio servidor possa permitir/bloquear esta aplicação independentemente do SUITE/iOS. Integrado sem costuras com **autenticação biométrica** (impressão digital/rosto) para a renovação segura de tokens. Um WebSocket fechado com o código `1008` (token inválido/expirado) é tratado como «inicie sessão de novo», e não repetido num ciclo de religação (`network/HydraWebSocket.kt`).
- **Sincronização atómica de comandos** (o `sendAtomicCommand()` do `viewmodel/RobotViewModel.kt`) - cada escrita (play/pause/stop/jog/jogStep/válvula/bomba/velocidade/visão/ferramenta) envia um pequeno comando atómico de um só robô em vez da árvore de definições inteira - o servidor calcula que robôs combinados também são afetados, persiste em disco e difunde para todos os outros clientes ligados por si. O Enable/Disable propaga-se aos irmãos `combinedWith` de um robô tal como o Play/Pause/Stop, já que todos partilham o mesmo cálculo de robôs afetados.
- **Widget de STOP global** (`widget/GlobalStopWidget.kt`) - **widget de ecrã inicial** dedicado ao acesso rápido. Oferece um botão **STOP global** muito visível e de acesso imediato - a mesma chamada de cima: o toque traz a aplicação para primeiro plano e envia a paragem assim que existe ligação ao servidor, mesmo a partir de um arranque a frio completo (processo ainda não iniciado), e mostra STOP NÃO CONFIRMADO se não conseguir chegar ao servidor. A ação da notificação faz o mesmo. É a paragem por software do servidor: não é um E-STOP de hardware e não corta a alimentação de nada.
- **Resposta háptica** (`ui/ControlScreen.kt`) - sistema avançado de retorno sensorial. O STOP global responde a um toque com uma vibração dupla imediata; o botão Parar de cada robô mantém a sua **proteção de pressão longa** (um toque rápido dá apenas uma vibração curta + uma dica; só uma pressão mantida envia o comando). Assinaturas hápticas diferenciadas (impulsos de Sucesso, Erro e Emergência) dão confirmação física ao operador em ambientes ruidosos.
- **Canal de atualização na própria aplicação** (`update/GitHubReleaseUpdater.kt`, `update/ReleaseMetadataParser.kt`, `update/SemanticVersion.kt`) - consulta a Release estável mais recente do GitHub ao arrancar e em **Definições → Atualizações**; descarrega apenas o ativo exato `HYDRA-UMC-ANDROID-CONTROL-release.apk` de uma etiqueta que não seja rascunho nem pré-lançamento, nunca instala automaticamente - o próprio instalador de pacotes do Android pede o consentimento final. Contrato completo da release: [`docs/GITHUB_RELEASE_UPDATES.md`](docs/GITHUB_RELEASE_UPDATES.md).
- **Bluetooth LE (GATT)** (`network/HydraBleClient.kt`, `network/BleContract.kt`, `network/AndroidBleGatt.kt`) - Um cliente GATT real que segue um contrato documentado ([`docs/BLE_CONTRACT.md`](docs/BLE_CONTRACT.md)): procura filtrada pelo serviço HYDRA, ligação com negociação do MTU, verificação do serviço, notificações, mensagens divididas em tramas nos dois sentidos, uma escrita de cada vez, religação automática com esperas crescentes, permissões do Android 12+ (`BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT`) e uma mensagem clara na língua do utilizador para cada falha. **Nenhum dispositivo HYDRA-UMC implementa ainda o lado periférico:** o cliente só foi testado contra um GATT simulado, a parte Android nunca correu num telemóvel e os comandos do robô não são enviados por Bluetooth.
- **TLS opcional** (`network/TlsSettings.kt`, `network/HydraHttp.kt`, `ui/SecuritySettings.kt`) - **Definições → Segurança** escolhe Automático (predefinição, o comportamento de sempre), Exigir TLS ou Apenas sem cifra, e pode fixar a impressão digital (SHA-256) do certificado do servidor ou confiar numa AC própria. Uma sessão sem cifra para um equipamento fora da rede local mostra um aviso vermelho por cima de todos os ecrãs. A vista 3D incorporada ainda não segue estas regras. Testado com handshakes reais contra um servidor local; nunca contra um servidor HYDRA-UMC real com TLS.
- **Sem tráfego não cifrado fora da rede local** (`network/TlsSettings.kt`, `PlainRemoteGuard`) - a aplicação recusa HTTP/WS não cifrado para um servidor que não esteja na rede local, a menos que **Definições → Segurança → Permitir tráfego não cifrado fora da rede local** esteja ativado (desativado por predefinição). A vista 3D incorporada recebe o token de sessão no fragmento do endereço (`#token=`), nunca na consulta, pelo que nenhum registo de servidor ou proxy o guarda. As cópias de segurança automáticas dos dados da aplicação estão desativadas.
- **Companheiro Wear OS emparelhado e relé de voz** (`wear/WatchVoiceRelayService.kt`, `wear/WatchCompanionProtocol.kt`) - retransmite um turno de voz já reconhecido e iniciado pelo utilizador, vindo da aplicação [HYDRA-UMC-WATCH](https://github.com/JuanenRac/HYDRA-UMC-WATCH) emparelhada, através da própria sessão autenticada desta aplicação com o Server até ao `HYDRA-UMC-VOICE-UI`, devolvendo uma resposta tipada ao Watch; nem este relé nem o cartão de estado do sistema do relógio emitem alguma vez um comando de robô nem tocam diretamente no `HydraState` - uma resposta relacionada com movimento é explicitamente marcada `requiresConfirmation` e tem de passar por uma interface de controlo principal. Contrato completo: [`docs/WATCH_VOICE_RELAY.md`](docs/WATCH_VOICE_RELAY.md).
- **Assistente de voz na aplicação** (`ui/VoiceAssistantDialog.kt`) - um ícone de microfone na barra superior abre uma caixa de diálogo para escrever uma pergunta/ordem, ou usar o reconhecimento de fala no próprio dispositivo deste telemóvel (`RecognizerIntent`, o mesmo padrão real do botão de voz do próprio Watch). Envia a transcrição reconhecida pela mesma rota `POST /api/voice/turn` que o relé do Watch usa, mas como tráfego normal deste próprio telemóvel (`HydraApiClient.postVoiceTurn()`) em vez de um relé - para que o Config > Remote Access possa permitir/bloquear este botão independentemente do Watch emparelhado. Nunca envia áudio, e uma resposta nunca invoca diretamente um comando de robô.
- **Cadeia de ferramentas e qualidade do projeto** - AGP 9.3.1, Kotlin 2.2.10, Gradle 9.7.0, compileSdk 36, **JDK 21** (`compileOptions`, `gradle-daemon-jvm.properties` e os ficheiros de projeto de `.idea/`/`.vscode/` visam todos de facto esta versão, não apenas esta linha de documentação). Saída de compilação limpa sem avisos, variantes de produção R8 otimizadas e testes de capturas de ecrã **Roborazzi** avançados.
- **Texto JSON nulo tratado a sério** (`util/JsonText.kt`) - o `JSONObject.optString` do Android devolve as quatro letras `null` para um campo presente mas `null` em JSON, pelo que uma release sem notas mostrava `null`, um robô sem nome chamava-se `null` e um início de sessão com `"token": null` guardava o token `null`. O auxiliar `optStr` trata um `null` JSON como uma chave em falta e dá o valor de recurso; todos os 32 pontos de utilização o usam. *(implementado)*

**Estado: Wi-Fi, Bluetooth, biometria e notificações implementados.** A aplicação é uma consola industrial de operador; o Bluetooth ainda é experimental e nenhum ecrã substitui uma paragem de emergência física.

## 🔒 Segurança e proteção

| Ameaça | Defesa | Onde |
|---|---|---|
| Um token roubado do telemóvel | O token de sessão e o de renovação guardam-se com EncryptedSharedPreferences (AES-256-GCM por valor, chave mestra no Keystore do Android); a cópia de segurança automática dos dados da aplicação está desligada (`allowBackup=false`) | `network/AuthPrefs.kt`, `AndroidManifest.xml` |
| Tráfego sem cifra para um servidor fora da rede local | O `PlainRemoteGuard` recusa HTTP e WebSocket sem cifra para esse servidor, a menos que Definições > Segurança > Permitir tráfego sem cifra fora da rede local esteja ativo (desligado por predefinição); uma sessão sem cifra mostra um aviso vermelho sobre cada ecrã | `network/TlsSettings.kt`, `ui/SecuritySettings.kt` |
| Um servidor falso | TLS opcional com três modos (Automático, Exigir TLS, Só texto simples); com uma impressão digital SHA-256 fixada só se aceita um certificado com exatamente essa impressão, e pode confiar-se numa CA própria em vez disso | `network/TlsSettings.kt`, `network/HydraHttp.kt` |
| Uma atualização falsa | Só se aceita o ficheiro chamado `HYDRA-UMC-ANDROID-CONTROL-release.apk` da última versão estável que não seja rascunho; a aplicação nunca instala sozinha, o instalador do Android pede consentimento | `update/ReleaseMetadataParser.kt` |
| Um robô movido por duas pessoas, ou uma paragem que não chega | O robô é reservado antes de ser movido e o ecrã mostra quem o detém; a paragem global é enviada com tempos limite curtos, repetida uma vez, recorre a uma paragem por robô e mantém um estado persistente «PARAGEM NÃO CONFIRMADA» se não pôde ser entregue | `ui/GlobalStop.kt`, `network/AccessModels.kt`, `network/StopAllResult.kt` |

**O que nunca faz:** substituir uma paragem de emergência física. A PARAGEM é a paragem por software do servidor e não corta a alimentação a nada.

## 🔌 Interfaces

| Tópico ou rota | O que leva |
|---|---|
| `POST /api/login`, `/api/logout`, `/api/refresh` | iniciar e terminar sessão e renová-la |
| `GET /api/hydra-info` | reconhece um servidor real por `remoteApiVersion`; usado pela pesquisa da sub-rede e pelo endereço manual |
| `GET /api/settings`, `/api/ecosystem/status`, `/api/system/metrics`, `/api/safety-limits`, `/api/claims` | o estado dos robôs, da família, do módulo de cálculo, os limites de segurança e as reservas |
| `POST /api/robot/{id}/command`, `/claim`, `/release`, `/jog-arm` | um pequeno comando para um robô, a sua reserva e o armar de um movimento manual |
| `POST /api/stop-all` | a PARAGEM global: todos os robôs, mesmo os reservados por outra pessoa |
| `GET /api/audit`, `/api/camera/ticket`, `POST /api/voice/turn`, `GET /api/watch/system-status` | o registo de auditoria (administradores), um bilhete para o fluxo da câmara, um turno de voz e o cartão de estado do relógio |
| WebSocket `hydra.bearer` | alterações de estado em direto enviadas pelo servidor; o token vai no subprotocolo e a aplicação religa-se a cada 3 segundos |
| Bluetooth LE GATT | o contrato de `docs/BLE_CONTRACT.md`: mensagens com moldura, uma escrita de cada vez, permissões do Android 12; nenhum dispositivo HYDRA-UMC implementa ainda o outro lado |

## ⚙️ Configuração

A aplicação não tem ficheiro de configuração: tudo se define no ecrã e fica guardado no telemóvel. Os valores predefinidos e os limites vêm do código.

| Variável | Predefinição | O que define |
|---|---|---|
| Definições > Segurança > Modo TLS | Automático | Automático, Exigir TLS ou Só texto simples; impressão digital SHA-256 fixada ou CA própria opcionais |
| Definições > Segurança > Permitir tráfego sem cifra fora da rede local | desligado | deixa a aplicação falar HTTP e WebSocket sem cifra com um servidor que não está na rede local |
| Endereço e porta do servidor | `3000` | escreve-se no ecrã de início de sessão, ou é encontrado pela pesquisa da sub-rede /24 do próprio telemóvel (1,5 s por equipamento, 16 de cada vez) mais mDNS |
| Renovação da sessão | 20 s / 3 / 10 min | a aplicação renova a sessão sozinha no máximo 3 vezes em 10 minutos e com pelo menos 20 s de intervalo; passado isso pede de novo o início de sessão |
| Definições > Notificações | ligado | avisos quando um robô termina um trabalho ou ocorre um evento crítico (dois canais: missões e segurança) |

## 🚀 Início rápido

Requer o JDK 21 e o SDK do Android (o Android Studio traz ambos), e um telemóvel ou emulador com Android 7.0 (API 24) ou superior.

```bash
./build-android.sh           # builds the debug APK and installs it by adb (build-android.bat on Windows)
./gradlew assembleDebug      # only builds; the APK lands in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest  # runs the unit tests (gradlew.bat on Windows)
```

## 🧪 Testes

303 testes automáticos correm com `./gradlew testDebugUnitTest`. Não precisam de hardware nem de nada fora desta máquina.

A CI executa `HYDRA_UMC_CI=1 python tools/ci_validate.py`, que verifica o manifesto, os documentos, os oito README e as imagens.

### Testar contra um servidor em direto

1. Execute o back-end: `cd HYDRA-UMC-SERVER && npm run dev` (porta 3000) - esta é a API REST/WS real com que esta aplicação fala (ver Projetos relacionados abaixo); o `npm run dev` do próprio `HYDRA-UMC-STUDIO` apenas arranca o seu servidor de desenvolvimento Vite de frontend (porta 5173) contra esse mesmo back-end, não é o servidor da API em si.
2. Ligue o seu dispositivo Android à mesma rede Wi-Fi.
3. Use o **Seletor global de servidor** ou introduza o IP manualmente no cabeçalho.
4. **Biometria:** ative «Início de sessão biométrico» no seu Perfil do utilizador para saltar o ecrã de palavra-passe no próximo arranque.

## 🏗️ Compilação e publicação

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

O APK fica em `app/build/outputs/apk/debug/app-debug.apk`. Instale-o com `adb install -r -d app/build/outputs/apk/debug/app-debug.apk`, ou transfira-o para o dispositivo manualmente. Troque `assembleDebug` por `assembleRelease` para uma compilação de lançamento - assina com a chave de lançamento privada quando existe `keystore.properties` (ou as variáveis de ambiente de assinatura de lançamento) e, caso contrário, recorre à chave de depuração, que instala bem para testes mas nunca deve ser distribuída.

### Versionamento

Este repositório segue uma política de todo o ecossistema: compilar nunca altera a versão. O `app/version.properties` guarda os valores atuais de `versionMajor`/`versionMinor`/`versionPatch`/`versionCode`; só o comando `bump` explícito os sobe (`python tools/hydra_project_tool.py bump`: `bump_manifest_version.py` para a versão nativa, o manifesto `hydra-umc.project.json` e o `CHANGELOG.md`, e `bump_version_code.py` para o `versionCode` do Android, separado e sempre monótono). O `app/build.gradle.kts` apenas *lê* o `version.properties` e nunca o escreve.

- **`versionCode`:** um contador monótono simples, +1 em cada bump, sem transporte - o Android exige que aumente estritamente em cada compilação que alguma vez seja distribuída.

Um simples `./gradlew assembleDebug` (o caminho de «Compilação manual» acima, sem que nenhum dos scripts de subida tenha corrido antes) reutiliza o que o `version.properties` tinha por último - não sobe nada por si. A versão em execução é visível em direto na caixa **Acerca de** (`BuildConfig.VERSION_NAME`, que lê o `versionName` que o `version.properties` tiver por último). Consulte o [CHANGELOG.md](CHANGELOG.md) para o histórico de versões.

## 🩺 Resolução de problemas

| Sintoma | Causa | Solução |
|---|---|---|
| Sem notificações | Permissão negada | Conceda a permissão «Notificações» nas definições do Android para esta aplicação |
| Sem biometria | Hardware não configurado | Garanta que tem uma impressão digital/rosto registado na Segurança do sistema Android |
| O robô não se move | Sessão expirada, robô reservado por outra conta ou trajetória em curso | Leia a mensagem na barra de estado; o servidor reproduz as trajetórias por si e não é preciso nenhum separador do navegador |
| Bluetooth desativado | Chip físico desligado | Use o botão 3D «ENABLE SYSTEM BT» na aplicação |

## 📂 Estrutura do repositório

<p align="center">
  <img src="images/repository-structure.svg" alt="Repository structure of HYDRA-UMC-ANDROID-CONTROL" width="100%">
</p>

<details>
<summary>Versão em texto da árvore</summary>

```text
HYDRA-UMC-ANDROID-CONTROL/
├── app/src/main/java/com/hydraumc/control/
│   ├── MainActivity.kt                                          Ponto de entrada - splash, controlo de acesso ao ecrã de login/principal, tratamento do E-STOP global seguro a frio
│   ├── MainScreen.kt                                            Esqueleto de navegação inferior, barra superior (seletor de servidor, perfil, telemetria, definições)
│   ├── kinematics/                                              cinemática direta e inversa do braço PAROL6
│   ├── model/                                                   o estado do servidor tal como a aplicação o lê
│   ├── network/                                                 cliente REST, WebSocket e descoberta
│   ├── ui/                                                      ecrãs e widgets
│   ├── update/                                                  o canal de atualização da aplicação
│   ├── util/                                                    regras puras da aplicação
│   ├── viewmodel/                                               o estado partilhado que cada ecrã escuta
│   ├── wear/                                                    reencaminhamento de voz e estado
│   └── widget/                                                  o widget de PARAGEM do ecrã inicial
├── docs/                                                        documentos do projeto
├── images/                                                      Faixa do README + ativos de origem do ecrã de abertura
├── tools/                                                       ferramentas do projeto e verificações da CI
├── .github/                                                     fluxo de CI e modelos de problemas
├── gradle/                                                      ficheiros do wrapper do Gradle
├── bump_manifest_version.py                                     Sincroniza a versão do hydra-umc.project.json com a nativa (--sync)
├── bump_version_code.py                                         Subida do contador versionCode do Android (executada só pelo comando `bump` explícito)
├── gradlew / gradlew.bat                                        Wrapper do Gradle
├── build.gradle.kts / settings.gradle.kts / gradle.properties   Configuração Gradle do projeto raiz
├── keystore.properties.example                                  Modelo da configuração privada de assinatura de lançamento
├── .env.example                                                 Exemplo de variáveis de ambiente
├── LICENSE                                                      GPL-3.0-or-later
├── CHANGELOG.md                                                 o que mudou em cada versão
├── build-test.sh                                                verificação de compilação sem alterações
├── hydra-umc.project.json                                       manifesto: versão, maturidade, família
├── test.sh                                                      verificação de compilação sem alterações
├── prepare-github-release.sh / prepare-github-release.bat       compila o APK de lançamento assinado em privado; não muda nenhuma versão
├── publish-github-release.sh / publish-github-release.ps1       publica esse APK como a versão do GitHub da versão atual
├── app/version.properties                                       versão e versionCode
└── app/src/test/                                                testes unitários na JVM
```

</details>

## 🗺️ Roteiro e limites conhecidos

O Bluetooth LE continua experimental até que um dispositivo implemente o lado periférico de `docs/BLE_CONTRACT.md`. O TLS e a impressão digital fixada foram testados com handshakes reais contra um servidor de testes local, não contra um servidor HYDRA-UMC com TLS. A vista 3D incorporada ainda não segue as definições de TLS. O armazenamento no Keystore, o pedido biométrico e o transporte Bluetooth não foram verificados de novo em hardware desde que foram escritos.

## 🌍 Idiomas

Este README e os documentos do repositório existem em oito idiomas: inglês, espanhol, alemão, francês, italiano, japonês, chinês e português (Portugal). São escritos em conjunto e a CI verifica que os oito ficheiros têm as mesmas secções e as mesmas ligações.

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
- **[URTC-GATEWAY](https://github.com/JuanenRac/URTC-GATEWAY)** — gateway à frente do barramento CAN do URTC: lista de identificadores permitidos, limite de taxa e chave de sessão; software testado num computador, ainda não num barramento real.

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

## 📚 Documentação

Onde ler mais:

* [docs/SOURCE_MAP.md](docs/SOURCE_MAP.md) - cada ficheiro fonte da aplicação com uma linha sobre o que faz
* [Base de segurança do ecossistema](https://github.com/JuanenRac/HYDRA-UMC-DOCS/blob/main/docs/SECURITY_BASELINE.md)
* [Modelo de ameaças do ecossistema](https://github.com/JuanenRac/HYDRA-UMC-DOCS/blob/main/docs/THREAT_MODEL.md)
* [Registo de alterações deste repositório](CHANGELOG.md)
* [Licença deste repositório](LICENSE.md)

## 🤝 Contribuir e comunidade

* Antes de um pull request execute `HYDRA_UMC_CI=1 py -3 tools/ci_validate.py` (ou `HYDRA_UMC_CI=1 python3 tools/ci_validate.py`): tem de imprimir `CI_VALIDATION=PASS`. Não execute `build.sh` nem `build.bat` para esta verificação; compilar não é verificar.
* Os oito README são escritos a partir de uma única fonte em HYDRA-UMC-DOCS; não os edite à mão. Altere a fonte e escreva-os de novo com `tools/make_readmes.py`.
* Não afirme num README o que não foi executado: diga «simulado» ou «não testado em hardware» quando for a verdade.
* Consulte [CONTRIBUTING.md](CONTRIBUTING.md), o [código de conduta](CODE_OF_CONDUCT.md), [SECURITY.md](SECURITY.md) e [SUPPORT.md](SUPPORT.md).

## 👤 AUTOR

**JuanenRac (Electro Hobby 3D)** · electrohobby3d@gmail.com

## 📜 LICENÇA

GPL-3.0-or-later - [LICENSE](LICENSE).
