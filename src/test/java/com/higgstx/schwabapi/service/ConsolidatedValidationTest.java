package com.higgstx.schwabapi.service;

import com.higgstx.schwabapi.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Essential validation testing for utility classes
 */
@DisplayName("Core Validation Tests")
class SimplifiedValidationTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    @DisplayName("String validation should handle blank inputs consistently")
    void testBlankStringHandling(String input) {
        // StringUtils validation
        assertTrue(StringUtils.isBlank(input));
        assertFalse(StringUtils.hasContent(input));
        assertNull(StringUtils.normalizeSymbol(input));
        
        // UtilityClass validation should reject blank strings
        assertThrows(IllegalArgumentException.class, 
            () -> UtilityClass.validateParameter(input, "test"));
    }

    @Test
    @DisplayName("Valid strings should pass validation")
    void testValidStringHandling() {
        String validInput = "  AAPL  ";
        
        assertFalse(StringUtils.isBlank(validInput));
        assertTrue(StringUtils.hasContent(validInput));
        assertEquals("AAPL", StringUtils.normalizeSymbol(validInput));
        assertEquals("AAPL", StringUtils.validateRequired(validInput, "symbol"));
    }

    @Test
    @DisplayName("URL encoding should be reversible")
    void testUrlEncodingReversibility() {
        String[] testCases = {
            "hello world",
            "special!@#$%chars", 
            "unicode: café"
        };

        for (String original : testCases) {
            String encoded = StringUtils.urlEncode(original);
            String decoded = StringUtils.urlDecode(encoded);
            assertEquals(original, decoded);
        }
    }

    @Test
    @DisplayName("Authorization code extraction should work for valid URLs")
    void testAuthCodeExtraction() {
        String url = "https://localhost:8182/?code=AUTH_CODE_123&state=random";
        assertEquals("AUTH_CODE_123", StringUtils.extractAuthorizationCode(url));
        
        String urlWithEncoding = "https://localhost:8182/?code=AUTH%2BCODE%21123";
        assertEquals("AUTH+CODE!123", StringUtils.extractAuthorizationCode(urlWithEncoding));
    }

    @Test
    @DisplayName("Authorization code extraction should reject invalid URLs")
    void testAuthCodeExtractionErrors() {
        assertThrows(RuntimeException.class, 
            () -> StringUtils.extractAuthorizationCode(null));
        assertThrows(RuntimeException.class, 
            () -> StringUtils.extractAuthorizationCode("https://localhost:8182/?state=random"));
    }

    @Test
    @DisplayName("Numeric conversions should handle valid inputs")
    void testNumericConversions() {
        assertEquals(42.0, ConversionUtils.convertToDouble(42));
        assertEquals(42L, ConversionUtils.convertToLong("42"));
        assertEquals(42.5, ConversionUtils.convertToDouble("42.5"));
        
        // Invalid conversions should return null
        assertNull(ConversionUtils.convertToDouble("not_a_number"));
        assertNull(ConversionUtils.convertToLong("42.5")); // Can't parse decimal as Long
    }

    @Test
    @DisplayName("Boolean conversions should handle standard cases")
    void testBooleanConversions() {
        assertTrue(ConversionUtils.convertToBoolean(true));
        assertTrue(ConversionUtils.convertToBoolean("true"));
        assertTrue(ConversionUtils.convertToBoolean(1));
        
        assertFalse(ConversionUtils.convertToBoolean(false));
        assertFalse(ConversionUtils.convertToBoolean("false"));
        assertFalse(ConversionUtils.convertToBoolean(0));
        
        assertNull(ConversionUtils.convertToBoolean("invalid"));
    }

    @Test
    @DisplayName("All utilities should handle null inputs safely")
    void testNullSafety() {
        // String utilities
        assertNull(StringUtils.urlEncode(null));
        assertNull(StringUtils.normalizeSymbol(null));
        assertTrue(StringUtils.isBlank(null));
        
        // Conversion utilities
        assertNull(ConversionUtils.convertToDouble(null));
        assertNull(ConversionUtils.convertToLong(null));
        assertNull(ConversionUtils.convertToBoolean(null));
        
        // Validation should throw for null
        assertThrows(IllegalArgumentException.class,
            () -> UtilityClass.validateNotNull(null, "test"));
    }

    @Test
    @DisplayName("Symbol normalization should handle various formats")
    void testSymbolNormalization() {
        assertEquals("AAPL", StringUtils.normalizeSymbol("aapl"));
        assertEquals("AAPL", StringUtils.normalizeSymbol("  AAPL  "));
        assertEquals("BRK.A", StringUtils.normalizeSymbol("brk.a"));
        
        // Edge cases
        assertNull(StringUtils.normalizeSymbol(null));
        assertNull(StringUtils.normalizeSymbol(""));
        assertNull(StringUtils.normalizeSymbol("   "));
    }

    @Test
    @DisplayName("hasContent and isBlank should be complementary")
    void testStringContentMethods() {
        String[] testCases = {null, "", "   ", "content", " content "};
        
        for (String test : testCases) {
            boolean hasContent = StringUtils.hasContent(test);
            boolean isBlank = StringUtils.isBlank(test);
            assertEquals(hasContent, !isBlank, 
                "hasContent and isBlank should be opposite for: '" + test + "'");
        }
    }
}