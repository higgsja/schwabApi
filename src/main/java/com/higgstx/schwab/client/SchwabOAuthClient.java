package com.higgstx.schwab.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.higgstx.schwab.config.SchwabConfig;
import com.higgstx.schwab.model.ApiResponse;
import com.higgstx.schwab.model.TokenResponse;
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

/**
 * Client for interacting with the Schwab OAuth 2.0 and API endpoints.
 */
public class SchwabOAuthClient implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(SchwabOAuthClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    private final OkHttpClient httpClient;

    public SchwabOAuthClient() {
        this(false);
    }

    public SchwabOAuthClient(boolean enableLogging) {
        try {
            SchwabConfig.validateConfig();
        } catch (IllegalStateException e) {
            logger.error("❌ SchwabOAuthClient configuration error: {}", e.getMessage());
            throw new RuntimeException(e);
        }

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(SchwabConfig.HTTP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(SchwabConfig.HTTP_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        if (enableLogging) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor(message -> logger.debug(message));
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);
            builder.addInterceptor(logging);
        }

        this.httpClient = builder.build();
    }

    /**
     * Initiates the OAuth 2.0 authorization flow by building the URL.
     */
    public String getAuthorizationUrl() {
        return String.format("%s?response_type=code&client_id=%s&redirect_uri=%s",
                SchwabConfig.AUTH_URL, SchwabConfig.CLIENT_ID, URLEncoder.encode(SchwabConfig.REDIRECT_URI, StandardCharsets.UTF_8));
    }

    /**
     * Exchanges an authorization code for an access token and refresh token.
     */
    public TokenResponse getTokens(String authCode) throws Exception {
        RequestBody formBody = new FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", authCode)
                .add("redirect_uri", SchwabConfig.REDIRECT_URI)
                .build();

        Request request = new Request.Builder()
                .url(SchwabConfig.TOKEN_URL)
                .header("Authorization", "Basic " + getBasicAuthHeader())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .post(formBody)
                .build();

        ApiResponse response = callApi(request, false);

        if (response.getStatusCode() != 200) {
            throw new IOException("Failed to get tokens. HTTP " + response.getStatusCode() + ": " + response.getBody());
        }

        TokenResponse tokens = objectMapper.readValue(response.getBody(), TokenResponse.class);
        tokens.setExpiresAt(Instant.now().plusSeconds(tokens.getExpiresIn()));
        tokens.setRefreshTokenExpiresAt(Instant.now().plusSeconds(tokens.getRefreshTokenExpiresIn()));
        return tokens;
    }

    /**
     * Refreshes an access token using a refresh token.
     */
    public TokenResponse refreshTokens(String refreshToken) throws Exception {
        RequestBody formBody = new FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .build();

        Request request = new Request.Builder()
                .url(SchwabConfig.TOKEN_URL)
                .header("Authorization", "Basic " + getBasicAuthHeader())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .post(formBody)
                .build();

        ApiResponse response = callApi(request, false);

        if (response.getStatusCode() != 200) {
            throw new IOException("Failed to refresh tokens. HTTP " + response.getStatusCode() + ": " + response.getBody());
        }

        TokenResponse tokens = objectMapper.readValue(response.getBody(), TokenResponse.class);
        tokens.setExpiresAt(Instant.now().plusSeconds(tokens.getExpiresIn()));
        tokens.setRefreshTokenExpiresAt(Instant.now().plusSeconds(tokens.getRefreshTokenExpiresIn()));
        return tokens;
    }
    
    /**
     * Gets quotes for a single symbol (string parameter)
     */
    public ApiResponse getQuotes(String symbol, String accessToken) throws IOException {
        return getQuotes(new String[]{symbol}, accessToken);
    }

    /**
     * Gets quotes for a list of symbols
     */
    public ApiResponse getQuotes(String[] symbols, String accessToken) throws IOException {
        String url = String.format("%s/quotes?symbols=%s", SchwabConfig.MARKET_DATA_URL,
                URLEncoder.encode(String.join(",", symbols), StandardCharsets.UTF_8));
        return callApi(url, "GET", null, accessToken);
    }

    /**
     * Gets price history for a symbol
     */
    public ApiResponse getPriceHistory(String symbol, int period, String periodType,
                                     int frequency, String frequencyType, String accessToken) throws IOException {
        String url = String.format("%s/%s/pricehistory?periodType=%s&period=%d&frequencyType=%s&frequency=%d",
                SchwabConfig.MARKET_DATA_URL, symbol, periodType, period, frequencyType, frequency);
        return callApi(url, "GET", null, accessToken);
    }

    /**
     * Gets market hours for a market type
     */
    public ApiResponse getMarketHours(String marketType, String accessToken) throws IOException {
        String url = String.format("%s/market/%s/hours", SchwabConfig.MARKET_DATA_URL, marketType);
        return callApi(url, "GET", null, accessToken);
    }

    private ApiResponse callApi(Request request, boolean needsAuth) throws IOException {
        long startTime = System.currentTimeMillis();
        try (Response response = httpClient.newCall(request).execute()) {
            long responseTime = System.currentTimeMillis() - startTime;
            String responseBody = response.body() != null ? response.body().string() : "";
            
            // Correctly convert the multimap headers to a single-value map
            Map<String, String> singleValueHeaders = response.headers().toMultimap().entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().get(0), (v1, v2) -> v1));

            return new ApiResponse(response.code(), responseBody, singleValueHeaders, responseTime);
        }
    }

    private ApiResponse callApi(String url, String method, RequestBody body, String accessToken) throws IOException {
        Request.Builder requestBuilder = new Request.Builder().url(url).method(method, body);

        if (accessToken != null && !accessToken.isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + accessToken);
        }

        long startTime = System.currentTimeMillis();
        try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
            long responseTime = System.currentTimeMillis() - startTime;
            String responseBody = response.body() != null ? response.body().string() : "";

            // Correctly convert the multimap headers to a single-value map
            Map<String, String> singleValueHeaders = response.headers().toMultimap().entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().get(0), (v1, v2) -> v1));
            
            return new ApiResponse(response.code(), responseBody, singleValueHeaders, responseTime);
        }
    }

    private String getBasicAuthHeader() {
        String credentials = SchwabConfig.CLIENT_ID + ":" + SchwabConfig.CLIENT_SECRET;
        return Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Refreshes access token using refresh token (alias method for compatibility)
     */
    public TokenResponse refreshAccessToken(String refreshToken) throws Exception {
        return refreshTokens(refreshToken);
    }
    
     /**
     * Gets instruments by symbol or description search
     */
    public ApiResponse getInstruments(String searchTerm, String projection, String accessToken) throws IOException {
        String url = String.format("%s/instruments?symbol=%s&projection=%s", 
            SchwabConfig.MARKET_DATA_URL,
            URLEncoder.encode(searchTerm, StandardCharsets.UTF_8),
            URLEncoder.encode(projection, StandardCharsets.UTF_8));
        return callApi(url, "GET", null, accessToken);
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