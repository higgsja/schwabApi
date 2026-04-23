package com.higgstx.schwabapi.util;

import lombok.extern.slf4j.Slf4j;

/**
 * Type conversion utilities with consistent @Slf4j usage
 */
@Slf4j
public final class ConversionUtils {
    
    private ConversionUtils() {}
    
    public static Double convertToDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                log.warn("Could not convert string to Double: {}", value);
                return null;
            }
        }
        return null;
    }
    
    public static Long convertToLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                log.warn("Could not convert string to Long: {}", value);
                return null;
            }
        }
        return null;
    }
    
    public static Boolean convertToBoolean(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        if (value instanceof String) {
            String str = ((String) value).toLowerCase().trim();
            return switch (str) {
                case "true", "1", "yes" -> true;
                case "false", "0", "no" -> false;
                default -> null;
            };
        }
        return null;
    }
    
    public static long parseRetryAfterSeconds(String retryAfterHeader, long defaultSeconds) {
        if (retryAfterHeader == null || retryAfterHeader.trim().isEmpty()) {
            return defaultSeconds;
        }
        try {
            return Long.parseLong(retryAfterHeader.trim());
        } catch (NumberFormatException e) {
            log.warn("Could not parse Retry-After header: {}", retryAfterHeader);
            return defaultSeconds;
        }
    }
}