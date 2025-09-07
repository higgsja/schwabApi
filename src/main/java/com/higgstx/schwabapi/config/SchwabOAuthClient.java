package com.higgstx.schwabapi.config;

import com.higgstx.schwabapi.exception.SchwabApiException;
import com.higgstx.schwabapi.model.ApiResponse;
import com.higgstx.schwabapi.model.TokenResponse;
import com.higgstx.schwabapi.util.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * Schwab OAuth 2.0 client - Spring-managed configuration only
 */
@Slf4j
@Getter
public class SchwabOAuthClient implements AutoCloseable {

    private final SchwabApiProperties properties;
    private final String authUrl;
    private final String tokenUrl;
    private final String marketDataUrl;
    private final String defaultRedirectUri;
    private final int timeoutMs;
    private final OkHttpClient httpClient;

    /**
     * Constructor with explicit configuration (Spring will provide this)
     */
    public SchwabOAuthClient(SchwabApiProperties properties) throws SchwabApiException {
        this(properties, false);
    }

    /**
     * Constructor with explicit configuration and logging option
     */
    public SchwabOAuthClient(SchwabApiProperties properties, boolean enableLogging) throws SchwabApiException {
        UtilityClass.validateNotNull(properties, "SchwabApiProperties");

        this.properties = properties;
        
        try {
            this.authUrl = StringUtils.validateRequired(properties.getAuthUrl(), "Auth URL");
            this.tokenUrl = StringUtils.validateRequired(properties.getTokenUrl(), "Token URL");
            this.marketDataUrl = StringUtils.validateRequired(properties.getMarketDataUrl(), "Market Data URL");
            this.defaultRedirectUri = properties.getDefaultRedirectUri();
            this.timeoutMs = properties.getHttpTimeoutMs();

            this.httpClient = HttpUtils.buildHttpClient(timeoutMs, enableLogging);

            log.debug("SchwabOAuthClient initialized with authUrl: {}, tokenUrl: {}, marketDataUrl: {}",
                    authUrl, tokenUrl, marketDataUrl);
        } catch (Exception e) {
            throw SchwabApiException.configurationError("Failed to initialize OAuth client: " + e.getMessage());
        }
    }

    /**
     * Build authorization URL with default scope
     */
    public String buildAuthorizationUrl(String clientId, String redirectUri) throws SchwabApiException {
        return buildAuthorizationUrl(clientId, redirectUri, null);
    }

    /**
     * Build authorization URL with custom scope
     */
    public String buildAuthorizationUrl(String clientId, String redirectUri, String scope) throws SchwabApiException {
        try {
            UtilityClass.validateParameter(clientId, "Client ID");

            String uri = StringUtils.hasContent(redirectUri) ? redirectUri : defaultRedirectUri;
            String scopeParam = StringUtils.hasContent(scope) ? scope : properties.getDefaultScope();

            return String.format("%s?response_type=code&client_id=%s&redirect_uri=%s&scope=%s",
                    authUrl, 
                    clientId,
                    StringUtils.urlEncode(uri),
                    StringUtils.urlEncode(scopeParam));
        } catch (Exception e) {
            throw SchwabApiException.validationError("Failed to build authorization URL: " + e.getMessage());
        }
    }

    /**
     * Extract and decode authorization code from redirect URL
     */
    public String extractAuthorizationCode(String redirectUrl) throws SchwabApiException {
        try {
            return StringUtils.extractAuthorizationCode(redirectUrl);
        } catch (Exception e) {
            throw SchwabApiException.validationError("Failed to extract authorization code: " + e.getMessage());
        }
    }

    /**
     * Exchange redirect URL for tokens (automatically extracts and decodes authorization code)
     */
    public TokenResponse getTokensFromRedirectUrl(String clientId, String clientSecret,
                                                String redirectUrl, String redirectUri) throws SchwabApiException {
        String authCode = extractAuthorizationCode(redirectUrl);
        return getTokens(clientId, clientSecret, authCode, redirectUri);
    }

    /**
     * Exchange authorization code for tokens
     */
    public TokenResponse getTokens(String clientId, String clientSecret, String authCode, String redirectUri) 
            throws SchwabApiException {
        
        log.debug("Exchanging authorization code for tokens");
        
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

        try {
            long startTime = System.currentTimeMillis();
            ApiResponse response = callApi(request);
            long responseTime = System.currentTimeMillis() - startTime;

            if (!HttpUtils.isSuccessCode(response.getStatusCode())) {
                throw SchwabApiException.fromApiResponse("exchange authorization code for tokens", response);
            }

            TokenResponse tokens = UtilityClass.getObjectMapper().readValue(response.getBody(), TokenResponse.class);

            log.debug("Token response: access_token present: {}", tokens.getAccessToken() != null);
            log.debug("Token response: refresh_token present: {}", tokens.getRefreshToken() != null);
            log.debug("Token response: expires_in: {}", tokens.getExpiresIn());
            log.debug("Token response: refresh_token_expires_in: {}", tokens.getRefreshTokenExpiresIn());

            log.info("Raw token response: {}", response.getBody());

            // Set expiration times
            if (tokens.getExpiresIn() > 0) {
                tokens.setExpiresAt(Instant.now().plusSeconds(tokens.getExpiresIn()));
            }
            
            if (tokens.getRefreshTokenExpiresIn() > 0) {
                tokens.setRefreshTokenExpiresAt(Instant.now().plusSeconds(tokens.getRefreshTokenExpiresIn()));
            } else {
                log.debug("No refresh_token_expires_in in response, setting default of 7 days. Normal behavior for schwab.");
                tokens.setRefreshTokenExpiresAt(Instant.now().plusSeconds(7 * 24 * 60 * 60));
            }

            tokens.setSource(TokenResponse.TokenSource.AUTHORIZATION_CODE);
            tokens.setIssuedAt(UtilityClass.now());

            log.debug("Token exchange completed successfully in {}ms", responseTime);
            return tokens;
            
        } catch (SchwabApiException e) {
            throw e; // Re-throw SchwabApiException as-is
        } catch (IOException e) {
            throw SchwabApiException.networkError("exchange authorization code for tokens", e);
        } catch (Exception e) {
            throw SchwabApiException.serverError("Failed to process token response: " + e.getMessage());
        }
    }

    /**
     * Refresh tokens
     */
    public TokenResponse refreshTokens(String clientId, String clientSecret, String refreshToken) throws SchwabApiException {
        log.debug("Refreshing tokens");
        
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

        try {
            long startTime = System.currentTimeMillis();
            ApiResponse response = callApi(request);
            long responseTime = System.currentTimeMillis() - startTime;

            if (!HttpUtils.isSuccessCode(response.getStatusCode())) {
                throw SchwabApiException.fromApiResponse("refresh tokens", response);
            }

            TokenResponse tokens = UtilityClass.getObjectMapper().readValue(response.getBody(), TokenResponse.class);

            if (tokens.getExpiresIn() > 0) {
                tokens.setExpiresAt(Instant.now().plusSeconds(tokens.getExpiresIn()));
            }
            
            if (tokens.getRefreshTokenExpiresIn() > 0) {
                tokens.setRefreshTokenExpiresAt(Instant.now().plusSeconds(tokens.getRefreshTokenExpiresIn()));
            } else {
                log.warn("No refresh_token_expires_in in refresh response, setting default of 7 days from now");
                tokens.setRefreshTokenExpiresAt(Instant.now().plusSeconds(7 * 24 * 60 * 60));
            }

            tokens.setSource(TokenResponse.TokenSource.REFRESH_TOKEN);
            tokens.setIssuedAt(UtilityClass.now());

            log.debug("Token refresh completed successfully in {}ms", responseTime);
            return tokens;
            
        } catch (SchwabApiException e) {
            throw e; // Re-throw SchwabApiException as-is
        } catch (IOException e) {
            throw SchwabApiException.networkError("refresh tokens", e);
        } catch (Exception e) {
            throw SchwabApiException.serverError("Failed to refresh tokens: " + e.getMessage());
        }
    }

    /**
     * Get quotes
     */
    public ApiResponse getQuotes(String[] symbols, String accessToken) throws SchwabApiException {
        UtilityClass.validateNotNull(symbols, "Symbols array");
        if (symbols.length == 0) {
            throw SchwabApiException.validationError("Symbols array cannot be empty");
        }
        UtilityClass.validateParameter(accessToken, "Access token");

        String symbolsParam = String.join(",", symbols);
        String url = String.format("%s/quotes?symbols=%s", marketDataUrl, 
                StringUtils.urlEncode(symbolsParam));
        
        try {
            return callApiWithAuth(url, "GET", null, accessToken);
        } catch (IOException e) {
            throw SchwabApiException.networkError("get quotes for symbols: " + symbolsParam, e);
        }
    }

    /**
     * Get price history
     */
    public ApiResponse getPriceHistory(String symbol, String periodType, int period,
                                     String frequencyType, int frequency, String accessToken) throws SchwabApiException {
        UtilityClass.validateParameter(symbol, "Symbol");
        UtilityClass.validateParameter(accessToken, "Access token");

        String url = String.format(
                "%s/pricehistory?symbol=%s&periodType=%s&period=%d&frequencyType=%s&frequency=%d&needPreviousClose=true",
                marketDataUrl, symbol, periodType, period, frequencyType, frequency);

        try {
            return callApiWithAuth(url, "GET", null, accessToken);
        } catch (IOException e) {
            throw SchwabApiException.networkError("get price history for symbol: " + symbol, e);
        }
    }

    /**
     * Get market hours
     */
    public ApiResponse getMarketHours(String marketType, String accessToken) throws SchwabApiException {
        UtilityClass.validateParameter(marketType, "Market type");
        UtilityClass.validateParameter(accessToken, "Access token");

        String url = String.format("%s/market/%s/hours", marketDataUrl, marketType);
        
        try {
            return callApiWithAuth(url, "GET", null, accessToken);
        } catch (IOException e) {
            throw SchwabApiException.networkError("get market hours for type: " + marketType, e);
        }
    }

    /**
     * Get instruments
     */
    public ApiResponse getInstruments(String searchTerm, String projection, String accessToken) throws SchwabApiException {
        UtilityClass.validateParameter(searchTerm, "Search term");
        UtilityClass.validateParameter(accessToken, "Access token");

        String proj = StringUtils.hasContent(projection) ? projection : "symbol-search";
        String url = String.format("%s/instruments?symbol=%s&projection=%s", marketDataUrl,
                StringUtils.urlEncode(searchTerm), StringUtils.urlEncode(proj));
                
        try {
            return callApiWithAuth(url, "GET", null, accessToken);
        } catch (IOException e) {
            throw SchwabApiException.networkError("get instruments for search term: " + searchTerm, e);
        }
    }

    // Helper methods
    private ApiResponse callApi(Request request) throws IOException, SchwabApiException {
        long startTime = System.currentTimeMillis();
        
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = "";
            ResponseBody body = response.body();
            if (body != null) {
                responseBody = body.string();
            }

            Map<String, String> headers = HttpUtils.headersToSingleValueMap(response.headers());
            long responseTime = System.currentTimeMillis() - startTime;
            
            log.debug("API call completed in {}ms", responseTime);
            return new ApiResponse(response.code(), responseBody, headers, responseTime);
        }
    }

    private ApiResponse callApiWithAuth(String url, String method, RequestBody body, String accessToken) 
            throws IOException, SchwabApiException {
        
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
        log.debug("SchwabOAuthClient closed");
    }
}