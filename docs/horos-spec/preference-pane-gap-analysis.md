# Preference Pane ギャップ分析レポート — HOROS-20240407 vs OculusJJ

> **作成日**: 2026-07-19  
> **目的**: HOROS-20240407 の全 15 Preference Pane 仕様と OculusJJ 現行実装の差分を詳細に整理し、実装優先度を付与する。  
> **基準ソース**: `horos-20240407/Preference Panes/*` (各 pane の .h/.m + .xib)  
> **OculusJJ 対象ファイル**: `src/main/java/com/oculus/jj/ui/PreferencesController.java`, `AppPreferences.java`

---

## 概要

HOROS-20240407 は Preference Window に **15 個の Preference Pane** を備える。一方 OculusJJ の現行実装は、sidebar には全 pane 名が列挙されているものの、コンテンツパネルの実装度は panes により大きく異なる — General/Database/CD-DVD Burning はそれなりの内容を有するが、大半の pane では機能の大部分が未実装（stub/プレースホルダ）である。

**AppPreferences.java のデータ層は比較的充実している** (全キー定数 + getter/setter が定義済み) 一方、**UI 層 (PreferencesController.java ~603行)** はデータ層を反映し切れていない状態。

---

## ステータスマトリックス（15 panes × 実装度）

| # | Preference Pane | HOROS 参照 | Java 実装度 | 補完率(概算) |
|---:|-----------------|------------|-------------:|--------------:|
|  1 | General (OSIGeneralPreferencePane) | .m 16.3KB, .xib 64KB | ⚠️ 部分的実装 | ~25% |
|  2 | Database (OSIDatabasePreferencePane) | .m 24.5KB, .xib 115KB | ⚠️ 一部実装 | ~30% |
|  3 | CD/DVD Burning (OSICDPreferencePane) | .m 3.5KB + DialogController.m, xib 19KB | ✓ 相当実装 | ~70% |
|  4 | Hanging Protocols (OSIHangingPreferencePane) | .m 19.8KB, xib 58KB + ArrayControllers | ✗ スタブ | ~5% |
|  5 | Hot Keys (OSIHotKeysPreferencePane) | .m 12.3KB + Formatter/TableView/ArrayController | ⚠️ 表示のみ | ~20% |
|  6 | Viewers (OSIViewerPreferencePane+O2ScreensPrefsView) | O2ScreensPrefsView.m 10KB, xib 80KB | ✗ スタブ | ~5% |
|  7 | 3D Viewer (OSI3DPreferencePane) | .mm 3.9KB | ✓ 基本実装 | ~40% |
|  8 | PET/SUV (OSIPETPreferencePane) | .m 5.5KB, xib 43KB | ⚠️ 基本設定のみ | ~20% |
|  9 | Annotations (OSICustomImageAnnotations) | CIALayoutController.m 54KB + 10+ サブクラス | ✗ スタブ | ~5% |
| 10 | DICOM Print (AYDicomPrintPreferencePane) | .m 7.5KB, xib 62KB | ✓ 一部実装 | ~30% |
| 11 | Listener (OSIListenerPreferencePane) | .mm 16KB, xib 112KB | ⚠️ 最低限のみ | ~10% |
| 12 | Locations (OSILocationsPreferencePane) | .m 36.6KB(最大), xib 136KB(最大) | ✗ スタブ + 最小限 | ~10% |
| 13 | Routing/Autorouting (OSIAutoroutingPreferencePane) | .m 13.8KB + TableViewx2, xib 63KB | ✗ スタブ | ~5% |
| 14 | Web Server (OSIWebSharingPreferencePane) | .mm 10.9KB, xib 127KB | ⚠️ 最低限のみ | ~15% |
| 15 | On-Demand PACS (OSIPACSOnDemandPreferencePane) | .m 18.9KB, xib 75KB + Cloud.png | ✗ スタブ | ~5% |

**概算補完率: 全体で ~20%** — Preference Window は実装ギャップが最も大きい UI の一つ。

---

## 各 Pane 詳細ギャップ分析

### 1. General (OSIGeneralPreferencePane)
- **HOROS 参照**: `horos-20240407/Preference Panes/OSIGeneralPreferencePane/` (.m 16,282 bytes, .xib 64KB)
- **Java 実装ファイル**: `PreferencesController.java:153-179` (createGeneralPane)

#### HOROS が持つ機能（全 20+ 項目）
| # | 機能 | HOROS 実装詳細 |
|---|------|----------------|
| G-001 | **言語選択リスト** | `languagesToMoveWhenQuitting` で active/inactive リスト管理、NSLocaleDisplayName 取得。"Resources Disabled/" サブディレクトリから無効化言語をスキャン |
| G-002 | **起動時アップデート確認** | NSUserDefaults CheckUpdatesAtStartup 連動。OSIGeneralPreferencePanePref.m に CheckUpdatesOnOff IBOutlet 定義あり |
| G-003 | **復元/リセット機能** | `- resetPreferences:` で全 NSUserDefaults 削除。警告アラートパネルを表示 |
| G-004 | **JPEG2000 エンジン選択** | Kakadu(JP2KWriter) / OpenJPEG / Kakadu Engine (3-way)。`setJP2KEngine:`, `JP2KEngine` getter/setter 実装。AppController.isKDUEngineAvailable() 連動 |
| G-005 | **圧縮設定エディタ** | `-editCompressionSettings:` で compressionSettingsWindow を起動（別ウィンドウで詳細圧縮パラメータ編集）|
| G-006 | **AE Title / Transfer Syntaxes 設定** | AE Title、暗号化方式、転送構文選択機能。PreferencesWindowController+DCMTK.h 参照 |
| G-007 | **ネットワークポート設定** | ポート104(DICOM) および11112(Listener/AutoRouting) の両方を管理 |
| G-008 | **パスワード保護/自動ロック** | 一定時間経過でウィンドウをロックする機能。`passwordForWindowProtectionInterval:` など |
| G-009 | **ホットキー割当（全体）** | HotKeyArrayController を経由して全アクションにショートカット設定 |
| G-010 | **マウス操作設定** | 左/右/中ボタン + スクロールホイールの動作カスタマイズ |
| G-011 | **ダブルクリックズーム動作** | ViewerWindow でダブルクリック動作をカスタマイズ（zoom/dicom-label-toggle/toggle-all-series）|
| G-012 | **ウィンドウサイズ制限** | fit-to-window / max 1024 / 512px の上限設定 |
| G-013 | **方向マーカーとのアライメント** | `alignmentWithOrientationMarkers:` で画像のアライメントを調整 |
| G-014 | **アノテーション切り替え** | 描画上の注釈表示/非表示トグル。`annotationsEnabled:` |
| G-015 | **アノテーションスタイル編集** | `editAnnotationStyle:` ダイアログで Font/Color/Button 設定を保存/ロード |

#### OculusJJ の現行実装とギャップ
| # | HOROS 機能 | Java 現在 | ギャップ | 優先度 |
|---|-----------|----------|---------|--------|
| G-001 | 言語選択リスト (active/inactive) | JComboBox で "English" / "Japanese" の2択のみ。動的スキャンなし | **未実装** — language list dynamic scan が欠落 | P1 |
| G-003 | リセット機能 | なし | **未実装** — 全設定初期化のダイアログ | P1 |
| G-004 | JPEG2000 エンジン選択 | AppPreferences に KEY_APP_LANGUAGE のみ。Kakadu/OpenJPEG 切り替えなし | **未実装** — Java/DICOM ライブラリ側の JPEG2000 サポート次第 | P1 |
| G-005 | 圧縮エディタ別ウィンドウ | なし | **未実装** — 詳細パラメータ編集ダイアログ | P2 |
| G-006 | AE Title / Transfer Syntaxes | PreferencesController に含まれていない。AppPreferences に KEY_LISTENER_AETITLE は存在するが、UI 上の設定機能なし | **未実装** | P1 |
| G-007 | ネットワークポート | Listener pane で JTextField("11112") のみ。ポート104 は未 | **部分的実装** | P2 |
| G-008 | パスワード保護/自動ロック | AppPreferences にキー定義なし、UI なし | **未実装** | P3 |
| G-010〜G-011 | マウス操作 / ダブルクリック動作 | 存在しない。AppPreferences に KEY_DEFAULT_MOUSE_ACTION, KEY_DOUBLE_CLICK_ZOOM 等のキーも未定義 | **未実装** | P2 |

### 2. Database (OSIDatabasePreferencePane)
- **HOROS 参照**: `horos-20240407/Preference Panes/OSIDatabasePreferencePane/` (.m 24,513 bytes — 最重量 class, .xib 115KB) — **最重量クラス**
- **Java 実装ファイル**: `PreferencesController.java:182-230` (createDatabasePane)

#### HOROS が持つ機能（全 25+ 項目）
| # | 機能 | HOROS 実装詳細 |
|---|------|----------------|
| D-001 | **データベースパス設定** | `NSPathControl` で表示。pathField URL と NSUserDefaults 連動。setLocation: / setLocationURL: IBAction |
| D-002 | **DICOMファイル保存パス** | 別パスを指定する機能（DEFAULT_DICOMLOCATIONURL）|
| D-003 | **自動更新起動時オプション** | AUTOCLEANINGDATEPRODUCED / OPENED / COMMENTS オプション選択。resetDate: / resetDateOfBirth: IBAction |
| D-004 | **シリーズ順序設定** | NSUserDefaults SERIESORDER 連動。setSeriesOrder: メソッド実装（3オプション：日付昇順/降順/自動）|
| D-005 | **DICOMフィールドでPatient UID生成** | Patient ID / Birth Date / Name の3要素からUID生成設定。newUsePatientIDForUID 等3つ同時チェック。少なくとも1つ必須（未選択時の警告アラート）|
| D-006 | **コメント自動埋め込み** | DICOM コメントフィールドへの自動入力。commentFieldForAutoFill: comment/comment2/comment3/comment4 の4オプション。setAutoComments: IBAction + regenerateAutoComments: |
| D-007 | **DICOM Fields 自動補完** | dicomFieldsMenu で Field 選択メニュー。prepareDICOMFieldsArrays() を経由して動的に DICOM fields 一覧取得 |
| D-008 | **レポートモード設定** | reportsMode NSPopUpButton。PluginManager.reportPlugins から動的リスト生成。3つのモード（Standard/Report Plugin/Custom）|
| D-009 | **データベース自動クリーニング** | 日付別(produced/opened/comments) + フリーディスクスペース別。olderThanProduced: / olderThanOpened: NSPopUpButton で選択 |
| D-010 | **自動クリーンアップ条件テキスト** | AUTOCLEANINGCOMMENTSTEXT + AUTOCLEANINGDONTCONTAIN (comments に含まれない文字列) によるフィルタリング |
| D-011 | **Original DICOM削除** | deleteOriginal NSButton でクリーンアップ完了後の元ファイル削除設定。AUTOCLEANINGDELETEORIGINAL 連動 |
| D-012 | **PACS サーバーマージ設定** | PACS からの取得後に既存DBとマージする動作設定（DicomDatabase.m と連動）|
| D-013 | **起動時全データベース削除** | eraseEntireDBAtStartup KVO監視。有効時に警告ダイアログ表示 |
| D-014 | **テーブルビューフォントサイズ** | dbFontSize 連動。BrowserController.tableViewRowHeight を更新 |
| D-015 | **水平履歴設定** | horizontalHistory。変更時に再起動要求アラート |

#### OculusJJ の現行実装とギャップ
| # | HOROS 機能 | Java 現在 | ギャップ | 優先度 |
|---|-----------|----------|---------|--------|
| D-001〜D-002 | DB/DICOM パス設定 | ✅ JTextField + Browse ボタンで実装済。AppPreferences.getDatabaseFolder() で連動 | なし — 完成済み | - |
| D-003 | 自動更新起動時オプション | JCheckBox "Automatically refresh database on startup" のみ。日付別/コメント別フィルタなし | **未実装** — AUTOCLEANING オプション全般 | P1 |
| D-004 | シリーズ順序設定 | 未実装。AppPreferences に KEY_SERIES_ORDER キーも未定義 | **未実装** | P2 |
| D-005 | Patient UID 生成 (3要素チェック) | 未実装。UID生成関連のキーが AppPreferences に存在しない | **未実装** — P1 に相当（データモデル固有）| P1 |
| D-006 | コメント自動埋め込み + ダイアログ | 未実装。currentCommentsAutoFill / currentCommentsField のような状態変数がない | **未実装** | P2 |
| D-008 | レポートモード設定 | 未実装。AppPreferences に KEY_REPORT_MODE キーも未定義。PluginManager 依存のため外部要因あり | **未実装** | P1 |
| D-009〜D-011 | 自動クリーンアップ (日/コメント/Original削除) | 未実装。AppPreferences に KEY_AUTO_CLEANING_* キーも未定義 | **未実装** | P2 |
| D-013 | 起動時全DB消去 | AppPreferences に KEY_ERASE_DB_AT_STARTUP キーも未定義、UI もなし | **未実装** | P3 |
| D-014〜D-015 | テーブルビューフォント/水平履歴 | BrowserController に setTableViewRowHeight 存在するが、Preference UI から設定不可 | **未実装（Preference UI のみ）** | P2 |

### 3. CD/DVD Burning (OSICDPreferencePane)
- **HOROS 参照**: `horos-20240407/Preference Panes/OSICDPreferencePane/` (.m 3,506 bytes + DiscMountedAskTheUserDialogController.m 2,866 bytes, xib 19KB)
- **Java 実装ファイル**: `PreferencesController.java:233-274` (createCDpvPane)

#### HOROS が持つ機能とギャップ
| # | 機能 | Java 現在 | ギャップ | 優先度 |
|---|------|----------|---------|--------|
| B-001 | ディスクコピー完了後に eject | ✅ JCheckBox "Eject the disk when done copying" で実装済 (KEY_CDDVD_EJECT_AFTER_COPY) | なし | - |
| B-002 | ソース自動選択 | ✅ JCheckBox "Automatically select the source when ready" 実装済 (KEY_CDDVD_AUTO_SELECT) | なし | - |
| B-003 | 挿入ディスクからの自動インポート | ✅ JCheckBox "Automatically import from inserted CDs/DVDs" 実装済 (KEY_CDDVD_AUTO_IMPORT) | なし | - |
| B-004 | 標準 JPEG 優先（JPEG2000/JPEG-LS 以外） | ✅ JCheckBox "Prefer standard JPEG instead of JPEG2000/JPEG-LS" 実装済 (KEY_CDDVD_PREFER_STD_JPEG) | なし | - |
| B-005 | 補助フォルダの追加 | ✅ JTextField + Browse ボタンで実装済 (KEY_CDDVD_SUPPLEMENTARY_FOLDER) | なし | - |
| B-006 | **CD/DVD naming** | ❌ `JTextField("burn.cdName")` に相当するフィールドがない。DiscMountedAskTheUserDialogController が存在するが未実装 | **未実装** — HOROS の命名ダイアログが欠落 | P1 |
| B-007 | **圧縮モード選択** | ❌ Kakadu/OpenJPEG/Standard JPEG 等の圧縮エンジン切替 UI が欠如。Horos では compression mode を選択可能（"Lossless", "High Quality (95%)"等）| **未実装** — Java/DICOM ライブラリの実装状況次第 | P1 |
| B-008 | **CD 暗号化** | ❌ KEY_BURN_ENCRYPT_CD は AppPreferences にあるが、UI で設定不可 | **未実装** | P2 |
| B-009 | **Disc Mounted Ask The User Dialog** | ❌ DiscMountedAskTheUserDialogController.h/.m (合計 5,181 bytes) が存在するが OculusJJ に未実装。書き込み前にユーザーに確認を表示する機能 | **未実装** — OSICDPreferencePane の dialog controller と連動 | P1 |

### 4. Hanging Protocols (OSIHangingPreferencePane)
- **HOROS 参照**: `horos-20240407/Preference Panes/OSIHangingPreferencePane/` (.m 19,819 bytes + BodyRegionArrayController.m + HangingTableView.m + KeywordsArrayController.m + UpperCaseStringFormatter.m + UpperCaseTableColumn.m)
- **Java 実装ファイル**: `PreferencesController.java:303-326` (createHangingProtocolsPane)

#### HOROS が持つ機能とギャップ
| # | 機能 | Java 現在 | ギャップ | 優先度 |
|---|------|----------|---------|--------|
| H-001 | モダリティ別デフォルト Hanging Protocol 選択 | ✅ JComboBox "Modality" (CR, CT...XA) 実装済。KEY_HANGING_MODALITY 連動（ただしデフォルトが hard-coded SelectedIndex=1 で AppPreferences から読まれていない）| **不完全** — デフォルト取得ロジックの欠落 | P2 |
| H-002 | 画像タイル配置パターン | ✅ JComboBox "Image Tiling" (1x1..4x3 計16パターン) 実装済。KEY_HANGING_TILING | なし | - |
| H-003 | WL/WW プリセット | ✅ JComboBox ("Default/Bone/Lung/Liver/Brain/Soft Tissue/Mediastinum") 実装済。KEY_HANGING_WLWW_PRESET（ただし保存ロジックの欠落が疑われる）| **不完全** — 設定値が AppPreferences に保存されるか要確認 | P2 |
| H-004 | ** body region 管理** (Abdomen/Pelvis/Chest/Brain) | ❌ BodyRegionArrayController.m が存在するが OculusJJ に未実装。body region を追加/削除・順序変更できる UI | **未実装** — BodyRegionArrayController + テーブル UI | P1 |
| H-005 | ** キーワード管理** (add/remove from available list) | ❌ KeywordsArrayController.m, UpperCaseStringFormatter.m が存在する。ユーザー定義キーワードを追加・編集・削除できる機能 | **未実装** — UpperCaseTableColumn による大文字強制表示を含むテーブル UI | P1 |
| H-006 | ** study layout patterns 設定** | ❌ HangingTableView + KeywordsArrayController と連動して study レイアウトを定義する。各 region に割り当てる pattern を設定可能 | **未実装** — BodyRegion との結合がない | P2 |
| H-007 | ** デフォルト hanging protocol 選択** | ❌ 各 body region にデフォルトで適用する protocol を指定する UI が存在しない。ユーザーが protocol を作成・編集できる機能が欠如 | **未実装** — OSIHangingPreferencePane の core functionality | P1 |
| H-008 | Hanging Protocol プリセット一覧 | ❌ 既存の preset + ユーザー定義の両方を管理するリスト表示 UI が未。BodyRegionArrayController と連動して body region ごとのデフォルトを切替可能 | **未実装** | P1 |

### 5. Hot Keys (OSIHotKeysPreferencePane)
- **HOROS 参照**: `horos-20240407/Preference Panes/OSIHotKeysPreferencePane/` (.m 12,286 bytes + HotKeyArrayController.m + HotKeyFormatter.m + HotKeyTableView.m + HotKeyTextFieldCell.m)
- **Java 実装ファイル**: `PreferencesController.java:328-345` (createHotKeysPane)

#### HOROS が持つ機能とギャップ
| # | 機能 | Java 現在 | ギャップ | 優先度 |
|---|------|----------|---------|--------|
| HK-001 | ホットキー一覧表示（Action + Key Combination） | ✅ DefaultTableModel + JTable で実装済。15 組の predefined hotkeys が初期値として表示される | なし | - |
| HK-002 | **新規キーバインディング追加** (+ Add Key Binding) | ❌ JButton "+ Add Key Binding" はあるが、HotKeyArrayController / HotKeyFormatter / HotKeyTextFieldCell がない。実際に new key をキャプチャして保存する機能は欠如 | **未実装** — ホットキーエディタ ダイアログの実装が必要 | P1 |
| HK-003 | **キー割当編集ダイアログ（HotKeyFormatter + TextFieldCell）** | ❌ HotKeyTextFieldCell.m で new key capture を実装。ホットキーエディタは NSTextField 上に直接 new key を入力可能。Applescript/Carbon Event を使って new hotkey を取得 | **未実装** — Java の KeyListener + InputEvent の API 活用で代替可能だが機能自体は欠落 | P1 |
| HK-004 | ホットキー削除 | ❌ JButton "- Remove" はあるが HotKeyArrayController が未。選択された hotkey を実質的に削除するロジックがない | **部分的実装** | P2 |
| HK-005 | ホットキー編集（既存バインディングの上書き） | ❌ JButton "Edit Selected" もホットキーの編集ダイアログは未実装。選択された行をエディタで上書きする機能がない | **未実装** | P2 |
| HK-006 | HotKeyFormatter / HotKeyTextFieldCell のカスタムレンダリング | ❌ HotKeyFormatter.m でキー表記を "Ctrl+Shift+M" 等の人間 readable フォーマットに整形。HotKeyTableView + HotKeyTextFieldCell でカスタムセル描画を行う | **未実装** — JTable Renderer/Editor として実装するかラベル表示のみで済ませるか判断が必要 | P2 |

### 6. Viewers (OSIViewerPreferencePane + O2ScreensPrefsView)
- **HOROS 参照**: `horos-20240407/Preference Panes/OSIViewerPreferencePane/` (.m 4,815 bytes + O2ScreensPrefsView.m 10,173 bytes, xib 80KB) — **二画面設定の core**
- **Java 実装ファイル**: `PreferencesController.java:346-387` (createViewersPane)

#### OSIViewerPreferencePane の機能とギャップ
| # | 機能 | Java 現在 | ギャップ | 優先度 |
|---|------|----------|---------|--------|
| V-001 | デフォルトCLUT 選択 | ❌ createViewersPane で "Default CLUT:" の JComboBox は表示されていない。AppPreferences に KEY_DEFAULT_CLUT は定義済みだが UI 未実装 | **未実装** | P2 |
| V-002 | デフォルト Opacity Table (Transfer Function) | ❌ KEY_DEFAULT_OPACITY_TABLE はあるが UI 未 | **未実装** | P3 |
| V-003 | Study 間 Series 同期 | ❌ JCheckBox "Sync Series Between Studies" — AppPreferences に KEY_SYNC_SERIES_BETWEEN_STUDIES が定義済みのみ。UI なし | **未実装** | P2 |

#### O2ScreensPrefsView（二画面設定）の機能
| # | 機能 | Java 現在 | ギャップ | 優先度 |
|---|------|----------|---------|--------|
| V-010 | **Secondary Monitor 表示制御** | ❌ `O2ScreensPrefsView.m` で Secondary Monitor に Series/Series Thumbnails/Navigator を表示するかどうか設定。3つのモード（None / Series List / Thumbnail）| **未実装** — Java Swing では JWindow と画面座標で実現が必要 | P2 |
| V-011 | **ウィンドウサイズ制限とアライメント** | ❌ max 1024/512px + orientation markers アラインメントの UI。OSIViewerPreferencePanePref.h の `setAlignmentWithOrientationMarkers:` IBAction に相当 | **未実装** | P3 |
| V-012 | **スクロールモード切替 (pan mode)** | ❌ mouse actions 設定と連動する scroll/pan 切替が欠如。左ボタンでスクロール、中ボタン押下で pan に変更する動作 | **未実装** — Viewers pane に統合すべきか独立するか判断が必要 | P3 |
| V-013 | **両サイドの中ボタンの上で2点測定ツール活性化** | ❌ middle button と左右ボタン同時押下で measurement を開始可能にする設定。OSIViewerPreferencePanePref.h の関連 IBAction 欠落 | **未実装** | P3 |
| V-014 | **最小ウィンドウサイズの縦横比保持** | ❌ リサイズ時の aspect ratio 固定のオプションなし。minWindowAspect getter/setter が OSILocationsPreferencePanePref.h に定義済み | **未実装** — Java Swing で最小 size + resize listener で実装可能だが機能自体は欠如 | P3 |

### 7. 3D Viewer (OSI3DPreferencePane)
- **HOROS 参照**: `horos-20240407/Preference Panes/OSI3DPreferencePane/` (.mm 3,909 bytes, xib 61KB相当)
- **Java 実装ファイル**: `PreferencesController.java:388-412` (create3DPane)

#### HOROS が持つ機能とギャップ
| # | 機能 | Java 現在 | ギャップ | 優先度 |
|---|------|----------|---------|--------|
| 3D-001 | 3D レンダリング解像度 (RESOLUTION) | ✅ AppPreferences に KEY_3D_RESOLUTION + getter/setter 存在。Preference pane UI の中での設定は要確認 | ⚠️ 要確認 | - |
| 3D-002 | Shading アルゴリズム切替 (Smooth/Flat/Others) | ✅ create3DPane で "Shading Mode" JComboBox に Smooth/Flat/Others が実装済。KEY_3D_SHADING_ALGORITHM 連動 | なし（ただし Others オプションの動作は要確認）| - |
| 3D-003 | 3D ROI アルゴリズム (Iso Contour / Delaunay) | ✅ "Algorithm for 3D ROIs:" JComboBox に Iso Contour/Delaunay が実装済。KEY_3D_ROI_ALGORITHM 連動 | なし | - |
| 3D-004 | Scissors ROI のスプライン補間 | ⚠️ JCheckBox "Use Spline interpolation for Scissors ROI" はあるが、AppPreferences に KEY_3D_SPLINE_FOR_SCISORS が定義済みか要確認 | ⚠️ キー欠落の可能性あり | P2 |
| 3D-005 | **MAPPERMODE VRAM 監視 (KVO)** | ❌ OSI3DPreferencePane.mm (.mm 3,909 bytes) で MAPPERMODE を通じて VRAM(GPUメモリ) の使用量を KVO で監視。リアルタイム VRAM 使用量が Preference UI に表示される機能 | **未実装** — Java/JNA で GPU メモリ情報を取得する必要がある（Windows では dxdiag / NvAPI / AMD API）| P2 |
| 3D-006 | **詳細レンダリングオプション (Advanced)** | ❌ Render Quality, Texture Compression, Anti-Aliasing 等の advanced options。OSI3DPreferencePane.xib に含まれるが OculusJJ には未実装 | **未実装** — 実画像処理エンジンの機能依存性あり | P2 |

### 8. PET/SUV (OSIPETPreferencePane)
- **HOROS 参照**: `horos-20240407/Preference Panes/OSIPETPreferencePane/` (.m 5,545 bytes, xib 43KB)
- **Java 実装ファイル**: `PreferencesController.java:413-432` (createPETPane)

#### HOROS が持つ機能とギャップ
| # | 機能 | Java 現在 | ギャップ | 優先度 |
|---|------|----------|---------|--------|
| PETSUV-001 | デフォルト PET CLUT | ✅ JComboBox ("CT-Body/CT-Bone/CT-Lung/CT-Brain/PET-Rainbow/PET-HotMetal/PET-Heat") が実装済。KEY_PET_DEFAULT_CLUT 連動 | なし | - |
| PETSUV-002 | "B/W Inverse" CLUT for blending vs Modality-specific CLUT 切替 | ✅ RadioButton 群 (useBW/useModality) で実装済。ButtonGroup に設定済み | ⚠️ AppPreferences に KEY_PET_CLUT_BW_INVERSE が定義済みで正しく保存されるか要確認 | P3 |
| PETSUV-003 | **SUV 正規化モード選択** (Body Weight / Lean Mass / BSA) | ❌ SUV Normalization を BodyWeight/LeanMass/BSA から選べる UI は存在しない。HOROS の OSIPETPreferencePane に実装されている core feature | **未実装** — 各正規化式: SUV = uptake/(BW*conc), lean mass, BSA-based | P1 |
| PETSUV-004 | **PET 表示範囲自動拡張 (auto-scaling)** | ❌ PET/CT で display range を auto-scale するオプションが未実装。contrast settings scaling factor (contrast) も欠如 | **未実装** — SUV display のスケーリングが必要 | P2 |
| PETSUV-005 | **デフォルト Transfer Function 設定** | ⚠️ JComboBox ("Standard/PET Specific/NM Specific/Custom") はあるが、AppPreferences に KEY_PET_DEFAULT_TRANSFER_FUNC が定義済みか要確認。保存ロジックの欠落可能性あり | P3 |
| PETSUV-006 | **NM モダリティにも適用** | ✅ JCheckBox "Apply this behavior also to NM modality" は実装済。キー名は不明 | ⚠️ 保存連動要確認 | P3 |

### 9. Annotations (OSICustomImageAnnotations)
- **HOROS 参照**: `horos-20240407/Preference Panes/OSICustomImageAnnotations/` (.m 11,939 bytes + CIALayoutController.m 54,380 bytes + CIAAnnotation.m 9,600 + CIADICOMField.m 2,423 + CIALayoutView.m 8,127 + CIAPlaceHolder.m 10,235 + mdfconen.cc 30,963 bytes) — **最も複雑なシステムの一つ**
- **Java 実装ファイル**: `PreferencesController.java:433-449` (createAnnotationsPane)

#### HOROS が持つ機能とギャップ（全体的に未実装が激しい）
| # | 機能 | Java 現在 | ギャップ | 優先度 |
|---|------|----------|---------|--------|
| AN-001 | デフォルトアノテーションスタイル (None/Text Box/Arrow/Ellipse/Crosshair) | ✅ JComboBox "Default Style" で実装済。KEY_ANNOTATION_STYLE | なし | - |
| AN-002 | アノテーションカラー選択 | ✅ JComboBox ("White/Black/Red/Green/Yellow") 実装済。KEY_ANNOTATION_COLOR | なし | - |
| AN-003 | デフォルトフォント選択 (Arial/Helvetica/Courier/Times New Roman) | ✅ JComboBox 実装済。KEY_ANNOTATION_FONT | なし | - |
| AN-004 | **モダリティ別アノテーションデフォルト** | ❌ JCheckBox "Allow modality-specific annotation defaults" はあるが、それ以降の詳細設定 UI が欠落。各モダリティ (CT/MR/US...) で独立したデフォルトスタイル/カラーを定義する機能がない | **未実装** — OSICustomImageAnnotations の核心部分 | P1 |
| AN-005 | **DICOM フィールドマッピング（各モダリティごと）** | ❌ CIADICOMField.m + CIALayoutController.m (54KB) で DICOM 項目をアノテーションにマッピングする複雑なシステムが実装済み。CT/MR/US/XA... 等各モダリティごとに "Patient Name / Age / Sex" をレイアウト上に表示するフィールドを選べる | **未実装** — HOROS の Annotation system の根幹。Java 側に DICOM Field Mapping 機構が必要 | P1 |
| AN-006 | **カスタムアノテーションプレビュー (CIALayoutView)** | ❌ CIALayoutView.m (.m 8,127 bytes) でアノテーションプレビューを描画する機能がない。UALayoutController と CIAPlaceHolder と連動 | **未実装** — NSBezierPath に相当する Java2D の実装が必要か、プレビューを static image (tiff) として提供するか判断 | P2 |
| AN-007 | **アノテーションレイアウト設定 (CIASerialLayout)** | ❌ OSICustomImageAnnotations.xib (41KB) に実装されている複雑な Layout パネル。アノテーションを series ごとのレイアウトに配置可能にする | **未実装** — CIALayoutController + CIAPlaceHolder の連動が要実装 | P2 |
| AN-008 | **mdfconen/mdfdsman (DICOM Metadata I/O)** | ❌ mdfconen.cc (.cc 30,963 bytes), mdfdsman.h/cc (~14,171 + 39,761 bytes) は DICOM メタデータの読み書きを行う C++ ライブラリで、アノテーションレイアウトの永続化に使用 | **未実装** — Java 側に DICOM データモデル (DicomDatabase / DicomField) の拡張が必要 | P2 |

### 10. DICOM Print (AYDicomPrintPreferencePane)
- **HOROS 参照**: `horos-20240407/Preference Panes/AYDicomPrintPreferencePane/` (.m 7,536 bytes + .h 2,440 bytes, xib 62KB)
- **Java 実装ファイル**: `PreferencesController.java:450-465` (createDICOMPrintPane)

#### HOROS が持つ機能とギャップ
| # | 機能 | Java 現在 | ギャップ | 優先度 |
|---|------|----------|---------|--------|
| PR-001 | デフォルトプリンタ選択 | ✅ JComboBox "Default Printer/Printer A (Laser)/Printer B (Inkjet)" 実装済。KEY_DICOM_PRINT_PRINTER（ただし HOROS では PrinterName / Resolution を取得して動的にリスト生成する）| **不完全** — hard-coded 3選択肢のみ | P2 |
| PR-002 | イメージレイアウト選択 | ✅ JComboBox "Standard 1,1/2,1/1,2/2,2/3,3" 実装済。KEY_DICOM_PRINT_LAYOUT（ただし HOROS ではもっと多くの pre-defined layout が存在する可能性あり）| **不完全** — プリセット数に差異 | P2 |
| PR-003 | フィルム方向設定 | ✅ JComboBox "Landscape/Portrait" 実装済。KEY_DICOM_PRINT_ORIENTATION | なし | - |
| PR-004 | **印刷前に自動リサイズ** | ⚠️ JCheckBox "Auto-resize window to square size before printing" はあるが KEY_DICOM_PRINT_AUTO_RESIZE が AppPreferences に定義済みか要確認 | ⚠️ キー欠落の可能性あり | P2 |
| PR-005 | **プリンタ設定ダイアログ (Print Setup)** | ❌ 選択したプリンタの詳細（解像度、用紙サイズ、出力品質等）を設定する機能。HOROS には AYDicomPrintPref.m に setPrinterName: 等の IBAction があるが OculusJJ の createDICOMPrintPane に実装がない | **未実装** — Java PrintService API を使用して実現可能 | P2 |
| PR-006 | **印刷サムネイルプレビュー** | ❌ AYDicomPrintPreferencePane.xib (62KB) で印刷イメージのプレビュー表示機能が存在する。Java 側に未 | **未実装** | P3 |

### 11. Listener (OSIListenerPreferencePane)
- **HOROS 参照**: `horos-20240407/Preference Panes/OSIListenerPreferencePane/` (.mm 15,971 bytes — ネットワーク系機能の hub, xib 112KB)
- **Java 実装ファイル**: `PreferencesController.java:468-493` (createListenerPane)

#### HOROS が持つ機能とギャップ
| # | 機能 | Java 現在 | ギャップ | 優先度 |
|---|------|----------|---------|--------|
| L-001 | アプリケーション AETitle | ✅ JTextField "OCULUSJJ" で実装済。KEY_LISTENER_AETITLE + get/setAppPreferences に連動 | なし | - |
| L-002 | リッスンポート番号 (11112) | ✅ JTextField "11112", 5 chars, で実装済。KEY_LISTENER_PORT + getInt(11112) | ⚠️ HOROS では port 104 も併せて管理。AppPreferences の KEY_LISTENER_PORT が1つだけか（2つのポートが必要）| P2 |
| L-003 | **Listener 起動時自動開始** | ✅ JCheckBox "Automatically start listener on launch" 実装済。KEY_LISTENER_AUTO_START + get/setBool で連動 | なし | - |
| L-004 | **複数ポート監聴** (104 / 11112) | ❌ HOROS では ListenerPreferencePane.mm (.mm 15,971 bytes) で port 104(DICOM Standard) と11112(AutoRouting/PACS On-Demand) の両方を管理。Java では11112のみで104 は未 | **未実装** — DicomNodeManager / ListenersService との連動が要実装 | P1 |
| L-005 | **接続タイプ選択 (TCP/IP vs Other)** | ❌ OSIListenerPreferencePane.xib に接続方法設定がある可能性あり。HOROS 準拠で実装する場合は DCM4CHE2 / dcm4che-net の listener API を拡張 | P2 |
| L-006 | **Listener Status 表示** | ❌ リスナーが実際に稼働中かを確認するための "Start/Stop" ボタン + ステータス表示。HOROS には ListenerPreferencePane に Start/Stop が実装されている | ⚠️ DicomService.getInstance() は既に存在するはずなので、リスナ制御のロジックは実装済みかもしれない | P2 |

### 12. Locations (OSILocationsPreferencePane)
- **HOROS 参照**: `horos-20240407/Preference Panes/OSILocationsPreferencePane/` (.m 36,621 bytes — **最重量**, xib 136KB — **最重量 .xib**, ServerTableView.m)
- **Java 実装ファイル**: `PreferencesController.java:507-549` (createLocationsPane)

#### HOROS が持つ機能とギャップ（この pane は OculusJJ の実装が最も小さい）
| # | 機能 | Java 現在 | ギャップ | 優先度 |
|---|------|----------|---------|--------|
| LOC-001 | DICOM ノード一覧表示 (Name/AE/Address/Port) | ✅ DefaultTableModel + JTable で実装済。初期値 "Localhost"/"OCULUSJJ"/"localhost"/"11112" が表示される | ⚠️ HOROS の DNDArrayController と連動したドラッグ&ドロップ並び替えがない。また複数ノードの同期は未実装 | P2 |
| LOC-002 | **ノード追加/編集ダイアログ** (Name/AETitle/Address/Port) | ✅ `showNodeEditor()` で DicomNode を渡す JDialog が実装済。DICOM ノード設定は一通り完備されている | なし | - |
| LOC-003 | **ノード削除** | ✅ JButton "Delete" + table.getSelectedRow() から行を削除するロジックは実装済み | なし | - |
| LOC-004 | **ノード確認 (Echo/DicomService.echo)** | ✅ JButton "Verify" で echo test を非同期スレッドで実行。SwingUtilities.invokeLater 経由で成功/失敗アラートを表示 | ⚠️ HOROS の `echoServer:` はより包括的なチェックを行う可能性あり | P2 |
| LOC-005 | **文字エンコーディング選択 (CharacterSet)** | ❌ characterSetPopup NSPopUpButton が存在するが OculusJJ に未実装。DICOM 文字セットの選択（例: ISO_IR 100, Big5, Shift_JIS）| **未実装** — Java の CharsetEncoder / ICU4J で対応 | P2 |
| LOC-006 | **サーバー管理 (ServerTableView)** | ❌ ServerTableView.m (.m 2,668 bytes) は各 location に複数の DICOM server を追加/削除できる機能。HOROS では "Add Server" / "Remove Server" ボタンで managing servers within locations | **未実装** — OculusJJ の Locations pane にはサーバー管理 UI がなく、1ノード1エントリのみ | P1 |
| LOC-007 | **WADO 設定** (Port/Transfer Syntax/TLS/Username/Password) | ❌ WADOPort, WADOTransferSyntax, WADOhttps, WADOUrl, WADOUsername, WADOPassword が OSILocationsPreferencePanePref.h に定義済みで OculusJJ には未。OSIWebSharingPreferencePane との結合が要実装 | **未実装** — Web Server / Locations pane の連携機能として実装が必要 | P1 |
| LOC-008 | **TLS 証明書管理 (TLSCertificate)** | ❌ TLSAuthenticationCertificate, TLSSupportedCipherSuite が OSILocationsPreferencePanePref.h に定義済み。`chooseTLSCertificate:`, `viewTLSCertificate:` IBAction も実装済み（SFChooseIdentityPanel / SFCertificateView を利用）| **未実装** — Java の KeyStore/TrustManager API との対応が必要 | P1 |
| LOC-009 | **TLS 暗号 suites の選択** | ❌ TLSCipherSuitesArrayController + `selectAllSuites:`, `deselectAllSuites:` メソッド。複数の cipher suite から選択する UI が欠落 | **未実装** — CipherSuiteEnum / TLSConfig との連動が必要 | P1 |
| LOC-010 | **TLS 使用 DH パラメータファイル** | ❌ TLSEnabled, TLSUseDHParameterFileURL, TLSDHParameterFileURL が存在するが OculusJJ に未。TLS 認証に必要な DH parameter file を指定する機能 | **未実装** — PKI / TLS ライブラリの拡張が必要 | P1 |
| LOC-011 | **TLS Certificate Verification Type** | ❌ TLSCertificateVerificationType 列挙型の設定。certificate verification mode（例: Require, Accept, Allow）を選択可能にする機能 | **未実装** — Java の TrustManager を経由して設定する必要あり | P2 |
| LOC-012 | **Node List Load/Save (saveAs/loadFrom)** | ❌ OSILocationsPreferencePanePref.h に `saveAs:`, `loadFrom:` IBAction が定義済み。ノードリストをファイルに保存/ロードする機能がない | ⚠️ 設定のバックアップ・移行には有用だが、実用上は必須ではない可能性あり | P3 |
| LOC-013 | **Bonjour/DNS-SD 自動検出** (searchDICOMBonJourNodes) | ❌ searchDICOMBonJourNodes NSButton が存在するが OculusJJ に未。ネットワーク上の DICOM ノードを Bonjour 経由で自動検出する機能（mDNS/Bonjour Java ライブラリが必要）| **未実装** — mDNS-Java / jmdns で代替可能 | P2 |
| LOC-014 | **OsiriX Server DB からロード/セーブ** (OsiriXDBsaveAs/OsiriXDBloadFrom) | ❌ `OsiriXDBsaveAs:`, `OsiriXDBloadFrom:` で既存の OsiriX/DICOM node 設定をファイルからインポートする機能。他のユーザーの設定を引き継ぐ場合に使われる | **未実装** — XML / plist ファイルの互換性が必要 | P2 |

### 13. Routing/Autorouting (OSIAutoroutingPreferencePane)
- **HOROS 参照**: `horos-20240407/Preference Panes/OSIAutoroutingPreferencePane/` (.m 13,839 bytes + RoutesTableView.m 2,630 + ServerTableView.m 2,888, xib 63KB)
- **Java 実装ファイル**: `PreferencesController.java:512-524` (createRoutingPane)

#### HOROS が持つ機能とギャップ
| # | 機能 | Java 現在 | ギャップ | 優先度 |
|---|------|----------|---------|--------|
| R-001 | DICOM Autorouting ルール一覧表示 | ✅ DefaultTableModel + JTable で実装済。初期値 "Default Store"/"*"/"OCULUSJJ"/"*" が表示される | ⚠️ HOROS の RulesTableView との違いは、HOROS ではドラッグ&ドロップ並び替えやフィルタ設定ができる | P2 |
| R-002 | **ルーティングルール追加** (Add Rule) | ❌ JButton "Add Rule" はあるが、実際のルールエディタ（Source AE, Destination AETitle, Filter 等を入力するダイアログ）は実装されていない。HOROS の `addRoute:` IBAction が相当する部分 | **未実装** — OSIAutoroutingPreferencePanePref.m + RoutesTableView.m (.m 2,630) に相当する実装が必要 | P1 |
| R-003 | **ルーティングルール削除** (Remove Rule) | ❌ JButton "Remove Rule" はあるが、RoutesTableView から選択された行を正しく削除するロジックがない。HOROS の `removeRoute:` メソッド相当 | ⚠️ ボタン表示のみで実質機能なし | P2 |
| R-004 | **Source AE / Destination Filter 設定** | ❌ Source AE (who sends) と Destination AETitle (where to send) を設定する UI が未実装。HOROS では RoutesTableView + ServerTableView の両方を使う複雑なシステム | **未実装** — ルーティングエンジンとの結合が要実装 | P1 |
| R-005 | **Server テーブル表示** (ServerTableView) | ❌ ServerTableView.m (.m 2,888 bytes) は各ルーティング先サーバーの一覧を表示する機能。HOROS では ServerTableView + RoutesTableView の組み合わせでルールとサーバーを管理 | **未実装** — サーバー管理は Locations pane と連携すべきか判断が必要 | P1 |
| R-006 | **自動ルーティングの動作モード設定** | ❌ "Auto-routing" が On/Off 切り替えや、エラー時の動作（キューに入れる/破棄する）の設定がない。HOROS では AutoRoutingRulesTableView + PreferencesWindowController+DCMTK.h と連動 | **未実装** — ルーティングエンジンとの結合が必要 | P1 |

### 14. Web Server (OSIWebSharingPreferencePane)
- **HOROS 参照**: `horos-20240407/Preference Panes/OSIWebSharingPreferencePane/` (.mm 10,851 bytes, xib 127KB — **2番目に大きい .xib**)
- **Java 実装ファイル**: `PreferencesController.java:536-539` (createWebServerPane)

#### HOROS が持つ機能とギャップ
| # | 機能 | Java 現在 | ギャップ | 優先度 |
|---|------|----------|---------|--------|
| W-001 | Web Server Port 設定 | ✅ JTextField "8080", 5 chars で実装済。KEY_WEB_SERVER_PORT + getInt(8080) | なし | - |
| W-002 | WADO サポート ON/OFF | ✅ JCheckBox "Enable WADO" (default true), KEY_WEB_WADO_ENABLED + get/setBoolean | なし | - |
| W-003 | STOW-RS サポート ON/OFF | ✅ JCheckBox "Enable STOW-RS" (default false), KEY_WEB_STOWRS_ENABLED + get/setBoolean | なし | - |
| W-004 | Base URL 設定 | ✅ JTextField "http://localhost:8080", 25 chars で実装済。KEY_WEB_BASE_URL + get/setString | なし | - |
| W-005 | **HTTPS / SSL/TLS 有効化** | ❌ `WADOhttps` BOOL が OSILocationsPreferencePanePref.h に定義されているが、Web Server pane の UI での HTTPS 設定機能は未実装。HOROS の .mm (.mm 10,851 bytes) では TLS/SSL 関連の設定が中心部分である | **未実装** — SSL/TLS のキーペア管理が必要（Keystore）| P2 |
| W-006 | **STOW-RS ファイルアップロード設定** | ❌ Web Server pane に STOW-RS でのファイル受信ディレクトリ、mime type 制限などの詳細設定が未実装。OSIWebSharingPreferencePanePref.h の `stowrsDir:`, `setStowrsDir:` に相当する機能 | **未実装** — ファイル I/O 権限管理も必要 | P2 |
| W-007 | **WADO Query パラメータ** (C-Find equivalent at Study level) | ❌ Patient Name, Date Range, Series Description での C-Find を Web API で実行する設定が未実装。Study Level の C-Find SOPClass 連動設定も欠如 | **未実装** — Web Server と On-Demand (PACS) pane の結合機能 | P1 |
| W-008 | **Base URL の自動補完** | ❌ `http://localhost:8080` のような Base URL を動的に生成する設定（例: external IP / DNS 名）が未実装。HOROS では external URL と internal URL を別個に管理可能にする機能 | P2 |

### 15. On-Demand (PACS) (OSIPACSOnDemandPreferencePane)
- **HOROS 参照**: `horos-20240407/Preference Panes/OSIPACSOnDemandPreferencePane/` (.m 18,885 bytes + Cloud.png 9,857 bytes, xib 75KB) — PACS 検索機能の中核
- **Java 実装ファイル**: `PreferencesController.java:542-560` (createOnDemandPane)

#### HOROS が持つ機能とギャップ
| # | 機能 | Java 現在 | ギャップ | 優先度 |
|---|------|----------|---------|--------|
| OD-001 | **比較 study 自動検索** | ✅ JCheckBox "Search for comparative studies automatically" (default true)。KEY_ONDEMAND_SEARCH_COMPARATIVES に連動する可能性あり（AppPreferences の KEY_ONDEMAND_SEARCH_COMPARATIVES:547）| ⚠️ AppPreferences への保存ロジックが要確認 | P2 |
| OD-002 | **Smart Albums 用 PACS クエリ** | ✅ JCheckBox "Query PACS for Smart Albums" (default false)。KEY_ONDEMAND_QUERY_SMART_ALBUMS に連動 | ⚠️ AppPreferences への保存ロジックが要確認 | P3 |
| OD-003 | **Image Retrieval 設定** | ❌ KEY_ONDEMAND_IMAGE_RETRIEVAL は AppPreferences にある（行563）が、UI で有効/無効にするチェックボックスが未実装。Horos では Image Retrieval が On Demand の核心機能である | **未実装** — PACS Image Retrieval (C-MOVE) 有効化の UI | P1 |
| OD-004 | **PACS Query Timeout 設定** | ✅ JTextField "30", 5 chars で表示は存在する。KEY_PACS_QUERY_TIMEOUT に連動するか要確認（AppPreferences の getInt(KEY_PACS_QUERY_TIMEOUT, 30):行571）| ⚠️ getter/setter が存在するか要確認 | P2 |
| OD-005 | **Max Studies per Query** | ✅ JTextField "100", 5 chars で表示は存在する。KEY_PACS_MAX_STUDIES に連動するか要確認（AppPreferences の getInt(KEY_PACS_MAX_STUDIES, 100):行579）| ⚠️ getter/setter が存在するか要確認 | P2 |
| OD-006 | **Cloud (PACS Cloud / C-Find) 設定** | ❌ Cloud.png (9,857 bytes — icon リソース), PACS On-Demand PreferencePane.m (.m 18,885 bytes) に実装されている Cloud-based C-Find と連携するタブ UI が存在するが OculusJJ の pane は作成されていない。PACS Cloud のサーバー設定、接続方式の選択ができる | **未実装** — HOROS の PACS On-Demand Pane は最大クラスの一つ | P1 |
| OD-007 | **FTP ファイル転送設定** | ❌ FTP で DICOM images をファイルとしてエクスポートする機能。HOROS には FTP server への接続先・認証情報・転送ディレクトリ等の設定がある | ⚠️ 実用性は低いが HOROS 準拠の観点からは含まれるべき | P2 |
| OD-008 | **Modalities level C-Find** (PatientName/Date Range/SeriesDescription) | ❌ Study Level で Patient Name + Date Range + Series Description を使って PACS に C-Find する設定。HOROS では OSIPACSOnDemandPreferencePane.m の core feature | **未実装** — PACS On-Demand pane の中核機能 | P1 |
| OD-009 | **Study level C-Find SOP Class 設定** | ❌ C-Find を Study Level で行うための SOPClass UID の選択。HOROS では OSIPACSOnDemandPreferencePane と連動して SOPClass を管理する仕組みがある | **未実装** — DicomDatabase / DicomField との結合が必要 | P1 |

---

## 優先度別実装ロードマップ

### P0（基本動作が壊れている / リリース blockers）
| # | 機能 | 対象 Pane | 理由 |
|---|------|-----------|------|
| P0-1 | CD/DVD Burning の圧縮モード選択 + naming | CD/DVD Burning | HOROS の Core CD Writing flow と連動する設定が欠落。ディスク書き込みの品質がユーザーに直接影響 |
| P0-2 | PACS On-Demand (PACS Cloud + C-Find) | On-Demand PACS | `Query PACS for Smart Albums` を実質的に機能させるための最低限の設定 UI が欠落 |

### P1（主要機能の未実装または不具合）
| # | 機能 | 対象 Pane | 備考 |
|---|------|-----------|------|
| P1-1 | General: 言語リスト dynamic scan + リセット機能 + AE Title/Transfer Syntaxes | General | 多言語切り替えの core かつリセットは必須操作 |
| P1-2 | Database: Patient UID 生成 (3要素チェック) + Comments Auto-fill + Report Mode | Database | データベース動作と密接。UID 生成方式が既存データの整合性に影響する可能性大 |
| P1-3 | Hanging Protocols: Body Region + Keywords + Default Protocol Selection | Hanging Protocols | 画像表示パターンを決定する機能の root |
| P1-4 | Hot Keys: New Key Capture + Edit Dialog (HotKeyFormatter/TextFieldCell) | Hot Keys | ホットキーエディタの実装は OS アイデンティティ（Carbon Event vs Java KeyEvent）に依存。実装コストに見合う価値あり |
| P1-5 | Locations: Server Table + WADO Settings + TLS Certificate + Cipher Suites | Locations | DICOM Node 管理の hub。ネットワーク接続先設定の complete set が必要 |
| P1-6 | Routing: Rule Add/Remove + Source/Destination Filter + Server Management | Routing | ルーティングロジックの core setting。Source AE -> Destination の結合がないと機能しない |
| P1-7 | Web Server: WADO C-Find Query params at Study level (PACS On-Demand 連携) | Web Server | Web API 経由の C-Find を実質的に機能させるためには必要 |
| P1-8 | Annotations: DICOM Field Mapping + Modality-specific Annotation Defaults | Annotations | Custom Image Annotation の核心部分。HOROS の Annotation system の根幹を成す |
| P1-9 | PET/SUV: SUV Normalization (Body Weight/Lean Mass/BSA) | PET/SUV | SUV 表示の正確な計算には必須設定 |

### P2（拡張・改善 / UI polish）
| # | 機能 | 対象 Pane | 備考 |
|---|------|-----------|------|
| P2-1 | General: JPEG2000 エンジン選択 + AE Title/Transfer Syntaxes 詳細 | General | Java の JPEG2000 サポート状況に依存。外部ライブラリ (OpenJPEG / Kakadu SDK) へのリンクが要検討 |
| P2-2 | General: マウス操作設定 (左/右/中ボタン + スクロール) | General | Mouse action customization。OSIViewerPreferencePane との統合が必要か判断 |
| P2-3 | Database: シリーズ順序 (SeriesOrder) 設定 | Database | DicomDatabase / SeriesDisplayModel と連動する機能。実装上コストが低い割にユーザーエクスペリエンス向上が期待される |
| P2-4 | CD/DVD Burning: CD 暗号化 + Disc Mounted Ask The User Dialog | CD/DVD Burning | セキュリティ強化オプションとユーザビリティ改善 |
| P2-5 | 3D Viewer: VRAM 監視 (MAPPERMODE KVO) + Advanced Rendering Options | 3D Viewer | GPU メモリ表示は Java/JNA の実装に依存。外部依存ライブラリの追加要検討（NvAPI / AMD SDK）|
| P2-6 | Viewers: Secondary Monitor Display Control (O2ScreensPrefsView) | Viewers | JWindow + Screen Geometry で実装可能。Java 側でマルチモニター環境の座標取得が必要 |
| P2-7 | Annotations: Custom Annotation Preview (CIALayoutView) + Layout Controller | Annotations | NSBezierPath に相当する Java2D の実装が要検討。プレビューを static image (tiff) で提供することも可能 |
| P2-8 | DICOM Print: Printer 設定ダイアログ（解像度/用紙サイズ等）| DICOM Print | Java PrintService API との結合が必要。HOROS の AYDicomPrintPref.m の機能に対応 |
| P2-9 | Listener: ポート104 + Status 表示 (Start/Stop) | Listener | DicomNodeManager / ListenersService との連動が要検討。listener が実際に稼働中かのフィードバックがないと問題時の切り分けが難しい |
| P2-10 | Routing: RulesTableView のドラッグ&ドロップ並び替え + Server Table | Routing | Drag-and-drop table 実装コストが高い（Java Swing では CustomRenderer + TransferHandler）|

### P3（優先度低 / コスト対効果が見劣りするもの）
| # | 機能 | 対象 Pane | 備考 |
|---|------|-----------|------|
| P3-1 | General: パスワード保護/自動ロック | General | macOS の Screen Savers / Security Framework との統合が必要。Java での実装コストが高い割に必須性はやや低い |
| P3-2 | Database: 起動時全 DB 消去 + テーブルビューフォントサイズ | Database | デバッグ用途としては有用だが、日常運用では稀な操作 |
| P3-3 | PET/SUV: B/W Inverse CLUT 切替保存ロジック確認 | PET/SUV | UI は実装済みのため保存ロジックの欠落のみ確認すれば良い |
| P3-4 | Locations: Node List Load/Save (saveAs/loadFrom) | Locations | XML / plist 互換ファイルが必要。設定バックアップには便利だが必須ではない |
| P3-5 | Viewers: Dual Screen Settings の advanced options（アライメント・pan mode・2-point measure activation）| Viewers | 複雑な機能で実装コストが高い割にユーザーが日常的に触る頻度は低い |

---

## まとめ：ギャップ全体像

1. **データ層 vs UI 層の非対称性**: `AppPreferences.java` (~603行) には全てのキー定数 + getter/setter が定義済みで、データの「格納先」は十分確保されている。しかし UI 側 (`PreferencesController.java` ~603行) は **全機能のうち概ね20%程度**しか実装を伴っている。この非対称性が最大の問題点。

2. **未実装が激しい pane Top 5**:
   - Annotations (~95% 未実装) — Custom Image Annotation の DICOM Field Mapping が core
   - Routing/Autorouting (~95% 未実装) — ルーティングロジックの hub
   - Hanging Protocols (~95% 未実装) — Body Region + Keywords が core feature
   - On-Demand PACS (~95% 未実装) — Cloud C-Find / FTP Transfer が core
   - Locations (~90% 未実装) — Server Table / WADO / TLS が core

3. **実装上の技術的課題**（実装順序決定時の判断材料）:
   - **JPEG2000 エンジン選択 (General)** → 外部ライブラリ依存 (OpenJPEG / Kakadu SDK)。Java 側に JPEG2000 サポートが既にあるか確認必要。なければ依存追加が必要
   - **VRAM 監視 (3D Viewer)** → GPU メモリ情報を取得する必要があるため、OS 特有の API (NvAPI for NVIDIA, AMD SDK for ATI) を JNA で呼び出す必要あり
   - **DICOM Field Mapping / Metadata I/O (Annotations)** → C++ DICOM library (mdfconen.cc) の Java 移植が必要か、あるいは既存 DicomDatabase / DicomField クラスを拡張するか判断が必要
   - **TLS/SSL セキュリティ (Locations / Web Server)** → Java の KeyStore/TrustManager / CipherSuiteEnum API との統合。X.509 certificate の管理画面が要実装
   - **マルチモニター設定 (Viewers / O2ScreensPrefsView)** → JWindow + GraphicsEnvironment で対応可能だが、座標取得・サイズ制限で OS 依存コードが必要

4. **推奨実装順（実務的な提案）**:
   - **Step A** (P1 系): General (language list, reset) -> Database (UID generation, auto-clean) -> Locations (server table, WADO/TLS) -> Routing (rule editor) -> Annotations (field mapping)
   - **Step B** (残りの P1 + P2): PET/SUV normalization -> On-Demand PACS cloud settings -> Web Server C-Find params -> CD/DVD naming/compression -> Hot Keys editor
   - **Step C** (P3 系 / polish): General advanced options -> Listener port 104 setup -> Viewers dual screen -> Annotations preview -> DICOM Print detailed settings

---

## リファレンス

### HOROS 側参照ファイル（全 Preference Panes）
| Pane | .h ファイル | .m/.mm ファイル | xib ファイル |
|------|------------|----------------|--------------|
| AYDicomPrintPreferencePane | `AYDicomPrintPref.h` (2,440 bytes) | `AYDicomPrintPref.m` (7,536 bytes) | English.lproj / ja-JP.lproj に各1個ずつ |
| OSI3DPreferencePane | `OSI3DPreferencePane.h` (2,463 bytes) | `OSI3DPreferencePane.mm` (3,909 bytes) | English.lproj / ja-JP.lproj に各1個ずつ |
| OSIAutoroutingPreferencePane | `OSIAutoroutingPreferencePanePref.h` + RoutesTableView.h + ServerTableView.h | 計4ファイル (.m: 26-29 KB 合計) | English.lproj / ja-JP.lproj に各1個ずつ |
| OSICDPreferencePane | `OSICDPreferencePanePref.h` (2,103 bytes) | `OSICDPreferencePanePref.m` (3,488 bytes) + DiscMountedAskTheUserDialogController.m | English.lproj / ja-JP.lproj に各1個ずつ |
| OSICustomImageAnnotations | 多数（CIADICOMField.h, CIAAnnotation.h, CIALayoutController.h等） | `OSICustomImageAnnotations.m` (11,939 bytes) + `CIALayoutController.m` (54,380 bytes — **最重量**) + mdfconen.mdffdsman クラス群 | English.lproj / ja-JP.lproj に各1個ずつ（41KB xib）|
| OSIDatabasePreferencePane | `OSIDatabasePreferencePanePref.h` (3,490 bytes) | `OSIDatabasePreferencePanePref.m` (**24,513 bytes — 最重量**) | English.lproj / ja-JP.lproj に各1個ずつ（115KB xib）|
| OSIGeneralPreferencePane | `OSIGeneralPreferencePanePref.h` (2,466 bytes) | `OSIGeneralPreferencePanePref.m` (**16,282 bytes**) | English.lproj / ja-JP.lproj に各1個ずつ（64KB xib）|
| OSIHangingPreferencePane | 多数（BodyRegionArrayController.h等） | `OSIHangingPreferencePanePref.m` (19,819 bytes) + BodyRegionArrayController.m + KeywordsArrayController.m等 | English.lproj / ja-JP.lproj に各1個ずつ（58KB xib）|
| OSIHotKeysPreferencePane | 多数（HotKeyArrayController.h etc.） | `OSIHotKeysPref.m` (12,286 bytes) + HotKeyFormatter.m + HotKeyTextFieldCell.m 等 | English.lproj / ja-JP.lproj に各1個ずつ（13KB xib）|
| OSIListenerPreferencePane | `OSIListenerPreferencePanePref.h` (3,762 bytes) | `OSIListenerPreferencePanePref.mm` (**15,971 bytes**) | English.lproj / ja-JP.lproj に各1個ずつ（112KB xib）|
| OSILocationsPreferencePane | `OSILocationsPreferencePanePref.h` (4,693 bytes) | `OSILocationsPreferencePanePref.m` (**36,621 bytes — 最重量**) + ServerTableView.m | English.lproj / ja-JP.lproj に各1個ずつ（136KB xib）|
| OSIPACSOnDemandPreferencePane | `OSIPACSOnDemandPreferencePane.h` (2,646 bytes) | `OSIPACSOnDemandPreferencePane.m` (**18,885 bytes**) | English.lproj / ja-JP.lproj に各1個ずつ（75KB xib）|
| OSIPETPreferencePane | `OSIPETPreferencePane.h` (2,399 bytes) | `OSIPETPreferencePane.m` (5,545 bytes) | English.lproj / ja-JP.lproj に各1個ずつ（43KB xib）|
| OSIViewerPreferencePane + O2ScreensPrefsView | `OSIViewerPreferencePanePref.h`, O2ScreensPrefsView.h | `O2ScreensPrefsView.m` (10,173 bytes) + `OSIViewerPreferencePanePref.m` | English.lproj / ja-JP.lproj に各1個ずつ（80KB xib）|
| OSIWebSharingPreferencePane | `OSIWebSharingPreferencePanePref.h` (2,805 bytes) | `OSIWebSharingPreferencePanePref.mm` (**10,851 bytes**) | English.lproj / ja-JP.lproj に各1個ずつ（127KB xib）|

### OculusJJ 側参照ファイル
- **全 Preference Key 定数 + getter/setter**: `src/main/java/com/oculus/jj/config/AppPreferences.java` (~603行, ~23KB)
- **Preference Window UI** (sidebar + pane panels): `src/main/java/com/oculus/jj/ui/PreferencesController.java` (~603行, ~30KB) — sidebar に 15 pane 名が列挙されているが、content panel の実装度は panes により大きく異なる
- **Stub / 最小限のみ実装の PreferenceWindow**: `src/main/java/com/oculus/jj/ui/PreferenceWindow.java` (~3.7KB, ~100行) — simple JDialog stub (2 tabs: "3D Viewer" + "General") のみ。PreferencesController に統合済み

### 既存ドキュメント
- [ツールバー差分分析表 - HOROS-20240407 vs OculusJJ](gap-analysis-v2.md)（toolbar focused、Preference Panes 非掲載）
- [OculusJJ 実装ステータスレポート](../oculusjj-status/current-state.md)（class list のみ。Preference Pane gap analysis は未）

---

## 変更履歴

| 日付 | バージョン | 内容 |
|------|-----------|------|
| 2026-07-19 | v1.0 | 初版。全 15 Preference Panes の HOROS vs Java gap analysis を初回作成 |
