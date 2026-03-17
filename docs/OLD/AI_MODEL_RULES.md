# AIエージェント開発用 MODELルール

## 概要
このドキュメントは、AIツールキットを使用したエージェント開発時のMODEL設定に関するルールを記載します。

## MODELの設定ルール

### 1. MODELの選択方法
- **MODELが明示的に指定されている場合**: その値をそのまま使用
- **MODELが"Auto"に設定されている場合**: 自動的に適切なMODELを選択

### 2. 必須ルール：MODELがAutoの場合
**重要**: MODELが"Auto"に設定されている場合、以下の対応が必須です：

#### 2.1 使用MODELの明示的表示
- **表示場所**: 処理開始時のログ、コンソール出力、またはレスポンスの冒頭
- **表示形式**: 
  ```
  [AI Model] Using model: <selected_model_name>
  ```
- **表示例**:
  ```
  [AI Model] Using model: Claude Haiku 4.5
  [AI Model] Using model: Claude 3.5 Sonnet
  ```

#### 2.2 表示が必須となる理由
1. **透明性**: ユーザーがどのMODELが使用されているかを明確に認識できる
2. **再現性**: 後で処理内容を追跡する際にMODELの特定が容易
3. **デバッグ**: 予期しない結果が得られた場合、MODELが原因である可能性を確認できる
4. **監査**: システム監査時にMODEL選択ログの確認が可能

#### 2.3 実装例（Javaコード）
```java
// Autoモードの場合、選択されたMODELを表示
if ("Auto".equalsIgnoreCase(modelSetting)) {
    String selectedModel = selectOptimalModel();
    logger.info("[AI Model] Using model: {}", selectedModel);
    System.out.println("[AI Model] Using model: " + selectedModel);
    // 処理を継続...
}
```

### 3. 対応するモダリティ別の推奨MODEL（参考）
- **DICOM画像解析**: Claude 3.5 Sonnet（高精度、複雑な処理向け）
- **単純なテキスト処理**: Claude Haiku 4.5（高速処理向け）
- **エージェント開発**: Claude 3.5 Sonnet（推論能力が必要）

### 4. チェックリスト
MODELがAutoに設定されている場合、以下の項目を確認してください：

- [ ] 使用MODELが明示的に表示されている
- [ ] ログに「[AI Model] Using model: XXX」が記録されている
- [ ] ユーザーが使用MODELを認識できる状態である
- [ ] 複数回実行時に同じMODELが使用されている（必要に応じて）

### 5. トラブルシューティング

#### Q: 表示されたMODELと期待されるMODELが異なる
- **原因**: Autoモード時の選択ロジックの誤設定
- **対応**: selectionLogicを確認し、適切なMODELが選択されているか検証

#### Q: 表示が重複している
- **原因**: 複数の場所で同じメッセージを出力している
- **対応**: 出力場所を一箇所に統一

---

**作成日**: 2026年1月6日  
**バージョン**: 1.0  
**関連ツール**: AI Toolkit, Agent Framework
