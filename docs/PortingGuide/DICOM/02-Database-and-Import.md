# DICOM データ管理・DB実装仕様書 (Java Porting)

## 1. 概要
本ドキュメントは、HorosのDICOMメタデータ管理およびデータベース(SQLite)へのインポートフローをJavaで再現するための仕様を定義する。高速なブラウジングを実現するため、ファイルシステムへの直接アクセスを避け、DBを介した階層管理を行う。

## 2. データモデル (Entity Relationship)

Javaでは以下の3つのエンティティを定義し、Hibernate または MyBatis 等を用いて管理する。

### 2.1 Study (検査)
- **主キー**: `studyInstanceUID` (String)
- **主要属性**:
  - `patientUID`: 患者の一意識別子 (PatientName + PatientID + DOB から生成)
  - `patientName`: 患者名
  - `patientID`: 患者ID
  - `studyDate`: 検査日時
  - `modality`: 検査に含まれるモダリティの集合
  - `numberOfImages`: 総画像数

### 2.2 Series (シリーズ)
- **主キー**: `seriesInstanceUID` (String)
- **外部キー**: `studyUID` → `Study.studyInstanceUID`
- **主要属性**:
  - `seriesDescription`: シリーズ説明
  - `modality`: モダリティ
  - `seriesNumber`: シリーズ番号
  - `numberOfImages`: シリーズ内画像数

### 2.3 Image (画像)
- **主キー**: `sopInstanceUID` (String)

- **外部キー**: `seriesUID` → `Series.seriesInstanceUID`
- **主要属性**:
  - `filePath`: ファイルシステムの絶対パス
  - `instanceNumber`: インスタンス番号
  - `sliceLocation`: スライス位置

## 3. インポートパイプライン (Import Flow)

ファイルをインポートする際は、以下のフローを厳守する。

### Step 1: メタデータ抽出 (Fast Parsing)
- `dcm4che-core` の `DicomInputStream` を使用。`setIncludePixelData(false)` を設定し、**ピクセルデータを読み込まずにヘッダーのみを高速にパース**する。

### Step 2: 文字クレンジング (Character Cleaning)
- Horosの互換性を維持するため、以下の置換ルールを適用する。
  - `^` → ` ` (スペース)
  - `/` → `-` (ハイフン)
  - `
`, `
` → 削除
  - `:` → `-` (ハイフン)
  - 末尾の空白をトリム

### Step 3: UID生成と紐付け
- DICOMタグに `PatientUID` がない場合、`PatientName` + `PatientID` + `BirthDate` を組み合わせて内部的な `patientUID` を生成し、同一患者をグループ化する。

### Step 4: DB書き込み
- `Study` → `Series` → `Image` の順にトランザクションを張り、一括書き込みを行う。

## 4. ブラウザUI (Tree View) 実装戦略

Java Swing の `JTree` で実装する場合、以下のメモリ戦略を採る。

1. **遅延読み込み (Lazy Loading)**:
   - 起動時は `Study` のリストのみをDBから取得してロードする。
   - ユーザーがノードを展開したタイミングで、その `StudyUID` に紐づく `Series` をクエリして動的に追加する。
2. **フィルタリング**: 
   - 検索キーワードやモダリティフィルタが適用された場合、DB側で `WHERE` 句を用いて絞り込みを行い、結果を再ロードする。

## 5. 非DICOMファイルへの対応

- **TIFF / NIfTI**: 
  - ファイル名から `StudyID` / `SeriesID` / `ImageID` を抽出するフォールバックロジックを実装する。
  - ファイル名の末尾数字を `InstanceNumber` と見なしてインデックス化する。
