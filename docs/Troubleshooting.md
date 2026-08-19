# OculusJJ Troubleshooting

## 十字カーソルになる問題 (X11 / WSL / Linux)

**症状**: アプリ起動後、マウスカーソルが常に十字 (crosshair) になり、クリックできないように見える。
(実際はクリック自体は動作しています)

**原因**: X サーバー側に X カーソルテーマ (`xcursor`) がない環境では、
Java (libXcursor) がカーソルを読み込めず X server がデフォルトの十字カーソルを
フォールバック表示します。アプリ側のバグではありません。

**確認方法**:
```bash
# カーソルテーマがインストールされているか
ls /usr/share/icons/*/cursors 2>/dev/null   # 何も出なければ未インストール
echo $XCURSOR_THEME                          # 空なら未設定
```

**修正方法 (Ubuntu/Debian)**:
```bash
# 1. カーソルテーマのインストール
sudo apt install dmz-cursor-theme

# 2. 環境変数の設定 (カーソルをレンダリングする端末のプロファイルに)
echo 'export XCURSOR_THEME=DMZ-Black' >> ~/.bashrc
echo 'export XCURSOR_SIZE=24' >> ~/.bashrc
source ~/.bashrc

# 3. アプリを再起動
```

> **注**: WSL + Windows 側 X サーバー (VcXsrv / X410 / Xming / Windows Terminal)
> を使う場合、カーソルのレンダリングは **X サーバーを実行している側 (Windows)** が
> 担当します。WSL 側で `XCURSOR_THEME` を設定しても効かない場合があります。
> その場合は X サーバー アプリ側のカーソル設定 (VcXsrv: Xserver Properties →
> "Allow Windows class override" / Xming: cursor shape の設定) で矢印ポインターを
> 有効にしてください。

## その他

| 問題 | 対処 |
|------|------|
| `UnsatisfiedLinkError: wrong ELF class: ELFCLASS32` | 64-bit 環境では `resources/linux-x86-64/` の OpenCV native lib を使用すること (2026-08-19 で修正済み) |
| `java.awt.HeadlessException` | JRE (ヘッドレス版) ではなく JDK あるいは JRE + X 環境で起動すること |
| データが表示されない | ローカル DB (`~/OculusJJ/Database`) が空な場合があります。Orthanc からの MOVE / DICOM インポートが必要です |
