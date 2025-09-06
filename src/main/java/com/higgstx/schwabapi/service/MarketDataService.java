package com.higgstx.schwabapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.higgstx.schwabapi.config.SchwabOAuthClient;
import com.higgstx.schwabapi.config.SchwabApiProperties;
import com.higgstx.schwabapi.exception.SchwabApiException;
import com.higgstx.schwabapi.model.ApiResponse;
import com.higgstx.schwabapi.model.TokenResponse;
import com.higgstx.schwabapi.model.market.*;
import com.higgstx.schwabapi.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service class for market data operations - Spring-managed only
 */
public class MarketDataService implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(MarketDataService.class);
    
    private final SchwabOAuthClient client;
    private final ObjectMapper objectMapper;
    private final TokenManager tokenManager;

    /**
     * Constructor with dependencies injected by Spring
     */
    public MarketDataService(SchwabApiProperties properties, TokenManager tokenManager) throws SchwabApiException {
        this.client = new SchwabOAuthClient(properties, true);
        this.objectMapper = UtilityClass.getObjectMapper();
        this.tokenManager = tokenManager;
    }

    /**
     * Ensures the service is ready by attempting automated token refresh if needed.
     */
    public boolean ensureServiceReady(String operation) {
        logger.debug("Checking service readiness for operation: {}", operation);

        String tokenStatus = getTokenStatus();
        logger.debug("Current token status: {}", tokenStatus);

        if (!isReady()) {
            logger.info("Service not ready - attempting automated token refresh for operation: {}", operation);

            try {
                // Try to load tokens with auto-refresh enabled
                TokenResponse refreshedTokens = tokenManager.loadTokens(true);

                if (refreshedTokens != null && refreshedTokens.isAccessTokenValid()) {
                    logger.info("SUCCESS: Automated token refresh completed for operation: {}", operation);
                    logger.debug("Service is now ready for API calls");
                    return true;
                } else if (refreshedTokens != null && refreshedTokens.isRefreshTokenValid()) {
                    // Try force refresh if auto-refresh didn't work
                    logger.info("Auto-refresh didn't work for {}, attempting force refresh...", operation);
                    refreshedTokens = tokenManager.forceTokenRefresh();

                    if (refreshedTokens != null && refreshedTokens.isAccessTokenValid()) {
                        logger.info("SUCCESS: Force token refresh completed for operation: {}", operation);
                        logger.debug("Service is now ready for API calls");
                        return true;
                    } else {
                        logger.error("ERROR: Force refresh failed or returned invalid tokens for operation: {}", operation);
                        logger.warn("Manual re-authentication required. Please run OAuth authorization.");
                        return false;
                    }
                } else {
                    logger.error("ERROR: No valid refresh token available for operation: {}", operation);
                    logger.warn("Manual authentication required. Please run OAuth authorization.");
                    return false;
                }
            } catch (Exception e) {
                logger.error("ERROR: Automated token refresh failed for operation {}: {}", operation, e.getMessage());
                logger.warn("Manual re-authentication required. Please run OAuth authorization.");
                logger.debug("Token refresh exception details", e);
                return false;
            }
        }

        logger.debug("Service already ready for operation: {}", operation);
        return true;
    }

    /**
     * Ensures the service is ready with a default operation name.
     */
    public boolean ensureServiceReady() {
        return ensureServiceReady("API operation");
    }

    /**
     * Gets price history for a symbol and returns parsed DailyPriceData objects
     */
    public List<DailyPriceData> getPriceHistoryData(String symbol, String periodType,
            int period, String frequencyType, int frequency) throws SchwabApiException {
        return getPriceHistoryData(symbol, periodType, period, frequencyType, frequency, 
                tokenManager.getValidAccessToken());
    }

    public List<DailyPriceData> getPriceHistoryData(String symbol, String periodType,
            int period, String frequencyType, int frequency, String accessToken) throws SchwabApiException {
        
        UtilityClass.validateParameter(symbol, "Symbol");
        String cleanSymbol = StringUtils.normalizeSymbol(symbol);
        
        if (cleanSymbol == null) {
            throw SchwabApiException.validationError("Invalid symbol: " + symbol);
        }

        try {
            // Use the existing working getPriceHistory method
            ApiResponse response = getPriceHistory(cleanSymbol, periodType, period,
                    frequencyType, frequency, accessToken);

            if (HttpUtils.isSuccessCode(response.getStatusCode())) {
                // Parse the response into DailyPriceData objects
                return parsePriceHistoryResponse(cleanSymbol, response.getBody());
            } else {
                logger.error("API error for symbol {}: HTTP {}, Body: {}",
                        cleanSymbol, response.getStatusCode(), response.getBody());

                // Create API exception from response
                throw SchwabApiException.fromApiResponse("get price history for symbol: " + cleanSymbol, response);
            }
        } catch (SchwabApiException e) {
            throw e; // Re-throw SchwabApiException as-is
        } catch (Exception e) {
            logger.error("Error fetching data for symbol {}: {}", cleanSymbol, e.getMessage());
            throw SchwabApiException.serverError("Failed to fetch price history for symbol: " + cleanSymbol + " - " + e.getMessage());
        }
    }

    /**
     * Gets historical price data for multiple symbols (1 month of daily data per symbol)
     */
    public List<DailyPriceData> getBulkHistoricalData(String[] symbols) throws SchwabApiException {
        return getBulkHistoricalData(symbols, tokenManager.getValidAccessToken());
    }

    public List<DailyPriceData> getBulkHistoricalData(String[] symbols, String accessToken) throws SchwabApiException {
        UtilityClass.validateNotNull(symbols, "Symbols array");
        if (symbols.length == 0) {
            throw SchwabApiException.validationError("Symbols array cannot be empty");
        }

        List<DailyPriceData> allData = new ArrayList<>();

        for (String symbol : symbols) {
            if (StringUtils.isBlank(symbol)) {
                logger.warn("Skipping null or empty symbol");
                continue;
            }

            String cleanSymbol = StringUtils.normalizeSymbol(symbol);
            logger.debug("Fetching historical data for symbol: {}", cleanSymbol);

            try {
                // Call getPriceHistory
                ApiResponse response = getPriceHistory(
                        cleanSymbol,
                        "month", // periodType
                        1, // period (1 month)
                        "daily", // frequencyType
                        1, // frequency (1 day)
                        accessToken
                );

                if (HttpUtils.isSuccessCode(response.getStatusCode())) {
                    String responseBody = response.getBody();

                    // Parse the response
                    List<DailyPriceData> symbolData = parsePriceHistoryResponse(cleanSymbol, responseBody);
                    allData.addAll(symbolData);

                    long successCount = symbolData.stream().filter(DailyPriceData::isSuccess).count();
                    logger.debug("Successfully processed {} data points for {} ({} successful)",
                            symbolData.size(), cleanSymbol, successCount);
                } else {
                    logger.error("HTTP error for symbol {}: Status={}, Body={}",
                            cleanSymbol, response.getStatusCode(),
                            response.getBody() != null ? response.getBody() : "null");

                    // Add error data instead of throwing exception (graceful degradation)
                    allData.add(DailyPriceData.error(cleanSymbol,
                            "HTTP " + response.getStatusCode() + ": " + response.getBody()));
                }

                // Add delay between requests
                UtilityClass.safeSleep(100);

            } catch (SchwabApiException e) {
                logger.error("API exception fetching data for symbol {}: {}", cleanSymbol, e.getMessage(), e);
                allData.add(DailyPriceData.error(cleanSymbol, e.getDisplayMessage()));
            } catch (Exception e) {
                logger.error("Exception fetching data for symbol {}: {}", cleanSymbol, e.getMessage(), e);
                allData.add(DailyPriceData.error(cleanSymbol, "Exception: " + e.getMessage()));
            }
        }

        logger.info("Bulk fetch completed: {} total data points, {} symbols processed",
                allData.size(), symbols.length);

        return allData;
    }

    /**
     * Parses the Schwab API price history response into DailyPriceData objects
     */
    private List<DailyPriceData> parsePriceHistoryResponse(String symbol, String jsonResponse) {
        List<DailyPriceData> dailyData = new ArrayList<>();

        try {
            if (StringUtils.isBlank(jsonResponse)) {
                dailyData.add(DailyPriceData.error(symbol, "Empty API response"));
                return dailyData;
            }

            Map<String, Object> response = JsonUtils.parseJsonToMap(jsonResponse, objectMapper);

            // Check for candles array
            Object candlesObj = response.get("candles");
            if (candlesObj == null) {
                dailyData.add(DailyPriceData.error(symbol, "No candles data in API response"));
                return dailyData;
            }

            if (!(candlesObj instanceof List)) {
                dailyData.add(DailyPriceData.error(symbol, "Invalid candles data format"));
                return dailyData;
            }

            @SuppressWarnings("unchecked")
            List<Object> candles = (List<Object>) candlesObj;

            if (candles.isEmpty()) {
                dailyData.add(DailyPriceData.error(symbol, "No historical data available"));
                return dailyData;
            }

            for (Object candleObj : candles) {
                // Handle candles as objects (new Schwab API format)
                if (candleObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> candle = (Map<String, Object>) candleObj;

                    try {
                        // Extract values from the candle object using utility functions
                        Long datetime = ConversionUtils.convertToLong(candle.get("datetime"));
                        Double open = ConversionUtils.convertToDouble(candle.get("open"));
                        Double high = ConversionUtils.convertToDouble(candle.get("high"));
                        Double low = ConversionUtils.convertToDouble(candle.get("low"));
                        Double close = ConversionUtils.convertToDouble(candle.get("close"));
                        Long volume = ConversionUtils.convertToLong(candle.get("volume"));

                        DailyPriceData dailyPrice = DailyPriceData.success(
                                symbol, datetime, open, high, low, close, volume);
                        dailyData.add(dailyPrice);
                    } catch (Exception e) {
                        logger.warn("Error parsing individual candle for symbol {}: {}", symbol, e.getMessage());
                    }
                }
            }

            if (dailyData.isEmpty()) {
                dailyData.add(DailyPriceData.error(symbol, "No historical data available"));
            }

        } catch (Exception e) {
            logger.error("Exception parsing response for symbol {}: {}", symbol, e.getMessage(), e);
            dailyData.add(DailyPriceData.error(symbol, "Error parsing response: " + e.getMessage()));
        }

        return dailyData;
    }

    /**
     * Gets a single quote for a ticker symbol.
     */
    public QuoteData getQuote(String ticker) throws SchwabApiException {
        return getQuote(ticker, tokenManager.getValidAccessToken());
    }

    public QuoteData getQuote(String ticker, String accessToken) throws SchwabApiException {
        List<QuoteData> results = getQuotes(List.of(ticker), accessToken);
        if (results != null && !results.isEmpty()) {
            return results.get(0);
        } else {
            return QuoteData.error(ticker, "No data returned");
        }
    }

    /**
     * Gets quotes for a list of symbols
     */
    public List<QuoteData> getQuotes(List<String> symbols) throws SchwabApiException {
        return getQuotes(symbols, tokenManager.getValidAccessToken());
    }

    public List<QuoteData> getQuotes(List<String> symbols, String accessToken) throws SchwabApiException {
        UtilityClass.validateNotNull(symbols, "Symbols list");
        if (symbols.isEmpty()) {
            throw SchwabApiException.validationError("Symbols list cannot be empty");
        }

        try {
            ApiResponse response = client.getQuotes(symbols.toArray(new String[0]), accessToken);
            List<QuoteData> quoteDataList = new ArrayList<>();

            if (HttpUtils.isSuccessCode(response.getStatusCode())) {
                try {
                    Map<String, Object> data = JsonUtils.parseJsonToMap(response.getBody(), objectMapper);
                    
                    for (String symbol : symbols) {
                        Object quoteObject = data.get(symbol);
                        if (quoteObject instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> quoteMap = (Map<String, Object>) quoteObject;

                            Object statusObj = quoteMap.get("status");
                            String status = statusObj != null ? statusObj.toString() : null;

                            if ("SUCCESS".equals(status)) {
                                Double closePrice = ConversionUtils.convertToDouble(quoteMap.get("closePrice"));
                                Double highPrice = ConversionUtils.convertToDouble(quoteMap.get("highPrice"));
                                Double lowPrice = ConversionUtils.convertToDouble(quoteMap.get("lowPrice"));
                                Double openPrice = ConversionUtils.convertToDouble(quoteMap.get("openPrice"));
                                Long totalVolume = ConversionUtils.convertToLong(quoteMap.get("totalVolume"));

                                quoteDataList.add(QuoteData.success(symbol, closePrice, highPrice, lowPrice, openPrice, totalVolume));
                            } else {
                                quoteDataList.add(QuoteData.notFound(symbol));
                            }
                        } else {
                            quoteDataList.add(QuoteData.notFound(symbol));
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error parsing API response: {}", e.getMessage());
                    throw SchwabApiException.serverError("Failed to parse quotes response: " + e.getMessage());
                }
            } else {
                logger.error("API call failed with status code: {}", response.getStatusCode());
                throw SchwabApiException.fromApiResponse("get quotes", response);
            }
            return quoteDataList;
        } catch (SchwabApiException e) {
            throw e; // Re-throw SchwabApiException as-is
        }
    }

    /**
     * Gets price history for a symbol (returns raw ApiResponse for backward compatibility)
     */
    public ApiResponse getPriceHistory(String symbol, String periodType, int period,
            String frequencyType, int frequency) throws SchwabApiException {
        return getPriceHistory(symbol, periodType, period, frequencyType, frequency, 
                tokenManager.getValidAccessToken());
    }

    public ApiResponse getPriceHistory(String symbol, String periodType, int period,
            String frequencyType, int frequency, String accessToken) throws SchwabApiException {
        return client.getPriceHistory(symbol, periodType, period, frequencyType, frequency, accessToken);
    }

    /**
     * Gets market hours for a market type
     */
    public ApiResponse getMarketHours(String marketType) throws SchwabApiException {
        return getMarketHours(marketType, tokenManager.getValidAccessToken());
    }

    public ApiResponse getMarketHours(String marketType, String accessToken) throws SchwabApiException {
        return client.getMarketHours(marketType, accessToken);
    }

    /**
     * Check if service is ready (has valid tokens)
     */
    public boolean isReady() {
        return tokenManager.hasValidTokens();
    }

    /**
     * Get token status for debugging
     */
    public String getTokenStatus() {
        try {
            TokenResponse tokens = tokenManager.loadTokens(false);
            if (tokens == null) {
                return "NO TOKENS";
            }
            return tokens.getQuickStatus();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Get the TokenManager instance (for advanced usage)
     */
    public TokenManager getTokenManager() {
        return tokenManager;
    }

    @Override
    public void close() {
        if (client != null) {
            client.close();
        }
    }
}