# Preference Window ギャップ分析書 — HOROS-20240407 vs OculusJJ (完全版)

> 作成日：2026-07-20（最新）
> 目的：全15 Preference Pane のHOROS-20240407とOculusJJ間の差分を詳細に整理し、実装優先度を付与する。
> **基準ソース**: horos-20240407/Preference Panes/*/ (各paneの .h/.m/.xib)

---

## ギャップ状況サマリー（15 panes）

| # | Preference Pane | 補完率 | HOROS参照 | OculusJJ 現状 |
|:--:|----------------|------:|-----------|-------------|
| 1 | General (OSIGeneralPreferencePane) | ~30% | .m 16,282B / xib 64KB | 言語リスト表示のみ、JP2K/圧縮/AE Title未実装 |
| 2 | Database (OSIDatabasePreferencePane) | ~25% | .m 24,513B / xib 115KB | UID生成/日付フォーマット/サーバー選択未実装 |
| 3 | CD/DVD Burning | ~70% | .m 3,488B + DialogController.m / xib 19KB | 基本機能相当、一部UI補完必要 |
| 4 | Hanging Protocols | ~5% | .m 19,819B / xib 58KB + ArrayControllers | スタブのみ、BodyRegion/Keywords未実装 |
| 5 | Hot Keys | ~20% | .m 12,367B / Formatter / TableView / ArrayController | テーブル表示のみ、バインディング未実装 |
| 6 | Viewers (O2ScreensPrefsView) | ~5% | O2ScreensPrefsView.m 10,173B / xib 80KB | スタブのみ、DualScreen/CLUT/Opacity未実装 |
| 7 | 3D Viewer | ~40% | .mm 3,909B / xibあり | MIP/VR/OAR基本設定のみ |
| 8 | PET/SUV | ~20% | .m 5,545B / xib 43KB | データ層は存在、UIが不完全 |
| 9 | Annotations (OSICustomImageAnnotations) | ~10% | CIADICOMField.h + CIALayoutController.m **54KB** +10サブクラス | スタブのみ、レイアウトエディタ完全未実装 |
| 10 | DICOM Print (AYDicomPrintPreferencePane) | ~30% | .m 7,536B / xib 62KB | プリンタ/レイアウト/方向基本設定 |
| 11 | Listener (OSIListenerPreferencePane) | ~10% | .mm **15,971B** / xib 112KB | AE Title/Port/AutoStartのみ、TLS完全未実装 |
| 12 | Locations (OSILocationsPreferencePane) | ~15% | .m **36,621B**(最重量) / xib 136KB | WADOパラメータ基本、サーバーテーブル/TLSダイアログ不完全 |
| 13 | Routing/Autorouting | ~5% | .m 13,819B + TableViewx2 / xib 63KB | スタブのみ |
| 14 | Web Server (OSIWebSharingPreferencePane) | ~20% | .mm **10,851B** / xib 127KB | ポート/WADO基本、ユーザー管理/研究フィルタ未実装 |
| 15 | On-Demand PACS | ~30% | .m 18,885B / xib 75KB + Cloud.png | サーバー設定なし、検索パラメータのみ |

**全体補完率: ~20%** — Preference Windowが実装ギャップ最大。

---

## AppPreferences.java (データ層) ギャップ

| GAP# | 未設定/不足のキー | 該当pane | HOROS参照 |
|------|------------------|---------|-----------|
| D-001 | JP2KEngine (Kakadu/OpenJPEG/3rd option) | General | OSIGeneral.m: setJP2KWriter:, isKDUEngineAvailable() |
| D-002 | CompressionSettings / CompressionSettingsLowRes (配列JSON保存) | General | OSIGeneral.m: compressionSettingsCopy, endEditCompressionSettings: |
| D-003 | AutoCleanDaysToKeep + 日付タイプ選択(DATEPRODUCED/OCTOBER/COMMENTS等) | Database | OSIDatabase.m: AUTOCLEANINGDATEPRODUCED, etc. |
| D-004 | UID生成方式(Use Patient Name / Birth Date / Patient ID) | Database | OSIDatabase.m: newUsePatientNameForUID, newUsePatientBirthDateForUID |
| D-005 | DB日付フォーマット/生年月日フォーマットリセット | Database | OSIDatabase.m: resetDate:, resetDateOfBirth: |
| D-006 | Use Series Description / Split Multi-Echo MR | Database | OSIDatabase.m: useSeriesDescription, splitMultiEchoMR |
| D-007 | TLS認証証明書(TLSAuthenticationCertificate) | Listener | OSIListener.mm: synthesize TLSSettingsWindow etc. |
| D-008 | TLS暗号化スイート(TLSSupportedCipherSuite) / DHパラメータ/証明書検証レベル | Listener | OSIListener.mm: selectAllSuites:, deselectAllSuites:, chooseTLSCertificate: |
| D-009 | Same AETitle for TLS (TLSStoreSCPAETITLEIsDefaultAET) | Listener | OSIListener.mm: useSameAETitleForTLSListener |
| D-010 | WADO port/URL/TLS/転送構文(UserName, Password) | Locations | OSILocations.m: WADOPort, WADOTransferSyntax, WADOUrl etc. |
| D-011 | サーバーテーブル管理(DicomNodeManager配列保存) | Locations | OSILocations.m: dicomNodes arrangedObjects, TLS settings per server |
| D-012 | Web Portal URL / Custom HTML path | Web Server | OSIWebSharing.mm: defaultWebPortalAddress, copyMissingCustomizedFiles |
| D-013 | ユーザー管理(ユーザー名/パスワードリスト) | Web Server | OSIWebSharing.mm: usersArrayController, WebPortalUser.h |
| D-014 | 研究フィルタテスト機能 | Web Server | OSIWebSharing.mm: smartAlbumHelpButton:, testPredicate |
| D-015 | Hanging Protocol BodyRegion配列 + Keywords配列 | Hanging Protocols | OSIHangingPref.m: modalityForHangingProtocols, convertWLWWToMenuTag |
| D-016 | アノテーションDICOMフィールドマッピング(CIAAnnotation.m等) | Annotations | OSICustomImageAnnotations.h/.m + CIALayoutController.m 54KB |

---

## PreferencesController.java (UI層) パネル別ギャップ

### General Pane — 実装済み: 30%
| ID | 機能 | HOROS仕様 | Java現状 |
|----|------|----------|---------|
| G-001 | **言語スキャン/管理** (resources-disabledからinactive言語自動検出) | contentsOfDirectoryAtPath: で .lproj をスキャン、active/inactive切り替え可能 | hardcoded 2言語のみ(English/Japanese)、動的リスト未対応 |
| G-002 | **JP2Kエンジン選択** (Kakadu/OpenJPEG/両方判定) | setJP2KWriter:, isKDUEngineAvailable()連動で3way選択 | スライダーのみ、JP2Kエンジン判定ロジック欠如 |
| G-003 | **圧縮設定エディタ起動** (compressionSettingsWindow 別Sheet) | -editCompressionSettings: で NSApp beginSheet / compressionSettingsCopy保存 | ボタンなし |
| G-004 | **AE Title & 暗号化/転送構文** (NSPreferencePane+DCMTK準拠) | NSUserDefaults + DCMTKカテゴリでAE Title管理 | AppPreferencesのlistener.aetitleのみ(UIレベル未実装) |
| G-005 | **ネットワークポート設定** (Listener:11112 / AutoRouting:11112 等) | portTextField, TLSStoreSCPAEPORT自動調整ロジック | listener.portのみ、衝突検出ロジック欠如 |
| G-006 | **パスワード保護/自動ロック** | passwordForWindowProtectionInterval: IBAction実装あり | UI未実装 |
| G-007 | **復元/リセット** | 全NSUserDefaults削除 + 警告アラート | ✅ 部分的に実装済み(ただし全設定ではなくkey単位) |

### Database Pane — 実装済み: 25%
| ID | 機能 | HOROS仕様 | Java現状 |
|----|------|----------|---------|
| D-010 | **UID生成方式選択** (PatientName/BirthDate/PatientID) | IBOutlets + NSUserDefaults 連動 | 未実装 |
| D-011 | **DB日付フォーマットリセット** | NSDateFormatterShortStyle + NSUserDefaults保存 | 未実装 |
| D-012 | **自動クリーンアップ詳細** (DATEPRODUCED/OCTOBER/COMMENTS等) | 複数の日付タイプ選択肢 | 未実装(単純なdaysチェックのみ) |
| D-013 | **サーバー選択(DATABASE/DICOM_FILE/REMOTE)** | locationMatrix selectCellWithTag:でDATABASE/ファイル/RIS-REMOTE切替 | 未実装 |

### Listener Pane — 実装済み: 10%
| ID | 機能 | HOROS仕様 | Java現状 |
|----|------|----------|---------|
| L-001 | **TLS認証証明書選択** (SFChooseIdentityPanel + Keychain) | chooseTLSCertificate: IBAction、KeychainAccessCertificatesList | ❌ TLS設定UI未実装 |
| L-002 | **暗号化スイート選択テーブル** (selectAll/deselectAll) | TLSSupportedCipherSuite配列管理、選択状態の保存 | ❌ 未実装 |
| L-003 | **DHパラメータファイルパス** | TLSUseDHParameterFileURL + ファイル選択ダイアログ | ❌ 未実装 |
| L-004 | **ホストIP自動検出** (IPv4 addresses from host) | gethostname() + private IP enumeration + プレースホルダ | ❌ 未実装 |
| L-005 | **TLS StoreSCP AE Title同期** | TLSStoreSCPAETITLEIsDefaultAET + 自動port調整 | ❌ 未実装 |

### Locations Pane — 実装済み: 15%
| ID | 機能 | HOROS仕様 | Java現状 |
|----|------|----------|---------|
| W-001 | **WADOサーバー設定テーブル** (DicomNode配列管理) | dicomNodes arrangedObjects / ServerTableView | ⚠️ DicomNodeManagerは存在するがUI連携不完全 |
| W-002 | **各サーバーに個別TLS設定** | TLS per-serverダイアログ + DHパラメータ/証明書/CipherSuite | ❌ 未実装(Web Server paneの簡易TLSのみ) |

### Web Server Pane — 実装済み: 20%
| ID | 機能 | HOROS仕様 | Java現状 |
|----|------|----------|---------|
| W-010 | **ユーザー管理ダイアログ** (usersArrayController + user edit) | editUsers: IBActionでNSApp beginSheet usersPanel | ❌ ユーザー管理UI未実装 |
| W-011 | **研究フィルタテスト** (testPredicate for SmartAlbum) | smartAlbumHelpButton: で BrowserController displayStudy 連携 | ❌ 未実装 |

---

## 推奨実装順序

### Phase 1 — データ層補完 (AppPreferences.java)
- JP2K Engine, 圧縮設定保存/復元、UID生成方式、DB日付フォーマット、TLS関連キー、WADO/サーバー管理、ユーザー管理データ構造

### Phase 2 — General + Database pane UI（最重要・基本機能）
- 言語スキャン/管理、JP2Kエンジン選択UI、圧縮エディタ統合、AE Title & ポート衝突検出、UID生成方式、DB日付フォーマット

### Phase 3 — Listener + Locations (共有TLS基盤)
- TLS認証設定ダイアログ共通化、暗号化スイート管理テーブル、ホストIP自動検出、WADOサーバー設定テーブル統合

### Phase 4 — Web Server + Remaining panes
- ユーザー管理パネル、研究フィルタテスト、3D Viewer/PET-SUV補完、Hanging Protocols/Hot Keys/Annotations補完

---

## 引用ソース一覧

| Pane | HOROS .m/.mm ファイル | サイズ |
|------|---------------------|--------|
| General | horos-20240407/Preference Panes/OSIGeneralPreferencePane/OSIGeneralPreferencePanePref.m | 16,282 B |
| Database | horos-20240407/Preference Panes/OSIDatabasePreferencePane/OSIDatabasePreferencePanePref.m | 24,513 B |
| Listener | horos-20240407/Preference Panes/OSIListenerPreferencePane/OSIListenerPreferencePanePref.mm | 15,971 B |
| Locations | horos-20240407/Preference Panes/OSILocationsPreferencePane/OSILocationsPreferencePanePref.m | 36,621 B |
| Web Server | horos-20240407/Preference Panes/OSIWebSharingPreferencePane/OSIWebSharingPreferencePanePref.mm | 10,851 B |
| Hanging Protocols | horos-20240407/Preference Panes/OSIHangingPreferencePane/OSIHangingPreferencePanePref.m + BodyRegionArrayController.h/.m etc. | 19,819 B / 合計 |
| Hot Keys | horos-20240407/Preference Panes/OSIHotKeysPreferencePane/OSIHotKeysPref.m + HotKeyFormatter.m + HotKeyTextFieldCell.m | 12,367 B + サブクラス群 |
| Viewers | horos-20240407/Preference Panes/OSIViewerPreferencePane/O2ScreensPrefsView.m + OSIViewerPreferencePanePref.m / xib 80KB | 10,173 B + .m |
| Annotations | horos-20240407/Preference Panes/OSICustomImageAnnotations/OSICustomImageAnnotations.m + **CIALayoutController.m (54,380B)** + mdfconen.mdffdsmanサブクラス群 | 最重量 |
| On-Demand PACS | horos-20240407/Preference Panes/OSIPACSOnDemandPreferencePane/OSIPACSOnDemandPreferencePane.m | 18,885 B / xib 75KB |

---

*最終更新: 2026-07-20 (gap-analysis-new.md 完全版作成)*
