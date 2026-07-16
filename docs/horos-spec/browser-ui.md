# HOROS ブラウザ UI 仕様書

## タイトル: BrowserController.m / ToolbarPanel.xib UI マッピング

## 概要

- 目的: HOROS Browser の NSUserDefaults キーとツールバー項目のマッピング
- 対象画面/機能: ブラウザビューアー全体、ツールバー
- 参照元ファイル: `horos-20240407/Horos/Sources/BrowserController.m`, `horos-20240407/Horos/Resources/en.lproj/ToolbarPanel.xib`

## NSUserDefaults キー一覧（BrowserController.m 基準）

| Key | 型 | 役割 |
|---|---|---|
| `dbFontSize` | int | データベースフォントサイズ |
| `DefaultFolderSizeForDB` | int | データベースデフォルダサイズ |
| `searchType` | int | 検索タイプ (0-4) |
| `HIDEPATIENTNAME` | bool | 患者名非表示フラグ |
| `LASTURL` | string | 最後に使用したWADO URL |
| `clearSearchAndTimeIntervalWhenSelectingAlbum` | bool | アルバム選択時に検索/時間インターバルをクリアするか |
| `AUTOROUTINGACTIVATED` | bool | 自動ルーティング有効 |
| `COMMENTSAUTOFILL`, `COMMENTSAUTOFILLStudyLevel`, `COMMENTSAUTOFILLSeriesLevel` | bool | コメント自動入力フラグ |
| `copySettingsInSeries` | int (0-3) | コピー設定モード |
| `rotationAngle` | float | 画像回転角度 |
| `windowLevel`, `windowWidth` | float | WL/WW設定 |
| `xFlipped`, `yFlipped` | bool | フリップフラグ |
| `xOffset`, `yOffset` | int | オフセット量 |
| `AUTOTILING` | bool | 自動タイリング有効 |
| `AUTOHIDEMATRIX` | bool | 行列自動非表示 |

## ツールバー項目識別子（BrowserController.m 265-289行目準拠）

| Identifier | 対応するJavaクラス/メソッド |
|---|---|
| `Import.pdf` | BrowserController.importItem |
| `QTExport.pdf` | BrowserController.moviePlaybackPanel |
| `Export.pdf` | BrowserController.exportToPACS / JPEG / TIFF |
| `ExportROIAndKeyImages.tif` | exportSelectedROIandKeyImages |
| `Anonymizer.pdf` | anonymizeDICOM |
| `QueryRetrieve.pdf` | QueryController |
| `Send.pdf` | sendMail |
| `Viewer.pdf` | mprViewer / VRViewer / MIPViewer etc. |
| `Movie.pdf` | MoviePlaybackPanel (movie playback) |
| `trash.icns` | deleteItem: |
| `Report.icns` | generateReport |
| `Burner.icns` | BurnerDialog |
| `StartupDisk.tif` | toggleDatabaseOutlineVisibility |
| `Search` | searchField action |
| `TimeInterval` | timeIntervalView selector (0-100) |
| `ModalityFilter` | modality filter button |

## 画面ショット / リソース参照

- BrowserController.xib: `horos-20240407/Horos/Resources/en.lproj/ToolbarPanel.xib`
- BurnerDialog: `horos-20240407/Horos/Resources/en.lproj/BurnViewer.xib`

## 備考 / 実装上の注意点

- HOROS では NSSegmentedControl (BrowserController.m 2831行目) で検索タイプ切り替え
- Java/SwingではJComboBoxで実装（安定版実装準拠）
- ツールバー項目は BrowserToolbar.java の toolbarDefaultItemIdentifiers に完全一致するよう実装済み

## 変更履歴

| 作成者 | 日付 | バージョン |
|---|---|---|
| OculusJJ Team | 2026-07-17 | v1.0 |
