package com.higgstx.schwabapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.higgstx.schwabapi.config.SchwabOAuthClient;
import com.higgstx.schwabapi.exception.SchwabApiException;
import com.higgstx.schwabapi.model.TokenResponse;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

/**
 * Token Manager with automatic refresh capability using stored credentials
 */
@Getter
@Setter
public class TokenManager {
    private static final Logger logger = LoggerFactory.getLogger(
            TokenManager.class);
    private static final ObjectMapper objectMapper = createObjectMapper();

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
        this.tokenPropertiesFile = tokenPropertiesFile != null ? tokenPropertiesFile : "schwab-api.json";
        this.refreshTokenFile = refreshTokenFile != null ? refreshTokenFile : "schwab-refresh-token.txt";
        
        // This is the key change: load credentials from yml
        this.clientId = loadCredentialFromYml("appKey");
        this.clientSecret = loadCredentialFromYml("appSecret");
    }

    /**
     * Constructor with explicit credentials
     */
    public TokenManager(String tokenPropertiesFile, String refreshTokenFile,
            String clientId, String clientSecret) {
        this.tokenPropertiesFile = tokenPropertiesFile;
        this.refreshTokenFile = refreshTokenFile;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    private String loadCredentialFromYml(String key) {
        try {
            Path ymlPath = Paths.get("src/main/resources/application.yml");
            if (Files.exists(ymlPath)) {
                String content = Files.readString(ymlPath);
                return extractYmlValue(content, key);
            } else {
                // Try from classpath
                try (var is = TokenManager.class.getClassLoader().getResourceAsStream("application.yml")) {
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

    private String extractYmlValue(String yamlContent, String key) {
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
            // Ignore parsing errors
        }
        return null;
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

    public static void saveTokens(TokenResponse tokens) throws
            SchwabApiException {
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
        lock.readLock().lock();
        try {
            CachedTokens cached = cachedTokensRef.get();
            if (cached != null && !cached.isExpired()) {
                logger.debug("Using cached tokens");
                return cached.getTokens();
            }
        } finally {
            lock.readLock().unlock();
        }

        lock.writeLock().lock();
        try {
            CachedTokens cached = cachedTokensRef.get();
            if (cached != null && !cached.isExpired()) {
                return cached.getTokens();
            }

            TokenResponse tokens = loadTokensFromFile();
            if (tokens == null) {
                logger.info("No tokens found. Manual authorization required.");
                return null;
            }

            logger.info("Loaded tokens from file. Status: {}", tokens.getQuickStatus());

            if (autoRefresh && canRefresh()) {
                tokens = handleTokenRefresh(tokens);
            }

            cachedTokensRef.set(new CachedTokens(tokens, Instant.now().plusSeconds(CACHE_DURATION_SECONDS)));
            return tokens;

        } finally {
            lock.writeLock().unlock();
        }
    }

    private boolean canRefresh() {
        return clientId != null && clientSecret != null;
    }

    private TokenResponse handleTokenRefresh(TokenResponse tokens) {
        if (tokens.isAccessTokenValid() && !tokens.willAccessTokenExpireSoon(TOKEN_REFRESH_BUFFER_SECONDS)) {
            return tokens; // No refresh needed
        }

        if (!tokens.isRefreshTokenValid()) {
            logger.error("Both access and refresh tokens are expired. Re-authorization required.");
            return tokens;
        }

        try {
            logger.info("Refreshing access token...");

            try (SchwabOAuthClient client = new SchwabOAuthClient()) {
                TokenResponse newTokens = client.refreshTokens(clientId, clientSecret, tokens.getRefreshToken());

                // Save new tokens
                saveTokensInstance(newTokens);
                logger.info("Access token refreshed successfully");
                return newTokens;
            }
        } catch (Exception e) {
            logger.error("Failed to refresh access token: {}", e.getMessage());
            return tokens; // Return original tokens if refresh fails
        }
    }

    private TokenResponse loadTokensFromFile() {
        try {
            File tokenFile = new File(tokenPropertiesFile);
            if (!tokenFile.exists()) {
                logger.debug("Token file not found: {}", tokenFile.getAbsolutePath());
                return null;
            }

            if (!tokenFile.canRead()) {
                logger.error("Cannot read token file: {}", tokenFile.getAbsolutePath());
                return null;
            }

            TokenResponse tokens = objectMapper.readValue(tokenFile, TokenResponse.class);

            if (tokens.getAccessToken() == null || tokens.getAccessToken().trim().isEmpty()) {
                logger.warn("Loaded tokens contain empty access token");
            }

            return tokens;
        } catch (IOException e) {
            logger.error("Error loading tokens from file: {}", e.getMessage());
            return null;
        }
    }

    public TokenResponse forceTokenRefreshInstance() throws SchwabApiException {
        if (!canRefresh()) {
            throw new SchwabApiException(500, "Token refresh requires credentials - ensure appKey and appSecret are configured", "REFRESH_NOT_AVAILABLE", null, (Throwable) null);
        }

        TokenResponse tokens = loadTokensFromFile();
        if (tokens == null) {
            throw new SchwabApiException(404, "No tokens found to refresh", "NO_TOKENS", null, (Throwable) null);
        }

        if (!tokens.isRefreshTokenValid()) {
            throw new SchwabApiException(401, "Refresh token is expired - re-authorization required", "REFRESH_TOKEN_EXPIRED", null, (Throwable) null);
        }

        return handleTokenRefresh(tokens);
    }

    public void saveTokensInstance(TokenResponse tokens) throws SchwabApiException {
        if (tokens == null) {
            throw new IllegalArgumentException("Tokens cannot be null");
        }

        lock.writeLock().lock();
        try {
            Path tokenPath = Paths.get(tokenPropertiesFile);
            Path tempPath = Paths.get(tokenPropertiesFile + ".tmp");
            Path backupPath = Paths.get(tokenPropertiesFile + ".backup");

            if (Files.exists(tokenPath)) {
                Files.copy(tokenPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
                logger.debug("Created backup of existing token file");
            }

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempPath.toFile(), tokens);
            Files.move(tempPath, tokenPath, StandardCopyOption.REPLACE_EXISTING);

            logger.info("Tokens saved to file: {}", tokenPropertiesFile);
            cleanupOldBackups();
            cachedTokensRef.set(new CachedTokens(tokens, Instant.now().plusSeconds(CACHE_DURATION_SECONDS)));
        } catch (IOException e) {
            logger.error("Failed to save tokens: {}", e.getMessage());
            throw new SchwabApiException(500, "Failed to save tokens: " + e.getMessage(), "SAVE_ERROR", null, e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void cleanupOldBackups() {
        try {
            Path tokenDir = Paths.get(".").toAbsolutePath();
            final String backupPattern = tokenPropertiesFile + ".backup";

            try (Stream<Path> stream = Files.list(tokenDir)) {
                stream.filter(path -> path.getFileName().toString().startsWith(backupPattern))
                        .sorted((p1, p2) -> {
                            try {
                                return Files.getLastModifiedTime(p2).compareTo(Files.getLastModifiedTime(p1));
                            } catch (IOException e) {
                                return 0;
                            }
                        })
                        .skip(MAX_BACKUP_FILES)
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                                logger.debug("Deleted old backup: {}", path.getFileName());
                            } catch (IOException e) {
                                logger.warn("Failed to delete old backup {}: {}", path.getFileName(), e.getMessage());
                            }
                        });
            }
        } catch (IOException e) {
            logger.debug("Error cleaning up backups: {}", e.getMessage());
        }
    }

    /**
     * Checks if a refresh token file exists.
     * @return true if the refresh token file exists, false otherwise.
     */
    public boolean hasRefreshToken() {
        return new File(refreshTokenFile).exists();
    }

    public boolean hasTokensInstance() {
        return Files.exists(Paths.get(tokenPropertiesFile));
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
        System.out.println("  JSON Tokens: " + Paths.get(tokenPropertiesFile).toAbsolutePath());
        System.out.println("  Refresh Token: " + Paths.get(refreshTokenFile).toAbsolutePath());
    }

    public void showTokenStatus() {
        System.out.println("Token Status Report:");
        System.out.println("=".repeat(70));

        try {
            TokenResponse tokens = loadTokensInstance(false);

            if (tokens == null) {
                System.out.println("No tokens found");
                System.out.println("Run OAuth authorization to obtain tokens");
                System.out.println("\nFile locations checked:");
                System.out.println("  • " + Paths.get(tokenPropertiesFile).toAbsolutePath());
                return;
            }

            System.out.println(tokens.getDisplayInfo());
            System.out.println(
                    "Refresh capability: " + (canRefresh() ? "Available" : "Not available (missing credentials)"));
        } catch (Exception e) {
            System.out.println("Error checking token status: " + e.getMessage());
            logger.error("Token status check failed", e);
        }
    }

    public void clearTokenFilesInstance() {
        lock.writeLock().lock();
        try {
            System.out.println("Clearing token files...");
            final AtomicInteger deletedCount = new AtomicInteger(0);
            Path tokenFile = Paths.get(tokenPropertiesFile);
            Path refreshFile = Paths.get(refreshTokenFile);

            if (Files.exists(tokenFile)) {
                try {
                    Files.delete(tokenFile);
                    System.out.println("    Deleted: " + tokenPropertiesFile);
                    deletedCount.incrementAndGet();
                } catch (IOException e) {
                    System.out.println("    Failed to delete: " + tokenPropertiesFile + " - " + e.getMessage());
                }
            }

            if (Files.exists(refreshFile)) {
                try {
                    Files.delete(refreshFile);
                    System.out.println("    Deleted: " + refreshTokenFile);
                    deletedCount.incrementAndGet();
                } catch (IOException e) {
                    System.out.println("    Failed to delete: " + refreshTokenFile + " - " + e.getMessage());
                }
            }
            
            // Remove the problematic backup cleanup loop from here.
            
            cachedTokensRef.set(null);
            System.out.println("Summary: " + deletedCount.get() + " files deleted, cache cleared");
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

    private static ObjectMapper createObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(SerializationFeature.INDENT_OUTPUT, true);
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