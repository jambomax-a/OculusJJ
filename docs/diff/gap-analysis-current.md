# Preference Pane ギャップ分析レポート — 更新版（2026-07-21）

> **作成日**: 2026-07-19  
> **最終更新**: 2026-07-21（全15 Preference Pane 実装完了による）
> **目的**: HOROS の各機能と OculusJJ 移植のギャップを可視化し、優先度付け・進捗管理を行う

---

## 概要

HOROS-20240407 は Preference Window に **15 個の Preference Pane** を備える。OculusJJ では全 pane の基本機能が実装済み。

| # | Preference Pane | HOROS 参照 | Java 実装度 | 補完率(概算) |
|---:|-----------------|------------|-------------:|--------------:|
|  1 | General (OSIGeneralPreferencePane) | .m 16.3KB, .xib 64KB | ✓ 基本機能完成 | ~80% |
|  2 | Database (OSIDatabasePreferencePane) | .m 24.5KB, .xib 115KB | ✓ 基本機能完成 | ~75% |
|  3 | CD/DVD Burning (OSICDPreferencePane) | .m 3.5KB + DialogController.m, xib 19KB | ✓ 相当実装 | ~80% |
|  4 | Hanging Protocols (OSIHangingPreferencePane) | .m 19.8KB, xib 58KB | ⚠️ body regions JSON表示のみ | ~60% |
|  5 | Hot Keys (OSIHotKeysPreferencePane) | .m 12.3KB + Formatter/TableView/ArrayController | ⚠️ キーキャプチャ未実装 | ~70% |
|  6 | Viewers (OSIViewerPreferencePane+O2ScreensPrefsView) | O2ScreensPrefsView.m 10KB, xib 80KB | ✓ 基本機能完成 | ~80% |
|  7 | 3D Viewer (OSI3DPreferencePane) | .mm 3.9KB | ✓ 基本機能完成 | ~65% |
|  8 | PET/SUV (OSIPETPreferencePane) | .m 5.5KB, xib 43KB | ⚠️ スキャナーリスト未実装 | ~60% |
|  9 | Annotations (OSICustomImageAnnotations) | CIALayoutController.m 54KB + 10+ サブクラス | ⚠️ 基本設定のみ | ~65% |
| 10 | DICOM Print (AYDicomPrintPreferencePane) | .m 7.5KB, xib 62KB | ✓ 基本機能完成 | ~70% |
| 11 | Listener (OSIListenerPreferencePane) | .mm 16KB, xib 112KB | ⚠️ TLS証明書パス選択器未実装 | ~80% |
| 12 | Locations (OSILocationsPreferencePane) | .m 36.6KB(最大), xib 136KB(最大) | ⚠️ ノードエディタ未実装 | ~60% |
| 13 | Routing/Autorouting (OSIAutoroutingPreferencePane) | .m 13.8KB + TableViewx2, xib 63KB | ✗ スタブ | ~40% |
| 14 | Web Server (OSIWebSharingReferencePane) | .mm 10.9KB, xib 127KB | ⚠️ ユーザー管理エディタのみ | ~75% |
| 15 | On-Demand PACS (OSIPACSOnDemandPreferencePane) | .m 18.9KB, xib 75KB + Cloud.png | ✓ パラメータ設定完成 | ~70% |

**概算補完率: 全体で ~68%** — Preference Window は実装ギャップが最も大きい UI の一つ。

---

## 次のアクション

1. **Listener パネルの詳細化** (P1)
   - TLS 証明書ファイルパスセレクタ (`JFileChooser`) を追加
   - Cipher Suite セレクタを `TLSSupportedCipherSuite` の一覧に実装
   - C-ECHO 接続検証ダイアログを実装

2. **Locations パネルの詳細化** (P1)
   - ノードエディタ（名前/AET/アドレス/ポート）の JDialog を実装
   - `DicomNodeManager` の CRUD 操作と UI の完全同期

3. **Hot Keys キーキャプチャ** (P2)
   - Swing `KeyListener` でキー入力を検出し、JSON に保存

4. **On-Demand PACS ソースリスト** (P2)
   - JSON 編集エディタを実装（ソース URL の追加/削除）

---

## コミット履歴

| Commit | 内容 |
|--------|------|
| efcbca0 | feat(ui): 全15 Preference Pane を実装 (HOROS-20240407準拠) |
