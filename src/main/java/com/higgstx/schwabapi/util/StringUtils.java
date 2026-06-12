package com.higgstx.schwabapi.util;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * String utility functions for Schwab API operations
 */
public final class StringUtils {
    
    private StringUtils() {
        // Utility class - prevent instantiation
    }
    
    public static String lTrim(String input) {
        if (input == null) return null;
        return input.replaceFirst("^\\s+", "");
    }
    
    /**
     * Validates that a string is not null or empty/whitespace
     */
    public static String validateRequired(String value, String propertyName) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(propertyName + " cannot be null or empty");
        }
        return value.trim();
    }
    
    /**
     * Safely URL encode a string
     */
    public static String urlEncode(String value) {
        if (value == null) {
            return null;
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
    
    /**
     * Safely URL decode a string
     */
    public static String urlDecode(String value) {
        if (value == null) {
            return null;
        }
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
    
    /**
     * Extract authorization code from redirect URL
     */
    public static String extractAuthorizationCode(String redirectUrl) {
        if (redirectUrl == null || redirectUrl.isEmpty()) {
            throw new IllegalArgumentException("Redirect URL cannot be null or empty");
        }

        try {
            String[] parts = redirectUrl.split("[?&]");
            for (String part : parts) {
                if (part.startsWith("code=")) {
                    String code = part.substring(5); // Remove "code=" prefix
                    return urlDecode(code);
                }
            }
            throw new IllegalArgumentException("No authorization code found in URL: " + redirectUrl);
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract authorization code from URL", e);
        }
    }
    
    /**
     * Clean and normalize a stock symbol
     */
    public static String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return null;
        }
        return symbol.trim().toUpperCase();
    }

    /**
     * Validate and normalize an OSI option symbol.
     *
     * OSI format (21 characters):
     *   [0-5]  6-char underlying root, left-justified, space-padded  e.g. "CAT   " or "MSFT  "
     *   [6-11] 6-digit expiry YYMMDD                                 e.g. "270617"
     *   [12]   option type C or P                                     e.g. "C"
     *   [13-20] 8-digit strike price (strike * 1000, zero-padded)    e.g. "00900000"
     *
     * Examples: "CAT   270617C00900000", "MSFT  270617C00550000"
     *
     * @return the uppercased OSI symbol if valid
     * @throws IllegalArgumentException if the symbol does not conform to OSI format
     */
    public static String validateOsiSymbol(String symbol) {
        if (symbol == null) {
            throw new IllegalArgumentException("OSI symbol cannot be null");
        }
        String upper = symbol.toUpperCase();
        if (upper.length() != 21) {
            throw new IllegalArgumentException(
                "OSI symbol must be exactly 21 characters, got " + upper.length()
                + " for: '" + symbol + "'");
        }
        // Chars 6-11: expiry YYMMDD — must be 6 digits
        String expiry = upper.substring(6, 12);
        if (!expiry.matches("\\d{6}")) {
            throw new IllegalArgumentException(
                "OSI symbol expiry (positions 6-11) must be 6 digits (YYMMDD), got: '" + expiry + "'");
        }
        // Char 12: option type — must be C or P
        char optionType = upper.charAt(12);
        if (optionType != 'C' && optionType != 'P') {
            throw new IllegalArgumentException(
                "OSI symbol type (position 12) must be 'C' or 'P', got: '" + optionType + "'");
        }
        // Chars 13-20: strike — must be 8 digits
        String strike = upper.substring(13, 21);
        if (!strike.matches("\\d{8}")) {
            throw new IllegalArgumentException(
                "OSI symbol strike (positions 13-20) must be 8 digits, got: '" + strike + "'");
        }
        return upper;
    }
    
    /**
     * Check if a string is null, empty, or whitespace only
     */
    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
    
    /**
     * Check if a string has actual content (not null, empty, or whitespace)
     */
    public static boolean hasContent(String str) {
        return !isBlank(str);
    }
}