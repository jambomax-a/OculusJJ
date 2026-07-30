# ツールバー差分分析表 - HOROS-20240407 vs OculusJJ

> **作成日:** 2026-07-11 | **更新日:** 2026-07-30  
> **目的:** HOROS のツールボタンを OculusJJ に実装した後のギャップ分析  
> **関連ドキュメント:** [PORT_STATUS.md](../../PORT_STATUS.md) — P2 ツールバー項目の全体進捗参照

## 注意
このファイルは `PORT_STATUS.md` と内容が重複しています。ツールバー固有の詳細（ボタン配置順、HOROS ID マッピング）のみを記載し、詳細な実装状況は PORT_STATUS.md を参照してください。

## ステータスサマリー

| カテゴリ | 状態 | 備考 |
|--------|---------|------|
| **Browser Toolbar** | ✔️ 完了 | Search / **ModalityFilter (filter logic実装済)** / **Time Interval Filter**（Time Interval + scroll panel）
| **2D Viewer Toolbar (ToolbarPanel)** | ⚠️ 一部未実装 | Annotations popup に updateAnnotationType メソッド追加したが、一部の項目がダイアログのみのまま |
| **MPR/3D Viewer** | 🔧 実装待ち/プレースホルダ | UIは存在するがロジックの実装が伴っていないものが多い |

---

## ツールバー実装済アイテム一覧

### Browser Toolbar (BrowserToolbar.java) - HOROS準拠ボタン配置順

| 順番 | HOROS ID | ボタン名 | Java現在 | アクション |
|------|----------|---------|---------|-----------|
| 1 | Import.pdf | Import | ✅完了 | importFiles(null) |
| 2 | Export.pdf | Export | ✅完了 | exportSelected() |
| 3 | Mail.icns | Email | ✅完了 | sendMail(this) |
| 4 | Print.tiff | Print | ✅完了 | printDICOM(this) |
| 5 | QueryRetrieve.pdf | Query/PACS | ✅完了 | showPACSQuery() |
| 6 | Send.icns | Send to PACS | ✅完了 | sendToPACS() |
| 7 | Anonymizer.pdf | Anonymize | ✅完了 | AnonymizationDialog.showAnonymization(...) |
| 8 | Burner.icns | Burn CD/DVD | ✅完了 | BurnerDialog.showBurnerDialog(...) |
| -- | XML.icns | XML Export | ✅完了 | exportToXML() |
| -- | Trash.icns | Trash | ✅完了 | moveToTrash() |
| -- | - | _(separator)_ | - | - |
| -- | - | _(flexible space)_ | - | - |
| **NEW** | ROIManager.pdf | ROI Manager | ✅実装済 | roiGetManager(null) |

  Shutter Tool + Reports (HOROS default order)

| -- | SetShutterToolToolbarItemIdentifier | Shutter Popup | ✅実装済 | popup menu: Off/Top/Bottom/Left/Right |
| -- | ReportToolbarItemIdentifier | Reports | ✅実装済 | generateReport(null) |
| -- | NSToolbarFlexibleSpaceItemIdentifier | _(flex space)_ | - | - |

  --- Time Interval / Modality Filter / Search (HOROS default order) ---

| # | HOROS ID | Java現在 | ステータス |
|---|-----------|---------|-----------|
| 12 | TimeIntervalToolbarItemIdentifier | ✅ **filter logic実装済**（不具合報告→修正） | 日付未設定項目のデフォルト値処理 + フィルタ条件分岐改善
| 13 | ModalityFilterToolbarItemIdentifier | ✅ filter logic実装済 | BrowserController + BrowserToolbar連携完成
| 14 | SearchToolbarItemIdentifier | ✅ JTextField + JComboBox | 2026-07-11 追加済 |

| -- | QTSaveToolbarItemIdentifier | Movie Export | ✅完了 | showMovieExport() |

---

## 残ギャップ一覧（未完成項目）

### P0 - Browser Toolbar 表示不具合（実装済→修正済み）

| # | HOROSアイテム | Java現状 | Gap内容 | 優先度 |
|---|--------------|---------|---------|--------|
| 1 | BrowserToolbar レンダリング | **完了** | initializeUIComponents() 内での BrowserToolbar/JScrollPane 初期化コードに 3行の重複宣言 + 変数未定義。browserWindow 外側のフィールド宣言 + JScrollPane HORIZONTAL_SCROLLBAR_ALWAYS + NORTHレイアウトへ修正済み（BUILD SUCCESSFUL） | P0 |
| 2 | BrowserToolbar コンストラクタ | **完了** | BrowserToolbar クラスのコンストラクタで initializeButtons() が呼ばれておらず、ツールバーにボタンが配置されなかった。browserWindow 外側からの new BrowserToolbar() で即座にボタンが表示されるよう修正済み（BUILD SUCCESSFUL） | P0 |

### P1 - UI のみでロジック未実装のもの

| # | HOROSアイテム | Java現状 | Gap内容 | 優先度 |
|---|--------------|---------|---------|--------|
| 1 | Navigator Panel | ✅ UIあり (Stub) | toggleNavigatorPanel() がJDialog表示のみ。実パネルの統合が未 | P1 |

### P2 - Browser Toolbar にまだないHOROSアイテム

| # | HOROSアイテム | Java現状 | Gap内容 | 優先度 |
|---|--------------|---------|---------|--------|
| 1 | Subtraction (HOROS: SubtractionToolbarItemIdentifier) | **未実装** | 複数seriesの減算表示ボタンが未 | P2 |
| 2 | Status Display (HOROS: StatusToolbarItemIdentifier) | **未実装** | Patient/Series/KeyImages status display | P2 |

---

## 実装済みリスト（コンパイル成功時点）

| ファイル | 変更内容 | ステータス |
|---------|---------|-----------|
| BrowserToolbar.java | Search/ModalityFilter/TimeInterval をHOROS準拠で追加 + layout再構成 | ✅完了 |
| gap-analysis.md | P2項目更新（Search/ModalityFilter/TimeInterval 追加済として記録） | ✅完了 |

### ビルド証跡
- ./gradlew compileJava: **BUILD SUCCESSFUL** (最終更新 2026-07-11)
- BrowserToolbar.java: Search / ModalityFilter / TimeInterval 3要素をHOROS準拠で追加（完了）
- gap-analysis.md: P2項目3件を更新
- BrowserController.java: toolbarScroll のフィールド宣言 + initializeUIComponents() 内の重複コード修正 + HORIZONTAL_SCROLLBAR_ALWAYS 設定 + NORTHレイアウト
- エラー数: 0


