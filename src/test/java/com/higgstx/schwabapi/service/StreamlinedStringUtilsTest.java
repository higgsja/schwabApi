package com.higgstx.schwabapi.service;

import com.higgstx.schwabapi.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * STREAMLINED StringUtils testing - removes duplicated validation/edge cases
 * Focuses only on StringUtils-specific functionality not covered in consolidated tests
 */
@DisplayName("StringUtils - Specific Functionality")
class StreamlinedStringUtilsTest {

    @Nested
    @DisplayName("URL Operations")
    class UrlOperationsTest {
        
        @Test
        @DisplayName("URL encoding should handle complex characters correctly")
        void urlEncode_ComplexCharacters_HandlesCorrectly() {
            String complex = "hello world & special chars! @#$%^&*()";
            String encoded = StringUtils.urlEncode(complex);
            String decoded = StringUtils.urlDecode(encoded);
            
            assertNotNull(encoded);
            assertEquals(complex, decoded);
            assertTrue(encoded.contains("+") || encoded.contains("%20")); // Space should be encoded
            assertTrue(encoded.contains("%26")); // & should be encoded
        }

        @Test
        @DisplayName("URL encoding should be reversible")
        void urlEncoding_Reversibility_WorksCorrectly() {
            String[] testStrings = {
                "simple text",
                "text with spaces",
                "special!@#$%^&*()chars",
                "unicode: 世界 🌍",
                "mixed: hello world & café"
            };

            for (String original : testStrings) {
                String encoded = StringUtils.urlEncode(original);
                String decoded = StringUtils.urlDecode(encoded);
                assertEquals(original, decoded, "Encoding should be reversible for: " + original);
            }
        }
    }

    @Nested
    @DisplayName("Authorization Code Extraction")
    class AuthCodeExtractionTest {
        
        @Test
        @DisplayName("extractAuthorizationCode should handle various URL formats")
        void extractAuthorizationCode_VariousFormats_ExtractsCorrectly() {
            // Standard format
            String url1 = "https://localhost:8182/?code=AUTH_CODE_123&state=random";
            assertEquals("AUTH_CODE_123", StringUtils.extractAuthorizationCode(url1));
            
            // Code in middle of parameters
            String url2 = "https://localhost:8182/?state=random&code=MIDDLE_CODE&other=param";
            assertEquals("MIDDLE_CODE", StringUtils.extractAuthorizationCode(url2));
            
            // Encoded code
            String url3 = "https://localhost:8182/?code=AUTH%2BCODE%21123&state=random";
            assertEquals("AUTH+CODE!123", StringUtils.extractAuthorizationCode(url3));
            
            // Code with complex characters
            String url4 = "https://localhost:8182/?code=complex%2Fcode%3D123%26test&state=xyz";
            assertEquals("complex/code=123&test", StringUtils.extractAuthorizationCode(url4));
        }

        @Test
        @DisplayName("extractAuthorizationCode should handle error cases appropriately")
        void extractAuthorizationCode_ErrorCases_HandlesAppropriately() {
            // URL without code parameter - throws exception
            String urlWithoutCode = "https://localhost:8182/?state=random&other=param";
            assertThrows(RuntimeException.class, () -> StringUtils.extractAuthorizationCode(urlWithoutCode));
            
            // Empty code value - returns empty string (no exception)
            String urlWithEmptyCode = "https://localhost:8182/?code=&state=random";
            String result = StringUtils.extractAuthorizationCode(urlWithEmptyCode);
            assertEquals("", result);
        }
    }

    @Nested
    @DisplayName("Symbol Normalization")
    class SymbolNormalizationTest {
        
        @Test
        @DisplayName("normalizeSymbol should handle various symbol formats")
        void normalizeSymbol_VariousFormats_NormalizesCorrectly() {
            // Standard cases
            assertEquals("AAPL", StringUtils.normalizeSymbol("aapl"));
            assertEquals("AAPL", StringUtils.normalizeSymbol("AAPL"));
            assertEquals("AAPL", StringUtils.normalizeSymbol("  aapl  "));
            
            // Mixed case
            assertEquals("MSFT", StringUtils.normalizeSymbol("Msft"));
            assertEquals("GOOGL", StringUtils.normalizeSymbol("gOoGl"));
            
            // Complex symbols
            assertEquals("BRK.A", StringUtils.normalizeSymbol("brk.a"));
            assertEquals("SPY", StringUtils.normalizeSymbol(" spy "));
        }

        @Test
        @DisplayName("normalizeSymbol should handle special characters")
        void normalizeSymbol_SpecialCharacters_HandlesCorrectly() {
            // Symbols with dots, dashes, etc.
            assertEquals("BRK.B", StringUtils.normalizeSymbol("brk.b"));
            assertEquals("T-MOBILE", StringUtils.normalizeSymbol("t-mobile"));
            
            // Should preserve valid symbol characters
            String complexSymbol = "ABC.PRD";
            assertEquals(complexSymbol.toUpperCase(), StringUtils.normalizeSymbol(complexSymbol.toLowerCase()));
        }
    }

    @Nested
    @DisplayName("String Utility Methods")
    class StringUtilityMethodsTest {
        
        @Test
        @DisplayName("lTrim should remove only leading whitespace")
        void lTrim_LeadingWhitespace_RemovesOnlyLeading() {
            assertEquals("hello world   ", StringUtils.lTrim("   hello world   "));
            assertEquals("hello world", StringUtils.lTrim("hello world"));
            assertEquals("", StringUtils.lTrim("   "));
            assertEquals("text", StringUtils.lTrim("\t\ntext"));
        }

        @Test
        @DisplayName("hasContent vs isBlank should be complementary")
        void hasContentVsIsBlank_Complementary_WorksCorrectly() {
            String[] testStrings = {
                null,
                "",
                "   ",
                "\t\n",
                "actual content",
                " content with spaces ",
                "a"
            };

            for (String testString : testStrings) {
                boolean hasContent = StringUtils.hasContent(testString);
                boolean isBlank = StringUtils.isBlank(testString);
                
                // These should always be opposite
                assertEquals(hasContent, !isBlank, 
                    "hasContent and isBlank should be complementary for: '" + testString + "'");
            }
        }

        @Test
        @DisplayName("String validation should provide meaningful error messages")
        void validateRequired_ErrorMessages_AreMeaningful() {
            try {
                StringUtils.validateRequired(null, "testField");
                fail("Should have thrown exception");
            } catch (Exception e) {
                assertTrue(e.getMessage().contains("testField"));
                assertTrue(e.getMessage().toLowerCase().contains("cannot be null") || 
                          e.getMessage().toLowerCase().contains("empty"));
            }

            try {
                StringUtils.validateRequired("   ", "anotherField");
                fail("Should have thrown exception");
            } catch (Exception e) {
                assertTrue(e.getMessage().contains("anotherField"));
            }
        }
    }

    @Nested
    @DisplayName("Integration with Other Utils")
    class IntegrationTest {
        
        @Test
        @DisplayName("String operations should work well with conversion utils")
        void stringOperations_WithConversionUtils_WorkWell() {
            // Test string to number conversions after normalization
            String numberString = "  123.45  ";
            String trimmed = StringUtils.validateRequired(numberString, "number");
            Double converted = ConversionUtils.convertToDouble(trimmed);
            
            assertEquals(123.45, converted);
        }

        @Test
        @DisplayName("Symbol normalization should work with file operations")
        void symbolNormalization_WithFileOperations_WorksWell() {
            String symbol = "  aapl  ";
            String normalized = StringUtils.normalizeSymbol(symbol);
            
            // Should be safe for file names
            assertNotNull(normalized);
            assertFalse(normalized.contains(" "));
            assertTrue(normalized.matches("[A-Z0-9.-]+"));
        }
    }
}