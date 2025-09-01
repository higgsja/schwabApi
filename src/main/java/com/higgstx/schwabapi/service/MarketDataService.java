package com.higgstx.schwabapi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.higgstx.schwabapi.config.SchwabOAuthClient;
import com.higgstx.schwabapi.config.SchwabApiProperties;
import com.higgstx.schwabapi.exception.SchwabApiException;
import com.higgstx.schwabapi.model.ApiResponse;
import com.higgstx.schwabapi.model.TokenResponse;
import com.higgstx.schwabapi.model.market.QuoteData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service class for market data operations - no credential management
 */
public class MarketDataService implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(MarketDataService.class);
    private final SchwabOAuthClient client;
    private final ObjectMapper objectMapper;

    public MarketDataService() {
        // Use the instance-based SchwabOAuthClient with default configuration
        this.client = new SchwabOAuthClient(true); // Enable logging
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    public MarketDataService(SchwabApiProperties properties) {
        // Use explicit configuration
        this.client = new SchwabOAuthClient(properties, true);
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    /**
     * Gets a single quote for a ticker symbol.
     */
    public QuoteData getQuote(String ticker) throws IOException, SchwabApiException {
        return getQuote(ticker, TokenManager.getValidAccessToken());
    }

    public QuoteData getQuote(String ticker, String accessToken) throws IOException {
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
    public List<QuoteData> getQuotes(List<String> symbols) throws IOException, SchwabApiException {
        return getQuotes(symbols, TokenManager.getValidAccessToken());
    }

    public List<QuoteData> getQuotes(List<String> symbols, String accessToken) throws IOException {
        ApiResponse response = client.getQuotes(symbols.toArray(new String[0]), accessToken);
        List<QuoteData> quoteDataList = new ArrayList<>();

        if (response.getStatusCode() == 200) {
            try {
                Map<String, Object> data = objectMapper.readValue(response.getBody(), new TypeReference<Map<String, Object>>() {});
                for (String symbol : symbols) {
                    Object quoteObject = data.get(symbol);
                    if (quoteObject instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> quoteMap = (Map<String, Object>) quoteObject;
                        
                        Object statusObj = quoteMap.get("status");
                        String status = statusObj != null ? statusObj.toString() : null;
                        
                        if ("SUCCESS".equals(status)) {
                            Double closePrice = convertToDouble(quoteMap.get("closePrice"));
                            Double highPrice = convertToDouble(quoteMap.get("highPrice"));
                            Double lowPrice = convertToDouble(quoteMap.get("lowPrice"));
                            Double openPrice = convertToDouble(quoteMap.get("openPrice"));
                            Long totalVolume = convertToLong(quoteMap.get("totalVolume"));
                            
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
                for (String symbol : symbols) {
                    quoteDataList.add(QuoteData.error(symbol, "Error parsing API response"));
                }
            }
        } else {
            logger.error("API call failed with status code: {}", response.getStatusCode());
            String errorMessage = "API error: " + response.getStatusCode();
            for (String symbol : symbols) {
                quoteDataList.add(QuoteData.error(symbol, errorMessage));
            }
        }
        return quoteDataList;
    }

    /**
     * Gets price history for a symbol
     */
    public ApiResponse getPriceHistory(String symbol, String periodType, int period,
                                     String frequencyType, int frequency) throws IOException, SchwabApiException {
        return getPriceHistory(symbol, periodType, period, frequencyType, frequency, TokenManager.getValidAccessToken());
    }

    public ApiResponse getPriceHistory(String symbol, String periodType, int period,
                                     String frequencyType, int frequency, String accessToken) throws IOException {
        return client.getPriceHistory(symbol, periodType, period, frequencyType, frequency, accessToken);
    }

    /**
     * Gets market hours for a market type
     */
    public ApiResponse getMarketHours(String marketType) throws IOException, SchwabApiException {
        return getMarketHours(marketType, TokenManager.getValidAccessToken());
    }

    public ApiResponse getMarketHours(String marketType, String accessToken) throws IOException {
        return client.getMarketHours(marketType, accessToken);
    }

    /**
     * Check if service is ready (has valid tokens)
     */
    public boolean isReady() {
        return TokenManager.hasValidTokens();
    }

    /**
     * Get token status for debugging
     */
    public String getTokenStatus() {
        try {
            TokenResponse tokens = TokenManager.loadTokens(false);
            if (tokens == null) {
                return "NO TOKENS";
            }
            return tokens.getQuickStatus();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    // Helper methods
    private Double convertToDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Double) return (Double) value;
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                logger.warn("Could not convert string to Double: {}", value);
                return null;
            }
        }
        return null;
    }

    private Long convertToLong(Object value) {
        if (value == null) return null;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                logger.warn("Could not convert string to Long: {}", value);
                return null;
            }
        }
        return null;
    }

    @Override
    public void close() {
        if (client != null) {
            client.close();
        }
    }
}