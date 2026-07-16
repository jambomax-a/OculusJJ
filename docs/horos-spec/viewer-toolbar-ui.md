# HOROS ビューア/ツールバー UI 仕様書

## タイトル: Viewer.xib / ToolbarPanel.m UI マッピング

## 概要

- 目的: ビューア表示部の Swing 実装と HOROS の差異特定
- 対象画面/機能: DCMView, ToolbarPanel, ViewerController
- 参照元ファイル: `horos-20240407/Horos/Sources/ToolbarPanel.m`, `horos-20240407/Horos/Resources/en.lproj/Viewer.xib`

## UI 構成（安定版準拠）

| 要素 | HOROS (Obj-C) | OculusJJ (Java) | 実装度 |
|---|---:|---|---|
| BrowserToolbar | NSSegmentedControl | JComboBox/JToolBar | P1: 部分実装 |
| ViewerController | NSWindow / NSToolbar | JFrame / JPanel | P2: 簡易化 |
| ToolbarPanel | NSToolbar | JToolBar (Java) | P0: 実装済 |
| BurnerDialog | NSWindow (BurnViewer.xib) | JOptionPane/JDialog | P1: UI差異あり |

## 挙動・遷移

- ツールバー項目クリック時 → BrowserController がアイテム識別子で判別
- SearchField リアルタイム検索 → HOROS準拠で実装済（安定版時点）
- Time Interval Filter (0-100) →HOROS ブラウザと同様のポップアップセレクタ
- Modality Filter →HOROS 同様、DICOM modality 値によるフィルタリング

## 設定 / ユーザーデフォルト（Viewer/ツールバー関連）

| NSUserDefaults キー | Java キー名 | 説明 |
|---|---|---|
| `windowLevel` | windowLevel | WL 初期値 |
| `windowWidth` | windowWidth | WW 初期値 |
| `rotationAngle` | rotationAngle | 回転角度 |
| `scale` | scale | スケール係数 |
| `xFlipped`, `yFlipped` | xFlipped, yFlipped | フリップ状態 |

## HOROS 準拠で必要な修正点（Diff分析待ち）

- [ ] BrowserToolbar セグメントコントロールのリアルタイム検索動作
- [ ] ToolbarPanel のデフォルトレイアウト
- [ ] Viewer Controller の NSUserDefaults 同期
- [ ] BurnerDialog UI 構造 (xib準拠)

## 変更履歴

| 作成者 | 日付 | バージョン |
|---|---|---|
| OculusJJ Team | 2026-07-17 | v1.0 |
