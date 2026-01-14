import * as vscode from 'vscode';
import Anthropic from '@anthropic-ai/sdk';
import * as fs from 'fs';
import * as path from 'path';
import { exec } from 'child_process';
import { promisify } from 'util';

const execAsync = promisify(exec);

interface Message {
    role: 'user' | 'assistant';
    content: string | Array<any>;
}

export class ChatViewProvider implements vscode.WebviewViewProvider {
    public static readonly viewType = 'dicomChatView';
    private _view?: vscode.WebviewView;
    private _messages: Message[] = [];
    private _anthropic?: Anthropic;

    constructor(private readonly _extensionUri: vscode.Uri) {}

    public resolveWebviewView(
        webviewView: vscode.WebviewView,
        context: vscode.WebviewViewResolveContext,
        _token: vscode.CancellationToken
    ) {
        this._view = webviewView;

        webviewView.webview.options = {
            enableScripts: true,
            localResourceRoots: [this._extensionUri]
        };

        webviewView.webview.html = this._getHtmlForWebview(webviewView.webview);

        // Webviewからのメッセージを受信
        webviewView.webview.onDidReceiveMessage(async (data) => {
            switch (data.type) {
                case 'sendMessage':
                    await this.handleUserMessage(data.message);
                    break;
                case 'clearChat':
                    this.clearChat();
                    break;
            }
        });

        // APIキーの初期化
        this.initializeAnthropicClient();
    }

    private initializeAnthropicClient() {
        const config = vscode.workspace.getConfiguration('dicomChat');
        const apiKey = config.get<string>('apiKey');

        if (apiKey && apiKey.trim() !== '') {
            try {
                this._anthropic = new Anthropic({
                    apiKey: apiKey.trim()
                });
            } catch (error) {
                console.error('Anthropic初期化エラー:', error);
                this._anthropic = undefined;
            }
        } else {
            this._anthropic = undefined;
        }
    }

    private async handleUserMessage(message: string) {
        if (!message.trim()) {
            return;
        }

        // ユーザーメッセージを追加
        this._messages.push({
            role: 'user',
            content: message
        });

        // UIに表示
        this._view?.webview.postMessage({
            type: 'addMessage',
            role: 'user',
            content: message
        });

        // ローディング表示
        this._view?.webview.postMessage({
            type: 'setLoading',
            loading: true
        });

        // Claude APIを呼び出す
        try {
            // APIキー再確認
            this.initializeAnthropicClient();

            if (!this._anthropic) {
                throw new Error('APIキーが設定されていません');
            }

            const config = vscode.workspace.getConfiguration('dicomChat');
            const model = config.get<string>('model') || 'claude-sonnet-4-5-20250929';
            const maxTokens = config.get<number>('maxTokens') || 4096;

            const tools = this.getTools();

            let response = await this._anthropic.messages.create({
                model: model,
                max_tokens: maxTokens,
                messages: this._messages.map(msg => ({
                    role: msg.role,
                    content: msg.content
                })),
                tools: tools,
                system: 'あなたはDICOM医用画像ビューワーの開発を支援するAIアシスタントです。プロジェクト内のファイルを読み取り、検索し、編集することができます。必要に応じてツールを使用して、ユーザーの質問に正確に答えてください。丁寧で分かりやすい日本語で回答してください。'
            });

            // ツール使用のループ処理
            while (response.stop_reason === 'tool_use') {
                const toolUseBlocks = response.content.filter(block => block.type === 'tool_use');
                const toolResults: any[] = [];

                for (const toolUse of toolUseBlocks) {
                    if (toolUse.type === 'tool_use') {
                        const result = await this.executeTool(toolUse.name, toolUse.input);
                        toolResults.push({
                            type: 'tool_result',
                            tool_use_id: toolUse.id,
                            content: JSON.stringify(result)
                        });

                        // ツール実行をUIに表示
                        this._view?.webview.postMessage({
                            type: 'addMessage',
                            role: 'assistant',
                            content: `🔧 ツール実行: ${toolUse.name}\n${JSON.stringify(toolUse.input, null, 2)}`
                        });
                    }
                }

                // アシスタントのメッセージとツール結果を追加
                this._messages.push({
                    role: 'assistant',
                    content: response.content
                });

                this._messages.push({
                    role: 'user',
                    content: toolResults
                });

                // 次のレスポンスを取得
                response = await this._anthropic.messages.create({
                    model: model,
                    max_tokens: maxTokens,
                    messages: this._messages.map(msg => ({
                        role: msg.role,
                        content: msg.content
                    })),
                    tools: tools,
                    system: 'あなたはDICOM医用画像ビューワーの開発を支援するAIアシスタントです。プロジェクト内のファイルを読み取り、検索し、編集することができます。必要に応じてツールを使用して、ユーザーの質問に正確に答えてください。丁寧で分かりやすい日本語で回答してください。'
                });
            }

            // 最終的なテキスト応答を取得
            const textBlocks = response.content.filter(block => block.type === 'text');
            const assistantMessage = textBlocks.map(block => block.type === 'text' ? block.text : '').join('\n');

            this._messages.push({
                role: 'assistant',
                content: response.content
            });

            this._view?.webview.postMessage({
                type: 'addMessage',
                role: 'assistant',
                content: assistantMessage
            });

        } catch (error: any) {
            let errorMsg = 'エラーが発生しました';
            if (error.message) {
                errorMsg = error.message;
            }
            if (error.status === 401) {
                errorMsg = 'APIキーが無効です。設定を確認してください。';
            }

            vscode.window.showErrorMessage(`Claude API エラー: ${errorMsg}`);

            this._view?.webview.postMessage({
                type: 'addMessage',
                role: 'assistant',
                content: `❌ エラー: ${errorMsg}\n\n設定(Ctrl+,)で「dicomChat.apiKey」を確認してください。`
            });
        } finally {
            this._view?.webview.postMessage({
                type: 'setLoading',
                loading: false
            });
        }
    }

    public clearChat() {
        this._messages = [];
        this._view?.webview.postMessage({
            type: 'clearMessages'
        });
    }

    public sendMessage(message: string) {
        this.handleUserMessage(message);
    }

    private getTools(): any[] {
        return [
            {
                name: 'read_file',
                description: 'ファイルの内容を読み取ります。',
                input_schema: {
                    type: 'object' as const,
                    properties: {
                        file_path: {
                            type: 'string' as const,
                            description: 'ワークスペースルートからの相対パス'
                        }
                    },
                    required: ['file_path']
                }
            },
            {
                name: 'search_files',
                description: 'ファイル名パターンでファイルを検索します。',
                input_schema: {
                    type: 'object' as const,
                    properties: {
                        pattern: {
                            type: 'string' as const,
                            description: 'globパターン (例: **/*.java, src/**/ViewerController.java)'
                        }
                    },
                    required: ['pattern']
                }
            },
            {
                name: 'search_code',
                description: 'コード内のテキストを検索します。',
                input_schema: {
                    type: 'object' as const,
                    properties: {
                        query: {
                            type: 'string' as const,
                            description: '検索するテキスト'
                        },
                        file_pattern: {
                            type: 'string' as const,
                            description: '検索対象のファイルパターン (オプション、例: *.java)'
                        }
                    },
                    required: ['query']
                }
            },
            {
                name: 'execute_command',
                description: 'シェルコマンドを実行します（ビルド、テストなど）。',
                input_schema: {
                    type: 'object' as const,
                    properties: {
                        command: {
                            type: 'string' as const,
                            description: '実行するコマンド'
                        }
                    },
                    required: ['command']
                }
            }
        ];
    }

    private async executeTool(toolName: string, input: any): Promise<any> {
        const workspaceFolder = vscode.workspace.workspaceFolders?.[0];
        if (!workspaceFolder) {
            return { error: 'ワークスペースが開かれていません' };
        }

        const workspacePath = workspaceFolder.uri.fsPath;

        try {
            switch (toolName) {
                case 'read_file':
                    return await this.readFile(workspacePath, input.file_path);

                case 'search_files':
                    return await this.searchFiles(input.pattern);

                case 'search_code':
                    return await this.searchCode(input.query, input.file_pattern);

                case 'execute_command':
                    return await this.executeCommand(workspacePath, input.command);

                default:
                    return { error: `未知のツール: ${toolName}` };
            }
        } catch (error: any) {
            return { error: error.message };
        }
    }

    private async readFile(workspacePath: string, filePath: string): Promise<any> {
        try {
            const fullPath = path.join(workspacePath, filePath);
            const content = fs.readFileSync(fullPath, 'utf-8');
            return { content, path: filePath };
        } catch (error: any) {
            return { error: `ファイル読み取りエラー: ${error.message}` };
        }
    }

    private async searchFiles(pattern: string): Promise<any> {
        try {
            const files = await vscode.workspace.findFiles(pattern, '**/node_modules/**', 100);
            return {
                files: files.map(f => vscode.workspace.asRelativePath(f))
            };
        } catch (error: any) {
            return { error: `ファイル検索エラー: ${error.message}` };
        }
    }

    private async searchCode(query: string, filePattern?: string): Promise<any> {
        try {
            const pattern = filePattern || '**/*';
            const files = await vscode.workspace.findFiles(pattern, '**/node_modules/**', 50);
            const results: any[] = [];

            for (const file of files) {
                try {
                    const content = fs.readFileSync(file.fsPath, 'utf-8');
                    const lines = content.split('\n');
                    const matches: any[] = [];

                    lines.forEach((line, index) => {
                        if (line.includes(query)) {
                            matches.push({
                                line: index + 1,
                                content: line.trim()
                            });
                        }
                    });

                    if (matches.length > 0) {
                        results.push({
                            file: vscode.workspace.asRelativePath(file),
                            matches: matches.slice(0, 10) // 最大10マッチ
                        });
                    }
                } catch (err) {
                    // ファイル読み取りエラーは無視
                }

                if (results.length >= 10) break; // 最大10ファイル
            }

            return { results };
        } catch (error: any) {
            return { error: `コード検索エラー: ${error.message}` };
        }
    }

    private async executeCommand(workspacePath: string, command: string): Promise<any> {
        try {
            const { stdout, stderr } = await execAsync(command, {
                cwd: workspacePath,
                timeout: 30000 // 30秒タイムアウト
            });
            return {
                stdout: stdout.substring(0, 5000), // 最大5000文字
                stderr: stderr.substring(0, 1000)
            };
        } catch (error: any) {
            return {
                error: `コマンド実行エラー: ${error.message}`,
                stdout: error.stdout?.substring(0, 5000),
                stderr: error.stderr?.substring(0, 1000)
            };
        }
    }

    private _getHtmlForWebview(webview: vscode.Webview) {
        return `<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>DICOM Chat</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }

        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background-color: var(--vscode-editor-background);
            color: var(--vscode-editor-foreground);
            height: 100vh;
            display: flex;
            flex-direction: column;
        }

        #chat-container {
            flex: 1;
            overflow-y: auto;
            padding: 16px;
            display: flex;
            flex-direction: column;
            gap: 12px;
        }

        .message {
            padding: 12px;
            border-radius: 8px;
            max-width: 85%;
            word-wrap: break-word;
            white-space: pre-wrap;
        }

        .message.user {
            background-color: var(--vscode-input-background);
            border: 1px solid var(--vscode-input-border);
            align-self: flex-end;
        }

        .message.assistant {
            background-color: var(--vscode-editor-inactiveSelectionBackground);
            border: 1px solid var(--vscode-panel-border);
            align-self: flex-start;
        }

        #input-container {
            padding: 12px;
            background-color: var(--vscode-panel-background);
            border-top: 1px solid var(--vscode-panel-border);
            display: flex;
            gap: 8px;
        }

        #message-input {
            flex: 1;
            padding: 8px 12px;
            background-color: var(--vscode-input-background);
            color: var(--vscode-input-foreground);
            border: 1px solid var(--vscode-input-border);
            border-radius: 4px;
            font-size: 14px;
        }

        #send-button {
            padding: 8px 16px;
            background-color: var(--vscode-button-background);
            color: var(--vscode-button-foreground);
            border: none;
            border-radius: 4px;
            cursor: pointer;
        }

        .header {
            padding: 12px;
            background-color: var(--vscode-panel-background);
            border-bottom: 1px solid var(--vscode-panel-border);
            display: flex;
            justify-content: space-between;
        }

        .loading {
            padding: 12px;
            text-align: center;
            font-style: italic;
            color: var(--vscode-descriptionForeground);
        }

        #send-button:disabled {
            opacity: 0.5;
            cursor: not-allowed;
        }

        .thinking-indicator {
            padding: 8px 12px;
            background-color: var(--vscode-inputValidation-infoBackground);
            border: 1px solid var(--vscode-inputValidation-infoBorder);
            border-radius: 4px;
            font-size: 12px;
            color: var(--vscode-inputValidation-infoForeground);
            align-self: flex-start;
        }
    </style>
</head>
<body>
    <div class="header">
        <h3>DICOM Chat Assistant</h3>
        <button id="clear-button">クリア</button>
    </div>
    <div id="chat-container"></div>
    <div id="input-container">
        <input type="text" id="message-input" placeholder="メッセージを入力..." />
        <button id="send-button">送信</button>
    </div>

    <script>
        const vscode = acquireVsCodeApi();
        const chatContainer = document.getElementById('chat-container');
        const messageInput = document.getElementById('message-input');
        const sendButton = document.getElementById('send-button');
        const clearButton = document.getElementById('clear-button');

        let isLoading = false;

        function sendMessage() {
            const message = messageInput.value.trim();
            if (message && !isLoading) {
                vscode.postMessage({
                    type: 'sendMessage',
                    message: message
                });
                messageInput.value = '';
            }
        }

        sendButton.addEventListener('click', sendMessage);

        messageInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter' && !isLoading) {
                e.preventDefault();
                sendMessage();
            }
        });

        clearButton.addEventListener('click', () => {
            vscode.postMessage({ type: 'clearChat' });
        });

        window.addEventListener('message', (event) => {
            const message = event.data;
            switch (message.type) {
                case 'addMessage':
                    const messageDiv = document.createElement('div');
                    messageDiv.className = \`message \${message.role}\`;
                    messageDiv.textContent = message.content;
                    chatContainer.appendChild(messageDiv);
                    chatContainer.scrollTop = chatContainer.scrollHeight;
                    break;
                case 'clearMessages':
                    chatContainer.innerHTML = '';
                    break;
                case 'setLoading':
                    isLoading = message.loading;
                    sendButton.disabled = isLoading;
                    messageInput.disabled = isLoading;

                    if (message.loading) {
                        const loadingDiv = document.createElement('div');
                        loadingDiv.className = 'thinking-indicator';
                        loadingDiv.id = 'loading-indicator';
                        loadingDiv.textContent = '💭 思考中...';
                        chatContainer.appendChild(loadingDiv);
                    } else {
                        const loadingIndicator = document.getElementById('loading-indicator');
                        if (loadingIndicator) {
                            loadingIndicator.remove();
                        }
                    }
                    chatContainer.scrollTop = chatContainer.scrollHeight;
                    break;
            }
        });

        messageInput.focus();
    </script>
</body>
</html>`;
    }
}
