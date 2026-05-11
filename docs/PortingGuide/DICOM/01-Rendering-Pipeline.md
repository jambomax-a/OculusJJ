# DICOM レンダリング・パイプライン実装仕様書 (Java Porting)

## 1. 概要
本ドキュメントは、Horosのピクセルデータ処理をJavaで忠実に再現し、位置ズレや縮尺異常を排除するためのレンダリングパイプラインを定義する。dcm4che-imageio の自動処理に依存せず、低レイヤーでの制御を行う。

## 2. 処理フロー (Data Pipeline)

画像データは以下のステップで変換され、最終的に画面に描画される。

### Step 1: Raw Pixel Data 読み込み
- **処理**: `dcm4che-core` を使用して `PixelData` タグから生データを抽出。
- **実装**: `DirectByteBuffer` を使用してオフヒープメモリにロードし、メモリコピー回数を最小限に抑える。

### Step 2: fImage 生成 (32-bit Float 変換)
- **目的**: 演算精度を確保し、Rescale Slope/Intercept を適用するため。
- **計算式**: `fImage[i] = (RawPixel[i] * RescaleSlope) + RescaleIntercept`
- **保持形式**: `float[]` または `FloatBuffer`。

### Step 3: Window Level/Width (WW/WL) 適用
- **目的**: 32-bit float データをディスプレイ表示可能な 8-bit (0-255) に変換する。
- **計算ロジック**:
  - `min = WindowCenter - (WindowWidth / 2)`
  - `max = WindowCenter + (WindowWidth / 2)`
  - `8bitValue = (fImage[i] - min) / (max - min) * 255` (0-255でクランプ)
- **Java実装**: `vImageConvert_PlanarFtoPlanar8` 相当の処理を高速なループまたは SIMD 指令（Java 17+ Vector API）で実装する。

### Step 4: baseAddr 生成 (8-bit 表示用バッファ)
- **結果**: `BufferedImage` (TYPE_BYTE_GRAY) または OpenGL テクスチャに転送可能な 8-bit バッファ。

## 3. 座標系と縮尺の管理 (Geometry)

位置ズレを防ぐため、以下のDICOMタグを厳格に管理し、描画時の座標変換に適用する。

| 変数名 | DICOMタグ | 役割 |
| :--- | :--- | :--- |
| `pixelSpacingX` | (0028,0030) | ピクセル間隔 (mm) - X方向 |
| `pixelSpacingY` | (0028,0030) | ピクセル間隔 (mm) - Y方向 |
| `originX / Y / Z` | (0020,0032) | Image Position Patient (mm) |
| `orientation` | (0020,0037) | Image Orientation Patient (方向ベクトル) |
| `sliceLocation` | (0020,1041) | スライス位置 (mm) |

## 4. Java実装におけるメモリ戦略

### 4.1 オフヒープメモリの活用
- 大容量の DICOM シリーズをロードするため、`java.nio.ByteBuffer.allocateDirect()` を使用して JVM ヒープ外でピクセルデータを保持する。
- 参照が不要になった時点で明示的に解放する仕組みを構築する。

### 4.2 計算の最適化
- WW/WL 処理は全ピクセルに対して行われるため、以下の最適化を行う：
  - **ルックアップテーブル (LUT)**: WW/WL が変更されたときのみ LUT を再生成し、描画時は `8bitValue = LUT[RawValue]` で高速に変換する。
  - **マルチスレッド化**: `ParallelStream` または `ForkJoinPool` を使用し、画像行単位で並列処理する。

## 5. 期待される結果
- dcm4che-imageio の内部処理による予期せぬ値変換を排除できる。
- Horos と同一の計算式を用いることで、物理的な距離計測や位置合わせの精度を完全に一致させることができる。
