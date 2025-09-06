package com.higgstx.schwabapi.config;

import com.higgstx.schwabapi.model.ApiResponse;
import com.higgstx.schwabapi.model.TokenResponse;
import com.higgstx.schwabapi.util.*;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * Instance-based Schwab OAuth 2.0 client with configurable endpoints
 * Refactored to use utility package for common operations
 */
public class SchwabOAuthClient implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(SchwabOAuthClient.class);

    private final SchwabApiProperties properties;
    private final String authUrl;
    private final String tokenUrl;
    private final String marketDataUrl;
    private final String defaultRedirectUri;
    private final int timeoutMs;
    private final OkHttpClient httpClient;

    /**
     * Default constructor - loads configuration from application.yml
     */
    public SchwabOAuthClient() {
        this(loadDefaultProperties(), false);
    }

    /**
     * Constructor with logging option - loads configuration from application.yml
     */
    public SchwabOAuthClient(boolean enableLogging) {
        this(loadDefaultProperties(), enableLogging);
    }

    /**
     * Constructor with explicit configuration
     */
    public SchwabOAuthClient(SchwabApiProperties properties) {
        this(properties, false);
    }

    /**
     * Constructor with explicit configuration and logging option
     */
    public SchwabOAuthClient(SchwabApiProperties properties, boolean enableLogging) {
        UtilityClass.validateNotNull(properties, "SchwabApiProperties");

        this.properties = properties;
        this.authUrl = StringUtils.validateUrl(properties.getAuthUrl(), "Auth URL");
        this.tokenUrl = StringUtils.validateUrl(properties.getTokenUrl(), "Token URL");
        this.marketDataUrl = StringUtils.validateUrl(properties.getMarketDataUrl(), "Market Data URL");
        this.defaultRedirectUri = properties.getDefaultRedirectUri();
        this.timeoutMs = properties.getHttpTimeoutMs();

        this.httpClient = HttpUtils.buildHttpClient(timeoutMs, enableLogging);

        logger.debug("SchwabOAuthClient initialized with authUrl: {}, tokenUrl: {}, marketDataUrl: {}",
                authUrl, tokenUrl, marketDataUrl);
    }

    private static SchwabApiProperties loadDefaultProperties() {
        try {
            return new SchwabApiProperties();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load default Schwab API properties from application.yml", e);
        }
    }

    /**
     * Build authorization URL with default scope
     */
    public String buildAuthorizationUrl(String clientId, String redirectUri) {
        return buildAuthorizationUrl(clientId, redirectUri, null);
    }

    /**
     * Build authorization URL with custom scope
     */
    public String buildAuthorizationUrl(String clientId, String redirectUri, String scope) {
        UtilityClass.validateParameter(clientId, "Client ID");

        String uri = StringUtils.hasContent(redirectUri) ? redirectUri : defaultRedirectUri;
        String scopeParam = StringUtils.hasContent(scope) ? scope : properties.getDefaultScope();

        return String.format("%s?response_type=code&client_id=%s&redirect_uri=%s&scope=%s",
                authUrl, 
                clientId,
                StringUtils.urlEncode(uri),
                StringUtils.urlEncode(scopeParam));
    }

    /**
     * Extract and decode authorization code from redirect URL
     */
    public String extractAuthorizationCode(String redirectUrl) {
        return StringUtils.extractAuthorizationCode(redirectUrl);
    }

    /**
     * Exchange redirect URL for tokens (automatically extracts and decodes authorization code)
     */
    public TokenResponse getTokensFromRedirectUrl(String clientId, String clientSecret,
                                                String redirectUrl, String redirectUri) throws Exception {
        String authCode = extractAuthorizationCode(redirectUrl);
        return getTokens(clientId, clientSecret, authCode, redirectUri);
    }

    /**
     * Exchange authorization code for tokens
     */
    public TokenResponse getTokens(String clientId, String clientSecret, String authCode, String redirectUri) 
            throws Exception {
        
        UtilityClass.logMethodEntry("SchwabOAuthClient", "getTokens", clientId, "***", "***", redirectUri);
        
        UtilityClass.validateParameter(clientId, "Client ID");
        UtilityClass.validateParameter(clientSecret, "Client secret");
        UtilityClass.validateParameter(authCode, "Authorization code");

        String uri = StringUtils.hasContent(redirectUri) ? redirectUri : defaultRedirectUri;

        RequestBody formBody = new FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", authCode)
                .add("redirect_uri", uri)
                .build();

        String basicAuth = HttpUtils.createBasicAuthHeader(clientId, clientSecret);

        Request request = new Request.Builder()
                .url(tokenUrl)
                .header("Authorization", "Basic " + basicAuth)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .post(formBody)
                .build();

        ApiResponse response = callApi(request);

        if (!HttpUtils.isSuccessCode(response.getStatusCode())) {
            throw new IOException(UtilityClass.buildErrorMessage("get tokens", 
                "HTTP " + response.getStatusCode() + ": " + response.getBody()));
        }

        TokenResponse tokens = UtilityClass.getObjectMapper().readValue(response.getBody(), TokenResponse.class);

        // Add debug logging
        logger.debug("Token response: access_token present: {}", tokens.getAccessToken() != null);
        logger.debug("Token response: refresh_token present: {}", tokens.getRefreshToken() != null);
        logger.debug("Token response: expires_in: {}", tokens.getExpiresIn());
        logger.debug("Token response: refresh_token_expires_in: {}", tokens.getRefreshTokenExpiresIn());

        logger.info("Raw token response: {}", response.getBody());

        // Set expiration times using utility functions
        if (tokens.getExpiresIn() > 0) {
            tokens.setExpiresAt(Instant.now().plusSeconds(tokens.getExpiresIn()));
        }
        
        if (tokens.getRefreshTokenExpiresIn() > 0) {
            tokens.setRefreshTokenExpiresAt(Instant.now().plusSeconds(tokens.getRefreshTokenExpiresIn()));
        } else {
            // Schwab might not return refresh_token_expires_in, set a default (7 days typical)
            logger.debug("No refresh_token_expires_in in response, setting default of 7 days. Normal behavior for schwab.");
            tokens.setRefreshTokenExpiresAt(Instant.now().plusSeconds(7 * 24 * 60 * 60));
        }

        tokens.setSource(TokenResponse.TokenSource.AUTHORIZATION_CODE);
        tokens.setIssuedAt(UtilityClass.now());

        UtilityClass.logMethodExit("SchwabOAuthClient", "getTokens", "TokenResponse[valid=" + tokens.isAccessTokenValid() + "]");
        return tokens;
    }

    /**
     * Refresh tokens
     */
    public TokenResponse refreshTokens(String clientId, String clientSecret, String refreshToken) throws Exception {
        UtilityClass.logMethodEntry("SchwabOAuthClient", "refreshTokens", clientId, "***", "***");
        
        UtilityClass.validateParameter(clientId, "Client ID");
        UtilityClass.validateParameter(clientSecret, "Client secret");
        UtilityClass.validateParameter(refreshToken, "Refresh token");

        RequestBody formBody = new FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .build();

        String basicAuth = HttpUtils.createBasicAuthHeader(clientId, clientSecret);

        Request request = new Request.Builder()
                .url(tokenUrl)
                .header("Authorization", "Basic " + basicAuth)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .post(formBody)
                .build();

        ApiResponse response = callApi(request);

        if (!HttpUtils.isSuccessCode(response.getStatusCode())) {
            throw new IOException(UtilityClass.buildErrorMessage("refresh tokens", 
                "HTTP " + response.getStatusCode() + ": " + response.getBody()));
        }

        TokenResponse tokens = UtilityClass.getObjectMapper().readValue(response.getBody(), TokenResponse.class);

        if (tokens.getExpiresIn() > 0) {
            tokens.setExpiresAt(Instant.now().plusSeconds(tokens.getExpiresIn()));
        }
        
        if (tokens.getRefreshTokenExpiresIn() > 0) {
            tokens.setRefreshTokenExpiresAt(Instant.now().plusSeconds(tokens.getRefreshTokenExpiresIn()));
        } else {
            logger.warn("No refresh_token_expires_in in refresh response, setting default of 7 days from now");
            tokens.setRefreshTokenExpiresAt(Instant.now().plusSeconds(7 * 24 * 60 * 60));
        }

        tokens.setSource(TokenResponse.TokenSource.REFRESH_TOKEN);
        tokens.setIssuedAt(UtilityClass.now());

        UtilityClass.logMethodExit("SchwabOAuthClient", "refreshTokens", "TokenResponse[valid=" + tokens.isAccessTokenValid() + "]");
        return tokens;
    }

    /**
     * Get quotes
     */
    public ApiResponse getQuotes(String[] symbols, String accessToken) throws IOException {
        UtilityClass.validateNotNull(symbols, "Symbols array");
        if (symbols.length == 0) {
            throw new IllegalArgumentException("Symbols array cannot be empty");
        }
        UtilityClass.validateParameter(accessToken, "Access token");

        String symbolsParam = String.join(",", symbols);
        String url = String.format("%s/quotes?symbols=%s", marketDataUrl, 
                StringUtils.urlEncode(symbolsParam));
        
        return callApiWithAuth(url, "GET", null, accessToken);
    }

    /**
     * Get price history
     */
    public ApiResponse getPriceHistory(String symbol, String periodType, int period,
                                     String frequencyType, int frequency, String accessToken) throws IOException {
        UtilityClass.validateParameter(symbol, "Symbol");
        UtilityClass.validateParameter(accessToken, "Access token");

        String url = String.format(
                "%s/pricehistory?symbol=%s&periodType=%s&period=%d&frequencyType=%s&frequency=%d&needPreviousClose=true",
                marketDataUrl, symbol, periodType, period, frequencyType, frequency);

        return callApiWithAuth(url, "GET", null, accessToken);
    }

    /**
     * Get market hours
     */
    public ApiResponse getMarketHours(String marketType, String accessToken) throws IOException {
        UtilityClass.validateParameter(marketType, "Market type");
        UtilityClass.validateParameter(accessToken, "Access token");

        String url = String.format("%s/market/%s/hours", marketDataUrl, marketType);
        return callApiWithAuth(url, "GET", null, accessToken);
    }

    /**
     * Get instruments
     */
    public ApiResponse getInstruments(String searchTerm, String projection, String accessToken) throws IOException {
        UtilityClass.validateParameter(searchTerm, "Search term");
        UtilityClass.validateParameter(accessToken, "Access token");

        String proj = StringUtils.hasContent(projection) ? projection : "symbol-search";
        String url = String.format("%s/instruments?symbol=%s&projection=%s", marketDataUrl,
                StringUtils.urlEncode(searchTerm), StringUtils.urlEncode(proj));
                
        return callApiWithAuth(url, "GET", null, accessToken);
    }

    // Getters for configuration (useful for debugging)
    public String getAuthUrl() { return authUrl; }
    public String getTokenUrl() { return tokenUrl; }
    public String getMarketDataUrl() { return marketDataUrl; }
    public String getDefaultRedirectUri() { return defaultRedirectUri; }
    public String getDefaultScope() { return properties.getDefaultScope(); }
    public int getTimeoutMs() { return timeoutMs; }

    // Helper methods using utilities
    private ApiResponse callApi(Request request) throws IOException {
        UtilityClass.MetricsContext metrics = UtilityClass.createMetricsContext("HTTP Request");
        
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = "";
            ResponseBody body = response.body();
            if (body != null) {
                responseBody = body.string();
            }

            Map<String, String> headers = HttpUtils.headersToSingleValueMap(response.headers());
            
            metrics.stop("completed");
            return new ApiResponse(response.code(), responseBody, headers, metrics.getElapsedMillis());
        }
    }

    private ApiResponse callApiWithAuth(String url, String method, RequestBody body, String accessToken) 
            throws IOException {
        
        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .method(method, body);

        if (StringUtils.hasContent(accessToken)) {
            requestBuilder.header("Authorization", "Bearer " + HttpUtils.createBearerAuthHeader(accessToken));
        }

        return callApi(requestBuilder.build());
    }

    @Override
    public void close() {
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        }
        logger.debug("SchwabOAuthClient closed");
    }
}