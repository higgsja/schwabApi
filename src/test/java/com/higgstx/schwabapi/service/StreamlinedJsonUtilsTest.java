package com.higgstx.schwabapi.service;

import com.higgstx.schwabapi.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Essential JsonUtils functionality tests - now using SimpleJsonParser
 */
@DisplayName("JsonUtils Core Tests")
class SimplifiedJsonUtilsTest {

    @Test
    @DisplayName("Valid JSON should parse to map correctly")
    void testJsonParsingToMap() {
        String json = "{\"symbol\": \"AAPL\", \"price\": 150.25, \"active\": true}";

        Map<String, Object> result = SimpleJsonParser.parseToMap(json);

        assertEquals("AAPL", result.get("symbol"));
        assertEquals(150.25, ((Number) result.get("price")).doubleValue());
        assertEquals(true, result.get("active"));
    }

    @Test
    @DisplayName("Invalid JSON should return empty map")
    void testInvalidJsonParsing() {
        String[] invalidJsons = {
            "{ invalid json }",
            "not json at all",
            null,
            ""
        };

        for (String invalidJson : invalidJsons) {
            Map<String, Object> result = SimpleJsonParser.parseToMap(invalidJson);
            assertTrue(result.isEmpty());
        }
    }

    @Test
    @DisplayName("Error details should be extracted from JSON response")
    void testErrorDetailsExtraction() {
        String errorJson = "{" +
                "\"error\": \"invalid_request\"," +
                "\"error_description\": \"The request is missing required parameters\"," +
                "\"message\": \"Bad request\"" +
                "}";

        Map<String, Object> details = SimpleJsonParser.extractErrorDetails(errorJson);

        assertEquals("invalid_request", details.get("error"));
        assertEquals("The request is missing required parameters", details.get("description"));
        assertEquals("Bad request", details.get("message"));
    }

    @Test
    @DisplayName("Error message should be extracted with proper fallback hierarchy")
    void testErrorMessageExtraction() {
        // Test priority: error_description > message > error
        String jsonWithDescription = "{\"error\": \"bad\", \"error_description\": \"detailed error\", \"message\": \"generic\"}";
        assertEquals("detailed error", SimpleJsonParser.extractErrorMessage(jsonWithDescription));

        String jsonWithMessage = "{\"error\": \"bad\", \"message\": \"generic message\"}";
        assertEquals("generic message", SimpleJsonParser.extractErrorMessage(jsonWithMessage));

        String jsonWithError = "{\"error\": \"simple error\"}";
        assertEquals("simple error", SimpleJsonParser.extractErrorMessage(jsonWithError));

        String invalidJson = "not json";
        assertEquals("Unknown error", SimpleJsonParser.extractErrorMessage(invalidJson));
    }

    @Test
    @DisplayName("Objects should serialize to JSON correctly")
    void testJsonSerialization() {
        Map<String, Object> object = Map.of(
            "name", "AAPL",
            "price", 150.25,
            "active", true
        );

        String json = SimpleJsonParser.toJsonString(object);

        assertNotNull(json);
        assertTrue(json.contains("\"name\""));
        assertTrue(json.contains("\"AAPL\""));
        assertTrue(json.contains("150.25"));
    }

    @Test
    @DisplayName("Pretty JSON should format with proper indentation")
    void testPrettyJsonFormatting() {
        Map<String, Object> object = Map.of("key", "value", "nested", Map.of("inner", "data"));

        String prettyJson = SimpleJsonParser.toPrettyJsonString(object);

        assertNotNull(prettyJson);
        assertTrue(prettyJson.contains("\n")); // Should have newlines
        assertTrue(prettyJson.split("\n").length > 3); // Multiple lines due to formatting
    }

    @Test
    @DisplayName("JSON validation should identify valid and invalid JSON")
    void testJsonValidation() {
        // Valid JSON
        assertTrue(SimpleJsonParser.isValidJson("{\"valid\": \"json\"}"));
        assertTrue(SimpleJsonParser.isValidJson("[]"));
        assertTrue(SimpleJsonParser.isValidJson("\"string\""));
        assertTrue(SimpleJsonParser.isValidJson("42"));

        // Invalid JSON
        assertFalse(SimpleJsonParser.isValidJson("{ invalid }"));
        assertFalse(SimpleJsonParser.isValidJson("not json"));
        assertFalse(SimpleJsonParser.isValidJson(null));
        assertFalse(SimpleJsonParser.isValidJson(""));
    }

    @Test
    @DisplayName("JSON object merging through parsing")
    void testJsonObjectHandling() {
        String json1 = "{\"field1\": \"value1\", \"shared\": \"base\"}";
        String json2 = "{\"field2\": \"value2\", \"shared\": \"overlay\"}";

        Map<String, Object> map1 = SimpleJsonParser.parseToMap(json1);
        Map<String, Object> map2 = SimpleJsonParser.parseToMap(json2);

        assertEquals("value1", map1.get("field1"));
        assertEquals("value2", map2.get("field2"));
        assertEquals("base", map1.get("shared"));
        assertEquals("overlay", map2.get("shared"));
    }

    @Test
    @DisplayName("Null inputs should be handled safely")
    void testNullSafety() {
        assertNull(SimpleJsonParser.toJsonString(null));
        
        assertTrue(SimpleJsonParser.parseToMap(null).isEmpty());
        assertTrue(SimpleJsonParser.extractErrorDetails(null).isEmpty());
        assertEquals("Unknown error", SimpleJsonParser.extractErrorMessage(null));
    }

    @Test
    @DisplayName("Complex nested JSON should parse correctly")
    void testComplexJsonParsing() {
        String complexJson = "{" +
                "\"user\": {" +
                "  \"name\": \"John\"," +
                "  \"age\": 30," +
                "  \"active\": true" +
                "}," +
                "\"scores\": [85, 92, 78]," +
                "\"metadata\": null" +
                "}";

        Map<String, Object> result = SimpleJsonParser.parseToMap(complexJson);

        assertNotNull(result);
        assertTrue(result.containsKey("user"));
        assertTrue(result.containsKey("scores"));
        assertTrue(result.containsKey("metadata"));

        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) result.get("user");
        assertEquals("John", user.get("name"));
        assertEquals(30, ((Number) user.get("age")).intValue());
        assertEquals(true, user.get("active"));

        @SuppressWarnings("unchecked")
        java.util.List<Object> scores = (java.util.List<Object>) result.get("scores");
        assertEquals(3, scores.size());
        assertEquals(85, ((Number) scores.get(0)).intValue());

        assertNull(result.get("metadata"));
    }

    @Test
    @DisplayName("JsonUtils compatibility layer should work")
    void testJsonUtilsCompatibility() {
        String json = "{\"test\": \"value\"}";
        
        // Test the compatibility methods in JsonUtils
        Map<String, Object> result = JsonUtils.parseJsonToMap(json, null);
        assertEquals("value", result.get("test"));
        
        String serialized = JsonUtils.toJsonString(Map.of("key", "value"), null);
        assertNotNull(serialized);
        assertTrue(serialized.contains("key"));
        
        assertTrue(JsonUtils.isValidJson(json, null));
        assertFalse(JsonUtils.isValidJson("invalid", null));
    }
}