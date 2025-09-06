package com.higgstx.schwabapi.service;

import com.higgstx.schwabapi.config.SchwabOAuthClient;
import com.higgstx.schwabapi.exception.SchwabApiException;
import com.higgstx.schwabapi.model.TokenResponse;
import com.higgstx.schwabapi.util.*;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Token Manager with automatic refresh capability using stored credentials
 * Refactored to use utility package for common operations
 */
@Getter
@Setter
public class TokenManager {

    private static final Logger logger = LoggerFactory.getLogger(TokenManager.class);

    // Configuration
    private static final long TOKEN_REFRESH_BUFFER_SECONDS = 300; // 5 minutes
    private static final long CACHE_DURATION_SECONDS = 30; // Cache for 30 seconds
    private static final int MAX_BACKUP_FILES = 5;

    // Thread-safe cached token instance
    private static final AtomicReference<CachedTokens> cachedTokensRef = new AtomicReference<>();
    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // Configuration instance
    private final String tokenPropertiesFile;
    private final String refreshTokenFile;
    private final String clientId;
    private final String clientSecret;

    /**
     * Default constructor - loads credentials from application.yml
     */
    public TokenManager() {
        this("schwab-api.json", "schwab-refresh-token.txt");
    }

    /**
     * Constructor with custom file names - loads credentials from application.yml
     */
    public TokenManager(String tokenPropertiesFile, String refreshTokenFile) {
        this.tokenPropertiesFile = StringUtils.hasContent(tokenPropertiesFile) ? tokenPropertiesFile : "schwab-api.json";
        this.refreshTokenFile = StringUtils.hasContent(refreshTokenFile) ? refreshTokenFile : "schwab-refresh-token.txt";

        // Load credentials from yml using utility functions
        this.clientId = YamlUtils.loadCredentialFromYml("appKey");
        this.clientSecret = YamlUtils.loadCredentialFromYml("appSecret");
    }

    /**
     * Constructor with explicit credentials
     */
    public TokenManager(String tokenPropertiesFile, String refreshTokenFile, String clientId, String clientSecret) {
        this.tokenPropertiesFile = tokenPropertiesFile;
        this.refreshTokenFile = refreshTokenFile;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    // Static methods for backward compatibility
    public static String getValidAccessToken() throws SchwabApiException {
        TokenManager defaultManager = new TokenManager();
        return defaultManager.getValidAccessTokenInstance();
    }

    public static TokenResponse loadTokens(boolean autoRefresh) {
        TokenManager defaultManager = new TokenManager();
        return defaultManager.loadTokensInstance(autoRefresh);
    }

    public static boolean hasValidTokens() {
        TokenManager defaultManager = new TokenManager();
        return defaultManager.hasValidTokensInstance();
    }

    public static boolean hasUsableTokens() {
        TokenManager defaultManager = new TokenManager();
        return defaultManager.hasUsableTokensInstance();
    }

    public static TokenResponse forceTokenRefresh() throws SchwabApiException {
        TokenManager defaultManager = new TokenManager();
        return defaultManager.forceTokenRefreshInstance();
    }

    public static void saveTokens(TokenResponse tokens) throws SchwabApiException {
        TokenManager defaultManager = new TokenManager();
        defaultManager.saveTokensInstance(tokens);
    }

    public static void showTokenFilePaths() {
        TokenManager defaultManager = new TokenManager();
        defaultManager.showTokenFilePathsInstance();
    }

    public static void clearTokenFiles() {
        TokenManager defaultManager = new TokenManager();
        defaultManager.clearTokenFilesInstance();
    }

    // Instance methods
    public String getValidAccessTokenInstance() throws SchwabApiException {
        TokenResponse tokens = loadTokensInstance(true);
        if (tokens != null && tokens.isAccessTokenValid()) {
            return tokens.getAccessToken();
        }
        throw new SchwabApiException(401,
                "Unable to get a valid access token. Re-authorization may be required.",
                "TOKEN_UNAVAILABLE", null, (Throwable) null);
    }

    public TokenResponse loadTokensInstance(boolean autoRefresh) {
        UtilityClass.logMethodEntry("TokenManager", "loadTokensInstance", autoRefresh);

        lock.readLock().lock();
        try {
            CachedTokens cached = cachedTokensRef.get();
            if (cached != null && !cached.isExpired()) {
                TokenResponse cachedTokens = cached.getTokens();
                // For cron jobs, always check if refresh is needed even with valid cache
                if (cachedTokens != null && cachedTokens.isAccessTokenValid() && !autoRefresh) {
                    logger.debug("Using cached tokens");
                    return cachedTokens;
                }
                logger.debug("Cache available but checking for refresh need");
            }
        } finally {
            lock.readLock().unlock();
        }

        lock.writeLock().lock();
        try {
            // Always load fresh tokens for auto-refresh scenarios
            TokenResponse tokens = loadTokensFromFile();
            if (tokens == null) {
                logger.info("No tokens found. Manual authorization required.");
                return null;
            }

            logger.info("Loaded tokens from file. Access valid: {}, Refresh valid: {}",
                    tokens.isAccessTokenValid(), tokens.isRefreshTokenValid());

            if (autoRefresh && canRefresh()) {
                tokens = handleTokenRefresh(tokens);
            }

            // Update cache with final tokens
            cachedTokensRef.set(new CachedTokens(tokens, 
                    Instant.now().plusSeconds(CACHE_DURATION_SECONDS)));
            return tokens;

        } finally {
            lock.writeLock().unlock();
        }
    }

    private boolean canRefresh() {
        boolean canRefresh = StringUtils.hasContent(clientId) && StringUtils.hasContent(clientSecret);
        logger.debug("Can refresh tokens: {} (clientId: {}, clientSecret: {})",
                canRefresh, clientId != null, clientSecret != null);
        return canRefresh;
    }

    private TokenResponse handleTokenRefresh(TokenResponse tokens) {
        // Always attempt refresh if access token is expired or expiring soon
        boolean needsRefresh = !tokens.isAccessTokenValid() || 
                tokens.willAccessTokenExpireSoon(TOKEN_REFRESH_BUFFER_SECONDS);

        logger.info("Token refresh check - Access valid: {}, Expires soon: {}, Refresh valid: {}",
                tokens.isAccessTokenValid(),
                tokens.willAccessTokenExpireSoon(TOKEN_REFRESH_BUFFER_SECONDS),
                tokens.isRefreshTokenValid());

        if (!needsRefresh) {
            logger.info("Access token is still valid, no refresh needed");
            return tokens; // No refresh needed
        }

        if (!tokens.isRefreshTokenValid()) {
            logger.error("Refresh token is expired. Manual re-authorization required.");
            return tokens;
        }

        try {
            logger.info("Attempting token refresh...");

            try (SchwabOAuthClient client = new SchwabOAuthClient()) {
                TokenResponse newTokens = client.refreshTokens(clientId, clientSecret, tokens.getRefreshToken());

                logger.info("Raw refresh response - Access token: {}, Refresh token: {}, Expires in: {}, Refresh expires in: {}",
                        newTokens.getAccessToken() != null ? "present" : "null",
                        newTokens.getRefreshToken() != null ? "present" : "null",
                        newTokens.getExpiresIn(),
                        newTokens.getRefreshTokenExpiresIn());

                // Check if we got a new refresh token vs the same one
                boolean isNewRefreshToken = !UtilityClass.safeEquals(
                        newTokens.getRefreshToken(), tokens.getRefreshToken());
                logger.info("Refresh token comparison - Same token: {}", !isNewRefreshToken);

                if (newTokens.getRefreshTokenExpiresAt() == null) {
                    if (isNewRefreshToken) {
                        logger.info("New refresh token issued without expiration, setting 7 day default");
                        newTokens.setRefreshTokenExpiresAt(Instant.now().plusSeconds(7 * 24 * 60 * 60));
                    } else {
                        logger.info("Same refresh token returned, preserving original expiration");
                        newTokens.setRefreshTokenExpiresAt(tokens.getRefreshTokenExpiresAt());
                    }
                } else {
                    logger.info("Refresh token expiration provided in response: {}", 
                            newTokens.getRefreshTokenExpiresAt());
                }

                // Save new tokens to JSON file (this will also update cache)
                saveTokensInstance(newTokens);

                // Also update the separate refresh token file
                try {
                    FileUtils.writeString(refreshTokenFile, newTokens.getRefreshToken());
                    logger.debug("Updated refresh token file");
                } catch (Exception ioException) {
                    logger.error("Failed to update refresh token file: {}", ioException.getMessage());
                }

                logger.info("Token refresh completed successfully. New access expires: {}, Refresh expires: {}",
                        newTokens.getExpiresAt(), newTokens.getRefreshTokenExpiresAt());
                return newTokens;
            }
        } catch (Exception e) {
            logger.error("Failed to refresh access token: {}", e.getMessage(), e);
            return tokens; // Return original tokens if refresh fails
        }
    }

    private TokenResponse loadTokensFromFile() {
        try {
            if (!FileUtils.isReadableFile(tokenPropertiesFile)) {
                logger.debug("Token file not found or not readable: {}", 
                        FileUtils.getAbsolutePath(tokenPropertiesFile));
                return null;
            }

            TokenResponse tokens = FileUtils.loadJson(tokenPropertiesFile, 
                    UtilityClass.getObjectMapper(), TokenResponse.class);

            if (tokens != null && StringUtils.isBlank(tokens.getAccessToken())) {
                logger.warn("Loaded tokens contain empty access token");
            }

            return tokens;
        } catch (Exception e) {
            logger.error("Error loading tokens from file: {}", e.getMessage());
            return null;
        }
    }

    public TokenResponse forceTokenRefreshInstance() throws SchwabApiException {
        UtilityClass.logMethodEntry("TokenManager", "forceTokenRefreshInstance");

        if (!canRefresh()) {
            throw new SchwabApiException(500,
                    "Token refresh requires credentials - ensure appKey and appSecret are configured",
                    "REFRESH_NOT_AVAILABLE", null, (Throwable) null);
        }

        // Clear cache to force fresh load
        clearCache();

        TokenResponse tokens = loadTokensFromFile();
        if (tokens == null) {
            throw new SchwabApiException(404, "No tokens found to refresh",
                    "NO_TOKENS", null, (Throwable) null);
        }

        if (!tokens.isRefreshTokenValid()) {
            throw new SchwabApiException(401,
                    "Refresh token is expired - re-authorization required",
                    "REFRESH_TOKEN_EXPIRED", null, (Throwable) null);
        }

        // Force refresh regardless of access token validity
        logger.info("Force refresh requested - bypassing validity checks");

        try (SchwabOAuthClient client = new SchwabOAuthClient()) {
            TokenResponse newTokens = client.refreshTokens(clientId, clientSecret, tokens.getRefreshToken());

            // Handle refresh token expiration
            boolean isNewRefreshToken = !UtilityClass.safeEquals(
                    newTokens.getRefreshToken(), tokens.getRefreshToken());

            if (newTokens.getRefreshTokenExpiresAt() == null) {
                if (isNewRefreshToken) {
                    newTokens.setRefreshTokenExpiresAt(Instant.now().plusSeconds(7 * 24 * 60 * 60));
                } else {
                    newTokens.setRefreshTokenExpiresAt(tokens.getRefreshTokenExpiresAt());
                }
            }

            // Save and cache new tokens
            saveTokensInstance(newTokens);

            logger.info("Force refresh completed. New access expires: {}, Refresh expires: {}",
                    newTokens.getExpiresAt(), newTokens.getRefreshTokenExpiresAt());

            return newTokens;
        } catch (Exception e) {
            logger.error("Force refresh failed: {}", e.getMessage(), e);
            throw new SchwabApiException(500, "Force refresh failed: " + e.getMessage(),
                    "FORCE_REFRESH_ERROR", null, e);
        }
    }

    public void saveTokensInstance(TokenResponse tokens) throws SchwabApiException {
        UtilityClass.validateNotNull(tokens, "Tokens");

        lock.writeLock().lock();
        try {
            FileUtils.saveJsonWithBackup(tokens, tokenPropertiesFile, UtilityClass.getObjectMapper());
            logger.info("Tokens saved to file: {}", tokenPropertiesFile);
            
            FileUtils.cleanupOldBackups(tokenPropertiesFile, MAX_BACKUP_FILES);

            // Update cache with the saved tokens
            cachedTokensRef.set(new CachedTokens(tokens, 
                    Instant.now().plusSeconds(CACHE_DURATION_SECONDS)));
        } catch (Exception e) {
            logger.error("Failed to save tokens: {}", e.getMessage());
            throw new SchwabApiException(500, "Failed to save tokens: " + e.getMessage(), 
                    "SAVE_ERROR", null, e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Checks if a refresh token file exists.
     */
    public boolean hasRefreshToken() {
        return FileUtils.isReadableFile(refreshTokenFile);
    }

    public boolean hasTokensInstance() {
        return FileUtils.isReadableFile(tokenPropertiesFile);
    }

    public boolean hasValidTokensInstance() {
        try {
            TokenResponse tokens = loadTokensInstance(false);
            return tokens != null && tokens.isAccessTokenValid();
        } catch (Exception e) {
            logger.debug("Error checking token validity: {}", e.getMessage());
            return false;
        }
    }

    public boolean hasUsableTokensInstance() {
        try {
            TokenResponse tokens = loadTokensInstance(false);
            return tokens != null && tokens.isRefreshTokenValid();
        } catch (Exception e) {
            logger.debug("Error checking token usability: {}", e.getMessage());
            return false;
        }
    }

    public void showTokenFilePathsInstance() {
        System.out.println("Token File Paths:");
        System.out.println("  JSON Tokens: " + FileUtils.getAbsolutePath(tokenPropertiesFile));
        System.out.println("  Refresh Token: " + FileUtils.getAbsolutePath(refreshTokenFile));
    }

    public void showTokenStatus() {
        System.out.println("Token Status Report:");
        System.out.println("=".repeat(70));

        try {
            // Clear cache and force fresh load to show current status
            clearCache();
            TokenResponse tokens = loadTokensInstance(false);

            if (tokens == null) {
                System.out.println("No tokens found");
                System.out.println("Run OAuth authorization to obtain tokens");
                System.out.println("\nFile locations checked:");
                System.out.println("  • " + FileUtils.getAbsolutePath(tokenPropertiesFile));
                return;
            }

            System.out.println(tokens.getDisplayInfo());
            System.out.println("Refresh capability: " + (canRefresh() ? "Available" : "Not available (missing credentials)"));
        } catch (Exception e) {
            System.out.println("Error checking token status: " + e.getMessage());
            logger.error("Token status check failed", e);
        }
    }

    public void clearTokenFilesInstance() {
        lock.writeLock().lock();
        try {
            System.out.println("Clearing token files...");
            int deletedCount = 0;

            if (FileUtils.safeDelete(tokenPropertiesFile)) {
                System.out.println("    Deleted: " + tokenPropertiesFile);
                deletedCount++;
            }

            if (FileUtils.safeDelete(refreshTokenFile)) {
                System.out.println("    Deleted: " + refreshTokenFile);
                deletedCount++;
            }

            cachedTokensRef.set(null);
            System.out.println("Summary: " + deletedCount + " files deleted, cache cleared");
        } catch (Exception e) {
            System.err.println("Error during file cleanup: " + e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clearCache() {
        lock.writeLock().lock();
        try {
            cachedTokensRef.set(null);
            logger.debug("Token cache cleared");
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Helper classes
    private static class CachedTokens {
        private final TokenResponse tokens;
        private final Instant expirationTime;

        public CachedTokens(TokenResponse tokens, Instant expirationTime) {
            this.tokens = tokens;
            this.expirationTime = expirationTime;
        }

        public TokenResponse getTokens() {
            return tokens;
        }

        public boolean isExpired() {
            return Instant.now().isAfter(expirationTime);
        }
    }
}