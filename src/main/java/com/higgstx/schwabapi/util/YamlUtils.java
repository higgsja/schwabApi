package com.higgstx.schwabapi.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * YAML parsing utility functions extracted from SchwabApiProperties and TokenManager
 */
public final class YamlUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(YamlUtils.class);
    
    private YamlUtils() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Load YAML from classpath and parse into a Map
     * @param resourceName The name of the YAML resource
     * @return Map of parsed YAML properties
     * @throws RuntimeException if the file cannot be loaded or parsed
     */
    public static Map<String, String> loadFromClasspath(String resourceName) {
        try (InputStream inputStream = YamlUtils.class.getClassLoader()
                .getResourceAsStream(resourceName)) {
            
            if (inputStream == null) {
                throw new RuntimeException(resourceName + " not found in classpath");
            }
            
            return parseSimpleYaml(inputStream);
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + resourceName + ": " + e.getMessage(), e);
        }
    }
    
    /**
     * Parse simple YAML from an InputStream into a flat Map
     * This is a simplified YAML parser that handles the specific structure needed for the API configuration
     * @param inputStream The InputStream containing YAML content
     * @return Map of parsed properties
     * @throws IOException if reading fails
     */
    public static Map<String, String> parseSimpleYaml(InputStream inputStream) throws IOException {
        String content = new String(inputStream.readAllBytes());
        Map<String, String> properties = new HashMap<>();
        
        String[] lines = content.split("\n");
        boolean inSchwabApiUrls = false;
        boolean inSchwabApiDefaults = false;
        
        for (String line : lines) {
            String trimmed = line.trim();
            
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            
            if (trimmed.equals("urls:")) {
                inSchwabApiUrls = true;
                inSchwabApiDefaults = false;
                continue;
            } else if (trimmed.equals("defaults:")) {
                inSchwabApiUrls = false;
                inSchwabApiDefaults = true;
                continue;
            } else if (!line.startsWith(" ") && line.contains(":")) {
                inSchwabApiUrls = false;
                inSchwabApiDefaults = false;
                continue;
            }
            
            if ((inSchwabApiUrls || inSchwabApiDefaults) && line.startsWith("      ") && line.contains(":")) {
                String[] parts = trimmed.split(":", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    
                    properties.put(key, value);
                }
            }
        }
        
        return properties;
    }
    
    /**
     * Extract a specific value from YAML content by key
     * @param yamlContent The raw YAML content as string
     * @param key The key to extract
     * @return The value for the key, or null if not found
     */
    public static String extractYmlValue(String yamlContent, String key) {
        try {
            String[] lines = yamlContent.split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith(key + ":")) {
                    String value = trimmed.substring(key.length() + 1).trim();
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    return value;
                }
            }
        } catch (Exception e) {
            logger.debug("Error extracting value for key '{}': {}", key, e.getMessage());
        }
        return null;
    }
    
    /**
     * Load credential from YAML file (first from filesystem, then from classpath)
     * @param key The credential key to extract
     * @param yamlFileName The YAML file name (e.g., "application.yml")
     * @return The credential value, or null if not found
     */
    public static String loadCredentialFromYml(String key, String yamlFileName) {
        try {
            // Try from filesystem first
            Path ymlPath = Paths.get("src/main/resources/" + yamlFileName);
            if (Files.exists(ymlPath)) {
                String content = Files.readString(ymlPath);
                return extractYmlValue(content, key);
            } else {
                // Try from classpath
                try (var is = YamlUtils.class.getClassLoader().getResourceAsStream(yamlFileName)) {
                    if (is != null) {
                        String content = new String(is.readAllBytes());
                        return extractYmlValue(content, key);
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Error loading {} from YAML: {}", key, e.getMessage());
        }
        return null;
    }
    
    /**
     * Load credential from default application.yml
     * @param key The credential key to extract
     * @return The credential value, or null if not found
     */
    public static String loadCredentialFromYml(String key) {
        return loadCredentialFromYml(key, "application.yml");
    }
    
    /**
     * Check if a YAML file exists in the classpath
     * @param resourceName The resource name
     * @return true if the resource exists
     */
    public static boolean resourceExists(String resourceName) {
        try (InputStream is = YamlUtils.class.getClassLoader().getResourceAsStream(resourceName)) {
            return is != null;
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * Parse nested YAML properties with dot notation
     * For example: "schwab.api.urls.auth" -> Map access via nested keys
     * @param yamlContent The YAML content
     * @param nestedKey The nested key in dot notation
     * @return The value, or null if not found
     */
    public static String extractNestedValue(String yamlContent, String nestedKey) {
        String[] keyParts = nestedKey.split("\\.");
        String[] lines = yamlContent.split("\n");
        
        int currentDepth = 0;
        boolean[] inSection = new boolean[keyParts.length];
        
        for (String line : lines) {
            String trimmed = line.trim();
            
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            
            // Calculate indentation depth
            int indentDepth = (line.length() - StringUtils.lTrim(line).length()) / 2;
            
            if (line.contains(":")) {
                String[] parts = trimmed.split(":", 2);
                if (parts.length >= 1) {
                    String currentKey = parts[0].trim();
                    
                    // Check if this matches our current key part
                    if (currentDepth < keyParts.length && currentKey.equals(keyParts[currentDepth])) {
                        inSection[currentDepth] = true;
                        
                        // If this is the final key and has a value
                        if (currentDepth == keyParts.length - 1 && parts.length == 2) {
                            String value = parts[1].trim();
                            if (value.startsWith("\"") && value.endsWith("\"")) {
                                value = value.substring(1, value.length() - 1);
                            }
                            return value;
                        }
                        
                        currentDepth++;
                    } else {
                        // Reset if we're at the wrong level
                        if (indentDepth <= currentDepth * 2) {
                            currentDepth = 0;
                            for (int i = 0; i < inSection.length; i++) {
                                inSection[i] = false;
                            }
                        }
                    }
                }
            }
        }
        
        return null;
    }
}