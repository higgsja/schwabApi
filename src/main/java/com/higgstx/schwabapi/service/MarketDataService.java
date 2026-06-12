package com.higgstx.schwabapi.service;

import com.higgstx.schwabapi.config.SchwabOAuthClient;
import com.higgstx.schwabapi.config.SchwabApiProperties;
import com.higgstx.schwabapi.exception.SchwabApiException;
import com.higgstx.schwabapi.model.ApiResponse;
import com.higgstx.schwabapi.model.market.*;
import com.higgstx.schwabapi.util.*;
import java.time.*;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Streamlined market data service - uses SimpleJsonParser instead of Jackson
 * Single constructor design eliminates ambiguity issues
 */
@Slf4j
public class MarketDataService implements AutoCloseable
{

    private final SchwabOAuthClient client;
    private final TokenManager tokenManager;

    /**
     * Single constructor for all use cases - works for Spring injection and testing
     */
    public MarketDataService(SchwabApiProperties properties,
            TokenManager tokenManager) throws SchwabApiException
    {
        UtilityClass.validateNotNull(properties, "SchwabApiProperties");
        this.tokenManager = tokenManager;
        this.client = new SchwabOAuthClient(properties, true);
    }

    // Quote operations
    public QuoteData getQuote(String ticker) throws SchwabApiException
    {
        List<QuoteData> results = getQuotes(List.of(ticker));
        return results.isEmpty() ? QuoteData.error(ticker, "No data returned") : results.
                get(0);
    }

    public List<QuoteData> getQuotes(List<String> symbols) throws
            SchwabApiException
    {
        UtilityClass.validateNotNull(symbols, "Symbols list");
        if (symbols.isEmpty())
        {
            throw SchwabApiException.validationError(
                    "Symbols list cannot be empty");
        }

        ensureServiceReady("get quotes");

        try
        {
            ApiResponse response = client.getQuotes(symbols.toArray(
                    String[]::new), tokenManager.getValidAccessToken());

            if (!HttpUtils.isSuccessCode(response.getStatusCode()))
            {
                throw SchwabApiException.fromApiResponse("get quotes", response);
            }

            return parseQuoteResponse(symbols, response.getBody());

        }
        catch (SchwabApiException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw SchwabApiException.serverError("Failed to get quotes: " + e.
                    getMessage());
        }
    }

    // Price history operations
    public List<DailyPriceData> getPriceHistoryData(String symbol,
            String periodType,
            int period, String frequencyType, int frequency) throws
            SchwabApiException
    {

        String cleanSymbol = StringUtils.normalizeSymbol(symbol);
        if (cleanSymbol == null)
        {
            throw SchwabApiException.
                    validationError("Invalid symbol: " + symbol);
        }

        ensureServiceReady("get price history");

        try
        {
            ApiResponse response = client.getPriceHistory(cleanSymbol,
                    periodType, period,
                    frequencyType, frequency, tokenManager.getValidAccessToken());

            if (!HttpUtils.isSuccessCode(response.getStatusCode()))
            {
                throw SchwabApiException.fromApiResponse(
                        "get price history for symbol: " + cleanSymbol, response);
            }

            return parsePriceHistoryResponse(cleanSymbol, response.getBody());

        }
        catch (SchwabApiException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw SchwabApiException.serverError(
                    "Failed to fetch price history for symbol: " + cleanSymbol + " - " + e.
                            getMessage());
        }
    }

    /*
    if prevDate is null, all historical data goes into the array
    else only data >= prevDate will be stored
     */
    public List<DailyPriceData> getBulkHistoricalData(String[] symbols,
            LocalDate prevDate) throws SchwabApiException
    {
        UtilityClass.validateNotNull(symbols, "Symbols array");
        if (symbols.length == 0)
        {
            throw SchwabApiException.validationError(
                    "Symbols array cannot be empty");
        }
        ensureServiceReady("get bulk historical data");
        List<DailyPriceData> allData = new ArrayList<>();
        int symbolCount = 0;
        Instant startInstant = Instant.now();

        for (String symbol : symbols)
        {
            String cleanSymbol = StringUtils.normalizeSymbol(symbol);
            if (cleanSymbol == null)
            {
                continue;
            }
            try
            {
                List<DailyPriceData> symbolData = getPriceHistoryData(
                        cleanSymbol, "month", 1, "daily", 1);

                
                if (prevDate == null)
                {
                    // without a previous date, just add all historical quotes
                    //todo: assuming that if first quote is ok they all are
                    if (symbolData.get(0).isSuccess())
                    {
                        allData.addAll(symbolData);
                    }
                }
                else
                {
                    for (DailyPriceData checkData : symbolData)
                    {
//                        if (checkData.getLocalDate() == null)
//                        {
//                            int i = 0;
//                        }
                        // symbolData is in ascending date order
                        // this has to run over 20 checks to get to the one we want
                        if (checkData.isSuccess()
                                && checkData.getLocalDate().compareTo(prevDate) >= 0)
                        {
                            allData.add(checkData);
                        }
                    }
                }
                symbolCount++;

//                if (symbolCount > 100) break;
                // Rate limiting - delay
                if (symbolCount % 80 == 0)
                {
                    System.out.println("Sleep: " + cleanSymbol
                            + " Symbol count: " + allData.size());
                    UtilityClass.safeSleep(8000);
                }
            }
            catch (SchwabApiException e)
            {
                allData.add(DailyPriceData.error(cleanSymbol, "Exception: "
                        + e.getMessage()));
                System.out.println("Done broke: "
                        + cleanSymbol + " " + e.getMessage()
                        + " callCount: " + symbolCount
                        + " Array size: " + allData.size());

                symbolCount++; // Still count failed calls toward the rate limit
            }
        }
        Instant endInstant = Instant.now();

        System.out.println("Number of Symbols: " + allData.size() + "\n");
        System.out.println("Duration: " + Duration.between(startInstant,
                endInstant) + "\n\n");
        return allData;
    }

    public List<DailyPriceData> getBulkHistoricalData(String[] symbols) throws
            SchwabApiException
    {
        return this.getBulkHistoricalData(symbols, null);
//        UtilityClass.validateNotNull(symbols, "Symbols array");
//        if (symbols.length == 0)
//        {
//            throw SchwabApiException.validationError(
//                    "Symbols array cannot be empty");
//        }
//        ensureServiceReady("get bulk historical data");
//        List<DailyPriceData> allData = new ArrayList<>();
//        int callCount = 0;
//        Instant startInstant = Instant.now();
//
//        for (String symbol : symbols)
//        {
//            String cleanSymbol = StringUtils.normalizeSymbol(symbol);
//            if (cleanSymbol == null)
//            {
//                continue;
//            }
//            try
//            {
//                List<DailyPriceData> symbolData = getPriceHistoryData(
//                        cleanSymbol, "month", 1, "daily", 1);
//                allData.addAll(symbolData);
//                callCount++;
//
//                // Rate limiting - delay every 50 calls
//                if (callCount % 80 == 0)
//                {
//                    System.out.println("Sleep: " + cleanSymbol
//                            + " Array size: " + allData.size());
//                    UtilityClass.safeSleep(7000);
//                }
//            }
//            catch (SchwabApiException e)
//            {
//                allData.add(DailyPriceData.error(cleanSymbol, "Exception: "
//                        + e.getMessage()));
//                System.out.println("Done broke: "
//                        + cleanSymbol + " " + e.getMessage()
//                        + " callCount: " + callCount
//                        + " Array size: " + allData.size());
//
//                callCount++; // Still count failed calls toward the rate limit
//            }
//        }
//        Instant endInstant = Instant.now();
//
//        System.out.println("Number of Symbols: " + allData.size() + "\n");
//        System.out.println("Duration: " + Duration.between(startInstant,
//                endInstant) + "\n\n");
//        return allData;
    }

    // Option price history operations

    /**
     * Get OHLCV price history for an option over a date range.
     *
     * Accepts OSI-format symbols with embedded spaces.
     * Examples: "CAT   270617C00900000", "MSFT  270617C00550000"
     *
     * @param osiSymbol  21-character OSI option symbol
     * @param startDate  first date of the range, inclusive
     * @param endDate    last date of the range, inclusive
     * @return list of daily OHLCV candles; error records for days with no data
     */
    public List<DailyPriceData> getOptionPriceHistory(String osiSymbol,
            LocalDate startDate, LocalDate endDate) throws SchwabApiException {

        String validatedSymbol;
        try {
            validatedSymbol = StringUtils.validateOsiSymbol(osiSymbol);
        } catch (IllegalArgumentException e) {
            throw SchwabApiException.validationError(e.getMessage());
        }

        UtilityClass.validateNotNull(startDate, "Start date");
        UtilityClass.validateNotNull(endDate, "End date");

        if (endDate.isBefore(startDate)) {
            throw SchwabApiException.validationError(
                    "End date cannot be before start date");
        }

        ensureServiceReady("get option price history");

        try {
            long startEpochMs = startDate
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
            // endDate is inclusive: advance to the next day's start so the API includes it
            long endEpochMs = endDate.plusDays(1)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();

            ApiResponse response = client.getPriceHistoryByDateRange(
                    validatedSymbol, startEpochMs, endEpochMs,
                    "daily", 1, tokenManager.getValidAccessToken());

            if (!HttpUtils.isSuccessCode(response.getStatusCode())) {
                throw SchwabApiException.fromApiResponse(
                        "get option price history for symbol: " + validatedSymbol, response);
            }

            return parsePriceHistoryResponse(validatedSymbol, response.getBody());

        } catch (SchwabApiException e) {
            throw e;
        } catch (Exception e) {
            throw SchwabApiException.serverError(
                    "Failed to fetch option price history for symbol: "
                    + validatedSymbol + " - " + e.getMessage());
        }
    }

    /**
     * Get OHLCV price history for an option on a single trading date.
     *
     * Convenience wrapper around {@link #getOptionPriceHistory(String, LocalDate, LocalDate)}.
     * Returns an error record if the market was closed or no data is available for that date.
     *
     * @param osiSymbol  21-character OSI option symbol
     * @param date       the trading date requested
     * @return list containing the candle for that date, or an error record if unavailable
     */
    public List<DailyPriceData> getOptionPriceHistory(String osiSymbol,
            LocalDate date) throws SchwabApiException {

        UtilityClass.validateNotNull(date, "Date");

        // Validate symbol up front so callers get the error before the network call
        String validatedSymbol;
        try {
            validatedSymbol = StringUtils.validateOsiSymbol(osiSymbol);
        } catch (IllegalArgumentException e) {
            throw SchwabApiException.validationError(e.getMessage());
        }

        List<DailyPriceData> results = getOptionPriceHistory(validatedSymbol, date, date);

        // Filter to the requested date; the API may return an adjacent candle
        List<DailyPriceData> filtered = results.stream()
                .filter(d -> !d.isSuccess() || date.equals(d.getLocalDate()))
                .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            filtered.add(DailyPriceData.error(validatedSymbol, date,
                    "No data available for " + date + " (market may have been closed)"));
        }

        return filtered;
    }

    /**
     * Raw delegate for option price history — returns the ApiResponse directly.
     * Use this when you need to inspect the full response body for debugging.
     */
    public ApiResponse getRawOptionPriceHistory(String osiSymbol,
            LocalDate startDate, LocalDate endDate) throws SchwabApiException {

        String validatedSymbol;
        try {
            validatedSymbol = StringUtils.validateOsiSymbol(osiSymbol);
        } catch (IllegalArgumentException e) {
            throw SchwabApiException.validationError(e.getMessage());
        }

        UtilityClass.validateNotNull(startDate, "Start date");
        UtilityClass.validateNotNull(endDate, "End date");
        ensureServiceReady("getRawOptionPriceHistory");

        long startEpochMs = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long endEpochMs = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();

        return client.getPriceHistoryByDateRange(
                validatedSymbol, startEpochMs, endEpochMs,
                "daily", 1, tokenManager.getValidAccessToken());
    }

    // Delegate methods for backward compatibility
    public ApiResponse getPriceHistory(String symbol, String periodType,
            int period,
            String frequencyType, int frequency) throws SchwabApiException
    {
        return client.getPriceHistory(symbol, periodType, period, frequencyType,
                frequency,
                tokenManager.getValidAccessToken());
    }

    public ApiResponse getMarketHours(String marketType) throws
            SchwabApiException
    {
        return client.getMarketHours(marketType, tokenManager.
                getValidAccessToken());
    }

    // Status methods
    public boolean isReady()
    {
        //hasvalidtokens returns false
        return tokenManager != null && tokenManager.hasValidTokens();
    }

    public String getTokenStatus()
    {
        try
        {
            if (tokenManager == null)
            {
                return "ERROR: No token manager";
            }
            if (!tokenManager.hasValidTokens())
            {
                return tokenManager.hasUsableTokens() ? "REFRESH_NEEDED" : "NO_TOKENS";
            }
            return "READY";
        }
        catch (Exception e)
        {
            return "ERROR: " + e.getMessage();
        }
    }

    public TokenManager getTokenManager()
    {
        return tokenManager;
    }

    // Private helper methods
    public void ensureServiceReady(String operation) throws SchwabApiException
    {
        if (tokenManager == null)
        {
            throw SchwabApiException.tokenError(
                    "No token manager available for " + operation);
        }

        if (!isReady())
        {
            log.info("Service not ready - attempting token refresh for: {}",
                    operation);
            try
            {
                //currentTokens are empty
                tokenManager.forceTokenRefresh();
                log.info("Token refresh completed for: {}", operation);
            }
            catch (SchwabApiException e)
            {
                throw SchwabApiException.tokenError(
                        "Token refresh failed for " + operation + ": " + e.
                                getMessage());
            }
        }
    }

    private List<QuoteData> parseQuoteResponse(List<String> symbols,
            String jsonResponse)
    {
        List<QuoteData> quoteDataList = new ArrayList<>();

        try
        {
            Map<String, Object> data = SimpleJsonParser.parseToMap(jsonResponse);

            if (data.isEmpty())
            {
                // Parser returned empty map — log a snippet so the caller can diagnose
                String snippet = jsonResponse != null && jsonResponse.length() > 300
                        ? jsonResponse.substring(0, 300) + "..." : jsonResponse;
                log.warn("Quote response parsed to empty map. Response snippet: {}", snippet);
                symbols.forEach(symbol -> quoteDataList.add(QuoteData.error(symbol,
                        "Quote response could not be parsed")));
                return quoteDataList;
            }

            for (String symbol : symbols)
            {
                Object quoteObject = data.get(symbol);
                if (!(quoteObject instanceof Map))
                {
                    log.warn("Symbol {} not found in quote response. Available keys: {}",
                            symbol, data.keySet());
                    quoteDataList.add(QuoteData.notFound(symbol));
                    continue;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> quoteMap = (Map<String, Object>) quoteObject;

                // Schwab may omit the status field or return it in mixed case.
                // Treat absent or non-"error" status as success — the presence of
                // the symbol key in the response map is the real success signal.
                String status = quoteMap.getOrDefault("status", "SUCCESS").toString();
                if ("ERROR".equalsIgnoreCase(status) || "NOT_FOUND".equalsIgnoreCase(status))
                {
                    quoteDataList.add(QuoteData.notFound(symbol));
                    continue;
                }

                // Schwab v1 API nests price fields inside a "quote" sub-object.
                // Fall back to the top-level map for older/flat response shapes.
                Map<String, Object> priceSource = quoteMap;
                Object quoteSub = quoteMap.get("quote");
                if (quoteSub instanceof Map)
                {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> quoteSubMap = (Map<String, Object>) quoteSub;
                    priceSource = quoteSubMap;
                }

                Double closePrice = ConversionUtils.convertToDouble(
                        priceSource.get("closePrice"));
                Double lastPrice = ConversionUtils.convertToDouble(
                        priceSource.get("lastPrice"));
                Double highPrice = ConversionUtils.convertToDouble(
                        priceSource.get("highPrice"));
                Double lowPrice = ConversionUtils.convertToDouble(
                        priceSource.get("lowPrice"));
                Double openPrice = ConversionUtils.convertToDouble(
                        priceSource.get("openPrice"));
                Long totalVolume = ConversionUtils.convertToLong(
                        priceSource.get("totalVolume"));

                if (closePrice == null && lastPrice == null)
                {
                    log.warn("Prices null for {} — priceSource keys: {}", symbol,
                            priceSource.keySet());
                }

                quoteDataList.add(QuoteData.success(symbol, closePrice,
                        lastPrice, highPrice, lowPrice, openPrice, totalVolume));
            }
        }
        catch (Exception e)
        {
            log.error("Error parsing quote response: {}", e.getMessage());
            symbols.forEach(symbol -> quoteDataList.add(QuoteData.error(symbol,
                    "Parse error: " + e.getMessage())));
        }

        return quoteDataList;
    }

    private List<DailyPriceData> parsePriceHistoryResponse(String symbol,
            String jsonResponse)
    {
        List<DailyPriceData> dailyData = new ArrayList<>();

        try
        {
            if (StringUtils.isBlank(jsonResponse))
            {
                dailyData.
                        add(DailyPriceData.error(symbol, "Empty API response"));
                return dailyData;
            }

            Map<String, Object> response = SimpleJsonParser.parseToMap(
                    jsonResponse);
            Object candlesObj = response.get("candles");

            if (!(candlesObj instanceof List))
            {
                dailyData.add(DailyPriceData.error(symbol,
                        "No candles data in API response"));
                return dailyData;
            }

            @SuppressWarnings("unchecked")
            List<Object> candles = (List<Object>) candlesObj;

            if (candles.isEmpty())
            {
                dailyData.add(DailyPriceData.error(symbol,
                        "No historical data available"));
                return dailyData;
            }

            for (Object candleObj : candles)
            {
                if (candleObj instanceof Map)
                {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> candle = (Map<String, Object>) candleObj;

                    try
                    {
                        Long datetime = ConversionUtils.convertToLong(candle.
                                get("datetime"));
                        Double open = ConversionUtils.convertToDouble(candle.
                                get("open"));
                        Double high = ConversionUtils.convertToDouble(candle.
                                get("high"));
                        Double low = ConversionUtils.convertToDouble(candle.get(
                                "low"));
                        Double close = ConversionUtils.convertToDouble(candle.
                                get("close"));
                        Long volume = ConversionUtils.convertToLong(candle.get(
                                "volume"));

                        dailyData.add(DailyPriceData.success(symbol, datetime,
                                open, high, low, close, volume));
                    }
                    catch (Exception e)
                    {
                        log.warn(
                                "Error parsing individual candle for symbol {}: {}",
                                symbol, e.getMessage());
                    }
                }
            }

            if (dailyData.isEmpty())
            {
                dailyData.add(DailyPriceData.error(symbol,
                        "No valid historical data found"));
            }

        }
        catch (Exception e)
        {
            log.error("Exception parsing response for symbol {}: {}", symbol, e.
                    getMessage());
            dailyData.add(DailyPriceData.error(symbol,
                    "Error parsing response: " + e.getMessage()));
        }

        return dailyData;
    }

    // Option chain snapshot

    /**
     * Get a snapshot of yesterday's closing prices for every option contract
     * on a given equity, filtered to strikes within a percentage band of today's price.
     *
     * For contracts that did not trade yesterday, the most recent closing price
     * within the last 7 calendar days is returned with its actual date so callers
     * can see how stale the price is.
     *
     * @param equity        underlying equity symbol, e.g. "CAT"
     * @param strikeBandPct fractional band around today's price, e.g. 0.30 for ±30%
     * @return one DailyPriceData per contract — success records for priced contracts,
     *         error records for contracts with no data in the lookback window
     */
    public List<DailyPriceData> getOptionChainSnapshot(String equity,
            double strikeBandPct) throws SchwabApiException {

        String cleanEquity = StringUtils.normalizeSymbol(equity);
        if (cleanEquity == null) {
            throw SchwabApiException.validationError("Invalid equity symbol: " + equity);
        }
        if (strikeBandPct <= 0 || strikeBandPct > 1.0) {
            throw SchwabApiException.validationError(
                    "strikeBandPct must be between 0 (exclusive) and 1.0 (inclusive), got: "
                    + strikeBandPct);
        }

        ensureServiceReady("getOptionChainSnapshot");

        // Step 1: yesterday's equity closing price as strike anchor.
        // Using price history rather than a quote because the history endpoint
        // reliably returns a structured close price regardless of market hours,
        // and yesterday's close is the semantically correct anchor for a
        // yesterday-dated option chain snapshot.
        LocalDate yesterday = LocalDate.now(ZoneId.systemDefault()).minusDays(1);
        List<DailyPriceData> equityHistory = getPriceHistoryData(
                cleanEquity, "month", 1, "daily", 1);
        DailyPriceData anchorCandle = equityHistory.stream()
                .filter(DailyPriceData::isSuccess)
                .max(Comparator.comparing(DailyPriceData::getLocalDate))
                .orElse(null);
        if (anchorCandle == null || anchorCandle.getClose() == null
                || anchorCandle.getClose() == 0.0) {
            throw SchwabApiException.serverError(
                    "Could not retrieve equity price history for strike anchor: "
                    + cleanEquity);
        }
        double anchorPrice = anchorCandle.getClose();
        log.info("Equity anchor: {} close={} as-of={}", cleanEquity, anchorPrice,
                anchorCandle.getLocalDate());
        double lowerStrike = anchorPrice * (1.0 - strikeBandPct);
        double upperStrike = anchorPrice * (1.0 + strikeBandPct);

        log.info("Chain snapshot: {} anchor={} band=+/-{}% strikes=[{},{}]",
                cleanEquity, anchorPrice, (int) (strikeBandPct * 100),
                String.format("%.2f", lowerStrike), String.format("%.2f", upperStrike));

        // Step 2: fetch the full option chain
        ApiResponse chainResponse = client.getOptionChain(
                cleanEquity, "ALL", tokenManager.getValidAccessToken());
        if (!HttpUtils.isSuccessCode(chainResponse.getStatusCode())) {
            throw SchwabApiException.fromApiResponse(
                    "get option chain for " + cleanEquity, chainResponse);
        }

        // Step 3: parse OSI symbols and strike prices from the chain response
        Map<String, Double> symbolToStrike = parseOptionChainSymbols(chainResponse.getBody());
        if (symbolToStrike.isEmpty()) {
            throw SchwabApiException.serverError(
                    "No option contracts found in chain for: " + cleanEquity);
        }
        log.info("Chain parsed: {} total contracts", symbolToStrike.size());

        // Step 4: filter to the strike band
        Map<String, Double> filtered = symbolToStrike.entrySet().stream()
                .filter(e -> e.getValue() >= lowerStrike && e.getValue() <= upperStrike)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
        log.info("After ±{}% band filter: {} contracts", (int) (strikeBandPct * 100),
                filtered.size());

        // Step 5: fetch most recent price (up to 7 days back) for each contract
        yesterday = LocalDate.now(ZoneId.systemDefault()).minusDays(1);
        List<DailyPriceData> results = new ArrayList<>();
        int callCount = 0;
        Instant start = Instant.now();

        for (String osiSymbol : filtered.keySet()) {
            try {
                DailyPriceData price = getMostRecentOptionPrice(osiSymbol, yesterday, 7);
                results.add(price);
            } catch (SchwabApiException e) {
                results.add(DailyPriceData.error(osiSymbol, yesterday,
                        "API error: " + e.getMessage()));
            }
            callCount++;
            if (callCount % 80 == 0) {
                log.info("Rate limit pause after {} calls...", callCount);
                UtilityClass.safeSleep(8000);
            }
        }

        log.info("Chain snapshot complete: {} contracts in {}",
                results.size(), Duration.between(start, Instant.now()));
        return results;
    }

    /**
     * Convenience overload with the default ±30% strike band.
     */
    public List<DailyPriceData> getOptionChainSnapshot(String equity) throws SchwabApiException {
        return getOptionChainSnapshot(equity, 0.30);
    }

    /**
     * Fetch the last N calendar days ending on asOfDate for an option and return
     * the most recent candle found.  Returns an error record if no data exists
     * within the lookback window.
     */
    private DailyPriceData getMostRecentOptionPrice(String osiSymbol,
            LocalDate asOfDate, int lookbackDays) throws SchwabApiException {

        LocalDate startDate = asOfDate.minusDays(lookbackDays);
        List<DailyPriceData> history = getOptionPriceHistory(osiSymbol, startDate, asOfDate);

        return history.stream()
                .filter(DailyPriceData::isSuccess)
                .max(Comparator.comparing(DailyPriceData::getLocalDate))
                .orElse(DailyPriceData.error(osiSymbol, asOfDate,
                        "No price data in last " + lookbackDays + " days"));
    }

    /**
     * Parse all option contracts from a Schwab /chains JSON response.
     *
     * The chains response nests contracts as:
     *   callExpDateMap / putExpDateMap
     *     → { "YYYY-MM-DD:DTE" → { "strike.0" → [ { symbol, strikePrice, ... } ] } }
     *
     * @return map of OSI symbol → strike price for every contract in the response
     */
    private Map<String, Double> parseOptionChainSymbols(String jsonResponse) {
        Map<String, Double> result = new LinkedHashMap<>();

        try {
            Map<String, Object> root = SimpleJsonParser.parseToMap(jsonResponse);

            Object callMap = root.get("callExpDateMap");
            if (callMap instanceof Map) {
                extractContractsFromExpDateMap((Map<?, ?>) callMap, result);
            }

            Object putMap = root.get("putExpDateMap");
            if (putMap instanceof Map) {
                extractContractsFromExpDateMap((Map<?, ?>) putMap, result);
            }

        } catch (Exception e) {
            log.error("Failed to parse option chain response: {}", e.getMessage());
        }

        return result;
    }

    /**
     * Walk one expDateMap (calls or puts) and collect symbol → strikePrice entries.
     */
    @SuppressWarnings("unchecked")
    private void extractContractsFromExpDateMap(Map<?, ?> expDateMap,
            Map<String, Double> result) {

        for (Object strikeMapObj : expDateMap.values()) {
            if (!(strikeMapObj instanceof Map)) {
                continue;
            }
            Map<?, ?> strikeMap = (Map<?, ?>) strikeMapObj;

            for (Object contractsObj : strikeMap.values()) {
                if (!(contractsObj instanceof List)) {
                    continue;
                }
                List<?> contracts = (List<?>) contractsObj;

                for (Object contractObj : contracts) {
                    if (!(contractObj instanceof Map)) {
                        continue;
                    }
                    Map<String, Object> contract = (Map<String, Object>) contractObj;

                    Object symbolObj = contract.get("symbol");
                    Double strikePrice = ConversionUtils.convertToDouble(
                            contract.get("strikePrice"));

                    if (symbolObj instanceof String && strikePrice != null) {
                        result.put((String) symbolObj, strikePrice);
                    }
                }
            }
        }
    }

    @Override
    public void close()
    {
        if (client != null)
        {
            try
            {
                client.close();
            }
            catch (Exception e)
            {
                log.warn("Error closing OAuth client: {}", e.getMessage());
            }
        }
        log.debug("MarketDataService closed");
    }
}
