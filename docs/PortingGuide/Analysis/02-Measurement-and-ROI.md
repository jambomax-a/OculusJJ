# OculusJJ Porting Guide

---

**最終更新日:** 2026-07-30

---
# 計測ツールおよびROI（関心領域）管理仕様書

## 1. 概要
本ドキュメントは、DICOM画像上での距離・角度測定およびROI（Region of Interest）による統計解析機能の実装仕様を定義する。本機能の核心は、画面上のピクセル座標をDICOMタグに基づいた物理的な世界座標（mm）に正確に変換することにある。

## 2. ROI (Region of Interest) 定義

### 2.1 サポートされるROIタイプ
以下のタイプを `ROIType` enum として実装する。

| 内部コード | タイプ名 | 説明 | 統計計算 |
| :--- | :--- | :--- | :--- |
| 5 | `MEASURE` | 線（距離測定） | ✕ |
| 6 | `RECTANGLE` | 矩形 | ○ |
| 9 | `OVAL` | 楕円 | ○ |
| 10 | `OPEN_POLYGON` | 開いた多角形 | ✕ |
| 11 | `CLOSED_POLYGON` | 閉じた多角形 | ○ |
| 12 | `ANGLE` | 角度測定 | ✕ |
| 13 | `TEXT` | テキスト注釈 | ✕ |
| 14 | `ARROW` | 矢印 | ✕ |
| 15 | `PENCIL` | フリーハンド描画 | ○ |
| 16 | `POINT_3D` | 3Dポイント (MPR用) | ✕ |
| 19 | `POINT_2D` | 2Dポイント | ✕ |
| 20 | `BRUSH` | ブラシ塗りつぶし領域 | ○ |
| 24 | `LAYER` | レイヤーオーバーレイ | ✕ |
| 26 | `AXIS` | 座標軸 | ✕ |
| 27 | `DYNAMIC_ANGLE` | 動的角度測定 | ✕ |
| 28 | `CURVED` | 曲線ROI（スプライン） | ○ |
| 29 | `TAGT` | 特殊測定線 (平行2本+垂直1本) | ✕ |

### 2.2 ROI基底クラス設計
すべてのROIは共通の基底クラス `ROI` を継承し、以下の属性を持つ。
- `type`: `ROIType`
- `points`: `List<Point2D>` (ピクセル座標系での頂点リスト)
- `color`: `Color` (描画色)
- `selected`: `boolean` (選択状態)
- `thickness`: `float` (線の太さ)
- `opacity`: `float` (不透明度)

## 3. 測定ロジックと数学的定義

### 3.1 座標変換フロー
**重要**: 描画座標（Display）と物理座標（World）を厳密に分離する。
`Display座標` -> `Pixel座標` -> `World座標(mm)`

### 3.2 距離測定 (`MEASURE`)
2点 $P_1(x_1, y_1)$ と $P_2(x_2, y_2)$ の間の物理距離を算出する。

1. **ピクセル差分を計算**:
   - $\Delta x = x_2 - x_1$
   - $\Delta y = y_2 - y_1$
2. **Pixel Spacingを適用**:
   - DICOMタグ `(0028,0030)` から $\text{Spacing}_x$ と $\text{Spacing}_y$ を取得する。
   - $\text{Dist}_x = \Delta x \times \text{Spacing}_x$
   - $\text{Dist}_y = \Delta y \times \text{Spacing}_y$
3. **最終距離の算出**:
   - $\text{Total Distance} = \sqrt{(\text{Dist}_x)^2 + (\text{Dist}_y)^2} \text{ [mm]}$

### 3.3 角度測定 (`ANGLE`)
頂点 $V$ と2点 $P_1, P_2$ によって形成される角度を算出する。

- ベクトル $\vec{VP_1} = (x_1 - x_v, y_1 - y_v)$
- ベクトル $\vec{VP_2} = (x_2 - x_v, y_2 - y_v)$
- $\text{Angle} = \text{abs}(\text{atan2}(\Delta y_1, \Delta x_1) - \text{atan2}(\Delta y_2, \Delta x_2))$
- $\text{Angle} > \pi$ の場合は $2\pi - \text{Angle}$ として補正し、度数法（degree）に変換する。

## 4. 統計解析ロジック

### 4.1 統計計算対象
`supportsStatistics()` が true のROIタイプ（RECTANGLE, OVAL, CLOSED_POLYGON, PENCIL, BRUSH, CURVED）に対して実行する。

### 4.2 計算プロセス
1. **ピクセル抽出**: ROIの境界線内部にあるすべてのピクセルの輝度値（Float）を `DicomPix` から抽出する。
2. **統計量算出**:
   - **Count**: 抽出ピクセル数
   - **Min / Max**: 最小値および最大値
   - **Mean**: $\text{平均} = \frac{\sum \text{Value}}{\text{Count}}$
   - **StdDev**: $\text{標準偏差} = \sqrt{\frac{\sum (\text{Value} - \text{Mean})^2}{\text{Count}}}$

## 5. ROI管理 (`ROIManager`)

- **z-order管理**: ROIはリスト形式で保持し、後から追加されたものが前面に描画される。
- **ヒット判定**: `ROI.contains(Point2D point)` メソッドを実装し、マウスカーソル位置から最近接するROIを特定する。
  - 線分ROIの場合、点から線分への最短距離が $\le 5\text{px}$ であればヒットと判定する。
- **永続化**: ROIの定義（タイプ、頂点座標、色）をJSON形式で保存・読み込みし、画像と紐付けて管理する。
