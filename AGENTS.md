# OculusJJ エージェントチーム

## プロジェクト概要

OculusJJ は HOROS-20240407 を参考にした Java/Swing ベースの DICOM ビューワー移植プロジェクトです。Windows/macOS/Linux に対応し、HOROS の UI/動作を忠実に再現します。

**参照元**: `horos-20240407/Horos/`（Objective-C/Cocoa 実装）
**実装領域**: `src/main/java/com/oculus/jj/`（Java/Swing ポート）

## 基本方針

### HOROS 準拠写経ルール

- **すべての実装は HOROS-20240407 のソースコードを基準とする**。メソッド名、変数名、UI 構造、デフォルト値を可能な限り維持する。
- 行番号コメントを付ける（例: `// HOROS-20240407準拠: BrowserController.m 2905行目`）。
- **推測による実装は禁止**。HOROS を確認してから実装する。
- カスタムロジックは Java/Windows と Objective-C/macOS の差分を埋める最小限のみ。理由をコメントで明記する。

### 禁則事項

- HOROS に存在しない機能の追加
- 「より良い」という理由での HOROS からの逸脱
- HOROS を確認せずにの実装

## チームと役割

| # | チーム | 役割 | 対象 | 成果物 |
|---|--------|------|------|--------|
| 1 | **HOROS 解析** | HOROS 側の仕様を詳解（UI、設定値、NSUserDefaults キー等） | `horos-20240407/Horos/` | `docs/horos-spec/*.md` |
| 2 | **OculusJJ 実装解析** | 現行 Java/Swing の現状把握、未実装リスト | `src/main/java/com/oculus/jj/` | `docs/oculusjj-status/current-state.md` |
| 3 | **Diff 解析** | (1) と (2) を突合しギャップを優先度付け | 仕様書 + 状態レポート | `docs/diff/gap-analysis.md` |
| 4 | **Gap Filler** | ギャップを実装し、テスト・ビルド確認 | Gap リスト + 仕様書 | コード + テスト + ビルド証跡 |

**並行ルール**: HOROS 解析と OculusJJ 解析は同時実行可。Diff は両方の成果が揃ってから開始。

## ワークフロー

HOROS 解析 / OculusJJ 解析 → Diff 分析 → Gap Filler 実装 → ビルド確認 → マージ

### フロー詳細

1. **解析フェーズ**: 該当チームはテンプレートを使って成果物を `docs/` に配置
2. **Diff フェーズ**: ギャップ分析を作成し優先度 P0/P1/P2 を付与
3. **実装フェーズ**: Gap Filler が実装 → ローカルビルド確認 → PR 作成
4. **レビュー**: PR は少なくとも 1 名の承認が必要

## プロジェクト設定

### ビルド・テスト

```powershell
./gradlew compileJava        # コンパイル確認
./gradlew build              # テスト含むフルビルド
./gradlew jar                # JAR 生成（bin/に出力）
./gradlew test               # テストのみ
```

### コーディング規約

- Java 17+（Gradle 9.2.1 / toolchain 指定）
- ソースエンコーディング: UTF-8
- インデント: 4スペース
- クラス名は既存の命名パターンに準拠（例: `*Controller`, `*Dialog`, `*Panel`）

### ドメイン構造

```
src/main/java/com/oculus/jj/
├── config/      # 設定管理 (AppPreferences, ConfigManager)
├── math/        # 数学ユーティリティ (Matrix4x4, Quaternion, Vector3)
├── model/       # ドメインモデル (DicomStudy, DicomSeries, DicomRepository...)
├── repository/  # データアクセス層 (SqliteService, DatabaseRegistry)
├── threads/     # スレッド管理
├── ui/          # Swing UI コントローラ・ダイアログ
└── util/        # ユーティリティ (MemoryManager, StringUtils)
```

## 成果物テンプレート（docs/templates/ 配下に配置済み）

| テンプレート | パス | 用途 |
|-------------|------|------|
| HOROS 仕様書 | `docs/templates/horos-spec-template.md` | HOROS UI・設定のマッピング |
| 実装ステータス | `docs/templates/implementation-status-template.md` | クラスの実装度一覧 |
| ギャップ分析 | `docs/templates/gap-analysis-template.md` | ギャップの優先度付き抽出 |
| PR チェックリスト | `docs/templates/pr-checklist-template.md` | PR 作成時の確認項目 |

各成果物は対応するテンプレートを使用して `docs/` に保存し、PR でリンクを貼ってください。

## 運用ルール

### ブランチ命名

- 解析: `analysis/{team}-{topic}`（例: `analysis/horos-burner`）
- 実装: `feature/{short-task}`（例: `feature/burner-panel-fix`）

### コミットメッセージ

- 日本語で短く要約
- 該当 HOROS ソースを参照（例: `"Fix burner panel bottom border (HOROS BurnViewer.xib)"`）

### 優先度基準

| 優先度 | 基準 |
|------|------|
| **P0** | 基本動作が壊れている（ファイル読み込み、表示の欠落等） |
| **P1** | 主要機能の未実装または不具合（測定、WL/WW、ビューア同期等） |
| **P2** | 拡張・改善（UI モダン化、パフォーマンス最適化等） |

## 次のアクション

1. 解析を開始するチームは `docs/horos-spec/` または `docs/oculusjj-status/` を作成し、対応テンプレートを使って最初の成果物を配置
2. Gap Filler は Diff チームの成果物が出揃い次第、実装に取り掛かる