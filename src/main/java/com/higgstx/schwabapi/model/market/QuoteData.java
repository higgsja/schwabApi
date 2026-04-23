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
        return QuoteData.builder()
                .symbol(symbol)
                .closePrice(closePrice)
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