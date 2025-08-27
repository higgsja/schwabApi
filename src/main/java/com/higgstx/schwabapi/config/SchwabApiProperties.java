package com.higgstx.schwabapi.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Instance-based configuration properties loaded from application.yml
 */
public class SchwabApiProperties {
    
    private static final Logger logger = LoggerFactory.getLogger(SchwabApiProperties.class);
    
    private final String authUrl;
    private final String tokenUrl;
    private final String marketDataUrl;
    private final String defaultRedirectUri;
    private final String defaultScope;
    private final int httpTimeoutMs;
    
    /**
     * Default constructor - loads from application.yml in classpath
     */
    public SchwabApiProperties() {
        Map<String, String> properties = loadFromApplicationYml();
        
        this.authUrl = validateRequired(properties.get("auth"), "schwab.api.urls.auth");
        this.tokenUrl = validateRequired(properties.get("token"), "schwab.api.urls.token");
        this.marketDataUrl = validateRequired(properties.get("marketData"), "schwab.api.urls.marketData");
        this.defaultRedirectUri = validateRequired(properties.get("redirectUri"), "schwab.api.defaults.redirectUri");
        this.defaultScope = validateRequired(properties.get("scope"), "schwab.api.defaults.scope");
        
        this.httpTimeoutMs = Integer.parseInt(validateRequired(properties.get("httpTimeoutMs"), "schwab.api.defaults.httpTimeoutMs"));
        
        String timeoutStr = properties.get("httpTimeoutMs");
//        if (timeoutStr == null) {
//            throw new RuntimeException("Required property missing: schwab.api.defaults.httpTimeoutMs");
//        }
//        this.httpTimeoutMs = Integer.parseInt(timeoutStr);
        
        logger.info("SchwabApiProperties loaded successfully");
    }
    
    /**
     * Constructor with explicit values (for testing)
     */
    public SchwabApiProperties(String authUrl, String tokenUrl, String marketDataUrl, 
                             String redirectUri, String scope, int timeoutMs) {
        this.authUrl = validateRequired(authUrl, "authUrl");
        this.tokenUrl = validateRequired(tokenUrl, "tokenUrl");
        this.marketDataUrl = validateRequired(marketDataUrl, "marketDataUrl");
        this.defaultRedirectUri = validateRequired(redirectUri, "redirectUri");
        this.defaultScope = validateRequired(scope, "scope");
        this.httpTimeoutMs = timeoutMs;
    }
    
    private String validateRequired(String value, String propertyName) {
        if (value == null || value.trim().isEmpty()) {
            throw new RuntimeException("Required property missing or empty: " + propertyName);
        }
        return value.trim();
    }
    
    private static Map<String, String> loadFromApplicationYml() {
        try (InputStream inputStream = SchwabApiProperties.class.getClassLoader()
                .getResourceAsStream("application.yml")) {
            
            if (inputStream == null) {
                throw new RuntimeException("application.yml not found in classpath");
            }
            
            return parseSimpleYaml(inputStream);
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to load application.yml: " + e.getMessage(), e);
        }
    }
    
    private static Map<String, String> parseSimpleYaml(InputStream inputStream) throws IOException {
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
    
    // Getters
    public String getAuthUrl() { return authUrl; }
    public String getTokenUrl() { return tokenUrl; }
    public String getMarketDataUrl() { return marketDataUrl; }
    public String getDefaultRedirectUri() { return defaultRedirectUri; }
    public String getDefaultScope() { return defaultScope; }
    public int getHttpTimeoutMs() { return httpTimeoutMs; }
    
    public void showLoadedProperties() {
        System.out.println("=== Schwab API Properties ===");
        System.out.println("Auth URL: " + authUrl);
        System.out.println("Token URL: " + tokenUrl);
        System.out.println("Market Data URL: " + marketDataUrl);
        System.out.println("Default Redirect URI: " + defaultRedirectUri);
        System.out.println("Default Scope: " + defaultScope);
        System.out.println("HTTP Timeout: " + httpTimeoutMs + "ms");
    }
}