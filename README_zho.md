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
  🇨🇳 <b>简体中文</b> |
  <a href="README_jpn.md">🇯🇵 日本語</a>
</p>


<p align="left">
  <img src="https://img.shields.io/badge/License-GPL%203.0-blue.svg" alt="GPL 3.0">
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF.svg" alt="Kotlin">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg" alt="Compose">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84.svg" alt="Android">
</p>


一款原生 Android 应用（Kotlin + Jetpack Compose），通过 Wi-Fi 或蓝牙控制 [HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC) 平台上的机器人，使用与 [HYDRA-UMC SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE) 完全相同的 [`REMOTE_API.md`](https://github.com/JuanenRac/HYDRA-UMC-SERVER/blob/main/docs/REMOTE_API.md) 契约——针对运行中的 [HYDRA-UMC-SERVER](https://github.com/JuanenRac/HYDRA-UMC-SERVER) 后端（与 [HYDRA-UMC STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO) 自身网页仪表盘所通信的同一个后端）进行发现、完整状态读写以及实时 WebSocket 同步。是 [HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL) 的直接 Android 版本对应物。完整设计参见 [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)。

## 🏗️ 已实现的功能

- **访问控制与生物识别**（`ui/LoginScreen.kt`、`util/BiometricHelper.kt`）—— 专业级登录系统，支持**指纹和面部解锁**（`androidx.biometric`），并在同一界面上直接提供 IP/端口字段,无需先单独跳转到设置页面才能指定服务器。包含“记住我”功能、安全的**退出登录**机制,并完全支持**5 种语言**本地化。缓存的用户名/密码/令牌（`network/AuthPrefs.kt`）存放在由 Keystore 支持的**加密 SharedPreferences**（AES256-GCM）中,而非明文——本生态系统中每台服务器在首次启动时都会预置一个默认的 `admin`/`admin` 账户,并可在服务端从 Config > Users 创建额外的低权限**操作员**账户。
- **离线模式与状态缓存**（`network/StateCache.kt`）—— 使用 **DataStore** 集成的持久化引擎。本应用会自动缓存最后已知的系统状态,即使没有活动的 Wi-Fi 连接,也能实现即时的仪表盘查看和配置审查。
- **任务通知与警报**（`util/NotificationHelper.kt`）—— 工业级警报系统。当机器人完成一个作业序列或发生关键硬件事件时,发送高优先级推送通知,确保即使应用在后台运行,操作员也能收到通知。
- **工业遥测终端**（`ui/TelemetryScreen.kt`）—— 一个采用终端风格界面的专用实时日志查看器。跟踪系统事件、REST/WebSocket 同步,并提供颜色编码的诊断信息（成功为矩阵绿色,错误为工业红色）。
- **高级仪表盘**（`ui/DashboardScreen.kt`）—— 具有透视滑动效果的高保真 **3D 水平轮播**。显示丰富的机器人元数据：**制造商**（Source Robotics、Annin、Universal Robots、AgileX 等）、**机器人角色**（CNC、激光、PnP）,以及一个**工业模块矩阵**,显示 CAM、XY、ATC、PNP、CNC、LSR、BED、VAC 和 RCK 模块的实时状态。
- **系统健康监视器**（`ui/DashboardScreen.kt`）—— 针对已连接的 Compute Module 5 的实时指标,包括**主机名**、**格式化的运行时间**（例如“2天4小时15分钟”）,以及控制器和机器人的活动计数。
- **增强型手动控制**（`ui/ControlScreen.kt`）—— 具有专业的纵向布局,配备**放大 50% 的摇杆按钮**以实现最大精度。包含一个**作业/轨迹选择器**,可直接从服务器浏览并执行文件。
- **安全与回放面板**（`ui/ControlScreen.kt`）—— 固定的底部控制栏,容纳**紧急停止（E-STOP）**、**启动**、**暂停**和**停止**按钮。这些控件始终可见,并具有**触觉反馈**功能,提供物理感官层面的确认。
- **3D 视图**（`ui/ThreeDScreen.kt`）—— 通过 WebView 嵌入 HYDRA-UMC STUDIO 自身的实时 3D 视口（`?hideUI=true&robotId=&token=`）,而非原生渲染器——`ui/NativeThreeDScreen.kt` 是一个尚未完成的 Google Filament 实验（已声明但尚未接入导航,也还没有 `.glb` 资产加载功能）,保留在代码树中以便后续有人接手,但不是当前活跃的代码路径。这种 WebView 方式免费获得了真实的、当前正在使用的 STUDIO 3D 场景（每一个真实的机器人网格/运动学）,而无需原生重新实现——代价是 WebView 渲染开销,并非电池最优,但今天功能上是完整的。
- **实时八路视觉**（`ui/CameraScreen.kt`、`ui/MjpegPlayer.kt`）—— 工业级**原生 MJPEG 流播放器**。具备自主的后台解析器和基于 Canvas 的渲染器,实现零延迟视频遥测,当机器人的视觉系统关闭时会显示一个明确的“摄像头已禁用”状态（而非静默显示空白画面）,并配有一个可直接从服务器打开/关闭机器人摄像头的开关。在手动控制界面中支持自动**画中画（PIP）**叠加,通过服务器的摄像头配置映射到特定的机器人。
- **智能发现与连接**（`network/Discovery.kt`、`network/HydraApiClient.kt`、`network/HydraWebSocket.kt`）—— 针对手机自身 /24 网段上的每一个候选主机（包括手机自身的局域网 IP 和 localhost,而不仅仅是其他主机）进行并发子网扫描,探测 `GET /api/hydra-info`,并仅通过 `remoteApiVersion` 字段的存在来识别真实服务器——与手动输入 IP 路径所使用的检测方式相同,因此即使服务器所有者将其重命名为默认产品字符串以外的名称,也依然能被找到。一个 **NsdManager**（mDNS/Bonjour）监听器与其并行运行——服务器确实会以 `_hydra._tcp`（`bonjour-service`）的形式广播自身,`MainActivity` 会预先请求此功能所需的运行时位置/附近设备权限（仅在清单中声明,在 API 23+ 上从不会自动授予）——但由于 Wi-Fi 上的组播可达性本身不如纯粹的 HTTP 探测可靠,子网扫描仍然是主要路径。本应用会在启动时自动激活 Wi-Fi,扫描本地工厂网络,并对第一个可用的 HYDRA-UMC 服务器执行**零点击自动连接**。
- **安全工业访问**（`network/HydraApiClient.kt`、`ui/LoginScreen.kt`）—— 使用 **JWT（JSON Web Token）** 的专业安全层。每一条控制指令（点动、播放、紧急停止）都由服务器使用签名令牌进行验证,通过原子化的 `POST /api/robot/:id/command` 端点发送（见下文“原子化指令同步”）——`admin` 或 `operator` 角色均可使用,不同于完整的 `POST /api/settings` 写入（仅限服务端 admin）。每个请求还携带一个 `X-Hydra-Client: android` 请求头,以便服务器自身的 Config > Remote Access 选项卡可以独立于 SUITE/iOS 来允许/阻止本应用。与**生物识别认证**（指纹/面部）无缝集成,用于安全的令牌续期。以代码 `1008`（无效/过期令牌）关闭的 WebSocket 会被视为“需要重新登录”,而不会进入重连循环重试（`network/HydraWebSocket.kt`）。
- **原子化指令同步**（`viewmodel/RobotViewModel.kt` 自身的 `sendAtomicCommand()`）—— 每一次写入（启用/禁用/播放/暂停/停止/点动/阀门/泵/速度/视觉）都发送一个小型的、单机器人的原子指令,而非整棵设置树——服务器自行计算哪些合并机器人也会受到影响,持久化到磁盘,并向所有其他已连接客户端广播。启用/禁用会以与播放/暂停/停止相同的方式传播到机器人自身的 `combinedWith` 同伴,因为它们都共享相同的受影响机器人计算逻辑。
- **紧急管理小组件**（`widget/GlobalStopWidget.kt`）—— 专用的**主屏幕小组件**,用于关键安全场景。提供一个高可见性、即时访问的**全局紧急停止**按钮,无需打开应用即可冻结整个集群的所有机器人操作——即使从完全冷启动开始（进程尚未运行）,也能可靠地等待机器人名册真正加载完成后再执行操作。
- **工业触觉与安全**（`ui/ControlScreen.kt`）—— 高级感官反馈系统。紧急停止和停止按钮具备真正的**长按保护**（快速轻触不会产生任何效果,只有短促的振动+提示;只有真正的长按才会发送指令）,以及差异化的触觉特征（成功、错误和紧急脉冲）,在嘈杂环境中为操作员提供物理层面的确认。
- **工具链与项目质量** —— AGP 9.3.1、Kotlin 2.2.10、Gradle 9.7.0、compileSdk 36、**JDK 21**（`compileOptions`、`gradle-daemon-jvm.properties`,以及 `.idea/`/`.vscode/` 项目文件都实际以此为目标,而不仅仅是这行文档）。构建输出干净,零警告,经过优化的 R8 生产变体,以及高级的 **Roborazzi** 截图测试。

**状态：Wi-Fi、蓝牙、生物识别和通知均已实现。** 本应用是一款为任务关键型机器人操作准备就绪的高等级工业控制台。

## 🚀 构建

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

APK 会生成在 `app/build/outputs/apk/debug/app-debug.apk`。使用 `adb install -r -d app/build/outputs/apk/debug/app-debug.apk` 安装,或手动传输到设备上。将 `assembleDebug` 替换为 `assembleRelease` 即可构建发布版本——目前它使用调试密钥签名（`app/build.gradle.kts` 自身的 `release` 代码块,这样设置是为了方便测试）,因此可以正常安装,但按原样还不适合分发。

## 🔢 版本管理

本仓库遵循一项全生态系统统一的策略：版本号在**每次真正的构建**时自动递增,无需手动编辑 `app/build.gradle.kts` 的 `versionName`/`versionCode`。`app/version.properties` 保存当前的 `versionMajor`/`versionMinor`/`versionPatch`/`versionCode`;`app/build.gradle.kts` 会在 Gradle **配置**阶段读取、递增并重写它——这一阶段在每次真正的构建（`assembleDebug`、`compileDebugKotlin`、IDE 同步等）中都会运行——因此生成的 APK 始终携带一个严格新于上一次的版本号：

- **Patch,里程表方式（十进制）：** 每次构建 +1;一旦超过 9 就重置为 0,并将 minor 加 1——例如 `0.0.9` -> `0.1.0`。Major 从不被自动修改。
- **`versionCode`：** 一个纯粹的单调计数器,每次构建 +1,不进位——Android 要求它在每一个曾经发布过的构建中都严格递增。

当前运行的版本可在 **About** 对话框中实时查看（`BuildConfig.VERSION_NAME`,读取的正是 Gradle 刚刚计算出的 `versionName`）。完整版本历史见 [CHANGELOG.md](CHANGELOG.md)。

## 📲 针对真实服务器进行测试

1. 运行后端：`cd HYDRA-UMC-SERVER && npm run dev`（端口 3000）——这才是本应用实际通信的真正 REST/WS API（见下方“相关项目”）;`HYDRA-UMC-STUDIO` 自身的 `npm run dev` 只会针对同一个后端启动其 Vite 前端开发服务器（端口 5173）,它本身并不是那个 API 服务器。
2. 将你的 Android 设备连接到同一个 Wi-Fi。
3. 使用**全局服务器选择器**,或在顶部手动输入 IP。
4. **生物识别：** 在用户资料中启用“生物识别登录”,下次启动时即可跳过密码界面。

## 🩺 故障排查

| 症状 | 原因 | 解决方法 |
|---|---|---|
| 没有通知 | 权限被拒绝 | 在 Android 设置中为本应用授予“通知”权限 |
| 没有生物识别 | 硬件未设置 | 确保你已在 Android 系统安全设置中注册了指纹/面容 |
| 机器人无法移动 | 浏览器脑联结 | 保持一个 HYDRA-UMC STUDIO 浏览器标签页处于打开状态以进行逆运动学处理 |
| 蓝牙已禁用 | 物理芯片关闭 | 使用应用中的“启用系统蓝牙”3D 按钮 |

## 📂 仓库结构

```text
HYDRA-UMC-ANDROID-CONTROL/
├── app/
│   ├── build.gradle.kts          # 应用模块 Gradle 配置——AGP/Kotlin/Compose 版本、依赖、debug 签名的 release 构建类型
│   ├── version.properties        # 里程表式应用版本 + Android versionCode，由 bump_manifest_version.py/bump_version_code.py 同步
│   ├── proguard-rules.pro        # release 构建的代码压缩/混淆规则
│   └── src/main/
│       ├── AndroidManifest.xml   # 权限、activity/receiver 声明、usesCleartextTraffic（纯 HTTP 局域网服务器，无 TLS）
│       ├── java/com/hydraumc/control/
│       │   ├── MainActivity.kt          # 入口点——启动画面、登录/主界面门禁、冷启动安全的全局紧急停止处理
│       │   ├── MainScreen.kt            # 底部导航脚手架、顶部栏（服务器选择器、资料、遥测、设置）
│       │   ├── kinematics/
│       │   │   └── Parol6Kinematics.kt   # Parol6 专用的正/逆运动学
│       │   ├── model/
│       │   │   ├── BleDevice.kt          # 蓝牙 LE 扫描结果数据类
│       │   │   └── HydraState.kt         # settings.json 逐字段镜像（RobotView/ControllerView/JobView）+ ServerInfo 发现模型
│       │   ├── network/
│       │   │   ├── AuthPrefs.kt           # 加密（AES256-GCM）凭证/会话存储
│       │   │   ├── ConnectionPrefs.kt     # 持久化的服务器 IP/端口（DataStore Preferences）
│       │   │   ├── Discovery.kt           # 并发 /24 子网扫描（主要）+ NSD/mDNS 监听器（次要），用于在局域网中查找服务器
│       │   │   ├── HydraApiClient.kt      # REST 客户端——登录、设置读写、原子化机器人指令、系统指标
│       │   │   ├── HydraBleClient.kt      # 蓝牙 GATT 客户端，Wi-Fi 之外的替代传输方式
│       │   │   ├── HydraWebSocket.kt      # 通过 WS 进行实时状态增量推送、重连处理
│       │   │   └── StateCache.kt          # 最后已知状态缓存（DataStore），用于离线仪表盘查看
│       │   ├── ui/
│       │   │   ├── AboutDialog.kt          # 应用/版本信息对话框
│       │   │   ├── CameraScreen.kt         # 每机器人 MJPEG 摄像头画面 + 视觉开关
│       │   │   ├── ControlScreen.kt        # 手动点动控制、带长按保护的紧急停止/播放/暂停/停止
│       │   │   ├── DashboardScreen.kt      # 3D 轮播机器人选择器 + 系统健康状态 + 模块矩阵
│       │   │   ├── Joystick3D.kt           # 可复用的双轴摇杆控件
│       │   │   ├── LoginScreen.kt          # 用户名/密码 + IP/端口输入，生物识别登录
│       │   │   ├── MjpegPlayer.kt          # MJPEG 流解析器 + Canvas 渲染器
│       │   │   ├── NativeThreeDScreen.kt   # Google Filament 原生 3D 视图——尚未接入导航，无 .glb 流程
│       │   │   ├── PlaybackConsole.kt      # 共享的悬浮式紧急停止/播放/暂停/停止控制台
│       │   │   ├── SettingsScreen.kt       # Wi-Fi/蓝牙扫描界面、连接设置
│       │   │   ├── SplashScreen.kt         # 自定义 Compose 启动画面
│       │   │   ├── TelemetryScreen.kt      # 终端风格的事件/同步日志查看器
│       │   │   ├── ThreeDScreen.kt         # 真实 3D 视口——通过 WebView 嵌入 STUDIO 自身的无头式 3D 场景
│       │   │   ├── UserProfileDialog.kt    # 资料编辑 + 生物识别开关对话框
│       │   │   └── theme/
│       │   │       ├── Color.kt, Theme.kt, Typography.kt   # Material 3 配色方案、主题包装器、字体比例
│       │   │       └── HydraButton.kt, IndustrialComponents.kt, IndustrialStyle.kt   # 共享的工业风 UI 构建组件
│       │   ├── update/
│       │   │   ├── GitHubReleaseUpdater.kt   # 安全的 GitHub Release 更新客户端
│       │   │   ├── ReleaseMetadataParser.kt  # 安全的 GitHub Release 元数据解析器
│       │   │   └── SemanticVersion.kt        # 用于更新的严格语义化版本解析器
│       │   ├── util/
│       │   │   ├── BiometricHelper.kt      # androidx.biometric 提示包装器
│       │   │   ├── NotificationHelper.kt   # 作业完成/安全推送通知
│       │   │   └── NotificationPrefs.kt    # 应用内通知开关的持久化存储
│       │   ├── viewmodel/
│       │   │   ├── AppUpdateViewModel.kt   # 感知生命周期的应用更新状态
│       │   │   └── RobotViewModel.kt   # 共享 ViewModel——网络、认证、发现、原子指令分发、所有 UI 状态
│       │   ├── wear/
│       │   │   ├── WatchCompanionProtocol.kt    # 手表伴生设备版本状态通信契约
│       │   │   ├── WatchVoiceRelayContract.kt   # 已认证的手表语音中继通信契约
│       │   │   └── WatchVoiceRelayService.kt    # Wear OS 语音中继服务
│       │   └── widget/
│       │       └── GlobalStopWidget.kt # 无需打开应用即可实现全局紧急停止的主屏幕小组件
│       └── res/
│           ├── drawable/, layout/, mipmap*/, xml/   # 图标、小组件布局、启动器图标、备份/数据提取规则
│           └── values/, values-es/, values-de/, values-fr/, values-it/, values-zh/, values-ja/   # 7 种语言的字符串、颜色、主题
├── docs/
│   ├── ARCHITECTURE.md              # 设计/架构说明
│   ├── GITHUB_RELEASE_UPDATES.md    # 应用内更新检查/下载/安装流程
│   └── WATCH_VOICE_RELAY.md         # 手表-手机-服务器语音中继契约
├── images/                       # README 横幅 + 启动画面源资产
├── tools/
│   ├── build_test.py             # 不递增版本号的构建/编译检查
│   └── ci_validate.py            # CI 使用的 manifest/CHANGELOG/docs 校验
├── dist/                         # 已签名发布 APK 输出（已被 gitignore）
├── build-android.bat / .sh       # 一键构建 + adb 安装便捷脚本
├── build-test.bat / .sh          # 不递增版本号的构建/编译检查
├── prepare-github-release.bat / .sh  # 构建一个私密签名、稳定的发布 APK，不递增版本号
├── publish-github-release.ps1 / .sh  # 仅限本地：将 dist/ 中的 APK 发布为 GitHub Release
├── bump_manifest_version.py      # 将 hydra-umc.project.json 的版本与原生版本同步（--sync）
├── bump_version_code.py          # 递增 app/version.properties 中 Android 自身的 versionCode 计数器
├── gradlew, gradlew.bat          # Gradle 包装器
├── build.gradle.kts, settings.gradle.kts, gradle.properties   # 根 Gradle 项目配置
├── local.properties              # 本地 Android SDK 路径（机器特定，不提交）
├── keystore.properties.example   # 私密发布签名配置模板
├── .env.example                  # 环境变量示例
├── metadata.json                 # 应用商店信息元数据（名称/描述）
├── README.md                     # 本文件
├── README_spa.md / README_ita.md / README_fra.md / README_deu.md / README_zho.md / README_jpn.md   # 翻译
└── LICENSE                       # GPL-3.0
```

## 🔗 相关项目

本项目是同一作者(JuanenRac / Electro Hobby 3D)打造的 HYDRA-UMC 机器人生态系统的一部分。值得了解,因为某个请求实际上可能是关于这些项目之一,而非本仓库本身。

**父项目**
- **[HYDRA-UMC-SERVER](https://github.com/JuanenRac/HYDRA-UMC-SERVER)** —— 每个控制客户端真正通信的真实无头后端(REST/WebSocket);本应用自身的发现、认证与 WebSocket 同步都基于此后端运行。

**兄弟项目** —— 同样与 HYDRA-UMC-SERVER 自身 API 通信,各自作为独立客户端
- **[HYDRA-UMC-STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO)** —— 具有实时多机器人 3D 可视化的网页控制面板;其自身的 3D 视图通过 WebView 直接嵌入本应用的 3D 视图界面。
- **[HYDRA-UMC-SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE)** —— 面向多台服务器的桌面(PySide6)集群指挥中心,打包为独立可执行文件;与本应用使用完全相同的 `REMOTE_API.md` 契约。
- **[HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL)** —— 具有实时 WebSocket 同步的 iOS/iPadOS 控制应用(Flutter);本应用直接对应的 iOS/iPadOS 版本,功能集相同。
- **[HYDRA-UMC-DSI](https://github.com/JuanenRac/HYDRA-UMC-DSI)** —— 面向机载 7 英寸 DSI 触摸屏的原生触控界面,直接嵌入 CM5 本体。
- **[HYDRA-UMC-BRIDGE-AMR](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-AMR)** —— 通过真实的 VDA 5050 MQTT 发布者为 AGV/AMR 车队提供的协调边界。
- **[HYDRA-UMC-BRIDGE-CNC](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-CNC)** —— 具备真实 GRBL 状态/控制字节访问能力的高层 CNC 单元协调器。
- **[HYDRA-UMC-BRIDGE-DROIDS](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-DROIDS)** —— 面向足式/人形机器人的协调边界,具备真实的 Boston Dynamics Spot 指令发送器。
- **[HYDRA-UMC-BRIDGE-LASER](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-LASER)** —— 读取 3 项真实钥匙/外壳/联锁 GPIO 安全信号的激光单元安全协调器。
- **[HYDRA-UMC-BRIDGE-OPENPNP](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-OPENPNP)** —— 面向 OpenPnP 贴片机板级流程的安全高层协调器。
- **[HYDRA-UMC-BRIDGE-PRINTER3D](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-PRINTER3D)** —— 面向 Moonraker/Klipper 3D 打印机的安全协调边界,具备真实的受控作业指令。
- **[HYDRA-UMC-BRIDGE-ROS2](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-ROS2)** —— 具备真实的惰性导入 rclpy ROS 2 传输层的安全协调器。
- **[HYDRA-UMC-BRIDGE-UAV](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-UAV)** —— 面向搭载摄像头的无人机的协调边界,具备真实的 MAVLink 指令发送器。

**直接相关**
- **[HYDRA-UMC-WATCH](https://github.com/JuanenRac/HYDRA-UMC-WATCH)** —— 具备真实触觉提醒与配对手机语音中继功能的 WearOS 伴侣应用;本应用的 WearOS 伴侣,可从手腕一目了然地查看机器人状态并进行控制。
- **[HYDRA-UMC-HIL-BRIDGE](https://github.com/JuanenRac/HYDRA-UMC-HIL-BRIDGE)** —— 在仿真与真实硬件之间路由指令的真实硬件在环安全联锁;支持直接从本应用远程控制数字孪生。

**生态系统中的其他项目**

*核心硬件与平台*
- **[HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC)** —— 机器人手臂的真实主板——CM5 主机 + 双核 STM32H745,通过 CAN-OTA/SPI-OTA 协调最多 8 条工具臂。
- **[HYDRA-UMC-OS](https://github.com/JuanenRac/HYDRA-UMC-OS)** —— 面向 CM5 的可复现 Raspberry Pi OS 产品层——只读代理、经过验证的配置/配置文件、WiFi 首次配网。
- **[HYDRA-UMC-SDK](https://github.com/JuanenRac/HYDRA-UMC-SDK)** —— 每个桥接都据此校验自身指令的共享 JSON-Schema 契约与安全门限边界。

*核心后端与客户端*
- **[HYDRA-UMC-EDITOR-URDF](https://github.com/JuanenRac/HYDRA-UMC-EDITOR-URDF)** —— 将完成的模型推送到 STUDIO 自身目录的桌面版图形化 URDF 创建/编辑工具。

*URTC 工具平台*
- **[URTC](https://github.com/JuanenRac/URTC)** —— 面向实体 Universal Robot Tool Controller 板卡的固件,通过 CAN 总线支持 25 种以上工具配置。
- **[URTC-FLASHER](https://github.com/JuanenRac/URTC-FLASHER)** —— 面向 URTC 板卡的桌面图形烧录工具,支持 CAN-OTA 以及全芯片 SWD/JTAG。
- **[URTC-TESTER](https://github.com/JuanenRac/URTC-TESTER)** —— 面向 URTC 板卡的桌面实时 CAN 总线诊断工具,每种工具配置对应一个面板。
- **[URTC-WEB-STUDIO](https://github.com/JuanenRac/URTC-WEB-STUDIO)** —— 通过 Web Serial API 实现的浏览器版 URTC-TESTER 替代方案,无需本地安装。

*视觉 AI 节点(Hailo-8)*
- **[HYDRA-UMC-VISION-NODE](https://github.com/JuanenRac/HYDRA-UMC-VISION-NODE)** —— 面向 Hailo-8 视觉流水线的集成中枢,具备逐阶段的真实硬件就绪检测。
- **[HYDRA-UMC-DETECTION-HEF](https://github.com/JuanenRac/HYDRA-UMC-DETECTION-HEF)** —— 具备 Hailo 架构/校验和安全加载验证的真实编译模型注册表。
- **[HYDRA-UMC-VISION-STREAMER](https://github.com/JuanenRac/HYDRA-UMC-VISION-STREAMER)** —— 具备真实 HailoRT 集成边界的真实 GStreamer 流水线 + MediaMTX 配置生成器。
- **[HYDRA-UMC-VISUAL-SERVOING-API](https://github.com/JuanenRac/HYDRA-UMC-VISUAL-SERVOING-API)** —— 具备真实 Position-Based Visual Servoing 修正律,并依据上游区域状态进行安全门控。
- **[HYDRA-UMC-SAFETY-ZONES](https://github.com/JuanenRac/HYDRA-UMC-SAFETY-ZONES)** —— 具备校准新鲜度强制检查的真实区域入侵检测与 E-STOP 请求。

*认知 AI 节点(Hailo-10)*
- **[HYDRA-UMC-COGNITIVE-NODE](https://github.com/JuanenRac/HYDRA-UMC-COGNITIVE-NODE)** —— 面向 Hailo-10 认知流水线(LLM/VLA/语音编排)的集成中枢。
- **[HYDRA-UMC-VLA-ENGINE](https://github.com/JuanenRac/HYDRA-UMC-VLA-ENGINE)** —— 面向 Vision-Language-Action 模型的真实动作 token 编解码与轨迹生成。
- **[HYDRA-UMC-VOICE-UI](https://github.com/JuanenRac/HYDRA-UMC-VOICE-UI)** —— 具备受限、需确认的 Watch 中继的真实语音前端(VAD + 意图解析)。
- **[HYDRA-UMC-SEMANTIC-PLANNER](https://github.com/JuanenRac/HYDRA-UMC-SEMANTIC-PLANNER)** —— 基于真实规则的任务分解,以及针对 MCU 错误码的语义化错误恢复。
- **[HYDRA-UMC-DOCS-QA](https://github.com/JuanenRac/HYDRA-UMC-DOCS-QA)** —— 面向本生态系统自身 Markdown 文档的真实纯标准库 TF-IDF 文档检索。

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

## 👤 作者
**JuanenRac** (Electro Hobby 3D)
📧 electrohobby3d@gmail.com
📺 [youtube.com/@electrohobby3d](https://youtube.com/@electrohobby3d)

## 📜 许可证

源代码采用 **GNU 通用公共许可证 v3.0（GPL-3.0）**——见 [`LICENSE`](LICENSE)。

本文档（本 README 及其自身的翻译版本——`README_spa.md`、`README_ita.md`、`README_fra.md`、`README_deu.md`、`README_zho.md`、`README_jpn.md`）依据 **知识共享 署名-相同方式共享 4.0 国际许可协议（CC BY-SA 4.0）** 提供。完整文本见 https://creativecommons.org/licenses/by-sa/4.0/。
