package com.higgstx.schwabapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.higgstx.schwabapi.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * STREAMLINED JsonUtils testing - removes duplicated validation/edge cases
 * Focuses only on JsonUtils-specific functionality
 */
@DisplayName("JsonUtils - Core Functionality")
class StreamlinedJsonUtilsTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = JsonUtils.createStandardObjectMapper();
    }

    @Nested
    @DisplayName("ObjectMapper Configuration")
    class ObjectMapperConfigurationTests {

        @Test
        @DisplayName("createStandardObjectMapper should create configured ObjectMapper")
        void createStandardObjectMapper_CreatesConfiguredMapper() {
            ObjectMapper mapper = JsonUtils.createStandardObjectMapper();

            assertNotNull(mapper);
            assertFalse(mapper.getDeserializationConfig().isEnabled(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        }

        @Test
        @DisplayName("createStandardObjectMapper should handle Java time module")
        void createStandardObjectMapper_HandlesJavaTimeModule() {
            ObjectMapper mapper = JsonUtils.createStandardObjectMapper();

            assertDoesNotThrow(() -> {
                String json = "{\"timestamp\": \"2024-01-15T10:30:00Z\", \"date\": \"2024-01-15\"}";
                mapper.readTree(json);
            });
        }
    }

    @Nested
    @DisplayName("JSON Parsing")
    class JsonParsingTests {

        @Test
        @DisplayName("parseJsonToMap should parse valid JSON with nested objects")
        void parseJsonToMap_ValidNestedJson_ParsesCorrectly() {
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

            Map<String, Object> result = JsonUtils.parseJsonToMap(json, objectMapper);

            assertEquals("AAPL", result.get("symbol"));
            assertInstanceOf(Map.class, result.get("quote"));
            assertInstanceOf(Map.class, result.get("metadata"));
            
            @SuppressWarnings("unchecked")
            Map<String, Object> quote = (Map<String, Object>) result.get("quote");
            assertEquals(150.25, quote.get("price"));
            assertEquals(2.5, quote.get("change"));
        }

        @Test
        @DisplayName("parseJsonToMap with default mapper should work")
        void parseJsonToMap_DefaultMapper_Works() {
            String json = "{\"test\": \"value\", \"number\": 42}";

            Map<String, Object> result = JsonUtils.parseJsonToMap(json);

            assertEquals("value", result.get("test"));
            assertEquals(42, result.get("number"));
        }
    }

    @Nested
    @DisplayName("Error Details Extraction")
    class ErrorDetailsExtractionTests {

        @Test
        @DisplayName("extractErrorDetails should extract comprehensive error information")
        void extractErrorDetails_ComprehensiveError_ExtractsAll() {
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

            Map<String, Object> details = JsonUtils.extractErrorDetails(errorJson, objectMapper);

            assertEquals("invalid_request", details.get("error"));
            assertEquals("The request is missing required parameters", details.get("description"));
            assertEquals("Bad request", details.get("message"));
            assertEquals("ERR_001", details.get("error_code"));
            assertEquals("2024-01-15T10:30:00Z", details.get("timestamp"));
            assertNotNull(details.get("errors"));
        }

        @Test
        @DisplayName("extractErrorDetails should handle partial error information")
        void extractErrorDetails_PartialError_ExtractsAvailable() {
            String errorJson = "{\"error\": \"server_error\", \"other_field\": \"ignored\"}";

            Map<String, Object> details = JsonUtils.extractErrorDetails(errorJson, objectMapper);

            assertEquals("server_error", details.get("error"));
            assertFalse(details.containsKey("description"));
            assertFalse(details.containsKey("other_field"));
        }
    }

    @Nested
    @DisplayName("Error Message Extraction")
    class ErrorMessageExtractionTests {

        @Test
        @DisplayName("extractErrorMessage should prioritize error_description")
        void extractErrorMessage_MultipleFields_PrioritizesErrorDescription() {
            String errorJson = "{" +
                    "\"error\": \"invalid_request\"," +
                    "\"error_description\": \"Detailed error description\"," +
                    "\"message\": \"Generic message\"" +
                    "}";

            String errorMessage = JsonUtils.extractErrorMessage(errorJson, objectMapper);

            assertEquals("Detailed error description", errorMessage);
        }

        @Test
        @DisplayName("extractErrorMessage should fall back through hierarchy")
        void extractErrorMessage_FallbackHierarchy_WorksCorrectly() {
            // Test fallback to message
            String errorJson1 = "{\"error\": \"invalid_request\", \"message\": \"Generic message\"}";
            assertEquals("Generic message", JsonUtils.extractErrorMessage(errorJson1, objectMapper));

            // Test fallback to error
            String errorJson2 = "{\"error\": \"invalid_request\"}";
            assertEquals("invalid_request", JsonUtils.extractErrorMessage(errorJson2, objectMapper));
        }
    }

    @Nested
    @DisplayName("JSON Serialization")
    class JsonSerializationTests {

        @Test
        @DisplayName("toJsonString should convert complex objects to JSON")
        void toJsonString_ComplexObject_ConvertsCorrectly() {
            Map<String, Object> complexObject = new HashMap<>();
            complexObject.put("name", "AAPL");
            complexObject.put("price", 150.25);
            complexObject.put("active", true);
            complexObject.put("metadata", Map.of("source", "schwab", "verified", true));

            String json = JsonUtils.toJsonString(complexObject, objectMapper);

            assertNotNull(json);
            assertTrue(json.contains("\"name\""));
            assertTrue(json.contains("\"AAPL\""));
            assertTrue(json.contains("\"price\""));
            assertTrue(json.contains("150.25"));
            assertTrue(json.contains("\"metadata\""));
        }

        @Test
        @DisplayName("toPrettyJsonString should format JSON with indentation")
        void toPrettyJsonString_ValidObject_FormatsPretty() {
            Map<String, Object> object = Map.of(
                "name", "AAPL",
                "price", 150.25,
                "nested", Map.of("key", "value")
            );

            String prettyJson = JsonUtils.toPrettyJsonString(object, objectMapper);

            assertNotNull(prettyJson);
            assertTrue(prettyJson.contains("\n"));
            assertTrue(prettyJson.contains("  "));
            int indentedLines = prettyJson.split("\n").length;
            assertTrue(indentedLines > 3); // Should have multiple lines due to formatting
        }
    }

    @Nested
    @DisplayName("JSON Node Value Extraction")
    class JsonNodeValueExtractionTests {

        @Test
        @DisplayName("Value extraction methods should handle all data types correctly")
        void valueExtraction_AllDataTypes_HandlesCorrectly() throws Exception {
            String json = "{" +
                    "\"stringField\": \"test value\"," +
                    "\"doubleField\": 123.45," +
                    "\"intField\": 42," +
                    "\"longField\": 9223372036854775807," +
                    "\"booleanTrue\": true," +
                    "\"booleanFalse\": false," +
                    "\"numberAsString\": \"999\"" +
                    "}";
            JsonNode node = objectMapper.readTree(json);

            // String extraction
            assertEquals("test value", JsonUtils.getStringValue(node, "stringField"));
            assertNull(JsonUtils.getStringValue(node, "doubleField")); // Not a string

            // Numeric extraction
            assertEquals(123.45, JsonUtils.getDoubleValue(node, "doubleField"));
            assertEquals(42.0, JsonUtils.getDoubleValue(node, "intField"));
            assertEquals(42L, JsonUtils.getLongValue(node, "intField"));
            assertEquals(9223372036854775807L, JsonUtils.getLongValue(node, "longField"));

            // Boolean extraction
            assertTrue(JsonUtils.getBooleanValue(node, "booleanTrue"));
            assertFalse(JsonUtils.getBooleanValue(node, "booleanFalse"));
            
            // Non-existent fields
            assertNull(JsonUtils.getStringValue(node, "nonexistent"));
            assertNull(JsonUtils.getDoubleValue(node, "nonexistent"));
            assertNull(JsonUtils.getLongValue(node, "nonexistent"));
            assertNull(JsonUtils.getBooleanValue(node, "nonexistent"));
        }
    }

    @Nested
    @DisplayName("JSON Object Manipulation")
    class JsonObjectManipulationTests {

        @Test
        @DisplayName("mergeJsonObjects should handle complex merging scenarios")
        void mergeJsonObjects_ComplexMerging_WorksCorrectly() {
            Map<String, Object> base = new HashMap<>();
            base.put("field1", "value1");
            base.put("field2", "value2");
            base.put("shared", "base_value");

            Map<String, Object> overlay = new HashMap<>();
            overlay.put("field2", "overridden");
            overlay.put("field3", "value3");
            overlay.put("shared", "overlay_value");

            Map<String, Object> result = JsonUtils.mergeJsonObjects(base, overlay);

            assertEquals("value1", result.get("field1")); // From base
            assertEquals("overridden", result.get("field2")); // Overlay wins
            assertEquals("value3", result.get("field3")); // From overlay
            assertEquals("overlay_value", result.get("shared")); // Overlay wins
            assertEquals(4, result.size());
        }

        @Test
        @DisplayName("deepClone should create completely independent copies")
        void deepClone_ComplexObject_CreatesIndependentCopy() {
            Map<String, Object> original = new HashMap<>();
            original.put("field1", "value1");
            original.put("field2", 42);
            original.put("nested", Map.of("inner", "innerValue"));

            @SuppressWarnings("unchecked")
            Map<String, Object> cloned = JsonUtils.deepClone(original, Map.class, objectMapper);

            assertNotNull(cloned);
            assertNotSame(original, cloned);
            assertEquals(original.get("field1"), cloned.get("field1"));
            assertEquals(original.get("field2"), cloned.get("field2"));

            // Verify independence by modifying original
            original.put("field3", "new value");
            assertFalse(cloned.containsKey("field3"));
        }
    }

    @Nested
    @DisplayName("JSON Validation")
    class JsonValidationTests {

        @Test
        @DisplayName("isValidJson should correctly identify valid JSON structures")
        void isValidJson_VariousValidStructures_IdentifiesCorrectly() {
            String[] validJsons = {
                "{\"valid\": \"json\"}",
                "[]",
                "{\"nested\": {\"object\": true}}",
                "[{\"array\": \"of\"}, {\"objects\": true}]",
                "\"simple string\"",
                "42",
                "true",
                "null"
            };

            for (String validJson : validJsons) {
                assertTrue(JsonUtils.isValidJson(validJson, objectMapper), 
                    "Should be valid: " + validJson);
            }
        }

        @Test
        @DisplayName("isValidJson should correctly reject invalid JSON")
        void isValidJson_InvalidJson_RejectsCorrectly() {
            String[] invalidJsons = {
                "{ invalid json }",
                "not json at all",
                "{",
                "}",
                "[",
                "]",
                "{\"unclosed\": \"string",
                "{\"trailing\": \"comma\",}"
            };

            for (String invalidJson : invalidJsons) {
                assertFalse(JsonUtils.isValidJson(invalidJson, objectMapper),
                    "Should be invalid: " + invalidJson);
            }
        }
    }
}