package com.higgstx.schwabapi.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Type conversion utility functions extracted from MarketDataService and other classes
 */
public final class ConversionUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(ConversionUtils.class);
    
    private ConversionUtils() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Safely convert an object to Double
     * @param value The object to convert
     * @return The Double value, or null if conversion fails
     */
    public static Double convertToDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Double) {
            return (Double) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                logger.warn("Could not convert string to Double: {}", value);
                return null;
            }
        }
        logger.warn("Could not convert {} to Double: unsupported type {}", 
                   value, value.getClass().getSimpleName());
        return null;
    }
    
    /**
     * Safely convert an object to Long
     * @param value The object to convert
     * @return The Long value, or null if conversion fails
     */
    public static Long convertToLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                logger.warn("Could not convert string to Long: {}", value);
                return null;
            }
        }
        logger.warn("Could not convert {} to Long: unsupported type {}", 
                   value, value.getClass().getSimpleName());
        return null;
    }
    
    /**
     * Safely convert an object to Integer
     * @param value The object to convert
     * @return The Integer value, or null if conversion fails
     */
    public static Integer convertToInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                logger.warn("Could not convert string to Integer: {}", value);
                return null;
            }
        }
        logger.warn("Could not convert {} to Integer: unsupported type {}", 
                   value, value.getClass().getSimpleName());
        return null;
    }
    
    /**
     * Safely convert an object to Boolean
     * @param value The object to convert
     * @return The Boolean value, or null if conversion fails
     */
    public static Boolean convertToBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            String str = ((String) value).toLowerCase().trim();
            if ("true".equals(str) || "1".equals(str) || "yes".equals(str)) {
                return true;
            }
            if ("false".equals(str) || "0".equals(str) || "no".equals(str)) {
                return false;
            }
            logger.warn("Could not convert string to Boolean: {}", value);
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        logger.warn("Could not convert {} to Boolean: unsupported type {}", 
                   value, value.getClass().getSimpleName());
        return null;
    }
    
    /**
     * Safely convert an object to String
     * @param value The object to convert
     * @return The String value, or null if the input is null
     */
    public static String convertToString(Object value) {
        if (value == null) {
            return null;
        }
        return value.toString();
    }
    
    /**
     * Convert seconds to milliseconds safely
     * @param seconds The seconds value
     * @return Milliseconds, or 0 if input is null or negative
     */
    public static long secondsToMillis(Long seconds) {
        if (seconds == null || seconds < 0) {
            return 0;
        }
        return seconds * 1000L;
    }
    
    /**
     * Convert milliseconds to seconds safely
     * @param millis The milliseconds value
     * @return Seconds, or 0 if input is null or negative
     */
    public static long millisToSeconds(Long millis) {
        if (millis == null || millis < 0) {
            return 0;
        }
        return millis / 1000L;
    }
    
    /**
     * Parse a retry-after header value (could be seconds or HTTP date)
     * @param retryAfterHeader The Retry-After header value
     * @param defaultSeconds Default value if parsing fails
     * @return The retry delay in seconds
     */
    public static long parseRetryAfterSeconds(String retryAfterHeader, long defaultSeconds) {
        if (retryAfterHeader == null || retryAfterHeader.trim().isEmpty()) {
            return defaultSeconds;
        }
        
        try {
            // Try parsing as number of seconds
            return Long.parseLong(retryAfterHeader.trim());
        } catch (NumberFormatException e) {
            // Could be HTTP date format, but for simplicity return default
            logger.debug("Could not parse Retry-After header as seconds: {}", retryAfterHeader);
            return defaultSeconds;
        }
    }
    
    /**
     * Convert percentage to decimal (e.g., 15.5% -> 0.155)
     * @param percentage The percentage value
     * @return The decimal value
     */
    public static Double percentageToDecimal(Double percentage) {
        if (percentage == null) {
            return null;
        }
        return percentage / 100.0;
    }
    
    /**
     * Convert decimal to percentage (e.g., 0.155 -> 15.5%)
     * @param decimal The decimal value
     * @return The percentage value
     */
    public static Double decimalToPercentage(Double decimal) {
        if (decimal == null) {
            return null;
        }
        return decimal * 100.0;
    }
}