# MPR/3D エンジン実装仕様書 (Java Porting)

## 1. 概要
本ドキュメントは、OculusJJにおける多断面再構成 (MPR) および 3D ボリュームレンダリング（MIP, VR, ISO）機能の数学的定義、実装構造、および HOROS-20240407 (VRController.m / DCM Framework) との仕様比較を厳格に記述する。
物理的なDICOM空間座標系に基づき、幾何学的整合性と超高速ハードウェア加速を両立させたハイブリッド設計を定義する。

---

## 2. 3D座標系の構築と異方性ボクセル補正 (Anisotropic Pixel Spacing)

### 2.1 物理ワールド座標の定義 (Patient Coordinate System)
DICOM規格（LPS: Left-Posterior-Superior）に基づき、3D空間上の原点と方向ベクトルを定義する。
- **原点 $\mathbf{O}$**: `Image Position Patient` (0020,0032) $ \to $ $[x_0, y_0, z_0]$ (mm)
- **方向ベクトル $\mathbf{I, J}$**: `Image Orientation Patient` (0020,0037) $ \to $ $[X_x, X_y, X_z, Y_x, Y_y, Y_z]$
- **ピクセル間隔 $\mathbf{S}$**: `Pixel Spacing` (0028,0030) $ \to $ $[\Delta x, \Delta y]$
- **スライス間隔 $\mathbf{\Delta z}$**: ソートされたスライスの `SliceLocation` の絶対差分 $|\text{SliceLoc}_1 - \text{SliceLoc}_0|$

### 2.2 アスペクト比の正規化 (Anisotropic Spacing Correction)
CTやMRIデータセットは、通常 Z 軸方向（スライス厚）の間隔が X, Y 軸（スライス内解像度）よりも粗い「異方性（Anisotropic）」を持つ。
HOROS と同様に、OculusJJ では描画されるボリュームの物理サイズを等方（Isotropic）に正規化する。
- 各軸の物理総幅を計算：
  $$\text{Phys}_X = \text{Dim}_X \times \Delta x, \quad \text{Phys}_Y = \text{Dim}_Y \times \Delta y, \quad \text{Phys}_Z = \text{Dim}_Z \times \Delta z$$
- 最大軸を $1.0$ に正規化し、モデル変換行列のスケール係数（`voxScaleX`, `voxScaleY`, `voxScaleZ`）を算出：
  $$\text{MaxPhys} = \max(\text{Phys}_X, \text{Phys}_Y, \text{Phys}_Z)$$
  $$\text{voxScale}_X = \frac{\text{Phys}_X}{\text{MaxPhys}}, \quad \text{voxScale}_Y = \frac{\text{Phys}_Y}{\text{MaxPhys}}, \quad \text{voxScale}_Z = \frac{\text{Phys}_Z}{\text{MaxPhys}}$$
- **結果**: 3Dビュー上での扁平化や引き延ばし歪みが完全に補正され、実空間と同じ縦横比で描画される。

---

## 3. 再スライス (Reslicing) アルゴリズム
任意の斜め断面（Oblique Slice）をリアルタイムに切り出す処理。
- **基底ベクトルの構築**: 法線ベクトル $\mathbf{n}$ に対し、グラム・シュミットの直交化を用いて、2D断面画面の横軸 $\mathbf{u}$ と縦軸 $\mathbf{v}$ を構築。
- **逆投影とサンプリング**:
  $$\mathbf{P}_{world} = \mathbf{P_c} + (x \cdot \Delta x \cdot \mathbf{u}) + (y \cdot \Delta y \cdot \mathbf{v})$$
  上記物理座標をボリュームインデックス $[ix, iy, iz]$ に逆変換し、隣接する8個のボクセル値から **トリリニア補間 (Trilinear Interpolation)** を用いてピクセル値を算出。

---

## 4. 3D レンダリングエンジン (MIP / VR / ISO) と HOROS 仕様比較

OculusJJ は、超高速な **OpenGL 3.3 GPU レンダラー** と、完全スレッドセーフな **Javaマルチスレッド CPU レンダラー** を兼ね備えたハイブリッド構造を持つ。

| 機能項目 | HOROS-20240407 仕様 | OculusJJ 実装 (GPU/CPU ハイブリッド) |
| :--- | :--- | :--- |
| **描画ハードウェア** | VTK (OpenGL加速付き3Dグラフィックス) | **LWJGL 3 (OpenGL 3.3 Core Profile)** / 自動 CPU Fallback |
| **スライス順ソート** | `SliceLocation` 大（頭側）が先頭 | 同様：`SliceLocation` 降順ソートによる上下・左右定位の一致 |
| **ボリュームデータ転送** | `vtkImageData` (float*) | **GL_R16F 3Dテクスチャ** (符号付き16bit生データをシェーダー内で実HU化) |
| **法線・陰影計算** | `vtkEncodedGradientEstimator` | **Sobelフィルタ事前計算 GL_RGBA8 3Dテクスチャ** による高精度陰影法線 |
| **伝達関数 (TF)** | `vtkColorTransferFunction` | **GL_TEXTURE_1D (GL_RGBA16F) 伝達関数テクスチャ** |
| **初期窓レベル (WL/WW)** | 3D用 1:1 線形マッピング範囲 | **`WL = 1024 / WW = 4096` デフォルト設定** (筋肉〜骨格のふっくらとした肉付きを再現) |
| **骨格描画色プリセット** | VR Muscles-Bones CLUT | **乳白色 (`R=255, G=245, B=240`)** 高不透明度反射マッピング |

### 4.1 レンダリングモードアルゴリズム
1. **MIP (Maximum Intensity Projection)**:
   レイキャスト走査中に、WL/WW窓でスケーリングされた最大輝度を保持。
   $$\text{FragColor} = \max(\text{maxV}, \text{winT})$$
2. **VR (Volume Rendering)**:
   伝達関数テクスチャからカラー $C_{tf}$ と不透明度 $A_{tf}$ をサンプリングし、Blinn-Phong反射モデルをブレンドしてレイに沿って前進合成（Alpha Blending）。
   $$\text{Acc}_{rgb} = \text{Acc}_{rgb} + (1.0 - \text{Acc}_a) \times C_{color} \times A_{tf}$$
   $$\text{Acc}_a = \text{Acc}_a + (1.0 - \text{Acc}_a) \times A_{tf}$$
   ※ $\text{Acc}_a \ge 0.95$ で走査早期離脱 (Early Ray Termination) し、処理を高速化。
3. **ISO (Surface Rendering)**:
   指定閾値 `isoThreshold` に最初に交差したボクセルで走査を停止し、その点の法線ベクトル $N$ と光源ベクトル $L$ からサーフェスを陰影描画。

---

## 5. インタラクション数学（HOROS完全準拠）

### 5.1 完璧なスクリーン整合パニング (Mathematically Corrected Panning)
カメラの視野が回転している状態でマウスをドラッグした際、操作と画像の動きが完全に一致するよう、ボリューム空間上のカメラ Up ベクトル $U_{vol}$ およびカメラ Right ベクトル $R_{vol}$ を算出して平行移動オフセットを加算する。
- **回転行列から導出した Right/Up ベクトル**:
  $$R_{vol} = (\cos(\theta_y), \sin(\theta_y)\sin(\theta_x), \sin(\theta_y)\cos(\theta_x))$$
  $$U_{vol} = (0, \cos(\theta_x), -\sin(\theta_x))$$
- **同期移動式 (パニング極性補正済)**:
  $$\Delta \text{Offset} = -dx \cdot R_{vol} + dy \cdot U_{vol}$$
- **効果**: ボリュームがどの方向に回転していても、マウスの移動方向と画像の追従方向が 100% 直感的に同期する。

---

## 6. 技術的ロバストネス設計 (GPU-CPU Fallback)
OpenGLコンテキスト生成エラー、プラットフォーム依存のドライバ不具合、マルチウィンドウ切替時のハングアウトを完全に防止するため、以下の防護ロジックを実装。
1. **EDT スレッド完全隔離**: AWT/EDT スレッドと OpenGL レンダリングループのデッドロックを回避するため、リソース割り当てとコンテキスト破棄を SwingUtilities パス上で排他制御。
2. **Watchdog タイマー監視**: GPUエンジンのアクティベーションが 12秒 以内に完了しない場合、自動的に CPU ソフトウェアレンダラーへ切り替えるウォッチドッグ機構を配備。
3. **アクティブGCクリーンアップ**: 巨大ボリュームのHU変換時、不要になった DICOM スライスメモリを即座に null 化し、定期的なガベージコレクションを強制実行することで OutOfMemory を物理的に防止。
