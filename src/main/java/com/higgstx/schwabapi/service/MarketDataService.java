package com.higgstx.schwabapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.higgstx.schwabapi.config.SchwabOAuthClient;
import com.higgstx.schwabapi.config.SchwabApiProperties;
import com.higgstx.schwabapi.exception.SchwabApiException;
import com.higgstx.schwabapi.model.ApiResponse;
import com.higgstx.schwabapi.model.TokenResponse;
import com.higgstx.schwabapi.model.market.*;
import com.higgstx.schwabapi.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Market data service with consistent @Slf4j and @RequiredArgsConstructor usage
 */
@Slf4j
@RequiredArgsConstructor
public class MarketDataService implements AutoCloseable {
    
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

    public boolean ensureServiceReady(String operation) {
        log.debug("Checking service readiness for operation: {}", operation);

        if (!isReady()) {
            log.info("Service not ready - attempting automated token refresh for operation: {}", operation);
            try {
                TokenResponse refreshedTokens = tokenManager.loadTokens(true);
                if (refreshedTokens != null && refreshedTokens.isAccessTokenValid()) {
                    log.info("SUCCESS: Automated token refresh completed for operation: {}", operation);
                    return true;
                }
                
                if (refreshedTokens != null && refreshedTokens.isRefreshTokenValid()) {
                    refreshedTokens = tokenManager.forceTokenRefresh();
                    if (refreshedTokens != null && refreshedTokens.isAccessTokenValid()) {
                        log.info("SUCCESS: Force token refresh completed for operation: {}", operation);
                        return true;
                    }
                }
                
                log.error("ERROR: Token refresh failed for operation: {}", operation);
                return false;
            } catch (Exception e) {
                log.error("ERROR: Automated token refresh failed for operation {}: {}", operation, e.getMessage());
                return false;
            }
        }
        return true;
    }

    public boolean ensureServiceReady() {
        return ensureServiceReady("API operation");
    }

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
            ApiResponse response = getPriceHistory(cleanSymbol, periodType, period,
                    frequencyType, frequency, accessToken);

            if (HttpUtils.isSuccessCode(response.getStatusCode())) {
                return parsePriceHistoryResponse(cleanSymbol, response.getBody());
            } else {
                throw SchwabApiException.fromApiResponse("get price history for symbol: " + cleanSymbol, response);
            }
        } catch (SchwabApiException e) {
            throw e;
        } catch (Exception e) {
            throw SchwabApiException.serverError("Failed to fetch price history for symbol: " + cleanSymbol + " - " + e.getMessage());
        }
    }

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
                continue;
            }

            String cleanSymbol = StringUtils.normalizeSymbol(symbol);
            try {
                ApiResponse response = getPriceHistory(cleanSymbol, "month", 1, "daily", 1, accessToken);

                if (HttpUtils.isSuccessCode(response.getStatusCode())) {
                    List<DailyPriceData> symbolData = parsePriceHistoryResponse(cleanSymbol, response.getBody());
                    allData.addAll(symbolData);
                } else {
                    allData.add(DailyPriceData.error(cleanSymbol,
                            "HTTP " + response.getStatusCode() + ": " + response.getBody()));
                }

                UtilityClass.safeSleep(100);

            } catch (Exception e) {
                allData.add(DailyPriceData.error(cleanSymbol, "Exception: " + e.getMessage()));
            }
        }

        return allData;
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

                        DailyPriceData dailyPrice = DailyPriceData.success(
                                symbol, datetime, open, high, low, close, volume);
                        dailyData.add(dailyPrice);
                    } catch (Exception e) {
                        log.warn("Error parsing individual candle for symbol {}: {}", symbol, e.getMessage());
                    }
                }
            }

            if (dailyData.isEmpty()) {
                dailyData.add(DailyPriceData.error(symbol, "No historical data available"));
            }

        } catch (Exception e) {
            log.error("Exception parsing response for symbol {}: {}", symbol, e.getMessage());
            dailyData.add(DailyPriceData.error(symbol, "Error parsing response: " + e.getMessage()));
        }

        return dailyData;
    }

    // Delegate methods
    public QuoteData getQuote(String ticker) throws SchwabApiException {
        return getQuote(ticker, tokenManager.getValidAccessToken());
    }

    public QuoteData getQuote(String ticker, String accessToken) throws SchwabApiException {
        List<QuoteData> results = getQuotes(List.of(ticker), accessToken);
        return results != null && !results.isEmpty() ? results.get(0) : QuoteData.error(ticker, "No data returned");
    }

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
            } else {
                throw SchwabApiException.fromApiResponse("get quotes", response);
            }
            return quoteDataList;
        } catch (SchwabApiException e) {
            throw e;
        }
    }

    public ApiResponse getPriceHistory(String symbol, String periodType, int period,
            String frequencyType, int frequency) throws SchwabApiException {
        return getPriceHistory(symbol, periodType, period, frequencyType, frequency, 
                tokenManager.getValidAccessToken());
    }

    public ApiResponse getPriceHistory(String symbol, String periodType, int period,
            String frequencyType, int frequency, String accessToken) throws SchwabApiException {
        return client.getPriceHistory(symbol, periodType, period, frequencyType, frequency, accessToken);
    }

    public ApiResponse getMarketHours(String marketType) throws SchwabApiException {
        return getMarketHours(marketType, tokenManager.getValidAccessToken());
    }

    public ApiResponse getMarketHours(String marketType, String accessToken) throws SchwabApiException {
        return client.getMarketHours(marketType, accessToken);
    }

    public boolean isReady() {
        return tokenManager.hasValidTokens();
    }

    public String getTokenStatus() {
        try {
            TokenResponse tokens = tokenManager.loadTokens(false);
            return tokens == null ? "NO TOKENS" : tokens.getQuickStatus();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

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