package com.higgstx.schwabapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.higgstx.schwabapi.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for JsonUtils utility class
 */
class JsonUtilsTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = JsonUtils.createStandardObjectMapper();
    }

    @Nested
    @DisplayName("ObjectMapper Creation Tests")
    class ObjectMapperCreationTests {

        @Test
        @DisplayName("createStandardObjectMapper should create configured ObjectMapper")
        void createStandardObjectMapper_CreatesConfiguredMapper() {
            // When
            ObjectMapper mapper = JsonUtils.createStandardObjectMapper();

            // Then
            assertNotNull(mapper);
            // Verify it's configured for Schwab API needs
            assertFalse(mapper.getDeserializationConfig().isEnabled(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        }

        @Test
        @DisplayName("createStandardObjectMapper should handle Java time module")
        void createStandardObjectMapper_HandlesJavaTimeModule() {
            // Given
            ObjectMapper mapper = JsonUtils.createStandardObjectMapper();

            // When & Then - Should not throw exception with time types
            assertDoesNotThrow(() -> {
                String json = "{" +
                    "\"timestamp\": \"2024-01-15T10:30:00Z\"," +
                    "\"date\": \"2024-01-15\"" +
                    "}";
                mapper.readTree(json);
            });
        }
    }

    @Nested
    @DisplayName("JSON Parsing Tests")
    class JsonParsingTests {

        @Test
        @DisplayName("parseJsonToMap should parse valid JSON")
        void parseJsonToMap_ValidJson_ParsesCorrectly() {
            // Given
            String json = "{" +
                    "\"name\": \"AAPL\"," +
                    "\"price\": 150.25," +
                    "\"volume\": 1000000," +
                    "\"active\": true" +
                    "}";

            // When
            Map<String, Object> result = JsonUtils.parseJsonToMap(json, objectMapper);

            // Then
            assertEquals("AAPL", result.get("name"));
            assertEquals(150.25, result.get("price"));
            assertEquals(1000000, result.get("volume"));
            assertEquals(true, result.get("active"));
        }

        @Test
        @DisplayName("parseJsonToMap should handle nested objects")
        void parseJsonToMap_NestedObjects_ParsesCorrectly() {
            // Given
            String json = "{" +
                    "\"symbol\": \"AAPL\"," +
                    "\"quote\": {" +
                    "\"price\": 150.25," +
                    "\"change\": 2.5" +
                    "}," +
                    "\"metadata\": {" +
                    "\"source\": \"schwab\"," +
                    "\"timestamp\": \"2024-01-15T10:30:00Z\"" +
                    "}" +
                    "}";

            // When
            Map<String, Object> result = JsonUtils.parseJsonToMap(json, objectMapper);

            // Then
            assertEquals("AAPL", result.get("symbol"));
            assertInstanceOf(Map.class, result.get("quote"));
            assertInstanceOf(Map.class, result.get("metadata"));
            
            @SuppressWarnings("unchecked")
            Map<String, Object> quote = (Map<String, Object>) result.get("quote");
            assertEquals(150.25, quote.get("price"));
            assertEquals(2.5, quote.get("change"));
        }

        @Test
        @DisplayName("parseJsonToMap should return empty map for null input")
        void parseJsonToMap_NullInput_ReturnsEmptyMap() {
            // When
            Map<String, Object> result = JsonUtils.parseJsonToMap(null, objectMapper);

            // Then
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("parseJsonToMap should return empty map for empty input")
        void parseJsonToMap_EmptyInput_ReturnsEmptyMap() {
            // When
            Map<String, Object> result = JsonUtils.parseJsonToMap("", objectMapper);

            // Then
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("parseJsonToMap should return empty map for invalid JSON")
        void parseJsonToMap_InvalidJson_ReturnsEmptyMap() {
            // Given
            String invalidJson = "{ invalid json }";

            // When
            Map<String, Object> result = JsonUtils.parseJsonToMap(invalidJson, objectMapper);

            // Then
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("parseJsonToMap with default mapper should work")
        void parseJsonToMap_DefaultMapper_Works() {
            // Given
            String json = "{\"test\": \"value\"}";

            // When
            Map<String, Object> result = JsonUtils.parseJsonToMap(json);

            // Then
            assertEquals("value", result.get("test"));
        }
    }

    @Nested
    @DisplayName("Error Details Extraction Tests")
    class ErrorDetailsExtractionTests {

        @Test
        @DisplayName("extractErrorDetails should extract all error fields")
        void extractErrorDetails_AllErrorFields_ExtractsAll() {
            // Given
            String errorJson = "{" +
                    "\"error\": \"invalid_request\"," +
                    "\"error_description\": \"The request is missing required parameters\"," +
                    "\"message\": \"Bad request\"," +
                    "\"error_code\": \"ERR_001\"," +
                    "\"timestamp\": \"2024-01-15T10:30:00Z\"," +
                    "\"errors\": [" +
                    "{\"field\": \"username\", \"message\": \"required\"}," +
                    "{\"field\": \"password\", \"message\": \"too_short\"}" +
                    "]" +
                    "}";

            // When
            Map<String, Object> details = JsonUtils.extractErrorDetails(errorJson, objectMapper);

            // Then
            assertEquals("invalid_request", details.get("error"));
            assertEquals("The request is missing required parameters", details.get("description"));
            assertEquals("Bad request", details.get("message"));
            assertEquals("ERR_001", details.get("error_code"));
            assertEquals("2024-01-15T10:30:00Z", details.get("timestamp"));
            assertNotNull(details.get("errors"));
        }

        @Test
        @DisplayName("extractErrorDetails should handle partial error information")
        void extractErrorDetails_PartialErrorInfo_ExtractsAvailable() {
            // Given
            String errorJson = "{" +
                    "\"error\": \"server_error\"," +
                    "\"other_field\": \"ignored\"" +
                    "}";

            // When
            Map<String, Object> details = JsonUtils.extractErrorDetails(errorJson, objectMapper);

            // Then
            assertEquals("server_error", details.get("error"));
            assertFalse(details.containsKey("description"));
            assertFalse(details.containsKey("other_field"));
        }

        @Test
        @DisplayName("extractErrorDetails should return empty map for null input")
        void extractErrorDetails_NullInput_ReturnsEmptyMap() {
            // When
            Map<String, Object> details = JsonUtils.extractErrorDetails(null, objectMapper);

            // Then
            assertNotNull(details);
            assertTrue(details.isEmpty());
        }

        @Test
        @DisplayName("extractErrorDetails should handle invalid JSON gracefully")
        void extractErrorDetails_InvalidJson_HandlesGracefully() {
            // Given
            String invalidJson = "{ invalid json }";

            // When
            Map<String, Object> details = JsonUtils.extractErrorDetails(invalidJson, objectMapper);

            // Then
            assertNotNull(details);
            assertEquals(invalidJson, details.get("raw_response"));
        }
    }

    @Nested
    @DisplayName("Error Message Extraction Tests")
    class ErrorMessageExtractionTests {

        @Test
        @DisplayName("extractErrorMessage should extract error_description first")
        void extractErrorMessage_ErrorDescription_ExtractsErrorDescription() {
            // Given
            String errorJson = "{" +
                    "\"error\": \"invalid_request\"," +
                    "\"error_description\": \"Detailed error description\"," +
                    "\"message\": \"Generic message\"" +
                    "}";

            // When
            String errorMessage = JsonUtils.extractErrorMessage(errorJson, objectMapper);

            // Then
            assertEquals("Detailed error description", errorMessage);
        }

        @Test
        @DisplayName("extractErrorMessage should fall back to message")
        void extractErrorMessage_NoErrorDescription_FallsBackToMessage() {
            // Given
            String errorJson = "{" +
                    "\"error\": \"invalid_request\"," +
                    "\"message\": \"Generic message\"" +
                    "}";

            // When
            String errorMessage = JsonUtils.extractErrorMessage(errorJson, objectMapper);

            // Then
            assertEquals("Generic message", errorMessage);
        }

        @Test
        @DisplayName("extractErrorMessage should fall back to error")
        void extractErrorMessage_NoMessageOrDescription_FallsBackToError() {
            // Given
            String errorJson = "{" +
                    "\"error\": \"invalid_request\"" +
                    "}";

            // When
            String errorMessage = JsonUtils.extractErrorMessage(errorJson, objectMapper);

            // Then
            assertEquals("invalid_request", errorMessage);
        }

        @Test
        @DisplayName("extractErrorMessage should return unknown error for invalid JSON")
        void extractErrorMessage_InvalidJson_ReturnsUnknownError() {
            // Given
            String invalidJson = "{ invalid json }";

            // When
            String errorMessage = JsonUtils.extractErrorMessage(invalidJson, objectMapper);

            // Then
            assertEquals("Unknown error", errorMessage);
        }

        @Test
        @DisplayName("extractErrorMessage should return unknown error for empty input")
        void extractErrorMessage_EmptyInput_ReturnsUnknownError() {
            // When
            String errorMessage = JsonUtils.extractErrorMessage("", objectMapper);

            // Then
            assertEquals("Unknown error", errorMessage);
        }
    }

    @Nested
    @DisplayName("JSON Serialization Tests")
    class JsonSerializationTests {

        @Test
        @DisplayName("toJsonString should convert object to JSON")
        void toJsonString_ValidObject_ConvertsToJson() {
            // Given
            Map<String, Object> object = new HashMap<>();
            object.put("name", "AAPL");
            object.put("price", 150.25);

            // When
            String json = JsonUtils.toJsonString(object, objectMapper);

            // Then
            assertNotNull(json);
            assertTrue(json.contains("\"name\""));
            assertTrue(json.contains("\"AAPL\""));
            assertTrue(json.contains("\"price\""));
            assertTrue(json.contains("150.25"));
        }

        @Test
        @DisplayName("toJsonString should return null for null input")
        void toJsonString_NullInput_ReturnsNull() {
            // When
            String json = JsonUtils.toJsonString(null, objectMapper);

            // Then
            assertNull(json);
        }

        @Test
        @DisplayName("toPrettyJsonString should format JSON nicely")
        void toPrettyJsonString_ValidObject_FormatsPretty() {
            // Given
            Map<String, Object> object = new HashMap<>();
            object.put("name", "AAPL");
            object.put("price", 150.25);

            // When
            String prettyJson = JsonUtils.toPrettyJsonString(object, objectMapper);

            // Then
            assertNotNull(prettyJson);
            assertTrue(prettyJson.contains("\n")); // Should have line breaks
            assertTrue(prettyJson.contains("  ")); // Should have indentation
        }

        @Test
        @DisplayName("toPrettyJsonString should return null for null input")
        void toPrettyJsonString_NullInput_ReturnsNull() {
            // When
            String json = JsonUtils.toPrettyJsonString(null, objectMapper);

            // Then
            assertNull(json);
        }
    }

    @Nested
    @DisplayName("JSON Validation Tests")
    class JsonValidationTests {

        @ParameterizedTest
        @ValueSource(strings = {
            "{\"valid\": \"json\"}",
            "[]",
            "{\"nested\": {\"object\": true}}",
            "[{\"array\": \"of\"}, {\"objects\": true}]"
        })
        @DisplayName("isValidJson should return true for valid JSON")
        void isValidJson_ValidJson_ReturnsTrue(String json) {
            // When & Then
            assertTrue(JsonUtils.isValidJson(json, objectMapper));
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "{ invalid json }",
            "not json at all",
            "{",
            "}",
            "[",
            "]",
            ""
        })
        @DisplayName("isValidJson should return false for invalid JSON")
        void isValidJson_InvalidJson_ReturnsFalse(String json) {
            // When & Then
            assertFalse(JsonUtils.isValidJson(json, objectMapper));
        }

        @Test
        @DisplayName("isValidJson should return false for null input")
        void isValidJson_NullInput_ReturnsFalse() {
            // When & Then
            assertFalse(JsonUtils.isValidJson(null, objectMapper));
        }
    }

    @Nested
    @DisplayName("JSON Node Value Extraction Tests")
    class JsonNodeValueExtractionTests {

        @Test
        @DisplayName("getStringValue should extract string values")
        void getStringValue_StringField_ExtractsValue() throws Exception {
            // Given
            String json = "{\"stringField\": \"test value\", \"numberField\": 123}";
            JsonNode node = objectMapper.readTree(json);

            // When & Then
            assertEquals("test value", JsonUtils.getStringValue(node, "stringField"));
            assertNull(JsonUtils.getStringValue(node, "numberField")); // Not a string
            assertNull(JsonUtils.getStringValue(node, "nonexistent"));
        }

        @Test
        @DisplayName("getDoubleValue should extract numeric values")
        void getDoubleValue_NumericField_ExtractsValue() throws Exception {
            // Given
            String json = "{\"doubleField\": 123.45, \"intField\": 42, \"stringField\": \"not a number\"}";
            JsonNode node = objectMapper.readTree(json);

            // When & Then
            assertEquals(123.45, JsonUtils.getDoubleValue(node, "doubleField"));
            assertEquals(42.0, JsonUtils.getDoubleValue(node, "intField"));
            assertNull(JsonUtils.getDoubleValue(node, "stringField")); // Not numeric
            assertNull(JsonUtils.getDoubleValue(node, "nonexistent"));
        }

        @Test
        @DisplayName("getLongValue should extract long values")
        void getLongValue_NumericField_ExtractsValue() throws Exception {
            // Given
            String json = "{\"longField\": 9223372036854775807, \"intField\": 42, \"doubleField\": 123.45}";
            JsonNode node = objectMapper.readTree(json);

            // When & Then
            assertEquals(9223372036854775807L, JsonUtils.getLongValue(node, "longField"));
            assertEquals(42L, JsonUtils.getLongValue(node, "intField"));
            assertEquals(123L, JsonUtils.getLongValue(node, "doubleField")); // Truncated
            assertNull(JsonUtils.getLongValue(node, "nonexistent"));
        }

        @Test
        @DisplayName("getBooleanValue should extract boolean values")
        void getBooleanValue_BooleanField_ExtractsValue() throws Exception {
            // Given
            String json = "{\"trueField\": true, \"falseField\": false, \"stringField\": \"not boolean\"}";
            JsonNode node = objectMapper.readTree(json);

            // When & Then
            assertTrue(JsonUtils.getBooleanValue(node, "trueField"));
            assertFalse(JsonUtils.getBooleanValue(node, "falseField"));
            assertNull(JsonUtils.getBooleanValue(node, "stringField")); // Not boolean
            assertNull(JsonUtils.getBooleanValue(node, "nonexistent"));
        }

        @Test
        @DisplayName("value extraction methods should handle null node")
        void valueExtraction_NullNode_ReturnsNull() {
            // When & Then
            assertNull(JsonUtils.getStringValue(null, "field"));
            assertNull(JsonUtils.getDoubleValue(null, "field"));
            assertNull(JsonUtils.getLongValue(null, "field"));
            assertNull(JsonUtils.getBooleanValue(null, "field"));
        }
    }

    @Nested
    @DisplayName("JSON Object Manipulation Tests")
    class JsonObjectManipulationTests {

        @Test
        @DisplayName("mergeJsonObjects should merge two maps")
        void mergeJsonObjects_TwoMaps_MergesCorrectly() {
            // Given
            Map<String, Object> base = new HashMap<>();
            base.put("field1", "value1");
            base.put("field2", "value2");

            Map<String, Object> overlay = new HashMap<>();
            overlay.put("field2", "overridden");
            overlay.put("field3", "value3");

            // When
            Map<String, Object> result = JsonUtils.mergeJsonObjects(base, overlay);

            // Then
            assertEquals("value1", result.get("field1"));
            assertEquals("overridden", result.get("field2")); // Overlay wins
            assertEquals("value3", result.get("field3"));
        }

        @Test
        @DisplayName("mergeJsonObjects should handle null inputs")
        void mergeJsonObjects_NullInputs_HandlesGracefully() {
            // Given
            Map<String, Object> base = new HashMap<>();
            base.put("field1", "value1");

            // When & Then
            Map<String, Object> result1 = JsonUtils.mergeJsonObjects(null, base);
            assertEquals(base, result1);

            Map<String, Object> result2 = JsonUtils.mergeJsonObjects(base, null);
            assertEquals(base, result2);

            Map<String, Object> result3 = JsonUtils.mergeJsonObjects(null, null);
            assertTrue(result3.isEmpty());
        }

        @Test
        @DisplayName("deepClone should create independent copy")
        void deepClone_Object_CreatesIndependentCopy() {
            // Given
            Map<String, Object> original = new HashMap<>();
            original.put("field1", "value1");
            original.put("field2", 42);

            // When
            @SuppressWarnings("unchecked")
            Map<String, Object> cloned = JsonUtils.deepClone(original, Map.class, objectMapper);

            // Then
            assertNotNull(cloned);
            assertNotSame(original, cloned);
            assertEquals(original, cloned);

            // Verify independence
            original.put("field3", "new value");
            assertFalse(cloned.containsKey("field3"));
        }

        @Test
        @DisplayName("deepClone should return null for null input")
        void deepClone_NullInput_ReturnsNull() {
            // When
            Object cloned = JsonUtils.deepClone(null, Object.class, objectMapper);

            // Then
            assertNull(cloned);
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesTests {

        @Test
        @DisplayName("should handle very large JSON objects")
        void jsonUtils_VeryLargeObjects_HandlesCorrectly() {
            // Given
            Map<String, Object> largeObject = new HashMap<>();
            for (int i = 0; i < 1000; i++) {
                largeObject.put("field" + i, "value" + i);
            }

            // When & Then - Should not throw exceptions
            assertDoesNotThrow(() -> {
                String json = JsonUtils.toJsonString(largeObject, objectMapper);
                assertNotNull(json);
                
                Map<String, Object> parsed = JsonUtils.parseJsonToMap(json, objectMapper);
                assertEquals(1000, parsed.size());
            });
        }

        @Test
        @DisplayName("should handle special characters in JSON")
        void jsonUtils_SpecialCharacters_HandlesCorrectly() {
            // Given
            Map<String, Object> object = new HashMap<>();
            object.put("unicode", "Hello 世界 🌍");
            object.put("quotes", "String with \"quotes\" and 'apostrophes'");
            object.put("newlines", "Line 1\nLine 2\rLine 3");

            // When
            String json = JsonUtils.toJsonString(object, objectMapper);
            Map<String, Object> parsed = JsonUtils.parseJsonToMap(json, objectMapper);

            // Then
            assertEquals("Hello 世界 🌍", parsed.get("unicode"));
            assertEquals("String with \"quotes\" and 'apostrophes'", parsed.get("quotes"));
            assertEquals("Line 1\nLine 2\rLine 3", parsed.get("newlines"));
        }

        @Test
        @DisplayName("should handle circular reference gracefully in deepClone")
        void deepClone_CircularReference_HandlesGracefully() {
            // Given - Create object that would cause circular reference issues
            Map<String, Object> object = new HashMap<>();
            object.put("self", object); // Circular reference

            // When
            @SuppressWarnings("unchecked")
            Map<String, Object> cloned = JsonUtils.deepClone(object, Map.class, objectMapper);

            // Then - Should handle gracefully (return null rather than infinite loop)
            assertNull(cloned);
        }
    }
}