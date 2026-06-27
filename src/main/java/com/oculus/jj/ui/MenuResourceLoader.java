package com.oculus.jj.ui;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.MissingResourceException;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * MenuResourceLoader - 多言語メニューリソースローダー
 * HOROS-20240407準拠: NSLocalizedStringシステム相当の多言語対応
 * 
 * 使用方法:
 *   String text = MenuResourceLoader.getString("menu.file.open");
 *   JMenuItem item = new JMenuItem(MenuResourceLoader.getString("menu.file.open"));
 */
public class MenuResourceLoader {

    private static final Logger LOGGER = Logger.getLogger(MenuResourceLoader.class.getName());
    private static final String BUNDLE_BASE_NAME = "i18n.messages";
    
    private static ResourceBundle currentBundle;
    private static Locale currentLocale;
    private static boolean initialized = false;

    /**
     * システムデフォルトロケールで初期化
     */
    public static void initialize() {
        initialize(Locale.getDefault());
    }

    /**
     * 指定ロケールで初期化
     * 
     * @param locale 使用するロケール（ja, en 等）
     */
    public static void initialize(Locale locale) {
        currentLocale = locale;
        loadBundle(locale);
        initialized = true;
        LOGGER.info("MenuResourceLoader initialized with locale: " + locale);
    }

    /**
     * リソースバンドルを読み込み
     */
    private static void loadBundle(Locale locale) {
        try {
            currentBundle = ResourceBundle.getBundle(BUNDLE_BASE_NAME, locale);
            LOGGER.fine("Loaded menu bundle for locale: " + locale);
        } catch (MissingResourceException e) {
            LOGGER.warning("Menu bundle not found for locale: " + locale + ", falling back to default");
            // デフォルト（英語）にフォールバック
            if (!locale.equals(Locale.ENGLISH)) {
                currentBundle = ResourceBundle.getBundle(BUNDLE_BASE_NAME, Locale.ENGLISH);
            } else {
                throw new RuntimeException("Failed to load default menu bundle", e);
            }
        }
    }

    /**
     * キーに対応するローカライズ済み文字列を取得
     * キーが見つからない場合はキー自体を返す（開発時のデバッグ用）
     * 
     * @param key リソースキー（例: "menu.file.open"）
     * @return ローカライズ済み文字列
     */
    public static String getString(String key) {
        if (!initialized) {
            initialize();
        }
        try {
            return currentBundle.getString(key);
        } catch (MissingResourceException e) {
            LOGGER.warning("Menu resource key not found: " + key);
            return key; // キー自体を返す（未翻訳箇所の特定用）
        }
    }

    /**
     * キーに対応する文字列を取得（デフォルト値指定可能）
     * 
     * @param key リソースキー
     * @param defaultValue キーが見つからない場合のデフォルト値
     * @return ローカライズ済み文字列またはデフォルト値
     */
    public static String getString(String key, String defaultValue) {
        if (!initialized) {
            initialize();
        }
        try {
            return currentBundle.getString(key);
        } catch (MissingResourceException e) {
            return defaultValue;
        }
    }

    /**
     * 現在のロケールを取得
     */
    public static Locale getCurrentLocale() {
        return currentLocale;
    }

    /**
     * ロケールを切り替え（実行時言語切り替え用）
     */
    public static void setLocale(Locale newLocale) {
        if (newLocale != null && !newLocale.equals(currentLocale)) {
            currentLocale = newLocale;
            loadBundle(newLocale);
            LOGGER.info("Menu locale changed to: " + newLocale);
        }
    }

    /**
     * 日本語モードかどうか判定
     */
    public static boolean isJapanese() {
        return currentLocale != null && "ja".equals(currentLocale.getLanguage());
    }

    /**
     * メニュー項目作成ヘルパー: JMenuItem
     */
    public static javax.swing.JMenuItem createMenuItem(String key) {
        return new javax.swing.JMenuItem(getString(key));
    }

    /**
     * メニュー項目作成ヘルパー: JMenuItem (アクションリスナー付き)
     */
    public static javax.swing.JMenuItem createMenuItem(String key, java.awt.event.ActionListener listener) {
        javax.swing.JMenuItem item = new javax.swing.JMenuItem(getString(key));
        if (listener != null) {
            item.addActionListener(listener);
        }
        return item;
    }

    /**
     * メニュー作成ヘルパー: JMenu
     */
    public static javax.swing.JMenu createMenu(String key) {
        return new javax.swing.JMenu(getString(key));
    }

    /**
     * チェックボックスメニュー項目作成ヘルパー
     */
    public static javax.swing.JCheckBoxMenuItem createCheckBoxMenuItem(String key) {
        return new javax.swing.JCheckBoxMenuItem(getString(key));
    }

    /**
     * ラジオボタンメニュー項目作成ヘルパー
     */
    public static javax.swing.JRadioButtonMenuItem createRadioButtonMenuItem(String key) {
        return new javax.swing.JRadioButtonMenuItem(getString(key));
    }

    /**
     * セパレーター作成ヘルパー
     */
    public static javax.swing.JSeparator createSeparator() {
        return new javax.swing.JSeparator();
    }
}
