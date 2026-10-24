# OculusJJ — Veterinary DICOM Viewer & 3D/AI Imaging Workstation

[![License: LGPL v3](https://img.shields.io/badge/License-LGPL_v3-blue.svg)](https://www.gnu.org/licenses/lgpl-3.0)
[![Java](https://img.shields.io/badge/Java-21%2B-ed8b00.svg)](https://openjdk.org/)
[![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20macOS%20%7C%20Linux-lightgrey.svg)]()

*[English](#english) | [日本語](#japanese)*

---

<a id="english"></a>

## Overview

**OculusJJ** is an open-source, cross-platform DICOM viewer and medical image processing workstation tailored for **veterinary medicine**. 
Inspired by and ported from the architecture of **[Horos](https://horosproject.org/)** (`HOROS-20240407`) and **OsiriX**, OculusJJ brings macOS-native radiology workflows to **Java (Swing + OpenGL/LWJGL)** so that veterinary professionals can run advanced 2D/3D/MPR visualization and AI-assisted analysis across Windows, macOS, and Linux.

## ⚠️ Disclaimer (Veterinary & Research Use Only)

> **IMPORTANT — PLEASE READ CAREFULLY BEFORE USE:**
>
> 1. **Not Certified for Human Clinical Diagnosis:** OculusJJ is NOT cleared, approved, or certified as a medical device (e.g., FDA, CE, PMDA) for human diagnostic use. Do not use this software for primary clinical diagnosis in humans.
> 2. **Veterinary & Educational Support Tool:** This software is designed strictly for **veterinary clinical reference, research, and educational purposes**.
> 3. **AI-Assisted Features Disclaimer:** Any Artificial Intelligence (AI) or automated segmentation/analysis features included in or connected to OculusJJ are provided strictly as experimental reference aids. AI outputs may contain errors or artifacts and **must never substitute for the independent professional judgment of a qualified veterinarian**.
> 4. **No Warranty & Limitation of Liability:** The software is provided "AS IS", without warranty of any kind, express or implied. Under no circumstances shall the authors, contributors, or copyright holders be liable for any direct, indirect, incidental, or consequential damages, including misdiagnosis or clinical outcomes arising from the use of this software. The attending veterinarian assumes full responsibility for all diagnostic and treatment decisions.

## Key Features

- **Comprehensive DICOM Support**: Powered by `dcm4che` (5.34.1) and `weasis-core-img` (OpenCV native decoding), supporting uncompressed, JPEG (Lossless/Lossy/2000), and RLE transfer syntaxes, as well as nested DICOM sequences and `DICOMDIR`.
- **2D Viewer & Clinical Tools**:
  - Window Level / Window Width (WL/WW) presets, CLUT (Color Look-Up Tables), and convolution filters.
  - Complete ROI & measurement suite (Length, Angle, Polygon, Oval, Pencil, Brush with morphological Dilate/Erode, SUV conversion, Pixel calibration).
  - Image Fusion (PET-CT / MRI-CT blending), Subtraction, and Key Image (DICOM Secondary Capture) export.
  - Navigator strip & 3D Position Synchronizer across multi-series studies.
- **GPU-Accelerated 3D & MPR**:
  - Multi-Planar Reconstruction (MPR), Curved MPR, and Orthogonal views.
  - OpenGL (`LWJGL 3`) hardware-accelerated Volume Rendering (Ray-marching VR, MIP, Isosurface) with 16-bit Transfer Functions, Phong shading, Stereo 3D modes, and automatic CPU fallback.
- **PACS & DICOM Networking**:
  - Built-in local SQLite database with Study/Series/Image hierarchy.
  - DICOM C-ECHO, C-FIND, C-MOVE, and C-STORE SCP/SCU integration.
- **AI-Assisted Veterinary Imaging (Experimental)**:
  - Extensible architecture for integrating AI inference models (automated organ/lesion segmentation and diagnostic assistance for companion animals).
- **Internationalization (i18n)**: English and Japanese UI support.

## System Requirements

- **Java**: JDK 21 or higher
- **OS**: Windows 10/11 (x64), macOS, Linux
- **RAM**: 16 GB+ recommended (JVM configured with `-Xmx24g` by default for large 3D/MPR volumes)
- **GPU**: OpenGL 3.3+ compatible graphics card
- **Input**: 3-button mouse with scroll wheel (strongly recommended for 2D/3D navigation)

## Mouse Controls (2D / 3D Viewer)

| Input | Action |
| :--- | :--- |
| **Left Drag** | 3D Rotation / Active 2D Tool |
| **Right Drag** | Window Level / Window Width (WL/WW) adjustment |
| **Middle Drag (Wheel Click)** | Pan (Translation) |
| **Shift + Left Drag / Scroll Wheel** | Zoom / Slice Scroll |

## Quick Start (Build & Run)

```bash
# Clone the repository
git clone https://github.com/jambomax-a/OculusJJ.git
cd OculusJJ

# Build the project
./gradlew build        # macOS / Linux
.\gradlew.bat build    # Windows

# Run the application
./gradlew run          # macOS / Linux
.\gradlew.bat run      # Windows
```

## Credits & Acknowledgments (Horos / OsiriX)

OculusJJ stands on the shoulders of giants in the open-source medical imaging community:

- **[Horos Project](https://horosproject.org/)** (`HOROS-20240407`): The user interface design, workflow architecture, 2D/3D rendering logic, and ROI manipulation concepts of OculusJJ are ported from and heavily inspired by **Horos**, a free and open-source medical image viewer distributed under the **GNU Lesser General Public License v3.0 (LGPL-3.0)**.
- **[OsiriX](https://www.osirix-viewer.com/)**: Horos itself is based on the original open-source versions of OsiriX (developed by Antoine Rosset, MD, et al. / Pixmeo SARL). We gratefully acknowledge the pioneering work of both the OsiriX and Horos development teams.
- **Third-Party Libraries**:
  - [dcm4che](https://github.com/dcm4che/dcm4che) (MPL 1.1 / GPL 2.0 / LGPL 2.1) — DICOM toolkit in Java
  - [Weasis](https://github.com/nroduit/Weasis) (EPL 2.0 / Apache 2.0) — Native OpenCV/DICOM image decompression
  - [LWJGL 3](https://www.lwjgl.org/) (BSD-3-Clause) — OpenGL GPU volume rendering

## License

This project is licensed under the **GNU Lesser General Public License v3.0 (LGPL-3.0)** in compliance with the upstream Horos / OsiriX license terms. See the [LICENSE](LICENSE) and [COPYING.LESSER](COPYING.LESSER) files for full details.

---

<a id="japanese"></a>

## 概要 (Japanese)

**OculusJJ** は、**獣医療（動物病院・獣医画像診断）向け**に開発されたオープンソース・クロスプラットフォームのDICOMビューワーおよび3D/AI画像解析ワークステーションです。
macOS向けの定番オープンソース医用画像ビューワーである **[Horos](https://horosproject.org/)**（`HOROS-20240407`）および **OsiriX** のソースコード・設計思想を詳細に解析し、**Java (Swing + OpenGL/LWJGL)** へ移植（ポーティング）することで、Windows・macOS・Linuxを問わず動作する高速な2D/3D/MPR表示とAI画像解析基盤を実現しています。

## ⚠️ 免責事項（獣医師向け・ご利用上の注意）

> **【重要】ご使用前に必ずお読みください**
>
> 1. **人医療における薬機法上の未承認（人体への診断使用禁止）**: 本ソフトウェア（OculusJJ）は、医薬品医療機器等法（薬機法）および各国の医療機器規制（FDA、CE等）における「医療機器プログラム（SaMD）」としての承認・認証・届出を受けていません。**人間の臨床診断目的には絶対に使用しないでください。**
> 2. **獣医療における補助・研究・教育目的**: 本ソフトウェアは、**獣医療における画像参照・診療補助、ならびに学術研究・教育目的**として提供されています。
> 3. **AI解析機能に関する免責**: 本ソフトウェアに搭載または連携されるAI（人工知能）画像解析・自動セグメンテーション機能は、実験的な参照補助機能です。AIの推論結果には誤検出や見落としが含まれる可能性があり、**確定診断を代替するものではありません。**
> 4. **無保証および責任の制限**: 本ソフトウェアは「現状有姿（AS IS）」で提供され、明示または黙示を問わずいかなる保証も行いません。本ソフトウェアの使用または使用不能から生じるいかなる損害（誤診、診療上の結果、データ消失等を含む）についても、開発者および貢献者は一切の責任を負いません。**最終的な診断および治療方針の決定は、必ず担当獣医師ご自身の専門的判断と責任において行ってください。**

## 主な機能

- **包括的なDICOMフォーマット対応**: `dcm4che` (5.34.1) および `weasis-core-img`（ネイティブOpenCVデコーダ）により、非圧縮・JPEG系（Lossless/Lossy/JPEG2000）・RLE圧縮構文、ネストされたシーケンスタグ、`DICOMDIR` の読み込みに対応。
- **2Dビューワー・計測・解析機能**:
  - WL/WW（ウィンドウレベル/幅）プリセット、CLUT（カラーマップ）、コンボリューションフィルタ。
  - ROI・計測ツール一式（距離、角度、ポリゴン、楕円、ペンシル、モルフォロジー膨張/収縮対応ブラシ、SUV換算、ピクセル校正）。
  - 画像フュージョン（PET-CT / MRI-CT ブレンディング）、サブトラクション、Key Image（DICOM Secondary Capture）出力。
  - シリーズ横断の Navigator ストリップおよび 3D Position 同期パネル。
- **GPUアクセラレーション 3D / MPR**:
  - MPR（多断面再構成）、Curved MPR、直交3断面表示。
  - OpenGL (`LWJGL 3`) によるハードウェア高速化ボリュームレンダリング（Ray-marching VR / MIP / Isosurface）、16-bit トランスファーファンクション、Phongシェーディング、ステレオ立体視、および環境に応じたCPUフォールバック機能。
- **PACS通信・ローカルDB管理**:
  - SQLiteベースのローカルStudy/Series/Image階層管理。
  - DICOM通信機能（C-ECHO / C-FIND / C-MOVE / C-STORE SCP・SCU）。
- **AI画像解析連携（拡張機能）**:
  - 伴侶動物（犬・猫等）の臓器・病変セグメンテーションや画像診断支援を行うAI推論モデルを統合するための拡張機能。
- **多言語対応 (i18n)**: 日本語・英語UIの切り替えに対応。

## システム要件

- **Java**: JDK 21 以上
- **OS**: Windows 10/11 (64-bit)、macOS、Linux
- **メモリ**: 16 GB以上推奨（大容量3D/MPR処理のためデフォルトJVM引数で `-Xmx24g` を指定しています。環境に合わせて `build.gradle.kts` で調整可能です）
- **GPU**: OpenGL 3.3 以上に対応したグラフィックス環境
- **入力デバイス**: ホイール付き3ボタンマウス（3D/2D操作に強く推奨）

## マウス操作 (2D / 3D Viewer)

| 操作 | アクション |
| :--- | :--- |
| **左ドラッグ** | 3D視点の回転 / 選択中の2Dツール操作 |
| **右ドラッグ** | 明るさ・コントラスト調整 (WL/WW) |
| **中ドラッグ (ホイール押し)** | パン (平行移動) |
| **Shift + 左ドラッグ / ホイール回転** | ズーム (拡大・縮小) / スライス移動 |

## クイックスタート（ビルドと実行）

```bash
# リポジトリのクローン
git clone https://github.com/jambomax-a/OculusJJ.git
cd OculusJJ

# ビルド
.\gradlew.bat build    # Windows
./gradlew build        # macOS / Linux

# アプリケーションの起動
.\gradlew.bat run      # Windows
./gradlew run          # macOS / Linux
```

## 関連プロジェクト・ドキュメント

- **開発ドキュメント**: アーキテクチャや移植ステータスの詳細は [PORT_STATUS.md](PORT_STATUS.md) および `docs/` ディレクトリを参照してください。


## クレジット・謝辞（Horos / OsiriX およびライセンス表記）

OculusJJ は、オープンソース医用画像コミュニティの偉大な先行プロジェクトに深い敬意を表し、その知見を基に開発されています。

- **[Horos Project](https://horosproject.org/)** (`HOROS-20240407`):
  本ソフトウェアのUI設計、操作ワークフロー、2D/3Dレンダリングパイプライン、およびROI操作ロジックは、**GNU Lesser General Public License v3.0 (LGPL-3.0)** の下で公開されているオープンソース医用画像ビューワー **Horos** のソースコードを解析し、Java Swing / OpenGL 環境へ移植・再構築したものです。
- **[OsiriX](https://www.osirix-viewer.com/)**:
  Horos の基盤となったオープンソース版 **OsiriX**（Antoine Rosset 氏らおよび Pixmeo SARL により開発）の開発陣に深く感謝いたします。
- **主な使用オープンソースライブラリ**:
  - [dcm4che](https://github.com/dcm4che/dcm4che) (MPL 1.1 / GPL 2.0 / LGPL 2.1) — Java DICOMツールキット
  - [Weasis](https://github.com/nroduit/Weasis) (EPL 2.0 / Apache 2.0) — ネイティブDICOM画像デコード基盤
  - [LWJGL 3](https://www.lwjgl.org/) (BSD-3-Clause) — OpenGL GPUボリュームレンダリング

## ライセンス (License)

本プロジェクトは、移植元である Horos / OsiriX のライセンス条件を継承・遵守し、**GNU Lesser General Public License v3.0 (LGPL-3.0)** の下で公開されています。
詳細についてはリポジトリ内の [LICENSE](LICENSE) および [COPYING.LESSER](COPYING.LESSER) ファイルをご参照ください。
