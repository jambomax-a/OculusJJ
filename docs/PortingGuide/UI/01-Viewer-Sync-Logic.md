# Viewer 同期ロジック実装仕様書 (Java Porting)

## 1. 概要
Horosの核心機能である「複数ビューア間の同期（Sync）」をJavaで再現するための仕様書。本機能は、あるビューアのスライス変更を他のビューアに伝播させ、座標やインデックスを同期させるものである。

## 2. 同期イベントモデル

### 2.1 SyncEvent クラス
同期時に送信されるデータ構造。Javaでは不変(Immutable)なレコードまたはクラスとして実装する。

- `int direction`: 移動方向
- `int pos`: 現在の画像インデックス
- `float location`: スライス位置 (Slice Location)
- `String studyId`: StudyInstanceUID
- `String frameOfReferenceUID`: Frame of Reference UID
- `PixelData oPix`: 送信元のピクセルデータ参照
- `ViewerContainer view`: 送信元のビューア参照
- `boolean offsetSync`: オフセット同期フラグ

## 3. 受信側フィルタ (Sync Filter)

受信側ビューアは、以下の条件を**すべて**満たす場合のみ同期処理を実行する。一つでも不整合があれば `return` する。

| 条件 | Javaでの実装チェック | 備考 |
| :--- | :--- | :--- |
| **Global Mute** | `if (GlobalSettings.dontListenToSync) return;` | 全体的な同期停止設定 |
| **Different View** | `if (event.getSource() == this) return;` | 自分自身のイベントは無視 |
| **2D Viewer** | `if (!this.is2DViewer) return;` | 2Dビューアのみが同期対象 |
| **Window Status** | `if (this.window.isClosing()) return;` | 閉鎖中のウィンドウは除外 |
| **Recursive Guard** | `if (this.avoidRecursiveSync > 1) return;` | 同期ループの防止 (後述) |
| **Key View** | `if (!this.isKeyView) return;` | フォーカス（キービュー）がある場合のみ受信 |
| **Tiling Off** | `if (this.matrix != 0) return;` | タイリング表示時は同期不可 |

## 4. 同期モード別計算ロジック

`syncMode` に応じて、次の中座画像インデックス `newImage` を決定する。

### 4.1 Absolute (絶対値同期)
- **ロジック**: 送信側のインデックス `pos` をそのまま採用。
- **補正**: `flippedData` が true の場合、`newImage = (totalCount - 1) - pos`。

### 4.2 Ratio (比率同期)
- **ロジック**: 送信側の位置比率を計算し、受信側の総数に掛ける。
- `ratio = (float)pos / otherView.totalCount`
- `newImage = round(ratio * this.totalCount)`

### 4.3 Relative (相対値同期)
- **ロジック**: 送信側の移動量 `diff` を現在のインデックスに加減算する。
- `newImage = this.curImage + diff` (flipped時は反転)
- **ラップ処理**: インデックスが範囲外に出た場合は、反対側からループさせる。

### 4.4 Location (位置同期) - 最重要
- **条件**: `same3DReferenceWorld` (同一参照世界) または `registeredViewer` (登録済み) であること。
- **計算**: 
  1. 全画像がロード済みで手動同期中でない場合 → `findPlaneForPoint` (最近接平面探索) を使用。
  2. それ以外 → `sliceLocation` (DICOMタグ) の差分が最小となるインデックスを探索。
  3. 手動同期中 (`syncSeriesIndex != -1`) → `syncRelativeDiff` によるオフセット補正を適用。

## 5. 再帰防止メカニズム (Recursive Sync Guard)

同期イベントが連鎖して無限ループに陥るのを防ぐための実装。

1. **カウンタ**: 各ビューアに `int avoidRecursiveSync` フィールドを持つ。
2. **処理フロー**:
   - `sync()` メソッド開始時に `if (avoidRecursiveSync > 1) return;`
   - 通過後、直ちに `avoidRecursiveSync++` を実行。
   - 同期処理（画像更新など）を完了後、メソッドの最後で `avoidRecursiveSync--` を実行する。

## 6. 画像更新と交線表示

- **更新条件**: `newImage != prevImage` かつ `(this != frontMostViewer && other == frontMostViewer)` の場合に `setIndex(newImage)` を呼び出す。
- **交線 (Cross-reference Line)**: `same3DReferenceWorld` が true の場合、`computeSlice()` を呼び出し、他ビューアの断面位置を線として描画する。
