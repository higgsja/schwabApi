package com.higgstx.schwabapi.config;

import com.higgstx.schwabapi.util.StringUtils;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * Minimal configuration properties for the Schwab API library
 * Pure data holder - Spring handles all the configuration binding
 */
@Value
@Slf4j
public class SchwabApiProperties {
    
    String authUrl;
    String tokenUrl;
    String marketDataUrl;
    String defaultRedirectUri;
    String defaultScope;
    int httpTimeoutMs;
    
    /**
     * Constructor with validation
     */
    public SchwabApiProperties(String authUrl, String tokenUrl, String marketDataUrl, 
                             String redirectUri, String scope, int timeoutMs) {
        this.authUrl = StringUtils.validateRequired(authUrl, "Auth URL");
        this.tokenUrl = StringUtils.validateRequired(tokenUrl, "Token URL");
        this.marketDataUrl = StringUtils.validateRequired(marketDataUrl, "Market Data URL");
        this.defaultRedirectUri = StringUtils.validateRequired(redirectUri, "Redirect URI");
        this.defaultScope = StringUtils.validateRequired(scope, "Scope");
        this.httpTimeoutMs = timeoutMs;
        
        log.debug("SchwabApiProperties initialized: authUrl={}, tokenUrl={}, marketDataUrl={}", 
                 authUrl, tokenUrl, marketDataUrl);
    }
}