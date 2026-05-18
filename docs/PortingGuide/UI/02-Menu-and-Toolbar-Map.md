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

### 4.2 3D ビューア用ツールバー
3D画面のコンテキストに合わせて提供される高速切り替えバー。
- **3D Rotate (回転ボタン)**: ボリューム回転ツール。
- **3D Pan (パンボタン)**: スクリーン整合パンニングツール。
- **3D Zoom (ズームボタン)**: 拡大縮小ツール。
- **3D Reset (リセットボタン)**: 視野角度・位置の初期リセット。

---

## 5. フォーカス管理とアクション共通化

1. **アクションの共通化**: メニュー項目とツールバーボタンは同一の Swing `Action` を共有し、ツールの有効/無効化やツール状態の同期を一貫して管理する。
2. **フォーカス制御（Key Window）**: サムネイルクリックやメニュー押下時、アクティブなビューアコンポーネントが `requestFocusInWindow()` を呼び出し、`isKeyView` 状態（同期元のアクティブフラグ）を強制確保する。
