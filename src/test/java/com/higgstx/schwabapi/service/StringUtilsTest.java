package com.higgstx.schwabapi.service;

import com.higgstx.schwabapi.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for StringUtils utility class
 */
class StringUtilsTest {

    @Nested
    @DisplayName("Validation Methods")
    class ValidationTests {

        @Test
        @DisplayName("validateRequired should return trimmed string for valid input")
        void validateRequired_ValidInput_ReturnsTrimmedString() {
            // Given
            String input = "  valid-value  ";
            String propertyName = "testProperty";

            // When
            String result = StringUtils.validateRequired(input, propertyName);

            // Then
            assertEquals("valid-value", result);
        }

        @Test
        @DisplayName("validateRequired should throw exception for null input")
        void validateRequired_NullInput_ThrowsException() {
            // Given
            String input = null;
            String propertyName = "testProperty";

            // When & Then
            RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> StringUtils.validateRequired(input, propertyName));
            
            assertTrue(exception.getMessage().contains("testProperty"));
            assertTrue(exception.getMessage().contains("Required property missing"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "\t", "\n", "\r\n"})
        @DisplayName("validateRequired should throw exception for empty/whitespace input")
        void validateRequired_EmptyOrWhitespace_ThrowsException(String input) {
            // Given
            String propertyName = "testProperty";

            // When & Then
            RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> StringUtils.validateRequired(input, propertyName));
            
            assertTrue(exception.getMessage().contains("testProperty"));
        }

        @Test
        @DisplayName("validateUrl should return trimmed URL for valid input")
        void validateUrl_ValidInput_ReturnsTrimmedUrl() {
            // Given
            String url = "  https://api.example.com  ";
            String name = "API URL";

            // When
            String result = StringUtils.validateUrl(url, name);

            // Then
            assertEquals("https://api.example.com", result);
        }

        @Test
        @DisplayName("validateUrl should throw exception for null input")
        void validateUrl_NullInput_ThrowsException() {
            // Given
            String url = null;
            String name = "API URL";

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                () -> StringUtils.validateUrl(url, name));
            
            assertTrue(exception.getMessage().contains("API URL"));
        }
    }

    @Nested
    @DisplayName("URL Encoding/Decoding")
    class UrlEncodingTests {

        @Test
        @DisplayName("urlEncode should encode special characters")
        void urlEncode_SpecialCharacters_EncodesCorrectly() {
            // Given
            String input = "hello world & special chars!";

            // When
            String result = StringUtils.urlEncode(input);

            // Then
            assertEquals("hello+world+%26+special+chars%21", result);
        }

        @Test
        @DisplayName("urlEncode should handle null input")
        void urlEncode_NullInput_ReturnsNull() {
            // When
            String result = StringUtils.urlEncode(null);

            // Then
            assertNull(result);
        }

        @Test
        @DisplayName("urlDecode should decode encoded characters")
        void urlDecode_EncodedCharacters_DecodesCorrectly() {
            // Given
            String input = "hello+world+%26+special+chars%21";

            // When
            String result = StringUtils.urlDecode(input);

            // Then
            assertEquals("hello world & special chars!", result);
        }

        @Test
        @DisplayName("urlDecode should handle null input")
        void urlDecode_NullInput_ReturnsNull() {
            // When
            String result = StringUtils.urlDecode(null);

            // Then
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("Authorization Code Extraction")
    class AuthCodeExtractionTests {

        @Test
        @DisplayName("extractAuthorizationCode should extract code from valid URL")
        void extractAuthorizationCode_ValidUrl_ExtractsCode() {
            // Given
            String redirectUrl = "https://localhost:8182/?code=AUTH_CODE_123&state=random";

            // When
            String result = StringUtils.extractAuthorizationCode(redirectUrl);

            // Then
            assertEquals("AUTH_CODE_123", result);
        }

        @Test
        @DisplayName("extractAuthorizationCode should extract and decode encoded code")
        void extractAuthorizationCode_EncodedCode_ExtractsAndDecodes() {
            // Given
            String redirectUrl = "https://localhost:8182/?code=AUTH%2BCODE%21123&state=random";

            // When
            String result = StringUtils.extractAuthorizationCode(redirectUrl);

            // Then
            assertEquals("AUTH+CODE!123", result);
        }

        @Test
        @DisplayName("extractAuthorizationCode should handle code parameter in middle")
        void extractAuthorizationCode_CodeInMiddle_ExtractsCode() {
            // Given
            String redirectUrl = "https://localhost:8182/?state=random&code=MIDDLE_CODE&other=param";

            // When
            String result = StringUtils.extractAuthorizationCode(redirectUrl);

            // Then
            assertEquals("MIDDLE_CODE", result);
        }

        @Test
        @DisplayName("extractAuthorizationCode should throw exception for null URL")
        void extractAuthorizationCode_NullUrl_ThrowsException() {
            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> StringUtils.extractAuthorizationCode(null));
            
            assertTrue(exception.getMessage().contains("cannot be null"));
        }

        @Test
        @DisplayName("extractAuthorizationCode should throw exception for empty URL")
        void extractAuthorizationCode_EmptyUrl_ThrowsException() {
            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> StringUtils.extractAuthorizationCode(""));
            
            assertTrue(exception.getMessage().contains("cannot be null or empty"));
        }

        @Test
        @DisplayName("extractAuthorizationCode should throw exception when code not found")
        void extractAuthorizationCode_NoCode_ThrowsException() {
            // Given
            String redirectUrl = "https://localhost:8182/?state=random&other=param";

            // When & Then
            RuntimeException exception = assertThrows(RuntimeException.class,
                () -> StringUtils.extractAuthorizationCode(redirectUrl));
            
            assertTrue(exception.getMessage().contains("Failed to extract authorization code"));
        }
    }

    

    @Nested
    @DisplayName("Symbol Normalization")
    class SymbolNormalizationTests {

        @Test
        @DisplayName("normalizeSymbol should convert to uppercase and trim")
        void normalizeSymbol_ValidSymbol_ConvertsToUppercase() {
            // Given
            String symbol = "  aapl  ";

            // When
            String result = StringUtils.normalizeSymbol(symbol);

            // Then
            assertEquals("AAPL", result);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "\t", "\n"})
        @DisplayName("normalizeSymbol should return null for empty/whitespace")
        void normalizeSymbol_EmptyOrWhitespace_ReturnsNull(String symbol) {
            // When
            String result = StringUtils.normalizeSymbol(symbol);

            // Then
            assertNull(result);
        }

        @Test
        @DisplayName("normalizeSymbol should return null for null input")
        void normalizeSymbol_NullInput_ReturnsNull() {
            // When
            String result = StringUtils.normalizeSymbol(null);

            // Then
            assertNull(result);
        }

        @Test
        @DisplayName("normalizeSymbol should handle mixed case symbols")
        void normalizeSymbol_MixedCase_ConvertsToUppercase() {
            // Given
            String symbol = "Msft";

            // When
            String result = StringUtils.normalizeSymbol(symbol);

            // Then
            assertEquals("MSFT", result);
        }
    }

    @Nested
    @DisplayName("String Utility Methods")
    class StringUtilityTests {

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "\t", "\n", "\r\n", "  \t\n  "})
        @DisplayName("isBlank should return true for null, empty, or whitespace strings")
        void isBlank_EmptyOrWhitespace_ReturnsTrue(String input) {
            // When & Then
            assertTrue(StringUtils.isBlank(input));
        }

        @Test
        @DisplayName("isBlank should return true for null")
        void isBlank_Null_ReturnsTrue() {
            // When & Then
            assertTrue(StringUtils.isBlank(null));
        }

        @Test
        @DisplayName("isBlank should return false for non-empty strings")
        void isBlank_NonEmpty_ReturnsFalse() {
            // When & Then
            assertFalse(StringUtils.isBlank("hello"));
            assertFalse(StringUtils.isBlank(" hello "));
            assertFalse(StringUtils.isBlank("a"));
        }

        @Test
        @DisplayName("hasContent should return true for non-empty strings")
        void hasContent_NonEmpty_ReturnsTrue() {
            // When & Then
            assertTrue(StringUtils.hasContent("hello"));
            assertTrue(StringUtils.hasContent(" hello "));
            assertTrue(StringUtils.hasContent("a"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "\t", "\n"})
        @DisplayName("hasContent should return false for empty/whitespace strings")
        void hasContent_EmptyOrWhitespace_ReturnsFalse(String input) {
            // When & Then
            assertFalse(StringUtils.hasContent(input));
        }

        @Test
        @DisplayName("hasContent should return false for null")
        void hasContent_Null_ReturnsFalse() {
            // When & Then
            assertFalse(StringUtils.hasContent(null));
        }

        @Test
        @DisplayName("lTrim should remove leading whitespace")
        void lTrim_LeadingWhitespace_RemovesLeading() {
            // Given
            String input = "   hello world   ";

            // When
            String result = StringUtils.lTrim(input);

            // Then
            assertEquals("hello world   ", result);
        }

        @Test
        @DisplayName("lTrim should handle strings with no leading whitespace")
        void lTrim_NoLeadingWhitespace_ReturnsOriginal() {
            // Given
            String input = "hello world   ";

            // When
            String result = StringUtils.lTrim(input);

            // Then
            assertEquals("hello world   ", result);
        }

        @Test
        @DisplayName("lTrim should handle empty string")
        void lTrim_EmptyString_ReturnsEmpty() {
            // When
            String result = StringUtils.lTrim("");

            // Then
            assertEquals("", result);
        }
    }
}