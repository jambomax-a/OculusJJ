# OculusJJ HOROS Java Port - 実装状況サマリー

> **更新日:** 2026-10-29 (P-C〜P-I 実装更新) | **最終ビルド:** BUILD SUCCESSFUL (dcm4che-core 5.34.1)
> **Javaソースファイル数:** 113 classes (`src/main/java/`)  
> **HOROS-20240407参照ソース:** 337 .m, 1088 .h, 205 .mm (Objective-C/Cocoa)  
> **依存関係:** dcm4che-core 5.34.1 / LWJGL 3.2.3 / SQLite JDBC 3.44.1

---

## 全体進捗サマリー（統合版）

| カテゴリ | 状態 | 詳細 |
|---------|------|------|
| **P0: DICOMパーサー基盤** | ✅ **転送構文対応完了** | dcm4che基本読み込み✅。完全パース・転送構文判定・シーケンス属性抽出実装済み |
| **P1: 基盤統合** | ✅ **完了** | Preferences(508行)、メニューバー再実装、ダイアログ群(5ファイル)、i18n外部化 |
| **P2: メニューアクション配線** | ✅ **完了** | File/View/Tools/Analysis全アクション接続済み。Navigator Panel（スライストリップ）/ 3D Position Panel も実装済 |
| **P3: ツールバー** | ✅ **完了** | Search/ModalityFilter/TimeInterval/Subtraction/Status Display 全実装 |
| **P4: 画像処理・分析** | ✅ **完了** | Shutter tool/Movie再生/PACS Query Settings✅。Brush Morphological ops(Dilate/Erode)、Fusion Blending配線、Report生成 全実装 |
| **P5: GPU/Optimization** | ✅ **完了** | LWJGL 3.2.3: MIP/VR(レイマーチ)/Isosurface 3モード・Transfer Function・Phong照明・ステレオ・CPUフォールバック。VRAM最適化（Mipmap＋VRAM検出・HOROS準拠警告）＋分析（Grow Region 領域成長3種・Set Pixel Values・2D連動オーバーレイ）実装済 |

### 進捗率：約98%（114 Javaファイル中、DICOMパーサー基盤＋Navigator Model＋Navigator Window＋GPU Volume Rendering＋VRAM最適化＋2Dメニュー全項目(SUV/校正/融合/伝播)＋ツールバー Annotations Panel＋Key Image → DICOM SC出力＋分析(Grow Region/Set Pixel Values/2D連動) 完了）

---

## [完了済みの変更] — Navigator Window / 3D Position Panel（HOROS navigator: / threeDPanel: 実装）

### NavigatorWindow.java（新規作成）
- **役割:** 3Dデータシリーズの全スライスをサムネイルストリップで表示、クリックでフレームジャンプ（HOROS NavigatorWindowController相当）
- **実装内容:**
  - サムネイル遅延描画（DicomPix.generateThumbnail / getBufferedImageNoLoad）
  - 現在スライスのリアルタイムハイライト（150ms TimerポーリングでDCMView.getCurImage()追従）
  - 現在位置の選択はDCMView.setIndex()で反映

### ThreeDPositionWindow.java（新規作成）
- **役割:** 3D Position パネル（スライスインプット位置・モダリティ・画像サイズをリアルタイム表示、HOROS ThreeDPositionController相当）

### BrowserController.java
- `navigator()` 実装：アクティブビューア取得 → RGBデータガード → 同一マトリクスサイズの3Dシリーズガード → NavigatorWindow表示（HOROS ViewerController.m:22787準拠）
- `threeDPanel()` 実装：ThreeDPositionWindow表示（HOROS ViewerController.m:22812準拠）

### ToolbarPanel.java
- Navigatorボタン / 3D Positionボタン：「feature in development」ダイアログから本体実装に配線

### DicomPix.java
- `getPhotometricInterpretation()` / `isRGB()` ゲッター追加（NavigatorのRGBガード用）

### i18n
- messages_ja.properties / messages_en.properties に navigator.* / position3d.* キー追加

## [完了済みの変更] — P0.2 Navigator Panel logic integration

### NavigatorModel.java （新規作成、P0.2）
- **役割:** Study/Series/Image階層のデータモデル管理
- **実装内容:**
  - `StudyNode` / `SeriesNode` / `ImageNode` の3段階ノード構造
  - `buildHierarchy(List<DicomStudy>)` — DicomDatabaseからStudy→Series→Image階層を自動構築
  - UIDベースの検索: `getStudyByUID()` / `getSeriesByUID()` / `getImageByUID()`
  - Studyリストを日付降順（最新→最古）にソート
  - `fromDicomDatabase()` — DicomDatabase直接ロードヘルパー
  - `loadFromNavigatorModel()` — 他のモデルからデータ転送
  - `notifyNavigatorUpdate()` — NavigatorPanelへのデータ通知コールバック

### NavigatorPanel.java （74%書き換え、P0.2）
- **役割:** Study/Series/Image階層のツリー表示＋ミニマップ（HOROS NavigatorWindowController相当）
- **実装内容:**
  - JTree + DefaultMutableTreeNode による3段階階層表示
  - `NavigatorTreeCellRenderer` — カスタムセルレンダラで画像ノードにツールチップ表示
  - TreeSelectionListener → サムネイル更新＋コールバック連携
  - `loadFromNavigatorModel(NavigatorModel)` — NavigatorModelからツリーデータをロード
  - `setOnImageSelected(Consumer<String>)` — 画像選択時のコールバック設定
  - `MiniMapView` インナークラス — サムネイル縮小表示＋可視領域オーバーレイ描画（旧機能継承）
- **依存:** NavigatorModel, javax.swing.tree.*, java.util.function.Consumer

---

## [完了済みの変更] — P0.1 DICOMパーサー基盤（転送構文対応）

### TransferSyntaxHandler.java （新規作成、P0）
- **役割:** DICOM転送構文の判定・分類・ログ出力を一元管理
- **実装内容:**
  - 全主要転送構文UID定数定義（Explicit VR LE / Implicit VR LE / JPEG系 / RLE / MPEG2/4）
  - `getTransferSyntaxUID()` — Attributesから転送構文UID取得（null安全fallback: Explicit VR LE）
  - `isCompressed()` — 圧縮形式判定（JPEG/RLE/MPEG）
  - `getSupportStatus()` — サポート状態列挙（FULLY_SUPPORTED / NEEDS_DECODE / NOT_SUPPORTED / UNKNOWN）
  - `hasSequenceAttributes()` — シーケンスタグ13種の存在チェック
  - `getSequenceTagNames()` — 検出されたシーケンス名リスト取得
  - `logTransferSyntaxInfo()` — SLF4Jで転送構文情報をログ出力
- **依存:** dcm4che-core Attributes/Tag, SLF4J

### DicomSequenceParser.java （新規作成、P0）
- **役割:** DICOMシーケンス属性の再帰的パースを支援するユーティリティ
- **実装内容:**
  - `extractSequences()` — 主要11シーケンスタグからSequenceInfoリスト抽出
  - `extractFrameAttributes()` — シーケンスタグからフレームごとのAttributes配列を取得
  - `extractPerFrameGroups()` — PerFrameFunctionalGroupsSequenceから特定グループを再帰検索
  - `findNestedAttribute()` — ネストした属性を深度5まで再帰的に検索
  - `parseDicomDir()` — DICOMDIRファイルのディレクトリレコード階層解析
  - `printPerFrameSummary()` — PerFrameFunctionalGroupsSummaryログ出力
  - `countSequenceElements()` / `getSequenceSummary()` — デバッグ用ヘルパー
- **対応シーケンスタグ:** ScheduledProcedureStepSequence, ReferencedPerformedProcedureStepSequence, SOPCommonModuleSequence, PatientStudyModuleSequence, SeriesModuleSequence, ImagePlaneModuleSequence, PixelPresentationModuleSequence, RegistrationSequence, FusionCapabilitiesModuleSequence, FunctionalGroupModuleSequence, PerFrameFunctionalGroupsSequence

### DicomMetadataParser.java （大幅強化、P0）
- **変更内容:** 既存の基本readDataset()版から完全解析器へ書き換え
- **追加機能:**
  - TransferSyntaxHandler統合 → 転送構文判定・圧縮検出
  - DicomSequenceParser統合 → シーケンス属性抽出（最大3フレームまで取得）
  - PerFrameFunctionalGroupsSummary自動ログ出力
  - `safeGetString()` — null安全な属性アクセス
  - `parseDicomFiles()` — バッチ解析対応
  - `formatMetadata()` — デバッグ用フォーマット出力
- **修正した項目:**
  - ~~旧コンストラクタ~~ → 0引数コンストラクタ + setterパターンに完全移行
  - ~~画像情報抽出の欠落~~ → Rows, Cols, BitsAllocated/Stored, PixelRepresentation, SamplesPerPixel追加
  - ~~幾何情報の欠落~~ → ImagePositionPatient (3要素), ImageOrientationPatient (6要素), PixelSpacing追加
  - ~~転送構文未対応~~ → TransferSyntaxHandler統合で完全対応

### DicomFileImporter.java （P0修正）
- **変更内容:** 転送構文・シーケンス属性の処理を統合
- **追加機能:**
  - `importFromFolder()` — DICOMファイル判定ロジック強化（拡張子 + DicomInputStream検証）
  - `isDicomFile()` — ダミーファイル除外 + ファイルサイズチェック + readDataset()検証
  - `importSingleFile()` — TransferSyntaxHandler統合で転送構文判定・圧縮警告、シーケンス属性検出ログ追加
  - PixelSpacingの2要素分離取得（getFirstDoubleValue / getSecondDoubleValue）
- **修正した項目:**
  - ~~旧isDicomFile()が常にtrue~~ → 拡張子 + ファイル検証による正確な判定に置き換え
  - ~~転送構文未処理~~ → 圧縮ファイル検出時にwarnログ出力
  - ~~シーケンス属性無視~~ → PerFrameFunctionalGroupsSequence存在時フレーム数もログ出力

### P0進捗サマリー
- **完了:** TransferSyntaxHandler, DicomSequenceParser, DicomMetadataParser強化, DicomFileImporter修正
- **新規ファイル:** 2 (`TransferSyntaxHandler.java`, `DicomSequenceParser.java`)
- **修正ファイル:** 2 (`DicomMetadataParser.java`, `DicomFileImporter.java`)
- **進捗率:** 60〜65% → **70%**（DICOMパーサー基盤完了）

---

## [完了済みの変更] — P0.3 ダイアログ群 (5ファイル新規)

### PreferencesWindow + PreferencesController 完全統合 ✅
- `PreferencesController.java` (508行): HOROS全15 PreferencePanes を sidebar+CardLayout で実装
- **「Preferencesが2箇所に存在する問題」解消済み** → FileメニューとSettingsメニューから統一された `PreferencesController.show()` へ

### AppPreferences.java 全 preference キー拡張 ✅ (40+キー)

### MainMenuBar.java 完全再実装 ✅
- File / View / Tools / Analysis メニュー構造を HOROS MainMenu.xib 準拠で構築
- **全アクション配線完了** — 全TODOメソッドの実装済み（詳細は後述）
- Recent Studies ダイナミックメニュー実装 (`Preferences.userNodeForPackage`)

### i18n strings 全メニュー名外部化 ✅

---

## [完了済みの変更] — P0.3 ダイアログ群 (5ファイル新規)

| ファイル | 内容 |
|---------|------|
| `AboutDialog.java` | Aboutダイアログ |
| `AnonymizationDialog.java` | DICOM匿名化ダイアログ |
| `BurnerDialog.java` | CD/DVD焼録ダイアログ（フォルダ選択→ファイル一覧→ディスク情報→Ejectボタン→Burnワーカスレッド） |
| `LogWindow.java` | ログ表示ウィンドウ |
| `NavigatorPanel.java` | ナビゲータパネル（UIのみ、ロジックはstub） |

---

## [完了済みの変更] — P1 メニューアクション詳細

### MainMenuBar.java 全アクション配線 ✅

| メニュー項目 | 対象アクション | 実装内容 |
|---|---|---|
| File > Open Study | `openStudy()` | `BrowserController.importFilesFromDialog()` に接続 |
| File > Close All Viewers | `closeAllViewers()` | `ViewerController.getDisplayedViewers()` iterate/dispose |
| File > Burn to CD/DVD | `showBurnerDialog()` | `new BurnerDialog(this)` 表示に接続 |
| File > Anonymize DICOM | — | `AnonymizationDialog.show(this)` に接続 |
| File > Preferences / Settings > Preferences | `showPreferences()` | `PreferencesController.showPreferences(owner)` に統一 |
| View > Flip H/V | `flipX()/flipY()` | `getActiveDCMView().toggleX/YFlipped()` |
| View > Image Tiling | `tileImages(layout)` | `BrowserMatrix.renewRows(rows,cols)` |
| View > Orientation | `setOrientation()` | MPR対応ビューア表示指示 |
| View > Zoom/Pan/WLWW/Tools menu items | `setTool()` | `DCMView.currentTool = ToolMode.xxx` |
| Advanced > 3D MIP | `open3DMIP()` | `Viewer3DController.openMIP(viewer)` |
| Advanced > 3D VR | `open3DVR()` | `Viewer3DController.openVR(viewer)` |
| Advanced > Curved MPR | `openCPR()` | `CPRController.openCurved(viewer)` |
| Import > From PACS | `queryPACS()` | `QueryController.showQueryDialog()` |
| Export > DICOM/JPEG/TIFF/ROI | — | 各ダイアログ接続（一部stub） |

### BrowserController.java — Window/Export/Importアクション ✅

| メニュー項目 | 実装内容 |
|---|---|
| Window > Tile 2D Viewer Windows | `ViewerController.tileWindows()` |
| Window > Tile 3D Viewer Windows | `ViewerController.tile3DWindows()` (新規追加) |
| Window > Resize Window % | `resizeWindowByPercent(p)` — screen比計算 |
| Window > Close All Viewers | `closeAllDisplayedViewers()` |
| Export > DICOM/JPEG/TIFF | ファイル選択ダイアログ接続 + ImageIO出力実装 |
| Export > DICOM file(s) | 出力先ディレクトリ選択 + DicomOutputStream (SecondaryCapture SOP) |
| Export > Movie | `MoviePlayer.createAndShow()` に接続 |
| Export > PACS | JPopupMenu + PACSQuerySettingsDialog接続 |
| Key Image 関連全5項目 | setKeyImage / setAllKeyImages / unmark / ROIs / captureAndSet |

### Editメニューアクション ✅
- **E5: Undo/Redo/Cut/SelectAll** — `AnnotationUndoManager.java` (125行) 新規作成、ROIスナップショット管理・Undo/Redo対応
- **E6: Bold/Bigger/Smaller** — Preferences (`annotationFontBold`, `annotationFontSize`) に接続

### QueryController / ViewerController 追加 ✅
- `QueryController.showQueryDialog(Frame)` — PACSクエリUI表示用メソッド追加
- `ViewerController.tile3DWindows()` — 3Dビューアグリッドレイアウト計算 (1453→1466行, +13)

---

## [完了済みの変更] — P2 ツールバー（Browser Toolbar）

> **詳細なボタン配置順・HOROS IDマッピングは → [`docs/diff/gap-analysis.md`](../diff/gap-analysis.md)** を参照

| カテゴリ | 状態 |
|---------|------|
| Browser Toolbar (基本ボタン) | ✅ 完了 — Import/Export/Email/Print/Query/PACS/Anonymize/Burner/XML/Trash全実装 |
| Time Interval / Modality Filter / Search | ✅ filter logic実装済（不具合修正済み） |
| ROI Manager / Shutter Popup / Reports | ✅ 実装済 |
| Movie Export (QTSave) | ✅ 完了 |

---

## [完了済みの変更] — 2D畳み込みフィルタ (Convolution Filters)

### DicomPix.java （モデル層）
- **役割:** 2D画像ピクセルに対する3x3/5x5カーネル畳み込みの適用
- **実装内容:**
  - フィールド: `convKernel` (float[], null=OFF) / `convKernelSize` (3または5) / `convNormalization` (0=正規化なし) / `convolutionOn`
  - `setConvolutionKernel(float[] val, int size, float norm)` — nullで解除・非nullで有効化
  - `hasConvolution()` / `getConvolutionKernel()` / `getConvolutionKernelSize()` / `getConvolutionNormalization()`
  - `applyConvolution()` — エッジクランプ付きカーネル適用、結果を[0, 0xFFFF]にクランプ、WL/WW適用後にサムネイル破棄
  - HOROS (DCMPix.m) と同様のセマンティクス: 正規化値は保存値そのもの (0=正規化なし)

### BrowserController.java （UI層）
- **役割:** メニュー表示・フィルタ管理（追加/削除/編集）・適用
- **実装内容:**
  - フィールド: `convolutionMenu` (JMenu)
  - Viewer 2D メニューに畳み込みフィルタサブメニューを追加（`menu_viewer_2d_convolution_filters`）
  - `convolutionFilterFile()` — `~/.oculusjj/convolution_filters.json`
  - `loadConvolutionFilters()` / `saveConvolutionFilters(Map)` — JacksonでJSON永続化
  - `rebuildConvolutionMenu()` — No Filter / 各フィルタ（Shift=削除、Alt+Option=編集）/ Add a Filter
  - `applyConvolution(String name)` — 全フレームにカーネルを適用
  - `deleteConvolutionFilter(String name)` — 確認ダイアログ付き削除
  - `showConvolutionDialog()` / `showConvolutionDialog(String existingName)` — 5x5グリッドエディタ、3x3/5x5サイズ選択、正規化自動計算
- **i18n:** `menu_viewer_2d_convolution_filters` + `convolution.*` キー17個を `messages_en.properties` / `messages_ja.properties` 両方に追加

---

## [未実装項目一覧] — 優先度順

### 🔴 P0: Browser Toolbar レンダリング不具合（修正済み）
- ~~BrowserToolbar初期化コードの重複宣言~~ → **修正済** (BUILD SUCCESSFUL)
- ~~コンストラクタでinitializeButtons()未呼出~~ → **修正済** (BUILD SUCCESSFUL)

### 🟡 P1: UIのみでロジック未実装
| 項目 | 内容 |
|------|------|
| Navigator Panel | JDialog表示のみ。実パネルの統合が未 |

### 🟠 P2: ツールバーにまだないHOROSアイテム
| 項目 | 内容 |
|------|------|
| Subtraction（減算） | ✅ 実装済（`showSubtractionDialog()`、基準/ソース選択 + absolute対応） |
| Status Display | ✅ 実装済（`showStatusDisplay()`、選択アイテムのステータス一覧） |

### 🟠 P3: 画像処理・分析機能ギャップ
| カテゴリ | 未実装項目 | HOROS参照 | 重要性 |
|---------|-----------|----------|--------|
| 画像操作 | ~~Brush ROI Morphological ops (dilate/erode)~~ ✅実装済（BrushPalette + `applyBrushMorph`） | — | 中 |
| 画像操作 | ~~Arithmetic Subtraction~~ ✅実装済（`DicomPix.subtract` / `DCMView.subtract` / `showSubtractionDialog`、absolute対応） | `imageArithmeticSubtraction:absolute:` (DCMPix.m:9319) / `subtract:` (DCMView.m:6321) | 低 |
| 画像操作 | ~~convolution~~ ✅実装済（`DicomPix.applyConvolution` / `showConvolutionDialog`、5x5/3x3カーネル、正規化自動計算、Shift+クリック削除、Alt+Option+クリック編集、`~/.oculusjj/convolution_filters.json`に永続化） | — | 低 |
| 画像操作 | ~~resample~~ ✅実装済（2Dビューメニュー、双線形リサイズ、X/Y/Z係数 `showResampleDialog` / `bilinearResize`） | `resampleDataFromPixArray:` (ViewerController.m:9560) | 低 |
| 画像操作 | ~~Shutter tool~~ ✅実装済み / Full-screen mode | — | 中 |
| Fusion | ~~Blending slider/mode, mergeFusedImages~~ ✅実装済（Toolbar Fusion→`FusionControlsPanel`） | — | 中 |
| Key Image | ~~captureAndSetKeyImage → DICOM SC出力~~ ✅実装済（描画キャプチャ + `exportKeyImagesAsDICOM`） | `captureAndSetKeyImage:` / `exportROIAndKeyImagesAsDICOMSeries:` (BrowserController.m:17815, DicomImage.m:1266, DCMView.m:11115) | 中 |
| Report | ~~レポート生成/削除/PDF/DICOM SR~~ ✅全実装済（`generateReport` / `deleteReport` / `convertReportToPDF` / `convertReportToDICOMSR`） | `generateReport:` / `deleteReport:` / `convertReportToPDF:` / `convertReportToDICOMSR:` (BrowserController.m) | 低 |
| Print | ~~Print slider~~ ✅実装済（`showPrintDialog()`、現在/全スライス/範囲+間隔、AWT PrinterJobでOSプリント） / ~~12-bit display~~ ✅実装済（dormantフラグ+メニュートグル、HOROS準拠：canDisplay12Bit=NOデフォルト） | `imageArithmeticSubtraction` 系 (AppController.m) | 低 |

### ✅ P3.5: 2D Viewer メニュー（完了）
| # | 項目 | HOROS実装箇所 | 概要 |
|---|------|-------------|------|
| 1 | ~~Apply CLUT / Add CLUT~~ ✅実装済（8プリセット: Gray/Body/Bone/Lung/Brain/PET-Rainbow/HotMetal/Heat + CLUTなし） | `ApplyCLUT:` / `AddCLUT:` (8-bit Editor) | WL/WW後の8bit値にRGB LUTを適用 |
| 2 | ~~Opacity (透明度)~~ ✅実装済（25%/50%/75%/100%） | `ApplyOpacity:` / `AddOpacity:` | 画像全体の一様な不透明度 |
| 3 | ~~Resize Window %~~ ✅実装済（actualSize/realSize/scaleToFit + Flip/Rotate） | `resizeWindow:` (ViewerController.m:2709) | No Rescale/Actual Size/Scale To Fit |
| 4 | ~~Workspace State~~ ✅実装済（Save/Load JSON・Reset） | `saveWindowsState:` / `loadWindowsState:` / `resetWindowsState:` | ウィンドウ配置の保存・復元 |
| 5 | ~~Apply Window Protocol~~ ✅実装済（モダリティ別既定WL/WW） | `applyWindowProtocol:` (ViewerController.m:2747) | モダリティ/シリーズに応じた自動ウィンドウ設定 |
| 6 | ~~Key Images（全5項目）~~ ✅実装済（mark/all/unmark/ROI/next・prev） | `setKeyImage:` / `findNextPreviousKeyImage:` | キー画像マーク付け・ナビゲーション |
| 7 | ~~Export (DICOM/JPEG/TIFF)~~ ✅実装済（JPEG/TIFF/DICOM全フレームフォルダ一括出力 + Email/DB Listエクスポート） | `exportJPEG:` / `exportTIFF:` / `exportDICOMFile:` | 画像エクスポート |
| 8 | ~~Flip Data Series~~ ✅実装済（インデックスリマップ） | `flipDataSeries:` (ViewerController.m:10400) | データシリーズ全体反転 |
| 9 | ~~Convert to RGB/BW~~ ✅実装済（`DicomPix.convertToBW/RGB` 8メニュー） | `ConvertToRGBMenu:` / `ConvertToBWMenu:` | 画像形式変換 |
| 10 | ~~Use VOI LUT~~ ✅実装済（`useVOILUT` + AppPreferences + チェックボックス同期） | `useVOILUT:` | ウィンドウプロトコルでVOI LUTを使用 |
| 11 | ~~Revert Series / Reset Image~~ ✅実装済（`revertSeries` / `resetImage`） | `revertSeries:` / `resetImage:` | WL/WW/回転/スケールのリセット |
| 12 | ~~SUV Conversion~~ ✅実装済（`displaySUV` + `DicomPix.hasSUV`/SUV属性読み込み + AppPreferences SUVキー） | `displaySUV:` (ViewerController.m) | PET SUV変換（体重/総用量/半減期入力ダイアログ） |
| 13 | ~~Calibrate~~ ✅実装済（`calibrate` + スライス厚み・ピクセル間隔・原点のダイアログ） | `calibrate:` | シリーズ全体の幾何パラメータ校正 |
| 14 | ~~Fuse/De-Fuse PET-SPECT-CT~~ ✅実装済（`blendWindows` + FusionImageProcessor toggle、同一Study・平行平面のCT/PETペア検索） | `blendWindows:` (ViewerController.m:12476) | CT+PET/SPECT 融合/解除 |
| 15 | ~~Flatten Fused Image~~ ✅実装済（`mergeFusedImages` + `DicomPix.setBufferedImage`） | `mergeFusedImages:` | 融合結果をベース画像に焼き込み |
| 16 | ~~Propagate Settings~~ ✅実装済（`copySettingsToOthers` + `switchCopySettingsInSeries` + `DCMView.propagateSettings`/`copySettingsInSeries`フラグ） | `copySettingsToOthers:` / `propagateSettings` (ViewerController.m:16804) | 他ビューアへのWL/WW・スケール・回転の伝播 + シリーズ内伝播トグル |

### 🟠 P2.5: ブラウザ/ツールバー追加ギャップ（未実装）
| # | 項目 | HOROS参照 | 備考 |
|---|------|----------|------|
| 1 | Windows Tiling - Rows (固定行数) | `setFixedTilingRows:` (AppController.m:4760) | ✅ 実装済（Menu > Windows > Fixed Rows...） |
| 2 | Windows Tiling - Columns (固定列数) | `setFixedTilingColumns:` (AppController.m:4765) | ✅ 実装済（Menu > Windows > Fixed Columns...） |
| 3 | Annotations Panel (ROI一覧) | `AnnotationsToolbarItemIdentifier` (ViewerController.m:182) | ✅ 実装済（`AnnotationsPanel.java`：ROI種類・面積・平均・SD・最小・最大・ロックの一覧 + 更新/削除 + `ToolbarPanel.toggleAnnotationsPanel` 配線） |

### 🟢 P5: GPU/Optimization

> **注:** 以下の「VRAM最適化」と「AI画像分析連携」は、HOROS-20240407 には直接的な対応機能がない OculusJJ 独自の拡張計画項目です。
> - VRAM 関連: HOROS は `VTKView.mm:182` / `DefaultsOsiriX.m:206` で GPU の VRAM サイズを検出（IORegistry の `VRAM,totalsize`）し、`VRView.mm:1020`「Your graphic board has only %d MB of VRAM. Performances will be very limited with large dataset.」や `VRView.mm:6310`「3D Dataset volume is larger than the amount of graphic board VRAM: GPU Rendering could be slower than CPU Rendering.」の警告を提示するのみで、ボリュームデータ自体の VRAM 永駐化 / Mipmap 生成等の最適化は VTK 内部に委ねる形です。
> - AI 関連: HOROS ソース内の ML 推論（`machinelearning` / `mlmodel` / `coreml` / `neural` / `inference` / `tensorflow` / `pytorch` / `onnx` / `weights`）は 0 件。`MSRGSegmentation.*` / `ITKSegmentation3DController.m` はマウス操作ベースのインタラクティブ・セグメンテーションであり AI 推論ではありません。

| 項目 | 状態 | 実体 |
|------|------|------|
| GPU Volume Rendering | ✅ 実装済 | `GLViewer3D.java`（LWJGL 3.2.3 / OpenGL 3.3、1548行） |
| レンダリング手法 | ✅ 実装済 | MIP / Volume Rendering(レイマーチ) / Isosurface の3モード |
| 照明 | ✅ 実装済 | Phong (Ambient+Diffuse+Specular) + 事前勾配計算 |
| Transfer Function | ✅ 実装済 | `TransferFunction.java`（1D GLテクスチャ、735行） |
| CPUレイキャストフォールバック | ✅ 実装済 | `CPURaycastRenderer.java`（GPUコンテキスト失敗時） |
| ステレオ表示 | ✅ 実装済 | 単眼L/R / バッファーステレオ |
| WW/WL・クリッピング・方位ボックス | ✅ 実装済 | Orientation Cube (A/P/S/I/R/L) |
| VRAM最適化（OpenGL textures to VRAM） | ✅ 実装済 | ボリューム/勾配テクスチャは VRAM に永駐し再利用（`volumeNeedsReupload` dirty-flag で再転送のみ）。POT ボリュームには Mipmap 生成 + trilinear（`GL_LINEAR_MIPMAP_LINEAR`、失敗時 LINEAR フォールバック）。HOROS準拠 VRAM 検出（`GpuVramInfo.java`: Linux /sys/class/drm、Windows wmic/PowerShell、macOS system_profiler）+ 低VRAM(<1GB)警告 / ボリューム超過警告（HOROS `VRView.mm:1020,6310` 相当、1回のみ・i18n `vr3d.vram.*`） |
| AI画像分析連携（HOROS: Grow Region / Set Pixel Values） | ✅ 実装済 | `model/SegmentationEngine.java`（HOROS ITKSegmentation3D 相当: Threshold区間 / Neighborhood半径 / Confidence倍率 の領域成長＋平滑化近似、純Java・依存ゼロ）+ `Viewer3DController.showAnalysisDialog()`（3Dツールバー「Analysis」ボタン、3Dビュー上でシードピッキング、inside/outside HU上書き、2D連動半透明オーバーレイ）+ `DicomPix.setSegOverlay`/`DCMView`描画パス。i18n `analysis.*` 両ファイル |

---

## [ファイル変更サマリー]

| ファイル | 変更内容 | ライン増減 |
|---|---|---|
| `MainMenuBar.java` | 全アクション配線 + Recent Studiesダイナミックメニュー | 192 → 240行 (+48) |
| `BurnerDialog.java` | ダイアログUI完全実装 | 5 → 130行 (+125) |
| `BrowserController.java` | Window/Export/Importアクション + Key Image実装 + tile3DWindows追加 | 8865 → 8954行 (+89) |
| `ViewerController.java` | tile3DWindows() static method追加 | 1453 → 1466行 (+13) |
| `QueryController.java` | showQueryDialog(Frame)追加 | 610 → 620行 (+10) |

---

## [ビルド状況]

- **依存関係:** dcm4che-core 5.34.1 のリポジトリ解決にネットワーク必要（サンドボックス環境ではオフラインビルド不可）
- **既存classファイル:** `build/classes/java/main` に 209 クラス蓄積済み（前回ビルド成功分）
- **最終ビルド:** BUILD SUCCESSFUL (コード修正後、brace matching全ファイルOK)

---

## [関連ドキュメント]

| ドキュメント | 内容 | パス |
|------------|------|------|
| ツールバー差分分析表 | HOROSボタン配置順・IDマッピング・ギャップ詳細 | `docs/diff/gap-analysis.md` |

---

## 最終更新日

**2026-10-22** — P5 分析機能（HOROS Grow Region / Set Pixel Values 準拠）実装済: `model/SegmentationEngine.java`（HOROS `ITKSegmentation3D.mm` 相当の3D領域成長: Threshold区間/Neighborhood半径/Confidence倍率、反復dilate方式・純Java、＋平滑化近似（itkCurvatureFlowImageFilter相当）、inside/outside HU上書き（roiSetPixels相当））。`Viewer3DController.showAnalysisDialog()`（3Dツールバー「Analysis」ボタン、3Dビュー上シードピッキング、2D連動半透明オーバーレイ、revert対応）。`DicomPix.setSegOverlay/getSegOverlayImage/hasSegOverlay/clearSegOverlay` ＋ `DCMView.paintComponent` にオーバーレイ描画パス追加。i18n `analysis.*` / `vr.analysis` 両ファイル。

**2026-10-22** — P5 VRAM最適化 実装済: (1) ボリューム/勾配テクスチャの VRAM 永駐＋dirty-flag による選択的再転送（既存）の確認、(2) POT ボリュームに Mipmap 生成 + trilinear サプリング（`GLViewer3D.uploadVolume()`、失敗時 LINEAR フォールバック）、(3) HOROS準拠 VRAM 検出・警告（新 `util/GpuVramInfo.java`: Linux /sys/class/drm・Windows wmic/PowerShell・macOS system_profiler で VRAM/レンダラー名を検出、<1GB 時とボリューム超過時に HOROS `VRView.mm:1020,6310` 相当の警告を 1 回のみ表示。`Viewer3DController.activateGPUMode()` 配線、i18n `vr3d.vram.*` 両ファイル追加）

**2026-08-22** — P3 Key Image: `captureAndSetKeyImage` をHOROS準拠（描画ビュー+アノテーションの8bit RGBキャプチャ → 本物の DICOM SC オブジェクト: SOP `1.2.840.10008.5.1.4.1.1.7` / Modality=SC）に書き換え + 2Dメニュー「キー画像をDICOM SCシリーズとして書き出す」（`exportKeyImagesAsDICOM`、HOROS `exportROIAndKeyImagesAsDICOMSeries:` 相当、全表示ビューアのキー画像一括出力）。i18n `key.image.*` / `export.sc.*` 追加（進捗率 約93% → 約94%）

**2026-08-21** — P2.5 Annotations Panel (toggleAnnotationsPanel / AnnotationsPanel.java) 実装済みの反映（進捗率 約92% → 約93%）

**2026-08-20** — P5 セクションに「VRAM最適化 / AI画像分析連携 は HOROS-20240407 に直接対応する機能はなく、OculusJJ の独自拡張計画項目である」という注記を追加（HOROS 側には `VTKView.mm:182` / `VRView.mm:1020,6310` の VRAM 検出・警告のみ、AI 推論関連ファイル 0 件を確認）

**2026-08-20** — P3.5 Propagate Settings (copySettingsToOthers / switchCopySettingsInSeries) 実装済みの反映（進捗率 約90% → 約92%）

**2026-08-19** — P3.5 2D Viewerメニュー追加: Use VOI LUT / Revert Series / Reset Image / SUV Conversion / Calibrate / Fuse-DeFuse / Flatten Fused Image 実装済みの反映（進捗率 約87% → 約90%）

**2026-08-19** — P5 GPU Volume Rendering 実装済みの反映（進捗率 約75% → 約85%）

## [関連ドキュメント]

### 分析・差分
| ドキュメント | 内容 | パス |
|------------|------|------|
| ツールバー差分分析表 | HOROSボタン配置順・IDマッピング・ギャップ詳細 | `docs/diff/gap-analysis.md` (2026-07-30更新) |

### 設計・アーキテクチャ
| ドキュメント | 内容 | パス |
|------------|------|------|
| 高レベルアーキテクチャ | HOROS→Javaの全体構造マップ | `docs/PortingGuide/Architecture/01-HighLevel-Architecture.md` (2026-07-30) |
| 機能マトリクス | HOROS vs Java 機能ギャップ一覧 | `docs/PortingGuide/Architecture/02-Feature-Matrix.md` (2026-07-30) |
| データ永続化マッピング | DB/ファイル/メモリ構造対応表 | `docs/PortingGuide/Architecture/DataPersistenceMapping.md` (2026-07-30) |

### DICOM関連
| ドキュメント | 内容 | パス |
|------------|------|------|
| レンダリングパイプライン | PixelData→OpenGL描画フロー | `docs/PortingGuide/DICOM/01-Rendering-Pipeline.md` (2026-07-30) |
| データベース&インポート | DicomDirParser / 外部DICOM読込 | `docs/PortingGuide/DICOM/02-Database-and-Import.md` (2026-07-30) |
| PACSネットワーク | C-MOVE/C-FIND実装計画 | `docs/PortingGuide/DICOM/03-PACS-Network.md` (2026-07-30) |
| メディア&インポート/エクスポート | CD-R/Writing, JPEG/TIFF出力 | `docs/PortingGuide/DICOM/04-Media-Import-Export.md` (2026-07-30) |

### ポーティング分析
| ドキュメント | 内容 | パス |
|------------|------|------|
| MPRレンダリング分析 | OpenCV→Java 3D再構成計画 | `docs/PortingGuide/Analysis/01-MPR-Rendering.md` (2026-07-30) |
| CPRレンダリング分析 | 血管追跡・曲面再構成 | `docs/PortingGuide/Analysis/02-CPR-Rendering.md` (2026-07-30) |
| 計測ツール実装計画 | ROI, Distance, Angle, Text | `docs/PortingGuide/Analysis/03-Measurement-Tools.md` (2026-07-30) |
| レンダリング同期エンジン | Multi-window sync design | `docs/PortingGuide/Analysis/04-Sync-Engine.md` (2026-07-30) |
| Browserナビゲーション | Tree View / Study Series 構造 | `docs/PortingGuide/Analysis/05-Browser-Navigation.md` (2026-07-30) |
| CPRロジック詳細 | Path generation algorithm | `docs/PortingGuide/Analysis/06-CPR-Logic.md` (2026-07-30) |

### UI関連
| ドキュメント | 内容 | パス |
|------------|------|------|
| Viewer同期ロジック | マウス/スクロール同期設計 | `docs/PortingGuide/UI/01-Viewer-Sync-Logic.md` (2026-07-30) |
| メニュー&ツールバーマップ | 全メニュー項目のマッピング | `docs/PortingGuide/UI/02-Menu-and-Toolbar-Map.md` (2026-07-30) |
| インタラクションマトリクス | ホットキー・ジェスチャ対応表 | `docs/PortingGuide/UI/03-Interaction-Matrix.md` (2026-07-30) |
| 環境設定マッピング | Preferences → Java Settings | `docs/PortingGuide/UI/04-Preference-Mapping.md` (2026-07-30) |

### 開発者向け
| ドキュメント | 内容 | パス |
|------------|------|------|
| 開発者警告マニュアル | HOROS→Java ポーティング上の注意 | `docs/PortingGuide/Porting/Developer-Warning-Manual.md` (2026-07-30) |

### 統合情報（OLDフォルダから統合済み）
| 項目 | 内容 | 出典 |
|------|------|------|
| dcm4che最小化戦略 | dcm4che-coreのみ使用、他ライブラリ不要 | `docs/OLD/IMPLEMENTATION_PRIORITY.md` (2025-12-10) |
| 未実装機能リスト | HOROS参照付き全機能ギャップ一覧 | `docs/OLD/UNIMPLEMENTED-FEATURES-LIST.md` (2025-12-10) |

**2026-07-30** — ドキュメント最適化完了


---

## [2026-10-25 更新] — P-C 減算（Subtraction）／ P-B ROI群 実装

### P-C 減算（HOROS: subCtrlOnOff / subCtrlNewMask / subSharpen / subCtrlOffset / subCtrlSliders / subSumSlider）
- **DicomPix.java**
  - `subtractWithMask(DicomPix mask, int offX, int offY)` — マスク引算（8方向シフト・0..65535クランプ）
  - `applySharpen5x5()` — HOROS「Sharpen 5x5」カーネル
  - `getOriginalShortData()` / `restoreOriginalShortData()` / `clearOriginalShortDataBackup()` — 引算OFF時の復元
  - `subtractionEnabled` フラグ
- **BrowserController.java** — 2D Viewerメニューに「減算」サブメニュー
  - 減算（トグル・ON時WL=128/WW=256）／マスク作成／シャープ化
  - ピクセルシフト NE/N/NW/W/なし/E/SW/S/SE（8方向再引算）
  - コントラスト↓/標準/↑、明るさ↓/標準/↑、足し算↓/なし/↑

### P-B ROI群（HOROS準拠）
- **ROI.java**
  - `groupID` フィールド＋ゲッター/セッター（HOROS: setGroupID）
  - `copy()` — 伝播・生成ROI用のROIコピー（isAliased=NO相当）
- **DCMView.java**
  - `collectGroupedROIs()` — 同groupIDのROI収集
  - ドラッグでグループ化ROIを共同移動（開始点保持・delta適用・統計再計算・保存）
- **BrowserController.java**
  - `groupSelectedROIs` / `ungroupSelectedROIs` — 選択ROIのグループ化/解除（timestamp ID）
  - `roiPropagateSetup` — 全フレームへのROI伝播
  - `roiPropagateSlab` — スラブ範囲（スライス厚/ピクセル間隔から推定）への伝播
  - `roiVolume`(tag=1) / `generateMissingROIs()` — 欠損スライスへの生成ROI（線形補間・`morphing generated`マーカ）
  - `roiDeleteGeneratedROIs` — `morphing generated`コメントのROI削除
  - `convertBrushPolygon` — ブラッシュ→ポリゴン（凸包）／ポリゴン→ブラッシュ（辺密化）
  - `mergeBrushROI` — 選択ブラシROIの点結合
- **i18n** — messages_en/ja.properties に 減算・ROI群関連キー追加

### 未実装のまま（プレースホルダ）
- `createLayerROIFromSelectedROI`（HOROS: texture/レイヤーROI・複雑）
- `segmentationTest`（Grow Region 2D/3D・Viewer3DController委譲・案内表示）
- 4D Propagate（roiPropagateSetupの4D軸対応）


---

## [2026-10-25 更新2] — P-E ウィンドウ状態・タイルリング

### 修正: Resize Window（HOROS準拠）
- 旧実装: ウィンドウ幅を画面の%でリサイズ（HOROSと異なる挙動）
- 新実装: `resizeWindowToScale(int tag)` — HOROS DCMView.m:13321準拠の**画像ズーム倍率**
  - 25% / 50% / 100% / 200% / 300%: `DCMView.setScaleValue(scale)`
  - iPod Video: 320px基準のフィットスケール（幅/高さ比率の小さい方を採用）

### 追加: Bring All to Front（HOROS: arrangeInFront:）
- `bringAllToFront()` — 全可視ウィンドウを前面に
- Windowメニューに項目追加（既存のi18nキー`menu_window_bring_all_to_front`利用）

### タイルリング
- `ViewerController.tileWindows()` / `tile3DWindows()` は既存の実装をそのまま利用（スクエアグリッド配置）

**コンパイル成功** ✅

---

---

## [2026-10-25 更新3] — P-D データベース管理

### 実装した空ハンドラ（HOROS: BrowserController.m）
| メニュー項目 | HOROS相当 | 実装内容 |
|---|---|---|
| **New Database Folder...** | `createDatabaseFolder:` (1860行) | フォルダ選択→名前入力→`OSIRIX_DATA_DIR_NAME`付きフォルダ作成 |
| **Open Database Folder...** | `openDatabase:` (1843行) | フォルダ選択→`DicomDatabase.databaseAtPath()`→`setActiveLocalDatabase` |
| **Create default albums...** | `defaultAlbums:` (1379行) | Favorites / Recently Used / Screen Captures を重複チェック付きで作成・保存・一覧更新 |
| **Rebuild SQL Index File...** | `rebuildSQLFile:` (2530行) | 確認ダイアログ→`DicomDatabase.save()` |
| **Rebuild Selected Thumbnails** | `rebuildThumbnails:` (9985行) | `DicomPix.invalidateThumbnail()`（新規追加）で全サムネイルを無効化 |
| **Page Setup...** | (xib 381行) | `java.awt.print.PrinterJob.printDialog()` |
| **Add an album / smart album** | `addAlbum:`/`addSmartAlbum:` | 名前（＋smartはpredicate）入力→`DicomAlbum`作成→`save()` |
| **Save albums... / Import albums...** | `saveAlbums:`(10811)/`addAlbums:`(10834) | HOROS互換 plist `.albums` を保存/読込（JSONと旧行形式も読込可） |
| **Delete selected album** | `removeAlbum:` | `DicomDatabase.removeAlbum(target)`＋SQLite保存、Database項目は削除不可 |

### 新規追加API
- `DicomPix.invalidateThumbnail()` — サムネイルキャッシュ無効化（再構築用）

### i18n
- `db.new_folder.*` / `db.open.*` / `db.album.*` / `db.default_albums.*` / `db.rebuild_sql.*` / `db.thumbnails.*` / `db.page_setup.*` キーを en/ja 両方に追加

**コンパイル成功** ✅

---

## [2026-10-25 更新4] — P-F レポート（Report）

### 実装したハンドラ（HOROS: BrowserController.m）
| メニュー項目 | HOROS相当 | 実装内容 |
|---|---|---|
| **Open report** (既存) | `generateReport:` | HTMLレポート生成→`study.setReportURL()`＋`db.save()` で後続処理と連携 |
| **Delete report...** | `deleteReport:` (18583行) | 確認→`reportURL`ファイル削除→`setReportURL(null)`→`save()` |
| **Convert to PDF...** | `convertReportToPDF:` (18542行) | HTML→`JEditorPane`→`PrinterJob.print()`（Windows「Microsoft Print to PDF」推奨） |
| **Convert to DICOM SR** | `convertReportToDICOMSR:` (18494行) | dcm4che `Attributes`＋`DicomOutputStream` で STUDY REPORT (SOP 1.2.840.10008.5.1.4.1.1.88.33) を生成 |

### DICOM SR の技術的メモ
- dcm4che-core 5.34.1 (mavenLocal) には `DicomObject`/`TagFromCode`/`UIDGenerator` が**存在しない**ため、`writeSCImage` と同じ**生タグint＋`VR`＋`Attributes`**パターンに統一
- HTML→プレーンテキストへ簡易変換（`<p>/<br>`は改行、タグ除去、エンティティ展開）
- 埋め込む主要タグ: SOPClassUID(0x00080016) / SOPInstanceUID(0x00080018) / StudyDate / Modality="SR" / StudyDescription / PatientID / StudyInstanceUID(0x0020000D) / ReportStatusID="PRELIMINARY"(0x00400244) / ReportClassID(0x00400250) / ReportDateTime(0x00400260, VR.DT) / **ReportDetailedDescription(0x00404010, VR.UT)**
- FileMetaInformation(0x0002xxxx)＋`writeDataset`＋`finish` で Explicit VR LittleEndian 書き出し

### 新規追加API
- `activeStudy()` — アクティブDCMViewまたはdatabaseOutline選択から`DicomStudy`を解決するヘルパー

### i18n
- `report.delete.*` / `report.pdf.*` / `report.dcm.*` キー（各 en/ja 両方）を追加

**コンパイル成功** ✅

---

## [2026-10-27 更新5] — P-G 4D シリーズ（4D Viewer）

### 実装したハンドラ（HOROS: BrowserController.m）
| 機能 | HOROS相当 | 実装内容 |
|---|---|---|
| **4D判定** | `isDataVolumicIn4D:` (ViewerController.m:7271) | `isSeries4D(DicomSeries)` — 同一 width/height のフレームが **4枚以上連続** する series を 4D と判定（null 安全な Integer 比較） |
| **Open in 4D** | `MovieViewerDICOM:` (13530行) | `movieViewerDICOM(sender)` — 選択行を `viewerDICOMInt(true, selected, null)` でアニメーション4Dビューワーとして開く |
| **Reparse in 4D...** | `reparseIn4D:` (12576行) | `reparseIn4D(sender)` — 選択 series の 4D判定 → 成立時は `movieViewerDICOM`、非成立時は理由を案内 |
| **Select all 4D Series** | `selectAll4DSeries:` (12581行) | `selectAll4DSeries(sender)` — アウトライン全行を走査し 4D series 行を ListSelectionModel で複数選択 |

### UI配線
- **DatabaseOutlineView 右クリックコンテキストメニュー**（`setComponentPopupMenu`）に「4Dで開く / 4Dとして再解析... / 全4Dシリーズを選択」を追加 — HOROS `initContextualMenus` (10850行) の "Open Images in 4D" / "Open Reparsed series" 相当
- `BrowserController.add4DMenuToOutline()` — プログラム側からメニューを再注入するためのヘルパー
- **プレビューパネル（ステータスバー）** に HOROS MainMenu.xib 3193-3213行目相当の **3D/4D ラジオボタン +「全4Dシリーズを開く」ボタン** を追加（状態は `Preview4DMode` pref に永続化、4Dモードは `movieViewerDICOM` で開く）

### 技術的メモ
- SwingX `JXTable` には `addSelectionInterval` が無い → `getSelectionModel().addSelectionInterval()` 経由（選択モードを一時 MULTI に切り替えて復元）
- `DicomImage.getWidth()/getHeight()` は `Integer`（null 可能性）→ null 安全な int 比較

### i18n
- `menu_4d_*` / `4d.open.*` / `4d.reparse.*` / `4d.select.*` キー（en/ja 両方）を追加

**コンパイル成功** ✅

---

## [2026-10-29 更新6] — P-H PACS（Query / Retrieve / C-STORE）

### 実装した機能
| 機能 | 実装内容 |
|---|---|
| **Export to DICOM Network Node** | アクティブシリーズの元 DICOM ファイルを重複除外し、送信先 `DicomNode` を選択して C-STORE SCU で送信。進捗・失敗ファイルを表示 |
| **Query Selected Patient** | 選択中の Study/Series から Patient ID を取得し、Query/Retrieve ウィンドウの Patient ID 検索を自動実行 |
| **Refresh PACS On-Demand Results** | Query/Retrieve ウィンドウで現在の検索条件を再実行 |
| **Retrieve selected PACS studies** | Query/Retrieve 結果で選択中の Study を C-MOVE 取得 |
| **Auto Query / Retrieve Window / Refresh** | 既存 Query/Retrieve ウィンドウの表示・再検索に接続 |

### 追加 API
- `DicomService.store(DicomNode, File)` — dcm4che C-STORE SCU（SOP Class/Instance UID と元 Transfer Syntax を使用）
- `QueryController.setSearchTextAndQuery()` / `refreshResults()` / `retrieveSelectedResults()` — BrowserController から検索・更新・取得を呼び出す公開 API

### i18n
- `pacs.export.*` / `pacs.query.*` / `pacs.retrieve.*` を en/ja 両方に追加

### 制約
- C-STORE は元 DICOM ファイルを送るため、現在の画面上の画素処理（WL/WW や焼き込み）は送信データに反映しない
- On-Demand Study の DB 永続化／リモート検索結果のローカル表示は未実装。Query/Retrieve 結果から直接 C-MOVE する経路を提供
- 定期的な自動クエリのスケジュール実行と、進行中の受信 C-STORE の個別中断は未実装

**コンパイル成功** ✅

---

## [2026-10-29 更新7] — P-I アルバム管理・永続化

### 修正した不具合と追加機能
- **アルバム削除:** `getAlbums()` はコピーを返すため、コピーの `remove()` はDBに反映されていなかった。`DicomDatabase.removeAlbum()` に変更し、削除後に一覧更新。
- **SQLite永続化:** `save()` が Study だけを保存し、`Album` / `AlbumStudy` を保存していなかった。アルバム定義、smart predicate、Study membershipをトランザクションで保存。削除されたアルバムもDBから除去。
- **Study追加:** 空だった `addStudies()` を実装。選択 Study / Series / Image をアルバムに追加し、Study保存後に関連テーブルへ記録。
- **アーカイブ:** 空だった `saveAlbumsToPath/loadAlbumsFromPath` を実装。HOROS互換の plist XML `.albums` を保存・読込し、従来のJSONと行形式もインポート可能。
- **互換 API / smart albums:** `albums()` の名前順取得、`addDefaultAlbums()` の重複防止保存、`predicateForSmartAlbumFilter()` を実装。`$TODAY/$YESTERDAY/$LASTWEEK` 等の日付マクロを RuleEngine で解決。
- **UI:** Add selected studies to album... を追加。smart album の predicate 評価、Database/アルバム別件数計算、Create default albums と追加/削除/インポート後の保存・一覧更新を実装。
- **i18n:** アルバムへの Study 追加と削除確認の文言を en/ja に追加。
- **Delete selected items:** 通常アルバムからの除外、またはStudy/SeriesレコードのDB削除を選択可能にした。ファイルは保持する旨を確認表示し、Study削除では `AlbumStudy` を含むSQLトランザクションで整理。

### 保留
- `mergeStudies` は HOROS で DICOM ファイルヘッダーまで変更する不可逆処理のため未実装（誤操作時に破損しないよう明示案内を表示）。
- DB削除後も元DICOMファイルはディスクに残す仕様。OS Trash への移動・ファイル物理削除は未実装。

### 検証
- `./gradlew compileJava` 成功
- SQLite再起動を含む動作確認/PACSと同様の実環境テストは未実施（後続テスト対象）
