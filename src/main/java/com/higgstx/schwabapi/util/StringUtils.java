package com.higgstx.schwabapi.util;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * String utility functions extracted from various classes in the API
 */
public final class StringUtils {
    
    private StringUtils() {
        // Utility class - prevent instantiation
    }
    
    public static String lTrim(String source)
    {
    return source.replaceAll("^\\s+", "");
    }
    
    /**
     * Validates that a string is not null or empty/whitespace
     * @param value The string to validate
     * @param propertyName The property name for error messages
     * @return The trimmed string
     * @throws RuntimeException if the string is null or empty
     */
    public static String validateRequired(String value, String propertyName) {
        if (value == null || value.trim().isEmpty()) {
            throw new RuntimeException("Required property missing or empty: " + propertyName);
        }
        return value.trim();
    }
    
    /**
     * Validates a URL string
     * @param url The URL to validate
     * @param name The name for error messages
     * @return The trimmed URL
     * @throws IllegalArgumentException if the URL is null or empty
     */
    public static String validateUrl(String url, String name) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be null or empty");
        }
        return url.trim();
    }
    
    /**
     * Safely URL encode a string
     * @param value The string to encode
     * @return The URL encoded string
     */
    public static String urlEncode(String value) {
        if (value == null) {
            return null;
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
    
    /**
     * Safely URL decode a string
     * @param value The string to decode
     * @return The URL decoded string
     */
    public static String urlDecode(String value) {
        if (value == null) {
            return null;
        }
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
    
    /**
     * Extract authorization code from redirect URL
     * @param redirectUrl The redirect URL containing the code parameter
     * @return The extracted and decoded authorization code
     * @throws IllegalArgumentException if URL is invalid or code not found
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
     * Extract error information from OAuth callback URL
     * @param redirectUrl The redirect URL containing error parameters
     * @return A formatted error message
     */
    public static String extractErrorInfo(String redirectUrl) {
        try {
            if (redirectUrl == null || !redirectUrl.contains("?")) {
                return "Unknown error";
            }

            String query = redirectUrl.substring(redirectUrl.indexOf("?") + 1);
            String[] params = query.split("&");
            StringBuilder errorInfo = new StringBuilder();

            for (String param : params) {
                String[] pair = param.split("=", 2);
                if (pair.length == 2 && (pair[0].equals("error") || pair[0].equals("error_description"))) {
                    if (errorInfo.length() > 0) {
                        errorInfo.append(", ");
                    }
                    errorInfo.append(pair[0]).append("=").append(urlDecode(pair[1]));
                }
            }

            return errorInfo.length() > 0 ? errorInfo.toString() : "Unknown error";

        } catch (Exception e) {
            return "Error parsing error information";
        }
    }
    
    /**
     * Format duration in seconds to a human-readable string
     * @param seconds The duration in seconds
     * @return Formatted duration string
     */
    public static String formatDuration(long seconds) {
        if (seconds < 0) {
            return "Expired";
        } else if (seconds < 60) {
            return seconds + " seconds";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            return minutes + " minute" + (minutes != 1 ? "s" : "");
        } else if (seconds < 86400) {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return hours + "h " + minutes + "m";
        } else {
            long days = seconds / 86400;
            long hours = (seconds % 86400) / 3600;
            return days + "d " + hours + "h";
        }
    }
    
    /**
     * Clean and normalize a stock symbol
     * @param symbol The raw symbol
     * @return The cleaned, uppercase symbol, or null if invalid
     */
    public static String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return null;
        }
        return symbol.trim().toUpperCase();
    }
    
    /**
     * Check if a string is null, empty, or whitespace only
     * @param str The string to check
     * @return true if the string is null, empty, or whitespace only
     */
    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
    
    /**
     * Check if a string has actual content (not null, empty, or whitespace)
     * @param str The string to check
     * @return true if the string has content
     */
    public static boolean hasContent(String str) {
        return !isBlank(str);
    }
}