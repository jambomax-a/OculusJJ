# MPR/3D エンジン実装仕様書 (Java Porting)

## 1. 概要
本ドキュメントは、Horosの多断面再構成 (MPR) および 3D ボリュームレンダリング機能をJavaで再現するための数学的定義と実装フローを定める。単なる画像処理ではなく、DICOMの物理座標系に基づいた幾何学的再構成を行う。

## 2. 3D座標系の構築 (Coordinate System)

### 2.1 ワールド座標の定義
DICOMの以下のタグを用いて、3D空間上の位置を定義する。
- **原点 $\mathbf{O}$**: `Image Position Patient` (0020,0032) $ → $ $[x_0, y_0, z_0]$ (mm)
- **方向ベクトル $\mathbf{I, J, K}$**: `Image Orientation Patient` (0020,0037) $ → $ $[X_x, X_y, X_z, Y_x, Y_y, Y_z]$
- **ピクセル間隔 $\mathbf{S}$**: `Pixel Spacing` (0028,0030) $ → $ $[\Delta x, \Delta y, \Delta z]$ (mm)

### 2.2 座標変換フロー
**[2D画像座標 $(u, v)$] $ → $ [3Dワールド座標 $\mathbf{P}$]**
$$\mathbf{P} = \mathbf{O} + (u \cdot \Delta x \cdot \mathbf{I}) + (v \cdot \Delta y \cdot \mathbf{J}) + (s \cdot \Delta z \cdot \mathbf{K})$$
※ $s$ はスライスインデックス。

## 3. 再スライス (Reslicing) アルゴリズム

任意の平面を切り出すための処理フローを定義する。

### 3.1 平面の定義
- **法線ベクトル $\mathbf{n}$**: 平面の向きを決定する。
- **中心点 $\mathbf{P_c}$**: 平面上の基準点。
- **基底ベクトル $\mathbf{u, v}$**: $\mathbf{n}$ に垂直な2つの直交ベクトルを算出し、2D表示画面の X軸、Y軸とする。

### 3.2 サンプリングフロー
1. **2Dピクセル走査**: 出力画像の各ピクセル $(x, y)$ についてループ。
2. **ワールド座標への逆投影**:
   $$\mathbf{P}_{world} = \mathbf{P_c} + (x \cdot \Delta x \cdot \mathbf{u}) + (y \cdot \Delta y \cdot \mathbf{v})$$
3. **ボリューム座標への変換**: $\mathbf{P}_{world}$ をボリュームのインデックス $[ix, iy, iz]$ に変換する。
4. **トリリニア補間 (Trilinear Interpolation)**: 整数インデックス間の値を補間し、滑らかな画像を生成する。

## 4. ボリュームレンダリング (VR) と投影法

### 4.1 レイキャスティング (Ray Casting)
1. **レイ生成**: カメラ位置から各ピクセル方向にレイ (直線) を飛ばす。
2. **サンプリング**: レイ上の点に沿って一定間隔でボクセル値をサンプリングする。
3. **色・不透明度適用**: `CLUT (Color Look-Up Table)` と `Opacity Transfer Function` を適用し、前後方向に合成 (Alpha Blending) する。

### 4.2 MIP / MinIP (最大/最小強度投影)
- **MIP**: レイ上のサンプリング値の中で **最大値** を採用して描画する。
- **MinIP**: レイ上のサンプリング値の中で **最小値** を採用して描画する。

## 5. Java実装における技術スタック

| 機能 | 推奨ライブラリ / 実装方法 |
| :--- | :--- |
| **行列・ベクトル演算** | `JOML (Java OpenGL Math Library)` $ → $ `Vector3f`, `Matrix4f` を使用 |
| **メモリ管理** | `java.nio.FloatBuffer` (Off-heap) $ → $ 大容量ボリュームデータの保持 |
| **描画** | `JOGL` または `LWJGL` $ → $ GPUによる高速描画とシェーダー (GLSL) の活用 |
| **並列処理** | `ForkJoinPool` または `Java Vector API` $ → $ 再スライス計算のマルチスレッド化 |

## 6. 期待される精度
- 同一の $\mathbf{O}, \mathbf{I}, \mathbf{J}, \mathbf{K}$ を用いることで、HorosのMPRビューとピクセル単位で一致する結果を得ることを目標とする。
