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