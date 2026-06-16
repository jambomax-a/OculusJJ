package com.oculus.jj.utils;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.*;

/**
 * UIスタイルを管理する共通定数クラス。
 * アプリケーション全体で統一された見た目を保証するため使用される。
 */
public class UIStyle {
    // ベースカラー（Horos/OculusJJ スタイル）
    public static final Color BACKGROUND_COLOR = new Color(240, 240, 240);
    public static final Color TEXT_COLOR = Color.BLACK;
    public static final Color BORDER_COLOR = Color.LIGHT_GRAY;

    // ボタン・インタラクションカラー
    public static final Color SELECTED_ITEM_COLOR = new Color(252, 177, 141); // 鮭色 (Salmon)
    public static final Color FUSIONED_ITEM_COLOR = new Color(195, 249, 145); // 薄緑 (Pale Green)
    public static final Color OPEN_ITEM_COLOR = new Color(200, 230, 200); // 緑系統
    public static final Color DIFFERENT_STUDY_COLOR = new Color(245, 245, 245); // 薄グレー
    public static final Color BOTTOM_LINE_COLOR = new Color(60, 60, 60); // 暗いグレー

    // フォント
    public static final Font MAIN_FONT = new Font("SansSerif", Font.PLAIN, 13);
    public static final Font HEADER_FONT = new Font("SansSerif", Font.BOLD, 14);

    // パネル境界線の太さ
    public static final int BORDER_WIDTH = 1;

    /**
     * タートルバーやメニューバー用の共通の境界線を取得。
     */
    public static Border getCommonBorder() {
        return BorderFactory.createMatteBorder(0, 0, BORDER_WIDTH, 0, BORDER_COLOR);
    }

    /**
     * 背景色を適用したパネルを作成するユーティリティ。
     */
    public static JPanel createStyledPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(BACKGROUND_COLOR);
        return panel;
    }
}