package com.higgstx.schwabapi.interceptor;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.Map;
import java.util.HashMap;

/**
 * Metrics interceptor to collect HTTP request statistics and performance data.
 */
public class MetricsInterceptor implements Interceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsInterceptor.class);
    
    // Overall metrics
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalResponseTime = new AtomicLong(0);
    private final AtomicLong minResponseTime = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxResponseTime = new AtomicLong(0);
    
    // Status code counters
    private final Map<Integer, LongAdder> statusCodeCounts = new ConcurrentHashMap<>();
    
    // Endpoint metrics
    private final Map<String, EndpointMetrics> endpointMetrics = new ConcurrentHashMap<>();
    
    // Error tracking
    private final AtomicLong totalErrors = new AtomicLong(0);
    private final AtomicLong networkErrors = new AtomicLong(0);
    
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        String endpoint = getEndpointKey(request);
        long startTime = System.currentTimeMillis();
        
        totalRequests.incrementAndGet();
        
        try {
            Response response = chain.proceed(request);
            long responseTime = System.currentTimeMillis() - startTime;
            
            recordSuccess(endpoint, response.code(), responseTime);
            return response;
            
        } catch (IOException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            recordError(endpoint, e, responseTime);
            throw e;
        }
    }
    
    /**
     * Record successful response metrics
     */
    private void recordSuccess(String endpoint, int statusCode, long responseTime) {
        // Update overall response time metrics
        totalResponseTime.addAndGet(responseTime);
        updateMinMax(responseTime);
        
        // Update status code counts
        statusCodeCounts.computeIfAbsent(statusCode, k -> new LongAdder()).increment();
        
        // Update endpoint metrics
        getEndpointMetrics(endpoint).recordSuccess(statusCode, responseTime);
        
        // Track errors (4xx and 5xx as errors)
        if (statusCode >= 400) {
            totalErrors.incrementAndGet();
        }
        
        logger.debug("Request to {} completed in {}ms with status {}", endpoint, responseTime, statusCode);
    }
    
    /**
     * Record error metrics
     */
    private void recordError(String endpoint, IOException error, long responseTime) {
        totalErrors.incrementAndGet();
        networkErrors.incrementAndGet();
        
        // Update overall response time even for errors
        totalResponseTime.addAndGet(responseTime);
        updateMinMax(responseTime);
        
        // Update endpoint metrics
        getEndpointMetrics(endpoint).recordError(error, responseTime);
        
        logger.warn("Request to {} failed after {}ms: {}", endpoint, responseTime, error.getMessage());
    }
    
    /**
     * Update min/max response times atomically
     */
    private void updateMinMax(long responseTime) {
        // Update minimum
        long currentMin = minResponseTime.get();
        while (responseTime < currentMin) {
            if (minResponseTime.compareAndSet(currentMin, responseTime)) {
                break;
            }
            currentMin = minResponseTime.get();
        }
        
        // Update maximum
        long currentMax = maxResponseTime.get();
        while (responseTime > currentMax) {
            if (maxResponseTime.compareAndSet(currentMax, responseTime)) {
                break;
            }
            currentMax = maxResponseTime.get();
        }
    }
    
    /**
     * Get endpoint key for metrics grouping
     */
    private String getEndpointKey(Request request) {
        String url = request.url().toString();
        String path = request.url().encodedPath();
        
        // Normalize paths to group similar endpoints
        if (path.contains("/quotes")) {
            return "GET /quotes";
        } else if (path.contains("/pricehistory")) {
            return "GET /pricehistory";
        } else if (path.contains("/market/") && path.contains("/hours")) {
            return "GET /market/hours";
        } else if (path.contains("/instruments")) {
            return "GET /instruments";
        } else if (path.contains("/oauth/token")) {
            return "POST /oauth/token";
        } else if (path.contains("/oauth/authorize")) {
            return "GET /oauth/authorize";
        } else {
            // For other endpoints, use method + simplified path
            return request.method() + " " + path;
        }
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
        
        // Overall metrics
        metrics.put("total_requests", requests);
        metrics.put("total_errors", totalErrors.get());
        metrics.put("network_errors", networkErrors.get());
        metrics.put("success_rate", requests > 0 ? 1.0 - (double) totalErrors.get() / requests : 1.0);
        
        // Response time metrics
        if (requests > 0) {
            metrics.put("avg_response_time_ms", totalTime / requests);
            metrics.put("min_response_time_ms", minResponseTime.get() == Long.MAX_VALUE ? 0 : minResponseTime.get());
            metrics.put("max_response_time_ms", maxResponseTime.get());
        } else {
            metrics.put("avg_response_time_ms", 0);
            metrics.put("min_response_time_ms", 0);
            metrics.put("max_response_time_ms", 0);
        }
        
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
        minResponseTime.set(Long.MAX_VALUE);
        maxResponseTime.set(0);
        totalErrors.set(0);
        networkErrors.set(0);
        statusCodeCounts.clear();
        endpointMetrics.clear();
        
        logger.info("Metrics reset");
    }
    
    /**
     * Get metrics summary as string
     */
    public String getMetricsSummary() {
        long requests = totalRequests.get();
        if (requests == 0) {
            return "No requests recorded";
        }
        
        long totalTime = totalResponseTime.get();
        long errors = totalErrors.get();
        double successRate = 1.0 - (double) errors / requests;
        
        return String.format(
            "API Metrics: %d requests, %.1f%% success rate, avg %.0fms response time, %d errors",
            requests, successRate * 100, (double) totalTime / requests, errors
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
            
            if (statusCode >= 400) {
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
            map.put("success_rate", requestCount > 0 ? 1.0 - (double) errors.get() / requestCount : 1.0);
            map.put("avg_response_time_ms", requestCount > 0 ? totalResponseTime.get() / requestCount : 0);
            
            Map<String, Long> statusCodeMap = new HashMap<>();
            statusCodes.forEach((code, count) -> statusCodeMap.put(String.valueOf(code), count.sum()));
            map.put("status_codes", statusCodeMap);
            
            return map;
        }
    }
}