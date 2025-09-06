package com.higgstx.schwabapi.service;

import com.higgstx.schwabapi.config.SchwabApiProperties;
import com.higgstx.schwabapi.model.market.DailyPriceData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for bulk historical data functionality in MarketDataService
 */
class BulkHistoricalDataTest {

    private MarketDataService marketDataService;
    private boolean hadTokenFile = false;
    private String originalTokenContent = null;

    @BeforeEach
    void setUp() {
        // Backup and remove token file to ensure clean test environment
        backupAndRemoveTokenFile();

        try {
            // Create with test properties
            SchwabApiProperties testProps = new SchwabApiProperties(
                    "https://api.schwabapi.com/v1/oauth/authorize",
                    "https://api.schwabapi.com/v1/oauth/token",
                    "https://api.schwabapi.com/marketdata/v1",
                    "https://127.0.0.1:8182",
                    "readonly",
                    30000
            );
            marketDataService = new MarketDataService(testProps);
        } catch (Exception e) {
            // Fallback to default constructor if properties fail
            try {
                marketDataService = new MarketDataService();
            } catch (Exception ex) {
                // Create with minimal test properties
                SchwabApiProperties fallbackProps = new SchwabApiProperties(
                        "https://test.api.com/auth",
                        "https://test.api.com/token",
                        "https://test.api.com/market",
                        "http://localhost:8080",
                        "readonly",
                        5000
                );
                marketDataService = new MarketDataService(fallbackProps);
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
        } catch (Exception e) {
            // Ignore errors during cleanup
        }
    }

    private void restoreTokenFile() {
        try {
            if (hadTokenFile && originalTokenContent != null) {
                Files.writeString(Paths.get("schwab-api.json"), originalTokenContent);
            }
        } catch (Exception e) {
            // Ignore errors during restoration
        }
    }

    @Nested
    @DisplayName("Bulk Historical Data Operations")
    class BulkHistoricalDataOperationsTest {

        

        @Test
        @DisplayName("Should handle bulk request with explicit access token and return error data")
        void testGetBulkHistoricalData_WithFakeToken_ReturnsErrorData() {
            // Given
            String[] symbols = {"AAPL", "GOOGL"};
            String fakeToken = "fake-access-token";

            // When
            assertDoesNotThrow(() -> {
                List<DailyPriceData> result = marketDataService.getBulkHistoricalData(symbols, fakeToken);
                
                // Then - Should return error data for each symbol (API calls fail but method continues)
                assertNotNull(result);
                assertEquals(2, result.size());
                
                // Check that all returned data points indicate errors
                for (DailyPriceData data : result) {
                    assertFalse(data.isSuccess(), "Data should indicate error");
                    assertNotNull(data.getErrorMessage(), "Error message should be present");
                    assertTrue(symbols[0].equals(data.getSymbol()) || symbols[1].equals(data.getSymbol()));
                }
            });
        }

        @Test
        @DisplayName("Should skip null and empty symbols")
        void testGetBulkHistoricalData_WithNullAndEmptySymbols_SkipsInvalidSymbols() {
            // Given
            String[] symbolsWithNulls = {"AAPL", null, "", "  ", "GOOGL"};
            String fakeToken = "fake-access-token";

            // When - This will return error data for valid symbols, skip invalid ones
            assertDoesNotThrow(() -> {
                List<DailyPriceData> result = marketDataService.getBulkHistoricalData(symbolsWithNulls, fakeToken);
                
                // Should return 2 error entries (for AAPL and GOOGL), skip the rest
                assertNotNull(result);
                assertEquals(2, result.size());
                
                // All should be errors due to fake token, but only valid symbols processed
                for (DailyPriceData data : result) {
                    assertFalse(data.isSuccess());
                    assertTrue("AAPL".equals(data.getSymbol()) || "GOOGL".equals(data.getSymbol()));
                }
            });
        }

        @Test
        @DisplayName("Should normalize symbol case")
        void testGetBulkHistoricalData_CaseNormalization() {
            // Given
            String[] mixedCaseSymbols = {"aapl", "GOOGL", "MsFt"};
            String fakeToken = "fake-access-token";

            // When
            assertDoesNotThrow(() -> {
                List<DailyPriceData> result = marketDataService.getBulkHistoricalData(mixedCaseSymbols, fakeToken);
                
                // Should return 3 error entries with normalized symbols
                assertNotNull(result);
                assertEquals(3, result.size());
                
                // Check that symbols are normalized to uppercase
                for (DailyPriceData data : result) {
                    assertFalse(data.isSuccess());
                    assertTrue("AAPL".equals(data.getSymbol()) || 
                              "GOOGL".equals(data.getSymbol()) || 
                              "MSFT".equals(data.getSymbol()));
                }
            });
        }
    }

    @Nested
    @DisplayName("DailyPriceData Model Tests")
    class DailyPriceDataModelTest {

        @Test
        @DisplayName("Should create successful DailyPriceData with all fields")
        void testDailyPriceData_Success_AllFields() {
            // Given
            String symbol = "AAPL";
            Long datetime = System.currentTimeMillis();
            Double open = 150.0;
            Double high = 155.0;
            Double low = 149.0;
            Double close = 152.0;
            Long volume = 1000000L;

            // When
            DailyPriceData data = DailyPriceData.success(symbol, datetime, open, high, low, close, volume);

            // Then
            assertNotNull(data);
            assertTrue(data.isSuccess());
            assertNull(data.getErrorMessage());
            assertEquals(symbol, data.getSymbol());
            assertEquals(datetime, data.getDatetime());
            assertEquals(open, data.getOpen());
            assertEquals(high, data.getHigh());
            assertEquals(low, data.getLow());
            assertEquals(close, data.getClose());
            assertEquals(volume, data.getVolume());
            assertNotNull(data.getLocalDate());
        }

        @Test
        @DisplayName("Should create error DailyPriceData")
        void testDailyPriceData_Error() {
            // Given
            String symbol = "INVALID";
            String errorMessage = "Symbol not found";

            // When
            DailyPriceData data = DailyPriceData.error(symbol, errorMessage);

            // Then
            assertNotNull(data);
            assertFalse(data.isSuccess());
            assertEquals(errorMessage, data.getErrorMessage());
            assertEquals(symbol, data.getSymbol());
            assertNull(data.getOpen());
            assertNull(data.getHigh());
            assertNull(data.getLow());
            assertNull(data.getClose());
            assertNull(data.getVolume());
        }

        @Test
        @DisplayName("Should create error DailyPriceData with date")
        void testDailyPriceData_ErrorWithDate() {
            // Given
            String symbol = "INVALID";
            LocalDate date = LocalDate.of(2024, 1, 15);
            String errorMessage = "No data for date";

            // When
            DailyPriceData data = DailyPriceData.error(symbol, date, errorMessage);

            // Then
            assertNotNull(data);
            assertFalse(data.isSuccess());
            assertEquals(errorMessage, data.getErrorMessage());
            assertEquals(symbol, data.getSymbol());
            assertEquals(date, data.getDate());
        }

        @Test
        @DisplayName("Should handle null datetime in getLocalDate")
        void testDailyPriceData_NullDatetime_GetLocalDate() {
            // Given
            DailyPriceData data = new DailyPriceData();
            data.setSymbol("TEST");
            data.setDatetime(null);
            data.setDate(null);

            // When
            LocalDate result = data.getLocalDate();

            // Then
            assertNull(result);
        }

        @Test
        @DisplayName("Should convert datetime to LocalDate")
        void testDailyPriceData_DatetimeConversion() {
            // Given
            // January 15, 2024 at midnight UTC
            Long timestamp = 1705276800000L; // 2024-01-15 00:00:00 UTC
            
            DailyPriceData data = DailyPriceData.success("TEST", timestamp, 100.0, 101.0, 99.0, 100.5, 50000L);

            // When
            LocalDate localDate = data.getLocalDate();

            // Then
            assertNotNull(localDate);
            // Note: The exact date depends on system timezone, but should be around Jan 14-15, 2024
            assertTrue(localDate.getYear() == 2024);
            assertTrue(localDate.getMonthValue() == 1);
            assertTrue(localDate.getDayOfMonth() >= 14 && localDate.getDayOfMonth() <= 15);
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesTest {

        @Test
        @DisplayName("Should handle whitespace-only symbols by skipping them")
        void testGetBulkHistoricalData_WhitespaceSymbols() {
            // Given
            String[] whitespaceSymbols = {"  ", "\t", "\n"};
            String fakeToken = "fake-access-token";

            // When & Then - Should not crash, skips all whitespace symbols
            assertDoesNotThrow(() -> {
                List<DailyPriceData> result = marketDataService.getBulkHistoricalData(whitespaceSymbols, fakeToken);
                
                // Should return empty list since all symbols are skipped
                assertNotNull(result);
                assertEquals(0, result.size());
            });
        }

        @Test
        @DisplayName("Should handle very long symbol names")
        void testGetBulkHistoricalData_LongSymbolNames() {
            // Given
            String[] longSymbols = {"A".repeat(100), "B".repeat(50)};
            String fakeToken = "fake-access-token";

            // When & Then - Should not crash, will return error data
            assertDoesNotThrow(() -> {
                List<DailyPriceData> result = marketDataService.getBulkHistoricalData(longSymbols, fakeToken);
                
                // Should return 2 error entries
                assertNotNull(result);
                assertEquals(2, result.size());
                
                for (DailyPriceData data : result) {
                    assertFalse(data.isSuccess());
                    assertNotNull(data.getErrorMessage());
                }
            });
        }

        @Test
        @DisplayName("Should handle single symbol array")
        void testGetBulkHistoricalData_SingleSymbol() {
            // Given
            String[] singleSymbol = {"AAPL"};
            String fakeToken = "fake-access-token";

            // When & Then - Should not crash, will return error data
            assertDoesNotThrow(() -> {
                List<DailyPriceData> result = marketDataService.getBulkHistoricalData(singleSymbol, fakeToken);
                
                // Should return 1 error entry
                assertNotNull(result);
                assertEquals(1, result.size());
                assertFalse(result.get(0).isSuccess());
                assertEquals("AAPL", result.get(0).getSymbol());
            });
        }

        
    }

    @Nested
    @DisplayName("Utility Method Tests")
    class UtilityMethodsTest {

        @Test
        @DisplayName("Should handle service close gracefully")
        void testClose_GracefulShutdown() {
            // When & Then
            assertDoesNotThrow(() -> {
                SchwabApiProperties testProps = new SchwabApiProperties(
                        "https://api.schwabapi.com/v1/oauth/authorize",
                        "https://api.schwabapi.com/v1/oauth/token",
                        "https://api.schwabapi.com/marketdata/v1",
                        "https://127.0.0.1:8182",
                        "readonly",
                        30000
                );
                MarketDataService service = new MarketDataService(testProps);
                service.close();
            });
        }

        @Test
        @DisplayName("Should handle multiple close calls")
        void testClose_MultipleCalls() {
            // When & Then
            assertDoesNotThrow(() -> {
                SchwabApiProperties testProps = new SchwabApiProperties(
                        "https://api.schwabapi.com/v1/oauth/authorize",
                        "https://api.schwabapi.com/v1/oauth/token",
                        "https://api.schwabapi.com/marketdata/v1",
                        "https://127.0.0.1:8182",
                        "readonly",
                        30000
                );
                MarketDataService service = new MarketDataService(testProps);
                service.close();
                service.close(); // Should not throw exception on second close
            });
        }
    }
}