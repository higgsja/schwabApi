package com.higgstx.schwabapi.interceptor;

import com.higgstx.schwabapi.util.HttpUtils;
import com.higgstx.schwabapi.util.UtilityClass;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Retry interceptor with exponential backoff for handling transient failures.
 * Refactored to use utility package for common operations
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
                if (HttpUtils.isRetryableStatusCode(response.code())) {
                    if (attempt < maxRetries) {
                        long retryAfterMs = getRetryDelayMs(response, attempt);
                        logger.warn("Retryable response {} on attempt {}/{}, retrying in {}ms", 
                                response.code(), attempt + 1, maxRetries + 1, retryAfterMs);
                        
                        response.close();
                        UtilityClass.safeSleep(retryAfterMs, java.util.concurrent.TimeUnit.MILLISECONDS);
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
                
                if (attempt < maxRetries && HttpUtils.isRetryableException(e)) {
                    long delayMs = HttpUtils.calculateBackoffDelay(attempt, baseDelayMs, backoffMultiplier, maxDelayMs);
                    logger.warn("Retrying after {}ms due to: {}", delayMs, e.getMessage());
                    UtilityClass.safeSleep(delayMs, java.util.concurrent.TimeUnit.MILLISECONDS);
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
     * Get retry delay from response headers or calculate backoff
     */
    private long getRetryDelayMs(Response response, int attempt) {
        Map<String, String> headers = HttpUtils.headersToSingleValueMap(response.headers());
        
        // Check for Retry-After header using utility function
        long retryAfterSeconds = HttpUtils.getRetryAfterSeconds(headers, -1);
        if (retryAfterSeconds > 0) {
            return retryAfterSeconds * 1000L; // Convert to milliseconds
        }
        
        return HttpUtils.calculateBackoffDelay(attempt, baseDelayMs, backoffMultiplier, maxDelayMs);
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