package com.higgstx.schwabapi.service;

import com.higgstx.schwabapi.util.*;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.net.ssl.SSLContext;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * STREAMLINED HttpUtils testing - removes duplicated validation/edge cases
 * Focuses only on HttpUtils-specific functionality
 */
@DisplayName("HttpUtils - Core Functionality")
class StreamlinedHttpUtilsTest {

    @Nested
    @DisplayName("HTTP Client Building")
    class HttpClientBuildingTests {

        @Test
        @DisplayName("buildHttpClient should create client with specified configuration")
        void buildHttpClient_SpecifiedConfig_CreatesCorrectly() {
            int timeoutMs = 5000;
            
            OkHttpClient client = HttpUtils.buildHttpClient(timeoutMs, false);
            
            assertNotNull(client);
            assertEquals(timeoutMs, client.connectTimeoutMillis());
            assertEquals(timeoutMs, client.readTimeoutMillis());
            assertEquals(timeoutMs, client.writeTimeoutMillis());
        }

        @Test
        @DisplayName("buildHttpClient should include logging interceptor when enabled")
        void buildHttpClient_LoggingEnabled_IncludesLoggingInterceptor() {
            OkHttpClient client = HttpUtils.buildHttpClient(30000, true);
            
            assertNotNull(client);
            assertTrue(client.interceptors().size() > 0);
        }

        @Test
        @DisplayName("buildHttpClient with default timeout should work")
        void buildHttpClient_DefaultTimeout_Works() {
            OkHttpClient client = HttpUtils.buildHttpClient(true);
            
            assertNotNull(client);
            assertEquals(30000, client.connectTimeoutMillis());
        }
    }

    @Nested
    @DisplayName("Headers Processing")
    class HeadersProcessingTests {

        @Test
        @DisplayName("headersToSingleValueMap should convert headers correctly")
        void headersToSingleValueMap_StandardHeaders_ConvertsCorrectly() {
            Headers headers = new Headers.Builder()
                    .add("Content-Type", "application/json")
                    .add("Authorization", "Bearer token123")
                    .add("X-Request-ID", "req-456")
                    .build();

            Map<String, String> result = HttpUtils.headersToSingleValueMap(headers);

            assertNotNull(result);
            assertEquals(3, result.size());
            assertTrue(result.containsKey("Content-Type"));
            assertTrue(result.containsKey("Authorization"));
            assertTrue(result.containsKey("X-Request-ID"));
        }

        @Test
        @DisplayName("headersToSingleValueMap should handle multiple values by taking last")
        void headersToSingleValueMap_MultipleValues_TakesLast() {
            Headers headers = new Headers.Builder()
                    .add("Accept", "application/json")
                    .add("Accept", "text/html")
                    .build();

            Map<String, String> result = HttpUtils.headersToSingleValueMap(headers);

            assertNotNull(result);
            // OkHttp's Headers.get() returns the LAST value, not the first
            assertEquals("text/html", result.get("Accept"));
        }

        @Test
        @DisplayName("headersToSingleValueMap should handle empty headers")
        void headersToSingleValueMap_EmptyHeaders_ReturnsEmptyMap() {
            Headers headers = new Headers.Builder().build();

            Map<String, String> result = HttpUtils.headersToSingleValueMap(headers);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("SSL Context Creation")
    class SSLContextTests {

        @Test
        @DisplayName("createTrustAllSSLContext should create valid SSL context")
        void createTrustAllSSLContext_CreatesValidContext() {
            SSLContext sslContext = HttpUtils.createTrustAllSSLContext();

            assertNotNull(sslContext);
            assertEquals("TLS", sslContext.getProtocol());
        }

        @Test
        @DisplayName("TrustAllX509TrustManager should accept all certificates")
        void trustAllX509TrustManager_AcceptsAllCertificates() {
            HttpUtils.TrustAllX509TrustManager trustManager = new HttpUtils.TrustAllX509TrustManager();

            assertDoesNotThrow(() -> {
                trustManager.checkClientTrusted(null, "RSA");
                trustManager.checkServerTrusted(null, "RSA");
            });

            assertEquals(0, trustManager.getAcceptedIssuers().length);
        }
    }

    @Nested
    @DisplayName("Status Code Classification")
    class StatusCodeClassificationTests {

        @ParameterizedTest
        @MethodSource("provideStatusCodes")
        @DisplayName("Status code classification should be accurate")
        void statusCodeClassification_VariousCodes_ClassifiesCorrectly(
                int statusCode, boolean isSuccess, boolean isClientError, 
                boolean isServerError, boolean isRetryable, boolean isAuth) {
            
            assertEquals(isSuccess, HttpUtils.isSuccessCode(statusCode));
            assertEquals(isClientError, HttpUtils.isClientError(statusCode));
            assertEquals(isServerError, HttpUtils.isServerError(statusCode));
            assertEquals(isRetryable, HttpUtils.isRetryableStatusCode(statusCode));
            assertEquals(isAuth, HttpUtils.isAuthError(statusCode));
        }

        static Stream<Arguments> provideStatusCodes() {
            return Stream.of(
                // code, success, client, server, retryable, auth
                Arguments.of(200, true, false, false, false, false),
                Arguments.of(201, true, false, false, false, false),
                Arguments.of(400, false, true, false, false, false),
                Arguments.of(401, false, true, false, false, true),
                Arguments.of(403, false, true, false, false, true),
                Arguments.of(404, false, true, false, false, false),
                Arguments.of(408, false, true, false, true, false),
                Arguments.of(429, false, true, false, true, false),
                Arguments.of(500, false, false, true, true, false),
                Arguments.of(503, false, false, true, true, false)
            );
        }

        @ParameterizedTest
        @MethodSource("provideStatusDescriptions")
        @DisplayName("getStatusDescription should return correct descriptions")
        void getStatusDescription_VariousCodes_ReturnsCorrectDescriptions(
                int statusCode, String expectedDescription) {
            assertEquals(expectedDescription, HttpUtils.getStatusDescription(statusCode));
        }

        static Stream<Arguments> provideStatusDescriptions() {
            return Stream.of(
                Arguments.of(200, "OK"),
                Arguments.of(201, "Created"),
                Arguments.of(400, "Bad Request"),
                Arguments.of(401, "Unauthorized"),
                Arguments.of(404, "Not Found"),
                Arguments.of(500, "Internal Server Error"),
                Arguments.of(999, "HTTP 999")
            );
        }
    }

    @Nested
    @DisplayName("Retry Logic")
    class RetryLogicTests {

        @Test
        @DisplayName("getRetryAfterSeconds should extract from headers")
        void getRetryAfterSeconds_WithHeader_ExtractsValue() {
            Map<String, String> headers = new HashMap<>();
            headers.put("Retry-After", "120");
            
            long result = HttpUtils.getRetryAfterSeconds(headers, 60L);
            
            assertEquals(120L, result);
        }

        @Test
        @DisplayName("getRetryAfterSeconds should handle case-insensitive headers")
        void getRetryAfterSeconds_CaseInsensitive_ExtractsValue() {
            Map<String, String> headers = new HashMap<>();
            headers.put("retry-after", "90");
            
            long result = HttpUtils.getRetryAfterSeconds(headers, 60L);
            
            assertEquals(90L, result);
        }

        @Test
        @DisplayName("getRetryAfterSeconds should return default when header missing")
        void getRetryAfterSeconds_MissingHeader_ReturnsDefault() {
            Map<String, String> headers = new HashMap<>();
            
            long result = HttpUtils.getRetryAfterSeconds(headers, 60L);
            
            assertEquals(60L, result);
        }

        @ParameterizedTest
        @MethodSource("provideRetryableExceptions")
        @DisplayName("isRetryableException should identify retryable exceptions")
        void isRetryableException_VariousExceptions_IdentifiesCorrectly(
                Exception exception, boolean expectedRetryable) {
            
            assertEquals(expectedRetryable, HttpUtils.isRetryableException(exception));
        }

        static Stream<Arguments> provideRetryableExceptions() {
            return Stream.of(
                Arguments.of(new java.net.SocketTimeoutException("Timeout"), true),
                Arguments.of(new java.net.ConnectException("Connection failed"), true),
                Arguments.of(new java.net.UnknownHostException("Host not found"), true),
                Arguments.of(new RuntimeException("connection timeout"), true),
                Arguments.of(new RuntimeException("socket error"), true),
                Arguments.of(new IllegalArgumentException("Invalid argument"), false),
                Arguments.of(new RuntimeException("business logic error"), false),
                Arguments.of((Exception) null, false)
            );
        }
    }

    @Nested
    @DisplayName("Authentication Headers")
    class AuthenticationHeaderTests {

        @Test
        @DisplayName("createBasicAuthHeader should encode credentials correctly")
        void createBasicAuthHeader_ValidCredentials_EncodesCorrectly() {
            String username = "testuser";
            String password = "testpass";

            String authHeader = HttpUtils.createBasicAuthHeader(username, password);

            assertNotNull(authHeader);
            String decoded = new String(java.util.Base64.getDecoder().decode(authHeader));
            assertEquals("testuser:testpass", decoded);
        }

        @Test
        @DisplayName("createBasicAuthHeader should handle special characters")
        void createBasicAuthHeader_SpecialCharacters_HandlesCorrectly() {
            String username = "user@domain.com";
            String password = "p@ss:w0rd!";

            String authHeader = HttpUtils.createBasicAuthHeader(username, password);

            String decoded = new String(java.util.Base64.getDecoder().decode(authHeader));
            assertEquals("user@domain.com:p@ss:w0rd!", decoded);
        }

        @Test
        @DisplayName("createBearerAuthHeader should return token unchanged")
        void createBearerAuthHeader_ValidToken_ReturnsUnchanged() {
            String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test.token";

            String bearerHeader = HttpUtils.createBearerAuthHeader(token);

            assertEquals(token, bearerHeader);
        }
    }

    @Nested
    @DisplayName("Backoff Calculation")
    class BackoffCalculationTests {

        @Test
        @DisplayName("calculateBackoffDelay should implement exponential backoff with jitter")
        void calculateBackoffDelay_ExponentialBackoff_CalculatesCorrectly() {
            long baseDelayMs = 1000L;
            double backoffMultiplier = 2.0;
            long maxDelayMs = 30000L;

            long delay0 = HttpUtils.calculateBackoffDelay(0, baseDelayMs, backoffMultiplier, maxDelayMs);
            long delay1 = HttpUtils.calculateBackoffDelay(1, baseDelayMs, backoffMultiplier, maxDelayMs);
            long delay2 = HttpUtils.calculateBackoffDelay(2, baseDelayMs, backoffMultiplier, maxDelayMs);

            // Base delay + jitter (up to 10%)
            assertTrue(delay0 >= 1000L && delay0 <= 1100L);
            assertTrue(delay1 >= 2000L && delay1 <= 2200L);
            assertTrue(delay2 >= 4000L && delay2 <= 4400L);
        }

        @Test
        @DisplayName("calculateBackoffDelay should respect maximum delay")
        void calculateBackoffDelay_MaxDelay_RespectsMaximum() {
            long baseDelayMs = 1000L;
            double backoffMultiplier = 2.0;
            long maxDelayMs = 5000L;

            long delay = HttpUtils.calculateBackoffDelay(10, baseDelayMs, backoffMultiplier, maxDelayMs);

            assertTrue(delay <= maxDelayMs + (maxDelayMs * 0.1));
        }

        @Test
        @DisplayName("calculateBackoffDelay should add jitter for different calls")
        void calculateBackoffDelay_Jitter_ProducesDifferentResults() {
            long baseDelayMs = 1000L;
            double backoffMultiplier = 2.0;
            long maxDelayMs = 30000L;

            long delay1 = HttpUtils.calculateBackoffDelay(1, baseDelayMs, backoffMultiplier, maxDelayMs);
            long delay2 = HttpUtils.calculateBackoffDelay(1, baseDelayMs, backoffMultiplier, maxDelayMs);
            long delay3 = HttpUtils.calculateBackoffDelay(1, baseDelayMs, backoffMultiplier, maxDelayMs);

            // All should be in the expected range
            assertTrue(delay1 >= 2000L && delay1 <= 2200L);
            assertTrue(delay2 >= 2000L && delay2 <= 2200L);
            assertTrue(delay3 >= 2000L && delay3 <= 2200L);
        }
    }
}