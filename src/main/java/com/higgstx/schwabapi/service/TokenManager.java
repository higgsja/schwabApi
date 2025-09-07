package com.higgstx.schwabapi.service;

import com.higgstx.schwabapi.config.*;
import com.higgstx.schwabapi.exception.SchwabApiException;
import com.higgstx.schwabapi.model.TokenResponse;
import com.higgstx.schwabapi.util.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Simplified token manager - works with simplified TokenResponse
 */
@Slf4j
@Getter
public class TokenManager {

    private static final long TOKEN_REFRESH_BUFFER_SECONDS = 300; // 5 minutes
    
    private final String tokenFile;
    private final String clientId;
    private final String clientSecret;
    private final ReentrantLock refreshLock = new ReentrantLock();
    private volatile boolean refreshInProgress = false;

    public TokenManager(String tokenFile, String clientId, String clientSecret) throws SchwabApiException {
        this.tokenFile = tokenFile;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        
        if (StringUtils.isBlank(this.clientId) || StringUtils.isBlank(this.clientSecret)) {
            throw SchwabApiException.configurationError("Client ID and Secret cannot be null or empty");
        }
        
        log.info("TokenManager configured with tokenFile: {}", tokenFile);
    }

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

    private TokenResponse loadTokensFromFile() {
        try {
            if (!FileUtils.isReadableFile(tokenFile)) {
                log.debug("Token file not found: {}", tokenFile);
                return null;
            }

            TokenResponse tokens = FileUtils.loadJson(tokenFile, UtilityClass.getObjectMapper(), TokenResponse.class);
            
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

    public TokenResponse loadTokens() {
        return loadTokensFromFile();
    }

    public TokenResponse loadTokens(boolean autoRefresh) throws SchwabApiException {
        TokenResponse tokens = loadTokensFromFile();
        
        if (autoRefresh && tokens != null && needsRefreshForTokens(tokens)) {
            tokens = refreshTokens(tokens.getRefreshToken());
            saveTokens(tokens);
        }
        
        return tokens;
    }

    public void saveTokens(TokenResponse tokens) throws SchwabApiException {
        UtilityClass.validateNotNull(tokens, "Tokens");

        try {
            FileUtils.saveJsonWithBackup(tokens, tokenFile, UtilityClass.getObjectMapper());
            log.info("Tokens saved to file: {}", tokenFile);
        } catch (Exception e) {
            log.error("Failed to save tokens: {}", e.getMessage());
            throw SchwabApiException.serverError("Failed to save tokens: " + e.getMessage());
        }
    }

    public TokenResponse refreshTokens(String refreshToken) throws SchwabApiException {
        UtilityClass.validateParameter(refreshToken, "Refresh token");

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
                newTokens.setRefreshTokenExpiresAt(Instant.now().plusSeconds(7 * 24 * 60 * 60));
            }

            newTokens.setSource(TokenResponse.TokenSource.REFRESH_TOKEN);
            newTokens.setIssuedAt(Instant.now());

            log.info("Tokens refreshed successfully");
            return newTokens;
            
        } catch (SchwabApiException e) {
            throw e;
        } catch (Exception e) {
            throw SchwabApiException.serverError("Failed to refresh tokens: " + e.getMessage());
        }
    }

    @Async
    public CompletableFuture<TokenResponse> refreshTokensAsync() throws SchwabApiException {
        return CompletableFuture.supplyAsync(() -> {
            try {
                refreshLock.lock();
                try {
                    if (refreshInProgress) {
                        log.debug("Refresh already in progress, skipping");
                        return loadTokensFromFile();
                    }
                    
                    refreshInProgress = true;
                    log.info("Starting asynchronous token refresh");
                    
                    TokenResponse tokens = loadTokensFromFile();
                    if (tokens == null || !tokens.isRefreshTokenValid()) {
                        throw new RuntimeException("No valid refresh token available");
                    }
                    
                    TokenResponse refreshed = refreshTokens(tokens.getRefreshToken());
                    saveTokens(refreshed);
                    
                    log.info("Asynchronous token refresh completed successfully");
                    return refreshed;
                    
                } finally {
                    refreshInProgress = false;
                    refreshLock.unlock();
                }
            } catch (Exception e) {
                log.error("Asynchronous token refresh failed: {}", e.getMessage());
                throw new RuntimeException("Failed to refresh tokens asynchronously", e);
            }
        });
    }

    public boolean needsRefreshSoon(long bufferSeconds) {
        try {
            TokenResponse tokens = loadTokensFromFile();
            return tokens != null && 
                   tokens.isRefreshTokenValid() && 
                   tokens.willAccessTokenExpireSoon(bufferSeconds);
        } catch (Exception e) {
            log.debug("Error checking if tokens need refresh soon: {}", e.getMessage());
            return false;
        }
    }

    public boolean hasTokens() {
        return FileUtils.isReadableFile(tokenFile);
    }

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
        return needsRefreshSoon(TOKEN_REFRESH_BUFFER_SECONDS);
    }

    private boolean needsRefreshForTokens(TokenResponse tokens) {
        return tokens.isRefreshTokenValid() && 
               tokens.willAccessTokenExpireSoon(TOKEN_REFRESH_BUFFER_SECONDS);
    }

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

    @Async
    public CompletableFuture<TokenResponse> forceTokenRefreshAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return forceTokenRefresh();
            } catch (SchwabApiException e) {
                log.error("Async force refresh failed: {}", e.getMessage());
                throw new RuntimeException("Failed to force refresh tokens", e);
            }
        });
    }

    public long getSecondsUntilExpiration() {
        try {
            TokenResponse tokens = loadTokensFromFile();
            return tokens != null ? tokens.getSecondsUntilAccessExpiry() : -1;
        } catch (Exception e) {
            log.debug("Error getting expiration time: {}", e.getMessage());
            return -1;
        }
    }

    public long getSecondsUntilRefreshExpiration() {
        try {
            TokenResponse tokens = loadTokensFromFile();
            return tokens != null ? tokens.getSecondsUntilRefreshExpiry() : -1;
        } catch (Exception e) {
            log.debug("Error getting refresh expiration time: {}", e.getMessage());
            return -1;
        }
    }

    // Simplified status display
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

            // Use simplified status display since getDisplayInfo() was removed
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
            
            if (tokens.getRefreshTokenExpiresAt() != null) {
                System.out.println("Refresh Token Expires: " + tokens.getRefreshTokenExpiresAt());
                long refreshSecondsLeft = tokens.getSecondsUntilRefreshExpiry();
                if (refreshSecondsLeft > 0) {
                    System.out.println("Refresh Time Remaining: " + formatDuration(refreshSecondsLeft));
                }
            }
            
            System.out.println("Can refresh: " + (StringUtils.hasContent(clientId) && StringUtils.hasContent(clientSecret)));
            System.out.println("Needs refresh soon: " + needsRefresh());
            System.out.println("Refresh in progress: " + refreshInProgress);
            
        } catch (Exception e) {
            System.out.println("Error checking token status: " + e.getMessage());
        }
    }

    // Simple duration formatting (since it was removed from TokenResponse)
    private String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + " seconds";
        } else if (seconds < 3600) {
            return (seconds / 60) + " minutes";
        } else if (seconds < 86400) {
            return (seconds / 3600) + " hours";
        } else {
            return (seconds / 86400) + " days";
        }
    }

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

    public String getTokenFilePath() {
        return FileUtils.getAbsolutePath(tokenFile);
    }

    public void showTokenFilePaths() {
        System.out.println("Token file: " + getTokenFilePath());
    }

    public void clearTokenFiles() {
        clearTokens();
    }

    public long getRefreshBufferSeconds() {
        return TOKEN_REFRESH_BUFFER_SECONDS;
    }

    public boolean needsRefreshWithCustomBuffer(long customBufferSeconds) {
        return needsRefreshSoon(customBufferSeconds);
    }
}