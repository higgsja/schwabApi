package com.higgstx.schwabapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.higgstx.schwabapi.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Essential JsonUtils functionality tests
 */
@DisplayName("JsonUtils Core Tests")
class SimplifiedJsonUtilsTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = JsonUtils.createStandardObjectMapper();
    }

    @Test
    @DisplayName("Standard ObjectMapper should be properly configured")
    void testObjectMapperConfiguration() {
        ObjectMapper mapper = JsonUtils.createStandardObjectMapper();

        assertNotNull(mapper);
        assertFalse(mapper.getDeserializationConfig().isEnabled(
            com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
    }

    @Test
    @DisplayName("Valid JSON should parse to map correctly")
    void testJsonParsingToMap() {
        String json = "{\"symbol\": \"AAPL\", \"price\": 150.25, \"active\": true}";

        Map<String, Object> result = JsonUtils.parseJsonToMap(json, objectMapper);

        assertEquals("AAPL", result.get("symbol"));
        assertEquals(150.25, result.get("price"));
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
            Map<String, Object> result = JsonUtils.parseJsonToMap(invalidJson, objectMapper);
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

        Map<String, Object> details = JsonUtils.extractErrorDetails(errorJson, objectMapper);

        assertEquals("invalid_request", details.get("error"));
        assertEquals("The request is missing required parameters", details.get("description"));
        assertEquals("Bad request", details.get("message"));
    }

    @Test
    @DisplayName("Error message should be extracted with proper fallback hierarchy")
    void testErrorMessageExtraction() {
        // Test priority: error_description > message > error
        String jsonWithDescription = "{\"error\": \"bad\", \"error_description\": \"detailed error\", \"message\": \"generic\"}";
        assertEquals("detailed error", JsonUtils.extractErrorMessage(jsonWithDescription, objectMapper));

        String jsonWithMessage = "{\"error\": \"bad\", \"message\": \"generic message\"}";
        assertEquals("generic message", JsonUtils.extractErrorMessage(jsonWithMessage, objectMapper));

        String jsonWithError = "{\"error\": \"simple error\"}";
        assertEquals("simple error", JsonUtils.extractErrorMessage(jsonWithError, objectMapper));

        String invalidJson = "not json";
        assertEquals("Unknown error", JsonUtils.extractErrorMessage(invalidJson, objectMapper));
    }

    @Test
    @DisplayName("Objects should serialize to JSON correctly")
    void testJsonSerialization() {
        Map<String, Object> object = Map.of(
            "name", "AAPL",
            "price", 150.25,
            "active", true
        );

        String json = JsonUtils.toJsonString(object, objectMapper);

        assertNotNull(json);
        assertTrue(json.contains("\"name\""));
        assertTrue(json.contains("\"AAPL\""));
        assertTrue(json.contains("150.25"));
    }

    @Test
    @DisplayName("Pretty JSON should format with proper indentation")
    void testPrettyJsonFormatting() {
        Map<String, Object> object = Map.of("key", "value", "nested", Map.of("inner", "data"));

        String prettyJson = JsonUtils.toPrettyJsonString(object, objectMapper);

        assertNotNull(prettyJson);
        assertTrue(prettyJson.contains("\n")); // Should have newlines
        assertTrue(prettyJson.split("\n").length > 3); // Multiple lines due to formatting
    }

    @Test
    @DisplayName("JSON validation should identify valid and invalid JSON")
    void testJsonValidation() {
        // Valid JSON
        assertTrue(JsonUtils.isValidJson("{\"valid\": \"json\"}", objectMapper));
        assertTrue(JsonUtils.isValidJson("[]", objectMapper));
        assertTrue(JsonUtils.isValidJson("\"string\"", objectMapper));
        assertTrue(JsonUtils.isValidJson("42", objectMapper));

        // Invalid JSON
        assertFalse(JsonUtils.isValidJson("{ invalid }", objectMapper));
        assertFalse(JsonUtils.isValidJson("not json", objectMapper));
        assertFalse(JsonUtils.isValidJson(null, objectMapper));
        assertFalse(JsonUtils.isValidJson("", objectMapper));
    }

    @Test
    @DisplayName("JSON node value extraction should handle all data types")
    void testJsonNodeValueExtraction() throws Exception {
        String json = "{" +
                "\"stringField\": \"test\"," +
                "\"doubleField\": 123.45," +
                "\"longField\": 9876543210," +
                "\"booleanField\": true" +
                "}";
        JsonNode node = objectMapper.readTree(json);

        // String extraction
        assertEquals("test", JsonUtils.getStringValue(node, "stringField"));
        assertNull(JsonUtils.getStringValue(node, "doubleField")); // Not a string

        // Numeric extraction
        assertEquals(123.45, JsonUtils.getDoubleValue(node, "doubleField"));
        assertEquals(9876543210L, JsonUtils.getLongValue(node, "longField"));

        // Boolean extraction
        assertTrue(JsonUtils.getBooleanValue(node, "booleanField"));

        // Non-existent fields
        assertNull(JsonUtils.getStringValue(node, "missing"));
        assertNull(JsonUtils.getDoubleValue(node, "missing"));
    }

    @Test
    @DisplayName("JSON object merging should work correctly")
    void testJsonObjectMerging() {
        Map<String, Object> base = Map.of("field1", "value1", "shared", "base");
        Map<String, Object> overlay = Map.of("field2", "value2", "shared", "overlay");

        Map<String, Object> result = JsonUtils.mergeJsonObjects(base, overlay);

        assertEquals("value1", result.get("field1")); // From base
        assertEquals("value2", result.get("field2")); // From overlay
        assertEquals("overlay", result.get("shared")); // Overlay wins
        assertEquals(3, result.size());
    }

    @Test
    @DisplayName("Deep cloning should create independent copies")
    void testDeepCloning() {
        Map<String, Object> original = Map.of("field", "value", "number", 42);

        @SuppressWarnings("unchecked")
        Map<String, Object> cloned = JsonUtils.deepClone(original, Map.class, objectMapper);

        assertNotNull(cloned);
        assertNotSame(original, cloned);
        assertEquals(original.get("field"), cloned.get("field"));
        assertEquals(original.get("number"), cloned.get("number"));
    }

    @Test
    @DisplayName("Null inputs should be handled safely")
    void testNullSafety() {
        assertNull(JsonUtils.toJsonString(null, objectMapper));
        assertNull(JsonUtils.toPrettyJsonString(null, objectMapper));
        assertNull(JsonUtils.deepClone(null, Map.class, objectMapper));
        
        assertTrue(JsonUtils.parseJsonToMap(null, objectMapper).isEmpty());
        assertTrue(JsonUtils.extractErrorDetails(null, objectMapper).isEmpty());
        assertEquals("Unknown error", JsonUtils.extractErrorMessage(null, objectMapper));
    }
}