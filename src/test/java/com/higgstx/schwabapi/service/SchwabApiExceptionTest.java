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
}