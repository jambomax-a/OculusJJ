# Preferences 差分ドキュメント（HOROS実機 vs OculusJJ）

> 作成日: 2026-10-30
> 対象: `src/main/java/com/oculus/jj/ui/PreferencesController.java` の全15ペイン
> 基準: HOROS 20240407 Preferences 実機スクリーンショット（ユーザー提供、15画面）
> 目的: HOROS準拠への移植実装の差分管理。実装順 = Phase 1 → 2 → 3。

凡例:
- `NEW` = 未実装（追加が必要）
- `GAP` = 実装はあるが内容が異なる
- `OK` = 実機と概ね一致
- `(ON)/(OFF)` = 実機スクショの既定の選択状態

---

## Phase 1

### 1. General
| HOROS 実機項目 | OculusJJ 現状 | 差分 |
|---|---|---|
| Reset Preferences / Save Preferences... / Load Preferences... (ボタン3つ) | なし | NEW |
| Require an 'Administrator' authentication to modify preferences settings (OFF) | なし | NEW |
| Synchronize preferences at startup with this url: (OFF, URL欄) | なし | NEW |
| Display Notifications (Grow required) (OFF) | なし | NEW |
| JPEG Compression: Compression Levels: [Configure...] | なし | NEW (JPEG compression levels 編集UIも要検討) |
| For Color JPEG DICOMS, use JPEG photometric value, instead of DICOM photometric value (ON) | なし | NEW |
| Check version: Automatically check for installed Plugins updates at startup time (ON) | `general.checkUpdatesAtStartup`（アプリ更新のみ） | NEW (plugins用キー追加) |
| Check version: Automatically check for Horos application updates at startup time (ON) | `general.checkUpdatesAtStartup` | OK (既存を流用) |
| DICOM Editing: Allow DICOM Editing. See Meta-Data Window. (OFF) | なし | NEW |
| Languages: Enabled/Language テーブル + 複数言語選択 + "Restart to apply..." 補足 | `langCombo`（単一選択のみ） | GAP → テーブル化 |
| （現行のみ）Image Display Type (combo) | 実機Generalに無いが他ペインに相当あり | 維持 or 移動要検討 |
| （現行のみ）Open each selected study in a separate window | 実機Generalに無い（Viewers側概念） | 維持 or 移動要検討 |

### 2. Viewers
| HOROS 実機項目 | OculusJJ 現状 | 差分 |
|---|---|---|
| **Export** Automatically crop the view to the image area (ON) | なし | NEW |
| If there are tiled views, export all tiled views, not only the selected view (ON) | なし | NEW |
| Automatically open the exported images after exporting with Preview or Quicktime Player (ON) | なし | NEW |
| **Screens & Window size** Reserve the database screen for the database (ON) | なし | NEW |
| Use database screen in the last (OFF) | なし | NEW |
| Group series of same study on same screen | なし | NEW |
| Window Size: Fullscreen / 150% / 100% / 200% (ラジオ) | なし | NEW |
| **Tiling** Automatically Tile Windows when closing or opening a new series (ON) | なし | NEW |
| Magnetic Windows for move & resize (ON) | なし | NEW |
| Stretch last windows to fill the whole screen (ON) | なし | NEW |
| Order windows from most recent to oldest studies (ON) | なし | NEW |
| Reversed order | なし | NEW |
| **Workspace State** Automatically save Workspace State when closing all viewers (ON) | なし | NEW |
| Automatically load Workspace State when opening a study (ON) | なし | NEW |
| **Series List** Single Series List per screen (ON) | なし | NEW |
| Automatically showhide the list (OFF) | なし | NEW |
| **12bit Display** Automatically enable 12 Bit Totoku Display support for CR, MQ, DX, DR, XA and RF (OFF) | なし（別メニューに isLUT12Bit 実装あり 193a768） | GAP（キー連携） |
| Compute Skewness and Kurtosis for ROI (OFF) | `viewers.skewKurt` CB + ROI.getSkewness()/getKurtosis() + DicomPix.computeSkewKurt()（2026-10-31実装） | OK（有効化時 ROIテキストBox6行目 "SKEW/KURT" + AnnotationsPanel列） |
| **Scale** Always 'scale to fit' when displaying or resizing windows (ON) | なし | NEW |
| **Misc** Save left bottom mouse tool (OFF) | なし | NEW |
| Reverse scroll wheel direction (ON) | なし | NEW |
| Loop in the series with mouse scroll wheel (OFF) | なし | NEW |
| Zoom with horizontal scrollwheel (OFF) | なし | NEW |
| No 'Propagate settings in series' for: [Modalities] | なし | NEW |
| Apply Next/Previous Series button to all opened viewers (ON) | なし | NEW |
| Use VOI LUT if available (ON) | `viewer2d.useVOILUT`（メニューCBに実装済み 10a10d0系） | GAP（キー連携） |
| Spline Rendering for polygons and pencils ROIs (ON) | なし | NEW |
| New ROIs are unnamed (OFF) | `roi.defaultName` 実装あり | GAP（キー連携） |
| Scroll through image and series for: [Modalities] | なし | NEW |
| Keep CLUT Bars when exporting Screen Captures (OFF) | なし | NEW |
| Compute Skewness and Kurtosis for ROI (OFF) | なし | NEW |
| Use FrameofReferenceUID for cross reference lines (OFF) | なし | NEW |
| Zoom centered around mouse position (ON) | なし | NEW |
| Convert RSTRUCT ROIs to Brush ROIs (OFF) | なし | NEW |
| Don't switch series if already displayed (ON) | なし | NEW |
| Open PDF with Preview application (ON) | なし | NEW |
| No interpolation for zoom (ON) | なし | NEW |
| High Quality Zoom (Lanczos 5) (ON) | なし | NEW |
| Show Magnifying Lens when using 'shift' key (ON) | なし | NEW |
| Mark ROI Image as Key Image (ON) | なし | NEW |
| Only display images of the same patient (OFF) | なし | NEW |
| Full 32-bit OpenGl pipeline (ON) | なし | NEW |
| Select window when using scroll wheel (ON) | なし | NEW |
| Switch to 1x1 Tiling in Fullscreen mode (OFF) | なし | NEW |
| Display Overflow Lines (ON) | なし | NEW |
| Display the Center of Circle ROIs (ON) | なし | NEW |
| ROI color rotation (ON) | なし | NEW |
| Photos Album: [Horos] | なし | NEW (OculusJJ) |
| Default CLUT / Default Opacity Table / Synchronize series（現行のみ） | 実機にこの3項目は無い | 維持 or 統合検討 |

### 3. 3D
| HOROS 実機項目 | OculusJJ 現状 | 差分 |
|---|---|---|
| Best Rendering Button: Ray cast: Super Sampling: 3.50 + Good→Better→Best スライダー | `3d.resolution` スライダー（1-10相当） | GAP（SS値ラベル+3.50既定へ） |
| Default View Size: Square-Full Screen / 512x512 / Full Screen / 768x768 (ラジオ) | なし | NEW |
| Renderer Engine: **CPU - Raycasting** / GPU - Rendering on the graphic processor (ラジオ) | なし（実態はGPU優先+CPUフォールバック） | NEW（エンジン選択キー） |
| Default Shading value: On / **Off** | `3d.shading`（既定true） | GAP（既定Offに） |
| If there are multiple screens, open the 3D window on a different screen of the 2D Viewer (OFF) | なし | NEW |
| Auto-Rotate objects after 3 min. in Volume Rendering/MIP viewers. (ON) | なし | NEW |
| Use Spline for Scissors ROI (OFF) | `3d.splineForScissorsROI` | OK |
| For representing 3D ROIs, use this algorithm: **Iso Cuntour** / Delaunay | `3d.roiAlgorithm` (Iso/Delaunay) | OK |
| Shading Algorithm: Phong/Gouraud/Metallic（現行のみ） | 実機に無い | 維持 or 廃止検討 |
| Default ROI Color（現行のみ） | 実機に無い（実機はROI color rotationがViewers側） | 維持 |

### 4. PET
| HOROS 実機項目 | OculusJJ 現状 | 差分 |
|---|---|---|
| CLUT: Use "B/W Inverse" CLUT with this CLUT for blending: [PET] | `pet.clutBwInverse`（radio） | GAP（対象CLUT選択combo追加） |
| Use this CLUT for PET, including for blending: [PET] | `pet.defaultCLUT`（combo） | GAP（radiobuttonize） |
| Apply this behavior also to NM modality | `pet.applyToNMModality` | OK |
| Opacity Table: Apply this Opacity Table when viewing a PET: [Log...] (ON) | `pet.defaultTransferFunction`（combo, checkboxなし） | GAP |
| Mouse Windowing mode: Classic / **Fixed Minimum (X: nothing Y: maximum) with minimum at: 0** / Maximum-Minimum / Apply to NM | なし | NEW |
| Window Level & Width: **Use stored WL & WW** / Use fixed levels, in percentages: from 0 to 100 / Use fixed levels, in SUV: from 0 to 6 | なし | NEW |
| Fusion: Automatically fuse PET and CT series for PET-CT study. (ON) | `fusion.mode/weight`（別画面で実装） | NEW（自動fusedフラグ） |
| Default fusion type: [Linear] | `fusion.mode`（COLOR系） | GAP（enum追加） |
| SUV Computation: Automatically convert PET in SUV when loading, if possible. (OFF) | SUV手動メニューのみ | NEW |
| Use for Acquisition Time parameter: [SeriesTime (0008,0031)] | なし | NEW |
| 補足: Dose is corrected by the half-life...（説明文） | なし | NEW（説明文） |

---

## Phase 2

### 5. Database（全項目 NEW。現行は folder 選択4項目のみ）
- **File Management**: Default/Custom DB location + Select; "Import DICOM file format only (ignore JPEG, TIFF, LSM, XnAna...)"; "Validate files before importing from CD/DVD or external drives (crash test)"; "Copy files to Horos Data folder if: Always / If files aren't located on the HD / **Ask the user**"; "Use DICOMDIR structure when exporting to filesystem"
- **Database Display**: Date & Time Format `y/MM/dd, H:mm` + Reset; Date Only Format `y/MM/dd` + Reset; Font Size: Regular; Patient's age: Both, if different; "Always display all studies of each patient"(ON); "Display patient's names and descriptions with Capitalized Words"; "Clear Search field (in the toolbar), when changing current album"; "Display the History Drawer horizontally"
- **Patient Unique Identifier**: "Use following fields to generate Patient UID:" Patient ID / Patient BirthDate / Patient's Name (3CB); 補足 "Changing these settings will recompute all database patientUIDs..."
- **Comments & Status fields**: "Store them in DICOM fields"(ON); "Load them from DICOM files fields"; "DICOM Field(s) to fill: Comments ▾"; Group/Element + [Regenerate]; Level: Study/Series
- **Series Parsing**: "Split Multi-echo MR"; "Split 4D Cardiac CT"; "For US: one series per file"; "For CR, DR, DX, CT and RF: Combine/Split(S)"; "Gather localizer images with Series Description:" + `localizer,socal,survey,locator,tracker`; "List of SOP Class UIDs displayed [Disp...]"; "List of SOP Class UIDs stored [Store...]"
- **Sort Series by**: **Series ID** / Series Acquisition Time / Instance Number
- **Sort Images by**: **Ascending Slice Location** / Descending Slice Location / Ascending Date / Descending Date
- **Database Auto-Cleaning**: "Delete studies at these conditions:" 補足 + "Acquisition date is older than: Three months"; "Not opened during last: Three months"; "Only if 'comments' field: contains / doesn't contain:" + 入力; "If HD free space is less than: 1 GB" → delete: **Least recently created** / added / opened; "If image files are linked files..., also delete the original file"; "Don't delete a study, if there are comments..."; "Don't delete a study, if it is stored in an album..."; "Automatically delete the ENTIRE default database, at each startup..."
- **Reports**: "Create reports using: Pages (pages)"; "Automatically create a DICOM PDF file of the report, when status is changed to 'validated'"

### 6. CD/DVD
| HOROS 実機項目 | OculusJJ 現状 | 差分 |
|---|---|---|
| When a disc is inserted: **Copy all its contents into the database** / Show disc content as a separate source / Ask the user / Ignore it (ラジオ) | `cddvd.autoImportFromInsertedDiscs`(CB) | GAP（ラジオ4択へ） |
| Eject the disk when done copying (ON) | `cddvd.ejectAfterCopy` | OK |
| Automatically select the source when ready (ON) | `cddvd.autoSelectSource` | OK |
| Determine disc content by considering: All DICOM files on disk (Scan Mode), ignoring DICOMDIR (Slower) / **Files indexed in the DICOMDIR file (Faster)** (ラジオ) | なし | NEW |
| If no DICOM files are found, switch to Scan Mode (ON) | なし | NEW |
| Writing: Prefer standard JPEG instead of JPEG2000/JPEG LS (OFF) | `cddvd.preferStdJPEG`（既定true） | GAP（既定OFF） |
| Include contents of this folder: `~/Documents > FolderToBurn` | `cddvd.supplementaryFolder` | OK |

### 7. Protocols（Hanging Protocols）
| HOROS 実機項目 | OculusJJ 現状 | 差分 |
|---|---|---|
| Modality dropdown + [New] | `hanging.defaultModality` | GAP（New/編集テーブル） |
| プロトコルテーブル: Study Description (0008,1030) / Windows Tiling / Image Tiling / Previous Study / Sync / Propagate / WL/WW（Default行） | なし | NEW |
| Windows: If not enough series for number of windows, repeat series | なし | NEW |
| Previous Study: Same Modality / Same Description (CB); Display last study, if possible (combo); Display 1 series per previous study (combo) | なし | NEW |
| Series Order: 多行テキスト（"Sort series according to their Series Description field, Use commas to separate terms."） | なし | NEW |
| Ignore case sensitivity | なし | NEW |
| （現行のみ）Default WL/WW Preset combo | 実機はテーブル列に存在 | 統合 |

### 8. Locations
| HOROS 実機項目 | OculusJJ 現状 | 差分 |
|---|---|---|
| DICOM Nodes テーブル: [✓] Address/AETitle/Port/Q../Retrieve/Send/TLS/Name/Send Transfer Syntax | Name/AETitle/Address/Port のみ | GAP（列増: Q&Retrieve/Send/TLS/Transfer Syntax, All/None, Save/Load, Delete key対応） |
| [All][None] / [Save...][Load...][Verify] / [Add new node] | Add/Delete/Verify | GAP |
| Automatically sync the DICOM Nodes list from this URL + リフレッシュ | なし | NEW |
| For C-GET and C-MOVE, try to retrieve images, at IMAGE level (instead of STUDY or SERIES level) | なし | NEW |
| Restart DICOM Auto Query & Retrieve settings, at launch | なし | NEW |
| Search for other DICOM Nodes through Bonjour protocol (ON) | なし | NEW |
| For C-FIND, support status (0x4008,0x0212) and comments (0x0032,0x0400; 0x0020,0x4000) fields (ON) | なし | NEW |
| Text encoding: Western European: ISO_IR 100 | なし | NEW |
| Network DB servers (tcp/ip port 8780): Address/Description テーブル + Save as/Load from/Add new server | なし | NEW |
| Automatically sync this list from this URL: http://list.dicom.dcm/OSirXDBs.plist | なし | NEW |
| Turn Off Bonjour searching protocol - Do not search for Bonjour Horos Databases | なし | NEW |
| Local DB files/folder: Local Path/Description + [Add a local path] | なし | NEW |

### 9. Listener
| HOROS 実機項目 | OculusJJ 現状 | 差分 |
|---|---|---|
| Activate DICOM listener when Horos is running (ON) | `listener.autoStart` | OK |
| AETitle: HOROS; Use Host Name for AETitle | `listener.aetitle`（既定OCULUSJJ） | OK/GAP（既定値） |
| Port Number: 11112 (between 1 and 131072) | `listener.port` | OK |
| Address(es): 192.168.24.18 [Edit]; Host Name: YasuyukiInoMac [Edit] | なし | NEW |
| Preferred Syntax: Explicit Little Endian (used during CQR Retrieve) | なし | NEW |
| Store Destination AETitle in PrivateInformationCreatorUID (0002,0100) | なし | NEW |
| Store Source AETitle in SourceApplicationEntityTitle (0002,0016) | なし | NEW |
| Activate the DICOM Listener only if this user session is active (user switching) | なし | NEW |
| Time-out for DICOM communications: 40 seconds; Connection time-out: 10 seconds | なし | NEW |
| Activate DICOM TLS Listener + [TLS Settings] | なし | NEW |
| Incoming files: Check for new files every: 3 seconds | なし | NEW |
| Don't modify / **Decompress compressed images** / Compress non-compressed images with JPEG (ラジオ) | なし | NEW |
| If Horos is not able to open a file received by the DICOM listener: **Delete it** / Move it to the NOT READABLE folder | なし | NEW |
| Replace existing files with the newly received files | なし | NEW |
| Other options: Activate Horos database sharing, and publish it using the name: [YasuyukiInoMac] | なし | NEW |
| Publish it with a password | なし | NEW |
| Publish my DICOM node over the network through the Bonjour protocol | なし | NEW |
| Incoming and shared files are always linked to the default database | なし | NEW |
| Generate logs for DICOM Network events; Keep logs for: 1 week | なし | NEW |
| Server mode (don't display errors messages, hide Database window at startup) | なし | NEW |
| Activate C-GET SCP support (ON) | なし | NEW |
| Activate C-FIND SCP support | なし | NEW |
| Activate the URL support and HTTP XML-RPC Server on port: 8080 | なし | NEW |
| Also search on PACS- On-Demand servers for [horos://, eairo://] URLs and XML-RPC orders | なし | NEW |
| Automatically close all 2D Viewers before displaying studies with XML-RPC orders | なし | NEW |

---

## Phase 3

### 10. Annotations
| HOROS 実機項目 | OculusJJ 現状 | 差分 |
|---|---|---|
| Modality: [Default ▾] + [Reset] + [+][-] Add/Remove Annotation + Orientation(ON) | `annotations.modalitySpecificDefaults` | GAP |
| 編集キャンバス: 8プレースホルダ枠（左上: Image Size/View Size/WL/WW/Mouse(px)/Mouse(mm); 上中央/左中央/右中央/下中央: Orientation; 右上: Patient ID/Study Description/Series Description/Series ID; 左下: Zoom&Angle/Image Position/Compression/Thickness&Location; 右下: MRI/Field Strength/Image Acquisition Date）+ "Drag Annotations in the place holders" | なし（Style/Color/Fontのみ） | NEW（ドロップ&ドロップUI） |
| [Save] / [Load] ボタン | なし | NEW |
| Selected Annotation Title: [input] | なし | NEW |
| Content: [textarea] | なし | NEW |
| 項目ソース: DICOM fields (combo) / Custom DICOM field (Group/Element/Name) / Database fields (combo) / Other infos (combo) | なし | NEW |
| Default Style/Color/Font（現行のみ） | 実機に無い | 廃止検討 |

### 11. DICOM Print
| HOROS 実機項目 | OculusJJ 現状 | 差分 |
|---|---|---|
| Automatically resize window to a 'square size' before printing (ON) | `dicomPrint.autoResizeWindow` | OK |
| DICOM Printers テーブル: Printer/Host/Port/AETitle + [+][-] set as default + [Save as...][Load from...] | `dicomPrint.defaultPrinter` combo | GAP（テーブル化） |
| Settings: Film Orientation: Portrait; Film Size: 8 IN x 10 IN; Film Destination: Processor; Magnification Type: NONE; Trim: NO; Configuration Information: No Selection | orientation のみ | NEW（combo5つ追加） |
| Layout: Format: Standard 1:1; Automatically; Border Color: BLACK; Empty Image Color: BLACK | `dicomPrint.layout` combo | GAP |
| Jobs: Priority: HIGH; Medium: Bias Film; Copies: No S | なし | NEW |

### 12. Routing
| HOROS 実機項目 | OculusJJ 現状 | 差分 |
|---|---|---|
| Activate AutoRouting (OFF) | なし | NEW |
| Display an error if an autorouting rule failed (ON) | なし | NEW |
| "Horos will automatically send data added in the database according to the following routes:" | なし | NEW |
| ルートテーブル: Activated / Routes / Destination / Comments（空） | Rule/Source AE/Destination/Filter（Default行あり） | GAP（列名・既定行） |
| Double-Click on a route to edit filter parameters / Press Delete key to delete a route | なし | NEW（UX文言） |
| [Add new route] | [Add Rule][Remove Rule] | GAP（Add new route + Delete key） |

### 13. Web Server
| HOROS 実機項目 | OculusJJ 現状 | 差分 |
|---|---|---|
| Activate the built-in Web Server (OFF) | なし | NEW |
| Public address: [YasuyukiInoMac.local] (Edit); Local port: 3333 | `webserver.port`（既定8080） | GAP |
| Secure (HTTPS) connections with certificate: [Choose...] | なし | NEW |
| Options: Activate WADO Server at /wado (ON) | `webserver.wadoEnabled` | OK |
| Only accept WADO request from identified user (ON) | なし | NEW |
| Authorize Weasis & Horos as web viewer (ON) | なし | NEW |
| Prefer Flash over QuickTime movies for series viewing (ON) | なし | NEW |
| Maximum number of frames for movies (Quicktime or Flash): 400 frames | なし | NEW |
| Minimum width for movies / still images: 512 pixels | なし | NEW |
| Maximum width for movies: 880 pixels | なし | NEW |
| Maximum width for still images: 880 pixels | なし | NEW |
| Generate Log Events (see network menu) | なし | NEW |
| Use template at `~/.library/Application Support/Horos/WebServices/HTML/` + [Create missing files] | なし | NEW |
| Require user authentication + [Edit users]; Timeout: 30 minutes | なし | NEW |
| Admin users can delete studies or series | なし | NEW |
| Authorize users to restore password by email (see below) (ON) | なし | NEW |
| Duration for temporary user: 60 days | なし | NEW |
| Emails: Email Engine: **Mail.app** / Built-in SMTP; Email Address: [ ]; Issue automatic notifications when adding studies, send them every: 1 minutes | なし | NEW |
| STOW-RS / Base URL（現行のみ） | 実機に無い | 維持 or 廃止 |

### 14. On-Demand
| HOROS 実機項目 | OculusJJ 現状 | 差分 |
|---|---|---|
| Activate PACS On Demand (OFF) | なし | NEW |
| DICOM Nodes for PACS On Demand matching and retrieving: テーブル + "See Locations Preferences..." + "Drag sources into the priority order" | なし | NEW |
| If a distant study has more images than a local study, display the distant study (ON) | なし | NEW |
| Automatically retrieve studies that have already some local images (ON) | なし | NEW |
| Use PACS On Demand, when using the toolbar search fields (ON) | なし | NEW |
| Activate search for following Smart Albums, with Q&R Date & Modality filters: テーブル（Just Acquired✓/Last hour, Just Added/Any date, Today CR✓/Today/CR, Today CT✓/Today/CT, Today MG✓/Today/MG）+ "Double-Click on a line to edit..." | `ondemand.querySmartAlbums`(CB) | GAP（テーブル化） |
| Maximum number of background auto-retrieve requests per server: 2 | `ondemand.maxBackgroundRequests` Spinner(1-6) + QueryController.moveStudiesWithLimit()（Semaphore並行制御, 2026-10-31実装） | OK（smart album auto-retrieve と partial-local auto-retrieve 両方に適用） |
| Activate PACS On Demand for following Web Server functions: Comparative Studies (Study level) / Search function / Albums | `ondemand.searchComparatives`(CB) | GAP |
| Query Timeout / Max Studies（現行のみ） | 実機に無い（実機は背景req制限） | 維持 or 統合 |

### 15. Hot Keys — **現行維持（ユーザー確認済み）**
- 現行: 読み取り専用テーブル + Add/Edit/Remove ボタン。
- 実機の Special Keys (Double-Click / +Alt / +Cmd) は今回スルー。

---

## 実装の方針・共通事項
1. **UIは実機に忠実**：セクション枠（JSectionBox相当）、ラジオ/チェック/テーブル/スライダーの配置を実機準拠で組む。
2. **`AppPreferences`**：未定義キーを追加（`KEY_*` 定数 + get/set 一対）。既定値は実機スクショの選択状態（`**太字**`=選択中）を優先。
3. **i18n**：`prefs.*` キーを `messages_en.properties` / `messages_ja.properties` 両方に追加（日本語訳は HOROS の日本語ロケールを参考）。
4. **機能実装の範囲**：設定UI + 永続化 + 既存コードとのキー連携（`useVOILUT` / `roi.defaultName` / `isLUT12Bit` / `fusion.*` など既存キーへの統一）を優先。未実装の機能本体（Bonjour 検索、XML-RPC サーバ等）は設定値を読み取る側を後続タスクで対応。
5. **検証**：各Phase完了後 `./gradlew compileJava` + `./gradlew test`（あれば）。

## 実装進捗
- [x] 差分ドキュメント作成
- [x] Phase 1 UI: General / Viewers / 3D / PET（設定UI・ローカル永続化）
- [x] Phase 2 UI: Database / CD/DVD / Protocols / Locations / Listener（設定UI・ローカル永続化）
- [x] Phase 3 UI: Annotations / DICOM Print / Routing / Web Server / On-Demand
- [x] Phase 1・2 `./gradlew compileJava`
- [x] Phase 3 `./gradlew compileJava` / `./gradlew test`

注: UI設定値は永続化されますが、Bonjour探索、HTTP XML-RPC、Web Server機能や各種画像挙動との連携は実処理側への接続が残っています。TLS証明書やWebユーザー管理、外部テンプレート生成など一部ダイアログも未実装です。

## Runtime Integration Progress (2026-10-30)
Connected to runtime behavior:
- 2D wheel direction and within-series looping (`DCMView.scrollWheel`).
- Mouse-centered zoom and zoom interpolation quality (`DCMView`).
- VOI LUT parsing/application when the Viewers preference is enabled (`DicomPix`).
- Unnamed ROI creation (`DCMView`).
- PET fixed-minimum / minimum-maximum mouse windowing, including optional NM behavior (`DCMView`).
- PET/CT automatic fusion after opening series, using the existing fusion processor; fusion type selection now persists.
- Automatic/manual SUV display conversion for BQML PET, using patient weight, administered dose, half-life, and acquisition/injection times; raw pixels remain unchanged.
- 3D CPU/GPU selection, super-sampling quality, shading, default window size, and idle auto-rotation (`Viewer3DController`).
- Database Series/Image sort preferences (`DicomStudy` / `DicomSeries`).
- Database Auto-Cleaning evaluates enabled criteria together, protects regular-album/commented studies, deletes managed files, and only deletes linked originals when explicitly enabled (`DicomDatabase`).
- Database DICOM-only filtering and copy/ask/external-volume import policy, with copied files placed in managed storage (`BrowserController` / `DicomDatabase`).
- Folder imports prefer DICOMDIR-referenced files when configured; empty/invalid references fall back to Scan Mode only when enabled (`DicomDirectoryUtils`).
- Patient UID field selection, deterministic UID generation, and per-database legacy UID migration (`DicomDatabase`).
- Scale-to-fit behavior on 2D window resize (`DCMView`).
- DICOM listener auto-start, AETitle, and port (`OculusJJApp` / `DicomService`). The listener checkbox starts or stops the running service.
- Listener replacement policy controls duplicate SOP Instance handling; listener check interval polls the INCOMING directory, while C-STORE receives still trigger immediate import (`DicomStoreSCP` / `DicomService`).
- DICOM Editing preference gates editable text/numeric tags in Metadata Window; writes use a temporary file, and identity UIDs/File Meta-less files are protected (`DicomMetadataDialog`).
- On-Demand toolbar search invokes PACS C-FIND; All Fields queries supported study-level attributes and merges results by Study UID. Partial local studies can trigger background C-MOVE when On-Demand is enabled (`BrowserController` / `QueryController`).
- Enabled Smart Albums issue C-FIND with Study Date/Modality filters; Last hour results are filtered by Study Date/Time. Auto-retrieve rows C-MOVE only when the remote image count exceeds the local count (`QueryController`).
- Enabled AutoRouting rules send newly imported DICOM images through C-STORE to a destination node matched by name, AETitle, or host:port. The filter field supports `*` or semicolon-separated `key=value` conditions for modality, patientID, studyDescription, seriesDescription, accessionNumber, and studyUID (`DicomDatabase`).
- Built-in HTTP server lifecycle follows the Web Server preference and serves local DICOM instances over `/wado` by Study/Series/SOP Instance UID; TLS/auth-required modes fail closed until configured (`WebServerService`).
- General preferences reset and XML import/export.

## Runtime Integration Progress (2026-10-31)
Connected to runtime behavior:
- Viewers "Compute Skewness and Kurtosis for ROI" (`viewers.skewKurt`):
  `DicomPix.computeROIStatistics` が合計値/平方和/三階積矩/四階積矩を1パスで集計し、
  `DicomPix.computeSkewKurt`（標準化3/4階積矩、静的メソッドで単体テスト可能）から
  歪度・尖度を算出。`ROI.setSkewKurt/getSkewness/getKurtosis` に格納し、
  `viewers.skewKurt` が有効でSD>0の場合、ROIテキストBoxの6行目に
  `SKEW: %.3f KURT: %.2f` を表示、AnnotationsPanelに Skew/Kurt 列を追加
  （i18n `annotations.col.skew/kurt` を en/ja 両方に追加）。
  `DicomPixTest` に4ケース追加（対称分布・右歪検出・定数サンプル・空入力）。
- On-Demand "Maximum number of background auto-retrieve requests per server":
  UIを `preferenceSpinner`（1-6, 既定2）に置き換え（以前は `preferenceCombo` で
  int/値混在の潜在バグを解消）。`QueryController.maxAutoRetrievePerServer()`
  で読み込み、`moveStudiesWithLimit`（Semaphore + CompletableFuture）で
  smart album の自動C-MOVEと partial-local 自動C-MOVEの両方を
  サーバー毎の最大並行数で制御（従来は逐次実行）。失敗一覧も両経路で反映。
- DICOM Print (`dicomPrint.*`):
  `DicomService.printDICOMImages` で Basic Grayscale Print Management Meta SOP Class
  (`N-CREATE BasicFilmSession` → `N-CREATE BasicFilmBox` → `N-SET BasicGrayscaleImageBox` →
  `N-ACTION BasicFilmBox Print` → `N-DELETE`) を実装。`dicomPrint.printers` からデフォルト
  プリンタを解決し、`dicomPrint.priority`/`medium`/`filmDestination`/`copies`、
  `filmOrientationIndex`/`filmSize`/`magnification`/`trim`/`borderColor`/`emptyColor`/`configuration`、
  `layoutFormat`/`automaticLayout`、`autoResizeWindow` を `BrowserController.printDICOM` に接続。

Still requiring real service/domain integration:
- SUV conversion intentionally refuses CNTS, missing acquisition/injection timing for START/NONE, and invalid dose/weight/half-life rather than displaying a potentially incorrect value. Validate against representative vendor PET datasets before clinical use.
- Import crash-test validation, automatic disc insertion detection/handling, and hanging-protocol application.
- Listener TLS, per-user session gating, inbound decompression/JPEG transformation, Bonjour publishing, C-FIND SCP, and XML-RPC.
- Locations network/local database discovery and sync; On-Demand distant-study substitution.
- Web Server HTTPS/TLS, user authentication/administration, notifications, and email delivery. WADO returns 401 when identified-user/authentication is required because user management is not implemented.
- Remaining viewer export/display toggles (crop-on-export, tiled-view export, open-after-export, CLUT bar retention on screenshot).

Tests: `DicomPixTest` covers VOI LUT input clamping, 8/16-bit scaling, MONOCHROME1 inversion, SUV weight/dose scaling, half-life correction, invalid parameter rejection, Patient UID field selection/compatibility, skew/kurt moment computation (symmetric distribution, right-skew detection, constant samples, empty input), and DICOM Print attribute builders (Film Session / Film Box / Grayscale Image Box). `DicomDatabaseTest` covers routing wildcard matches. `DicomDirectoryUtilsTest` covers referenced-file resolution and traversal rejection. `WebServerServiceTest` exercises HTTP startup and WADO request validation. Full `./gradlew test` passes.
