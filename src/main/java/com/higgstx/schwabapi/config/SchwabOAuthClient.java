package com.higgstx.schwabapi.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.higgstx.schwabapi.model.ApiResponse;
import com.higgstx.schwabapi.model.TokenResponse;
import okhttp3.*;
import okhttp3.logging.HttpLoggingInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.net.URLDecoder;


/**
 * Instance-based Schwab OAuth 2.0 client with configurable endpoints
 */
public class SchwabOAuthClient implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(SchwabOAuthClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

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
        if (properties == null) {
            throw new IllegalArgumentException("SchwabApiProperties cannot be null");
        }

        this.properties = properties;
        this.authUrl = validateUrl(properties.getAuthUrl(), "Auth URL");
        this.tokenUrl = validateUrl(properties.getTokenUrl(), "Token URL");
        this.marketDataUrl = validateUrl(properties.getMarketDataUrl(), "Market Data URL");
        this.defaultRedirectUri = properties.getDefaultRedirectUri();
        this.timeoutMs = properties.getHttpTimeoutMs();

        this.httpClient = buildHttpClient(enableLogging);
        
        logger.debug("SchwabOAuthClient initialized with authUrl: {}, tokenUrl: {}, marketDataUrl: {}", 
                authUrl, tokenUrl, marketDataUrl);
    }

//    /**
//     * Constructor with explicit URLs (for testing or custom configurations)
//     */
//    public SchwabOAuthClient(String authUrl, String tokenUrl, String marketDataUrl, 
//                           String defaultRedirectUri, int timeoutMs, boolean enableLogging) {
//        // Create properties object for consistency
//        this.properties = new SchwabApiProperties(authUrl, tokenUrl, marketDataUrl, defaultRedirectUri, timeoutMs);
//        this.authUrl = validateUrl(authUrl, "Auth URL");
//        this.tokenUrl = validateUrl(tokenUrl, "Token URL");
//        this.marketDataUrl = validateUrl(marketDataUrl, "Market Data URL");
//        this.defaultRedirectUri = defaultRedirectUri != null ? defaultRedirectUri : "http://127.0.0.1";
//        this.timeoutMs = timeoutMs > 0 ? timeoutMs : 30000;
//
//        this.httpClient = buildHttpClient(enableLogging);
//    }

    private static SchwabApiProperties loadDefaultProperties() {
        try {
            return new SchwabApiProperties();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load default Schwab API properties from application.yml", e);
        }
    }

    private String validateUrl(String url, String name) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be null or empty");
        }
        return url.trim();
    }

    private OkHttpClient buildHttpClient(boolean enableLogging) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS);

        if (enableLogging) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor(message -> logger.debug(message));
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);
            builder.addInterceptor(logging);
        }

        return builder.build();
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
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalArgumentException("Client ID cannot be null or empty");
        }

        String uri = redirectUri != null ? redirectUri : defaultRedirectUri;
        String scopeParam = scope != null ? scope : properties.getDefaultScope();
        
        return String.format("%s?response_type=code&client_id=%s&redirect_uri=%s&scope=%s",
                authUrl, clientId, 
                URLEncoder.encode(uri, StandardCharsets.UTF_8),
                URLEncoder.encode(scopeParam, StandardCharsets.UTF_8));
    }
    
    /**
 * Extract and decode authorization code from redirect URL
 */
public String extractAuthorizationCode(String redirectUrl) {
    if (redirectUrl == null || redirectUrl.isEmpty()) {
        throw new IllegalArgumentException("Redirect URL cannot be null or empty");
    }
    
    try {
        String[] parts = redirectUrl.split("[?&]");
        for (String part : parts) {
            if (part.startsWith("code=")) {
                String code = part.substring(5); // Remove "code=" prefix
                return URLDecoder.decode(code, StandardCharsets.UTF_8);
            }
        }
        throw new IllegalArgumentException("No authorization code found in URL: " + redirectUrl);
    } catch (Exception e) {
        throw new RuntimeException("Failed to extract authorization code from URL", e);
    }
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
    public TokenResponse getTokens(String clientId, String clientSecret, String authCode, String redirectUri) throws Exception {
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalArgumentException("Client ID cannot be null or empty");
        }
        if (clientSecret == null || clientSecret.trim().isEmpty()) {
            throw new IllegalArgumentException("Client secret cannot be null or empty");
        }
        if (authCode == null || authCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Authorization code cannot be null or empty");
        }

        String uri = redirectUri != null ? redirectUri : defaultRedirectUri;

        RequestBody formBody = new FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", authCode)
                .add("redirect_uri", uri)
                .build();

        String basicAuth = Base64.getEncoder().encodeToString(
                (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));

        Request request = new Request.Builder()
                .url(tokenUrl)
                .header("Authorization", "Basic " + basicAuth)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .post(formBody)
                .build();

        ApiResponse response = callApi(request);

        if (response.getStatusCode() != 200) {
            throw new IOException("Failed to get tokens. HTTP " + response.getStatusCode() + ": " + response.getBody());
        }

        TokenResponse tokens = objectMapper.readValue(response.getBody(), TokenResponse.class);

// Add debug logging
logger.debug("Token response: access_token present: {}", tokens.getAccessToken() != null);
logger.debug("Token response: refresh_token present: {}", tokens.getRefreshToken() != null);
logger.debug("Token response: expires_in: {}", tokens.getExpiresIn());
logger.debug("Token response: refresh_token_expires_in: {}", tokens.getRefreshTokenExpiresIn());

logger.info("Raw token response: {}", response.getBody());

if (tokens.getExpiresIn() > 0) {
    tokens.setExpiresAt(Instant.now().plusSeconds(tokens.getExpiresIn()));
}
if (tokens.getRefreshTokenExpiresIn() > 0) {
    tokens.setRefreshTokenExpiresAt(Instant.now().plusSeconds(tokens.getRefreshTokenExpiresIn()));
} else {
    // Schwab might not return refresh_token_expires_in, set a default (7 days typical)
    logger.warn("No refresh_token_expires_in in response, setting default of 7 days");
    tokens.setRefreshTokenExpiresAt(Instant.now().plusSeconds(7 * 24 * 60 * 60));
}

        if (tokens.getExpiresIn() > 0) {
            tokens.setExpiresAt(Instant.now().plusSeconds(tokens.getExpiresIn()));
        }
        if (tokens.getRefreshTokenExpiresIn() > 0) {
            tokens.setRefreshTokenExpiresAt(Instant.now().plusSeconds(tokens.getRefreshTokenExpiresIn()));
        }

        tokens.setSource(TokenResponse.TokenSource.AUTHORIZATION_CODE);
        tokens.setIssuedAt(Instant.now());

        return tokens;
    }

    /**
     * Refresh tokens
     */
    public TokenResponse refreshTokens(String clientId, String clientSecret, String refreshToken) throws Exception {
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalArgumentException("Client ID cannot be null or empty");
        }
        if (clientSecret == null || clientSecret.trim().isEmpty()) {
            throw new IllegalArgumentException("Client secret cannot be null or empty");
        }
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Refresh token cannot be null or empty");
        }

        RequestBody formBody = new FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .build();

        String basicAuth = Base64.getEncoder().encodeToString(
                (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));

        Request request = new Request.Builder()
                .url(tokenUrl)
                .header("Authorization", "Basic " + basicAuth)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .post(formBody)
                .build();

        ApiResponse response = callApi(request);

        if (response.getStatusCode() != 200) {
            throw new IOException("Failed to refresh tokens. HTTP " + response.getStatusCode() + ": " + response.getBody());
        }

        TokenResponse tokens = objectMapper.readValue(response.getBody(), TokenResponse.class);

        if (tokens.getExpiresIn() > 0) {
            tokens.setExpiresAt(Instant.now().plusSeconds(tokens.getExpiresIn()));
        }
        if (tokens.getRefreshTokenExpiresIn() > 0) {
            tokens.setRefreshTokenExpiresAt(Instant.now().plusSeconds(tokens.getRefreshTokenExpiresIn()));
        }

        tokens.setSource(TokenResponse.TokenSource.REFRESH_TOKEN);
        tokens.setIssuedAt(Instant.now());

        return tokens;
    }

    /**
     * Get quotes
     */
    public ApiResponse getQuotes(String[] symbols, String accessToken) throws IOException {
        if (symbols == null || symbols.length == 0) {
            throw new IllegalArgumentException("Symbols array cannot be null or empty");
        }
        if (accessToken == null || accessToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Access token cannot be null or empty");
        }

        String url = String.format("%s/quotes?symbols=%s", marketDataUrl,
                URLEncoder.encode(String.join(",", symbols), StandardCharsets.UTF_8));
        return callApiWithAuth(url, "GET", null, accessToken);
    }

    /**
     * Get price history
     */
    public ApiResponse getPriceHistory(String symbol, int period, String periodType,
                                     int frequency, String frequencyType, String accessToken) throws IOException {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
        if (accessToken == null || accessToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Access token cannot be null or empty");
        }

        String url = String.format("%s/%s/pricehistory?periodType=%s&period=%d&frequencyType=%s&frequency=%d",
                marketDataUrl, symbol, periodType, period, frequencyType, frequency);
        return callApiWithAuth(url, "GET", null, accessToken);
    }

    /**
     * Get market hours
     */
    public ApiResponse getMarketHours(String marketType, String accessToken) throws IOException {
        if (marketType == null || marketType.trim().isEmpty()) {
            throw new IllegalArgumentException("Market type cannot be null or empty");
        }
        if (accessToken == null || accessToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Access token cannot be null or empty");
        }

        String url = String.format("%s/market/%s/hours", marketDataUrl, marketType);
        return callApiWithAuth(url, "GET", null, accessToken);
    }

    /**
     * Get instruments
     */
    public ApiResponse getInstruments(String searchTerm, String projection, String accessToken) throws IOException {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            throw new IllegalArgumentException("Search term cannot be null or empty");
        }
        if (accessToken == null || accessToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Access token cannot be null or empty");
        }

        String url = String.format("%s/instruments?symbol=%s&projection=%s",
                marketDataUrl,
                URLEncoder.encode(searchTerm, StandardCharsets.UTF_8),
                URLEncoder.encode(projection != null ? projection : "symbol-search", StandardCharsets.UTF_8));
        return callApiWithAuth(url, "GET", null, accessToken);
    }

    // Getters for configuration (useful for debugging)
    public String getAuthUrl() { return authUrl; }
    public String getTokenUrl() { return tokenUrl; }
    public String getMarketDataUrl() { return marketDataUrl; }
    public String getDefaultRedirectUri() { return defaultRedirectUri; }
    public String getDefaultScope() { return properties.getDefaultScope(); }
    public int getTimeoutMs() { return timeoutMs; }

    // Helper methods
    private ApiResponse callApi(Request request) throws IOException {
        long startTime = System.currentTimeMillis();
        try (Response response = httpClient.newCall(request).execute()) {
            long responseTime = System.currentTimeMillis() - startTime;

            String responseBody = "";
            ResponseBody body = response.body();
            if (body != null) {
                responseBody = body.string();
            }

            Map<String, String> singleValueHeaders = response.headers().toMultimap().entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().isEmpty() ? "" : entry.getValue().get(0),
                            (v1, v2) -> v1
                    ));

            return new ApiResponse(response.code(), responseBody, singleValueHeaders, responseTime);
        }
    }

    private ApiResponse callApiWithAuth(String url, String method, RequestBody body, String accessToken) throws IOException {
        Request.Builder requestBuilder = new Request.Builder().url(url).method(method, body);

        if (accessToken != null && !accessToken.isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + accessToken);
        }

        long startTime = System.currentTimeMillis();
        try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
            long responseTime = System.currentTimeMillis() - startTime;

            String responseBody = "";
            ResponseBody responseBodyObj = response.body();
            if (responseBodyObj != null) {
                responseBody = responseBodyObj.string();
            }

            Map<String, String> singleValueHeaders = response.headers().toMultimap().entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().isEmpty() ? "" : entry.getValue().get(0),
                            (v1, v2) -> v1
                    ));

            return new ApiResponse(response.code(), responseBody, singleValueHeaders, responseTime);
        }
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