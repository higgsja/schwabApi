package com.higgstx.schwabapi.service;

import com.higgstx.schwabapi.config.*;
import com.higgstx.schwabapi.exception.SchwabApiException;
import com.higgstx.schwabapi.model.TokenResponse;
import com.higgstx.schwabapi.util.*;
import java.io.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Simplified token manager - handles token lifecycle with automatic refresh
 * Now uses SimpleJsonParser instead of Jackson
 */
@Slf4j
@Getter
public class TokenManager {

    private static final long TOKEN_REFRESH_BUFFER_SECONDS = 300; // 5 minutes
    
    private final String tokenFile;
    private final String clientId;
    private final String clientSecret;
    private final ReentrantLock refreshLock = new ReentrantLock();

    public TokenManager(String tokenFile, String clientId, String clientSecret) throws SchwabApiException {
        this.tokenFile = StringUtils.validateRequired(tokenFile, "Token file path");
        this.clientId = StringUtils.validateRequired(clientId, "Client ID");
        this.clientSecret = StringUtils.validateRequired(clientSecret, "Client Secret");
        
        log.info("TokenManager configured with tokenFile: {}", tokenFile);
    }

    /**
     * Get a valid access token, refreshing if necessary
     */
    public String getValidAccessToken() throws SchwabApiException {
        TokenResponse tokens = loadTokens();
        
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
     * Load tokens from file
     */
    public TokenResponse loadTokens() throws SchwabApiException {
        return loadTokens(false);
    }

    /**
     * Load tokens with optional auto-refresh
     */
    public TokenResponse loadTokens(boolean autoRefresh) throws SchwabApiException {
        TokenResponse tokens = loadTokensFromFile();
        
        if (autoRefresh && tokens != null && needsRefresh(tokens)) {
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
            // Convert TokenResponse to Map for JSON serialization
            Map<String, Object> tokenMap = tokens.toMap();
            FileUtils.saveJsonWithBackup(tokenMap, tokenFile);
            log.info("Tokens saved to file: {}", tokenFile);
        } catch (IOException e) {
            throw SchwabApiException.serverError("Failed to save tokens: " + e.getMessage());
        }
    }

    /**
     * Refresh tokens using refresh token
     */
    public TokenResponse refreshTokens(String refreshToken) throws SchwabApiException {
        UtilityClass.validateParameter(refreshToken, "Refresh token");

        refreshLock.lock();
        try {
            SchwabApiProperties apiProperties = createApiProperties();

            try (SchwabOAuthClient client = new SchwabOAuthClient(apiProperties)) {
                // Get the raw API response
                String clientBasicAuth = HttpUtils.createBasicAuthHeader(clientId, clientSecret);
                
                // Build the request manually to get JSON response
                okhttp3.RequestBody formBody = new okhttp3.FormBody.Builder()
                        .add("grant_type", "refresh_token")
                        .add("refresh_token", refreshToken)
                        .build();

                okhttp3.Request request = new okhttp3.Request.Builder()
                        .url(apiProperties.getTokenUrl())
                        .header("Authorization", "Basic " + clientBasicAuth)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .post(formBody)
                        .build();

                okhttp3.OkHttpClient httpClient = HttpUtils.buildHttpClient(apiProperties.getHttpTimeoutMs(), false);
                
                try (okhttp3.Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw SchwabApiException.serverError("Token refresh failed with status: " + response.code());
                    }
                    
                    String responseBody = response.body() != null ? response.body().string() : "";
                    Map<String, Object> tokenData = SimpleJsonParser.parseToMap(responseBody);
                    
                    TokenResponse newTokens = TokenResponse.fromMap(tokenData);
                    if (newTokens == null) {
                        throw SchwabApiException.serverError("Failed to parse token response");
                    }
                    
                    setTokenExpirationTimes(newTokens);
                    newTokens.setSource(TokenResponse.TokenSource.REFRESH_TOKEN);
                    newTokens.setIssuedAt(Instant.now());

                    log.info("Tokens refreshed successfully");
                    return newTokens;
                }
            }
        } catch (Exception e) {
            throw SchwabApiException.serverError("Token refresh failed: " + e.getMessage());
        } finally {
            refreshLock.unlock();
        }
    }

    /**
     * Force token refresh (for manual refresh operations)
     */
    public TokenResponse forceTokenRefresh() throws SchwabApiException {
        TokenResponse tokens = loadTokensFromFile();
        
        if (tokens == null || !tokens.isRefreshTokenValid()) {
            throw SchwabApiException.tokenError("No valid refresh token available");
        }
        
        TokenResponse refreshed = refreshTokens(tokens.getRefreshToken());
        saveTokens(refreshed);
        return refreshed;
    }

    // Status check methods
    public boolean hasValidTokens() {
        try {
            TokenResponse tokens = loadTokensFromFile();
            return tokens != null && tokens.isAccessTokenValid();
        } catch (Exception e) {
            log.debug("Error checking token validity: {}", e.getMessage());
            return false;
        }
    }

    public boolean hasUsableTokens() {
        try {
            TokenResponse tokens = loadTokensFromFile();
            return tokens != null && tokens.isRefreshTokenValid();
        } catch (Exception e) {
            log.debug("Error checking token usability: {}", e.getMessage());
            return false;
        }
    }

    public boolean needsRefresh() {
        try {
            TokenResponse tokens = loadTokensFromFile();
            return tokens != null && needsRefresh(tokens);
        } catch (Exception e) {
            log.debug("Error checking if tokens need refresh: {}", e.getMessage());
            return false;
        }
    }

    // Utility methods
    public void clearTokens() {
        try {
            if (FileUtils.safeDelete(tokenFile)) {
                log.info("Deleted token file: {}", tokenFile);
            }
        } catch (Exception e) {
            log.warn("Error deleting token file: {}", e.getMessage());
        }
    }

    public String getTokenFilePath() {
        return FileUtils.getAbsolutePath(tokenFile);
    }

    public void showTokenStatus() {
        System.out.println("Token Status Report:");
        System.out.println("=".repeat(50));

        try {
            TokenResponse tokens = loadTokensFromFile();

            if (tokens == null) {
                System.out.println("No tokens found");
                System.out.println("File: " + getTokenFilePath());
                return;
            }

            System.out.println("Status: " + tokens.getQuickStatus());
            System.out.println("Access Token Valid: " + tokens.isAccessTokenValid());
            System.out.println("Refresh Token Valid: " + tokens.isRefreshTokenValid());
            
            if (tokens.getExpiresAt() != null) {
                System.out.println("Access Token Expires: " + tokens.getExpiresAt());
                long secondsLeft = tokens.getSecondsUntilAccessExpiry();
                if (secondsLeft > 0) {
                    System.out.println("Time Remaining: " + formatDuration(secondsLeft));
                }
            }
            
            System.out.println("Needs refresh soon: " + needsRefresh());
            
        } catch (Exception e) {
            System.out.println("Error checking token status: " + e.getMessage());
        }
    }

    // Private helper methods
    private TokenResponse loadTokensFromFile() {
        try {
            if (!FileUtils.isReadableFile(tokenFile)) {
                log.debug("Token file not found: {}", tokenFile);
                return null;
            }

            String content = FileUtils.readString(tokenFile);
            if (StringUtils.isBlank(content)) {
                log.warn("Token file is empty: {}", tokenFile);
                return null;
            }

            Map<String, Object> tokenData = SimpleJsonParser.parseToMap(content);
            TokenResponse tokens = TokenResponse.fromMap(tokenData);
            
            if (tokens != null && StringUtils.isBlank(tokens.getAccessToken())) {
                log.warn("Loaded tokens contain empty access token");
                return null;
            }

            return tokens;
        } catch (Exception e) {
            log.error("Error loading tokens from file: {}", e.getMessage());
            return null;
        }
    }

    private boolean needsRefresh(TokenResponse tokens) {
        return tokens.isRefreshTokenValid() && 
               tokens.willAccessTokenExpireSoon(TOKEN_REFRESH_BUFFER_SECONDS);
    }

    private SchwabApiProperties createApiProperties() {
        return new SchwabApiProperties(
            "https://api.schwabapi.com/v1/oauth/authorize",
            "https://api.schwabapi.com/v1/oauth/token", 
            "https://api.schwabapi.com/marketdata/v1",
            "https://127.0.0.1:8182",
            "readonly",
            30000
        );
    }

    private void setTokenExpirationTimes(TokenResponse tokens) {
        if (tokens.getExpiresIn() > 0) {
            tokens.setExpiresAt(Instant.now().plusSeconds(tokens.getExpiresIn()));
        }
        
        if (tokens.getRefreshTokenExpiresIn() > 0) {
            tokens.setRefreshTokenExpiresAt(Instant.now().plusSeconds(tokens.getRefreshTokenExpiresIn()));
        } else {
            tokens.setRefreshTokenExpiresAt(Instant.now().plusSeconds(7 * 24 * 60 * 60));
        }
    }

    private String formatDuration(long seconds) {
        if (seconds < 60) return seconds + " seconds";
        if (seconds < 3600) return (seconds / 60) + " minutes";
        if (seconds < 86400) return (seconds / 3600) + " hours";
        return (seconds / 86400) + " days";
    }
}