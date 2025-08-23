package com.higgstx.schwab.model.market;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * POJO representing a comprehensive quote for a ticker symbol.
 */
@Getter
@Setter
public class QuoteData {

    private String symbol;
    @JsonProperty("closePrice")
    private Double closePrice;
    @JsonProperty("highPrice")
    private Double highPrice;
    @JsonProperty("lowPrice")
    private Double lowPrice;
    @JsonProperty("openPrice")
    private Double openPrice;
    @JsonProperty("totalVolume")
    private Long totalVolume;
    private String status;
    private String errorMessage;

    // Default constructor for Jackson
    public QuoteData() {
        this.status = "SUCCESS";
    }

    /**
     * Factory method for successful results.
     */
    public static QuoteData success(String symbol, Double closePrice, Double highPrice, Double lowPrice, Double openPrice, Long totalVolume) {
        QuoteData quote = new QuoteData();
        quote.setSymbol(symbol);
        quote.setClosePrice(closePrice);
        quote.setHighPrice(highPrice);
        quote.setLowPrice(lowPrice);
        quote.setOpenPrice(openPrice);
        quote.setTotalVolume(totalVolume);
        return quote;
    }

    /**
     * Factory method for error results.
     */
    public static QuoteData error(String symbol, String errorMessage) {
        QuoteData quote = new QuoteData();
        quote.setSymbol(symbol);
        quote.setStatus("ERROR");
        quote.setErrorMessage(errorMessage);
        return quote;
    }

    /**
     * Factory method for not found results.
     */
    public static QuoteData notFound(String symbol) {
        QuoteData quote = new QuoteData();
        quote.setSymbol(symbol);
        quote.setStatus("NOT_FOUND");
        quote.setErrorMessage("Symbol not found");
        return quote;
    }

    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }
}