# OculusJJ Porting Guide

---

**最終更新日:** 2026-07-30

---
# Horos 全機能棚卸しマトリクス (Feature Matrix)

## 1. 概要
本ドキュメントは、Horosの全機能を網羅的にリストアップし、それぞれの実装詳細がどのドキュメントに記載されているかを紐付けたマスターインデックスである。Java移植における「実装漏れ」をゼロにすることを目的とする。

## 2. 機能マトリクス

### 2.1 DICOM コア & データ管理
| 機能名 | 詳細内容 | 参照ドキュメント (old/new) | 移植難易度 |
| :--- | :--- | :--- | :--- |
| **ファイルパース** | DICOMヘッダー解析, 転送構文判定 | `02-DICOM-File-Processing.md` (OLD) → `DICOM/02-Database-and-Import.md` | 低 |
| **レンダリング** | 32bit Float $ → $ 8bit 変換, LUT適用 | `03-Image-Display-Rendering.md` (OLD) → `DICOM/01-Rendering-Pipeline.md` | 中 |
| **DB管理** | Study/Series/Image 階層管理, SQLite | `05-Database-Management.md` (OLD) → `Architecture/02-DataPersistenceMapping.md` | 中 |
| **非DICOM対応** | TIFF, NIfTI 読み込み | `02-DICOM-File-Processing.md` | 中 |

### 2.2 ネットワーク & PACS 機能
| 機能名 | 詳細内容 | 参照ドキュメント (old/new) | 移植難易度 |
| :--- | :--- | :--- | :--- |
| **PACS Query** | C-FIND による患者/検査検索 | `04-DICOM-Network-Communication.md`, `13-PACS-Functionality.md` | 高 |
| **PACS Retrieve** | C-MOVE / C-GET による画像転送 | `04-DICOM-Network-Communication.md`, `13-PACS-Functionality.md` | 高 |
| **DICOM Listener** | 外部からの C-STORE 受信サーバー | `04-DICOM-Network-Communication.md` | 高 |
| **Auto-Routing** | 受信データの自動振り分け設定 | `13-PACS-Functionality.md` | 中 |

### 2.3 ビューア & 診断ツール
| 機能名 | 詳細内容 | 参照ドキュメント (old/new) | 移植難易度 |
| :--- | :--- | :--- | :--- |
| **2D同期エンジン** | 複数ビューア間の座標/インデックス同期 | `Analysis/02-Sync-Engine.md` (新規) ← `HOROS_SYNC_COMPLETE_REFERENCE.md` (OLD) | 高 |
| **WW/WL 調整** | ウィンドウレベル/幅の変更, プリセット | `UI/01-Viewer-Sync-Logic.md` (新規) ← `HOROS-WW-WL-COMPLETE-ANALYSIS.md` (OLD) | 低 |
| **計測ツール** | 長さ, 角度, 面積, 楕円/矩形ROI | `07-Image-Analysis.md` | 中 |
| **アノテーション** | テキスト入力, 図形描画, レイヤー管理 | `07-Image-Analysis.md` | 中 |
| **MPR/3D** | 直交再構成, VR, MIP, MinIP | `Analysis/01-MPR-3D-Engine.md` (新規) ← `11-MPR-3D-Image-Generation.md` (OLD) | 最高 |
| **CPR** | 曲線平面再構成 (Curved Planar) | `Analysis/02-CPR-Reconstruction.md` / `Analysis/03-CPR-Logic.md` (新規) ← `11-MPR-3D-Image-Generation.md` (OLD) | 最高 |
| **AI解析** | AIモデル連携, 自動セグメンテーション | `12-AI-Image-Analysis.md` | 高 |

### 2.4 メディア & インポート
| 機能名 | 詳細内容 | 参照ドキュメント (old/new) | 移植難易度 |
| :--- | :--- | :--- | :--- |
| **CD/DVD Import** | ディレクトリ走査, DICOMDIR解析, 自動読込 | `16-Auto-Import-CD-DVD.md` | 中 |
| **Burning機能** | DICOM CD/DVD 書き出し, ファイル構造作成 | `16-Auto-Import-CD-DVD.md` | 中 |

### 2.5 システム & 設定
| 機能名 | 詳細内容 | 参照ドキュメント (old/new) | 移植難易度 |
| :--- | :--- | :--- | :--- |
| **Preference Panes** | 各種設定パネル (DB, Net, UI, Hotkey) | `UI/04-Preference-Mapping.md` (新規) ← `09-Configuration-Management.md` (OLD) | 中 |
| **国際化 (i18n)** | 多言語対応, 言語リソース管理 | `10-Internationalization.md` | 低 |
| **レイアウト管理** | タイル表示, ウィンドウリサイズ, 保存済みレイアウト | `UI/03-Interaction-Matrix.md` / `UI/02-Menu-and-Toolbar-Map.md` (新規) ← `HOROS-MULTIPLE-VIEWER-LAYOUT-ANALYSIS.md` (OLD) | 中 |
| **Vet System** | 動物病院向けカスタム統合機能 | `14-Vet-System-Integration.md` | 中 |

## 3. 未着手・要詳細化リスト
- [ ] **Burning機能の詳細フロー**: `16-Auto-Import-CD-DVD.md` (OLD) から書き込み手順を抽出する必要あり。
- [ ] **各診断ツールの数学的定義**: `07-Image-Analysis.md` (OLD) の中身を詳細仕様書へ展開する必要あり。
- [ ] **PACS通信のシーケンス図**: `04-DICOM-Network-Communication.md` (OLD) から通信フローを可視化する必要あり。
- [ ] **Preferenceの全項目リスト**: `UI/04-Preference-Mapping.md` (新規) にて管理済み。
