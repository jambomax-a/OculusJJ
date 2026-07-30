# OculusJJ Porting Guide

---

**最終更新日:** 2026-07-30

---
# メニューおよびツールバー構成定義書 (Java Porting)

## 1. 概要
本ドキュメントは、OculusJJにおける2D/3Dビューアのメニューおよびツールバーの構成を定義する。HOROS-20240407の操作系統を再現し、Java Swingの `JMenuBar` および `JToolBar` を介して提供される機能を網羅する。

---

## 2. 2D ビューア メニュー構造 (Menu Hierarchy)

2Dビューアウィンドウにおけるメインメニューの構成。

### 2.1 File メニュー
- **Open...**: 単一または複数DICOMファイルの選択ロード。
- **Import...**: フォルダ指定によるDICOMスタディの一括スキャン・データベース登録。
- **Export...**: 表示中の画像シリーズをDICOM形式でローカルへ書き出し。
- **Close Window**: ビューアを閉じ、割り当てられていた2D画像キャッシュメモリを安全に解放。

### 2.2 View メニュー
- **Zoom / Pan**: 拡大倍率、およびパニングオフセットの個別リセット。
- **Window Level/Width (WL/WW)**: 
  - `Reset`: デフォルト値への復帰。
  - `Presets`: Brain (40/80), Bone (300/1500), Lung (-600/1500), Soft Tissue (40/400) 等の窓適用。
- **Tiling (タイル・レイアウト表示)**: 
  - `1x1`, `2x1`, `2x2`, `4x1` 等の分割レイアウト切り替え。
- **Sync**: ビューア間同期モード (Absolute, Ratio, Relative, Location) の有効化。

---

## 3. 3D ビューア専用メニュー構造 (3D Viewer Menus)

3D ビューア (MIP/VR/ISO) ウィンドウで利用可能な専用アクションメニュー。

### 3.1 Mode (描画モード切替) メニュー
- **MIP (Maximum Intensity Projection)**: 最大強度投影モードの即時適用。
- **Volume Rendering (VR)**: 3Dボリュームレンダリング（伝達関数マッピング付き）への切り替え。
- **Surface Rendering (ISO)**: 閾値等値面抽出描画モードの適用。
- **Reset View**: ズーム、パニング移動量、および回転角度（CT軸位断面俯瞰）を初期状態に完全復元。

### 3.2 3D 制御パネル
- **ISO Threshold (閾値調整スピナー/スライダー)**: Surface Rendering における抽出閾値の微細コントロール。
- **WW/WL 窓調整機能**: 3Dボリューム伝達関数（TF）マッピングの境界調節。
- **自動解放 (Window Closing / Cleanup)**:
  - ウィンドウがクローズ（`dispose`）された際、AWTGLCanvasのリソース、事前計算勾配バッファ、HU変換済み `short[]` ボリューム、およびTFテクスチャキャッシュを安全に解放（`cleanup()` メソッドの自動実行）。メモリリーク（OOM）を徹底防止。

---

## 4. ツールバー構成 (Toolbar Panel Map)

ツールバーは、頻繁に使用するツールへのクイックアクセスを提供し、同時にウィンドウフォーカスを制御する。

### 4.1 2D ビューア用ツールバー
| アイコン / ラベル | 実行されるアクション | 対応ショートカット | 内部状態遷移 |
| :--- | :--- | :--- | :--- |
| **🔍 Zoom** | ズームツールをアクティブ化。 | `Z` | `currentTool = ToolMode.tZoom` |
| **↔️ Pan** | 平行移動ツールをアクティブ化。 | `P` | `currentTool = ToolMode.tPan` |
| **📏 Measure** | 長さ計測線ROIの描画ツール。 | `M` | `currentTool = ToolMode.tMeasure` |
| **🎚️ WL/WW** | Window Level/Width 調整ツール。 | `W` | `currentTool = ToolMode.tWL` |
| **🖱️ Selector** | 通常の選択・ROI操作ツール。 | `S` | `currentTool = ToolMode.tSelect` |
| **3D MIP** | 現在のスタディから3DビューアをMIPモードで起動。 | - | `Viewer3DController.openMIP()` |
| **3D VR** | 現在のスタディから3DビューアをVRモードで起動。 | - | `Viewer3DController.openVR()` |

### 4.2 3D ビューア用ツールバー (5分割グループ)
3D画面のコンテキストに合わせて提供される、HOROS-20240407仕様に完全準拠したプレミアム5連グループパネル：

#### 1) Mouse button function (左クリック操作ツール切替)
※ **使いやすさ向上のための最適化**: 右ドラッグ（WL/WW）、中ドラッグ（Pan）、ホイールスクロール（Zoom）は常にダイレクトに動作するため、重複するWL/WW、Pan、Zoomボタンを排除し、左クリックで行うメインアクションの切り替えのみに絞り込みました（HOROS/OsiriXの使いづらさを改善した高効率設計）。
- **↻ Rotate**: 直感的な3Dトラックボール回転ツール（デフォルト）。
- **🟢 Point**: ボリューム座標へのインタラクティブなシードポイント指定。
- **📏 Meas**: 空間3Dの二点間距離計測（3Dルーラー）。
- **🎯 Scissor / ROI / In / Out**: ボリュームの不要部分をカットアウト/カットインする3D領域切削ツール。

#### 2) WL/WW & CLUT & Opacity (カラー＆窓関数制御)
- **WL/WW プリセット**: `Default`, `Bone` (骨強調), `Soft Tissue` (軟部組織), `Lung` (肺野), `Vascular` (血管造影) 等を即時適用し、Hounsfield Unit (HU) スケールと完全同期。
- **CLUT プリセット**: カラー検索テーブルを `VR Muscles & Bones`, `Hot Iron`, `Rainbow`, `No CLUT` 等で即時スイッチ。
- **Opacity 曲線**: 不透明度関数を `Linear` / `Logarithmic` で切り替え。
- **8-bit / 16-bit ボタン**: LUTビット深度の高速切り替え。
- **🎛️ Presets ボタン**: クイック3Dプリセットメニューをポップアップ表示し、各種伝達関数（Transfer Function）を動的にボリュームにロード。

#### 3) Level of Detail & Orientation (解像度・エンジン・方向)
- **LOD スライダー**: `Fine` (微細画質: 0) から `Coarse` (高速粗描画: 100) まで無段階に制御。
  - **GPUモード**: 3Dフラグメントシェーダのレイステップ幅（`stepSize`）を `0.001f`〜`0.015f` の間でリアルタイムスケーリング。
  - **CPUモード**: レイステップ数（`lodSteps`: `128`〜`512`）と、CPUレンダリングバッファサイズ（幅 `180px`〜`720px`）の双方を動的にダウンサンプリングし、操作中のレスポンスを爆発的に高速化。
- **✨ Best ボタン**: スライダー値を `0` (Fine) にリセットし、最高解像度での静止画最終レンダリングをトリガー。
- **Engine ラジオボタン**: ソフトウェア並列レンダラ（`CPU`）とハードウェアアクセラレーション（`GPU`）をシームレスに切り替え。
- **✂ Crop Box トグル**: ボリュームの外郭バウンディングボックスのアウトライン表示をトグル。
- **🧊 Orient ボタン**: 解剖学的ビュー方向（Lateral, Anterior-Posterior, Superior-Inferior）を即時リセット。

#### 4) Shading (シェーディングパラメータ)
- **Shading チェックボックス**: 3Dサーフェスハイライト（Blinn-Phong反射）の有効・無効を制御。無効時はフラットな不透明色投影に。
- **Edit ボタン & パラメータ表示**: Ambient (0.20), Diffuse (0.90), Specular (0.30) - Shininess (15.0) の反射定数を設定ダイアログから微調整。

#### 5) Perspective & Filters (カメラ投影＆畳み込みフィルタ)
- **カメラ投影ラジオボタン**: `Parallel`（平行・正投影）、`Perspective`（遠近・透視投影）、`Endoscopy`（内視鏡ビュー）の切り替え。
- **Convolution Filter ドロップダウン**: 各種3D畳み込みフィルタ（Smooth 3x3, Sharpen 3x3 等）の選択。
- **>> ボタン**: 詳細フィルターオプションの展開トグル。

---

## 5. フォーカス管理とアクション共通化

1. **アクションの共通化**: メニュー項目とツールバーボタンは同一の Swing `Action` を共有し、ツールの有効/無効化やツール状態の同期を一貫して管理する。
2. **フォーカス制御（Key Window）**: サムネイルクリックやメニュー押下時、アクティブなビューアコンポーネントが `requestFocusInWindow()` を呼び出し、`isKeyView` 状態（同期元のアクティブフラグ）を強制確保する。
