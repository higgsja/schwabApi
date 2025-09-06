package com.higgstx.schwabapi.util;

import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * HTTP utility functions extracted from various client classes
 */
public final class HttpUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(HttpUtils.class);
    
    private HttpUtils() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Build a standard OkHttpClient with timeout and optional logging
     * @param timeoutMs The timeout in milliseconds
     * @param enableLogging Whether to enable HTTP logging
     * @return Configured OkHttpClient
     */
    public static OkHttpClient buildHttpClient(int timeoutMs, boolean enableLogging) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS);

        if (enableLogging) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor(
                    message -> logger.debug(message));
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);
            builder.addInterceptor(logging);
        }

        return builder.build();
    }
    
    /**
     * Build an OkHttpClient with default 30-second timeout
     * @param enableLogging Whether to enable HTTP logging
     * @return Configured OkHttpClient
     */
    public static OkHttpClient buildHttpClient(boolean enableLogging) {
        return buildHttpClient(30000, enableLogging);
    }
    
    /**
     * Convert OkHttp Headers to a single-value Map
     * Takes the first value for each header name
     * @param headers The OkHttp Headers object
     * @return Map with single values per header name
     */
    public static Map<String, String> headersToSingleValueMap(Headers headers) {
        return headers.toMultimap().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().isEmpty() ? "" : entry.getValue().get(0),
                        (v1, v2) -> v1 // Keep first value if duplicates
                ));
    }
    
    /**
     * Create a trust-all SSL context for development/testing
     * WARNING: This should not be used in production
     * @return SSL context that trusts all certificates
     */
    public static SSLContext createTrustAllSSLContext() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{new TrustAllX509TrustManager()}, 
                           new java.security.SecureRandom());
            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create trust-all SSL context", e);
        }
    }
    
    /**
     * Check if an HTTP status code indicates success (2xx)
     * @param statusCode The HTTP status code
     * @return true if the status code indicates success
     */
    public static boolean isSuccessCode(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }
    
    /**
     * Check if an HTTP status code indicates a client error (4xx)
     * @param statusCode The HTTP status code
     * @return true if the status code indicates client error
     */
    public static boolean isClientError(int statusCode) {
        return statusCode >= 400 && statusCode < 500;
    }
    
    /**
     * Check if an HTTP status code indicates a server error (5xx)
     * @param statusCode The HTTP status code
     * @return true if the status code indicates server error
     */
    public static boolean isServerError(int statusCode) {
        return statusCode >= 500 && statusCode < 600;
    }
    
    /**
     * Check if an HTTP status code indicates a retryable condition
     * @param statusCode The HTTP status code
     * @return true if the status code suggests the request can be retried
     */
    public static boolean isRetryableStatusCode(int statusCode) {
        // Server errors are retryable
        if (statusCode >= 500) {
            return true;
        }
        
        // Rate limiting is retryable
        if (statusCode == 429) {
            return true;
        }
        
        // Request timeout is retryable
        if (statusCode == 408) {
            return true;
        }
        
        // Service unavailable
        if (statusCode == 503) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if an HTTP status code indicates an authentication/authorization error
     * @param statusCode The HTTP status code
     * @return true if the status code indicates auth error
     */
    public static boolean isAuthError(int statusCode) {
        return statusCode == 401 || statusCode == 403;
    }
    
    /**
     * Get a user-friendly description of an HTTP status code
     * @param statusCode The HTTP status code
     * @return Human-readable description
     */
    public static String getStatusDescription(int statusCode) {
        return switch (statusCode) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 204 -> "No Content";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 408 -> "Request Timeout";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            case 504 -> "Gateway Timeout";
            default -> "HTTP " + statusCode;
        };
    }
    
    /**
     * Extract retry delay from headers (Retry-After)
     * @param headers The response headers
     * @param defaultSeconds Default value if header not present or invalid
     * @return Retry delay in seconds
     */
    public static long getRetryAfterSeconds(Map<String, String> headers, long defaultSeconds) {
        String retryAfter = headers.get("Retry-After");
        if (retryAfter == null) {
            retryAfter = headers.get("retry-after"); // case-insensitive fallback
        }
        
        return ConversionUtils.parseRetryAfterSeconds(retryAfter, defaultSeconds);
    }
    
    /**
     * Create authorization header value for basic auth
     * @param username The username
     * @param password The password
     * @return Basic auth header value (without "Basic " prefix)
     */
    public static String createBasicAuthHeader(String username, String password) {
        String credentials = username + ":" + password;
        return java.util.Base64.getEncoder().encodeToString(
                credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    
    /**
     * Create authorization header value for bearer token
     * @param token The bearer token
     * @return Bearer auth header value (without "Bearer " prefix)
     */
    public static String createBearerAuthHeader(String token) {
        return token;
    }
    
    /**
     * Trust-all X509TrustManager for development/testing
     * WARNING: Do not use in production
     */
    public static class TrustAllX509TrustManager implements X509TrustManager {
        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        @Override
        public void checkClientTrusted(X509Certificate[] certs, String authType) {
            // Trust all
        }

        @Override
        public void checkServerTrusted(X509Certificate[] certs, String authType) {
            // Trust all
        }
    }
    
    /**
     * Calculate exponential backoff delay with jitter
     * @param attempt The attempt number (0-based)
     * @param baseDelayMs Base delay in milliseconds
     * @param backoffMultiplier Multiplier for exponential backoff
     * @param maxDelayMs Maximum delay in milliseconds
     * @return Calculated delay with jitter in milliseconds
     */
    public static long calculateBackoffDelay(int attempt, long baseDelayMs, 
                                           double backoffMultiplier, long maxDelayMs) {
        double delay = baseDelayMs * Math.pow(backoffMultiplier, attempt);
        long delayMs = Math.min((long) delay, maxDelayMs);
        
        // Add some jitter to prevent thundering herd (up to 10% of the delay)
        long jitter = (long) (delayMs * 0.1 * Math.random());
        return delayMs + jitter;
    }
    
    /**
     * Check if an exception indicates a retryable network condition
     * @param exception The exception to check
     * @return true if the exception suggests the operation can be retried
     */
    public static boolean isRetryableException(Exception exception) {
        if (exception == null) {
            return false;
        }
        
        String message = exception.getMessage();
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            
            // Network-related exceptions are generally retryable
            return lowerMessage.contains("timeout") || 
                   lowerMessage.contains("connection") || 
                   lowerMessage.contains("network") ||
                   lowerMessage.contains("socket") ||
                   lowerMessage.contains("host");
        }
        
        // Check exception types
        return exception instanceof java.net.SocketTimeoutException ||
               exception instanceof java.net.ConnectException ||
               exception instanceof java.net.UnknownHostException ||
               exception instanceof java.io.IOException;
    }
}