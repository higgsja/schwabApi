package com.higgstx.schwab.service;

import com.higgstx.schwab.model.market.QuoteData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * Test class for MarketDataService
 */
public class MarketDataServiceTest {
    
    private static final Logger logger = LoggerFactory.getLogger(MarketDataServiceTest.class);
    
    public static void main(String[] args) {
        System.out.println("╔═ 📊 MARKET DATA SERVICE TEST ═══════════════════════════════════════╗");
        System.out.println("║ Testing comprehensive quote functionality                          ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════════╝");
        System.out.println();
        
        try (MarketDataService marketService = new MarketDataService()) {
            
            // Check if service is ready
            System.out.println("🔍 Checking service status...");
            if (!marketService.isReady()) {
                System.out.println("❌ Service not ready - token issues detected");
                System.out.println("   Token Status: " + marketService.getTokenStatus());
                System.out.println("   Please run the main harness to authorize first");
                return;
            }
            
            System.out.println("✅ Service ready - Token Status: " + marketService.getTokenStatus());
            System.out.println();
            
            // Test 1: Single Ticker
            testSingleTicker(marketService);
            
            // Test 2: Multiple Tickers
            testMultipleTickers(marketService);
            
            // Test 3: Invalid Ticker
            testInvalidTicker(marketService);
            
        } catch (Exception e) {
            System.out.println("❌ An unhandled error occurred: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("╔═ 📊 TEST COMPLETE ═════════════════════════════════════════════════╗");
    }
    
    private static void testSingleTicker(MarketDataService service) {
        System.out.println("📈 Test 1: Single Ticker (AAPL)");
        System.out.println("─".repeat(40));
        
        try {
            QuoteData result = service.getQuote("AAPL");
            
            if (result.isSuccess()) {
                System.out.println("✅ Successfully retrieved quote for AAPL");
                System.out.printf("   Close Price: $%.2f%n", result.getClosePrice());
                System.out.printf("   High Price:  $%.2f%n", result.getHighPrice());
                System.out.printf("   Low Price:   $%.2f%n", result.getLowPrice());
                System.out.printf("   Open Price:  $%.2f%n", result.getOpenPrice());
                System.out.printf("   Total Volume: %d%n", result.getTotalVolume());
            } else {
                System.out.println("❌ Failed to retrieve quote for AAPL");
                System.out.println("   Status: " + result.getStatus());
                System.out.println("   Message: " + result.getErrorMessage());
            }
        } catch (Exception e) {
            System.out.println("❌ Error in single ticker test: " + e.getMessage());
        }
        System.out.println();
    }
    
    private static void testMultipleTickers(MarketDataService service) {
        System.out.println("📈 Test 2: Multiple Tickers (MSFT, GOOGL, AMZN)");
        System.out.println("─".repeat(40));
        
        try {
            List<String> tickers = Arrays.asList("MSFT", "GOOGL", "AMZN");
            List<QuoteData> results = service.getQuotes(tickers);
            
            System.out.println("Results for multiple tickers:");
            results.forEach(result -> {
                if (result.isSuccess()) {
                    System.out.printf("   ✅ %-6s: Close=$%.2f, High=$%.2f%n", 
                        result.getSymbol(), result.getClosePrice(), result.getHighPrice());
                } else {
                    System.out.printf("   ❌ %-6s: %s%n", result.getSymbol(), result.getErrorMessage());
                }
            });
            
        } catch (Exception e) {
            System.out.println("❌ Error in list tickers test: " + e.getMessage());
        }
        System.out.println();
    }
    
    private static void testInvalidTicker(MarketDataService service) {
        System.out.println("🚫 Test 3: Invalid Ticker");
        System.out.println("─".repeat(40));
        
        try {
            QuoteData result = service.getQuote("INVALID_TICKER_XYZ");
            
            if (!result.isSuccess()) {
                System.out.println("✅ Correctly handled invalid ticker");
                System.out.println("   Status: " + result.getStatus());
                System.out.println("   Message: " + result.getErrorMessage());
            } else {
                System.out.println("⚠️ Unexpected success for invalid ticker");
            }
            
        } catch (Exception e) {
            System.out.println("❌ Error in invalid ticker test: " + e.getMessage());
        }
        System.out.println();
    }
}