package com.higgstx.schwab.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.higgstx.schwab.client.SchwabOAuthClient;
import com.higgstx.schwab.model.ApiResponse;
import com.higgstx.schwab.model.TokenResponse;
import com.higgstx.schwab.model.market.QuoteData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service class for market data operations using the Schwab API.
 * Handles all market data requests with automatic token management.
 */
public class MarketDataService implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(MarketDataService.class);
    private final SchwabOAuthClient client;
    private final ObjectMapper objectMapper;

    public MarketDataService() {
        this.client = new SchwabOAuthClient(true);
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    /**
     * Gets a single quote for a ticker symbol.
     */
    public QuoteData getQuote(String ticker) throws IOException {
        List<QuoteData> results = getQuotes(List.of(ticker));
        if (results != null && !results.isEmpty()) {
            return results.get(0);
        } else {
            return QuoteData.error(ticker, "No data returned");
        }
    }

    /**
     * Gets quotes for a list of symbols
     */
    public List<QuoteData> getQuotes(List<String> symbols) throws IOException {
        String accessToken = TokenManager.getValidAccessToken();
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
                        
                        // Safe type casting with null checks
                        Object statusObj = quoteMap.get("status");
                        String status = statusObj != null ? statusObj.toString() : null;
                        
                        if ("SUCCESS".equals(status)) {
                            // Safe conversion for Double values
                            Double closePrice = convertToDouble(quoteMap.get("closePrice"));
                            Double highPrice = convertToDouble(quoteMap.get("highPrice"));
                            Double lowPrice = convertToDouble(quoteMap.get("lowPrice"));
                            Double openPrice = convertToDouble(quoteMap.get("openPrice"));
                            
                            // Safe conversion for volume
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
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> errorResponse = objectMapper.readValue(response.getBody(), Map.class);
                Object errorObj = errorResponse.get("error");
                if (errorObj != null) {
                    errorMessage = errorObj.toString();
                }
            } catch (Exception e) {
                // Ignore parsing error, use default message
            }
            for (String symbol : symbols) {
                quoteDataList.add(QuoteData.error(symbol, errorMessage));
            }
        }
        return quoteDataList;
    }

    /**
     * Safe conversion to Double
     */
    private Double convertToDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Double) {
            return (Double) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
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

    /**
     * Safe conversion to Long
     */
    private Long convertToLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
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

    /**
     * Gets price history for a symbol
     */
    public ApiResponse getPriceHistory(String symbol, int period, String periodType,
                                     int frequency, String frequencyType) throws IOException {
        String accessToken = TokenManager.getValidAccessToken();
        return client.getPriceHistory(symbol, period, periodType, frequency, frequencyType, accessToken);
    }

    /**
     * Gets market hours for a market type
     */
    public ApiResponse getMarketHours(String marketType) throws IOException {
        String accessToken = TokenManager.getValidAccessToken();
        return client.getMarketHours(marketType, accessToken);
    }

    /**
     * Checks if the service is ready (has valid tokens)
     */
    public boolean isReady() {
        try {
            return TokenManager.hasValidTokens() || TokenManager.hasUsableTokens();
        } catch (Exception e) {
            logger.error("Error checking token status: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Gets the current token status for monitoring
     */
    public String getTokenStatus() {
        try {
            TokenResponse tokens = TokenManager.loadTokens(false);
            if (tokens != null) {
                return tokens.getQuickStatus();
            } else {
                return "❌ NO TOKENS";
            }
        } catch (Exception e) {
            return "❌ ERROR: " + e.getMessage();
        }
    }

    @Override
    public void close() {
        if (client != null) {
            client.close();
        }
    }
}