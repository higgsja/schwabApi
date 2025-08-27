package com.higgstx.schwabapi.model.market;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data model for stock quote information
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuoteData {
    private String symbol;
    private Double closePrice;
    private Double highPrice;
    private Double lowPrice;
    private Double openPrice;
    private Long totalVolume;
    private String status;
    private String errorMessage;
    private boolean success;

    /**
     * Create a successful quote data instance
     */
    public static QuoteData success(String symbol, Double closePrice, Double highPrice, 
                                  Double lowPrice, Double openPrice, Long totalVolume) {
        QuoteData quote = new QuoteData();
        quote.symbol = symbol;
        quote.closePrice = closePrice;
        quote.highPrice = highPrice;
        quote.lowPrice = lowPrice;
        quote.openPrice = openPrice;
        quote.totalVolume = totalVolume;
        quote.status = "SUCCESS";
        quote.success = true;
        return quote;
    }

    /**
     * Create a quote data instance for symbol not found
     */
    public static QuoteData notFound(String symbol) {
        QuoteData quote = new QuoteData();
        quote.symbol = symbol;
        quote.status = "NOT_FOUND";
        quote.errorMessage = "Symbol not found";
        quote.success = false;
        return quote;
    }

    /**
     * Create an error quote data instance
     */
    public static QuoteData error(String symbol, String errorMessage) {
        QuoteData quote = new QuoteData();
        quote.symbol = symbol;
        quote.status = "ERROR";
        quote.errorMessage = errorMessage;
        quote.success = false;
        return quote;
    }
}