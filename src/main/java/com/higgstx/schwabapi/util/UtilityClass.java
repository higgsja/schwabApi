package com.higgstx.schwabapi.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * Main utility class providing factory methods and common utility functions
 * This serves as a centralized access point for utility functions used across the API
 */
public final class UtilityClass {
    
    private static final Logger logger = LoggerFactory.getLogger(UtilityClass.class);
    
    // Cached instances for performance
    private static volatile ObjectMapper cachedObjectMapper;
    private static volatile OkHttpClient cachedHttpClient;
    
    private UtilityClass() {
        // Utility class - prevent instantiation
    }
    
    // Factory Methods
    
    /**
     * Get a shared, configured ObjectMapper instance
     * @return Configured ObjectMapper for JSON processing
     */
    public static ObjectMapper getObjectMapper() {
        if (cachedObjectMapper == null) {
            synchronized (UtilityClass.class) {
                if (cachedObjectMapper == null) {
                    cachedObjectMapper = JsonUtils.createStandardObjectMapper();
                }
            }
        }
        return cachedObjectMapper;
    }
    
    /**
     * Get a shared, configured OkHttpClient instance
     * @return Configured OkHttpClient for HTTP requests
     */
    public static OkHttpClient getHttpClient() {
        return getHttpClient(false);
    }
    
    /**
     * Get a shared, configured OkHttpClient instance with optional logging
     * @param enableLogging Whether to enable HTTP request/response logging
     * @return Configured OkHttpClient for HTTP requests
     */
    public static OkHttpClient getHttpClient(boolean enableLogging) {
        if (!enableLogging && cachedHttpClient == null) {
            synchronized (UtilityClass.class) {
                if (cachedHttpClient == null) {
                    cachedHttpClient = HttpUtils.buildHttpClient(false);
                }
            }
            return cachedHttpClient;
        }
        
        // If logging is requested, create a new client each time
        // since logging preference might change
        return HttpUtils.buildHttpClient(enableLogging);
    }
    
    // Common Utility Functions
    
    /**
     * Validate that a string parameter is not null or empty
     * @param value The value to validate
     * @param parameterName The parameter name for error messages
     * @throws IllegalArgumentException if the value is null or empty
     */
    public static void validateParameter(String value, String parameterName) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(parameterName + " cannot be null or empty");
        }
    }
    
    /**
     * Validate that an object parameter is not null
     * @param value The value to validate
     * @param parameterName The parameter name for error messages
     * @throws IllegalArgumentException if the value is null
     */
    public static void validateNotNull(Object value, String parameterName) {
        if (value == null) {
            throw new IllegalArgumentException(parameterName + " cannot be null");
        }
    }
    
    /**
     * Sleep for the specified duration, handling interruption gracefully
     * @param duration The duration to sleep
     * @param unit The time unit
     */
    public static void safeSleep(long duration, TimeUnit unit) {
        try {
            unit.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Sleep interrupted");
        }
    }
    
    /**
     * Convert Unix timestamp (milliseconds) to LocalDate
     * @param timestampMillis The Unix timestamp in milliseconds
     * @return LocalDate representation
     */
    public static LocalDate timestampToLocalDate(Long timestampMillis) {
        if (timestampMillis == null) {
            return null;
        }
        return Instant.ofEpochMilli(timestampMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }
    
    /**
     * Convert LocalDate to Unix timestamp (milliseconds) at start of day
     * @param localDate The LocalDate to convert
     * @return Unix timestamp in milliseconds
     */
    public static Long localDateToTimestamp(LocalDate localDate) {
        if (localDate == null) {
            return null;
        }
        return localDate.atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
    }
    
    /**
     * Format an Instant to a readable string
     * @param instant The Instant to format
     * @return Formatted string, or "N/A" if instant is null
     */
    public static String formatInstant(Instant instant) {
        if (instant == null) {
            return "N/A";
        }
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
                .withZone(ZoneId.systemDefault());
        return formatter.format(instant);
    }
    
    /**
     * Check if a timestamp is within a specified number of seconds from now
     * @param timestamp The timestamp to check
     * @param withinSeconds The time window in seconds
     * @return true if the timestamp is within the specified window
     */
    public static boolean isWithinSeconds(Instant timestamp, long withinSeconds) {
        if (timestamp == null) {
            return false;
        }
        
        long secondsFromNow = Math.abs(timestamp.getEpochSecond() - Instant.now().getEpochSecond());
        return secondsFromNow <= withinSeconds;
    }
    
    /**
     * Calculate percentage between two numbers
     * @param value The value
     * @param total The total
     * @return The percentage (0-100), or 0 if total is 0
     */
    public static double calculatePercentage(double value, double total) {
        if (total == 0) {
            return 0.0;
        }
        return (value / total) * 100.0;
    }
    
    /**
     * Clamp a value between min and max bounds
     * @param value The value to clamp
     * @param min The minimum bound
     * @param max The maximum bound
     * @return The clamped value
     */
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
    
    /**
     * Clamp a value between min and max bounds
     * @param value The value to clamp
     * @param min The minimum bound
     * @param max The maximum bound
     * @return The clamped value
     */
    public static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
    
    /**
     * Clamp a value between min and max bounds
     * @param value The value to clamp
     * @param min The minimum bound
     * @param max The maximum bound
     * @return The clamped value
     */
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
    
    /**
     * Generate a simple hash code for debugging/logging purposes
     * @param objects The objects to include in the hash
     * @return A hash code
     */
    public static int simpleHash(Object... objects) {
        int hash = 1;
        for (Object obj : objects) {
            hash = 31 * hash + (obj == null ? 0 : obj.hashCode());
        }
        return hash;
    }
    
    /**
     * Create a simple string representation of an object for logging
     * @param obj The object to represent
     * @return String representation, handling null safely
     */
    public static String safeToString(Object obj) {
        if (obj == null) {
            return "null";
        }
        try {
            return obj.toString();
        } catch (Exception e) {
            return obj.getClass().getSimpleName() + "@" + Integer.toHexString(obj.hashCode());
        }
    }
    
    /**
     * Get the current time in milliseconds (for consistency across the API)
     * @return Current time in milliseconds since epoch
     */
    public static long currentTimeMillis() {
        return System.currentTimeMillis();
    }
    
    /**
     * Get the current time as an Instant
     * @return Current time as Instant
     */
    public static Instant now() {
        return Instant.now();
    }
    
    /**
     * Check if two objects are equal, handling nulls safely
     * @param obj1 First object
     * @param obj2 Second object
     * @return true if both are null or equal
     */
    public static boolean safeEquals(Object obj1, Object obj2) {
        if (obj1 == obj2) {
            return true;
        }
        if (obj1 == null || obj2 == null) {
            return false;
        }
        return obj1.equals(obj2);
    }
    
    /**
     * Get system property with default value
     * @param propertyName The system property name
     * @param defaultValue The default value if property is not set
     * @return The property value or default
     */
    public static String getSystemProperty(String propertyName, String defaultValue) {
        try {
            return System.getProperty(propertyName, defaultValue);
        } catch (SecurityException e) {
            logger.debug("Cannot access system property {}: {}", propertyName, e.getMessage());
            return defaultValue;
        }
    }
    
    /**
     * Get environment variable with default value
     * @param variableName The environment variable name
     * @param defaultValue The default value if variable is not set
     * @return The environment variable value or default
     */
    public static String getEnvironmentVariable(String variableName, String defaultValue) {
        try {
            String value = System.getenv(variableName);
            return value != null ? value : defaultValue;
        } catch (SecurityException e) {
            logger.debug("Cannot access environment variable {}: {}", variableName, e.getMessage());
            return defaultValue;
        }
    }
    
    /**
     * Build a standardized error message
     * @param operation The operation that failed
     * @param cause The cause of the failure
     * @return Formatted error message
     */
    public static String buildErrorMessage(String operation, String cause) {
        return String.format("Failed to %s: %s", operation, cause);
    }
    
    /**
     * Build a standardized error message with additional context
     * @param operation The operation that failed
     * @param cause The cause of the failure
     * @param context Additional context information
     * @return Formatted error message
     */
    public static String buildErrorMessage(String operation, String cause, String context) {
        return String.format("Failed to %s: %s (Context: %s)", operation, cause, context);
    }
    
    /**
     * Log method entry for debugging (use sparingly to avoid log spam)
     * @param className The class name
     * @param methodName The method name
     * @param parameters Optional parameters to log
     */
    public static void logMethodEntry(String className, String methodName, Object... parameters) {
        if (logger.isDebugEnabled()) {
            if (parameters.length == 0) {
                logger.debug("Entering {}.{}", className, methodName);
            } else {
                logger.debug("Entering {}.{} with parameters: {}", className, methodName, 
                           java.util.Arrays.toString(parameters));
            }
        }
    }
    
    /**
     * Log method exit for debugging (use sparingly to avoid log spam)
     * @param className The class name
     * @param methodName The method name
     * @param result Optional result to log
     */
    public static void logMethodExit(String className, String methodName, Object result) {
        if (logger.isDebugEnabled()) {
            if (result == null) {
                logger.debug("Exiting {}.{}", className, methodName);
            } else {
                logger.debug("Exiting {}.{} with result: {}", className, methodName, safeToString(result));
            }
        }
    }
    
    /**
     * Create a simple metrics context for tracking operation performance
     * @param operationName The name of the operation
     * @return A metrics context that can be used to track timing
     */
    public static MetricsContext createMetricsContext(String operationName) {
        return new MetricsContext(operationName);
    }
    
    /**
     * Simple metrics context for tracking operation timing
     */
    public static class MetricsContext {
        private final String operationName;
        private final long startTime;
        
        private MetricsContext(String operationName) {
            this.operationName = operationName;
            this.startTime = System.currentTimeMillis();
        }
        
        /**
         * Stop timing and log the operation duration
         */
        public void stop() {
            long duration = System.currentTimeMillis() - startTime;
            logger.debug("Operation '{}' completed in {}ms", operationName, duration);
        }
        
        /**
         * Stop timing and log with custom message
         * @param message Custom message to log
         */
        public void stop(String message) {
            long duration = System.currentTimeMillis() - startTime;
            logger.debug("Operation '{}' {}: {}ms", operationName, message, duration);
        }
        
        /**
         * Get the elapsed time without stopping the context
         * @return Elapsed time in milliseconds
         */
        public long getElapsedMillis() {
            return System.currentTimeMillis() - startTime;
        }
    }
}