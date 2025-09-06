package com.higgstx.schwabapi.service;

import com.higgstx.schwabapi.config.*;
import com.higgstx.schwabapi.exception.SchwabApiException;
import com.higgstx.schwabapi.model.TokenResponse;
import com.higgstx.schwabapi.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Instance-based token manager - Spring-managed configuration only
 */
public class TokenManager {

    private static final Logger logger = LoggerFactory.getLogger(TokenManager.class);
    private static final long TOKEN_REFRESH_BUFFER_SECONDS = 300; // 5 minutes
    
    private final String tokenFile;
    private final String clientId;
    private final String clientSecret;
    private final ReentrantLock refreshLock = new ReentrantLock();
    private volatile boolean refreshInProgress = false;

    /**
     * Create TokenManager with explicit credentials (Spring injection)
     */
    public TokenManager(String tokenFile, String clientId, String clientSecret) throws SchwabApiException {
        this.tokenFile = tokenFile;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        
        if (StringUtils.isBlank(this.clientId) || StringUtils.isBlank(this.clientSecret)) {
            throw SchwabApiException.configurationError("Client ID and Secret cannot be null or empty");
        }
        
        logger.info("TokenManager configured with tokenFile: {}", tokenFile);
    }

    /**
     * Get a valid access token, refreshing if necessary
     */
    public String getValidAccessToken() throws SchwabApiException {
        TokenResponse tokens = loadTokensFromFile();
        
        if (tokens == null) {
            throw SchwabApiException.tokenError("No tokens found. Authorization required.");
        }
        
        if (tokens.isAccessTokenValid()) {
            return tokens.getAccessToken();
        }
        
        if (tokens.isRefreshTokenValid()) {
            tokens = refreshTokens(tokens.getRefreshToken());
            saveTokens(tokens);
            return tokens.getAccessToken();
        }
        
        throw SchwabApiException.tokenError("All tokens expired. Re-authorization required.");
    }

    /**
     * Load tokens from file (core method)
     */
    private TokenResponse loadTokensFromFile() {
        try {
            if (!FileUtils.isReadableFile(tokenFile)) {
                logger.debug("Token file not found: {}", tokenFile);
                return null;
            }

            TokenResponse tokens = FileUtils.loadJson(tokenFile, UtilityClass.getObjectMapper(), TokenResponse.class);
            
            if (tokens != null && StringUtils.isBlank(tokens.getAccessToken())) {
                logger.warn("Loaded tokens contain empty access token");
                return null;
            }

            return tokens;
        } catch (Exception e) {
            logger.error("Error loading tokens from file: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Load tokens without auto-refresh
     */
    public TokenResponse loadTokens() {
        return loadTokensFromFile();
    }

    /**
     * Load tokens with optional auto-refresh
     */
    public TokenResponse loadTokens(boolean autoRefresh) throws SchwabApiException {
        TokenResponse tokens = loadTokensFromFile();
        
        if (autoRefresh && tokens != null && needsRefreshForTokens(tokens)) {
            tokens = refreshTokens(tokens.getRefreshToken());
            saveTokens(tokens);
        }
        
        return tokens;
    }

    /**
     * Save tokens to file
     */
    public void saveTokens(TokenResponse tokens) throws SchwabApiException {
        UtilityClass.validateNotNull(tokens, "Tokens");

        try {
            FileUtils.saveJsonWithBackup(tokens, tokenFile, UtilityClass.getObjectMapper());
            logger.info("Tokens saved to file: {}", tokenFile);
        } catch (Exception e) {
            logger.error("Failed to save tokens: {}", e.getMessage());
            throw SchwabApiException.serverError("Failed to save tokens: " + e.getMessage());
        }
    }

    /**
     * Refresh tokens using refresh token
     */
    public TokenResponse refreshTokens(String refreshToken) throws SchwabApiException {
        UtilityClass.validateParameter(refreshToken, "Refresh token");

        // Create ApiProperties for the SchwabOAuthClient
        SchwabApiProperties apiProperties = new SchwabApiProperties(
            "https://api.schwabapi.com/v1/oauth/authorize",
            "https://api.schwabapi.com/v1/oauth/token", 
            "https://api.schwabapi.com/marketdata/v1",
            "https://127.0.0.1:8182",
            "readonly",
            30000
        );

        try (SchwabOAuthClient client = new SchwabOAuthClient(apiProperties)) {
            TokenResponse newTokens = client.refreshTokens(clientId, clientSecret, refreshToken);

            // Set expiration times
            if (newTokens.getExpiresIn() > 0) {
                newTokens.setExpiresAt(Instant.now().plusSeconds(newTokens.getExpiresIn()));
            }
            
            if (newTokens.getRefreshTokenExpiresIn() > 0) {
                newTokens.setRefreshTokenExpiresAt(Instant.now().plusSeconds(newTokens.getRefreshTokenExpiresIn()));
            } else {
                // Default 7 days for refresh token if not specified
                newTokens.setRefreshTokenExpiresAt(Instant.now().plusSeconds(7 * 24 * 60 * 60));
            }

            newTokens.setSource(TokenResponse.TokenSource.REFRESH_TOKEN);
            newTokens.setIssuedAt(Instant.now());

            logger.info("Tokens refreshed successfully");
            return newTokens;
            
        } catch (SchwabApiException e) {
            throw e;
        } catch (Exception e) {
            throw SchwabApiException.serverError("Failed to refresh tokens: " + e.getMessage());
        }
    }

    /**
     * Asynchronous token refresh
     */
    @Async
    public CompletableFuture<TokenResponse> refreshTokensAsync() throws SchwabApiException {
        return CompletableFuture.supplyAsync(() -> {
            try {
                refreshLock.lock();
                try {
                    if (refreshInProgress) {
                        logger.debug("Refresh already in progress, skipping");
                        return loadTokensFromFile();
                    }
                    
                    refreshInProgress = true;
                    logger.info("Starting asynchronous token refresh");
                    
                    TokenResponse tokens = loadTokensFromFile();
                    if (tokens == null || !tokens.isRefreshTokenValid()) {
                        throw new RuntimeException("No valid refresh token available");
                    }
                    
                    TokenResponse refreshed = refreshTokens(tokens.getRefreshToken());
                    saveTokens(refreshed);
                    
                    logger.info("Asynchronous token refresh completed successfully");
                    return refreshed;
                    
                } finally {
                    refreshInProgress = false;
                    refreshLock.unlock();
                }
            } catch (Exception e) {
                logger.error("Asynchronous token refresh failed: {}", e.getMessage());
                throw new RuntimeException("Failed to refresh tokens asynchronously", e);
            }
        });
    }

    /**
     * Check if tokens need refresh soon with custom buffer
     */
    public boolean needsRefreshSoon(long bufferSeconds) {
        try {
            TokenResponse tokens = loadTokensFromFile();
            return tokens != null && 
                   tokens.isRefreshTokenValid() && 
                   tokens.willAccessTokenExpireSoon(bufferSeconds);
        } catch (Exception e) {
            logger.debug("Error checking if tokens need refresh soon: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if we have any tokens
     */
    public boolean hasTokens() {
        return FileUtils.isReadableFile(tokenFile);
    }

    /**
     * Check if we have valid access tokens
     */
    public boolean hasValidTokens() {
        try {
            TokenResponse tokens = loadTokensFromFile();
            return tokens != null && tokens.isAccessTokenValid();
        } catch (Exception e) {
            logger.debug("Error checking token validity: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if we have usable tokens (valid refresh token)
     */
    public boolean hasUsableTokens() {
        try {
            TokenResponse tokens = loadTokensFromFile();
            return tokens != null && tokens.isRefreshTokenValid();
        } catch (Exception e) {
            logger.debug("Error checking token usability: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if tokens need refresh soon (using default buffer)
     */
    public boolean needsRefresh() {
        return needsRefreshSoon(TOKEN_REFRESH_BUFFER_SECONDS);
    }

    /**
     * Helper method to check if specific tokens need refresh
     */
    private boolean needsRefreshForTokens(TokenResponse tokens) {
        return tokens.isRefreshTokenValid() && 
               tokens.willAccessTokenExpireSoon(TOKEN_REFRESH_BUFFER_SECONDS);
    }

    /**
     * Force token refresh
     */
    public TokenResponse forceTokenRefresh() throws SchwabApiException {
        refreshLock.lock();
        try {
            TokenResponse tokens = loadTokensFromFile();
            
            if (tokens == null || !tokens.isRefreshTokenValid()) {
                throw SchwabApiException.tokenError("No valid refresh token available");
            }
            
            TokenResponse refreshed = refreshTokens(tokens.getRefreshToken());
            saveTokens(refreshed);
            return refreshed;
        } finally {
            refreshLock.unlock();
        }
    }

    /**
     * Force token refresh asynchronously
     */
    @Async
    public CompletableFuture<TokenResponse> forceTokenRefreshAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return forceTokenRefresh();
            } catch (SchwabApiException e) {
                logger.error("Async force refresh failed: {}", e.getMessage());
                throw new RuntimeException("Failed to force refresh tokens", e);
            }
        });
    }

    /**
     * Get time until access token expiration in seconds
     */
    public long getSecondsUntilExpiration() {
        try {
            TokenResponse tokens = loadTokensFromFile();
            return tokens != null ? tokens.getSecondsUntilAccessExpiry() : -1;
        } catch (Exception e) {
            logger.debug("Error getting expiration time: {}", e.getMessage());
            return -1;
        }
    }

    /**
     * Get time until refresh token expiration in seconds
     */
    public long getSecondsUntilRefreshExpiration() {
        try {
            TokenResponse tokens = loadTokensFromFile();
            return tokens != null ? tokens.getSecondsUntilRefreshExpiry() : -1;
        } catch (Exception e) {
            logger.debug("Error getting refresh expiration time: {}", e.getMessage());
            return -1;
        }
    }

    /**
     * Check if refresh is currently in progress
     */
    public boolean isRefreshInProgress() {
        return refreshInProgress;
    }

    /**
     * Show current token status
     */
    public void showTokenStatus() {
        System.out.println("Token Status Report:");
        System.out.println("=".repeat(50));

        try {
            TokenResponse tokens = loadTokensFromFile();

            if (tokens == null) {
                System.out.println("No tokens found");
                System.out.println("File: " + FileUtils.getAbsolutePath(tokenFile));
                return;
            }

            System.out.println(tokens.getDisplayInfo());
            System.out.println("Can refresh: " + (StringUtils.hasContent(clientId) && StringUtils.hasContent(clientSecret)));
            System.out.println("Needs refresh soon: " + needsRefresh());
            System.out.println("Refresh in progress: " + refreshInProgress);
            
        } catch (Exception e) {
            System.out.println("Error checking token status: " + e.getMessage());
        }
    }

    /**
     * Clear token files
     */
    public void clearTokens() {
        try {
            if (FileUtils.safeDelete(tokenFile)) {
                System.out.println("Deleted: " + tokenFile);
            } else {
                System.out.println("No token file to delete");
            }
        } catch (Exception e) {
            System.err.println("Error deleting token file: " + e.getMessage());
        }
    }

    /**
     * Get token file path
     */
    public String getTokenFilePath() {
        return FileUtils.getAbsolutePath(tokenFile);
    }

    /**
     * Show token file paths
     */
    public void showTokenFilePaths() {
        System.out.println("Token file: " + getTokenFilePath());
    }

    /**
     * Clear token files (alias for clearTokens for compatibility)
     */
    public void clearTokenFiles() {
        clearTokens();
    }

    /**
     * Get refresh buffer time in seconds
     */
    public long getRefreshBufferSeconds() {
        return TOKEN_REFRESH_BUFFER_SECONDS;
    }

    /**
     * Set custom refresh buffer (for testing or special cases)
     */
    public boolean needsRefreshWithCustomBuffer(long customBufferSeconds) {
        return needsRefreshSoon(customBufferSeconds);
    }
}