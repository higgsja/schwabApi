package com.higgstx.schwabapi.model;

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
import java.util.List;
import java.util.Map;

/**
 * Enhanced token response with additional metadata, health status, and improved display formatting.
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

    // Enhanced fields
    @JsonProperty("expiresAt")
    private Instant expiresAt;
    
    @JsonProperty("refreshTokenExpiresAt")
    private Instant refreshTokenExpiresAt;

    @JsonProperty("issuedAt")
    private Instant issuedAt;
    
    @JsonProperty("issuer")
    private String issuer;
    
    @JsonProperty("audiences")
    private List<String> audiences;
    
    @JsonProperty("customClaims")
    private Map<String, Object> customClaims;
    
    @JsonProperty("source")
    private TokenSource source;

    /**
     * Token source enumeration
     */
    public enum TokenSource {
        AUTHORIZATION_CODE,
        REFRESH_TOKEN,
        CLIENT_CREDENTIALS
    }

    // Utility methods
    @JsonIgnore
    public boolean isAccessTokenValid() {
        return expiresAt != null && expiresAt.isAfter(Instant.now());
    }

    @JsonIgnore
    public boolean isRefreshTokenValid() {
        return refreshTokenExpiresAt != null && refreshTokenExpiresAt.isAfter(Instant.now());
    }

    @JsonIgnore
    public long getSecondsUntilAccessExpiry() {
        if (expiresAt == null) return -1;
        return expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
    }

    @JsonIgnore
    public long getSecondsUntilRefreshExpiry() {
        if (refreshTokenExpiresAt == null) return -1;
        return refreshTokenExpiresAt.getEpochSecond() - Instant.now().getEpochSecond();
    }

    @JsonIgnore
    public boolean willAccessTokenExpireSoon(long withinSeconds) {
        long remaining = getSecondsUntilAccessExpiry();
        return remaining >= 0 && remaining <= withinSeconds;
    }

    @JsonIgnore
    public boolean needsRefreshSoon(long bufferSeconds) {
        return willAccessTokenExpireSoon(bufferSeconds);
    }

    @JsonIgnore
    public String getQuickStatus() {
        if (isAccessTokenValid()) return "VALID";
        if (isRefreshTokenValid()) return "REFRESH_NEEDED";
        return "EXPIRED";
    }

    @JsonIgnore
    public String getDisplayInfo() {
        StringBuilder sb = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
                .withZone(ZoneId.systemDefault());

        sb.append("=== Token Status ===\n");
        sb.append("Access Token Valid: ").append(isAccessTokenValid()).append("\n");
        sb.append("Refresh Token Valid: ").append(isRefreshTokenValid()).append("\n");
        
        if (expiresAt != null) {
            sb.append("Access Token Expires: ").append(formatter.format(expiresAt)).append("\n");
            long secondsRemaining = getSecondsUntilAccessExpiry();
            if (secondsRemaining > 0) {
                sb.append("Time Remaining: ").append(formatDuration(secondsRemaining)).append("\n");
            } else {
                sb.append("Status: EXPIRED\n");
            }
        }
        
        if (refreshTokenExpiresAt != null) {
            sb.append("Refresh Token Expires: ").append(formatter.format(refreshTokenExpiresAt)).append("\n");
            long refreshSecondsRemaining = getSecondsUntilRefreshExpiry();
            if (refreshSecondsRemaining > 0) {
                sb.append("Refresh Time Remaining: ").append(formatDuration(refreshSecondsRemaining)).append("\n");
            } else {
                sb.append("Refresh Status: EXPIRED\n");
            }
        }
        
        return sb.toString();
    }

    @JsonIgnore
    public String getHealthStatus() {
        if (isAccessTokenValid()) return "HEALTHY";
        if (isRefreshTokenValid()) return "NEEDS_REFRESH";
        return "NEEDS_REAUTH";
    }

    private String formatDuration(long seconds) {
        if (seconds < 0) {
            return "Expired";
        } else if (seconds < 60) {
            return seconds + " seconds";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            return minutes + " minute" + (minutes != 1 ? "s" : "");
        } else if (seconds < 86400) {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return hours + "h " + minutes + "m";
        } else {
            long days = seconds / 86400;
            long hours = (seconds % 86400) / 3600;
            return days + "d " + hours + "h";
        }
    }

    /**
     * Enhanced toString with more comprehensive information
     */
    @Override
    public String toString() {
        return String.format("TokenResponse{" +
                "tokenType='%s', " +
                "scope='%s', " +
                "source='%s', " +
                "healthStatus='%s', " +
                "expiresIn=%d, " +
                "refreshTokenExpiresIn=%d, " +
                "expiresAt=%s, " +
                "refreshTokenExpiresAt=%s, " +
                "accessTokenValid=%s, " +
                "refreshTokenValid=%s" +
                '}',
                tokenType, scope, source,
                getHealthStatus(),
                expiresIn, refreshTokenExpiresIn,
                expiresAt, refreshTokenExpiresAt,
                isAccessTokenValid(), isRefreshTokenValid());
    }
}