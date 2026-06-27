package com.oculus.jj.utils;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.ResourceBundle;

/**
 * LanguageManager - Handles localization for the application.
 * Supports both internal resources and external "Langage" folders (HOROS-20240407 style).
 */
public class LanguageManager {
    private static LanguageManager instance;
    private Properties properties;
    private String currentLocale = "en"; // Default to English

    private static final String DEFAULT_INTERNAL_PATH = "/i18n/";
    // Path where an external Langage folder might be located (e.g., in the execution directory)
    private static final String EXTERNAL_FOLDER_NAME = "Langage"; 

    public static LanguageManager getInstance() {
        if (instance == null) {
            instance = new LanguageManager();
        }
        return instance;
    }

    private LanguageManager() {
        loadLanguage(currentLocale);
    }

    /**
     * Loads the language resource bundle.
     * Prioritizes external "Langage" folder based on ConfigManager settings, otherwise uses internal resources.
     */
    public void loadLanguage(String locale) {
        this.currentLocale = locale;
        try {
            File langDir = null;
            // Fetch the language folder name from ConfigManager (default: "Langage")
            String externalFolder = ConfigManager.getInstance().getString("langage.folder", "Langage");

            if (!externalFolder.isEmpty()) {
                File potentialExternalDir = new File(System.getProperty("user.dir"), externalFolder);
                if (potentialExternalDir.exists() && potentialExternalDir.isDirectory()) {
                    langDir = potentialExternalDir;
                    System.out.println("[LanguageManager] Using external folder: " + langDir.getAbsolutePath());
                }
            }

            properties = new Properties();
            if (langDir != null) {
                // Load from external directory
                String fileName = "messages_" + locale + ".properties";
                File file = new File(langDir, fileName);
                if (file.exists()) {
                    try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
                        properties.load(bis);
                        System.out.println("[LanguageManager] Loaded external properties: " + file.getAbsolutePath());
                    } catch (IOException e) {
                        System.err.println("[LanguageManager] Error loading external property file: " + e.getMessage());
                    }
                } else {
                    System.err.println("[LanguageManager] External property file not found: " + file.getAbsolutePath());
                }
            }

            if (properties == null || properties.isEmpty()) {
                // Fallback to internal resources if external failed or doesn't exist
                ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages_" + locale);
                properties = new Properties();
                for (String key : bundle.keySet()) {
                    properties.setProperty(key, bundle.getString(key));
                }
                System.out.println("[LanguageManager] Loaded internal properties for: " + locale);
            }
        } catch (Exception e) {
            System.err.println("[LanguageManager] Failed to load language " + locale + ", falling back to English.");
            try {
                ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages_en");
                properties = new Properties();
                for (String key : bundle.keySet()) {
                    properties.setProperty(key, bundle.getString(key));
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public String getString(String key) {
        if (properties == null) return key;
        return properties.getProperty(key, key);
    }

    /**
     * Get a localized string with fallback default value.
     */
    public String getString(String key, String defaultValue) {
        if (properties == null) return defaultValue;
        return properties.getProperty(key, defaultValue);
    }

    // Convenience method for common usage
    public static String get(String key) {
        return getInstance().getString(key);
    }

    /**
     * Get a localized string with fallback default value.
     */
    public static String get(String key, String defaultValue) {
        return getInstance().getString(key, defaultValue);
    }
}