# メニューおよびツールバー構成定義書 (Java Porting)

## 1. 概要
本ドキュメントは、Horosのビューアにおけるメニュー構造とツールバーの構成を定義する。Java Swingでの `JMenuBar` および `JToolBar` 実装の設計根拠とする。

## 2. メニュー構造 (Menu Hierarchy)

ビューアウィンドウに付随するメインメニューの構成は以下の通り。各項目は `ViewerController` または `AppController` のアクションに紐づく。

### 2.1 File メニュー
- **Open...**: ファイル選択ダイアログを開き、DICOMファイルをロードする。
- **Import...**: フォルダを指定して一括インポートを実行する。
- **Export...**: 現在のシリーズをDICOM形式で書き出す。
- **Print...**: `AYDicomPrintWindowController` を介して印刷設定を行う。
- **Close Window**: 現在のビューアウィンドウを閉じる。

### 2.2 Edit メニュー
- **Undo / Redo**: ROI編集やアノテーションの操作履歴を管理。
- **Copy / Paste**: ROIやアノテーションの複製。
- **Select All**: 全てのROIを選択状態にする。

### 2.3 View メニュー
- **Zoom / Pan**: ズームリセット、パンリセット機能。
- **Window Level/Width**: 
  - `Reset`: デフォルト値に戻す。
  - `Presets`: Brain, Bone, Lung などのプリセット適用。
- **Tiling (タイル表示)**: 
  - `1x1`, `2x1`, `2x2`, `4x1` などのレイアウト切り替え。
  - `Tiled Windows` モードの有効/無効。
- **Full Screen**: 全画面表示への切り替え。
- **Sync**: 同期モード (Absolute, Ratio, Relative, Location) の選択。

### 2.4 Window メニュー
- **Preferences...**: `Preference Panes` を開き、システム設定を変更する。
- **Layouts**: 保存済みのレイアウトを適用する。

## 3. ツールバー (Toolbar Panel) 定義

ツールバーは、頻繁に使用するツールへのクイックアクセスを提供し、同時にウィンドウフォーカスを制御する役割を持つ。

### 3.1 配置ツールボタン (Tool Buttons)
| アイコン | 機能 | 対応ショートカット | 内部アクション |
| :--- | :--- | :--- | :--- |
| 🔍 | **Zoom** | `Z` | `currentTool = ZoomTool` |
| ↔️ | **Pan** | `P` | `currentTool = PanTool` |
| 📏 | **Measure** | `M` | `currentTool = MeasureTool` |
| 🎚️ | **WL/WW** | `W` | `currentTool = WLWWTool` |
| ✍️ | **Text** | `T` | `currentTool = TextTool` |
| 🖱️ | **Selector** | `S` | `currentTool = SelectorTool` |

### 3.2 フォーカス管理ロジック (Focus Management)
ツールバーおよびサムネイルパネルは、以下の通知を監視し、対応するビューアウィンドウを前面に出す挙動を持つ。

- **監視通知**: `NSWindowDidBecomeMainNotification`, `NSWindowDidBecomeKeyNotification`
- **挙動**: ツールバーパネル自体がフォーカスを得た際、現在アクティブな `ViewerController` が管理するウィンドウを `makeKeyAndOrderFront` (Javaでは `toFront()` および `requestFocus()`) する。

## 4. 実装上の注意点 (Java Swing)

1. **動的メニュー更新**: 選択されているシリーズや画像の枚数に応じて、メニュー項目の有効/無効 (Enabled/Disabled) を動的に切り替える必要がある。
2. **アクションの共通化**: メニュー項目とツールバーボタンは、同一の `Action` オブジェクトを共有して実装し、一貫性を確保する。
3. **コンテキストメニュー**: 右クリック時に表示されるメニューは、`JPopupMenu` を使用し、`03-Interaction-Matrix.md` で定義された座標ベースの判定ロジックを適用する。
