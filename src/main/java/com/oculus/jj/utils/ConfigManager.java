package com.oculus.jj.utils;

import java.io.*;
import java.util.*;
import java.nio.file.*;

/**
 * ConfigManager - Handles application configuration from an external properties file.
 * Supports default values and reloading at runtime.
 */
public class ConfigManager {
    private static ConfigManager instance;
    private Properties props = new Properties();
    private String configPath;

    // Default configuration path (relative to execution directory)
    private static final String DEFAULT_CONFIG_PATH = "config.properties";

    public static ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    private ConfigManager() {
        loadConfig();
    }

    /**
     * Loads the configuration from a file. 
     * If no config file exists, it uses default values.
     */
    public void loadConfig() {
        File configFile = new File(DEFAULT_CONFIG_PATH);
        if (configFile.exists()) {
            try (InputStream input = new FileInputStream(configFile)) {
                props.load(input);
                System.out.println("[ConfigManager] Loaded configuration from " + configFile.getAbsolutePath());
            } catch (IOException ex) {
                System.err.println("[ConfigManager] Error loading config file, using defaults.");
            }
        } else {
            createDefaultConfig();
            System.out.println("[ConfigManager] Created default configuration file at " + DEFAULT_CONFIG_PATH);
        }
    }

    /**
     * Creates a basic default configuration file if it doesn't exist.
     */
    private void createDefaultConfig() {
        Properties defaultProps = new Properties();
        // Default settings
        defaultProps.setProperty("langage.folder", "Langage");
        defaultProps.setProperty("app.title", "OculusJJ");
        defaultProps.setProperty("ui.theme", "light");

        try (OutputStream output = new FileOutputStream(DEFAULT_CONFIG_PATH)) {
            defaultProps.store(output, "Default Configuration");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets a configuration value or returns the default if not found.
     */
    public String getString(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        String val = props.getProperty(key);
        if (val != null) {
            try {
                return Integer.parseInt(val);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * Saves the current properties to the config file.
     */
    public void saveConfig() {
        try (OutputStream output = new FileOutputStream(DEFAULT_CONFIG_PATH)) {
            props.store(output, "Saved Configuration");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}