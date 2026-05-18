# OculusJJ

HOROS-20240407を参考にしたJava SwingベースのDICOMビューワー

## 概要

OculusJJは、獣医向けDICOMビューワーとして開発されているJava Swingアプリケーションです。
HOROS-20240407のソースコードを詳細に解析し、Java Swingへの移植を行っています。

## 主な機能

- DICOMファイルの読み込み・表示
- 画像操作（ズーム、パン、WL/WW調整）
- [ ] **GPUアクセラレーションの導入**
  - OpenGL (JOGL/LWJGL) および GLSL シェーダーによるボリュームレンダリングの高速化・高画質化
  - ビデオメモリへの3Dテクスチャ転送最適化
- [ ] **DICOM通信機能の拡張**
  - Query/Retrieve (C-FIND, C-MOVE) の完全な実装
  - WADO/STOW-RS対応
- [ ] **計測機能の充実**
  - 3D空間での自由な距離・角度計測
  - ROIの3D表示と自動抽出（セグメンテーション）
- [ ] **UIのモダン化**
  - ダークモードへの完全対応
  - ユーザーカスタマイズ可能なツールバー
- [ ] **プラグインアーキテクチャ**
  - サードパーティ製画像解析アルゴリズムの統合基盤
- DICOM通信（C-STORE、C-FIND、C-MOVE）
- PACS機能（DICOMサーバー）
- 画像解析機能（ROI、測定）
- MPR/3D画像作成（オプション）
- AI画像解析（オプション）
- CD/DVD自動インポート・作成機能
- 匿名化機能
- レポート機能

## システム要件

- Java 21以上
- Windows 10/11、macOS、Linux
- **推奨：ホイール付き3ボタンマウス**（3D操作に必須）

## 操作方法 (3D Viewer)

| 操作 | アクション |
| :--- | :--- |
| **左ドラッグ** | 視点の回転 (デフォルト) |
| **右ドラッグ** | **明るさ・コントラスト調整 (WL/WW)** |
| **中ドラッグ (ホイール押し)** | **パン (平行移動)** |
| **Shift + 左ドラッグ / ホイール回転** | ズーム (拡大・縮小) |

## ビルド方法

### 前提条件

- Java 21以上がインストールされていること
- Gradle 8.5以上（またはGradle Wrapperを使用）

### ビルド手順

1. プロジェクトをクローンまたはダウンロード
2. プロジェクトディレクトリに移動
3. ビルドを実行

```bash
# Windows
.\gradlew.bat build

# macOS/Linux
./gradlew build
```

### 実行方法

```bash
# Windows
.\gradlew.bat run

# macOS/Linux
./gradlew run
```

または、ビルドされたJARファイルを直接実行：

```bash
java -jar build/libs/OculusJJ-1.0.0.jar
```

## プロジェクト構造

```
OculusJJ/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/oculus/jj/
│   │   │       └── OculusJJApp.java
│   │   └── resources/
│   │       └── i18n/
│   │           ├── messages_ja.properties
│   │           └── messages_en.properties
│   └── test/
│       └── java/
│           └── com/oculus/jj/
├── docs/              # ドキュメント
├── build.gradle.kts   # Gradleビルド設定
└── settings.gradle.kts
```

## 開発状況

現在、基本的なプロジェクト構造とメインアプリケーションクラスが作成されています。

### 実装済み

- ✅ プロジェクト構造の作成
- ✅ メインアプリケーションクラスの作成
- ✅ 国際化リソースファイル（日本語/英語）
- ✅ Gradleビルド設定

### 実装予定

- ⏳ **UI/メニューの最適化**
  - HOROS方式（グレーアウト）ではなく、各画面ごとに不要なメニュー項目を削除してUIを整理する設計
- ⏳ **多言語対応 (i18n) の外部化**
  - メニュー名やボタン表示を外部リソースファイル（英語・日本語等）で管理し、拡張可能な設計に変更
- ⏳ **設定情報の外部ファイル管理**
  - データベースフォルダ、DICOMサーバー情報などの各種設定を外部ファイルで管理・変更可能にする
- ⏳ DICOMファイル処理
- ⏳ 画像表示・レンダリング
- ⏳ データベース管理
- ⏳ DICOM通信
- ⏳ PACS機能
- ⏳ 画像解析機能
- ⏳ MPR/3D画像作成
- ⏳ AI画像解析
- ⏳ CD/DVD自動インポート・作成

詳細な実装計画は `docs/README.md` を参照してください。

## ライセンス

HOROSはLGPLライセンスの下で公開されています。Java実装時も同様のライセンスを遵守してください。

## 関連プロジェクト

- **Vet-System**: 獣医向け受付・電子カルテシステム
- **JJDICOMViewer-Lite**: CD/DVD同梱用の簡易DICOMビューワー

## 参考資料

- HOROS-20240407ソースコード
- dcm4che-5.34.1ライブラリ
- DICOM標準（Part 3, 4, 10, 11）

## 開発者向け情報

詳細なドキュメントは `docs/` ディレクトリを参照してください。

- [プロジェクト概要](docs/00-Project-Overview.md)
- [アーキテクチャ概要](docs/01-Architecture-Overview.md)
- [DICOMファイル処理](docs/02-DICOM-File-Processing.md)
- [画像表示・レンダリング](docs/03-Image-Display-Rendering.md)
- その他、詳細な技術ドキュメント

