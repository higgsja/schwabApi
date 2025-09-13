package com.higgstx.schwabapi.util;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Simple JSON parser using only built-in Java libraries
 * Replaces Jackson for basic JSON operations to avoid module issues
 */
@Slf4j
public final class SimpleJsonParser {
    
    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?");
    
    private SimpleJsonParser() {}
    
    /**
     * Parse JSON string to Map
     */
    public static Map<String, Object> parseToMap(String json) {
        if (StringUtils.isBlank(json)) {
            return new HashMap<>();
        }
        
        try {
            return (Map<String, Object>) parseValue(json.trim());
        } catch (Exception e) {
            log.warn("Failed to parse JSON: {}", e.getMessage());
            return new HashMap<>();
        }
    }
    
    /**
     * Convert object to JSON string
     */
    public static String toJsonString(Object obj) {
        if (obj == null) return null;  // Return null for null input
        
        if (obj instanceof String) {
            return "\"" + escapeString((String) obj) + "\"";
        }
        
        if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }
        
        if (obj instanceof Map) {
            return mapToJson((Map<?, ?>) obj);
        }
        
        if (obj instanceof Collection) {
            return collectionToJson((Collection<?>) obj);
        }
        
        if (obj instanceof Instant) {
            return "\"" + obj.toString() + "\"";
        }
        
        // For other objects, use toString and quote it
        return "\"" + escapeString(obj.toString()) + "\"";
    }
    
    /**
     * Convert object to JSON string with JSON-compliant null handling
     */
    public static String toJsonStringWithNulls(Object obj) {
        if (obj == null) return "null";  // Return JSON null for actual JSON serialization
        return toJsonString(obj);
    }
    
    /**
     * Pretty print JSON with indentation
     */
    public static String toPrettyJsonString(Object obj) {
        return formatJson(toJsonString(obj), 0);
    }
    
    /**
     * Extract error message from JSON response
     */
    public static String extractErrorMessage(String jsonResponse) {
        if (StringUtils.isBlank(jsonResponse)) {
            return "Unknown error";
        }
        
        try {
            Map<String, Object> data = parseToMap(jsonResponse);
            
            // Priority: error_description > message > error
            if (data.containsKey("error_description")) {
                return data.get("error_description").toString();
            }
            
            if (data.containsKey("message")) {
                return data.get("message").toString();
            }
            
            if (data.containsKey("error")) {
                return data.get("error").toString();
            }
            
        } catch (Exception e) {
            log.debug("Failed to extract error message from JSON: {}", e.getMessage());
        }
        
        return "Unknown error";
    }
    
    /**
     * Extract error details from JSON response
     */
    public static Map<String, Object> extractErrorDetails(String jsonResponse) {
        Map<String, Object> details = new HashMap<>();
        
        if (StringUtils.isBlank(jsonResponse)) {
            return details;
        }
        
        try {
            Map<String, Object> data = parseToMap(jsonResponse);
            
            if (data.containsKey("error")) {
                details.put("error", data.get("error"));
            }
            
            if (data.containsKey("error_description")) {
                details.put("description", data.get("error_description"));
            }
            
            if (data.containsKey("message")) {
                details.put("message", data.get("message"));
            }
            
            if (data.containsKey("error_code")) {
                details.put("error_code", data.get("error_code"));
            }
            
        } catch (Exception e) {
            log.debug("Failed to parse error JSON, storing raw response: {}", e.getMessage());
            details.put("raw_response", jsonResponse);
        }
        
        return details;
    }
    
    /**
     * Check if string is valid JSON
     */
    public static boolean isValidJson(String json) {
        if (StringUtils.isBlank(json)) {
            return false;
        }
        
        try {
            parseValue(json.trim());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    // Private parsing methods
    private static Object parseValue(String json) {
        json = json.trim();
        
        if (json.isEmpty()) {
            throw new IllegalArgumentException("Empty JSON");
        }
        
        char first = json.charAt(0);
        
        switch (first) {
            case '{':
                return parseObject(json);
            case '[':
                return parseArray(json);
            case '"':
                return parseString(json);
            case 't':
                if (json.equals("true")) return true;
                throw new IllegalArgumentException("Invalid JSON value: " + json);
            case 'f':
                if (json.equals("false")) return false;
                throw new IllegalArgumentException("Invalid JSON value: " + json);
            case 'n':
                if (json.equals("null")) return null;
                throw new IllegalArgumentException("Invalid JSON value: " + json);
            default:
                if (first == '-' || Character.isDigit(first)) {
                    return parseNumber(json);
                }
        }
        
        throw new IllegalArgumentException("Invalid JSON value: " + json);
    }
    
    private static Map<String, Object> parseObject(String json) {
        Map<String, Object> result = new HashMap<>();
        
        if (!json.startsWith("{") || !json.endsWith("}")) {
            throw new IllegalArgumentException("Invalid JSON object: " + json);
        }
        
        if (json.equals("{}")) {
            return result;
        }
        
        json = json.substring(1, json.length() - 1).trim(); // Remove {}
        
        if (json.isEmpty()) {
            return result;
        }
        
        List<String> pairs = splitJsonArray(json);
        
        for (String pair : pairs) {
            int colonIndex = findColon(pair);
            if (colonIndex == -1) {
                throw new IllegalArgumentException("Invalid JSON object pair: " + pair);
            }
            
            String keyPart = pair.substring(0, colonIndex).trim();
            String valuePart = pair.substring(colonIndex + 1).trim();
            
            // Keys must be strings in JSON
            if (!keyPart.startsWith("\"") || !keyPart.endsWith("\"")) {
                throw new IllegalArgumentException("Invalid JSON object key: " + keyPart);
            }
            
            String key = (String) parseValue(keyPart);
            Object value = parseValue(valuePart);
            
            result.put(key, value);
        }
        
        return result;
    }
    
    private static List<Object> parseArray(String json) {
        List<Object> result = new ArrayList<>();
        
        if (!json.startsWith("[") || !json.endsWith("]")) {
            throw new IllegalArgumentException("Invalid JSON array: " + json);
        }
        
        if (json.equals("[]")) {
            return result;
        }
        
        json = json.substring(1, json.length() - 1).trim(); // Remove []
        
        if (json.isEmpty()) {
            return result;
        }
        
        List<String> elements = splitJsonArray(json);
        
        for (String element : elements) {
            result.add(parseValue(element.trim()));
        }
        
        return result;
    }
    
    private static String parseString(String json) {
        if (json.length() < 2 || !json.startsWith("\"") || !json.endsWith("\"")) {
            throw new IllegalArgumentException("Invalid string JSON: " + json);
        }
        
        return unescapeString(json.substring(1, json.length() - 1));
    }
    
    private static Number parseNumber(String json) {
        if (!NUMBER_PATTERN.matcher(json).matches()) {
            throw new IllegalArgumentException("Invalid number JSON: " + json);
        }
        
        if (json.contains(".") || json.toLowerCase().contains("e")) {
            return Double.parseDouble(json);
        } else {
            long longValue = Long.parseLong(json);
            if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                return (int) longValue;
            }
            return longValue;
        }
    }
    
    private static List<String> splitJsonArray(String content) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        
        for (char c : content.toCharArray()) {
            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }
            
            if (c == '\\' && inString) {
                escaped = true;
                current.append(c);
                continue;
            }
            
            if (c == '"') {
                inString = !inString;
            }
            
            if (!inString) {
                if (c == '{' || c == '[') {
                    depth++;
                } else if (c == '}' || c == ']') {
                    depth--;
                } else if (c == ',' && depth == 0) {
                    result.add(current.toString());
                    current = new StringBuilder();
                    continue;
                }
            }
            
            current.append(c);
        }
        
        if (current.length() > 0) {
            result.add(current.toString());
        }
        
        return result;
    }
    
    private static int findColon(String pair) {
        boolean inString = false;
        boolean escaped = false;
        
        for (int i = 0; i < pair.length(); i++) {
            char c = pair.charAt(i);
            
            if (escaped) {
                escaped = false;
                continue;
            }
            
            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }
            
            if (c == '"') {
                inString = !inString;
            }
            
            if (!inString && c == ':') {
                return i;
            }
        }
        
        return -1;
    }
    
    private static String escapeString(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    private static String unescapeString(String str) {
        return str.replace("\\\"", "\"")
                  .replace("\\\\", "\\")
                  .replace("\\n", "\n")
                  .replace("\\r", "\r")
                  .replace("\\t", "\t");
    }
    
    private static String mapToJson(Map<?, ?> map) {
        if (map.isEmpty()) {
            return "{}";
        }
        
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            
            sb.append(toJsonString(entry.getKey().toString()));
            sb.append(":");
            sb.append(toJsonStringWithNulls(entry.getValue())); // Use JSON-compliant null handling for values
        }
        
        sb.append("}");
        return sb.toString();
    }
    
    private static String collectionToJson(Collection<?> collection) {
        if (collection.isEmpty()) {
            return "[]";
        }
        
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        
        for (Object item : collection) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append(toJsonStringWithNulls(item)); // Use JSON-compliant null handling for array elements
        }
        
        sb.append("]");
        return sb.toString();
    }
    
    private static String formatJson(String json, int indent) {
        if (json == null) {
            return null;
        }
        
        // Simple pretty printing - add newlines and indentation
        StringBuilder result = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;
        
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            
            if (escaped) {
                result.append(c);
                escaped = false;
                continue;
            }
            
            if (c == '\\' && inString) {
                escaped = true;
                result.append(c);
                continue;
            }
            
            if (c == '"') {
                inString = !inString;
            }
            
            if (!inString) {
                if (c == '{' || c == '[') {
                    result.append(c).append('\n');
                    indent += 2;
                    result.append(" ".repeat(indent));
                } else if (c == '}' || c == ']') {
                    result.append('\n');
                    indent -= 2;
                    result.append(" ".repeat(indent)).append(c);
                } else if (c == ',') {
                    result.append(c).append('\n').append(" ".repeat(indent));
                } else if (c == ':') {
                    result.append(c).append(' ');
                } else {
                    result.append(c);
                }
            } else {
                result.append(c);
            }
        }
        
        return result.toString();
    }
}