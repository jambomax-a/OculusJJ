import * as vscode from 'vscode';
import { ChatViewProvider } from './chatViewProvider';

export function activate(context: vscode.ExtensionContext) {
    console.log('DICOM Chat Assistant が起動しました');

    // チャットビュープロバイダーを登録
    const provider = new ChatViewProvider(context.extensionUri);

    context.subscriptions.push(
        vscode.window.registerWebviewViewProvider(
            ChatViewProvider.viewType,
            provider
        )
    );

    // コマンド: チャット履歴をクリア
    context.subscriptions.push(
        vscode.commands.registerCommand('dicomChat.clearChat', () => {
            provider.clearChat();
            vscode.window.showInformationMessage('チャット履歴をクリアしました');
        })
    );

    // コマンド: メッセージを送信
    context.subscriptions.push(
        vscode.commands.registerCommand('dicomChat.sendMessage', async () => {
            const message = await vscode.window.showInputBox({
                prompt: 'メッセージを入力してください',
                placeHolder: 'Claude に質問する...'
            });

            if (message) {
                provider.sendMessage(message);
            }
        })
    );
}

export function deactivate() {
    console.log('DICOM Chat Assistant が無効化されました');
}
