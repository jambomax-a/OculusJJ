# 設定画面（Preference Panes）マッピング仕様書

## 1. 概要
本ドキュメントは、HOROSのPreference（設定）画面に存在する各項目をJava/Swing環境で再現するためのマッピングテーブルである。設定値の管理には以下の二段階方式を採用する。
1. **永続化層**: `java.util.prefs.Preferences` (Windowsレジストリ等) を使用し、OS標準のパスに保存。
2. **アプリケーション層**: `AppPreferences` クラスを介して型安全にアクセス。必要に応じて `application.properties` からのオーバーライドを可能とする。

## 2. 設定項目マッピングテーブル

### 2.1 General (一般設定)
| 設定項目 | プロパティキー | データ型 | デフォルト値 | 説明 |
| :--- | :--- | :--- | :--- | :--- |
| DICOMデータフォルダ | `data.dicom.folder` | `Path` | `~/OculusJJ/DICOM` | DICOMファイルの物理保存先 |
| データベースフォルダ | `data.database.folder` | `Path` | `~/OculusJJ/Database` | SQLite DBファイルの保存先 |
| アプリケーション言語 | `ui.language` | `String` | `ja` | `ja` (日本語), `en` (英語) |
| UIテーマ | `ui.theme` | `String` | `system` | `light`, `dark`, `system` |
| 起動時に最大化 | `window.maximized` | `Boolean` | `false` | 起動時のウィンドウ状態 |

### 2.2 Display (表示設定)
| 設定項目 | プロパティキー | データ型 | デフォルト値 | 説明 |
| :--- | :--- | :--- | :--- | :--- | 
| デフォルトWindow Level | `image.default.window.level` | `Integer` | `0` | 初期表示時のウィンドウレベル |
| デフォルトWindow Width | `image.default.window.width` | `Integer` | `0` | 初期表示時のウィンドウ幅 |
| 補完アルゴリズム | `image.interpolation` | `String` | `bilinear` | `bilinear` (双線形), `nearest` (最近傍) |
| 輝度値の丸め処理 | `image.rounding.mode` | `String` | `half_up` | ピクセル値変換時の丸め方式 |

### 2.3 Network (ネットワーク設定)
| 設定項目 | プロパティキー | データ型 | デフォルト値 | 説明 |
| :--- | :--- | :--- | :--- | :--- | 
| Local AE Title | `net.local.ae.title` | `String` | `JJDICOM_VIEWER` | 自局のAEタイトル |
| Local Port | `net.local.port` | `Integer` | `104` | 受信待ちポート番号 |
| 通信タイムアウト | `net.timeout.ms` | `Integer` | `30000` | PACS通信のタイムアウト(ms) |
| 自動ルーティング有効化 | `net.auto.routing` | `Boolean` | `false` | 受信データの自動フォルダ振り分け |

### 2.4 Tool & Annotation (ツール設定)
| 設定項目 | プロパティキー | データ型 | デフォルト値 | 説明 |
| :--- | :--- | :--- | :--- | :--- | 
| 計測線デフォルト色 | `tool.measure.color` | `Color` | `#FFFF00` | 距離測定線のデフォルト色 |
| 線の太さ | `tool.line.thickness` | `Float` | `2.0` | ROIおよび計測線の太さ (px) |
| 小数点表示桁数 | `tool.measure.precision` | `Integer` | `2` | 距離表示の小数点以下桁数 |
| ROIデフォルト不透明度 | `tool.roi.opacity` | `Float` | `1.0` | 塗りつぶしROIの不透明度 (0.0-1.0) |

### 2.5 Performance (パフォーマンス設定)
| 設定項目 | プロパティキー | データ型 | デフォルト値 | 説明 |
| :--- | :--- | :--- | :--- | :--- | 
| LOD閾値 | `perf.lod.threshold` | `Integer` | `1024` | 低解像度表示に切り替わるピクセル数 |
| キャッシュサイズ | `perf.cache.size.mb` | `Integer` | `2048` | 読み込み済みボリュームデータの最大メモリ量 |
| 並列処理スレッド数 | `perf.threads.max` | `Integer` | `Runtime.availableProcessors()` | CPR等の再構成に使用する最大スレッド数 |

## 3. Java実装ガイドライン

### 3.1 設定変更の伝搬 (Event-Driven Update)
設定変更が即座にビューアに反映されるよう、以下のパターンを採用する。
1. **ConfigurationManager**: 設定値の変更時に `PropertyChangeSupport` を用いてイベントを通知する。
2. **Subscriber**: `MPRViewPanel` や `ROIManager` は設定変更イベントを購読し、`repaint()` や再計算をトリガーする。

### 3.2 型安全なアクセス
`getProperty` メソッドによる文字列ベースのアクセスを避け、以下のような型安全なラッパーを提供する。
```java
public int getIntProperty(String key, int defaultValue) {
    String val = appProperties.getProperty(key);
    return val != null ? Integer.parseInt(val) : defaultValue;
}
```

## 4. 設定ファイルの優先順位
1. **ユーザー指定設定**: `config/application.properties` (最優先)
2. **デフォルト設定**: `ConfigurationManager` 内にハードコードされたデフォルト値
3. **環境変数**: `${user.home}` 等による動的展開
