package com.higgstx.schwabapi.config;

import com.higgstx.schwabapi.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration properties - now purely a data holder for explicit configuration
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
     * Constructor with explicit values (Spring will provide these)
     */
    public SchwabApiProperties(String authUrl, String tokenUrl, String marketDataUrl, 
                             String redirectUri, String scope, int timeoutMs) {
        this.authUrl = StringUtils.validateRequired(authUrl, "authUrl");
        this.tokenUrl = StringUtils.validateRequired(tokenUrl, "tokenUrl");
        this.marketDataUrl = StringUtils.validateRequired(marketDataUrl, "marketDataUrl");
        this.defaultRedirectUri = StringUtils.validateRequired(redirectUri, "redirectUri");
        this.defaultScope = StringUtils.validateRequired(scope, "scope");
        this.httpTimeoutMs = timeoutMs;
        
        logger.info("SchwabApiProperties configured via Spring injection");
    }
    
    // Getters only - remove all YAML loading logic
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