package com.higgstx.schwab.service;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.higgstx.schwab.client.SchwabOAuthClient;
import com.higgstx.schwab.config.SchwabConfig;
import com.higgstx.schwab.model.TokenResponse;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;

/**
 * Enhanced Token Manager with automatic token reuse and refresh capabilities.
 * Handles persistent storage and intelligent token lifecycle management.
 */
@Getter
@Setter
public class TokenManager {

    private static final Logger logger = LoggerFactory.getLogger(TokenManager.class);
    private static final ObjectMapper objectMapper = createObjectMapper();

    // Token refresh buffer - refresh tokens this many seconds before expiry
    private static final long TOKEN_REFRESH_BUFFER_SECONDS = 300; // 5 minutes

    // Cached token instance for performance
    private static TokenResponse cachedTokens = null;
    private static Instant lastCacheUpdate = null;
    private static final long CACHE_DURATION_SECONDS = 30; // Cache for 30 seconds

    /**
     * Gets a valid access token, refreshing if necessary.
     */
    public static String getValidAccessToken() throws IOException {
        TokenResponse tokens = loadTokens(true);
        if (tokens != null && tokens.isAccessTokenValid()) {
            return tokens.getAccessToken();
        }
        throw new IOException("Unable to get a valid access token. Re-authorization may be required.");
    }

    /**
     * Loads tokens from file, with optional auto-refresh.
     */
    public static TokenResponse loadTokens(boolean autoRefresh) {
        // Use cached tokens if they are fresh
        if (cachedTokens != null && lastCacheUpdate != null &&
                Instant.now().isBefore(lastCacheUpdate.plusSeconds(CACHE_DURATION_SECONDS))) {
            return cachedTokens;
        }

        try {
            // Check if token file exists
            File tokenFile = new File(SchwabConfig.TOKEN_PROPERTIES_FILE);
            if (!tokenFile.exists()) {
                logger.info("Token file not found. Manual authorization required.");
                return null;
            }

            // Load tokens from file
            TokenResponse tokens = objectMapper.readValue(tokenFile, TokenResponse.class);
            logger.info("Loaded tokens from file. Status: {}", getTokenOverallStatus(tokens));

            // Check if access token is still valid
            if (tokens.isAccessTokenValid()) {
                // Now check if it's about to expire with our buffer
                if (tokens.willAccessTokenExpireSoon(TOKEN_REFRESH_BUFFER_SECONDS)) {
                    logger.warn("Access token will expire soon. Attempting to refresh...");
                    if (autoRefresh && tokens.isRefreshTokenValid()) {
                        TokenResponse refreshedTokens = forceTokenRefresh(tokens);
                        if (refreshedTokens != null) {
                            logger.info("✅ Tokens refreshed successfully!");
                            updateCache(refreshedTokens);
                            return refreshedTokens;
                        }
                    }
                } else {
                    logger.info("Access token is valid and fresh. No refresh needed.");
                }
                updateCache(tokens);
                return tokens;
            }

            // If access token is expired, try to refresh
            if (autoRefresh && tokens.isRefreshTokenValid()) {
                logger.warn("Access token expired. Attempting to refresh with refresh token...");
                TokenResponse refreshedTokens = forceTokenRefresh(tokens);
                if (refreshedTokens != null) {
                    logger.info("✅ Tokens refreshed successfully!");
                    updateCache(refreshedTokens);
                    return refreshedTokens;
                }
            }

            // If we get here, either auto-refresh is off, or refresh token is expired
            return tokens;

        } catch (IOException e) {
            logger.error("Error loading or refreshing tokens: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Forces a token refresh regardless of expiry.
     */
    public static TokenResponse forceTokenRefresh() throws IOException {
        TokenResponse currentTokens = loadTokens(false);
        if (currentTokens == null) {
            throw new IOException("No tokens available to refresh.");
        }
        return forceTokenRefresh(currentTokens);
    }

    /**
     * Internal method to perform the actual token refresh API call.
     */
    private static TokenResponse forceTokenRefresh(TokenResponse currentTokens) throws IOException {
        try (SchwabOAuthClient client = new SchwabOAuthClient()) { // Use no-arg constructor
            TokenResponse refreshed = client.refreshTokens(currentTokens.getRefreshToken());
            saveTokens(refreshed);
            return refreshed;
        } catch (Exception e) {
            logger.error("❌ Token refresh failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Saves tokens to a file.
     */
    public static void saveTokens(TokenResponse tokens) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(SchwabConfig.TOKEN_PROPERTIES_FILE), tokens);
        logger.info("Tokens saved to file: {}", SchwabConfig.TOKEN_PROPERTIES_FILE);
        updateCache(tokens);
    }

    /**
     * Check if tokens exist on disk.
     */
    public static boolean hasTokens() {
        return new File(SchwabConfig.TOKEN_PROPERTIES_FILE).exists();
    }

    /**
     * Check if the current tokens are valid and usable.
     */
    public static boolean hasValidTokens() {
        TokenResponse tokens = loadTokens(false);
        return tokens != null && tokens.isAccessTokenValid();
    }

    /**
     * Check if a refresh token is available, even if the access token has expired.
     */
    public static boolean hasUsableTokens() {
        TokenResponse tokens = loadTokens(false);
        return tokens != null && tokens.isRefreshTokenValid();
    }

    /**
     * Shows token file paths in formatted output
     */
    public static void showTokenFilePaths() {
        System.out.println("📁 Token File Locations:");
        System.out.println("─".repeat(50));
        
        Path tokenFile = Paths.get(SchwabConfig.TOKEN_PROPERTIES_FILE).toAbsolutePath();
        Path refreshFile = Paths.get(SchwabConfig.REFRESH_TOKEN_FILE).toAbsolutePath();
        
        System.out.println("📄 JSON Token File:");
        System.out.println("   " + tokenFile);
        System.out.println("   Exists: " + (Files.exists(tokenFile) ? "✅ Yes" : "❌ No"));
        
        if (Files.exists(tokenFile)) {
            try {
                long size = Files.size(tokenFile);
                String lastModified = Files.getLastModifiedTime(tokenFile)
                    .toInstant()
                    .atZone(java.time.ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                System.out.println("   Size: " + formatFileSize(size));
                System.out.println("   Modified: " + lastModified);
            } catch (IOException e) {
                System.out.println("   Error reading file info: " + e.getMessage());
            }
        }
        
        System.out.println();
        System.out.println("📄 Refresh Token File:");
        System.out.println("   " + refreshFile);
        System.out.println("   Exists: " + (Files.exists(refreshFile) ? "✅ Yes" : "❌ No"));
        
        if (Files.exists(refreshFile)) {
            try {
                long size = Files.size(refreshFile);
                String lastModified = Files.getLastModifiedTime(refreshFile)
                    .toInstant()
                    .atZone(java.time.ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                System.out.println("   Size: " + formatFileSize(size));
                System.out.println("   Modified: " + lastModified);
            } catch (IOException e) {
                System.out.println("   Error reading file info: " + e.getMessage());
            }
        }
    }

    /**
     * Shows comprehensive token status
     */
    public static void showTokenStatus() {
        System.out.println("🎫 Token Status Report:");
        System.out.println("═".repeat(60));
        
        try {
            TokenResponse tokens = loadTokens(false);
            
            if (tokens == null) {
                System.out.println("❌ No tokens found");
                System.out.println("💡 Run OAuth authorization to obtain tokens");
                return;
            }
            
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
                    .withZone(java.time.ZoneId.systemDefault());
            Instant now = Instant.now();
            
            // Access token status
            System.out.println("🔑 ACCESS TOKEN:");
            if (tokens.getAccessToken() != null) {
                String preview = tokens.getAccessToken().substring(0, Math.min(15, tokens.getAccessToken().length())) + "...";
                System.out.println("   Token: " + preview);
                System.out.println("   Type: " + (tokens.getTokenType() != null ? tokens.getTokenType() : "Bearer"));
                
                if (tokens.getExpiresAt() != null) {
                    System.out.println("   Expires: " + formatter.format(tokens.getExpiresAt()));
                    long secondsRemaining = tokens.getExpiresAt().getEpochSecond() - now.getEpochSecond();
                    
                    if (secondsRemaining > 0) {
                        System.out.println("   Remaining: " + formatDuration(secondsRemaining));
                        System.out.println("   Status: ✅ VALID");
                    } else {
                        System.out.println("   Status: ❌ EXPIRED");
                    }
                } else {
                    System.out.println("   Status: ❓ UNKNOWN");
                }
            } else {
                System.out.println("   ❌ No access token");
            }
            
            System.out.println();
            
            // Refresh token status
            System.out.println("🔄 REFRESH TOKEN:");
            if (tokens.getRefreshToken() != null) {
                String refreshPreview = tokens.getRefreshToken().substring(0, Math.min(15, tokens.getRefreshToken().length())) + "...";
                System.out.println("   Token: " + refreshPreview);
                
                if (tokens.getRefreshTokenExpiresAt() != null) {
                    System.out.println("   Expires: " + formatter.format(tokens.getRefreshTokenExpiresAt()));
                    long refreshSecondsRemaining = tokens.getRefreshTokenExpiresAt().getEpochSecond() - now.getEpochSecond();
                    
                    if (refreshSecondsRemaining > 0) {
                        System.out.println("   Remaining: " + formatDuration(refreshSecondsRemaining));
                        System.out.println("   Status: ✅ VALID");
                    } else {
                        System.out.println("   Status: ❌ EXPIRED");
                    }
                } else {
                    System.out.println("   Status: ❓ UNKNOWN");
                }
            } else {
                System.out.println("   ❌ No refresh token");
            }
            
            // Overall status
            System.out.println();
            System.out.println("📊 OVERALL STATUS: " + getOverallStatus(tokens));
            
        } catch (Exception e) {
            System.out.println("❌ Error checking token status: " + e.getMessage());
            logger.error("Token status check failed", e);
        }
    }

    /**
     * Clears all token files
     */
    public static void clearTokenFiles() {
        System.out.println("🧹 Clearing token files...");
        
        Path jsonTokenFile = Paths.get(SchwabConfig.TOKEN_PROPERTIES_FILE);
        Path refreshTokenFile = Paths.get(SchwabConfig.REFRESH_TOKEN_FILE);
        
        int deletedCount = 0;
        
        // Clear JSON token file
        if (Files.exists(jsonTokenFile)) {
            try {
                Files.delete(jsonTokenFile);
                System.out.println("   ✅ Deleted: " + SchwabConfig.TOKEN_PROPERTIES_FILE);
                deletedCount++;
            } catch (IOException e) {
                System.out.println("   ❌ Failed to delete: " + SchwabConfig.TOKEN_PROPERTIES_FILE + " - " + e.getMessage());
            }
        } else {
            System.out.println("   ℹ️  Not found: " + SchwabConfig.TOKEN_PROPERTIES_FILE);
        }
        
        // Clear refresh token file
        if (Files.exists(refreshTokenFile)) {
            try {
                Files.delete(refreshTokenFile);
                System.out.println("   ✅ Deleted: " + SchwabConfig.REFRESH_TOKEN_FILE);
                deletedCount++;
            } catch (IOException e) {
                System.out.println("   ❌ Failed to delete: " + SchwabConfig.REFRESH_TOKEN_FILE + " - " + e.getMessage());
            }
        } else {
            System.out.println("   ℹ️  Not found: " + SchwabConfig.REFRESH_TOKEN_FILE);
        }
        
        // Clear cache
        cachedTokens = null;
        lastCacheUpdate = null;
        
        System.out.println("📋 Summary: " + deletedCount + " files deleted, cache cleared");
    }

    /**
     * Updates the in-memory cache with the new tokens.
     */
    private static void updateCache(TokenResponse tokens) {
        cachedTokens = tokens;
        lastCacheUpdate = Instant.now();
    }

    /**
     * Creates the ObjectMapper with common settings.
     */
    private static ObjectMapper createObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    /**
     * Logs the token response to the console in a readable format.
     */
    public static void logTokenResponse(String message, TokenResponse tokens) throws JsonProcessingException {
        logger.info("{}", message);
        ObjectNode json = objectMapper.convertValue(tokens, ObjectNode.class);
        logger.info("{}", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json));
    }

    /**
     * Logs a specific field of the token response.
     */
    private static void logTokenField(JsonNode rootNode, String fieldName) {
        if (rootNode.has(fieldName)) {
            com.fasterxml.jackson.databind.JsonNode fieldNode = rootNode.get(fieldName);
            String value;

            if (fieldNode.isNull()) {
                value = "NULL";
            } else if (fieldNode.isTextual()) {
                String text = fieldNode.asText();
                if (fieldName.contains("token") && text.length() > 20) {
                    value = text.substring(0, 10) + "...[TRUNCATED]..." + text.substring(text.length() - 5);
                } else {
                    value = text;
                }
            } else if (fieldNode.isNumber()) {
                value = String.valueOf(fieldNode.asLong());
            } else {
                value = fieldNode.toString();
            }

            logger.error("    {}: {}", fieldName, value);
        } else {
            logger.error("    {}: MISSING", fieldName);
        }
    }

    /**
     * Gets overall token status description
     */
    private static String getTokenOverallStatus(TokenResponse tokens) {
        if (tokens.isAccessTokenValid()) {
            if (tokens.willAccessTokenExpireSoon(TOKEN_REFRESH_BUFFER_SECONDS)) {
                return "✅ ACTIVE (Refresh recommended soon)";
            } else {
                return "✅ ACTIVE & READY";
            }
        } else if (tokens.isRefreshTokenValid()) {
            return "⚠️ ACCESS EXPIRED - Refresh token available";
        } else {
            return "❌ EXPIRED - Re-authorization required";
        }
    }

    /**
     * Gets overall token status description
     */
    private static String getOverallStatus(TokenResponse tokens) {
        if (tokens.isAccessTokenValid()) {
            if (tokens.willAccessTokenExpireSoon(TOKEN_REFRESH_BUFFER_SECONDS)) {
                return "✅ ACTIVE (Refresh recommended soon)";
            } else {
                return "✅ ACTIVE & READY";
            }
        } else if (tokens.isRefreshTokenValid()) {
            return "⚠️ ACCESS EXPIRED - Refresh token available";
        } else {
            return "❌ EXPIRED - Re-authorization required";
        }
    }

    /**
     * Formats duration in seconds to human-readable format
     */
    private static String formatDuration(long seconds) {
        if (seconds < 0) {
            return "Expired";
        } else if (seconds < 60) {
            return seconds + " sec";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            long remainingSecs = seconds % 60;
            return String.format("%d:%02d min", minutes, remainingSecs);
        } else if (seconds < 86400) {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return String.format("%d:%02d hrs", hours, minutes);
        } else {
            long days = seconds / 86400;
            long hours = (seconds % 86400) / 3600;
            return String.format("%d days %d hrs", days, hours);
        }
    }

    /**
     * Formats file size in human-readable format
     */
    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}