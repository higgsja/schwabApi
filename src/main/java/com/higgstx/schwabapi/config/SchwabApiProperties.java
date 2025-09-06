package com.higgstx.schwabapi.config;

import com.higgstx.schwabapi.util.StringUtils;
import com.higgstx.schwabapi.util.YamlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Instance-based configuration properties loaded from application.yml
 * Refactored to use utility package for common operations
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
        Map<String, String> properties = YamlUtils.loadFromClasspath("application.yml");
        
        this.authUrl = StringUtils.validateRequired(properties.get("auth"), "schwab.api.urls.auth");
        this.tokenUrl = StringUtils.validateRequired(properties.get("token"), "schwab.api.urls.token");
        this.marketDataUrl = StringUtils.validateRequired(properties.get("marketData"), "schwab.api.urls.marketData");
        this.defaultRedirectUri = StringUtils.validateRequired(properties.get("redirectUri"), "schwab.api.defaults.redirectUri");
        this.defaultScope = StringUtils.validateRequired(properties.get("scope"), "schwab.api.defaults.scope");
        
        String timeoutStr = StringUtils.validateRequired(properties.get("httpTimeoutMs"), "schwab.api.defaults.httpTimeoutMs");
        this.httpTimeoutMs = Integer.parseInt(timeoutStr);
        
        logger.info("SchwabApiProperties loaded successfully");
    }
    
    /**
     * Constructor with explicit values (for testing)
     */
    public SchwabApiProperties(String authUrl, String tokenUrl, String marketDataUrl, 
                             String redirectUri, String scope, int timeoutMs) {
        this.authUrl = StringUtils.validateRequired(authUrl, "authUrl");
        this.tokenUrl = StringUtils.validateRequired(tokenUrl, "tokenUrl");
        this.marketDataUrl = StringUtils.validateRequired(marketDataUrl, "marketDataUrl");
        this.defaultRedirectUri = StringUtils.validateRequired(redirectUri, "redirectUri");
        this.defaultScope = StringUtils.validateRequired(scope, "scope");
        this.httpTimeoutMs = timeoutMs;
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