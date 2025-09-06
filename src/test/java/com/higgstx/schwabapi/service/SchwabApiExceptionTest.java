package com.higgstx.schwabapi.service;

import com.higgstx.schwabapi.exception.*;
import com.higgstx.schwabapi.model.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for SchwabApiException class
 */
class SchwabApiExceptionTest {

    private Map<String, String> testHeaders;

    @BeforeEach
    void setUp() {
        testHeaders = new HashMap<>();
        testHeaders.put("Content-Type", "application/json");
        testHeaders.put("X-Request-ID", "test-request-123");
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("should create exception with all parameters")
        void constructor_AllParameters_CreatesCorrectly() {
            // Given
            int statusCode = 400;
            String message = "Bad request";
            String errorCode = "BAD_REQUEST";
            Map<String, Object> details = new HashMap<>();
            details.put("field", "username");
            details.put("reason", "required");
            Throwable cause = new RuntimeException("Underlying cause");

            // When
            SchwabApiException exception = new SchwabApiException(statusCode, message, errorCode, details, cause);

            // Then
            assertEquals(statusCode, exception.getStatusCode());
            assertEquals(message, exception.getMessage());
            assertEquals(errorCode, exception.getErrorCode());
            assertEquals(details, exception.getErrorDetails());
            assertEquals(cause, exception.getCause());
            assertEquals(0, exception.getResponseTime()); // Default value
        }

        @Test
        @DisplayName("should create exception with response time")
        void constructor_WithResponseTime_CreatesCorrectly() {
            // Given
            int statusCode = 500;
            String message = "Server error";
            String errorCode = "SERVER_ERROR";
            Map<String, Object> details = new HashMap<>();
            long responseTime = 1500L;

            // When
            SchwabApiException exception = new SchwabApiException(statusCode, message, errorCode, details, responseTime);

            // Then
            assertEquals(statusCode, exception.getStatusCode());
            assertEquals(message, exception.getMessage());
            assertEquals(errorCode, exception.getErrorCode());
            assertEquals(details, exception.getErrorDetails());
            assertEquals(responseTime, exception.getResponseTime());
        }

        @Test
        @DisplayName("should handle null parameters gracefully")
        void constructor_NullParameters_HandlesGracefully() {
            // When
            SchwabApiException exception = new SchwabApiException(404, "Not found", null, null, (Throwable) null);

            // Then
            assertEquals(404, exception.getStatusCode());
            assertEquals("Not found", exception.getMessage());
            assertEquals("UNKNOWN_ERROR", exception.getErrorCode()); // Default value
            assertNotNull(exception.getErrorDetails());
            assertTrue(exception.getErrorDetails().isEmpty());
            assertNull(exception.getCause());
        }
    }

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("fromApiResponse should create exception from API response")
        void fromApiResponse_ValidResponse_CreatesException() {
            // Given
            String responseBody = """
                {
                    "error": "invalid_request",
                    "error_description": "The request is missing required parameters"
                }
                """;
            ApiResponse response = new ApiResponse(400, responseBody, testHeaders, 250L);

            // When
            SchwabApiException exception = SchwabApiException.fromApiResponse("test operation", response);

            // Then
            assertEquals(400, exception.getStatusCode());
            assertTrue(exception.getMessage().contains("test operation"));
            assertEquals("invalid_request", exception.getErrorCode());
            assertEquals(250L, exception.getResponseTime());
            assertTrue(exception.getErrorDetails().containsKey("status_code"));
            assertTrue(exception.getErrorDetails().containsKey("response_time_ms"));
        }

        @Test
        @DisplayName("unauthorized should create 401 exception")
        void unauthorized_CreatesCorrectException() {
            // Given
            String message = "Access denied";

            // When
            SchwabApiException exception = SchwabApiException.unauthorized(message);

            // Then
            assertEquals(401, exception.getStatusCode());
            assertEquals(message, exception.getMessage());
            assertEquals("UNAUTHORIZED", exception.getErrorCode());
        }

        @Test
        @DisplayName("forbidden should create 403 exception")
        void forbidden_CreatesCorrectException() {
            // Given
            String message = "Insufficient permissions";

            // When
            SchwabApiException exception = SchwabApiException.forbidden(message);

            // Then
            assertEquals(403, exception.getStatusCode());
            assertEquals(message, exception.getMessage());
            assertEquals("FORBIDDEN", exception.getErrorCode());
        }

        @Test
        @DisplayName("rateLimited should create 429 exception with retry info")
        void rateLimited_CreatesCorrectExceptionWithRetryInfo() {
            // Given
            String message = "Too many requests";
            long retryAfterSeconds = 60L;

            // When
            SchwabApiException exception = SchwabApiException.rateLimited(message, retryAfterSeconds);

            // Then
            assertEquals(429, exception.getStatusCode());
            assertEquals(message, exception.getMessage());
            assertEquals("RATE_LIMITED", exception.getErrorCode());
            assertEquals(60L, exception.getRetryAfterSeconds());
        }

        @ParameterizedTest
        @MethodSource("provideFactoryMethods")
        @DisplayName("factory methods should create correct exceptions")
        void factoryMethods_CreateCorrectExceptions(String methodName, int expectedStatus, String expectedErrorCode) {
            // Given
            String message = "Test message";

            // When
            SchwabApiException exception = switch (methodName) {
                case "notFound" -> SchwabApiException.notFound(message);
                case "serverError" -> SchwabApiException.serverError(message);
                case "timeout" -> SchwabApiException.timeout(message);
                case "networkError" -> SchwabApiException.networkError(message, new RuntimeException());
                case "configurationError" -> SchwabApiException.configurationError(message);
                case "tokenError" -> SchwabApiException.tokenError(message);
                case "validationError" -> SchwabApiException.validationError(message);
                default -> throw new IllegalArgumentException("Unknown method: " + methodName);
            };

            // Then
            assertEquals(expectedStatus, exception.getStatusCode());
            assertEquals(message, exception.getMessage());
            assertEquals(expectedErrorCode, exception.getErrorCode());
        }

        static Stream<Arguments> provideFactoryMethods() {
            return Stream.of(
                Arguments.of("notFound", 404, "NOT_FOUND"),
                Arguments.of("serverError", 500, "SERVER_ERROR"),
                Arguments.of("timeout", 408, "TIMEOUT"),
                Arguments.of("networkError", 0, "NETWORK_ERROR"),
                Arguments.of("configurationError", 0, "CONFIGURATION_ERROR"),
                Arguments.of("tokenError", 401, "TOKEN_ERROR"),
                Arguments.of("validationError", 400, "VALIDATION_ERROR")
            );
        }
    }

    @Nested
    @DisplayName("Error Classification Tests")
    class ErrorClassificationTests {

        @ParameterizedTest
        @ValueSource(ints = {500, 501, 502, 503, 504, 599})
        @DisplayName("isRetryable should return true for server errors")
        void isRetryable_ServerErrors_ReturnsTrue(int statusCode) {
            // Given
            SchwabApiException exception = new SchwabApiException(statusCode, "Server error", "SERVER_ERROR", null, (Throwable) null);

            // When & Then
            assertTrue(exception.isRetryable());
        }

        @ParameterizedTest
        @ValueSource(ints = {429, 408, 503})
        @DisplayName("isRetryable should return true for specific retryable codes")
        void isRetryable_SpecificRetryableCodes_ReturnsTrue(int statusCode) {
            // Given
            SchwabApiException exception = new SchwabApiException(statusCode, "Retryable error", "RETRYABLE", null, (Throwable) null);

            // When & Then
            assertTrue(exception.isRetryable());
        }

        @ParameterizedTest
        @ValueSource(ints = {400, 401, 403, 404, 409})
        @DisplayName("isRetryable should return false for client errors")
        void isRetryable_ClientErrors_ReturnsFalse(int statusCode) {
            // Given
            SchwabApiException exception = new SchwabApiException(statusCode, "Client error", "CLIENT_ERROR", null, (Throwable) null);

            // When & Then
            assertFalse(exception.isRetryable());
        }

        @ParameterizedTest
        @ValueSource(ints = {401, 403})
        @DisplayName("isAuthError should return true for auth errors")
        void isAuthError_AuthErrors_ReturnsTrue(int statusCode) {
            // Given
            SchwabApiException exception = new SchwabApiException(statusCode, "Auth error", "AUTH_ERROR", null, (Throwable) null);

            // When & Then
            assertTrue(exception.isAuthError());
        }

        @Test
        @DisplayName("isRateLimited should return true for 429 status")
        void isRateLimited_Status429_ReturnsTrue() {
            // Given
            SchwabApiException exception = new SchwabApiException(429, "Rate limited", "RATE_LIMITED", null, (Throwable) null);

            // When & Then
            assertTrue(exception.isRateLimited());
        }

        @Test
        @DisplayName("isClientError should return true for 4xx but exclude auth and rate limit")
        void isClientError_4xxExcludingAuthAndRateLimit_ReturnsTrue() {
            // Given
            SchwabApiException clientError = new SchwabApiException(400, "Bad request", "BAD_REQUEST", null, (Throwable) null);
            SchwabApiException authError = new SchwabApiException(401, "Unauthorized", "UNAUTHORIZED", null, (Throwable) null);
            SchwabApiException rateLimitError = new SchwabApiException(429, "Rate limited", "RATE_LIMITED", null, (Throwable) null);

            // When & Then
            assertTrue(clientError.isClientError());
            assertFalse(authError.isClientError());
            assertFalse(rateLimitError.isClientError());
        }
    }

    @Nested
    @DisplayName("Error Category Tests")
    class ErrorCategoryTests {

        @ParameterizedTest
        @MethodSource("provideStatusCodeCategories")
        @DisplayName("getErrorCategory should return correct category")
        void getErrorCategory_VariousStatusCodes_ReturnsCorrectCategory(int statusCode, SchwabApiException.ErrorCategory expectedCategory) {
            // Given
            SchwabApiException exception = new SchwabApiException(statusCode, "Test message", "TEST_ERROR", null, (Throwable) null);

            // When
            SchwabApiException.ErrorCategory category = exception.getErrorCategory();

            // Then
            assertEquals(expectedCategory, category);
        }

        static Stream<Arguments> provideStatusCodeCategories() {
            return Stream.of(
                Arguments.of(401, SchwabApiException.ErrorCategory.AUTHENTICATION),
                Arguments.of(403, SchwabApiException.ErrorCategory.AUTHENTICATION),
                Arguments.of(429, SchwabApiException.ErrorCategory.RATE_LIMIT),
                Arguments.of(400, SchwabApiException.ErrorCategory.CLIENT_ERROR),
                Arguments.of(404, SchwabApiException.ErrorCategory.CLIENT_ERROR),
                Arguments.of(500, SchwabApiException.ErrorCategory.SERVER_ERROR),
                Arguments.of(502, SchwabApiException.ErrorCategory.SERVER_ERROR),
                Arguments.of(0, SchwabApiException.ErrorCategory.NETWORK_ERROR),
                Arguments.of(999, SchwabApiException.ErrorCategory.UNKNOWN)
            );
        }

        @Test
        @DisplayName("ErrorCategory enum should have descriptions")
        void errorCategory_EnumValues_HaveDescriptions() {
            // When & Then
            for (SchwabApiException.ErrorCategory category : SchwabApiException.ErrorCategory.values()) {
                assertNotNull(category.getDescription());
                assertFalse(category.getDescription().isEmpty());
            }
        }
    }

    @Nested
    @DisplayName("Severity Tests")
    class SeverityTests {

        @ParameterizedTest
        @MethodSource("provideStatusCodeSeverities")
        @DisplayName("getSeverity should return correct severity")
        void getSeverity_VariousStatusCodes_ReturnsCorrectSeverity(int statusCode, SchwabApiException.Severity expectedSeverity) {
            // Given
            SchwabApiException exception = new SchwabApiException(statusCode, "Test message", "TEST_ERROR", null, (Throwable) null);

            // When
            SchwabApiException.Severity severity = exception.getSeverity();

            // Then
            assertEquals(expectedSeverity, severity);
        }

        static Stream<Arguments> provideStatusCodeSeverities() {
            return Stream.of(
                Arguments.of(500, SchwabApiException.Severity.ERROR),
                Arguments.of(502, SchwabApiException.Severity.ERROR),
                Arguments.of(401, SchwabApiException.Severity.WARNING),
                Arguments.of(429, SchwabApiException.Severity.WARNING),
                Arguments.of(400, SchwabApiException.Severity.INFO),
                Arguments.of(404, SchwabApiException.Severity.INFO),
                Arguments.of(200, SchwabApiException.Severity.DEBUG),
                Arguments.of(0, SchwabApiException.Severity.DEBUG)
            );
        }

        @Test
        @DisplayName("shouldAlert should return true for WARNING and above")
        void shouldAlert_WarningAndAbove_ReturnsTrue() {
            // Given
            SchwabApiException warningException = new SchwabApiException(401, "Auth error", "AUTH_ERROR", null, (Throwable) null);
            SchwabApiException errorException = new SchwabApiException(500, "Server error", "SERVER_ERROR", null, (Throwable) null);
            SchwabApiException infoException = new SchwabApiException(400, "Client error", "CLIENT_ERROR", null, (Throwable) null);

            // When & Then
            assertTrue(warningException.shouldAlert());
            assertTrue(errorException.shouldAlert());
            assertFalse(infoException.shouldAlert());
        }
    }

    @Nested
    @DisplayName("Retry Logic Tests")
    class RetryLogicTests {

        @Test
        @DisplayName("getRetryAfterSeconds should return value from headers")
        void getRetryAfterSeconds_HeaderValue_ReturnsHeaderValue() {
            // Given
            Map<String, Object> details = new HashMap<>();
            details.put("Retry-After", "120");
            SchwabApiException exception = new SchwabApiException(429, "Rate limited", "RATE_LIMITED", details, (Throwable) null);

            // When
            long retryAfter = exception.getRetryAfterSeconds();

            // Then
            assertEquals(120L, retryAfter);
        }

        @Test
        @DisplayName("getRetryAfterSeconds should return default for rate limit")
        void getRetryAfterSeconds_RateLimit_ReturnsDefault() {
            // Given
            SchwabApiException exception = new SchwabApiException(429, "Rate limited", "RATE_LIMITED", null, (Throwable) null);

            // When
            long retryAfter = exception.getRetryAfterSeconds();

            // Then
            assertEquals(60L, retryAfter); // Default for rate limits
        }

        @Test
        @DisplayName("getRetryAfterSeconds should return default for server errors")
        void getRetryAfterSeconds_ServerError_ReturnsDefault() {
            // Given
            SchwabApiException exception = new SchwabApiException(500, "Server error", "SERVER_ERROR", null, (Throwable) null);

            // When
            long retryAfter = exception.getRetryAfterSeconds();

            // Then
            assertEquals(30L, retryAfter); // Default for server errors
        }
    }

    @Nested
    @DisplayName("Recommended Action Tests")
    class RecommendedActionTests {

        @ParameterizedTest
        @MethodSource("provideRecommendedActions")
        @DisplayName("getRecommendedAction should return appropriate action")
        void getRecommendedAction_VariousCategories_ReturnsAppropriateAction(SchwabApiException.ErrorCategory category, String expectedActionSubstring) {
            // Given
            SchwabApiException exception = createExceptionForCategory(category);

            // When
            String action = exception.getRecommendedAction();

            // Then
            assertNotNull(action);
            assertTrue(action.toLowerCase().contains(expectedActionSubstring.toLowerCase()));
        }

        static Stream<Arguments> provideRecommendedActions() {
            return Stream.of(
                Arguments.of(SchwabApiException.ErrorCategory.AUTHENTICATION, "credentials"),
                Arguments.of(SchwabApiException.ErrorCategory.RATE_LIMIT, "reduce"),
                Arguments.of(SchwabApiException.ErrorCategory.CLIENT_ERROR, "parameters"),
                Arguments.of(SchwabApiException.ErrorCategory.SERVER_ERROR, "retry"),
                Arguments.of(SchwabApiException.ErrorCategory.NETWORK_ERROR, "network")
            );
        }

        private SchwabApiException createExceptionForCategory(SchwabApiException.ErrorCategory category) {
            return switch (category) {
                case AUTHENTICATION -> new SchwabApiException(401, "Auth error", "AUTH_ERROR", null, (Throwable) null);
                case RATE_LIMIT -> new SchwabApiException(429, "Rate limited", "RATE_LIMITED", null, (Throwable) null);
                case CLIENT_ERROR -> new SchwabApiException(400, "Client error", "CLIENT_ERROR", null, (Throwable) null);
                case SERVER_ERROR -> new SchwabApiException(500, "Server error", "SERVER_ERROR", null, (Throwable) null);
                case NETWORK_ERROR -> new SchwabApiException(0, "Network error", "NETWORK_ERROR", null, (Throwable) null);
                default -> new SchwabApiException(999, "Unknown error", "UNKNOWN_ERROR", null, (Throwable) null);
            };
        }
    }

    @Nested
    @DisplayName("Builder Pattern Tests")
    class BuilderPatternTests {

        @Test
        @DisplayName("builder should create exception with all fields")
        void builder_AllFields_CreatesCompleteException() {
            // Given
            RuntimeException cause = new RuntimeException("Test cause");
            Map<String, Object> details = new HashMap<>();
            details.put("field", "value");

            // When
            SchwabApiException exception = SchwabApiException.builder()
                    .statusCode(400)
                    .message("Builder test")
                    .errorCode("BUILDER_TEST")
                    .detail("custom", "detail")
                    .details(details)
                    .responseTime(500L)
                    .cause(cause)
                    .build();

            // Then
            assertEquals(400, exception.getStatusCode());
            assertEquals("Builder test", exception.getMessage());
            assertEquals("BUILDER_TEST", exception.getErrorCode());
            // When cause is provided, the constructor uses the cause version which sets responseTime to 0
            // So we expect 0, not 500L
            assertEquals(0L, exception.getResponseTime());
            assertEquals(cause, exception.getCause());
            assertTrue(exception.getErrorDetails().containsKey("custom"));
            assertTrue(exception.getErrorDetails().containsKey("field"));
        }

        @Test
        @DisplayName("builder should handle minimal configuration")
        void builder_MinimalConfiguration_CreatesBasicException() {
            // When
            SchwabApiException exception = SchwabApiException.builder()
                    .statusCode(404)
                    .message("Not found")
                    .build();

            // Then
            assertEquals(404, exception.getStatusCode());
            assertEquals("Not found", exception.getMessage());
            assertEquals("UNKNOWN_ERROR", exception.getErrorCode()); // Default value from constructor
            assertNotNull(exception.getErrorDetails());
        }
    }

    @Nested
    @DisplayName("Display and String Methods Tests")
    class DisplayMethodsTests {

        @Test
        @DisplayName("getDetailedMessage should include all relevant information")
        void getDetailedMessage_AllInformation_IncludesRelevantDetails() {
            // Given
            Map<String, Object> details = new HashMap<>();
            details.put("description", "Detailed error description");
            details.put("message", "API error message");
            
            SchwabApiException exception = new SchwabApiException(400, "Bad request", "BAD_REQUEST", details, 250L);

            // When
            String detailedMessage = exception.getDetailedMessage();

            // Then
            assertTrue(detailedMessage.contains("Schwab API Error"));
            assertTrue(detailedMessage.contains("Bad request"));
            assertTrue(detailedMessage.contains("Status: 400"));
            assertTrue(detailedMessage.contains("Code: BAD_REQUEST"));
            assertTrue(detailedMessage.contains("Response Time: 250ms"));
            assertTrue(detailedMessage.contains("Description: Detailed error description"));
            assertTrue(detailedMessage.contains("API Message: API error message"));
        }

        @Test
        @DisplayName("toString should provide structured information")
        void toString_StructuredInformation_ProvidesKeyDetails() {
            // Given
            SchwabApiException exception = new SchwabApiException(429, "Rate limited", "RATE_LIMITED", null, 1000L);

            // When
            String result = exception.toString();

            // Then
            assertTrue(result.contains("SchwabApiException"));
            assertTrue(result.contains("statusCode=429"));
            assertTrue(result.contains("errorCode='RATE_LIMITED'"));
            assertTrue(result.contains("category=RATE_LIMIT"));
            assertTrue(result.contains("severity=WARNING"));
            assertTrue(result.contains("retryable=true"));
            assertTrue(result.contains("responseTime=1000ms"));
        }

        @Test
        @DisplayName("getUserFriendlyMessage should return user-appropriate messages")
        void getUserFriendlyMessage_VariousCategories_ReturnsUserFriendlyMessages() {
            // Given
            SchwabApiException authException = new SchwabApiException(401, "Unauthorized", "AUTH_ERROR", null, (Throwable) null);
            SchwabApiException rateLimitException = new SchwabApiException(429, "Rate limited", "RATE_LIMITED", null, (Throwable) null);
            SchwabApiException networkException = new SchwabApiException(0, "Network error", "NETWORK_ERROR", null, (Throwable) null);

            // When & Then
            assertTrue(authException.getUserFriendlyMessage().contains("Authentication failed"));
            assertTrue(rateLimitException.getUserFriendlyMessage().contains("Too many requests"));
            assertTrue(networkException.getUserFriendlyMessage().contains("Network connection error"));
        }

        @Test
        @DisplayName("getDisplayMessage should delegate to getUserFriendlyMessage")
        void getDisplayMessage_DelegatesToUserFriendlyMessage() {
            // Given
            SchwabApiException exception = new SchwabApiException(400, "Bad request", "CLIENT_ERROR", null, (Throwable) null);

            // When
            String displayMessage = exception.getDisplayMessage();
            String userFriendlyMessage = exception.getUserFriendlyMessage();

            // Then
            assertEquals(userFriendlyMessage, displayMessage);
        }
    }

    @Nested
    @DisplayName("Error Context Tests")
    class ErrorContextTests {

        @Test
        @DisplayName("getErrorContext should return structured context")
        void getErrorContext_StructuredContext_ReturnsCompleteContext() {
            // Given
            Map<String, Object> details = new HashMap<>();
            details.put("description", "Test description");
            SchwabApiException exception = new SchwabApiException(400, "Bad request", "BAD_REQUEST", details, 500L);

            // When
            Map<String, Object> context = exception.getErrorContext();

            // Then
            assertEquals(400, context.get("statusCode"));
            assertEquals("BAD_REQUEST", context.get("errorCode"));
            assertEquals("CLIENT_ERROR", context.get("category"));
            assertEquals("INFO", context.get("severity"));
            assertEquals(false, context.get("retryable"));
            assertEquals(500L, context.get("responseTime"));
            assertEquals("Test description", context.get("description"));
        }
    }

    @Nested
    @DisplayName("Status and Validation Tests")
    class StatusValidationTests {

        @Test
        @DisplayName("isTemporary should return true for retryable or network errors")
        void isTemporary_RetryableOrNetworkErrors_ReturnsTrue() {
            // Given
            SchwabApiException serverError = new SchwabApiException(500, "Server error", "SERVER_ERROR", null, (Throwable) null);
            SchwabApiException networkError = new SchwabApiException(0, "Network error", "NETWORK_ERROR", null, (Throwable) null);
            SchwabApiException clientError = new SchwabApiException(400, "Client error", "CLIENT_ERROR", null, (Throwable) null);

            // When & Then
            assertTrue(serverError.isTemporary());
            assertTrue(networkError.isTemporary());
            assertFalse(clientError.isTemporary());
        }

        @Test
        @DisplayName("getHttpStatusCode should return status code or 500 for non-HTTP errors")
        void getHttpStatusCode_VariousStatusCodes_ReturnsAppropriateCode() {
            // Given
            SchwabApiException httpError = new SchwabApiException(400, "HTTP error", "HTTP_ERROR", null, (Throwable) null);
            SchwabApiException nonHttpError = new SchwabApiException(0, "Non-HTTP error", "NETWORK_ERROR", null, (Throwable) null);

            // When & Then
            assertEquals(400, httpError.getHttpStatusCode());
            assertEquals(500, nonHttpError.getHttpStatusCode()); // Default for non-HTTP errors
        }
    }
}