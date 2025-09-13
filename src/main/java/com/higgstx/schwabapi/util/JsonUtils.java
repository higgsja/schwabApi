package com.higgstx.schwabapi.util;

import java.util.Map;

/**
 * JSON processing utility functions - now delegates to SimpleJsonParser
 */
public final class JsonUtils {
    
    private JsonUtils() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Safely parse JSON string into a Map
     * @param jsonString The JSON string to parse
     * @param objectMapper Ignored - kept for compatibility
     * @return Map representation of the JSON, or empty map if parsing fails
     */
    public static Map<String, Object> parseJsonToMap(String jsonString, Object objectMapper) {
        return SimpleJsonParser.parseToMap(jsonString);
    }
    
    /**
     * Extract error information from a JSON response
     * @param jsonResponse The JSON response string
     * @param objectMapper Ignored - kept for compatibility
     * @return Map containing error details
     */
    public static Map<String, Object> extractErrorDetails(String jsonResponse, Object objectMapper) {
        return SimpleJsonParser.extractErrorDetails(jsonResponse);
    }
    
    /**
     * Extract error message from a JSON response
     * @param jsonResponse The JSON response string
     * @param objectMapper Ignored - kept for compatibility
     * @return Error message, or a default message if extraction fails
     */
    public static String extractErrorMessage(String jsonResponse, Object objectMapper) {
        return SimpleJsonParser.extractErrorMessage(jsonResponse);
    }
    
    /**
     * Safely convert object to JSON string
     * @param object The object to convert
     * @param objectMapper Ignored - kept for compatibility
     * @return JSON string representation, or null if conversion fails
     */
    public static String toJsonString(Object object, Object objectMapper) {
        return SimpleJsonParser.toJsonString(object);
    }
    
    /**
     * Safely convert object to pretty JSON string
     * @param object The object to convert
     * @param objectMapper Ignored - kept for compatibility
     * @return Pretty JSON string representation, or null if conversion fails
     */
    public static String toPrettyJsonString(Object object, Object objectMapper) {
        return SimpleJsonParser.toPrettyJsonString(object);
    }
    
    /**
     * Check if a string is valid JSON
     * @param jsonString The string to check
     * @param objectMapper Ignored - kept for compatibility
     * @return true if the string is valid JSON
     */
    public static boolean isValidJson(String jsonString, Object objectMapper) {
        return SimpleJsonParser.isValidJson(jsonString);
    }
}