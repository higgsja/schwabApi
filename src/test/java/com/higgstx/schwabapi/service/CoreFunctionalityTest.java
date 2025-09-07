package com.higgstx.schwabapi.service;

import com.higgstx.schwabapi.config.SchwabApiProperties;
import com.higgstx.schwabapi.exception.*;
import com.higgstx.schwabapi.model.market.DailyPriceData;
import com.higgstx.schwabapi.model.TokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FIXED core functionality testing - compatible with simplified TokenResponse
 * Focuses on business logic rather than utility validation
 */
@DisplayName("Core Functionality Testing")
class FixedCoreFunctionalityTest {

    private MarketDataService marketDataService;
    private TokenManager tokenManager;
    private boolean hadTokenFile = false;
    private String originalTokenContent = null;

    @BeforeEach
    void setUp() throws SchwabApiException {
        backupAndRemoveTokenFile();

        try {
            SchwabApiProperties testProps = new SchwabApiProperties(
                    "https://api.schwabapi.com/v1/oauth/authorize",
                    "https://api.schwabapi.com/v1/oauth/token",
                    "https://api.schwabapi.com/marketdata/v1",
                    "https://127.0.0.1:8182",
                    "readonly",
                    30000
            );
            
            tokenManager = new TokenManager("test-tokens.json", "test-client-id", "test-client-secret");
            marketDataService = new MarketDataService(testProps, tokenManager);
            
        } catch (SchwabApiException e) {
            try {
                SchwabApiProperties fallbackProps = new SchwabApiProperties(
                        "https://test.api.com/auth",
                        "https://test.api.com/token",
                        "https://test.api.com/market",
                        "http://localhost:8080",
                        "readonly",
                        5000
                );
                tokenManager = new TokenManager("test-tokens.json", "dummy-id", "dummy-secret");
                marketDataService = new MarketDataService(fallbackProps, tokenManager);
            } catch (SchwabApiException fallbackEx) {
                throw new RuntimeException("Failed to create MarketDataService for testing", fallbackEx);
            }
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (marketDataService != null) {
            marketDataService.close();
        }
        restoreTokenFile();
    }

    @Nested
    @DisplayName("Market Data Operations")
    class MarketDataOperationsTest {

        @Test
        @DisplayName("getBulkHistoricalData should handle mixed valid/invalid symbols")
        void testGetBulkHistoricalData_MixedSymbols_HandlesCorrectly() {
            String[] mixedSymbols = {"AAPL", null, "", "  ", "GOOGL"};
            String fakeToken = "fake-access-token";

            assertDoesNotThrow(() -> {
                List<DailyPriceData> result = marketDataService.getBulkHistoricalData(mixedSymbols, fakeToken);

                assertNotNull(result);
                assertEquals(2, result.size()); // Only AAPL and GOOGL should be processed

                for (DailyPriceData data : result) {
                    assertFalse(data.isSuccess()); // Will fail with fake token
                    assertTrue("AAPL".equals(data.getSymbol()) || "GOOGL".equals(data.getSymbol()));
                }
            });
        }

        @Test
        @DisplayName("getBulkHistoricalData should normalize symbol case")
        void testGetBulkHistoricalData_CaseNormalization_WorksCorrectly() {
            String[] mixedCaseSymbols = {"aapl", "GOOGL", "MsFt"};
            String fakeToken = "fake-access-token";

            assertDoesNotThrow(() -> {
                List<DailyPriceData> result = marketDataService.getBulkHistoricalData(mixedCaseSymbols, fakeToken);

                assertNotNull(result);
                assertEquals(3, result.size());

                for (DailyPriceData data : result) {
                    assertTrue("AAPL".equals(data.getSymbol()) 
                            || "GOOGL".equals(data.getSymbol()) 
                            || "MSFT".equals(data.getSymbol()));
                }
            });
        }

        @Test
        @DisplayName("DailyPriceData should handle all creation scenarios correctly")
        void testDailyPriceData_CreationScenarios_WorkCorrectly() {
            // Test successful creation
            DailyPriceData successData = DailyPriceData.success("AAPL", System.currentTimeMillis(), 
                150.0, 155.0, 149.0, 152.0, 1000000L);
            
            assertTrue(successData.isSuccess());
            assertNull(successData.getErrorMessage());
            assertEquals("AAPL", successData.getSymbol());
            assertNotNull(successData.getLocalDate());

            // Test error creation
            DailyPriceData errorData = DailyPriceData.error("INVALID", "Symbol not found");
            
            assertFalse(errorData.isSuccess());
            assertEquals("Symbol not found", errorData.getErrorMessage());
            assertEquals("INVALID", errorData.getSymbol());

            // Test error with date
            LocalDate testDate = LocalDate.of(2024, 1, 15);
            DailyPriceData errorWithDate = DailyPriceData.error("INVALID", testDate, "No data for date");
            
            assertFalse(errorWithDate.isSuccess());
            assertEquals("No data for date", errorWithDate.getErrorMessage());
            assertEquals(testDate, errorWithDate.getDate());
        }
    }

    @Nested
    @DisplayName("Token Response Functionality")
    class TokenResponseFunctionalityTest {

        private TokenResponse tokenResponse;
        private Instant now;

        @BeforeEach
        void setUp() {
            tokenResponse = new TokenResponse();
            now = Instant.now();
        }

        @Test
        @DisplayName("Token validation should work correctly across time boundaries")
        void testTokenValidation_TimeBoundaries_WorksCorrectly() {
            // Test future expiration
            tokenResponse.setExpiresAt(now.plus(1, ChronoUnit.HOURS));
            assertTrue(tokenResponse.isAccessTokenValid());

            // Test past expiration
            tokenResponse.setExpiresAt(now.minus(1, ChronoUnit.HOURS));
            assertFalse(tokenResponse.isAccessTokenValid());

            // Test refresh token validation
            tokenResponse.setRefreshTokenExpiresAt(now.plus(7, ChronoUnit.DAYS));
            assertTrue(tokenResponse.isRefreshTokenValid());

            tokenResponse.setRefreshTokenExpiresAt(now.minus(1, ChronoUnit.DAYS));
            assertFalse(tokenResponse.isRefreshTokenValid());
        }

        @Test
        @DisplayName("Token status methods should return correct states")
        void testTokenStatus_VariousStates_ReturnsCorrectStatus() {
            // Valid access token
            tokenResponse.setExpiresAt(now.plus(1, ChronoUnit.HOURS));
            assertEquals("VALID", tokenResponse.getQuickStatus());
            // Note: getHealthStatus() was removed in simplified version

            // Expired access, valid refresh
            tokenResponse.setExpiresAt(now.minus(1, ChronoUnit.HOURS));
            tokenResponse.setRefreshTokenExpiresAt(now.plus(1, ChronoUnit.DAYS));
            assertEquals("REFRESH_NEEDED", tokenResponse.getQuickStatus());

            // Both expired
            tokenResponse.setRefreshTokenExpiresAt(now.minus(1, ChronoUnit.HOURS));
            assertEquals("EXPIRED", tokenResponse.getQuickStatus());
        }

        @Test
        @DisplayName("Token expiry predictions should work correctly")
        void testTokenExpiryPredictions_VariousScenarios_WorksCorrectly() {
            // Token expiring soon
            tokenResponse.setExpiresAt(now.plus(30, ChronoUnit.SECONDS));
            assertTrue(tokenResponse.willAccessTokenExpireSoon(60));
            // Note: needsRefreshSoon() was removed in simplified version

            // Token expiring beyond buffer
            tokenResponse.setExpiresAt(now.plus(120, ChronoUnit.SECONDS));
            assertFalse(tokenResponse.willAccessTokenExpireSoon(60));

            // Already expired token
            tokenResponse.setExpiresAt(now.minus(30, ChronoUnit.SECONDS));
            assertFalse(tokenResponse.willAccessTokenExpireSoon(60));
        }

        @Test
        @DisplayName("Token time calculations should be accurate")
        void testTokenTimeCalculations_VariousScenarios_CalculatesCorrectly() {
            // Future expiration
            Instant futureExpiry = now.plus(3600, ChronoUnit.SECONDS);
            tokenResponse.setExpiresAt(futureExpiry);

            long seconds = tokenResponse.getSecondsUntilAccessExpiry();
            assertTrue(seconds > 3590 && seconds <= 3600);

            // Past expiration
            tokenResponse.setExpiresAt(now.minus(1, ChronoUnit.HOURS));
            assertTrue(tokenResponse.getSecondsUntilAccessExpiry() < 0);

            // Null expiration
            tokenResponse.setExpiresAt(null);
            assertEquals(-1, tokenResponse.getSecondsUntilAccessExpiry());
        }

        @Test
        @DisplayName("Token should handle all field assignments correctly")
        void testTokenFields_AllFields_HandleCorrectly() {
            // Setup comprehensive token
            tokenResponse.setAccessToken("access-token-123");
            tokenResponse.setRefreshToken("refresh-token-456");
            tokenResponse.setScope("readonly");
            tokenResponse.setExpiresIn(3600L);
            tokenResponse.setRefreshTokenExpiresIn(604800L);
            tokenResponse.setTokenType("Bearer");
            tokenResponse.setIdToken("id-token-789");
            tokenResponse.setSource(TokenResponse.TokenSource.AUTHORIZATION_CODE);

            // Verify all fields
            assertEquals("access-token-123", tokenResponse.getAccessToken());
            assertEquals("refresh-token-456", tokenResponse.getRefreshToken());
            assertEquals("readonly", tokenResponse.getScope());
            assertEquals(3600L, tokenResponse.getExpiresIn());
            assertEquals(604800L, tokenResponse.getRefreshTokenExpiresIn());
            assertEquals("Bearer", tokenResponse.getTokenType());
            assertEquals("id-token-789", tokenResponse.getIdToken());
            assertEquals(TokenResponse.TokenSource.AUTHORIZATION_CODE, tokenResponse.getSource());
        }

        @Test
        @DisplayName("Token toString should provide useful information")
        void testTokenToString_ProvidesUsefulInfo() {
            // Setup token with basic data
            tokenResponse.setTokenType("Bearer");
            tokenResponse.setScope("readonly");
            tokenResponse.setSource(TokenResponse.TokenSource.AUTHORIZATION_CODE);
            tokenResponse.setExpiresAt(now.plus(1, ChronoUnit.HOURS));

            String result = tokenResponse.toString();
            
            assertNotNull(result);
            assertTrue(result.contains("TokenResponse"));
            assertTrue(result.contains("Bearer"));
            assertTrue(result.contains("readonly"));
            assertTrue(result.contains("VALID")); // Should show current status
        }
    }

    @Nested
    @DisplayName("Service Integration")
    class ServiceIntegrationTest {

        @Test
        @DisplayName("MarketDataService should provide access to TokenManager")
        void testMarketDataService_TokenManagerAccess_WorksCorrectly() {
            TokenManager result = marketDataService.getTokenManager();
            
            assertNotNull(result);
            assertSame(tokenManager, result);
        }

        @Test
        @DisplayName("Service should handle close operations gracefully")
        void testServiceClose_MultipleOperations_HandlesGracefully() {
            assertDoesNotThrow(() -> {
                marketDataService.close();
                marketDataService.close(); // Multiple close calls should not fail
            });
        }

        @Test
        @DisplayName("Service readiness should reflect token state")
        void testServiceReadiness_TokenState_ReflectsCorrectly() {
            // Initially should not be ready (no valid tokens)
            assertFalse(marketDataService.isReady());
            
            String status = marketDataService.getTokenStatus();
            assertTrue(status.equals("NO TOKENS") || status.startsWith("ERROR"));
        }
    }

    // Helper methods
    private void backupAndRemoveTokenFile() {
        try {
            String tokenFile = "schwab-api.json";
            if (Files.exists(Paths.get(tokenFile))) {
                hadTokenFile = true;
                originalTokenContent = Files.readString(Paths.get(tokenFile));
                Files.delete(Paths.get(tokenFile));

                String refreshTokenFile = "schwab-refresh-token.txt";
                if (Files.exists(Paths.get(refreshTokenFile))) {
                    Files.delete(Paths.get(refreshTokenFile));
                }
            }
            
            String testTokenFile = "test-tokens.json";
            if (Files.exists(Paths.get(testTokenFile))) {
                Files.delete(Paths.get(testTokenFile));
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    private void restoreTokenFile() {
        try {
            if (hadTokenFile && originalTokenContent != null) {
                Files.writeString(Paths.get("schwab-api.json"), originalTokenContent);
            }
            
            String testTokenFile = "test-tokens.json";
            if (Files.exists(Paths.get(testTokenFile))) {
                Files.delete(Paths.get(testTokenFile));
            }
        } catch (Exception e) {
            // Ignore restoration errors
        }
    }
}