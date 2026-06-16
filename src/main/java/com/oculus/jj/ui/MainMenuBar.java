package com.oculus.jj.ui;

import javax.swing.*;
import com.oculus.jj.utils.LanguageManager;

/**
 * MainMenuBar - メインメニューバー
 * 
 * HOROS-20240407のメニューバーをJava Swingに移植
 */
public class MainMenuBar extends JMenuBar {
    
    /**
     * コンストラクタ
     */
    public MainMenuBar() {
        super();
        initMenu();
    }

    /**
     * メニューの初期化
     */
    private void initMenu() {
        // ファイルメニュー
        JMenu fileMenu = new JMenu(LanguageManager.get("menu_file"));
        fileMenu.add(new JMenuItem(LanguageManager.get("menu_file.new"))); // 新規
        fileMenu.add(new JMenuItem(LanguageManager.get("menu_file.open"))); // 開く
        fileMenu.addSeparator();
        fileMenu.add(new JMenuItem(LanguageManager.get("menu_file.exit"))); // 終了
        add(fileMenu);

        // ビューメニュー (Viewer)
        JMenu viewMenu = new JMenu(LanguageManager.get("menu_view"));
        JMenu navigation = new JMenu(""); 
        navigation.add(new JMenuItem(LanguageManager.get("menu_view.zoom"))); // ズーム
        navigation.add(new JMenuItem(LanguageManager.get("menu_view.pan"))); // パン
        navigation.add(new JMenuItem(LanguageManager.get("menu_view.rotate"))); // 回転
        viewMenu.add(navigation);

        JMenu display = new JMenu(""); 
        display.add(new JMenuItem(LanguageManager.get("display.wlww"))); // WL/WW
        display.add(new JMenuItem(LanguageManager.get("display.flip_lr"))); // 左右反転
        display.add(new JMenuItem(LanguageManager.get("display.flip_ud"))); // 上下反転
        display.add(new JMenuItem(LanguageManager.get("display.refresh"))); // リフレッシュ
        viewMenu.add(display);

        JMenu advanced = new JMenu(""); 
        advanced.add(new JMenuItem(LanguageManager.get("advanced.mip3d"))); // 3D MIP
        advanced.add(new JMenuItem(LanguageManager.get("advanced.vr3d"))); // 3D VR
        advanced.add(new JMenuItem(LanguageManager.get("advanced.cpr"))); // Curved MPR
        viewMenu.add(advanced);
        add(viewMenu);

        // ツールメニュー
        JMenu toolsMenu = new JMenu(LanguageManager.get("menu_tools"));
        JMenu roiTools = new JMenu(""); 
        roiTools.add(new JMenuItem(LanguageManager.get("tools_roi.oval"))); // 楕円
        roiTools.add(new JMenuItem(LanguageManager.get("tools_roi.poly_closed"))); // 多角形 (閉)
        roiTools.add(new JMenuItem(LanguageManager.get("tools_roi.poly_open"))); // 多角形 (開)
        roiTools.add(new JMenuItem(LanguageManager.get("tools_roi.brush"))); // ブラシ
        roiTools.add(new JMenuItem(LanguageManager.get("tools_roi.axis"))); // 軸
        roiTools.add(new JMenuItem(LanguageManager.get("tools_roi.dangle"))); // 動角度
        roiTools.add(new JMenuItem(LanguageManager.get("tools_roi.perp"))); // 垂直線
        roiTools.add(new JMenuItem(LanguageManager.get("tools_roi.length"))); // 距離測定
        roiTools.add(new JMenuItem(LanguageManager.get("tools_roi.angle"))); // 角度
        toolsMenu.add(roiTools);

        JMenu annotation = new JMenu(""); 
        annotation.add(new JMenuItem(LanguageManager.get("annotation_text"))); // テキスト
        annotation.add(new JMenuItem(LanguageManager.get("annotation_arrow"))); // 矢印
        annotation.add(new JMenuItem(LanguageManager.get("annotation_pencil"))); // 鉛筆
        toolsMenu.add(annotation);
        add(toolsMenu);

        // 解析メニュー (Analysis)
        JMenu analysisMenu = new JMenu(""); 
        analysisMenu.add(new JMenuItem(LanguageManager.get("analysis_bones"))); // 骨除去
        analysisMenu.add(new JMenuItem(LanguageManager.get("analysis_repulse"))); // リパルサー
        add(analysisMenu);

        // 設定メニュー
        JMenu settingsMenu = new JMenu(LanguageManager.get("menu_settings"));
        settingsMenu.add(new JMenuItem(LanguageManager.get("settings_basic"))); // 基本設定
        settingsMenu.add(new JMenuItem(LanguageManager.get("settings_display"))); // 表示設定
        add(settingsMenu);
    }
}
