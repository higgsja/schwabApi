package com.higgstx.schwabapi.service;

import com.higgstx.schwabapi.util.*;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Essential HttpUtils functionality tests
 */
@DisplayName("HttpUtils Core Tests")
class SimplifiedHttpUtilsTest {

    @Test
    @DisplayName("HTTP client should be created with specified timeout")
    void testHttpClientCreation() {
        OkHttpClient client = HttpUtils.buildHttpClient(5000, false);
        
        assertNotNull(client);
        assertEquals(5000, client.connectTimeoutMillis());
        assertEquals(5000, client.readTimeoutMillis());
        assertEquals(5000, client.writeTimeoutMillis());
    }

    @Test
    @DisplayName("HTTP client should include logging when enabled")
    void testHttpClientWithLogging() {
        OkHttpClient client = HttpUtils.buildHttpClient(true);
        
        assertNotNull(client);
        assertTrue(client.interceptors().size() > 0);
        assertEquals(30000, client.connectTimeoutMillis()); // Default timeout
    }

    @Test
    @DisplayName("Headers should be converted to map correctly")
    void testHeadersConversion() {
        Headers headers = new Headers.Builder()
                .add("Content-Type", "application/json")
                .add("Authorization", "Bearer token")
                .build();

        Map<String, String> result = HttpUtils.headersToSingleValueMap(headers);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("application/json", result.get("Content-Type"));
        assertEquals("Bearer token", result.get("Authorization"));
    }

    @Test
    @DisplayName("Empty headers should return empty map")
    void testEmptyHeaders() {
        Headers headers = new Headers.Builder().build();
        Map<String, String> result = HttpUtils.headersToSingleValueMap(headers);
        
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 201, 204, 299})
    @DisplayName("Success status codes should be identified correctly")
    void testSuccessStatusCodes(int statusCode) {
        assertTrue(HttpUtils.isSuccessCode(statusCode));
        assertFalse(HttpUtils.isClientError(statusCode));
        assertFalse(HttpUtils.isServerError(statusCode));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 429})
    @DisplayName("Client error status codes should be identified correctly")
    void testClientErrorStatusCodes(int statusCode) {
        assertFalse(HttpUtils.isSuccessCode(statusCode));
        assertTrue(HttpUtils.isClientError(statusCode));
        assertFalse(HttpUtils.isServerError(statusCode));
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 504})
    @DisplayName("Server error status codes should be identified correctly")
    void testServerErrorStatusCodes(int statusCode) {
        assertFalse(HttpUtils.isSuccessCode(statusCode));
        assertFalse(HttpUtils.isClientError(statusCode));
        assertTrue(HttpUtils.isServerError(statusCode));
        assertTrue(HttpUtils.isRetryableStatusCode(statusCode)); // All server errors are retryable
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    @DisplayName("Auth error status codes should be identified correctly")
    void testAuthErrorStatusCodes(int statusCode) {
        assertTrue(HttpUtils.isAuthError(statusCode));
    }

    @ParameterizedTest
    @ValueSource(ints = {408, 429, 500, 503})
    @DisplayName("Retryable status codes should be identified correctly")
    void testRetryableStatusCodes(int statusCode) {
        assertTrue(HttpUtils.isRetryableStatusCode(statusCode));
    }

    @Test
    @DisplayName("Basic auth header should encode credentials correctly")
    void testBasicAuthHeader() {
        String authHeader = HttpUtils.createBasicAuthHeader("user", "pass");
        
        assertNotNull(authHeader);
        String decoded = new String(java.util.Base64.getDecoder().decode(authHeader));
        assertEquals("user:pass", decoded);
    }

    @Test
    @DisplayName("Bearer auth header should return token unchanged")
    void testBearerAuthHeader() {
        String token = "test-token-123";
        String result = HttpUtils.createBearerAuthHeader(token);
        assertEquals(token, result);
    }

    @Test
    @DisplayName("Retryable exceptions should be identified correctly")
    void testRetryableExceptions() {
        assertTrue(HttpUtils.isRetryableException(new java.net.SocketTimeoutException()));
        assertTrue(HttpUtils.isRetryableException(new java.net.ConnectException()));
        assertTrue(HttpUtils.isRetryableException(new RuntimeException("connection timeout")));
        
        assertFalse(HttpUtils.isRetryableException(new IllegalArgumentException()));
        assertFalse(HttpUtils.isRetryableException(null));
    }

    @Test
    @DisplayName("SSL context should be created successfully")
    void testSSLContextCreation() {
        assertDoesNotThrow(() -> {
            var sslContext = HttpUtils.createTrustAllSSLContext();
            assertNotNull(sslContext);
            assertEquals("TLS", sslContext.getProtocol());
        });
    }

    @Test
    @DisplayName("Trust all manager should accept any certificate")
    void testTrustAllManager() {
        HttpUtils.TrustAllX509TrustManager trustManager = new HttpUtils.TrustAllX509TrustManager();
        
        assertDoesNotThrow(() -> {
            trustManager.checkClientTrusted(null, "RSA");
            trustManager.checkServerTrusted(null, "RSA");
        });
        
        assertEquals(0, trustManager.getAcceptedIssuers().length);
    }
}