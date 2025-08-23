package com.higgstx.schwab.config;

import lombok.Data;

/**
 * Configuration for the Schwab API client - standalone version without Spring dependencies.
 */
@Data
public class SchwabConfig {

    // File constants
    public static final String TOKEN_PROPERTIES_FILE = "schwab_tokens.json";
    public static final String REFRESH_TOKEN_FILE = "schwab_refresh_token.txt";

    // API endpoint constants
    public static final String AUTH_URL = "https://api.schwabapi.com/v1/oauth/authorize";
    public static final String TOKEN_URL = "https://api.schwabapi.com/v1/oauth/token";
    public static final String MARKET_DATA_URL = "https://api.schwabapi.com/marketdata/v1";
    
    // HTTP configuration
    public static final long HTTP_TIMEOUT_MS = 30000; // 30 seconds
    
    // IMPORTANT: Schwab requires EXACTLY this URI - no port number allowed
    public static final String REDIRECT_URI = "https://127.0.0.1";

    // Instance fields (can be set manually or loaded from properties)
    private String appKey;
    private String appSecret;
    private String callbackUrl;

    // Static accessors for OAuth client
    public static String CLIENT_ID;
    public static String CLIENT_SECRET;

    /**
     * Default constructor
     */
    public SchwabConfig() {
        // Default constructor
    }

    /**
     * Constructor with credentials
     */
    public SchwabConfig(String appKey, String appSecret) {
        this.appKey = appKey;
        this.appSecret = appSecret;
        setStaticCredentials(appKey, appSecret);
    }

    /**
     * Initialize static fields from instance fields
     */
    public void init() {
        setStaticCredentials(this.appKey, this.appSecret);
    }

    /**
     * Set static credentials directly
     */
    public static void setStaticCredentials(String clientId, String clientSecret) {
        CLIENT_ID = clientId;
        CLIENT_SECRET = clientSecret;
    }

    /**
     * Load configuration from system properties or environment variables
     */
    public static SchwabConfig loadFromEnvironment() {
        String clientId = System.getProperty("schwab.client.id", System.getenv("SCHWAB_CLIENT_ID"));
        String clientSecret = System.getProperty("schwab.client.secret", System.getenv("SCHWAB_CLIENT_SECRET"));
        String callbackUrl = System.getProperty("schwab.callback.url", System.getenv("SCHWAB_CALLBACK_URL"));
        
        if (clientId == null || clientSecret == null) {
            throw new IllegalStateException("Schwab client credentials must be provided via system properties or environment variables");
        }
        
        SchwabConfig config = new SchwabConfig(clientId, clientSecret);
        config.setCallbackUrl(callbackUrl != null ? callbackUrl : REDIRECT_URI);
        return config;
    }

    public boolean isConfigSet() {
        return appKey != null && !appKey.isEmpty() && appSecret != null && !appSecret.isEmpty();
    }

    /**
     * Load configuration (for non-Spring usage)
     */
    public static void loadConfig() {
        System.out.println("📋 Configuration loading...");
        if (CLIENT_ID == null || CLIENT_SECRET == null) {
            System.out.println("⚠️  Warning: Configuration not loaded. Use setStaticCredentials() or loadFromEnvironment()");
        }
    }

    /**
     * Show current configuration info
     */
    public static void showConfigInfo() {
        System.out.println("\n--- Schwab API Configuration ---");
        System.out.println("Auth URL: " + AUTH_URL);
        System.out.println("Token URL: " + TOKEN_URL);
        System.out.println("Market Data URL: " + MARKET_DATA_URL);
        System.out.println("Redirect URI: " + REDIRECT_URI + " (EXACT - no port allowed)");
        System.out.println("HTTP Timeout: " + HTTP_TIMEOUT_MS + "ms");
        System.out.println("Client ID: " + (CLIENT_ID != null && !CLIENT_ID.isEmpty() ? "✅ Set (" + CLIENT_ID.substring(0, Math.min(8, CLIENT_ID.length())) + "...)" : "❌ Not set"));
        System.out.println("Client Secret: " + (CLIENT_SECRET != null && !CLIENT_SECRET.isEmpty() ? "✅ Set" : "❌ Not set"));
        System.out.println("Token File: " + TOKEN_PROPERTIES_FILE);
        System.out.println("Refresh Token File: " + REFRESH_TOKEN_FILE);
        
        System.out.println("\n💡 OAuth Flow Notes:");
        System.out.println("• Schwab requires redirect URI to be EXACTLY: " + REDIRECT_URI);
        System.out.println("• No port numbers are allowed in the redirect URI");
        System.out.println("• Browser will show 'site can't be reached' - this is normal");
        System.out.println("• Manual authorization code extraction is required");
    }

    /**
     * Validate that configuration is properly set
     */
    public static void validateConfig() {
        if (CLIENT_ID == null || CLIENT_ID.isEmpty()) {
            throw new IllegalStateException("CLIENT_ID is not set. Call setStaticCredentials() or loadFromEnvironment() first.");
        }
        if (CLIENT_SECRET == null || CLIENT_SECRET.isEmpty()) {
            throw new IllegalStateException("CLIENT_SECRET is not set. Call setStaticCredentials() or loadFromEnvironment() first.");
        }
    }
}