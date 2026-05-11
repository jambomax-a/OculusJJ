# OculusJJ 3D Viewer 機能仕様書 (DOCS-3D-001)

## 概要
OculusJJの3Dビューワーモジュールは、DICOMボリュームデータ（CT/MRI/PET等）をリアルタイムで三次元可視化するためのコンポーネントです。
HOROS-20240407 (DCM Framework / VRController.m) のアーキテクチャを参考に、Javaを用いたCPUベースのソフトウェアレンダリングを実装しています。

## 1. レンダリングエンジン
本システムは **CPU Ray Casting (レイキャスティング法)** を採用しています。

- **並列処理**: Java 8 の `IntStream.range().parallel()` を利用し、利用可能なすべてのCPUコアを使用して各ピクセルの計算を同時に行います。
- **イメージ生成**: `BufferedImage` の `WritableRaster` または `int[]` 配列を直接操作し、AWT/Swingで高速に描画します。
- **座標変換**: オイラー角（X軸、Y軸）による回転行列を計算し、3D空間上の視線を定義します。

## 2. 実装されている3Dモード
`Viewer3DController.java` にて以下のモードが切り替え可能です。

### 2.1 MIP (Maximum Intensity Projection: 最大値投影)
- **原理**: レイ（視線）上のボクセルの最大輝度を選択して表示します。
- **用途**: 造影血管、骨、石灰化の観察に最適です。
- **特徴**: 奥行き情報は欠落しますが、連続した構造（血管など）の把握が容易です。

### 2.2 MinIP (Minimum Intensity Projection: 最小値投影)
- **原理**: レイ上のボクセルの最小輝度を選択して表示します。
- **用途**: 肺野などの低吸収域、気道、胆管（空気）の観察に用いられます。

### 2.3 VR (Volume Rendering: ボリュームレンダリング)
- **原理**: 透明度（Opacity）と色（Color）の伝達関数を用いて、全ボクセルの寄与を積分計算します。
- **実装内容**: 現在は簡易的な線形勾配を用いたボリュームレンダリングを実装しており、組織の重なりを半透明に透過して表示可能です。
- **ウィンドウ操作**: マウスの右ドラッグによる Window Level / Width 操作により、組織の抽出度合いを動的に変更できます。

### 2.4 ISO (Surface Rendering: サーフェスレンダリング / 等値面抽出)
- **原理**: 指定された閾値 (`isoThreshold`) に最初に到達した点をサーフェス（表面）として描画します。
- **シェーディング**: 勾配（Gradient）に基づいた表面法線を計算し、**Lambertian Shading (ランバート反射)** を適用することで立体感を演出します。
- **設定ダイアログ**: 描画開始前に「閾値 (Threshold)」と「解像度 (Resolution)」を設定可能です。

## 3. Horos-20240407 準拠の内部仕様解析

### 3.1 2D ROIの3D投影 (ROI Projection)
Horosにおいて2D ROIは「3D Point」および「Scissor（切り抜き）」として3D空間に投影されます。
- **3D Point ROI**: 2D上の `t2DPoint` 型ROIを `convertPixX:pixY:toDICOMCoords:` を用いてDICOM座標（x, y, z）に変換し、3D空間内に球体等のActorとして配置します。
- **Scissor / ROI Filling**: `applyScissor` により、2Dで描画されたクローズドパスROIの形状に基づき、ボリュームデータの特定部位を消去または特定値へ置換（Cropping）します。

### 3.2 レンダリングプリセット (Clut/Opacity Tables)
- **転送関数**: `vtkColorTransferFunction` (Color) と `vtkPiecewiseFunction` (Opacity) を使用。
- **プリセット**: デフォルトで `"VR Muscles-Bones"` (CLUT) や `"Logarithmic Inverse Table"` (Opacity) が定義されています。
- **定数定義**: `BONEOPACITY` (1.1) などの定数により、骨等の高濃度組織の不透明度を制御しています。

### 3.3 3D専用ツールセット (3D-specific Tools)
- **Clipping / Cropping**:
    - **ClippingRange**: カメラからの距離に基づくクリッピング（`SetClippingRange`）。厚みを指定したスライス表示も可能。
    - **BoxWidget**: `vtkBoxWidget` を用いた対話的な3Dボックスクロッピング。
- **Scissor (3D自由形状切り抜き)**: 2D ROIを投影し、ボリュームデータの密度値を直接操作（`fillROI:newVal:`）することで、3D形状を「彫刻」するように編集します。
- **Orientation Marker**: `vtkAnnotatedCubeActor` 等による解剖学的方向（A, P, L, R, S, I）のインジケータ表示。

### 3.4 シェーディングとライティング (Shading & Lighting)
Horosでは `vtkVolumeProperty` を介して詳細なライティングパラメータを制御しています。
- **Ambient (環境光)**: `SetAmbient()`。影の部分の基本輝度。
- **Diffuse (拡散反射)**: `SetDiffuse()`。光源からの直接光による表面輝度。
- **Specular (鏡面反射)**: `SetSpecular()`。ハイライト（テカリ）の強さ。
- **Specular Power**: `SetSpecularPower()`。ハイライトの収束度合い。

## 4. 詳細機能の技術仕様 (Horos ソース解析に基づく)

### 4.1 Window Level / Width (WL/WW) と転送関数 (Transfer Functions)
3D空間におけるコントラスト調整は、カラーと不透明度の転送関数（Transfer Function）へのマッピングによって実現されています。
- **カラーマッピング (`vtkColorTransferFunction`)**: `setWLWW:iww:` メソッドにより、$[WL - WW/2, WL + WW/2]$ の範囲を255段階のカラーテーブルに線形マッピングします。16bit DICOMデータの場合、オフセット（`OFFSET16`）とスケール係数（`valueFactor`）を考慮して計算されます。
- **不透明度制御 (`vtkPiecewiseFunction`)**: UIからの不透明度配列（`currentOpacityArray`）を `AddPoint()` でVTKに登録。骨（Bone）等の特定組織向けには `#define BONEOPACITY 1.1` などのハードコードされた係数が適用される場合があります。

### 4.2 3D描画・シザー (Scissor) ・部分描画
特定の部位を切り出したり隠したりするための高度な操作機能です。
- **3D Scissor (ROI抽出)**: `applyScissor:` メソッドが中核。2Dで描画されたクローズドパスROIに基づき、`DCMPix` クラスの `fillROI:` を呼び出して、ボリュームデータの生メモリ（`float*`）を直接書き換えます（Flash Fill）。削除時は通常、画素値を最小輝度に置換することで「消去」を表現します。
- **Cropping Box (Region of Interest)**: `vtkBoxWidget` を使用して実装。`vtkMyCallbackVR` がボックスの変形を検知し、`vtkVolumeMapper` の `SetClippingPlanes` に対して6枚のクリッピングプレーンを設定することで、レンダリング範囲を制限します。

### 4.3 3D空間での計測とアノテーション
- **3D距離計測**: マウス座標を `DisplayToWorld()` で3D空間のユークリッド座標に変換し、`Line2DData` (vtkPolyData) として管理。`computeLength` メソッドにより実距離を算出します。
- **ROIVolume**: シザー処理等で特定された3D領域は `ROIVolume` クラスで管理され、他のスタディへの適用やボリュームデータとしてのエクスポートが可能です。

### 4.4 パフォーマンス最適化 (LOD: Level of Detail)
操作時のレスポンスを維持するための解像度制御戦略です。
- **動的解像度制御**: `setLOD:` メソッドで操作中（Interaction）と静止時（Final Rendering）の品質を切り替えます。
- **サンプル距離の調整**: 操作中は `vtkVolumeMapper` の `SetMinimumImageSampleDistance` に対して `lowResLODFactor`（通常1.2〜2.5倍）を乗じ、サンプル密度を下げることで描画速度を稼ぎます。操作停止後は `renderImageWithBestQuality:` により、最高画質で再描画が実行されます。

## 5. 各描画モードの機能詳細比較 (Horos vs Java)
Horos-20240407 のソース解析に基づき、現在の Java プロジェクトでの実装状況と未実装機能を整理します。

### 5.1 3D MPR (Multi-Planar Reconstruction)
- **Horos実装 (`MPRController.m`, `MPRView.mm`)**:
    - 3つの直交する法線ベクトル ($vA, vC, vS$) を Quaternion で管理。
    - `computeStableBasis` により、表示を安定化。
    - 4Dデータ（時間軸）対応。
- **Javaプロジェクト現状**:
    - **実装済み**: 基本的な直交3断面表示、クロスヘア連動。
    - **未実装**: 任意の斜めスライスへの完全な自由回転、時間軸対応。

### 5.2 3D Curved MPR (曲面再構成)
- **Horos実装 (`CurvedMPR.m`)**:
    - スプライン曲線サンプリング。
    - **Stretched** および **Straightened** の再構成機能。
- **Javaプロジェクト現状**:
    - **実装済み**: 曲線パス（ノード）の定義と距離計算。
    - **未実装**: **再構成画像自体の生成ロジック**。

### 5.3 3D Orthogonal MPR
- **Horos実装 (`OrthogonalReslice.m`)**:
    - SIMD (Accelerate.framework) を使用した高速Reslice。
- **Javaプロジェクト現状**:
    - **実装済み**: 標準的3断面表示。
    - **未実装**: ハードウェアレベルの高速化、高度なメモリキャッシュ。

### 5.4 3D MIP / VR / SR (Volume&Surface Rendering)
- **Horos実装 (`VRView.mm`, `vtkHoros...`)**:
    - ハードウェア加速された Ray Casting。
    - 高度な CLUT プリセット（骨、筋肉等）と不透明度曲線 UI。
- **Javaプロジェクト現状**:
    - **実装済み**: CPUベースのMIP、ISO(Surface)、簡易VR。
    - **未実装**: ライティング詳細設定 (Specular等) の UI、VR用 CLUT プリセット、16bit 階調の高度な最適化。

## 6. インタラクション機能
### 6.1 マウス操作
- **左ドラッグ**: 自由回転（Rotate X/Y）。
- **右ドラッグ**: Window Level / Width の調整（コントラスト調整）。
- **ホイールスロール**: ズームイン・アウト。

## 7. LOD (Level of Detail) 制御
- **ドラッグ中**: 回転操作をスムーズにするため、ステップ数を増やして粗い描画（Low Res）を行います。
- **静止時**: ユーザーが操作を停止した際に高精細な描画工程（High Res）に切り替わります。

## 8. ツールバーとメニュー
- **3D Mode切替**: MIP / VR / MinIP / ISO などのワンクリック切り替え。
- **ISO Threshold**: スピナーによる閾値の微調整。
- **Shading Toggle**: 陰影のON/OFF（サーフェスモード時）。
- **Reset**: 視点とコントラストを初期状態に戻します。

## 9. 技術的制約
- **メモリ**: 全短軸画像を `short[][][]` 配列にキャッシュするため、大規模データセット（1000枚以上）では十分なヒープメモリ割り当てが必要です。
- **解像度**: CPUレンダリングのため、4Kなどの高解像度画面では `resolution` 値を調整してパフォーマンスを維持する必要があります。
