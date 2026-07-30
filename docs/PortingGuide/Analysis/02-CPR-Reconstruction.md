# OculusJJ Porting Guide

---

**最終更新日:** 2026-07-30

---
# CPR 再構成機能 (Coronal Pre-Reconstruction)

CPR（Coronal Pre-Reconstruction）は、マルチスライスデータを統合し、断面方向に再投影することで2Dイメージを生成する技術。OculusJJ においてはこの機能の正確な再現と高速化が重要な移植目標となる。

## 1. プロジェクションモード
本システムでは以下の3つの基本プロジェクションを提供し、それぞれ `CPRProjectionOperation` として非同期で処理される。
- **MIP (Maximum Intensity Projection)**: 指定した奥行き方向における最大輝度値を抽出。血管や骨の構造視認に適する。
- **MinIP (Minimum Intensity Projection)**: 最小輝度値を抽出。
- **Mean**: 平均輝度を抽出。

## 2. 実装仕様と最適化
1. **並列処理と加速**: 計算負荷の高いボリューム再投影は `NSOperation` を継承した非同期クラスで実行する。内部では Accelerate framework (`vDSP`) のベクトル演算を用い、MIP/MinIP/Meanの各ピクセル計算を高速に行う。
2. **空間的な整合性**: 出力されるCPRイメージは「異方性ボクセル補正」の結果と統合され、3Dビュー上でのアスペクト比（縦横比）が他のMPRビューやVRビューと完全に一致するように物理総幅に基づく正規化が行われる。
3. **動的ボリューム生成**: 必要な時のみ `CPRVolumeData` を構築し、メモリ負荷を抑えつつ高品質な再投影結果を提供するためのバッファリング機構を持つ。

## 3. 数学的考慮事項
- **座標系の変換**: DICOM空間からレンダリング空間への変換において、各プロジェクションモードに適切なスケーリング（`voxScaleX/Y/Z`）を適用する。
- **リニア補間**: ボリュームのサンプリングにはトリリニア補間 (`CPRVolumeDataCubicInterpolatedFloatAtVolumeCoordinate`) を用い、滑らかな再構成を実現する。
