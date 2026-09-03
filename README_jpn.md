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
  🇯🇵 <b>日本語</b>
</p>


<p align="left">
  <img src="https://img.shields.io/badge/License-GPL%203.0-blue.svg" alt="GPL 3.0">
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF.svg" alt="Kotlin">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg" alt="Compose">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84.svg" alt="Android">
</p>


Wi-Fi または Bluetooth 経由で [HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC) プラットフォーム上のロボットを制御する、ネイティブ Android アプリ（Kotlin + Jetpack Compose）です。[HYDRA-UMC SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE) が使用しているのとまったく同じ [`REMOTE_API.md`](https://github.com/JuanenRac/HYDRA-UMC-SERVER/blob/main/docs/REMOTE_API.md) 契約を話します——稼働中の [HYDRA-UMC-SERVER](https://github.com/JuanenRac/HYDRA-UMC-SERVER) バックエンド（[HYDRA-UMC STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO) 自身の Web ダッシュボードが通信しているのと同じもの）に対するディスカバリー、完全な状態の読み書き、リアルタイム WebSocket 同期。[HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL) の直接的な Android 版対応物です。完全な設計は [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) を参照してください。

## 🏗️ 実装済みの内容

- **アクセス制御と生体認証**（`ui/LoginScreen.kt`、`util/BiometricHelper.kt`）—— **指紋認証と顔認証**（`androidx.biometric`）に対応したプロフェッショナルなログインシステムで、同じ画面上に IP/ポートフィールドを直接配置しているため、先に設定画面を経由しなくてもサーバーを指定できます。「ログイン情報を記憶する」機能、安全な**ログアウト**機構を備え、**5 言語**で完全にローカライズされています。キャッシュされたユーザー名/パスワード/トークン（`network/AuthPrefs.kt`）は、平文ではなく、Keystore に裏打ちされた**暗号化 SharedPreferences**（AES256-GCM）に保存されます——本エコシステム内のすべてのサーバーは初回起動時にデフォルトの `admin`/`admin` アカウントを用意し、サーバー側の Config > Users から追加の低権限**オペレーター**アカウントを作成できます。
- **オフラインモードと状態キャッシュ**（`network/StateCache.kt`）—— **DataStore** を使用した統合永続化エンジン。本アプリは最後に確認されたシステム状態を自動的にキャッシュし、アクティブな Wi-Fi 接続がなくても即座にダッシュボードの閲覧や設定の確認ができます。
- **ミッション通知とアラート**（`util/NotificationHelper.kt`）—— 産業グレードのアラートシステム。ロボットがジョブシーケンスを完了したとき、または重大なハードウェアイベントが発生したときに、高優先度のプッシュ通知を送信し、アプリがバックグラウンドにある場合でも操作員に確実に通知します。
- **産業用テレメトリターミナル**（`ui/TelemetryScreen.kt`）—— ターミナル風のインターフェースを備えた専用のリアルタイムログビューアー。システムイベント、REST/WebSocket 同期を追跡し、色分けされた診断情報を提供します（成功はマトリックスグリーン、エラーはインダストリアルレッド）。
- **高度なダッシュボード**（`ui/DashboardScreen.kt`）—— 遠近感のあるスワイプ効果を備えた高精細な **3D 水平カルーセル**。豊富なロボットのメタデータを表示します：**メーカー**（Source Robotics、Annin、Universal Robots、AgileX など）、**ロボットの役割**（CNC、レーザー、PnP）、そして CAM、XY、ATC、PNP、CNC、LSR、BED、VAC、RCK モジュールのライブ状態を示す**産業用モジュールマトリクス**。
- **システムヘルスモニター**（`ui/DashboardScreen.kt`）—— 接続された Compute Module 5 のリアルタイム指標。**ホスト名**、**整形された稼働時間**（例：「2日4時間15分」）、コントローラーとロボットのアクティブ数を含みます。
- **強化された手動制御**（`ui/ControlScreen.kt`）—— 最大の精度を実現する**50% 拡大されたジョイスティックボタン**を備えた、プロフェッショナルな縦型レイアウト。サーバーから直接ファイルを閲覧・実行できる**ジョブ/軌道セレクター**を含みます。
- **安全性と再生パネル**（`ui/ControlScreen.kt`）—— **緊急停止（E-STOP）**、**開始**、**一時停止**、**停止**ボタンを収めた固定の下部コントロールバー。これらのコントロールは常に表示され、物理的な感覚での確認のための**触覚フィードバック**機能を備えています。
- **3D ビュー**（`ui/ThreeDScreen.kt`）—— ネイティブレンダラーではなく、HYDRA-UMC STUDIO 自身のリアルタイム 3D ビューポートを WebView に埋め込む方式です（`?hideUI=true&robotId=&token=`）——`ui/NativeThreeDScreen.kt` は未完成の Google Filament 実験です（宣言済みですがナビゲーションには接続されておらず、`.glb` アセットの読み込みもまだありません）。今後誰かが引き継ぐ場合のためにツリー内に残されていますが、現在アクティブなコードパスではありません。この WebView アプローチにより、実際に、現在提供されている STUDIO の 3D シーン（すべての実際のロボットメッシュ/運動学）をネイティブに再実装することなく無料で得られます——トレードオフは WebView のレンダリングオーバーヘッドで、バッテリー最適ではありませんが、今日の時点で機能的には完全です。
- **リアルタイム 8 系統ビジョン**（`ui/CameraScreen.kt`、`ui/MjpegPlayer.kt`）—— 産業グレードの**ネイティブ MJPEG ストリーマー**。自律的なバックグラウンドパーサーと Canvas ベースのレンダラーによるゼロレイテンシーの映像テレメトリ、ロボットのビジョンシステムがオフのときに（サイレントに空白のフィードを表示するのではなく）明確な「カメラ無効」状態を表示し、サーバーから直接ロボットのカメラをオン/オフする切り替えスイッチを備えています。手動制御画面での自動**ピクチャーインピクチャー（PIP）**オーバーレイをサポートし、サーバーのカメラ設定を通じて特定のロボットにマッピングされます。
- **スマートディスカバリーと接続性**（`network/Discovery.kt`、`network/HydraApiClient.kt`、`network/HydraWebSocket.kt`）—— スマートフォン自身の /24 上のすべての候補ホスト（他のホストだけでなく、スマートフォン自身の LAN IP や localhost も含む）に対する並行サブネットスキャンで、`GET /api/hydra-info` を探査し、`remoteApiVersion` の存在のみによって実際のサーバーを識別します——これは手動 IP 入力パスが使用しているのと同じチェックであるため、所有者がデフォルトの製品文字列から名前を変更したサーバーでも見つけることができます。**NsdManager**（mDNS/Bonjour）リスナーがそれと並行して動作します——サーバーは実際に `_hydra._tcp`（`bonjour-service`）として自身を告知し、`MainActivity` はこれに必要なランタイムの位置情報/近くのデバイス権限を事前にリクエストします（マニフェストの宣言だけでは API 23 以上で決して自動的には付与されません）——ただし、Wi-Fi 上のマルチキャストの到達性はプレーンな HTTP プローブほど本質的に信頼性が高くないため、サブネットスキャンが主要な経路のままです。本アプリは起動時に自動的に Wi-Fi をアクティブ化し、ローカルの工場ネットワークをスキャンし、最初に利用可能な HYDRA-UMC サーバーへ**ゼロクリック自動接続**を実行します。
- **セキュアな産業アクセス**（`network/HydraApiClient.kt`、`ui/LoginScreen.kt`）—— **JWT（JSON Web Token）** を使用したプロフェッショナルなセキュリティ層。すべての制御指令（ジョグ、再生、緊急停止）は、原子的な `POST /api/robot/:id/command` エンドポイント経由で送信される署名付きトークンを使ってサーバーによって検証されます（下記「原子的な指令同期」参照）——完全な `POST /api/settings` 書き込み（サーバー側で admin 専用）とは異なり、`admin` ロールでも `operator` ロールでも動作します。すべてのリクエストは `X-Hydra-Client: android` ヘッダーも運び、サーバー自身の Config > Remote Access タブが、SUITE/iOS とは独立してこのアプリを許可/ブロックできるようにします。安全なトークンの更新のために**生体認証**（指紋/顔）とシームレスに統合されています。コード `1008`（無効/期限切れトークン）でクローズされた WebSocket は、再接続ループでリトライされるのではなく、「再ログインが必要」として扱われます（`network/HydraWebSocket.kt`）。
- **原子的な指令同期**（`viewmodel/RobotViewModel.kt` 自身の `sendAtomicCommand()`）—— すべての書き込み（有効化/無効化/再生/一時停止/停止/ジョグ/バルブ/ポンプ/速度/ビジョン）は、設定ツリー全体ではなく、小さな単一ロボットの原子的な指令を送信します——サーバーは、他にどの統合ロボットが影響を受けるかを自ら計算し、ディスクに永続化し、他のすべての接続中クライアントへブロードキャストします。有効化/無効化は、再生/一時停止/停止と同じ方法でロボット自身の `combinedWith` 兄弟へ伝播します。これらはすべて同じ影響を受けるロボットの計算ロジックを共有しているためです。
- **緊急管理ウィジェット**（`widget/GlobalStopWidget.kt`）—— 重大な安全性のための専用の**ホーム画面ウィジェット**。アプリを開く必要なく群れ全体のすべてのロボット操作を凍結させる、視認性の高い、即座にアクセスできる**グローバル緊急停止**ボタンを提供します——完全なコールドスタート（プロセスがまだ動作していない状態）からでも、ロボット名簿が実際にロードされるのを確実に待ってから動作します。
- **産業用ハプティクスと安全性**（`ui/ControlScreen.kt`）—— 高度な感覚フィードバックシステム。緊急停止と停止ボタンには本物の**長押し保護**（すばやいタップでは何も起こらず、短い振動+ヒントのみ；本当に長押しした場合にのみ指令が送信されます）と、差別化された触覚パターン（成功、エラー、緊急のパルス）を備え、騒がしい環境でも操作員に物理的な確認を提供します。
- **アプリ内アップデートチャンネル**（`update/GitHubReleaseUpdater.kt`、`update/ReleaseMetadataParser.kt`、`update/SemanticVersion.kt`）—— 起動時および「設定 → アップデート」から最新の安定版 GitHub Release を確認します。下書きでもプレリリースでもないタグの、正確な `HYDRA-UMC-ANDROID-CONTROL-release.apk` アセットのみをダウンロードし、自動インストールは一切行いません —— 最終的な同意は Android 自体のパッケージインストーラーが求めます。完全なリリース契約: [`docs/GITHUB_RELEASE_UPDATES.md`](docs/GITHUB_RELEASE_UPDATES.md)。
- **ペアリングされた Wear OS コンパニオン＆音声リレー**（`wear/WatchVoiceRelayService.kt`、`wear/WatchCompanionProtocol.kt`）—— ペアリングされた [HYDRA-UMC-WATCH](https://github.com/JuanenRac/HYDRA-UMC-WATCH) アプリからの、既に認識済みでユーザーが開始した音声ターンを、本アプリ自身の認証済み Server セッションを通じて `HYDRA-UMC-VOICE-UI` へ中継し、型付きの応答を Watch へ返します。このリレーも Watch のシステムステータスカードも、ロボットコマンドを発行したり `HydraState` を直接操作したりすることは一切ありません —— 動作に関連する応答は明示的に `requiresConfirmation` としてマークされ、プライマリの制御 UI を経由して確認する必要があります。完全な契約: [`docs/WATCH_VOICE_RELAY.md`](docs/WATCH_VOICE_RELAY.md)。
- **ツールチェーンとプロジェクト品質** —— AGP 9.3.1、Kotlin 2.2.10、Gradle 9.7.0、compileSdk 36、**JDK 21**（`compileOptions`、`gradle-daemon-jvm.properties`、そして `.idea/`/`.vscode/` のプロジェクトファイルもすべて、このドキュメントの記述だけでなく実際にそれをターゲットとしています）。警告ゼロのクリーンなビルド出力、最適化された R8 プロダクションバリアント、高度な **Roborazzi** スクリーンショットテスト。

**状態：Wi-Fi、Bluetooth、生体認証、通知が実装済み。** 本アプリは、ミッションクリティカルなロボット運用に対応できる高等級の産業用コンソールです。

## 🚀 ビルド

**特に JDK 21** と Android SDK が必要です。

1. [Android Studio](https://developer.android.com/studio) をインストールします。
2. プロジェクトルートを開き、Gradle の同期が完了するのを待ちます。
3. デバイスを接続して ▶️ 実行を押すか、下記のスクリプトを使用します。

### 🛠️ ビルド + インストールスクリプト

リポジトリルートのターミナルから最も速い経路——デバッグ APK をビルドし、`adb` 経由で接続中のデバイスを一覧表示し、一括でインストールします：

```bash
./build-android.sh     # Linux/macOS
build-android.bat      # Windows
```

`adb` が `PATH` にない場合でも、スクリプトはビルドを完了させ、APK の格納場所を表示するので、手動でインストールできます。

### ⚙️ 手動ビルド

スクリプトを使わない同等の手順、CI やプレーンなターミナル向け：

```bash
./gradlew assembleDebug        # Linux/macOS
gradlew.bat assembleDebug      # Windows
```

APK は `app/build/outputs/apk/debug/app-debug.apk` に生成されます。`adb install -r -d app/build/outputs/apk/debug/app-debug.apk` でインストールするか、手動でデバイスに転送してください。リリースビルドには `assembleDebug` の代わりに `assembleRelease` を使用してください——現在はデバッグキーで署名しています（`app/build.gradle.kts` 自身の `release` ブロック、テストを容易にするためにそのままにしてあります）。そのため問題なくインストールできますが、そのままでは配布の準備はできていません。

## 🔢 バージョン管理

本リポジトリは、エコシステム全体で統一されたポリシーに従います：バージョンは**実際のビルドのたび**に自動的に加算され、`app/build.gradle.kts` の `versionName`/`versionCode` を手動で編集する必要はありません。`app/version.properties` は現在の `versionMajor`/`versionMinor`/`versionPatch`/`versionCode` を保持します。`app/build.gradle.kts` は Gradle の**構成**時にそれを読み取り、加算し、書き戻します——これは実際のすべてのビルド（`assembleDebug`、`compileDebugKotlin`、IDE 同期など）で実行されるため、生成される APK は常に最後のものより厳密に新しい番号を持ちます：

- **Patch、オドメーター方式（10 進法）：** 毎回のビルドで +1；9 を超えるとリセットされて 0 になり、代わりに minor が +1 されます——例：`0.0.9` -> `0.1.0`。Major は自動的には決して変更されません。
- **`versionCode`：** 単純な単調カウンター、毎回のビルドで +1、繰り上がりなし——Android は、これまでに出荷されたすべてのビルドにわたって厳密に増加することを要求します。

現在実行中のバージョンは **About** ダイアログでリアルタイムに確認できます（`BuildConfig.VERSION_NAME`、Gradle がちょうど計算した `versionName` を読み取ります）。バージョン履歴は [CHANGELOG.md](CHANGELOG.md) を参照してください。

## 📲 実際のサーバーに対するテスト

1. バックエンドを実行：`cd HYDRA-UMC-SERVER && npm run dev`（ポート 3000）——これが本アプリが実際に通信する本物の REST/WS API です（下記「関連プロジェクト」参照）。`HYDRA-UMC-STUDIO` 自身の `npm run dev` は、その同じバックエンドに対して自身の Vite フロントエンド開発サーバー（ポート 5173）を起動するだけであり、それ自体が API サーバーではありません。
2. Android デバイスを同じ Wi-Fi に接続します。
3. **グローバルサーバーセレクター**を使用するか、ヘッダーで IP を手動入力してください。
4. **生体認証：** ユーザープロフィールで「生体認証ログイン」を有効にすると、次回起動時にパスワード画面をスキップできます。

## 🩺 トラブルシューティング

| 症状 | 原因 | 解決方法 |
|---|---|---|
| 通知が来ない | 権限が拒否されている | Android の設定で本アプリの「通知」権限を許可してください |
| 生体認証がない | ハードウェアが未設定 | Android システムセキュリティに指紋/顔が登録されていることを確認してください |
| ロボットが動かない | ブラウザ側の脳リンク | 逆運動学処理のために HYDRA-UMC STUDIO のブラウザタブを開いたままにしてください |
| Bluetooth が無効 | 物理チップがオフ | アプリ内の「システム Bluetooth を有効化」3D ボタンを使用してください |

## 📂 リポジトリ構成

```text
HYDRA-UMC-ANDROID-CONTROL/
├── app/
│   ├── build.gradle.kts          # アプリモジュールの Gradle 設定——AGP/Kotlin/Compose バージョン、依存関係、デバッグ署名の release ビルドタイプ
│   ├── version.properties        # オドメーター式アプリバージョン + Android の versionCode。bump_manifest_version.py/bump_version_code.py が同期
│   ├── proguard-rules.pro        # release ビルド用のコード縮小/難読化ルール
│   └── src/main/
│       ├── AndroidManifest.xml   # 権限、activity/receiver 宣言、usesCleartextTraffic（プレーン HTTP LAN サーバー、TLS なし）
│       ├── java/com/hydraumc/control/
│       │   ├── MainActivity.kt          # エントリポイント——スプラッシュ、ログイン/メイン画面ゲーティング、コールドスタートセーフなグローバル緊急停止処理
│       │   ├── MainScreen.kt            # ボトムナビゲーションのスキャフォールド、トップバー（サーバーセレクター、プロフィール、テレメトリ、設定）
│       │   ├── kinematics/
│       │   │   └── Parol6Kinematics.kt   # Parol6 固有の順運動学/逆運動学
│       │   ├── model/
│       │   │   ├── BleDevice.kt          # Bluetooth LE スキャン結果データクラス
│       │   │   └── HydraState.kt         # settings.json のフィールド単位のミラー（RobotView/ControllerView/JobView）+ ServerInfo ディスカバリーモデル
│       │   ├── network/
│       │   │   ├── AuthPrefs.kt           # 暗号化（AES256-GCM）された資格情報/セッションストレージ
│       │   │   ├── ConnectionPrefs.kt     # 永続化されたサーバー IP/ポート（DataStore Preferences）
│       │   │   ├── Discovery.kt           # 並行 /24 サブネットスキャン（主）+ NSD/mDNS リスナー（副）、LAN 上のサーバーを見つけるため
│       │   │   ├── HydraApiClient.kt      # REST クライアント——ログイン、設定読み書き、原子的ロボット指令、システム指標
│       │   │   ├── HydraBleClient.kt      # Bluetooth GATT クライアント、Wi-Fi の代替トランスポート
│       │   │   ├── HydraWebSocket.kt      # WS 経由のリアルタイム状態差分プッシュ、再接続処理
│       │   │   └── StateCache.kt          # オフラインダッシュボード表示用の最後の既知状態キャッシュ（DataStore）
│       │   ├── ui/
│       │   │   ├── AboutDialog.kt          # アプリ/バージョン情報ダイアログ
│       │   │   ├── CameraScreen.kt         # ロボットごとの MJPEG カメラフィード + ビジョンオン/オフスイッチ
│       │   │   ├── ControlScreen.kt        # 手動ジョグ制御、長押し保護付き緊急停止/再生/一時停止/停止
│       │   │   ├── DashboardScreen.kt      # 3D カルーセルロボットピッカー + システムヘルス + モジュールマトリクス
│       │   │   ├── Joystick3D.kt           # 再利用可能な 2 軸ジョイスティックコンポーネント
│       │   │   ├── LoginScreen.kt          # ユーザー名/パスワード + IP/ポート入力、生体認証ログイン
│       │   │   ├── MjpegPlayer.kt          # MJPEG ストリームパーサー + Canvas レンダラー
│       │   │   ├── NativeThreeDScreen.kt   # Google Filament ネイティブ 3D ビューアー——まだナビゲーションに接続されておらず、.glb パイプラインもなし
│       │   │   ├── PlaybackConsole.kt      # 共有のフローティング緊急停止/再生/一時停止/停止コンソール
│       │   │   ├── SettingsScreen.kt       # Wi-Fi/Bluetooth スキャン UI、接続設定
│       │   │   ├── SplashScreen.kt         # カスタム Compose スプラッシュスクリーン
│       │   │   ├── TelemetryScreen.kt      # ターミナル風のイベント/同期ログビューアー
│       │   │   ├── ThreeDScreen.kt         # 実際の 3D ビューポート——STUDIO 自身のヘッドレス 3D シーンを埋め込む WebView
│       │   │   ├── UserProfileDialog.kt    # プロフィール編集 + 生体認証切り替えダイアログ
│       │   │   └── theme/
│       │   │       ├── Color.kt, Theme.kt, Typography.kt   # Material 3 配色スキーム、テーマラッパー、タイプスケール
│       │   │       └── HydraButton.kt, IndustrialComponents.kt, IndustrialStyle.kt   # 共有の産業風 UI ビルディングブロック
│       │   ├── update/
│       │   │   ├── GitHubReleaseUpdater.kt   # 安全な GitHub Release アップデートクライアント
│       │   │   ├── ReleaseMetadataParser.kt  # 安全な GitHub Release メタデータパーサー
│       │   │   └── SemanticVersion.kt        # アップデート用の厳密なセマンティックバージョンパーサー
│       │   ├── util/
│       │   │   ├── BiometricHelper.kt      # androidx.biometric プロンプトラッパー
│       │   │   ├── NotificationHelper.kt   # ジョブ完了/安全プッシュ通知
│       │   │   └── NotificationPrefs.kt    # アプリ内通知トグルの永続ストレージ
│       │   ├── viewmodel/
│       │   │   ├── AppUpdateViewModel.kt   # ライフサイクルを意識したアプリ更新状態
│       │   │   └── RobotViewModel.kt   # 共有 ViewModel——ネットワーキング、認証、ディスカバリー、原子的指令ディスパッチ、すべての UI 状態
│       │   ├── wear/
│       │   │   ├── WatchCompanionProtocol.kt    # Watch コンパニオンのバージョン状態ワイヤ契約
│       │   │   ├── WatchVoiceRelayContract.kt   # 認証付き Watch 音声リレーワイヤ契約
│       │   │   └── WatchVoiceRelayService.kt    # Wear OS 音声リレーサービス
│       │   └── widget/
│       │       └── GlobalStopWidget.kt # アプリを開かずにグローバル緊急停止を行うためのホーム画面ウィジェット
│       └── res/
│           ├── drawable/, layout/, mipmap*/, xml/   # アイコン、ウィジェットレイアウト、ランチャーアイコン、バックアップ/データ抽出ルール
│           └── values/, values-es/, values-de/, values-fr/, values-it/, values-zh/, values-ja/   # 7 言語の文字列、色、テーマ
├── docs/
│   ├── ARCHITECTURE.md              # 設計/アーキテクチャノート
│   ├── GITHUB_RELEASE_UPDATES.md    # アプリ内アップデート確認/ダウンロード/インストールフロー
│   └── WATCH_VOICE_RELAY.md         # Watch-電話-サーバー間の音声リレー契約
├── images/                       # README バナー + スプラッシュスクリーンのソースアセット
├── tools/
│   ├── build_test.py             # バージョンを更新しないビルド/コンパイル確認
│   └── ci_validate.py            # CI が使用する manifest/CHANGELOG/docs の検証
├── dist/                         # 署名済みリリース APK 出力（gitignore 対象）
├── build-android.bat / .sh       # ワンショットビルド + adb インストールの便利スクリプト
├── build-test.bat / .sh          # バージョンを更新しないビルド/コンパイル確認
├── prepare-github-release.bat / .sh  # バージョンを更新せず、プライベート署名された安定版リリース APK をビルド
├── publish-github-release.ps1 / .sh  # ローカル限定：dist/ の APK を GitHub Release として公開
├── bump_manifest_version.py      # hydra-umc.project.json のバージョンをネイティブ側と同期（--sync）
├── bump_version_code.py          # app/version.properties 内の Android 独自の versionCode カウンターを増加
├── gradlew, gradlew.bat          # Gradle ラッパー
├── build.gradle.kts, settings.gradle.kts, gradle.properties   # ルート Gradle プロジェクト設定
├── local.properties              # ローカルの Android SDK パス（マシン固有、コミットされない）
├── keystore.properties.example   # プライベートリリース署名設定のテンプレート
├── .env.example                  # 環境変数の例
├── metadata.json                 # アプリストア掲載メタデータ（名前/説明）
├── README.md                     # 本ファイル
├── README_spa.md / README_ita.md / README_fra.md / README_deu.md / README_zho.md / README_jpn.md   # 翻訳
└── LICENSE                       # GPL-3.0
```

## 🔗 関連プロジェクト

本プロジェクトは、同じ作者(JuanenRac / Electro Hobby 3D)による HYDRA-UMC ロボティクスエコシステムの一部です。リクエストが実はこの中のどれかについてのものである可能性があるため、知っておく価値があります。

**親プロジェクト**
- **[HYDRA-UMC-SERVER](https://github.com/JuanenRac/HYDRA-UMC-SERVER)** — すべての制御クライアントが実際に通信する、本物のヘッドレスバックエンド(REST/WebSocket)。本アプリ自身のディスカバリー、認証、WebSocket 同期がすべてこれに対して動作するバックエンド。

**兄弟プロジェクト** —— それぞれ独自のクライアントとして、同じく HYDRA-UMC-SERVER 自身の API と通信する
- **[HYDRA-UMC-STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO)** — リアルタイムのマルチロボット 3D 可視化を備えたウェブ制御ダッシュボード。その 3D ビューポートは、WebView 経由で本アプリ自身の 3D ビュー画面に直接組み込まれている。
- **[HYDRA-UMC-SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE)** — 複数のサーバーを同時に扱えるデスクトップ(PySide6)スウォームコマンドセンター、スタンドアロン実行ファイルとしてパッケージ化。本アプリとまったく同じ `REMOTE_API.md` 契約を話す。
- **[HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL)** — リアルタイム WebSocket 同期を備えた iOS/iPadOS 制御アプリ(Flutter)。本アプリの直接の iOS/iPadOS 版であり、機能セットは同一。
- **[HYDRA-UMC-DSI](https://github.com/JuanenRac/HYDRA-UMC-DSI)** — 本体搭載の 7 インチ DSI タッチスクリーン向けネイティブタッチ UI、CM5 自体に組み込み。
- **[HYDRA-UMC-BRIDGE-AMR](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-AMR)** — 実際の VDA 5050 MQTT パブリッシャーによる AGV/AMR フリートの調整境界。
- **[HYDRA-UMC-BRIDGE-CNC](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-CNC)** — 実際の GRBL ステータス/制御バイトへのアクセスを持つ、CNC セルの高レベルコーディネーター。
- **[HYDRA-UMC-BRIDGE-DROIDS](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-DROIDS)** — 実際の Boston Dynamics Spot コマンド送信機能を持つ、脚型/ヒューマノイドドロイドの調整境界。
- **[HYDRA-UMC-BRIDGE-LASER](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-LASER)** — 実際のキー/筐体/インターロック GPIO セーフガード 3 系統を読み取る、レーザーセルの安全コーディネーター。
- **[HYDRA-UMC-BRIDGE-OPENPNP](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-OPENPNP)** — OpenPnP ピックアンドプレースの基板フローを安全に統括する高レベルコーディネーター。
- **[HYDRA-UMC-BRIDGE-PRINTER3D](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-PRINTER3D)** — 実際にゲート制御されたジョブコマンドを持つ、Moonraker/Klipper 3D プリンター向けの安全な調整境界。
- **[HYDRA-UMC-BRIDGE-ROS2](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-ROS2)** — 実際の遅延インポート rclpy ROS 2 トランスポートを持つ安全コーディネーター。
- **[HYDRA-UMC-BRIDGE-UAV](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-UAV)** — 実際の MAVLink コマンド送信機能を持つ、カメラ搭載 UAV の調整境界。

**直接関連**
- **[HYDRA-UMC-WATCH](https://github.com/JuanenRac/HYDRA-UMC-WATCH)** — 実際の触覚アラートとペアリングされたスマートフォンへの音声リレーを備えた WearOS コンパニオンアプリ。本アプリの WearOS コンパニオンであり、手首からロボットの状態を一目で確認し制御できる。
- **[HYDRA-UMC-HIL-BRIDGE](https://github.com/JuanenRac/HYDRA-UMC-HIL-BRIDGE)** — シミュレーションと実際のハードウェアの間でコマンドをルーティングする、実際のハードウェア・イン・ザ・ループ安全インターロック。本アプリから直接デジタルツインをリモート制御できるようにする。

**エコシステムの他のプロジェクト**

*コアハードウェア&プラットフォーム*
- **[HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC)** — 実際のロボットアームのマザーボード——CM5 ホスト + デュアルコア STM32H745、CAN-OTA/SPI-OTA 経由で最大 8 本のツールアームを統括。
- **[HYDRA-UMC-OS](https://github.com/JuanenRac/HYDRA-UMC-OS)** — CM5 向けの再現可能な Raspberry Pi OS プロダクト層——読み取り専用エージェント、検証済み設定/プロファイル、WiFi 初回接続プロビジョニング。
- **[HYDRA-UMC-SDK](https://github.com/JuanenRac/HYDRA-UMC-SDK)** — すべてのブリッジが自身のコマンドを検証する共有 JSON-Schema 契約と安全ゲートの境界。

*コアバックエンド&クライアント*
- **[HYDRA-UMC-EDITOR-URDF](https://github.com/JuanenRac/HYDRA-UMC-EDITOR-URDF)** — 完成したモデルを STUDIO 自身のカタログへ送信するデスクトップ用グラフィカル URDF 作成/編集ツール。

*URTC ツールプラットフォーム*
- **[URTC](https://github.com/JuanenRac/URTC)** — 物理的な Universal Robot Tool Controller 基板向けファームウェア、CAN バス経由の 25 以上のツールプロファイル。
- **[URTC-FLASHER](https://github.com/JuanenRac/URTC-FLASHER)** — URTC 基板用のデスクトップ GUI 書き込みツール、CAN-OTA およびフルチップ SWD/JTAG。
- **[URTC-TESTER](https://github.com/JuanenRac/URTC-TESTER)** — URTC 基板向けのデスクトップ CAN バスライブ診断ツール、ツールプロファイルごとに 1 パネル。
- **[URTC-WEB-STUDIO](https://github.com/JuanenRac/URTC-WEB-STUDIO)** — Web Serial API を使ったブラウザベースの URTC-TESTER の代替、ローカルインストール不要。

*ビジョン AI ノード(Hailo-8)*
- **[HYDRA-UMC-VISION-NODE](https://github.com/JuanenRac/HYDRA-UMC-VISION-NODE)** — Hailo-8 ビジョンパイプラインの統合ハブ、段階ごとの実際のハードウェア準備状況チェック付き。
- **[HYDRA-UMC-DETECTION-HEF](https://github.com/JuanenRac/HYDRA-UMC-DETECTION-HEF)** — Hailo アーキテクチャ/チェックサムによる安全読み込み検証を備えた、実際のコンパイル済みモデルレジストリ。
- **[HYDRA-UMC-VISION-STREAMER](https://github.com/JuanenRac/HYDRA-UMC-VISION-STREAMER)** — 実際の HailoRT 統合境界を持つ、実際の GStreamer パイプライン + MediaMTX 設定生成器。
- **[HYDRA-UMC-VISUAL-SERVOING-API](https://github.com/JuanenRac/HYDRA-UMC-VISUAL-SERVOING-API)** — 上流のゾーン状態に応じて安全ゲート制御される、実際の Position-Based Visual Servoing 補正則。
- **[HYDRA-UMC-SAFETY-ZONES](https://github.com/JuanenRac/HYDRA-UMC-SAFETY-ZONES)** — キャリブレーションの鮮度を強制する、実際のゾーン侵入チェックと E-STOP 要求。

*コグニティブ AI ノード(Hailo-10)*
- **[HYDRA-UMC-COGNITIVE-NODE](https://github.com/JuanenRac/HYDRA-UMC-COGNITIVE-NODE)** — Hailo-10 コグニティブパイプライン(LLM/VLA/音声オーケストレーション)の統合ハブ。
- **[HYDRA-UMC-VLA-ENGINE](https://github.com/JuanenRac/HYDRA-UMC-VLA-ENGINE)** — Vision-Language-Action モデル向けの、実際のアクショントークンのエンコード/デコードと軌道生成。
- **[HYDRA-UMC-VOICE-UI](https://github.com/JuanenRac/HYDRA-UMC-VOICE-UI)** — 確認ゲート付きの限定的な Watch リレーを備えた、実際の音声フロントエンド(VAD + 意図解析)。
- **[HYDRA-UMC-SEMANTIC-PLANNER](https://github.com/JuanenRac/HYDRA-UMC-SEMANTIC-PLANNER)** — MCU エラーコードに対する、実際のルールベースのタスク分解と意味的エラー復旧。
- **[HYDRA-UMC-DOCS-QA](https://github.com/JuanenRac/HYDRA-UMC-DOCS-QA)** — このエコシステム自身の Markdown ドキュメントに対する、標準ライブラリのみの実際の TF-IDF 文書検索。

*オーケストレーション&スウォーム*
- **[HYDRA-UMC-ORCHESTRATOR](https://github.com/JuanenRac/HYDRA-UMC-ORCHESTRATOR)** — 実際の gRPC/Protobuf ヘルスレポート契約とミッションステートマシンを持つ統合ハブ。
- **[HYDRA-UMC-JOB-DISPATCHER](https://github.com/JuanenRac/HYDRA-UMC-JOB-DISPATCHER)** — 実際の HTTP API 上に構築された、優先度ベースの実際のジョブキュー(重複排除付き)。
- **[HYDRA-UMC-NODE-HEALING](https://github.com/JuanenRac/HYDRA-UMC-NODE-HEALING)** — リトライ/バックオフとアイデンティティ不一致検出を備えた、実際の gRPC ベースのフリートヘルスウォッチドッグ。
- **[HYDRA-UMC-PATH-PLANNER-3D](https://github.com/JuanenRac/HYDRA-UMC-PATH-PLANNER-3D)** — 実際の障害物/ワークスペース衝突検証を備えた、実際の RRT ベースの 3D 経路プランナー。
- **[HYDRA-UMC-SWARM-SYNC](https://github.com/JuanenRac/HYDRA-UMC-SWARM-SYNC)** — 複数セルの収束についてプロパティテストされた、実際の CRDT LWW-Element-Map 状態同期。

*デジタルツイン&シミュレーション*
- **[HYDRA-UMC-TWIN](https://github.com/JuanenRac/HYDRA-UMC-TWIN)** — 実際のバージョン互換性同期契約を持つ、デジタルツインエンジンの統合ハブ。
- **[HYDRA-UMC-PHYSICS-REPLICA](https://github.com/JuanenRac/HYDRA-UMC-PHYSICS-REPLICA)** — 実際の URDF サブセットに対する、実際の順運動学と関節限界検証。
- **[HYDRA-UMC-SYNTHETIC-DATA-GEN](https://github.com/JuanenRac/HYDRA-UMC-SYNTHETIC-DATA-GEN)** — YOLO/COCO アノテーションのエクスポート機能を持つ、実際のプロシージャル 2D シーンジェネレーター。

*データ&分析*
- **[HYDRA-UMC-DATALAKE](https://github.com/JuanenRac/HYDRA-UMC-DATALAKE)** — 実際の取り込み/クエリ HTTP API を備えた、実際の sqlite3 ベースの時系列ストア。
- **[HYDRA-UMC-ANOMALY-DETECTOR](https://github.com/JuanenRac/HYDRA-UMC-ANOMALY-DETECTOR)** — ドリフト監視を備えた、実際の FFT + 統計ベースラインによる異常検知器。
- **[HYDRA-UMC-PRODUCTION-REPORTS](https://github.com/JuanenRac/HYDRA-UMC-PRODUCTION-REPORTS)** — DATALAKE の履歴に対する実際の OEE/稼働率計算、再現可能な CSV エクスポート付き。
- **[HYDRA-UMC-TELEMETRY-COLLECTOR](https://github.com/JuanenRac/HYDRA-UMC-TELEMETRY-COLLECTOR)** — シーケンス重複排除機能を備えた、DATALAKE への実際の CAN/WebSocket 取り込みパイプライン。

*産業用ゲートウェイ*
- **[HYDRA-UMC-GATEWAY-INDUSTRIAL](https://github.com/JuanenRac/HYDRA-UMC-GATEWAY-INDUSTRIAL)** — 実際のコマンド許可リスト/バックプレッシャー層を持つ、産業用プロトコルへ中継する統合ハブ。
- **[HYDRA-UMC-OPCUA-SERVER](https://github.com/JuanenRac/HYDRA-UMC-OPCUA-SERVER)** — 実際のバイナリプロトコルクライアントセッションで検証された、実際の OPC-UA アドレス空間。
- **[HYDRA-UMC-MQTT-BROKER](https://github.com/JuanenRac/HYDRA-UMC-MQTT-BROKER)** — クライアント単位のオプション認証とトピック ACL を備えた、実際の MQTT ブローカー。
- **[HYDRA-UMC-MTCONNECT-ADAPTER](https://github.com/JuanenRac/HYDRA-UMC-MTCONNECT-ADAPTER)** — 縮退モード出力を備えた、実際の MTConnect `/probe` および `/current` XML エンドポイント。

*補完ツール&エコシステム運用*
- **[HYDRA-UMC-DASHBOARD-AI](https://github.com/JuanenRac/HYDRA-UMC-DASHBOARD-AI)** — 誠実な統計フォールバックを備えた、DATALAKE/ANOMALY-DETECTOR 上のスマートサマリーと異常ハイライトパネル。
- **[HYDRA-UMC-TOOL-CLI](https://github.com/JuanenRac/HYDRA-UMC-TOOL-CLI)** — 実際の安定した終了コード契約を持つフリート CLI、HYDRA-UMC-SERVER 自身の API の本物のライブクライアント。
- **[URTC-SMART-RACK](https://github.com/JuanenRac/URTC-SMART-RACK)** — 実際の工具 ID デコードと Smart Idle 予熱ロジックを備えた、基板搭載ラック用ファームウェア。
- **[URTC-VISION-TOOL](https://github.com/JuanenRac/URTC-VISION-TOOL)** — サーマル/RGB 検査ツールヘッド向けの、ファームウェアと実際の Python ビジョンコンパニオン。
- **[HYDRA-UMC-UPDATER](https://github.com/JuanenRac/HYDRA-UMC-UPDATER)** — このエコシステム内のすべてのリポジトリを検出・クローン・更新する、管理用デスクトップツール。

---

## 📚 ドキュメント & コミュニティ

- **[CONTRIBUTING.md](CONTRIBUTING.md)** —— プルリクエストのための技術スタックとコーディング指針。
- **[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)** —— このコミュニティで期待される行動規範。
- **[SECURITY.md](SECURITY.md)** —— 脆弱性の報告方法と、このプロジェクトの実際のセキュリティ重点領域。
- **[SUPPORT.md](SUPPORT.md)** —— 質問の投稿先とバグの報告先。
- **[LICENSE.md](LICENSE.md)** —— このプロジェクト自身のライセンス。

## 👤 作者
**JuanenRac** (Electro Hobby 3D)
📧 electrohobby3d@gmail.com
📺 [youtube.com/@electrohobby3d](https://youtube.com/@electrohobby3d)

## 📜 ライセンス

ソースコードは **GNU General Public License v3.0（GPL-3.0）**——[`LICENSE`](LICENSE) を参照してください。

本ドキュメント（本 README およびその自身の翻訳版——`README_spa.md`、`README_ita.md`、`README_fra.md`、`README_deu.md`、`README_zho.md`、`README_jpn.md`）は、**クリエイティブ・コモンズ 表示-継承 4.0 国際（CC BY-SA 4.0）** の下で提供されます。全文は https://creativecommons.org/licenses/by-sa/4.0/ を参照してください。
