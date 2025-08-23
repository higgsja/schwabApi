package com.higgstx.schwab.service;

import com.higgstx.schwab.model.TokenResponse;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Smart Token Service that automatically manages token lifecycle.
 * Provides intelligent token reuse, automatic refresh, and background monitoring.
 */
@Getter
@Setter
public class SmartTokenService {

    private static final Logger logger = LoggerFactory.getLogger(SmartTokenService.class);
    
    // Singleton instance
    private static volatile SmartTokenService instance;
    
    // Background scheduler for token monitoring
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    // Service state
    private boolean isMonitoringEnabled = true;
    private long monitoringIntervalMinutes = 5;
    private long refreshBufferMinutes = 10; // Refresh tokens 10 minutes before expiry
    
    // Statistics
    private int automaticRefreshCount = 0;
    private int manualRefreshCount = 0;
    private Instant lastRefreshAttempt = null;
    private String lastRefreshResult = null;

    /**
     * Private constructor for singleton pattern
     */
    private SmartTokenService() {
        startTokenMonitoring();
    }

    /**
     * Gets the singleton instance
     */
    public static SmartTokenService getInstance() {
        if (instance == null) {
            synchronized (SmartTokenService.class) {
                if (instance == null) {
                    instance = new SmartTokenService();
                }
            }
        }
        return instance;
    }
    
    /**
     * Starts the background token monitoring and refresh task
     */
    private void startTokenMonitoring() {
        if (isMonitoringEnabled) {
            scheduler.scheduleAtFixedRate(this::checkAndRefresh, 0, monitoringIntervalMinutes, TimeUnit.MINUTES);
            logger.info("✅ Token monitoring started, running every {} minutes", monitoringIntervalMinutes);
        } else {
            logger.info("❌ Token monitoring is disabled");
        }
    }
    
    /**
     * Checks token status and refreshes if needed.
     */
    private void checkAndRefresh() {
        if (!isMonitoringEnabled) {
            return;
        }
        
        try {
            TokenResponse tokens = TokenManager.loadTokens(false);
            if (tokens != null) {
                if (!tokens.isAccessTokenValid()) {
                    logger.warn("⚠️ Access token has expired. Attempting to refresh...");
                    refreshTokens();
                } else if (tokens.willAccessTokenExpireSoon(refreshBufferMinutes * 60)) {
                    logger.info("🔄 Access token will expire in less than {} minutes. Attempting to refresh...", refreshBufferMinutes);
                    refreshTokens();
                }
            } else {
                logger.warn("❌ No tokens found. Manual authorization required.");
            }
        } catch (Exception e) {
            logger.error("❌ Error in token monitoring: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Attempts to refresh the tokens and updates service status
     */
    private void refreshTokens() {
        lastRefreshAttempt = Instant.now();
        
        try {
            TokenResponse refreshedTokens = TokenManager.forceTokenRefresh();
            if (refreshedTokens != null) {
                automaticRefreshCount++;
                lastRefreshResult = "SUCCESS";
                logger.info("✅ Token refreshed successfully via automatic process.");
            } else {
                lastRefreshResult = "FAILURE";
                logger.error("❌ Automatic token refresh failed. Manual re-authorization may be required.");
            }
        } catch (Exception e) {
            lastRefreshResult = "ERROR";
            logger.error("❌ An exception occurred during automatic token refresh: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Returns the current status of the token service
     */
    public TokenServiceStatus getStatus() {
        boolean hasTokens = TokenManager.loadTokens(false) != null;
        boolean hasValidAccess = TokenManager.hasValidTokens();
        boolean hasValidRefresh = false;
        long accessExpiresInSeconds = -1;
        long refreshExpiresInSeconds = -1;
        
        try {
            TokenResponse tokens = TokenManager.loadTokens(false);
            if (tokens != null) {
                hasValidRefresh = tokens.isRefreshTokenValid();
                accessExpiresInSeconds = tokens.getSecondsUntilAccessExpiry();
                refreshExpiresInSeconds = tokens.getSecondsUntilRefreshExpiry();
            }
        } catch (Exception e) {
            logger.error("Error getting token status: {}", e.getMessage());
        }
        
        return new TokenServiceStatus(hasTokens, hasValidAccess, hasValidRefresh, 
                                      isMonitoringEnabled, automaticRefreshCount, manualRefreshCount,
                                      lastRefreshAttempt, lastRefreshResult,
                                      accessExpiresInSeconds, refreshExpiresInSeconds);
    }
    
    /**
     * Shuts down the background scheduler
     */
    public void shutdown() {
        logger.info("Shutting down SmartTokenService scheduler...");
        scheduler.shutdown();
    }
    
    /**
     * A simple DTO to hold the status of the service
     */
    public static class TokenServiceStatus {
        private final boolean hasTokens;
        private final boolean hasValidAccess;
        private final boolean hasValidRefresh;
        private final boolean monitoringEnabled;
        private final int automaticRefreshCount;
        private final int manualRefreshCount;
        private final Instant lastRefreshAttempt;
        private final String lastRefreshResult;
        private final long accessExpiresInSeconds;
        private final long refreshExpiresInSeconds;

        public TokenServiceStatus(boolean hasTokens, boolean hasValidAccess, boolean hasValidRefresh,
                                boolean monitoringEnabled, int automaticRefreshCount, int manualRefreshCount,
                                Instant lastRefreshAttempt, String lastRefreshResult,
                                long accessExpiresInSeconds, long refreshExpiresInSeconds) {
            this.hasTokens = hasTokens;
            this.hasValidAccess = hasValidAccess;
            this.hasValidRefresh = hasValidRefresh;
            this.monitoringEnabled = monitoringEnabled;
            this.automaticRefreshCount = automaticRefreshCount;
            this.manualRefreshCount = manualRefreshCount;
            this.lastRefreshAttempt = lastRefreshAttempt;
            this.lastRefreshResult = lastRefreshResult;
            this.accessExpiresInSeconds = accessExpiresInSeconds;
            this.refreshExpiresInSeconds = refreshExpiresInSeconds;
        }

        /**
         * Gets overall service health status
         */
        public String getOverallStatus() {
            if (!hasTokens) {
                return "❌ NO TOKENS";
            } else if (hasValidAccess) {
                return "✅ READY";
            } else if (hasValidRefresh) {
                return "⚠️ REFRESH NEEDED";
            } else {
                return "❌ RE-AUTH REQUIRED";
            }
        }
    }
}