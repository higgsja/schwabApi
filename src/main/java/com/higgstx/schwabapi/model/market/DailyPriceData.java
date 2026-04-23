package com.higgstx.schwabapi.model.market;

import com.higgstx.schwabapi.util.UtilityClass;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;

/**
 * Daily price data model using @Data and @Builder
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyPriceData {
    private String symbol;
    private Long datetime;
    private LocalDate date;
    private Double open;
    private Double high;
    private Double low;
    private Double close;
    private Long volume;
    private boolean success;
    private String errorMessage;

    /**
     * Create a successful daily price data instance from Schwab API candle data
     */
    public static DailyPriceData success(String symbol, Long datetime, Double open, 
                                       Double high, Double low, Double close, Long volume) {
        return DailyPriceData.builder()
                .symbol(symbol)
                .datetime(datetime)
                .open(open)
                .high(high)
                .low(low)
                .close(close)
                .volume(volume)
                .success(true)
                .date(datetime != null ? UtilityClass.timestampToLocalDate(datetime) : null)
                .build();
    }

    /**
     * Create an error daily price data instance
     */
    public static DailyPriceData error(String symbol, String errorMessage) {
        return DailyPriceData.builder()
                .symbol(symbol)
                .errorMessage(errorMessage)
                .success(false)
                .build();
    }

    /**
     * Create an error instance for a specific date
     */
    public static DailyPriceData error(String symbol, LocalDate date, String errorMessage) {
        return DailyPriceData.builder()
                .symbol(symbol)
                .date(date)
                .errorMessage(errorMessage)
                .success(false)
                .build();
    }

    /**
     * Get the date as LocalDate (convenience method)
     */
    public LocalDate getLocalDate() {
        if (date != null) {
            return date;
        }
        if (datetime != null) {
            return UtilityClass.timestampToLocalDate(datetime);
        }
        return null;
    }
}