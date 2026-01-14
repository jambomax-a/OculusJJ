# DICOM Chat Assistant

VS Code のサイドバーに統合された AI チャットアシスタントです。DICOM 医用画像ビューワーの開発をサポートします。

## 機能

- VS Code サイドバーにチャットパネルを表示
- Claude AI（Anthropic）との対話
- DICOM ビューワープロジェクトに特化したアシスタント
- チャット履歴の管理

## セットアップ

### 1. 依存関係のインストール

```bash
cd vscode-dicom-chat-extension
npm install
```

### 2. Claude API キーの設定

1. [Anthropic Console](https://console.anthropic.com/) でAPIキーを取得
2. VS Code の設定を開く（`Ctrl+,` または `Cmd+,`）
3. "DICOM Chat" で検索
4. "Dicom Chat: Api Key" にAPIキーを入力

### 3. 拡張機能のビルド

```bash
npm run compile
```

### 4. 拡張機能の実行

1. VS Code で拡張機能プロジェクトを開く
2. F5 キーを押してデバッグモードで起動
3. 新しいウィンドウが開きます
4. サイドバーに "DICOM Chat" アイコンが表示されます

## 使い方

1. サイドバーの "DICOM Chat" アイコンをクリック
2. チャット入力欄にメッセージを入力
3. Enter キーまたは「送信」ボタンでメッセージを送信
4. Claude AI からの応答が表示されます

## 設定

- `dicomChat.apiKey`: Claude API キー
- `dicomChat.model`: 使用する Claude モデル（デフォルト: claude-3-5-sonnet-20241022）
- `dicomChat.maxTokens`: 最大トークン数（デフォルト: 4096）

## コマンド

- `dicomChat.clearChat`: チャット履歴をクリア
- `dicomChat.sendMessage`: メッセージを送信（コマンドパレットから）

## 開発

### ビルド

```bash
npm run compile
```

### ウォッチモード

```bash
npm run watch
```

### リント

```bash
npm run lint
```

## パッケージ化

拡張機能を `.vsix` ファイルとしてパッケージ化：

```bash
npm install -g @vscode/vsce
vsce package
```

## インストール

パッケージ化した拡張機能をインストール：

```bash
code --install-extension dicom-chat-assistant-0.0.1.vsix
```

## ライセンス

MIT
