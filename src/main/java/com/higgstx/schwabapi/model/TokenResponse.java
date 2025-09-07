package com.higgstx.schwabapi.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;

/**
 * Token response model using @Data and @Builder
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
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
    public String getQuickStatus() {
        if (isAccessTokenValid()) return "VALID";
        if (isRefreshTokenValid()) return "REFRESH_NEEDED";
        return "EXPIRED";
    }

    @Override
    public String toString() {
        return String.format("TokenResponse{tokenType='%s', scope='%s', source=%s, status='%s', accessValid=%s, refreshValid=%s}",
                tokenType, scope, source, getQuickStatus(), isAccessTokenValid(), isRefreshTokenValid());
    }
}