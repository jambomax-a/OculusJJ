package com.oculus.jj.model;

import java.io.Serializable;

/**
 * KeyBinding - ホットキーバインディングのデータを表す。
 */
public class KeyBinding implements Serializable {
    private static final long serialVersionUID = 1L;

    public String displayName;
    public String keyCombo;
    public int actionId;

    public KeyBinding() {
        this.displayName = "";
        this.keyCombo = "";
        this.actionId = -1;
    }

    public KeyBinding(String displayName, String keyCombo, int actionId) {
        this.displayName = displayName != null ? displayName : "";
        this.keyCombo = keyCombo != null ? keyCombo : "";
        this.actionId = actionId;
    }

    /**
     * 表示形式のキーコンボ（例: "Ctrl+Shift+A"）をパースし、モディファイアフラグとキーコードに変換する。
     */
    public static int parseModifiers(String combo) {
        if (combo == null || combo.isEmpty()) return 0;

        int mod = 0;
        String[] parts = combo.split("\\+");
        for (String part : parts) {
            switch (part.trim().toLowerCase()) {
                case "ctrl":
                    mod |= java.awt.event.InputEvent.CTRL_DOWN_MASK;
                    break;
                case "alt":
                    mod |= java.awt.event.InputEvent.ALT_DOWN_MASK;
                    break;
                case "shift":
                    mod |= java.awt.event.InputEvent.SHIFT_DOWN_MASK;
                    break;
                case "meta":
                    mod |= java.awt.event.InputEvent.META_DOWN_MASK;
                    break;
            }
        }
        return mod;
    }

    /**
     * KeyEvent の Modifier 情報と キーコード から、表示用キーコンボ文字列を生成する。
     */
    public static String generateComboString(int modifiers, int keyCode) {
        StringBuilder sb = new StringBuilder();
        if ((modifiers & java.awt.event.InputEvent.CTRL_DOWN_MASK) != 0) {
            sb.append("Ctrl+");
        }
        if ((modifiers & java.awt.event.InputEvent.ALT_DOWN_MASK) != 0) {
            sb.append("Alt+");
        }
        if ((modifiers & java.awt.event.InputEvent.SHIFT_DOWN_MASK) != 0) {
            sb.append("Shift+");
        }
        if ((modifiers & java.awt.event.InputEvent.META_DOWN_MASK) != 0) {
            sb.append("Meta+");
        }

        // キー名を取得（英数字の場合は大文字、特殊キーの場合は F1, BackSpace 等）
        String keyName = java.awt.event.KeyEvent.getKeyText(keyCode);
        if (keyName == null || keyName.isEmpty()) {
            keyName = String.valueOf((char) keyCode);
        }
        sb.append(keyName);
        return sb.toString();
    }

    @Override
    public String toString() {
        return displayName + " = [" + keyCombo + "]";
    }
}