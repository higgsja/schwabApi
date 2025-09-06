package com.higgstx.schwabapi.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * JSON processing utility functions extracted from various classes
 */
public final class JsonUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(JsonUtils.class);
    
    private JsonUtils() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Create a standard ObjectMapper configured for the Schwab API
     * @return Configured ObjectMapper
     */
    public static ObjectMapper createStandardObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(
                    com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, 
                    false)
                .configure(SerializationFeature.INDENT_OUTPUT, true);
    }
    
    /**
     * Safely parse JSON string into a Map
     * @param jsonString The JSON string to parse
     * @param objectMapper The ObjectMapper to use
     * @return Map representation of the JSON, or empty map if parsing fails
     */
    public static Map<String, Object> parseJsonToMap(String jsonString, ObjectMapper objectMapper) {
        if (StringUtils.isBlank(jsonString)) {
            return new HashMap<>();
        }
        
        try {
            return objectMapper.readValue(jsonString, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            logger.warn("Failed to parse JSON string: {}", e.getMessage());
            return new HashMap<>();
        }
    }
    
    /**
     * Safely parse JSON string into a Map using standard ObjectMapper
     * @param jsonString The JSON string to parse
     * @return Map representation of the JSON, or empty map if parsing fails
     */
    public static Map<String, Object> parseJsonToMap(String jsonString) {
        return parseJsonToMap(jsonString, createStandardObjectMapper());
    }
    
    /**
     * Extract error information from a JSON response
     * @param jsonResponse The JSON response string
     * @param objectMapper The ObjectMapper to use
     * @return Map containing error details
     */
    public static Map<String, Object> extractErrorDetails(String jsonResponse, ObjectMapper objectMapper) {
        Map<String, Object> details = new HashMap<>();
        
        if (StringUtils.isBlank(jsonResponse)) {
            return details;
        }
        
        try {
            JsonNode errorJson = objectMapper.readTree(jsonResponse);
            
            if (errorJson.has("error")) {
                details.put("error", errorJson.get("error").asText());
            }
            
            if (errorJson.has("error_description")) {
                details.put("description", errorJson.get("error_description").asText());
            }
            
            if (errorJson.has("message")) {
                details.put("message", errorJson.get("message").asText());
            }
            
            if (errorJson.has("errors")) {
                details.put("errors", errorJson.get("errors"));
            }
            
            // Additional common error fields
            if (errorJson.has("error_code")) {
                details.put("error_code", errorJson.get("error_code").asText());
            }
            
            if (errorJson.has("timestamp")) {
                details.put("timestamp", errorJson.get("timestamp").asText());
            }
            
        } catch (Exception e) {
            logger.debug("Failed to parse error JSON, storing raw response: {}", e.getMessage());
            details.put("raw_response", jsonResponse);
        }
        
        return details;
    }
    
    /**
     * Extract error message from a JSON response
     * @param jsonResponse The JSON response string
     * @param objectMapper The ObjectMapper to use
     * @return Error message, or a default message if extraction fails
     */
    public static String extractErrorMessage(String jsonResponse, ObjectMapper objectMapper) {
        if (StringUtils.isBlank(jsonResponse)) {
            return "Unknown error";
        }
        
        try {
            JsonNode errorJson = objectMapper.readTree(jsonResponse);
            
            if (errorJson.has("error_description")) {
                return errorJson.get("error_description").asText();
            }
            
            if (errorJson.has("message")) {
                return errorJson.get("message").asText();
            }
            
            if (errorJson.has("error")) {
                return errorJson.get("error").asText();
            }
            
        } catch (Exception e) {
            logger.debug("Failed to extract error message from JSON: {}", e.getMessage());
        }
        
        return "Unknown error";
    }
    
    /**
     * Safely convert object to JSON string
     * @param object The object to convert
     * @param objectMapper The ObjectMapper to use
     * @return JSON string representation, or null if conversion fails
     */
    public static String toJsonString(Object object, ObjectMapper objectMapper) {
        if (object == null) {
            return null;
        }
        
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            logger.warn("Failed to convert object to JSON: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Safely convert object to pretty JSON string
     * @param object The object to convert
     * @param objectMapper The ObjectMapper to use
     * @return Pretty JSON string representation, or null if conversion fails
     */
    public static String toPrettyJsonString(Object object, ObjectMapper objectMapper) {
        if (object == null) {
            return null;
        }
        
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (Exception e) {
            logger.warn("Failed to convert object to pretty JSON: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Check if a string is valid JSON
     * @param jsonString The string to check
     * @param objectMapper The ObjectMapper to use
     * @return true if the string is valid JSON
     */
    public static boolean isValidJson(String jsonString, ObjectMapper objectMapper) {
        if (StringUtils.isBlank(jsonString)) {
            return false;
        }
        
        try {
            objectMapper.readTree(jsonString);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Safely get a string value from a JSON node
     * @param node The JSON node
     * @param fieldName The field name
     * @return The string value, or null if not found or not a string
     */
    public static String getStringValue(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName)) {
            return null;
        }
        
        JsonNode fieldNode = node.get(fieldName);
        return fieldNode.isTextual() ? fieldNode.asText() : null;
    }
    
    /**
     * Safely get a numeric value from a JSON node as Double
     * @param node The JSON node
     * @param fieldName The field name
     * @return The numeric value as Double, or null if not found or not numeric
     */
    public static Double getDoubleValue(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName)) {
            return null;
        }
        
        JsonNode fieldNode = node.get(fieldName);
        return fieldNode.isNumber() ? fieldNode.asDouble() : null;
    }
    
    /**
     * Safely get a numeric value from a JSON node as Long
     * @param node The JSON node
     * @param fieldName The field name
     * @return The numeric value as Long, or null if not found or not numeric
     */
    public static Long getLongValue(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName)) {
            return null;
        }
        
        JsonNode fieldNode = node.get(fieldName);
        return fieldNode.isNumber() ? fieldNode.asLong() : null;
    }
    
    /**
     * Safely get a boolean value from a JSON node
     * @param node The JSON node
     * @param fieldName The field name
     * @return The boolean value, or null if not found or not boolean
     */
    public static Boolean getBooleanValue(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName)) {
            return null;
        }
        
        JsonNode fieldNode = node.get(fieldName);
        return fieldNode.isBoolean() ? fieldNode.asBoolean() : null;
    }
    
    /**
     * Merge two JSON objects (maps)
     * @param base The base JSON object
     * @param overlay The overlay JSON object (values will override base)
     * @return Merged JSON object
     */
    public static Map<String, Object> mergeJsonObjects(Map<String, Object> base, Map<String, Object> overlay) {
        Map<String, Object> result = new HashMap<>();
        
        if (base != null) {
            result.putAll(base);
        }
        
        if (overlay != null) {
            result.putAll(overlay);
        }
        
        return result;
    }
    
    /**
     * Deep clone an object using JSON serialization
     * @param object The object to clone
     * @param objectClass The class of the object
     * @param objectMapper The ObjectMapper to use
     * @param <T> The type of the object
     * @return Cloned object, or null if cloning fails
     */
    public static <T> T deepClone(T object, Class<T> objectClass, ObjectMapper objectMapper) {
        if (object == null) {
            return null;
        }
        
        try {
            String json = objectMapper.writeValueAsString(object);
            return objectMapper.readValue(json, objectClass);
        } catch (Exception e) {
            logger.warn("Failed to deep clone object of type {}: {}", 
                       objectClass.getSimpleName(), e.getMessage());
            return null;
        }
    }
}