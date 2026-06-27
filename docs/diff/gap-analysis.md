# ツールバー差分分析表 - HOROS-20240407 vs OculusJJ

> 作成日: 2026-06-26 (最終更新)
> 目的: 全ビューアのツールバー実装ギャップを特定し、優先度付きで実装計画を立てる

## ステータスサマリー

| 優先度 | カテゴリ | 状態 |
|--------|---------|------|
| **P0** | 2Dツールバー i18n統一 | ✅完了 (全ボタン英語化 + LanguageManager対応) |
| **P0** | プロパティファイルキー整合性 | ✅完了 (En/Ja 双方124キーで一致) |
| **P1** | MPR Controller ツールバー強化 | 🔧実装中 |
| **P1** | VR Controller Stereo/Fusion/Engine | 🔧一部実装済み、未実装あり |
| **P2** | BrowserController メニュー追加 | ✅Endoscopy/SRメニュー追加済み |

---

## 詳細ギャップ一覧（優先度順）

### P1 - MPR Controller ツールバー強化

| # | HOROSアイテム | Java現状 | Gap内容 | 優先度 |
|---|--------------|---------|---------|--------|
| 1 | tbLOD (LOD Slider) | **未実装** | LODスライダー（解像度調整） | P1 |
| 2 | tbWLWW (WL&WW Panel) | **未実装** | WL/WWプリセットコントロール | P1 |
| 3 | tbShading | **未実装** | シェーディングパラメータエディタ | P1 |
| 4 | tbBlending (Fusion) | **未実装** | 2Dブレンド/Fusion制御 | P1 |
| 5 | AxisShowHide | **未実装** | 断面軸表示切替ボタン | P1 |
| 6 | MousePositionShowHide | **未実装** | マウス座標表示切替 | P1 |
| 7 | syncZoomLevel | **未実装** | 全ビューズーム同期スライダー | P1 |
| 8 | ViewsPosition | **未実装** | 断面位置切り替え(Normal/Horiz/Vert) | P1 |

### P1 - VR Controller 強化

| # | HOROSアイテム | Java現状 | Gap内容 | 優先度 |
|---|--------------|---------|---------|--------|
| 1 | Stereo Mode (Stereo.icns) | ✅トグル追加済み | GLViewer3D.setStereoMode()実装待ち | P1 |
| 2 | Fusion/Blending (2DBlending) | ✅トグル追加済み | GLViewer3D.setFusionMode()実装待ち | P1 |
| 3 | ROI Manager (ROIManager.pdf) | **未実装** | ROI管理ポップアップ | P1 |
| 4 | Engine Selection (CPU/GPU) | ✅追加済み（ラジオボタン） | OK | - |

### P2 - その他

| # | HOROSアイテム | Java現状 | Gap内容 | 優先度 |
|---|--------------|---------|---------|--------|
| 1 | DICOM Export (MPR) | ✅ボタン追加済み | エクスポート処理実装待ち | P2 |
| 2 | Best Rendering | ✅ボタン追加済み | キャプチャ処理実装待ち | P2 |
| 3 | Email/PACS export | **未実装** | DICOM mail送信 | P2 |
| 4 | FlyThru (内視鏡飛行) | **未実装** | フライスルーアニメーション | P3 |

---

## 実装済みの変更一覧

| ファイル | 変更内容 | ステータス |
|---------|---------|-----------|
| ToolbarPanel.java | 全28ボタンをLanguageManager経由の英語ラベル化 | ✅完了 |
| messages_en.properties | 124キー（全メニュー/ボタン/i18n） | ✅完了 |
| messages_ja.properties | 124キー（日本語訳） | ✅完了 |
| MainMenuBar.java | Endoscopy/SRメニュー追加 | ✅完了 |
| MPRController.java | DICOM Export/Best Rendering ボタン追加 | ✅完了 |
| Viewer3DController.java | Stereo/Fusion トグル追加 | ✅完了（プレースホルダ） |

---

## 次回実装計画（P1 Priority）

### Step 1: MPR Controller ツールバー強化（7アイテム）
- LODスライダー, WL&WWパネル, Shadingエディタ, Fusion制御
- Axis/MousePos/Toggle表示切替, SyncZoomスライダー

### Step 2: VR Controller ROI Manager & Stereo/Fusion実装
- ROI Managerポップアップ連携
- GLViewer3D.setStereoMode()/setFusionMode()実装

### Step 3: ビルド確認 & テスト