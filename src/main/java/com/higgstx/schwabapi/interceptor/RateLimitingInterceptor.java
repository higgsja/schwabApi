package com.higgstx.schwabapi.interceptor;

import okhttp3.Interceptor;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate limiting interceptor using token bucket algorithm to respect API limits.
 */
public class RateLimitingInterceptor implements Interceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(RateLimitingInterceptor.class);
    
    private final double permitsPerSecond;
    private final Semaphore semaphore;
    private final AtomicLong lastRefillTime;
    private final AtomicLong availablePermits;
    private final long refillIntervalMs;
    
    public RateLimitingInterceptor(double permitsPerSecond) {
        if (permitsPerSecond <= 0) {
            throw new IllegalArgumentException("Permits per second must be positive");
        }
        
        this.permitsPerSecond = permitsPerSecond;
        this.semaphore = new Semaphore((int) Math.ceil(permitsPerSecond), true); // Fair semaphore
        this.lastRefillTime = new AtomicLong(System.currentTimeMillis());
        this.availablePermits = new AtomicLong((long) permitsPerSecond);
        this.refillIntervalMs = (long) (1000.0 / permitsPerSecond);
        
        logger.debug("Rate limiting initialized: {} requests per second", permitsPerSecond);
    }
    
    @Override
    public Response intercept(Chain chain) throws IOException {
        try {
            acquirePermit();
            return chain.proceed(chain.request());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Rate limiting interrupted", e);
        }
    }
    
    /**
     * Acquire a permit using token bucket algorithm
     */
    private void acquirePermit() throws InterruptedException {
        long now = System.currentTimeMillis();
        long lastRefill = lastRefillTime.get();
        
        // Calculate how many permits should be added since last refill
        if (now > lastRefill) {
            long timeSinceLastRefill = now - lastRefill;
            long permitsToAdd = (long) (timeSinceLastRefill * permitsPerSecond / 1000.0);
            
            if (permitsToAdd > 0) {
                // Try to update last refill time atomically
                if (lastRefillTime.compareAndSet(lastRefill, now)) {
                    // Add permits up to the maximum
                    long currentPermits = availablePermits.get();
                    long maxPermits = (long) Math.ceil(permitsPerSecond * 2); // Allow burst up to 2x rate
                    long newPermits = Math.min(currentPermits + permitsToAdd, maxPermits);
                    availablePermits.set(newPermits);
                    
                    // Release permits to semaphore
                    if (newPermits > currentPermits) {
                        semaphore.release((int) (newPermits - currentPermits));
                    }
                }
            }
        }
        
        // Acquire permit with timeout
        boolean acquired = semaphore.tryAcquire(30, TimeUnit.SECONDS);
        if (!acquired) {
            throw new InterruptedException("Rate limit acquisition timeout");
        }
        
        // Decrease available permits counter
        availablePermits.decrementAndGet();
        
        logger.debug("Rate limit permit acquired. Available permits: {}", availablePermits.get());
    }
    
    /**
     * Get current rate limit statistics
     */
    public RateLimitStats getStats() {
        return new RateLimitStats(
            permitsPerSecond,
            availablePermits.get(),
            semaphore.availablePermits()
        );
    }
    
    /**
     * Rate limit statistics
     */
    public static class RateLimitStats {
        private final double maxPermitsPerSecond;
        private final long availablePermits;
        private final int semaphorePermits;
        
        public RateLimitStats(double maxPermitsPerSecond, long availablePermits, int semaphorePermits) {
            this.maxPermitsPerSecond = maxPermitsPerSecond;
            this.availablePermits = availablePermits;
            this.semaphorePermits = semaphorePermits;
        }
        
        public double getMaxPermitsPerSecond() {
            return maxPermitsPerSecond;
        }
        
        public long getAvailablePermits() {
            return availablePermits;
        }
        
        public int getSemaphorePermits() {
            return semaphorePermits;
        }
        
        @Override
        public String toString() {
            return String.format("RateLimit{max=%.1f/sec, available=%d, semaphore=%d}", 
                    maxPermitsPerSecond, availablePermits, semaphorePermits);
        }
    }
}