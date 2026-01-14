import * as vscode from 'vscode';

interface Message {
    role: 'user' | 'assistant';
    content: string;
}

export class ChatViewProvider implements vscode.WebviewViewProvider {
    public static readonly viewType = 'dicomChatView';
    private _view?: vscode.WebviewView;
    private _messages: Message[] = [];

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

        // テスト用: すぐにダミーの応答を返す
        setTimeout(() => {
            const testResponse = `テストメッセージを受信しました: "${message}"\n\nこれはテストバージョンです。API接続の問題を切り分けるため、実際のClaude APIは呼び出していません。`;

            this._messages.push({
                role: 'assistant',
                content: testResponse
            });

            this._view?.webview.postMessage({
                type: 'addMessage',
                role: 'assistant',
                content: testResponse
            });

            this._view?.webview.postMessage({
                type: 'setLoading',
                loading: false
            });
        }, 1000);
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

    private _getHtmlForWebview(webview: vscode.Webview) {
        return `<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>DICOM Chat (Test)</title>
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
        }
    </style>
</head>
<body>
    <div class="header">
        <h3>DICOM Chat (テストモード)</h3>
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

        function sendMessage() {
            const message = messageInput.value.trim();
            if (message) {
                vscode.postMessage({
                    type: 'sendMessage',
                    message: message
                });
                messageInput.value = '';
            }
        }

        sendButton.addEventListener('click', sendMessage);

        messageInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
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
                    if (message.loading) {
                        const loadingDiv = document.createElement('div');
                        loadingDiv.className = 'loading';
                        loadingDiv.id = 'loading-indicator';
                        loadingDiv.textContent = '応答を生成中...';
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
