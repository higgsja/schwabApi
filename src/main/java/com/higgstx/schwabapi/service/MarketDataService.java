package com.higgstx.schwabapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.higgstx.schwabapi.config.SchwabOAuthClient;
import com.higgstx.schwabapi.config.SchwabApiProperties;
import com.higgstx.schwabapi.exception.SchwabApiException;
import com.higgstx.schwabapi.model.ApiResponse;
import com.higgstx.schwabapi.model.market.*;
import com.higgstx.schwabapi.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Streamlined market data service
 */
@Slf4j
@RequiredArgsConstructor
public class MarketDataService implements AutoCloseable {
    
    private final SchwabOAuthClient client;
    private final ObjectMapper objectMapper;
    private final TokenManager tokenManager;

    /**
     * Constructor for Spring injection
     */
    public MarketDataService(SchwabApiProperties properties, TokenManager tokenManager) throws SchwabApiException {
        this.client = new SchwabOAuthClient(properties, true);
        this.objectMapper = UtilityClass.getObjectMapper();
        this.tokenManager = tokenManager;
    }

    // Quote operations
    public QuoteData getQuote(String ticker) throws SchwabApiException {
        List<QuoteData> results = getQuotes(List.of(ticker));
        return results.isEmpty() ? QuoteData.error(ticker, "No data returned") : results.get(0);
    }

    public List<QuoteData> getQuotes(List<String> symbols) throws SchwabApiException {
        UtilityClass.validateNotNull(symbols, "Symbols list");
        if (symbols.isEmpty()) {
            throw SchwabApiException.validationError("Symbols list cannot be empty");
        }

        ensureServiceReady("get quotes");
        
        try {
            ApiResponse response;
            response = client.getQuotes(symbols.toArray(String[]::new), tokenManager.getValidAccessToken());
            
            if (!HttpUtils.isSuccessCode(response.getStatusCode())) {
                throw SchwabApiException.fromApiResponse("get quotes", response);
            }

            return parseQuoteResponse(symbols, response.getBody());
            
        } catch (SchwabApiException e) {
            throw e;
        } catch (Exception e) {
            throw SchwabApiException.serverError("Failed to get quotes: " + e.getMessage());
        }
    }

    // Price history operations
    public List<DailyPriceData> getPriceHistoryData(String symbol, String periodType,
            int period, String frequencyType, int frequency) throws SchwabApiException {
        
        String cleanSymbol = StringUtils.normalizeSymbol(symbol);
        if (cleanSymbol == null) {
            throw SchwabApiException.validationError("Invalid symbol: " + symbol);
        }

        ensureServiceReady("get price history");

        try {
            ApiResponse response = client.getPriceHistory(cleanSymbol, periodType, period,
                    frequencyType, frequency, tokenManager.getValidAccessToken());

            if (!HttpUtils.isSuccessCode(response.getStatusCode())) {
                throw SchwabApiException.fromApiResponse("get price history for symbol: " + cleanSymbol, response);
            }

            return parsePriceHistoryResponse(cleanSymbol, response.getBody());
            
        } catch (SchwabApiException e) {
            throw e;
        } catch (Exception e) {
            throw SchwabApiException.serverError("Failed to fetch price history for symbol: " + cleanSymbol + " - " + e.getMessage());
        }
    }

    public List<DailyPriceData> getBulkHistoricalData(String[] symbols) throws SchwabApiException {
        UtilityClass.validateNotNull(symbols, "Symbols array");
        if (symbols.length == 0) {
            throw SchwabApiException.validationError("Symbols array cannot be empty");
        }

        ensureServiceReady("get bulk historical data");
        List<DailyPriceData> allData = new ArrayList<>();

        for (String symbol : symbols) {
            String cleanSymbol = StringUtils.normalizeSymbol(symbol);
            if (cleanSymbol == null) continue;

            try {
                List<DailyPriceData> symbolData = getPriceHistoryData(cleanSymbol, "month", 1, "daily", 1);
                allData.addAll(symbolData);
                UtilityClass.safeSleep(100); // Rate limiting
            } catch (SchwabApiException e) {
                allData.add(DailyPriceData.error(cleanSymbol, "Exception: " + e.getMessage()));
            }
        }

        return allData;
    }

    // Delegate methods for backward compatibility
    public ApiResponse getPriceHistory(String symbol, String periodType, int period,
            String frequencyType, int frequency) throws SchwabApiException {
        return client.getPriceHistory(symbol, periodType, period, frequencyType, frequency, 
                tokenManager.getValidAccessToken());
    }

    public ApiResponse getMarketHours(String marketType) throws SchwabApiException {
        return client.getMarketHours(marketType, tokenManager.getValidAccessToken());
    }

    // Status methods
    public boolean isReady() {
        return tokenManager.hasValidTokens();
    }

    public String getTokenStatus() {
        try {
            if (!tokenManager.hasValidTokens()) {
                return tokenManager.hasUsableTokens() ? "REFRESH_NEEDED" : "NO_TOKENS";
            }
            return "READY";
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    public TokenManager getTokenManager() {
        return tokenManager;
    }

    // Private helper methods
    public void ensureServiceReady(String operation) throws SchwabApiException {
        if (!isReady()) {
            log.info("Service not ready - attempting token refresh for: {}", operation);
            try {
                tokenManager.forceTokenRefresh();
                log.info("Token refresh completed for: {}", operation);
            } catch (SchwabApiException e) {
                throw SchwabApiException.tokenError("Token refresh failed for " + operation + ": " + e.getMessage());
            }
        }
    }

    private List<QuoteData> parseQuoteResponse(List<String> symbols, String jsonResponse) {
        List<QuoteData> quoteDataList = new ArrayList<>();
        
        try {
            Map<String, Object> data = JsonUtils.parseJsonToMap(jsonResponse, objectMapper);
            
            for (String symbol : symbols) {
                Object quoteObject = data.get(symbol);
                if (quoteObject instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> quoteMap = (Map<String, Object>) quoteObject;

                    String status = quoteMap.getOrDefault("status", "").toString();

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
            log.error("Error parsing quote response: {}", e.getMessage());
            // Add error entries for all symbols
            symbols.forEach(symbol -> quoteDataList.add(QuoteData.error(symbol, "Parse error: " + e.getMessage())));
        }
        
        return quoteDataList;
    }

    private List<DailyPriceData> parsePriceHistoryResponse(String symbol, String jsonResponse) {
        List<DailyPriceData> dailyData = new ArrayList<>();

        try {
            if (StringUtils.isBlank(jsonResponse)) {
                dailyData.add(DailyPriceData.error(symbol, "Empty API response"));
                return dailyData;
            }

            Map<String, Object> response = JsonUtils.parseJsonToMap(jsonResponse, objectMapper);
            Object candlesObj = response.get("candles");
            
            if (!(candlesObj instanceof List)) {
                dailyData.add(DailyPriceData.error(symbol, "No candles data in API response"));
                return dailyData;
            }

            @SuppressWarnings("unchecked")
            List<Object> candles = (List<Object>) candlesObj;

            if (candles.isEmpty()) {
                dailyData.add(DailyPriceData.error(symbol, "No historical data available"));
                return dailyData;
            }

            for (Object candleObj : candles) {
                if (candleObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> candle = (Map<String, Object>) candleObj;

                    try {
                        Long datetime = ConversionUtils.convertToLong(candle.get("datetime"));
                        Double open = ConversionUtils.convertToDouble(candle.get("open"));
                        Double high = ConversionUtils.convertToDouble(candle.get("high"));
                        Double low = ConversionUtils.convertToDouble(candle.get("low"));
                        Double close = ConversionUtils.convertToDouble(candle.get("close"));
                        Long volume = ConversionUtils.convertToLong(candle.get("volume"));

                        dailyData.add(DailyPriceData.success(symbol, datetime, open, high, low, close, volume));
                    } catch (Exception e) {
                        log.warn("Error parsing individual candle for symbol {}: {}", symbol, e.getMessage());
                    }
                }
            }

            if (dailyData.isEmpty()) {
                dailyData.add(DailyPriceData.error(symbol, "No valid historical data found"));
            }

        } catch (Exception e) {
            log.error("Exception parsing response for symbol {}: {}", symbol, e.getMessage());
            dailyData.add(DailyPriceData.error(symbol, "Error parsing response: " + e.getMessage()));
        }

        return dailyData;
    }

    @Override
    public void close() {
        if (client != null) {
            client.close();
        }
    }
}