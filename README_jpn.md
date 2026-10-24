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


<p align="center">
  <img src="https://img.shields.io/badge/License-GPL%203.0--or--later-blue.svg" alt="GPL 3.0 or later">
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF.svg" alt="Kotlin">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg" alt="Compose">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84.svg" alt="Android">
</p>


Wi-Fi または Bluetooth 経由で [HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC) プラットフォーム上のロボットを制御する、ネイティブ Android アプリ（Kotlin + Jetpack Compose）です。[HYDRA-UMC SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE) が使用しているのとまったく同じ [`REMOTE_API.md`](https://github.com/JuanenRac/HYDRA-UMC-SERVER/blob/main/docs/REMOTE_API.md) 契約を話します——稼働中の [HYDRA-UMC-SERVER](https://github.com/JuanenRac/HYDRA-UMC-SERVER) バックエンド（[HYDRA-UMC STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO) 自身の Web ダッシュボードが通信しているのと同じもの）に対するディスカバリー、完全な状態の読み書き、リアルタイム WebSocket 同期。[HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL) の直接的な Android 版対応物です。完全な設計は [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) を参照してください。

> **誠実性チェック - 今日実際に動くもの:** 中核のビジネスロジック - Parol6 運動学（`kinematics/Parol6Kinematics.kt`）、settings ツリーの状態契約（`model/HydraState.kt`）、アトミックコマンド送出と音声リレー（`viewmodel/RobotViewModel.kt`）、WebSocket 再接続処理（`network/HydraWebSocket.kt`）、MJPEG フレーム解析（`ui/MjpegPlayer.kt`）、アプリ内アップデートチャネル（`update/GitHubReleaseUpdater.kt`、`ReleaseMetadataParser.kt`、`SemanticVersion.kt`）、および Wear 音声リレーのリクエスト管理（`wear/BoundedRequestScope.kt`、`WatchCompanionProtocol.kt`）は本物であり、280件の JVM/Robolectric ユニットテストがすべて通過している（`./gradlew testDebugUnitTest`）。これらのテストが構造的に到達できない部分 - そしてこの Windows 専用の開発環境が実機の Android デバイスを持たず検証できない部分 - は `network/AuthPrefs.kt` の実際の Keystore 裏付け `EncryptedSharedPreferences`、`androidx.biometric` の指紋・顔認証プロンプト、`network/HydraBleClient.kt` の Bluetooth GATT トランスポート、そして `MjpegStreamParser` 内の API 28+ ハードウェア `ImageDecoder` パスであり、これらは実装され実際にコンパイルされているコードだが、書かれて以来実機で再検証されていない。基本的な起動・ログインについては、過去のリリースで実機上でのライブ確認が*行われている*(`CHANGELOG.md` の `v0.3.2`/`v0.3.3` の項目を参照)が、それは Bluetooth・生体認証・ハードウェアデコードパスより前の話である。実際に出荷された内容の詳細、および何がまだハードウェア未検証かの各エントリごとの注記については `CHANGELOG.md` を参照。

> **安全性とセキュリティの現状:** このエコシステムは認証された安全システムではありません。ハードウェアの E-STOP 経路、ファームウェアが強制する制限やインターロック、検証済みの動作コマンドはまだありません。ファームウェアイメージは CRC32 と共有鍵の HMAC-SHA256 で検査しており、非対称署名ではありません。TLS と相互 TLS は任意で、既定では無効です。独自基板はまだ実装も試験もされていません。ここに書かれた内容を人の安全のために頼る前に、[セキュリティベースライン](https://github.com/JuanenRac/HYDRA-UMC-DOCS/blob/main/docs/SECURITY_BASELINE.md)と[脅威モデル](https://github.com/JuanenRac/HYDRA-UMC-DOCS/blob/main/docs/THREAT_MODEL.md)をお読みください。

## 📋 ひと目で

| | |
|---|---|
| バージョン | `0.7.2` |
| 成熟度 | 機能する |
| テスト | 300 (`./gradlew testDebugUnitTest`) |
| 技術 | Kotlin / Jetpack / biometric auth / WebSocket client |
| 対象 | Android のスマートフォンまたはタブレット |
| 実機での確認 | まだです：このバージョンについて実機での動作記録はありません |

## 🚦 プロジェクトの状況

各行はこのリポジトリの一部分で、信号は今日どこまで進んでいるかを示します。計画ではなく、リポジトリが示せるもの（テスト、ビルド、実行）に基づいて書かれています。

🟢 完了・検証済み · 🟡 動作する（既知の制限あり） · 🟠 一部のみ、または未検証 · 🔴 未実装または動作しない

| 領域 | 状態 | ここでの意味 |
|---|---|---|
| ソフトウェア | 🟡 | 主な目的では動作します。足りない点は上の正直さの確認に書かれています |
| テスト | 🟢 | 自動テスト 300 件。変更のたびに実行されます |
| 実機 | 🔴 | まだです：このバージョンについて実機での動作記録はありません |
| ドキュメント | 🟢 | 8 言語の README。変更のたびにマニフェストと照合されます |

## 🏗️ 実装済みの内容

- **アクセス制御と生体認証**（`ui/LoginScreen.kt`、`util/BiometricHelper.kt`）—— **指紋認証と顔認証**（`androidx.biometric`）に対応したプロフェッショナルなログインシステムで、同じ画面上に IP/ポートフィールドを直接配置しているため、先に設定画面を経由しなくてもサーバーを指定できます。「ログイン情報を記憶する」機能、安全な**ログアウト**機構を備え、**8 言語**で完全にローカライズされています。キャッシュされたユーザー名/パスワード/トークン（`network/AuthPrefs.kt`）は、平文ではなく、Keystore に裏打ちされた**暗号化 SharedPreferences**（AES256-GCM）に保存されます——開発用サーバー（`NODE_ENV` 未設定）は初回起動時にデフォルトの `admin`/`admin` アカウントを用意しますが、本番用サーバー（`NODE_ENV=production`）は代わりに `HYDRA_UMC_BOOTSTRAP_ADMIN_USERNAME`/`HYDRA_UMC_BOOTSTRAP_ADMIN_PASSWORD` を必須とし、`admin`/`admin` に黙って戻ることはありません — 本番構成の詳細は HYDRA-UMC-SERVER 自身の README を参照してください。追加の低権限**オペレーター**アカウントはいずれの場合もサーバー側の Config > Users から作成できます。
- **オフラインモードと状態キャッシュ**（`network/StateCache.kt`）—— **DataStore** を使用した統合永続化エンジン。本アプリは最後に確認されたシステム状態を自動的にキャッシュし、アクティブな Wi-Fi 接続がなくても即座にダッシュボードの閲覧や設定の確認ができます。
- **ミッション通知とアラート**（`util/NotificationHelper.kt`）—— アラートシステム。ロボットがジョブシーケンスを完了したとき、または重大なハードウェアイベントが発生したときに、高優先度のプッシュ通知を送信し、アプリがバックグラウンドにある場合でも操作員に確実に通知します。
- **産業用テレメトリターミナル**（`ui/TelemetryScreen.kt`）—— ターミナル風のインターフェースを備えた専用のリアルタイムログビューアー。システムイベント、REST/WebSocket 同期を追跡し、色分けされた診断情報を提供します（成功はマトリックスグリーン、エラーはインダストリアルレッド）。
- **高度なダッシュボード**（`ui/DashboardScreen.kt`）—— 遠近感のあるスワイプ効果を備えた高精細な **3D 水平カルーセル**。豊富なロボットのメタデータを表示します：**メーカー**（Source Robotics、Annin、Universal Robots、AgileX など）、**ロボットの役割**（CNC、レーザー、PnP）、そして CAM、XY、ATC、PNP、CNC、LSR、BED、VAC、RCK モジュールのライブ状態を示す**産業用モジュールマトリクス**。
- **システムヘルスモニター**（`ui/DashboardScreen.kt`）—— 接続された Compute Module 5 のリアルタイム指標。**ホスト名**、**整形された稼働時間**（例：「2日4時間15分」）、コントローラーとロボットのアクティブ数を含みます。
- **強化された手動制御**（`ui/ControlScreen.kt`）—— 最大の精度を実現する**50% 拡大されたジョイスティックボタン**を備えた、プロフェッショナルな縦型レイアウト。
- **再生パネル**（`ui/ControlScreen.kt`、`ui/PlaybackConsole.kt`）—— 赤い **STOP** ボタン（下記のグローバル STOP）と、**開始**、**一時停止**、**停止**（選択中のロボットのみ）ボタンを収めた固定の下部コントロールバー。これらのコントロールは常に表示され、物理的な感覚による確認のための**触覚フィードバック**を備えています。
- **グローバル STOP**（`ui/GlobalStop.kt`、`network/HydraApiClient.kt`、`viewmodel/RobotViewModel.kt`）—— すべての画面の上部バー、操作画面のコンソール、3D ビュー（縦向きとフルスクリーン）に赤い **STOP** ボタン（キャプション「全停止」、8 言語に翻訳済み）があります。1 回のタップで、長押しも確認も不要：すぐに `POST /api/stop-all` を送信し、他のアカウントが予約していても、すべてのロボットを停止します。結果（停止したロボット、停止しなかったロボット、他の人が予約しているロボット、サーバーが停止状態を保存できなかった場合の警告）を表示し、応答がなければ 1 回再試行し、サーバーが呼び出しを制限した場合（429）はロボットを 1 台ずつ停止し、次の STOP が成功するまで常時表示の **STOP 未確認** バナーを残します。これはサーバーのソフトウェア停止であり、ハードウェアの E-STOP ではなく、何の電源も遮断しません。
- **予約と安全上限**（`ui/AccessPanel.kt`、`network/AccessModels.kt`、`network/HydraApiClient.kt`、`viewmodel/RobotViewModel.kt`）- Control 画面は、選択したロボットを誰が保持しているか（誰も、この端末、別の端末の同じアカウント、別のアカウント）を表示し、**予約**と**解放**を提供し、サーバーのすべての予約の一覧（`GET /api/claims`）も表示します。端末はインストールごとにコントローラーIDを1つ保持し、予約・解放・ジョグのアーム・コマンドのたびにそれを送ります（本文と `X-Controller-Id` ヘッダー）。このため予約はアカウント全体ではなく、アカウントとこの端末に属します。サーバーの拒否は区別されます：別のアカウントが予約、あなたのアカウントが別の端末またはウィンドウで予約、コントローラーID必須、ジョグ未アーム。ロボットのモデルの上限は `GET /api/safety-limits` から読み取ります：上限を超えるジョグのステップは送信されず、サーバーの警告が表示されます。管理者が要求した場合は、ジョグでロボットを動かす前に**ジョグをアームする**（カウントダウン付き）を押す必要があり、アームは自動で、STOP で、または解放時に終了します。予約は任意です：誰も予約していないロボットはこれまでどおり操作できます。 **管理者**にのみ、他の人が予約しているロボットに **制御を引き継ぐ** ボタンが表示されます。確認を求めたうえで `force: true` でロボットを予約します (サーバーは管理者からのみ受け付け、ロールを再確認します)。 書き込みは条件付きです。各書き込みには、このアプリが最後に見たロボットのリビジョン(`If-Match`、`network/ContractV2.kt`)が付き、その間に他の人がロボットを変更していた場合、サーバーは 412 を返し、何も適用されず、ロボットが読み直されてメッセージで知らされます(標準は予約、テーブル、単発のコマンドを確認、厳格は STOP 以外すべてを確認、オフは何も確認せず、選択は予約一覧の中にあり、STOP は確認されません)。XY テーブルのジョグはアトミックな `table` コマンド 1 回で(古いサーバーには理解できるジョグを送ります)、サーバーは WebSocket で予約を通知するため Control 画面は通知が届く間ポーリングを止め、管理者は監査記録(`ui/AuditDialog.kt`)を、SHA-256 チェーンをサーバーと端末で 2 回検証した状態で読め、その行を `.jsonl` として共有できます。 PLAY は事前に確認します(ロボット、記録済みの点、速度、予約している人)。予約の欄でオフにすることもできます。
- **3D ビュー**（`ui/ThreeDScreen.kt`）—— ネイティブレンダラーではなく、HYDRA-UMC STUDIO 自身のリアルタイム 3D ビューポートを WebView に埋め込む方式です（`?hideUI=true&robotId=#token=`）。この WebView アプローチにより、実際に、現在提供されている STUDIO の 3D シーン（すべての実際のロボットメッシュ/運動学）をネイティブに再実装することなく無料で得られます——トレードオフは WebView のレンダリングオーバーヘッドで、バッテリー最適ではありませんが、今日の時点で機能的には完全です。
- **リアルタイム 8 系統ビジョン**（`ui/CameraScreen.kt`、`ui/MjpegPlayer.kt`）—— **ネイティブ MJPEG ストリーマー**。バックグラウンドパーサーと Canvas ベースのレンダラーによる低遅延の映像、ロボットのビジョンシステムがオフのときに（サイレントに空白のフィードを表示するのではなく）明確な「カメラ無効」状態を表示し、サーバーから直接ロボットのカメラをオン/オフする切り替えスイッチを備えています。手動制御画面での自動**ピクチャーインピクチャー（PIP）**オーバーレイをサポートし、サーバーのカメラ設定を通じて特定のロボットにマッピングされます。API 28 以降では、各フレームはプラットフォーム自身のハードウェアアクセラレーテッドな `ImageDecoder` 経路(GPU 常駐ビットマップ、CPU 側のデコード/アップロードなし)でデコードされ、元の CPU デコードへの自動的かつ自己修復的なフォールバックを備えています。
- **スマートディスカバリーと接続性**（`network/Discovery.kt`、`network/HydraApiClient.kt`、`network/HydraWebSocket.kt`）—— スマートフォン自身の /24 上のすべての候補ホスト（他のホストだけでなく、スマートフォン自身の LAN IP や localhost も含む）に対する並行サブネットスキャンで、`GET /api/hydra-info` を探査し、`remoteApiVersion` の存在のみによって実際のサーバーを識別します——これは手動 IP 入力パスが使用しているのと同じチェックであるため、所有者がデフォルトの製品文字列から名前を変更したサーバーでも見つけることができます。**NsdManager**（mDNS/Bonjour）リスナーがそれと並行して動作します——サーバーは実際に `_hydra._tcp`（`bonjour-service`）として自身を告知し、`MainActivity` はこれに必要なランタイムの位置情報/近くのデバイス権限を事前にリクエストします（マニフェストの宣言だけでは API 23 以上で決して自動的には付与されません）——ただし、Wi-Fi 上のマルチキャストの到達性はプレーンな HTTP プローブほど本質的に信頼性が高くないため、サブネットスキャンが主要な経路のままです。本アプリは起動時に自動的に Wi-Fi をアクティブ化し、ローカルの工場ネットワークをスキャンし、最初に利用可能な HYDRA-UMC サーバーへ**ゼロクリック自動接続**を実行します。
- **JWT アクセス**（`network/HydraApiClient.kt`、`ui/LoginScreen.kt`）—— **JWT（JSON Web Token）** を使ったログイン。すべての制御指令（ジョグ、再生、サーバーのグローバル停止）は、原子的な `POST /api/robot/:id/command` エンドポイント経由で送信される署名付きトークンを使ってサーバーによって検証されます（下記「原子的な指令同期」参照）——完全な `POST /api/settings` 書き込み（サーバー側で admin 専用）とは異なり、`admin` ロールでも `operator` ロールでも動作します。すべてのリクエストは `X-Hydra-Client: android` ヘッダーも運び、サーバー自身の Config > Remote Access タブが、SUITE/iOS とは独立してこのアプリを許可/ブロックできるようにします。安全なトークンの更新のために**生体認証**（指紋/顔）とシームレスに統合されています。コード `1008`（無効/期限切れトークン）でクローズされた WebSocket は、再接続ループでリトライされるのではなく、「再ログインが必要」として扱われます（`network/HydraWebSocket.kt`）。
- **原子的な指令同期**（`viewmodel/RobotViewModel.kt` 自身の `sendAtomicCommand()`）—— すべての書き込み（再生/一時停止/停止/ジョグ/ジョグ幅/バルブ/ポンプ/速度/ビジョン/ツール）は、設定ツリー全体ではなく、小さな単一ロボットの原子的な指令を送信します——サーバーは、他にどの統合ロボットが影響を受けるかを自ら計算し、ディスクに永続化し、他のすべての接続中クライアントへブロードキャストします。有効化/無効化は、再生/一時停止/停止と同じ方法でロボット自身の `combinedWith` 兄弟へ伝播します。これらはすべて同じ影響を受けるロボットの計算ロジックを共有しているためです。
- **グローバル STOP ウィジェット**（`widget/GlobalStopWidget.kt`）—— 素早くアクセスするための専用の**ホーム画面ウィジェット**。視認性が高く、すぐに使える**グローバル STOP** ボタンを提供します——上記と同じ呼び出しです：タップするとアプリが前面に出て、サーバー接続ができ次第すぐに停止を送信します（プロセスがまだ起動していない完全なコールドスタートでも同様）。サーバーに届かない場合は STOP 未確認を表示します。通知のアクションも同じ動作です。これはサーバーのソフトウェア停止であり、ハードウェアの E-STOP ではなく、何の電源も遮断しません。
- **触覚フィードバック**（`ui/ControlScreen.kt`）—— 高度な感覚フィードバックシステム。グローバル STOP はタップに即座の 2 連続振動で応えます。各ロボットの停止ボタンは**長押し保護**を維持します（素早いタップは短い振動とヒントのみで、本当に長押ししたときだけコマンドを送信）。区別された触覚シグネチャ（成功、エラー、緊急パルス）が、騒がしい環境でオペレーターに物理的な確認を提供します。
- **アプリ内アップデートチャンネル**（`update/GitHubReleaseUpdater.kt`、`update/ReleaseMetadataParser.kt`、`update/SemanticVersion.kt`）—— 起動時および「設定 → アップデート」から最新の安定版 GitHub Release を確認します。下書きでもプレリリースでもないタグの、正確な `HYDRA-UMC-ANDROID-CONTROL-release.apk` アセットのみをダウンロードし、自動インストールは一切行いません —— 最終的な同意は Android 自体のパッケージインストーラーが求めます。完全なリリース契約: [`docs/GITHUB_RELEASE_UPDATES.md`](docs/GITHUB_RELEASE_UPDATES.md)。
- **Bluetooth LE (GATT)**(`network/HydraBleClient.kt`、`network/BleContract.kt`、`network/AndroidBleGatt.kt`)- 文書化された規約([`docs/BLE_CONTRACT.md`](docs/BLE_CONTRACT.md))に従う本物のGATTクライアント。HYDRAサービスで絞り込むスキャン、MTU交渉付きの接続、サービスの確認、通知、双方向のフレーム分割メッセージ、書き込みは一度に1件、待ち時間を延ばしながらの自動再接続、Android 12以降の権限(`BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT`)、失敗ごとにユーザーの言語で表示する明確なメッセージ。**ペリフェラル側を実装したHYDRA-UMCデバイスはまだありません。** クライアントは模擬GATTに対してのみテストされており、Android側の実装はスマートフォンで実行されたことがなく、ロボットへのコマンドはBluetoothでは送信されません。
- **オプションのTLS**(`network/TlsSettings.kt`、`network/HydraHttp.kt`、`ui/SecuritySettings.kt`)- **設定 → セキュリティ**で「自動」(既定。従来どおりの動作)、「TLSを必須にする」、「暗号化なしのみ」を選べ、サーバー証明書のフィンガープリント(SHA-256)の固定や独自CAの信頼も設定できます。ローカルネットワーク外のホストへの暗号化されていないセッションでは、すべての画面の上に赤い警告が表示されます。埋め込みの3Dビューはまだこの規則に従いません。ローカルサーバーとの本物のハンドシェイクでテスト済みで、TLS付きの実際のHYDRA-UMCサーバーでは未検証です。
- **ローカルネットワーク外では非暗号化通信なし** (`network/TlsSettings.kt`, `PlainRemoteGuard`) - **設定 → セキュリティ → ローカルネットワーク外の非暗号化通信を許可** がオン(既定ではオフ)でない限り、ローカルネットワーク外のサーバーへの暗号化されていない HTTP/WS を拒否します。埋め込み 3D ビューはセッショントークンをクエリではなくアドレスのフラグメント(`#token=`)で受け取るため、サーバーやプロキシのログには記録されません。アプリデータの自動バックアップは無効です。
- **ペアリングされた Wear OS コンパニオン＆音声リレー**（`wear/WatchVoiceRelayService.kt`、`wear/WatchCompanionProtocol.kt`）—— ペアリングされた [HYDRA-UMC-WATCH](https://github.com/JuanenRac/HYDRA-UMC-WATCH) アプリからの、既に認識済みでユーザーが開始した音声ターンを、本アプリ自身の認証済み Server セッションを通じて `HYDRA-UMC-VOICE-UI` へ中継し、型付きの応答を Watch へ返します。このリレーも Watch のシステムステータスカードも、ロボットコマンドを発行したり `HydraState` を直接操作したりすることは一切ありません —— 動作に関連する応答は明示的に `requiresConfirmation` としてマークされ、プライマリの制御 UI を経由して確認する必要があります。完全な契約: [`docs/WATCH_VOICE_RELAY.md`](docs/WATCH_VOICE_RELAY.md)。
- **アプリ内音声アシスタント**(`ui/VoiceAssistantDialog.kt`)—— 上部バーのマイクアイコンをタップするとダイアログが開き、質問や指示を入力するか、この端末自体の音声認識(`RecognizerIntent`、Watch の音声ボタンで既に使われているのと同じ実際のパターン)を使用できます。認識されたテキストは、Watch リレーと同じ `POST /api/voice/turn` ルートを経由して送信されますが、リレーとしてではなくこの端末自身の通常トラフィック(`HydraApiClient.postVoiceTurn()`)として扱われます —— そのため設定 > リモートアクセスは、ペアリングされた Watch とは独立してこのボタンを許可/ブロックできます。音声がアップロードされることは一切なく、返信が直接ロボットコマンドを発行することもありません。
- **ツールチェーンとプロジェクト品質** —— AGP 9.3.1、Kotlin 2.2.10、Gradle 9.7.0、compileSdk 36、**JDK 21**（`compileOptions`、`gradle-daemon-jvm.properties`、そして `.idea/`/`.vscode/` のプロジェクトファイルもすべて、このドキュメントの記述だけでなく実際にそれをターゲットとしています）。警告ゼロのクリーンなビルド出力、最適化された R8 プロダクションバリアント、高度な **Roborazzi** スクリーンショットテスト。

**状態：Wi-Fi、Bluetooth、生体認証、通知が実装済み。** 本アプリは産業用オペレーターコンソールです。Bluetooth はまだ実験的で、どの画面も物理的な非常停止の代わりにはなりません。

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

APK は `app/build/outputs/apk/debug/app-debug.apk` に生成されます。`adb install -r -d app/build/outputs/apk/debug/app-debug.apk` でインストールするか、手動でデバイスに転送してください。リリースビルドには `assembleDebug` の代わりに `assembleRelease` を使用してください——`keystore.properties`（またはリリース署名用の環境変数）がある場合は非公開のリリースキーで署名し、ない場合はデバッグキーにフォールバックします。デバッグキーはテスト用には問題なくインストールできますが、配布してはいけません。

## 🔢 バージョン管理

このリポジトリはエコシステム全体の方針に従います：ビルドはバージョンを一切変更しません。`app/version.properties` には現在の `versionMajor`/`versionMinor`/`versionPatch`/`versionCode` が入っており、明示的な `bump` コマンドだけがそれらを上げます（`python tools/hydra_project_tool.py bump`：ネイティブバージョン、`hydra-umc.project.json` マニフェスト、`CHANGELOG.md` は `bump_manifest_version.py`、独立した常に単調増加の Android `versionCode` は `bump_version_code.py`）。`app/build.gradle.kts` は `version.properties` を*読む*だけで、書き込むことはありません。

- **Patch、オドメーター方式（10 進法）：** bump のたびに +1；9 を超えるとリセットされて 0 になり、代わりに minor が +1 されます——例：`0.0.9` -> `0.1.0`。Major は自動的には決して変更されません。
- **`versionCode`：** 単純な単調カウンター、bump のたびに +1、繰り上がりなし——Android は、これまでに出荷されたすべてのビルドにわたって厳密に増加することを要求します。

単純に `./gradlew assembleDebug`(上記の「手動ビルド」の方法で、加算スクリプトを先に実行しない場合)を実行しても、`version.properties` に最後にあった値をそのまま使うだけで、それ自体は何も加算しません。現在実行中のバージョンは **About** ダイアログでリアルタイムに確認できます（`BuildConfig.VERSION_NAME`、`version.properties` に最後に保持されていた `versionName` を読み取ります）。バージョン履歴は [CHANGELOG.md](CHANGELOG.md) を参照してください。

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
| ロボットが動かない | セッション切れ、別のアカウントによるロボットの予約、または軌道の再生中 | ステータスバーのメッセージを確認してください。軌道はサーバー自身が再生するため、ブラウザタブは不要です |
| Bluetooth が無効 | 物理チップがオフ | アプリ内の「システム Bluetooth を有効化」3D ボタンを使用してください |

## 📂 リポジトリ構成

```text
HYDRA-UMC-ANDROID-CONTROL/
├── app/
│   ├── build.gradle.kts          # アプリモジュールの Gradle 設定——AGP/Kotlin/Compose バージョン、依存関係、デバッグ署名の release ビルドタイプ
│   ├── version.properties        # versionMajor/Minor/Patch/Code (明示的な `bump` コマンドのみが加算)
│   ├── proguard-rules.pro        # release ビルド用のコード縮小/難読化ルール
│   └── src/main/
│       ├── AndroidManifest.xml   # 権限、activity/receiver 宣言、usesCleartextTraffic（LAN サーバーは TLS を有効にしない限りプレーン HTTP で、すべての呼び出しにサインイン済みトークンが必要）
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
│       │   │   ├── AccessModels.kt         # コントローラーID・予約・安全上限・ジョグ確認（純粋なルール）
│       │   │   ├── HydraApiClient.kt      # REST クライアント——ログイン、設定読み書き、原子的ロボット指令、システム指標
│       │   │   ├── HydraBleClient.kt      # Bluetooth GATT クライアント、Wi-Fi の代替トランスポート
│       │   │   ├── BleContract.kt / BleGatt.kt / AndroidBleGatt.kt   # BLE規約、GATT抽象化、そのAndroid実装
│       │   │   ├── TlsSettings.kt / HydraHttp.kt / TlsPrefs.kt      # オプションのTLS:モード、固定フィンガープリント、独自CA、保存
│       │   │   ├── HydraWebSocket.kt      # WS 経由のリアルタイム状態差分プッシュ、再接続処理
│       │   │   ├── StateCache.kt          # オフラインダッシュボード表示用の最後の既知状態キャッシュ（DataStore）
│       │   │   └── StopAllResult.kt       # グローバル STOP の結果（ロボットごとの応答、状態）
│       │   ├── ui/
│       │   │   ├── AboutDialog.kt          # アプリ/バージョン情報ダイアログ
│       │   │   ├── AccessPanel.kt          # Control 画面の予約・ジョグアームのパネル
│       │   │   ├── AuditDialog.kt           # サーバーの監査記録(管理者)とチェーン検証
│       │   │   ├── CameraScreen.kt         # ロボットごとの MJPEG カメラフィード + ビジョンオン/オフスイッチ
│       │   │   ├── ControlScreen.kt        # 手動ジョグ制御、長押し保護付き緊急停止/再生/一時停止/停止
│       │   │   ├── DashboardScreen.kt      # 3D カルーセルロボットピッカー + システムヘルス + モジュールマトリクス
│       │   │   ├── GlobalStop.kt           # グローバル STOP ボタンと結果バナー（1 タップで全ロボット）
│       │   │   ├── Joystick3D.kt           # 再利用可能な 2 軸ジョイスティックコンポーネント
│       │   │   ├── LoginScreen.kt          # ユーザー名/パスワード + IP/ポート入力、生体認証ログイン
│       │   │   ├── MjpegPlayer.kt          # MJPEG ストリームパーサー + Canvas レンダラー
│       │   │   ├── PlaybackConsole.kt      # 共有のフローティング停止/再生/一時停止/停止コンソール
│       │   │   ├── SettingsScreen.kt       # Wi-Fi/Bluetooth スキャン UI、接続設定
│       │   │   ├── SplashScreen.kt         # カスタム Compose スプラッシュスクリーン
│       │   │   ├── TelemetryScreen.kt      # ターミナル風のイベント/同期ログビューアー
│       │   │   ├── ThreeDScreen.kt         # 実際の 3D ビューポート——STUDIO 自身のヘッドレス 3D シーンを埋め込む WebView
│       │   │   ├── UserProfileDialog.kt    # プロフィール編集 + 生体認証切り替えダイアログ
│       │   │   ├── VoiceAssistantDialog.kt # HYDRA-UMC-VOICE-UI へのアプリ内マイク/テキストボタン
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
│       │   │   ├── BoundedRequestScope.kt     # requestId 単位の実行中ジョブスコープ。置き換えられた試行はキャンセルされ、サービス破棄時に cancelAll()
│       │   │   ├── WatchCompanionProtocol.kt    # Watch コンパニオンのバージョン状態ワイヤ契約
│       │   │   ├── WatchVoiceRelayContract.kt   # 認証付き Watch 音声リレーワイヤ契約
│       │   │   └── WatchVoiceRelayService.kt    # Wear OS 音声リレーサービス
│       │   └── widget/
│       │       └── GlobalStopWidget.kt # グローバル緊急停止を行うためのホーム画面ウィジェット
│       └── res/
│           ├── drawable/, layout/, mipmap*/, xml/   # アイコン、ウィジェットレイアウト、ランチャーアイコン、バックアップ/データ抽出ルール
│           └── values/, values-es/, values-de/, values-fr/, values-it/, values-zh/, values-ja/, values-pt/   # 8 言語の文字列、色、テーマ
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
├── README.md                     # 本ファイル
├── README_spa.md / README_ita.md / README_fra.md / README_deu.md / README_zho.md / README_jpn.md / README_por.md   # 翻訳
└── LICENSE                       # GPL-3.0-or-later
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
- **[HYDRA-UMC-BRIDGE-AMR](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-AMR)** — VDA 5050 MQTT パブリッシャーによる AGV/AMR フリートの調整境界(エミュレートしたフリートに対してのみテスト済み)。
- **[HYDRA-UMC-BRIDGE-CNC](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-CNC)** — GRBL ステータス/制御バイトへのアクセスを持つ、CNC セルの高レベルコーディネーター(エミュレーターに対してのみテスト済み)。
- **[HYDRA-UMC-BRIDGE-DROIDS](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-DROIDS)** — Boston Dynamics Spot コマンド送信機能を持つ、脚型/ヒューマノイドドロイドの調整境界(エミュレーターに対してのみテスト済み)。
- **[HYDRA-UMC-BRIDGE-LASER](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-LASER)** — キー/筐体/インターロックの GPIO 信号 3 系統を読み取るレーザーセルのコーディネーター(模擬チップに対してのみテスト済み。レーザーの武装や発射はできない)。
- **[HYDRA-UMC-BRIDGE-OPENPNP](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-OPENPNP)** — OpenPnP ピックアンドプレースの基板フローを安全に統括する高レベルコーディネーター。
- **[HYDRA-UMC-BRIDGE-PRINTER3D](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-PRINTER3D)** — 実際にゲート制御されたジョブコマンドを持つ、Moonraker/Klipper 3D プリンター向けの安全な調整境界。
- **[HYDRA-UMC-BRIDGE-ROS2](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-ROS2)** — 遅延インポートの rclpy ROS 2 トランスポートを持つコーディネーター(ROS 2 環境ではまだ実行していない)。
- **[HYDRA-UMC-BRIDGE-UAV](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-UAV)** — MAVLink コマンド送信機能を持つ、カメラ搭載 UAV の調整境界(エミュレーターに対してのみテスト済み)。

**直接関連**
- **[HYDRA-UMC-WATCH](https://github.com/JuanenRac/HYDRA-UMC-WATCH)** — 触覚アラートのパターンとペアリング済みスマートフォンへの音声リレーを備えた WearOS コンパニオンアプリ(物理的なウォッチではまだ実行していない)。本アプリの WearOS コンパニオンであり、手首からロボットの状態を一目で確認し制御できる。
- **[HYDRA-UMC-HIL-BRIDGE](https://github.com/JuanenRac/HYDRA-UMC-HIL-BRIDGE)** — シミュレーションと実機の間にソフトウェアの衝突インターロックを備えたハードウェア・イン・ザ・ループのルーター(実機なしでテスト済み)。本アプリから直接デジタルツインをリモート制御できるようにする。

**エコシステムの他のプロジェクト**

*コアハードウェア&プラットフォーム*
- **[HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC)** — ロボットアームのマザーボードの設計——CM5 ホスト + デュアルコア STM32H745、CAN-OTA/SPI-OTA 経由で最大 8 本のツールアームを統括することを目指す(ファームウェアは初期段階で、実装済みの基板はまだない)。
- **[HYDRA-UMC-OS](https://github.com/JuanenRac/HYDRA-UMC-OS)** — CM5 向けの再現可能な Raspberry Pi OS プロダクト層——読み取り専用エージェント、検証済み設定/プロファイル、WiFi 初回接続プロビジョニング。
- **[HYDRA-UMC-SDK](https://github.com/JuanenRac/HYDRA-UMC-SDK)** — すべてのブリッジが自身のコマンドを検証する共有 JSON-Schema 契約とソフトウェアのジョブゲート境界(認証済みの安全機能ではない)。
- **[HYDRA-UMC-CONNECTOR-HUB](https://github.com/JuanenRac/HYDRA-UMC-CONNECTOR-HUB)** — 外部マシン用コネクタのための宣言的アダプターマニフェストのレジストリとバリデーター。SDK 自身の契約という発想を外部マシンにまで拡張し、産業用ゲートウェイ系のプロジェクトを置き換えることはありません。

*コアバックエンド&クライアント*
- **[HYDRA-UMC-EDITOR-URDF](https://github.com/JuanenRac/HYDRA-UMC-EDITOR-URDF)** — 完成したモデルを STUDIO 自身のカタログへ送信するデスクトップ用グラフィカル URDF 作成/編集ツール。
- **[HYDRA-UMC-EDITOR-STL](https://github.com/JuanenRac/HYDRA-UMC-EDITOR-STL)** — HYDRA-UMC-EDITOR-URDF も編集する同じモデルカタログ内で、実在するパーツを変換/置換/削除/追加するデスクトップ STL モデルエディタ。

*URTC ツールプラットフォーム*
- **[URTC](https://github.com/JuanenRac/URTC)** — 物理的な Universal Robot Tool Controller 基板向けファームウェア、CAN バス経由の 25 以上のツールプロファイル。
- **[URTC-FLASHER](https://github.com/JuanenRac/URTC-FLASHER)** — URTC 基板用のデスクトップ GUI 書き込みツール、CAN-OTA およびフルチップ SWD/JTAG。
- **[URTC-TESTER](https://github.com/JuanenRac/URTC-TESTER)** — URTC 基板向けのデスクトップ CAN バスライブ診断ツール、ツールプロファイルごとに 1 パネル。
- **[URTC-WEB-STUDIO](https://github.com/JuanenRac/URTC-WEB-STUDIO)** — Web Serial API を使ったブラウザベースの URTC-TESTER の代替、ローカルインストール不要。
- **[URTC-GATEWAY](https://github.com/JuanenRac/URTC-GATEWAY)** — URTC の CAN バスの前に置くゲートウェイ：許可する識別子のリスト、レート制限、セッション鍵。コンピューター上でテスト済みで、実際のバスではまだ試していません。

*ビジョン AI ノード(Hailo-8)*
- **[HYDRA-UMC-VISION-NODE](https://github.com/JuanenRac/HYDRA-UMC-VISION-NODE)** — Hailo-8 ビジョンパイプラインの統合ハブ、段階ごとの実際のハードウェア準備状況チェック付き。
- **[HYDRA-UMC-DETECTION-HEF](https://github.com/JuanenRac/HYDRA-UMC-DETECTION-HEF)** — Hailo アーキテクチャ/チェックサムによる安全読み込み検証を備えた、実際のコンパイル済みモデルレジストリ。
- **[HYDRA-UMC-VISION-STREAMER](https://github.com/JuanenRac/HYDRA-UMC-VISION-STREAMER)** — 実際の HailoRT 統合境界を持つ、実際の GStreamer パイプライン + MediaMTX 設定生成器。
- **[HYDRA-UMC-VISUAL-SERVOING-API](https://github.com/JuanenRac/HYDRA-UMC-VISUAL-SERVOING-API)** — ゾーン状態を条件に承認する Position-Based Visual Servoing 補正則。カメラによる姿勢推定はまだない。
- **[HYDRA-UMC-SAFETY-ZONES](https://github.com/JuanenRac/HYDRA-UMC-SAFETY-ZONES)** — ファイルから物体位置を受け取る、ゾーン侵入・キャリブレーション鮮度・E-STOP 要求の v0 ロジック。検出器は接続されておらず、物理 E-STOP もまだ配線されていない。

*コグニティブ AI ノード(Hailo-10)*
- **[HYDRA-UMC-COGNITIVE-NODE](https://github.com/JuanenRac/HYDRA-UMC-COGNITIVE-NODE)** — Hailo-10 コグニティブパイプライン(LLM/VLA/音声オーケストレーション)の統合ハブ。
- **[HYDRA-UMC-VLA-ENGINE](https://github.com/JuanenRac/HYDRA-UMC-VLA-ENGINE)** — Vision-Language-Action モデル向けの、実際のアクショントークンのエンコード/デコードと軌道生成。
- **[HYDRA-UMC-VOICE-UI](https://github.com/JuanenRac/HYDRA-UMC-VOICE-UI)** — 確認ゲート付きの限定的な Watch リレーを備えた、実際の音声フロントエンド(VAD + 意図解析)。
- **[HYDRA-UMC-SEMANTIC-PLANNER](https://github.com/JuanenRac/HYDRA-UMC-SEMANTIC-PLANNER)** — MCU エラーコードに対する、実際のルールベースのタスク分解と意味的エラー復旧。
- **[HYDRA-UMC-DOCS-QA](https://github.com/JuanenRac/HYDRA-UMC-DOCS-QA)** — このエコシステム自身の Markdown ドキュメントに対する、標準ライブラリのみの実際の TF-IDF 文書検索。
- **[HYDRA-UMC-LOCAL-TECHNICIAN](https://github.com/JuanenRac/HYDRA-UMC-LOCAL-TECHNICIAN)** — エコシステム自身のための、ローカルでポリシーゲート付きのAIメンテナンス技術者 — 観察、診断し、修正を提案する;最上位の2つのリスクレベルは意図的にまだ実装されていない。

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
- **[HYDRA-UMC-OS-REBUILDER](https://github.com/JuanenRac/HYDRA-UMC-OS-REBUILDER)** — エコシステムの最新バージョンをプリロードした、書き込み可能なCM5イメージを構築するWindows/Linuxデスクトップツール。Raspberry Pi Imager方式の初回起動Wi-Fi/ユーザー/SSH設定を備える。
- **[HYDRA-UMC-OPS-AGENT](https://github.com/JuanenRac/HYDRA-UMC-OPS-AGENT)** — 保守インシデントコーディネーター: 低権限のエッジ役割がサニタイズされたインベントリ/ヘルスのスナップショットを収集し、コントロールプレーン役割がそれを読み取り専用でレンダリングして AI プロバイダーに診断の提案を依頼します - パッチを適用することも、何かをデプロイすることも決してありません。
- **[HYDRA-UMC-DEV-SERVER](https://github.com/JuanenRac/HYDRA-UMC-DEV-SERVER)** — 再現可能な開発ホスト（Raspberry Pi 5 / CM5）。エコシステムのソースコードを保管し、永続キューのもとで境界の明確なビルド/テストタスクを実行する。運用中の CM5 とは明確に異なる、開発専用の役割。

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

ソースコードは **GNU General Public License v3.0（GPL-3.0-or-later）**——[`LICENSE`](LICENSE) を参照してください。

本ドキュメント（本 README およびその自身の翻訳版——`README_spa.md`、`README_ita.md`、`README_fra.md`、`README_deu.md`、`README_zho.md`、`README_jpn.md`、`README_por.md`）は、**クリエイティブ・コモンズ 表示-継承 4.0 国際（CC BY-SA 4.0）** の下で提供されます。全文は https://creativecommons.org/licenses/by-sa/4.0/ を参照してください。
