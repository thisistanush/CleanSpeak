package com.tanush.cleanspeech.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Manages configuration settings for CleanSpeech.
 * Reads API key from either environment variable or config.properties file.
 */
public class ConfigManager {

    // Environment variable name for API key
    private static final String API_KEY_ENV = "GEMINI_API_KEY";

    // Config file name
    private static final String CONFIG_FILE = "config.properties";

    // Cached API key
    private static String cachedApiKey = null;

    /**
     * Gets the Gemini API key from environment variable or config file.
     * 
     * Priority:
     * 1. Environment variable GEMINI_API_KEY
     * 2. config.properties file in project root
     * 
     * @return The API key, or null if not found
     */
    public static String getApiKey() {
        if (cachedApiKey != null) {
            return cachedApiKey;
        }

        // First, try environment variable
        String envKey = System.getenv(API_KEY_ENV);
        if (envKey != null && !envKey.isBlank() && !envKey.contains("paste-your-key")) {
            cachedApiKey = envKey;
            return cachedApiKey;
        }

        // Second, try config.properties file
        String fileKey = loadFromConfigFile();
        if (fileKey != null && !fileKey.isBlank() && !fileKey.contains("paste-your-key")) {
            cachedApiKey = fileKey;
            return cachedApiKey;
        }

        return null;
    }

    /**
     * Checks if the API key is available.
     */
    public static boolean isApiKeyAvailable() {
        String key = getApiKey();
        return key != null && !key.isBlank();
    }

    /**
     * Loads API key from config.properties file.
     */
    private static String loadFromConfigFile() {
        // Try current directory first
        Path configPath = Paths.get(CONFIG_FILE);
        if (!Files.exists(configPath)) {
            // Try project root (look for pom.xml)
            configPath = findConfigFile();
        }

        if (configPath == null || !Files.exists(configPath)) {
            return null;
        }

        try (InputStream input = Files.newInputStream(configPath)) {
            Properties props = new Properties();
            props.load(input);
            return props.getProperty(API_KEY_ENV);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Finds the config file by looking for it relative to pom.xml.
     */
    private static Path findConfigFile() {
        Path currentDir = Paths.get("").toAbsolutePath();

        // Check current directory
        Path configPath = currentDir.resolve(CONFIG_FILE);
        if (Files.exists(configPath)) {
            return configPath;
        }

        // Check parent directories
        Path parent = currentDir.getParent();
        while (parent != null) {
            configPath = parent.resolve(CONFIG_FILE);
            if (Files.exists(configPath)) {
                return configPath;
            }
            parent = parent.getParent();
        }

        return null;
    }
}
