<!-- =============================================================================
HYDRA-UMC-ANDROID-CONTROL - Android operator console for a HYDRA-UMC server: control, cameras, 3D view and a global STOP
Copyright (C) JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
GPL-3.0-or-later - see LICENSE
============================================================================= -->

<p align="center">
  <img src="images/HYDRA_UMC_BANNER.svg" alt="HYDRA-UMC-ANDROID-CONTROL banner" width="100%">
</p>

# 📱 HYDRA-UMC-ANDROID-CONTROL

<p align="center"><a href="README.md">🇺🇸 English</a> | <a href="README_spa.md">🇪🇸 Español</a> | <a href="README_fra.md">🇫🇷 Français</a> | <a href="README_ita.md">🇮🇹 Italiano</a> | <a href="README_deu.md">🇩🇪 Deutsch</a> | 🇨🇳 <b>简体中文</b> | <a href="README_jpn.md">🇯🇵 日本語</a> | <a href="README_por.md">🇵🇹 Português</a></p>

### 把 HYDRA-UMC 机器人装进口袋：找到服务器，移动并监看机器人，用一个红色按钮全部停止。

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

一款原生 Android 应用（Kotlin + Jetpack Compose），通过 Wi-Fi 或蓝牙控制 [HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC) 平台上的机器人，使用与 [HYDRA-UMC SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE) 完全相同的 [`REMOTE_API.md`](https://github.com/JuanenRac/HYDRA-UMC-SERVER/blob/main/docs/REMOTE_API.md) 契约——针对运行中的 [HYDRA-UMC-SERVER](https://github.com/JuanenRac/HYDRA-UMC-SERVER) 后端（与 [HYDRA-UMC STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO) 自身网页仪表盘所通信的同一个后端）进行发现、完整状态读写以及实时 WebSocket 同步。是 [HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL) 的直接 Android 版本对应物。完整设计参见 [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)。

> **诚实检查——今天真正可运行的部分：** 核心业务逻辑——Parol6 运动学（`kinematics/Parol6Kinematics.kt`）、settings 树状态契约（`model/HydraState.kt`）、原子命令下发与语音中继（`viewmodel/RobotViewModel.kt`）、WebSocket 重连处理（`network/HydraWebSocket.kt`）、MJPEG 帧解析（`ui/MjpegPlayer.kt`）、应用内更新通道（`update/GitHubReleaseUpdater.kt`、`ReleaseMetadataParser.kt`、`SemanticVersion.kt`），以及 Wear 语音中继请求管理（`wear/BoundedRequestScope.kt`、`WatchCompanionProtocol.kt`）都是真实的，并由 303 个通过的 JVM/Robolectric 单元测试覆盖（`./gradlew testDebugUnitTest`）。这些测试在结构上无法触及——而这个仅有 Windows 的开发环境也没有实体 Android 设备可以验证——的是 `network/AuthPrefs.kt` 中真正基于 Keystore 的 `EncryptedSharedPreferences`、`androidx.biometric` 的指纹/面部提示、`network/HydraBleClient.kt` 的蓝牙 GATT 传输，以及 `MjpegStreamParser` 中 API 28+ 的硬件 `ImageDecoder` 路径：这些都是真实、已编译的代码，但自编写以来尚未在真实硬件上重新验证过。早期版本的基本启动/登录*确实*在真实设备上得到过现场确认（见 `CHANGELOG.md` 的 `v0.3.2`/`v0.3.3` 条目），但那早于蓝牙、生物识别和硬件解码路径。具体已交付的内容见 `CHANGELOG.md`，其中每条记录也附有关于哪些部分仍未经硬件验证的说明。

> **安全与安防现状:**本生态系统不是经认证的安全系统。目前尚无硬件 E-STOP 通路,没有由固件强制执行的限位或联锁,也没有经过验证的运动指令;固件映像使用 CRC-32 和 Ed25519 签名校验,发布构建会拒绝已公开的开发密钥(中继之后的工具头和扩展板仍运行 URTC 的引导程序,它们使用共享密钥的 HMAC-SHA256 而不是签名);TLS 与双向 TLS 均为可选项,默认关闭;自制电路板尚未组装或测试。在依赖此处任何说法来保障人身安全之前,请先阅读[安全基线](https://github.com/JuanenRac/HYDRA-UMC-DOCS/blob/main/docs/SECURITY_BASELINE.md)和[威胁模型](https://github.com/JuanenRac/HYDRA-UMC-DOCS/blob/main/docs/THREAT_MODEL.md)。

---

## 📋 速览

| | |
|---|---|
| 版本 | `0.7.4` |
| 成熟度 | 可用 |
| 测试 | 303 (`./gradlew testDebugUnitTest`) |
| 技术栈 | Kotlin / Jetpack / biometric auth / WebSocket client |
| 目标 | Android 手机或平板 |
| 真实硬件 | 尚未：此版本没有在真实硬件上运行的记录 |

**跳转：** [概述](#-概述) · [架构](#-架构) · [功能](#-功能) · [安全与防护](#-安全与防护) · [接口](#-接口) · [配置](#-配置) · [快速开始](#-快速开始) · [测试](#-测试) · [故障排除](#-故障排除) · [仓库结构](#-仓库结构) · [相关项目](#-相关项目)

## 🚦 项目状态

每一行是本仓库的一个部分，灯号表示它今天做到了哪一步。内容依据仓库能够展示的证据（测试、构建、运行），而不是计划。

🟢 已完成并验证 · 🟡 可用，有已知限制 · 🟠 部分完成或尚未验证 · 🔴 未完成或不可用

| 领域 | 状态 | 在这里的含义 |
|---|---|---|
| 软件 | 🟡 | 主要用途可用；上面的诚实性检查列出了仍然缺少的内容 |
| 测试 | 🟢 | 303 项自动测试，每次变更都会运行 |
| 真实硬件 | 🔴 | 尚未：此版本没有在真实硬件上运行的记录 |
| 文档 | 🟢 | 八种语言的 README，每次变更都会与清单核对 |

## 🎯 概述

应用的界面：**登录**（服务器地址、密码、指纹或面部）、**仪表盘**（机器人的 3D 轮播，附模块矩阵和计算模块的健康状态）、**控制**（摇杆、播放、暂停、停止、机器人预约）、**摄像头**（带画中画的实时 MJPEG）、**3D 视图**（STUDIO 的真实场景）、**遥测**（终端风格的日志）和**设置**（连接、安全、通知、更新）。红色的**全部停止**按钮位于每个界面的顶部栏，也在主屏幕小组件上。

## 🧩 在 HYDRA-UMC 中的角色

**HYDRA-UMC-ANDROID-CONTROL** 是 HYDRA-UMC 的原生 Android 操作员控制台：它在本地网络中找到 HYDRA-UMC-SERVER，登录后显示机器人、移动它们、查看摄像头和 3D 视图，并可一键全部停止。它使用与 SUITE 和 iOS 应用相同的远程契约，并保留最后状态的副本以应对没有 Wi-Fi 的情况。它不是安全设备。

## 🏛️ 架构

<p align="center">
  <img src="images/architecture.svg" alt="HYDRA-UMC-ANDROID-CONTROL architecture" width="100%">
</p>

## ✨ 功能

- **访问控制与生物识别**（`ui/LoginScreen.kt`、`util/BiometricHelper.kt`）—— 专业级登录系统，支持**指纹和面部解锁**（`androidx.biometric`），并在同一界面上直接提供 IP/端口字段,无需先单独跳转到设置页面才能指定服务器。包含“记住我”功能、安全的**退出登录**机制,并完全支持**8 种语言**本地化。缓存的用户名/密码/令牌（`network/AuthPrefs.kt`）存放在由 Keystore 支持的**加密 SharedPreferences**（AES256-GCM）中,而非明文——开发服务器(未设置 `NODE_ENV`)会在首次启动时预置默认的 `admin`/`admin` 账户;生产服务器(`NODE_ENV=production`)则要求改用 `HYDRA_UMC_BOOTSTRAP_ADMIN_USERNAME`/`HYDRA_UMC_BOOTSTRAP_ADMIN_PASSWORD`,绝不会静默回退到 `admin`/`admin` —— 完整的生产环境配置请参见 HYDRA-UMC-SERVER 自身的 README。无论哪种情况,额外的低权限**操作员**账户都可以在服务端从 Config > Users 创建。
- **离线模式与状态缓存**（`network/StateCache.kt`）—— 使用 **DataStore** 集成的持久化引擎。本应用会自动缓存最后已知的系统状态,即使没有活动的 Wi-Fi 连接,也能实现即时的仪表盘查看和配置审查。
- **任务通知与警报**（`util/NotificationHelper.kt`）—— 警报系统。当机器人完成一个作业序列或发生关键硬件事件时,发送高优先级推送通知,确保即使应用在后台运行,操作员也能收到通知。
- **工业遥测终端**（`ui/TelemetryScreen.kt`）—— 一个采用终端风格界面的专用实时日志查看器。跟踪系统事件、REST/WebSocket 同步,并提供颜色编码的诊断信息（成功为矩阵绿色,错误为工业红色）。
- **高级仪表盘**（`ui/DashboardScreen.kt`）—— 具有透视滑动效果的高保真 **3D 水平轮播**。显示丰富的机器人元数据：**制造商**（Source Robotics、Annin、Universal Robots、AgileX 等）、**机器人角色**（CNC、激光、PnP）,以及一个**工业模块矩阵**,显示 CAM、XY、ATC、PNP、CNC、LSR、BED、VAC 和 RCK 模块的实时状态。
- **系统健康监视器**（`ui/DashboardScreen.kt`）—— 针对已连接的 Compute Module 5 的实时指标,包括**主机名**、**格式化的运行时间**（例如“2天4小时15分钟”）,以及控制器和机器人的活动计数。
- **增强型手动控制**（`ui/ControlScreen.kt`）—— 具有专业的纵向布局,配备**放大 50% 的摇杆按钮**以实现最大精度。
- **回放面板**（`ui/ControlScreen.kt`、`ui/PlaybackConsole.kt`）—— 固定的底部控制栏,容纳红色 **STOP** 按钮（下文的全局 STOP）以及**启动**、**暂停**、**停止**（仅所选机器人）按钮。这些控件始终可见,并提供**触觉反馈**作为物理感官确认。
- **全局 STOP**（`ui/GlobalStop.kt`、`network/HydraApiClient.kt`、`viewmodel/RobotViewModel.kt`）—— 每个界面的顶部栏、控制界面的控制台以及 3D 视图（竖屏和全屏）中都有一个红色 **STOP** 按钮（说明文字“全部停止”,已翻译为 8 种语言）。轻点一下,无需长按,无需确认：立即发送 `POST /api/stop-all` 并停止所有机器人,即使被其他账户预约。它会显示结果（哪些机器人已停止、哪些没有、哪些被他人预约,以及服务器无法保存停止状态时的警告）,无人应答时重试一次,服务器限制调用（429）时逐台停止机器人,并保持常驻的 **STOP 未确认** 横幅,直到之后的 STOP 成功。这是服务器的软件停止，不是硬件 E-STOP，也不会切断任何设备的电源。
- **预留与安全限值**（`ui/AccessPanel.kt`、`network/AccessModels.kt`、`network/HydraApiClient.kt`、`viewmodel/RobotViewModel.kt`）- Control 屏幕显示谁持有所选机器人（无人、本手机、同一账户的另一台设备，或其他账户），提供**预留**和**释放**，并列出服务器上的所有预留（`GET /api/claims`）。手机为每次安装保存一个控制器 ID，并在每次预留、释放、点动解锁和指令中发送（请求体和 `X-Controller-Id` 头），因此预留属于账户和这台手机，而不是整个账户。服务器的拒绝会被区分：被其他账户预留、被你的账户在另一台设备或窗口预留、需要控制器 ID、点动未解锁。机器人型号的限值从 `GET /api/safety-limits` 读取：超过限值的点动步长不会发送，并显示服务器的警告。当管理员要求时，必须先按**解锁点动**（带倒计时），摇杆才会移动机器人；解锁会自动结束，也会在按下 STOP 或释放时结束。预留是可选的：没人预留的机器人照常控制。 仅**管理员**会在他人持有的机器人上看到 **接管控制** 按钮；点击后先要求确认，然后以 `force: true` 预约该机器人（服务器只接受管理员的此请求，并会再次检查角色）。 写入是有条件的:每次写入都带有此应用最后看到的机器人修订号(`If-Match`,`network/ContractV2.kt`),如果期间有其他人修改了机器人,服务器返回 412,不会应用任何内容,机器人会被重新读取并给出提示(标准模式检查预约、工作台和一次性命令;严格模式检查除 STOP 外的全部命令;关闭则不检查;该选项位于预约列表中;STOP 永远不检查)。XY 工作台的点动是一条原子的 `table` 命令(较旧的服务器会收到它能理解的点动),服务器通过 WebSocket 推送预约,因此在推送到达期间 Control 屏幕不再轮询,管理员可以读取审计记录(`ui/AuditDialog.kt`),其 SHA-256 链由服务器和手机各校验一次,并可将各行以 `.jsonl` 分享。 PLAY 会先确认(机器人、已记录的点、速度以及预留者),除非在预留部分把它关闭。
- **3D 视图**（`ui/ThreeDScreen.kt`）—— 通过 WebView 嵌入 HYDRA-UMC STUDIO 自身的实时 3D 视口（`?hideUI=true&robotId=#token=`）,而非原生渲染器。这种 WebView 方式免费获得了真实的、当前正在使用的 STUDIO 3D 场景（每一个真实的机器人网格/运动学）,而无需原生重新实现——代价是 WebView 渲染开销,并非电池最优,但今天功能上是完整的。
- **实时八路视觉**（`ui/CameraScreen.kt`、`ui/MjpegPlayer.kt`）—— **原生 MJPEG 流播放器**。具备后台解析器和基于 Canvas 的渲染器,实现低延迟视频,当机器人的视觉系统关闭时会显示一个明确的“摄像头已禁用”状态（而非静默显示空白画面）,并配有一个可直接从服务器打开/关闭机器人摄像头的开关。在手动控制界面中支持自动**画中画（PIP）**叠加,通过服务器的摄像头配置映射到特定的机器人。在 API 28+ 上,每一帧都通过平台自身硬件加速的 `ImageDecoder` 路径解码（一个驻留在 GPU 中的位图,省去了 CPU 端的解码/上传）,并具备自动、自愈式回退到原有 CPU 解码的能力。
- **智能发现与连接**（`network/Discovery.kt`、`network/HydraApiClient.kt`、`network/HydraWebSocket.kt`）—— 针对手机自身 /24 网段上的每一个候选主机（包括手机自身的局域网 IP 和 localhost,而不仅仅是其他主机）进行并发子网扫描,探测 `GET /api/hydra-info`,并仅通过 `remoteApiVersion` 字段的存在来识别真实服务器——与手动输入 IP 路径所使用的检测方式相同,因此即使服务器所有者将其重命名为默认产品字符串以外的名称,也依然能被找到。一个 **NsdManager**（mDNS/Bonjour）监听器与其并行运行——服务器确实会以 `_hydra._tcp`（`bonjour-service`）的形式广播自身,`MainActivity` 会预先请求此功能所需的运行时位置/附近设备权限（仅在清单中声明,在 API 23+ 上从不会自动授予）——但由于 Wi-Fi 上的组播可达性本身不如纯粹的 HTTP 探测可靠,子网扫描仍然是主要路径。本应用会在启动时自动激活 Wi-Fi,扫描本地工厂网络,并对第一个可用的 HYDRA-UMC 服务器执行**零点击自动连接**。
- **JWT 访问**（`network/HydraApiClient.kt`、`ui/LoginScreen.kt`）—— 使用 **JWT（JSON Web Token）** 登录。每一条控制指令（点动、播放、服务器的全局停止）都由服务器使用签名令牌进行验证,通过原子化的 `POST /api/robot/:id/command` 端点发送（见下文“原子化指令同步”）——`admin` 或 `operator` 角色均可使用,不同于完整的 `POST /api/settings` 写入（仅限服务端 admin）。每个请求还携带一个 `X-Hydra-Client: android` 请求头,以便服务器自身的 Config > Remote Access 选项卡可以独立于 SUITE/iOS 来允许/阻止本应用。与**生物识别认证**（指纹/面部）无缝集成,用于安全的令牌续期。以代码 `1008`（无效/过期令牌）关闭的 WebSocket 会被视为“需要重新登录”,而不会进入重连循环重试（`network/HydraWebSocket.kt`）。
- **原子化指令同步**（`viewmodel/RobotViewModel.kt` 自身的 `sendAtomicCommand()`）—— 每一次写入（播放/暂停/停止/点动/点动步长/阀门/泵/速度/视觉/工具）都发送一个小型的、单机器人的原子指令,而非整棵设置树——服务器自行计算哪些合并机器人也会受到影响,持久化到磁盘,并向所有其他已连接客户端广播。启用/禁用会以与播放/暂停/停止相同的方式传播到机器人自身的 `combinedWith` 同伴,因为它们都共享相同的受影响机器人计算逻辑。
- **全局 STOP 小组件**（`widget/GlobalStopWidget.kt`）—— 专用的**主屏幕小组件**,用于快速访问。提供一个高可见度、可立即访问的**全局 STOP** 按钮——与上面相同的调用：轻点后应用回到前台,服务器连接一建立就发送停止,即使是完全冷启动（进程尚未运行）也是如此；如果无法连接服务器,则显示 STOP 未确认。通知中的操作作用相同。这是服务器的软件停止，不是硬件 E-STOP，也不会切断任何设备的电源。
- **触觉反馈**（`ui/ControlScreen.kt`）—— 高级感官反馈系统。全局 STOP 在轻点时立即以两次短振动回应；每个机器人自己的停止按钮保留**长按保护**（快速轻点只有一次短振动和提示；只有真正长按才会发送命令）。差异化的触觉特征（成功、错误和紧急脉冲）在嘈杂环境中为操作员提供物理确认。
- **应用内更新渠道**（`update/GitHubReleaseUpdater.kt`、`update/ReleaseMetadataParser.kt`、`update/SemanticVersion.kt`）—— 启动时以及从「设置 → 更新」检查最新的稳定版 GitHub Release;仅下载来自非草稿、非预发布标签的确切 `HYDRA-UMC-ANDROID-CONTROL-release.apk` 资源,从不自动安装 —— 最终同意由 Android 自身的软件包安装程序请求。完整发布契约:[`docs/GITHUB_RELEASE_UPDATES.md`](docs/GITHUB_RELEASE_UPDATES.md)。
- **蓝牙 LE（GATT）**（`network/HydraBleClient.kt`、`network/BleContract.kt`、`network/AndroidBleGatt.kt`）- 遵循有文档约定（[`docs/BLE_CONTRACT.md`](docs/BLE_CONTRACT.md)）的真实 GATT 客户端：按 HYDRA 服务过滤的扫描、协商 MTU 的连接、服务检查、通知、双向分帧消息、一次只写一条、等待时间递增的自动重连、Android 12+ 权限（`BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT`），以及每种失败都用用户语言给出的清晰提示。**目前还没有任何 HYDRA-UMC 设备实现外设一侧：** 客户端只针对模拟的 GATT 做过测试，Android 部分从未在手机上运行过，机器人命令也不通过蓝牙发送。
- **可选 TLS**（`network/TlsSettings.kt`、`network/HydraHttp.kt`、`ui/SecuritySettings.kt`）- **设置 → 安全** 可选择自动（默认，即一直以来的行为）、强制使用 TLS 或仅明文，并可固定服务器证书指纹（SHA-256）或信任自定义 CA。对本地网络之外主机的未加密会话，会在所有屏幕上方显示红色警告。内嵌的 3D 视图暂不遵循这些规则。已用真实握手对本地服务器测试；从未对启用 TLS 的真实 HYDRA-UMC 服务器测试。
- **本地网络之外不使用未加密通信** (`network/TlsSettings.kt`, `PlainRemoteGuard`) - 除非开启 **设置 → 安全 → 允许在本地网络之外使用未加密通信**(默认关闭),应用会拒绝向本地网络之外的服务器发送未加密的 HTTP/WS。内嵌的 3D 视图通过地址片段(`#token=`)而不是查询参数接收会话令牌,因此任何服务器或代理日志都不会记录它。应用数据的自动备份已关闭。
- **配对的 Wear OS 伴侣应用与语音中继**（`wear/WatchVoiceRelayService.kt`、`wear/WatchCompanionProtocol.kt`）—— 将来自配对的 [HYDRA-UMC-WATCH](https://github.com/JuanenRac/HYDRA-UMC-WATCH) 应用、已识别且由用户发起的语音回合,通过本应用自身经过认证的 Server 会话中继到 `HYDRA-UMC-VOICE-UI`,并向手表返回类型化的回复;无论是此中继还是手表的系统状态卡片,都从不发出机器人指令或直接操作 `HydraState` —— 与运动相关的回复会被明确标记为 `requiresConfirmation`,必须通过主控制 UI 进行确认。完整契约:[`docs/WATCH_VOICE_RELAY.md`](docs/WATCH_VOICE_RELAY.md)。
- **应用内语音助手**(`ui/VoiceAssistantDialog.kt`)—— 顶部栏中的麦克风图标会打开一个对话框,可输入问题/指令,或使用本手机自带的语音识别(`RecognizerIntent`,与手表语音按钮已使用的真实模式相同)。识别出的文字会通过与手表中继相同的 `POST /api/voice/turn` 路由发送,但作为本手机自身的普通流量(`HydraApiClient.postVoiceTurn()`)而非中继 —— 因此“配置 > 远程访问”可以独立于配对的手表来允许/阻止此按钮。绝不上传音频,回复也绝不会直接发出机器人指令。
- **工具链与项目质量** —— AGP 9.3.1、Kotlin 2.2.10、Gradle 9.7.0、compileSdk 36、**JDK 21**（`compileOptions`、`gradle-daemon-jvm.properties`,以及 `.idea/`/`.vscode/` 项目文件都实际以此为目标,而不仅仅是这行文档）。构建输出干净,零警告,经过优化的 R8 生产变体,以及高级的 **Roborazzi** 截图测试。

**状态：Wi-Fi、蓝牙、生物识别和通知均已实现。** 本应用是一款工业操作员控制台；蓝牙仍处于实验阶段，任何屏幕都不能取代物理急停。

## 🔒 安全与防护

| 威胁 | 防御 | 位置 |
|---|---|---|
| 从手机窃取的令牌 | 登录令牌和刷新令牌使用 EncryptedSharedPreferences 保存（每个值 AES-256-GCM，主密钥在 Android Keystore 中）；应用数据的自动备份已关闭（`allowBackup=false`） | `network/AuthPrefs.kt`, `AndroidManifest.xml` |
| 向本地网络之外的服务器发送明文流量 | `PlainRemoteGuard` 会拒绝向此类服务器发送未加密的 HTTP 和 WebSocket，除非“设置 > 安全 > 允许在本地网络之外使用明文流量”已开启（默认关闭）；未加密的会话会在每个界面上方显示红色警告 | `network/TlsSettings.kt`, `ui/SecuritySettings.kt` |
| 伪造的服务器 | 可选的 TLS，有三种模式（自动、要求 TLS、仅明文）；固定 SHA-256 指纹后，只接受指纹完全相同的证书，也可以改为信任自定义 CA | `network/TlsSettings.kt`, `network/HydraHttp.kt` |
| 伪造的更新 | 只接受最新稳定、非草稿发布中名为 `HYDRA-UMC-ANDROID-CONTROL-release.apk` 的资源；应用从不自行安装，由 Android 自带的安装程序请求同意 | `update/ReleaseMetadataParser.kt` |
| 被两个人同时移动的机器人，或没有送达的停止 | 机器人在点动前先预约，屏幕会显示谁持有它；全局停止以较短的超时发送，重试一次，回退为逐个机器人停止，若未能送达则保持“停止未确认”状态 | `ui/GlobalStop.kt`, `network/AccessModels.kt`, `network/StopAllResult.kt` |

**它绝不做的事：** 替代物理紧急停止。停止是服务器的软件停止，不会切断任何设备的电源。

## 🔌 接口

| 主题或路由 | 内容 |
|---|---|
| `POST /api/login`, `/api/logout`, `/api/refresh` | 登录、登出并续期会话 |
| `GET /api/hydra-info` | 通过 `remoteApiVersion` 识别真正的服务器；子网扫描和手动地址都使用它 |
| `GET /api/settings`, `/api/ecosystem/status`, `/api/system/metrics`, `/api/safety-limits`, `/api/claims` | 机器人、系列、计算模块的状态，安全限制和预约 |
| `POST /api/robot/{id}/command`, `/claim`, `/release`, `/jog-arm` | 针对单个机器人的小命令、它的预约以及点动的武装 |
| `POST /api/stop-all` | 全局停止：所有机器人，包括被他人预约的 |
| `GET /api/audit`, `/api/camera/ticket`, `POST /api/voice/turn`, `GET /api/watch/system-status` | 审计记录（管理员）、摄像头流的票据、语音回合和手表的状态卡 |
| WebSocket `hydra.bearer` | 服务器推送的实时状态变化；令牌放在子协议中，应用每 3 秒重连一次 |
| Bluetooth LE GATT | `docs/BLE_CONTRACT.md` 的契约：带帧的消息、一次一个写入、Android 12 权限；目前还没有 HYDRA-UMC 设备实现另一端 |

## ⚙️ 配置

应用没有配置文件：一切都在屏幕上设置并保存在手机上。默认值和限制来自代码。

| 变量 | 默认值 | 设置内容 |
|---|---|---|
| 设置 > 安全 > TLS 模式 | 自动 | 自动、要求 TLS 或仅明文；可选固定 SHA-256 指纹或自定义 CA |
| 设置 > 安全 > 允许在本地网络之外使用明文流量 | 关闭 | 允许应用与本地网络之外的服务器使用明文 HTTP 和 WebSocket 通信 |
| 服务器地址和端口 | `3000` | 在登录界面输入，或通过扫描手机自身的 /24 子网（每台主机 1.5 秒，同时 16 台）加 mDNS 找到 |
| 会话续期 | 20 s / 3 / 10 min | 应用在 10 分钟内最多自行续期 3 次，间隔至少 20 秒；超过后会再次要求登录 |
| 设置 > 通知 | 开启 | 机器人完成任务或发生关键事件时提醒（两个通道：任务和安全） |

## 🚀 快速开始

需要 JDK 21 和 Android SDK（Android Studio 两者都带），以及运行 Android 7.0（API 24）或更高版本的手机或模拟器。

```bash
./build-android.sh           # builds the debug APK and installs it by adb (build-android.bat on Windows)
./gradlew assembleDebug      # only builds; the APK lands in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest  # runs the unit tests (gradlew.bat on Windows)
```

## 🧪 测试

运行 `./gradlew testDebugUnitTest` 会执行 303 个自动测试。 不需要硬件，也不需要这台机器以外的任何东西。

CI 运行 `HYDRA_UMC_CI=1 python tools/ci_validate.py`，检查清单、文档、八个 README 和图片。

### 针对真实服务器进行测试

1. 运行后端：`cd HYDRA-UMC-SERVER && npm run dev`（端口 3000）——这才是本应用实际通信的真正 REST/WS API（见下方“相关项目”）;`HYDRA-UMC-STUDIO` 自身的 `npm run dev` 只会针对同一个后端启动其 Vite 前端开发服务器（端口 5173）,它本身并不是那个 API 服务器。
2. 将你的 Android 设备连接到同一个 Wi-Fi。
3. 使用**全局服务器选择器**,或在顶部手动输入 IP。
4. **生物识别：** 在用户资料中启用“生物识别登录”,下次启动时即可跳过密码界面。

## 🏗️ 构建与发布

需要**特别是 JDK 21** 以及 Android SDK。

1. 安装 [Android Studio](https://developer.android.com/studio)。
2. 打开项目根目录,等待 Gradle 同步完成。
3. 连接一台设备并按下 ▶️ 运行,或使用下方的脚本。

### 🛠️ 构建 + 安装脚本

从仓库根目录的终端出发的最快路径——一步构建调试版 APK、通过 `adb` 列出已连接设备,并将其安装：

```bash
./build-android.sh     # Linux/macOS
build-android.bat      # Windows
```

如果 `adb` 不在 `PATH` 中,脚本仍会完成构建,并打印出 APK 所在位置,以便手动安装。

### ⚙️ 手动构建

不使用脚本的等效步骤,适用于 CI 或纯终端环境：

```bash
./gradlew assembleDebug        # Linux/macOS
gradlew.bat assembleDebug      # Windows
```

APK 会生成在 `app/build/outputs/apk/debug/app-debug.apk`。使用 `adb install -r -d app/build/outputs/apk/debug/app-debug.apk` 安装,或手动传输到设备上。将 `assembleDebug` 替换为 `assembleRelease` 即可构建发布版本——存在 `keystore.properties`（或发布签名环境变量）时使用私有发布密钥签名，否则回退到调试密钥；调试密钥可正常安装用于测试，但绝不能用于分发。

### 版本管理

本仓库遵循生态系统统一的策略：构建绝不会更改版本号。`app/version.properties` 保存当前的 `versionMajor`/`versionMinor`/`versionPatch`/`versionCode`；只有显式的 `bump` 命令才会提升它们（`python tools/hydra_project_tool.py bump`：`bump_manifest_version.py` 处理原生版本、`hydra-umc.project.json` 清单和 `CHANGELOG.md`，`bump_version_code.py` 处理独立且始终单调递增的 Android `versionCode`）。`app/build.gradle.kts` 只*读取* `version.properties`，从不写入。

- **`versionCode`：** 一个纯粹的单调计数器,每次 bump +1,不进位——Android 要求它在每一个曾经发布过的构建中都严格递增。

单纯执行 `./gradlew assembleDebug`（上面的"手动构建"方式,事先不运行任何递增脚本）只会沿用 `version.properties` 中已有的值——它本身不会递增任何东西。当前运行的版本可在 **About** 对话框中实时查看（`BuildConfig.VERSION_NAME`,读取的是 `version.properties` 中最后保存的 `versionName`）。完整版本历史见 [CHANGELOG.md](CHANGELOG.md)。

## 🩺 故障排除

| 症状 | 原因 | 解决方法 |
|---|---|---|
| 没有通知 | 权限被拒绝 | 在 Android 设置中为本应用授予“通知”权限 |
| 没有生物识别 | 硬件未设置 | 确保你已在 Android 系统安全设置中注册了指纹/面容 |
| 机器人无法移动 | 会话已过期、机器人被其他账户预留,或轨迹正在运行 | 查看状态栏中的消息；轨迹由服务器自行播放,无需浏览器标签页 |
| 蓝牙已禁用 | 物理芯片关闭 | 使用应用中的“启用系统蓝牙”3D 按钮 |

## 📂 仓库结构

<p align="center">
  <img src="images/repository-structure.svg" alt="Repository structure of HYDRA-UMC-ANDROID-CONTROL" width="100%">
</p>

<details>
<summary>目录树的文本版本</summary>

```text
HYDRA-UMC-ANDROID-CONTROL/
├── app/src/main/java/com/hydraumc/control/
│   ├── MainActivity.kt                                          入口点——启动画面、登录/主界面门禁、冷启动安全的全局紧急停止处理
│   ├── MainScreen.kt                                            底部导航脚手架、顶部栏（服务器选择器、资料、遥测、设置）
│   ├── kinematics/                                              PAROL6 机械臂的正逆运动学
│   ├── model/                                                   应用读取到的服务器状态
│   ├── network/                                                 REST 客户端、WebSocket 和发现
│   ├── ui/                                                      界面和控件
│   ├── update/                                                  应用内更新通道
│   ├── util/                                                    应用的纯规则
│   ├── viewmodel/                                               每个界面监听的共享状态
│   ├── wear/                                                    语音和状态中继
│   └── widget/                                                  主屏幕的停止小组件
├── docs/                                                        项目文档
├── images/                                                      README 横幅 + 启动画面源资产
├── tools/                                                       项目工具和 CI 检查
├── .github/                                                     CI 工作流和 Issue 模板
├── gradle/                                                      Gradle 包装器文件
├── bump_manifest_version.py                                     将 hydra-umc.project.json 的版本与原生版本同步（--sync）
├── bump_version_code.py                                         Android versionCode 计数器递增（仅由显式 `bump` 命令运行）
├── gradlew / gradlew.bat                                        Gradle 包装器
├── build.gradle.kts / settings.gradle.kts / gradle.properties   根 Gradle 项目配置
├── keystore.properties.example                                  私密发布签名配置模板
├── .env.example                                                 环境变量示例
├── LICENSE                                                      GPL-3.0-or-later
├── CHANGELOG.md                                                 每个版本的变更
├── build-test.sh                                                不修改内容的构建检查
├── hydra-umc.project.json                                       清单：版本、成熟度、系列
├── test.sh                                                      不修改内容的构建检查
├── prepare-github-release.sh / prepare-github-release.bat       构建用私钥签名的发布 APK；不改变任何版本
├── publish-github-release.sh / publish-github-release.ps1       把该 APK 发布为当前版本的 GitHub 发布
├── app/version.properties                                       版本和 versionCode
└── app/src/test/                                                JVM 单元测试
```

</details>

## 🗺️ 路线图与已知限制

在有设备实现 `docs/BLE_CONTRACT.md` 的外设一侧之前，蓝牙 LE 仍是实验性的。TLS 和固定指纹是用真实握手对本地测试服务器测试的，而不是对启用 TLS 的 HYDRA-UMC 服务器。内嵌的 3D 视图尚未遵循 TLS 设置。Keystore 存储、生物识别提示和蓝牙传输自编写以来没有在硬件上重新验证过。

## 🌍 语言

本 README 和仓库文档提供八种语言：英语、西班牙语、德语、法语、意大利语、日语、中文和葡萄牙语（葡萄牙）。它们同时编写，并由 CI 检查八个文件拥有相同的章节和相同的链接。

## 🔗 相关项目

本项目是同一作者(JuanenRac / Electro Hobby 3D)打造的 HYDRA-UMC 机器人生态系统的一部分。值得了解,因为某个请求实际上可能是关于这些项目之一,而非本仓库本身。

**父项目**
- **[HYDRA-UMC-SERVER](https://github.com/JuanenRac/HYDRA-UMC-SERVER)** —— 每个控制客户端真正通信的真实无头后端(REST/WebSocket);本应用自身的发现、认证与 WebSocket 同步都基于此后端运行。

**兄弟项目** —— 同样与 HYDRA-UMC-SERVER 自身 API 通信,各自作为独立客户端
- **[HYDRA-UMC-STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO)** —— 具有实时多机器人 3D 可视化的网页控制面板;其自身的 3D 视图通过 WebView 直接嵌入本应用的 3D 视图界面。
- **[HYDRA-UMC-SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE)** —— 面向多台服务器的桌面(PySide6)集群指挥中心,打包为独立可执行文件;与本应用使用完全相同的 `REMOTE_API.md` 契约。
- **[HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL)** —— 具有实时 WebSocket 同步的 iOS/iPadOS 控制应用(Flutter);本应用直接对应的 iOS/iPadOS 版本,功能集相同。
- **[HYDRA-UMC-DSI](https://github.com/JuanenRac/HYDRA-UMC-DSI)** —— 面向机载 7 英寸 DSI 触摸屏的原生触控界面,直接嵌入 CM5 本体。
- **[HYDRA-UMC-BRIDGE-AMR](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-AMR)** —— 通过 VDA 5050 MQTT 发布者为 AGV/AMR 车队提供的协调边界(仅针对仿真车队测试)。
- **[HYDRA-UMC-BRIDGE-CNC](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-CNC)** —— 具备 GRBL 状态/控制字节访问能力的高层 CNC 单元协调器(仅针对模拟器测试)。
- **[HYDRA-UMC-BRIDGE-DROIDS](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-DROIDS)** —— 面向足式/人形机器人的协调边界,带有 Boston Dynamics Spot 指令发送器(仅针对模拟器测试)。
- **[HYDRA-UMC-BRIDGE-LASER](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-LASER)** —— 读取 3 路钥匙/外壳/联锁 GPIO 信号的激光单元协调器(仅针对模拟芯片测试;无法使激光器上膛或发射)。
- **[HYDRA-UMC-BRIDGE-OPENPNP](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-OPENPNP)** —— 面向 OpenPnP 贴片机板级流程的安全高层协调器。
- **[HYDRA-UMC-BRIDGE-PRINTER3D](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-PRINTER3D)** —— 面向 Moonraker/Klipper 3D 打印机的安全协调边界,具备真实的受控作业指令。
- **[HYDRA-UMC-BRIDGE-ROS2](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-ROS2)** —— 带有惰性导入 rclpy ROS 2 传输层的协调器(尚未在 ROS 2 环境中运行)。
- **[HYDRA-UMC-BRIDGE-UAV](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-UAV)** —— 面向搭载摄像头的无人机的协调边界,带有 MAVLink 指令发送器(仅针对模拟器测试)。

**直接相关**
- **[HYDRA-UMC-WATCH](https://github.com/JuanenRac/HYDRA-UMC-WATCH)** —— 带有触觉提醒模式与配对手机语音中继的 WearOS 伴侣应用(尚未在实体手表上运行);本应用的 WearOS 伴侣,可从手腕一目了然地查看机器人状态并进行控制。
- **[HYDRA-UMC-HIL-BRIDGE](https://github.com/JuanenRac/HYDRA-UMC-HIL-BRIDGE)** —— 在仿真与真实硬件之间带有软件碰撞联锁的硬件在环路由器(未在真实硬件上测试);支持直接从本应用远程控制数字孪生。

**生态系统中的其他项目**

*核心硬件与平台*
- **[HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC)** —— 机器人手臂主板设计——CM5 主机 + 双核 STM32H745,目标是通过 CAN-OTA/SPI-OTA 协调最多 8 条工具臂(固件处于早期阶段,尚无已组装的电路板)。
- **[HYDRA-UMC-OS](https://github.com/JuanenRac/HYDRA-UMC-OS)** —— 面向 CM5 的可复现 Raspberry Pi OS 产品层——只读代理、经过验证的配置/配置文件、WiFi 首次配网。
- **[HYDRA-UMC-SDK](https://github.com/JuanenRac/HYDRA-UMC-SDK)** —— 每个桥接都据此校验自身指令的共享 JSON-Schema 契约与软件作业门限边界(并非经认证的安全功能)。
- **[HYDRA-UMC-CONNECTOR-HUB](https://github.com/JuanenRac/HYDRA-UMC-CONNECTOR-HUB)** — 面向外部机器连接器的声明式适配器清单注册与校验工具；把 SDK 自身的契约理念扩展到外部机器，而不取代工业网关类项目。

*核心后端与客户端*
- **[HYDRA-UMC-EDITOR-URDF](https://github.com/JuanenRac/HYDRA-UMC-EDITOR-URDF)** —— 将完成的模型推送到 STUDIO 自身目录的桌面版图形化 URDF 创建/编辑工具。
- **[HYDRA-UMC-EDITOR-STL](https://github.com/JuanenRac/HYDRA-UMC-EDITOR-STL)** — 桌面 STL 模型编辑器，在 HYDRA-UMC-EDITOR-URDF 编辑的同一模型目录中变换/替换/移除/添加真实部件。

*URTC 工具平台*
- **[URTC](https://github.com/JuanenRac/URTC)** —— 面向实体 Universal Robot Tool Controller 板卡的固件,通过 CAN 总线支持 25 种以上工具配置。
- **[URTC-FLASHER](https://github.com/JuanenRac/URTC-FLASHER)** —— 面向 URTC 板卡的桌面图形烧录工具,支持 CAN-OTA 以及全芯片 SWD/JTAG。
- **[URTC-TESTER](https://github.com/JuanenRac/URTC-TESTER)** —— 面向 URTC 板卡的桌面实时 CAN 总线诊断工具,每种工具配置对应一个面板。
- **[URTC-WEB-STUDIO](https://github.com/JuanenRac/URTC-WEB-STUDIO)** —— 通过 Web Serial API 实现的浏览器版 URTC-TESTER 替代方案,无需本地安装。
- **[URTC-GATEWAY](https://github.com/JuanenRac/URTC-GATEWAY)** —— URTC CAN 总线前的网关：允许的标识符列表、速率限制和会话密钥；软件已在计算机上测试，尚未在真实总线上测试。

*视觉 AI 节点(Hailo-8)*
- **[HYDRA-UMC-VISION-NODE](https://github.com/JuanenRac/HYDRA-UMC-VISION-NODE)** —— 面向 Hailo-8 视觉流水线的集成中枢,具备逐阶段的真实硬件就绪检测。
- **[HYDRA-UMC-DETECTION-HEF](https://github.com/JuanenRac/HYDRA-UMC-DETECTION-HEF)** —— 具备 Hailo 架构/校验和安全加载验证的真实编译模型注册表。
- **[HYDRA-UMC-VISION-STREAMER](https://github.com/JuanenRac/HYDRA-UMC-VISION-STREAMER)** —— 具备真实 HailoRT 集成边界的真实 GStreamer 流水线 + MediaMTX 配置生成器。
- **[HYDRA-UMC-VISUAL-SERVOING-API](https://github.com/JuanenRac/HYDRA-UMC-VISUAL-SERVOING-API)** —— 以区域状态为授权条件的 Position-Based Visual Servoing 修正律;尚无摄像头位姿估计。
- **[HYDRA-UMC-SAFETY-ZONES](https://github.com/JuanenRac/HYDRA-UMC-SAFETY-ZONES)** —— 从文件读取物体位置的 v0 区域入侵、校准新鲜度与 E-STOP 请求逻辑;没有检测器为其供数,也尚未连接任何物理 E-STOP。

*认知 AI 节点(Hailo-10)*
- **[HYDRA-UMC-COGNITIVE-NODE](https://github.com/JuanenRac/HYDRA-UMC-COGNITIVE-NODE)** —— 面向 Hailo-10 认知流水线(LLM/VLA/语音编排)的集成中枢。
- **[HYDRA-UMC-VLA-ENGINE](https://github.com/JuanenRac/HYDRA-UMC-VLA-ENGINE)** —— 面向 Vision-Language-Action 模型的真实动作 token 编解码与轨迹生成。
- **[HYDRA-UMC-VOICE-UI](https://github.com/JuanenRac/HYDRA-UMC-VOICE-UI)** —— 具备受限、需确认的 Watch 中继的真实语音前端(VAD + 意图解析)。
- **[HYDRA-UMC-SEMANTIC-PLANNER](https://github.com/JuanenRac/HYDRA-UMC-SEMANTIC-PLANNER)** —— 基于真实规则的任务分解,以及针对 MCU 错误码的语义化错误恢复。
- **[HYDRA-UMC-DOCS-QA](https://github.com/JuanenRac/HYDRA-UMC-DOCS-QA)** —— 面向本生态系统自身 Markdown 文档的真实纯标准库 TF-IDF 文档检索。
- **[HYDRA-UMC-LOCAL-TECHNICIAN](https://github.com/JuanenRac/HYDRA-UMC-LOCAL-TECHNICIAN)** — 面向生态系统自身的本地、按策略门控的 AI 维护技师——观察、诊断并提出修复建议;两个最高风险等级目前特意尚未实现。

*编排与集群*
- **[HYDRA-UMC-ORCHESTRATOR](https://github.com/JuanenRac/HYDRA-UMC-ORCHESTRATOR)** —— 具备真实 gRPC/Protobuf 健康报告契约与任务状态机的集成中枢。
- **[HYDRA-UMC-JOB-DISPATCHER](https://github.com/JuanenRac/HYDRA-UMC-JOB-DISPATCHER)** —— 基于真实 HTTP API 的真实优先级任务队列,支持去重。
- **[HYDRA-UMC-NODE-HEALING](https://github.com/JuanenRac/HYDRA-UMC-NODE-HEALING)** —— 具备重试/退避与身份不匹配检测的真实基于 gRPC 的车队健康看门狗。
- **[HYDRA-UMC-PATH-PLANNER-3D](https://github.com/JuanenRac/HYDRA-UMC-PATH-PLANNER-3D)** —— 具备真实障碍物/工作空间碰撞校验的真实基于 RRT 的三维路径规划器。
- **[HYDRA-UMC-SWARM-SYNC](https://github.com/JuanenRac/HYDRA-UMC-SWARM-SYNC)** —— 经过多单元收敛属性测试的真实 CRDT LWW-Element-Map 状态同步。

*数字孪生与仿真*
- **[HYDRA-UMC-TWIN](https://github.com/JuanenRac/HYDRA-UMC-TWIN)** —— 面向数字孪生引擎的集成中枢,具备真实的版本兼容性同步契约。
- **[HYDRA-UMC-PHYSICS-REPLICA](https://github.com/JuanenRac/HYDRA-UMC-PHYSICS-REPLICA)** —— 面向真实 URDF 子集的真实正向运动学与关节限位校验。
- **[HYDRA-UMC-SYNTHETIC-DATA-GEN](https://github.com/JuanenRac/HYDRA-UMC-SYNTHETIC-DATA-GEN)** —— 具备 YOLO/COCO 标注导出功能的真实程序化 2D 场景生成器。

*数据与分析*
- **[HYDRA-UMC-DATALAKE](https://github.com/JuanenRac/HYDRA-UMC-DATALAKE)** —— 具备真实数据摄入/查询 HTTP API 的真实 sqlite3 时序数据存储。
- **[HYDRA-UMC-ANOMALY-DETECTOR](https://github.com/JuanenRac/HYDRA-UMC-ANOMALY-DETECTOR)** —— 具备漂移监测能力的真实 FFT + 统计基线异常检测器。
- **[HYDRA-UMC-PRODUCTION-REPORTS](https://github.com/JuanenRac/HYDRA-UMC-PRODUCTION-REPORTS)** —— 基于 DATALAKE 历史数据的真实 OEE/可用率计算,支持可复现的 CSV 导出。
- **[HYDRA-UMC-TELEMETRY-COLLECTOR](https://github.com/JuanenRac/HYDRA-UMC-TELEMETRY-COLLECTOR)** —— 面向 DATALAKE 的真实 CAN/WebSocket 数据摄入管道,支持序列去重。

*工业网关*
- **[HYDRA-UMC-GATEWAY-INDUSTRIAL](https://github.com/JuanenRac/HYDRA-UMC-GATEWAY-INDUSTRIAL)** —— 中继至工业协议的集成中枢,具备真实的指令白名单/背压控制层。
- **[HYDRA-UMC-OPCUA-SERVER](https://github.com/JuanenRac/HYDRA-UMC-OPCUA-SERVER)** —— 经真实二进制协议客户端会话验证的真实 OPC-UA 地址空间。
- **[HYDRA-UMC-MQTT-BROKER](https://github.com/JuanenRac/HYDRA-UMC-MQTT-BROKER)** —— 具备可选按客户端认证与主题 ACL 的真实 MQTT 代理。
- **[HYDRA-UMC-MTCONNECT-ADAPTER](https://github.com/JuanenRac/HYDRA-UMC-MTCONNECT-ADAPTER)** —— 具备降级模式输出的真实 MTConnect `/probe` 与 `/current` XML 端点。

*辅助工具与生态系统运维*
- **[HYDRA-UMC-DASHBOARD-AI](https://github.com/JuanenRac/HYDRA-UMC-DASHBOARD-AI)** —— 基于 DATALAKE/ANOMALY-DETECTOR 的智能摘要与异常高亮面板,具备诚实的统计回退机制。
- **[HYDRA-UMC-TOOL-CLI](https://github.com/JuanenRac/HYDRA-UMC-TOOL-CLI)** —— 具备真实、稳定退出码契约的车队 CLI,是 HYDRA-UMC-SERVER 自身 API 的真实在线客户端。
- **[URTC-SMART-RACK](https://github.com/JuanenRac/URTC-SMART-RACK)** —— 面向板卡安装机架的固件,具备真实的工具 ID 解码与 Smart Idle 预热逻辑。
- **[URTC-VISION-TOOL](https://github.com/JuanenRac/URTC-VISION-TOOL)** —— 面向热成像/RGB 检测工具头的固件及真实 Python 视觉伴侣程序。
- **[HYDRA-UMC-UPDATER](https://github.com/JuanenRac/HYDRA-UMC-UPDATER)** —— 发现、克隆并更新本生态系统中每个仓库的管理类桌面工具。
- **[HYDRA-UMC-OS-REBUILDER](https://github.com/JuanenRac/HYDRA-UMC-OS-REBUILDER)** —— 构建即刻可烧录、预装生态系统最新版本的 CM5 镜像的 Windows/Linux 桌面工具,具备类似 Raspberry Pi Imager 风格的首次启动 Wi-Fi/用户/SSH 配置。
- **[HYDRA-UMC-OPS-AGENT](https://github.com/JuanenRac/HYDRA-UMC-OPS-AGENT)** — 维护事件协调器：一个低权限的边缘角色采集经过脱敏的库存/健康快照，一个控制面角色以只读方式渲染它，并请求某个 AI 提供方给出诊断建议——从不应用补丁，也从不部署任何内容。
- **[HYDRA-UMC-DEV-SERVER](https://github.com/JuanenRac/HYDRA-UMC-DEV-SERVER)** — 可复现的开发主机（Raspberry Pi 5 / CM5），保存整个生态系统的源代码，并在持久队列下运行有界的构建/测试任务；这是专用的开发角色，明确区别于运行中的 CM5。

## 📚 文档

更多阅读：

* [docs/SOURCE_MAP.md](docs/SOURCE_MAP.md) - 应用的每个源文件及一行说明
* [生态系统的安全基线](https://github.com/JuanenRac/HYDRA-UMC-DOCS/blob/main/docs/SECURITY_BASELINE.md)
* [生态系统的威胁模型](https://github.com/JuanenRac/HYDRA-UMC-DOCS/blob/main/docs/THREAT_MODEL.md)
* [本仓库的更新日志](CHANGELOG.md)
* [本仓库的许可证](LICENSE.md)

## 🤝 参与贡献与社区

* 提交 pull request 之前，请运行 `HYDRA_UMC_CI=1 py -3 tools/ci_validate.py`（或 `HYDRA_UMC_CI=1 python3 tools/ci_validate.py`）：它必须输出 `CI_VALIDATION=PASS`。此检查不要运行 `build.sh` 或 `build.bat`；构建不是检查。
* 八个 README 由 HYDRA-UMC-DOCS 中的同一个源生成；请勿手工编辑。修改源文件，再用 `tools/make_readmes.py` 重新生成。
* 不要在 README 中声称尚未运行过的内容：属实时请写“仅模拟”或“尚未在硬件上试过”。
* 请参阅 [CONTRIBUTING.md](CONTRIBUTING.md)、[行为准则](CODE_OF_CONDUCT.md)、[SECURITY.md](SECURITY.md) 和 [SUPPORT.md](SUPPORT.md)。

## 👤 作者

**JuanenRac (Electro Hobby 3D)** · electrohobby3d@gmail.com

## 📜 许可证

GPL-3.0-or-later - [LICENSE](LICENSE).
