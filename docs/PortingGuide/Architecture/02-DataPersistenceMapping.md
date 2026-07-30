# OculusJJ Porting Guide

---

**最終更新日:** 2026-07-30

---
# DICOM Tag Mapping & Persistence Strategy

このドキュメントでは、各機能モジュールが必要とするDICOMタグと、それらをJavaのデータモデルおよびデータベース（SQLite等）にどのようにマッピングするかを定義します。

## 1. ブラウザ・ナビゲーション (Browser & Navigation)
基本的な検索と表示に必要な情報。

| 機能 | 必要なDICOMタグ | データ構造 (DicomStudy/Series/Image) |備考 |
| :--- | :--- | :--- | :--- |
| 患者識別 | PatientID | `patientID` | Primary Key (with StudyUID) |
| スタディ識別 | StudyInstanceUID, AccessionNumber | `studyInstanceUID`, `accessionNumber` | ブラウザの検索主軸 |
| 系列識別 | SeriesInstanceUID, Modality | `seriesInstanceUID`, `modality` | スライス表示の基本単位 |
| 画像パス | SOPInstanceUID, ImagePositionPatient | `sopInstanceUID`, `fullPath` | 物理ファイルへの紐付け |

## 2. MPR / 3D エンジン (Rendering Engine)
レンダリングとボリューム再構成に必要な位置・幾何情報。

| 機能 | 必要なDICOMタグ | データ構造 (DicomSeries/Image) |備考 |
| :--- | :--- | :--- | :--- |
| スライス厚 / 位置 | ImagePositionPatient, ImagePixelSpacing | `sliceLocation`, `pixelSpacing` | MPRの再構成に使用 |
| 転置・回転 | Orientation, ImageOrientationPatient | `rotationAngle`, `scale` | 画像の向きを補正 |
| ウィンドウ設定 | WindowCenter, WindowWidth | `windowLevel`, `windowWidth` | 表示時のコントラスト制御 |
| ボリュームデータ | SeriesInstanceUID (Specific MR/CT codes) | 内部キャッシュ (`DicomPix`) | MPR用データの抽出条件 |

## 3. セグメンテーション / ROI (Segmentation & ROI)
領域選択およびマスク情報の管理。

| 機能 | 必要なDICOMタグ | データ構造 (Image/ROI) |備考 |
| :--- | :--- | :--- | :--- |
| ROI座標 | 各フレームの画像位置情報 | `frameRois` (Map<Integer, List<ROI>>) | フレームごとのマスク保持 |
| 役割識別 | DICOM SR Tags (if saved) | `roiType`, `label` | セグメンテーションの種類 |

## データ整合性維持方針
1. **UIDの優先順位**: すべての検索とリレーションは、可能な限り最高精度のUID（SOPInstanceUID）をベースに行う。
2. **キャッシュ戦略**: 画像データ自体（PixelData）はデータベースに保存せず、`DicomPix` オブジェクトとしてメモリ/オフヒープで管理する。
3. **パスの正規化**: `DicomPathService` を介してすべての物理パスを取得し、複数環境での整合性を確保する。