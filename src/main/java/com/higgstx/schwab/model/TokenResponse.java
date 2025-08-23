package com.higgstx.schwab.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * POJO representing the response from a token exchange with the Schwab API.
 * Uses Lombok @Getter and @Setter for clean code and provides formatted output methods.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TokenResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("refresh_token")
    private String refreshToken;

    @JsonProperty("scope")
    private String scope;

    @JsonProperty("expires_in")
    private long expiresIn;

    @JsonProperty("refresh_token_expires_in")
    private long refreshTokenExpiresIn;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("id_token")
    private String idToken;

    // Computed fields (not from JSON)
    @JsonProperty("expiresAt")
    private Instant expiresAt;
    
    @JsonProperty("refreshTokenExpiresAt")
    private Instant refreshTokenExpiresAt;

    /**
     * Formats and displays comprehensive token information in a beautiful table format
     */
    @JsonIgnore
    public String getDisplayInfo() {
        StringBuilder sb = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
                .withZone(ZoneId.systemDefault());

        // Header
        sb.append("╔═ 🎫 SCHWAB API TOKEN INFORMATION ").append("═".repeat(25)).append("╗\n");
        
        // Access Token Section
        sb.append("║ 🔒 ACCESS TOKEN                                      ║\n");
        sb.append("╠═══════════════════════════════════════════════════════╣\n");
        
        if (accessToken != null) {
            String preview = truncateToken(accessToken, 45);
            sb.append("║ Token: ").append(String.format("%-45s", preview)).append(" ║\n");
        } else {
            sb.append("║ Token: ").append(String.format("%-45s", "❌ Not available")).append(" ║\n");
        }
        
        sb.append("║ Type:  ").append(String.format("%-45s", tokenType != null ? tokenType : "Unknown")).append(" ║\n");
        sb.append("║ Scope: ").append(String.format("%-45s", scope != null ? scope : "Unknown")).append(" ║\n");
        
        // Expiration info
        if (expiresAt != null) {
            String expirationStr = formatter.format(expiresAt);
            sb.append("║ Expires: ").append(String.format("%-43s", expirationStr)).append(" ║\n");
            
            long secondsRemaining = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
            if (secondsRemaining > 0) {
                String timeRemaining = formatDuration(secondsRemaining);
                sb.append("║ ⏳ Remaining: ").append(String.format("%-39s", timeRemaining)).append(" ║\n");
                sb.append("║ Status: ").append(String.format("%-43s", "✅ ACTIVE")).append(" ║\n");
            } else {
                sb.append("║ Status: ").append(String.format("%-43s", "❌ EXPIRED")).append(" ║\n");
            }
        } else {
            sb.append("║ Expires: ").append(String.format("%-43s", "❌ Not set")).append(" ║\n");
        }
        
        // Refresh Token Section
        sb.append("╠═ 🔄 REFRESH TOKEN ").append("═".repeat(34)).append("╣\n");
        
        if (refreshToken != null) {
            String refreshPreview = truncateToken(refreshToken, 45);
            sb.append("║ Token: ").append(String.format("%-45s", refreshPreview)).append(" ║\n");
            
            if (refreshTokenExpiresAt != null) {
                String refreshExpirationStr = formatter.format(refreshTokenExpiresAt);
                sb.append("║ Expires: ").append(String.format("%-43s", refreshExpirationStr)).append(" ║\n");
                
                long refreshSecondsRemaining = refreshTokenExpiresAt.getEpochSecond() - Instant.now().getEpochSecond();
                if (refreshSecondsRemaining > 0) {
                    String refreshTimeRemaining = formatDuration(refreshSecondsRemaining);
                    sb.append("║ ⏳ Remaining: ").append(String.format("%-39s", refreshTimeRemaining)).append(" ║\n");
                    sb.append("║ Status: ").append(String.format("%-43s", "✅ VALID")).append(" ║\n");
                } else {
                    sb.append("║ Status: ").append(String.format("%-43s", "❌ EXPIRED")).append(" ║\n");
                }
            } else if (refreshTokenExpiresIn > 0) {
                String validity = formatDuration(refreshTokenExpiresIn);
                sb.append("║ Validity: ").append(String.format("%-41s", validity)).append(" ║\n");
            }
        } else {
            sb.append("║ Token: ").append(String.format("%-45s", "❌ Not available")).append(" ║\n");
        }
        
        // Additional Info Section
        if (idToken != null || expiresIn > 0) {
            sb.append("╠═ 📊 ADDITIONAL INFO ").append("═".repeat(32)).append("╣\n");
            
            if (expiresIn > 0) {
                String accessValidity = formatDuration(expiresIn);
                sb.append("║ Access Token Lifetime: ").append(String.format("%-28s", accessValidity)).append(" ║\n");
            }
            
            if (refreshTokenExpiresIn > 0) {
                String refreshValidity = formatDuration(refreshTokenExpiresIn);
                sb.append("║ Refresh Token Lifetime: ").append(String.format("%-27s", refreshValidity)).append(" ║\n");
            }
            
            if (idToken != null) {
                sb.append("║ ID Token: ").append(String.format("%-41s", "✅ Present")).append(" ║\n");
            }
        }
        
        // Footer with overall status
        sb.append("╠═══════════════════════════════════════════════════════╣\n");
        String overallStatus = getOverallStatus();
        sb.append("║ 🎯 OVERALL STATUS: ").append(String.format("%-33s", overallStatus)).append(" ║\n");
        sb.append("╚═══════════════════════════════════════════════════════╝");
        
        return sb.toString();
    }

    /**
     * Provides a compact, single-line summary
     */
    @JsonIgnore
    public String getCompactInfo() {
        return String.format("🎫 %s | 🕐 Expires: %s | 🔄 Refresh: %s",
                getOverallStatus(),
                expiresAt != null ? 
                    DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(expiresAt) : 
                    "N/A",
                refreshTokenExpiresAt != null ? 
                    DateTimeFormatter.ofPattern("MM-dd").withZone(ZoneId.systemDefault()).format(refreshTokenExpiresAt) : 
                    "N/A"
        );
    }

    /**
     * Quick status check for console output
     */
    @JsonIgnore
    public String getQuickStatus() {
        if (isAccessTokenValid()) {
            return "✅ READY";
        } else if (isRefreshTokenValid()) {
            return "⚠️ REFRESH NEEDED";
        } else {
            return "❌ RE-AUTH REQUIRED";
        }
    }

    /**
     * Formats duration in seconds to human-readable format
     */
    @JsonIgnore
    private String formatDuration(long seconds) {
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
     * Truncates a token for display purposes
     */
    @JsonIgnore
    private String truncateToken(String token, int maxLength) {
        if (token == null) return "null";
        if (token.length() <= maxLength) return token;
        
        int prefixLength = Math.min(15, maxLength / 3);
        int suffixLength = Math.min(8, maxLength / 4);
        
        return token.substring(0, prefixLength) + "..." + 
               token.substring(token.length() - suffixLength);
    }

    /**
     * Gets the overall status of the token set
     */
    @JsonIgnore
    private String getOverallStatus() {
        if (isAccessTokenValid()) {
            return "✅ ACTIVE & READY";
        } else if (isRefreshTokenValid()) {
            return "⚠️ REFRESH REQUIRED";
        } else {
            return "❌ REAUTHORIZATION NEEDED";
        }
    }

    // Utility methods for token validation
    
    /**
     * Checks if the access token is currently valid
     */
    @JsonIgnore
    public boolean isAccessTokenValid() {
        return expiresAt != null && expiresAt.isAfter(Instant.now());
    }

    /**
     * Checks if the refresh token is currently valid
     */
    @JsonIgnore
    public boolean isRefreshTokenValid() {
        return refreshTokenExpiresAt != null && refreshTokenExpiresAt.isAfter(Instant.now());
    }

    /**
     * Gets seconds until access token expires (negative if expired)
     */
    @JsonIgnore
    public long getSecondsUntilAccessExpiry() {
        if (expiresAt == null) return -1;
        return expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
    }

    /**
     * Gets seconds until refresh token expires (negative if expired)
     */
    @JsonIgnore
    public long getSecondsUntilRefreshExpiry() {
        if (refreshTokenExpiresAt == null) return -1;
        return refreshTokenExpiresAt.getEpochSecond() - Instant.now().getEpochSecond();
    }

    /**
     * Checks if access token will expire within the given seconds
     */
    @JsonIgnore
    public boolean willAccessTokenExpireSoon(long withinSeconds) {
        long remaining = getSecondsUntilAccessExpiry();
        return remaining >= 0 && remaining <= withinSeconds;
    }

    /**
     * Custom toString() method for debugging
     */
    @Override
    public String toString() {
        return "TokenResponse{" +
                "tokenType='" + tokenType + '\'' +
                ", scope='" + scope + '\'' +
                ", expiresIn=" + expiresIn +
                ", refreshTokenExpiresIn=" + refreshTokenExpiresIn +
                ", expiresAt=" + expiresAt +
                ", refreshTokenExpiresAt=" + refreshTokenExpiresAt +
                ", accessTokenValid=" + isAccessTokenValid() +
                ", refreshTokenValid=" + isRefreshTokenValid() +
                '}';
    }
}