package com.higgstx.schwabapi.model.market;

import com.higgstx.schwabapi.util.UtilityClass;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import lombok.*;

/**
 * Data model for daily price information from Schwab API historical data
 * Based on Schwab API candle structure: [datetime, open, high, low, close, volume]
 * Refactored to use utility package for common operations
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DailyPriceData {
    private String symbol;
    private Long datetime;          // Unix timestamp in milliseconds from Schwab API
    private LocalDate date;         // Derived date for convenience
    private Double open;
    private Double high;
    private Double low;
    private Double close;
    private Long volume;
    private boolean success;
    private String errorMessage;

    /**
     * Create a successful daily price data instance from Schwab API candle data
     * @param symbol The stock symbol
     * @param datetime Unix timestamp in milliseconds
     * @param open Opening price
     * @param high High price
     * @param low Low price
     * @param close Closing price
     * @param volume Trading volume
     */
    public static DailyPriceData success(String symbol, Long datetime, Double open, 
                                       Double high, Double low, Double close, Long volume) {
        DailyPriceData data = new DailyPriceData();
        data.symbol = symbol;
        data.datetime = datetime;
        data.open = open;
        data.high = high;
        data.low = low;
        data.close = close;
        data.volume = volume;
        data.success = true;
        
        // Convert timestamp to LocalDate using utility function
        if (datetime != null) {
            data.date = UtilityClass.timestampToLocalDate(datetime);
        }
        
        return data;
    }

    /**
     * Create an error daily price data instance
     */
    public static DailyPriceData error(String symbol, String errorMessage) {
        DailyPriceData data = new DailyPriceData();
        data.symbol = symbol;
        data.errorMessage = errorMessage;
        data.success = false;
        return data;
    }

    /**
     * Create an error instance for a specific date
     */
    public static DailyPriceData error(String symbol, LocalDate date, String errorMessage) {
        DailyPriceData data = new DailyPriceData();
        data.symbol = symbol;
        data.date = date;
        data.errorMessage = errorMessage;
        data.success = false;
        return data;
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