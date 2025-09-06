package com.higgstx.schwabapi.interceptor;

import com.higgstx.schwabapi.util.HttpUtils;
import com.higgstx.schwabapi.util.UtilityClass;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.Map;
import java.util.HashMap;

/**
 * Unified interceptor combining rate limiting, retry logic, and metrics collection
 * Updated to work with simplified UtilityClass
 */
public class SchwabApiInterceptor implements Interceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(SchwabApiInterceptor.class);
    
    // Rate limiting
    private final double permitsPerSecond;
    private final Semaphore semaphore;
    private final AtomicLong lastRefillTime;
    private final AtomicLong availablePermits;
    
    // Retry configuration
    private final int maxRetries;
    private final long baseDelayMs;
    private final double backoffMultiplier;
    private final long maxDelayMs;
    
    // Metrics
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalResponseTime = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    private final AtomicLong totalRetries = new AtomicLong(0);
    private final Map<Integer, LongAdder> statusCodeCounts = new ConcurrentHashMap<>();
    private final Map<String, EndpointMetrics> endpointMetrics = new ConcurrentHashMap<>();
    
    /**
     * Create interceptor with default settings
     */
    public SchwabApiInterceptor() {
        this(2.0, 3, 1000L, 2.0, 30000L);
    }
    
    /**
     * Create interceptor with custom settings
     */
    public SchwabApiInterceptor(double permitsPerSecond, int maxRetries, 
                               long baseDelayMs, double backoffMultiplier, long maxDelayMs) {
        this.permitsPerSecond = permitsPerSecond;
        this.maxRetries = maxRetries;
        this.baseDelayMs = baseDelayMs;
        this.backoffMultiplier = backoffMultiplier;
        this.maxDelayMs = maxDelayMs;
        
        // Initialize rate limiting
        this.semaphore = new Semaphore((int) Math.ceil(permitsPerSecond), true);
        this.lastRefillTime = new AtomicLong(System.currentTimeMillis());
        this.availablePermits = new AtomicLong((long) permitsPerSecond);
        
        logger.debug("SchwabApiInterceptor initialized: {}req/sec, {}retries", permitsPerSecond, maxRetries);
    }
    
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        String endpoint = getEndpointKey(request);
        long startTime = System.currentTimeMillis();
        
        totalRequests.incrementAndGet();
        
        // Apply rate limiting
        try {
            acquirePermit();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Rate limiting interrupted", e);
        }
        
        // Execute with retry logic
        Response response = null;
        IOException lastException = null;
        
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                if (response != null) {
                    response.close();
                }
                
                response = chain.proceed(request);
                long responseTime = System.currentTimeMillis() - startTime;
                
                // Check if response indicates retry needed
                if (HttpUtils.isRetryableStatusCode(response.code()) && attempt < maxRetries) {
                    logger.warn("Retryable response {} on attempt {}/{}", 
                              response.code(), attempt + 1, maxRetries + 1);
                    
                    long delayMs = getRetryDelayMs(response, attempt);
                    response.close();
                    
                    UtilityClass.safeSleep(delayMs);
                    totalRetries.incrementAndGet();
                    continue;
                }
                
                // Success or non-retryable response
                recordSuccess(endpoint, response.code(), responseTime);
                if (attempt > 0) {
                    logger.info("Request succeeded on attempt {}/{}", attempt + 1, maxRetries + 1);
                }
                return response;
                
            } catch (IOException e) {
                lastException = e;
                long responseTime = System.currentTimeMillis() - startTime;
                
                if (attempt < maxRetries && HttpUtils.isRetryableException(e)) {
                    logger.warn("IOException on attempt {}/{}: {}", 
                              attempt + 1, maxRetries + 1, e.getMessage());
                    
                    long delayMs = HttpUtils.calculateBackoffDelay(attempt, baseDelayMs, 
                                                                 backoffMultiplier, maxDelayMs);
                    UtilityClass.safeSleep(delayMs);
                    totalRetries.incrementAndGet();
                } else {
                    recordError(endpoint, e, responseTime);
                    throw e;
                }
            }
        }
        
        // Should not reach here, but handle just in case
        if (lastException != null) {
            throw lastException;
        }
        return response;
    }
    
    /**
     * Rate limiting using token bucket algorithm
     */
    private void acquirePermit() throws InterruptedException {
        long now = System.currentTimeMillis();
        long lastRefill = lastRefillTime.get();
        
        // Refill tokens if needed
        if (now > lastRefill) {
            long timeSinceLastRefill = now - lastRefill;
            long permitsToAdd = (long) (timeSinceLastRefill * permitsPerSecond / 1000.0);
            
            if (permitsToAdd > 0 && lastRefillTime.compareAndSet(lastRefill, now)) {
                long currentPermits = availablePermits.get();
                long maxPermits = (long) Math.ceil(permitsPerSecond * 2); // Allow burst
                long newPermits = Math.min(currentPermits + permitsToAdd, maxPermits);
                availablePermits.set(newPermits);
                
                if (newPermits > currentPermits) {
                    semaphore.release((int) (newPermits - currentPermits));
                }
            }
        }
        
        // Acquire permit with timeout
        if (!semaphore.tryAcquire(30, java.util.concurrent.TimeUnit.SECONDS)) {
            throw new InterruptedException("Rate limit acquisition timeout");
        }
        
        availablePermits.decrementAndGet();
    }
    
    /**
     * Get retry delay from response headers or calculate backoff
     */
    private long getRetryDelayMs(Response response, int attempt) {
        // Check for Retry-After header
        String retryAfter = response.header("Retry-After");
        if (retryAfter != null) {
            try {
                return Long.parseLong(retryAfter) * 1000L;
            } catch (NumberFormatException e) {
                logger.debug("Invalid Retry-After header: {}", retryAfter);
            }
        }
        
        return HttpUtils.calculateBackoffDelay(attempt, baseDelayMs, backoffMultiplier, maxDelayMs);
    }
    
    /**
     * Get endpoint key for metrics grouping
     */
    private String getEndpointKey(Request request) {
        String path = request.url().encodedPath();
        String method = request.method();
        
        // Normalize common Schwab API paths
        if (path.contains("/quotes")) return "GET /quotes";
        if (path.contains("/pricehistory")) return "GET /pricehistory";
        if (path.contains("/market/") && path.contains("/hours")) return "GET /market/hours";
        if (path.contains("/instruments")) return "GET /instruments";
        if (path.contains("/oauth/token")) return "POST /oauth/token";
        if (path.contains("/oauth/authorize")) return "GET /oauth/authorize";
        
        return method + " " + path;
    }
    
    /**
     * Record successful response metrics
     */
    private void recordSuccess(String endpoint, int statusCode, long responseTime) {
        totalResponseTime.addAndGet(responseTime);
        statusCodeCounts.computeIfAbsent(statusCode, k -> new LongAdder()).increment();
        getEndpointMetrics(endpoint).recordSuccess(statusCode, responseTime);
        
        if (!HttpUtils.isSuccessCode(statusCode)) {
            totalErrors.incrementAndGet();
        }
        
        logger.debug("Request to {} completed in {}ms with status {}", endpoint, responseTime, statusCode);
    }
    
    /**
     * Record error metrics
     */
    private void recordError(String endpoint, IOException error, long responseTime) {
        totalErrors.incrementAndGet();
        totalResponseTime.addAndGet(responseTime);
        getEndpointMetrics(endpoint).recordError(error, responseTime);
        
        logger.warn("Request to {} failed after {}ms: {}", endpoint, responseTime, error.getMessage());
    }
    
    /**
     * Get or create endpoint metrics
     */
    private EndpointMetrics getEndpointMetrics(String endpoint) {
        return endpointMetrics.computeIfAbsent(endpoint, k -> new EndpointMetrics());
    }
    
    /**
     * Get comprehensive metrics
     */
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        long requests = totalRequests.get();
        long totalTime = totalResponseTime.get();
        long errors = totalErrors.get();
        long retries = totalRetries.get();
        
        // Overall metrics
        metrics.put("total_requests", requests);
        metrics.put("total_errors", errors);
        metrics.put("total_retries", retries);
        metrics.put("success_rate", requests > 0 ? 
                UtilityClass.calculatePercentage(requests - errors, requests) / 100.0 : 1.0);
        metrics.put("retry_rate", requests > 0 ? (double) retries / requests : 0.0);
        
        // Response time metrics
        if (requests > 0) {
            metrics.put("avg_response_time_ms", totalTime / requests);
        }
        
        // Rate limiting stats
        metrics.put("rate_limit_permits_per_second", permitsPerSecond);
        metrics.put("available_permits", availablePermits.get());
        
        // Status code distribution
        Map<String, Long> statusCodes = new HashMap<>();
        statusCodeCounts.forEach((code, count) -> 
            statusCodes.put(String.valueOf(code), count.sum()));
        metrics.put("status_codes", statusCodes);
        
        // Endpoint metrics
        Map<String, Map<String, Object>> endpoints = new HashMap<>();
        endpointMetrics.forEach((endpoint, endpointMetric) -> 
            endpoints.put(endpoint, endpointMetric.toMap()));
        metrics.put("endpoints", endpoints);
        
        return metrics;
    }
    
    /**
     * Reset all metrics
     */
    public void resetMetrics() {
        totalRequests.set(0);
        totalResponseTime.set(0);
        totalErrors.set(0);
        totalRetries.set(0);
        statusCodeCounts.clear();
        endpointMetrics.clear();
        
        logger.info("Metrics reset");
    }
    
    /**
     * Get metrics summary
     */
    public String getMetricsSummary() {
        long requests = totalRequests.get();
        if (requests == 0) return "No requests recorded";
        
        long totalTime = totalResponseTime.get();
        long errors = totalErrors.get();
        long retries = totalRetries.get();
        double successRate = UtilityClass.calculatePercentage(requests - errors, requests);
        
        return String.format(
            "API Metrics: %d requests, %.1f%% success, avg %.0fms, %d errors, %d retries",
            requests, successRate, (double) totalTime / requests, errors, retries
        );
    }
    
    /**
     * Endpoint-specific metrics
     */
    private static class EndpointMetrics {
        private final AtomicLong requests = new AtomicLong(0);
        private final AtomicLong totalResponseTime = new AtomicLong(0);
        private final AtomicLong errors = new AtomicLong(0);
        private final Map<Integer, LongAdder> statusCodes = new ConcurrentHashMap<>();
        
        void recordSuccess(int statusCode, long responseTime) {
            requests.incrementAndGet();
            totalResponseTime.addAndGet(responseTime);
            statusCodes.computeIfAbsent(statusCode, k -> new LongAdder()).increment();
            
            if (!HttpUtils.isSuccessCode(statusCode)) {
                errors.incrementAndGet();
            }
        }
        
        void recordError(IOException error, long responseTime) {
            requests.incrementAndGet();
            totalResponseTime.addAndGet(responseTime);
            errors.incrementAndGet();
        }
        
        Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            long requestCount = requests.get();
            
            map.put("requests", requestCount);
            map.put("errors", errors.get());
            map.put("success_rate", requestCount > 0 ? 
                    UtilityClass.calculatePercentage(requestCount - errors.get(), requestCount) / 100.0 : 1.0);
            map.put("avg_response_time_ms", requestCount > 0 ? totalResponseTime.get() / requestCount : 0);
            
            Map<String, Long> statusCodeMap = new HashMap<>();
            statusCodes.forEach((code, count) -> statusCodeMap.put(String.valueOf(code), count.sum()));
            map.put("status_codes", statusCodeMap);
            
            return map;
        }
    }
}