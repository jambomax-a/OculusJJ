# PACS ネットワーク通信仕様書 (Java Porting)

## 1. 概要
本ドキュメントは、HorosのPACS通信機能（SCU/SCP）をJavaで再現するための仕様を定義する。dcm4cheライブラリを用い、C-FIND, C-MOVE, C-STORE, C-ECHO の各サービスを実装する。

## 2. 通信基盤 (Core Networking)

### 2.1 接続管理 (Association)
Java実装における最重要注意点として、`org.dcm4che3.net.Association` は `AutoCloseable` を実装していない。そのため、`try-with-resources` は使用できず、必ず `finally` ブロックでの `release()` と `waitForSocketClose()` が必要である。

**実装パターン:**
```java
Association as = null;
try {
    as = ae.connect(remoteAE, null);
    // 通信処理
} finally {
    if (as != null) {
        as.release();
        as.waitForSocketClose();
    }
}
```

### 2.2 基本パラメータ
- **Calling AET**: 自局のApplication Entity Title
- **Called AET**: 相手局のApplication Entity Title
- **Hostname / Port**: 相手局のネットワークアドレス
- **Transfer Syntax**: 転送構文（Implicit VR Little Endian 等）

## 3. SCU (Client) 機能仕様

### 3.1 C-ECHO (接続確認)
- **目的**: リモートAEとの疎通確認。
- **成功条件**: レスポンスの `Status` タグが `0` であること。

### 3.2 C-FIND (検索)
- **階層的検索**: `QueryRetrieveLevel` (PATIENT → STUDY → SERIES → IMAGE) に基づき、絞り込み条件を構築する。
- **検索キー**: `PatientName`, `StudyInstanceUID`, `Modality` 等を `Attributes` に設定し送信。

### 3.3 C-MOVE (取得)
- **動作**: 相手局に「指定したデータを、指定したDestination AETへ送信せよ」と要求する。
- **注意**: C-MOVEの要求を出すと、相手局から **C-STORE SCUとして** 自身のサーバーへデータが送られてくるため、自局の C-STORE SCP が起動している必要がある。

## 4. SCP (Server) 機能仕様

### 4.1 DicomServer 構成
- `Device` → `ApplicationEntity` → `Connection` の階層で構成。
- `setAssociationAcceptor(true)` を設定し、外部からの接続を待ち受ける。

### 4.2 C-STORE SCP (受信サーバー)
- **保存フロー**: 受信データ → `sopInstanceUID` 抽出 → パス決定 (`StudyUID/SeriesUID/SOPUID.dcm`) → ファイル保存 → DB登録。
- **DB連携**: ファイル保存後、`DicomDatabase.importDicomFile()` を呼び出し、ブラウザに即時反映させる。

### 4.3 C-FIND/C-MOVE SCP (検索・提供サーバー)
- **検索応答**: `DicomDatabase` 内の SQLite インデックスを検索し、結果を `Attributes` 形式で逐次返信する。
- **提供応答**: `DicomDatabase` からファイルパスを取得し、`DicomStoreSCU` を用いて相手局へデータをプッシュ送信する。

## 5. 高度なサーバー機能

### 5.1 自動ルーティング (Auto-Routing)
- **ロジック**: 受信したデータのタグ（例：Modality = 'CT'）を判定し、あらかじめ定義したアクションを実行する。
- **アクション例**:
  - `CopyToDirectory`: 特定のフォルダへコピー
  - `SendToPacs`: 別の外部PACSへ転送

### 5.2 スケジュールタスク (Scheduled Tasks)
- `ScheduledExecutorService` を用い、定期的に「特定の条件のStudyをPACSから取得する」タスクを実行する。
- **周期設定**: Daily, Weekly, Monthly などのスケジュールを管理。

## 6. エラーハンドリングとログ
- **ステータス管理**: DICOM標準のステータスコード（Success, Pending, Failure）を厳格に処理する。
- **通信ログ**: `CallingAET`, `Operation`, `Status`, `Timestamp` を記録し、管理画面から確認可能とする。
