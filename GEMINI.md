# OculusJJ — アーキテクチャ概要 & AI開発ガイド (GEMINI.md)

このファイルは、AIアシスタント（Antigravity および ブラウザ版Gemini）が **OculusJJ** プロジェクトの全体像・クラス構造・設計制約を正確に把握し、開発者と噛み合った会話・実装を行うためのシステムメモリ（プロジェクトルール）です。

---

## 1. プロジェクトの本質と目的

- **プロジェクト名**: **OculusJJ** (`com.oculus.jj`)
- **概要**: 獣医療（動物病院・獣医画像診断）向けに開発された、クロスプラットフォーム（Windows / macOS / Linux）対応のオープンソースDICOMビューワー＆3D/AI画像解析ワークステーション。
- **ベース設計（移植元）**: macOS用オープンソース医用画像ビューワー **Horos (`HOROS-20240407`)**（およびその前身である **OsiriX**）のUI・操作体系・データ構造・レンダリングパイプラインを、**Java 21 (Swing + OpenGL/LWJGL 3)** に忠実にポーティング＆拡張したもの。
- **ライセンス**: GNU Lesser General Public License v3.0 (LGPL-3.0)

---

## 2. ワークスペースのディレクトリ構造（最重要：混同注意！）

リポジトリルート直下には、**OculusJJ本体のコード**と、**移植・参照用のアップストリームソース**が同居しています。**編集対象は必ず `src/` 配下のみ**であり、参照用フォルダを本体コードと混同しないでください。

| パス | 役割 | AIへの注意事項 |
| :--- | :--- | :--- |
| **`src/main/java/com/oculus/jj/`** | **OculusJJ 本体ソースコード**（106 Javaファイル） | **実装・修正はここに対して行う。** |
| **`src/main/java/org/dcm4che3/opencv/`** | OpenCV Native ImageIO ブリッジ（19 Javaファイル） | `weasis-core-img` 4.12.2と連携してJPEG/JPEG2000/JPEG-LS圧縮DICOMをデコードするカスタム組み込みコード。 |
| **全体** | 本体・ネイティブブリッジ合計125 Javaファイル | ルート直下の参照用アップストリームソースとは別管理。 |
| **`src/main/resources/`** | リソースファイル | `i18n/messages_ja.properties`, `messages_en.properties`（日英辞書）、`native/`（OS別OpenCVネイティブライブラリ）。 |
| **`PORT_STATUS.md`** | **実装進捗・変更履歴のマスタードキュメント** | 何が実装済みで何が未実装か、直近でどう実装されたかの事実確認に最優先で参照すること。 |
| **`docs/`** | ポーティング設計書・差分分析 | `docs/PortingGuide/`（アーキテクチャ、DICOM、UI、MPR/CPR解析）、`docs/diff/gap-analysis.md`（ツールバー等のHoros比較）。 |
| `horos-20240407/` | **【参照専用】** 移植元 Horos の Objective-C / Cocoa ソース | 挙動の正解（仕様）を確認するためのリファレンス。ビルド対象外。 |
| `Weasis-4.6.5/` | **【参照専用】** Weasis の参照ソース | DICOMデコードや画像処理の参考用。ビルド対象外。 |
| `dcm4che-5.34.1/` | **【参照専用】** dcm4che の参照ソース | ライブラリの内部実装確認用。ビルド対象外。 |

---

## 3. 技術スタック・ビルド環境

- **言語 / ランタイム**: Java 21 (`sourceCompatibility = VERSION_21`)
- **UIフレームワーク**: Java Swing + SwingX (`swingx-all:1.6.5-1` — アウトラインツリーテーブル `JXTreeTable` 等で使用)
- **DICOM エンジン & ネットワーク探索**:
  - `org.dcm4che:dcm4che-core:5.34.1`, `dcm4che-image`, `dcm4che-imageio`, `dcm4che-imageio-rle`, `dcm4che-net`
  - `org.weasis.core:weasis-core-img:4.12.2` + OpenCVネイティブライブラリ（`opencv_java`）による圧縮DICOM高速デコード
  - `org.jmdns:jmdns:3.6.1` — Bonjour / Zeroconf (`_dicom._tcp.local.`) によるローカルネットワーク上のDICOMノード自動探索
- **3D / GPU レンダリング**:
  - **LWJGL 3.2.3** (`lwjgl`, `lwjgl-opengl`, `lwjgl-glfw`, `lwjgl-jawt`) + `lwjgl3-awt:0.1.8` (`AWTGLCanvas`)
  - OpenGL 3.3 GLSLシェーダーによるハードウェアボリュームレンダリング（GPU非対応環境向けに `CPURaycastRenderer` フォールバックあり）
- **データベース**: SQLite (`org.xerial:sqlite-jdbc:3.44.1.0`) — ORM（Hibernate等）は使わず、直接SQL/JDBCとモデルクラスで管理
- **設定・シリアライズ・AI通信**:
  - Jackson (`jackson-databind`, `jackson-dataformat-yaml`)、Java `Preferences` API
  - Java標準 `HttpClient` (`java.net.http`) + Jackson によるローカル優先LLM連携（Ollama `/api/chat` および OpenAI互換 `/chat/completions`）
- **必須JVM引数** (`build.gradle.kts` に定義済み):
  - `-Xmx24g`（大容量3D/MPRボリューム展開用）
  - `-Dsun.java2d.d3d=false`（**重要**: Java2DのDirect3DやOpenGLパイプラインを有効にするとLWJGLのOpenGLコンテキストと競合するため、GDIレンダリングに固定）
  - `--add-opens=java.desktop/javax.imageio.stream=ALL-UNNAMED` / `--add-opens=java.base/java.io=ALL-UNNAMED`（`NativeImageReader` のリフレクション用）
  - `--enable-native-access=ALL-UNNAMED`

---

## 4. アーキテクチャと主要クラスマッピング（Horos ↔ OculusJJ）

Horos (Objective-C) のクラス構造・メソッド名を可能な限り対応させて設計されています。会話の中で「Horosの〇〇機能」と言われた場合は、以下の対応クラスを参照してください。

### 4.1 起動・全体管理・設定
| 役割 | Horos (Obj-C) | OculusJJ (Java) | 説明 |
| :--- | :--- | :--- | :--- |
| エントリポイント | `main.m` / `AppController` | `OculusJJApp.java` | OpenCVネイティブ読込、ImageIO SPI登録、Swing起動、`BrowserController` 生成。 |
| メインメニュー | `MainMenu.xib` | `ui/MainMenuBar.java` | File / Edit / View / 2D Viewer / 3D Viewer / Window 等のメニューバー配線。 |
| 環境設定 | `PreferencesWindowController` | `ui/PreferencesController.java`<br>`config/AppPreferences.java` | サイドバー＋CardLayoutによる全16設定ペイン（AI Assistant含む）と設定値の永続化。 |
| 多言語対応 (i18n) | `Localizable.strings` | `utils/LanguageManager.java` | `messages_ja.properties` / `messages_en.properties` による日英切り替え。 |
| 通知・イベント | `NSNotificationCenter` | `ui/NotificationCenter.java` | コンポーネント間の疎結合イベント通知。 |
| バックグラウンド処理 | `ThreadsManager` | `threads/ThreadsManager.java` | 非同期タスク（DICOM読込、PACS通信等）の進捗管理とUI表示。 |
| CD/DVD自動検出 | `BrowserController` (Disc) | `DiscWatcher.java` | 光学ドライブへのDICOMメディア挿入監視（自動コピー / 表示 / 無視モード対応）。 |

### 4.2 データベース・ブラウザ（メイン画面）
| 役割 | Horos (Obj-C) | OculusJJ (Java) | 説明 |
| :--- | :--- | :--- | :--- |
| **データベースブラウザ司令塔** | **`BrowserController.m`** | **`ui/BrowserController.java`** | **最中核クラス（約9,000行）**。DB操作、インポート/エクスポート、アルバム管理、ビューア起動、2Dメニューアクション等を集約。 |
| ブラウザツールバー | `BrowserController` Toolbar | `ui/BrowserToolbar.java` | 検索、モダリティフィルタ、時間フィルタ、PACS、匿名化、CD焼録等のツールバー。 |
| 患者/検査アウトライン表 | `BrowserOutlineView` | `ui/DatabaseOutlineView.java`<br>`ui/DatabaseOutlineTreeModel.java` | Study → Series の階層ツリーテーブル表示（右クリック4Dメニュー等を含む）。 |
| アルバム・スマートアルバム | `DicomAlbum` | `model/DicomAlbum.java`<br>`model/AlbumRuleEngine.java`<br>`ui/AlbumTableView.java` | 通常アルバムと条件式（`$TODAY`等のマクロ対応）によるスマートアルバム管理。 |
| ローカルDB管理 | `DicomDatabase.m` | `model/DicomDatabase.java`<br>`repository/SqliteService.java`<br>`repository/LocalDicomRepository.java` | SQLiteによる `DicomStudy` → `DicomSeries` → `DicomImage` 階層の永続化。 |
| DICOMインポート/解析 | `DicomFile` / `DCMObject` | `model/DicomFileImporter.java`<br>`util/DicomMetadataParser.java`<br>`model/TransferSyntaxHandler.java`<br>`model/DicomSequenceParser.java` | ファイル検証、転送構文（圧縮/非圧縮）判定、ネストされたSequenceタグ解析、`DICOMDIR` 解析。 |

### 4.3 2D ビューワー・画像処理・ROI
| 役割 | Horos (Obj-C) | OculusJJ (Java) | 説明 |
| :--- | :--- | :--- | :--- |
| **2Dビューワーウィンドウ** | **`ViewerController.m`** | **`ui/ViewerController.java`** | 2Dビューアのフレーム管理、シリーズ切り替え、ウィンドウタイリング、3D/MPR起動。 |
| **ハンギングプロトコル** | `WindowLayoutManager.m`<br>`BrowserController.m` | **`ui/HangingProtocolManager.java`** | モダリティ・検査名に応じた画面分割（WindowsTiling / ImageTiling）、Series並び替え、過去検査比較（Comparative Studies / `previousSeriesMode`）の自動割り当て。 |
| **2D画像キャンバス** | **`DCMView.m`** | **`ui/DCMView.java`** | **描画・マウス操作の核**。WL/WW調整、Zoom/Pan/回転、1ビュー内グリッド表示（ImageTiling）、ROI描画・操作、クロスリファレンス線・Overflow Lines、オーバーレイ表示、ビューア間同期。 |
| **1スライス画素モデル** | **`DCMPix.m`** | **`model/DicomPix.java`** | **ピクセル演算の核**。16bit/float画素保持、WL/WW LUT変換、CLUT適用、3x3/5x5畳み込みフィルタ、サブトラクション（減算）、SUV換算、RGB/BW変換、セグメンテーションオーバーレイ保持。 |
| **ROI（関心領域・計測）** | **`ROI.m`** | **`model/ROI.java`** | 距離・角度・矩形・楕円・ポリゴン・ペンシル・ブラシROI。モルフォロジー膨張/収縮（Dilate/Erode）、グループ化、スライス間伝播・欠損補間、Undo/Redo (`AnnotationUndoManager`)。獣医心臓ROIとしてVHS（T4基準の長軸・短軸）／VLAS／M-VLAS（左房背側ドーム）にも対応し、六点・四点・五点のガイド付き入力と実寸換算表示を提供。 |
| 2Dビューアツールバー | `ViewerController` Toolbar | `ui/ToolbarPanel.java`<br>`ui/ToolbarPanelController.java` | 2Dビューア上部のツール切替、WL/WWプリセット、CLUT、Fusion、Shutter等。 |
| 画像フュージョン | Fusion Blending | `model/FusionImageProcessor.java`<br>`ui/FusionControlsPanel.java` | PET-CT / MRI-CT 等の重ね合わせ（ブレンド率調整・フラット化焼き込み）。 |
| ナビゲーター / 3D位置 | `NavigatorWindowController`<br>`ThreeDPositionController` | `ui/NavigatorWindow.java`<br>`ui/NavigatorPanel.java`<br>`ui/ThreeDPositionWindow.java` | 全スライスのサムネイルストリップ表示・ジャンプ、および3D座標同期パネル。 |

### 4.4 3D / MPR / CPR / AI・領域成長解析
| 役割 | Horos (Obj-C) | OculusJJ (Java) | 説明 |
| :--- | :--- | :--- | :--- |
| **3Dビューワーコントローラ** | `VRController.mm`<br>`MIPController.m` | **`ui/Viewer3DController.java`** | 3D表示ウィンドウ管理、ツールバー、GPU/CPU切替、VRAM警告、3D Analysisダイアログ。 |
| **OpenGL 3Dレンダラー** | `VRView.mm` (VTK) | **`ui/GLViewer3D.java`** | **VTKを使わずLWJGL 3 (OpenGL 3.3) で独自実装**。3Dテクスチャ＋GLSLレイマーチングによる **VR / MIP / Isosurface** 描画、Phong照明、16-bit `TransferFunction`、Orientation Cube、ステレオ表示、Mipmap・VRAM常駐最適化。 |
| CPUフォールバック3D | — | `ui/CPURaycastRenderer.java` | OpenGLコンテキスト作成失敗時等に自動で切り替わる純Javaマルチスレッドレイキャスター。 |
| **直交MPR（多断面再構成）** | `MPRController.m`<br>`MPRView.m` | **`ui/MPRController.java`**<br>**`ui/MPRView.java`** | Axial / Coronal / Sagittal の3断面直交再構成、クロスヘア連動、Thick Slab（MIP/Mean/MinIP）。 |
| **Curved MPR (CPR)** | `CPRController.m`<br>`CPRView.m` | **`ui/CPRController.java`**<br>`ui/CPRView.java`<br>`ui/CPROrthogonalView.java`<br>`ui/CPRTransverseView.java` | 血管や消化管などに沿った曲面再構成（パス描画、Stretched/Straightened CPR、直交断面表示）。 |
| **3D領域成長・セグメンテーション** | `ITKSegmentation3D.mm` | **`model/SegmentationEngine.java`** | ITKに依存せず純Javaで実装した3D Region Growing（Threshold区間 / Neighborhood / Confidence倍率）＋平滑化＋HU値書き換え（Set Pixel Values）＋2Dビューへの半透明オーバーレイ連動。 |

### 4.5 PACS ネットワーク通信・レポート出力・LLM草稿支援
| 役割 | Horos (Obj-C) | OculusJJ (Java) | 説明 |
| :--- | :--- | :--- | :--- |
| PACS Query / Retrieve UI | `QueryController.m` | `ui/QueryController.java`<br>`ui/PACSQuerySettingsDialog.java` | PACS検索ウィンドウ、Auto-Query（定期自動検索）、リモートStudyのローカルDB登録、C-MOVE取得。 |
| DICOM通信サービス | `DCMNetService` | `model/DicomService.java`<br>`model/DicomStoreSCP.java`<br>`model/DicomNodeManager.java` | `dcm4che-net` を用いた C-ECHO, C-FIND, C-MOVE SCU、C-STORE SCU（送信）および C-STORE SCP（バックグラウンド受信サーバー）。 |
| Bonjour ノード探索 | `BonjourBrowser.m` | `ui/BonjourBrowser.java` | `JmDNS` (`_dicom._tcp.local.`) によるDICOMノードの自動検出・TXTレコード解析・ブラウザ画面のソース一覧連携。 |
| 内蔵Webサーバー | `WebPortal` | `model/WebServerService.java` | HTTP経由でのStudy/Series一覧閲覧・DICOM配信サービス。 |
| レポート・Key Image出力 | `DicomReport` / Key Image | `model/DicomReport.java`<br>`BrowserController` 内メソッド | HTMLレポート生成、PDF出力、DICOM SR (`1.2.840.10008.5.1.4.1.1.88.33`) 変換出力、Key Image の DICOM Secondary Capture (`1.2.840.10008.5.1.4.1.1.7`) シリーズ出力。 |
| ローカル優先LLM草稿支援 | — (OculusJJ独自拡張) | `ai/AITextClient.java`<br>`ui/AIReportDraftDialog.java` | ローカルOllama（既定 `http://localhost:11434`）およびOpenAI互換API（HTTPS必須）を用いた手入力所見メモからのレポート草稿生成支援。 |

---

## 5. AI（Gemini）が絶対に守るべき設計ルールと「罠」の回避

会話やコード生成で話が噛み合わなくなるのを防ぐため、以下のプロジェクト固有の前提を厳守してください。

1. **「VTK / ITK / Cocoa」を前提にしないこと**:
   - 移植元のHorosは Objective-C + Cocoa + VTK + ITK で書かれていますが、OculusJJは **Java Swing + LWJGL 3 (OpenGL 3.3) + 純Javaアルゴリズム + dcm4che + OpenCV (画像デコードのみ)** で構築されています。「VTKのラッパーを呼ぶ」といった提案はNGです。
2. **dcm4che のバージョンとAPIに注意 (`5.34.1`)**:
   - 古い `dcm4che2`（`DicomObject` 等）と、現在使用している `dcm4che3/5`（`org.dcm4che3.data.Attributes`, `Tag`, `VR`）を絶対に混同しないでください。
   - また、ローカル構成の都合上、一部のユーティリティクラスに頼らず `Attributes` に生タグ（`0x00080016` 等）と `VR` を指定する実装パターン（`BrowserController.writeSCImage` 等）が採用されています。
3. **dcm4che `Association` のクローズ漏れ厳禁**:
   - `org.dcm4che3.net.Association` は `AutoCloseable` ではありません。PACS通信コードを書く際は、必ず `finally` ブロックで `assoc.release()` と `assoc.waitForSocketClose()` を呼んでください。
4. **ビューア同期の無限ループ防止 (`avoidRecursiveSync`)**:
   - 複数ビューア間でスライス位置やWL/WWを同期する際、A→B→Aのイベント無限ループによる `StackOverflowError` を防ぐため、必ず再帰防止ガードフラグを通す設計にしてください。
5. **物理座標（`Pixel Spacing`）の考慮**:
   - CT以外のモダリティ（CR, DR, US等）ではピクセルサイズや縦横比が異なります。計測や描画スケール計算では、必ず DICOM の `Pixel Spacing (0028,0030)` を考慮した実空間座標を使ってください。
6. **国際化（i18n）の徹底**:
   - UIに新しいボタン・メニュー・ダイアログメッセージを追加する際は、ハードコードせず `LanguageManager` を通し、`src/main/resources/i18n/messages_ja.properties` と `messages_en.properties` の**両方**にキーを追加してください。
7. **巨大クラスの編集時の注意**:
   - `BrowserController.java`（約9,000行）、`DCMView.java`、`GLViewer3D.java`、`ViewerController.java`、`DicomPix.java` は非常に大きなファイルです。修正時は該当箇所の周辺を正確に読み取り、既存のメソッド配線やフィールドを壊さないよう最小限の差分で編集してください。
8. **AI / LLM 機能のセキュリティ制約と画像解析機能との区別**:
   - **アルゴリズムの区別**: 既存の `Grow Region` / `Set Pixel Values`（`SegmentationEngine.java`）は閾値・近傍条件に基づく対話的3D領域成長アルゴリズム（純Java実装）であり、機械学習（ML）推論や外部LLM連携ではありません。LLMレポート草稿支援（`AITextClient` / `AIReportDraftDialog`）と混同しないでください。
   - **プライバシーと通信・認証の原則**: LLM草稿支援はローカルOllama接続を既定とし、外部クラウド（OpenAI互換）接続時は **HTTPS通信のみ許可（非TLSの外部HTTPは拒否）** します。また、**APIキーは設定ファイルやXMLに保存せず環境変数 `OCULUSJJ_LLM_API_KEY` からのみ取得**し、**DICOMメタデータや患者画像は自動添付しない**（手入力メモのみを送信対象とし、クラウド送信時は毎回送信先と内容の確認を行う）設計を厳守してください。

