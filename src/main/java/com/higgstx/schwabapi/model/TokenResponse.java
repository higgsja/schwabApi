package com.higgstx.schwabapi.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Token response model - now with simple JSON support
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenResponse {

    private String accessToken;
    private String refreshToken;
    private String scope;
    private long expiresIn;
    private long refreshTokenExpiresIn;
    private String tokenType;
    private String idToken;

    // Calculated expiration times
    private Instant expiresAt;
    private Instant refreshTokenExpiresAt;
    private Instant issuedAt;
    private TokenSource source;

    public enum TokenSource {
        AUTHORIZATION_CODE,
        REFRESH_TOKEN,
        CLIENT_CREDENTIALS
    }

    // Core validation methods
    public boolean isAccessTokenValid() {
        return expiresAt != null && expiresAt.isAfter(Instant.now());
    }

    public boolean isRefreshTokenValid() {
        return refreshTokenExpiresAt != null && refreshTokenExpiresAt.isAfter(Instant.now());
    }

    public long getSecondsUntilAccessExpiry() {
        if (expiresAt == null) return -1;
        return expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
    }

    public long getSecondsUntilRefreshExpiry() {
        if (refreshTokenExpiresAt == null) return -1;
        return refreshTokenExpiresAt.getEpochSecond() - Instant.now().getEpochSecond();
    }

    public boolean willAccessTokenExpireSoon(long withinSeconds) {
        long remaining = getSecondsUntilAccessExpiry();
        return remaining >= 0 && remaining <= withinSeconds;
    }

    public String getQuickStatus() {
        if (isAccessTokenValid()) return "VALID";
        if (isRefreshTokenValid()) return "REFRESH_NEEDED";
        return "EXPIRED";
    }

    /**
     * Create TokenResponse from JSON Map
     */
    public static TokenResponse fromMap(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }

        TokenResponse.TokenResponseBuilder builder = TokenResponse.builder();

        if (data.containsKey("access_token")) {
            builder.accessToken(data.get("access_token").toString());
        }

        if (data.containsKey("refresh_token")) {
            builder.refreshToken(data.get("refresh_token").toString());
        }

        if (data.containsKey("scope")) {
            builder.scope(data.get("scope").toString());
        }

        if (data.containsKey("expires_in")) {
            builder.expiresIn(convertToLong(data.get("expires_in")));
        }

        if (data.containsKey("refresh_token_expires_in")) {
            builder.refreshTokenExpiresIn(convertToLong(data.get("refresh_token_expires_in")));
        }

        if (data.containsKey("token_type")) {
            builder.tokenType(data.get("token_type").toString());
        }

        if (data.containsKey("id_token")) {
            builder.idToken(data.get("id_token").toString());
        }

        // Handle computed fields
        if (data.containsKey("expiresAt")) {
            String expiresAtStr = data.get("expiresAt").toString();
            try {
                builder.expiresAt(Instant.parse(expiresAtStr));
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }

        if (data.containsKey("refreshTokenExpiresAt")) {
            String refreshExpiresAtStr = data.get("refreshTokenExpiresAt").toString();
            try {
                builder.refreshTokenExpiresAt(Instant.parse(refreshExpiresAtStr));
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }

        if (data.containsKey("issuedAt")) {
            String issuedAtStr = data.get("issuedAt").toString();
            try {
                builder.issuedAt(Instant.parse(issuedAtStr));
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }

        if (data.containsKey("source")) {
            String sourceStr = data.get("source").toString();
            try {
                builder.source(TokenSource.valueOf(sourceStr));
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }

        return builder.build();
    }

    /**
     * Convert TokenResponse to Map for JSON serialization
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new HashMap<>();

        if (accessToken != null) result.put("access_token", accessToken);
        if (refreshToken != null) result.put("refresh_token", refreshToken);
        if (scope != null) result.put("scope", scope);
        if (expiresIn > 0) result.put("expires_in", expiresIn);
        if (refreshTokenExpiresIn > 0) result.put("refresh_token_expires_in", refreshTokenExpiresIn);
        if (tokenType != null) result.put("token_type", tokenType);
        if (idToken != null) result.put("id_token", idToken);

        // Add computed fields
        if (expiresAt != null) result.put("expiresAt", expiresAt.toString());
        if (refreshTokenExpiresAt != null) result.put("refreshTokenExpiresAt", refreshTokenExpiresAt.toString());
        if (issuedAt != null) result.put("issuedAt", issuedAt.toString());
        if (source != null) result.put("source", source.toString());

        return result;
    }

    private static long convertToLong(Object value) {
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        return String.format("TokenResponse{tokenType='%s', scope='%s', source=%s, status='%s', accessValid=%s, refreshValid=%s}",
                tokenType, scope, source, getQuickStatus(), isAccessTokenValid(), isRefreshTokenValid());
    }
}