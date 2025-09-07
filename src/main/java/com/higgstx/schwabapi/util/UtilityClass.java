package com.higgstx.schwabapi.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Simplified utility class with only essential functions
 */
public final class UtilityClass {
    
    // Single shared ObjectMapper instance
    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();
    
    private UtilityClass() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Get the shared ObjectMapper instance
     */
    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }
    
    /**
     * Create a configured ObjectMapper for JSON processing
     */
    private static ObjectMapper createObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    
    /**
     * Validate that a parameter is not null or empty
     */
    public static void validateParameter(String value, String parameterName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(parameterName + " cannot be null or empty");
        }
    }
    
    /**
     * Validate that an object is not null
     */
    public static void validateNotNull(Object value, String parameterName) {
        if (value == null) {
            throw new IllegalArgumentException(parameterName + " cannot be null");
        }
    }
    
    /**
     * Convert Unix timestamp (milliseconds) to LocalDate
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
     * Get current time as Instant
     */
    public static Instant now() {
        return Instant.now();
    }
    
    /**
     * Safe sleep that handles interruption
     */
    public static void safeSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Build a standardized error message
     */
    public static String buildErrorMessage(String operation, String cause) {
        return String.format("Failed to %s: %s", operation, cause);
    }
}