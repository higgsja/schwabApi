package com.higgstx.schwabapi.interceptor;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Retry interceptor with exponential backoff for handling transient failures.
 */
public class RetryInterceptor implements Interceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(RetryInterceptor.class);
    
    private final int maxRetries;
    private final long baseDelayMs;
    private final double backoffMultiplier;
    private final long maxDelayMs;
    private final AtomicInteger totalRetries = new AtomicInteger(0);
    
    public RetryInterceptor(int maxRetries) {
        this(maxRetries, 1000L, 2.0, 30000L);
    }
    
    public RetryInterceptor(int maxRetries, long baseDelayMs, double backoffMultiplier, long maxDelayMs) {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("Max retries cannot be negative");
        }
        if (baseDelayMs <= 0) {
            throw new IllegalArgumentException("Base delay must be positive");
        }
        if (backoffMultiplier < 1.0) {
            throw new IllegalArgumentException("Backoff multiplier must be >= 1.0");
        }
        if (maxDelayMs < baseDelayMs) {
            throw new IllegalArgumentException("Max delay must be >= base delay");
        }
        
        this.maxRetries = maxRetries;
        this.baseDelayMs = baseDelayMs;
        this.backoffMultiplier = backoffMultiplier;
        this.maxDelayMs = maxDelayMs;
        
        logger.debug("Retry interceptor initialized: maxRetries={}, baseDelay={}ms, backoff={}, maxDelay={}ms", 
                maxRetries, baseDelayMs, backoffMultiplier, maxDelayMs);
    }
    
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        Response response = null;
        IOException lastException = null;
        
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                // Close previous response if exists
                if (response != null) {
                    response.close();
                }
                
                response = chain.proceed(request);
                
                // Check if response indicates a retryable condition
                if (isRetryableResponse(response)) {
                    if (attempt < maxRetries) {
                        long retryAfterMs = getRetryDelayMs(response, attempt);
                        logger.warn("Retryable response {} on attempt {}/{}, retrying in {}ms", 
                                response.code(), attempt + 1, maxRetries + 1, retryAfterMs);
                        
                        response.close();
                        sleep(retryAfterMs);
                        totalRetries.incrementAndGet();
                        continue;
                    } else {
                        logger.error("Max retries ({}) exceeded for request to {}", 
                                maxRetries, request.url());
                        return response; // Return the last response
                    }
                }
                
                // Success or non-retryable response
                if (attempt > 0) {
                    logger.info("Request succeeded on attempt {}/{}", attempt + 1, maxRetries + 1);
                }
                return response;
                
            } catch (IOException e) {
                lastException = e;
                logger.warn("IOException on attempt {}/{}: {}", 
                        attempt + 1, maxRetries + 1, e.getMessage());
                
                if (attempt < maxRetries && isRetryableException(e)) {
                    long delayMs = calculateBackoffDelay(attempt);
                    logger.warn("Retrying after {}ms due to: {}", delayMs, e.getMessage());
                    sleep(delayMs);
                    totalRetries.incrementAndGet();
                } else {
                    throw e;
                }
            }
        }
        
        // This should not be reached, but just in case
        if (lastException != null) {
            throw lastException;
        }
        return response;
    }
    
    /**
     * Check if the response indicates a retryable condition
     */
    private boolean isRetryableResponse(Response response) {
        int code = response.code();
        
        // Server errors are retryable
        if (code >= 500) {
            return true;
        }
        
        // Rate limiting is retryable
        if (code == 429) {
            return true;
        }
        
        // Request timeout is retryable
        if (code == 408) {
            return true;
        }
        
        // Service unavailable
        if (code == 503) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if the exception indicates a retryable condition
     */
    private boolean isRetryableException(IOException e) {
        String message = e.getMessage().toLowerCase();
        
        // Network-related exceptions are generally retryable
        return message.contains("timeout") || 
               message.contains("connection") || 
               message.contains("network") ||
               message.contains("socket") ||
               message.contains("host") ||
               e instanceof java.net.SocketTimeoutException ||
               e instanceof java.net.ConnectException ||
               e instanceof java.net.UnknownHostException;
    }
    
    /**
     * Get retry delay from response headers or calculate backoff
     */
    private long getRetryDelayMs(Response response, int attempt) {
        // Check for Retry-After header
        String retryAfter = response.header("Retry-After");
        if (retryAfter != null) {
            try {
                long seconds = Long.parseLong(retryAfter);
                return TimeUnit.SECONDS.toMillis(seconds);
            } catch (NumberFormatException e) {
                // Retry-After might be an HTTP date, but we'll just use backoff
                logger.debug("Could not parse Retry-After header: {}", retryAfter);
            }
        }
        
        return calculateBackoffDelay(attempt);
    }
    
    /**
     * Calculate exponential backoff delay
     */
    private long calculateBackoffDelay(int attempt) {
        double delay = baseDelayMs * Math.pow(backoffMultiplier, attempt);
        long delayMs = Math.min((long) delay, maxDelayMs);
        
        // Add some jitter to prevent thundering herd
        long jitter = (long) (delayMs * 0.1 * Math.random());
        return delayMs + jitter;
    }
    
    /**
     * Sleep for the specified duration, handling interruption
     */
    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Retry delay interrupted");
        }
    }
    
    /**
     * Get retry statistics
     */
    public RetryStats getStats() {
        return new RetryStats(maxRetries, totalRetries.get());
    }
    
    /**
     * Reset retry statistics
     */
    public void resetStats() {
        totalRetries.set(0);
    }
    
    /**
     * Retry statistics
     */
    public static class RetryStats {
        private final int maxRetries;
        private final int totalRetries;
        
        public RetryStats(int maxRetries, int totalRetries) {
            this.maxRetries = maxRetries;
            this.totalRetries = totalRetries;
        }
        
        public int getMaxRetries() {
            return maxRetries;
        }
        
        public int getTotalRetries() {
            return totalRetries;
        }
        
        public double getRetryRate() {
            return totalRetries > 0 ? (double) totalRetries / (totalRetries + 1) : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("Retry{max=%d, total=%d, rate=%.1f%%}", 
                    maxRetries, totalRetries, getRetryRate() * 100);
        }
    }
}