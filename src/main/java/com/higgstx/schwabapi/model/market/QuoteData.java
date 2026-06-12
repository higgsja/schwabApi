package com.higgstx.schwabapi.model.market;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Quote data model using @Data and @Builder
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuoteData {
    private String symbol;
    private Double closePrice;
    private Double lastPrice;   // most recent trade price — fallback when closePrice is 0
    private Double highPrice;
    private Double lowPrice;
    private Double openPrice;
    private Long totalVolume;
    private String status;
    private String errorMessage;
    private boolean success;

    /**
     * Best available price: closePrice if non-zero, otherwise lastPrice.
     * Use this as the equity price anchor rather than closePrice directly.
     */
    public Double getBestPrice() {
        if (closePrice != null && closePrice != 0.0) return closePrice;
        return lastPrice;
    }

    /**
     * Create a successful quote data instance
     */
    public static QuoteData success(String symbol, Double closePrice, Double lastPrice,
                                  Double highPrice, Double lowPrice, Double openPrice,
                                  Long totalVolume) {
        return QuoteData.builder()
                .symbol(symbol)
                .closePrice(closePrice)
                .lastPrice(lastPrice)
                .highPrice(highPrice)
                .lowPrice(lowPrice)
                .openPrice(openPrice)
                .totalVolume(totalVolume)
                .status("SUCCESS")
                .success(true)
                .build();
    }

    /**
     * Create a quote data instance for symbol not found
     */
    public static QuoteData notFound(String symbol) {
        return QuoteData.builder()
                .symbol(symbol)
                .status("NOT_FOUND")
                .errorMessage("Symbol not found")
                .success(false)
                .build();
    }

    /**
     * Create an error quote data instance
     */
    public static QuoteData error(String symbol, String errorMessage) {
        return QuoteData.builder()
                .symbol(symbol)
                .status("ERROR")
                .errorMessage(errorMessage)
                .success(false)
                .build();
    }
}