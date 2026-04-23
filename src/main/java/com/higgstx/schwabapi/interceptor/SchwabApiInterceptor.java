package com.higgstx.schwabapi.interceptor;

import com.higgstx.schwabapi.util.HttpUtils;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple interceptor with basic rate limiting and retry logic
 */
public class SchwabApiInterceptor implements Interceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(SchwabApiInterceptor.class);
    
    private final int maxRetries;
    private final long baseDelayMs;
    private final long minRequestIntervalMs;
    private final AtomicLong lastRequestTime = new AtomicLong(0);
    
    public SchwabApiInterceptor() {
        this(3, 1000L, 500L); // 3 retries, 1s base delay, 500ms between requests (2 req/sec)
    }
    
    public SchwabApiInterceptor(int maxRetries, long baseDelayMs, long minRequestIntervalMs) {
        this.maxRetries = maxRetries;
        this.baseDelayMs = baseDelayMs;
        this.minRequestIntervalMs = minRequestIntervalMs;
    }
    
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        
        // Simple rate limiting
        enforceRateLimit();
        
        // Retry logic
        IOException lastException = null;
        
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                Response response = chain.proceed(request);
                
                if (HttpUtils.isRetryableStatusCode(response.code()) && attempt < maxRetries) {
                    response.close();
                    sleep(calculateDelay(response, attempt));
                    continue;
                }
                
                return response;
                
            } catch (IOException e) {
                lastException = e;
                if (attempt < maxRetries && HttpUtils.isRetryableException(e)) {
                    sleep(calculateBackoffDelay(attempt));
                } else {
                    throw e;
                }
            }
        }
        
        throw lastException;
    }
    
    private void enforceRateLimit() {
        long now = System.currentTimeMillis();
        long lastRequest = lastRequestTime.get();
        long timeSinceLastRequest = now - lastRequest;
        
        if (timeSinceLastRequest < minRequestIntervalMs) {
            sleep(minRequestIntervalMs - timeSinceLastRequest);
        }
        
        lastRequestTime.set(System.currentTimeMillis());
    }
    
    private long calculateDelay(Response response, int attempt) {
        String retryAfter = response.header("Retry-After");
        if (retryAfter != null) {
            try {
                return Long.parseLong(retryAfter) * 1000L;
            } catch (NumberFormatException ignored) {}
        }
        return calculateBackoffDelay(attempt);
    }
    
    private long calculateBackoffDelay(int attempt) {
        return Math.min(baseDelayMs * (1L << attempt), 30000L); // Cap at 30 seconds
    }
    
    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}