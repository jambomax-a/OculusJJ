# OculusJJ Agent Teams

## Overview

This project is a Java/Swing port of HOROS-20240407 (DICOM viewer / medical image browser). The workspace contains:
- horos-20240407/Horos/ -- original Objective-C Cocoa source (reference)
- src/main/java/com/oculus/jj/ -- current Java/Swing port (target)

Team 1: HOROS-20240407 解析チーム
役割: macOS/HOROS オリジナルソースの仕様解読
対象範囲: horos-20240407/Horos/ の .xib, .m, .h ファイル全体
アウトプット: 各機能のUI構造、ラベル、配置座標、ボタン名、チェックボックス状態、デフォルト値、NSUserDefaults key名のマッピング表
出力ファイル例: docs/horos-spec/01-burner.md, docs/horos-spec/02-browser.md など

Team 2: OculusJJ 実装解析チーム
役割: Java/Swing ポートの現状把握
対象範囲: src/main/java/com/oculus/jj/ の全Javaファイル
アウトプット: 現在の実装状況（stub含む）、クラス構造、既存のUIレイアウト、未実装機能の一覧
出力ファイル例: docs/oculusjj-status/current-state.md


Team 3: Diff 解析チーム
役割: 1と2の差分抽出
入力は: Team 1の仕様書 + Team 2の状態レポート
アウトプット: 「HOROSにはあるがOculusJJにない」機能の優先度付きリスト、実装ギャップの詳細な記述
出力ファイル例: docs/diff/gap-analysis.md, docs/diff/priority-list.md


Team 4: Gap Filler コーディングチーム
役割: Diff を埋める実装
入力は: Team 3の差分リスト + HOROS仕様書
アウトプット: Java/Swing コード、テスト、ビルド成功確認
作業フロー: 差分リストから1機能ずつ実装 → ビルドテスト → クローズ

HOROS解析    → 仕様書（何を作るべきか）
                 ↓
OculusJJ解析 → 現状マップ（どこまでできているか）
                 ↓
   Diff分析  → ギャップリスト + 優先順位
                 ↓
  Gap Filler → コード実装 → ビルド確認 → クローズ


注意点:
HOROS解析チームとOculusJJ解析チームは並行実行できる（独立したタスク）
Diff分析は両方の出力完了後
Gap Fillerは差分リストの優先順位付きで機能ごとに順次処理