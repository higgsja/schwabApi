package com.higgstx.schwabapi.service;

import com.higgstx.schwabapi.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Essential StringUtils functionality tests
 */
@DisplayName("StringUtils Core Tests")
class SimplifiedStringUtilsTest {

    @Test
    @DisplayName("URL encoding should handle special characters")
    void testUrlEncoding() {
        String input = "hello world & special!";
        String encoded = StringUtils.urlEncode(input);
        String decoded = StringUtils.urlDecode(encoded);
        
        assertNotNull(encoded);
        assertEquals(input, decoded);
        assertTrue(encoded.contains("+") || encoded.contains("%20")); // Space encoded
        assertTrue(encoded.contains("%26")); // & encoded
    }

    @Test
    @DisplayName("URL encoding should be reversible for various inputs")
    void testUrlEncodingReversibility() {
        String[] testCases = {
            "simple text",
            "special!@#$%chars",
            "unicode: café",
            "mixed: hello & world"
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
        // Standard format
        String url1 = "https://localhost:8182/?code=AUTH_CODE_123&state=random";
        assertEquals("AUTH_CODE_123", StringUtils.extractAuthorizationCode(url1));
        
        // Code in middle of parameters
        String url2 = "https://localhost:8182/?state=random&code=MIDDLE_CODE&other=param";
        assertEquals("MIDDLE_CODE", StringUtils.extractAuthorizationCode(url2));
        
        // Encoded code
        String url3 = "https://localhost:8182/?code=AUTH%2BCODE%21123&state=random";
        assertEquals("AUTH+CODE!123", StringUtils.extractAuthorizationCode(url3));
    }

    @Test
    @DisplayName("Authorization code extraction should handle error cases")
    void testAuthCodeExtractionErrors() {
        // No code parameter
        String urlWithoutCode = "https://localhost:8182/?state=random&other=param";
        assertThrows(RuntimeException.class, () -> StringUtils.extractAuthorizationCode(urlWithoutCode));
        
        // Null/empty URL
        assertThrows(RuntimeException.class, () -> StringUtils.extractAuthorizationCode(null));
        assertThrows(RuntimeException.class, () -> StringUtils.extractAuthorizationCode(""));
        
        // Empty code value should not throw - returns empty string
        String urlWithEmptyCode = "https://localhost:8182/?code=&state=random";
        assertEquals("", StringUtils.extractAuthorizationCode(urlWithEmptyCode));
    }

    @Test
    @DisplayName("Symbol normalization should handle various formats")
    void testSymbolNormalization() {
        // Basic normalization
        assertEquals("AAPL", StringUtils.normalizeSymbol("aapl"));
        assertEquals("AAPL", StringUtils.normalizeSymbol("AAPL"));
        assertEquals("AAPL", StringUtils.normalizeSymbol("  aapl  "));
        
        // Mixed case
        assertEquals("MSFT", StringUtils.normalizeSymbol("Msft"));
        assertEquals("GOOGL", StringUtils.normalizeSymbol("gOoGl"));
        
        // Complex symbols
        assertEquals("BRK.A", StringUtils.normalizeSymbol("brk.a"));
        assertEquals("BRK.B", StringUtils.normalizeSymbol("brk.b"));
        
        // Edge cases return null
        assertNull(StringUtils.normalizeSymbol(null));
        assertNull(StringUtils.normalizeSymbol(""));
        assertNull(StringUtils.normalizeSymbol("   "));
    }

    @Test
    @DisplayName("Content checking methods should be complementary")
    void testContentMethods() {
        String[] testCases = {null, "", "   ", "\t\n", "content", " content "};
        
        for (String test : testCases) {
            boolean hasContent = StringUtils.hasContent(test);
            boolean isBlank = StringUtils.isBlank(test);
            
            // These should always be opposite
            assertEquals(hasContent, !isBlank, 
                "hasContent and isBlank should be opposite for: '" + test + "'");
        }
    }

    @Test
    @DisplayName("String validation should provide meaningful error messages")
    void testValidationErrorMessages() {
        Exception nullException = assertThrows(IllegalArgumentException.class,
            () -> StringUtils.validateRequired(null, "testField"));
        assertTrue(nullException.getMessage().contains("testField"));
        
        Exception emptyException = assertThrows(IllegalArgumentException.class,
            () -> StringUtils.validateRequired("   ", "anotherField"));
        assertTrue(emptyException.getMessage().contains("anotherField"));
        
        // Valid input should not throw
        assertDoesNotThrow(() -> StringUtils.validateRequired("valid", "test"));
        assertEquals("valid", StringUtils.validateRequired("  valid  ", "test"));
    }

    @Test
    @DisplayName("Null safety should be consistent across all methods")
    void testNullSafety() {
        assertNull(StringUtils.urlEncode(null));
        assertNull(StringUtils.urlDecode(null));
        assertNull(StringUtils.normalizeSymbol(null));
        assertTrue(StringUtils.isBlank(null));
        assertFalse(StringUtils.hasContent(null));
        
        // Validation methods should throw for null
        assertThrows(IllegalArgumentException.class,
            () -> StringUtils.validateRequired(null, "test"));
    }

    @Test
    @DisplayName("Empty string handling should be consistent")
    void testEmptyStringHandling() {
        String[] empties = {"", "   ", "\t", "\n"};
        
        for (String empty : empties) {
            assertTrue(StringUtils.isBlank(empty));
            assertFalse(StringUtils.hasContent(empty));
            assertNull(StringUtils.normalizeSymbol(empty));
            
            // URL encoding empty should work
            String encoded = StringUtils.urlEncode(empty);
            assertTrue(encoded != null);
        }
    }

    @Test
    @DisplayName("Special characters in symbols should be preserved")
    void testSpecialCharactersInSymbols() {
        // Symbols with dots, dashes should be preserved
        assertEquals("BRK.B", StringUtils.normalizeSymbol("brk.b"));
        assertEquals("T-MOBILE", StringUtils.normalizeSymbol("t-mobile"));
        
        // Should preserve valid symbol characters while uppercasing
        String complexSymbol = "ABC.PRD-123";
        assertEquals(complexSymbol.toUpperCase(), StringUtils.normalizeSymbol(complexSymbol.toLowerCase()));
    }
}